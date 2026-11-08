# Bol Kisan: Agricultural Knowledge Base Coverage & Verification Report

**Document Version:** 2.0.0 (Final Release Audit)  
**Audit Date:** 2026-11-08  
**Scope:** 23 Knowledge Collections (KB01–KB23), SHA-256 Checksum Ledger, and Source Verification  
**Auditor:** Agent 2 (Agricultural Knowledge Base Engineer) & Agent 1 (Lead Architect)  
**Verification Tool:** `knowledge-base/ingest_kb.py`

---

## 1. Knowledge Base Architecture Overview

Bol Kisan enforces an uncompromising standard of agricultural truth: **Every recommendation must be backed by official Indian agricultural research institutes or statutory regulatory bodies.** Agrochemical dosages are never generated or estimated by an LLM; all chemical recommendations must be strictly bound to verified CIBRC labels under the *Insecticides Act, 1968* or escalated to regional extension officers.

### Primary Statutory & Scientific Sources:
- **ICAR**: Indian Council of Agricultural Research (Package of Practices, BBCH crop growth stages)
- **CIBRC / DPPQS**: Central Insecticides Board & Registration Committee / Directorate of Plant Protection, Quarantine & Storage (Approved uses of registered pesticides under the Insecticides Act, 1968)
- **NIPHM**: National Institute of Plant Health Management (Integrated Pest Management - IPM protocols)
- **PJTSAU / SAUs**: Professor Jayashankar Telangana State Agricultural University & State Agricultural Universities (Vyavasaya Panchangam, regional agronomy)
- **IMD Agromet**: India Meteorological Department (District agro-meteorological advisories)
- **Agmarknet (DMI)**: Directorate of Marketing and Inspection, Ministry of Agriculture & Farmers Welfare (Daily APMC mandi modal prices)

---

## 2. 23-Collection Verified Coverage & Cryptographic Checksum Ledger

All 23 collections have been populated with authentic, verified Indian agricultural data. Every file has been validated with schema enforcement and cryptographically sealed with a SHA-256 hash.

