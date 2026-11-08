# BOL KISAN – CIBRC Pesticide Safety & Agrochemical Compliance

## 1. Regulatory Context & Legal Mandates

Under the **Insecticides Act, 1968** and statutory directives from the **Central Insecticides Board & Registration Committee (CIBRC)**, recommending unauthorized pesticide formulations, incorrect application dosages, unapproved tank mixtures, or off-label use is a cognizable legal offence that poses severe threats to human health and environmental biosecurity.

BOL KISAN implements the **Zero LLM Discretion Principle**:
> *"Language models are strictly forbidden from generating, interpolating, or predicting chemical dosages or pesticide treatments from statistical weights. All agrochemical advice is deterministically queried from verified CIBRC label records."*

---

## 2. The 8 Mandatory CIBRC Safety Parameters

Every approved chemical recommendation must contain all eight mandatory statutory fields:

1. **Approved Target Crop**: Authorized crop under CIBRC label claim.
2. **Target Pest / Pathogen**: Target species with confirmed bio-efficacy trials.
3. **Product Trade Name & Active Ingredient**: Commercial trade name alongside generic active ingredient formulation (e.g. *Emamectin benzoate 5% SG*).
4. **Dosage per Acre & Hectare**: Official metric application rate.
5. **Dilution Water Volume**: Required water carrier volume (e.g., 150-200 L per acre).
6. **Knapsack Sprayer Proportion**: Exact calculation for a standard 15-liter manual knapsack sprayer.
7. **Pre-Harvest Interval (PHI)**: Mandatory statutory waiting period in days before harvesting.
8. **Toxicity Band & Safety Precautions**: CIBRC toxicity color code (Red, Yellow, Blue, Green) and protective equipment mandates.

---

## 3. Threat Interception Gates

```
               [Incoming Farmer Voice Query]
                             │
                             ▼
              ┌──────────────────────────────┐
              │ Banned Chemical Blacklist?   │
              └──────────────────────────────┘
                    │ YES                   │ NO
                    ▼                       ▼
            [CRITICAL ESCALATION]    ┌──────────────────────────────┐
            Immediate Ticket to MAO  │ Unapproved Tank Mix Cocktail?│
                                     └──────────────────────────────┘
                                           │ YES                   │ NO
                                           ▼                       ▼
                                   [HIGH RISK ESCALATION]   ┌──────────────────────────────┐
                                   Prohibited Cocktail Gate │ Valid CIBRC Registered Match?│
                                                            └──────────────────────────────┘
                                                                  │ NO                    │ YES
                                                                  ▼                       ▼
                                                          [MAO ESCALATION]       [VERIFIED CIBRC LABEL]
                                                          Off-label / Unknown    Prescription Output
```

### 3.1 Banned Agrochemical Blacklist
The system immediately intercepts and refuses to process any request involving substances banned or severely restricted in India:
- **Endosulfan**: Total nationwide ban (Supreme Court W.P. 213/2011).
- **Monocrotophos**: Strictly banned on all vegetables (CIBRC Statutory Order).
- **Paraquat Dichloride**: Prohibited for foliar use on vegetables and uncertified food crops (No known antidote).
- **Methyl Parathion, Phorate, Phosphamidon, Diazinon, Methomyl**: Banned organophosphates and carbamates.

### 3.2 Prohibited Tank Mixtures (Pesticide Cocktails)
Farmers frequently mix separate commercial insecticides, fungicides, and micronutrients in a single tank. CIBRC regulations prohibit uncertified tank mixing due to risks of:
- **Phytotoxicity**: Chemical scorch killing the standing crop.
- **Antagonism**: Inactivating the active ingredients.
- **Toxic Vapors**: Endangering the farmer during application.
When cocktail phrases are detected, the system immediately halts and escalates the query.

### 3.3 Agricultural Extension Officer (MAO) Escalation SLA
Whenever a query is escalated:
1. An official ticket is created in PostgreSQL (`AgriculturalTicket` with a unique ID `ESC-YYYYMMDD-XXXXXX`).
2. The ticket appears in real time on the **Officer Dashboard** (`apps/officer-dashboard` at port 5173).
3. Priority SLAs:
   - **Emergency / High Priority** (Banned chemical or acute intoxication query): **4-Hour SLA**.
   - **Standard Escalation** (Unverified crop/pest combination): **24-Hour SLA**.
4. The farmer receives an immediate localized audio/text notification explaining why the query was escalated, assuring them that their Mandal Agricultural Officer is investigating.
