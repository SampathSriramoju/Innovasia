package com.bolkisan.farmer.presentation.common

/**
 * Central runtime translation catalog.
 *
 * The farmer language is stored in [com.bolkisan.farmer.data.repository.BolKisanRepository]
 * as an observable StateFlow, so screens must read the latest value and pass it here.
 * Keeping the strings in one place avoids the old problem of screens hardcoding Telugu.
 */
class AppStrings(val code: String) {

    private fun t(te: String, hi: String, en: String): String = when (code) {
        "te" -> te
        "hi" -> hi
        else -> en
    }

    // ---------------------------------------------------------------- common
    val back get() = t("వెనుకకు", "वापस", "Back")
    val ok get() = t("సరే", "ठीक है", "OK")
    val close get() = t("మూసివేయి", "बंद करें", "Close")
    val cancel get() = t("రద్దు చేయి", "रद्द करें", "Cancel")
    val dismiss get() = t("మూసివేయి", "खारिज करें", "Dismiss")

    val onlineTitle get() = t(
        "ఆన్‌లైన్ - Gemini & విస్తరణ నెట్‌వర్క్‌కు కనెక్ట్ అయింది",
        "ऑनलाइन - जेमिनी व विस्तार नेटवर्क से जुड़ा है",
        "Online - Connected to Gemini & Extension Network"
    )
    val offlineTitle get() = t(
        "ఆఫ్‌లైన్ - స్థానిక ICAR ధృవీకరించిన మార్గదర్శకాలు",
        "ऑफ़लाइन - स्थानीय आईसीएआर सत्यापित दिशानिर्देश",
        "Offline - Using Local Verified ICAR Guidelines"
    )
    val onlineSubtitle get() = t(
        "ఆన్‌లైన్ - AI & విస్తరణ వ్యవస్థతో కనెక్ట్ అయింది",
        "ऑनलाइन - एआई व विस्तार नेटवर्क से जुड़ा है",
        "Cloud synced with extension officers"
    )
    val offlineSubtitle get() = t(
        "ఆఫ్‌లైన్ - స్థానిక ICAR ధృవీకరించిన సమాచారం",
        "ऑफ़लाइन - स्थानीय आईसीएआर ज्ञानकोश सक्रिय",
        "Local zero-latency verified knowledge"
    )

    // -------------------------------------------------------- language screen
    val languageScreenSubtitle get() = t(
        "రైతు వాయిస్ అసిస్టెంట్",
        "कृषि वॉयस सहायक",
        "Farmer Voice Assistant"
    )
    val languageChoosePrompt get() = t(
        "మీ భాషను ఎంచుకోండి",
        "अपनी भाषा चुनें",
        "Choose your language"
    )
    val languageTeluguDesc get() = t(
        "ఆంధ్రప్రదేశ్ & తెలంగాణ రైతుల కోసం",
        "आंध्र प्रदेश व तेलंगाना के किसानों के लिए",
        "For farmers of Andhra Pradesh & Telangana"
    )
    val languageHindiDesc get() = t(
        "అన్ని రాష్ట్రాల రైతుల కోసం",
        "सभी राज्यों के किसानों के लिए",
        "For farmers across all states"
    )
    val languageEnglishDesc get() = t(
        "జాతీయ & విస్తరణ సహాయం",
        "राष्ट्रीय व विस्तार सहायता",
        "National & extension assistance"
    )

    // --------------------------------------------------------------- login
    val loginTitle get() = t("రైతు లాగిన్", "किसान लॉगिन", "Farmer Login")
    val mobileNumberLabel get() = t("మొబైల్ సంఖ్య", "मोबाइल नंबर", "Mobile Number")
    val getOtp get() = t("OTP పంపండి", "OTP भेजें", "Get OTP")
    val sendingOtp get() = t("పంపుతున్నాము...", "भेज रहे हैं...", "Sending...")
    val otpLabel get() = t("OTP కోడ్ (6 అంకెలు)", "OTP कोड (6 अंक)", "OTP Code (6 digits)")
    val loginButton get() = t("లాగిన్ అవ్వండి", "लॉगिन करें", "Login")
    val verifyingOtp get() = t("ధృవీకరిస్తున్నాము...", "सत्यापित कर रहे हैं...", "Verifying...")
    val demoAccount get() = t("డెమో రైతు ఖాతా నింపండి", "डेमो किसान खाता भरें", "Fill Demo Farmer Account")

