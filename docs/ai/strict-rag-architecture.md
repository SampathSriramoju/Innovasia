# BOL KISAN – Strict Grounded RAG Architecture Specification

## 1. Architectural Philosophy & Safety Mandate

In rural Indian agriculture, inaccurate artificial intelligence advice poses catastrophic risks: a hallucinated chemical dosage burns an entire harvest, off-label pesticide recommendations contaminate food chains, and misdiagnosed plant pathogens lead to total crop failure.

**BOL KISAN (बोल किसान / బోల్ కిసాన్)** enforces an unconditional **Strict Grounding Mandate**:
> *"Never invent, interpolate, or extrapolate agricultural advice. Every factual claim must be mathematically attributable to verified ICAR, State Agricultural University (PJTSAU/ANGRAU), or CIBRC regulatory records."*

```
   [Farmer Voice / Text Query]
               │
               ▼
┌─────────────────────────────────┐
│     QueryUnderstanding          │ ──► Extracts: Crop, Intent, Target Entity, Language
└─────────────────────────────────┘
               │
               ▼
┌─────────────────────────────────┐
│     SafetyGate (Gate 1)         │ ──► Checks: Banned Agrochemicals, Tank Mix Cocktails
└─────────────────────────────────┘
          │               │
      (Violates)      (Compliant)
          │               │
          ▼               ▼
┌──────────────────┐ ┌──────────────────────────────────────────────┐
│ Emergency Human  │ │ Hybrid RAG Retrieval (BM25 + Dense Cosine)   │
│ MAO Escalation   │ │ 23 Collections (547 Verified Chunks)         │
└──────────────────┘ └──────────────────────────────────────────────┘
                                  │
                                  ▼
                     ┌──────────────────────────┐
                     │ Evidence Formatting &    │
                     │ Document ID Tagging      │
                     └──────────────────────────┘
                                  │
                                  ▼
                     ┌──────────────────────────┐
                     │ Gemini 3.8 / 3.5 Flash   │ ──► Strict JSON Schema Output
                     └──────────────────────────┘
                                  │
                                  ▼
                     ┌──────────────────────────┐
                     │ Claim Verification Gate  │ ──► Validates source_ids against chunks
                     │ (Gate 2: Hallucination)  │
                     └──────────────────────────┘
                                  │
                                  ▼
                     ┌──────────────────────────┐
                     │ Post-Gen Safety Validate │ ──► Dosage & PHI numerical safety audit
                     │ (Gate 3: CIBRC Match)    │
                     └──────────────────────────┘
                                  │
                                  ▼
                     [Verified Farmer Response & Audio TTS]
```

---

## 2. Component Pipeline

### 2.1 Intelligent Query Understanding (`QueryUnderstanding`)
Located at `backend/app/ai/query_understanding.py`:
- **Multilingual Tokenizer**: Extracts linguistic intent across Telugu, Hindi, and English.
- **Crop Entity Recognition**: Regular expressions and keyword dictionaries identify target crops (e.g., Rice, Cotton, Maize, Chilli, Groundnut, Tomato, Red Gram, Bengal Gram, Wheat, Soybean).
- **Target Pathogen/Pest Matching**: Maps colloquial regional names (e.g., "అగ్గితెగులు", "కత్తెర పురుగు", "గులాబీ పురుగు", "झुलसा") to canonical identifiers (`rice blast`, `fall armyworm`, `pink bollworm`).
- **Intent Classifier**: Maps queries into 12 core domains (`crop_disease`, `pest_management`, `fertilizer_soil`, `irrigation`, `sowing_cultivation`, `pesticide_safety`, `mandi_market`, `weather_advisory`, `harvesting`, `crop_rotation`, `organic_farming`, `government_scheme`).

### 2.2 Hybrid Retrieval Engine (`RAGService`)
Located at `backend/app/ai/rag_service.py`:
- **BM25 Lexical Ranking**: High-precision BM25Okapi scoring using multilingual term frequencies and IDF.
- **Dense Cosine Vector Similarity**: Query token embeddings scored against precomputed document vectors.
- **Combined Hybrid Score**:
  $$\text{Score} = (0.55 \times \text{BM25}_{\text{norm}}) + (0.45 \times \text{VectorSim}) + \text{Boost}_{\text{col}} + \text{Boost}_{\text{crop}} + \text{Boost}_{\text{target}}$$
- **Dynamic Crop Isolation**: Penalizes documents belonging to unrelated crops ($-0.6$) while boosting relevant crop documents ($+1.8$).

### 2.3 Strict Evidence-Constrained LLM Generation (`GeminiService`)
Located at `backend/app/ai/gemini_service.py`:
- **Model Topology**: Primary deployment target `gemini-3.8-flash` with responsive high-availability failover to `gemini-3.5-flash`.
- **Enforced JSON Schema**: Invoked via `response_mime_type="application/json"` enforcing:
  - `status`: `"answered"` | `"insufficient_evidence"`
  - `topic`: Agronomic subject
  - `crop`: Identified crop
  - `answer`: Localized farmer explanation
  - `claims`: Array of individual factual statements, each bound to specific `source_ids`
  - `evidence_sufficient`: Boolean flag
  - `safety_status`: Status indicator
  - `escalation_required`: Escalation flag
- **Gate 2 Claim Verification**: Checks that every `source_id` referenced in `claims` corresponds to a physical retrieved chunk in the context window. Hallucinated IDs are caught and filtered before presentation.

### 2.4 Resilient Deterministic Fallback Engine
When API quota limits or network outages occur, `_generate_grounded_fallback` executes:
- Zero hallucination on dosages or crop recommendations.
- Selects the exact top retrieved RAG chunk for the query intent.
- Formulates customized, non-repetitive answers for each topic in Telugu, Hindi, or English.
