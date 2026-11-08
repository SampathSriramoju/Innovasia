# Bol Kisan — Multilingual Embedding Model Evaluation & Benchmark Report

**Audit Date:** November 9, 2026  
**Benchmarking Environment:** PyTorch 2.6.0+cpu, Windows 11 x64, 5,075 Agricultural Chunks  
**Test Suite:** 30 Hand-Crafted Trilingual Queries across Telugu, Hindi, and English (10 per language)  
**Evaluation Script:** `backend/tests/evaluate_embedding_models.py`  
**Raw Results:** `backend/embedding_model_evaluation_results.json`  

---

## 1. Executive Summary

As required by Phase 2, we conducted an empirical evaluation comparing:
1. **Pure BM25** (Lexical retrieval with multilingual tokenization).
2. **Existing Custom Dense Multilingual 384 Embedder** (`DenseMultilingual384Embedder` with concept seeds, token hashing modulo 128, and character ordinal modulo projections).
3. **Genuine Trained Transformer Model** (`sentence-transformers/paraphrase-multilingual-MiniLM-L12-v2`, 384-dimensional dense multilingual model).
4. **Existing Uncalibrated Hybrid Retrieval** (BM25 + Custom Embeddings with uncalibrated `+1.8` additive boosts).
5. **Calibrated Multilingual Hybrid Retrieval** (BM25 + Dense Semantic Features + Bounded Intent/Crop Multipliers).

Evaluation was conducted separately across Telugu, Hindi, and English to measure **Precision@1 (P@1)**, **Recall@3 (R@3)**, and **Mean Reciprocal Rank (MRR)**.

---

## 2. Empirical Benchmark Measurements

| Method / Architecture | Telugu P@1 | Telugu R@3 | Hindi P@1 | Hindi R@3 | English P@1 | English R@3 | Overall P@1 | Overall R@3 | Overall MRR |
| :--- | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: |
| **Pure BM25 (Lexical Only)** | 0.3000 | 0.7000 | 0.2000 | 0.5000 | 0.8000 | 0.9000 | **0.4333** | **0.7000** | **0.5389** |
| **Custom Dense 384 Embedder Alone** | 0.2000 | 0.4000 | 0.2000 | 0.4000 | 0.7000 | 0.7000 | **0.3667** | **0.5000** | **0.4333** |
| **Trained MiniLM-L12-v2 Alone** | 0.0000 | 0.0000 | 0.3000 | 0.4000 | 0.9000 | 1.0000 | **0.4000** | **0.4667** | **0.4333** |
| **Existing Uncalibrated Hybrid** | 0.7000 | 0.8000 | 0.6000 | 0.6000 | 0.8000 | 0.8000 | **0.7000** | **0.7333** | **0.7111** |
| **Calibrated Multilingual Hybrid** | **0.7000** | **0.8000** | **0.6000** | **0.7000** | **0.9000** | **1.0000** | **0.7333** | **0.8333** | **0.7833** |

---

## 3. Key Technical Insights & Analysis

### 3.1 Why Trained MiniLM-L12-v2 Alone Scored 0.00 on Telugu
- While `paraphrase-multilingual-MiniLM-L12-v2` is a standard multilingual sentence transformer trained on 50+ languages, its vocabulary and pretraining distribution are heavily skewed toward high-resource languages (English, German, French, Spanish, Russian, Chinese, Hindi).
- In the knowledge base, the overwhelming majority of ICAR agronomic content and CIBRC chemical labels are authored in English.
- When a farmer asks in Telugu (e.g., `"పత్తిలో గులాబీ రంగు పురుగు నివారణకు ఏ మందు వాడాలి?"`), the Telugu query embedding in MiniLM vector space does **not** map cleanly to English agricultural text (`"pink bollworm in cotton"`) without cross-lingual dictionary alignment.
- Consequently, pure vector similarity with MiniLM alone failed completely on Telugu ($P@1 = 0.0000$), whereas BM25 paired with `MULTILINGUAL_DICTIONARY` expansion achieved $P@1 = 0.3000$ and $R@3 = 0.7000$.

### 3.2 Performance of the Custom 384 Embedder
- The custom embedder (`DenseMultilingual384Embedder`) utilizes manually pre-seeded concept anchors (such as `"cotton" <-> "పత్తి" <-> "कपास" -> seed 11`).
- This artificial concept alignment gave it non-zero cross-lingual capability ($0.20$ in Telugu and Hindi), but its token hashing and character modulo projections introduced a uniform cosine noise floor of `0.30 - 0.45` across all documents regardless of actual semantic relevance.
- As a standalone retrieval model, its overall P@1 of `0.3667` is inferior to BM25's `0.4333`.

### 3.3 The Power of Calibrated Hybrid Retrieval
- **BM25** provides strict lexical grounding: queries with exact chemical names (e.g. *Coragen*, *Emamectin Benzoate*, *Pendimethalin*), crop varieties, or Telugu/Hindi translated terms match precisely.
- **Cross-Lingual Dictionary Expansion** bridges the language gap by translating Telugu and Hindi agricultural terms into English synonyms before retrieval.
- **Calibrated Multipliers** ensure that documents matching the query's identified crop and intent receive a controlled boost ($1.30\times - 1.45\times$) without obliterating base textual relevance scores.
- This hybrid combination achieves the highest performance across all three languages: **0.7333 P@1**, **0.8333 R@3**, and **0.7833 MRR**.

---

## 4. Production Embedding Architecture Recommendation

1. **Retain In-Memory Hybrid Architecture:** Do not replace the runtime embedder with a heavy 470MB transformer on CPU. The 173-second embedding latency on 5,075 documents would severely compromise mobile/edge execution and backend restart performance.
2. **Expand the Domain Dictionary:** The decisive factor for Indic retrieval precision is the cross-lingual synonym dictionary (`MULTILINGUAL_DICTIONARY`) and document-level multilingual keyword enrichment.
3. **Enforce Calibrated Bounded Scoring:** Never use additive boosts ($>0.5$). Use bounded multiplicative scaling ($1.15\times - 1.45\times$) with explicit cross-crop penalties ($0.15\times$) and minimum relevance floors ($0.32$).
