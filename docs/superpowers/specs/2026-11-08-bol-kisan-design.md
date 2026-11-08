# BOL KISAN: Product Specification & System Design
**Document Version:** 1.0.0  
**Classification:** Architectural (Superpowers Brainstorming Path)  
**Date:** 2026-11-08  
**Status:** Approved for Implementation  

---

## 1. Executive Summary & Product Definition

**Product Name:** BOL KISAN (बोल किसान / బోల్ కిసాన్)  
**Product Category:** Multilingual, Voice-First AI Agricultural Assistance Android Application & Extension Officer Operations Platform.  
**Target Demographics:**
1. **Farmers**: Smallholders, marginal farmers, and agricultural workers in India (Telugu, Hindi, English). Many have limited digital literacy or encounter language barriers when navigating complex text-heavy government portals.
2. **Regional Agricultural Extension Officers (Krishi Vistar Adhikari / Vyavasaya Adhikari)**: Certified public or university agricultural scientists responsible for rural taluks, mandals, and districts.

### Problem Statement
Indian farmers face severe crop losses due to timely unavailability of verified agricultural advice. When facing pest outbreaks, unknown fungal diseases, or anomalous weather events:
- Existing portals are text-dense, bureaucratic, and available only in formal bureaucratic language.
- Chemical product labels (pesticides, fungicides) are printed in tiny English/Hindi print with complex active ingredients (e.g., *Chlorantraniliprole 18.5% SC* vs *Emamectin Benzoate 5% SG*). Incorrect self-dosages poison crops or fail to control pests.
- LLM hallucinations in agriculture have lethal or ruinous financial consequences (e.g., prescribing incorrect toxic dosages).
- Rural network connectivity is volatile (3G/intermittent 4G); apps requiring constant 100Mbps cloud roundtrips crash in the field.

### Core Product Principle
> **NEVER GENERATE UNSUPPORTED AGRICULTURAL ADVICE.**  
> If an agricultural assertion cannot be grounded in an authenticated, version-controlled official agricultural document (ICAR, State Agricultural Universities, Central Insecticides Board & Registration Committee - CIBRC), or if there is any ambiguity in dosage, formulation, crop stage, or diagnosis, the system **MUST NOT GUESS**. It must state what is verified and immediately escalate the case to a verified human extension officer.

---

## 2. Supported Languages & Localization
- **Telugu (తెలుగు)**: Primary regional language for Andhra Pradesh & Telangana.
- **Hindi (हिन्दी)**: Primary northern and central agricultural belt language.
- **English**: Secondary reference language and officer administrative language.

Every user-facing string, prompt template, speech model, and safety alert supports full i18n localization with phonetic script and localized audio synthesis.

---

## 3. System Architecture & High-Level Topology

```
+-----------------------------------------------------------------------------------+
|                              FARMER ANDROID CLIENT                                |
|  - Jetpack Compose UI (Material 3, 48dp+ touch targets, AAA sunlight contrast)    |
|  - Voice-First Interface (Push-to-talk, audio waveform visualizer)                |
|  - Offline SQLite/Room Cache + Local Knowledge Snippets                           |
|  - Offline Model Engine (LiteRT-LM / llama.cpp runtime evaluation)               |
|  - CameraX Leaf & Pest Macro Capture (Edge compression, EXIF scrub)               |
+-----------------------------------------------------------------------------------+
                                   |           ^
                      REST / JSON  |           | SSE / WebSocket / Audio Streams
                      (Gzip, ETag) |           |
                                   v           |
+-----------------------------------------------------------------------------------+
|                                FASTAPI BACKEND GATEWAY                             |
|  - Authentication & RBAC (Phone OTP, JWT, Farmer vs Officer roles)                |
|  - API Versioning (/api/v1)                                                       |
|  - Rate Limiting, Audit Logging & OWASP Guardrails                                |
+-----------------------------------------------------------------------------------+
         |                                                 |
         v                                                 v
+-------------------------------+      +-------------------------------------------+
|    AI & RAG RETRIEVAL ENGINE  |      |        DETERMINISTIC SAFETY GATE          |
| - LlamaIndex Hybrid Pipeline  |      | - CIBRC Approved Pesticide Matrix         |
| - PostgreSQL + pgvector       |      | - Exact Dosage & Formulation Verifier     |
| - Embeddings Abstraction Layer|      | - Symptom vs Diagnosis Disambiguator      |
| - Google Gemini 3.8 Flash     |      | - Pre-Harvest Interval (PHI) Guard        |
|   (Configurable Online LLM)   |      | - Escalation Decider                      |
+-------------------------------+      +-------------------------------------------+
         |                                                 |
         +------------------------+------------------------+
                                  |
                                  v
+-----------------------------------------------------------------------------------+
|                        POSTGRESQL + PGVECTOR DATA LAYER                           |
|  - Normalized Schemas: Users, Crops, Pests, Diseases, Pesticide Labels, Tickets   |
|  - 23 Knowledge Base Collections (KB01 - KB23) with Full Version Lineage          |
|  - Row-Level Security (RLS) & Audit Event Trails                                  |
+-----------------------------------------------------------------------------------+
                                  ^
                                  | Live Escalations & Telemetry
+-----------------------------------------------------------------------------------+
|                       OFFICER WEB DASHBOARD (REACT + TS)                          |
|  - Regional Queue Routing (Mandal / Taluk / District dispatch)                    |
|  - Multi-pane Case Review: Audio Transcript, Leaf Photos, Model Reasoning Trace  |
|  - Verified Prescription Builder, One-click Audio/SMS Farmer Callback             |
+-----------------------------------------------------------------------------------+
```

