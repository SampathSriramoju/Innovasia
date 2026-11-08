# MULTILINGUAL LOCALIZATION AUDIT & CODEBASE INSPECTION

**Date:** 2026-11-09  
**Project:** Bol Kisan — Offline/Online Agricultural AI Voice Assistant  
**Author:** AI & Mobile Engineering Team  
**Scope:** Complete Android Application, Backend APIs, Gemini AI Service, Room SQLite & RAG Knowledge Base  
**Status:** Audit Completed  

---

## 1. Executive Summary

A comprehensive page-by-page, component-by-component, and API-by-API audit was conducted across the Bol Kisan repository to identify the root causes of language inconsistency where Telugu content was displayed even when Hindi or English was selected by the farmer.

The audit revealed defects across five critical architectural layers:
1. **Backend Disease Endpoint Hardcoding:** `backend/app/api/v1/endpoints/disease.py` did not accept the `language` form field from Android, defaulted to `"te"`, and hardcoded diagnostic treatment templates in Telugu.
2. **Backend Crop Vision Engine Hardcoding:** `backend/app/ai/crop_vision_engine.py` hardcoded Telugu explanation strings in the vision diagnostic return object.
3. **Gemini Service Grounded Fallback Language Mismatch:** `backend/app/ai/gemini_service.py`'s `_generate_grounded_fallback()` did not check whether the retrieved knowledge chunk matched the user's requested language, occasionally returning Telugu chunks to Hindi or English users.
4. **Offline Knowledge Retrieval Language Bleed:** In `BolKisanRepository.kt`, when offline cards were queried using non-canonical crop names (e.g. localized Telugu or Hindi names), lookups failed and fell back without language validation, wrapping non-matching cards in localized headers.
5. **UI Component Inconsistencies:** In `OfflineLibraryScreen.kt`, `item.language == currentLang || item.language == "en"` caused English cards to bleed into Hindi view; in `HomeScreen.kt`, crop selector dialog displayed `"Cotton (Cotton)"` in English; and `diseaseCropOptions` lacked comprehensive crop options for Hindi and English.

---

## 2. Page-by-Page Android UI Localization Audit

### 2.1 Language Selection Screen (`LanguageSelectScreen.kt`)
- **Status:** Mostly Localized.
- **Issues Identified:**
  - Card for Telugu uses native string `"తెలుగు"`, Hindi uses `"हिन्दी"`, English uses `"English"`. This is intentional and desirable for language pickers.
  - Subtitle and descriptions correctly use `AppStrings`.

### 2.2 Login Screen (`LoginScreen.kt`)
- **Status:** Fully Localized.
- **Issues Identified:**
  - Form labels, buttons, loading states, and error alerts correctly bound to `appStrings(currentLang)`.

### 2.3 Home Dashboard Screen (`HomeScreen.kt`)
- **Status:** Partial Language Bleeds.
- **Issues Identified:**
  - **Demo Questions:** `sampleQuestions` bound to `homeSampleQuestions(selectedCrop, currentLang)`, but when `selectedCrop` had localized strings, fallback occurred.
  - **Crop Picker Dialog:** Showed `"$label ($cropKey)"`, resulting in awkward `"Cotton (Cotton)"` display in English.
  - **Language Toggle Pill:** Correctly displayed `"తెలుగు"`, `"हिन्दी"`, `"English"`.
  - **Chemical Confirmation Dialog:** Identified chemicals mapped English/Telugu trade names, but confirmation prompt needed complete Hindi verification.
  - **Mandi & Weather Dialogs:** Fully bound to `mandiRows` and `appStrings`, but required verified Hindi currency and measurement units (`क्विंटल`).

### 2.4 Voice Assistant Screen (`VoiceAssistantScreen.kt`)
- **Status:** Mostly Localized.
- **Issues Identified:**
  - Subtitle crop display used `localizedCropName(repository.selectedCrop, currentLang)`.
  - Quick voice prompts used `voiceQuickPrompts(currentLang)`.
  - Regex for stripping citation headers only handled Telugu and English; needed robust Hindi regex (`(?:ఆధారం|మూలం|स्रोत|Source|Citation)`).

### 2.5 Crop Disease Camera Screen (`DiseaseCameraScreen.kt`)
- **Status:** Critical Localization Failure.
- **Issues Identified:**
  - The Android client sent `language` via multipart form data (`langBody`), but the FastAPI backend endpoint `POST /api/v1/disease/diagnose` **did not declare or read the `language` form parameter**.
  - As a result, the backend always evaluated the diagnosis under `user_lang = "te"`, returning hardcoded Telugu strings (`• సహజ / సస్యరక్షణ చర్యలు:`, `CIBRC సిఫార్సు చేసిన రసాయన మందు:`, `రోజులు`).
  - The disease name for quality rejection was hardcoded as `"Quality Rejected / చిత్రం సరిగ్గా లేదు"`.
  - Escalation diagnosis was hardcoded as `"అనుమానిత తెగులు: ... "`.

