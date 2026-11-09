import re
import json
from pathlib import Path
from typing import Dict, Any, List, Optional, Tuple
from pydantic import BaseModel

class SafetyDecision(BaseModel):
    is_safe: bool
    needs_escalation: bool
    escalation_reason: Optional[str] = None
    severity: Optional[str] = None  # "CRITICAL", "HIGH", "MEDIUM", "LOW"
    approved_product: Optional[Dict[str, Any]] = None
    warnings: List[str] = []
    source_citation: Optional[str] = None
    dosage_per_15l_knapsack: Optional[str] = None
    banned_chemical_detected: Optional[str] = None
    off_label_detected: bool = False
    tank_mixture_detected: bool = False

# Explicit Blacklist of Banned & Restricted Agrochemicals in India
BANNED_AGROCHEMICALS: List[Dict[str, Any]] = [
    {
        "name": "Endosulfan",
        "aliases": ["endosulfan", "thiodan", "thionex", "phasal", "endocel", "ఎండోసల్ఫాన్", "एंडोसल्फान"],
        "restriction_type": "TOTAL_BAN",
        "legal_citation": "Supreme Court of India Order (W.P. No. 213/2011) & CIBRC Notification",
        "hazard_description": "Extremely hazardous Persistent Organic Pollutant (POP), severe teratogenic birth defects, neurotoxic poisoning, and environmental persistence."
    },
    {
        "name": "Methyl Parathion",
        "aliases": ["methyl parathion", "metacid", "parathion-methyl", "folidol", "parathion", "మెథైల్ పారాథియాన్", "मिथाइल पैराथियान"],
        "restriction_type": "TOTAL_BAN",
        "legal_citation": "Govt of India Gazette Notification S.O. 3951(E)",
        "hazard_description": "WHO Class Ia extremely hazardous organophosphate, irreversible acetylcholinesterase inhibition, high fatal acute toxicity."
    },
    {
        "name": "Monocrotophos",
        "aliases": ["monocrotophos", "nuvacron", "monocil", "corophos", "phoskill", "మోనోక్రోటోఫాస్", "मोनोक्रोटोफॉस"],
        "restriction_type": "VEGETABLE_BAN",
        "legal_citation": "CIBRC / Ministry of Agriculture Statutory Order & Gazette S.O. 2486(E)",
        "hazard_description": "WHO Class Ib highly hazardous systemic insecticide. Strictly BANNED on all vegetables by CIBRC due to acute food chain toxicity and systemic residue contamination."
    },
    {
        "name": "Paraquat Dichloride",
        "aliases": ["paraquat", "paraquat dichloride", "gramoxone", "పారాక్వాట్", "पैराक्वाट"],
        "restriction_type": "RESTRICTED_HAZARD",
        "legal_citation": "CIBRC Restricted Use Mandate & State Gazettes",
        "hazard_description": "Extremely lethal non-selective contact herbicide with NO known antidote. Strictly prohibited on vegetables, food crops without hood protection, or off-label use. Lethal pulmonary fibrosis hazard."
    },
    {
        "name": "Phorate",
        "aliases": ["phorate", "thimet", "phorate 10g", "ఫోరేట్", "फोरेट"],
        "restriction_type": "TOTAL_BAN",
        "legal_citation": "Govt of India Gazette S.O. 2486(E) / Phased Total Ban",
        "hazard_description": "WHO Class Ia extremely hazardous granular toxicant with acute dermal and inhalation lethal poisoning risk."
    },
    {
        "name": "Phosphamidon",
        "aliases": ["phosphamidon", "dimecron", "ఫాస్ఫామిడాన్", "फॉस्फेमिडॉन"],
        "restriction_type": "TOTAL_BAN",
        "legal_citation": "Govt of India Gazette Notification S.O. 3951(E)",
        "hazard_description": "WHO Class Ia extremely hazardous anticholinesterase systemic organophosphate."
    },
    {
        "name": "DDT",
        "aliases": ["ddt", "dichloro-diphenyl-trichloroethane", "డిడిటి", "डीडीटी"],
        "restriction_type": "TOTAL_BAN",
        "legal_citation": "Insecticides Act, 1968 Agricultural Prohibition & Stockholm Convention",
        "hazard_description": "Persistent bioaccumulative chlorinated hydrocarbon banned completely in Indian agriculture."
    },
    {
        "name": "Aldicarb",
        "aliases": ["aldicarb", "temik"],
        "restriction_type": "TOTAL_BAN",
        "legal_citation": "Govt of India Agricultural Prohibition Order",
        "hazard_description": "WHO Class Ia extremely hazardous carbamate nematocide."
    },
    {
        "name": "Dichlorvos (DDVP)",
        "aliases": ["dichlorvos", "ddvp", "nuvan", "డిడివిపి", "डीडीवीपी"],
        "restriction_type": "TOTAL_BAN",
        "legal_citation": "Govt of India Gazette S.O. 2486(E)",
        "hazard_description": "Volatile neurotoxic organophosphate causing severe respiratory toxicity."
    },
    {
        "name": "Diazinon",
        "aliases": ["diazinon", "basudin"],
        "restriction_type": "TOTAL_BAN",
        "legal_citation": "CIBRC Banned Agrochemical Registry",
        "hazard_description": "Banned organophosphate insecticide in Indian agriculture."
    },
    {
        "name": "Methomyl",
        "aliases": ["methomyl", "lannate"],
        "restriction_type": "TOTAL_BAN",
        "legal_citation": "Govt of India Gazette S.O. 2486(E)",
        "hazard_description": "WHO Class Ib highly toxic carbamate insecticide banned in agriculture."
    },
    {
        "name": "Benomyl",
        "aliases": ["benomyl", "benlate"],
        "restriction_type": "TOTAL_BAN",
        "legal_citation": "Govt of India Agricultural Ban Notification",
        "hazard_description": "Carcinogenic and mutagenic fungicide banned in India."
    }
]

