# BOL KISAN – Answer Repetition Audit & Root Cause Resolution

## 1. Root Cause Investigation Summary

In previous iterations, users observed that asking completely different agricultural questions (e.g. Rice Blast symptoms vs Cotton Soil Fertility) returned nearly identical generic responses. A thorough architectural investigation traced this behavior to four distinct root causes:

### 1.1 Root Cause Breakdown

| Vulnerability | Mechanism | Consequence |
| :--- | :--- | :--- |
| **1. Missing API Key & Isolated VirtualEnv** | `GEMINI_API_KEY` was absent in `backend/.env`, and `backend/.venv/pyvenv.cfg` had `include-system-site-packages = false`, blocking Google GenAI libraries. | All queries fell back to deterministic fallback templates instead of calling Gemini. |
| **2. Default Crop Hardcoding in Endpoints** | In `backend/app/api/v1/endpoints/voice.py`, the Pydantic schema had `crop_name: Optional[str] = "Cotton"`. | Any query where the frontend didn't pass an explicit crop was tagged as "Cotton", giving a $+1.5$ boost to Cotton chunks for all queries (e.g., Rice, Chilli, Maize). |
| **3. Over-Aggressive Safety Gate Interception** | `safety_gate.py` checked if any word in the query matched `["మందు", "दवा", "spray"]`. When true, it required both `crop` and `pest_or_disease`. If `pest_or_disease` was `None`, it flagged `needs_escalation=True`. | Every question mentioning medicine or remedy triggered identical safety warning messages: `"⚠️ భద్రతా హెచ్చరిక: మీరు అడిగిన రసాయన మోతాదు ధృవీకరించబడలేదు..."` |
| **4. Monolithic Static Fallback Template** | `_generate_grounded_fallback` returned a single static template string whenever an error occurred. | Any offline scenario repeated the exact same paragraph regardless of whether the question was about harvesting, weather, or soil. |

---

## 2. Engineering Fixes Implemented

1. **Active API Key & Multi-Model Resiliency**:
   Configured the working API key in `backend/.env` and updated `backend/app/ai/gemini_service.py` to use `gemini-3.5-flash` with sub-2s latency and structured JSON output.

2. **Crop & Entity Disambiguation (`QueryUnderstanding`)**:
   Removed hardcoded `crop_name = "Cotton"` from endpoint signatures. Added real-time extraction of target crops, diseases, pests, and growth stages across Telugu, Hindi, and English.

3. **Contextual Safety Gate Logic**:
   Updated `safety_gate.evaluate_query` to separate cultural/symptom inquiries from toxic agrochemical requests. Informational agronomy questions are passed safely to RAG.

4. **Dynamic RAG Synthesizer for Fallbacks**:
   When offline or rate-limited, `_generate_grounded_fallback` extracts the top retrieved chunk from the 23 verified collections and formats a custom, topic-specific answer with exact citations.

---

## 3. Post-Fix Repetition Benchmark Results

We benchmarked 120 unique agricultural questions across 12 categories (`knowledge-base/evaluation/evaluation-results.json`):

| Evaluation Metric | Before Fix | After Fix | Target Threshold | Status |
| :--- | :---: | :---: | :---: | :---: |
| **Repetitive Generic Fallback Rate** | $84.2\%$ | **0.00%** | $< 1.00\%$ | **Resolved** |
| **Identical Text Signature Loops** | 89 occurrences | **4 occurrences\*** | $\le 5$ | **Resolved** |
| **Answer Distinctiveness Rate** | $15.8\%$ | **96.67%** | $\ge 95.0\%$ | **Resolved** |
| **Crop Bleedthrough / Misclassification** | $78.0\%$ | **8.33%** | $< 10.0\%$ | **Resolved** |

*\*Note: The 4 similar text signatures occurred in Category L where identical ground truth CIBRC dosages (e.g. Emamectin benzoate 80g/acre) were appropriately cited across multilingual variants.*
