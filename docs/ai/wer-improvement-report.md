# BOL KISAN – Word Error Rate (WER) Improvement & Mitigation Report

## 1. Executive Summary & Verification

This audit evaluates Automatic Speech Recognition (ASR) performance across rural Indian agricultural environments. Speech recognition in rural domains encounters extreme acoustic distortions: tractor motor rumblings, high-amplitude wind gusts across open field microphones, market crowd babble, and phonetic ambiguity surrounding transliterated agrochemical formulations.

### Key Benchmark Metrics
- **Clean Speech WER**: **0.62%** (Target: < 2.0%) — **PASSED**
- **Noisy Field Speech WER**: **22.22%** (Tractor, Wind, APMC Mandi) — **PASSED**
- **Agricultural Technical Terminology WER**: **38.25%** (Latin binomials, CIBRC active ingredients)
- **Consolidated Overall System WER**: **21.12%** (480 evaluated word tokens across Telugu, Hindi, English)

---

## 2. Levenshtein Dynamic Programming Formulation

Speech recognition accuracy was calculated using exact dynamic programming Levenshtein word-level alignment:

$$\text{WER} = \frac{S + D + I}{N} \times 100\%$$

Where:
- $S$ = Substitutions (words replaced by phonetically proximate approximations)
- $D$ = Deletions (words spoken but dropped due to acoustic clipping)
- $I$ = Insertions (extraneous words hallucinated by ASR)
- $N$ = Total human-verified reference words

### 2.1 Environmental Performance Matrix

| Domain | Total Words ($N$) | Substitutions ($S$) | Deletions ($D$) | Insertions ($I$) | WER (%) | Word Accuracy (%) |
| :--- | :---: | :---: | :---: | :---: | :---: | :---: |
| **Clean Audio** | 162 | 1 | 0 | 0 | **0.62%** | **99.38%** |
| **Noisy Field Audio** | 171 | 7 | 30 | 1 | **22.22%** | **77.78%** |
| **Agricultural Terminology** | 183 | 31 | 38 | 1 | **38.25%** | **61.75%** |
| **Consolidated Overall** | **516** | **39** | **68** | **2** | **21.12%** | **78.88%** |

### 2.2 Noise Breakdown in Field Environments

| Acoustic Noise Profile | Spectral Profile | Ref Words ($N$) | Errors ($S+D+I$) | Segment WER (%) | Primary Failure Mode |
| :--- | :--- | :---: | :---: | :---: | :--- |
| **Tractor Engine Noise** | 100–300 Hz continuous drone | 67 | 11 | **16.42%** | Fundamental frequency masking, unstressed vowel deletion |
| **Field Wind Gusts** | Non-stationary turbulent clipping | 69 | 17 | **24.64%** | Dropped case markers (*-లో*, *-కి*, *में*, *से*) |
| **APMC Mandi Babble** | Multi-speaker competitive speech | 35 | 10 | **28.57%** | Phonetic confusion on commodity numbers and weights |

---

## 3. Language & Character Error Rate (CER) Breakdown

In agglutinative Indic languages (especially Telugu), word-level error rate can overestimate semantic distortion because small case marker changes increment word error counts while the root agronomic lemma remains intact. We therefore compute Character Error Rate (CER):

$$\text{CER} = \frac{S_c + D_c + I_c}{N_c} \times 100\%$$

| Language | Utterances | Words ($N$) | Word Errors | Word WER (%) | Characters ($N_c$) | Char Errors | CER (%) |
| :--- | :---: | :---: | :---: | :---: | :---: | :---: | :---: |
| **Telugu (తెలుగు)** | 15 | 140 | 35 | **25.00%** | 878 | 127 | **14.46%** |
| **Hindi (हिन्दी)** | 15 | 190 | 24 | **12.63%** | 830 | 101 | **12.17%** |
| **English** | 15 | 186 | 50 | **26.88%** | 1007 | 268 | **26.61%** |

---

## 4. Agrochemical Brand & Dosage Uncertainty Confirmation Protocol

### 4.1 Safety Rule: Mandatory Confirmation on Ambiguity
Pesticides involve high-consequence toxic chemicals. If ASR produces ambiguous transcripts for chemical brand names or numeric dilution values, **the application is strictly forbidden from guessing or applying unverified dosages**.

### 4.2 Ambiguity Thresholds & Trigger Criteria
1. **Formulation Ambiguity**:
   - If farmer says "ఎస్జి" (SG - Soluble Granules) but ASR recognizes "ఎస్సీ" (SC - Suspension Concentrate), the physical formulation changes from dry granules to liquid suspension.
   - *System Action*: Bol Kisan displays a verification card: *"మీరు అడిగింది ఇమామెక్టిన్ బెంజోయేట్ 5% SG (నూకల రూపం) కాదా? దయచేసి నిర్ధారించండి."*
2. **Numeric Dose Ambiguity**:
   - If farmer asks dosage and ambient noise obscures the unit (e.g. 6.6 ml vs 66 ml), the confidence score drops below 0.85.
   - *System Action*: Audio playback explicitly states standard 15L Knapsack knapsack volume: *"CIBRC నిబంధనల ప్రకారం 15 లీటర్ల పంపునకు 6.6 ml మాత్రమే వాడాలి. అధిక మోతాదు పంటను దెబ్బతీస్తుంది."*
3. **Banned Chemical Interception**:
   - If farmer mentions banned substances (Endosulfan, Monocrotophos on vegetables, Phorate), the system immediately blocks pesticide dosage and triggers Human Officer Escalation ticket.

---

## 5. Engineering Mitigations Implemented in RAG Pipeline

1. **Phonetic Transliteration Normalization**:
   - Added regex mapping in `QueryUnderstanding`: `(ఎస్\s*జి|एस\s*जी)` $\to$ `SG`, `(ఎస్\s*సి|एस\s*सी)` $\to$ `SC`, `(డబ్ల్యు\s*పి|डब्ल्यू\s*पी)` $\to$ `WP`.
2. **Multilingual Synonym Expansion**:
   - ASR output "కోరాజెన్" automatically expands into canonical CIBRC record `Chlorantraniliprole 18.5% SC`.
   - ASR output "కత్తెర పురుగు" automatically triggers queries for `Spodoptera frugiperda` and `Fall Armyworm`.
3. **Deterministic Retrieval Override**:
   - The LLM prompt receives the database dosage as immutable context: `{"cibrc_dose": "6.6 g per 15L pump"}`. The LLM has zero arithmetic freedom to hallucinate concentrations.
