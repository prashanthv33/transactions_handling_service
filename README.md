# Idempotent Background Transaction Processing Service

Practical FDE Assessment (FDE L2): a background service that processes transaction requests idempotently. It is built to handle requests that are duplicated, arrive out of order, or are retried after failures.

## Status

**Phase 1: Design for review.** The high-level design, assumptions and open questions are ready for feedback. Low-level design and implementation follow in Phase 2. Where an open question is still unanswered, Phase 2 uses the documented default.

## Documents

| Document | Contents |
|---|---|
| [docs/01-HLD.md](docs/01-HLD.md) | High-level design: architecture, status lifecycle, idempotency and ordering strategy, NFR mapping, assumptions, open questions |
| `docs/02-LLD.md` | *(Phase 2)* Low-level design: classes, schema, sequence diagrams, API, test matrix |
| `docs/03-DESIGN-NOTE.md` | *(Phase 2)* Final design note, AI-assisted development usage, production hardening |

## Planned stack

- Java 21
- Spring Boot 3
- Spring Data JPA
- H2 (embedded)
- JUnit 5
- Micrometer
- Logback JSON logging

## Execution instructions

Build and run instructions will be added with the Phase 2 implementation.
