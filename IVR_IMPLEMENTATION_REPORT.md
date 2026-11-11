# Bol Kisan — IVR Telephony Fallback Implementation & Audit Report

**Project:** Bol Kisan (Offline-First AI for Indian Agriculture)  
**Module:** Voice Telephony Fallback (IVR - Interactive Voice Response)  
**Date:** November 10, 2026  
**Auditor / Specialist:** Agent 3 — Testing and Evaluation Specialist  
**Status:** Implemented, Fully Tested, Audited, and Verified  
**Scope Classification:** Hackathon Interactive Demonstration System (Mock Telephony & Web Audio Simulator)

---

## 1. Executive Summary

In Indian rural agriculture, significant numbers of smallholder and marginal farmers operate basic feature phones without continuous 4G/5G data connectivity or smartphones capable of running on-device AI applications. To ensure zero farmer exclusion, **Bol Kisan** incorporates a resilient **IVR (Interactive Voice Response) Fallback System**. 

This system provides a full voice telephony interface accessible via standard toll-free dial-in numbers (`1800-425-1551`), enabling farmers to interact via DTMF keypad selection and spoken voice queries in **Telugu (`te`)**, **Hindi (`hi`)**, and **English (`en`)**.

### Key Verification Highlights
- **100% Non-Regression & Test Passing:** 57 automated backend pytest test cases passed cleanly across all test suites, including IVR core tests, SMS fallback tests, CIBRC stage safety tests, and Mandi API client tests.
- **Zero Frontend Build Errors:** `npm --prefix "apps/officer-dashboard" run build` compiles with 0 TypeScript and Vite errors (1,584 modules transformed, 319.89 kB production bundle).
- **Statutory CIBRC Safety Gate Enforcement:** Any mention of banned pesticides (e.g., Endosulfan) or unverified chemical dosages is intercepted immediately, generating a priority escalation ticket (`ESC-...`) with spoken safety warnings.
- **Full Conversational Memory & Continuity:** Contextual entity inheritance (crop and pest) persists across conversational turns during the call.
- **Natural Spoken Text Normalization:** TTS audio generation strips markdown formatting, bullet points, and bracketed citation keys (`[KB01]`, `(KB-DOC-02)`) while preserving safety warnings and chemical dosages.
- **Comprehensive Failure Resilience:** Automated handling and recovery for silence timeouts (10s), speech recognition failures (garbled audio), and backend 503 gateway errors with immediate officer desk handoff.

---

## 2. Architecture & Component Breakdown