### 2.6 Offline Knowledge Library Screen (`OfflineLibraryScreen.kt`)
- **Status:** Filter Bleed Issue.
- **Issues Identified:**
  - Filter logic used: `val matchesLang = item.language == currentLang || item.language == "en"`.
  - When Hindi was selected, both Hindi and English cards were displayed simultaneously without distinction, confusing Hindi-speaking farmers.
  - Model benchmarking summary labels were translated, but hardware metrics lacked fallback explanations in Hindi and Telugu.

### 2.7 Officer Tickets Screen (`OfficerTicketsScreen.kt`)
- **Status:** Fully Localized.
- **Issues Identified:**
  - Ticket statuses mapped via `strings.ticketResolved` and `strings.ticketReviewing`.
  - Backend ticket response in `tickets.py` line 307 prepended hardcoded `[అధికారి సిఫార్సు / Officer Prescription]` regardless of user language.

---

## 3. Backend & AI Response Localization Audit

### 3.1 Voice Query Endpoint (`backend/app/api/v1/endpoints/voice.py`)
- **Payload Language:** Accepts `language: Optional[str] = "te"`.
- **Safety Gate Escalation Response:** Localized for `te`, `hi`, and `en`.
- **Post-Generation Safety Override:** If safety gate detected hallucination, line 131 handled `te` and `else`, but omitted `hi`, falling back to English.

### 3.2 Gemini Service (`backend/app/ai/gemini_service.py`)
- **Prompt Language Instruction:** `user_prompt` includes `REQUESTED LANGUAGE CODE: {language}`. Gemini properly synthesizes the answer in the requested language when online.
- **Deterministic Grounded Fallback (`_generate_grounded_fallback`):**
  - Case 1 (CIBRC Chemical): Correctly localized in Telugu, Hindi, and English.
  - Case 2 (RAG Chunks): Candidate selection did not enforce language match. If a Telugu chunk was ranked top, and the query was in Hindi or English, the fallback returned Telugu text wrapped in Hindi or English headers.

### 3.3 Query Understanding (`backend/app/ai/query_understanding.py`)
- **Language Detection:** Detects Telugu script (`\u0C00-\u0C7F`), Devanagari/Hindi (`\u0900-\u097F`), and Latin/English.
- **Crop Extraction:** Maps multilingual crop synonyms to canonical English crop keys (`Cotton`, `Rice / Paddy`, `Chilli`, `Maize`, etc.).

---

## 4. Knowledge Base Language Coverage Audit

| Collection ID & Topic | Telugu Records | Hindi Records | English Records | Triplet Verified? |
| :--- | :---: | :---: | :---: | :---: |
| **Android Room Knowledge (`KnowledgePrepopulator.kt`)** | 12 | 12 | 12 | **Yes (100% Triplet Linked)** |
| **KB01: Crop Master Specifications** | 12 | 12 | 12 | **Yes** |
| **KB04: Crop Diseases** | 42 | 42 | 42 | **Yes** |
| **KB05: Insect Pests & ETL** | 38 | 38 | 38 | **Yes** |
| **KB07/KB08: CIBRC Pesticides & Labels** | 56 | 56 | 56 | **Yes** |
| **KB09: Fertilizers & Nutrients** | 18 | 18 | 18 | **Yes** |
| **KB10: Soil Health & Amendments** | 14 | 14 | 14 | **Yes** |
| **KB11: Irrigation Guidance** | 12 | 12 | 12 | **Yes** |
| **KB13: Weed Management** | 16 | 16 | 16 | **Yes** |
| **KB18: APMC Mandi Market Prices** | 24 | 24 | 24 | **Yes** |
| **KB21: Agricultural Extension FAQs** | 120 | 120 | 120 | **Yes** |

---

## 5. Summary of Required Corrections

1. **Backend `disease.py`:** Add `language: Optional[str] = Form(None)`, remove hardcoded Telugu strings, provide trilingual diagnosis explanations.
2. **Backend `crop_vision_engine.py`:** Localize `farmer_explanation` based on requested `language` (`te`, `hi`, `en`).
3. **Backend `gemini_service.py`:** Ensure `_generate_grounded_fallback()` checks candidate document language, extracts matching language blocks, and never returns unverified cross-language content.
4. **Android `BolKisanRepository.kt`:** Standardize on canonical crop names for all internal database and API requests; ensure offline fallback strictly enforces language matching or explicit cross-lingual attribution.
5. **Android `OfflineLibraryScreen.kt`:** Filter strictly by `item.language == currentLang`.
6. **Android `HomeScreen.kt`:** Clean up crop picker display formatting and chemical confirmation dialogs.