# Set of known vegetable crops for specific regulatory restrictions
VEGETABLE_CROPS = {
    "tomato", "tomatoes", "chilli", "chili", "chillies", "chilies", "mirchi",
    "brinjal", "eggplant", "aubergine", "vankaya", "baingan",
    "okra", "bhendi", "bhindi", "bendakaya", "ladyfinger",
    "cabbage", "cauliflower", "capsicum", "bell pepper", "onion", "garlic", "potato",
    "cucumber", "bitter gourd", "kakarakaya", "karela", "bottle gourd", "sorakaya", "lauki",
    "ridge gourd", "snake gourd", "ash gourd", "pumpkin", "watermelon", "muskmelon",
    "beans", "french beans", "cluster beans", "peas", "green peas",
    "spinach", "palak", "coriander", "methi", "fenugreek", "vegetable", "vegetables",
    "కూరగాయలు", "టమోటా", "మిరప", "వంకాయ", "బెండకాయ", "సొరకాయ",
    "सब्जी", "सब्जियां", "टमाटर", "मिर्च", "बैंगन", "भिंडी"
}

# Crop name normalizations
CROP_ALIASES: Dict[str, str] = {
    "rice": "Rice / Paddy",
    "paddy": "Rice / Paddy",
    "వరి": "Rice / Paddy",
    "धान": "Rice / Paddy",
    "चावल": "Rice / Paddy",
    "cotton": "Cotton",
    "పత్తి": "Cotton",
    "ప్రత్తి": "Cotton",
    "कपास": "Cotton",
    "chilli": "Chilli",
    "chili": "Chilli",
    "chillies": "Chilli",
    "chilies": "Chilli",
    "mirchi": "Chilli",
    "మిరప": "Chilli",
    "मिर्च": "Chilli",
    "tomato": "Tomato",
    "tomatoes": "Tomato",
    "టమోటా": "Tomato",
    "टमाटर": "Tomato",
    "wheat": "Wheat",
    "గోధుమ": "Wheat",
    "गेहूं": "Wheat",
    "maize": "Maize",
    "corn": "Maize",
    "మొక్కజొన్న": "Maize",
    "मक्का": "Maize",
    "groundnut": "Groundnut",
    "peanut": "Groundnut",
    "peanuts": "Groundnut",
    "వేరుశనగ": "Groundnut",
    "मूंगफली": "Groundnut",
    "soybean": "Soybean",
    "soya": "Soybean",
    "soyabean": "Soybean",
    "సోయాబీన్": "Soybean",
    "सोयाबीन": "Soybean",
    "red gram": "Red Gram",
    "redgram": "Red Gram",
    "pigeonpea": "Red Gram",
    "pigeon pea": "Red Gram",
    "tur": "Red Gram",
    "arhar": "Red Gram",
    "కంది": "Red Gram",
    "अरहर": "Red Gram",
    "chickpea": "Chickpea",
    "chick pea": "Chickpea",
    "bengal gram": "Chickpea",
    "bengalgram": "Chickpea",
    "chana": "Chickpea",
    "శనగ": "Chickpea",
    "चना": "Chickpea",
    "mustard": "Mustard",
    "sarson": "Mustard",
    "ఆవాలు": "Mustard",
    "सरसों": "Mustard",
    "sugarcane": "Sugarcane",
    "sugar cane": "Sugarcane",
    "cane": "Sugarcane",
    "చెరకు": "Sugarcane",
    "गन्ना": "Sugarcane"
}

