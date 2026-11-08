# ADR-004: Multilingual Voice Pipeline (Telugu, Hindi, English)

## Status
Accepted

## Context
Rural farmers have diverse literacy levels and regional accents. Text input is a significant friction point. Voice must be the primary interaction mode.
Languages:
1. **Telugu (తెలుగు)** (Andhra Pradesh, Telangana)
2. **Hindi (हिन्दी)** (Central and Northern India)
3. **English** (National, administrative, and testing)

## Decision
1. **Audio Capture**:
   - Android client captures raw audio in 16kHz mono PCM (AudioRecord / MediaRecorder) with silence trimming and Opus compression for cloud upload (reducing bandwidth to < 16 kbps on 2G/3G).
2. **Online Speech Processing**:
   - **ASR**: Utilize Digital India Bhashini ASR APIs (or verified Indic speech endpoints) with fallback to Whisper / IndicConformer container.
   - **TTS**: Utilize Bhashini Indic-TTS (or Google Cloud TTS Indic voices) delivering natural regional pitch and cadences suitable for rural listeners.
3. **Offline Speech Processing**:
   - ASR: IndicConformer (AI4Bharat) quantized 8-bit ONNX/LiteRT model or Android system native speech recognizer.
   - TTS: Android native TextToSpeech engine with installed offline Indic language packs (`te-IN`, `hi-IN`).
4. **Resilience & UI Audio Feedback**:
   - Tapping the Push-to-Talk button triggers a soft haptic vibration and tactile audio chirp (`#15803D` pulsing ring).
   - Real-time audio waveform visualizer provides immediate feedback that the microphone is active and recording.
   - Audio is cached locally in the app cache directory to avoid repeated bandwidth downloads for frequent answers.

## Consequences
- **Positive**: Direct, frictionless voice accessibility for non-literate farmers. High resilience on low bandwidth.
- **Negative**: Pronunciation of scientific chemical names requires phoneme mapping in the agricultural dictionary (`KB20`).
