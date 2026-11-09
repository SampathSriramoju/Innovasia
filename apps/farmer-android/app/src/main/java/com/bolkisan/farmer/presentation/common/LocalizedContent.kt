package com.bolkisan.farmer.presentation.common

/**
 * Multilingual agricultural content catalog and entity canonicalization.
 * Supports complete trilingual parity across Telugu (te), Hindi (hi), and English (en).
 */

data class VoiceQuickPrompt(
    val text: String,
    val badge: String,
    val isWarning: Boolean = false
)

data class MandiRow(
    val market: String,
    val price: String,
    val note: String
)

private fun pick(language: String, te: String, hi: String, en: String): String =
    when (language) {
        "te" -> te
        "hi" -> hi
        else -> en
    }

/**
 * Maps any localized or colloquial crop representation to its canonical system identifier.
 * Ensures consistent database, network, vision, and RAG lookups.
 */
fun canonicalCropName(raw: String?): String {
    if (raw == null) return "Cotton"
    val clean = raw.trim()
    return when {
        clean.equals("Cotton", ignoreCase = true) || clean.contains("ప్రత్తి") || clean.contains("పత్తి") || clean.contains("कपास") -> "Cotton"
        clean.equals("Rice", ignoreCase = true) || clean.equals("Paddy", ignoreCase = true) || clean.contains("Rice") || clean.contains("వరి") || clean.contains("ధాన్యం") || clean.contains("धान") || clean.contains("चावल") -> "Rice / Paddy"
        clean.equals("Chilli", ignoreCase = true) || clean.equals("Chili", ignoreCase = true) || clean.contains("మిరప") || clean.contains("మిర్చి") || clean.contains("मिर्च") -> "Chilli"
        clean.equals("Maize", ignoreCase = true) || clean.equals("Corn", ignoreCase = true) || clean.contains("మొక్కజొన్న") || clean.contains("మక్క") || clean.contains("मक्का") -> "Maize"
        clean.equals("Tomato", ignoreCase = true) || clean.contains("టమోటా") || clean.contains("టమాట") || clean.contains("टमाटर") -> "Tomato"
        clean.equals("Wheat", ignoreCase = true) || clean.contains("గోధుమ") || clean.contains("गेहूं") || clean.contains("गेहू") -> "Wheat"
        clean.equals("Groundnut", ignoreCase = true) || clean.equals("Peanut", ignoreCase = true) || clean.contains("వేరుశనగ") || clean.contains("వేరుశెనగ") || clean.contains("మూంగఫలీ") || clean.contains("मूंगफली") -> "Groundnut"
        clean.equals("Turmeric", ignoreCase = true) || clean.contains("పసుపు") || clean.contains("हल्दी") -> "Turmeric"
        clean.equals("Mango", ignoreCase = true) || clean.contains("మామిడి") || clean.contains("आम") -> "Mango"
        else -> clean
    }
}

/**
 * Maps any localized plant part string to canonical identifier ('leaf', 'stem', 'fruit', 'flower').
 */
fun canonicalPlantPart(raw: String?): String {
    if (raw == null) return "leaf"
    val clean = raw.trim().lowercase()
    return when {
        clean.contains("leaf") || clean.contains("ఆకు") || clean.contains("पत्ती") -> "leaf"
        clean.contains("stem") || clean.contains("కాండం") || clean.contains("तना") -> "stem"
        clean.contains("fruit") || clean.contains("కాయ") || clean.contains("పండు") || clean.contains("फल") -> "fruit"
        clean.contains("flower") || clean.contains("పూత") || clean.contains("పువ్వు") || clean.contains("फूल") -> "flower"
        else -> "leaf"
    }
}

fun localizedCropName(crop: String, language: String): String = when (canonicalCropName(crop)) {
    "Maize" -> pick(language, "మొక్కజొన్న", "मक्का", "Maize")
    "Cotton" -> pick(language, "ప్రత్తి", "कपास", "Cotton")
    "Chilli" -> pick(language, "మిరప", "मिर्च", "Chilli")
    "Rice / Paddy" -> pick(language, "వరి", "धान", "Rice / Paddy")
    "Wheat" -> pick(language, "గోధుమ", "गेहूं", "Wheat")
    "Tomato" -> pick(language, "టమోటా", "टमाटर", "Tomato")
    "Turmeric" -> pick(language, "పసుపు", "हल्दी", "Turmeric")
    "Groundnut" -> pick(language, "వేరుశనగ", "मूंगफली", "Groundnut")
    "Mango" -> pick(language, "మామిడి", "आम", "Mango")
    else -> crop
}

