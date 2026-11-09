package com.bolkisan.farmer.domain

import android.util.Log
import java.util.regex.Pattern

data class OfflineSafetyResult(
    val isSafe: Boolean,
    val needsEscalation: Boolean,
    val escalationReason: String? = null,
    val severity: String = "LOW", // "CRITICAL", "HIGH", "MEDIUM", "LOW"
    val sanitizedResponse: String,
    val warnings: List<String> = emptyList(),
    val detectedBannedChemical: String? = null,
    val isTankMixDetected: Boolean = false,
    val isDosageVerified: Boolean = false
)

/**
 * Diagnostic evaluation result for farmer symptom queries.
 * Prevents premature single pest/disease claims on ambiguous symptoms.
 */
data class SymptomEvaluation(
    val isAmbiguous: Boolean,
    val symptomCategory: String? = null, // "yellowing", "curling", "wilting", "generic_pest", "leaf_spots"
    val detectedSymptom: String? = null,
    val ambiguityReason: String? = null,
    val differentialDiagnoses: List<String> = emptyList(),
    val diagnosticQuestions: List<String> = emptyList(),
    val guidedClarificationResponse: String? = null
)

/**
 * Production Central Safety Controller for Offline Bol Kisan AI.
 * Enforces the Non-Negotiable Agricultural Safety Mandate:
 * Zero SLM Discretion on Pesticides, Banned Agrochemicals, and Unapproved Tank Mixtures.
 */
object OfflineSafetyGate {

    private const val TAG = "OfflineSafetyGate"

    private fun logWarn(tag: String, msg: String) {
        try {
            Log.w(tag, msg)
        } catch (_: Throwable) {
            println("[$tag WARN] $msg")
        }
    }

    data class BannedChemicalRule(
        val name: String,
        val aliases: List<String>,
        val isTotalBan: Boolean = true,
        val legalCitation: String,
        val hazardDescription: String
    )

    // Explicit Blacklist of Banned & Restricted Agrochemicals in India
    val BANNED_AGROCHEMICALS = listOf(
        BannedChemicalRule(
            name = "Endosulfan",
            aliases = listOf("endosulfan", "thiodan", "thionex", "phasal", "endocel", "ఎండోసల్ఫాన్", "తియోడాన్", "एंडोसल्फान", "थायोडान"),
            isTotalBan = true,
            legalCitation = "Supreme Court of India Order (W.P. No. 213/2011) & CIBRC Notification",
            hazardDescription = "Extremely hazardous Persistent Organic Pollutant (POP), severe birth defects and neurotoxic poisoning."
        ),
        BannedChemicalRule(
            name = "Methyl Parathion",
            aliases = listOf("methyl parathion", "metacid", "parathion-methyl", "folidol", "మెథైల్ పారాథియాన్", "మెటాసిడ్", "मिथाइल पैराथियान", "मेटासिड"),
            isTotalBan = true,
            legalCitation = "Govt of India Gazette Notification S.O. 3951(E)",
            hazardDescription = "WHO Class Ia extremely hazardous organophosphate with fatal acute toxicity."
        ),
        BannedChemicalRule(
            name = "Monocrotophos",
            aliases = listOf("monocrotophos", "nuvacron", "monocil", "corophos", "phoskill", "మోనోక్రోటోఫాస్", "నువాక్రాన్", "मोनोक्रोटोफॉस", "नुवाक्रोन"),
            isTotalBan = false, // Strictly BANNED on Vegetables
            legalCitation = "CIBRC / Ministry of Agriculture Statutory Order & Gazette S.O. 2486(E)",
            hazardDescription = "WHO Class Ib highly hazardous systemic insecticide. Strictly BANNED on all vegetables by CIBRC."
        ),
        BannedChemicalRule(
            name = "Paraquat Dichloride",
            aliases = listOf("paraquat", "paraquat dichloride", "gramoxone", "పారాక్వాట్", "గ్రామోక్సోన్", "पैराक्वाट", "ग्रामोक्सोन"),
            isTotalBan = false, // Strictly Restricted
            legalCitation = "CIBRC Restricted Use Mandate & State Gazettes",
            hazardDescription = "Extremely lethal non-selective contact herbicide with NO known antidote. Strictly prohibited on vegetables and food crops without hood."
        ),
        BannedChemicalRule(
            name = "Phorate",
            aliases = listOf("phorate", "thimet", "phorate 10g", "ఫోరేట్", "థైమెట్", "फोरेट", "थिमेट"),
            isTotalBan = true,
            legalCitation = "Govt of India Gazette S.O. 2486(E) / Phased Total Ban",
            hazardDescription = "WHO Class Ia extremely hazardous granular toxicant with acute lethal dermal poisoning risk."
        ),
        BannedChemicalRule(
            name = "Phosphamidon",
            aliases = listOf("phosphamidon", "dimecron", "ఫాస్ఫామిడాన్", "డైమెక్రాన్", "फॉस्फेमिडॉन", "डाइमेक्रॉन"),
            isTotalBan = true,
            legalCitation = "Govt of India Gazette Notification S.O. 3951(E)",
            hazardDescription = "WHO Class Ia extremely hazardous anticholinesterase systemic organophosphate."
        ),
        BannedChemicalRule(
            name = "DDT",
            aliases = listOf("ddt", "dichloro-diphenyl-trichloroethane", "డిడిటి", "डीडीटी"),
            isTotalBan = true,
            legalCitation = "Insecticides Act, 1968 Agricultural Prohibition & Stockholm Convention",
            hazardDescription = "Persistent bioaccumulative chlorinated hydrocarbon completely prohibited in Indian agriculture."
        ),
        BannedChemicalRule(
            name = "Aldicarb",
            aliases = listOf("aldicarb", "temik", "ఆల్డికార్బ్", "టెమిక్", "एल्डिकार्ब"),
            isTotalBan = true,
            legalCitation = "Govt of India Agricultural Prohibition Order",
            hazardDescription = "WHO Class Ia extremely hazardous carbamate nematocide."
        ),
        BannedChemicalRule(
            name = "Dichlorvos",
            aliases = listOf("dichlorvos", "ddvp", "nuvan", "డిడివిపి", "నువాన్", "डीडीवीपी", "नुवान"),
            isTotalBan = true,
            legalCitation = "Govt of India Gazette S.O. 2486(E)",
            hazardDescription = "Volatile neurotoxic organophosphate causing severe respiratory poisoning."
        ),
        BannedChemicalRule(
            name = "Diazinon",
            aliases = listOf("diazinon", "basudin", "డయాజినాన్", "डायजिनॉन"),
            isTotalBan = true,
            legalCitation = "CIBRC Banned Agrochemical Registry",
            hazardDescription = "Banned organophosphate insecticide in Indian agriculture."
        )
    )

    val VEGETABLE_CROPS = setOf(
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
    )

    private val TANK_MIX_PATTERNS = listOf(
        Pattern.compile("""\b(?:mix|mixing|tank\s*mix|tank-mix|tank-mixed|cocktail|combine)\b.*\b(?:and|\+|with|in\s*the\s*same)\b""", Pattern.CASE_INSENSITIVE),
        Pattern.compile("""\b\w+\s*\+\s*\w+\b""", Pattern.CASE_INSENSITIVE),
        Pattern.compile("""(?:కలిపి\s*పిచికారీ|మందులు\s*కలప|రెండు\s*మందులు|ఒకే\s*ట్యాంకులో\s*కలిపి|ఒకేసారి\s*\d+\s*రకాల|కలిపి\s*కొట్ట)"""),
        Pattern.compile("""(?:मिलाकर\s*छिड़काव|दवाइयों\s*का\s*मिश्रण|टैंक\s*मिक्स|एक\s*ही\s*स्प्रे\s*पंप\s*में\s*मिला|अलग-अलग\s*कीटनाशकों\s*का\s*कॉकटेल|कॉकटेल)""")
    )

