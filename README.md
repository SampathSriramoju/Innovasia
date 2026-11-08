# BOL KISAN (बोल किसान / బోల్ కిసాన్)
### Multilingual AI-Powered Agricultural Voice Assistant & Extension Operations Platform

Bol Kisan is a production-grade, voice-first agricultural assistance ecosystem built for Indian farmers across **Telugu**, **Hindi**, and **English**. It couples an accessible mobile client with a cloud AI backend, deterministic safety gate, 23 verified agricultural knowledge collections, and a human extension officer operations dashboard.

---

## Non-Negotiable Core Principle
> **NEVER GENERATE UNSUPPORTED AGRICULTURAL ADVICE.**  
> If an agricultural assertion cannot be grounded in an authenticated, version-controlled official agricultural document (ICAR, State Agricultural Universities, Central Insecticides Board & Registration Committee - CIBRC), or if there is any ambiguity in dosage, formulation, crop stage, or diagnosis, the system **MUST NOT GUESS**. It immediately escalates the case to a verified regional agricultural extension officer.

---

## System Architecture Overview

```
apps/
  farmer-android/       # Native Android app (Kotlin, Jetpack Compose, Room SQLite, CameraX, WorkManager)
  officer-dashboard/    # Extension officer web portal (React 18, TypeScript, Tailwind CSS, Vite)

backend/                # FastAPI async backend, Pydantic v2, SQLAlchemy, Alembic
  app/
    api/v1/             # Auth, Voice, Crops, Tickets, Disease, Weather, Mandi, Officers endpoints
    ai/                 # Gemini 3.8 Flash, Hybrid BM25 + 256-dim Vector RAG retriever
    safety/             # CIBRC 8-parameter deterministic pesticide validator & regex boundary scanner
    integrations/       # IMD Weather, Agmarknet Mandi Prices, Regional Extension Officers
    database/           # Normalized PostgreSQL models and session handlers
  tests/                # Pytest unit and integration test suite (81 tests, 100% pass rate)

knowledge-base/         # 23 Verified Agricultural Collections (KB01 - KB23, 547 Authentic Records)
  crop-information/     # KB01 (Crops Master), KB02 (Cultivation), KB03 (Growth Stages BBCH)
  disease-information/  # KB04 (Diseases), KB05 (Pests & ETLs), KB06 (Integrated Pest Management)
  pesticide-labels/     # KB07 (Pesticide Products), KB08 (CIBRC Labels, Knapsack Dilution & PHI)
  nutrients-soil/       # KB09 (Fertilizers & Nutrients), KB10 (Soil Health & Amendments)
  water-weed-management/# KB11 (Irrigation Regimes), KB12 (Crop Rotation), KB13 (Weed Management)
  harvest-postharvest/  # KB14 (Harvesting Guidelines), KB15 (Post-Harvest Storage)
  weather-advisories/   # KB16 (Agromet Bulletins), KB17 (Contingency Advisories)
  market-regional/      # KB18 (APMC Mandi Modal Prices), KB19 (Regional Recommendations)
  language-faqs/        # KB20 (Multilingual Dictionary), KB21 (KCC FAQs), KB22 (Officer Q&A), KB23 (Directory)
  metadata/             # kb_registry.json with SHA-256 integrity checksums for all collections

docs/                   # Full engineering and design documentation
  architecture.md       # Complete system architecture and sequence diagrams
  database-schema.md    # Normalized PostgreSQL schema and RLS policies
  security-report.md    # OWASP Top 10 API Security & STRIDE Threat Model
  performance-report.md # Latency benchmarks & Edge AI Memory Profiling
  knowledge-base-coverage-report.md # Audit of all 23 collections with SHA-256 checksums
  subagent-execution-report.md      # Swarm coordination and deliverables matrix across 8 roles
  testing-report.md     # Test execution logs (81 passing tests)
  skills-used.md        # Record of installed skills and toolkits consulted
  technical-decisions/  # ADR-001 through ADR-005
```

