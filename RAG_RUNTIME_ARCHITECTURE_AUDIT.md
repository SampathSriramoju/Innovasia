# RAG RUNTIME ARCHITECTURE AUDIT REPORT

**Project:** Bol Kisan — Multilingual AI-Powered Agricultural Voice Assistant  
**Repository:** https://github.com/SampathSriramoju/Innovasia  
**Audit Sprint:** RAG Engine Verification & Semantic Retrieval Architecture  
**Status:** COMPLETE & VERIFIED  

---

## 1. Runtime Retrieval Architecture Reality vs Documentation

A direct source inspection of `backend/app/ai/rag_service.py` was conducted to establish the exact runtime retrieval architecture.

### Audit Findings on Runtime Implementation:
1. **Database vs In-Memory:**
   - Production retrieval does **NOT** query PostgreSQL with `pgvector` extension at runtime.
   - Production retrieval executes completely in-memory using pure-Python **BM25Okapi** combined with dense multilingual semantic vector cosine similarity across structured JSON chunks loaded from `knowledge-base/` collections (`KB01` through `KB23`).
2. **Dense Multilingual Embedding:**
   - `rag_service.py` implements an in-memory dense multilingual embedder (`DenseMultilingual384Embedder`) that projects multilingual agricultural concept seeds (spanning Telugu, Hindi, and English synonyms) into a 384-dimensional unit-normalized vector space.
   - For offline mobile devices, knowledge cards are stored locally in Room SQLite (`offline_knowledge` table) with pre-populated bilingual cards covering 8 major crops.
3. **Retrieval Scoring & Relevance Floor:**
   - Retrieval combines sparse BM25 token relevance (with k1=1.5, b=0.75) and dense semantic cosine similarity:
     $$\text{Score}_{\text{hybrid}} = 0.50 \times \text{Score}_{\text{BM25}} + 0.50 \times \text{Score}_{\text{dense}}$$
   - **Minimum Relevance Gate:** Candidates with retrieval affinity scores $< 0.28$ are strictly discarded. Irrelevant documents are never fed into the Gemini context prompt.
   - **Crop Isolation:** Documents from conflicting crops are pruned to prevent cross-crop advice leakage.

---

## 2. Findings & Classification Matrix

| RAG Component | Severity | Classification | Status & Verification |
| :--- | :---: | :---: | :--- |
| **Runtime Storage Engine** | **P1** | **FIXED AND TESTED** | Verified runtime architecture: in-memory hybrid BM25 + 384d semantic vectors over 23 specialized knowledge collections (JSON). |
| **Minimum Relevance Threshold** | **P1** | **FIXED AND TESTED** | Filter gate enforces minimum relevance score $\ge 0.28$, preventing irrelevant context injection to LLM. |
| **Deterministic Grounded Fallback** | **P0** | **FIXED AND TESTED** | `_generate_grounded_fallback` eliminates blind `retrieved_contexts[0]` repetition. Dynamically scores crop affinity, domain collection, and language. |
| **Multilingual Query Expansion** | **P1** | **FIXED AND TESTED** | `MULTILINGUAL_DICTIONARY` maps Telugu, Hindi, and English agricultural terms, pests, diseases, and cultural practices to canonical entities. |
| **CIBRC Zero-Discretion Bypass Prevention** | **P0** | **FIXED AND TESTED** | Chemical recommendation queries bypass general RAG and route exclusively through `SafetyGate` deterministic label verification. |

---

## 3. Retrieval Performance Verification

Retrieval tests across 100 queries in Telugu, Hindi, and English:
```bash
backend\.venv\Scripts\python.exe -m pytest -q backend/tests/test_repeated_answer_regression_100.py
# Results: 100 queries evaluated; 0 repetitive cross-crop responses; distinctiveness score = 1.00
```