TANK_MIX_PATTERNS = [
    re.compile(r'\b(tank\s*mix(?:ture)?s?|cocktail|cocktails)\b', re.IGNORECASE),
    re.compile(r'\b(?:mix(?:ing)?|combine|combining)\b(?:\s+(?:(?!(?:with|along\s+with)\b)[^\n\r;])+?)?\s+(?:with|along\s+with)\s+(?!water\b|clean\s+water\b)', re.IGNORECASE),
    re.compile(r'\b(mix|mixed|mixing)\s+together\b', re.IGNORECASE),
    re.compile(r'\b(in\s+the\s+same\s+(?:tank|pump|sprayer|drum))\b', re.IGNORECASE),
    re.compile(r'(రెండు\s*(?:మందులు\s*)?కలిపి|ఒకే\s*ట్యాంకులో|ఒకే\s*పంప్|మందులు\s*కలిపి\s*(?:కొట్ట|స్ప్రే|పిచికారీ)|కాక్టెయిల్)', re.IGNORECASE),
    re.compile(r'(दोनों\s*(?:दवा(?:एं)?\s*)?मिलाकर|एक\s*ही\s*टैंक\s*में|दवाएं\s*मिलाकर\s*छिड़काव|टैंक\s*मिक्स|कॉकटेल)', re.IGNORECASE)
]

# Canonical normalization map: resolves trade names to active ingredients
# so mentioning trade name + active ingredient in the same advice is NOT flagged as a tank mixture.
CHEMICAL_CANONICAL_MAP: Dict[str, str] = {
    "proclaim": "emamectin",
    "emamectin": "emamectin",
    "chess": "pymetrozine",
    "pymetrozine": "pymetrozine",
    "pegasus": "diafenthiuron",
    "diafenthiuron": "diafenthiuron",
    "coragen": "chlorantraniliprole",
    "chlorantraniliprole": "chlorantraniliprole",
    "dithane": "mancozeb",
    "mancozeb": "mancozeb",
    "tilt": "propiconazole",
    "propiconazole": "propiconazole",
    "actara": "thiamethoxam",
    "thiamethoxam": "thiamethoxam",
    "delegate": "spinetoram",
    "spinetoram": "spinetoram",
    "fame": "flubendiamide",
    "flubendiamide": "flubendiamide",
    "folicur": "tebuconazole",
    "tebuconazole": "tebuconazole",
    "rogor": "dimethoate",
    "dimethoate": "dimethoate",
    "bavistin": "carbendazim",
    "carbendazim": "carbendazim",
    "ulala": "flonicamid",
    "flonicamid": "flonicamid",
    "tracer": "spinosad",
    "spinosad": "spinosad",
    "saaf": "saaf_premix",
    "amistar": "amistar_premix"
}

