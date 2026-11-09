import re
from typing import Dict, Any, Optional, List, Tuple

class QueryUnderstanding:
    """
    Intelligent Agricultural Query Understanding & Entity Extraction.
    Extracts crop, topic/intent, disease, pest, chemical, and growth stage across Telugu, Hindi, and English.
    """

    CROP_PATTERNS = {
        "Rice": [
            r"\brice\b", r"\bpaddy\b", r"\boryza\b", r"\bdhaan\b", r"\bvari\b",
            "వరి", "వరిలో", "వరిపంట", "వరికి", "ధాన్యం", "వడ్లు", "బియ్యం",
            "धान", "चावल", "धान में", "धान की"
        ],
        "Cotton": [
            r"\bcotton\b", r"\bgossypium\b", r"\bkapas\b", r"\bpatti\b",
            "పత్తి", "ప్రత్తి", "పత్తిలో", "ప్రత్తిలో", "పత్తికి", "ప్రత్తికి", "పత్తిచెట్టు", "దూది",
            "कपास", "कपास में", "कपास की"
        ],
        "Chilli": [
            r"\bchilli\b", r"\bchili\b", r"\bchillies\b", r"\bchilies\b", r"\bcapsicum\b", r"\bmirchi\b", r"\bmirapa\b",
            "మిరప", "మిరపలో", "మిరపకి", "మిర్చి", "మిర్చిలో",
            "मिर्च", "मिर्ची", "मिर्च में", "मिर्च की"
        ],
        "Maize": [
            r"\bmaize\b", r"\bcorn\b", r"\bmakka\b", r"\bmokkajonna\b",
            "మొక్కజొన్న", "మొక్కజొన్నలో", "మొక్కజొన్నకి", "మక్క", "మక్కజొన్న",
            "मक्का", "मक्के", "मक्का में"
        ],
        "Wheat": [
            r"\bwheat\b", r"\btriticum\b", r"\bgehun\b", r"\bgodhuma\b",
            "గోధుమ", "గోధుమలో", "గోధుమకి",
            "गेहूं", "गेंहू", "गेहूं में"
        ],
        "Tomato": [
            r"\btomato\b", r"\btomatoes\b", r"\btamatar\b",
            "టమోటా", "టమాటా", "టమోటాలో", "టమాటాల్లో",
            "टमाटर", "टमाटर में", "टमाटर की"
        ],
        "Groundnut": [
            r"\bgroundnut\b", r"\bpeanut\b", r"\bpalli\b",
            "వేరుశనగ", "వేరుశెనగ", "వేరుశనగలో", "పల్లీ",
            "मूंगफली", "मूंगफली में"
        ],
        "Sugarcane": [
            r"\bsugarcane\b", r"\bganna\b", r"\bcheraku\b",
            "చెరకు", "చెరకులో", "చెరకుకి",
            "गन्ना", "गन्ने"
        ],
        "Red Gram": [
            r"\bred gram\b", r"\bpigeon pea\b", r"\btur\b", r"\barhar\b", r"\bkandi\b",
            "కంది", "కందిలో", "కందికి",
            "अरहर", "तुअर"
        ],
        "Bengal Gram": [
            r"\bbengal gram\b", r"\bchickpea\b", r"\bchana\b", r"\bsenaga\b",
            "శనగ", "శనగలో", "హరిమంత",
            "चना", "चने"
        ],
        "Soybean": [
            r"\bsoybean\b", r"\bsoya\b",
            "సోయాబీన్", "సోయా",
            "सोयाबीन", "सोया"
        ],
        "Turmeric": [
            r"\bturmeric\b", r"\bcurcuma\b", r"\bhaldi\b", r"\bpasupu\b",
            "పసుపు", "పసుపులో", "పసుపుకి",
            "हल्दी", "हल्दी में"
        ],
        "Mango": [
            r"\bmango\b", r"\bmangoes\b", r"\bmangifera\b", r"\baam\b", r"\bmamidi\b",
            "మామిడి", "మామిడిలో", "మామిడికి", "మామిడిచెట్టు",
            "आम", "आम में"
        ],
        "Onion": [
            r"\bonion\b", r"\bonions\b", r"\ballium\b", r"\bpyaz\b", r"\bulli\b",
            "ఉల్లి", "ఉల్లిపాయ", "ఉల్లిలో", "ఉల్లిగడ్డ",
            "प्याज"
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
        ],
        "organic_farming": [
            "organic", "natural farming", "jeevamrit", "beejamrit", "neemastram", "brahmastram", "panchagavya", "vermicompost", "biofertilizer", "desi cow",
            "సేంద్రియ", "సేంద్రీయ", "జీవామృతం", "బీజామృతం", "నీమాస్త్రం", "బ్రహ్మాస్త్రం", "పంచగవ్య", "వర్మీ కంపోస్ట్", "ప్రకృతి వ్యవసాయం",
            "जैविक", "प्राकृतिक खेती", "जीवामृत", "बीजामृत", "नीमास्त्र", "ब्रह्मास्त्र", "पंचगव्य", "वर्मीकंपोस्ट", "देसी गाय"
        ]
    }

    AMBIGUOUS_SYMPTOM_PATTERNS = {
        "yellowing": [
            r"\byellow\b", r"\byellowing\b", r"\bpale yellow\b", r"\bchlorosis\b",
            "పసుపు", "పసుపు రంగు", "ఆకులు పసుపు", "పసుపుగా", "పసుపు పచ్చగా",
            "पीला", "पीलापन", "पीली", "पीले", "पत्तियां पीली"
        ],
        "leaf_spots": [
            r"\bspots\b", r"\bspotting\b", r"\bdots\b",
            "మచ్చలు", "ఆకుమచ్చలు", "చిన్న మచ్చలు",
            "धब्बे", "दाग", "निशान"
        ],
        "leaf_curling": [
            r"\bcurling\b", r"\bcurled\b", r"\bwrinkled\b",
            "ముడత", "ముడుచుకు", "ముడుచుకున్నాయి", "ఆకు ముడత",
            "मुड़ना", "मुड़ रही", "मरोड़", "पत्ती मुड़"
        ],
        "wilting_drying": [
            r"\bwilting\b", r"\bdrying\b", r"\bdrooping\b",
            "వాడిపోవడం", "ఎండిపోవడం", "వాడిపోతున్నాయి", "ఎండిపోతున్నాయి",
            "सूखना", "मुरझाना", "मुरझा रही", "सूख रही"
        ]
    }

    GENERIC_TERMS = {
        "spray", "spraying", "water", "watering", "leaf", "leaves", "crop", "crops",
        "plant", "plants", "field", "farm", "farmer", "dose", "dosage", "chemical", "medicine",
        "tell", "give", "how", "what", "can", "should", "problem", "remedy",
        "to", "on", "in", "of", "the", "a", "an", "is", "for", "with", "at", "by", "from", "and", "or", "so", "be", "do", "it",
        "my", "me", "we", "our", "you", "your", "they", "this", "that", "these", "those",
        "స్ప్రే", "పిచికారీ", "నీరు", "నీళ్ళు", "ఆకు", "ఆకులు", "పంట", "మొక్క", "చెట్టు",
        "మందు", "మందులు", "మోతాదు", "రసాయనం", "సమస్య", "నివారణ", "ఎలా", "ఏమిటి", "చెప్పండి",
        "కి", "కు", "లో", "పై", "మీద", "నుంచి", "నుండి", "కోసం", "తో", "వల్ల", "వలన", "ఈ", "ఆ", "నా", "మా",
        "स्प्रे", "छिड़काव", "पानी", "पत्ता", "पत्ती", "फसल", "पौधा", "दवा", "दवाई", "खुराक", "समस्या", "उपाय", "बताएं",
        "के", "की", "का", "को", "में", "पर", "से", "लिए", "और", "या", "यह", "वह", "इस", "उस", "मेरा", "मेरी"
    }

    TARGET_ENTITIES = {
        # Diseases
        "rice blast": ["rice blast", "blast", "అగ్గితెగులు", "झुलसा", "బ్లాస్ట్"],
        "late blight": ["late blight", "early blight", "blight", "లేట్ బ్లైట్", "లెక్క తెగులు", "లేట్ బ్లైట్ తెగులు", "लेट ब्लाइट", "झुलसा", "पछेती झुलसा", "अगेती झुलसा"],
        "tikka leaf spot": ["tikka", "tikka leaf spot", "సర్కోస్పోరా", "టిక్కా తెగులు", "టిక్కా", "टिक्का", "टिक्का रोग", "पत्ती धब्बा"],
        "bacterial blight": ["bacterial blight", "బాక్టీరియా ఆకుమచ్చ", "జీవాణు ఆకుమచ్చ"],
        "sheath blight": ["sheath blight", "మట్ట తెగులు"],
        "leaf curl": ["leaf curl", "ఆకు ముడత", "పెంకి ముడత", "मरोड़िया"],
        "turcicum leaf blight": ["turcicum", "టర్సికం", "ఆకు ఎండు తెగులు"],
        "anthracnose dieback": ["anthracnose", "dieback", "కొమ్మ ఎండు తెగులు"],
        "powdery mildew": ["powdery mildew", "బూడిద తెగులు"],
        "rust": ["yellow rust", "brown rust", "stripe rust", "rust", "కుంకుమ తెగులు", "రస్ట్", "रतुआ", "पीला रतुआ", "गेरुआ"],
        "root rot": ["root rot", "వేరు కుళ్ళు"],
        # Pests
        "fall armyworm": ["fall armyworm", "armyworm", "కత్తెర పురుగు", "సైనిక పురుగు", "फॉगी"],
        "pink bollworm": ["pink bollworm", "గులాబీ రంగు పురుగు", "గులాబీ పురుగు", "गुलाबी सुंडी"],
        "pod borer": ["pod borer", "fruit borer", "gram pod borer", "శనగ పచ్చ పురుగు", "కాయతొలుచు పురుగు", "కాయ తొలుచు పురుగు", "फली छेदक", "फल छेदक", "हेलिकोवर्पा"],
        "bollworm": ["bollworm", "american bollworm", "spotted bollworm", "పత్తి కాయ పురుగు", "కాయ పురుగు", "బోల్ వార్మ్"],
        "whitefly": ["whitefly", "తెల్లదోమ", "తెల్ల దోమ", "सफेद मक्खी"],
        "thrips": ["thrips", "black thrips", "తామర పురుగులు", "నల్ల తామర", "थ्रिप्स"],
        "brown plant hopper": ["brown plant hopper", "bph", "సుడిదోమ", "भूरा फुदका"],
        "stem borer": ["stem borer", "కాండం తొలుచు పురుగు", "तना छेदक"],
        "aphid": ["aphid", "aphids", "పేనుబంక", "మాహు", "माहू", "चेपा"],
        "mite": ["mite", "mites", "red spider mite", "ఎర్ర నల్లి", "నల్లి", "लाल मकड़ी", "माइट"]
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
        context_crop: Optional[str] = None,
        conversation_history: Optional[List[Dict[str, Any]]] = None
    ) -> Dict[str, Any]:
        """
        Parses query, extracts crop entities, target entities, intent, and recommended KB collections.
        Supports multi-turn conversational crop continuity and ambiguity detection.
        """
        lang = cls.detect_language(query_text)
        query_lower = query_text.lower()

        # 1. Extract Crop Entities from current query
        detected_crops: List[str] = []
        for crop_name, patterns in cls.CROP_PATTERNS.items():
            for pat in patterns:
                matched = False
                if pat.startswith("\\b"):
                    if re.search(pat, query_lower):
                        matched = True
                else:
                    if pat.lower() in query_lower:
                        matched = True
                if matched:
                    if crop_name not in detected_crops:
                        detected_crops.append(crop_name)
                    break

        final_crop: Optional[str] = None
        crop_source = "unknown"

        if len(detected_crops) == 1:
            final_crop = detected_crops[0]
            crop_source = "detected"
        elif len(detected_crops) > 1:
            final_crop = detected_crops[0]
            crop_source = "detected_multiple"
        else:
            # Check conversation history for previously established crop
            history_crop = None
            if conversation_history:
                # Traverse recent turns in reverse order
                for turn in reversed(conversation_history[-6:]):
                    t_crop = turn.get("crop") or turn.get("crop_name") or turn.get("crop_identified")
                    if t_crop:
                        c_canon = cls.canonical_crop(t_crop)
                        if c_canon:
                            history_crop = c_canon
                            break
                    # Also scan message text of recent turn
                    t_text = (turn.get("text") or turn.get("query_text") or turn.get("query") or "").lower()
                    if t_text:
                        for crop_name, patterns in cls.CROP_PATTERNS.items():
                            for pat in patterns:
                                if pat.startswith("\\b"):
                                    if re.search(pat, t_text):
                                        history_crop = crop_name
                                        break
                                elif pat.lower() in t_text:
                                    history_crop = crop_name
                                    break
                            if history_crop:
                                break
                    if history_crop:
                        break

            if history_crop:
                final_crop = history_crop
                crop_source = "history_inferred"
            elif context_crop:
                final_crop = context_crop
                crop_source = "explicit"
            else:
                final_crop = None
                crop_source = "unknown"

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
        has_agri_evidence = bool(detected_crops or target_entity or matched_intents)
        if has_agri_evidence:
            primary_intent = matched_intents[0][0] if matched_intents else "general_agri"
            recommended_collections = cls.INTENT_COLLECTIONS.get(primary_intent, ["KB21"])
        else:
            primary_intent = "out_of_domain"
            recommended_collections = []

        # 4. Ambiguous Symptoms Extraction
        detected_ambiguous_symptoms: List[str] = []
        for sym_type, patterns in cls.AMBIGUOUS_SYMPTOM_PATTERNS.items():
            for pat in patterns:
                matched = False
                if pat.startswith("\\b"):
                    if re.search(pat, query_lower):
                        matched = True
                else:
                    if pat.lower() in query_lower:
                        matched = True
                if matched:
                    if sym_type not in detected_ambiguous_symptoms:
                        detected_ambiguous_symptoms.append(sym_type)
                    break

        has_ambiguous_symptoms = bool(detected_ambiguous_symptoms) and (target_entity is None)

        # 5. Generic-Only Query Detection
        words_in_q = [w for w in re.findall(r'[\u0c00-\u0c7f]+|[\u0900-\u097f]+|[a-zA-Z0-9]+', query_lower) if len(w) >= 2]
        substantive_words = [w for w in words_in_q if w not in cls.GENERIC_TERMS]
        is_generic_query = (len(words_in_q) > 0 and len(substantive_words) == 0 and not target_entity and not detected_crops)

        # 6. Ambiguity Evaluation
        # If intent is heavily crop-specific (disease, pest, pesticide dose, sowing, yield, spacing) but no crop was identified or in history
        is_ambiguous = False
        clarification_prompt = None
        crop_dependent_intents = {"crop_disease", "pest_management", "sowing_cultivation", "harvesting", "pesticide_safety"}
        if final_crop is None and primary_intent in crop_dependent_intents and not target_entity:
            is_ambiguous = True
            if lang == "te":
                clarification_prompt = "మీరు ఏ పంట గురించి అడుగుతున్నారు? (ఉదా: పత్తి, వరి, మిరప, టమోటా, మొక్కజొన్న)"
            elif lang == "hi":
                clarification_prompt = "आप किस फसल के बारे में पूछ रहे हैं? (उदा: कपास, धान, मिर्च, टमाटर, मक्का)"
            else:
                clarification_prompt = "Which crop are you referring to? (e.g. Cotton, Rice, Chilli, Tomato, Maize)"
        elif is_generic_query:
            is_ambiguous = True
            if lang == "te":
                clarification_prompt = "దయచేసి మీ పంట పేరు మరియు ఆకులపై కనిపిస్తున్న నిర్దిష్ట లక్షణాలు లేదా పురుగులను వివరంగా చెప్పండి."
            elif lang == "hi":
                clarification_prompt = "कृपया अपनी फसल का नाम और पत्तियों पर दिखने वाले विशिष्ट लक्षण या कीट का विवरण बताएं।"
            else:
                clarification_prompt = "Please specify your crop name and the exact symptoms or pests you observe on the leaves."
        elif has_ambiguous_symptoms:
            # Symptoms are mentioned but multiple causes exist (differential diagnosis required)
            sym = detected_ambiguous_symptoms[0] if detected_ambiguous_symptoms else "symptoms"
            if sym == "yellowing":
                if lang == "te":
                    clarification_prompt = "ఆకులు పసుపు రంగులోకి మారడానికి పోషక లోపం (జింక్/నత్రజని/ఇనుము), నీటి ఎద్దడి లేదా రసం పీల్చే పురుగులు కారణం కావచ్చు. ఆకుల ఈనెలు పచ్చగా ఉండి మధ్య భాగం పసుపుగా ఉందా? ఆకుల అడుగున పురుగులు ఏమైనా ఉన్నాయా?"
                elif lang == "hi":
                    clarification_prompt = "पत्तियों का पीला पड़ना पोषक तत्वों की कमी (जिंक/नाइट्रोजन/लोहा), जल भराव या रस चूसक कीटों के कारण हो सकता है। क्या पत्तियों की नसें हरी हैं और बीच का भाग पीला है, या पत्ती के नीचे कीट हैं?"
                else:
                    clarification_prompt = "Leaf yellowing can be caused by nutrient deficiency (Zinc/Iron/Nitrogen), water stress, or sap-sucking pests. Are leaf veins green while tissue turns yellow, or are there insects under the leaves?"
            elif sym == "leaf_spots":
                if lang == "te":
                    clarification_prompt = "ఆకుమచ్చలు శిలీంధ్ర లేదా బాక్టీరియా వల్ల రావచ్చు. మచ్చల చుట్టూ పసుపు రంగు వలయం ఉందా, లేదా మచ్చలు కోణాకారంలో ఉన్నాయా? పంట ఏ దశలో ఉంది?"
                elif lang == "hi":
                    clarification_prompt = "पत्ती के धब्बे फफूंद या जीवाणु से हो सकते हैं। क्या धब्बों के चारों ओर पीला घेरा है, या धब्बे कोणीय हैं? फसल किस अवस्था में है?"
                else:
                    clarification_prompt = "Leaf spots may be fungal or bacterial. Do the spots have a yellow halo or concentric rings, and what is the crop growth stage?"
            elif sym == "leaf_curling":
                if lang == "te":
                    clarification_prompt = "ఆకు ముడత తామర పురుగులు, తెల్లదోమ లేదా వైరస్ వల్ల రావచ్చు. ఆకులు పైకి ముడుచుకుంటున్నాయా లేక కిందికి ముడుచుకుంటున్నాయా?"
                elif lang == "hi":
                    clarification_prompt = "पत्तियों का मुड़ना थ्रिप्स, सफेद मक्खी या वायरस से हो सकता है। क्या पत्तियां ऊपर की ओर मुड़ रही हैं या नीचे की ओर?"
                else:
                    clarification_prompt = "Leaf curl can be caused by thrips, whiteflies, or virus. Are the leaves curling upward (boat-shaped) or downward?"

        return {
            "language": lang,
            "crop": final_crop,
            "detected_crops": detected_crops,
            "crop_source": crop_source,
            "intent": primary_intent,
            "target_entity": target_entity,
            "is_agricultural": has_agri_evidence,
            "recommended_collections": recommended_collections,
            "is_ambiguous": is_ambiguous,
            "has_ambiguous_symptoms": has_ambiguous_symptoms,
            "ambiguous_symptoms": detected_ambiguous_symptoms,
            "is_generic_query": is_generic_query,
            "clarification_prompt": clarification_prompt
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
