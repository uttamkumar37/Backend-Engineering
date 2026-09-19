# Topic 13 — Resume, LinkedIn, and Mock Interview Prep

This is the packaging layer for everything in Topics 1–12 — the technical depth doesn't help if a
resume doesn't get past the first 20-second scan, or if a strong system design answer is undercut
by weak signaling in the behavioral round. This topic is about presentation and interview
mechanics specifically calibrated to a Senior Backend Engineer / Tech Lead bar, not general
job-search advice.

---

## 1. Resume — what actually changes at 6 years of experience

- **The single biggest shift from mid-level to senior resumes is impact/scale replacing
  responsibility descriptions.** "Built REST APIs using Spring Boot" describes an activity a
  junior engineer also did on day one; "Redesigned the order-processing pipeline to use the
  outbox pattern, eliminating a class of duplicate-payment incidents and reducing on-call pages by
  40%" describes a senior-level outcome — the same underlying work (Topic 4's outbox pattern)
  framed by its measured effect, not its mechanism. Every bullet should answer "so what" — if a
  bullet reads the same whether written by someone with 2 years or 10 years of experience, it's
  signaling the wrong level.
- **Quantify wherever honestly possible, but don't fabricate false precision.** Latency
  reduction (p99 went from X to Y — ties directly to Topic 9's percentile discipline), cost
  savings (infrastructure cost reduction from a redesign), throughput/scale handled (requests/sec,
  data volume), incident reduction, or team/mentoring scope (engineers mentored, on-call rotation
  led) are the categories that read as senior-level evidence. If a genuine number isn't available,
  a specific, concrete outcome ("passed a security audit with zero critical findings after a
  redesign") is still far stronger than an unquantified activity description — the failure mode to
  avoid is neither fabricating a number nor giving up and reverting to a responsibility list.
- **A tech-stack laundry list at the top of a resume ("Java, Spring Boot, Kafka, Redis, Docker,
  Kubernetes, AWS, Terraform, ...") is largely wasted space beyond a short keyword-matching
  section** — it doesn't differentiate candidates (everyone applying lists similar stacks) and
  says nothing about depth or judgment. It still has a role for ATS keyword matching (Section
  1.1), but the *bullets* under each role are where the actual signal lives, and should show the
  reasoning/trade-offs from Topics 1–10, not just name-drop the technology.
- **ATS (Applicant Tracking System) keyword matching is real but should be satisfied honestly,
  not gamed.** Mirroring the exact terminology used in a target job description (e.g., "circuit
  breaker," "event-driven architecture," "OAuth2/OIDC") where it's genuinely true of your
  experience improves match scores without resorting to invisible white-text keyword stuffing
  (which is both an integrity issue and increasingly detected/penalized by modern ATS tools) —
  the practical approach is tailoring the resume's specific bullets and terminology per
  application to reflect the actual job description's emphasis, not maintaining one static
  resume for every application.
- **Length**: at 6 years, a tight one page is still achievable and often preferred, but bleeding
  slightly into a second page is acceptable if every line is earning its place — the actual
  failure mode is a padded two-page resume with low information density, not the page count
  itself. Cut anything from early career that no longer reflects the level being targeted (a
  first-job internship bullet list rarely belongs on a senior resume next to a redesign that
  eliminated a production incident class).
- **Common mistakes at this level, specifically**: passive voice burying who did what ("APIs were
  designed..." vs. "Designed and led..."); leading with technology instead of outcome; describing
  what a *team* did without clarifying individual contribution or leadership role, which is
  exactly the ambiguity a hiring manager needs resolved to calibrate seniority; and omitting any
  mention of mentoring, technical decision ownership, or cross-team influence — for a Tech
  Lead-track application specifically, the complete absence of these signals reads as "strong
  IC, unclear leadership readiness" even if the underlying experience exists but was never written
  down.

---

## 2. LinkedIn — how it's actually searched, and why the weekly blog posts compound

- **Recruiters search LinkedIn largely via keyword/boolean-style queries against headline, current
  title, "About," and skills sections** — a headline that just repeats the current job title
  ("Software Engineer at CompanyX") wastes prime searchable real estate compared to one naming
  the actual specialization and level being targeted ("Senior Backend Engineer | Java, Spring
  Boot, Kafka, Distributed Systems") — this single change measurably affects inbound recruiter
  reach because it's directly matched against common search terms.
- **The "About" section is where the resume's impact-framing discipline (Section 1) should be
  restated in a slightly more narrative form** — not a copy-paste of the resume, but a short,
  specific summary of the kind of problems you solve and at what scale, written for a human
  skimming quickly, with the same "outcome over activity" framing.
- **This 120-day plan's weekly review posts are not a side activity — they're the actual content
  strategy.** Posting a genuine, specific technical write-up (e.g., "why I model domain states as
  sealed interfaces instead of enums," drawn directly from Topic 1's Week 1 material) accomplishes
  two things a generic "day 47 of learning" post does not: it demonstrates real depth to anyone
  evaluating the profile (including a hiring manager who looks at LinkedIn activity before an
  interview, which happens more than candidates assume), and it compounds — a small, consistent
  stream of specific technical posts over 120 days builds a visible track record that a resume
  bullet alone can't convey, because it shows the *reasoning*, not just the claimed outcome.
  Posts that connect a real production lesson to a named pattern (e.g., "the exact race condition
  that makes a Redis-only idempotency check unsafe, and the fix" — straight out of Topic 4) are
  far more differentiating than generic career-milestone posts.
- **Visibility settings ("Open to Work") are a genuine trade-off, not a strictly-positive
  toggle** — the public banner increases inbound recruiter volume but is visible to your current
  employer's network too if not set to "recruiters only," which matters if the job search should
  stay confidential; the recruiters-only setting gets most of the sourcing benefit without that
  exposure and is the reasonable default for an employed candidate.

---

## 3. Mock interviews — the rounds a senior/tech-lead loop actually contains

- **A typical senior backend loop**: a recruiter/phone screen, one DSA round (Topic 12 — usually
  a single, somewhat higher-bar question rather than two, since DSA is a filter at this level, not
  the focus), one or two system design rounds (Topic 10's HLD framework), sometimes a separate LLD
  round, a behavioral/leadership round, and a hiring-manager round that blends technical judgment
  with role/team fit. **The behavioral and hiring-manager rounds carry disproportionate weight for
  a Tech Lead-track role** compared to earlier-career loops, and are the rounds most candidates
  under-prepare for relative to their actual importance in the hiring decision.
- **STAR (Situation, Task, Action, Result) answers at senior level need a different emphasis than
  at mid-level**: the "Action" section should foreground *decisions made under ambiguity or
  disagreement*, not just execution — a strong senior behavioral answer names a specific
  technical trade-off you owned the call on (and why), a time you pushed back on a requirement or
  timeline with reasoning, how you influenced an outcome without formal authority over the people
  involved, and how you handled a production incident as an owner (not just a participant) —
  including what changed afterward (a postmortem action item actually implemented, ideally
  traceable to a concrete pattern from Topics 1–9, e.g., "we added the idempotent-consumer pattern
  after a duplicate-processing incident"). A STAR answer that's all "Situation/Task" narrative and
  thin on the actual decision-making in "Action" reads as someone who executed well but wasn't the
  one steering — the opposite of what a Tech Lead loop is evaluating for.
- **Mentoring and cross-team influence need explicit, prepared examples**, not left to come up
  organically — "tell me about a time you helped a struggling engineer" or "tell me about
  influencing a decision outside your immediate team" are close to guaranteed questions in a
  Tech Lead loop, and an unprepared, vague answer here is a common and avoidable way strong
  technical candidates underperform relative to their actual ability.
- **Practicing system design mock interviews specifically benefits from another person (a peer
  mock, a platform like Pramp, or a paid mock service) over solo practice**, because the actual
  skill being tested includes reading and responding to an interviewer's follow-up questions and
  requirement changes in real time (Topic 10, Section 1) — solo practice can rehearse the
  framework's steps but can't rehearse the adaptive, conversational half of the skill, which is
  often where the differentiation between "solid" and "senior-level" performance actually shows up.
  **Recording a mock session and reviewing it afterward** (specifically checking: did I ask
  clarifying questions before designing, did I name trade-offs unprompted, did I manage the time
  budget across the framework's steps) is a high-leverage but frequently skipped step — most
  people underestimate how much of their own interview performance (rambling, filler words, not
  structuring an answer) is only visible on review, not from the inside.
- **The specific senior-level "signal" an interviewer is listening for across every round,
  technical or behavioral, is comfort with ambiguity and the ability to drive structure into it
  unprompted** — this is the same theme from Topic 10, Section 1, applied to the whole loop, not
  just the system design round: a candidate who waits to be told what to do next, in a DSA
  problem, a design prompt, or a behavioral question about a messy real situation, is
  demonstrating exactly the gap a Tech Lead-level bar is screening for.

---

## Interview-depth Q&A (used as self-assessment here, not for an interviewer)

1. Pick three resume bullets from your current resume. For each, does it describe an outcome or
   an activity — and if it's an activity, what's the actual measurable or concrete outcome you can
   rewrite it around?
2. Write your LinkedIn headline right now. Does it contain the specific title and specialization
   keywords a recruiter searching for a Senior Backend Engineer with your stack would actually type?
3. Prepare one STAR answer for "tell me about a technical decision you made that you'd make
   differently today." What made it hard, and what would you actually change?
4. Prepare one STAR answer for a production incident you owned, ending with a specific pattern
   from Topics 1–9 (outbox, idempotency key, circuit breaker, etc.) that was adopted afterward.
5. Prepare one STAR answer for influencing a decision outside your direct reporting line or team —
   what made people actually change their approach, not just hear you out?
6. Record yourself doing one full system design mock end to end. On review: did you ask
   clarifying questions before designing anything, and did you name a trade-off in your own design
   without being asked?

## Proof of learning
_Rewrite your current resume's top 3 bullets using the outcome-over-activity framing from Section
1, and write your target LinkedIn headline — post both here as the final proof-of-learning entry
for the 120-day plan._