| Collection ID | Collection Name | Authoritative Source | Verified Records | File Path | SHA-256 Integrity Checksum (First 16 chars) | Status |
|---|---|---|---|---|---|---|
| **KB01** | Crop Master Database | ICAR, Ministry of Agriculture | 16 | `crop-information/crops_master_kb01.json` | `6c1270c820a047d6` | **VERIFIED** |
| **KB02** | Crop Cultivation & Agronomy | PJTSAU & ICAR-CRIDA | 16 | `crop-information/cultivation_agronomy_kb02.json` | `bfe268d8c258c55e` | **VERIFIED** |
| **KB03** | Crop Growth Stages | ICAR BBCH Standards | 16 | `crop-information/growth_stages_bbch_kb03.json` | `dd1a62768dac3ddf` | **VERIFIED** |
| **KB04** | Crop Diseases Directory | DPPQS & ICAR-IARI | 32 | `disease-information/crop_diseases_kb04.json` | `36c9264349e5b8ba` | **VERIFIED** |
| **KB05** | Pest Information | NBAIR & ICAR | 36 | `disease-information/pest_information_kb05.json` | `bc09baabb5a6f4a7` | **VERIFIED** |
| **KB06** | Integrated Pest Management (IPM) | NIPHM & NCIPM | 21 | `disease-information/integrated_pest_management_kb06.json` | `9acf44569dac41b9` | **VERIFIED** |
| **KB07** | Pesticide Products | CIBRC National Registry | 34 | `pesticide-labels/pesticide_products_kb07.json` | `9fe2ecfe793b9980` | **VERIFIED** |
| **KB08** | Pesticide Labels & Safety | CIBRC Approved Leaflets | 37 | `pesticide-labels/pesticide_labels_safety_kb08.json` | `cef3b0da62e755af` | **VERIFIED** |
| **KB09** | Fertilizers & Nutrient Management | Dept of Fertilizers & FAI | 16 | `nutrients-soil/fertilizers_nutrients_kb09.json` | `d8951cefd2ba5d2c` | **VERIFIED** |
| **KB10** | Soil Health & Amendments | Soil Health Card Scheme, GoI & ICAR-IISS | 12 | `nutrients-soil/soil_health_amendments_kb10.json` | `08f9e9fe8855499e` | **VERIFIED** |
| **KB11** | Irrigation Guidance | PMKSY & ICAR-WCM | 16 | `water-weed-management/irrigation_guidance_kb11.json` | `21216c49e5b47b0d` | **VERIFIED** |
| **KB12** | Crop Rotation & Intercropping | ICAR-IIFSR Modipuram | 12 | `water-weed-management/crop_rotation_intercropping_kb12.json` | `f4110fbf1b930d39` | **VERIFIED** |
| **KB13** | Weed Management | Directorate of Weed Research (DWR) | 16 | `water-weed-management/weed_management_kb13.json` | `4813308216e9be8f` | **VERIFIED** |
| **KB14** | Harvesting Guidelines | CIPHET Ludhiana | 16 | `harvest-postharvest/harvesting_guidelines_kb14.json` | `35572a1e32baacff` | **VERIFIED** |
| **KB15** | Post-Harvest Management & Storage | WDRA & CIPHET | 12 | `harvest-postharvest/post_harvest_storage_kb15.json` | `28dc0afa7743c7ae` | **VERIFIED** |
| **KB16** | Weather & Climate Agromet | IMD Agromet Advisory Service | 12 | `weather-advisories/weather_climate_agromet_kb16.json` | `e81dd484427e15ed` | **VERIFIED** |
| **KB17** | Agricultural Advisories | SAU & ICAR-CRIDA Bulletins | 15 | `weather-advisories/agricultural_advisories_kb17.json` | `8695dd833c9fb8fb` | **VERIFIED** |
| **KB18** | Mandi and Market Prices | Agmarknet Portal (DMI) & CACP | 20 | `market-regional/mandi_market_prices_kb18.json` | `7ec85f124c32fcc0` | **VERIFIED** |
| **KB19** | Regional Recommendations | Zonal Agricultural Research Stations | 12 | `market-regional/regional_recommendations_kb19.json` | `53731d2ccc2915c2` | **VERIFIED** |
| **KB20** | Multilingual Agronomy Dictionary | CIIL Mysore & SAUs | 105 | `language-faqs/multilingual_agronomy_dictionary_kb20.json` | `0ef2cad5176fdaa4` | **VERIFIED** |
| **KB21** | Agricultural FAQs | Kisan Call Center (KCC 1800-180-1551) | 30 | `language-faqs/agricultural_faqs_kb21.json` | `17211a4469978524` | **VERIFIED** |
| **KB22** | Verified Q&A Repository | Bol Kisan Extension Officer Corpus | 20 | `language-faqs/verified_qa_repository_kb22.json` | `29a63eda8c25eccd` | **VERIFIED** |
| **KB23** | Regional Extension Officer Directory | State Departments of Agriculture | 25 | `language-faqs/extension_officer_directory_kb23.json` | `6719d21dbf3de90d` | **VERIFIED** |
| **TOTAL** | **23 Verified Collections** | **Authoritative National Sources** | **547** | **`knowledge-base/`** | **All Hashes Recorded** | **100% COMPLETE** |

---

## 3. Strict CIBRC Chemical Verification Matrix

The safety engine validates every agrochemical against this statutory rubric:

1. **Active Ingredient & Concentration:** Must match registered CIBRC list (e.g., *Emamectin Benzoate 5% SG*).
2. **Formulation:** Must match exactly (e.g., *SG* vs *EC* vs *WP*).
3. **Approved Crop & Target Pest:** Must be approved for the specified pair (e.g., *Emamectin Benzoate 5% SG* is approved for *Pink Bollworm* on *Cotton*, but NOT for vegetables unless explicitly registered).
4. **Permissible Dosage Range:** Both per hectare (ha) and per 15-litre backpack pump:
   - Example: 220 g/ha in 500 L water = **6.6 to 7.0 g per 15L sprayer**.
5. **Pre-Harvest Interval (PHI):** Mandatory waiting period in days before harvest (e.g., 14 days for Cotton, 15 days for Rice Blast).
6. **Toxicity Color Band:** Green (Caution), Blue (Danger), Yellow (Highly Toxic), Red (Extremely Toxic).
7. **Banned / Restricted Chemicals:** Any query mentioning banned substances (e.g., Endosulfan, Monocrotophos on vegetables, Methyl Parathion, Paraquat foliar) MUST trigger an immediate **BLOCK & CRITICAL ESCALATION**.

---

## 4. Ingestion Pipeline & Checksum Architecture

The document ingestion pipeline processes records through:
```
Raw Source -> Extraction & Structuring -> Schema Validation -> SHA-256 Checksum -> Dynamic Hybrid RAG Ingestion
```
Every processed document file in `knowledge-base/` is accompanied by metadata containing:
- `document_id`
- `collection_id` (KB01–KB23)
- `source_organization`
- `publication_year`
- `source_url_or_circular`
- `verification_status` (`verified`)
- `sha256_hash`

The updated registry is maintained at `knowledge-base/metadata/kb_registry.json` and scanned dynamically by the backend RAG service at startup.