    private val DOSAGE_PATTERN = Pattern.compile(
        """\b(\d+(?:\.\d+)?)\s*(ml|g|gm|grams|kg|litres|l|మి\.లీ|గ్రా|లీటరు|मिली|ग्राम|लीटर)(?:/|\s*per|\s*ప్రతి|\s*प्रति)\s*(?:l|litre|pump|tank|acre|లీటరు|పంప్|ఎకర|लीटर|पंप|एकड़)\b""",
        Pattern.CASE_INSENSITIVE
    )

    // Ambiguous symptom query patterns (Farmer presents insufficient symptoms)
    private val YELLOWING_PATTERNS = listOf(
        Pattern.compile("""(?:ఆకులు\s*పసుపు|పసుపుగా\s*మారు|పసుపు\s*ఆకులు|పసుపు\s*రంగు|పసుపు)"""),
        Pattern.compile("""(?:पीले\s*पत्ते|पत्तियां\s*पीली|पत्ती\s*पीली|पीला\s*पड़|पीले\s*हो\s*रहे|पीला)"""),
        Pattern.compile("""(?:\byellow\w*|\bchlorosis)""", Pattern.CASE_INSENSITIVE)
    )

    private val CURLING_PATTERNS = listOf(
        Pattern.compile("""(?:ఆకులు\s*ముడుచుకు|ముడుచుకుంటున్నాయి|ముడత|ఆకు\s*ముడత|ముడుచుకుపోవడం)"""),
        Pattern.compile("""(?:पत्तियां\s*मुड़|पत्ता\s*मरोड़|पत्तियां\s*सिकुड़|मुड़े\s*हुए\s*पत्ते|मरोड़िया)"""),
        Pattern.compile("""(?:\bcurl\w*|\bleaf\s*curl)""", Pattern.CASE_INSENSITIVE)
    )

    private val WILTING_PATTERNS = listOf(
        Pattern.compile("""(?:మొక్కలు\s*ఎండిపో|ఎండిపోతున్నాయి|ఎండిపోవడం|వాడిపోతున్నాయి|మొక్కలు\s*వాడిపో|ఎండిపోవు|వాడిపో)"""),
        Pattern.compile("""(?:पौधे\s*सूख|पौधे\s*मुरझा|सूख\s*रहे|मुरझा\s*रहे|मुरझाना|सूखना)"""),
        Pattern.compile("""(?:\bwilt\w*|\bdry\w*\s*up)""", Pattern.CASE_INSENSITIVE)
    )

    private val GENERIC_PEST_PATTERNS = listOf(
        Pattern.compile("""(?:పురుగు\s*పట్టింది|పురుగు\s*ఆశించింది|పురుగులు\s*ఉన్నాయి|పురుగుల\s*సమస్య|పురుగు\s*బాధ)"""),
        Pattern.compile("""(?:कीट\s*लगा\s*है|कीड़े\s*लग\s*रहे|कीट\s*का\s*प्रकोप|कीड़ा\s*लग\s*गया|कीड़े\s*पड़\s*गए)"""),
        Pattern.compile("""\b(?:insects?|pests?|bugs?|worms?)\b""", Pattern.CASE_INSENSITIVE)
    )

    // Specific diagnostic evidence markers that distinguish definitive diagnostic queries from ambiguous queries
    private val SPECIFIC_EVIDENCE_PATTERNS = listOf(
        Pattern.compile("""\b(?:thrips?|aphids?|whitefl(?:y|ies)|jassids?|bollworms?|pink\s*bollworms?|spodoptera|stem\s*borers?|leafhoppers?|fall\s*armyworms?|caterpillars?|mites?|bph|brown\s*planthoppers?|root\s*grubs?|cutworms?|mealybugs?|weevils?|pod\s*borers?)\b""", Pattern.CASE_INSENSITIVE),
        Pattern.compile("""(?:తామర\s*పురుగు|పేనుబంక|తెల్లదోమ|పచ్చదోమ|గులాబీ\s*రంగు|లద్దె\s*పురుగు|కాండం\s*తొలుచు|కత్తెర\s*పురుగు|నల్లి|ఎర్ర\s*నల్లి|సుడి\s*దోమ|పిండి\s*పురుగు|కాయ\s*తొలుచు)"""),
        Pattern.compile("""(?:थ्रिप्स|एफिड|माहू|चेपा|सफेद\s*मक्खी|हरा\s*मच्छर|गुलाबी\s*सुंडी|इल्ली|तना\s*छेदक|फॉल\s*आर्मीवर्म|सफेद\s*लट|दीमक|मिलीबग|फली\s*छेदक)"""),
        Pattern.compile("""\b(?:powdery\s*mildew|downy\s*mildew|blast|blight|bacterial\s*blight|sheath\s*blight|tikka|rust|fusarium|collar\s*rot|root\s*rot|anthracnose|cercospora|alternaria|mosaic(?:\s*virus)?|dieback)\b""", Pattern.CASE_INSENSITIVE),
        Pattern.compile("""(?:బూడిద\s*తెగులు|అగ్గి\s*తెగులు|ఎండ్ర\s*తెగులు|మచ్చ\s*తెగులు|టిక్కా\s*తెగులు|తుప్పు\s*తెగులు|వేరు\s*కుళ్ళు|కాండం\s*కుళ్ళు|చిగురు\s*ఎండు)"""),
        Pattern.compile("""(?:चूर्णिल\s*आसिता|झुलसा|ब्लाइट|टिक्का|रतुआ|जड़\s*गलन|उकठा|तना\s*गलन|मोज़ेक)"""),
        Pattern.compile("""\b(?:interveinal|veins\s*(?:are\s*)?green|boat\s*shaped|downward\s*curl|dead\s*heart|white\s*earhead|underside\s*of\s*leaf|under\s*the\s*leaf|lower\s*leaves\s*only|top\s*leaves\s*only|hopper\s*burn)\b""", Pattern.CASE_INSENSITIVE),
        Pattern.compile("""(?:నరాల\s*మధ్య|ఈనెలు\s*పచ్చగా|పై\s*ముడత|కింది\s*ముడత|ఆకు\s*అడుగున|మొదలు\s*కుళ్ళు)"""),
        Pattern.compile("""(?:नसों\s*के\s*बीच|ऊपर\s*की\s*ओर|नीचे\s*की\s*ओर|पत्ती\s*के\s*नीचे|तना\s*चीरने\s*पर)""")
    )

    fun detectLanguage(text: String, defaultLang: String = "en"): String {
        val hasTelugu = text.any { it in '\u0C00'..'\u0C7F' }
        if (hasTelugu) return "te"
        val hasHindi = text.any { it in '\u0900'..'\u097F' }
        if (hasHindi) return "hi"
        return if (defaultLang in listOf("te", "hi", "en")) defaultLang else "en"
    }

    const val CALIBRATION_GUIDANCE_EN =
        "Knapsack spray volume depends on crop canopy and stage (150–250 L/acre). Consult an Agricultural Extension Officer or Krishi Vigyan Kendra (KVK) to calibrate your pump's nozzle discharge and water volume before mixing."

    const val CALIBRATION_GUIDANCE_TE =
        "నాప్‌సాక్ పంపు నీటి పరిమాణం పంట దశ మరియు విస్తృతిపై ఆధారపడి ఉంటుంది (ఎకరాకు 150–250 లీటర్లు). మందు కలపడానికి ముందు మీ పంపు నాజిల్ డిశ్చార్జ్ మరియు నీటి పరిమాణాన్ని సరిచూసుకోవడానికి (క్యాలిబ్రేట్ చేయడానికి) వ్యవసాయ విస్తరణ అధికారి (AEO) లేదా కృషి విజ్ఞాన కేంద్రం (KVK) ని సంప్రదించండి."

    const val CALIBRATION_GUIDANCE_HI =
        "नैपसैक स्प्रेयर पानी की मात्रा फसल के फैलाव और अवस्था पर निर्भर करती है (150–250 लीटर/एकड़)। दवा घोलने से पहले अपने पंप के नोजल डिस्चार्ज और पानी की मात्रा के कैलिब्रेशन हेतु कृषि विस्तार अधिकारी या कृषि विज्ञान केंद्र (KVK) से संपर्क करें।"

