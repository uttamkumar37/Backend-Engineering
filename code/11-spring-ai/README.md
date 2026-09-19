# Code Progression — Topic 11: Spring AI

Companion code for [topics/11-spring-ai.md](../../topics/11-spring-ai.md). Real Spring AI, a real
**local** LLM via Ollama (`llama3.2:1b` for chat, `nomic-embed-text` for embeddings) — no API key,
no hosted provider, no mocking.

## Prerequisites

```bash
ollama serve &
ollama pull llama3.2:1b
ollama pull nomic-embed-text
```

## Build once, then run any demo

```bash
mvn compile
mvn dependency:build-classpath -Dmdep.outputFile=cp.txt
CP="target/classes:$(cat cp.txt)"
java -cp "$CP" <fully.qualified.DemoClassName>
```

## Beginner (`beginner/`)

- **ChatClientBasicsDemo** — the smallest useful Spring AI call, against a real local model.
  Confirmed live: a real, coherent answer ("a component that temporarily interrupts... to prevent
  extreme overload") to "what is a circuit breaker?" — the same `ChatClient` API this uses is
  unchanged if you swap in OpenAI or Anthropic underneath.

## Intermediate (`intermediate/`)

- **NondeterminismDemo** — the same prompt, same model, called 3 times. Confirmed live: at
  `temperature=1.0`, three genuinely different answers (Dolphin, Rabbit, Hippopotamus); at
  `temperature=0.0`, the same answer (Rabbit) all three times. This is exactly why exact-string
  test assertions are the wrong default for LLM-backed code — and why "deterministic" still isn't
  a hard guarantee even at temperature 0 across all providers.
- **StructuredOutputDemo** — asks the model to classify a customer message into a real Java
  `record(category, urgent)` instead of parsing free text. Confirmed: the JSON-to-record parsing
  itself worked perfectly. **The classification was wrong** (`BILLING, false` for a shipping
  complaint that should have been `SHIPPING, true`) — an honest, unedited result showing that
  structured output solves the *parsing* problem, not the *accuracy* problem; a 1B-parameter local
  model is not a strong classifier, and that's a real, separate thing to evaluate.

## Advanced (`advanced/`)

- **RagDemo** — a full, real RAG pipeline: 4 short "internal facts" the model was never trained
  on, each embedded with `nomic-embed-text`; the query embedded the same way; retrieval by cosine
  similarity; generation grounded in only the retrieved chunk. Confirmed live:
  - **Retrieval worked perfectly** — the correct chunk (staging DB pool size) scored `0.8072`
    cosine similarity, clearly separated from the next-best `0.5059`.
  - **Generation still failed** — despite the correct answer being handed directly to it in the
    prompt, `llama3.2:1b` responded "I cannot provide an answer based on the provided context."
    This is a genuinely useful, unscripted result: it demonstrates that retrieval quality and
    generation capability are **separate failure axes** — the concept doc's Section 4 warns that
    most RAG failures are retrieval failures, and this run is the concrete counterexample showing
    the *other* failure mode is real too, especially with a very small local model. Retry the
    question or swap in a larger model (`llama3.2:3b` or bigger) to see generation succeed against
    the same, correctly-retrieved context.

## How to use this progression

Run `RagDemo` a few times and vary the model size — comparing where a small model succeeds vs
fails at the *generation* step, with retrieval held constant and verified correct, is the fastest
way to build real intuition for the retrieval-vs-generation distinction this topic is about.
