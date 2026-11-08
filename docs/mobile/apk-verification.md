# BOL KISAN – Android APK Integrity, Security & Packaging Audit

## 1. Package Identity & Build Specifications
- **Package Name**: `com.bolkisan.farmer`
- **Application Label**: `Bol Kisan`
- **Version Code**: `1`
- **Version Name**: `1.0.4-voice-clean`
- **File Name**: `release/bolkisan-latest.apk`
- **APK Binary Size**: **20.41 MB** (20410013 bytes)
- **Minimum SDK**: Android API 26 (Android 8.0 Oreo)
- **Target SDK**: Android API 34 (Android 14)
- **Compile SDK**: Android API 34

---

## 2. Declared Android Permissions Audit

| Permission Name | Security Level | Purpose in Bol Kisan | Least Privilege Compliance |
| :--- | :--- | :--- | :--- |
| `android.permission.INTERNET` | Normal | Connect to Gemini cascade & Extension Officer API | **COMPLIANT** |
| `android.permission.ACCESS_NETWORK_STATE` | Normal | NetworkObserver for offline/online state detection | **COMPLIANT** |
| `android.permission.RECORD_AUDIO` | Dangerous | Voice assistant speech recognition | **COMPLIANT** (Requested at runtime) |
| `android.permission.CAMERA` | Dangerous | Disease photo leaf scanning & diagnosis | **COMPLIANT** (Requested at runtime) |

> **Security Note**: No unnecessary background permissions (such as `READ_CONTACTS`, `ACCESS_FINE_LOCATION`, or `WRITE_EXTERNAL_STORAGE`) are requested.

---

## 3. Core Functional Flows Verified on Device

1. **Authentication & Language Persistence**:
   - Initial boot loads `HomeScreen` directly without mandatory blocking login.
   - 1-tap language toggle cycles Telugu, Hindi, and English with zero session loss.
2. **Voice Assistant & Speech Recognition**:
   - Mic button activates `SpeechRecognizer` with `RecognizerIntent.ACTION_RECOGNIZE_SPEECH`.
   - Answers render with CIBRC source citations and audible Text-to-Speech audio.
3. **Offline Voice Workflow & Cloud Disconnect**:
   - Verified with cellular data and WiFi disabled on emulator (`adb shell svc wifi disable`).
   - App seamlessly falls back to local SQLite Room cache (`OfflineKnowledgeDao`).
   - Outbox automatically enqueues unresolvable queries into `pending_sync_tickets`.
   - When network restores, `SyncWorker` delivers tickets to Extension Officer web dashboard.
4. **Officer Escalation Ticket Creation**:
   - Farmer can tap "అధికారికి పంపు" (Escalate to Officer) creating ticket ID (e.g. `BK-2026-X1`).

---

## 4. Signing & Release Hygiene
- **Debug Build**: Signed using standard Android debug keystore for development.
- **Production Secrets**: Zero production keystores or hardcoded private keys are present in repository source trees, strictly respecting security standards.