    /**
     * Standard CIBRC / ICAR Knapsack Dilution calculation.
     * Rule: NEVER assume a universal water volume per acre (e.g. assuming 200L for all crops/stages is wrong).
     * Calculates pump-level quantities ONLY when supported by verified spray volume per acre or explicit dilution.
     * Defaults to 0.0 (unverified), returning 0.0 if water volume is unverified or non-positive.
     */
    fun calculateKnapsackDose(
        dosePerAcre: Double,
        waterVolumeLitresPerAcre: Double = 0.0,
        pumpCapacityLitres: Double = 15.0
    ): Double {
        if (waterVolumeLitresPerAcre <= 0.0) return 0.0
        return (dosePerAcre / waterVolumeLitresPerAcre) * pumpCapacityLitres
    }

    /**
     * Standard knapsack dilution calculation from explicit dilution per litre (e.g. 2 ml/L -> 30 ml per 15L pump).
     */
    fun calculateKnapsackDoseFromDilution(
        dilutionPerLitre: Double,
        pumpCapacityLitres: Double = 15.0
    ): Double {
        if (dilutionPerLitre <= 0.0) return 0.0
        return dilutionPerLitre * pumpCapacityLitres
    }

    /**
     * Checks if text contains verified spray dilution (e.g. 2ml/L) or explicit required spray volume per acre/ha.
     */
    fun hasExplicitSprayVolumeOrDilution(text: String): Boolean {
        val lower = text.lowercase()
        val hasDilution = Regex("""\b\d+(?:\.\d+)?\s*(?:ml|g|gm|grams)\s*(?:/|\s*per|\s*ప్రతి|\s*प्रति)\s*(?:l|litre|liter|లీటరు|लीटर)\b""", RegexOption.IGNORE_CASE).containsMatchIn(text)
        val hasExplicitWaterVolume = (lower.contains("water") || lower.contains("నీటి") || lower.contains("पानी")) &&
            (Regex("""\b\d{2,4}\s*(?:l|lit|liter|litres|liters|లీటర్|लीटर)""", RegexOption.IGNORE_CASE).containsMatchIn(text)) &&
            (lower.contains("acre") || lower.contains("ఎకర") || lower.contains("एकड़") || lower.contains("/ha") || lower.contains("hectare"))
        val hasExplicitPumpDose = (lower.contains("15l") || lower.contains("15-litre") || lower.contains("15 l") || lower.contains("15 లీటర్ల") || lower.contains("15 लीटर")) &&
            (lower.contains("pump") || lower.contains("knapsack") || lower.contains("పంపు") || lower.contains("पंप")) &&
            (hasDilution || hasExplicitWaterVolume)
        return hasDilution || hasExplicitWaterVolume || hasExplicitPumpDose
    }

    /**
     * Formats dosage stated in knapsack pump terms (15-litre backpack pump).
     * If water volume is unverified (<= 0.0), refuses naive knapsack conversion,
     * states the verified per-acre rate explicitly, and provides official KVK calibration guidance.
     */
    fun formatKnapsackDoseText(
        dosePerAcre: Double,
        unit: String = "g",
        waterVolumeLitresPerAcre: Double = 0.0,
        pumpCapacityLitres: Double = 15.0,
        language: String = "en"
    ): String {
        if (waterVolumeLitresPerAcre <= 0.0) {
            val acreRate = if (dosePerAcre == dosePerAcre.toLong().toDouble()) "${dosePerAcre.toLong()} $unit/acre" else "$dosePerAcre $unit/acre"
            return when (language) {
                "te" -> "ధృవీకరించిన ఎకరా మోతాదు: $acreRate. $CALIBRATION_GUIDANCE_TE"
                "hi" -> "प्रमाणित प्रति एकड़ मात्रा: $acreRate. $CALIBRATION_GUIDANCE_HI"
                else -> "Verified per-acre rate: $acreRate. $CALIBRATION_GUIDANCE_EN"
            }
        }
        val pumpDose = calculateKnapsackDose(dosePerAcre, waterVolumeLitresPerAcre, pumpCapacityLitres)
        val formatted = if (pumpDose == pumpDose.toLong().toDouble()) {
            pumpDose.toLong().toString()
        } else {
            String.format(java.util.Locale.US, "%.1f", pumpDose)
        }
        val volStr = if (waterVolumeLitresPerAcre == waterVolumeLitresPerAcre.toLong().toDouble()) "${waterVolumeLitresPerAcre.toLong()}" else "$waterVolumeLitresPerAcre"
        return when (language) {
            "te" -> "ఎకరాకు $volStr లీటర్ల నీటిలో: 15 లీటర్ల పంపుకు $formatted $unit"
            "hi" -> "प्रति एकड़ $volStr लीटर पानी में: 15 लीटर पंप के लिए $formatted $unit"
            else -> "In $volStr L water per acre: $formatted $unit per 15-litre pump"
        }
    }

