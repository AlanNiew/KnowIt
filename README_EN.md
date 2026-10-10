# KnowIt

**A private, self-hosted RAG knowledge base — your documents never leave the machine room.**

A Java stack RAG knowledge base for small and medium businesses. Documents, embeddings and
inference all stay inside the customer's own network. No cloud API is ever called.

> Status: **P0 skeleton** (roadmap: [docs/architecture/roadmap.md](docs/architecture/roadmap.md))
> License: MIT
> Docs language: this file is a summary; the full design is in Chinese under `docs/`.

## Why another RAG project

Most Java RAG projects call DashScope / OpenAI APIs directly — which means every question
uploads the customer's private documents to a third-party cloud. That contradicts the entire
point of on-premise deployment.

| Aspect | Common practice | KnowIt |
|---|---|---|
| Inference | Cloud LLM + cloud embeddings | **Fully local**, OpenAI-compatible HTTP, tiered by hardware |
| Deployment | PG + ES + MinIO + MQ (7+ components) | **Lite mode: 4 containers**, one `docker compose up` |
| Chinese full-text | Elasticsearch + IK plugin | **App-layer jieba + PG tsvector**, zero C extensions |
| Weak hardware | Assumes a GPU | **Excerpt-first answering**: no LLM call, show the source text |
| Permissions | Filter by group after retrieval | **ACL inlined into SQL** — over-privileged rows are never read |
| Citations | Filename + similarity score | **Page number + char offset** — users can verify every word |
| Quality | No evaluation harness | Golden set + recall@k / refusal-accuracy regression |

**The key insight**: an SME server is typically 8 cores / 16 GB with no GPU. On CPU, local LLM
throughput is bound by *prefill* — one useful RAG answer needs 1,500–3,000 tokens of context,
which means ~100 s to first token with a 4B model. Interactivity collapses.

So KnowIt's default answering mode is **EXCERPT**: hybrid retrieval + rerank, then show the
source passages directly (highlighted, with page numbers). Sub-second, zero hallucination,
fully explainable. When a GPU is present it automatically upgrades to AI summarization.

## Architecture in one line

One Spring Boot process (Java 21 virtual threads) + one PostgreSQL 16 (pgvector as the vector
store) + an OpenAI-compatible local inference layer.

Start with [docs/architecture/overview.md](docs/architecture/overview.md).

## Quick start

> P0 skeleton: database and inference wiring lands in T0.2/T0.3. This only proves the
> engineering skeleton works.

```bash
./mvnw clean verify          # unit tests + format + style + architecture + package
./mvnw -pl knowit-server spring-boot:run

curl http://localhost:8080/ping
```

## Tech stack

Java 21 · Spring Boot 3.5.16 · Spring AI 1.1.8 · PostgreSQL 16 + pgvector · jieba4j ·
Flyway · bge-m3 + bge-reranker-v2-m3 · Qwen3 · htmx

Rationale in [ADR-0015](docs/adr/0015-framework-baseline.md).

## License

[MIT](LICENSE)
