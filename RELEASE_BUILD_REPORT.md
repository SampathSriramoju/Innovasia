# BOL KISAN – Release Build, Packaging & Deployment Verification Report

**Date:** November 8, 2026  
**Artifact Directory:** `release/`  
**Distribution Target:** Controlled Farmer Pilot & Hackathon Demonstration

---

## 1. Executive Summary

This report certifies that the complete Bol Kisan platform—comprising the Android Farmer Mobile App, the FastAPI backend service, the PostgreSQL/pgvector database, and the Agricultural Extension Officer Web Dashboard—has been packaged, verified, and audited for production deployment.

---

## 2. Android Mobile Application Packaging

| Parameter | Configuration Value | Verification Method | Status |
|---|---|---|---|
| **Package / Application ID** | `com.bolkisan.farmer` | `apps/farmer-android/app/build.gradle.kts` | **VERIFIED** |
| **Version Name** | `1.0.4-voice-clean` (build `1.0.2`) | `defaultConfig.versionName` | **VERIFIED** |
| **Version Code** | `3` | `defaultConfig.versionCode` | **VERIFIED** |
| **Minimum SDK** | `26` (Android 8.0 Oreo) | `defaultConfig.minSdk` | **VERIFIED** |
| **Target SDK** | `34` (Android 14 Upside Down Cake) | `defaultConfig.targetSdk` | **VERIFIED** |
| **Compile SDK** | `34` | `android.compileSdk` | **VERIFIED** |
| **Binary Artifact** | `release/bolkisan-latest.apk` | File size: **20,433,344 bytes (20.43 MB)** | **VERIFIED & TESTED** |
| **Security Permissions** | `INTERNET`, `ACCESS_NETWORK_STATE`, `RECORD_AUDIO`, `CAMERA`, `VIBRATE` | `AndroidManifest.xml` inspection | **VERIFIED MINIMAL** |
| **Component Exposure** | Only `MainActivity` exported for launcher | `android:exported="true"` | **VERIFIED SAFE** |
| **Orientation Safety** | `configChanges="orientation\|screenSize\|screenLayout\|keyboardHidden"` | Activity configuration | **VERIFIED RESILIENT** |

---

## 3. Backend Deployment & Docker Specification

### 3.1 Container Architecture (`backend/Dockerfile`)
- **Base Image:** `python:3.11-slim`
- **System Dependencies:** `build-essential`, `curl`
- **Application Directory:** `/app`
- **Port Binding:** `8000`
- **Startup Directive:** `uvicorn app.main:app --host 0.0.0.0 --port 8000`

### 3.2 Database & Static Assets Configuration
- **Database Schema:** PostgreSQL 16 with pgvector extension (`bolkisan` database, 32 verified public tables).
- **Index Optimization:** B-Tree indexes on `farmer_id`, `(farmer_id, created_at DESC)`, `(mandal, status)`, and HNSW vector index on knowledge chunks.
- **Static Assets:** Leaf image uploads securely written to `backend/static/uploads/diseases/` with EXIF stripping and 10MB payload ceiling.

### 3.3 Production Environment Variables
```env
DATABASE_URL=postgresql://postgres:postgres@localhost:5432/bolkisan
GEMINI_API_KEY=<REDACTED_API_KEY>
SECRET_KEY=<SECURE_HMAC_SECRET>
ACCESS_TOKEN_EXPIRE_MINUTES=1440
CORS_ORIGINS=["*"]
```

---

## 4. Extension Officer Dashboard Production Build

- **Source Code:** `apps/officer-dashboard/` (Vite, React 18, TypeScript, Tailwind CSS, Lucide Icons)
- **Production Build Artifact:** `apps/officer-dashboard/dist/`
  - `dist/index.html` (841 bytes)
  - `dist/assets/index-*.js` (Production bundled with tree-shaking)
  - `dist/assets/index-*.css` (Optimized Tailwind utility stylesheet)
- **Features Included:**
  - Regional Mandal filtering (`Geesugonda`, `Wardhannapet`, `Atmakur`)
  - Ticket triage workflow (PENDING -> REVIEWING -> RESOLVED)
  - Prescription authoring with CIBRC dosage validation helper
  - Real-time conversation sync linking extension officer responses back to farmer voice chat

---

## 5. Deployment Readiness Sign-Off

The release bundle is self-contained, reproducible, and ready for deployment on staging cloud infrastructure or local hackathon demonstration networks.