    // ---------------------------------------------------------------- home
    val appTagline get() = t(
        "రైతు సేవలో AI వాయిస్ అసిస్టెంట్",
        "किसान सेवा में AI वॉयस सहायक",
        "Farmer's AI Voice Assistant"
    )
    val onlineAiBadge get() = t("ఆన్‌లైన్ AI", "ऑनलाइन AI", "Online AI")
    val offlineIcarBadge get() = t("ఆఫ్‌లైన్ ICAR", "ऑफ़लाइन ICAR", "Offline ICAR")
    val chatOnlineStatus get() = t("ONLINE", "ONLINE", "ONLINE")
    val chatOfflineStatus get() = t("OFFLINE", "OFFLINE", "OFFLINE")
    val chatComposerHint get() = t(
        "వ్యవసాయ సందేహాన్ని టైప్ చేయండి...",
        "अपना कृषि प्रश्न टाइप करें...",
        "Type your farming question..."
    )
    val takePhotoSheet get() = t("ఫోటో తీయండి", "फोटो खींचें", "Take Photo")
    val chooseGallerySheet get() = t("గ్యాలరీ నుండి ఎంచుకోండి", "गैलरी से चुनें", "Choose from Gallery")
    val attachImageSheet get() = t("పంట తెగులు చిత్రాన్ని జతచేయండి", "फसल रोग का चित्र संलग्न करें", "Attach Crop Photo for Diagnosis")
    val removeAttachment get() = t("తొలగించు", "हटाएं", "Remove")
    val chatWelcomeTitle get() = t(
        "నమస్కారం! బోల్ కిసాన్ కి స్వాగతం",
        "नमस्ते! बोल किसान में आपका स्वागत है",
        "Welcome to Bol Kisan!"
    )
    val chatWelcomeDesc get() = t(
        "ఏ పంట గురించైనా, తెగుళ్లు, ఎరువులు లేదా వ్యవసాయ పద్ధతుల గురించి నేరుగా అడగండి. ఫోటో కూడా జతచేయవచ్చు.",
        "किसी भी फसल, कीट, रोग, खाद या कृषि तकनीक के बारे में सीधे पूछें। फोटो भी संलग्न कर सकते हैं।",
        "Ask anything about any crop, pest, disease, fertilizer, or organic practice. You can also attach photos."
    )
    val thinkingIndicator get() = t(
        "సమాధానం సిద్ధం చేస్తున్నాము...",
        "उत्तर तैयार किया जा रहा है...",
        "Bol Kisan is thinking..."
    )
    val followUpHeader get() = t(
        "సంబంధిత ప్రశ్నలు:",
        "संबंधित प्रश्न:",
        "Suggested Follow-ups:"
    )
    val tapToAskFollowUp get() = t(
        "నొక్కి అడగండి",
        "टैप करके पूछें",
        "Tap to ask"
    )
    val clarificationHeader get() = t(
        "స్పష్టత అవసరం:",
        "स्पष्टीकरण आवश्यक:",
        "Clarification Needed:"
    )
    val diseaseCameraChip get() = t("తెగులు కెమెరా", "रोग कैमरा", "Disease Camera")
    val pesticidesChip get() = t("పురుగు మందులు (CIBRC)", "कीटनाशक (CIBRC)", "Pesticides (CIBRC)")
    val mandiChip get() = t("మండి ధరలు", "मंडी भाव", "Mandi Prices")
    val weatherChip get() = t("వాతావరణం", "मौसम", "Weather")
    val ticketsChip get() = t("అధికారి టికెట్లు", "अधिकारी टिकट", "Tickets")
    val offlineLibraryChip get() = t("ఆఫ్‌లైన్ లైబ్రరీ", "ऑफ़लाइन ज्ञानकोश", "Offline Library")

