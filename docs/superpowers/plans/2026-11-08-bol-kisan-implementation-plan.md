# Bol Kisan Implementation Plan

- **Goal:** Build the complete, production-grade foundation and working vertical slice of Bol Kisan (multilingual voice-first Android app, FastAPI AI backend with deterministic safety gate, PostgreSQL data layer, and React officer dashboard).
- **Architecture:** Monorepo architecture separating mobile (`apps/farmer-android`), officer web (`apps/officer-dashboard`), backend services (`backend/`), and knowledge base collections (`knowledge-base/`).
- **Tech Stack:**
  - Android: Kotlin, Jetpack Compose, Room SQLite, CameraX, WorkManager, Retrofit, Material 3
  - Backend: Python 3.14, FastAPI, Pydantic v2, SQLAlchemy, Alembic, Uvicorn, Pytest
  - AI & RAG: Google Gemini 3.8 Flash, LlamaIndex, pgvector, Bhashini / IndicConformer speech pipeline
  - Safety: CIBRC deterministic 8-parameter pesticide verification engine
  - Officer Dashboard: React 18, TypeScript, Vite, Tailwind CSS
  - Infrastructure: Docker, Docker Compose, Git

---

## Task Decomposition & Execution Stages

### Stage 1: Project Foundation & Monorepo Scaffolding
- **Task 1.1**: Initialize monorepo directory tree and base configurations (`.env.example`, `.gitignore`, `docker-compose.yml`, `README.md`).
- **Task 1.2**: Scaffold Python backend with FastAPI, configuration management (`core/config.py`), logging, security utilities, and dependencies (`backend/requirements.txt`).
- **Task 1.3**: Implement SQLAlchemy models for Users, FarmerProfiles, ExtensionOfficers, Crops, Diseases, Pests, PesticideProducts, PesticideLabels, ApprovedPesticideUses, Tickets, and AuditEvents (`backend/app/database/`).
- **Task 1.4**: Configure Alembic migrations and database session management with SQLite test harness and PostgreSQL production support.
- **Task 1.5**: Implement Authentication Architecture (Phone OTP simulation/verification, JWT generation, Role-Based Access Control middleware).
- **Task 1.6**: Implement the Deterministic Pesticide Safety Gate (`backend/app/safety/`) with CIBRC 8-parameter verification and automated ticket escalation trigger.
- **Task 1.7**: Write comprehensive unit tests for Authentication, Safety Gate, and Database models using Pytest. Run tests and verify 100% pass rate.
- **Task 1.8**: Scaffold Android project (`apps/farmer-android/`) with Gradle configuration, Room database schemas, local fallback rules, Material 3 theme (`#15803D`, 48dp targets), and Compose navigation.
- **Task 1.9**: Scaffold Officer Dashboard (`apps/officer-dashboard/`) with React, TypeScript, Tailwind CSS, API service client, and basic routing.

### Stage 2: First Functional Vertical Application Slice
- **Task 2.1**: Implement backend API endpoints for:
  - `POST /api/v1/auth/request-otp` & `POST /api/v1/auth/verify-otp` (Farmer & Officer login)
  - `POST /api/v1/voice/query` (Voice-to-text input, safety validation, RAG retrieval, Gemini synthesis)
  - `GET /api/v1/crops` (Crop list with Telugu/Hindi/English vernacular names)
  - `GET /api/v1/tickets` & `POST /api/v1/tickets` (Escalated ticket management)
  - `POST /api/v1/tickets/{id}/resolve` (Officer prescription dispatch)
- **Task 2.2**: Implement Farmer Android Application Screens:
  - Phone OTP Login Screen with automatic SMS format validation.
  - Language Selection Screen (Telugu, Hindi, English) with native script rendering.
  - Farmer Home Screen with large high-sunlight accessible cards (Weather, Mandi Prices, Disease Camera, My Crops).
  - Voice Assistant Screen with tactile 80dp Push-to-Talk button, audio waveform visualizer, and response cards with citation badges.
- **Task 2.3**: Implement Officer Web Dashboard Pages:
  - Officer Login Screen with credential and OTP verification.
  - Escalated Ticket Queue with urgency color coding and filter by Mandal/District.
  - Ticket Detail View showing Farmer info, recognized speech transcript, audio playback, crop photographs, escalation reason, and Prescription Builder.
- **Task 2.4**: Connect Farmer Android App, Backend, and Officer Dashboard end-to-end.
- **Task 2.5**: Run automated test suite, verify builds, document test coverage in `docs/testing-report.md`, and update `docs/implementation-status.md`.
