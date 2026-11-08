import re
from typing import Dict, Any, Optional, List, Tuple

class QueryUnderstanding:
    """
    Intelligent Agricultural Query Understanding & Entity Extraction.
    Extracts crop, topic/intent, disease, pest, chemical, and growth stage across Telugu, Hindi, and English.
    """

    CROP_PATTERNS = {
        "Rice": [
            r"\brice\b", r"\bpaddy\b", r"\boryza\b",
            "వరి", "వరిలో", "వరిపంట", "వరికి", "ధాన్యం",
            "धान", "चावल"
        ],
        "Cotton": [
            r"\bcotton\b", r"\bgossypium\b",
            "పత్తి", "ప్రత్తి", "పత్తిలో", "ప్రత్తిలో", "పత్తికి", "ప్రత్తికి",
            "कपास"
        ],
        "Chilli": [
            r"\bchilli\b", r"\bchili\b", r"\bchillies\b", r"\bchilies\b", r"\bcapsicum\b",
            "మిరప", "మిరపలో", "మిరపకి", "మిర్చి",
            "मिर्च", "मिर्ची"
        ],
        "Maize": [
            r"\bmaize\b", r"\bcorn\b",
            "మొక్కజొన్న", "మొక్కజొన్నలో", "మొక్కజొన్నకి", "మక్క", "మక్కజొన్న",
            "मक्का", "मक्के"
        ],
        "Wheat": [
            r"\bwheat\b", r"\btriticum\b",
            "గోధుమ", "గోధుమలో", "గోధుమకి",
            "गेहूं", "गेंहू"
        ],
        "Tomato": [
            r"\btomato\b", r"\btomatoes\b",
            "టమోటా", "టమాటా", "టమోటాలో", "టమాటాల్లో",
            "टमाटर"
        ],
        "Groundnut": [
            r"\bgroundnut\b", r"\bpeanut\b",
            "వేరుశనగ", "వేరుశెనగ", "వేరుశనగలో", "పల్లీ",
            "मूंगफली"
        ],
        "Sugarcane": [
            r"\bsugarcane\b",
            "చెరకు", "చెరకులో",
            "गन्ना"
        ],
        "Red Gram": [
            r"\bred gram\b", r"\bpigeon pea\b", r"\btur\b", r"\barhar\b",
            "కంది", "కందిలో", "కందికి",
            "अरहर", "तुअर"
        ],
        "Bengal Gram": [
            r"\bbengal gram\b", r"\bchickpea\b", r"\bchana\b",
            "శనగ", "శనగలో", "హరిమంత",
            "चना", "चने"
        ],
        "Soybean": [
            r"\bsoybean\b", r"\bsoya\b",
            "సోయాబీన్", "సోయా",
            "सोयाबीन", "सोया"
        ]
    }

    INTENT_COLLECTIONS = {
        "crop_disease": ["KB04", "KB06", "KB21"],
        "pest_management": ["KB05", "KB06", "KB21"],
        "fertilizer_soil": ["KB09", "KB10"],
        "soil_health": ["KB10"],
        "irrigation": ["KB11"],
        "weed_management": ["KB13"],
        "sowing_cultivation": ["KB01", "KB02", "KB03"],
        "pesticide_safety": ["KB08", "KB07", "KB06"],
        "mandi_market": ["KB18"],
        "weather_advisory": ["KB16"],
        "harvesting": ["KB14", "KB15"],
        "crop_rotation": ["KB12"],
        "organic_farming": ["KB17"],
        "government_scheme": ["KB20", "KB22"],
        "general_agri": ["KB21", "KB22", "KB01"]
    }

    INTENT_KEYWORDS = {
        "crop_disease": [
            "symptom", "disease", "blast", "blight", "rot", "wilt", "spot", "leaf spot", "mildew", "rust", "mosaic", "curl", "fungus", "bacterial",
            "తెగులు", "అగ్గితెగులు", "ఆకుమచ్చ", "ఎండు తెగులు", "బూడిద తెగులు", "ఆకు ముడత", "కుళ్ళు", "మాడు", "మచ్చలు", "శిలీంధ్ర",
            "रोग", "झुलसा", "धब्बा", "सड़न", "उकठा", "मरोड़िया", "फफूंद", "लक्षण"
        ],
        "pest_management": [
            "pest", "insect", "bollworm", "bph", "hopper", "thrips", "aphid", "whitefly", "armyworm", "stem borer", "caterpillar", "mite", "weevil", "larva",
            "పురుగు", "కత్తెర పురుగు", "గులాబీ పురుగు", "సుడిదోమ", "తామర పురుగు", "తెల్లదోమ", "కాండం తొలుచు పురుగు", "పిండి పురుగు", "దోమ", "నల్లి", "కీటకం",
            "कीट", "सुंडी", "फुदका", "तना छेदक", "इल्ली", "माहू", "सफेद मक्खी", "कीड़ा"
        ],
        "weed_management": [
            "weed", "herbicide", "weeds", "post-emergence", "pre-emergence", "pendimethalin", "glyphosate", "de-weeding",
            "కలుపు", "కలుపు మందు", "గడ్డి", "కలుపు నివారణ", "గులకలు",
            "खरपतवार", "खरपतवार नाशक", "घास", "निंदाई", "गुड़ाई"
        ],
        "soil_health": [
            "soil", "black soil", "red soil", "saline", "alkaline", "organic carbon", "soil test", "gypsum", "drainage", "fertility",
            "నేల", "భూసారం", "నల్లరేగడి", "ఎర్ర నేలలు", "సేంద్రియ కర్బనం", "సారవంతం", "సౌడు నేలలు",
            "मिट्टी", "काली मिट्टी", "लाल मिट्टी", "मृदा", "जैविक कार्बन", "जिप्सम", "भूमि सुधार"
        ],
        "fertilizer_soil": [
            "fertilizer", "urea", "dap", "npk", "potash", "zinc", "nitrogen", "phosphorus", "nutrient", "deficiency", "manure", "compost",
            "ఎరువు", "యూరియా", "డిఎపి", "పోషకాలు", "జింక్", "నత్రజని", "భాస్వరం", "పొటాష్", "పోషక లోపం", "పశువుల ఎరువు",
            "खाद", "उर्वरक", "यूरिया", "डीएपी", "पोटाश", "जिंक", "पोषक तत्व", "कमी"
        ],
        "irrigation": [
            "irrigation", "irrigate", "water", "watering", "flooding", "drip", "sprinkler", "critical stage", "moisture",
            "నీరు", "నీటిపారుదల", "తడులు", "తడి", "డ్రిప్", "స్పింక్లర్", "తేమ", "నీటి యాజమాన్యం",
            "सिंचाई", "पानी", "ड्रिप", "नमी"
        ],
        "sowing_cultivation": [
            "sowing", "seed", "seed rate", "seed treatment", "spacing", "land preparation", "season", "variety", "cultivation", "field preparation",
            "విత్తనం", "విత్తే సమయం", "విత్తన శుద్ధి", "విత్తన మోతాదు", "పొలం తయారీ", "రకాలు", "బువ్వ", "దుక్కి", "సాగు పద్ధతులు", "విత్తు", "నాటు",
            "बुवाई", "बीज", "बीज उपचार", "बीज दर", "खेत की तैयारी", "किस्में", "खेती"
        ],
        "pesticide_safety": [
            "pesticide", "waiting period", "dosage", "dose", "phi", "pre-harvest interval", "pre-harvest", "pre harvest", "safety", "precaution", "antidote", "toxicity", "label", "mixing", "emamectin", "coragen",
            "రసాయనం", "పురుగు మందు", "మోతాదు", "వేచి ఉండే కాలం", "భద్రత", "జాగ్రత్తలు", "విరుగుడు", "విషపూరితం", "లేబుల్",
            "कीटनाशक", "दवा", "खुराक", "प्रतीक्षा अवधि", "सुरक्षा", "सावधानी", "विषाक्तता", "लेबल"
        ],
        "mandi_market": [
            "price", "mandi", "market", "rate", "modal price", "apmc", "quintal", "msp",
            "ధర", "రేటు", "మార్కెట్", "మండి", "క్వింటాల్", "మద్దతు ధర",
            "भाव", "मंडी", "दाम", "बाजार", "क्विंटल", "एमएसपी"
        ],
        "weather_advisory": [
            "weather", "rain", "rainfall", "forecast", "storm", "temperature", "climate", "frost",
            "వాతావరణం", "వర్షం", "ఎండ", "తుఫాను", "గాలి", "వర్షపాతం", "వాతావరణ సమాచారం",
            "मौसम", "बारिश", "तापमान", "पूर्वानुमान", "आंधी"
        ],
        "harvesting": [
            "harvest", "harvesting", "maturity", "picking", "storage", "drying", "moisture",
            "కోత", "కోత సమయం", "ఆరబెట్టడం", "నిల్వ", "కళ్ళం", "నూర్పిడి", "చేతికి రావడం",
            "कटाई", "तुड़ाई", "भंडारण", "सुखाना"
        ],
        "crop_rotation": [
            "rotation", "crop rotation", "intercropping", "next crop", "sequence",
            "పంట మార్పిడి", "అంతర పంట", "అనుకూల పంట", "పంటల మార్పిడి",
            "फसल चक्र", "अंतर्वर्ती फसल"
        ]
    }

    TARGET_ENTITIES = {
        # Diseases
        "rice blast": ["rice blast", "blast", "అగ్గితెగులు", "झुलसा", "బ్లాస్ట్"],
        "bacterial blight": ["bacterial blight", "బాక్టీరియా ఆకుమచ్చ", "జీవాణు ఆకుమచ్చ"],
        "sheath blight": ["sheath blight", "మట్ట తెగులు"],
        "leaf curl": ["leaf curl", "ఆకు ముడత", "పెంకి ముడత", "मरोड़िया"],
        "turcicum leaf blight": ["turcicum", "టర్సికం", "ఆకు ఎండు తెగులు"],
        "anthracnose dieback": ["anthracnose", "dieback", "కొమ్మ ఎండు తెగులు"],
        "powdery mildew": ["powdery mildew", "బూడిద తెగులు"],
        "root rot": ["root rot", "వేరు కుళ్ళు"],
        # Pests
        "fall armyworm": ["fall armyworm", "armyworm", "కత్తెర పురుగు", "సైనిక పురుగు", "फॉगी"],
        "pink bollworm": ["pink bollworm", "గులాబీ రంగు పురుగు", "గులాబీ పురుగు", "गुलाबी सुंडी"],
        "whitefly": ["whitefly", "తెల్లదోమ", "తెల్ల దోమ", "सफेद मक्खी"],
        "thrips": ["thrips", "black thrips", "తామర పురుగులు", "నల్ల తామర", "थ्रिप्स"],
        "brown plant hopper": ["brown plant hopper", "bph", "సుడిదోమ", "भूरा फुदका"],
        "stem borer": ["stem borer", "కాండం తొలుచు పురుగు", "તના छेदक"]
    }

    @classmethod
    def detect_language(cls, text: str) -> str:
        # Check Telugu Unicode range (0x0C00 - 0x0C7F)
        telugu_chars = sum(1 for c in text if '\u0c00' <= c <= '\u0c7f')
        # Check Devanagari Unicode range (0x0900 - 0x097F)
        hindi_chars = sum(1 for c in text if '\u0900' <= c <= '\u097f')
        
        if telugu_chars > 2:
            return "te"
        elif hindi_chars > 2:
            return "hi"
        return "en"

    @classmethod
    def analyze_query(
        cls,
        query_text: str,
        context_crop: Optional[str] = None
    ) -> Dict[str, Any]:
        """
        Parses query, extracts crop, target entity, intent, and recommended KB collections.
        """
        lang = cls.detect_language(query_text)
        query_lower = query_text.lower()

        # 1. Extract Crop
        extracted_crop = None
        for crop_name, patterns in cls.CROP_PATTERNS.items():
            for pat in patterns:
                if pat.startswith("\\b"):
                    if re.search(pat, query_lower):
                        extracted_crop = crop_name
                        break
                else:
                    if pat.lower() in query_lower:
                        extracted_crop = crop_name
                        break
            if extracted_crop:
                break

        final_crop = extracted_crop or context_crop

        # 2. Extract Intent / Topic
        matched_intents: List[Tuple[str, int]] = []
        for intent, keywords in cls.INTENT_KEYWORDS.items():
            count = 0
            for kw in keywords:
                if kw.lower() in query_lower:
                    if intent == "harvesting" and kw in ["harvest", "harvesting"] and ("pre-harvest" in query_lower or "pre harvest" in query_lower):
                        continue
                    count += 1
            if count > 0:
                matched_intents.append((intent, count))

        matched_intents.sort(key=lambda x: x[1], reverse=True)

        # 3. Extract Target Entity (Pest/Disease)
        target_entity = None
        for canonical_name, aliases in cls.TARGET_ENTITIES.items():
            for alias in aliases:
                if alias.lower() in query_lower:
                    target_entity = canonical_name
                    break
            if target_entity:
                break

        # Check domain relevance
        has_agri_evidence = bool(extracted_crop or target_entity or matched_intents)
        if has_agri_evidence:
            primary_intent = matched_intents[0][0] if matched_intents else "general_agri"
            recommended_collections = cls.INTENT_COLLECTIONS.get(primary_intent, ["KB21"])
        else:
            primary_intent = "out_of_domain"
            recommended_collections = []

        return {
            "language": lang,
            "crop": final_crop,
            "extracted_crop": extracted_crop,
            "intent": primary_intent,
            "target_entity": target_entity,
            "is_agricultural": has_agri_evidence,
            "recommended_collections": recommended_collections
        }

    @classmethod
    def canonical_crop(cls, crop: Optional[str]) -> Optional[str]:
        if not crop:
            return None
        crop_lower = crop.lower().strip()
        for canonical, patterns in cls.CROP_PATTERNS.items():
            for pat in patterns:
                if pat.startswith(r"\b"):
                    if re.search(pat, crop_lower):
                        return canonical
                elif pat in crop:
                    return canonical
        return crop
