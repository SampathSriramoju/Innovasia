# ADR-003: Offline AI Evaluation and Resilient Local Fallback Architecture

## Status
Accepted

## Context
Indian farmers operate frequently in rural low-connectivity areas (intermittent 2G/3G/4G). The application must remain functional offline. However, running local LLMs on typical farmer smartphones (typically 3GB or 4GB RAM running Android 11-14) poses severe technical challenges:
- High RAM footprint causing Android low-memory killer (LMK) to terminate the app.
- Thermal throttling causing battery drain and frame drops.
- High inference latency exceeding the voice-to-voice target of under 6 seconds.

Two candidate SLMs were specified for evaluation:
1. **AgriParam** (Domain-specialized agricultural SLM)
2. **Gemma 4 E2B** (Google Gemma Edge 2B model)

## Decision
1. **Decoupled Edge Runtime Abstraction**:
   - The Android client defines an `OfflineInferenceEngine` interface capable of supporting both **LiteRT-LM** (Google's optimized runtime) and **llama.cpp** (via JNI/C++ native bindings with Q4_K_M quantization).
2. **Hardware Target Tiering**:
   - **Primary Target (4GB RAM)**: Conditional offline LLM execution enabled if available free RAM >= 1.2GB and device temperature <= 40°C.
   - **Experimental Target (3GB RAM)**: Offline LLM execution is marked experimental; strict memory check triggers fallback if free RAM < 900MB.
3. **Verified Local Knowledge Fallback (Non-Negotiable)**:
   - The application does NOT rely solely on an on-device LLM to function offline.
   - A compressed, indexed SQLite database (Room FTS5) is shipped within the app bundle containing:
     - Verified regional packages of practices for top 10 regional crops.
     - CIBRC approved pesticide and non-chemical IPM matrices.
     - Mandi emergency contacts and Mandal Extension Officer directory.
     - High-frequency agricultural FAQs with pre-rendered audio.
4. **Offline Voice-to-Voice SLA**:
   - For offline local retrieval: Target response latency < 1.5 seconds.
   - For offline SLM generation (on 4GB RAM): Target response latency < 5.8 seconds.
   - If SLM does not begin generating tokens within 2.5 seconds, the engine aborts to the local verified template fallback.
5. **Pending Sync Outbox**:
   - Offline questions requiring cloud verification or officer escalation are stored in Room `PendingSyncQueue` and automatically synced via Android WorkManager when network connectivity is restored.

## Consequences
- **Positive**: App will never crash or hang on budget 3GB RAM devices. Zero-downtime agricultural advice even in remote fields.
- **Negative**: Model evaluation must be empirical; models cannot be pre-assumed to run without actual device profiling.
