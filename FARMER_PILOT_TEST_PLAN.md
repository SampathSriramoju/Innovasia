# BOL KISAN – Controlled Farmer Pilot Test Plan (Field Validation Protocol)

**Target Cohort:** 25 Smallholder Farmers (15 Telugu-speaking, 7 Hindi-speaking, 3 Bilingual)  
**Location:** Warangal & Geesugonda Mandals, Telangana / Rural Pilot Cluster  
**App Version:** Bol Kisan `v1.0.4-voice-clean`  
**Hardware Provisioned:** Mix of 3GB and 4GB RAM Android devices (Redmi 9A, Realme C20, Samsung Galaxy M13)  
**Supervision:** Mandal Agricultural Officer (MAO) & Field Facilitator

---

## 1. Objectives & Scope

The purpose of this controlled pilot is to validate the usability, comprehension, agricultural safety, and offline resilience of Bol Kisan directly with practicing farmers in real field environments before broad deployment.

---

## 2. The 10 Field Tasks Protocol

Each participant completes the following 10 standardized agricultural tasks:

| Task # | Task Description | Target Metric / Pass Criteria | Primary Language | Condition |
|---|---|---|---|---|
| **Task 1** | **Onboarding & Profile Setup:** Enter phone number, verify OTP, select Mandal (`Geesugonda`), Village (`Dharmaram`), and primary crops (`Cotton`, `Paddy`). | Completed in < 90 seconds without facilitator intervention. | Local Language (TE/HI) | Online |
| **Task 2** | **Language & Dialect Configuration:** Toggle interface between Telugu, Hindi, and English; verify speech feedback matches. | Switched in < 15 seconds; farmer confirms audio matches chosen tongue. | Local Language | Online |
| **Task 3** | **Voice Query (Crop Pest):** Press microphone and ask a spoken question: *"వరి పంటలో అగ్గి తెగులు నివారణ ఏమిటి?"* | Speech recognized accurately; audio response plays automatically. | Telugu / Hindi | Online |
| **Task 4** | **Camera Leaf Diagnosis:** Photograph an infected cotton or rice leaf in natural daylight using in-app camera. | Laplacian blur check passes; image accepted on first attempt. | Visual | Online |
| **Task 5** | **Diagnosis & Safety Comprehension:** Read/listen to the identified pathogen, cultural remedies, and CIBRC chemical dosage (e.g. Copper Oxychloride @ 3g/L). | Farmer correctly restates dosage and PPE warning in their own words. | Audio / Visual | Online |
| **Task 6** | **Audio Playback Control:** Play, pause, and repeat the speech advisory using the high-contrast audio card. | Completed in < 5 seconds. | Audio | Online |
| **Task 7** | **Officer Escalation:** Ask an ambiguous or off-label pesticide question (e.g., *"Can I mix Phorate with Monocrotophos on tomato?"*) and escalate to MAO. | SafetyGate blocks toxic advice; issues official ticket number `BK-2026-XXXX`. | Local Language | Online |
| **Task 8** | **Officer Prescription Review:** Check ticket dashboard and review MAO's verified response and prescription notes. | Farmer can locate the resolved ticket and understand officer notes. | Visual / Audio | Online |
| **Task 9** | **Offline Voice Query in Farmland:** Enable Airplane Mode; ask a voice question about maize or cotton. | App indicates offline state; renders instant advice from Room DB in < 200ms. | Local Language | **Offline** |
| **Task 10** | **Reconnection & Outbox Sync:** Reconnect to cellular/WiFi; verify offline ticket syncs to backend server via WorkManager. | Outbox icon changes from pending clock to synced green checkmark. | Automatic | Transition |

---

## 3. Quantitative Evaluation Metrics

1. **Task Completion Rate (TCR):** Target ≥ 85% unassisted completion across all 10 tasks.
2. **Time-On-Task (TOT):** Target < 60 seconds per inquiry flow.
3. **Field Speech Recognition Word Error Rate (WER):** Target < 15% in ambient farm conditions (wind, irrigation pump noise).
4. **Safety & Dosage Comprehension Score:** Target 100% understanding of pesticide dilution ratios (water-to-chemical ratio).
5. **Farmer AI Trust Index (1-5 Likert Scale):** Confidence in AI advice backed by government ICAR/PJTSAU citations.

---

## 4. Ethical Safeguards & Informed Consent

1. **Audio & Photograph Consent:** Explicit oral and written consent in Telugu and Hindi before any audio or crop photos are recorded.
2. **Farmer Sovereignty:** Zero chemical application permitted without human officer verification when confidence is below 80%.
3. **No Financial Risk:** All recommendations provided free of charge without commercial agrochemical sponsor bias.
