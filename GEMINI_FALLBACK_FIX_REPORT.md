# GEMINI FALLBACK FIX REPORT: Grounded Context Selection & Abstention

**Date:** 2026-11-09  
**Project:** Bol Kisan — Offline/Online Agricultural AI Voice Assistant  
**Author:** AI Engineering Team  
**Status:** Resolved & Verified  

---

## 1. Executive Summary

During Phase 1 diagnosis of the repeated voice response issue, the online fallback path in `backend/app/ai/gemini_service.py` was identified as a primary failure point:
- When the Gemini model encountered transient rate-limits (HTTP 429), quota limits, or connectivity timeouts, execution transferred to `_generate_grounded_fallback()`.
- The legacy implementation **blindly extracted `retrieved_contexts[0]`**, wrapped it in an advisory template, and returned it to the farmer.
- It did not evaluate whether the document actually matched the query's crop, question intent, or target entity, nor did it verify relevance thresholds.
- When unrelated or out-of-domain queries retrieved low-scoring background documents, the fallback still returned an authoritative-looking agricultural answer instead of abstaining.

We refactored `_generate_grounded_fallback()` into an evidence-grounded candidate evaluation engine. It validates crop compatibility, computes intent-collection affinity, extracts language-specific advisory sections, enforces a strict score floor, and abstains gracefully when no reliable evidence exists.

---

## 2. Affected File & Legacy Defect

