# BOL KISAN – Agricultural Safety & CIBRC Compliance Report

**Lead Safety Auditor:** Antigravity Agricultural Safety Engineering Team  
**Statutory Standards:** Central Insecticides Board & Registration Committee (CIBRC), Insecticides Act 1968, Supreme Court of India Orders, Ministry of Agriculture & Farmers Welfare Notifications.

---

## 1. Safety Principles & Architecture

Bol Kisan adheres to the strict principle that **No Generative AI Model may invent or calculate agrochemical dosages**. Agrochemical recommendations are governed by a deterministic, hard-coded safety gate implemented in `backend/app/ai/pesticide_validator.py`.

```
                    [ Farmer Voice / Text Query ]
                                  │
                                  ▼
                     [ Deterministic Safety Gate ]
                     ├─ Is banned chemical mentioned? ─────────► [ CRITICAL ESCALATION ]
                     ├─ Is chemical off-label or restricted? ──► [ CRITICAL ESCALATION ]
                     ├─ Is unapproved tank-mix requested? ─────► [ ESCALATION ]
                     └─ Is crop or pest ambiguous? ────────────► [ ESCALATION ]
                                  │
                                  ▼
                   [ Exact CIBRC Label DB Match ]
                     ├─ Brand / Active Ingredient Match
                     ├─ Official Acre Dose + 15L Knapsack Pump Conversion
                     ├─ Pre-Harvest Interval (PHI) + PPE Warnings
                     └─ Verified Statutory Citations (CIBRC/ICAR/PJTSAU)
```

---

## 2. Banned Agrochemical Blacklist Audit

The following 9 chemicals are explicitly blacklisted and trigger immediate critical officer escalation:

| Agrochemical Name | Commercial Trade Names | Statutory Status & Legal Basis | System Reaction |
| :--- | :--- | :--- | :--- |
| **Endosulfan** | Thiodan, Endocel | Completely banned by Supreme Court of India (W.P. No. 213/2011) | **BLOCKED & CRITICAL TICKET** |
| **Methyl Parathion** | Metacid, Folidol | Banned under Insecticides Act 1968 / CIBRC Notification | **BLOCKED & CRITICAL TICKET** |
| **Phorate** | Thimet 10G | Highly restricted / Phase-out notification | **BLOCKED & CRITICAL TICKET** |
| **Phosphamidon** | Dimecron | Phase-out notification due to acute mammalian toxicity | **BLOCKED & CRITICAL TICKET** |
| **DDT** | Chlorophenothane | Banned for agricultural use | **BLOCKED & CRITICAL TICKET** |
| **Aldicarb** | Temik | Banned due to severe groundwater toxicity | **BLOCKED & CRITICAL TICKET** |
| **Dichlorvos (DDVP)** | Nuvan, Vapona | Severe restriction / High hazard profile | **BLOCKED & CRITICAL TICKET** |
| **Monocrotophos (on Veg)**| Nuvacron, Monocil | Banned on all vegetable crops (Order S.O. 3012(E)) | **BLOCKED & CRITICAL TICKET** |
| **Paraquat Dichloride** | Gramoxone | Highly restricted herbicide; off-label spray banned | **BLOCKED & CRITICAL TICKET** |

---

## 3. Approved Agrochemical Dosages & Knapsack Dilution

All approved chemical recommendations in Bol Kisan provide both the **official per-acre dose** and the **exact 15-litre knapsack backpack dilution**:

| Crop | Target Pest / Disease | CIBRC Approved Formulation | Per-Acre Dose | 15L Knapsack Dilution | Mandatory PHI (Days) |
| :--- | :--- | :--- | :---: | :---: | :---: |
| **Cotton** | Pink Bollworm | Emamectin Benzoate 5% SG (Proclaim) | 88 g | **6.6 g** / 15L | 7 days |
| **Cotton** | Whitefly | Flonicamid 50% WG (Ulala) | 60 g | **4.5 g** / 15L | 15 days |
| **Cotton** | Alternaria Leaf Spot | Mancozeb 75% WP (Dithane M-45) | 400 g | **30.0 g** / 15L | 10 days |
| **Chilli** | Thrips & Mites | Diafenthiuron 50% WP (Pegasus) | 240 g | **18.0 g** / 15L | 7 days |
| **Chilli** | Fruit Rot / Dieback | Azoxystrobin 18.2% + Difenoconazole 11.4% SC (Amistar Top) | 200 ml | **15.0 ml** / 15L | 5 days |
| **Rice / Paddy** | Brown Plant Hopper (BPH)| Pymetrozine 50% WG (Chess) | 120 g | **9.0 g** / 15L | 19 days |
| **Rice / Paddy** | Rice Blast | Tricyclazole 75% WP (Beam) | 120 g | **9.0 g** / 15L | 30 days |
| **Wheat** | Yellow / Brown Rust | Propiconazole 25% EC (Tilt) | 200 ml | **15.0 ml** / 15L | 30 days |
| **Sugarcane** | Early Shoot Borer | Chlorantraniliprole 18.5% SC (Coragen) | 150 ml | **11.25 ml** / 15L| 21 days |
| **Chickpea** | Gram Pod Borer | Indoxacarb 14.5% SC (Avaunt) | 140 ml | **10.5 ml** / 15L | 14 days |

---

## 4. Tank Mixture & Off-Label Misuse Prevention

1. **Unapproved Tank Mixtures:**
   - Farmers frequently attempt mixing organophosphate insecticides with 2,4-D or glyphosate herbicides.
   - The safety gate detects conjunctions of distinct chemical entities and blocks unapproved mixtures (`test_unapproved_tank_mixture_in_query` and `test_unapproved_tank_mixture_phrasing`).
2. **Off-Label Crop Use:**
   - A chemical registered solely for cotton bollworm cannot be recommended for tomato or leafy vegetables.
   - Cross-crop misuse tests (`test_off_label_use_cotton_chemical_on_tomato` and `test_off_label_use_wheat_fungicide_on_chilli`) verify that the validator flags off-label usage and routes to an agricultural extension officer.
