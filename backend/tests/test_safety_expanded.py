import pytest
from app.safety.pesticide_validator import PesticideValidator, SafetyDecision
from app.safety.safety_gate import SafetyGate

# Test suite for expanded agricultural safety engine

@pytest.fixture
def safety_gate():
    return SafetyGate()

@pytest.fixture
def validator():
    return PesticideValidator()

# ==============================================================================
# 1. Exact Approved CIBRC Matches Across Multiple Crops
# ==============================================================================

@pytest.mark.parametrize("crop,pest_or_disease,chemical,expected_trade,expected_phi,expected_knapsack_unit", [
    ("Cotton", "Pink Bollworm", "Proclaim", "Proclaim 5% SG", 14, "g"),
    ("Cotton", "Whitefly", "Ulala", "Ulala 50% WG", 20, "g"),
    ("Rice / Paddy", "Brown Plant Hopper", "Chess", "Chess 50% WG", 19, "g"),
    ("Rice", "Rice Blast", "Amistar Top", "Amistar Top", 15, "ml"),
    ("Chilli", "Chilli Thrips", "Pegasus", "Pegasus 50% WP", 7, "g"),
    ("Chilli", "Fruit Borer", "Tracer", "Tracer 45% SC", 3, "ml"),
    ("Tomato", "Early Blight", "Dithane", "Dithane M-45", 5, "g"),
    ("Tomato", "Fruit Borer", "Coragen", "Coragen 18.5% SC", 3, "ml"),
    ("Wheat", "Yellow Rust", "Tilt", "Tilt 25% EC", 30, "ml"),
    ("Wheat", "Aphids", "Actara", "Actara 25% WG", 21, "g"),
    ("Maize", "Fall Armyworm", "Delegate", "Delegate 11.7% SC", 14, "ml"),
    ("Groundnut", "Tikka Disease", "Saaf", "Saaf 75% WP", 30, "g"),
    ("Soybean", "Semilooper", "Fame", "Fame 39.35% SC", 30, "ml"),
    ("Red Gram", "Pod Borer", "Coragen", "Coragen 18.5% SC", 29, "ml"),
    ("Chickpea", "Pod Borer", "Proclaim", "Proclaim 5% SG", 14, "g"),
    ("Mustard", "Mustard Aphid", "Rogor", "Rogor 30% EC", 15, "ml"),
    ("Sugarcane", "Early Shoot Borer", "Coragen", "Coragen 18.5% SC", 45, "ml"),
    ("Sugarcane", "Red Rot", "Bavistin", "Bavistin 50% WP", 30, "g"),
])
def test_exact_approved_cibrc_matches_across_crops(
    validator,
    crop,
    pest_or_disease,
    chemical,
    expected_trade,
    expected_phi,
    expected_knapsack_unit
):
    decision = validator.validate_pesticide_use(
        crop=crop,
        pest_or_disease=pest_or_disease,
        product_or_chemical=chemical
    )

    assert decision.is_safe is True
    assert decision.needs_escalation is False
    assert decision.approved_product is not None
    assert decision.approved_product["product_trade_name"] == expected_trade
    assert decision.approved_product["pre_harvest_interval_days"] == expected_phi
    assert decision.dosage_per_15l_knapsack is not None
    assert any(term in decision.dosage_per_15l_knapsack for term in ["15-litre", "15L", "pump", "knapsack", "refuse"])
    if "refuse" not in decision.dosage_per_15l_knapsack:
        assert expected_knapsack_unit in decision.dosage_per_15l_knapsack
    
    # Check mandatory PPE warnings are included
    assert any("PPE" in w or "Rubber gloves" in w or "Protective" in w for w in decision.warnings)
    # Check PHI is included in warnings
    assert any(f"{expected_phi} days" in w for w in decision.warnings)

def test_crop_aliases_multilingual_matching(validator):
    # Test Telugu crop alias
    decision_te = validator.validate_pesticide_use(
        crop="వరి",
        pest_or_disease="Brown Plant Hopper",
        product_or_chemical="Chess"
    )
    assert decision_te.is_safe is True
    assert decision_te.approved_product["product_trade_name"] == "Chess 50% WG"

    # Test Hindi crop alias
    decision_hi = validator.validate_pesticide_use(
        crop="टमाटर",
        pest_or_disease="Early Blight",
        product_or_chemical="Dithane"
    )
    assert decision_hi.is_safe is True
    assert decision_hi.approved_product["product_trade_name"] == "Dithane M-45"

# ==============================================================================
# 2. Banned & Restricted Agrochemicals Triggering CRITICAL Escalation
# ==============================================================================

