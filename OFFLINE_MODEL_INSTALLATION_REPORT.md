# Offline Model Installation & Lifecycle Management

## 1. Lifecycle State Machine

`OfflineModelManager` manages the genuine on-device SLM lifecycle through 7 distinct states:

```
                  ┌──────────────────────┐
                  │    Application Start │
                  └──────────┬───────────┘
                             │
                     Hardware Evaluation
                   RAM < 4GB?      RAM ≥ 4GB?
                   /                    \
                  ▼                      ▼
       ┌────────────────────┐   ┌─────────────────┐
       │ UNSUPPORTED_DEVICE │   │  NOT_INSTALLED  │
       │ (Use SQLite RAG)   │   └────────┬────────┘
       └────────────────────┘            │
                                  User Initiates
                                  Download (1.28GB)
                                         │
                                         ▼
                                ┌─────────────────┐
                                │   DOWNLOADING   │◄── Progress (0% - 100%)
                                └────────┬────────┘
                                         │
                                   Download Complete
                                         │
                                         ▼
                                ┌─────────────────┐
                                │    VERIFYING    │◄── SHA-256 Checksum
                                └────────┬────────┘
                                    /         \
                                MATCH       MISMATCH
                                /               \
                               ▼                 ▼
                     ┌──────────────────┐   ┌─────────┐
                     │   INITIALIZING   │   │ FAILED  │
                     └────────┬─────────┘   └─────────┘
                              │
                        Weights Loaded
                              │
                              ▼
                     ┌──────────────────┐
                     │      READY       │
                     └──────────────────┘
```

---

## 2. Independent Offline Subsystem Statuses

The offline architecture reports 4 independent capabilities instead of bundling them into an ambiguous "offline mode":

| Subsystem | Metric Checked | Default State | Behavior if Unavailable |
| :--- | :--- | :--- | :--- |
| **1. Offline Knowledge (Room DB)** | `dao.getKnowledgeCount() > 0` | `READY` (Prepopulated) | Initialized on background thread at app launch |
| **2. Offline Neural SLM** | `OfflineModelManager.modelStatus` | `NOT_INSTALLED` | Gracefully falls back to Room SQLite RAG |
| **3. Offline Speech Recognition** | `SpeechRecognizer.isOnDeviceRecognitionAvailable()` | Hardware Dependent | Shows instant text query fallback dialog |
| **4. Offline Text-to-Speech** | `tts.isLanguageAvailable(locale)` | Hardware Dependent | Falls back to system default English TTS with toast |

---

## 3. Storage Management & Clean Deletion

In `OfflineModelManager.kt`:
- **`downloadModel(context)`**: Streams file over HTTP/HTTPS with progress updates to `_downloadProgress` flow. Checks SHA-256 checksum upon completion.
- **`deleteModel(context)`**:
  ```kotlin
  fun deleteModel(context: Context): Boolean {
      val file = getModelFile(context)
      if (file.exists()) {
          val deleted = file.delete()
          _modelStatus.value = OfflineModelStatus.NOT_INSTALLED
          _downloadProgress.value = 0f
          _statusMessage.value = "Model uninstalled. Storage space freed (1.28 GB)."
          return deleted
      }
      return false
  }
  ```
- Instant reclamation of internal storage when the farmer chooses to uninstall the model.
