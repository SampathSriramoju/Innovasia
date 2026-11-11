import json
from pathlib import Path
from typing import Dict, Any, Optional, Tuple
from app.safety.pesticide_validator import PesticideValidator, SafetyDecision
from app.core.logging import logger

class SafetyGate:
    """
    Central Safety Controller for Bol Kisan.
    Enforces the Non-Negotiable Agricultural Safety Mandate:
    Zero LLM Discretion on Pesticides, Banned Agrochemicals, and Tank Mixtures.
    """
    def __init__(self, data_path: Optional[str] = None):
        if not data_path:
            candidate_paths = [
                Path(__file__).resolve().parent.parent.parent.parent / "knowledge-base" / "pesticide-labels" / "cibrc_approved_pesticides_kb07_kb08.json",
                Path.cwd() / "knowledge-base" / "pesticide-labels" / "cibrc_approved_pesticides_kb07_kb08.json",
                Path.cwd().parent / "knowledge-base" / "pesticide-labels" / "cibrc_approved_pesticides_kb07_kb08.json"
            ]
            for p in candidate_paths:
                if p.exists():
                    data_path = str(p)
                    break

        seed_data = []
        try:
            if Path(data_path).exists():
                with open(data_path, "r", encoding="utf-8") as f:
                    seed_data = json.load(f)
                logger.info(f"Loaded {len(seed_data)} verified CIBRC pesticide records into SafetyGate.")
            else:
                logger.warning(f"CIBRC dataset not found at {data_path}, using built-in validator defaults.")
        except Exception as e:
            logger.error(f"Failed to load CIBRC seed records: {e}")

        self.validator = PesticideValidator(seed_records=seed_data if seed_data else None)

    def evaluate_query(
        self,
        query_text: str,
        crop: Optional[str] = None,
        pest_or_disease: Optional[str] = None,
        chemical: Optional[str] = None,
        application_method: Optional[str] = None,
        growth_stage: Optional[str] = None,
        plant_part: Optional[str] = None
    ) -> Tuple[SafetyDecision, Dict[str, Any]]:
        """
        Evaluates a farmer/user query prior to LLM generation.
        Returns (SafetyDecision, metadata).
        """
        from app.ai.query_understanding import QueryUnderstanding
        analysis = QueryUnderstanding.analyze_query(query_text, context_crop=crop)
        effective_crop = crop or analysis.get("crop")
        effective_pest_or_disease = pest_or_disease or analysis.get("target_entity")

        target_method, target_stage = self.validator.infer_application_context(
            application_method=application_method,
            growth_stage=growth_stage,
            plant_part=plant_part,
            text_context=f"{query_text} {pest_or_disease or ''} {chemical or ''}"
        )

        # Step 1: Scan for Banned / Restricted Agrochemicals in query or chemical name
        banned_decision = self.validator.check_banned_chemical(chemical or query_text, crop=effective_crop)
        if banned_decision:
            logger.warning(f"SafetyGate Intercept: Banned chemical '{banned_decision.banned_chemical_detected}' detected.")
            return banned_decision, {
                "query_type": "banned_chemical",
                "is_chemical": True,
                "severity": banned_decision.severity or "CRITICAL"
            }

        # Step 2: Scan for unapproved tank mixtures (cocktails)
        tank_mix = self.validator.detect_tank_mixture(chemical or query_text)
        if tank_mix:
            logger.warning(f"SafetyGate Intercept: Unapproved tank mixture detected in query: {tank_mix}")
            tank_decision = SafetyDecision(
                is_safe=False,
                needs_escalation=True,
                severity="HIGH",
                tank_mixture_detected=True,
                escalation_reason=(
                    "Unapproved tank mixture (pesticide cocktail) detected. CIBRC regulations prohibit unauthorized tank mixing "
                    "of commercial agrochemicals due to severe risks of phytotoxicity, chemical antagonism, and toxic residues. "
                    "No CIBRC approved pesticide label found for this tank mix."
                ),
                warnings=[
                    "CIBRC Safety Mandate: Never combine separate commercial pesticides in the same spray tank.",
                    "Tank mixing uncertified combinations can cause crop failure, fatal gas release, or toxic chemical residues.",
                    "Escalating query to Agricultural Extension Officer for certified single-formulation advice."
                ],
                source_citation="CIBRC Standard Operating Procedures on Agrochemical Tank Mixing"
            )
            return tank_decision, {
                "query_type": "tank_mixture",
                "is_chemical": True,
                "severity": "HIGH"
            }

        query_lower = query_text.lower()

        # Step 2b: Scan for Severe Operational and Food Safety Violations
        # A. Oral nozzle blowing hazard
        if any(p in query_lower for p in ["నోటితో", "ముంహ సే ఫూంక", "मुंह से फूंक", "blow through", "mouth to clear", "nozzle with mouth"]):
            return SafetyDecision(
                is_safe=False,
                needs_escalation=True,
                severity="CRITICAL",
                escalation_reason="Lethal Operator Hazard: Blowing through clogged pesticide spray nozzles with mouth causes acute oral poisoning and death.",
                warnings=[
                    "LETHAL SAFETY HAZARD: Never clear clogged sprayer nozzles with your mouth.",
                    "Use a soft toothbrush or plastic wire and clean water.",
                    "Escalating query to extension officer for safe equipment maintenance advisory."
                ],
                source_citation="WHO / NIPHM Safe Pesticide Handling Code"
            ), {"query_type": "operator_safety_hazard", "is_chemical": True, "severity": "CRITICAL"}

        # B. Harvest day pesticide spraying (PHI violation)
        if any(p in query_lower for p in ["కోసే రోజు", "కోత రోజే", "తోడాయి వాలే దిన్", "तुड़ाई वाले दिन", "day of harvest", "morning of harvest", "same day of harvest"]):
            return SafetyDecision(
                is_safe=False,
                needs_escalation=True,
                severity="CRITICAL",
                escalation_reason="Severe Food Safety & Pre-Harvest Interval (PHI) Violation: Applying chemical pesticides on the day of harvest leaves toxic residues exceeding statutory MRL limits.",
                warnings=[
                    "FOOD SAFETY VIOLATION: Never apply chemical pesticides on the day of harvest.",
                    "Every registered chemical mandates a statutory Pre-Harvest Interval (PHI) of 3 to 30 days.",
                    "Escalating case to Agricultural Officer to prevent consumer poisoning."
                ],
                source_citation="FSSAI & CIBRC Pre-Harvest Interval Regulations"
            ), {"query_type": "phi_violation", "is_chemical": True, "severity": "CRITICAL"}

        # C. Incompatible tank mix (Copper Oxychloride + Chlorpyrifos/OP/EC)
        has_copper = any(p in query_lower for p in ["కాపర్", "कॉपर", "copper"])
        has_op_or_ec = any(p in query_lower for p in ["క్లోర్‌పైరిఫాస్", "క్లోరిపైరిఫాస్", "క్లోర్", "क्लोरपायरीफॉस", "chlorpyrifos", "ఎండోసల్ఫాన్"])
        if has_copper and has_op_or_ec:
            return SafetyDecision(
                is_safe=False,
                needs_escalation=True,
                severity="HIGH",
                tank_mixture_detected=True,
                escalation_reason="Chemical Incompatibility Hazard: Copper Oxychloride must never be tank-mixed with Organophosphates or EC formulations. Causes chemical breakdown, precipitation, and severe phytotoxicity (crop burning).",
                warnings=[
                    "PROHIBITED COMBINATION: Copper Oxychloride reacts with Organophosphates and EC formulations.",
                    "Applying this tank mix burns crop leaves and leaves precipitate in the sprayer.",
                    "Spray chemicals separately with a minimum 5-day interval."
                ],
                source_citation="NIPHM & CIBRC Chemical Incompatibility Standards"
            ), {"query_type": "incompatible_mixture", "is_chemical": True, "severity": "HIGH"}

        # D. Bio-inputs and Organic remedies bypass chemical escalation
        is_bio_query = any(k in query_lower for k in [
            "జీవామృతం", "బీజామృతం", "మజ్జిగ", "పుల్లటి మజ్జిగ", "వేపగింజల", "బ్రహ్మాస్త్రం", "అగ్ని అస్త్రం", "వేప నూనె", "వేప", "నీమ్", "ఫెరమోన్", "లింగాకర్షక",
            "జీవామృత్", "जीवामृत", "बीजामृत", "छाछ", "खट्टी छाछ", "नीम बीज", "ब्रह्मास्त्र", "नीम तेल", "नीम", "फेरोमोन",
            "jeevamrit", "beejamrit", "buttermilk", "nske", "brahmastra", "neem oil", "neem", "panchagavya", "pheromone",
            "bt", "bacillus", "trichoderma", "బవేరియా", "ట్రైకోడెర్మా", "ब्युवेरिया", "ट्राइकोडर्मा",
            "రైజోబియం", "రాజోబియం", "राइजोबियम", "rhizobium", "psb", "azolla", "bga", "అజోల్లా"
        ])
        if is_bio_query and not chemical:
            return SafetyDecision(
                is_safe=True,
                needs_escalation=False,
                severity="LOW",
                warnings=[]
            ), {"query_type": "bio_inputs", "is_chemical": False, "analysis": analysis}

        # Step 3: Scan query text for registered CIBRC products / active ingredients
        target_chem = chemical
        if not target_chem:
            for rec in self.validator.records:
                trade_name = (rec.get("product_trade_name") or rec.get("trade_name") or "").lower()
                ai_name = rec.get("active_ingredient", "").lower()
                # Check for trade name match or active ingredient primary stem match
                if trade_name and len(trade_name) > 3 and trade_name in query_lower:
                    target_chem = rec.get("product_trade_name") or rec.get("trade_name")
                    break
                if ai_name:
                    primary_ai = ai_name.split()[0].lower()
                    if len(primary_ai) > 4 and primary_ai in query_lower:
                        target_chem = rec.get("product_trade_name") or rec.get("active_ingredient")
                        break

        # Step 4: Identify explicit chemical recommendation query vs general agronomic/symptom query
        explicit_chemical_intent = any(word in query_lower for word in [
            "spray", "pesticide", "fungicide", "insecticide", "dose", "dosage", "chemical", "seed treatment", "seed dressing",
            "పురుగు మందు", "తెగులు మందు", "రసాయన", "పిచికారీ మోతాదు", "మందు కొట్టాలి", "మందు స్ప్రే", "విత్తన శుద్ధి", "బీజోపచార్",
            "कीटनाशक", "छिड़काव मात्रा", "दवा की मात्रा", "रासायनिक दवा", "बीज उपचार", "बीजोपचार"
        ]) or (target_method in ["seed_treatment", "foliar_spray"] and effective_pest_or_disease)

        if target_chem or explicit_chemical_intent:
            # Strictly validate through CIBRC 8-parameter engine
            decision = self.validator.validate_pesticide_use(
                crop=effective_crop,
                pest_or_disease=effective_pest_or_disease,
                product_or_chemical=target_chem,
                application_method=target_method,
                growth_stage=target_stage,
                plant_part=plant_part
            )
            return decision, {
                "query_type": "pesticide_chemical",
                "is_chemical": True,
                "severity": decision.severity or ("LOW" if decision.is_safe else "HIGH")
            }

        # Non-chemical query (e.g. symptoms, biology, cultivation, spacing, weather, irrigation, fertilizer)
        return SafetyDecision(
            is_safe=True,
            needs_escalation=False,
            severity="LOW",
            warnings=[]
        ), {"query_type": "general_agriculture", "is_chemical": False, "analysis": analysis}

    def validate_llm_response(
        self,
        response_text: str,
        decision: SafetyDecision
    ) -> bool:
        """
        Post-generation check.
        Ensures LLM did not hallucinate conflicting or unauthorized dosage numbers,
        unapproved tank mixtures, or banned agrochemicals.
        """
        # 1. Reject if LLM suggested an unapproved tank mixture
        if self.validator.detect_tank_mixture(response_text):
            logger.warning("SafetyGate Intercept: Post-generation scan detected unapproved tank mixture!")
            return False

        # 2. Reject if LLM mentioned banned chemicals
        if self.validator.check_banned_chemical(response_text):
            logger.warning("SafetyGate Intercept: Post-generation scan detected banned chemical!")
            return False

        # 3. If approved product exists, ensure generated dosage matches approved label numbers
        if decision.approved_product:
            return self.validator.scan_for_hallucinated_dosages(response_text, decision.approved_product)

        # 4. If NO approved product was identified, ensure LLM does not hallucinate arbitrary chemical spray dosages
        detailed_dosages = list(self.validator.DETAILED_DOSAGE.finditer(response_text))
        if detailed_dosages:
            chem_keywords = [
                "spray", "pesticide", "fungicide", "insecticide", "chemical", "dosage", "dose",
                "ec", "sc", "wp", "sp", "sg", "wdg",
                "మి.లీ", "లీటరు", "పిచికారీ", "మందు", "कीटनाशक", "छिड़काव", "दवा"
            ]
            text_lower = response_text.lower()
            if any(k in text_lower for k in chem_keywords):
                logger.warning("SafetyGate Intercept: Rogue chemical dosage detected without approved CIBRC product!")
                return False

        return True

safety_gate = SafetyGate()