```
+--------------------------------------------------------------------------------------------------+
|                                    FARMER AUDIO / DTMF INTERFACE                                 |
|                                                                                                  |
|   +--------------------------+        +---------------------------+        +-----------------+   |
|   | 12-Key DTMF Keypad       |        | Web Speech API Mic Input  |        | Simulated Voice |   |
|   | (Dual-Tone ITU-T Q.23)   |        | (te-IN / hi-IN / en-IN)   |        | Transcript Box  |   |
|   +-------------+------------+        +-------------+-------------+        +--------+--------+   |
+-----------------|-----------------------------------|-------------------------------|------------+
                  |                                   |                               |             
                  v                                   v                               v             
+--------------------------------------------------------------------------------------------------+
|                            BOL KISAN IVR FASTAPI BACKEND GATEWAY                                 |
|                                                                                                  |
|   * POST /api/v1/ivr/call/start           --> Initialize session & multilingual welcome menu     |
|   * POST /api/v1/ivr/call/dtmf            --> DTMF key routing (1=TE, 2=HI, 3=EN, 9=MAO, 0=End)  |
|   * POST /api/v1/ivr/call/voice           --> Process speech transcript with safety & context    |
|   * POST /api/v1/ivr/call/simulate-error  --> Inject telecom/ASR/timeout/503 errors              |
|   * POST /api/v1/ivr/call/end             --> Terminate call, compute duration, flush session    |
|   * GET  /api/v1/ivr/call/{call_id}       --> Full call telemetry, turns & ticket lookup         |
|   * GET  /api/v1/ivr/status               --> Gateway operational health & stats                 |
+-----------------------------------------------------+--------------------------------------------+
                                                      |
                                                      v
+--------------------------------------------------------------------------------------------------+
|                             IVR SERVICE ENGINE & CONVERSATION PIPELINE                           |
|                                                                                                  |
|   1. Query Understanding & Context Inheritance (Crop / Pest entity tracking across turns)        |
|   2. Statutory CIBRC Safety Gate Interceptor (Blocks banned chemicals & unverified cocktails)    |
|   3. Verified RAG Retrieval (ICAR / CIBRC / POP Grounded Knowledge Base)                         |
|   4. Gemini LLM Synthesis with Deterministic CIBRC/POP Fallback                                  |
|   5. format_ivr_spoken_text() Normalizer (Strips markdown, citations for clean TTS audio)       |
|   6. Dynamic Escalation Ticket Generator (Creates priority tickets: ESC-YYYYMMDD-XXXXXX)        |
+-----------------------------------------------------+--------------------------------------------+
                                                      |
                                                      v
+--------------------------------------------------------------------------------------------------+
|                                 AUDIO PLAYBACK & DASHBOARD DISPLAY                               |
|                                                                                                  |
|   * Browser Web Speech Synthesis Utterance (Native browser voice playback)                       |
|   * Backend Streaming TTS URL: /api/v1/voice/tts-stream?text=...&language=...                    |
|   * Full Dual-Tone Multi-Frequency (DTMF) Web Audio Synthesizer (Oscillator pair)                |
|   * Extension Officer Telemetry & Live Interactive Call Transcript Console                       |
+--------------------------------------------------------------------------------------------------+
```

### Key Components

1. **Backend Endpoints (`backend/app/api/v1/endpoints/ivr.py`):**
   - High-performance FastAPI router handling telephony call sessions, DTMF tone parsing, voice transcripts, error simulations, and session cleanup.
2. **IVR Service (`backend/app/services/ivr_service.py`):**
   - Implements `IVRCallSession` tracking call status, start/end timestamps, caller phone number, language preference, DTMF history, conversation turns, active crop/pest entities, and escalation tickets.
   - Manages in-memory state and JSON persistent store (`data/mock_ivr_store.json`).
3. **CIBRC Statutory Safety Gate (`backend/app/safety/safety_gate.py`):**
   - Strict adherence to Indian agrochemical statutes. Never invents dosages or recommends banned compounds.
4. **Natural Spoken Text Formatter (`format_ivr_spoken_text`):**
   - Converts markdown-heavy structured RAG text into natural conversational phrasing suitable for Text-to-Speech synthesis.
5. **Officer Dashboard Frontend Simulator (`apps/officer-dashboard/src/components/IVRFallbackSimulator.tsx`):**
   - Full realistic telephone handset UI featuring an OLED screen, animated waveform visualizer, 12-key DTMF keypad, push-to-talk mic input, preset farmer profiles, simulated voice transcript box, and error triggers.

---

## 3. Multilingual Call Flows & Language Persistence

### 1. Welcome Audio Prompt & Language Selection
Upon calling the toll-free number, the farmer is greeted with a clear trilingual audio menu:
- **Telugu:** *"నమస్తే! బోల్ కిసాన్ ఏఐ కిసాన్ హెల్ప్‌లైన్‌కు స్వాగతం. తెలుగు కొరకు 1 నొక్కండి..."*
- **Hindi:** *"नमस्ते! बोल किसान एआई हेल्पलाइन में आपका स्वागत है। हिन्दी के लिए 2 दबाएं..."*
- **English:** *"Welcome to Bol Kisan AI Kisan Helpline. For English press 3..."*

