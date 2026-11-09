# Automatic Crop Identification & Context Resolution

## 1. Overview

In previous versions of Bol Kisan, the query pipeline required an explicit crop parameter (`crop_name`), which defaulted to `"Cotton"`. If a farmer asked:
> *"టమోటాలో ఆకు ముడత తెగులు నివారణ ఏమిటి?"* *(How to treat leaf curl in tomato?)*

The system would pass `crop_name="Cotton"` to retrieval algorithms, leading to mixed or incorrect pest recommendations.

The updated Bol Kisan engine implements **Automatic Multilingual Crop Identification** in `backend/app/ai/query_understanding.py` and `BolKisanRepository.kt`. The system recognizes crops naturally across Telugu, Hindi, and English, infers context from previous conversational turns, and asks for clarification when a query is genuinely ambiguous.

---

## 2. Crop Alias & Dialectal Lexicon

`QueryUnderstanding` maintains a comprehensive regional dictionary mapping dialectal and colloquial terms to canonical agricultural crops:

| Canonical Crop | Telugu (`te`) | Hindi (`hi`) | English (`en`) |
| :--- | :--- | :--- | :--- |
| **Cotton** | పత్తి, దూది, పత్తి చేను, పత్తి పంట | कपास, रुई, नरमा, कपास की फसल | cotton, kapas, patti |
| **Chilli** | మిరప, మిర్చి, ఎండుమిర్చి, పచ్చిమిర్చి | मिर्च, हरी मिर्च, लाल मिर्च, मिर्ची | chilli, chili, mirchi, mirapa |
| **Rice / Paddy** | వరి, ధాన్యం, వడ్లు, బియ్యం, వరి చేను | धान, चावल, धान की फसल | rice, paddy, dhaan, vari, vadlu |
| **Tomato** | టమోటా, టమాట, రామ్ములగ | टमाटर, टमाटो | tomato, tamatar |
| **Groundnut** | వేరుశనగ, పల్లీ, వేరుశనగ కాయలు | मूंगफली, पीनट | groundnut, peanut, palli, moongphali |
| **Maize** | మొక్కజొన్న, జొన్న, మక్కజొన్న | मक्का, भुट्टा, मकई | maize, corn, makka, mokkajonna |
| **Wheat** | గోధుమ, గోధుమలు | गेहूं, गेहूँ | wheat, gehun, godhuma |
| **Turmeric** | పసుపు, పసుపు కొమ్ము, పసుపు పంట | हल्दी, हलदी | turmeric, haldi, pasupu |
| **Mango** | మామిడి, మామిడి తోట, మామిడి కాయ | आम, आम का बाग | mango, aam, mamidi |
| **Onion** | ఉల్లి, ఉల్లిపాయ, ఎర్రగడ్డ | प्याज, कांदा | onion, pyaz, ulli, kanda |
| **Sugarcane** | చెరకు, చెరకు తోట, చెరుకు | गन्ना, ईख | sugarcane, ganna, cheraku |
| **Pulses** | కంది, పెసర, మినుము, శనగ, పప్పు | दाल, चना, अरहर, मूंग, उड़द | pulses, dal, red gram, black gram |

---

## 3. Four-Tier Identification Strategy

`QueryUnderstanding.analyze_query(query_text, context_crop=None, conversation_history=None)` resolves the crop via four sequential stages:

```
                  ┌──────────────────────────────────────────────┐
                  │          Incoming Farmer Query Text          │
                  └──────────────────────┬───────────────────────┘
                                         │
                                         ▼
                                 [Stage 1: Explicit]
                       Does query contain known crop alias?
                                    /         \
                                 YES           NO
                                 /               \
              Crop Source: "detected"             ▼
                                         [Stage 2: History]
                               Are there previous conversation turns?
                                            /         \
                                         YES           NO
                                         /               \
                       Crop Source: "history_inferred"    ▼
                                                 [Stage 3: Ambiguity Check]
                                           Is query generic/dose/pesticide?
                                                    /         \
                                                 YES           NO
                                                 /               \
                                     Status: "needs_clarification"  Crop: "General / Unknown"
```

### Stage 1: Explicit Entity Extraction
- Scans `query_text` using regex-token boundaries against the multi-dialect dictionary.
- If found, sets `crop = matched_canonical_crop` and `crop_source = "detected"`.

### Stage 2: Multi-Turn Conversation History Inference
- When a farmer asks a follow-up question such as:
  - *"మరి ఎరువుల మోతాదు ఎంత వేయాలి?"* *(Then what should be the fertilizer dose?)*
  - *"इसके लिए जैविक उपाय क्या हैं?"* *(What are the organic remedies for this?)*
  - *"What is the harvesting time?"*
- The query text itself contains no crop name.
- The system inspects `conversation_history` in reverse chronological order (`reversed(conversation_history)`).
- When a turn with an identified crop is found, it is adopted:
  - `crop = previous_turn["crop"]`
  - `crop_source = "history_inferred"`

### Stage 3: Ambiguity Detection & Clarification
- If no crop is detected and no conversational history exists, the engine checks whether the query requires a crop to provide a safe, certified answer:
  - *"పురుగు మందు ఎంత మోతాదులో కొట్టాలి?"* *(How much pesticide dose per pump?)*
  - *"कीटनाशक का सही छिड़काव कैसे करें?"* *(How to spray pesticide?)*
  - *"What is the spray dosage?"*
- Dosages cannot be given responsibly without knowing the target crop (e.g. Monocrotophos is banned on vegetables but applied on fiber crops; dosages vary significantly between Cotton and Tomato).
- The system returns:
  - `status = "needs_clarification"`
  - `is_ambiguous = True`
  - Localized clarification prompt:
    - **Telugu:** *"మీరు ఏ పంట గురించి అడుగుతున్నారు? దయచేసి పత్తి, వరి, మిర్చి లేదా టమోటా వంటి పంట పేరు చెప్పండి."*
    - **Hindi:** *"आप किस फसल के बारे में पूछ रहे हैं? कृपया कपास, धान, मिर्च या टमाटर जैसी फसल का नाम बताएं।"*
    - **English:** *"Which crop are you asking about? Please mention Cotton, Rice, Chilli, Tomato or your crop name."*

### Stage 4: Generic Agricultural Knowledge Handling
- For queries that apply universally across farming (e.g. *"సేంద్రీయ వ్యవసాయంలో జీవామృతం ఎలా తయారు చేసుకోవాలి?"* or *"What is compost preparation?"*):
  - The system recognizes the intent as `"organic_farming"` or `"general_agronomy"`.
  - It does NOT block the farmer with clarification; it answers directly using ICAR organic package guidelines with `crop = None` and `crop_source = "general"`.

---

## 4. Response Contract

The voice API endpoint (`/api/v1/voice/query`) returns explicit crop metadata in every response:

```json
{
  "query_text": "వరిలో అగ్గి తెగులు నివారణ మందులు ఏమిటి?",
  "language": "te",
  "crop_identified": "Rice",
  "crop_source": "detected",
  "is_ambiguous": false,
  "clarification_prompt": null,
  "status": "answered",
  "is_safe": true,
  "response_text": "వరిలో అగ్గి తెగులు (Blast Disease) నివారణకు...",
  "citations": ["ICAR-IIRR Hyderabad Blast Advisory 2024"],
  "follow_up_suggestions": [
    "వరి లో సేంద్రీయ నివారణ మార్గాలు ఏమిటి?",
    "వరి లో ఎరువుల యాజమాన్యం మరియు మోతాదు?"
  ]
}
```