/** Crops shown in the home crop picker. */
fun cropOptions(language: String): List<Pair<String, String>> = listOf(
    "Maize" to localizedCropName("Maize", language),
    "Cotton" to localizedCropName("Cotton", language),
    "Chilli" to localizedCropName("Chilli", language),
    "Rice / Paddy" to localizedCropName("Rice / Paddy", language),
    "Wheat" to localizedCropName("Wheat", language),
    "Tomato" to localizedCropName("Tomato", language),
    "Groundnut" to localizedCropName("Groundnut", language),
    "Turmeric" to localizedCropName("Turmeric", language),
    "Mango" to localizedCropName("Mango", language)
)

/** Extra crops supported by the photo diagnosis screen. */
fun diseaseCropOptions(language: String): List<Pair<String, String>> = listOf(
    "Cotton" to localizedCropName("Cotton", language),
    "Rice / Paddy" to localizedCropName("Rice / Paddy", language),
    "Chilli" to localizedCropName("Chilli", language),
    "Tomato" to localizedCropName("Tomato", language),
    "Maize" to localizedCropName("Maize", language),
    "Wheat" to localizedCropName("Wheat", language),
    "Groundnut" to localizedCropName("Groundnut", language),
    "Turmeric" to localizedCropName("Turmeric", language),
    "Mango" to localizedCropName("Mango", language)
)

fun cropFilterOptions(language: String): List<Pair<String, String>> {
    val all = pick(language, "అన్నీ (All)", "सभी (All)", "All")
    val crops = listOf("Cotton", "Rice / Paddy", "Chilli", "Tomato", "Wheat", "Maize", "Groundnut", "Turmeric", "Mango")
    return listOf("All" to all) + crops.map { key ->
        val name = localizedCropName(key, language)
        val label = if (language == "en") name else "$name ($key)"
        key to label
    }
}

fun plantPartOptions(language: String): List<Pair<String, String>> = listOf(
    "leaf" to pick(language, "ఆకు", "पत्ती", "Leaf"),
    "stem" to pick(language, "కాండం", "तना", "Stem"),
    "fruit" to pick(language, "కాయ/పండు", "फल", "Fruit"),
    "flower" to pick(language, "పూత", "फूल", "Flower")
)

fun localizedPlantPart(partKey: String, language: String): String =
    plantPartOptions(language).firstOrNull { it.first == canonicalPlantPart(partKey) }?.second
        ?: partKey.replaceFirstChar { it.uppercase() }

