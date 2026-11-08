# BOL KISAN – Comprehensive Word & Character Error Rate Evaluation

## 1. Methodology
- **Test Corpus Size**: 45 curated agricultural spoken utterances.
- **Languages**: Telugu (140 words), Hindi (190 words), English (186 words).
- **Acoustic Conditions**: Studio Quiet (40 dB), Field Tractor Idle (78 dB), Open Field Gusty Wind (72 dB), APMC Mandi Babble (84 dB).

---

## 2. Quantitative Error Breakdown

### 2.1 By Operating Domain
- **Clean Studio Speech**:
  - Ref Words: 162
  - Errors: 1 (S=1, D=0, I=0)
  - **WER: 0.62%**
- **Noisy Rural Field Speech**:
  - Ref Words: 171
  - Errors: 38 (S=7, D=30, I=1)
  - **WER: 22.22%**
- **Technical Agricultural Terminology**:
  - Ref Words: 183
  - Errors: 70 (S=31, D=38, I=1)
  - **WER: 38.25%**

### 2.2 By Spoken Language
- **Telugu**: WER = **25.00%** | CER = **14.46%**
- **Hindi**: WER = **12.63%** | CER = **12.17%**
- **English**: WER = **26.88%** | CER = **26.61%**

---

## 3. Discrepancy & Hallucination Guardrails
- **Pesticide Product Uncertainties**: 3 instances where commercial brand names diverged from chemical nomenclature.
- **Numeric Dosage Uncertainties**: 1 instances where units were truncated.
- **RAG Recovery Rate**: **100%**. Due to bilingual keyword synonym tables, every transcribed query correctly resolved to the target agricultural leaflet.
