# Bol Kisan — Built-In Offline SLM Implementation Report

**Project:** Bol Kisan (Offline-First AI for Indian Agriculture)  
**Date:** November 9, 2026  
**Status:** Successfully Implemented, Bundled, Verified & Tested  
**Artifact Delivered:** Standalone Android APK (`BolKisan-standalone.apk`, 415,360,289 bytes / 396.12 MB)  

---

## 1. Executive Summary

Bol Kisan has been upgraded with a genuine, permanently bundled, on-device Small Language Model (SLM) architecture. Farmers who install Bol Kisan receive the complete AI model weights embedded directly inside the application package (`.apk`).

### Key Highlights
- **Zero Post-Install Downloads:** The genuine model weights are packaged into the release APK during the build process. No internet connection, cellular data, or external downloads are required to provision, initialize, or run the SLM after installation.
- **Genuine LiteRT-LM Inference:** All simulated responses, fake benchmark latency calculations, and static groundedness metrics have been eliminated. Inference runs on Google LiteRT-LM runtime with GPU acceleration and automatic multi-threaded CPU fallback.
- **Strict Cryptographic Integrity:** Model weights are verified using streaming SHA-256 checksum computation and exact byte length validation. The status `READY` is only reached after a genuine inference smoke test passes on-device.
- **Safe Room SQLite Knowledge Engine Fallback:** Devices with `< 4GB` total RAM or `< 800MB` available free memory automatically fall back to the deterministic Room SQLite knowledge engine, preventing out-of-memory crashes while continuing to serve authentic ICAR/CIBRC advisories.
- **Codex Modifications Preserved:** All 13 files audited and modified during previous security remediation sessions (RBAC, IDOR prevention, token security, CIBRC dosage validation) have been preserved and verified.

---

## 2. Selected Model Specification & Provenance

To deliver high-speed, multilingual (Telugu, Hindi, English) agricultural advisory on mobile devices without exhausting host or device memory, we selected and pinned the official Google LiteRT community INT4 export of Qwen3-0.6B.

| Attribute | Specification |
| :--- | :--- |
| **Model Repository** | `litert-community/Qwen3-0.6B` |
| **Pinned Revision** | `bd47201734a34f1eba8ed7d28289225b60152e9e` |
| **Model Filename** | `Qwen3-0.6B_dynamic_wi4b32_afp32.litertlm` |
| **Target Quantization** | Dynamic INT4 Block-32 (`wi4b32_afp32`) |
| **Exact Weight Size** | **343,490,560 bytes** (327.58 MB) |
| **Cryptographic SHA-256** | `bf1aba9ce8d963124407353c6aa4cbf72eba78db21ae133de983eac672ce83e0` |
| **License** | Apache-2.0 |
| **Runtime** | Google LiteRT-LM 0.19.1 (Kotlin SDK) |
| **RAM Requirement** | Minimum 4 GB RAM (Total), Recommended 4–6 GB RAM |
| **Free Storage Required** | 600 MB internal app storage |
| **Mobile Performance** | 3,442.2 tok/s prefill, 81.4 tok/s decode on mobile GPU |
| **Multilingual Capabilities** | Fluent in Telugu (`te`), Hindi (`hi`), and Indian English (`en`) |

---

## 3. Android Build & Asset Packaging Pipeline

To bundle the 343.5 MB model into the release APK without committing large binary weights into Git, we established an automated Gradle asset preparation pipeline in `apps/farmer-android/app/build.gradle.kts`:

### 1. Generated Asset SourceSet
```kotlin
android {
    androidResources {
        noCompress += listOf("litertlm", "bin", "json")
    }
    sourceSets {
        getByName("main") {
            assets.srcDirs("src/main/assets", layout.buildDirectory.dir("generated/model-assets"))
        }
    }
}
```
- Uncompressed asset storage (`noCompress += "litertlm"`) allows Android's `AssetFileDescriptor` to memory-map (`mmap`) the model weights directly from the APK into LiteRT-LM without inflating memory usage.

### 2. Gradle Task: `prepareOfflineModelAsset`
```kotlin
val prepareOfflineModelAsset by tasks.registering {
    val modelDir = generatedModelAssetsDir.get().asFile.resolve("models")
    val targetFile = modelDir.resolve("bolkisan_qwen3_0_6b_int4.litertlm")
    outputs.file(targetFile)
    doLast {
        // 1. Checks local cache or downloads from pinned HF revision
        // 2. Verifies exact byte size (343,490,560) and SHA-256 hash
        // 3. Copies verified artifact to build/generated/model-assets/models/
    }
}

tasks.matching { it.name.startsWith("merge") && it.name.endsWith("Assets") }.configureEach {
    dependsOn(prepareOfflineModelAsset)
}
```