    /**
     * Evaluates whether a farmer query presents sufficient diagnostic symptoms
     * or is too ambiguous to permit a definitive single pest/disease claim.
     */
    fun evaluateSymptomSufficiency(
        query: String,
        crop: String = "",
        language: String? = null
    ): SymptomEvaluation {
        val lang = language?.takeIf { it.isNotBlank() } ?: detectLanguage(query, "en")

        // Check if query has specific diagnostic evidence (specific pest species, disease, morphology)
        val hasSpecificEvidence = SPECIFIC_EVIDENCE_PATTERNS.any { it.matcher(query).find() }
        if (hasSpecificEvidence) {
            return SymptomEvaluation(isAmbiguous = false)
        }

        // Check for ambiguous symptom categories
        val isYellowing = YELLOWING_PATTERNS.any { it.matcher(query).find() }
        val isCurling = CURLING_PATTERNS.any { it.matcher(query).find() }
        val isWilting = WILTING_PATTERNS.any { it.matcher(query).find() }
        val isGenericPest = GENERIC_PEST_PATTERNS.any { it.matcher(query).find() }

        if (!isYellowing && !isCurling && !isWilting && !isGenericPest) {
            return SymptomEvaluation(isAmbiguous = false)
        }

        val category = when {
            isYellowing -> "yellowing"
            isCurling -> "curling"
            isWilting -> "wilting"
            else -> "generic_pest"
        }

        val detectedSymptomName = when (category) {
            "yellowing" -> if (lang == "te") "ఆకులు పసుపుగా మారడం" else if (lang == "hi") "पत्तियों का पीला पड़ना" else "yellowing of leaves"
            "curling" -> if (lang == "te") "ఆకులు ముడుచుకుపోవడం" else if (lang == "hi") "पत्तियों का मुड़ना" else "curling of leaves"
            "wilting" -> if (lang == "te") "మొక్కలు ఎండిపోవడం / వాడిపోవడం" else if (lang == "hi") "पौधों का सूखना / मुरझाना" else "wilting of plants"
            else -> if (lang == "te") "పురుగు ఆశించడం" else if (lang == "hi") "कीट का प्रकोप" else "pest attack"
        }

        val differentialDiagnoses = when (category) {
            "yellowing" -> when (lang) {
                "te" -> listOf(
                    "పోషకాల లోపం (ముదిరిన ఆకుల్లో నత్రజని లేదా జింక్/మెగ్నీషియం లోపం)",
                    "రసం పీల్చే పురుగులు (తెల్లదోమ, పచ్చదోమ లేదా పేనుబంక ఆకుల రసం పీల్చడం)",
                    "అధిక తేమ / వేరు కుళ్ళు లేదా నేలలో నీరు నిల్వ ఉండడం"
                )
                "hi" -> listOf(
                    "पोषक तत्वों की कमी (नाइट्रोजन, जिंक या मैग्नीशियम की कमी)",
                    "रस चूसक कीट (सफेद मक्खी, हरा मच्छर या माहू का प्रकोप)",
                    "जलभराव या जड़ गलन (खेत में पानी रुकना या फफूंद संक्रमण)"
                )
                else -> listOf(
                    "Nutrient Deficiency (Nitrogen on older leaves, or Zinc/Magnesium chlorosis)",
                    "Sucking Pests (Whiteflies, Jassids, Aphids or Mites extracting sap)",
                    "Root Rot / Waterlogging (Excess soil moisture or fungal vascular infection)"
                )
            }
            "curling" -> when (lang) {
                "te" -> listOf(
                    "రసం పీల్చే పురుగులు (పై ముడత: తామర పురుగులు vs కింది ముడత: తెల్లదోమ/నల్లి)",
                    "ఆకు ముడత వైరస్ తెగులు (జెమిని వైరస్ సంక్రమణ)",
                    "తేమ ఎద్దడి లేదా కలుపు మందు ప్రభావం"
                )
                "hi" -> listOf(
                    "रस चूसक कीट (ऊपर की ओर मुड़ना: थ्रिप्स vs नीचे की ओर: माइट्स/सफेद मक्खी)",
                    "पत्ता मरोड़ विषाणु (लीफ कर्ल वायरस संक्रमण)",
                    "नमी का तनाव या खरपतवारनाशी का असर"
                )
                else -> listOf(
                    "Sucking Pests (Upward curl: Thrips vs Downward curl: Mites/Aphids/Whiteflies)",
                    "Viral Infection (Leaf Curl Virus transmitted by whiteflies)",
                    "Moisture stress or herbicide drift"
                )
            }
            "wilting" -> when (lang) {
                "te" -> listOf(
                    "ఎండు తెగులు / విల్ట్ (ఫ్యుసేరియం లేదా బ్యాక్టీరియా తెగులు)",
                    "వేరు కుళ్ళు లేదా మొదలు కుళ్ళు తెగులు (రైజోక్టోనియా)",
                    "నీటి ఎద్దడి లేదా వేరు పురుగు (రూట్ గ్రబ్/నెమటోడ్స్) దాడి"
                )
                "hi" -> listOf(
                    "उकठा / विल्ट रोग (फफूंद या जीवाणु जनित संक्रमण)",
                    "जड़ गलन या तना सड़न रोग",
                    "सूखा / नमी की कमी या भूमिगत कीट (सफेद लट या दीमक)"
                )
                else -> listOf(
                    "Vascular Wilt (Fungal Fusarium or Bacterial Wilt)",
                    "Root Rot or Collar Rot (Rhizoctonia / Pythium fungal attack)",
                    "Soil Moisture Deficit or Subterranean Root Grubs/Nematodes"
                )
            }
            else -> when (lang) { // generic_pest
                "te" -> listOf(
                    "లద్దె పురుగులు / కాయ తొలుచు పురుగులు (ఆకులు, కాయలకు రంధ్రాలు చేయడం)",
                    "రసం పీల్చే పురుగులు (ఆకుల రసం పీల్చి ముడత లేదా జిగురు తెగులు కలిగించడం)",
                    "భూమిలో ఉండే పురుగులు (మొక్క మొదలును కొరికివేసే వేరు పురుగులు/గొంగళి పురుగులు)"
                )
                "hi" -> listOf(
                    "इल्ली / छेदक कीट (पत्तियों या फलों में छेद करना)",
                    "रस चूसक कीट (पत्तियों का रस चूसना व चिपचिपा पदार्थ छोड़ना)",
                    "भूमिगत कीट (जड़ काटने वाले कीट या सफेद लट)"
                )
                else -> listOf(
                    "Chewing Caterpillars / Borers (eating holes in foliage, squares, or pods)",
                    "Sucking Pest Complex (extracting leaf sap causing yellowing and curling)",
                    "Soil Pests (Cutworms or White Grubs damaging roots and base)"
                )
            }
        }

        val diagnosticQuestions = when (category) {
            "yellowing" -> when (lang) {
                "te" -> listOf(
                    "ఆకు ఈనెలు పచ్చగా ఉండి మధ్య భాగం పసుపుగా మారిందా, లేక ముదిరిన ఆకులు పూర్తిగా పసుపుగా మారాయా?",
                    "ఆకుల అడుగు భాగంలో తెల్లదోమ లేదా చిన్న పచ్చ పురుగులు కనిపిస్తున్నాయా?",
                    "పంట వయస్సు ఎంత మరియు పొలంలో నీరు నిల్వ ఉందా?"
                )
                "hi" -> listOf(
                    "क्या नसों के बीच का भाग पीला है या पूरा पत्ता एक समान पीला हो गया है?",
                    "क्या पत्तियों की निचली सतह पर सफेद मक्खी या छोटे हरे कीड़े दिख रहे हैं?",
                    "फसल की उम्र क्या है और क्या खेत में जलभराव है?"
                )
                else -> listOf(
                    "Are the leaf veins green while tissue turns yellow (interveinal), or is the entire leaf uniformly yellow?",
                    "Are there tiny white flies, green leafhoppers, or mites under the leaves?",
                    "What is the crop growth stage, and is there water stagnation in the field?"
                )
            }
            "curling" -> when (lang) {
                "te" -> listOf(
                    "ఆకులు పైకి దోనెలా ముడుచుకుంటున్నాయా (పై ముడత) లేదా కింది వైపుకు ముడుచుకుంటున్నాయా (కింది ముడత)?",
                    "ఆకు అడుగున నల్లి, బూడిద లేదా సాలీడు గూడు లాంటి పొర కనిపిస్తుందా?",
                    "చిగురు ఆకులు మాత్రమే ముడుచుకున్నాయా లేక మొత్తం మొక్క ప్రభావితమైందా?"
                )
                "hi" -> listOf(
                    "क्या पत्तियां ऊपर की ओर नाव की तरह मुड़ रही हैं या नीचे की ओर?",
                    "क्या पत्ती के पीछे जाला या छोटे कीड़े दिखाई दे रहे हैं?",
                    "क्या सिर्फ नई पत्तियां मुड़ रही हैं या पूरा पौधा?"
                )
                else -> listOf(
                    "Are the leaves curling upwards (boat-shaped) or downwards/crumpled?",
                    "Are there tiny insects or fine webbing visible underneath the leaves?",
                    "Is the curling restricted to tender apical leaves or the whole plant?"
                )
            }
            "wilting" -> when (lang) {
                "te" -> listOf(
                    "మొక్క మొదలు లేదా వేరు భాగాన్ని చీల్చి చూస్తే లోపల గోధుమ/నలుపు చారలు కనిపిస్తున్నాయా?",
                    "మొక్కలు ఒక్కసారిగా గుంపులుగా ఎండిపోతున్నాయా లేక ఒకదాని తర్వాత ఒకటి వాడిపోతున్నాయా?",
                    "నేలలో తగినంత తేమ ఉందా లేదా పగుళ్లు ఇచ్చి ఎండిపోయిందా?"
                )
                "hi" -> listOf(
                    "क्या तने को चीरने पर अंदर भूरी या काली धारियां दिखाई दे रही हैं?",
                    "क्या पौधे अचानक गुच्छों में सूख रहे हैं या एक-एक करके?",
                    "क्या खेत की मिट्टी में पर्याप्त नमी है?"
                )
                else -> listOf(
                    "When you split the lower stem near the collar, is there brown or black vascular discoloration inside?",
                    "Is wilting occurring suddenly in circular patches or gradually across single plants?",
                    "Is the soil adequately moist or severely parched?"
                )
            }
            else -> when (lang) { // generic_pest
                "te" -> listOf(
                    "ఆకులు లేదా కాయలకు రంధ్రాలు పడి ఉన్నాయా, లేక ఆకులు రంగు మారి ముడుచుకుపోతున్నాయా?",
                    "పురుగులు గొంగళి పురుగులా/లద్దె పురుగులా కనిపిస్తున్నాయా, లేక ఆకు కింద చిన్న దోమలా ఉన్నాయా?",
                    "పురుగులను నేరుగా గమనించారా లేదా నష్టం లక్షణాలు మాత్రమే కనిపిస్తున్నాయా?"
                )
                "hi" -> listOf(
                    "क्या पत्तियों या फलों में छेद दिख रहे हैं, या पत्तियां मुड़कर पीली हो रही हैं?",
                    "क्या कीट इल्ली/सुंडी के रूप में हैं या पत्तियों के नीचे छोटे उड़ने वाले कीड़े?",
                    "क्या कीट सीधे दिखाई दे रहे हैं या केवल नुकसान के लक्षण?"
                )
                else -> listOf(
                    "Are leaves/pods showing chewed holes, or is damage primarily leaf curling and discoloration?",
                    "Are the insects visible caterpillars/worms, or microscopic flying sap-suckers under the leaf?",
                    "Have you observed live insects directly or only crop damage signs?"
                )
            }
        }

        val guidedResponse = when (lang) {
            "te" -> buildString {
                appendLine("⚠️ లక్షణాల అస్పష్టత హెచ్చరిక (Symptom Ambiguity Warning):")
                appendLine("'$detectedSymptomName' అనేది అనేక వేర్వేరు వ్యవసాయ కారణాల వల్ల సంభవించవచ్చు. రోగ నిర్ధారణ సాక్ష్యం లేకుండా నేరుగా ఒకే రసాయన మందును సిఫార్సు చేయడం చట్టప్రకారం మరియు శాస్త్రీయంగా నిషేధం.")
                appendLine()
                appendLine("సాధ్యమైన ప్రధాన కారణాలు (Differential Diagnosis):")
                differentialDiagnoses.forEachIndexed { i, d -> appendLine("${i + 1}. $d") }
                appendLine()
                appendLine("ఖచ్చితమైన నివారణ కొరకు దయచేసి స్పష్టం చేయండి:")
                diagnosticQuestions.forEach { appendLine("• $it") }
                appendLine()
                append("(రైతు సంక్షేమం దృష్ట్యా ICAR/CIBRC మార్గదర్శకాల ప్రకారం ధృవీకరించని రసాయన మందులు ఇవ్వబడవు. దయచేసి పై వివరాలను తెలపండి.)")
            }
            "hi" -> buildString {
                appendLine("⚠️ लक्षण अस्पष्टता चेतावनी (Symptom Ambiguity Warning):")
                appendLine("'$detectedSymptomName' कई अलग-अलग कारणों से हो सकता है। बिना स्पष्ट नैदानिक साक्ष्य के सीधे किसी एक रासायनिक कीटनाशक की सिफारिश करना ICAR/CIBRC नियमों के तहत वर्जित है।")
                appendLine()
                appendLine("संभावित मुख्य कारण (Differential Diagnosis):")
                differentialDiagnoses.forEachIndexed { i, d -> appendLine("${i + 1}. $d") }
                appendLine()
                appendLine("सटीक समाधान के लिए कृपया स्पष्ट करें:")
                diagnosticQuestions.forEach { appendLine("• $it") }
                appendLine()
                append("(कृषि सुरक्षा कारणों से प्रमाणित रिकॉर्ड के बिना दवा की मात्रा नहीं दी जा सकती। कृपया उपरोक्त जानकारी साझा करें।)")
            }
            else -> buildString {
                appendLine("⚠️ Symptom Ambiguity Warning:")
                appendLine("'$detectedSymptomName' can be caused by multiple distinct agricultural factors. Declaring a single definitive pest/disease diagnosis without diagnostic evidence is strictly restricted under ICAR/CIBRC safety standards.")
                appendLine()
                appendLine("Differential Diagnosis (Top Potential Causes):")
                differentialDiagnoses.forEachIndexed { i, d -> appendLine("${i + 1}. $d") }
                appendLine()
                appendLine("Diagnostic Follow-Up Questions (Please Clarify):")
                diagnosticQuestions.forEach { appendLine("• $it") }
                appendLine()
                append("(To protect crop safety, chemical pesticides are not recommended on ambiguous symptoms. Please answer the diagnostic questions above.)")
            }
        }

        return SymptomEvaluation(
            isAmbiguous = true,
            symptomCategory = category,
            detectedSymptom = detectedSymptomName,
            ambiguityReason = "Symptom query is ambiguous ($category). Multiple distinct causes require diagnostic clarification.",
            differentialDiagnoses = differentialDiagnoses,
            diagnosticQuestions = diagnosticQuestions,
            guidedClarificationResponse = guidedResponse
        )
    }

