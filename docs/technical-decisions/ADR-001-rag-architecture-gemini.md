# ADR-001: Model-Independent RAG Architecture with pgvector and Gemini 3.8 Flash

## Status
Accepted

## Context
Bol Kisan requires an accurate, evidence-backed Question-Answering engine that can synthesize agricultural answers in Telugu, Hindi, and English. The core mandate requires that the system NEVER hallucinate agricultural advice.

We must:
1. Support modern LLMs (specifically Google Gemini 3.8 Flash via the official Google Gen AI SDK).
2. Maintain strict decoupling so that the retrieval, embeddings, and vector stores are model-independent.
3. Allow swapping or upgrading embedding models (e.g. Gemini Embeddings, BGE-M3, IndicBERT) without rewriting retrieval logic.
4. Keep the model identifier configurable via environment variables (`GEMINI_MODEL=gemini-3.8-flash`).

## Decision
1. **Orchestrator**: Use LlamaIndex with a custom retrieval node and deterministic post-filter.
2. **Vector & Hybrid Store**: Use PostgreSQL 16 with `pgvector` for dense 768-dimensional embeddings and native `tsvector` with GIN indexing for sparse lexical matching (Hybrid Search with Reciprocal Rank Fusion - RRF).
3. **LLM Provider**: Integrate Google Gemini 3.8 Flash using the official `google-genai` SDK (`from google import genai`).
4. **Prompt & Context Invariant**: The system prompt strictly prohibits the LLM from using pre-trained parametric knowledge when answering agricultural questions. Only context chunks retrieved with a cosine similarity threshold >= 0.72 are passed into the context window.
5. **Citations**: Every generated response must include explicit source references (`[KBxx-DocID]`).

## Consequences
- **Positive**: Clean separation of concerns. The LLM acts purely as a linguistic synthesizer over verified facts, preventing hallucinated advice. Easy swapping of model versions.
- **Negative**: High similarity threshold may result in "No verified information found" for uncataloged queries, which directly triggers our escalation protocol (intended behavior).