@pytest.mark.parametrize("banned_chem,crop,query_text", [
    ("Endosulfan", "Cotton", "Can I spray Endosulfan for bollworm?"),
    ("Thiodan", "Chilli", "I want to spray Thiodan on my crop."),
    ("Methyl Parathion", "Rice", "How much Methyl Parathion to spray?"),
    ("Metacid", "Wheat", "Dose of Metacid for aphids"),
    ("Phorate", "Sugarcane", "Phorate 10g application dosage"),
    ("Phosphamidon", "Mustard", "Can I use Dimecron / Phosphamidon?"),
    ("DDT", "Chickpea", "Spray DDT powder on chickpea"),
    ("Aldicarb", "Soybean", "Use Temik or Aldicarb for soil pests"),
    ("Dichlorvos", "Groundnut", "DDVP or Dichlorvos spray dose"),
])
def test_banned_chemicals_trigger_critical_escalation(safety_gate, banned_chem, crop, query_text):
    decision, meta = safety_gate.evaluate_query(
        query_text=query_text,
        crop=crop,
        chemical=banned_chem
    )

    assert decision.is_safe is False
    assert decision.needs_escalation is True
    assert decision.severity == "CRITICAL"
    assert decision.banned_chemical_detected is not None
    assert "CRITICAL SAFETY VIOLATION" in decision.escalation_reason
    assert "No CIBRC approved pesticide label found" in decision.escalation_reason
    assert any("BANNED" in w or "PROHIBITED" in w for w in decision.warnings)

def test_monocrotophos_banned_on_vegetables_triggers_critical(safety_gate):
    # Test Monocrotophos on Tomato -> CRITICAL VEGETABLE BAN
    decision, meta = safety_gate.evaluate_query(
        query_text="Can I spray Monocrotophos on tomato for caterpillars?",
        crop="Tomato",
        chemical="Monocrotophos"
    )

    assert decision.is_safe is False
    assert decision.needs_escalation is True
    assert decision.severity == "CRITICAL"
    assert decision.banned_chemical_detected == "Monocrotophos"
    assert "BANNED for use on vegetables" in decision.escalation_reason
    assert any("vegetable" in w.lower() for w in decision.warnings)

def test_monocrotophos_trade_name_on_chilli_vegetable(safety_gate):
    # Test brand name Nuvacron on Chilli
    decision, meta = safety_gate.evaluate_query(
        query_text="Nuvacron dosage for mirchi thrips",
        crop="Chilli",
        chemical="Nuvacron"
    )

    assert decision.is_safe is False
    assert decision.needs_escalation is True
    assert decision.severity == "CRITICAL"
    assert decision.banned_chemical_detected == "Monocrotophos"
    assert "BANNED for use on vegetables" in decision.escalation_reason

def test_paraquat_restricted_misuse_triggers_critical(safety_gate):
    # Test Paraquat misuse spray on vegetables
    decision, meta = safety_gate.evaluate_query(
        query_text="Can I spray Paraquat or Gramoxone on vegetables?",
        crop="Tomato",
        chemical="Paraquat"
    )

    assert decision.is_safe is False
    assert decision.needs_escalation is True
    assert decision.severity == "CRITICAL"
    assert "severely restricted" in decision.escalation_reason or "LETHAL POISON" in str(decision.warnings)

# ==============================================================================
# 3. Off-Label Chemical Use Detection
# ==============================================================================

def test_off_label_use_cotton_chemical_on_tomato(validator):
    # Proclaim is registered for Cotton/Chickpea, but NOT for Tomato
    decision = validator.validate_pesticide_use(
        crop="Tomato",
        pest_or_disease="Fruit Borer",
        product_or_chemical="Proclaim"
    )

    assert decision.is_safe is False
    assert decision.needs_escalation is True
    assert decision.off_label_detected is True
    assert decision.severity == "HIGH"
    assert "Off-label chemical use detected" in decision.escalation_reason
    assert "Tomato" in decision.escalation_reason
    assert "Cotton" in decision.escalation_reason

def test_off_label_use_wheat_fungicide_on_chilli(validator):
    # Tilt (Propiconazole) is registered for Wheat, but NOT for Chilli
    decision = validator.validate_pesticide_use(
        crop="Chilli",
        pest_or_disease="Anthracnose",
        product_or_chemical="Tilt"
    )

    assert decision.is_safe is False
    assert decision.needs_escalation is True
    assert decision.off_label_detected is True
    assert "Off-label chemical use detected" in decision.escalation_reason
    assert "Wheat" in decision.escalation_reason

# ==============================================================================
# 4. Unapproved Tank Mixtures (Cocktails) Detection
# ==============================================================================

def test_unapproved_tank_mixture_in_query(safety_gate):
    # Farmer asking to mix two commercial pesticides together
    query = "Can I do a tank mix of Proclaim and Pegasus for my cotton crop?"
    decision, meta = safety_gate.evaluate_query(
        query_text=query,
        crop="Cotton"
    )

    assert decision.is_safe is False
    assert decision.needs_escalation is True
    assert decision.tank_mixture_detected is True
    assert decision.severity == "HIGH"
    assert "Unapproved tank mixture" in decision.escalation_reason
    assert meta["query_type"] == "tank_mixture"

def test_unapproved_tank_mixture_phrasing(safety_gate):
    query = "Spray cocktail of Coragen with Tilt in single pump"
    decision, meta = safety_gate.evaluate_query(
        query_text=query,
        crop="Wheat"
    )

    assert decision.is_safe is False
    assert decision.needs_escalation is True
    assert decision.tank_mixture_detected is True

