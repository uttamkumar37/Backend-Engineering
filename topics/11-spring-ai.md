# Topic 11 — Spring AI: LLM Integration, Embeddings, RAG

Assumes you know how to call a REST API and haven't necessarily built an LLM-backed feature
before. This is about the specific new failure modes LLM integration introduces into an otherwise
conventional backend system — nondeterminism, cost/latency at a different order of magnitude than
a typical service call, and retrieval quality problems that look like application bugs but aren't.

---

## 1. Spring AI as an abstraction layer, and what it doesn't abstract away

- **Spring AI provides a portable API (`ChatClient`, `EmbeddingModel`, `VectorStore`) across LLM
  providers (OpenAI, Anthropic, Azure OpenAI, local models via Ollama)** the same way Spring Data
  abstracts over datastores — this matters for avoiding vendor lock-in and for testing (swapping
  in a fake/deterministic model implementation for unit tests), but it **does not abstract away
  provider-specific behavior differences**: token limits, function/tool-calling formats, and
  response quality/latency characteristics still differ meaningfully between providers and even
  between model versions from the same provider. Treating "we use Spring AI" as equivalent to
  "we're provider-agnostic in practice" overstates what the abstraction actually buys you — a
  prompt tuned against one model's behavior commonly needs rework when the underlying model
  changes, abstraction layer or not.
- **LLM calls are a fundamentally different kind of dependency than a typical downstream
  microservice call**, and the resilience patterns from Topic 4 apply but need different
  parameters: latency is an order of magnitude higher (hundreds of milliseconds to several
  seconds, more for longer generations) and highly variable, cost is per-token and can spike
  unpredictably with verbose outputs or large contexts, and failures include not just
  timeouts/errors but **silently wrong or unhelpful output that returns a 200 with no error at
  all** — a circuit breaker or retry policy tuned for a normal microservice's latency distribution
  will misbehave against this shape of dependency (e.g., a timeout set for a typical 200ms
  internal call will trip constantly against a multi-second LLM response).

---

## 2. Prompting as an interface contract, not a one-off string

- **A prompt template used in production code is an API contract with the model, and needs the
  same versioning/testing discipline as any other interface** — changing wording that seems
  cosmetic can change output structure/quality in ways that break downstream parsing, and this
  is much harder to catch than a typical breaking API change because there's no compiler or schema
  validator flagging it; it surfaces as a silent quality regression, often noticed by users before
  it's noticed in monitoring.
- **Structured output (asking the model to return JSON matching a schema, using Spring AI's
  structured output converters) should be preferred over parsing free-text responses whenever the
  consuming code needs to act on the result programmatically** — free-text parsing with regex or
  string matching against LLM output is fragile in the same way a hand-rolled JSON parser would be
  fragile compared to using an actual library, except the "format" here isn't even guaranteed
  syntactically well-formed without explicit prompting/parsing support.
- **Nondeterminism is the core new testing problem** — the same prompt can produce different
  outputs across calls (temperature > 0), which breaks the assumption most existing test practices
  (Topic 7) rely on: exact-match assertions don't work. Practical approaches: set temperature to 0
  for deterministic-as-possible behavior in tests where that's acceptable for the use case,
  assert on structural/semantic properties instead of exact strings (valid JSON matching a schema,
  contains expected entities, passes a business-rule check) rather than string equality, and treat
  a real, periodic **eval suite** (a fixed set of test prompts with quality-graded expected
  properties, potentially graded by another LLM call — "LLM-as-judge") as a genuinely different
  and necessary testing layer alongside conventional unit/integration tests, not a replacement for
  them.

---

## 3. Embeddings — what they actually represent

- **An embedding is a fixed-length numeric vector positioned in a high-dimensional space such
  that semantically similar text ends up at a small geometric distance** (cosine similarity or
  Euclidean distance, depending on the model) — this is what makes "search by meaning, not exact
  keyword match" possible, and is the mechanism behind RAG's retrieval step (Section 4).
- **Embedding models are separate from generation (chat) models and are usually far cheaper and
  faster** — a common mistake is using a full chat-completion model to do something an embedding
  model does more cheaply and appropriately (e.g., a semantic deduplication or clustering task).
- **Embedding dimensionality and model choice are effectively permanent for a given vector index**,
  much like Kafka's partition-count caveat and MongoDB's shard-key caveat from earlier topics —
  changing the embedding model (or even its version) changes the vector space, and old embeddings
  are not comparable to new ones; re-embedding an entire corpus is required after a model change,
  which is a real, sometimes expensive migration to plan for, not a config toggle.
- **Chunking strategy for embedding a document corpus is a real design decision with direct
  quality impact** — chunks too large dilute the embedding's specificity (a long chunk covering
  many topics embeds to a vague "average" position, retrieved poorly for specific queries); chunks
  too small lose surrounding context needed to make sense of the retrieved fragment on its own.
  Overlapping chunks (a sliding window with some shared context between adjacent chunks) is a
  common mitigation for context loss at chunk boundaries.

