# BOL KISAN – Evidence Verification & Source Governance

## 1. Verified Agricultural Knowledge Collections

BOL KISAN indexes 23 verified collections comprising **547 authoritative chunks**. Every chunk is cryptographically fingerprinted using SHA-256 to ensure data provenance and tamper-resistance.

| Collection ID | Domain | Primary Source Organization | Status |
| :--- | :--- | :--- | :---: |
| **KB01** | Crop Cultivation & Sowing Guidelines | ICAR / PJTSAU Agronomy Guide | Active |
| **KB02** | Seed Varieties & Germination Standards | National Seeds Corporation (NSC) | Active |
| **KB03** | Nursery Management & Spacing | Directorate of Rice/Oilseeds Research | Active |
| **KB04** | Plant Pathogens & Disease Diagnosis | ICAR-IARI Crop Pathology Division | Active |
| **KB05** | Integrated Pest Management (IPM) | National Centre for IPM (NCIPM) | Active |
| **KB06** | Biological & Organic Pest Control | ICAR-NBAIR / NIPHM Hyderabad | Active |
| **KB07** | CIBRC Approved Agrochemicals List | Central Insecticides Board & RC (CIBRC) | Active |
| **KB08** | CIBRC Pesticide Safety Labels & PHI | CIBRC National Statutory Database | Active |
| **KB09** | Soil Fertility & Nutrient Deficiencies | ICAR-Indian Institute of Soil Science | Active |
| **KB10** | Fertilizer Dosing & Micronutrients | Dept of Agriculture & Farmers Welfare | Active |
| **KB11** | Irrigation Scheduling & Critical Stages | ICAR-IIWM Bhubaneswar | Active |
| **KB12** | Crop Rotation & Intercropping Systems | CRIDA (Central Research Institute Dryland) | Active |
| **KB13** | Weed Management & Herbicides | ICAR-Directorate of Weed Research | Active |
| **KB14** | Harvesting & Safe Storage Moisture | ICAR-CIPHET Ludhiana | Active |
| **KB15** | Weather Advisories & Agro-Meteorology | India Meteorological Department (IMD) | Active |
| **KB16** | APMC Mandi Prices & MSP Framework | Agmarknet / Ministry of Agriculture | Active |
| **KB17** | Post-Harvest Processing & Milling | ICAR-IIMR / Indian Institute of Food Tech | Active |
| **KB18** | Organic Farming Standards & NPOP | APEDA National Standards for Organic | Active |
| **KB19** | Govt Agricultural Welfare Schemes | Rythu Bandhu / PM-KISAN / PKVY | Active |
| **KB20** | Farm Machinery & Drone Spraying | FMTTI / ICAR-CIAE Bhopal | Active |
| **KB21** | Telangana & AP Regional Agronomy | PJTSAU / ANGRAU Regional Guides | Active |
| **KB22** | Agricultural Extension Officer Manuals | Department of Agriculture Telangana | Active |
| **KB23** | Farmer FAQs & Multilingual Agronomy | ICAR Kisan Portal / Krishi Vigyan Kendra | Active |

---

## 2. Evidence Verification Pipeline

```
[Raw Agricultural Bulletins / CIBRC Gazettes]
                     │
                     ▼
┌──────────────────────────────────────────────┐
│  Chunking & Structural Sanitization          │
│  - Field parameters: Crop, Pest, Dose, PHI   │
└──────────────────────────────────────────────┘
                     │
                     ▼
┌──────────────────────────────────────────────┐
│  SHA-256 Provenance Hashing                  │
│  - Immutable checksum stored in registry     │
└──────────────────────────────────────────────┘
                     │
                     ▼
┌──────────────────────────────────────────────┐
│  Dual-Index Ingestion                        │
│  - Lexical Inverted Index (BM25 Okapi)       │
│  - Semantic Dense Vector Space (Cosine Sim)  │
└──────────────────────────────────────────────┘
```

---

## 3. Mandatory Citation Standards

Every generated response returned to a farmer or extension officer must include explicit citations complying with national guidelines:
- For CIBRC Agrochemicals:
  `[CIBRC Reg: CIR-XXXXX/YYYY-Formulation - Central Insecticides Board]`
- For Agronomic Practices:
  `[KB01 - ICAR/PJTSAU Package of Practices] Maize Sowing Guidelines`
- For Market Information:
  `[KB16 - Agmarknet Warangal APMC] Cotton Modal Rate Assessment`

If a question cannot be mapped to any verified chunk with a similarity score above the safety threshold ($\tau \ge 0.05$), the system **strictly avoids hallucinating** and triggers an extension officer consultation ticket.
