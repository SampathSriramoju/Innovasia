# BOL KISAN – Android Hardware & Device Compatibility Matrix

## 1. Physical & Virtual Testing Environment
- **Device Tested**: sdk_gphone64_x86_64 (AVD)
- **Host System**: Windows 11 x86_64 Host
- **Android Architecture**: `x86_64`
- **API Level**: Android SDK 37 (Release 17)
- **Allocated System RAM**: 3912 MB

---

## 2. Device Tier Support Matrix

| Hardware Tier | Typical Smartphone Profile | Supported Mode | Recommended Configuration |
| :--- | :--- | :--- | :--- |
| **Tier 1 (Entry)**<br>2GB – 3GB RAM | Redmi 9A, Realme C11, Galaxy A03 | **Online Cloud RAG + Offline Room Cache** | Strictly no SLM. SQLite rules and TTS playback. Storage: 25 MB. |
| **Tier 2 (Mid-Low)**<br>4GB RAM | Redmi Note 11, Realme 8, Vivo Y21 | **Online Cloud RAG + AgriParam Q4 (Conditional)** | AgriParam Q4 allowed if available RAM $\ge$ 1,400MB; otherwise auto-fallback to Room SQLite. |
| **Tier 3 (Mid-High)**<br>6GB RAM | Moto G62, POCO X5, Galaxy M33 | **Full Hybrid Mode** | Full AgriParam Q4 offline SLM + Cloud Gemini cascade. |
| **Tier 4 (Flagship)**<br>8GB+ RAM | OnePlus Nord, Galaxy S21+, Pixel 7 | **Full Hybrid + On-Device Whisper** | Full Gemma/AgriParam + Whisper Tiny offline ASR. |

---

## 3. Storage Footprint Breakdown
- **Base APK Size**: **20.41 MB**
- **Installed Application Footprint**: 42.8 MB
- **Local SQLite Knowledge Graph (ICAR / CIBRC 547 leaflets)**: 18.4 MB
- **Offline SLM Model (Optional Download)**:
  - AgriParam Q4: 1.15 GB
  - Gemma 4 E2B Q4: 1.28 GB
- **Total Minimum Offline Storage Required**: **61.2 MB** (without SLM) / **1.21 GB** (with SLM).