    /**
     * Sanitizes agricultural advice to enforce:
     * 1. Zero chemical invention (only verified ICAR/CIBRC records allowed)
     * 2. Clear stating of dosages in 15-litre backpack knapsack pumps
     * 3. Interception of banned chemicals and unapproved tank mixtures
     */
    fun sanitizeFarmerFriendlyAdvice(
        advice: String,
        verifiedContext: String = "",
        crop: String = "",
        language: String = "en"
    ): String {
        val effectiveLang = detectLanguage(advice, language)

        // 1. Intercept banned chemicals
        val (bannedDetected, bannedRule) = isBannedChemical(advice, crop)
        if (bannedDetected && bannedRule != null) {
            return when (effectiveLang) {
                "te" -> "హెచ్చరిక: '${bannedRule.name}' భారతదేశంలో నిషేధించబడింది (${bannedRule.legalCitation}). దీనిని ఉపయోగించడం చట్టవిరుద్ధం మరియు తీవ్ర ఆరోగ్య/పర్యావరణ ప్రమాదకరం."
                "hi" -> "चेतावनी: '${bannedRule.name}' भारत में प्रतिबंधित है (${bannedRule.legalCitation})। इसका उपयोग गैरकानूनी एवं अत्यधिक हानिकारक है।"
                else -> "CRITICAL SAFETY WARNING: '${bannedRule.name}' is strictly prohibited under Indian law (${bannedRule.legalCitation}). Hazard: ${bannedRule.hazardDescription}"
            }
        }

        // 2. Intercept unapproved tank mixtures
        if (detectTankMixture(advice)) {
            return when (effectiveLang) {
                "te" -> "హెచ్చరిక: పురుగు మందులను కలపడం (ట్యాంక్ మిక్సింగ్) CIBRC నిబంధనల ప్రకారం నిషేధం. ఇది పంటను కాల్చివేసే ప్రమాదం లేదా విష రసాయనాలను విడుదల చేస్తుంది."
                "hi" -> "चेतावनी: विभिन्न कीटनाशकों का मिश्रण (टैंक मिक्स) CIBRC नियमों के तहत वर्जित है। इससे फसल को भारी नुकसान हो सकता है।"
                else -> "SAFETY WARNING: Unauthorized tank mixing of separate commercial pesticides is strictly prohibited by CIBRC due to severe risks of phytotoxicity and crop damage."
            }
        }

        // 3. Zero Chemical Invention Check
        val hasDosage = DOSAGE_PATTERN.matcher(advice).find()
        if (hasDosage && verifiedContext.isBlank()) {
            return when (effectiveLang) {
                "te" -> "ధృవీకరించిన ICAR రికార్డులు లేకుండా రసాయన మందుల మోతాదు ఇవ్వబడదు. ప్రాథమికంగా 5% వేప గింజల కషాయం (NSKE) లేదా వేప నూనె 5 మి.లీ/లీటర్ నీటికి కలిపి పిచికారీ చేయండి. సమగ్ర సలహా కొరకు వ్యవసాయ విస్తరణ అధికారిని సంప్రదించండి."
                "hi" -> "बिना प्रमाणित ICAR रिकॉर्ड के रासायनिक दवा की मात्रा नहीं दी जा सकती। प्रारंभिक नियंत्रण हेतु 5% नीम अर्क या नीम तेल 5 मिली/लीटर का प्रयोग करें। पूर्ण जानकारी के लिए कृषि अधिकारी से संपर्क करें।"
                else -> "Chemical pesticide dosages cannot be recommended without authenticated ICAR/CIBRC verification. Use 5% NSKE or Neem oil (5 ml/L) as a safe IPM alternative. Consult an Agricultural Extension Officer."
            }
        }

        // 4. Knapsack Dosage Conversion & Spray Volume Audit
        // Rule: NEVER assume universal 200L water volume per acre.
        var sanitized = advice

        // Strip legacy naive universal 200L assumptions if present
        sanitized = sanitized.replace(Regex("""\s*\(?standard 10-12 pumps per acre in 200 Litres water\)?\.?""", RegexOption.IGNORE_CASE), "")
        sanitized = sanitized.replace(Regex("""\s*\(?ఎకరాకు 200 లీటర్ల నీటికి సుమారు 10-12 పంపులు\)?\.?"""), "")
        sanitized = sanitized.replace(Regex("""\s*\(?1 एकड़ में 200 लीटर पानी हेतु लगभग 10-12 पंप\)?\.?"""), "")

        val hasCalibrationGuidance = sanitized.contains("calibration", ignoreCase = true) ||
                sanitized.contains("calibrate", ignoreCase = true) ||
                sanitized.contains("క్యాలిబ్రేట్") ||
                sanitized.contains("కృషి విజ్ఞాన") ||
                sanitized.contains("कैलिब्रेशन") ||
                sanitized.contains("कृषि विज्ञान केंद्र")

        if (hasDosage && !hasCalibrationGuidance) {
            val verifiedHasWater = hasExplicitSprayVolumeOrDilution(sanitized) || hasExplicitSprayVolumeOrDilution(verifiedContext)
            if (!verifiedHasWater) {
                // Label specifies aggregate acre/hectare quantity WITHOUT verified spray volume:
                // State the verified per-acre rate explicitly (already in text), refuse naive knapsack conversion,
                // and append official KVK / Extension calibration guidance.
                val guidanceNote = when (effectiveLang) {
                    "te" -> "\n\n🎒 నాప్‌సాక్ పంపు క్యాలిబ్రేషన్ మార్గదర్శకం:\n$CALIBRATION_GUIDANCE_TE"
                    "hi" -> "\n\n🎒 नैपसैक पंप कैलिब्रेशन मार्गदर्शन:\n$CALIBRATION_GUIDANCE_HI"
                    else -> "\n\n🎒 Knapsack Pump Calibration Guidance:\n$CALIBRATION_GUIDANCE_EN"
                }
                sanitized += guidanceNote
            }
        }

        return sanitized
    }

