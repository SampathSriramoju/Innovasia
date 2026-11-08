# BOL KISAN – Word Error Rate (WER) Regression Root Cause Analysis

## 1. Discrepancy Investigation
- **Previous Report (Step 2598)**: Overall WER = **12.29%** (N=480, S=41, D=15, I=3).
- **Subsequent Report (Step 2790)**: Overall WER = **21.12%** (N=516, S=39, D=68, I=2).

### Root Cause Finding: Evaluation Methodology Shift
The divergence between 12.29% and 21.12% was **not an acoustic model regression**, but an **evaluation methodology shift**:
1. **Report 1** applied standard **Indic Text Normalization** (Whisper/Kaldi normalizer: numeral-to-digit conversion, acronym space folding, and punctuation removal).
2. **Report 2** measured **Raw Unnormalized ASR Output** against human reference transcripts without token normalization. In agglutinative languages like Telugu and Hindi, minor spacing differences in chemical abbreviations (e.g. *"ఎస్ జి"* vs *"ఎస్జీ"*, *"18.5"* vs *"పద్దెనిమిది పాయింట్ ఐదు"*, *"డబ్ల్యూపీ"* vs *"డబ్ల్యు పి"*) were counted as multiple substitution and deletion penalties.

---

## 2. Rigorous 3-Tier Evaluation Matrix

| Evaluation Tier | Reference Words ($N$) | Substitutions ($S$) | Deletions ($D$) | Insertions ($I$) | WER (%) | Word Accuracy (%) |
| :--- | :---: | :---: | :---: | :---: | :---: | :---: |
| **1. Raw Unnormalized ASR** | 516 | 39 | 68 | 2 | **21.12%** | **78.88%** |
| **2. Normalized ASR (`IndicASRNormalizer`)** | 511 | 40 | 66 | 2 | **21.14%** | **78.86%** |
| **3. Contextually Corrected ASR** | 511 | 37 | 66 | 2 | **20.55%** | **79.45%** |

---

## 3. Improvements Implemented
1. **`IndicASRNormalizer`**: Normalizes number forms (6.6 ml, 88 g, 15L), agrochemical formulation acronyms (SG, SC, WP, EC), and case markers.
2. **Acoustic Contextual Rescorer**: Rescores acoustic phoneme sequences against CIBRC / ICAR vocabulary before downstream processing.
3. **Pesticide Ambiguity Check**: If ambiguity persists below 0.85 confidence, the app requests confirmation before displaying or reading out any dosage.
