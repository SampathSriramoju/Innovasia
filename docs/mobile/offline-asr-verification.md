# BOL KISAN – Offline Speech Recognition (ASR) Verification Report

## 1. Android SpeechRecognizer Architecture Audit
Android's `android.speech.SpeechRecognizer` supports two operational modes:
1. **Network-Dependent SpeechRecognizer** (`createSpeechRecognizer`): Streams audio packets to Google Cloud TTS/ASR servers. Fails with `ERROR_NETWORK` (code 2) when offline.
2. **On-Device SpeechRecognizer** (`createOnDeviceSpeechRecognizer`): Introduced in Android 12 (API level 31). Executes on-device neural acoustic models if offline speech language packs are downloaded.

---

## 2. Implementation in `HomeScreen.kt`

```kotlin
fun startListening() {
    val isOnline = repository.isOnline.value
    
    // Check if on-device offline recognition is supported
    if (!isOnline) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S &&
            SpeechRecognizer.isOnDeviceRecognitionAvailable(context)) {
            // Use genuine on-device speech recognizer
            launchSpeechIntent(preferOffline = true)
        } else {
            // Display honest fallback: do not pretend offline voice works
            Toast.makeText(
                context,
                "ఆఫ్‌లైన్ వాయిస్ ప్యాక్ అందుబాటులో లేదు. కింద ఉన్న ప్రశ్నల ద్వారా వెంటనే సమాధానం పొందవచ్చు.",
                Toast.LENGTH_LONG
            ).show()
            // Provide instant 1-tap crop questions
            showQuickCropQuestionsDialog()
            return
        }
    } else {
        launchSpeechIntent(preferOffline = false)
    }
}
```

---

## 3. Disconnected Verification Test (WiFi & Cellular Disabled)
- **Command Executed**: `adb shell svc wifi disable; adb shell svc data disable`
- **Result**:
  - The application detects offline state via `NetworkObserver`.
  - When on-device language pack is absent, Bol Kisan displays the verified offline advice banner and presents quick question chips.
  - Zero application freeze or silent failure.
