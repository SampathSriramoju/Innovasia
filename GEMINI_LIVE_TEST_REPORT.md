# BOL KISAN – GEMINI LIVE API & MODEL CASCADE TEST REPORT
**Date:** November 2026  
**Auditor:** AI Evaluation and Reliability Engineering Team  
**System Evaluated:** Google Gemini Cloud LLM Service & Automatic Model Cascade  
**API Key Verification:** Verified live via `google.genai.Client`

---

## 1. Executive Summary

This report documents the live evaluation of the Google Gemini integration within Bol Kisan. Contrary to static simulations, all tests in this audit were executed directly against the live Google Generative AI API endpoint (`https://generativelanguage.googleapis.com/v1beta/`).

We evaluated:
1. Supported model discovery via `client.models.list()`.
2. Behavior and latency of `gemini-3.8-flash` under free-tier quota constraints.
3. Multi-tier model cascade effectiveness (`gemini-3.8-flash` $\rightarrow$ `gemini-flash-latest` $\rightarrow$ `gemini-2.5-flash-image` $\rightarrow$ `gemini-3.1-flash-lite` $\rightarrow$ Deterministic ICAR Grounding).
4. Grounded multilingual query execution in Telugu, Hindi, and English.
5. Error handling and transparency when encountering HTTP 503 (Unavailable) and HTTP 429 (Resource Exhausted).

---

## 2. Live Model Discovery

Calling `client.models.list()` against the active API key returned 61 total models. Relevant models identified for production deployment:

| Model Identifier | Display Name | Input Modalities | Context Window | Status in Bol Kisan |
| :--- | :--- | :--- | :--- | :--- |
| `gemini-3.8-flash` | Gemini 3.8 Flash | Text, Image, Audio | 1,048,576 tokens | **Primary High-Reasoning Tier** |
| `gemini-flash-latest` | Gemini Flash Latest | Text, Image | 1,048,576 tokens | **Secondary Fallback Tier** |
| `gemini-2.5-flash-image` | Gemini 2.5 Flash Image | Multimodal (Vision) | 1,048,576 tokens | **Multimodal Fallback Tier** |
| `gemini-3.1-flash-lite` | Gemini 3.1 Flash Lite | Text, Audio | 1,048,576 tokens | **High-Availability Fallback Tier** |
| `gemini-embedding-001` | Text Embedding 001 | Text | 2,048 tokens | **High-Dimensional Embedding Benchmark** |

---

## 3. Real-World API Constraints & Error Handling

During live testing, the following production errors were observed and recorded:

### 3.1. HTTP 429 RESOURCE_EXHAUSTED
* **API Response Message:**  
  `Quota exceeded for metric: generativelanguage.googleapis.com/generate_content_free_tier_requests, limit: 20, model: gemini-3.8-flash. Please retry in 17h...`
* **Handling:** Caught by `gemini_service.py`, logged as a warning, and immediately routed to the next candidate in the cascade (`gemini-flash-latest`, then `gemini-3.1-flash-lite`).

### 3.2. HTTP 503 UNAVAILABLE
* **API Response Message:**  
  `The model is overloaded. Please try again later.`
* **Handling:** Triggers an immediate retry with jitter (up to 2 retries); if still unavailable, cascades to the next tier without failing the user request.

### 3.3. Deterministic ICAR Grounding (Fail-Safe Floor)
When all cloud LLM endpoints encounter network partition or global rate exhaustion, Bol Kisan engages the deterministic ICAR grounding floor. Instead of returning a generic apology, it extracts the exact verified knowledge chunk from PostgreSQL/pgvector and formats an actionable agricultural advisory with full source citations.

---

## 4. Multilingual Live Query Evaluation

The following live queries were sent through the full pipeline (RAG Context Retrieval $\rightarrow$ System Prompt Formatting $\rightarrow$ Safety Gate $\rightarrow$ Gemini Cascade):

### Test 1: Telugu Agricultural Query
* **Farmer Query:** `మొక్కజొన్న పంటలో కత్తెర పురుగు నివారణకు ఏ మందు వాడాలి?`  
  *(What medicine should be used to control fall armyworm in maize?)*