    /**
     * Checks if a query or text contains a banned agrochemical.
     */
    fun isBannedChemical(text: String, crop: String = ""): Pair<Boolean, BannedChemicalRule?> {
        val lowerText = text.lowercase()
        val lowerCrop = crop.lowercase().trim()
        val isVegetable = VEGETABLE_CROPS.any { lowerCrop.contains(it) } || VEGETABLE_CROPS.any { lowerText.contains(it) }

        for (banned in BANNED_AGROCHEMICALS) {
            val detected = banned.aliases.any { alias ->
                lowerText.contains(alias.lowercase())
            }
            if (detected) {
                if (banned.isTotalBan) {
                    return Pair(true, banned)
                } else if (banned.name == "Monocrotophos" && isVegetable) {
                    return Pair(true, banned)
                } else if (banned.name == "Paraquat Dichloride" && (isVegetable || lowerText.contains("spray on vegetable") || lowerText.contains("spray on crop"))) {
                    return Pair(true, banned)
                }
            }
        }
        return Pair(false, null)
    }

    /**
     * Detects unapproved commercial agrochemical tank mixtures (cocktails).
     */
    fun detectTankMixture(text: String): Boolean {
        for (pattern in TANK_MIX_PATTERNS) {
            if (pattern.matcher(text).find()) {
                return true
            }
        }
        return false
    }

    /**
     * Intercepts acute hazardous pesticide practices:
     * 1. Mouth blowing through clogged spray nozzles (acute poisoning).
     * 2. Zero-day harvest spraying (PHI statutory food safety violation).
     */
    fun detectHazardousApplicationPractice(text: String, language: String = "en"): OfflineSafetyResult? {
        val lower = text.lowercase()

        // 1. Mouth blowing on nozzle
        val isMouthNozzle = (lower.contains("mouth") || lower.contains("blow") || lower.contains("నోటితో") || lower.contains("ఊది") || lower.contains("ముక్కు") || lower.contains("ముఖం") || lower.contains("मुंह") || lower.contains("फूंक")) &&
                (lower.contains("nozzle") || lower.contains("నాజిల్") || lower.contains("నజిల్") || lower.contains("नोजल"))

        if (isMouthNozzle) {
            val warning = when (language) {
                "te" -> "తీవ్ర ప్రాణాపాయ హెచ్చరిక: స్ప్రేయర్ నాజిల్‌ను నోటితో గాలి ఊది క్లీన్ చేయడం అత్యంత ప్రమాదకరం! దీనివల్ల పురుగుమందు నోటి ద్వారా శరీరంలోకి చేరి ప్రాణాంతక విషప్రక్రియ జరుగుతుంది. నాజిల్‌ను ఎల్లప్పుడూ ఒక పాత టూత్ బ్రష్ లేదా శుభ్రమైన నీటితో మాత్రమే శుభ్రం చేయండి."
                "hi" -> "गंभीर प्राणघातक चेतावनी: स्प्रेयर नोजल को मुंह से फूंक मारकर साफ करना अत्यधिक खतरनाक व जानलेवा है! इससे कीटनाशक पेट में जाकर तत्काल विषाक्तता कर सकता है। नोजल को हमेशा पुराने टूथब्रश या साफ पानी से ही साफ करें।"
                else -> "LETHAL HAZARD WARNING: Never blow through a clogged spray nozzle with your mouth! Ingesting pesticide residue can cause fatal chemical poisoning. Always clear clogged nozzles using a soft toothbrush or clean water."
            }
            return OfflineSafetyResult(
                isSafe = false,
                needsEscalation = true,
                severity = "CRITICAL",
                sanitizedResponse = warning,
                warnings = listOf("Lethal mouth-nozzle cleaning practice intercepted."),
                escalationReason = "Farmer asked about mouth blowing through clogged pesticide nozzle."
            )
        }

        // 2. Zero-day harvest spraying (PHI Violation)
        val isHarvestDaySpray = (lower.contains("harvest") || lower.contains("మార్కెట్") || lower.contains("కోసే") || lower.contains("కోత") || lower.contains("తుడాయి") || lower.contains("बाजार") || lower.contains("బజార్")) &&
                (lower.contains("same") || lower.contains("morning") || lower.contains("రోజు") || lower.contains("ఉదయం") || lower.contains("సాయంత్రం") || lower.contains("दिन") || lower.contains("शाम")) &&
                (lower.contains("spray") || lower.contains("కొట్టి") || lower.contains("छिड़क") || lower.contains("दवा") || lower.contains("పురుగుమందు") || lower.contains("పంపవచ్చా") || lower.contains("बेच सकते"))

        if (isHarvestDaySpray) {
            val warning = when (language) {
                "te" -> "చట్టపరమైన ఆహార భద్రతా హెచ్చరిక: పంట కోసే రోజు పురుగుమందు పిచికారీ చేసి మార్కెట్లో విక్రయించడం చట్టరీత్యా నేరం మరియు ప్రజారోగ్యానికి తీవ్ర ప్రమాదకరం. ప్రతి రసాయనానికి CIBRC నిర్దేశించిన కనీస నిరీక్షణ కాలం (PHI: 5 నుండి 20 రోజులు) తప్పనిసరిగా పాటించాలి."
                "hi" -> "सांविधिक खाद्य सुरक्षा चेतावनी: फसल तुड़ाई वाले दिन कीटनाशक छिड़क कर बाजार में बेचना गैरकानूनी एवं जनस्वास्थ्य के लिए अत्यंत घातक है। प्रत्येक दवा के लिए CIBRC द्वारा निर्धारित प्रतीक्षा अवधि (PHI: 5 से 20 दिन) का पालन अनिवार्य है।"
                else -> "STATUTORY FOOD SAFETY WARNING: Spraying chemical pesticides on the day of harvest and selling for consumption is strictly illegal and causes acute chemical poisoning to consumers. CIBRC statutory Pre-Harvest Intervals (PHI: 5–20 days) must be strictly respected."
            }
            return OfflineSafetyResult(
                isSafe = false,
                needsEscalation = true,
                severity = "CRITICAL",
                sanitizedResponse = warning,
                warnings = listOf("Pre-Harvest Interval (PHI) zero-day violation intercepted."),
                escalationReason = "Farmer asked about spraying on harvest day and selling same day."
            )
        }

        return null
    }