    val heroSearching get() = t(
        "జ్ఞానకోశంలో సమాధానం వెతుకుతున్నాము...",
        "कृषि ज्ञानकोश में खोज रहे हैं...",
        "Searching Agricultural Knowledge Base..."
    )
    val heroListening get() = t(
        "వింటున్నాను... మీ ప్రశ్న అడగండి",
        "सुन रहा हूँ... अपना प्रश्न बोलिए",
        "Listening... Please ask your question"
    )
    val heroSpeaking get() = t(
        "సమాధానం చదువుతున్నాము",
        "उत्तर सुना रहे हैं...",
        "Speaking Answer..."
    )
    val heroTapToSpeak get() = t(
        "వాయిస్ ద్వారా అడగండి",
        "आवाज़ से पूछें",
        "Ask via Voice"
    )
    fun heroHint(crop: String): String = t(
        "నొక్కి మాట్లాడండి • $crop లేదా ఇతర ఏ పంట గురించైనా అడగండి",
        "माइक दबाकर बोलें • $crop या किसी भी फसल की समस्या पूछें",
        "Tap the mic • Ask about $crop or any pest, disease, fertilizer"
    )
    val offlineBanner get() = t(
        "ఆఫ్‌లైన్ మోడ్: స్థానిక ICAR డేటాబేస్ నుండి తక్షణ సమాధానాలు లభిస్తాయి. ప్రశ్న నొక్కండి.",
        "ऑफ़लाइन मोड: स्थानीय ICAR ज्ञानकोश से त्वरित समाधान उपलब्ध हैं। प्रश्न चुनें।",
        "Offline Mode: Instant verified ICAR answers from local database. Tap a question."
    )
    val transcriptHeader get() = t(
        "మీరు అడిగిన ప్రశ్న (తనిఖీ చేయండి / మార్చండి):",
        "पहचाना गया प्रश्न (जांचें / सुधारें):",
        "Recognized Question (Preview & Edit):"
    )
    val askButton get() = t("సమాధానం పొందండి", "उत्तर पाएं", "Submit")
    val stopAudio get() = t("ఆడియో ఆపు", "ऑडियो रोकें", "Stop Audio")
    val typedQuestionHint get() = t(
        "లేదా ప్రశ్న టైప్ చేయండి...",
        "या प्रश्न टाइप करें...",
        "Or type a farming question..."
    )
    val bannedWarning get() = t(
        "హెచ్చరిక: నిషేధిత రసాయనం / అధికారి సమీక్ష అవసరం",
        "चेतावनी: प्रतिबंधित रसायन / अधिकारी समीक्षा आवश्यक",
        "WARNING: Banned Agrochemical / Officer Escalation Required"
    )
    val escalatedSuccess get() = t(
        "అధికారికి విజయవంతంగా పంపబడింది!",
        "सफलतापूर्वक अधिकारी को भेजा गया!",
        "Escalated to Extension Officer!"
    )
    fun escalatedNote(ticket: String): String = t(
        "టికెట్ నంబర్: $ticket\nవిస్తరణ అధికారి సమీక్షించి పరిష్కారం అందిస్తారు.",
        "टिकट नंबर: $ticket\nविस्तार अधिकारी समीक्षा कर समाधान देंगे।",
        "Ticket number: $ticket\nThe extension officer will review and provide a solution."
    )
    val escalateButton get() = t(
        "అధికారికి పంపు",
        "कृषि अधिकारी को भेजें",
        "Escalate to Human Officer"
    )
    val escalationFailed get() = t(
        "అధికారికి పంపడం విఫలమైంది. దయచేసి మళ్ళీ ప్రయత్నించండి.",
        "अधिकारी को भेजना विफल रहा। कृपया पुनः प्रयास करें।",
        "Could not escalate to an officer. Please try again."
    )
    fun historyHeader(count: Int): String = t(
        "గత సంభాషణలు ($count)",
        "पिछली बातचीत ($count)",
        "Conversation History ($count)"
    )
    val clear get() = t("క్లియర్", "हटाएं", "Clear")
    val historyEmpty get() = t(
        "ఇప్పటివరకు సంభాషణలు లేవు. పైన ఉన్న వాయిస్ మైక్ నొక్కి అడగండి!",
        "अभी तक कोई बातचीत नहीं हुई। ऊपर माइक दबाकर पूछें!",
        "No conversations yet. Tap the microphone above to ask!"
    )
    val selectCrop get() = t("పంటను ఎంచుకోండి", "फसल चुनें", "Select Crop")
    val errorAnswer get() = t(
        "క్షమించండి, సమాధానం పొందలేకపోయాము. దయచేసి మళ్ళీ ప్రయత్నించండి.",
        "क्षमा करें, उत्तर प्राप्त नहीं हो सका। कृपया पुनः प्रयास करें।",
        "Sorry, could not retrieve answer. Please try again."
    )

