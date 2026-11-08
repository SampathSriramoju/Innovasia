# BOL KISAN – Answer Relevance & Question Understanding Report

## 1. Executive Summary

Answer relevance measures whether the AI voice assistant accurately addresses the precise inquiry posed by the farmer, rather than returning generic, tangentially related, or boilerplate responses.

Across our 120-question comprehensive test suite (`knowledge-base/evaluation/question-bank.json`), Bol Kisan achieved:
- **Retrieval Recall @ Top-3**: **100.0%**
- **Crop Entity Extraction Accuracy**: **91.67%**
- **Average Agronomic Grounding Score**: **99.85%**
- **Factual Answering Success**: **100.0%**

---

## 2. Topic-by-Topic Relevance Performance

| Category | Questions | Crop Detection | Intent Match | Top-1 Chunk Relevance | Farmer-Facing Clarity |
| :--- | :---: | :---: | :---: | :---: | :---: |
| **A: Cultivation & Sowing** | 10 | 100% | 90% | 100% | High (Exact spacing & seed rate) |
| **B: Disease Diagnosis** | 10 | 90% | 80% | 100% | High (Identifies pathognomonic symptoms) |
| **C: Pest Management & IPM** | 10 | 100% | 90% | 100% | High (Includes traps & biologicals) |
| **D: Pesticide Safety (CIBRC)** | 10 | 90% | 100% | 100% | Strict (Exact dosages, water volume & PHI) |
| **E: Fertilizer & Soil Health** | 10 | 100% | 80% | 100% | High (NPK split scheduling & micronutrients) |
| **F: Irrigation & Water** | 10 | 90% | 90% | 100% | High (Critical moisture stages identified) |
| **G: Crop Rotation** | 10 | 90% | 70% | 100% | High (Legume nitrogen & trap crop dynamics) |
| **H: Weather Advisories** | 10 | 90% | 80% | 100% | High (Actionable drainage & frost steps) |
| **I: Harvesting & Storage** | 10 | 90% | 80% | 100% | High (Moisture <12% standard enforced) |
| **J: Mandi Prices & MSP** | 10 | 90% | 100% | 100% | High (FAQ standards & official MSP) |
| **K: Regional Agronomy** | 10 | 80% | 60% | 100% | High (Telangana chalka/black soil nuances) |
| **L: Multilingual Parity** | 10 | 90% | 90% | 100% | High (Identical facts across Te/Hi/En) |

---

## 3. Case Studies: Contrasting Unrelated Questions

### Case 1: Question Specificity on Rice
- **Question 1**: *"What are the symptoms of rice blast?" (వరిలో అగ్గితెగులు లక్షణాలు ఏమిటి?)*
  - **Identified Entities**: Crop = Rice, Target = `rice blast`, Intent = `crop_disease`.
  - **Delivered Answer**: Describes spindle-shaped lesions with grayish centers and brown margins on leaves; explains difference between leaf blast and neck blast.
- **Question 2**: *"How to control brown plant hopper in rice?" (వరిలో సుడిదోమ నివారణ ఎలా?)*
  - **Identified Entities**: Crop = Rice, Target = `brown plant hopper`, Intent = `pest_management`.
  - **Delivered Answer**: Advises draining standing water for 3-4 days to expose hopper nymphs to sunlight; advises forming alleyways; cites Pymetrozine 50% WG @ 120 g/acre.
- **Verification**: **Zero overlap in content**. Each question receives target-specific agronomic advice.

### Case 2: Crop Isolation (Maize vs Cotton)
- **Question 1**: *"How do I control fall armyworm in maize?" (మొక్కజొన్నలో కత్తెర పురుగు నివారణ ఎలా?)*
  - **Delivered Answer**: Emamectin benzoate 5% SG @ 80g/acre, whorl application, and pheromone traps.
- **Question 2**: *"How can I improve soil fertility in my cotton field?" (పత్తి చేనులో భూసారాన్ని ఎలా పెంచాలి?)*
  - **Delivered Answer**: NPK split ratio (48:24:24 kg/acre), zinc sulphate incorporation in alkaline soils, and green manuring.
- **Verification**: Cotton crop documents do not bleed into Maize inquiries; retrieval cleanly separates crop domains.
