# Bol Kisan: Stage 0 Architecture, Design & Security Reviews

**Date:** 2026-11-08  
**Participants:** Senior Engineering Team, Principal Architect, Design Director, Chief Security Officer (CSO)  
**Status:** Approved with Mandatory Safeguards  

---

## 1. Engineering Review (`gstack: plan-eng-review`)

### Architecture & Decoupling
- **Monorepo Layout**: Clean separation into `apps/farmer-android`, `apps/officer-dashboard`, `backend/`, and `knowledge-base/`. Ensures zero tight coupling between backend services and mobile presentation layers.
- **RAG & AI Layer Decoupling**: LlamaIndex and pgvector provide the core retrieval spine. Google Gemini 3.8 Flash (`gemini-3.8-flash`) is integrated via the official Google Gen AI SDK (`google-genai`). The system prompt is strictly constrained with a temperature of `0.1` and factual grounding instructions. Retrieval, embeddings, and generative layers are fully abstracted so models can be swapped via environment variables.
- **Offline Fallback Architecture**: Acknowledges that on-device LLMs (AgriParam, Gemma 4 E2B) cannot be guaranteed on 3GB RAM budget phones without thermal and OOM risks. Shipped with a verified local SQLite Room database containing ICAR packages of practice as an immediate fail-safe.
- **Data Flow & Caching**: ETag and Gzip compression applied on all API endpoints; offline outbox synchronization managed by Android WorkManager.

### Identified Technical Risks & Mitigations
1. **Risk**: Gemini API or internet connectivity dropped during field usage.  
   **Mitigation**: The Android app detects connection loss within 500ms and switches immediately to the offline local retrieval pipeline.
2. **Risk**: Discrepancies in pesticide names between local Telugu dialects and official CIBRC English nomenclature.  
   **Mitigation**: Multilingual agricultural dictionary (`KB20`) maps local terms (e.g., *పురుగు మందు*, *ఎరుపు పేను*, *కలుపు*) to standardized scientific and chemical entities before running database queries.

---

## 2. Design & Usability Review (`gstack: plan-design-review` & `UI/UX Pro Max`)

### Farmer Experience (Mobile-First Voice Assistant)
- **Design Read**: High-sunlight outdoor usability, large tactile touch targets, voice-first paradigm. Farmers should never have to navigate deep nested menus to ask a question.
- **Color Palette & Accessibility**:
  - Primary: `#15803D` (Forest Clover Green, WCAG AAA 7:1 contrast on white/mint background).
  - Secondary: `#22C55E` (Leaf Green).
  - Accent/CTA: `#A16207` (Harvest Gold).
  - Alert/Destructive: `#DC2626` (Bright Hazard Red for pesticide toxicity and mandatory safety alerts).
  - Background: `#F0FDF4` (Soft Mint Cream, reduces eye strain under direct sunlight).
- **Touch Target Guidance**: Minimum 48dp target sizes on Android with 8dp spacing between interactive elements. The central Voice Push-to-Talk button is 80dp in diameter with haptic pulsation.
- **Typography**: Outfit for display headings; Work Sans for high-legibility body text and speech transcripts.
- **Audio Feedback**: Visible waveform animation while recording; immediate clear voice playback with visual caption cards.

### Officer Dashboard Experience
- **Layout**: High data density, responsive desktop web interface (React + TypeScript + Tailwind CSS).
- **Triage Cockpit**: Color-coded urgency badges (`Red`: Emergency/Pesticide risk, `Yellow`: Disease review, `Green`: General advisory).
- **Case Dossier**: Side-by-side view of farmer speech transcript, audio playback, attached crop photographs, and one-click prescription dispatch.

---

## 3. Chief Security Officer (CSO) Review (`gstack: cso`)

### Threat Modeling (STRIDE) & OWASP Mitigations
1. **Elevation of Privilege**:
   - *Threat*: A farmer attempts to claim an `extension_officer` role or access officer triage endpoints.
   - *Mitigation*: The `/auth/verify-otp` endpoint strictly defaults new user registrations to the `farmer` role. Officer accounts cannot be created via public self-signup; they must be provisioned and verified by an authenticated administrator with valid departmental credentials.
   - *Enforcement*: Role-based middleware (`RoleChecker(["extension_officer", "admin"])`) guards all officer endpoints.
2. **Tampering & Pesticide Poisoning Attack**:
   - *Threat*: Malicious injection into agricultural knowledge chunks or tampering with pesticide dosage tables.
   - *Mitigation*: Database tables `approved_pesticide_uses` and `pesticide_labels` have strict write restrictions. In production, only authenticated admin migrations can alter chemical records. All edits trigger immutable `audit_events`.
3. **Information Disclosure & EXIF Leakage**:
   - *Threat*: Crop photographs leaking farmer home coordinates or device identifiers.
   - *Mitigation*: Backend image ingestion pipeline scrubs all EXIF metadata (camera serial number, device model, personal tags) while preserving only anonymized region coordinates for micro-climate analysis.
4. **Brute Force & Denial of Service**:
   - *Threat*: Repeated OTP generation attempts exhausting SMS quotas.
   - *Mitigation*: Rate limiting of max 3 OTP requests per phone number per 15 minutes; Redis-backed exponential backoff.
5. **Prompt Injection & Jailbreaking**:
   - *Threat*: Farmer voice input contains adversarial prompts attempting to force the LLM to endorse illegal or unapproved chemicals.
   - *Mitigation*: The deterministic safety gate sits *in front of* and *behind* the LLM. Even if an LLM is tricked into generating unsafe advice, the deterministic post-filter blocks any dosage numbers not present in the verified CIBRC database.

---

## 4. Sign-Off Decision
- **Architecture**: APPROVED.
- **Design System**: APPROVED.
- **Security & Safety Invariants**: APPROVED.
- **Directive**: Proceed to Stage 1 (Project Foundation Scaffolding & Initial Implementations).