    fun mandiTitle(crop: String): String = t(
        "మార్కెట్ ధరలు (మండి) - $crop",
        "मंडी भाव - $crop",
        "Mandi Prices - $crop"
    )
    val mandiLiveReport get() = t(
        "Agmarknet లైవ్ మార్కెట్ నివేదిక (ఈరోజు):",
        "Agmarknet लाइव मंडी रिपोर्ट (आज):",
        "Agmarknet live market report (today):"
    )

    val weatherTitle get() = t(
        "వాతావరణ సమాచారం (IMD వ్యవసాయ సలహా)",
        "मौसम जानकारी (IMD कृषि सलाह)",
        "Weather Advisory (IMD Agro Advisory)"
    )
    val weatherBulletin get() = t(
        "తెలంగాణ ప్రాంత వ్యవసాయ వాతావరణ బులెటిన్:",
        "तेलंगाना क्षेत्रीय कृषि मौसम बुलेटिन:",
        "Telangana regional agricultural weather bulletin:"
    )
    val weatherToday get() = t(
        "ఈరోజు: ఉష్ణోగ్రత 30°C • తేమ 62% • వర్షపాతం 10%",
        "आज: तापमान 30°C • आर्द्रता 62% • वर्षा 10%",
        "Today: Temperature 30°C • Humidity 62% • Rainfall 10%"
    )
    val weatherSpray get() = t(
        "పిచికారీ అనుకూలత: చాలా అనుకూలం (గాలి వేగం 7 km/h)",
        "छिड़काव अनुकूलता: बहुत अनुकूल (हवा की गति 7 km/h)",
        "Spray suitability: Highly suitable (wind speed 7 km/h)"
    )
    val weatherThreeDay get() = t(
        "రాగల 3 రోజుల సూచన:",
        "अगले 3 दिनों का पूर्वानुमान:",
        "3-day outlook:"
    )
    val weatherTomorrow get() = t(
        "• రేపు: పొడి వాతావరణం, ఎరువులు మరియు మందుల పిచికారీకి సరైన సమయం.",
        "• कल: शुष्क मौसम, उर्वरक व कीटनाशक छिड़काव के लिए उपयुक्त समय।",
        "• Tomorrow: Dry weather, good time for fertilizer and pesticide spraying."
    )
    val weatherDayAfter get() = t(
        "• ఎల్లుండి: పాక్షికంగా మేఘావృతం, గరిష్ట ఉష్ణోగ్రత 31°C.",
        "• परसों: आंशिक बादल, अधिकतम तापमान 31°C।",
        "• Day after: Partly cloudy, maximum temperature 31°C."
    )

    val pesticideTitle get() = t(
        "పురుగు మందుల భద్రత (CIBRC)",
        "कीटनाशक सुरक्षा (CIBRC)",
        "Pesticide Safety (CIBRC)"
    )
    fun pesticideDoseFor(crop: String): String = t(
        "$crop కొరకు CIBRC ధృవీకరించిన మోతాదు:",
        "$crop के लिए CIBRC सत्यापित मात्रा:",
        "CIBRC verified dose for $crop:"
    )
    val pesticidePumpLabel get() = t(
        "15 లీటర్ల పంపు మోతాదు (Knapsack Pump):",
        "15 लीटर पंप मात्रा (Knapsack Pump):",
        "15-liter pump dose (Knapsack Pump):"
    )
    val cameraDiagnosis get() = t("కెమెరా రోగ నిర్ధారణ", "कैमरा रोग निदान", "Camera Diagnosis")

    val offlineVoiceTitle get() = t(
        "ఆఫ్‌లైన్ వాయిస్ స్థితి",
        "ऑफ़लाइन आवाज़ स्थिति",
        "Offline Voice Status"
    )
    val offlineVoiceMessage get() = t(
        "ఈ మొబైల్ పరికరంలో ఆఫ్‌లైన్ వాయిస్ రికగ్నిషన్ మోడల్ ఇంకా డౌన్‌లోడ్ కాలేదు. ఇంటర్నెట్ లేకుండా ధృవీకరించిన సమాధానం కోసం కింద ఉన్న ప్రశ్నను నేరుగా ఎంచుకోండి:",
        "इस डिवाइस में ऑफ़लाइन वॉयस मॉडल उपलब्ध नहीं है। बिना इंटरनेट तुरंत समाधान के लिए नीचे दिए गए प्रश्न को चुनें:",
        "On-device offline voice model is not installed on this device. Tap a verified crop question below for instant offline response:"
    )