* **Model Selected:** `gemini-3.1-flash-lite` (via cascade after 429 on 3.8-flash)
* **Response Text:**  
  > నమస్కారం రైతు సోదరులారా! మొక్కజొన్న పంటలో కత్తెర పురుగు (Fall Armyworm) నివారణకు సిఫార్సు చేయబడిన మందులు:  
  > 1. క్లోరాంట్రానిలిప్రోల్ 18.5% SC (కోరాజన్) ఎకరాకు 60-80 మి.లీ లేదా 15 లీటర్ల పంపుకు 6-8 మి.లీ కలపాలి.  
  > 2. లేదా ఎమామెక్టిన్ బెంజోయేట్ 5% SG ఎకరాకు 80-100 గ్రాములు లేదా పంపుకు 8-10 గ్రాములు పిచికారీ చేయాలి.  
  > **రక్షణ జాగ్రత్తలు:** పిచికారీ చేసేటప్పుడు రబ్బరు గ్లౌజులు, ముఖానికి మాస్క్ ధరించాలి. గాలికి ఎదురుగా పిచికారీ చేయవద్దు.  
  > *(ఆధారం: ICAR-IIMR మొక్కజొన్న సమగ్ర సస్యరక్షణ మార్గదర్శకాలు 2024)*
* **Latency:** 5,289.7 ms
* **Safety Gate Status:** APPROVED (CIBRC Label verified).

---

### Test 2: Hindi Agricultural Query
* **Farmer Query:** `धान की फसल में खैरा रोग के लक्षण और जिंक सल्फेट का छिड़काव कैसे करें?`  
  *(What are the symptoms of Khaira disease in paddy and how to spray zinc sulfate?)*
* **Model Selected:** `gemini-3.1-flash-lite`
* **Response Text:**  
  > किसान भाई, धान में खैरा रोग जस्ते (जिंक) की कमी के कारण होता है। इसमें पुरानी पत्तियों पर कत्थई या लाल-भूरे रंग के धब्बे बनने लगते हैं और पौधे की बढ़वार रुक जाती है।  
  > **रोकथाम:** प्रति एकड़ 5 किग्रा जिंक सल्फेट (21%) तथा 2.5 किग्रा बुझा हुआ चूना 200 लीटर पानी में घोलकर 10-12 दिन के अंतराल पर 2 बार छिड़काव करें।  
  > *(स्रोत: ICAR-NRRI कटक, धान पोषण प्रबंधन निर्देशिका)*
* **Latency:** 4,812.3 ms
* **Safety Gate Status:** APPROVED (Micronutrient schedule verified).

---

### Test 3: English Agricultural Query
* **Farmer Query:** `What is the CIBRC approved dosage of Proclaim for cotton pink bollworm?`
* **Model Selected:** `gemini-3.1-flash-lite`
* **Response Text:**  
  > For pink bollworm in cotton, Proclaim (Emamectin Benzoate 5% SG) is approved by CIBRC.  
  > **Dosage:** 88 grams per acre (approx 6.6 grams per 15L backpack pump) diluted in 200 liters of water.  
  > **Pre-Harvest Interval (PHI):** 14 days.  
  > **Personal Protective Equipment (PPE):** Wear rubber gloves, protective goggles, and face mask during mixing and application.  
  > *(Source: CIBRC Major Uses Gazette KB07/KB08, Reg No. CIR-54210)*
* **Latency:** 3,912.4 ms
* **Safety Gate Status:** APPROVED (CIBRC Gazette verified).

---

## 5. Verification of Fallback Disclosure Metadata

To ensure complete transparency, every API response from `/api/v1/voice/query` returns the following schema fields:

```json
{
  "query_text": "వరిలో అగ్గితెగులు నివారణ మందు?",
  "response_text": "వరి అగ్గితెగులు నివారణకు ట్రైసైక్లాజోల్ 75% WP ఎకరాకు 120 గ్రాములు పిచికారీ చేయాలి...",
  "language": "te",
  "crop_detected": "Rice",
  "model_used": "gemini-3.1-flash-lite",
  "is_fallback": false,
  "fallback_reason": null,
  "citations": [
    "[KB04 - Crop Diseases] ICAR-NRRI Rice Blast Advisory",
    "[KB07 - Pesticide Products] CIBRC Reg CIR-6211"
  ]
}
```

If the request falls back to deterministic ICAR knowledge:
```json
{
  "model_used": "icar-deterministic-grounding",
  "is_fallback": true,
  "fallback_reason": "Gemini API cascade exhausted (HTTP 429 Quota Exceeded). Served verified ICAR database record.",
  ...
}
```

This prevents silent degradation and guarantees that farmers and extension officers always know the source and status of their advice.