# ==============================================================================
# 5. Ambiguous Queries Routing to Human Extension Officers
# ==============================================================================

def test_ambiguous_query_missing_crop(safety_gate):
    decision, meta = safety_gate.evaluate_query(
        query_text="What pesticide spray to use for caterpillars?",
        crop=None,
        pest_or_disease="caterpillars"
    )

    assert decision.is_safe is False
    assert decision.needs_escalation is True
    assert "Crop or target pest/disease could not be unambiguously identified" in decision.escalation_reason
    assert any("Both crop name and target pest/disease must be specified" in w for w in decision.warnings)

def test_ambiguous_query_missing_pest(safety_gate):
    decision, meta = safety_gate.evaluate_query(
        query_text="What chemical should I spray on my cotton?",
        crop="Cotton",
        pest_or_disease=None
    )

    assert decision.is_safe is False
    assert decision.needs_escalation is True
    assert "Crop or target pest/disease could not be unambiguously identified" in decision.escalation_reason

# ==============================================================================
# 6. Post-Generation Hallucinated Dosages & Rogue Numbers Scanning
# ==============================================================================

def test_post_generation_compliant_response_passes(safety_gate, validator):
    # Verify approved cotton record
    decision = validator.validate_pesticide_use(
        crop="Cotton",
        pest_or_disease="Pink Bollworm",
        product_or_chemical="Proclaim"
    )
    assert decision.is_safe is True

    # LLM response strictly citing verified CIBRC parameters
    compliant_llm_response = (
        "For Pink Bollworm in Cotton, apply Proclaim 5% SG at 88 g per acre (220 g per hectare). "
        "For a 15-litre knapsack sprayer, mix 6.6 g in water. Dilute in 200 liters of water per acre. "
        "Observe a pre-harvest interval (PHI) of 14 days. Wear protective rubber gloves and a face mask."
    )

    is_safe = safety_gate.validate_llm_response(compliant_llm_response, decision)
    assert is_safe is True

def test_post_generation_rogue_dosage_number_fails(safety_gate, validator):
    decision = validator.validate_pesticide_use(
        crop="Cotton",
        pest_or_disease="Pink Bollworm",
        product_or_chemical="Proclaim"
    )

    # LLM hallucinating 500 ml per acre for a granule formulation with 88 g label
    rogue_response = "You can spray 500 ml per acre of Proclaim for faster pest knock-down."
    is_safe = safety_gate.validate_llm_response(rogue_response, decision)
    assert is_safe is False

def test_post_generation_rogue_knapsack_number_fails(safety_gate, validator):
    decision = validator.validate_pesticide_use(
        crop="Cotton",
        pest_or_disease="Pink Bollworm",
        product_or_chemical="Proclaim"
    )

    # LLM hallucinating 50 g per 15L knapsack pump (approved is 6.6 g)
    rogue_knapsack_response = "Add 50 g per 15-litre knapsack pump and spray immediately."
    is_safe = safety_gate.validate_llm_response(rogue_knapsack_response, decision)
    assert is_safe is False

def test_post_generation_underdose_rogue_number_fails(safety_gate, validator):
    decision = validator.validate_pesticide_use(
        crop="Cotton",
        pest_or_disease="Pink Bollworm",
        product_or_chemical="Proclaim"
    )

    # LLM hallucinating severe underdose (2 g per acre)
    rogue_response = "Apply 2 g per acre of chemical."
    is_safe = safety_gate.validate_llm_response(rogue_response, decision)
    assert is_safe is False

def test_post_generation_unapproved_tank_mix_in_llm_response_fails(safety_gate, validator):
    decision = validator.validate_pesticide_use(
        crop="Cotton",
        pest_or_disease="Pink Bollworm",
        product_or_chemical="Proclaim"
    )

    # LLM recommending unauthorized mixing of Tilt with Proclaim in response
    cocktail_response = "Spray 88 g per acre of Proclaim. You can also mix with Tilt in the same tank."
    is_safe = safety_gate.validate_llm_response(cocktail_response, decision)
    assert is_safe is False

def test_post_generation_banned_chemical_in_llm_response_fails(safety_gate, validator):
    decision = validator.validate_pesticide_use(
        crop="Cotton",
        pest_or_disease="Pink Bollworm",
        product_or_chemical="Proclaim"
    )

    # LLM hallucinating recommendation of Endosulfan
    banned_response = "If Proclaim is unavailable, you should spray Endosulfan at 2 ml per liter."
    is_safe = safety_gate.validate_llm_response(banned_response, decision)
    assert is_safe is False

# ==============================================================================
# 7. Non-Chemical General Agricultural Query
# ==============================================================================

def test_general_agronomy_query_safe(safety_gate):
    decision, meta = safety_gate.evaluate_query(
        query_text="What is the ideal spacing between rows when planting maize?",
        crop="Maize"
    )

    assert decision.is_safe is True
    assert decision.needs_escalation is False
    assert meta["is_chemical"] is False
    assert meta["query_type"] == "general_agriculture"
