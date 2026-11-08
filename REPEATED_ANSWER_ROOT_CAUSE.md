# REPEATED ANSWER ROOT CAUSE ANALYSIS & DIAGNOSTICS

**Date:** 2026-11-09  
**Project:** Bol Kisan — Offline/Online Agricultural AI Voice Assistant  
**Status:** Diagnosed & Documented  

---

## 1. Executive Summary

Farmers using the Bol Kisan voice assistant observed that distinct agronomic questions—especially concerning the same crop or in offline/fallback modes—frequently returned identical or nearly identical answers.

Through a rigorous 30-query diagnostic suite spanning 7 crops, 10 intents, and 3 languages (Telugu, Hindi, English), we systematically analyzed all three execution pipelines:
1. **Online Gemini Generation Pipeline** (`gemini_service.py` -> `gemini-3.5-flash-lite`)
2. **Online Deterministic Fallback Pipeline** (`gemini_service._generate_grounded_fallback()`)
3. **Android Offline Room SQLite Retrieval Pipeline** (`BolKisanRepository.kt` -> `OfflineKnowledgeDao.kt`)

We discovered three interconnected architectural defects that caused identical answers to be generated and displayed.

---

## 2. Identified Defect 1: Blind First-Document Grounded Fallback

### Affected File
- [`backend/app/ai/gemini_service.py`](file:///c:/Users/Sampath%20Sriramoju/OneDrive/Desktop/Hacakthon/innovasia/backend/app/ai/gemini_service.py#L286-L315)

### Exact Suspicious Implementation
```python
# Case 2: Synthesize directly from retrieved context chunks
if retrieved_contexts:
    top_ctx = retrieved_contexts[0]
    content = top_ctx.get("content", "")
    title = top_ctx.get("title", "ICAR Verified Practice")
    ...
    if language == "te":
        text = f"వ్యవసాయ సమాచారం ({title}):\n{clean_content}"
    ...
```

### Root Cause
Whenever the Gemini API encounters a transient rate limit (429), quota exhaustion, or network disconnect, execution drops into `_generate_grounded_fallback()`.
- The fallback method **blindly extracted `retrieved_contexts[0]`**.
- It performed **no intent validation**, **no crop verification**, **no relevance score thresholding**, and **no check on whether the retrieved chunk answers the user's specific question**.
- When queries about different cotton topics (water, weeds, soil, diseases) retrieved the same top document (or when out-of-domain queries like Bitcoin or cinema tickets retrieved low-scoring background chunks), the fallback returned the exact same content string repeatedly without abstaining.

---

## 3. Identified Defect 2: In-Memory Pseudo-Embeddings & Disproportionate Additive Boosts

### Affected File
- [`backend/app/ai/rag_service.py`](file:///c:/Users/Sampath%20Sriramoju/OneDrive/Desktop/Hacakthon/innovasia/backend/app/ai/rag_service.py#L116-L180) & [`rag_service.py:520-550`](file:///c:/Users/Sampath%20Sriramoju/OneDrive/Desktop/Hacakthon/innovasia/backend/app/ai/rag_service.py#L520-L550)

### Exact Suspicious Implementation
1. **DenseMultilingual384Embedder:**
```python
self.concept_seeds = {
    "cotton": 11, "పత్తి": 11, "ప్రత్తి": 11, "कपास": 11,
    "rice": 17, "వరి": 17, ...
}
```
The embedder is not a trained neural model. It computes word hashes (`hash(token) % 128`) and projects character ordinals into fixed vector slots. Two semantically distinct words that share a crop keyword hash to near-identical positions.

2. **Runtime Vector Database Verification:**
- Production runtime **does NOT use PostgreSQL pgvector**.
- The runtime uses **SQLite (`bolkisan.db`)** for relational tables and maintains a **purely in-memory Python list** (`self.doc_vectors: List[List[float]]`) for vector similarities.

3. **Disproportionate Additive Boosts:**
```python
# Raw text similarity is bounded [0.0, 1.0]
hybrid_score = (0.55 * bm25_norm) + (0.45 * vec_sim)

# Fixed Additive Boosts:
if doc_col in recommended_cols:
    hybrid_score += 0.8
if crop_clean in doc_crop:
    hybrid_score += 1.8
if entity in title_lower or entity in target_lower:
    hybrid_score += 1.0
if analysis.get("target_entity") in target_lower:
    hybrid_score += 1.5
```
The additive boosts (`+1.8`, `+0.8`, `+1.5`) completely overwhelmed the base lexical (BM25) and semantic vector similarity (`0.0 to 1.0`). Any document that matched the crop name automatically received a boost of +1.8, regardless of whether the document addressed irrigation, fertilizers, or diseases. As a result, the same top document was frequently ranked #1 for multiple different queries about the same crop.

4. **Query Understanding Collection Mappings:**
In `backend/app/ai/query_understanding.py`:
- `mandi_market` was mapped to `["KB16"]` (which is Agromet Weather in the KB schema).
- `weather_advisory` was mapped to `["KB15"]` (which is Post-Harvest Storage).
- `weed_management` was unmapped, failing to recommend `KB13` (Weed Management).

---

## 4. Identified Defect 3: Android Room Offline Fallback Defaulting to First Card

### Affected File
- [`apps/farmer-android/app/src/main/java/com/bolkisan/farmer/data/repository/BolKisanRepository.kt`](file:///c:/Users/Sampath%20Sriramoju/OneDrive/Desktop/Hacakthon/innovasia/apps/farmer-android/app/src/main/java/com/bolkisan/farmer/data/repository/BolKisanRepository.kt#L231-L260)
- [`apps/farmer-android/app/src/main/java/com/bolkisan/farmer/data/local/OfflineKnowledgeDao.kt`](file:///c:/Users/Sampath%20Sriramoju/OneDrive/Desktop/Hacakthon/innovasia/apps/farmer-android/app/src/main/java/com/bolkisan/farmer/data/local/OfflineKnowledgeDao.kt#L32-L34)

### Exact Suspicious Implementation
```kotlin
val localMatches = dao.searchLocalKnowledge(queryText, currentLanguage)
val finalMatch = if (localMatches.isNotEmpty()) {
    localMatches.first()
} else {
    ...
    bestCard ?: cropCards.firstOrNull()  // <-- DEFECT: Arbitrary first-card fallback
}
```

### Root Cause
1. `dao.searchLocalKnowledge` performs:
   `SELECT * FROM offline_knowledge WHERE (content LIKE '%' || :query || '%' ...)`
   Passing a full multi-word query (e.g., "How much water does cotton require?") fails the full-string `LIKE` match.
2. In the token scoring fallback, if the farmer's question addresses a topic for which no local card exists (e.g. water requirement or weed control in cotton when only PBW and leaf reddening cards are prepopulated), `highestScore` remains `0` and `bestCard` is `null`.
3. The fallback expression `bestCard ?: cropCards.firstOrNull()` **unconditionally selects the first card in the crop group**—which in the case of Cotton is always `icar-cot-pbw` (Pink Bollworm).
4. Consequently, ANY query regarding water, soil, fertilizer, or weeds in Cotton that lacked an exact token match was answered with the Pink Bollworm pesticide recommendation.

---

## 5. Diagnostic Reproduction Results (30-Query Test Matrix)

A reproduction script ([`reproduce_repeated_answers.py`](file:///c:/Users/Sampath%20Sriramoju/OneDrive/Desktop/Hacakthon/innovasia/backend/tests/reproduce_repeated_answers.py)) evaluated 30 distinct queries across crops and intents:

| Query ID | Language | Crop | Topic / Intent | Top Retrieved Doc | Fallback Mode Output | Issue Identified |
|---|---|---|---|---|---|---|
| **Q01** | EN | Cotton | Irrigation / Water | `doc-kb11-001` (KB11) | Drip irrigation chunk | Passed |
| **Q02** | EN | Cotton | Fertilizer (NPK) | `doc-kb09-001` (KB09) | NPK Fertilizer chunk | Passed |
| **Q03** | EN | Cotton | Pink Bollworm | `p1000000-...` (KB05) | Proclaim CIBRC Dose | Passed |
| **Q04** | EN | Cotton | Bacterial Blight | `d1000000-...` (KB04) | Bacterial Blight chunk | Passed |
| **Q05** | EN | Cotton | Black Soil Mgmt | `Q-CAT-E-07` (KB21) | Raw Dict string `{'id': ...}` | Ingestion bug: unparsed dict |
| **Q06** | TE | Cotton | Irrigation (Telugu) | `Q-CAT-J-01` (KB21) | Raw Dict string `{'id': ...}` | Unparsed dict returned |
| **Q07** | TE | Cotton | Fertilizer (Telugu) | `Q-CAT-E-01` (KB21) | Raw Dict string `{'id': ...}` | Unparsed dict returned |
| **Q08** | TE | Cotton | PBW (Telugu) | `c1000000-...` (KB01) | Crop Master: Cotton | Failed to retrieve PBW card |
| **Q10** | TE | Cotton | Weeds (Telugu) | `c1000000-...` (KB01) | Crop Master: Cotton | Collision with Q08 in fallback |
| **Q28** | TE | None | Weather advisory | `Q-CAT-H-02` (KB21) | Unparsed Dict string | Wrong collection recommended |
| **Q29** | EN | None | Bitcoin (Out of Domain) | `kb16-313` (KB16) | Agromet Weather Bulletin | Fallback returned irrelevant doc |
| **Q30** | TE | None | Movie tickets (OOD) | `Q-CAT-I-01` (KB21) | Sowing FAQ chunk | Fallback returned irrelevant doc |

### Key Diagnostic Findings:
1. **Gemini Online Mode:** When the Gemini API is reachable with sufficient quota, Gemini generates distinct answers. However, when RAG returns irrelevant documents, Gemini either hallucinates or points out that the context discusses something unrelated.
2. **Deterministic Fallback Mode:** When API quotas expire or network fails, fallback blindly returns `retrieved_contexts[0]`. This caused:
   - Identical `Crop Master: Cotton` responses for both pest control (`Q08`) and weed control (`Q10`).
   - Raw Python dictionaries `{'id': 'Q-CAT-...'}` dumped as answers.
   - Completely irrelevant agricultural documents returned for non-agricultural queries (`Q29` Bitcoin, `Q30` Cinema).
3. **Android Offline Mode:** `bestCard ?: cropCards.firstOrNull()` caused 100% collision for any query without an exact keyword match, forcing the Pink Bollworm card to appear for unrelated topics.

---

## 6. Targeted Remediation Plan

1. **PHASE 2 (RAG Retrieval):**
   - Replace or upgrade embedding calculation to use trained multilingual embeddings or hybrid lexical-semantic matching with calibrated multiplicative boosts instead of overpowering additive boosts.
   - Correct collection mappings in `query_understanding.py`.
   - Implement an explicit relevance threshold ($score \ge \tau$) before chunks are forwarded to the generation or fallback stage.

2. **PHASE 3 (Gemini Grounded Fallback):**
   - Refactor `_generate_grounded_fallback()` to evaluate document relevance against the detected crop and intent.
   - Clean raw dictionary contents (`KB21`/`KB22`) into readable natural text.
   - Abstain with an informative message when candidate documents do not meet the relevance threshold.

3. **PHASE 4 (Android Offline Room Engine):**
   - Remove `cropCards.firstOrNull()`.
   - Implement tokenized scoring with language and intent weighting.
   - When no relevant card exceeds threshold: return a transparent offline notice ("Information not available locally in offline mode"), retain the query, and enqueue for synchronization.
