import pytest
from app.safety.pesticide_validator import PesticideValidator, SafetyDecision
from app.safety.safety_gate import SafetyGate

@pytest.fixture
def validator():
    return PesticideValidator()

@pytest.fixture
def safety_gate():
    return SafetyGate()

# ==============================================================================
# 1. Tomato Early Blight Standing Crop Foliar Verifications
# ==============================================================================

def test_tomato_early_blight_dithane_foliar_approved(validator):
    decision = validator.validate_pesticide_use(
        crop="Tomato",
        pest_or_disease="Early Blight",
        product_or_chemical="Dithane M-45",
        application_method="foliar_spray",
        growth_stage="standing_crop",
        plant_part="leaf"
    )
    assert decision.is_safe is True
    assert decision.needs_escalation is False
    assert decision.approved_product is not None
    assert decision.approved_product["product_trade_name"] == "Dithane M-45"
    assert "Mancozeb 75% WP" in decision.approved_product["active_ingredient"]
    assert "600-800" in decision.approved_product["dosage_per_acre"] or "600" in decision.approved_product["dosage_per_acre"]
    assert "30-40 g" in decision.dosage_per_15l_knapsack or "30.0 - 40.0" in decision.dosage_per_15l_knapsack
    assert decision.approved_product["pre_harvest_interval_days"] in [5, 7]

def test_tomato_early_blight_amistar_top_foliar_approved(validator):
    decision = validator.validate_pesticide_use(
        crop="Tomato",
        pest_or_disease="Early Blight",
        product_or_chemical="Amistar Top",
        application_method="foliar_spray",
        plant_part="leaf"
    )
    assert decision.is_safe is True
    assert decision.needs_escalation is False
    assert decision.approved_product is not None
    assert decision.approved_product["product_trade_name"] == "Amistar Top"
    assert "Azoxystrobin" in decision.approved_product["active_ingredient"]
    assert "Difenoconazole" in decision.approved_product["active_ingredient"]
    assert "200 ml" in decision.approved_product["dosage_per_acre"]
    assert "15 ml" in decision.dosage_per_15l_knapsack
    assert decision.approved_product["pre_harvest_interval_days"] == 5

def test_tomato_early_blight_blitox_foliar_approved(validator):
    decision = validator.validate_pesticide_use(
        crop="Tomato",
        pest_or_disease="Early Blight",
        product_or_chemical="Blitox 50% WP",
        application_method="foliar_spray",
        growth_stage="standing_crop",
        plant_part="leaf"
    )
    assert decision.is_safe is True
    assert decision.needs_escalation is False
    assert decision.approved_product is not None
    assert "Copper Oxychloride" in decision.approved_product["active_ingredient"]
    assert "1000 g" in decision.approved_product["dosage_per_acre"]
    assert "50 g" in decision.dosage_per_15l_knapsack or "37.5" in decision.dosage_per_15l_knapsack

def test_tomato_early_blight_seed_treatment_refused_on_foliage(validator):
    """
    Seed treatment chemicals (Thiram, Carbendazim seed dressing) must NEVER be
    recommended or approved as foliar sprays on standing tomato crops.
    """
    decision = validator.validate_pesticide_use(
        crop="Tomato",
        pest_or_disease="Early Blight",
        product_or_chemical="Thiram",
        application_method="foliar_spray",
        growth_stage="standing_crop",
        plant_part="leaf"
    )
    assert decision.is_safe is False
    assert decision.needs_escalation is True
    assert decision.off_label_detected is True
    assert decision.off_label_method_detected is True
    assert "Off-label" in decision.escalation_reason
    assert any("seed" in w.lower() for w in decision.warnings)

# ==============================================================================
# 2. Cross-Application-Method Violations (Foliar vs Seed vs Sett Treatments)
# ==============================================================================

def test_sugarcane_red_rot_sett_dip_approved_but_foliar_spray_refused(validator):
    # Case A: Sett treatment query -> APPROVED with sett dip rate & knapsack refusal
    sett_decision = validator.validate_pesticide_use(
        crop="Sugarcane",
        pest_or_disease="Red Rot",
        product_or_chemical="Bavistin",
        application_method="sett_treatment",
        growth_stage="pre_sowing"
    )
    assert sett_decision.is_safe is True
    assert sett_decision.approved_product["product_trade_name"] == "Bavistin 50% WP"
    assert "Sett dipping" in sett_decision.approved_product["application_method"]
    assert "Sett dip rate" in sett_decision.dosage_per_15l_knapsack or "Refuse" in sett_decision.dosage_per_15l_knapsack

    # Case B: Farmer asks to spray Bavistin on standing sugarcane leaves for red rot -> REFUSED
    foliar_decision = validator.validate_pesticide_use(
        crop="Sugarcane",
        pest_or_disease="Red Rot",
        product_or_chemical="Bavistin",
        application_method="foliar_spray",
        growth_stage="standing_crop",
        plant_part="leaf"
    )
    assert foliar_decision.is_safe is False
    assert foliar_decision.needs_escalation is True
    assert foliar_decision.off_label_detected is True
    assert foliar_decision.off_label_method_detected is True
    assert "Off-label application method violation" in foliar_decision.escalation_reason
    assert "sett_treatment" in foliar_decision.escalation_reason or "sett" in foliar_decision.escalation_reason