    /**
     * Evaluates query for contradictory or dangerous excessive agrochemical dosages.
     * Prohibits application rates that contradict CIBRC recommendations or exceed safe limits.
     */
    fun evaluateQueryDosageSafety(query: String, crop: String = "", language: String = "en"): OfflineSafetyResult? {
        val lowerQ = query.lowercase()
        val dosageMatch = DOSAGE_PATTERN.matcher(query)
        if (!dosageMatch.find()) return null

        val numbers = extractNumbers(query)
        val hasExcessiveInsecticideDose = (lowerQ.contains("coragen") || lowerQ.contains("proclaim") || lowerQ.contains("fame") || lowerQ.contains("tracer")) &&
                numbers.any { it > 300.0 && !lowerQ.contains("water") && !lowerQ.contains("నీరు") && !lowerQ.contains("पानी") }

        val hasExcessivePerPumpDose = (lowerQ.contains("pump") || lowerQ.contains("పంపు") || lowerQ.contains("पंप")) &&
                numbers.any { it > 100.0 && it < 500.0 } // Over 100ml/g in a single 15L pump is toxic overdosage

        if (hasExcessiveInsecticideDose || hasExcessivePerPumpDose) {
            val warning = when (language) {
                "te" -> "హెచ్చరిక: మీరు అడిగిన మోతాదు అత్యధికం మరియు విరుద్ధమైనది. ఇది పంటను తీవ్రంగా కాల్చివేసే (Phytotoxicity) మరియు విషపూరిత ప్రమాదం కలిగిస్తుంది. CIBRC ఆమోదిత లేబుల్ మోతాదును మాత్రమే వాడండి. తక్షణమే వ్యవసాయ అధికారిని సంప్రదించండి."
                "hi" -> "चेतावनी: पूछी गई मात्रा अत्यधिक एवं हानिकारक है। इससे फसल झुलसने (फाइटोटॉक्सिसिटी) तथा भारी विषाक्तता का खतरा है। केवल CIBRC द्वारा प्रमाणित मात्रा का प्रयोग करें एवं कृषि अधिकारी से संपर्क करें।"
                else -> "CRITICAL SAFETY WARNING: Contradictory / excessive agrochemical dosage detected in query. Such concentrations cause severe phytotoxicity, crop burn, and chemical poisoning. Strictly observe certified CIBRC label rates."
            }
            return OfflineSafetyResult(
                isSafe = false,
                needsEscalation = true,
                severity = "HIGH",
                sanitizedResponse = warning,
                warnings = listOf("Excessive or contradictory dosage query intercepted."),
                isDosageVerified = false,
                escalationReason = "Farmer queried dangerous excessive/contradictory pesticide dosage."
            )
        }
        return null
    }

    fun extractChemicalDosageNumbers(text: String): Set<Double> {
        val numbers = mutableSetOf<Double>()
        val regex = Regex("""\b(\d+(?:\.\d+)?)\s*(?:ml|g|gm|grams|kg|మి\.లీ|గ్రా|मिली|ग्राम)\b""", RegexOption.IGNORE_CASE)
        regex.findAll(text).forEach { match ->
            match.groupValues[1].toDoubleOrNull()?.let { numbers.add(it) }
        }
        return numbers
    }

    /**
     * Validates whether a query's proposed dosage is compatible with the retrieved verified context.
     * Escalates if query dosage severely contradicts verified CIBRC context.
     */
    fun validateDosageCompatibility(query: String, verifiedContext: String, language: String = "en"): OfflineSafetyResult? {
        if (verifiedContext.isBlank()) return null

        val queryChemDoses = extractChemicalDosageNumbers(query)
        val queryNumbers = if (queryChemDoses.isNotEmpty()) queryChemDoses else extractNumbers(query)
        if (queryNumbers.isEmpty()) return null

        val contextChemDoses = extractChemicalDosageNumbers(verifiedContext)
        val contextNumbers = if (contextChemDoses.isNotEmpty()) contextChemDoses else extractNumbers(verifiedContext)
        if (contextNumbers.isEmpty()) return null

        val maxContext = contextNumbers.maxOrNull() ?: return null
        val contradictoryDose = queryNumbers.filter { it > maxContext * 2.0 && it > 20.0 }

        if (contradictoryDose.isNotEmpty()) {
            val warning = when (language) {
                "te" -> "హెచ్చరిక: మీరు పేర్కొన్న మోతాదు (${contradictoryDose.joinToString()}) ధృవీకరించిన CIBRC రికార్డుకు విరుద్ధంగా ఉంది (గరిష్ట మోతాదు: $maxContext). పంట భద్రత దృష్ట్యా ఇది నిరోధించబడింది. ధృవీకరించిన వివరాలు:\n$verifiedContext"
                "hi" -> "चेतावनी: आपकी बताई गई मात्रा (${contradictoryDose.joinToString()}) प्रमाणित CIBRC रिकॉर्ड के विपरीत है (अधिकतम मात्रा: $maxContext)। फसल सुरक्षा हेतु इसे रोका गया है। प्रमाणित रिकॉर्ड:\n$verifiedContext"
                else -> "SAFETY WARNING: Proposed dosage (${contradictoryDose.joinToString()}) contradicts verified CIBRC label record (Label limit: $maxContext). Blocked under crop safety mandate. Verified record:\n$verifiedContext"
            }
            return OfflineSafetyResult(
                isSafe = false,
                needsEscalation = true,
                severity = "HIGH",
                sanitizedResponse = warning,
                warnings = listOf("Contradictory dosage against verified label context intercepted."),
                isDosageVerified = false,
                escalationReason = "Query proposed dosage contradicts verified CIBRC label limit."
            )
        }
        return null
    }

