package com.bolkisan.farmer.presentation.common

/** Small demo-content helpers so every screen changes language together. */

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

fun localizedCropName(crop: String, language: String): String = when (crop) {
    "Maize" -> pick(language, "మొక్కజొన్న", "मक्का", "Maize")
    "Cotton" -> pick(language, "ప్రత్తి", "कपास", "Cotton")
    "Chilli" -> pick(language, "మిరప", "मिर्च", "Chilli")
    "Rice / Paddy", "Rice", "Paddy" -> pick(language, "వరి", "धान", "Rice / Paddy")
    "Wheat" -> pick(language, "గోధుమ", "गेहूं", "Wheat")
    "Tomato" -> pick(language, "టమోటా", "टमाटर", "Tomato")
    "Turmeric" -> pick(language, "పసుపు", "हल्दी", "Turmeric")
    "Groundnut" -> pick(language, "వేరుశనగ", "मूंगफली", "Groundnut")
    "Mango" -> pick(language, "మామిడి", "आम", "Mango")
    else -> crop
}

/** Crops shown in the home crop picker, in the same order as before. */
fun cropOptions(language: String): List<Pair<String, String>> = listOf(
    "Maize" to localizedCropName("Maize", language),
    "Cotton" to localizedCropName("Cotton", language),
    "Chilli" to localizedCropName("Chilli", language),
    "Rice / Paddy" to localizedCropName("Rice / Paddy", language),
    "Wheat" to localizedCropName("Wheat", language),
    "Tomato" to localizedCropName("Tomato", language)
)

/** Extra crops supported by the photo diagnosis screen. */
fun diseaseCropOptions(language: String): List<Pair<String, String>> = listOf(
    "Cotton",
    "Rice / Paddy",
    "Chilli",
    "Tomato",
    "Maize",
    "Turmeric",
    "Groundnut",
    "Mango"
).map { it to localizedCropName(it, language) }