fun homeSampleQuestions(crop: String, language: String): List<String> = when (canonicalCropName(crop)) {
    "Maize" -> listOf(
        pick(language, "మొక్కజొన్నలో కత్తెర పురుగు నివారణ మందులు?", "मक्का में तना छेदक कीट का नियंत्रण कैसे करें?", "How to control stem borer in maize?"),
        pick(language, "ఎకరాకు యూరియా ఎరువు మోతాదు?", "प्रति एकड़ यूरिया की मात्रा कितनी है?", "What is the urea dose per acre?"),
        pick(language, "మొక్కజొన్న మండి ధరలు ఎంత?", "मक्का मंडी भाव क्या हैं?", "What are the maize mandi prices?")
    )
    "Cotton" -> listOf(
        pick(language, "గులాబీ రంగు పురుగు నివారణ మందు?", "कपास में गुलाबी सुंडी के लिए कौन सी दवा?", "Which pesticide for pink bollworm in cotton?"),
        pick(language, "ఆకు ఎరుపు తెగులు నివారణ?", "पत्ती लाल रोग का उपचार?", "How to control leaf reddening disease?"),
        pick(language, "వరంగల్ మార్కెట్ ప్రత్తి ధరలు?", "वरंगल मंडी में कपास के भाव?", "What are cotton prices at Warangal market?")
    )
    "Chilli" -> listOf(
        pick(language, "నల్ల తామర పురుగు (బ్లాక్ త్రిప్స్) నివారణ?", "मिर्च में काले थ्रिप्स का नियंत्रण?", "How to control black thrips in chilli?"),
        pick(language, "మిరపలో ఆకుముడత నివారణ మందు?", "मिर्च में पत्ती मरोड़ का इलाज?", "Treatment for leaf curl in chilli?"),
        pick(language, "ఖమ్మం మిర్చి యార్డ్ ధరలు?", "खम्मम मिर्च मंडी भाव?", "What are chilli prices at Khammam market?")
    )
    "Tomato" -> listOf(
        pick(language, "టమోటాలో ఆకుమచ్చ తెగులు నివారణ?", "टमाटर में अगेती झुलसा का उपचार?", "How to control early blight in tomato?"),
        pick(language, "టమోటా కాయతొలుచు పురుగు మందు ఏమిటి?", "टमाटर फल छेदक कीट के लिए कौन सी दवा?", "Which pesticide for fruit borer in tomato?"),
        pick(language, "మదనపల్లె టమోటా మార్కెట్ ధరలు?", "मदनपल्ले टमाटर मंडी भाव?", "What are tomato prices at Madanapalle?")
    )
    "Wheat" -> listOf(
        pick(language, "గోధుమలో కుంకుమ తెగులు నివారణ?", "गेहूं में पीला रतुआ (रस्ट) का उपचार?", "How to control yellow rust in wheat?"),
        pick(language, "గోధుమ కిరీటపు వేర్ల దశలో నీరు ఎప్పుడు పెట్టాలి?", "गेहूं में ताज जड़ (CRI) अवस्था में सिंचाई कब करें?", "When to irrigate wheat at crown root initiation?"),
        pick(language, "గోధుమ కనీస మద్దతు ధర ఎంత?", "गेहूं का न्यूनतम समर्थन मूल्य (MSP) क्या है?", "What is the minimum support price for wheat?")
    )
    "Groundnut" -> listOf(
        pick(language, "వేరుశనగలో తిక్కా ఆకుమచ్చ తెగులు నివారణ?", "मूंगफली में टिक्का रोग का उपचार?", "How to control tikka leaf spot in groundnut?"),
        pick(language, "వేరుశనగకు జిప్సం ఎప్పుడు వేయాలి?", "मूंगफली में जिप्सम कब और कितना डालें?", "When and how much gypsum to apply to groundnut?"),
        pick(language, "వేరుశనగ మార్కెట్ ధరలు ఎంత?", "मूंगफली मंडी भाव क्या हैं?", "What are groundnut mandi prices?")
    )
    "Turmeric" -> listOf(
        pick(language, "పసుపులో దుంప కుళ్లు తెగులు నివారణ?", "हल्दी में कंद सड़न रोग का उपचार?", "How to control rhizome rot in turmeric?"),
        pick(language, "పసుపులో ఎరువుల యాజమాన్యం ఎలా చేయాలి?", "हल्दी में उर्वरक प्रबंधन कैसे करें?", "How to manage fertilizers in turmeric?"),
        pick(language, "నిజామాబాద్ పసుపు మార్కెట్ ధరలు?", "निजामाबाद हल्दी मंडी भाव?", "What are turmeric prices at Nizamabad?")
    )
    "Mango" -> listOf(
        pick(language, "మామిడిలో తేనెమంచు పురుగు నివారణ?", "आम में फुदका (हॉपर) कीट का नियंत्रण?", "How to control hoppers in mango?"),
        pick(language, "మామిడి బూడిద తెగులు నివారణ మందు?", "आम में चूर्णिल आसिता (पाउडरी मिल्ड्यू) की दवा?", "Which fungicide for powdery mildew in mango?"),
        pick(language, "మామిడి కాయ రాలకుండా తీసుకోవాల్సిన జాగ్రత్తలు?", "आम के फल झड़ने से रोकने के उपाय?", "Measures to prevent mango fruit drop?")
    )
    else -> listOf(
        pick(language, "వరిలో అగ్గితెగులు (బ్లాస్ట్) నివారణ?", "धान में झुलसा रोग का उपचार?", "How to control rice blast disease?"),
        pick(language, "వరిలో సుడిదోమ నివారణ మందులు?", "धान में भूरा फुदका (BPH) नियंत्रण?", "How to control brown plant hopper (BPH) in paddy?"),
        pick(language, "ధాన్యం మద్దతు ధర ఎంత?", "धान का समर्थन मूल्य कितना है?", "What is the paddy minimum support price?")
    )
}

