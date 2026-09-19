# Backend Engineering — Senior Java/Spring Concept Notes

Deep theory and internals notes for a 6-year Java/Spring Boot engineer targeting Senior
Backend Engineer / Tech Lead roles. Organized by topic, in learning order — not day-wise.
Each doc assumes you already know the syntax; it focuses on internals, trade-offs, failure
modes, and the "why," at a depth suited for staff/senior-level system design and interview rounds.

## Stack context
Java 21, Spring Boot 3.x, PostgreSQL, Kafka, AWS.

## Topics (in order)

| # | Topic | Concept doc | Code progression |
|---|---|---|---|
| 1 | Modern Java 21 internals & concurrency | [topics/01-modern-java-21.md](topics/01-modern-java-21.md) | [code/01-modern-java-21/](code/01-modern-java-21/) |
| 2 | Spring Boot 3.x internals | [topics/02-spring-boot-internals.md](topics/02-spring-boot-internals.md) | [code/02-spring-boot-internals/](code/02-spring-boot-internals/) |
| 3 | Data layer: SQL, JPA/Hibernate, Redis, NoSQL | [topics/03-data-layer.md](topics/03-data-layer.md) | [code/03-data-layer/](code/03-data-layer/) |
| 4 | Microservices patterns | [topics/04-microservices-patterns.md](topics/04-microservices-patterns.md) | [code/04-microservices-patterns/](code/04-microservices-patterns/) |
| 5 | Kafka | [topics/05-kafka.md](topics/05-kafka.md) | [code/05-kafka/](code/05-kafka/) |
| 6 | Security | [topics/06-security.md](topics/06-security.md) | [code/06-security/](code/06-security/) |
| 7 | Testing | [topics/07-testing.md](topics/07-testing.md) | [code/07-testing/](code/07-testing/) |
| 8 | Docker, Kubernetes, CI/CD, Terraform, AWS | [topics/08-containers-cicd-cloud.md](topics/08-containers-cicd-cloud.md) | [code/08-containers-cicd-cloud/](code/08-containers-cicd-cloud/) |
| 9 | Observability | [topics/09-observability.md](topics/09-observability.md) | [code/09-observability/](code/09-observability/) |
| 10 | System design & LLD | [topics/10-system-design-lld.md](topics/10-system-design-lld.md) | [code/10-system-design-lld/](code/10-system-design-lld/) |
| 11 | Spring AI (LLM, embeddings, RAG) | [topics/11-spring-ai.md](topics/11-spring-ai.md) | [code/11-spring-ai/](code/11-spring-ai/) |
| 12 | DSA maintenance notes | [topics/12-dsa-notes.md](topics/12-dsa-notes.md) | [code/12-dsa-notes/](code/12-dsa-notes/) |
| 13 | Resume, LinkedIn, mock interview prep | [topics/13-resume-interview-prep.md](topics/13-resume-interview-prep.md) | — (not a coding topic) |

All 13 concept docs complete. Code progressions (beginner → intermediate → advanced, each
verified by actually running it — real Postgres/Redis/Kafka/Podman/Prometheus/Grafana/a local
LLM, not mocked) complete for all topics where a code progression applies.