    val chemicalTitle get() = t(
        "రసాయనం నిర్ధారణ",
        "कीटनाशक सत्यापन",
        "Confirm Agrochemical"
    )
    val chemicalIdentified get() = t(
        "వాయిస్ ద్వారా గుర్తించిన రసాయనం:",
        "पहचाना गया कीटनाशक:",
        "Identified Agrochemical / Trade Name:"
    )
    val chemicalWarning get() = t(
        "రసాయన తప్పుడు మోతాదు పంటను మాడ్చేస్తుంది లేదా నష్టం కలిగిస్తుంది. మీరు అడిగింది ఈ రసాయనం గురించేనా?",
        "गलत रासायनिक मात्रा से फसल जल सकती है। क्या आप इसी कीटनाशक के बारे में पूछ रहे हैं?",
        "Incorrect dosages cause chemical phytotoxicity or crop burn. Are you asking about this exact product?"
    )
    val confirmChemical get() = t("అవును, సరైనదే", "हाँ, सही है", "Yes, Confirm")
    val editText get() = t("మార్చండి", "सुधारें", "Edit Text")

    // --------------------------------------------------------------- voice
    val voiceTitle get() = t("వాయిస్ అసిస్టెంట్", "वॉयस सहायक", "Voice Assistant")
    fun voiceSubtitle(crop: String): String = t(
        "పంట: $crop • ధృవీకరించిన సమాచారం",
        "फसल: $crop • सत्यापित जानकारी",
        "Crop: $crop • Verified information"
    )
    val quickQuestions get() = t(
        "తరచుగా అడిగే ప్రశ్నలు",
        "अक्सर पूछे जाने वाले प्रश्न",
        "Quick Questions"
    )
    val voiceListening get() = t(
        "వింటున్నాను... మాట్లాడండి",
        "सुन रहे हैं... बोलिए",
        "Listening... Speak now"
    )
    val voiceTapToSpeak get() = t("నొక్కి మాట్లాడండి", "दबाकर बोलें", "Tap to Speak")
    val checkingSafety get() = t(
        "రసాయన భద్రతా నిబంధనలు తనిఖీ చేస్తున్నాము...",
        "रासायनिक सुरक्षा नियम जांच रहे हैं...",
        "Checking agrochemical safety rules..."
    )
    val safetyAlertEscalated get() = t(
        "భద్రతా హెచ్చరిక • అధికారికి పంపబడింది",
        "सुरक्षा चेतावनी • अधिकारी को भेजा गया",
        "Safety Alert • Escalated to Officer"
    )
    fun ticketNumber(value: String): String = t(
        "టికెట్ సంఖ్య: $value",
        "टिकट संख्या: $value",
        "Ticket number: $value"
    )
    fun reason(value: String): String = t(
        "కారణం: $value",
        "कारण: $value",
        "Reason: $value"
    )
    val verifiedInfo get() = t("ధృవీకరించబడిన సమాచారం", "सत्यापित जानकारी", "Verified Information")
    val voiceServerError get() = t(
        "సర్వర్ కనెక్ట్ కాలేదు. ఆఫ్‌లైన్ మోడ్ శోధించబడింది.",
        "सर्वर से कनेक्ट नहीं हो सका। ऑफ़लाइन मोड खोजा गया।",
        "Could not connect to server. Searched offline mode."
    )

