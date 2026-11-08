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
            # Look in knowledge-base/pesticide-labels/
            default_path = Path(__file__).resolve().parent.parent.parent.parent / "knowledge-base" / "pesticide-labels" / "cibrc_approved_pesticides_kb07_kb08.json"
            data_path = str(default_path)

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
        chemical: Optional[str] = None
    ) -> Tuple[SafetyDecision, Dict[str, Any]]:
        """
        Evaluates a farmer/user query prior to LLM generation.
        Returns (SafetyDecision, metadata).
        """
        from app.ai.query_understanding import QueryUnderstanding
        analysis = QueryUnderstanding.analyze_query(query_text, context_crop=crop)
        effective_crop = crop or analysis.get("crop")
        effective_pest_or_disease = pest_or_disease or analysis.get("target_entity")

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

        # Step 3: Identify explicit chemical recommendation query vs general agronomic/symptom query
        query_lower = query_text.lower()
        explicit_chemical_intent = any(word in query_lower for word in [
            "spray", "pesticide", "fungicide", "insecticide", "dose", "dosage", "chemical",
            "పురుగు మందు", "తెగులు మందు", "రసాయన", "పిచికారీ మోతాదు", "మందు కొట్టాలి", "మందు స్ప్రే",
            "कीटनाशक", "छिड़काव मात्रा", "दवा की मात्रा", "रासायनिक दवा"
        ])

        if chemical or explicit_chemical_intent:
            # Strictly validate through CIBRC 8-parameter engine
            decision = self.validator.validate_pesticide_use(
                crop=effective_crop,
                pest_or_disease=effective_pest_or_disease,
                product_or_chemical=chemical
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
        if not decision.approved_product:
            return True  # Non-chemical or escalated

        # 1. Reject if LLM suggested an unapproved tank mixture
        if self.validator.detect_tank_mixture(response_text):
            logger.warning("SafetyGate Intercept: Post-generation scan detected unapproved tank mixture!")
            return False

        # 2. Reject if LLM mentioned banned chemicals
        if self.validator.check_banned_chemical(response_text):
            logger.warning("SafetyGate Intercept: Post-generation scan detected banned chemical!")
            return False

        # 3. Reject if LLM generated rogue/unapproved dosage numbers
        return self.validator.scan_for_hallucinated_dosages(response_text, decision.approved_product)

safety_gate = SafetyGate()