---

## 4. RAG (Retrieval-Augmented Generation)

- **The problem RAG solves**: an LLM's knowledge is frozen at training time and has no access to
  private/proprietary/current data by default. RAG retrieves relevant context from an external
  knowledge base (via embedding similarity search against a vector store) and injects it into the
  prompt at request time, letting the model answer grounded in that retrieved content instead of
  (or in addition to) its training data — this is what makes an "ask questions about our internal
  docs" or "ask questions about this order's history" feature possible without fine-tuning a model.
- **RAG's failure modes are retrieval failures far more often than generation failures**, and
  debugging a bad RAG answer should start there: if the retrieval step returns irrelevant or
  incomplete chunks (wrong chunking, an embedding model mismatched to the domain's vocabulary,
  a similarity threshold that's too loose and admits noise, or too strict and returns nothing),
  the generation model is working correctly with bad input — no amount of prompt engineering on
  the generation side fixes a retrieval problem, and treating a bad answer as a "prompting issue"
  when it's actually a retrieval issue is a very common wasted-effort debugging path.
- **Hybrid search (combining vector/semantic similarity with traditional keyword/BM25 search)**
  addresses a real weakness of pure vector search: semantic similarity can miss exact-match
  requirements (a specific product SKU, an error code, a proper noun that wasn't well-represented
  in the embedding model's training) that keyword search catches trivially — production RAG
  systems commonly combine both rather than relying on vector search alone.
- **Re-ranking**: an initial retrieval pass (fast, approximate, over a large candidate set) is
  often followed by a more expensive re-ranking step (a cross-encoder model that scores
  query-document pairs more precisely than the embedding similarity used for initial retrieval) to
  improve the final top-N results actually passed to the generation model — this two-stage
  retrieve-then-rerank pattern is standard in production RAG for exactly the same "cheap filter,
  expensive precise step" reasoning that shows up elsewhere in system design (e.g., a search
  engine's candidate generation vs. final ranking).
- **RAG does not eliminate hallucination, it reduces it** — a model can still generate content not
  actually supported by the retrieved context, especially if the retrieved context is irrelevant
  or contradictory, or if the prompt doesn't clearly instruct the model to answer only from the
  provided context and to say so when it can't. Grounding the answer with citations back to
  specific retrieved chunks (and displaying them) is both a UX trust feature and a practical way
  to make hallucination detectable by a human reviewer, rather than invisible.
- **Cost/latency compounding**: a RAG request typically involves an embedding call for the query,
  a vector store query, and then a generation call — three network round-trips (at minimum) in
  the critical path of a single user-facing request, each with its own latency/failure
  characteristics from Section 1's resilience discussion. Caching frequent queries' retrieved
  context (and even full responses, when acceptable for the use case) is a meaningful and common
  optimization given how much more expensive this request shape is than a typical CRUD endpoint.

---

## 5. Where Spring AI/LLM features fit in the capstone-style architecture from earlier topics

- **Treat an LLM-backed feature as a bounded, isolated capability behind its own service
  boundary**, not sprinkled into existing business logic — its distinct latency, cost, and
  nondeterminism profile (Sections 1–2) means it needs its own circuit breaker/timeout tuning
  (Topic 4), its own observability (Topic 9 — tracking token usage, latency percentiles, and some
  measure of output quality as first-class metrics, not just "did the call succeed"), and its own
  testing strategy (Section 2's eval-suite approach) distinct from the rest of the system.
- **Idempotency still matters** — a user-facing "ask a question" feature retried by a flaky client
  or a resilience-layer retry (Topic 4) can trigger a duplicate, costly LLM call; the same
  idempotency-key discipline from Topic 4 applies, now protecting against unnecessary cost/latency
  rather than a duplicate financial transaction, but the mechanism is identical.

---

## Interview-depth Q&A

1. Why can't a circuit breaker/timeout configuration tuned for typical internal microservice calls
   be reused unmodified for calls to an LLM provider?
2. A RAG-based support assistant gives a plausible-sounding but factually wrong answer. What's the
   first thing you'd check, and why is it usually not a prompting problem?
3. Explain why re-embedding an entire document corpus is sometimes unavoidable after a seemingly
   minor model upgrade, using what an embedding actually represents.
4. Why is exact-string-match assertion the wrong default for testing LLM-backed functionality, and
   what would you assert instead?
5. Design the chunking strategy for embedding a large corpus of legal contracts for a RAG system —
   what trade-off are you making with chunk size, and how would overlapping chunks help?
6. Why would you combine keyword (BM25) search with vector similarity search in a RAG pipeline
   instead of relying on vector search alone?

## Proof of learning
_Write one paragraph on a real or hypothetical LLM-backed feature (e.g., the capstone's
RAG-based order-support assistant) — name the single most likely failure mode (retrieval quality,
cost, latency, hallucination) and how you'd design around it before ever writing a prompt._