### 2. DTMF Language Routing
| DTMF Key | Language Mapped | ISO Code | Confirmation Prompt Snippet |
| :---: | :--- | :---: | :--- |
| **`1`** | Telugu | `te` | *తెలుగు భాష ఎంపిక చేయబడింది. దయచేసి మీ పంట సమస్యను మాట్లాడండి...* |
| **`2`** | Hindi | `hi` | *हिन्दी भाषा का चयन किया गया है। कृपया अपनी फसल की समस्या बोलें...* |
| **`3`** | English | `en` | *English language selected. Please state your crop pest or advisory question...* |

### 3. Language Persistence Verification
- When the farmer selects a language (e.g. DTMF `2` for Hindi), `session.language` is updated to `"hi"`.
- All subsequent turns (spoken responses, error recoveries, safety warnings, goodbye prompts) are rendered in Hindi.
- The language remains locked across all turns unless explicitly switched by pressing another DTMF key.

---

## 4. Multi-Turn Conversational Continuity & Context Retention

A common failure mode in telephony IVR bots is forgetting the subject of the conversation between turns. Bol Kisan IVR solves this using conversational context inheritance.

### Verified Scenario:
1. **Turn 1 (Farmer):** *"My cotton leaves are turning yellow in Warangal district."*
   - Query Understanding identifies: `Crop = Cotton`, `Symptom = Chlorosis / Yellowing`.
   - `session.context_crop` is set to `"Cotton"`.
   - Conversational turn records `"crop": "Cotton"`.
2. **Turn 2 (Farmer Follow-up):** *"What is the recommended spray for pink bollworm?"*
   - The query does not mention "cotton".
   - `QueryUnderstanding.analyze_query` inspects `context_crop` and prior user conversation history.
   - Accurately resolves `Crop = Cotton` and `Entity = Pink Bollworm`.
   - Delivers grounded CIBRC recommendation (Chlorantraniliprole / Profenofos / Emamectin Benzoate with precise dosage per acre).

---

## 5. Safety Refusals, CIBRC Enforcement & MAO Escalation

Bol Kisan strictly enforces Indian pesticide regulations governed by the Central Insecticides Board & Registration Committee (CIBRC) and the Insecticides Act, 1968.

### 1. Banned Chemical Interception (e.g., Endosulfan)
- **Input:** *"Can I spray Endosulfan at 500ml per acre in cotton?"*
- **Action:** Intercepted immediately by `safety_gate.evaluate_query()`.
- **System Behavior:**
  - `is_safe`: `False`
  - `is_escalated`: `True`
  - Status changed to `"escalated"`
  - Escalation ticket generated: `ESC-YYYYMMDD-XXXXXX`
  - Escalation logged with caller phone number, reason, and query transcript.
- **Spoken Safety Advisory (Telugu):**
  > *"భద్రతా హెచ్చరిక: మీరు అడిగిన రసాయన మోతాదు అధికారిక సి.ఐ.బి.ఆర్.సి రికార్డుల్లో ఆమోదం పొందలేదు. తప్పుడు రసాయనాల వాడకం పంటను నాశనం చేస్తుంది. మీ ప్రశ్న మండల వ్యవసాయ అధికారికి బదిలీ చేయబడింది. మీ టికెట్ సంఖ్య: ESC-20261110-XXXXXX."*
- **Spoken Safety Advisory (English):**
  > *"Safety Warning: Chemical or dosage not approved in official CIBRC statutory records. To prevent crop burning or pesticide poisoning, this issue has been escalated to your Extension Officer. Tracking Ticket: ESC-20261110-XXXXXX."*

### 2. Direct Farmer Keypad Escalation (DTMF `9`)
- At any point during the call, pressing `9` triggers an immediate priority transfer to the Mandal Agricultural Extension Officer (MAO).
- Returns ticket starting with `ESC-` and informs the farmer in their chosen language.