fun cropFilterOptions(language: String): List<Pair<String, String>> {
    val all = pick(language, "అన్నీ (All)", "सभी (All)", "All")
    val shortKeys = mapOf("Rice / Paddy" to "Rice")
    val crops = listOf("Cotton", "Rice / Paddy", "Chilli", "Tomato", "Wheat", "Maize")
    return listOf("All" to all) + crops.map { key ->
        val name = localizedCropName(key, language)
        val label = if (language == "en") name else "$name (${shortKeys[key] ?: key})"
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
    plantPartOptions(language).firstOrNull { it.first == partKey }?.second
        ?: partKey.replaceFirstChar { it.uppercase() }

fun homeSampleQuestions(crop: String, language: String): List<String> = when (crop) {
    "Maize" -> listOf(
        pick(language, "కత్తెర పురుగు నివారణ మందులు?", "मक्का में तना छेदक कीट का नियंत्रण कैसे करें?", "How to control stem borer in maize?"),
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
    else -> listOf(
        pick(language, "వరిలో అగ్గితెగులు (బ్లాస్ట్) నివారణ?", "धान में झुलसा रोग का उपचार?", "How to control rice blast disease?"),
        pick(language, "వరిలో సుడిదోమ నివారణ మందులు?", "धान में भूरा फुदका (BPH) नियंत्रण?", "How to control brown plant hopper (BPH) in paddy?"),
        pick(language, "ధాన్యం మద్దతు ధర ఎంత?", "धान का समर्थन मूल्य कितना है?", "What is the paddy minimum support price?")
    )
}

fun offlineInstantQuestions(crop: String, language: String): List<String> = when (crop) {
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

fun mandiRows(crop: String, language: String): List<MandiRow> = when (crop) {
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
    else -> listOf(
        MandiRow(
            pick(language, "నల్గొండ ధాన్యం మండి", "नलगोंडा धान मंडी", "Nalgonda paddy mandi"),
            "₹2,203 - ₹2,320 / ${pick(language, "క్వింటాల్", "क्विंटल", "quintal")}",
            pick(language, "గ్రేడ్-A వరి", "ग्रेड-A धान", "Grade-A paddy")
        ),
        MandiRow(
            pick(language, "నిజామాబాద్ మార్కెట్", "निजामाबाद बाजार", "Nizamabad market"),
            "₹2,183 - ₹2,300 / ${pick(language, "క్వింటాల్", "क्विंटल", "quintal")}",
            pick(language, "సాధారణ వరి", "सामान्य धान", "Common paddy")
        ),
        MandiRow(
            pick(language, "కనీస మద్దతు ధర (MSP)", "न्यूनतम समर्थन मूल्य (MSP)", "Minimum Support Price (MSP)"),
            "₹2,203 / ${pick(language, "క్వింటాల్", "क्विंटल", "quintal")}",
            pick(language, "కేంద్ర ప్రభుత్వం", "केंद्र सरकार", "Central Government")
        )
    )
}

fun pesticideRecommendation(crop: String, language: String): String = when (crop) {
    "Maize" -> pick(
        language,
        "కత్తెర పురుగు నివారణకు: క్లోరాంట్రానిలిప్రోల్ 18.5% SC @ 6.6 ml లేదా ఇమామెక్టిన్ బెంజోయేట్ 5% SG @ 8.0 g ప్రతి 15 లీటర్ల నీటి పంపునకు కలపాలి.",
        "तना छेदक के लिए: क्लोरैंट्रानिलिप्रोल 18.5% SC @ 6.6 ml या इमामेक्टिन बेंजोएट 5% SG @ 8.0 g प्रति 15 लीटर पानी में मिलाएं।",
        "For stem borer: Chlorantraniliprole 18.5% SC @ 6.6 ml or Emamectin benzoate 5% SG @ 8.0 g per 15 liters of water."
    )
    "Cotton" -> pick(
        language,
        "గులాబీ రంగు పురుగు నివారణకు: క్లోరాంట్రానిలిప్రోల్ 18.5% SC @ 6.6 ml లేదా ప్రొఫెనోఫాస్ 50% EC @ 30 ml ప్రతి 15 లీటర్ల నీటికి.",
        "गुलाबी सुंडी के लिए: क्लोरैंट्रानिलिप्रोल 18.5% SC @ 6.6 ml या प्रोफेनोफॉस 50% EC @ 30 ml प्रति 15 लीटर पानी।",
        "For pink bollworm: Chlorantraniliprole 18.5% SC @ 6.6 ml or Profenophos 50% EC @ 30 ml per 15 liters of water."
    )
    "Chilli" -> pick(
        language,
        "తామర పురుగుల నివారణకు: స్పైనోటోరం 11.7% SC @ 15 ml లేదా ఫిప్రోనిల్ 5% SC @ 30 ml ప్రతి 15 లీటర్ల నీటికి.",
        "थ्रिप्स के लिए: स्पिनोटोरम 11.7% SC @ 15 ml या फिप्रोनिल 5% SC @ 30 ml प्रति 15 लीटर पानी।",
        "For thrips: Spinetoram 11.7% SC @ 15 ml or Fipronil 5% SC @ 30 ml per 15 liters of water."
    )
    else -> pick(
        language,
        "వరి అగ్గితెగులు కొరకు: ట్రైసైక్లాజోల్ 75% WP @ 9.0 g లేదా ఐసోప్రోథియోలేన్ 40% EC @ 22.5 ml ప్రతి 15 లీటర్ల నీటికి.",
        "धान झुलसा के लिए: ट्राइसाइक्लाजोल 75% WP @ 9.0 g या आइसोप्रोथियोलेन 40% EC @ 22.5 ml प्रति 15 लीटर पानी।",
        "For rice blast: Tricyclazole 75% WP @ 9.0 g or Isoprothiolane 40% EC @ 22.5 ml per 15 liters of water."
    )
}