---

## 4. End-to-End Workflows

### 4.1 Online Workflow
1. **Voice Input**: Farmer taps and holds the central green Voice button (`#15803D`) and speaks in Telugu, Hindi, or English.
2. **Audio Streaming**: Audio is captured in 16kHz mono PCM and streamed or uploaded via multipart to the backend.
3. **ASR (Speech-to-Text)**: Processed via Bhashini / IndicConformer speech recognition with automatic language identification.
4. **Intent & Entity Extraction**: Query is analyzed for intent (disease diagnosis, pesticide dose, weather advisory, mandi price, irrigation, fertilizer).
5. **Hybrid Knowledge Retrieval**: pgvector dense semantic search (cosine similarity) combined with PostgreSQL `tsvector` full-text search against the 23 verified agricultural collections.
6. **Deterministic Safety Verification**:
   - If pesticide query: Checks target crop, target pest, approved active ingredient, CIBRC label registry, dilution ratio, max applications, and pre-harvest interval (PHI).
   - If unapproved or ambiguous: **Hard stop**. Flags for immediate human escalation.
7. **Gemini 3.8 Flash Generation**: Prompts Gemini 3.8 Flash with strictly retrieved knowledge chunks as context, enforcing the rule: *No extrapolation outside retrieved source chunks*.
8. **Response Validation**: Deterministic post-processor checks generated answer against safety invariants.
9. **TTS (Text-to-Speech)**: Response is synthesized into clear, natural regional audio (Bhashini Indic-TTS).
10. **Delivery**: Farmer hears the spoken response and sees a high-contrast card with citation tags (`[CIBRC Label 2024-Rice]`, `[PJTSAU Advisory 2025]`).

### 4.2 Offline Workflow
1. **Device Assessment**: The Android app detects low/no connectivity (`NetworkCapabilities.NET_CAPABILITY_INTERNET`).
2. **Local ASR**: Evaluates offline IndicConformer (or quantized on-device ASR model).
3. **Local Knowledge Engine**:
   - **Tier 1 (Fallback Rules)**: Deterministic search in SQLite Room database containing regional package of practices, approved label tables, and emergency extension contacts.
   - **Tier 2 (Offline LLM Runtime)**: Model-independent inference container evaluating **AgriParam** and **Gemma 4 E2B** via LiteRT-LM / llama.cpp. If available and device has >= 4GB RAM without thermal throttling, generates local grounded response. If device has <= 3GB RAM or fails load, cleanly falls back to Tier 1.
4. **Offline Voice-to-Voice SLA**: Performance target is under 6 seconds from speech end to response start.
5. **Outbox Synchronization**: If farmer requests an escalation or query when offline, it is stored in Room `PendingSyncQueue` and automatically synced via Android WorkManager when internet reconnects.

---

## 5. Non-Negotiable Agricultural Safety & The Pesticide Engine

Pesticide poisoning and chemical misuse cause devastating crop and human health harm.
The system implements a **Dual-Tier Deterministic Safety Gate**:
1. **Zero LLM Discretion on Dosages**: The LLM is never allowed to compute, suggest, or interpolate dosages. Dosages come exclusively from the CIBRC-verified relational database table `approved_pesticide_uses`.
2. **Mandatory 8-Point Match**:
   1. Validated Commercial Product Name.
   2. Standard Active Ingredient & Concentration (e.g. *Chlorpyrifos 50% + Cypermethrin 5% EC*).
   3. Target Crop (e.g. *Cotton*).
   4. Target Pest/Disease (e.g. *Spotted Bollworm*).
   5. Formulation Type (EC, SC, WP, SG).
   6. Approved Dosage per Hectare / Acre and dilution per Liter of water.
   7. Waiting Period / Pre-Harvest Interval (PHI) in days.
   8. Specific toxicity band (Red/Yellow/Blue/Green triangle) and safety equipment warnings.