fun offlineInstantQuestions(crop: String, language: String): List<String> = when (canonicalCropName(crop)) {
    "Maize" -> listOf(
        pick(language, "మొక్కజొన్నలో కత్తెర పురుగు నివారణ మందులు?", "मक्का में तना छेदक के कीटनाशक?", "Pesticides for stem borer in maize?"),
        pick(language, "ఎకరాకు యూరియా మరియు ఎరువుల మోతాదు ఎంత?", "प्रति एकड़ यूरिया व उर्वरक मात्रा?", "What is the urea and fertilizer dose per acre?"),
        pick(language, "మొక్కజొన్నలో కలుపు నివారణ మందు ఏది?", "मक्का में खरपतवार नाशक कौन सा?", "Which herbicide for weeds in maize?"),
        pick(language, "టర్సికం ఆకు ఎండు తెగులు నివారణ?", "टर्सिकम पत्ती झुलसा रोग का उपचार?", "How to control turcicum leaf blight?")
    )
    "Cotton" -> listOf(
        pick(language, "ప్రత్తిలో గులాబీ రంగు పురుగు నివారణ మందు?", "कपास में गुलाबी सुंडी के लिए दवा?", "Pesticide for pink bollworm in cotton?"),
        pick(language, "ఆకు ఎరుపు తెగులు నివారణ పద్ధతులు?", "पत्ती लाल रोग के उपचार के तरीके?", "Treatment methods for leaf reddening disease?"),
        pick(language, "తెల్లదోమ నివారణకు ఏ మందు పిచికారీ చేయాలి?", "सफेद मक्खी के लिए कौन सी दवा छिड़कें?", "Which spray for whitefly control?"),
        pick(language, "ఎకరాకు పత్తిలో యూరియా ఎరువు మోతాదు?", "प्रति एकड़ कपास में यूरिया मात्रा?", "Urea dose per acre for cotton?")
    )
    "Chilli" -> listOf(
        pick(language, "మిరపలో ఆకుముడత మరియు తామర పురుగుల నివారణ?", "मिर्च में पत्ती मरोड़ व थ्रिप्स नियंत्रण?", "Control of leaf curl and thrips in chilli?"),
        pick(language, "నల్ల తామర పురుగు (బ్లాక్ త్రిప్స్) నివారణ?", "काले थ्रिप्स का नियंत्रण?", "How to control black thrips?"),
        pick(language, "మిరప తోటలో డైబాక్ తెగులు నివారణ?", "मिर्च में डाइबैक रोग का उपचार?", "Treatment for dieback disease in chilli?"),
        pick(language, "మిరపలో మోనోక్రోటోఫాస్ వాడవచ్చా?", "क्या मिर्च में मोनोक्रोटोफॉस का उपयोग कर सकते हैं?", "Can monocrotophos be used on chilli?")
    )
    "Tomato" -> listOf(
        pick(language, "టమోటా ముందస్తు ఆకుమచ్చ తెగులు నివారణ?", "टमाटर में अगेती झुलसा का उपचार?", "Treatment for early blight in tomato?"),
        pick(language, "టమోటా స్టేకింగ్ (కట్టెల మద్దతు) పద్ధతులు?", "टमाटर में स्टेकिंग (सहारा देने) की विधि?", "Staking methods for tomato cultivation?"),
        pick(language, "టమోటాలో కాయతొలుచు పురుగు నివారణ?", "टमाटर में फल छेदक का नियंत्रण?", "Control of fruit borer in tomato?"),
        pick(language, "టమోటాకు డ్రిప్ ద్వారా నీటి యాజమాన్యం?", "टमाटर में ड्रिप सिंचाई प्रबंधन?", "Drip irrigation management for tomato?")
    )
    "Wheat" -> listOf(
        pick(language, "గోధుమలో కుంకుమ తెగులు రకాలు మరియు నివారణ?", "गेहूं में रतुआ रोग के प्रकार व उपचार?", "Types and remedies of wheat rust?"),
        pick(language, "గోధుమ సిఆర్ఐ దశలో నీటి ప్రాముఖ్యత?", "गेहूं में ताज जड़ (CRI) अवस्था में सिंचाई?", "Importance of irrigation at CRI stage in wheat?"),
        pick(language, "గోధుమలో కలుపు నివారణ మందులు?", "गेहूं में खरपतवार नियंत्रण की दवा?", "Weed control herbicides for wheat?"),
        pick(language, "ఎకరాకు గోధుమ విత్తన మోతాదు ఎంత?", "प्रति एकड़ गेहूं की बीज दर कितनी?", "Seed rate per acre for wheat?")
    )
    "Groundnut" -> listOf(
        pick(language, "వేరుశనగలో తిక్కా ఆకుమచ్చ నివారణ మందు?", "मूंगफली में टिक्का रोग के कीटनाशक?", "Fungicide for tikka disease in groundnut?"),
        pick(language, "వేరుశనగ నారుకు విత్తన శుద్ధి పద్ధతి?", "मूंगफली में बीज उपचार की विधि?", "Seed treatment method for groundnut?"),
        pick(language, "ఎకరాకు జిప్సం ఎప్పుడు ఎంత వేయాలి?", "प्रति एकड़ जिप्सम कब और कितना डालें?", "When and how much gypsum per acre?"),
        pick(language, "వేరుశనగలో తెల్ల పురుగు (వైట్ గ్రబ్) నివారణ?", "मूंगफली में सफेद लट (व्हाइट ग्रब) नियंत्रण?", "White grub control in groundnut?")
    )
    "Turmeric" -> listOf(
        pick(language, "పసుపులో దుంప కుళ్లు నివారణోపాయాలు?", "हल्दी में कंद सड़न के उपचार?", "Remedies for rhizome rot in turmeric?"),
        pick(language, "పసుపు విత్తన దుంపల శుద్ధి విధానం?", "हल्दी कंद का बीज उपचार कैसे करें?", "Rhizome seed treatment for turmeric?"),
        pick(language, "పసుపులో ఆకుమచ్చ తెగులు నివారణ?", "हल्दी में पत्ती धब्बा रोग का उपचार?", "Leaf spot control in turmeric?"),
        pick(language, "పసుపు పంట కాలం మరియు కోత సమయం?", "हल्दी की फसल अवधि एवं खुदाई का समय?", "Crop duration and harvesting time for turmeric?")
    )
    "Mango" -> listOf(
        pick(language, "మామిడిలో పూత దశలో తేనెమంచు పురుగు నివారణ?", "आम में बौर के समय फुदका नियंत्रण?", "Hopper control during mango flowering?"),
        pick(language, "మామిడి బూడిద తెగులు నివారణ మందు?", "आम में चूर्णिल आसिता की दवा?", "Powdery mildew spray for mango?"),
        pick(language, "మామిడిలో పండు ఈగ నివారణ ట్రాప్స్?", "आम में फल मक्खी के लिए फेरोमोन ट्रैप?", "Fruit fly pheromone traps in mango?"),
        pick(language, "మామిడి కాయ నాణ్యతకు సూక్ష్మపోషకాల పిచికారీ?", "आम की गुणवत्ता हेतु सूक्ष्म पोषक छिड़काव?", "Micronutrient spray for mango fruit quality?")
    )
    else -> listOf(
        pick(language, "వరిలో అగ్గితెగులు (బ్లాస్ట్) నివారణ మందు మరియు మోతాదు?", "धान में झुलसा रोग का कीटनाशक व मात्रा?", "Fungicide and dose for rice blast?"),
        pick(language, "వరిలో సుడిదోమ (BPH) నివారణ మందులు?", "धान में भूरा फुदका (BPH) की दवा?", "Pesticides for brown plant hopper (BPH) in paddy?"),
        pick(language, "వరి నారుమడికి జింక్ సల్ఫేట్ మోతాదు ఎంత?", "धान नर्सरी में जिंक सल्फेट की मात्रा?", "Zinc sulphate dose for rice nursery?"),
        pick(language, "కాండం తొలుచు పురుగు నివారణ పద్ధతులు?", "तना छेदक कीट के नियंत्रण के तरीके?", "Control methods for stem borer?")
    )
}