    // ------------------------------------------------------------- disease
    val diseaseTitle get() = t(
        "పంట తెగులు నిర్ధారణ (Crop Vision AI)",
        "फसल रोग निदान (Crop Vision AI)",
        "Crop Disease Diagnosis (Crop Vision AI)"
    )
    val cameraPermissionRequired get() = t(
        "కెమెరా అనుమతి అవసరం",
        "कैमरा अनुमति आवश्यक है",
        "Camera Permission Required"
    )
    val grantPermission get() = t("అనుమతి ఇవ్వండి", "अनुमति दें", "Grant Permission")
    val alignInsideFrame get() = t(
        "తెగులు ఉన్న భాగాన్ని ఫ్రేమ్‌లో ఉంచండి",
        "रोगग्रस्त भाग को फ्रेम में रखें",
        "Align the affected part inside the frame"
    )
    val cropLabel get() = t("పంట:", "फसल:", "Crop:")
    val plantPartLabel get() = t("బాధిత భాగం:", "प्रभावित भाग:", "Plant Part:")
    val symptomsHint get() = t(
        "లక్షణాలు లేదా సందేహాలు రాయండి (ఉదా: ఆకులపై మచ్చలు, పురుగులు)",
        "लक्षण या शंकाएं लिखें (जैसे: पत्तियों पर धब्बे, कीड़े)",
        "Describe symptoms or concerns (e.g. spots on leaves, insects)"
    )
    val retake get() = t("మరలా తీయండి", "फिर से लें", "Retake")
    val analyzing get() = t("విశ్లేషిస్తున్నాము...", "विश्लेषण कर रहे हैं...", "Analyzing...")
    val diagnose get() = t("వ్యాధి నిర్ధారణ చేయండి", "रोग का निदान करें", "Diagnose Disease")
    val gallery get() = t("గ్యాలరీ", "गैलरी", "Gallery")
    val demoLeaf get() = t("డెమో ఆకు", "डेमो पत्ती", "Demo Leaf")
    val privacyBadge get() = t(
        "EXIF తొలగించబడింది • గోప్యత కాపాడబడింది",
        "EXIF हटाया गया • गोपनीयता सुरक्षित",
        "EXIF stripped • Privacy preserved"
    )
    val offlineSyncProtection get() = t(
        "ఆఫ్‌లైన్ ఆటో-సింక్ రక్షణ",
        "ऑफ़लाइन ऑटो-सिंक सुरक्षा",
        "Offline auto-sync protection"
    )
    val confirmedResult get() = t("నిర్ధారించిన ఫలితం", "पुष्ट परिणाम", "Confirmed Result")
    fun matchLabel(percent: Int): String = t(
        "$percent% సరిపోలిక",
        "$percent% मेल",
        "$percent% Match"
    )
    val cropProblem get() = t("పంట సమస్య", "फसल समस्या", "Crop problem")
    val suspectedDisease get() = t("అనుమానిత తెగులు", "संदिग्ध रोग", "Suspected disease")
    val icarRecommendation get() = t(
        "ICAR / CIBRC సిఫార్సు చేసిన నివారణ:",
        "ICAR / CIBRC अनुशंसित उपचार:",
        "ICAR / CIBRC recommended treatment:"
    )
    fun citation(value: String): String = t(
        "ఆధారం: $value",
        "स्रोत: $value",
        "Source: $value"
    )
    val syncSaved get() = t(
        "నెట్‌వర్క్ వచ్చినప్పుడు స్వయంచాలకంగా సమర్పించబడుతుంది",
        "नेटवर्क आने पर स्वचालित रूप से भेजा जाएगा",
        "Saved for automatic sync when network is restored"
    )
    fun syncWorker(ticket: String): String = t(
        "నెట్‌వర్క్ రాగానే వర్కర్‌తో సమర్పించబడుతుంది. టికెట్: $ticket",
        "नेटवर्क आते ही वर्कर द्वारा भेजा जाएगा। टिकट: $ticket",
        "Will be submitted by the background worker when the network returns. Ticket: $ticket"
    )
    val escalating get() = t(
        "అధికారికి పంపుతున్నాము...",
        "अधिकारी को भेज रहे हैं...",
        "Escalating to officer..."
    )
    val escalateToMao get() = t(
        "వ్యవసాయ అధికారి పరిశీలన కోరండి",
        "कृषि अधिकारी से जांच कराएं",
        "Escalate to MAO"
    )
    val qualityTooDark get() = t(
        "హెచ్చరిక: ఫోటో చాలా చీకటిగా ఉంది. ఫ్లాష్ ఆన్ చేసి మళ్లీ తీయండి",
        "चेतावनी: फोटो बहुत अंधेरी है। फ्लैश चालू कर फिर से लें",
        "Warning: Photo is too dark. Turn on flash and retake"
    )
    val qualityOverexposed get() = t(
        "హెచ్చరిక: కాంతి అతిగా ఉంది. నీడలో స్పష్టంగా తీయండి",
        "चेतावनी: रोशनी बहुत अधिक है। छाया में स्पष्ट फोटो लें",
        "Warning: Overexposed. Retake in shade for clarity"
    )
    fun officerEscalationRequest(crop: String, part: String, notes: String): String = t(
        "రైతు అభ్యర్థన: పంట: $crop, భాగం: $part, లక్షణాలు: $notes",
        "किसान अनुरोध: फसल: $crop, भाग: $part, लक्षण: $notes",
        "Farmer request: crop: $crop, part: $part, symptoms: $notes"
    )
    val officerEscalationReason get() = t(
        "రైతు ప్రత్యక్ష వ్యవసాయ అధికారి (MAO) పరిశీలన కోరారు",
        "किसान ने कृषि अधिकारी (MAO) से सीधी जांच का अनुरोध किया",
        "Farmer requested direct inspection by the Mandal Agricultural Officer (MAO)"
    )
    fun escalationSent(ticket: String): String = t(
        "టికెట్ #$ticket వ్యవసాయ అధికారికి పంపబడింది! త్వరలో సంప్రదిస్తారు.",
        "टिकट #$ticket कृषि अधिकारी को भेजा गया! जल्द संपर्क करेंगे।",
        "Ticket #$ticket sent to the agricultural officer! They will contact you soon."
    )
    val escalationSendFailed get() = t(
        "టికెట్ పంపడం విఫలమైంది. దయచేసి నెట్‌వర్క్ సరిచూడండి.",
        "टिकट भेजना विफल रहा। कृपया नेटवर्क जांचें।",
        "Failed to send the ticket. Please check your network."
    )

