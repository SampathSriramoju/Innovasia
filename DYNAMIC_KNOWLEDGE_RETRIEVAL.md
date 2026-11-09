# Dynamic Knowledge Retrieval & Intent Routing

## 1. Executive Summary

Previously, the Bol Kisan retrieval pipeline (`RAGService`) relied on rigid, overpowered static score boosts:
- Fixed crop boost (+0.35) applied even when the detected crop had no semantic relevance to the query.
- Fixed intent boost (+0.25) dominating the actual vector similarity score.
- Irrelevant documents being sent to Gemini, causing the LLM to hallucinate or regurgitate unrelated advisory text.

The new retrieval system implements **Dynamic Context-Aware Knowledge Retrieval** with strict relevance calibration, multi-stage hybrid scoring, and an authoritative abstention threshold.

---

## 2. Multi-Stage Retrieval Architecture

```
                  ┌──────────────────────────────────────────────┐
                  │             Farmer Natural Query             │
                  └──────────────────────┬───────────────────────┘
                                         │
                                         ▼
                             [Query Understanding]
                        Language • Crop • Intent • Entity
                                         │
                 ┌───────────────────────┴───────────────────────┐
                 ▼                                               ▼
         [Dense Vector Search]                         [Sparse BM25 Search]
      (Multilingual Embedding)                    (Morphological Token Match)
                 │                                               │
                 └───────────────────────┬───────────────────────┘
                                         │
                                         ▼
                              [Reciprocal Rank Fusion]
                                    RRF Score
                                         │
                                         ▼
                            [Calibrated Entity Boosts]
                             Crop (0.12) • Intent (0.08)
                                         │
                                         ▼
                            [Relevance Gate: Score >= 0.42]
                                /                 \
                             PASS                FAIL
                             /                     \
                   [Send to Gemini]        [Deterministic Safe Fallback]
```

---

## 3. Calibrated Relevance Scoring

Fixed boosts have been recalibrated to ensure semantic similarity remains the primary decider of document ranking:

| Component | Old Weight (Biased) | New Calibrated Weight | Purpose |
| :--- | :--- | :--- | :--- |
| **Semantic Vector Similarity** | 0.40 | **0.65** | Core semantic relevance across multilingual queries |
| **BM25 Lexical Score** | 0.20 | **0.25** | Exact chemical name, pest name, and dose number match |
| **Identified Crop Affinity** | 0.35 (Overpowering) | **0.12 (Calibrated)** | Soft preference for matched crop without excluding cross-crop practices |
| **Intent Category Affinity** | 0.25 (Overpowering) | **0.08 (Calibrated)** | Guides retrieval toward pesticide, fertilizer, or cultivation cards |
| **Minimum Relevance Gate** | None (Top-1 always sent) | **0.42** | Rejects irrelevant documents from being passed to the LLM |

---

## 4. Multi-Turn Contextual Retrieval

When a farmer asks a follow-up question (e.g., *"ఇప్పుడు అందులో సేంద్రీయంగా ఎలా నివారించాలి?"*):
1. `QueryUnderstanding` resolves `crop = "Cotton"` via `conversation_history`.
2. The retrieval query is enriched:
   $$\text{Retrieval Query} = \text{"Cotton"} + \text{"organic management remedies"} + \text{"pink bollworm"}$$
3. Retrieval fetches verified ICAR organic management cards (Jeevamrit, NSKE 5%, pheromone traps, Trichogramma egg parasitoids) specifically relevant to the established context.

---

## 5. Abstention & Safety Integrity

If no document clears the **0.42 relevance threshold**:
- The system **does not invent agricultural advice**.
- Gemini is given an explicit abstention directive.
- The query is safely escalated to a human agricultural extension officer (`status = "escalated"`).
