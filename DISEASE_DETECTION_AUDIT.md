# BOL KISAN – CROP DISEASE & PEST DETECTION AUDIT REPORT
**Document Version:** 1.0.0 (Phase 1 Audit)  
**Date:** November 8, 2026  
**Auditor:** Computer Vision Lead & Agricultural AI Systems Auditor  
**Scope:** Backend endpoints, model inference pipeline, image preprocessing, database schemas, Android CameraX integration, and evaluation suites.

---

## 1. Executive Summary

Bol Kisan's vision diagnosis capability was audited to evaluate its readiness for real-world farmer crop pathology workflows. Our audit reveals a critical architectural gap:

> [!WARNING]
> **Key Audit Finding:** The existing endpoint `/api/v1/disease/diagnose` in [`backend/app/api/v1/endpoints/disease.py`](file:///c:/Users/Sampath%20Sriramoju/OneDrive/Desktop/Hacakthon/innovasia/backend/app/api/v1/endpoints/disease.py) does **not** perform actual computer vision inference on image pixels. Instead, it extracts string patterns from `file.filename` and `farmer_notes` (e.g., checking if the string `"leaf spot"` or `"blast"` is present) and returns hardcoded confidence numbers (0.94, 0.92, 0.89). When a farmer captures a real photo on an Android phone (typically named `IMG_20261108_120401.jpg`) without explicit diagnostic notes, the system assigns a default confidence of 0.60 and auto-escalates to an agricultural officer.

This audit establishes the baseline facts, catalogs existing database structures, evaluates Android CameraX wiring, and specifies the necessary upgrades to build a genuine, reliable computer vision diagnostic pipeline.

---

## 2. Current Implementation Audit

### 2.1. Backend Endpoint (`backend/app/api/v1/endpoints/disease.py`)

| Component | Current Implementation | Audit Verdict | Identified Defect / Gap |
| :--- | :--- | :---: | :--- |
| **API Route** | `POST /api/v1/disease/diagnose` | **WORKING** | Accepts `file: UploadFile`, `crop_name: Form`, `farmer_notes: Form`, `lat/lon`. |
| **Image Sanitization** | `sanitize_image_bytes()` strips JPEG APP1 (EXIF) & PNG text chunks | **VERIFIED** | Successfully removes GPS EXIF metadata to preserve farmer privacy. |
| **Pixel Inference** | None (String heuristic on filename + farmer notes) | **DEFICIENT** | Pixel data is written to disk but never fed into a neural network or feature extractor. |
| **Supported Categories** | 6 hardcoded keys in `DISEASE_CATALOG`: `leaf_spot`, `blast`, `blight`, `chlorosis`, `mite_webbing`, `leaf_curl` | **LIMITED** | Narrow crop-disease coverage; lacks Telangana priority crops (Turmeric, Groundnut, Maize, Mango). |
| **Confidence Scoring** | Hardcoded constants: 0.94 (leaf spot), 0.92 (blast), 0.89 (blight), 0.86 (chlorosis), 0.91 (mites), 0.88 (curl), 0.55 (ambiguous), 0.60 (unknown) | **FABRICATED** | No calibrated probability or softmax distribution derived from image pixels. |
| **Quality Validation** | Validates magic bytes (JPEG/PNG) and minimum 16 bytes | **DEFICIENT** | No blur detection, no lighting checks, no out-of-distribution (non-leaf) detection. |
| **Auto-Escalation** | Threshold `< 0.80` creates `AgriculturalTicket` in PostgreSQL | **WORKING** | Correctly creates tickets with attached image URLs and routes to officers. |
| **SafetyGate & IPM** | Fetches non-chemical remedies and checks CIBRC pesticide labels | **WORKING** | Strictly enforces approved CIBRC dosages when chemical treatment is requested. |

### 2.2. Android CameraX Integration (`DiseaseCameraScreen.kt`)

| Mobile Feature | Implementation in Kotlin | Audit Verdict | Notes |
| :--- | :--- | :---: | :--- |
| **CameraX Pipeline** | `ProcessCameraProvider`, `PreviewView`, `ImageCapture` | **WORKING** | Implemented using modern CameraX Jetpack lifecycle libraries. |
| **Hardware Emulation** | `createSampleFallbackLeafFile` renders synthetic leaf on Canvas | **WORKING** | Enables automated testing on emulators without physical camera sensors. |
| **Permission Handling** | `ActivityResultContracts.RequestPermission(CAMERA)` | **WORKING** | Gracefully handles runtime permission grants and denials. |
| **Plant Part Selection** | Not implemented | **MISSING** | Farmer cannot designate whether the photograph captures a leaf, stem, fruit, or root. |
| **Voice Note Recording** | Not implemented on camera screen | **MISSING** | Farmer must type notes rather than recording a spoken description. |
| **Pre-Upload Quality Check**| Not implemented | **MISSING** | Does not alert the farmer to blur or poor lighting before submitting the upload. |
| **Offline Vision Inference**| Not implemented on mobile | **MISSING** | Only calls remote REST API; has no local TFLite or ONNX runtime integration. |

### 2.3. Database Infrastructure (`models.py` & PostgreSQL)

The relational database already has structured tables populated with verified agricultural data:
1. **`crops` (16 active rows):** Cotton, Rice, Chilli, Tomato, Wheat, Maize, Groundnut, Soybean, Red Gram, Chickpea, Mustard, Sugarcane, Onion, Potato, Turmeric, Black Gram.
2. **`diseases` (32 verified rows):** Covers Rice Blast, Bacterial Blight, Early Blight, Sheath Blight, Bacterial Leaf Blight, Grey Mildew, Root Rot, Anthracnose, Powdery Mildew, Chilli Leaf Curl, etc.
3. **`pests` (36 verified rows):** Covers Pink Bollworm, Brown Plant Hopper, Fall Armyworm, Chilli Thrips, Whitefly, Stem Borer, Fruit Borer, Spider Mites, Aphids, etc.
4. **`approved_pesticide_uses` & `pesticide_labels` (37 rows):** Verified CIBRC registered dosages and pre-harvest intervals.
5. **`knowledge_chunks` (562 rows):** Dense multilingual semantic vectors for RAG retrieval across ICAR and PJTSAU guidelines.

---

## 3. Supported Crops & Disease Classes (Existing vs Required)

| Crop | Current Backend Coverage | Knowledge Base Status | Required Telangana Production Coverage |
| :--- | :--- | :--- | :--- |
| **Rice / Paddy** | Blast (`blast`) | 5 verified diseases (Blast, Sheath Blight, BLB, False Smut, Brown Spot) | Leaf Blast, Neck Blast, Sheath Blight, Bacterial Leaf Blight, Brown Plant Hopper damage, Stem Borer deadheart. |
| **Cotton** | Bacterial Blight (`leaf_spot`), Chlorosis | 4 verified diseases (Bacterial Blight, Grey Mildew, Root Rot, Alternaria Leaf Spot) | Bacterial Blight, Grey Mildew, Pink Bollworm entry holes, Sucking pest leaf curling (Thrips/Aphids), Magnesium deficiency. |
| **Chilli** | Spider Mite (`mite_webbing`), Leaf Curl (`leaf_curl`) | 4 verified diseases (Anthracnose/Fruit Rot, Powdery Mildew, Leaf Curl, Choanephora Blight) | Chilli Leaf Curl (Gemini virus), Black Thrips scarring, Anthracnose ripe fruit rot, Cercospora leaf spot, Powdery Mildew. |
| **Tomato** | Early Blight (`blight`) | 3 verified diseases (Early Blight, Late Blight, Tomato Leaf Curl) | Early Blight (Alternaria), Late Blight, Leaf Curl, Fruit Borer damage, Calcium deficiency (Blossom End Rot). |
| **Maize** | None | 2 verified diseases (Turcicum Leaf Blight, Maydis Blight) | Fall Armyworm (whorl damage & frass), Turcicum Leaf Blight, Banded Leaf and Sheath Blight, Nitrogen deficiency. |
| **Turmeric** | None | 2 verified diseases (Colletotrichum Leaf Spot, Taphrina Leaf Blotch) | Leaf Spot, Leaf Blotch, Rhizome Rot (Pythium). |
| **Groundnut** | None | 2 verified diseases (Tikka Leaf Spot, Rust) | Early Tikka, Late Tikka Leaf Spot, Rust, Collar Rot, Iron chlorosis. |
| **Mango** | None | 2 verified diseases (Anthracnose, Powdery Mildew) | Anthracnose, Powdery Mildew, Mango Hopper damage, Malformation. |

---

## 4. Current Model Weights, Provenance, and Training Dataset Status

- **Model Weights File:** None present in the repository. No `.pth`, `.pt`, `.onnx`, `.tflite`, or `.h5` file exists.
- **Training Dataset:** None. The backend heuristic string matcher relies entirely on regular expressions.
- **Reported Accuracy:** Previous synthetic unit tests reported a "94% pass rate", but this measured only string keyword matching (`test_disease_api.py`), not computer vision accuracy.
- **True Computer Vision Accuracy:** **0.0%** (non-existent, as pixel data was not evaluated).

---

## 5. Architectural Upgrade Roadmap

To build a genuinely reliable diagnostic system, the following upgrades are required:

```
[Farmer Captures Image via CameraX / Gallery]
                      │
                      ▼
[Pre-Diagnostic Quality & Validation Gate]
  ├── Blur Detection (Laplacian Variance σ² ≥ 100)
  ├── Exposure / Lighting Check (40 ≤ Mean Brightness ≤ 220)
  └── Out-of-Distribution / Non-Crop Detection (Green Foliage Index & HSV masking)
                      │ (If Poor Quality / Blurry) ──> Re-capture Prompt with Guidance
                      ▼ (If Image Valid)
[Computer Vision Feature Extraction & Inference]
  ├── MobileNetV3-Small / EfficientNet-Lite Backbone (3.2 MB ONNX / PyTorch)
  ├── Multi-Crop Feature Extraction across 8 Telangana Crops
  └── Calibrated Confidence Handling (Temperature Scaled Softmax)
                      │
                      ▼
[Symptom-Based Differential Diagnostician]
  ├── Combines Vision Signatures + Farmer Plant Part + Growth Stage
  └── Differentiates: Fungal vs Bacterial vs Viral vs Pest vs Nutrient vs Abiotic
                      │
         ┌────────────┴────────────┐
         ▼                         ▼
 [High Confidence (≥ 0.80)]  [Medium/Low Confidence (< 0.80) or Ambiguous]
         │                         │
         ▼                         ▼
[RAG Knowledge Grounding]     [Auto-Escalation Ticket to Mandal Officer]
  ├── Non-Chemical / Cultural      ├── Original Sanitized Image
  ├── Biological IPM Remedies      ├── Crop, District, Growth Stage
  └── CIBRC Statutory Formulations ├── Tentative Predictions & Differential Ambiguity
                                   └── Response Delivered into Farmer Chat Thread
```

This audit confirms that Bol Kisan possesses solid database foundations and CameraX client infrastructure, but urgently requires a real computer vision inference pipeline, image quality gates, and symptom-based differential diagnosis.
