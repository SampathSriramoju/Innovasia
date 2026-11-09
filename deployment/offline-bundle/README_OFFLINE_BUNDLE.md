# Bol Kisan — Fully Air-Gapped Offline AI Bundle

This directory provides the complete distribution bundle for deploying Bol Kisan in field locations with zero or intermittent internet connectivity.

---

## 1. Architecture Overview

Bol Kisan features a dual-layer edge intelligence engine:
1. **Google LiteRT-LM Neural Engine**: Executes genuine on-device quantized neural inference (`Qwen3-0.6B Mobile SLM`) via CPU multi-threading or GPU acceleration.
2. **Deterministic SQLite FTS5 RAG Engine**: Contains verified ICAR / CIBRC crop guides and dosage rules, providing instant (<5ms) sub-second responses and safe fallback when devices are memory-constrained (<4GB RAM) or when the SLM weights are not yet provisioned.

---

## 2. Hardware Requirements

- **Operating System**: Android 8.0 (API Level 26) or newer (tested on Android 12, 13, 14, 15).
- **RAM Thresholds**:
  - **≥ 4GB Total RAM**: Full neural LiteRT-LM SLM generation + SQLite grounding.
  - **< 4GB Total RAM**: Safe Room SQLite FTS5 Knowledge Engine (guarantees zero OOM crashes on budget farmer smartphones).
- **Storage**: ~1.0 GB free internal storage (585.3 MB for `Qwen3-0.6B.litertlm` model artifact).

---

## 3. Bundle Contents

| File | Description |
|---|---|
| `app-debug.apk` | Bol Kisan Android Application (Launcher, Voice AI, Crop Vision, Room DB) |
| `model_manifest.json` | Pinned model metadata, Hugging Face URLs, and SHA-256 checksums |
| `download_model.py` | Official Python script to fetch and cryptographically verify model weights |
| `install_offline_bundle.ps1` | Automated deployment script for Windows ADB |
| `install_offline_bundle.sh` | Automated deployment script for macOS / Linux ADB |
| `README_OFFLINE_BUNDLE.md` | This technical guide |

---

## 4. Deployment Methods

### Method A: Automated Field Installer (Recommended for Extension Officers)

1. Connect the farmer's smartphone via USB with **USB Debugging** enabled.
2. Download the model weights once onto your laptop:
   ```bash
   python download_model.py
   ```
3. Run the installer:
   - **Windows PowerShell**:
     ```powershell
     .\install_offline_bundle.ps1
     ```
   - **macOS / Linux**:
     ```bash
     chmod +x install_offline_bundle.sh
     ./install_offline_bundle.sh
     ```
4. The installer automatically installs `app-debug.apk`, pushes `qwen3_0_6b.litertlm` to `/sdcard/Download/`, verifies the SHA-256 hash, and launches the app.

---

### Method B: In-App One-Click Download (Wi-Fi Provisioning)

1. Install `app-debug.apk` on the phone.
2. Open Bol Kisan and tap **ఆఫ్‌లైన్ లైబ్రరీ / Offline Library**.
3. Under the **Subsystems Dashboard**, tap **Download Model (HF)**.
4. The app downloads the genuine model directly from Hugging Face with byte-by-byte SHA-256 verification and atomic commit.

---

### Method C: Sideloading via SD Card / USB OTG / WhatsApp

1. Copy the downloaded `qwen3_0_6b.litertlm` file onto an SD card or USB flash drive.
2. Transfer it to the phone's **Downloads** folder or any accessible folder.
3. In Bol Kisan, open **Offline Library** and tap **Import Model File**.
4. Select the `.litertlm` file. The app validates the format, copies it into private app storage, and initializes the LiteRT-LM runtime.

---

## 5. Subsystems Status Dashboard

Open the **Offline Library** screen to inspect real-time subsystem states:
- **Neural SLM Engine**: Displays model name (`Qwen3-0.6B Mobile Edge SLM`), parameter count (`0.6B`), and quantization (`Dynamic INT8`).
- **Edge Runtime**: Confirms LiteRT-LM 0.19.1 execution status (`Active (LITERT_LM_CPU)` or `LITERT_LM_GPU`).
- **Hardware RAM**: Displays physical device RAM and available free RAM.
- **Storage**: Confirms exact disk consumption of the model file.
- **Test Offline AI**: Runs a real inference query on-device, reporting live token generation speed (tokens/sec), first-token latency, and total latency.