---

## Key Capabilities & Verified Invariants

1. **Voice-First Accessibility**:
   - Single-tap 80dp Push-to-Talk button with audio waveform feedback.
   - High-sunlight outdoor readability: Forest Clover Green (`#15803D`) with WCAG AAA contrast on soft mint background (`#F0FDF4`).
   - Native phonetic transcription and synthesis in Telugu, Hindi, and English.
2. **Deterministic Pesticide Safety Engine**:
   - Zero LLM discretion on chemical dosages.
   - 8-point parameter verification: Trade name, active ingredient, crop, pest, formulation, dose/ha, 15L knapsack dilution, and Pre-Harvest Interval (PHI).
   - Statutory blacklist of banned agrochemicals (Endosulfan, Monocrotophos on vegetables, Methyl Parathion) with emergency escalation.
   - Pre- and post-generation regex scanner eliminates hallucinated rogue numbers or unauthorized tank mixtures.
3. **Decoupled Online & Offline Intelligence**:
   - **Online**: Google Gemini 3.8 Flash (`gemini-3.8-flash`) via the official Google Gen AI SDK + hybrid BM25 and 256-dim semantic vector retrieval indexing 774 knowledge chunks.
   - **Offline**: Zero-crash Room SQLite local knowledge cache pre-populated with 36 verified ICAR crop practices (< 100ms response time, 0% OOM risk on 3GB RAM devices).
4. **Human Extension Officer Dashboard**:
   - Regional Mandal / District triage console with urgency tagging.
   - Detailed case dossier: Synchronized 48-bar audio waveform player, leaf photo inspection viewer (100%-350% zoom, EXIF tag validation badge).
   - CIBRC guided prescription builder with auto-dilution calculator and audio voice note dispatch.
5. **Real-Time Agrometeorological & Market Integrations**:
   - District IMD 5-day weather forecasts with dynamic agro risk alerts (blast/mildew humidity triggers).
   - Agmarknet APMC mandi modal price feeds comparing current rates against Minimum Support Prices (MSP).
   - Regional extension officer directory lookup mapped to farmer districts and mandals.

---

## Verified Build & Test Artifacts

| Component | Verification Command | Result | Verified Artifact / Output |
|---|---|---|---|
| **Backend API** | `pytest backend/tests/ -v` | **81 / 81 PASSED** (0.91s) | All endpoints, auth, disease diagnosis, integrations, and CIBRC safety rules |
| **Android APK** | `.\gradlew.bat assembleDebug` | **BUILD SUCCESSFUL** (13s) | `apps/farmer-android/app/build/outputs/apk/debug/app-debug.apk` (**20.3 MB**) |
| **Web Dashboard** | `npm run build` | **0 TS Errors** (3.03s) | `apps/officer-dashboard/dist/` (JS: 53.13 kB gzip, CSS: 4.21 kB gzip) |
| **Knowledge Base** | `python knowledge-base/ingest_kb.py` | **23 / 23 VERIFIED** | **547 authentic records** with SHA-256 hashes in `kb_registry.json` |

---

## Local Development Quickstart

### 1. Backend Setup & Run
```powershell
cd backend
python -m venv .venv
.\.venv\Scripts\activate
pip install -r requirements.txt

# Run full test suite (81 tests)
pytest

# Start FastAPI backend server
uvicorn app.main:app --host 127.0.0.1 --port 8000
```
Swagger UI available at `http://127.0.0.1:8000/docs`.

### 2. Officer Dashboard Setup & Run
```powershell
cd apps/officer-dashboard
npm install
npm run dev -- --host 127.0.0.1 --port 5173
```
Dashboard available at `http://127.0.0.1:5173/`. Demo Officer Login Code: `AGRI-TS-042`.

### 3. Android Application Build
```powershell
cd apps/farmer-android
.\gradlew.bat assembleDebug
```
Output APK generated at `app/build/outputs/apk/debug/app-debug.apk`.