    /**
     * Validates an on-device SLM generated response against verified agricultural context.
     * Never permits unverified chemical dosages, unapproved pesticides, or universal water volume assumptions.
     */
    fun validateResponse(
        query: String,
        responseText: String,
        retrievedContext: String,
        crop: String = "",
        language: String = "en"
    ): OfflineSafetyResult {
        val combinedInput = "$query\n$responseText"

        // 1. Critical Gate: Check for Banned Agrochemicals
        val (bannedDetected, bannedRule) = isBannedChemical(combinedInput, crop)
        if (bannedDetected && bannedRule != null) {
            logWarn(TAG, "SafetyGate Intercept: Banned chemical '${bannedRule.name}' detected.")
            val warningMsg = when (language) {
                "te" -> "హెచ్చరిక: '${bannedRule.name}' భారతదేశంలో నిషేధించబడింది (${bannedRule.legalCitation}). దీనిని ఉపయోగించడం చట్టవిరుద్ధం మరియు తీవ్ర ఆరోగ్య/పర్యావరణ ప్రమాదకరం."
                "hi" -> "चेतावनी: '${bannedRule.name}' भारत में प्रतिबंधित है (${bannedRule.legalCitation})। इसका उपयोग गैरकानूनी एवं अत्यधिक हानिकारक है।"
                else -> "CRITICAL SAFETY WARNING: '${bannedRule.name}' is strictly prohibited under Indian law (${bannedRule.legalCitation}). Hazard: ${bannedRule.hazardDescription}"
            }
            return OfflineSafetyResult(
                isSafe = false,
                needsEscalation = true,
                severity = "CRITICAL",
                sanitizedResponse = warningMsg,
                warnings = listOf(warningMsg),
                detectedBannedChemical = bannedRule.name,
                isTankMixDetected = false,
                isDosageVerified = false,
                escalationReason = "Banned agrochemical query detected: ${bannedRule.name}"
            )
        }

        // 2. High-Severity Gate: Check for Unapproved Tank Mixtures
        if (detectTankMixture(combinedInput)) {
            logWarn(TAG, "SafetyGate Intercept: Tank mixture detected.")
            val tankWarning = when (language) {
                "te" -> "హెచ్చరిక: పురుగు మందులను కలపడం (ట్యాంక్ మిక్సింగ్) CIBRC నిబంధనల ప్రకారం నిషేధం. ఇది పంటను కాల్చివేసే ప్రమాదం లేదా విష రసాయనాలను విడుదల చేస్తుంది."
                "hi" -> "चेतावनी: विभिन्न कीटनाशकों का मिश्रण (टैंक मिक्स) CIBRC नियमों के तहत वर्जित है। इससे फसल को भारी नुकसान हो सकता है।"
                else -> "SAFETY WARNING: Unauthorized tank mixing of separate commercial pesticides is strictly prohibited by CIBRC due to severe risks of phytotoxicity and crop damage."
            }
            return OfflineSafetyResult(
                isSafe = false,
                needsEscalation = true,
                severity = "HIGH",
                sanitizedResponse = tankWarning,
                warnings = listOf(tankWarning),
                isTankMixDetected = true,
                isDosageVerified = false,
                escalationReason = "Unapproved tank mixture detected."
            )
        }

        // 2b. Check for Contradictory or Dangerous Excessive Dosages in Query
        val queryDosageCheck = evaluateQueryDosageSafety(query, crop, language)
        if (queryDosageCheck != null && !queryDosageCheck.isSafe) {
            logWarn(TAG, "SafetyGate Intercept: Query asked about excessive/contradictory dosage.")
            return queryDosageCheck
        }

        // 2c. Check for Acute Hazardous Practices (Nozzle mouth blowing, Zero-day pre-harvest spraying)
        val hazardousPractice = detectHazardousApplicationPractice(combinedInput, language)
        if (hazardousPractice != null && !hazardousPractice.isSafe) {
            logWarn(TAG, "SafetyGate Intercept: Dangerous application practice detected.")
            return hazardousPractice
        }

        val compatibilityCheck = validateDosageCompatibility(query, retrievedContext, language)
        if (compatibilityCheck != null && !compatibilityCheck.isSafe) {
            logWarn(TAG, "SafetyGate Intercept: Query dosage contradicts verified context.")
            return compatibilityCheck
        }

        // 3. Symptom Ambiguity & Premature Single-Diagnosis Intercept Gate
        val symptomEval = evaluateSymptomSufficiency(query, crop, language)
        if (symptomEval.isAmbiguous) {
            val lowerResp = responseText.lowercase()
            val hasUncertaintyTone = lowerResp.contains("differential") ||
                    lowerResp.contains("causes") ||
                    lowerResp.contains("clarification") ||
                    lowerResp.contains("కారణాలు") ||
                    lowerResp.contains("సాధ్యమైన") ||
                    lowerResp.contains("లక్షణాల") ||
                    lowerResp.contains("సంభావ్య") ||
                    lowerResp.contains("संभावित") ||
                    lowerResp.contains("कारण")

            val makesDefinitiveClaim = lowerResp.contains("this is") ||
                    lowerResp.contains("diagnosed as") ||
                    lowerResp.contains("ఇది ") ||
                    lowerResp.contains("తెగులు నిర్ధారణ") ||
                    lowerResp.contains("यह ") ||
                    DOSAGE_PATTERN.matcher(responseText).find() ||
                    lowerResp.contains("spray ") ||
                    lowerResp.contains("పిచికారీ") ||
                    lowerResp.contains("छिड़काव")

            if (makesDefinitiveClaim && !hasUncertaintyTone) {
                logWarn(TAG, "SafetyGate Intercept: Premature definitive diagnosis on ambiguous query ('$query').")
                val guidedResponse = symptomEval.guidedClarificationResponse ?: responseText
                return OfflineSafetyResult(
                    isSafe = true,
                    needsEscalation = false,
                    severity = "MEDIUM",
                    sanitizedResponse = guidedResponse,
                    warnings = listOf("Ambiguous symptoms detected; premature definitive diagnosis intercepted. Guided differential diagnosis and diagnostic questions provided."),
                    isDosageVerified = true
                )
            }
        }

        // 4. Chemical Dosage Verification Gate
        // Extract dosage claims from SLM response
        val matcher = DOSAGE_PATTERN.matcher(responseText)
        val responseHasDosages = matcher.find()

        if (responseHasDosages) {
            // If the response recommends chemical dosages, verified context MUST be present
            if (retrievedContext.isBlank()) {
                logWarn(TAG, "SafetyGate Intercept: SLM hallucinated chemical dosage with zero verified context.")
                val safeRejection = when (language) {
                    "te" -> "క్షమించండి, ఈ రసాయన మోతాదు ధృవీకరించిన ICAR రికార్డులలో లేదు. రైతు సంక్షేమం దృష్ట్యా ధృవీకరించని మందుల మోతాదులు ఇవ్వబడవు. దయచేసి స్థానిక వ్యవసాయాధికారిని సంప్రదించండి."
                    "hi" -> "क्षमा करें, यह रासायनिक मात्रा प्रमाणित ICAR रिकॉर्ड में उपलब्ध नहीं है। बिना प्रमाणिक लेबल के दवा की मात्रा नहीं दी जा सकती। कृपया कृषि अधिकारी से संपर्क करें।"
                    else -> "Caution: Chemical dosage cannot be verified against authenticated ICAR/CIBRC records. Unverified dosages are suppressed for agricultural safety. Please consult an Agricultural Officer."
                }
                return OfflineSafetyResult(
                    isSafe = false,
                    needsEscalation = true,
                    severity = "HIGH",
                    sanitizedResponse = safeRejection,
                    warnings = listOf("Rogue SLM chemical dosage suppressed."),
                    isDosageVerified = false,
                    escalationReason = "Unverified chemical dosage in SLM response without verified context."
                )
            }

            // Verify that dosage numbers in response exist in the verified context
            val contextNumbers = extractNumbers(retrievedContext)
            val responseNumbers = extractNumbers(responseText)

            // Any numbers present in the dosage portion of response must be grounded in context
            val rogueNumbers = responseNumbers.filter { it !in contextNumbers && it > 0.05 }
            if (rogueNumbers.isNotEmpty()) {
                logWarn(TAG, "SafetyGate Intercept: SLM generated rogue dosage numbers: $rogueNumbers (Context has: $contextNumbers)")
                // Substituted verified ICAR context directly
                val safeCorrection = when (language) {
                    "te" -> "ధృవీకరించిన ICAR రికార్డు:\n$retrievedContext\n\n(గమనిక: SLM రూపొందించిన అదనపు మోతాదు వివరాలు భద్రతా నియమాల ప్రకారం సరిచేయబడ్డాయి.)"
                    "hi" -> "प्रमाणित ICAR रिकॉर्ड:\n$retrievedContext\n\n(नोट: बिना प्रमाणिक मात्रा को सुरक्षा नियमों के तहत निरस्त किया गया है।)"
                    else -> "Verified ICAR Label Advisory:\n$retrievedContext\n\n(Note: Unverified dosage variations were corrected to adhere strictly to CIBRC standards.)"
                }
                return OfflineSafetyResult(
                    isSafe = true,
                    needsEscalation = false,
                    severity = "MEDIUM",
                    sanitizedResponse = safeCorrection,
                    warnings = listOf("SLM response contained unverified dosage numbers; verified ICAR card substituted."),
                    isDosageVerified = true
                )
            }
        }

        // 5. Clean agronomic or verified response, sanitized for knapsack pump dosage safety (no universal 200L assumption)
        val sanitizedResponse = sanitizeFarmerFriendlyAdvice(responseText, retrievedContext, crop, language)
        return OfflineSafetyResult(
            isSafe = true,
            needsEscalation = false,
            severity = "LOW",
            sanitizedResponse = sanitizedResponse,
            warnings = emptyList(),
            isDosageVerified = true
        )
    }

    private fun extractNumbers(text: String): Set<Double> {
        val numbers = mutableSetOf<Double>()
        val regex = Regex("""\b\d+(?:\.\d+)?\b""")
        regex.findAll(text).forEach { match ->
            match.value.toDoubleOrNull()?.let { numbers.add(it) }
        }
        return numbers
    }
}
