import pytest
from app.safety.pesticide_validator import PesticideValidator, SafetyDecision
from app.safety.safety_gate import SafetyGate

# Seed test records
SAMPLE_CIBRC_RECORDS = [
    {
        "product_trade_name": "Proclaim 5% SG",
        "active_ingredient": "Emamectin Benzoate 5% SG",
        "cibrc_registration_number": "CIR-11425/2014-Emamectin",
        "target_crop": "Cotton",
        "target_pest": "Pink Bollworm",
        "dosage_per_hectare": "220 g",
        "dosage_per_acre": "88 g",
        "dilution_water_liters": "200 liters/acre",
        "pre_harvest_interval_days": 14,
        "safety_precautions": "Wear rubber gloves and mask. Avoid spraying near water bodies."
    },
    {
        "product_trade_name": "Chess 50% WG",
        "active_ingredient": "Pymetrozine 50% WG",
        "cibrc_registration_number": "CIR-22910/2016-Pymetrozine",
        "target_crop": "Rice / Paddy",
        "target_pest": "Brown Plant Hopper (BPH)",
        "dosage_per_hectare": "300 g",
        "dosage_per_acre": "120 g",
        "dilution_water_liters": "200 liters/acre",
        "pre_harvest_interval_days": 19,
        "safety_precautions": "Drain water slightly before spray. Direct spray to plant base."
    }
]

def test_pesticide_validator_exact_match():
    validator = PesticideValidator(seed_records=SAMPLE_CIBRC_RECORDS)
    decision = validator.validate_pesticide_use(
        crop="Cotton",
        pest_or_disease="Pink Bollworm",
        product_or_chemical="Proclaim"
    )

    assert decision.is_safe is True
    assert decision.needs_escalation is False
    assert decision.approved_product is not None
    assert decision.approved_product["dosage_per_acre"] == "88 g"
    assert decision.approved_product["pre_harvest_interval_days"] == 14

def test_pesticide_validator_unregistered_chemical_escalates():
    validator = PesticideValidator(seed_records=SAMPLE_CIBRC_RECORDS)
    # Testing unapproved crop-pest combination
    decision = validator.validate_pesticide_use(
        crop="Wheat",
        pest_or_disease="Locust",
        product_or_chemical="DDT"
    )

    assert decision.is_safe is False
    assert decision.needs_escalation is True
    assert "No CIBRC approved pesticide label found" in decision.escalation_reason

def test_pesticide_validator_ambiguous_query_escalates():
    validator = PesticideValidator(seed_records=SAMPLE_CIBRC_RECORDS)
    decision = validator.validate_pesticide_use(
        crop=None,
        pest_or_disease="some bug"
    )

    assert decision.is_safe is False
    assert decision.needs_escalation is True
    assert "Both crop name and target pest/disease must be specified" in decision.warnings[0]

def test_dosage_hallucination_detection():
    validator = PesticideValidator(seed_records=SAMPLE_CIBRC_RECORDS)
    approved_record = SAMPLE_CIBRC_RECORDS[0] # Cotton - 88 g/acre or 220 g/ha

    # Case A: Accurate text containing exact approved numbers -> PASS
    safe_text = "Please spray 88 g per acre of Proclaim 5% SG diluted in 200 liters/acre."
    assert validator.scan_for_hallucinated_dosages(safe_text, approved_record) is True

    # Case B: LLM hallucinated rogue dosage (e.g. 500 ml per acre) -> FAIL
    hallucinated_text = "You should spray 500 ml per acre for fast results."
    assert validator.scan_for_hallucinated_dosages(hallucinated_text, approved_record) is False

def test_safety_gate_general_agriculture():
    gate = SafetyGate()
    decision, meta = gate.evaluate_query(
        query_text="What is the recommended spacing for sowing cotton in black soil?",
        crop="Cotton"
    )
    assert decision.is_safe is True
    assert decision.needs_escalation is False
    assert meta["is_chemical"] is False