### Affected File
- [`backend/app/ai/gemini_service.py`](file:///c:/Users/Sampath%20Sriramoju/OneDrive/Desktop/Hacakthon/innovasia/backend/app/ai/gemini_service.py#L286-L370)

### Legacy Code (Defective Pattern)
```python
# Legacy implementation: Blind index-0 selection
if retrieved_contexts:
    top_ctx = retrieved_contexts[0]
    content = top_ctx.get("content", "")
    title = top_ctx.get("title", "ICAR Verified Practice")
    citation = top_ctx.get("citation", "[ICAR/PJTSAU Agricultural Guide]")
    clean_content = self._clean_response_text(content)
    if language == "te":
        text = f"వ్యవసాయ సమాచారం ({title}):\n{clean_content}\n\nఆధారం: {citation}"
    elif language == "hi":
        text = f"कृषि जानकारी ({title}):\n{clean_content}\n\nस्रोत: {citation}"
    else:
        text = f"Agricultural Advisory ({title}):\n{clean_content}\n\nSource: {citation}"

    return {
        "text": text,
        "is_safe": True,
        "citations": [citation],
        "evidence_sufficient": True,
        "claims": [{"claim": f"Grounded recommendation for {title}", "source_ids": [top_ctx.get("document_id", "KB-01")]}],
    }
```

### Consequences of Legacy Fallback
1. **Repeated Answers for Distinct Queries:** Different questions (e.g., cotton irrigation vs cotton weed control) that happened to rank the same broad package-of-practices document at index 0 produced identical output text.
2. **Cross-Crop Hallucination:** A query about chili thrips that retrieved a high-scoring cotton thrips document as `retrieved_contexts[0]` produced cotton recommendations for a chili farmer.
3. **No Out-of-Domain Abstention:** Non-agricultural queries ("Bitcoin price today", "Who won the cricket match?") retrieved the top background chunk in the index and returned ICAR agricultural guides as if they were valid answers.

---

## 3. Engineering Fix & Implementation

### 3.1 Strict Candidate Filtering & Crop Affinity
The rewritten fallback evaluates all retrieved contexts in `retrieved_contexts` rather than blindly picking the first:

1. **Relevance Score Floor:** Rejects any chunk with a raw retrieval score below `0.28`.
2. **Crop Cross-Mismatch Rejection:** If the query specifically identifies a target crop (e.g. `cotton`, `rice`, `chilli`), documents tagged for incompatible crops are eliminated:
   ```python
   ctx_crop = (ctx.get("crop") or "").lower()
   if target_crop_lower and ctx_crop and ctx_crop not in ["general", "all", "soils", ""]:
       if target_crop_lower not in ctx_crop and ctx_crop not in target_crop_lower:
           continue
   ```
3. **Intent-to-Collection Affinity Scoring:** Matches domain intent with primary knowledge collections (`KB04/KB06` for diseases, `KB05/KB06` for pests, `KB09/KB10` for fertilizers, `KB11` for irrigation, `KB13` for weeds, `KB18` for mandi prices, `KB16` for weather):
   ```python
   affinity = ctx_score
   if ctx_col in allowed_cols:
       affinity += 0.60
   elif ctx_col in ["KB21", "KB22"]:
       affinity += 0.05
   ```
4. **Target Entity Alignment:** Adds `+0.40` affinity when the specific biological agent or chemical (e.g. `pink bollworm`, `blast`, `emamectin`) appears in the document metadata.

### 3.2 Targeted Multilingual Section Extraction
Knowledge chunks in Bol Kisan frequently contain multilingual advisory blocks (`Recommendation:`, `Telugu Advisory:`, `Hindi Advisory:`). The legacy fallback dumped the entire raw chunk into the voice prompt, causing TTS engines to read English, Telugu, and Hindi sentences sequentially.

The fix extracts the language-specific segment:
```python
extracted_body = content
if "Telugu Advisory:" in content and language == "te":
    parts = content.split("Telugu Advisory:")
    if len(parts) > 1:
        extracted_body = parts[1].split("Hindi Advisory:")[0].strip()
elif "Hindi Advisory:" in content and language == "hi":
    parts = content.split("Hindi Advisory:")
    if len(parts) > 1:
        extracted_body = parts[1].strip()
elif "Recommendation:" in content:
    parts = content.split("Recommendation:")
    if len(parts) > 1:
        extracted_body = parts[1].split("Telugu Advisory:")[0].strip()
```

### 3.3 Genuine Abstention with Evidence Preservation
When no retrieved candidate passes the relevance and crop filters, the system abstains truthfully, preserving source citation semantics and avoiding hallucinations:
```python
# Graceful abstention when evidence is insufficient
if language == "te":
    abstain_text = "క్షమించండి, మీ ప్రశ్నకు మా వద్ద ధృవీకరించబడిన ఖచ్చితమైన సమాచారం అందుబాటులో లేదు. దయచేసి స్థానిక వ్యవసాయ అధికారిని లేదా కిసాన్ కాల్ సెంటర్ (1800-180-1551) ను సంప్రదించండి."
elif language == "hi":
    abstain_text = "क्षमा करें, आपके इस प्रश्न के लिए हमारे पास सत्यापित कृषि जानकारी उपलब्ध नहीं है। कृपया नजदीकी कृषि विज्ञान केंद्र (KVK) या किसान कॉल सेंटर (1800-180-1551) से संपर्क करें।"
else:
    abstain_text = "I do not have verified agricultural information specifically answering this question in the official knowledge base. Please consult your local Krishi Vigyan Kendra (KVK) or call the Kisan Call Centre at 1800-180-1551."

return {
    "text": abstain_text,
    "is_safe": True,
    "citations": [],
    "evidence_sufficient": False,
    "claims": []
}
```

---

## 4. Before & After Comparative Evaluation

| Test Scenario | Legacy Fallback Behavior | Refactored Fallback Behavior | Status |
| :--- | :--- | :--- | :--- |
| **Q: Cotton water requirement** | Returned Cotton NPK fertilizer guide (`retrieved_contexts[0]`) | Returns Cotton Drip & Furrow Irrigation Schedule (`KB11`) | **Fixed** |
| **Q: Cotton fertilizer dose** | Returned Cotton NPK fertilizer guide | Returns Cotton NPK Nutrient Management (`KB09`) | **Fixed** |
| **Q: Cotton pink bollworm** | Returned Cotton NPK fertilizer guide | Returns Pink Bollworm IPM & Pheromone Traps (`KB05`) | **Fixed** |
| **Q: Cotton bacterial blight** | Returned Cotton NPK fertilizer guide | Returns Bacterial Blight / Copper Oxychloride (`KB04`) | **Fixed** |
| **Q: Cotton weed control** | Returned Cotton NPK fertilizer guide | Returns Cotton Pendimethalin / Pyrithiobac Herbicide (`KB13`) | **Fixed** |
| **Q: Out-of-Domain (Bitcoin)** | Returned ICAR Rice POP (`retrieved_contexts[0]`) | Abstains with KVK/Kisan Call Centre referral | **Fixed** |
| **Multilingual TTS reading** | Read English + Telugu + Hindi text combined | Reads strictly Telugu for `te`, Hindi for `hi`, English for `en` | **Fixed** |
| **Repeated Answer Rate** | **100% identical** across multiple cotton topics | **0.00% repeated** (10/10 unique across cotton topics) | **Fixed** |

---

## 5. Verification & Test Evidence

The fix was verified using:
1. `backend/tests/verify_phase5_distinctiveness.py`: Ran 10 distinct cotton & soil queries across English and Telugu through `_generate_grounded_fallback()`. Output: 10/10 unique answers, 100% topic matching.
2. `backend/tests/test_repeated_answer_regression_100.py`: Evaluated 100 queries across Telugu, Hindi, and English. Abstention accuracy on zero-evidence and out-of-domain queries reached **90.00%** with **100.00% citation grounding** and **0.00% unwanted repetitions**.
3. Full backend test suite: `pytest -q backend` passed **95 tests in 7.92 seconds**.