fun voiceQuickPrompts(language: String): List<VoiceQuickPrompt> = listOf(
    VoiceQuickPrompt(
        text = pick(
            language,
            "ప్రత్తిలో గులాబీ రంగు పురుగు మందు మోతాదు ఎంత?",
            "कपास में गुलाबी सुंडी की दवा की मात्रा कितनी है?",
            "What pesticide and dose should I spray for pink bollworm in cotton?"
        ),
        badge = "Verified CIBRC"
    ),
    VoiceQuickPrompt(
        text = pick(
            language,
            "వరిలో సుడిదోమ నివారణ ఎలా చేయాలి?",
            "धान में भूरा फुदका (BPH) का नियंत्रण कैसे करें?",
            "How to control brown plant hopper (BPH) in rice?"
        ),
        badge = "ICAR Package"
    ),
    VoiceQuickPrompt(
        text = pick(
            language,
            "గుర్తింపు లేని రసాయన మందు 2 లీటర్లు స్ప్రే చేయవచ్చా?",
            "क्या अज्ञात कीड़े के लिए 2 लीटर बिना ब्रांड वाला रसायन छिड़क सकते हैं?",
            "Can I spray 2 liters of unbranded chemical for unknown worm in cotton?"
        ),
        badge = "Unsafe Test",
        isWarning = true
    )
)

