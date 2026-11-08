# ADR-002: Dual-Tier Deterministic Pesticide Safety Gate

## Status
Accepted

## Context
Pesticide recommendations carry severe biological, legal, environmental, and financial risks. An incorrect dose can cause phytotoxicity (scorching crops), contaminate ground water, cause human poisoning, or violate maximum residue limits (MRLs).

LLMs are prone to:
- Arithmetic errors in unit conversion (ml/acre vs l/ha vs gm/pump).
- Confusing similar chemical trade names (e.g. *Coragen* vs *Curacron*).
- Suggesting banned, unregistered, or off-label pesticides.

## Decision
1. **Zero LLM Discretion**: The LLM is strictly prohibited from generating, calculating, or suggesting any pesticide dosage independently.
2. **Deterministic Pre-Gate**:
   - Queries involving chemicals or pest/disease control are routed through the `pesticide_validator` module prior to any LLM call.
   - The query entities (Crop, Pest/Disease, Product, Active Ingredient) are extracted and queried against the relational `approved_pesticide_uses` table populated from official Central Insecticides Board & Registration Committee (CIBRC) labels.
3. **Mandatory 8-Parameter Verification**:
   - Commercial Trade Name
   - Active Ingredient & Concentration
   - Target Crop
   - Target Pest/Pathogen
   - Formulation Type (EC, SC, WP, SG, etc.)
   - Approved Dosage per Hectare / Acre and per liter of water
   - Pre-Harvest Interval (PHI) in days
   - Toxicity Classification Band (Red / Yellow / Blue / Green) & PPE guidance.
4. **Deterministic Post-Gate**:
   - All generated text is scanned for numerical dosage patterns (e.g., `\d+\s*(ml|gm|kg|liters?)\s*/\s*(acre|hectare|pump|tank)`).
   - Any numerical value not present in the verified CIBRC database record causes an immediate block and triggers an automatic ticket escalation to a regional human extension officer.
5. **Conflict Handling**: If State Agricultural University recommendations conflict with CIBRC label limits, the more restrictive safety rule applies, and a ticket is flagged.

## Consequences
- **Positive**: 100% elimination of hallucinated chemical dosages. Absolute legal and agricultural safety compliance.
- **Negative**: Strict matching requires comprehensive population of CIBRC labels; uncataloged pests/chemicals trigger human escalation.