---

## 6. Error Handling & Recovery Protocols

The IVR engine features automated recovery prompts for unpredictable network conditions:

| Error Type | Trigger / Simulation | System Recovery Prompt (English Translation) | Escalation Action |
| :--- | :--- | :--- | :---: |
| **Silence Timeout** | No audio detected for 10 seconds | *"No speech detected (10s Silence Timeout). Are you still on the line? Press 9 for an officer or speak your query."* | No (Re-prompts farmer) |
| **ASR Speech Failure** | Low confidence (< 0.20) or garbled noise | *"Sorry, we could not clearly capture your speech (Confidence: 0.14). Please speak closer to the phone or press 9 for assistance."* | No (Requests retry) |
| **Backend 503 Outage** | LLM / Service Timeout | *"Technical network timeout detected (HTTP 503). Your session state has been automatically transferred to the Extension Officer desk."* | **Yes** (Generates `ESC-` ticket) |
| **Unrecognized DTMF** | Unmapped key pressed (e.g., `7`) | *"Key 7 received. Please press 1 for Telugu, 2 for Hindi, 3 for English, 9 for Agricultural Officer, or 0 to end call."* | No (Re-prompts menu) |

---

## 7. Call Termination & Resource Lifecycle

- **Termination Triggers:**
  - Pressing DTMF keypad key **`0`**
  - Pressing DTMF keypad key **`*`**
  - Clicking the **"End Call"** button on the handset simulator
- **Lifecycle Actions:**
  1. Stops Web Speech recognition and audio playback immediately.
  2. Synthesizes localized goodbye prompt (*"Call terminated. Thank you for using Bol Kisan AI Helpline."*).
  3. Computes total call duration in seconds (`duration_seconds = max(0, int(ended_at - started_at))`).
  4. Updates session status to `"ended"`.
  5. Persists session record in `mock_ivr_store.json` for auditing.

---

## 8. Test Execution Evidence & Regression Matrix

All tests were executed using the dedicated backend virtual environment (`backend/.venv/Scripts/pytest.exe`) and Node package manager.

### 1. Test Suite Summary Table

| Test Suite | Command Executed | Tests Passed | Tests Failed | Execution Time | Status |
| :--- | :--- | :---: | :---: | :---: | :---: |
| **IVR Core Test Suite** | `pytest backend/tests/test_ivr_fallback.py` | 12 | 0 | 19.55s | **PASSED** |
| **IVR E2E Verification Suite** | `pytest backend/tests/test_ivr_e2e_verification.py` | 7 | 0 | 32.66s | **PASSED** |
| **SMS Fallback Test Suite** | `pytest backend/tests/test_sms_fallback.py` | 13 | 0 | 224.72s | **PASSED** |
| **Stage Safety & Mandi Suite** | `pytest backend/tests/test_agricultural_stage_safety.py backend/tests/test_mandi_api_client.py backend/tests/test_safety.py` | 25 | 0 | 6.71s | **PASSED** |
| **Officer Dashboard Build** | `npm --prefix "apps/officer-dashboard" run build` | 1,584 modules | 0 | 5.74s | **PASSED** |
| **Total** | | **57 Tests** | **0 Failures** | | **100% HEALTHY** |

### 2. Detailed Test Itemization