    // ------------------------------------------------------------- offline
    val offlineLibraryTitle get() = t(
        "ఆఫ్‌లైన్ వ్యవసాయ సమాచారం",
        "ऑफ़लाइन कृषि जानकारी",
        "Offline Agricultural Library"
    )
    val offlineLibrarySubtitle get() = t(
        "ఇంటర్నెట్ లేనప్పుడు కూడా పని చేస్తుంది (Room SQLite కాష్)",
        "इंटरनेट के बिना भी काम करता है (Room SQLite कैश)",
        "Works even without internet (Room SQLite cache)"
    )
    val preinstalledModel get() = t(
        "ముందే ఇన్‌స్టాల్ చేసిన ఆఫ్‌లైన్ AI మోడల్",
        "पहले से इंस्टॉल ऑफ़लाइन AI मॉडल",
        "Pre-installed Offline AI Model"
    )
    val preinstalledBadge get() = t("ముందే ఇన్‌స్టాల్", "पहले से इंस्टॉल", "PRE-INSTALLED")
    fun modelLabel(name: String, parameters: String): String = t(
        "మోడల్: $name ($parameters)",
        "मॉडल: $name ($parameters)",
        "Model: $name ($parameters)"
    )
    fun architectureLabel(architecture: String, quantization: String): String = t(
        "ఆర్కిటెక్చర్: $architecture • $quantization",
        "आर्किटेक्चर: $architecture • $quantization",
        "Architecture: $architecture • $quantization"
    )
    fun hardwareStatus(ramGb: Int, freeRamMb: Long): String = t(
        "హార్డ్‌వేర్ స్థితి: ${ramGb}GB RAM (ఉచిత మెమరీ: ${freeRamMb}MB)",
        "हार्डवेयर स्थिति: ${ramGb}GB RAM (मुक्त मेमोरी: ${freeRamMb}MB)",
        "Hardware status: ${ramGb}GB RAM (free memory: ${freeRamMb}MB)"
    )
    val benchmarkRunning get() = t("రన్ అవుతోంది...", "चल रहा है...", "Running...")
    val benchmarkButton get() = t(
        "ఆఫ్‌లైన్ AI మోడల్ బెంచ్‌మార్క్ రన్ చేయండి",
        "ऑफ़लाइन AI मॉडल बेंचमार्क चलाएं",
        "Run Offline AI Model Benchmark"
    )
    fun storedPackages(count: Int): String = t(
        "స్థానిక ICAR నిల్వ చేయబడిన ప్యాకేజీలు ($count):",
        "स्थानीय ICAR संग्रहित पैकेज ($count):",
        "Stored local ICAR packages ($count):"
    )
    val offlineAccess get() = t("100% ఆఫ్‌లైన్ యాక్సెస్", "100% ऑफ़लाइन एक्सेस", "100% Offline Access")
    val loadingCropInfo get() = t(
        "ఎంచుకున్న పంటకు సమాచారం లోడ్ అవుతోంది...",
        "चुनी गई फसल की जानकारी लोड हो रही है...",
        "Loading information for the selected crop..."
    )
    val icarVerifiedBadge get() = t("ICAR ధృవీకరించబడింది", "ICAR सत्यापित", "ICAR Verified")
    val benchmarkDeviceRam get() = t("పరికర RAM", "डिवाइस RAM", "Device RAM")
    val benchmarkModelEvaluated get() = t("మూల్యాంకన మోడల్", "मूल्यांकित मॉडल", "Model evaluated")
    val benchmarkFirstToken get() = t("మొదటి టోకెన్ సమయం", "पहले टोकन का समय", "Time to first token")
    val benchmarkTotalInference get() = t("మొత్తం ఇన్ఫరెన్స్", "कुल इन्फरेंस", "Total inference")
    val benchmarkVoiceLatency get() = t("వాయిస్-టు-వాయిస్ ఆలస్యం", "वॉयस-टू-वॉयस लेटेंसी", "Voice-to-voice latency")
    val benchmarkFallback get() = t("ఫాల్‌బ్యాక్ ప్రారంభమైంది", "फ़ॉलबैक सक्रिय", "Fallback engaged")
    val benchmarkGroundedness get() = t("గ్రౌండెడ్‌నెస్ విశ్వసనీయత", "ग्राउंडेडनेस विश्वसनीयता", "Groundedness fidelity")
    val benchmarkFallbackYes get() = t(
        "అవును (సురక్షిత Room SQLite నియమాలు)",
        "हाँ (सुरक्षित Room SQLite नियम)",
        "YES (Safe Room SQLite Rules)"
    )
    val benchmarkFallbackNo get() = t(
        "కాదు (స్థానిక SLM అమలైంది)",
        "नहीं (स्थानीय SLM चला)",
        "NO (Local SLM Executed)"
    )
    val benchmarkTarget get() = t("లక్ష్యం", "लक्ष्य", "Target")

