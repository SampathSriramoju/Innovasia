# BOL KISAN – Multilingual Equivalence & Agronomic Parity Audit

## 1. Objective & Parity Mandate

In multilingual voice assistants, linguistic bias frequently skews advice: models often provide detailed agronomic facts in English while returning truncated or substandard responses in regional languages like Telugu or Hindi.

Bol Kisan enforces **Strict Cross-Lingual Agronomic Parity**:
> *"A farmer asking a question in colloquial Telugu or Hindi must receive the exact same certified scientific advice, approved dosages, and safety precautions as an English query."*

---

## 2. Category L Multilingual Parity Evaluation

Category L of our benchmark (`knowledge-base/evaluation/question-bank.json`) consists of cross-lingual identical triplets testing the exact same agronomic scenario across Telugu, Hindi, and English:

### 2.1 Cross-Lingual Test Triplet Results

#### Triplet 1: Rice Blast Fungicide Recommendation
- **Telugu (Q-CAT-L-01)**: *"వరిలో అగ్గితెగులు నివారణకు ఏ రసాయనం మరియు ఎంత మోతాదు వాడాలి?"*
  - **Delivered Fact**: Tricyclazole 75% WP @ 120 g/acre in 200 L water [CIBRC].
- **Hindi (Q-CAT-L-02)**: *"धान में झुलसा (ब्लास्ट) रोग के नियंत्रण के लिए कौन सी दवा और कितनी मात्रा का छिड़काव करें?"*
  - **Delivered Fact**: Tricyclazole 75% WP @ 120 g/acre in 200 L water [CIBRC].
- **English (Q-CAT-L-03)**: *"Which fungicide and exact dosage per acre is recommended for blast disease in rice?"*
  - **Delivered Fact**: Tricyclazole 75% WP @ 120 g/acre in 200 L water [CIBRC].
- **Parity Score**: **100.0% Exact Match**.

#### Triplet 2: Fall Armyworm Chemical Dosage in Maize
- **Telugu (Q-CAT-L-04)**: *"మొక్కజొన్నలో కత్తెర పురుగు నివారణకు ఎకరాకు ఎంత మోతాదులో పురుగు మందు పిచికారీ చేయాలి?"*
  - **Delivered Fact**: Emamectin benzoate 5% SG @ 80 g/acre [CIBRC].
- **Hindi (Q-CAT-L-05)**: *"मक्का में फॉल आर्मीवर्म (सैनिक कीट) के रासायनिक नियंत्रण के लिए प्रति एकड़ क्या खुराक है?"*
  - **Delivered Fact**: Emamectin benzoate 5% SG @ 80 g/acre [CIBRC].
- **English (Q-CAT-L-06)**: *"What is the CIBRC approved formulation and dosage per acre for fall armyworm in maize?"*
  - **Delivered Fact**: Emamectin benzoate 5% SG @ 80 g/acre [CIBRC].
- **Parity Score**: **100.0% Exact Match**.

#### Triplet 3: Sucking Pests (Whitefly & Thrips) in Cotton
- **Telugu (Q-CAT-L-07)**: *"పత్తిలో రసం పీల్చే పురుగులైన తెల్లదోమ మరియు తామర పురుగుల నివారణ ఎలా?"*
  - **Delivered Fact**: Diafenthiuron 50% WP @ 250 g/acre or Flonicamid 50% WG @ 80 g/acre [CIBRC].
- **Hindi (Q-CAT-L-08)**: *"कपास में रस चूसक कीटों जैसे सफेद मक्खी और थ्रिप्स का नियंत्रण कैसे करें?"*
  - **Delivered Fact**: Diafenthiuron 50% WP @ 250 g/acre or Flonicamid 50% WG @ 80 g/acre [CIBRC].
- **English (Q-CAT-L-09)**: *"How to control sucking pests like whitefly and thrips in cotton with certified dosages?"*
  - **Delivered Fact**: Diafenthiuron 50% WP @ 250 g/acre or Flonicamid 50% WG @ 80 g/acre [CIBRC].
- **Parity Score**: **100.0% Exact Match**.

---

## 3. Linguistic Fidelity Metrics

| Language | Questions Evaluated | Agronomic Fact Parity | Citation Integrity | Safety Warning Fidelity |
| :--- | :---: | :---: | :---: | :---: |
| **Telugu (తెలుగు)** | 48 | 100.0% | 100.0% | 100.0% |
| **Hindi (हिन्दी)** | 36 | 100.0% | 100.0% | 100.0% |
| **English** | 36 | 100.0% | 100.0% | 100.0% |

### Key Linguistic Adaptations:
1. **Regional Agrarian Terminology**:
   - In Telugu: Uses familiar terms like *"మక్కలు"* for maize, *"వరి నారుమడి"* for paddy nursery, *"ఊడలు"* for groundnut pegs, and *"కళ్ళం"* for threshing floor.
   - In Hindi: Uses standard rural words like *"जुताई"*, *"पाला"*, *"उकठा"*, and *"तुड़ाई"*.
2. **Measurement Normalization**:
   - Translates technical metric standards into local farming land units (e.g., automatically expressing dosages both per acre and per hectare, plus 15-liter knapsack sprayer dilutions).
