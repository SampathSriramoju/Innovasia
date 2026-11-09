# Forensic Audit of Offline SLM Asset & Mobile Reality

## 1. Executive Summary & Audit Findings

During our deep inspection of the Bol Kisan Android application repository, we investigated the asset:
`apps/farmer-android/app/src/main/assets/models/agriparam_q4_edge.bin`
accompanied by:
`apps/farmer-android/app/src/main/assets/models/model_manifest.json`

### The Critical Issue:
The manifest and codebase claimed:
- **Model Name:** AgriParam-2.1B-Edge-Q4
- **Parameter Count:** 2,100,000,000 (2.1 Billion parameters)
- **Quantization:** 4-bit integer (INT4)
- **File on Disk:** Exactly **262,144 bytes (256 KB)**

---

## 2. Mathematical Reality of 2.1B Parameters

A 2.1 Billion parameter Large Language Model / Small Language Model has 2,100,000,000 individual weight parameters.

| Representation | Bits per Parameter | Bytes per Parameter | Minimum Theoretical Weight Size |
| :--- | :--- | :--- | :--- |
| **FP32 (Full Precision)** | 32 bits | 4.0 bytes | **8.40 GB** |
| **FP16 / BF16 (Half Precision)** | 16 bits | 2.0 bytes | **4.20 GB** |
| **INT8 (8-bit Quantization)** | 8 bits | 1.0 byte | **2.10 GB** |
| **INT4 (4-bit Quantization)** | 4 bits | 0.5 bytes | **1.05 GB** |
| **INT4 + Group Scales (Group 128)** | ~4.5 bits | ~0.56 bytes | **~1.18 GB – 1.28 GB** |

### Mathematical Proof:
$$2,100,000,000 \times 0.5\text{ bytes} = 1,050,000,000\text{ bytes} \approx 1.05\text{ GB (Raw Weights)}$$
$$\text{With embedding tables, tokenizer, vocab (256K tokens), norm layers} \ge \mathbf{1.28\text{ GB}}$$

**Conclusion:** It is physically, mathematically, and information-theoretically impossible for a 2.1 Billion parameter neural network to fit inside **256 Kilobytes**.
Inspection of the binary file `agriparam_q4_edge.bin` revealed it to be a dummy binary stub consisting of an ASCII header string followed by null-byte zero padding.

---

## 3. Corrective Architecture: Google Gemma 2B LiteRT-LM

To replace this placeholder with genuine, honest engineering, we implemented the following changes:

1. **Purged Placeholder Binary:**
   - Deleted `agriparam_q4_edge.bin` from Android assets (`git rm`).
   - Prevented APK bloat from shipping fake weights in the production bundle.
2. **Updated Model Manifest (`model_manifest.json`):**
   - Model ID: `gemma-2b-it-mobile-q4`
   - Real architecture: Transformer-Decoder (Google Gemma 2B Instruction Tuned)
   - Runtime: Google LiteRT-LM (formerly TensorFlow Lite / MediaPipe GenAI Runtime)
   - Bundle type: `optional_in_app_download` (1.28 GB)
   - Pre-installed: `false`
3. **RAM Profiling & Memory Safety:**
   - Loading a 1.28 GB model into RAM on 3GB Android devices triggers Android's **LowMemoryKiller (LMK)**, crashing the application.
   - Devices with **< 4GB RAM** strictly use verified Room SQLite / FTS5 deterministic RAG.
   - Devices with **≥ 4GB RAM** (recommended 6GB+) are permitted to download and initialize the genuine on-device Gemma SLM.
