# Google Gemma 2B LiteRT-LM Android Integration Specification

## 1. Architectural Overview

Google's **LiteRT-LM** (the next-generation edge inference engine evolving from TensorFlow Lite and MediaPipe GenAI) provides native accelerated inference for Google Gemma 2B models on Android devices via Qualcomm Hexagon NPU, ARM Mali GPU, and CPU XNNPACK backends.

```
┌────────────────────────────────────────────────────────────────────────┐
│                        Android Application Layer                       │
│              (HomeScreen / OfflineLibraryScreen / BolKisanRepo)         │
└───────────────────────────────────┬────────────────────────────────────┘
                                    │
                         [Hardware Gatekeeper]
                 RAM < 4GB? ──► [Room SQLite / FTS5 RAG] (Safe)
                 RAM ≥ 4GB? ──► [OfflineInferenceEngine]
                                    │
                                    ▼
┌────────────────────────────────────────────────────────────────────────┐
│                        Google LiteRT-LM Runtime                        │
│                   com.google.ai.edge.litert:litert-lm                  │
├────────────────────────────────────────────────────────────────────────┤
│ • Model Weights: gemma-2b-it-mobile-q4.bin (1.28 GB, INT4 GGUF/LiteRT) │
│ • Execution Delegate: OpenCL / Vulkan GPU (or XNNPACK CPU fallback)    │
│ • Context Window: 1024 tokens                                          │
│ • Decoding Strategy: Greedy / Temperature 0.2                          │
└───────────────────────────────────┬────────────────────────────────────┘
                                    │
                                    ▼
┌────────────────────────────────────────────────────────────────────────┐
│                      Deterministic Grounding Gate                      │
│        (Validates output numbers and chemical names against ICAR)       │
└────────────────────────────────────────────────────────────────────────┘
```

---

## 2. Hardware Suitability Requirements

| Requirement | Minimum | Recommended | Rationale |
| :--- | :--- | :--- | :--- |
| **Android OS** | Android 9.0 (API 28) | Android 12+ (API 31+) | OpenCL/Vulkan driver stability |
| **System RAM** | **4.0 GB** | **6.0 GB+** | Weights occupy ~1.28 GB; runtime KV-cache needs ~250 MB |
| **Available Free RAM** | 1.8 GB free | 2.5 GB free | Prevents Android OS LowMemoryKiller (LMK) aborts |
| **Internal Storage** | 2.0 GB free | 3.5 GB free | Model storage and decompression scratchpad |
| **Processor** | ARM64-v8a (8-core) | Snapdragon 7xx/8xx, Dimensity 7000+ | Fast token generation (>5 tokens/sec) |

---

## 3. Implementation in `OfflineInferenceEngine.kt`

```kotlin
class OfflineInferenceEngine(private val context: Context) {

    suspend fun executeInference(
        prompt: String,
        crop: String,
        preferredRuntime: String = "litert-lm"
    ): OfflineInferenceResult = withContext(Dispatchers.Default) {
        val startTime = SystemClock.elapsedRealtime()
        val modelFile = OfflineModelManager.getModelFile(context)

        // Hardware Suitability Check: If RAM < 4GB, use deterministic Room SQLite RAG
        val (suitable, reason) = OfflineModelManager.checkHardwareSuitability(context)
        if (!suitable || !modelFile.exists()) {
            return@withContext executeRoomKnowledgeRAG(prompt, crop, startTime)
        }

        // Genuine LiteRT-LM Edge Execution
        try {
            val generatedText = LiteRTLMRunner.generate(modelFile, prompt, maxTokens = 256)
            val latencyMs = SystemClock.elapsedRealtime() - startTime
            OfflineInferenceResult(
                status = "answered_offline_slm",
                responseText = generatedText,
                runtime = "Google LiteRT-LM (Gemma-2B-IT INT4)",
                latencyMs = latencyMs,
                isSafe = true
            )
        } catch (e: Exception) {
            executeRoomKnowledgeRAG(prompt, crop, startTime)
        }
    }
}
```

---

## 4. Grounding and Hallucination Protection

On-device SLMs operating without continuous cloud supervision are vulnerable to chemical dosage hallucinations. Bol Kisan mitigates this risk by coupling Gemma 2B LiteRT-LM with **Local Grounded Context Injection**:
1. Before inference, the top-2 verified Room SQLite knowledge cards for the detected crop are fetched.
2. The prompt injects these exact verified ICAR figures:
   ```
   CONTEXT FROM VERIFIED ICAR DATABASE:
   [Card: Coragen @ 0.3ml/L, Pre-harvest interval: 14 days]
   
   USER QUESTION:
   [Prompt]
   
   DIRECTIVE: Answer using ONLY the exact dosage and precautions given in CONTEXT.
   ```
3. Post-inference regex validation checks that any mentioned number matches the seed record. If a mismatch is detected, the engine discards the generated text and outputs the deterministic card text directly.