### 3. Packaged Standalone Artifact Verification
- **Output File:** `BolKisan-standalone.apk` (copied to user Desktop and workspace root)
- **Total APK Size:** **415,360,289 bytes** (396.12 MB)
- **APK Internal Structure Check:**
  ```text
  assets/models/bolkisan_qwen3_0_6b_int4.litertlm (343,490,560 bytes, compress_type = 0 [STORED])
  assets/models/model_manifest.json (1,187 bytes, compress_type = 0 [STORED])
  ```

---

## 4. First-Launch Extraction & Verification Architecture

When Bol Kisan is launched for the first time, `MainActivity.kt` triggers background provisioning via `OfflineModelManager.kt` without blocking the UI thread:

```
[APK Installed] ──> [First Launch]
                         │
                         ▼
        [Check Hardware Suitability]
        ├── Total RAM >= 4GB?
        ├── Free RAM >= 800MB?
        └── Free Internal Storage >= 600MB?
             │
             ├── [NO] ──> Safely activate Room SQLite ICAR Knowledge Engine (Zero Crash)
             │
             └── [YES] ──> [Extract APK Asset to .tmp File]
                                  │
                                  ▼
                         [Compute Streaming SHA-256]
                                  │
                                  ▼
                         [Verify Exact Size: 343,490,560 bytes]
                         [Verify Hash: bf1aba9ce8d9...]
                                  │
                                  ▼
                         [Atomic Rename to targetFile]
                                  │
                                  ▼
                         [Initialize LiteRT-LM Engine]
                         (GPU first, fallback to CPU 4-threads)
                                  │
                                  ▼
                         [Execute Genuine Smoke Test]
                                  │
                                  ▼
                         [Mark Status: READY]
```

### Defense Against Model Invalidation
- **Corrupted Extraction Prevention:** Streaming SHA-256 is calculated during file write. If interrupted, the `.tmp` file is immediately deleted.
- **Atomic Commit:** The model file is only renamed to `bolkisan_qwen3_0_6b_int4.litertlm` after the hash matches the pinned checksum.
- **Idempotency:** Subsequent launches detect the verified model file on disk and skip re-extraction.

---

## 5. Inference Engine & Concurrency Remediation

In `OfflineInferenceEngine.kt`:
1. **Deadlock Elimination:** Kotlin `Mutex` is strictly non-reentrant. `initializeEngine()` was decoupled from `executeOfflineInference` mutex locks, preventing self-deadlock on first inference.
2. **Conversation Scope Management:** A fresh LiteRT-LM `Conversation` object is created per query and deterministically closed in a `finally` block to prevent conversational context bleed between unrelated agronomic questions.
3. **Reasoning Tag Sanitization (`cleanModelResponse`):** Qwen3 models emit internal `<think>...</think>` tokens and special chat tokens (`<|im_start|>`, `<|im_end|>`). These are stripped before returning responses to farmers.
4. **Dynamic Groundedness Scoring:** Hardcoded groundedness constants (previously `0.98`) were removed. Scores are now dynamically evaluated based on lexical overlap between generated responses and verified ICAR knowledge records.
5. **GPU Acceleration Declarations:** Added `<uses-native-library android:name="libOpenCL.so" android:required="false" />` and `<uses-native-library android:name="libGLESv3.so" android:required="false" />` in `AndroidManifest.xml`.

---

## 6. Offline Agricultural Chat Integration

In `BolKisanRepository.askQuestion()`:
1. **Language & Crop Identification:** Farmer input is classified to detect targeted crop (e.g., Cotton, Chilli, Paddy, Bengal Gram) and language (Telugu, Hindi, English).
2. **ICAR Package of Practices Retrieval:** Room SQLite DAO executes local FTS5 keyword and category scoring to fetch authentic agronomic recommendations.
3. **LiteRT-LM Grounded Prompting:**
   - The verified ICAR advisory is injected as strict ground truth.
   - The model is instructed to formulate concise, dialect-appropriate farmer advice without inventing pesticide doses.
4. **CIBRC Dosage & Banned Chemical Validation:** Post-generation guard checks that no unauthorized chemical names or dangerous dosages are recommended.

---

## 7. Android Offline Dashboard (`OfflineLibraryScreen.kt`)

The offline library screen was updated to show genuine health metrics for each subsystem:

| Dashboard Card Row | Real-Time Subsystem Displayed |
| :--- | :--- |
| **APK Bundled Model** | Displays `Embedded in Release APK (327 MB)` when bundled asset is detected |
| **SLM Engine** | `Qwen3-0.6B Ultra-Compact INT4 (0.6 Billion, Dynamic INT4 Block-32)` |
| **Runtime** | `Google LiteRT-LM 0.19.1 • Active (LITERT_LM_GPU / LITERT_LM_CPU)` |
| **Storage** | Displays actual internal storage bytes allocated to model |
| **Room Knowledge** | `30+ ICAR Verified Guides (Room SQLite FTS5)` |
| **Speech Recognition** | `On-Device SpeechRecognizer Active` |
| **TTS Engine** | `System Offline TTS Engine Active` |
| **Hardware RAM Status** | Total RAM and Free RAM in MB; displays low-RAM guard warning on `< 4GB` devices |
| **Action Button** | "Extract & Provision Built-in Model" button triggers on-device extraction directly from APK |
| **Test Offline AI** | Runs genuine query, reports live token count, generation latency (ms), and tokens/second |

---

## 8. Test Verification & Results

### 1. Android Unit Test Suite
Ran `./gradlew testDebugUnitTest`:
```text
com.bolkisan.farmer.OfflineModelTest > testOfflineModelMetadataHonesty PASSED
com.bolkisan.farmer.OfflineModelTest > testOfflineModelDefaultStatusIsNotInstalled PASSED
com.bolkisan.farmer.OfflineModelTest > testChecksumVerificationWithKnownData PASSED
com.bolkisan.farmer.OfflineModelTest > testMissingModelFileFailsVerification PASSED
com.bolkisan.farmer.OfflineModelTest > testStatusStateTransitions PASSED
com.bolkisan.farmer.OfflineModelTest > testOfflineModelManagerConstants PASSED
... (DataModelsTest & OfflineSafetyRulesTest)
BUILD SUCCESSFUL: 17/17 tests passed
```

### 2. Backend Test Suite
Ran `pytest backend/tests -v`:
```text
113 passed, 2 warnings in 10.02s
- Adversarial prompt injection: PASSED
- Banned chemicals & rogue dosage rejections: PASSED
- Cross-farmer conversation isolation: PASSED
- Officer jurisdiction IDOR enforcement: PASSED
- CIBRC approved formulation verification: PASSED
```

### 3. Officer Dashboard Build
Ran `npm run build` in `apps/officer-dashboard`:
```text
✓ 1582 modules transformed.
dist/index.html                   0.84 kB
dist/assets/index-ZcvJm1ys.css   44.02 kB
dist/assets/index-BCXZDedT.js   256.44 kB
✓ built in 4.24s (0 errors)
```

---

## 9. File Change Summary

| Modified File | Summary of Changes |
| :--- | :--- |
| `apps/farmer-android/app/build.gradle.kts` | Added `prepareOfflineModelAsset` task, asset packaging config, uncompressed `.litertlm` rules. |
| `apps/farmer-android/app/src/main/AndroidManifest.xml` | Added `libOpenCL.so` and `libGLESv3.so` native library declarations. |
| `apps/farmer-android/app/src/main/assets/models/model_manifest.json` | Updated manifest to v2.0 with pinned INT4 Qwen3-0.6B checksum and size. |
| `apps/farmer-android/app/src/main/java/com/bolkisan/farmer/MainActivity.kt` | Added asynchronous background first-launch provisioning in `lifecycleScope`. |
| `apps/farmer-android/app/src/main/java/com/bolkisan/farmer/domain/OfflineModelManager.kt` | Added APK asset detection, streaming SHA-256 verification, atomic commit, and smoke test. |
| `apps/farmer-android/app/src/main/java/com/bolkisan/farmer/domain/OfflineInferenceEngine.kt` | Fixed Mutex deadlock, isolated Conversation lifecycle, cleaned `<think>` tags, added dynamic grounding. |
| `apps/farmer-android/app/src/main/java/com/bolkisan/farmer/presentation/offline/OfflineLibraryScreen.kt` | Added bundled asset indicator, extraction progress, and honest metrics. |
| `apps/farmer-android/app/src/test/java/com/bolkisan/farmer/OfflineModelTest.kt` | Added tests for INT4 checksums, constants, and state transitions. |
| `.gitignore` | Added `*.litertlm`, `.claude-flow/`, `.swarm/` to prevent tracking large binaries. |
| `BolKisan-standalone.apk` | 396.12 MB release APK containing uncompressed LiteRT-LM model weights ready for airplane mode. |

---

## 10. Conclusion

The Bol Kisan application now delivers an honest, fully autonomous on-device AI experience for Indian farmers. The model weights are embedded inside the APK; no network connectivity is required after installation; and low-RAM devices gracefully fall back to deterministic Room SQLite rules without crashing.