    // ------------------------------------------------------------- tickets
    val ticketsTitle get() = t(
        "విస్తరణ అధికారి టికెట్లు",
        "विस्तार अधिकारी टिकट",
        "Extension Officer Tickets"
    )
    val ticketsSubtitle get() = t(
        "మీరు అడిగిన ప్రశ్నలకు అధికారుల పరిష్కారాలు",
        "आपके प्रश्नों के अधिकारी समाधान",
        "Officer resolutions for your questions"
    )
    val noTickets get() = t(
        "ప్రస్తుతానికి ఏ టికెట్లు లేవు",
        "अभी कोई टिकट नहीं है",
        "No tickets at the moment"
    )
    val noTicketsHint get() = t(
        "వాయిస్ ప్రశ్న అడిగినప్పుడు అవసరమైతే అధికారికి పంపబడుతుంది.",
        "वॉयस प्रश्न पूछने पर जरूरत पड़ने पर अधिकारी को भेजा जाएगा।",
        "A voice question is sent to an officer when escalation is needed."
    )
    val ticketResolved get() = t("పరిష్కరించబడింది", "समाधान हो गया", "Resolved")
    val ticketReviewing get() = t("పరిశీలనలో ఉంది", "समीक्षा में है", "Reviewing")
    fun yourQuestion(question: String): String = t(
        "మీ ప్రశ్న: \"$question\"",
        "आपका प्रश्न: \"$question\"",
        "Your question: \"$question\""
    )
    val officerPrescription get() = t(
        "అధికారి సలహా:",
        "अधिकारी की सलाह:",
        "Officer prescription:"
    )
}

fun appStrings(language: String): AppStrings = AppStrings(
    when (language) {
        "te", "hi", "en" -> language
        else -> "te"
    }
)