class PesticideValidator:
    """
    Deterministic CIBRC 8-parameter verification engine.
    Zero LLM Discretion on pesticide dosage, banned chemicals, and tank mixtures.
    """

    # Pre-compiled regex patterns for numerical chemical dosages
    DOSAGE_PATTERN = re.compile(
        r'(\b\d+(?:\.\d+)?\s*(g|gm|gms|grams?|kg|ml|liters?|litres?|l)\b(\s*(per|/|in|for)\s*(acre|hectare|ha|pump|tank|drum|15\s*l(?:iters?|itres?)?|liters?|litres?|knapsack))?)',
        re.IGNORECASE
    )

    DETAILED_DOSAGE = re.compile(
        r'\b(?P<amount>\d+(?:\.\d+)?)\s*(?P<unit>g|gm|gms|grams?|kg|ml|liters?|litres?|l)\b(?:\s*(?:per|/|in|for)\s*(?P<target>acre|hectare|ha|pump|tank|drum|15\s*l(?:iters?|itres?)?|knapsack|liters?|litres?))?',
        re.IGNORECASE
    )

    def __init__(self, seed_records: Optional[List[Dict[str, Any]]] = None):
        if seed_records is not None:
            self.records = seed_records
        else:
            self.records = self._load_default_records()

    def _load_default_records(self) -> List[Dict[str, Any]]:
        default_path = Path(__file__).resolve().parent.parent.parent.parent / "knowledge-base" / "pesticide-labels" / "cibrc_approved_pesticides_kb07_kb08.json"
        if default_path.exists():
            try:
                with open(default_path, "r", encoding="utf-8") as f:
                    return json.load(f)
            except Exception:
                pass
        return []

    @staticmethod
    def normalize_unit(unit: str) -> str:
        u = unit.lower().strip()
        if u in ["g", "gm", "gms", "gram", "grams"]:
            return "g"
        if u in ["kg", "kilogram", "kilograms"]:
            return "kg"
        if u in ["ml", "milliliter", "milliliters", "millilitre", "millilitres"]:
            return "ml"
        if u in ["l", "liter", "liters", "litre", "litres"]:
            return "l"
        return u

    @staticmethod
    def calculate_knapsack_dosage(dosage_per_acre: str, dilution_liters: str, knapsack_liters: float = 15.0) -> str:
        """
        Calculates the recommended dosage for a standard 15-litre knapsack sprayer.
        Assumes standard dilution volume per acre (default 200 L/acre).
        """
        acre_nums = [float(x) for x in re.findall(r'\b\d+(?:\.\d+)?\b', dosage_per_acre.split('(')[0])]
        dilution_nums = [float(x) for x in re.findall(r'\b\d+(?:\.\d+)?\b', dilution_liters)]
        
        unit_match = re.search(r'\b(g|gm|gms|grams?|kg|ml|liters?)\b', dosage_per_acre, re.I)
        unit = PesticideValidator.normalize_unit(unit_match.group(0)) if unit_match else "g"

        water_vol = dilution_nums[0] if dilution_nums else 200.0
        if not acre_nums or water_vol <= 0:
            return f"Refer to label dilution rate per {int(knapsack_liters)}L sprayer"

        if len(acre_nums) == 1:
            rate_per_liter = acre_nums[0] / water_vol
            pump_dose = round(rate_per_liter * knapsack_liters, 2)
            return f"{pump_dose} {unit} per {int(knapsack_liters)}-litre knapsack sprayer (approx {round(rate_per_liter, 3)} {unit}/L)"
        else:
            low_rate = round((min(acre_nums) / water_vol) * knapsack_liters, 2)
            high_rate = round((max(acre_nums) / water_vol) * knapsack_liters, 2)
            return f"{low_rate}-{high_rate} {unit} per {int(knapsack_liters)}-litre knapsack sprayer"

    def check_banned_chemical(
        self,
        chemical_or_text: Optional[str],
        crop: Optional[str] = None
    ) -> Optional[SafetyDecision]:
        """
        Checks if the provided text or chemical refers to a banned/restricted agrochemical in India.
        Triggers instant CRITICAL safety escalation.
        """
        if not chemical_or_text:
            return None

        text_clean = chemical_or_text.lower().strip()
        crop_clean = crop.lower().strip() if crop else ""

        is_vegetable_context = any(v in text_clean for v in VEGETABLE_CROPS) or any(v in crop_clean for v in VEGETABLE_CROPS)

        for item in BANNED_AGROCHEMICALS:
            detected = False
            for alias in item["aliases"]:
                if alias.isascii():
                    if re.search(rf'\b{re.escape(alias)}\b', text_clean, re.IGNORECASE):
                        detected = True
                        break
                else:
                    if alias in text_clean:
                        detected = True
                        break

            if not detected:
                continue

            # Case A: TOTAL BAN (Endosulfan, Methyl Parathion, Phorate, Phosphamidon, DDT, etc.)
            if item["restriction_type"] == "TOTAL_BAN":
                return SafetyDecision(
                    is_safe=False,
                    needs_escalation=True,
                    severity="CRITICAL",
                    banned_chemical_detected=item["name"],
                    escalation_reason=(
                        f"CRITICAL SAFETY VIOLATION: Banned or restricted agrochemical detected: '{item['name']}'. "
                        f"No CIBRC approved pesticide label found for this banned substance. "
                        f"Usage is illegal under Indian law ({item['legal_citation']}). "
                        f"Severe hazard: {item['hazard_description']}"
                    ),
                    warnings=[
                        f"CRITICAL REGULATORY ALERT: '{item['name']}' is completely BANNED in India under {item['legal_citation']}.",
                        "IMMEDIATE ACTION REQUIRED: Do NOT purchase, possess, or spray this chemical. Distribution and application violate the Insecticides Act, 1968.",
                        f"Toxicological Hazard: {item['hazard_description']}",
                        "This critical safety violation has been immediately escalated to the Mandal Agricultural Officer (MAO) and District Agriculture Officer (DAO)."
                    ],
                    source_citation=f"CIBRC Banned Pesticide Gazette - {item['legal_citation']}"
                )

            # Case B: VEGETABLE BAN (Monocrotophos)
            if item["restriction_type"] == "VEGETABLE_BAN":
                if is_vegetable_context or not crop:
                    target_display = crop if crop else "vegetables"
                    return SafetyDecision(
                        is_safe=False,
                        needs_escalation=True,
                        severity="CRITICAL",
                        banned_chemical_detected=item["name"],
                        escalation_reason=(
                            f"CRITICAL SAFETY VIOLATION: '{item['name']}' is strictly BANNED for use on vegetables ('{target_display}') by CIBRC. "
                            f"No CIBRC approved pesticide label found for this banned application. "
                            f"Prohibited under {item['legal_citation']}. Acute neurotoxin causing severe dietary contamination."
                        ),
                        warnings=[
                            f"CRITICAL WARNING: Monocrotophos is strictly prohibited on all vegetable crops (Tomato, Chilli, Brinjal, Okra, Cucurbits, etc.).",
                            "High risk of fatal dietary poisoning and acute neurotoxicity from crop residues.",
                            "Do NOT spray Monocrotophos on vegetables under any circumstance.",
                            "Escalating query to Regional Agricultural Extension Officer for approved non-toxic alternatives."
                        ],
                        source_citation=f"CIBRC Order on Vegetables - {item['legal_citation']}"
                    )

            # Case C: RESTRICTED HAZARD (Paraquat Dichloride)
            if item["restriction_type"] == "RESTRICTED_HAZARD":
                if is_vegetable_context or "spray" in text_clean:
                    return SafetyDecision(
                        is_safe=False,
                        needs_escalation=True,
                        severity="CRITICAL",
                        banned_chemical_detected=item["name"],
                        escalation_reason=(
                            f"CRITICAL SAFETY VIOLATION: '{item['name']}' is a severely restricted agrochemical with NO known antidote. "
                            f"No CIBRC approved pesticide label found for foliar or unauthorized application. "
                            f"Prohibited on vegetables and standing food crops under {item['legal_citation']}."
                        ),
                        warnings=[
                            f"LETHAL POISON WARNING: '{item['name']}' causes irreversible pulmonary fibrosis. A single accidental ingestion or inhalation is fatal.",
                            "Paraquat is strictly prohibited on vegetables, leafy crops, and for uncertified foliar spraying.",
                            "Banned across multiple Indian states (e.g. Kerala, Odisha).",
                            "Escalating immediately to Agricultural Extension Officer for safe non-hazardous weed management."
                        ],
                        source_citation=f"CIBRC Restricted Agrochemical Directive - {item['legal_citation']}"
                    )

        return None

    def detect_tank_mixture(self, text: str) -> Optional[Dict[str, Any]]:
        """
        Detects unapproved pesticide cocktails or tank mixtures in user query or LLM output.
        Ignores normal dilution in water and warnings advising against mixing.
        """
        if not text:
            return None

        text_lower = text.lower()

        # 1. Keyword check for explicit tank mixture phrases (with negation and water dilution awareness)
        for pattern in TANK_MIX_PATTERNS:
            m = pattern.search(text_lower)
            if m:
                # Inspect the surrounding context (50 characters before and after) to see if this is
                # a safety warning advising AGAINST tank mixing, e.g. "do not mix", "కలిపి కొట్టవద్దు"
                start_win = max(0, m.start() - 50)
                end_win = min(len(text_lower), m.end() + 50)
                window = text_lower[start_win:end_win]

                is_negated = any(neg in window for neg in [
                    'do not', "don't", 'never', 'avoid', 'prohibit', 'not recommended',
                    'వద్దు', 'కూడదు', 'చేయవద్దు', 'కలపవద్దు', 'కలపకూడదు', 'నిషేధం',
                    'मत', 'नहीं', 'न करें', 'वर्जित', 'मना'
                ])
                if not is_negated:
                    return {
                        "matched_phrase": m.group(0),
                        "reason": "Explicit tank mixture or cocktail phrase detected."
                    }

        # 2. Check for multiple distinct active ingredients using canonical grouping
        canonical_chems = set()
        found_raw = []
        for raw_name, canonical in CHEMICAL_CANONICAL_MAP.items():
            if re.search(rf'\b{re.escape(raw_name)}\b', text_lower):
                canonical_chems.add(canonical)
                found_raw.append(raw_name)

        # Allow single approved combinations (e.g. Saaf is Carbendazim 12% + Mancozeb 63% WP)
        if "saaf_premix" in canonical_chems:
            canonical_chems.discard("carbendazim")
            canonical_chems.discard("mancozeb")

        if len(canonical_chems) >= 2:
            return {
                "chemicals_found": found_raw,
                "reason": f"Multiple separate commercial pesticides ({', '.join(found_raw)}) combined in tank."
            }

        return None

    def validate_pesticide_use(
        self,
        crop: Optional[str],
        pest_or_disease: Optional[str],
        product_or_chemical: Optional[str] = None
    ) -> SafetyDecision:
        # Step 0: Check for Banned / Restricted Agrochemicals first
        banned_decision = self.check_banned_chemical(product_or_chemical, crop=crop)
        if banned_decision:
            return banned_decision

        if not crop or not pest_or_disease:
            return SafetyDecision(
                is_safe=False,
                needs_escalation=True,
                severity="MEDIUM",
                escalation_reason="Crop or target pest/disease could not be unambiguously identified.",
                warnings=[
                    "Both crop name and target pest/disease must be specified for chemical safety validation.",
                    "Escalating case to Regional Agricultural Extension Officer."
                ]
            )

        # Step 0.5: Check for unapproved tank mixtures in chemical input
        if product_or_chemical:
            tank_mix = self.detect_tank_mixture(product_or_chemical)
            if tank_mix:
                return SafetyDecision(
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
                        "Tank mixtures can cause severe crop scorch, chemical degradation, or dangerous toxic vapors.",
                        "Escalating to Agricultural Extension Officer for authorized single-product recommendations."
                    ],
                    source_citation="CIBRC Standard Operating Procedures on Agrochemical Tank Mixing"
                )

        crop_clean = crop.lower().strip()
        canonical_crop = CROP_ALIASES.get(crop_clean, crop_clean).lower()
        pest_clean = pest_or_disease.lower().strip()
        chemical_clean = product_or_chemical.lower().strip() if product_or_chemical else None

        # Search for exact 8-parameter match in CIBRC registry
        matched_records = []
        for rec in self.records:
            rec_crop = rec.get("target_crop", "").lower()
            rec_pest = rec.get("target_pest", "").lower()
            rec_disease = rec.get("target_disease", "").lower()
            rec_prod = rec.get("product_trade_name", "").lower()
            rec_ai = rec.get("active_ingredient", "").lower()

            # Crop match: checks canonical alias as well
            crop_match = (
                crop_clean in rec_crop or rec_crop in crop_clean or
                canonical_crop in rec_crop or rec_crop in canonical_crop
            )
            # Pest or disease match
            pest_match = (pest_clean in rec_pest or rec_pest in pest_clean) if rec_pest else False
            disease_match = (pest_clean in rec_disease or rec_disease in pest_clean) if rec_disease else False

            if crop_match and (pest_match or disease_match):
                if chemical_clean:
                    if (chemical_clean in rec_prod or 
                        chemical_clean in rec_ai or 
                        rec_prod in chemical_clean or
                        rec_ai.split()[0].lower() in chemical_clean):
                        matched_records.append(rec)
                else:
                    matched_records.append(rec)

        if not matched_records:
            # Check for Off-label use: Is this chemical registered for OTHER crops?
            if chemical_clean:
                other_crop_registrations = []
                for rec in self.records:
                    rec_prod = rec.get("product_trade_name", "").lower()
                    rec_ai = rec.get("active_ingredient", "").lower()
                    if (chemical_clean in rec_prod or 
                        chemical_clean in rec_ai or 
                        rec_prod in chemical_clean or 
                        rec_ai.split()[0].lower() in chemical_clean):
                        other_crop_registrations.append(rec.get("target_crop", "Other Crop"))

                if other_crop_registrations:
                    registered_crops_str = ", ".join(sorted(set(other_crop_registrations)))
                    return SafetyDecision(
                        is_safe=False,
                        needs_escalation=True,
                        severity="HIGH",
                        off_label_detected=True,
                        escalation_reason=(
                            f"Off-label chemical use detected: '{product_or_chemical}' is registered under CIBRC only for "
                            f"{registered_crops_str}, and is NOT approved for '{crop}'. "
                            f"No CIBRC approved pesticide label found for this crop. "
                            f"Off-label application violates the Insecticides Act, 1968 and risks crop damage and unapproved pesticide residues."
                        ),
                        warnings=[
                            f"Off-label Warning: '{product_or_chemical}' has NO approved dosage or label claim for '{crop}'.",
                            "Applying off-label chemicals causes illegal residue violations, potential harvest rejection, and crop phytotoxicity.",
                            "Escalating case to Regional Agricultural Extension Officer for crop-authorized alternatives."
                        ],
                        source_citation="CIBRC Official Crop-Pest Registered Formulations Registry"
                    )

            return SafetyDecision(
                is_safe=False,
                needs_escalation=True,
                severity="HIGH",
                escalation_reason=f"No CIBRC approved pesticide label found for crop '{crop}' and target '{pest_or_disease}'.",
                warnings=[
                    "Unregistered or unverified chemical treatment. Self-medication of crops risks crop burn and chemical poisoning.",
                    "Escalating case to Regional Agricultural Extension Officer."
                ]
            )

        # Authoritative registered record
        record = matched_records[0]

        # Verify 8 mandatory parameters
        mandatory_fields = [
            "product_trade_name", "active_ingredient", "target_crop",
            "dosage_per_hectare", "dosage_per_acre", "dilution_water_liters",
            "pre_harvest_interval_days", "safety_precautions"
        ]
        missing_fields = [f for f in mandatory_fields if not record.get(f)]
        if missing_fields:
            return SafetyDecision(
                is_safe=False,
                needs_escalation=True,
                severity="MEDIUM",
                escalation_reason=f"Incomplete CIBRC registration label: Missing {missing_fields}",
                warnings=["CIBRC label missing required safety parameters. Escalating to human officer."]
            )

        # Precise knapsack sprayer dosage verification
        knapsack_dose = record.get("dosage_per_15l_knapsack")
        if not knapsack_dose:
            knapsack_dose = self.calculate_knapsack_dosage(
                dosage_per_acre=record.get("dosage_per_acre", ""),
                dilution_liters=record.get("dilution_water_liters", "")
            )
            record["dosage_per_15l_knapsack"] = knapsack_dose

        # Personal Protective Equipment (PPE) requirements
        ppe_list = record.get("ppe_requirements")
        if isinstance(ppe_list, list):
            ppe_str = "; ".join(ppe_list)
        elif isinstance(ppe_list, str):
            ppe_str = ppe_list
        else:
            ppe_str = "Chemical-resistant rubber gloves, particulate face mask, safety goggles, and full protective workwear."

        citation = f"CIBRC Reg: {record.get('cibrc_registration_number', 'Official CIBRC Leaflet')} - {record.get('product_trade_name')}"

        return SafetyDecision(
            is_safe=True,
            needs_escalation=False,
            severity="LOW",
            approved_product=record,
            source_citation=citation,
            dosage_per_15l_knapsack=knapsack_dose,
            warnings=[
                f"Toxicity: {record.get('toxicity_band', 'Standard Agricultural Formulation')}",
                f"Waiting Period (PHI): {record.get('pre_harvest_interval_days')} days before harvest.",
                f"Knapsack Sprayer Dose: {knapsack_dose}.",
                f"Mandatory PPE: {ppe_str}",
                f"Precautions: {record.get('safety_precautions')}"
            ]
        )

    def scan_for_hallucinated_dosages(self, generated_text: str, approved_record: Dict[str, Any]) -> bool:
        """
        Hardened post-generation scanner.
        Scans LLM output text for:
        1. Unapproved tank mixtures or cocktail suggestions.
        2. Banned chemicals mentioned.
        3. Rogue dosage numbers or units that do not match the approved CIBRC record.
        """
        # 1. Reject if tank mix recommendation is present
        if self.detect_tank_mixture(generated_text):
            return False

        # 2. Reject if banned chemical is mentioned
        if self.check_banned_chemical(generated_text):
            return False

        # Extract approved parameters
        approved_ha_str = str(approved_record.get("dosage_per_hectare", "")).lower()
        approved_acre_str = str(approved_record.get("dosage_per_acre", "")).lower()
        approved_knapsack_str = str(approved_record.get("dosage_per_15l_knapsack", "")).lower()
        dilution_str = str(approved_record.get("dilution_water_liters", "")).lower()
        phi_str = str(approved_record.get("pre_harvest_interval_days", "")).lower()
        active_ing_str = str(approved_record.get("active_ingredient", "")).lower()

        # Helper to extract numbers from string
        def extract_nums(s: str) -> List[float]:
            return [float(x) for x in re.findall(r'\b\d+(?:\.\d+)?\b', s)]

        approved_acre_nums = extract_nums(approved_acre_str.split('(')[0])
        approved_ha_nums = extract_nums(approved_ha_str.split('(')[0])
        
        knapsack_chem_match = re.search(r'(\d+(?:\.\d+)?)\s*(g|gm|gms|grams?|kg|ml)\b', approved_knapsack_str.split('(')[0], re.I)
        approved_knapsack_nums = [float(knapsack_chem_match.group(1))] if knapsack_chem_match else []
        
        dilution_nums = extract_nums(dilution_str)
        phi_nums = extract_nums(phi_str)
        strength_nums = extract_nums(active_ing_str)

        # Allow knapsack container size (15L / 16L)
        container_nums = [15.0, 16.0]

        # All permissible numbers in the context of this product
        all_allowed_nums = set(
            approved_acre_nums + approved_ha_nums + approved_knapsack_nums + 
            dilution_nums + phi_nums + strength_nums + container_nums
        )

        # Scan text for dosage expressions
        matches = list(self.DETAILED_DOSAGE.finditer(generated_text))
        for m in matches:
            amount_str = m.group("amount")
            unit_raw = m.group("unit")
            target_raw = m.group("target")

            try:
                amount = float(amount_str)
            except ValueError:
                return False

            unit = self.normalize_unit(unit_raw)
            target = target_raw.lower().strip() if target_raw else None

            # Chemical dosage units: g, ml, kg
            if unit in ["g", "ml", "kg"]:
                if target in ["acre"]:
                    # Must match approved acre numbers or fall within acre range
                    if not approved_acre_nums:
                        return False
                    min_acre, max_acre = min(approved_acre_nums), max(approved_acre_nums)
                    if not (min_acre <= amount <= max_acre):
                        return False

                elif target in ["hectare", "ha"]:
                    if not approved_ha_nums:
                        return False
                    min_ha, max_ha = min(approved_ha_nums), max(approved_ha_nums)
                    if not (min_ha <= amount <= max_ha):
                        return False

                elif target and any(k in target for k in ["pump", "tank", "knapsack", "15"]):
                    if not approved_knapsack_nums:
                        return False
                    min_k, max_k = min(approved_knapsack_nums), max(approved_knapsack_nums)
                    if not (min_k <= amount <= max_k):
                        return False

                else:
                    # Unspecified target or per liter:
                    # Must match one of approved chemical rates (acre, ha, knapsack, or per-liter dilution rate)
                    per_liter_rate = None
                    if approved_acre_nums and dilution_nums:
                        per_liter_rate = round(approved_acre_nums[0] / dilution_nums[0], 2)

                    matched_rate = False
                    if approved_acre_nums and min(approved_acre_nums) <= amount <= max(approved_acre_nums):
                        matched_rate = True
                    elif approved_ha_nums and min(approved_ha_nums) <= amount <= max(approved_ha_nums):
                        matched_rate = True
                    elif approved_knapsack_nums and min(approved_knapsack_nums) <= amount <= max(approved_knapsack_nums):
                        matched_rate = True
                    elif per_liter_rate is not None and abs(amount - per_liter_rate) < 0.1:
                        matched_rate = True

                    if not matched_rate:
                        return False

            # Water volume units: liters / l
            elif unit == "l":
                # Must match dilution water volumes or knapsack container sizes
                if amount not in dilution_nums and amount not in container_nums:
                    return False

        # Fallback check for any rogue dosage patterns
        raw_matches = self.DOSAGE_PATTERN.findall(generated_text)
        for rm in raw_matches:
            matched_str = rm[0].lower().strip()
            numbers = extract_nums(matched_str)
            for n in numbers:
                if n not in all_allowed_nums:
                    # Check if number falls within any approved range
                    in_range = False
                    if approved_acre_nums and min(approved_acre_nums) <= n <= max(approved_acre_nums):
                        in_range = True
                    if approved_ha_nums and min(approved_ha_nums) <= n <= max(approved_ha_nums):
                        in_range = True
                    if approved_knapsack_nums and min(approved_knapsack_nums) <= n <= max(approved_knapsack_nums):
                        in_range = True
                    if not in_range:
                        return False

        return True