#### A. `backend/tests/test_ivr_fallback.py` (12 Tests)
1. `test_format_ivr_spoken_text`: Verifies stripping of markdown formatting, bracketed citations (`[KB01]`), and document references for TTS.
2. `test_ivr_call_start_and_initial_menu`: Verifies initiation of simulated call, multilingual initial menu, and simulation metadata tags.
3. `test_ivr_dtmf_language_selection`: Verifies DTMF digits 1 (Telugu), 2 (Hindi), and 3 (English).
4. `test_ivr_dtmf_escalate_to_mao`: Verifies DTMF 9 creates `ESC-` ticket and routes to Extension Officer.
5. `test_ivr_dtmf_end_call`: Verifies DTMF 0 and * end call cleanly and compute duration.
6. `test_ivr_dtmf_unrecognized_key`: Verifies unmapped key fallback guidance.
7. `test_ivr_voice_input_safe_recommendation`: Verifies Cotton Pink Bollworm query returns safe recommendation and `/api/v1/voice/tts-stream` URL.
8. `test_ivr_voice_input_unsafe_chemical_escalation`: Verifies unapproved chemical dosage triggers CIBRC safety escalation with ticket starting with `ESC-`.
9. `test_ivr_conversational_continuity`: Verifies crop entity (`Cotton`) is preserved from turn 1 into turn 2.
10. `test_ivr_simulate_errors`: Verifies silence timeout, ASR failure, Gemini failure, and backend 503 failure.
11. `test_ivr_end_call_and_session_lookup`: Verifies duration calculation, call termination, and session lookup via `GET /call/{call_id}`.
12. `test_ivr_status_and_not_found`: Verifies gateway telemetry stats and 404 handling for invalid call IDs.

#### B. `backend/tests/test_ivr_e2e_verification.py` (7 Tests)
1. `test_req_1_multilingual_call_flows`: Full validation of language switching between Telugu, Hindi, and English with prompt confirmation.
2. `test_req_2_language_persistence`: Validates language persistence across multiple conversational turns.
3. `test_req_3_multi_turn_context_retention`: Validates context retention ("My cotton leaves are turning yellow" -> follow-up spray dosage).
4. `test_req_4_audio_input_and_spoken_output`: Validates Web Speech input, spoken TTS stream endpoints, and clean speech formatting.
5. `test_req_5_safety_refusals_and_cibrc_enforcement`: Validates instant refusal of Endosulfan and priority MAO escalation ticket.
6. `test_req_6_error_handling_and_recovery`: Validates recovery prompts for silence, garbled audio, and 503 backend failure.
7. `test_req_7_call_termination_and_session_cleanup`: Validates duration calculation and session cleanup upon call hangup.

---

## 9. Explicit Limitations & Hackathon Demonstration Scope

To ensure transparent evaluation, the following operational boundaries are explicitly documented:

> [!IMPORTANT]
> **HACKATHON DEMO DISCLAIMER — NO REAL TELEPHONE CALLS OR PAID TELECOM INFRASTRUCTURE:**
> 1. **No External PSTN/SIP Trunk Carrier:** This demonstration uses an in-memory/JSON-backed simulated telephony provider (`BOL_KISAN_MOCK_IVR`). It does not place real cellular calls or require paid enterprise telecom gateways (e.g., Twilio, Exotel, Plivo, Tata Tele Business).
> 2. **Client-Side Speech Processing:** Audio input in the web browser utilizes the Web Speech API (`webkitSpeechRecognition`) where available, backed by an accessible **Simulated Speech Transcript Input** box that allows seamless manual testing of any spoken phrase without hardware microphone dependencies.
> 3. **Audio Playback:** Spoken prompts are synthesized in real-time using the browser's native `window.speechSynthesis` engine with fallback audio streaming endpoints (`/api/v1/voice/tts-stream`).
> 4. **DTMF Audio Simulation:** Keypad tones adhere to the official ITU-T Q.23 standard frequency pairs (697–941 Hz row frequencies, 1209–1477 Hz column frequencies) generated via Web Audio API oscillators.

---

## 10. Conclusion & Production Readiness

The Bol Kisan IVR Fallback demonstration module is **fully functional, verified, and ready for evaluation**. It bridges the digital divide for rural farmers by offering:
- Natural voice interaction in Telugu, Hindi, and English.
- Rigorous, non-negotiable CIBRC statutory agricultural safety.
- Reliable multi-turn dialogue retention.
- Graceful degradation and error recovery under adverse telecom conditions.