def test_cotton_bacterial_blight_foliar_vs_seed_treatment(validator):
    # Case A: Standing crop foliar spray with Copper Oxychloride + Streptocycline -> APPROVED
    foliar_decision = validator.validate_pesticide_use(
        crop="Cotton",
        pest_or_disease="Bacterial Blight",
        product_or_chemical="Blitox 50% WP + Streptocycline",
        application_method="foliar_spray",
        plant_part="leaf"
    )
    assert foliar_decision.is_safe is True
    assert foliar_decision.needs_escalation is False
    assert "1000 g" in foliar_decision.approved_product["dosage_per_acre"]
    assert "50 g" in foliar_decision.dosage_per_15l_knapsack

    # Case B: Seed treatment formulation (Carboxin + Thiram DS) requested as foliar spray -> REFUSED
    cross_decision = validator.validate_pesticide_use(
        crop="Cotton",
        pest_or_disease="Bacterial Blight",
        product_or_chemical="Carboxin 37.5% + Thiram 37.5% DS",
        application_method="foliar_spray",
        plant_part="leaf"
    )
    assert cross_decision.is_safe is False
    assert cross_decision.needs_escalation is True
    assert cross_decision.off_label_detected is True
    assert cross_decision.off_label_method_detected is True
    assert "Off-label application method violation" in cross_decision.escalation_reason

# ==============================================================================
# 3. Pre-sowing Seed Treatment Queries
# ==============================================================================

def test_chickpea_wilt_seed_treatment_carbendazim(validator):
    decision = validator.validate_pesticide_use(
        crop="Chickpea",
        pest_or_disease="Fusarium Wilt",
        product_or_chemical="Bavistin",
        application_method="seed_treatment",
        growth_stage="pre_sowing"
    )
    assert decision.is_safe is True
    assert decision.needs_escalation is False
    assert decision.approved_product["product_trade_name"] == "Bavistin 50% WP"
    assert "2 g/kg seed" in decision.approved_product["dosage_per_acre"]
    assert "Refuse knapsack conversion" in decision.dosage_per_15l_knapsack

def test_chickpea_wilt_seed_treatment_thiram(validator):
    decision = validator.validate_pesticide_use(
        crop="Chickpea",
        pest_or_disease="Fusarium Wilt",
        product_or_chemical="Thiram",
        application_method="seed_treatment"
    )
    assert decision.is_safe is True
    assert decision.needs_escalation is False
    assert "Thiram" in decision.approved_product["product_trade_name"]
    assert "2-3 g/kg seed" in decision.approved_product["dosage_per_acre"]
    assert "Refuse knapsack conversion" in decision.dosage_per_15l_knapsack

def test_chickpea_seed_treatment_refused_as_foliar_spray(validator):
    """
    Attempting to spray seed treatment Thiram on standing chickpea leaves must be rejected.
    """
    decision = validator.validate_pesticide_use(
        crop="Chickpea",
        pest_or_disease="Fusarium Wilt",
        product_or_chemical="Thiram",
        application_method="foliar_spray",
        plant_part="leaf"
    )
    assert decision.is_safe is False
    assert decision.needs_escalation is True
    assert decision.off_label_detected is True
    assert decision.off_label_method_detected is True

# ==============================================================================
# 4. SafetyGate Integration & Natural Query Understanding
# ==============================================================================

def test_safety_gate_tomato_early_blight_spray_query(safety_gate):
    decision, meta = safety_gate.evaluate_query(
        query_text="My tomato leaves have concentric dark brown spots early blight. What pesticide spray dose should I use?",
        crop="Tomato",
        pest_or_disease="Early Blight",
        plant_part="leaf"
    )
    assert decision.is_safe is True
    assert decision.needs_escalation is False
    assert decision.approved_product is not None
    assert decision.application_method == "foliar_spray"
    assert any(prod in decision.approved_product["product_trade_name"] for prod in ["Dithane", "Amistar", "Blitox", "Ridomil"])

def test_safety_gate_seed_treatment_telugu_query(safety_gate):
    decision, meta = safety_gate.evaluate_query(
        query_text="శనగ పంట విత్తే ముందు విత్తన శుద్ధి మందు మోతాదు ఎంత?",
        crop="Chickpea"
    )
    assert decision.is_safe is True
    assert decision.approved_product is not None
    assert decision.application_method == "seed_treatment"
    assert "Refuse knapsack conversion" in decision.dosage_per_15l_knapsack

def test_safety_gate_seed_chemical_refused_on_foliar_query(safety_gate):
    decision, meta = safety_gate.evaluate_query(
        query_text="చెరకు ఎర్ర కుళ్ళు తెగులు నివారణకు ఆకులపై బావిస్టిన్ స్ప్రే చేయవచ్చా?",
        crop="Sugarcane",
        pest_or_disease="Red Rot",
        chemical="Bavistin",
        plant_part="leaf"
    )
    assert decision.is_safe is False
    assert decision.needs_escalation is True
    assert decision.off_label_method_detected is True
    assert "Off-label application method violation" in decision.escalation_reason

def test_safety_gate_no_approved_chemical_escalates_to_officer(safety_gate):
    """
    If no approved chemical is registered for a specific pest/disease on that crop,
    explicitly state that no CIBRC chemical is registered and escalate to Agricultural Officer.
    """
    decision, meta = safety_gate.evaluate_query(
        query_text="What chemical spray can cure tomato viral leaf curl disease completely?",
        crop="Tomato",
        pest_or_disease="Leaf Curl Virus"
    )
    assert decision.is_safe is False
    assert decision.needs_escalation is True
    assert "No CIBRC approved" in decision.escalation_reason