3. **Escalation Trigger**: If any of the 8 parameters are missing, ambiguous, or if conflicting advisories exist between State Agricultural University (SAU) and Central CIBRC, the system produces a safety warning and logs ticket `#ESC-XXXX` directly to the regional officer.

---

## 6. Agricultural Knowledge Base Structure (23 Collections)

| Collection ID | Name | Contents |
|---|---|---|
| `KB01` | Crop Master Database | Standardized crop taxonomic hierarchy, vernacular names (Telugu/Hindi/English). |
| `KB02` | Crop Cultivation | Package of practices, land prep, seed rates, spacing, sowing windows. |
| `KB03` | Crop Growth Stages | BBCH scales, vegetative, tillering, flowering, maturity stages. |
| `KB04` | Crop Diseases | Fungal, bacterial, viral crop pathologies, visual symptom taxonomy. |
| `KB05` | Pest Information | Insect pests, mites, nematodes, lifecycle stages, economic threshold levels (ETL). |
| `KB06` | Integrated Pest Management (IPM) | Cultural, mechanical, biological, pheromone traps, chemical controls. |
| `KB07` | Pesticide Products | Commercial brand registry, manufacturers, registration status in India. |
| `KB08` | Pesticide Labels & Safety | CIBRC approved leaflets, antidotes, toxicity color triangles, PPE instructions. |
| `KB09` | Fertilizers | Macro/micronutrient schedules, Urea, DAP, MOP, SSP, foliar sprays, bio-fertilizers. |
| `KB10` | Soil Health | Soil types (Black cotton, Red sandy, Alluvial), pH ranges, deficiency symptoms. |
| `KB11` | Irrigation | Water management, drip/sprinkler scheduling, critical irrigation stages. |
| `KB12` | Crop Rotation | Legume rotations, green manuring, soil rejuvenation sequences. |
| `KB13` | Weed Management | Pre-emergence, post-emergence herbicides, manual weeding schedules. |
| `KB14` | Harvesting | Maturity indices, moisture percentages for harvesting, field sanitation. |
| `KB15` | Post-Harvest Management | Drying, storage pest prevention, bagging, cold storage parameters. |
| `KB16` | Weather and Climate | IMD agro-meteorological advisories, monsoon forecasts, frost/heat warnings. |
| `KB17` | Agricultural Advisories | Real-time weekly district advisories from KVKs and SAUs. |
| `KB18` | Mandi & Market Prices | Agmarknet daily arrivals, minimum, maximum, and modal price indices. |
| `KB19` | Regional Recommendations | Agro-climatic zone specific varietal and management advisories. |
| `KB20` | Multilingual Dictionary | 5,000+ localized agricultural vernacular terms mapped to scientific names. |
| `KB21` | Agricultural FAQs | Curated standard farmer inquiries and verified answers. |
| `KB22` | Verified Q&A Repository | Human extension officer resolved tickets converted into validated QA pairs. |
| `KB23` | Regional Officer Directory | KVK, ATMA, and Department of Agriculture officer contacts and jurisdictions. |

---

## 7. Officer Dashboard Architecture
- Built with React, TypeScript, and Tailwind CSS.
- Real-time ticket dispatching based on Mandal / District jurisdiction.
- Three triage tabs:
  1. **Urgent Escalations**: Pesticide ambiguity, suspected quarantine pests, high-value crop emergencies.
  2. **Pending Disease Reviews**: Leaf photographs awaiting human diagnosis verification.
  3. **Resolved Tickets**: History with audit timestamps and farmer feedback.
- Detailed case view showing:
  - Farmer profile (village, acreage, soil type, primary crop).
  - Original voice recording + recognized speech transcript (with confidence score).
  - Attached high-res crop/leaf images.
  - LLM reason for escalation.
  - Officer prescription writer (generates audio notice + SMS/WhatsApp notification to farmer).

---

## 8. Implementation Methodology & Phase Plan
- **Stage 0**: Skill discovery, comprehensive planning, ADRs, engineering & design reviews (Current).
- **Stage 1**: Monorepo structure, FastAPI backend, SQLAlchemy models, Alembic migrations, Android project structure, Officer dashboard foundation, Docker environment, and base tests.
- **Stage 2**: Working vertical slice: Farmer phone login, language selection, farmer home & voice assistant screen, backend speech & query endpoint, officer login, officer ticket review queue.
- **Stage 3 to 8**: Knowledge base ingestion, online Gemini 3.8 Flash RAG, offline model benchmarking (AgriParam vs Gemma 4 E2B), vision leaf-diagnosis pipeline, full regional escalation lifecycle, and comprehensive evaluation.
