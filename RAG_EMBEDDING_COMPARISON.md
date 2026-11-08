# BOL KISAN – RAG RETRIEVAL & EMBEDDING COMPARISON BENCHMARK
**Date:** November 2026  
**Auditor:** Knowledge Engineering & RAG Evaluation Team  
**Dataset:** 562 Multilingual Chunks (Telugu, Hindi, English) across 23 ICAR Knowledge Collections  
**Evaluation Set:** 20 Gold-Standard Agricultural Query Pairs across Agronomy, Pest Management, Fertilizers, and Safety

---

## 1. Executive Summary

Previous iterations of Bol Kisan relied on a lightweight 256-dimensional random projection Hashing Embedder. When evaluated against real-world farmer queries in Indic languages (e.g., Telugu terms like `కత్తెర పురుగు`, `అగ్గితెగులు`, `పిల్లిపిచికారీ`), the baseline embedder suffered from high semantic collision rates and failed to distinguish between closely related crop protection concepts.

This benchmark rigorously evaluates 5 retrieval architectures across Precision@1, Recall@3, Latency (ms), Storage overhead, and Deployment viability.

---

## 2. Comparative Benchmark Matrix

All metrics below were empirically measured against the production 562-chunk PostgreSQL knowledge repository:

| Model / Pipeline | Embedding Dim | Precision@1 | Recall@3 | Avg Latency (ms) | Artifact Size | License / Cost | Production Verdict |
| :--- | :---: | :---: | :---: | :---: | :---: | :---: | :--- |
| **BM25 Alone** (Lexical Inverted Index) | N/A | 30.0% | 50.0% | **6.59 ms** | 0.4 MB | Apache-2.0 / $0 | Fast keyword matcher; fails on dialect synonyms |
| **256-d Hashing** *(Historical Baseline)* | 256 | 10.0% | 20.0% | 77.36 ms | **0.1 MB** | MIT / $0 | **REJECTED**: Severe semantic collapse |
| **`v2.0-agri-multilingual-384`** | 384 | 30.0% | 65.0% | 109.00 ms | 1.2 MB | Apache-2.0 / $0 | Strong Indic semantic grounding |
| **`gemini-embedding-001`** (Cloud Dense) | 3072 | **95.0%** | **100.0%** | 182.40 ms | Cloud | Commercial API | High accuracy; requires active internet & spend |
| **Production Hybrid (BM25 + 384-d)** | **384** | **65.0%** | **85.0%** | **127.66 ms** | **1.6 MB** | **Apache-2.0 / $0** | **SELECTED FOR PRODUCTION**: Zero-cost, resilient, high recall |

---

## 3. Detailed Performance Analysis

### 3.1. Why 256-d Hashing Failed
The 256-d hashing embedder mapped words into a low-dimensional pseudo-random subspace using feature hashing:
- Unrelated terms with matching character n-grams shared identical buckets.
- Telugu conjunct characters (e.g., `క్ష`, `త్ర`, `ఙ్ఞ`) produced skewed hash distributions.
- Recall@3 was only **20.0%**, causing the RAG retriever to inject irrelevant chunks into the prompt, confusing the LLM.

### 3.2. Advantages of `v2.0-agri-multilingual-384`
- Retains subword tokenization tuned for Devanagari and Telugu scripts.
- Aligns agricultural cross-lingual equivalents:
  - Telugu: `కత్తెర పురుగు` $\longleftrightarrow$ English: `Fall armyworm` $\longleftrightarrow$ Scientific: *Spodoptera frugiperda*
  - Telugu: `అగ్గితెగులు` $\longleftrightarrow$ Hindi: `झुलसा रोग` $\longleftrightarrow$ English: `Blast disease` (*Magnaporthe oryzae*)
- Pure local execution without network calls or external API quotas.

### 3.3. The Hybrid Retrieval Fusion Formula
To capture both exact product brand names (e.g., *Proclaim*, *Coragen*, *Bavistin*) and broad semantic queries, Bol Kisan deploys Reciprocal Rank Fusion / Convex Combination:

$$\text{Final\_Score}(d) = \alpha \cdot \frac{\text{BM25\_Score}(d)}{\max(\text{BM25})} + (1 - \alpha) \cdot \frac{\text{Cosine\_Sim}(e_q, e_d) + 1}{2}$$

Where $\alpha = 0.50$ balances exact keyword matching and multilingual semantic intent.

---

## 4. Query-Level Retrieval Test Samples

| Query (Language) | Target Entity | BM25 Retrieved Chunk | Dense 384-d Retrieved Chunk | Hybrid Top-1 Retrieved | Status |
| :--- | :--- | :--- | :--- | :--- | :---: |
| `వరి అగ్గితెగులు నివారణ` (te) | Rice Blast (KB04) | KB04-002 (Blast) | KB04-002 (Blast) | **KB04-002 (Tricyclazole 75% WP)** | **CORRECT** |
| `ప్రత్తిలో గులాబీ రంగు పురుగు` (te) | Pink Bollworm (KB05) | KB01-001 (Cotton) | KB05-001 (Pink Bollworm) | **KB05-001 (Emamectin Benzoate)** | **CORRECT** |
| `यूरिया और डीएपी की मात्रा` (hi) | Fertilizer Schedule (KB09) | KB09-001 (Fertilizers) | KB09-001 (Fertilizers) | **KB09-001 (Basal NPK Schedule)** | **CORRECT** |
| `CIBRC dosage of Proclaim` (en) | Chemical Label (KB07) | KB07-003 (Proclaim) | KB07-003 (Proclaim) | **KB07-003 (88g/acre, CIBRC Reg)** | **CORRECT** |
| `నల్లరేగడి నేలలో పత్తి సాగు` (te) | Soil Health (KB10) | KB01-001 (Cotton) | KB10-001 (Black Soils) | **KB10-001 (Gypsum & Drainage)** | **CORRECT** |

---

## 5. Storage and Database Verification

- **PostgreSQL Database:** Port 5432, Database `bolkisan`.
- **Knowledge Chunks Table:** `knowledge_chunks` (562 active records).
- **Vector Extension:** `pgvector` enabled with HNSW index for sub-10ms similarity searches on the 384-dimensional vectors.
- **Integrity Check:** Zero null embeddings; foreign key references to ICAR source collections validated.
