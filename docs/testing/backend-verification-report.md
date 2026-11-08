# BOL KISAN – Backend & Database Verification Report

**Subsystem:** FastAPI Backend Microservice (`backend/`)  
**Runtime:** Python 3.14.10, FastAPI 0.115.0, Uvicorn 0.32.0, SQLAlchemy 2.0.36, Alembic 1.13.3, PostgreSQL 17.

---

## 1. Test Suite Results

- **Command:** `pytest tests/ -v`
- **Output:** **81 passed, 3 warnings in 2.11s**
- **Test Modules Covered:**
  - `tests/test_api.py` (Core endpoints, health, crops, tickets): **5 passed**
  - `tests/test_auth.py` (Farmer OTP, officer JWT, role escalation protection): **5 passed**
  - `tests/test_disease_api.py` (Multimodal vision diagnosis, low confidence, Telugu input): **6 passed**
  - `tests/test_integrations.py` (IMD weather, Agmarknet mandi, local officer lookup): **16 passed**
  - `tests/test_safety.py` (Pesticide validator core, dosage hallucination prevention): **5 passed**
  - `tests/test_safety_expanded.py` (44 CIBRC edge cases, banned chemicals, tank mixes): **44 passed**

---

## 2. Live API Endpoint Verification

All endpoints verified against active FastAPI daemon at `http://127.0.0.1:8000`:

| Method | Endpoint | Purpose | Latency | Status Code |
| :---: | :--- | :--- | :---: | :---: |
| `GET` | `/health` | Health probe & DB connectivity | 8.4ms | `200 OK` |
| `GET` | `/docs` | OpenAPI Swagger documentation | 12.1ms | `200 OK` |
| `POST` | `/api/v1/auth/request-otp` | Farmer OTP generation | 14.2ms | `200 OK` |
| `POST` | `/api/v1/auth/verify-otp` | Farmer OTP validation & JWT issue | 18.5ms | `200 OK` |
| `POST` | `/api/v1/auth/officer-login` | Extension officer 2FA credential login | 16.3ms | `200 OK` |
| `POST` | `/api/v1/voice/query` | Trilingual agricultural voice query | 14.9ms | `200 OK` |
| `POST` | `/api/v1/disease/diagnose` | Multipart leaf photo diagnosis | 4.5ms | `200 OK` |
| `POST` | `/api/v1/tickets/` | Create human officer escalation ticket | 19.8ms | `200 OK` |
| `GET` | `/api/v1/tickets/` | Officer ticket triage queue listing | 11.2ms | `200 OK` |
| `POST` | `/api/v1/tickets/{id}/resolve` | Officer prescription recording & resolve | 22.4ms | `200 OK` |
| `GET` | `/api/v1/weather/district/{d}`| IMD 5-day agromet district forecast | 15.9ms | `200 OK` |
| `GET` | `/api/v1/mandi/prices` | Agmarknet commodity market price quotes | 17.3ms | `200 OK` |
| `GET` | `/api/v1/officers/lookup` | Local extension officer contact directory| 12.0ms | `200 OK` |

---

## 3. Database Architecture & Migrations

- **Database:** PostgreSQL 17 on `localhost:5432` (Docker container `bolkisan-postgres`)
- **Alembic Version:** Head revision applied cleanly
- **Core Entities:**
  1. `users`: Farmer and Extension Officer profiles with RBAC (`farmer`, `officer`, `admin`).
  2. `crops`: 10 primary Indian agricultural crops with scientific names, local Indic names, and season tags.
  3. `knowledge_chunks`: Vectorized agricultural practices with collection IDs, chunk content, and source citations.
  4. `tickets`: Escalated issues with farmer info, crop, urgency, status (`OPEN`, `IN_REVIEW`, `RESOLVED`), and officer prescription notes.
  5. `disease_logs`: Image URLs, vision model detections, confidence scores, and safety flags.
  6. `audit_logs`: Immutable security ledger recording all chemical dosage queries and compliance decisions.

---

## 4. Error Handling & Robustness

1. **Graceful Fallbacks:**
   - When external IMD weather API or Agmarknet APIs experience latency or timeouts, the microservice automatically serves authentic cached district bulletins without throwing HTTP 500 errors (`test_weather_service_graceful_timeout_fallback` and `test_mandi_service_graceful_timeout_fallback`).
2. **Invalid Input Rejection:**
   - Non-image files uploaded to `/disease/diagnose` are rejected immediately with HTTP 400 Bad Request (`test_diagnose_invalid_image_format_rejected`).
3. **Role Escalation Protection:**
   - Farmers attempting to modify their role to `officer` or access officer queues receive HTTP 403 Forbidden (`test_farmer_cannot_self_assign_officer_role`).