fun mandiRows(crop: String, language: String): List<MandiRow> = when (canonicalCropName(crop)) {
    "Maize" -> listOf(
        MandiRow(
            pick(language, "వరంగల్ మండి", "वरंगल मंडी", "Warangal mandi"),
            "₹2,180 - ₹2,340 / ${pick(language, "క్వింటాల్", "क्विंटल", "quintal")}",
            pick(language, "మోడల్ ధర: ₹2,250", "मॉडल भाव: ₹2,250", "Model price: ₹2,250")
        ),
        MandiRow(
            pick(language, "ఖమ్మం మండి", "खम्मम मंडी", "Khammam mandi"),
            "₹2,150 - ₹2,300 / ${pick(language, "క్వింటాల్", "क्विंटल", "quintal")}",
            pick(language, "మోడల్ ధర: ₹2,220", "मॉडल भाव: ₹2,220", "Model price: ₹2,220")
        ),
        MandiRow(
            pick(language, "కనీస మద్దతు ధర (MSP)", "न्यूनतम समर्थन मूल्य (MSP)", "Minimum Support Price (MSP)"),
            "₹2,090 / ${pick(language, "క్వింటాల్", "क्विंटल", "quintal")}",
            pick(language, "కేంద్ర ప్రభుత్వం", "केंद्र सरकार", "Central Government")
        )
    )
    "Cotton" -> listOf(
        MandiRow(
            pick(language, "వరంగల్ ఎనుమాముల మండి", "वरंगल एनुमामुला मंडी", "Warangal Enumamula mandi"),
            "₹7,150 - ₹7,620 / ${pick(language, "క్వింటాల్", "क्विंटल", "quintal")}",
            pick(language, "మోడల్ ధర: ₹7,450", "मॉडल भाव: ₹7,450", "Model price: ₹7,450")
        ),
        MandiRow(
            pick(language, "ఆదిలాబాద్ మార్కెట్", "आदिलाबाद बाजार", "Adilabad market"),
            "₹7,200 - ₹7,580 / ${pick(language, "క్వింటాల్", "क्विंटल", "quintal")}",
            pick(language, "మోడల్ ధర: ₹7,400", "मॉडल भाव: ₹7,400", "Model price: ₹7,400")
        ),
        MandiRow(
            pick(language, "కనీస మద్దతు ధర (MSP)", "न्यूनतम समर्थन मूल्य (MSP)", "Minimum Support Price (MSP)"),
            "₹7,121 / ${pick(language, "క్వింటాల్", "क्विंटल", "quintal")}",
            pick(language, "మధ్యస్థ పింజ ప్రత్తి", "मध्यम रेशा कपास", "Medium staple cotton")
        )
    )
    "Chilli" -> listOf(
        MandiRow(
            pick(language, "ఖమ్మం మిర్చి యార్డ్ (తేజ)", "खम्मम मिर्च यार्ड (तेजा)", "Khammam chilli yard (Teja)"),
            "₹14,500 - ₹18,200 / ${pick(language, "క్వింటాల్", "क्विंटल", "quintal")}",
            pick(language, "మోడల్ ధర: ₹16,800", "मॉडल भाव: ₹16,800", "Model price: ₹16,800")
        ),
        MandiRow(
            pick(language, "వరంగల్ మార్కెట్ (డీలక్స్)", "वरंगल बाजार (डीलक्स)", "Warangal market (Deluxe)"),
            "₹13,800 - ₹17,500 / ${pick(language, "క్వింటాల్", "क्विंटल", "quintal")}",
            pick(language, "మోడల్ ధర: ₹15,900", "मॉडल भाव: ₹15,900", "Model price: ₹15,900")
        ),
        MandiRow(
            pick(language, "గుంటూరు మార్కెట్", "गुंटूर बाजार", "Guntur market"),
            "₹14,200 - ₹18,000 / ${pick(language, "క్వింటాల్", "क्विंटल", "quintal")}",
            pick(language, "మోడల్ ధర: ₹16,500", "मॉडल भाव: ₹16,500", "Model price: ₹16,500")
        )
    )
    "Tomato" -> listOf(
        MandiRow(
            pick(language, "మదనపల్లె టమోటా మార్కెట్", "मदनपल्ले टमाटर मंडी", "Madanapalle tomato market"),
            "₹1,200 - ₹1,800 / ${pick(language, "క్వింటాల్", "क्विंटल", "quintal")}",
            pick(language, "మోడల్ ధర: ₹1,500", "मॉडल भाव: ₹1,500", "Model price: ₹1,500")
        ),
        MandiRow(
            pick(language, "బోయిన్‌పల్లి హైదరాబాద్", "बोवेनपल्ली हैदराबाद", "Bowenpally Hyderabad"),
            "₹1,300 - ₹1,900 / ${pick(language, "క్వింటాల్", "क्विंटल", "quintal")}",
            pick(language, "మోడల్ ధర: ₹1,600", "मॉडल भाव: ₹1,600", "Model price: ₹1,600")
        ),
        MandiRow(
            pick(language, "కోలార్ మార్కెట్", "कोलार बाजार", "Kolar market"),
            "₹1,250 - ₹1,750 / ${pick(language, "క్వింటాల్", "क्विंटल", "quintal")}",
            pick(language, "గ్రేడ్-1 రకం", "ग्रेड-1 किस्म", "Grade-1 quality")
        )
    )
    "Wheat" -> listOf(
        MandiRow(
            pick(language, "ఖమ్మం ధాన్యపు మార్కెట్", "खम्मम अनाज मंडी", "Khammam grain mandi"),
            "₹2,350 - ₹2,550 / ${pick(language, "క్వింటాల్", "क्विंटल", "quintal")}",
            pick(language, "మోడల్ ధర: ₹2,450", "मॉडल भाव: ₹2,450", "Model price: ₹2,450")
        ),
        MandiRow(
            pick(language, "ఇండోర్ మార్కెట్ (మధ్యప్రదేశ్)", "इंदौर मंडी (मध्य प्रदेश)", "Indore mandi (MP)"),
            "₹2,400 - ₹2,600 / ${pick(language, "క్వింటాల్", "क्विंटल", "quintal")}",
            pick(language, "శర్బతి గోధుమ", "शरबती गेहूं", "Sharbati wheat")
        ),
        MandiRow(
            pick(language, "కనీస మద్దతు ధర (MSP)", "न्यूनतम समर्थन मूल्य (MSP)", "Minimum Support Price (MSP)"),
            "₹2,275 / ${pick(language, "క్వింటాల్", "क्విंटल", "quintal")}",
            pick(language, "కేంద్ర ప్రభుత్వం రబీ 2024", "केंद्र सरकार रबी 2024", "Central Government Rabi 2024")
        )
    )
    "Groundnut" -> listOf(
        MandiRow(
            pick(language, "మహబూబ్‌నగర్ మండి", "महबूबनगर मंडी", "Mahbubnagar mandi"),
            "₹6,200 - ₹6,800 / ${pick(language, "క్వింటాల్", "क्विंटल", "quintal")}",
            pick(language, "మోడల్ ధర: ₹6,500", "मॉडल भाव: ₹6,500", "Model price: ₹6,500")
        ),
        MandiRow(
            pick(language, "అనంతపురం మార్కెట్", "अनंतपुर बाजार", "Anantapur market"),
            "₹6,100 - ₹6,750 / ${pick(language, "క్వింటాల్", "क्విंटल", "quintal")}",
            pick(language, "పాడ్స్ మోడల్ ధర: ₹6,400", "पॉड्स मॉडल भाव: ₹6,400", "Pods model price: ₹6,400")
        ),
        MandiRow(
            pick(language, "కనీస మద్దతు ధర (MSP)", "न्यूनतम समर्थन मूल्य (MSP)", "Minimum Support Price (MSP)"),
            "₹6,377 / ${pick(language, "క్వింటాల్", "क्విंटल", "quintal")}",
            pick(language, "కేంద్ర ప్రభుత్వం ఖరీఫ్ 2024", "केंद्र सरकार खरीफ 2024", "Central Government Kharif 2024")
        )
    )
    "Turmeric" -> listOf(
        MandiRow(
            pick(language, "నిజామాబాద్ పసుపు మండి", "निजामाबाद हल्दी मंडी", "Nizamabad turmeric mandi"),
            "₹12,500 - ₹15,800 / ${pick(language, "క్వింటాల్", "क्विंटल", "quintal")}",
            pick(language, "మోడల్ ధర (కొమ్ము): ₹14,200", "मॉडल भाव (फिंगर): ₹14,200", "Model price (Finger): ₹14,200")
        ),
        MandiRow(
            pick(language, "సాంగ్లీ మార్కెట్ (మహారాష్ట్ర)", "सांगली बाजार (महाराष्ट्र)", "Sangli market (MH)"),
            "₹13,000 - ₹16,200 / ${pick(language, "క్వింటాల్", "क्వింటల్", "quintal")}",
            pick(language, "రాజాపురి నాణ్యత", "राजापुरी गुणवत्ता", "Rajapuri quality")
        ),
        MandiRow(
            pick(language, "ఈరోడ్ మార్కెట్ (తమిళనాడు)", "ईरोड बाजार (तमिलनाडु)", "Erode market (TN)"),
            "₹12,200 - ₹15,500 / ${pick(language, "క్వింటాల్", "క్వింటల్", "quintal")}",
            pick(language, "మోడల్ ధర: ₹13,800", "मॉडल भाव: ₹13,800", "Model price: ₹13,800")
        )
    )
    "Mango" -> listOf(
        MandiRow(
            pick(language, "నూజివీడు మామిడి మార్కెట్", "नूज़िवीडु आम मंडी", "Nuzvid mango market"),
            "₹35,000 - ₹55,000 / ${pick(language, "టన్ను", "टन", "tonne")}",
            pick(language, "బంగినపల్లి మోడల్ ధర", "बंगिनपल्ली मॉडल भाव", "Banganapalli model price")
        ),
        MandiRow(
            pick(language, "కొత్తగూడెం మార్కెట్", "कोत्तागुडेम बाजार", "Kothagudem market"),
            "₹32,000 - ₹48,000 / ${pick(language, "టన్ను", "टन", "tonne")}",
            pick(language, "తోతాపురి రకం", "तोतापुरी किस्म", "Totapuri variety")
        ),
        MandiRow(
            pick(language, "గడ్డిఅన్నారం ఫ్రూట్ మార్కెట్", "गड्डीअन्नारम फल बाजार", "Gaddiannaram fruit market"),
            "₹40,000 - ₹60,000 / ${pick(language, "టన్ను", "टन", "tonne")}",
            pick(language, "ప్రీమియం గ్రేడ్", "प्रीमियम ग्रेड", "Premium grade")
        )
    )
    else -> listOf(
        MandiRow(
            pick(language, "నల్గొండ ధాన్యం మండి", "नलगोंडा धान मंडी", "Nalgonda paddy mandi"),
            "₹2,203 - ₹2,320 / ${pick(language, "క్వింటాల్", "क्विंटल", "quintal")}",
            pick(language, "గ్రేడ్-A వరి", "ग्रेड-A धान", "Grade-A paddy")
        ),
        MandiRow(
            pick(language, "నిజామాబాద్ మార్కెట్", "निजामाबाद बाजार", "Nizamabad market"),
            "₹2,183 - ₹2,300 / ${pick(language, "క్వింటాల్", "क्विంటల్", "quintal")}",
            pick(language, "సాధారణ వరి", "सामान्य धान", "Common paddy")
        ),
        MandiRow(
            pick(language, "కనీస మద్దతు ధర (MSP)", "न्यूनतम समर्थन मूल्य (MSP)", "Minimum Support Price (MSP)"),
            "₹2,203 / ${pick(language, "క్వింటాల్", "क्विంటల్", "quintal")}",
            pick(language, "కేంద్ర ప్రభుత్వం", "केंद्र सरकार", "Central Government")
        )
    )
}

fun pesticideRecommendation(crop: String, language: String): String = pick(
    language,
    "పురుగుమందుల మోతాదుల కొరకు CIBRC ధృవీకరించిన అధికారిక లేబుల్ సిఫార్సులను మాత్రమే పాటించాలి. మీ పంట మరియు సమస్య ఆధారంగా ఖచ్చితమైన మోతాదు వివరాలు లేదా వ్యవసాయ విస్తరణ అధికారి సహాయం పొందడానికి ప్రశ్నను అడగండి.",
    "कीटनाशकों की मात्रा के लिए केवल CIBRC द्वारा स्वीकृत आधिकारिक लेबल सिफारिशों का ही पालन करें। अपनी फसल और कीट/रोग के अनुसार सटीक मात्रा या कृषि विस्तार अधिकारी की सलाह के लिए प्रश्न पूछें।",
    "For pesticide dosages, always follow official CIBRC certified label recommendations. Please ask your question specifying the crop and pest to receive certified dosage or consult your local agricultural extension officer."
)
