package com.bolkisan.farmer.data.local

import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.bolkisan.farmer.data.local.entities.OfflineKnowledgeEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Pre-populates the Room SQLite Database with authentic ICAR offline packages of practice
 * for primary Indian crops (Cotton, Rice, Chilli, Tomato, Wheat, Maize) across
 * English, Telugu, and Hindi languages.
 */
object KnowledgePrepopulator {

    fun getDatabaseCallback(databaseProvider: () -> AppDatabase): RoomDatabase.Callback {
        return object : RoomDatabase.Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                CoroutineScope(Dispatchers.IO).launch {
                    val appDb = databaseProvider()
                    seedDatabase(appDb.offlineKnowledgeDao())
                }
            }
        }
    }

    suspend fun seedDatabase(dao: OfflineKnowledgeDao) {
        val currentCount = dao.getKnowledgeCount()
        if (currentCount < 10) {
            val allPackages = getPrepopulatedKnowledge()
            dao.insertKnowledge(allPackages)
        }
    }

    fun getPrepopulatedKnowledge(): List<OfflineKnowledgeEntity> {
        val list = mutableListOf<OfflineKnowledgeEntity>()

        // =========================================================================
        // 1. COTTON
        // =========================================================================
        // Topic A: Pink Bollworm ETL & Non-Chemical Management
        list.add(
            OfflineKnowledgeEntity(
                id = "icar-cot-pbw-te",
                collectionId = "KB04",
                crop = "Cotton",
                category = "pest_management",
                title = "ప్రత్తి: గులాబీ రంగు పురుగు ETL మరియు రసాయన రహిత నివారణ",
                content = "ఆర్థిక నష్ట పరిమితి (ETL): వరుసగా 3 రాత్రులు 8 రెక్కల పురుగులు/బుట్ట/రాత్రి లేదా 10% దెబ్బతిన్న పచ్చికాయలు (ముద్ద పువ్వులు/రోసెట్ ఫ్లవర్స్). రసాయన రహిత నివారణ: 1) ఎకరాకు 5 లింగాకర్షక బుట్టలు (గోసిప్లూర్) సర్వేక్షణకు, 18-20 సామూహిక ట్రాపింగ్ కోసం అమర్చాలి. 2) విత్తిన 45 రోజుల నుండి ట్రైకోగ్రామా బాక్ట్రే గుడ్ల పరాన్నజీవిని ఎకరాకు 60,000 చొప్పున వారం వ్యవధిలో 3 సార్లు వదలాలి. 3) మొగ్గ దశలో 5% వేప గింజల కషాయం (NSKE) లేదా వేప నూనె 5 మి.లీ/లీటరు పిచికారీ చేయాలి. 4) మోడు ప్రత్తి సాగు నివారించి, పంట వ్యర్థాలను కాల్చకుండా రోటవేటర్ తో భూమిలో కలపాలి.",
                sourceCitation = "ICAR-CICR Nagpur & CIBRC Pink Bollworm Management Advisory 2024",
                language = "te",
                lastVerifiedDate = "2026-11-08"
            )
        )
        list.add(
            OfflineKnowledgeEntity(
                id = "icar-cot-pbw-en",
                collectionId = "KB04",
                crop = "Cotton",
                category = "pest_management",
                title = "Cotton: Pink Bollworm ETL & Non-Chemical Management",
                content = "Economic Threshold Level (ETL): 8 moths/trap/night for 3 consecutive nights OR 10% damaged green bolls (rosette flower symptom). Non-Chemical Management: 1) Install pheromone traps with Gossyplure septa @ 5/acre for monitoring and 18-20/acre for mass trapping. 2) Release egg parasitoid Trichogramma bactrae @ 60,000/acre at weekly intervals 3 times starting 45 DAS. 3) Spray 5% Neem Seed Kernel Extract (NSKE) or Neem oil (1500 ppm @ 5 ml/L) at squaring stage. 4) Strictly avoid ratoon cotton and shred stalks post-harvest with rotavator.",
                sourceCitation = "ICAR-CICR Nagpur & CIBRC Pink Bollworm Management Advisory 2024",
                language = "en",
                lastVerifiedDate = "2026-11-08"
            )
        )
        list.add(
            OfflineKnowledgeEntity(
                id = "icar-cot-pbw-hi",
                collectionId = "KB04",
                crop = "Cotton",
                category = "pest_management",
                title = "कपास: गुलाबी सुंडी ईटीएल एवं गैर-रासायनिक प्रबंधन",
                content = "आर्थिक क्षति स्तर (ईटीएल): लगातार 3 रातों तक 8 कीट/ट्रैप/रात या 10% क्षतिग्रस्त हरे टिंडे (गुलाब के आकार के फूल)। गैर-रासायनिक प्रबंधन: 1) निगरानी हेतु 5 तथा मास ट्रैपिंग हेतु 18-20 फेरोमोन ट्रैप प्रति एकड़ लगाएं। 2) बुवाई के 45 दिनों बाद ट्राइकोग्रामा बैक्ट्रे 60,000 प्रति एकड़ की दर से 3 बार छोड़ें। 3) कली बनने की अवस्था में 5% नीम बीज अर्क (एनएसकेई) या नीम का तेल (5 मिली/लीटर) छिड़कें। 4) खूंटी (रतून) कपास न रखें तथा अवशेषों को रोटावेटर से मिट्टी में मिलाएं।",
                sourceCitation = "ICAR-CICR Nagpur & CIBRC Pink Bollworm Management Advisory 2024",
                language = "hi",
                lastVerifiedDate = "2026-11-08"
            )
        )

        // Topic B: Leaf Reddening Remedies (Magnesium Deficiency)
        list.add(
            OfflineKnowledgeEntity(
                id = "icar-cot-red-te",
                collectionId = "KB08",
                crop = "Cotton",
                category = "fertilizer",
                title = "ప్రత్తి: ఆకు ఎరుపు తెగులు (మెగ్నీషియం లోపం) నివారణోపాయాలు",
                content = "లక్షణాలు: కాయలు కాసే దశలో క్రింది ఆకులు ఈనెలు పచ్చగా ఉండి మధ్య భాగం ఎరుపు లేదా ఊదా రంగులోకి మారడం. కారణం మెగ్నీషియం లోపం, నేలలో అధిక తేమ లేదా చల్లని రాత్రులు. నివారణ: 1) ఎకరాకు 2 కిలోల మెగ్నీషియం సల్ఫేట్ (MgSO4 10గ్రా/లీ) + 2 కిలోల యూరియా (10గ్రా/లీ) లేదా 19:19:19 (10గ్రా/లీ) కలిపి 15 రోజుల వ్యవధిలో రెండుసార్లు ఆకులపై పిచికారీ చేయాలి. 2) చివరి దుక్కి లేదా మొక్క మొదళ్లలో ఎకరాకు 10-15 కిలోల మెగ్నీషియం సల్ఫేట్ వేయాలి. 3) నేలలో నీరు నిల్వ ఉండకుండా మురుగు నీటి కాలువలు తీయాలి.",
                sourceCitation = "ICAR-CICR Technical Bulletin on Cotton Nutrient Disorders 2024",
                language = "te",
                lastVerifiedDate = "2026-11-08"
            )
        )
        list.add(
            OfflineKnowledgeEntity(
                id = "icar-cot-red-en",
                collectionId = "KB08",
                crop = "Cotton",
                category = "fertilizer",
                title = "Cotton: Leaf Reddening (Magnesium Deficiency) Remedies",
                content = "Symptoms: Interveinal purplish-red discoloration on older leaves while veins stay green. Triggered by heavy boll load demand, temporary waterlogging, or cold nights. Management: 1) Foliar spray of Magnesium Sulphate (MgSO4) @ 10g/L + Urea @ 10g/L or soluble 19:19:19 @ 10g/L twice at 15-day intervals during flowering and boll bursting stages. 2) Basal or top-dressing of 10-15 kg MgSO4/acre. 3) Maintain proper drainage in black clay soils to prevent root hypoxia.",
                sourceCitation = "ICAR-CICR Technical Bulletin on Cotton Nutrient Disorders 2024",
                language = "en",
                lastVerifiedDate = "2026-11-08"
            )
        )
        list.add(
            OfflineKnowledgeEntity(
                id = "icar-cot-red-hi",
                collectionId = "KB08",
                crop = "Cotton",
                category = "fertilizer",
                title = "कपास: पत्तियों का लाल होना (मैग्नीशियम की कमी) एवं उपचार",
                content = "लक्षण: पुरानी पत्तियों पर नसों के बीच का भाग लाल या बैंगनी हो जाता है जबकि मुख्य नसें हरी रहती हैं। कारण: मैग्नीशियम की कमी व अत्यधिक नमी। उपचार: 1) फूल एवं टिंडे बनने की अवस्था में मैग्नीशियम सल्फेट (10 ग्राम/लीटर) + यूरिया (10 ग्राम/लीटर) या 19:19:19 (10 ग्राम/लीटर) का 15 दिन के अंतराल पर दो बार पर्णीय छिड़काव करें। 2) मिट्टी में 10-15 किग्रा मैग्नीशियम सल्फेट प्रति एकड़ डालें। 3) खेत से अतिरिक्त पानी निकालने की समुचित व्यवस्था रखें।",
                sourceCitation = "ICAR-CICR Technical Bulletin on Cotton Nutrient Disorders 2024",
                language = "hi",
                lastVerifiedDate = "2026-11-08"
            )
        )

        // =========================================================================
        // 2. RICE / PADDY
        // =========================================================================
        // Topic A: Brown Plant Hopper (BPH) Water Management
        list.add(
            OfflineKnowledgeEntity(
                id = "icar-rice-bph-te",
                collectionId = "KB04",
                crop = "Rice / Paddy",
                category = "pest_management",
                title = "వరి: సుడిదోమ (BPH) నీటి యాజమాన్యం మరియు అదుపు పద్ధతులు",
                content = "నీటి యాజమాన్యం (AWD పద్ధతి): సుడిదోమ ఆశించినట్లు గుర్తించగానే (మొదళ్ల వద్ద 5-10 దోమలు) పొలంలోని నీటిని 3-4 రోజుల పాటు పూర్తిగా తీసివేసి ఆరబెట్టాలి. మొదళ్ల వద్ద గాలి, వెలుతురు సోకడం వల్ల దోమల ఉధృతి 60-70% తగ్గుతుంది. సాగు పద్ధతులు: నాట్లు వేసేటప్పుడు ప్రతి 2 మీటర్లకు 20-30 సెం.మీ ఎడంగా 'బాటలు' (సూర్యరశ్మి కోసం) తీయాలి. సుడిదోమ సమయంలో యూరియా పిచికారీ/వాడకం తాత్కాలికంగా ఆపి, పొటాష్ ఎరువు వేయడం ద్వారా కాండం గట్టిపడుతుంది. మందుల పిచికారీ మొక్క మొదళ్ల తడిసేలా మాత్రమే చేయాలి.",
                sourceCitation = "ICAR-Indian Institute of Rice Research (IIRR) Hyderabad Advisory 2024",
                language = "te",
                lastVerifiedDate = "2026-11-08"
            )
        )
        list.add(
            OfflineKnowledgeEntity(
                id = "icar-rice-bph-en",
                collectionId = "KB04",
                crop = "Rice / Paddy",
                category = "pest_management",
                title = "Rice: Brown Plant Hopper (BPH) Water & Cultural Management",
                content = "Alternate Wetting and Drying (AWD): Drain standing water from the field completely for 3-4 days upon detecting BPH (5-10 hoppers/hill). Aeration and direct sunlight disrupt the humid microclimate at the stem base, slashing hopper nymph survival. Cultural practices: Form 20-30 cm alleyways ('battas') every 2 meters in east-west direction during transplanting. Immediately suspend split application of urea (nitrogen), which induces soft tissue succulence; apply muriate of potash to strengthen culms. Target spray nozzles at plant bases.",
                sourceCitation = "ICAR-Indian Institute of Rice Research (IIRR) Hyderabad Advisory 2024",
                language = "en",
                lastVerifiedDate = "2026-11-08"
            )
        )
        list.add(
            OfflineKnowledgeEntity(
                id = "icar-rice-bph-hi",
                collectionId = "KB04",
                crop = "Rice / Paddy",
                category = "pest_management",
                title = "धान: भूरा फुदका (बीपीएच) जल प्रबंधन एवं कृषि तकनीक",
                content = "वैकल्पिक जल निकासी (AWD विधि): बीपीएच (5-10 कीट प्रति पौधा) दिखते ही खेत का पानी 3-4 दिनों के लिए पूरी तरह निकाल कर सुखा दें। तनों के आधार पर हवा व धूप लगने से कीटों की संख्या में भारी गिरावट आती है। कृषि तकनीक: रोपाई के समय हर 2 मीटर पर 20-30 सेमी की गलियां (रास्ता) छोड़ें। यूरिया का उपयोग तुरंत रोकें तथा पोटाश डालें जिससे तना मजबूत हो। कीटनाशक छिड़काव तनों के आधार पर करें।",
                sourceCitation = "ICAR-Indian Institute of Rice Research (IIRR) Hyderabad Advisory 2024",
                language = "hi",
                lastVerifiedDate = "2026-11-08"
            )
        )

        // Topic B: Rice Blast Symptoms & Control
        list.add(
            OfflineKnowledgeEntity(
                id = "icar-rice-blast-te",
                collectionId = "KB05",
                crop = "Rice / Paddy",
                category = "disease",
                title = "వరి: అగ్గితెగులు (ఆకు మరియు మెడవిరుపు) గుర్తింపు మరియు నివారణ",
                content = "లక్షణాలు: ఆకు అగ్గితెగులులో నూలు కండె ఆకారపు (కంటి ఆకారపు) మచ్చలు ఏర్పడి అంచులు గోధుమ రంగులో, నడిమి భాగం బూడిద రంగులో ఉంటాయి. మెడవిరుపు తెగులులో వెన్ను కింద మెడ నల్లబడి విరిగిపోయి తాలు గింజలు (తెల్ల కంకులు) ఏర్పడతాయి. అనుకూల వాతావరణం: రాత్రి వేళ చలి (19-23°C), 90% పైగా గాలిలో తేమ, మంచు పడటం. నివారణ: నత్రజని వాడకాన్ని తగ్గించాలి. ట్రైసైక్లజోల్ 75% WP (0.6 గ్రా/లీ) లేదా ఐసోప్రోథియోలేన్ 40% EC (1.5 మి.లీ/లీ) పిచికారీ చేయాలి. వెన్ను ఈనే సమయంలో రెండోసారి తప్పక పిచికారీ చేయాలి.",
                sourceCitation = "ICAR-IIRR Rice Disease Compendium 2024",
                language = "te",
                lastVerifiedDate = "2026-11-08"
            )
        )
        list.add(
            OfflineKnowledgeEntity(
                id = "icar-rice-blast-en",
                collectionId = "KB05",
                crop = "Rice / Paddy",
                category = "disease",
                title = "Rice Blast: Leaf & Neck Blast Identification & Care",
                content = "Symptoms: Leaf blast exhibits spindle-shaped or diamond-shaped lesions with dark brown margins and ashy grey centers. Neck blast causes blackening and girdling of the neck node just below the panicle, resulting in upright chaffy empty earheads. Favorable weather: >90% humidity, night temp 19-23°C, overcast skies and prolonged dew. Management: Avoid excess nitrogen top-dressing. Spray Tricyclazole 75% WP @ 0.6g/L or Isoprothiolane 40% EC @ 1.5ml/L at disease onset; repeat once at early heading stage.",
                sourceCitation = "ICAR-IIRR Rice Disease Compendium 2024",
                language = "en",
                lastVerifiedDate = "2026-11-08"
            )
        )
        list.add(
            OfflineKnowledgeEntity(
                id = "icar-rice-blast-hi",
                collectionId = "KB05",
                crop = "Rice / Paddy",
                category = "disease",
                title = "धान: झुलसा रोग (लीफ एवं नेक ब्लास्ट) पहचान व रोकथाम",
                content = "लक्षण: पत्तियों पर नाव या आंख के आकार के धब्बे बनते हैं जिनके किनारे कत्थई और केंद्र राख जैसे सफेद-भूरे होते हैं। नेक ब्लास्ट में बाली की गर्दन काली होकर मुड़ जाती है और दाने नहीं भरते (सफेद बाली)। अनुकूल मौसम: 90% से अधिक आर्द्रता, 19-23°C तापमान व रात में ओस। रोकथाम: यूरिया का अत्यधिक छिड़काव रोकें। ट्राईसाइक्लाजोल 75% डब्ल्यूपी (0.6 ग्राम/लीटर) या आइसोप्रोपियोलेन 40% ईसी (1.5 मिली/लीटर) का छिड़काव लक्षण दिखते ही करें।",
                sourceCitation = "ICAR-IIRR Rice Disease Compendium 2024",
                language = "hi",
                lastVerifiedDate = "2026-11-08"
            )
        )

        // =========================================================================
        // 3. CHILLI
        // =========================================================================
        // Topic A: Thrips vs Mites Leaf Curl Identification
        list.add(
            OfflineKnowledgeEntity(
                id = "icar-chl-curl-te",
                collectionId = "KB04",
                crop = "Chilli",
                category = "disease",
                title = "మిరప: తామర పురుగులు (త్రిప్స్) vs నల్లి (మైట్స్) ఆకు ముడుత వ్యత్యాసం",
                content = "ఆకు ముడుత గుర్తింపు తేడాలు: 1) తామర పురుగులు (త్రిప్స్): ఆకులు పైకి దోనె ఆకారంలో (పై ముడుత) ముడుచుకుంటాయి. ఆకుల అడుగున గోధుమ రంగు గీతలు, ఈనెలు ఎండిపోవడం గమనించవచ్చు. 2) పచ్చ నల్లి (మైట్స్): ఆకులు కిందికి బొటనవ్రేలు లేదా గొడుగు ఆకారంలో (కింది ముడుత) ముడుచుకుంటాయి. ఆకులు రబ్బరులా మందంగా మారి తోకలా సాగుతాయి ('ఎలుక తోక' లక్షణం). నివారణ: త్రిప్స్ కోసం ఎకరాకు 15 నీలి జిగురు అట్టలు; నల్లి కోసం పసుపు జిగురు అట్టలు మరియు డయాఫెంథియురాన్ 50% WP (1.2 గ్రా/లీ) పిచికారీ చేయాలి.",
                sourceCitation = "ICAR-Indian Institute of Horticultural Research (IIHR) Bengaluru Advisory 2024",
                language = "te",
                lastVerifiedDate = "2026-11-08"
            )
        )
        list.add(
            OfflineKnowledgeEntity(
                id = "icar-chl-curl-en",
                collectionId = "KB04",
                crop = "Chilli",
                category = "disease",
                title = "Chilli: Thrips vs Mites Leaf Curl Differential Identification",
                content = "Differential Diagnosis: 1) Thrips Leaf Curl: Leaves curl UPWARDS (boat-shaped / cup-shaped). Lower surface shows bronzing/scratches, internodes shorten, and buds drop prematurely. 2) Yellow Mite Leaf Curl: Leaves curl DOWNWARDS (inverted cup / paw-shaped). Foliage becomes thickened, leathery, dark green, with severe petiole elongation ('rat tail' symptom). Control: Install blue sticky traps @ 15/acre for thrips, yellow sticky traps for whiteflies/mites. Spray Diafenthiuron 50% WP @ 1.2g/L or Fenpyroximate 5% EC @ 1ml/L for mites.",
                sourceCitation = "ICAR-Indian Institute of Horticultural Research (IIHR) Bengaluru Advisory 2024",
                language = "en",
                lastVerifiedDate = "2026-11-08"
            )
        )
        list.add(
            OfflineKnowledgeEntity(
                id = "icar-chl-curl-hi",
                collectionId = "KB04",
                crop = "Chilli",
                category = "disease",
                title = "मिर्च: थ्रिप्स बनाम माइट्स (मकड़ी) पर्ण कुंचन पहचान",
                content = "पर्ण कुंचन पहचान में अंतर: 1) थ्रिप्स: पत्तियां ऊपर की ओर नाव के आकार में मुड़ती हैं (ऊपरी घुमाव)। निचली सतह पर खुरचने के भूरे निशान बनते हैं। 2) माइट्स (मकड़ी): पत्तियां नीचे की ओर उल्टे कप के आकार में मुड़ती हैं (निचला घुमाव), पत्तियां मोटी और चमड़े जैसी हो जाती हैं। रोकथाम: थ्रिप्स के लिए नीले चिपचिपे ट्रैप (15/एकड़), माइट्स के लिए पीले ट्रैप और डायफेंथियूरॉन 50% डब्ल्यूपी (1.2 ग्राम/लीटर) या फेनपायरोक्सिमेट (1 मिली/लीटर) का छिड़काव करें।",
                sourceCitation = "ICAR-Indian Institute of Horticultural Research (IIHR) Bengaluru Advisory 2024",
                language = "hi",
                lastVerifiedDate = "2026-11-08"
            )
        )

        // Topic B: Neem Seed Kernel Extract (NSKE 5%)
        list.add(
            OfflineKnowledgeEntity(
                id = "icar-chl-nske-te",
                collectionId = "KB08",
                crop = "Chilli",
                category = "pest_management",
                title = "మిరప: వేప గింజల కషాయం (NSKE 5%) తయారీ మరియు పిచికారీ విధానం",
                content = "5% వేప గింజల కషాయం తయారీ విధానం (ఎకరాకు): 1) 10 కిలోల బాగా ఎండిన నాణ్యమైన వేప గింజల పప్పును రోట్లో మెత్తగా దంచాలి. 2) ఈ పొడిని పల్చని నూలు గుడ్డలో మూటకట్టి 20 లీటర్ల నీటిలో రాత్రంతా నానబెట్టాలి. 3) ఉదయాన్నే మూటను పిండుతూ తెల్లటి పాల ద్రవం పూర్తిగా నీటిలోకి వచ్చేలా చేయాలి. 4) ఈ ద్రావణానికి 100 గ్రాముల ఖాదీ సబ్బు పొడిని నీటిలో కరిగించి జిగురుగా కలపాలి. 5) దీనిని 200 లీటర్ల నీటిలో కలిపి సాయంత్రం వేళల్లో పిచికారీ చేయాలి. ఇది తామర పురుగులు, పేనుబంక, నల్లి మరియు కాయ తొలుచు పురుగులను గుడ్లు పెట్టకుండా సమర్థవంతంగా అరికడుతుంది.",
                sourceCitation = "ICAR-National Research Centre for Integrated Pest Management (NCIPM) 2024",
                language = "te",
                lastVerifiedDate = "2026-11-08"
            )
        )
        list.add(
            OfflineKnowledgeEntity(
                id = "icar-chl-nske-en",
                collectionId = "KB08",
                crop = "Chilli",
                category = "pest_management",
                title = "Chilli: Neem Seed Kernel Extract (NSKE 5%) Preparation & Usage",
                content = "Preparation of 5% NSKE (per acre): 1) Take 10 kg dried neem seed kernels and crush into a coarse powder. 2) Bundle powder in a muslin cloth and soak in 20 liters of water overnight. 3) Squeeze the pouch repeatedly in the morning until water turns milky white, then filter through cloth. 4) Dissolve 100g of khadi soap flakes in 1L water and mix as emulsifier. 5) Bring total volume to 200 liters with water and spray during calm evening hours. Acts as a potent anti-feedant, oviposition deterrent, and repellent against thrips, aphids, mites, and fruit borers.",
                sourceCitation = "ICAR-National Research Centre for Integrated Pest Management (NCIPM) 2024",
                language = "en",
                lastVerifiedDate = "2026-11-08"
            )
        )
        list.add(
            OfflineKnowledgeEntity(
                id = "icar-chl-nske-hi",
                collectionId = "KB08",
                crop = "Chilli",
                category = "pest_management",
                title = "मिर्च: नीम बीज गिरी अर्क (एनएसकेई 5%) निर्माण एवं उपयोग",
                content = "एनएसकेई 5% बनाने की विधि (प्रति एकड़): 1) 10 किग्रा अच्छी सूखी नीम की निंबोली गिरी कूट लें। 2) मलमल के कपड़े की पोटली बनाकर 20 लीटर पानी में रात भर भिगोएं। 3) सुबह पोटली को निचोड़कर सफेद अर्क निकालें और छान लें। 4) 100 ग्राम साबुन का घोल चिपकने वाले पदार्थ के रूप में मिलाएं। 5) पानी मिलाकर कुल 200 लीटर घोल तैयार करें। शाम के समय छिड़काव करें। यह रस चूसक कीटों और सुंडी के अंडों को रोकने में अत्यंत प्रभावी व पर्यावरण अनुकूल उपाय है।",
                sourceCitation = "ICAR-National Research Centre for Integrated Pest Management (NCIPM) 2024",
                language = "hi",
                lastVerifiedDate = "2026-11-08"
            )
        )

        // =========================================================================
        // 4. TOMATO
        // =========================================================================
        // Topic A: Early Blight vs Late Blight Differentiation
        list.add(
            OfflineKnowledgeEntity(
                id = "icar-tom-blight-te",
                collectionId = "KB05",
                crop = "Tomato",
                category = "disease",
                title = "టమోటా: ముందస్తు మచ్చ తెగులు vs లేట్ బ్లైట్ (మొగి తెగులు) గుర్తింపు",
                content = "గుర్తింపు తేడాలు: 1) ముందస్తు ఆకుమచ్చ తెగులు (ఎర్లీ బ్లైట్): మొదట క్రింది ముసలి ఆకులపై ఏర్పడుతుంది. మచ్చలపై ఉంగరాల వంటి ఏకకేంద్రక వలయాలు (టార్గెట్ బోర్డు లక్షణం) కనిపిస్తాయి. వెచ్చని వాతావరణం (24-30°C) లో వస్తుంది. 2) మొగి తెగులు (లేట్ బ్లైట్): చల్లని (15-22°C), అధిక తేమతో కూడిన వర్షపు వాతావరణంలో లేత ఆకులపై నీటి మచ్చలు నల్లగా మారి వేగంగా విస్తరిస్తాయి. ఆకు అడుగున తెల్లటి బూజు వస్తుంది; మొక్కలు రాత్రికి రాత్రే కుళ్ళిపోయి వాసన వస్తాయి. నివారణ: ఎర్లీ బ్లైట్ కు మ్యాంకోజెబ్ (2గ్రా/లీ); లేట్ బ్లైట్ కు సైమోక్సానిల్ + మ్యాంకోజెబ్ (2.5గ్రా/లీ) లేదా మెటలాక్సిల్ + మ్యాంకోజెబ్ పిచికారీ చేయాలి.",
                sourceCitation = "ICAR-Indian Agricultural Research Institute (IARI) Tomato Protection Guide 2024",
                language = "te",
                lastVerifiedDate = "2026-11-08"
            )
        )
        list.add(
            OfflineKnowledgeEntity(
                id = "icar-tom-blight-en",
                collectionId = "KB05",
                crop = "Tomato",
                category = "disease",
                title = "Tomato: Early Blight vs Late Blight Differentiation & Care",
                content = "Differential Diagnosis: 1) Early Blight (Alternaria solani): Appears on older lower leaves first. Features concentric dark brown rings giving a characteristic 'target board' pattern surrounded by yellow halos. Favored by warm (24-30°C) temperatures. 2) Late Blight (Phytophthora infestans): Extremely destructive in cool (15-22°C), high humidity (>90%) rainy spells. Water-soaked dark brown greasy blotches expand rapidly on young leaves, with delicate white downy mildew on leaf undersides. Stems blacken and foliage collapses rapidly. Control: Spray Mancozeb 75% WP @ 2g/L for early blight; Cymoxanil 8% + Mancozeb 64% WP @ 2.5g/L or Metalaxyl-M 4% + Mancozeb 64% @ 2g/L for late blight.",
                sourceCitation = "ICAR-Indian Agricultural Research Institute (IARI) Tomato Protection Guide 2024",
                language = "en",
                lastVerifiedDate = "2026-11-08"
            )
        )
        list.add(
            OfflineKnowledgeEntity(
                id = "icar-tom-blight-hi",
                collectionId = "KB05",
                crop = "Tomato",
                category = "disease",
                title = "टमाटर: अगेती झुलसा बनाम पछेती झुलसा पहचान एवं प्रबंधन",
                content = "पहचान में अंतर: 1) अगेती झुलसा: पुरानी निचली पत्तियों पर शुरू होता है। गोल छल्लेदार 'टारगेट बोर्ड' जैसे कत्थई धब्बे बनते हैं जिनके चारों ओर पीला घेरा होता है। 2) पछेती झुलसा: ठंडे व नम मौसम (15-22°C) में पत्तियों पर तेजी से पानीदार काले धब्बे बनते हैं और निचली सतह पर सफेद फफूंद दिखती है। पौधे सड़कर गिर जाते हैं। प्रबंधन: अगेती के लिए मैंकोजेब (2 ग्राम/लीटर), पछेती के लिए साइमोक्सानिल + मैंकोजेब (2.5 ग्राम/लीटर) या मेटालैक्सिल का छिड़काव करें।",
                sourceCitation = "ICAR-Indian Agricultural Research Institute (IARI) Tomato Protection Guide 2024",
                language = "hi",
                lastVerifiedDate = "2026-11-08"
            )
        )

        // Topic B: Staking & Trellising Advice
        list.add(
            OfflineKnowledgeEntity(
                id = "icar-tom-stake-te",
                collectionId = "KB12",
                crop = "Tomato",
                category = "cultivation",
                title = "టమోటా: కట్టెల మద్దతు (స్టేకింగ్) మరియు తీగ పందిరి పద్ధతులు",
                content = "స్టేకింగ్ ప్రాముఖ్యత: వెదురు బొంగులు, జీఐ తీగలు మరియు జనపనార తాడుతో టమోటా మొక్కలకు మద్దతు ఇవ్వడం. లాభాలు: 1) పండ్లు నేలను తాకకుండా ఉండటం వల్ల కాయకుళ్ళు మరియు కాయ తొలుచు పురుగుల నష్టం 40-50% తగ్గుతుంది. 2) మొక్కల మధ్య గాలి, సూర్యరశ్మి బాగా ప్రసరించి ఆకుమచ్చ మరియు మొగి తెగుళ్ల ఉధృతి గణనీయంగా తగ్గుతుంది. 3) మందుల పిచికారీ సమానంగా పడుతుంది, కోత సులువవుతుంది. పద్ధతి: నాటిన 25-30 రోజులకు వరుసలో ప్రతి 3 మీటర్లకు వెదురు కర్రలు నాటి, 30 సెం.మీ మరియు 90 సెం.మీ ఎత్తులో తీగలు లాగి మొక్కలను వదులుగా కట్టాలి.",
                sourceCitation = "ICAR-IIHR Vegetable Production Technologies 2024",
                language = "te",
                lastVerifiedDate = "2026-11-08"
            )
        )
        list.add(
            OfflineKnowledgeEntity(
                id = "icar-tom-stake-en",
                collectionId = "KB12",
                crop = "Tomato",
                category = "cultivation",
                title = "Tomato: Staking and Trellising Advice for Disease Suppression",
                content = "Benefits of Staking: Erect support with bamboo poles, galvanized iron (GI) wire, and jute twine keeps foliage and fruit off moist soil. 1) Slashes fruit rot (buckeye rot) and fruit borer damage by 40-50%. 2) Enhances airflow and sunlight penetration across the canopy, depressing microclimate humidity that triggers fungal blights. 3) Ensures uniform spray coverage and eases grading and picking. Method: Drive 2m bamboo stakes every 3 meters along the crop row, run two horizontal tension wires at 30cm and 90cm, and tie vines with loose figure-eight loops at 25-30 DAT.",
                sourceCitation = "ICAR-IIHR Vegetable Production Technologies 2024",
                language = "en",
                lastVerifiedDate = "2026-11-08"
            )
        )
        list.add(
            OfflineKnowledgeEntity(
                id = "icar-tom-stake-hi",
                collectionId = "KB12",
                crop = "Tomato",
                category = "cultivation",
                title = "टमाटर: स्टेकिंग (सहारा देना) एवं ट्रेलिसिंग विधि",
                content = "स्टेकिंग का महत्व: बांस के खंभे और तार से सहारा देने पर टमाटर के पौधे जमीन से ऊपर रहते हैं। लाभ: 1) फल सड़न रोग और फल छेदक कीट से 40-50% बचाव होता है। 2) हवा और धूप का बेहतर संचार होने से फंगल रोगों का प्रकोप घटता है। 3) दवा छिड़काव और फल तोड़ाई में आसानी रहती है। विधि: रोपाई के 25-30 दिन बाद 2 मीटर के बांस लगाकर 30 सेमी व 90 सेमी ऊंचाई पर तारों से पौधों को ढीला बांधें।",
                sourceCitation = "ICAR-IIHR Vegetable Production Technologies 2024",
                language = "hi",
                lastVerifiedDate = "2026-11-08"
            )
        )

        // =========================================================================
        // 5. WHEAT
        // =========================================================================
        // Topic A: Rust Identification (Stripe/Yellow, Leaf/Brown, Stem/Black)
        list.add(
            OfflineKnowledgeEntity(
                id = "icar-wht-rust-te",
                collectionId = "KB05",
                crop = "Wheat",
                category = "disease",
                title = "గోధుమ: కుంకుమ తెగులు (పసుపు, గోధుమ, నలుపు రస్ట్) గుర్తింపు",
                content = "గోధుమ కుంకుమ తెగులు రకాలు: 1) పసుపు రస్ట్ (స్ట్రైప్ రస్ట్): ఆకులపై పసుపు రంగు పుప్పొడి మచ్చలు ఈనెల వెంబడి గీతలు గీతలుగా వరుస క్రమంలో ఏర్పడతాయి. చేతితో తాకితే పసుపు పొడి అంటుకుంటుంది (చల్లని 10-15°C వాతావరణంలో వస్తుంది). 2) గోధుమ రస్ట్ (లీఫ్ రస్ట్): ఆకుపై గుండ్రటి నారింజ-గోధుమ రంగు మచ్చలు చెల్లాచెదురుగా ఏర్పడతాయి (20-25°C లో వస్తుంది). 3) నలుపు రస్ట్ (స్టెమ్ రస్ట్): కాండం మరియు ఆకు తొడుగులపై నల్లటి పొడవైన పొక్కులు వస్తాయి. నివారణ: ప్రారంభ దశలోనే ప్రొపికోనజోల్ 25% EC ఎకరాకు 200 మి.లీ (1 మి.లీ/లీటరు నీటికి) పిచికారీ చేయాలి.",
                sourceCitation = "ICAR-Indian Institute of Wheat and Barley Research (IIWBR) Karnal 2024",
                language = "te",
                lastVerifiedDate = "2026-11-08"
            )
        )
        list.add(
            OfflineKnowledgeEntity(
                id = "icar-wht-rust-en",
                collectionId = "KB05",
                crop = "Wheat",
                category = "disease",
                title = "Wheat: Rust Identification (Stripe/Yellow, Leaf/Brown, Stem/Black)",
                content = "Rust Differentiation: 1) Yellow/Stripe Rust (Puccinia striiformis): Lemon-yellow pustules arranged in linear stripes parallel to leaf veins; sheds powdery spores like turmeric powder on touch. Favored by cool temperatures (10-15°C). 2) Brown/Leaf Rust (Puccinia triticina): Orange-brown circular pustules scattered randomly across leaf blades without pattern. Favored by 20-25°C. 3) Black/Stem Rust (Puccinia graminis): Dark reddish-brown to black elongated pustules mainly on stems and leaf sheaths. Management: Spray Propiconazole 25% EC (Tilt) @ 1ml/L (200ml/acre in 200L water) immediately upon detecting the first pustule clusters.",
                sourceCitation = "ICAR-Indian Institute of Wheat and Barley Research (IIWBR) Karnal 2024",
                language = "en",
                lastVerifiedDate = "2026-11-08"
            )
        )
        list.add(
            OfflineKnowledgeEntity(
                id = "icar-wht-rust-hi",
                collectionId = "KB05",
                crop = "Wheat",
                category = "disease",
                title = "गेहूं: रतुआ (पीला, भूरा और काला रतुआ) पहचान एवं रोकथाम",
                content = "गेहूं रतुआ पहचान: 1) पीला रतुआ: पत्तियों की नसों के बीच पीले रंग की धारियों में फफोले बनते हैं जो छूने पर हल्दी जैसा पाउडर छोड़ते हैं (10-15°C तापमान)। 2) भूरा रतुआ: पत्तियों पर गोल नारंगी-भूरे धब्बे बिखरे होते हैं (20-25°C)। 3) काला रतुआ: तने और पर्णच्छद पर गहरे कत्थई-काले उभरे हुए फफोले बनते हैं। रोकथाम: प्रारंभिक लक्षण दिखते ही प्रोपिकोनाजोल 25% ईसी 200 मिली प्रति एकड़ (1 मिली/लीटर पानी) का तुरंत छिड़काव करें।",
                sourceCitation = "ICAR-Indian Institute of Wheat and Barley Research (IIWBR) Karnal 2024",
                language = "hi",
                lastVerifiedDate = "2026-11-08"
            )
        )

        // Topic B: Irrigation Timing at Crown Root Initiation (CRI)
        list.add(
            OfflineKnowledgeEntity(
                id = "icar-wht-cri-te",
                collectionId = "KB01",
                crop = "Wheat",
                category = "cultivation",
                title = "గోధుమ: కిరీటపు వేర్ల ప్రారంభ దశ (CRI) లో నీటి తడుల ప్రాముఖ్యత",
                content = "కిరీటపు వేర్ల దశ (CRI): గోధుమ విత్తిన 21 నుండి 25 రోజుల మధ్య ఈ దశ వస్తుంది. ఇది గోధుమ పంట జీవిత కాలంలో అత్యంత కీలకమైన నీటి తడి దశ. ఈ సమయంలోనే రెండవ వరుస కిరీటపు వేర్లు నేలలోకి దిగి పిలకలు తొడుగుతాయి. ఈ దశలో నీటి ఎద్దడి ఏర్పడితే పిలకల సంఖ్య 35-45% తగ్గి దిగుబడి దారుణంగా పడిపోతుంది. నియమం: మొదటి తడి తప్పనిసరిగా విత్తిన 21-25 రోజులకు ఇవ్వాలి. తదుపరి తడులు పిలక దశ (40-45 రోజులు), కణుపుల దశ (60-65 రోజులు), పూత దశ (80-85 రోజులు), పాలు పోసుకునే దశ (100-105 రోజులు) లో ఇవ్వాలి.",
                sourceCitation = "ICAR-IIWBR Technical Manual on Wheat Water Management 2024",
                language = "te",
                lastVerifiedDate = "2026-11-08"
            )
        )
        list.add(
            OfflineKnowledgeEntity(
                id = "icar-wht-cri-en",
                collectionId = "KB01",
                crop = "Wheat",
                category = "cultivation",
                title = "Wheat: Irrigation Timing at Crown Root Initiation (CRI) Stage",
                content = "Crown Root Initiation (CRI) Stage: Occurs precisely at 21 to 25 days after sowing (DAS). This is the single most sensitive moisture-critical stage in the wheat lifecycle. Secondary crown roots develop at this time, anchoring the plant and initiating tillering. If water stress occurs at CRI, tillering is permanently stunted by 35-45%, reducing effective ear-bearing spikes and grain yield drastically. Rule: First irrigation MUST be given at 21-25 DAS. Subsequent irrigations follow at Tillering (40-45 DAS), Jointing (60-65 DAS), Flowering (80-85 DAS), and Milk stage (100-105 DAS).",
                sourceCitation = "ICAR-IIWBR Technical Manual on Wheat Water Management 2024",
                language = "en",
                lastVerifiedDate = "2026-11-08"
            )
        )
        list.add(
            OfflineKnowledgeEntity(
                id = "icar-wht-cri-hi",
                collectionId = "KB01",
                crop = "Wheat",
                category = "cultivation",
                title = "गेहूं: ताज मूल दीक्षा (सीआरआई) अवस्था में सिंचाई का समय",
                content = "सीआरआई अवस्था (ताज मूल निकलना): बुवाई के 21 से 25 दिन बाद आती है। यह गेहूं की फसल का सबसे संवेदनशील समय है जब द्वितीयक जड़ें निकलती हैं और कल्ले फूटते हैं। इस समय पानी की कमी होने पर कल्ले बनने में 35-45% तक कमी आती है जिससे पैदावार घट जाती है। नियम: पहली सिंचाई बुवाई के 21-25 दिन पर हर हाल में दें। अगली सिंचाइयां कल्ले फूटते समय (40-45 दिन), गांठ बनते समय (60-65 दिन) और दुग्ध अवस्था में दें।",
                sourceCitation = "ICAR-IIWBR Technical Manual on Wheat Water Management 2024",
                language = "hi",
                lastVerifiedDate = "2026-11-08"
            )
        )

        // =========================================================================
        // 6. MAIZE
        // =========================================================================
        // Topic A: Fall Armyworm (FAW) Whorl Application
        list.add(
            OfflineKnowledgeEntity(
                id = "icar-mze-faw-te",
                collectionId = "KB04",
                crop = "Maize",
                category = "pest_management",
                title = "మొక్కజొన్న: కత్తెర పురుగు (FAW) సుడులలో మందుల వేత విధానం",
                content = "కత్తెర పురుగు (ఫాల్ ఆర్మీవార్మ్) నివారణ: లద్దె పురుగులు మొక్క సుడులలో దాగి ఉండి ఆకులను కొరికి రంపపు పొట్టు వంటి మలాన్ని విసర్జిస్తాయి. సుడులలో మందుల వేత పద్ధతి: 1) ఇసుక మరియు బూడిద (9:1 నిష్పత్తి) సుడిలో వేయడం వల్ల చిన్న పురుగులు ఊపిరాడక చనిపోతాయి. 2) జీవ నియంత్రణ: మెటారైజియం లేదా బాసిల్లస్ తురింజియెన్సిస్ (Bt) 2 గ్రా/లీ సుడిలో పడేలా పిచికారీ చేయాలి. 3) రసాయన మందు: క్లోరాంట్రానిలిప్రోల్ 18.5% SC (0.4 మి.లీ/లీ) లేదా ఎమామెక్టిన్ బెంజోయేట్ 5% SG (0.4 గ్రా/లీ) స్ప్రేయర్ నాజిల్ ను నేరుగా మొక్క సుడిలో ఉంచి పిచికారీ చేయాలి. వేచి ఉండే వ్యవధి 14 రోజులు.",
                sourceCitation = "ICAR-Indian Institute of Maize Research (IIMR) Ludhiana Guidelines 2024",
                language = "te",
                lastVerifiedDate = "2026-11-08"
            )
        )
        list.add(
            OfflineKnowledgeEntity(
                id = "icar-mze-faw-en",
                collectionId = "KB04",
                crop = "Maize",
                category = "pest_management",
                title = "Maize: Fall Armyworm (FAW) Whorl Application & Bio-Control",
                content = "Fall Armyworm (Spodoptera frugiperda) damage: Larvae hide deep inside the central leaf whorl, producing shot-holes, 'window pane' skeletonizing, and dense sawdust-like frass pellets. Whorl Application Technique: 1) Sand-Ash or Sand-Lime mixture (9:1 ratio) applied into whorls suffocates early instars. 2) Bio-control: Spray Metarhizium rileyi or Bacillus thuringiensis (Bt) kurstaki @ 2g/L directly into whorls. 3) Targeted chemical whorl spray: Chlorantraniliprole 18.5% SC @ 0.4 ml/L or Emamectin Benzoate 5% SG @ 0.4 g/L using a single knapsack nozzle directed into whorls. Waiting period: 14 days.",
                sourceCitation = "ICAR-Indian Institute of Maize Research (IIMR) Ludhiana Guidelines 2024",
                language = "en",
                lastVerifiedDate = "2026-11-08"
            )
        )
        list.add(
            OfflineKnowledgeEntity(
                id = "icar-mze-faw-hi",
                collectionId = "KB04",
                crop = "Maize",
                category = "pest_management",
                title = "मक्का: फॉल आर्मीवर्म (सैनिक कीट) गोभ अनुप्रयोग एवं जैविक नियंत्रण",
                content = "फॉल आर्मीवर्म (सैनिक कीट) गोभ प्रबंधन: सुंडी पौधे के बीच की गोभ (पोंगा) में छुपकर पत्तियों को खाती है और बुरादे जैसा मल छोड़ती है। गोभ अनुप्रयोग तकनीक: 1) रेत और राख का मिश्रण (9:1) गोभ में डालने से छोटी सुंडियां नष्ट हो जाती हैं। 2) जैविक नियंत्रण: मेटाराइजियम या बीटी पाउडर 2 ग्राम/लीटर सीधे गोभ में स्प्रे करें। 3) रासायनिक उपचार: क्लोरेंट्रानिलिप्रोल 18.5% एससी (0.4 मिली/लीटर) या एमामेक्टिन बेंजोएट 5% एसजी (0.4 ग्राम/लीटर) का नोजल सीधे गोभ पर रखकर छिड़काव करें। प्रतीक्षा अवधि 14 दिन।",
                sourceCitation = "ICAR-Indian Institute of Maize Research (IIMR) Ludhiana Guidelines 2024",
                language = "hi",
                lastVerifiedDate = "2026-11-08"
            )
        )

        // Topic B: Push-Pull Trap Crops
        list.add(
            OfflineKnowledgeEntity(
                id = "icar-mze-push-te",
                collectionId = "KB04",
                crop = "Maize",
                category = "cultivation",
                title = "మొక్కజొన్న: కత్తెర పురుగు నివారణకు పుష్-పుల్ (ఆకర్షణ-వికర్షణ) పంటల పద్ధతి",
                content = "పుష్-పుల్ పంటల విధానం: 1) ఆకర్షణ పంట (పుల్): మొక్కజొన్న పొలం గట్ల చుట్టూ 3-4 వరుసలు నేపియర్ గడ్డి (సూపర్ నేపియర్) లేదా పశుగ్రాస జొన్న నాటాలి. కత్తెర పురుగు రెక్కల తల్లులు మొక్కజొన్నను వదిలి నేపియర్ గడ్డిపై గుడ్లు పెడతాయి. నేపియర్ గడ్డిలోని జిగురు వల్ల లద్దె పురుగులు చనిపోతాయి. 2) వికర్షణ అంతర పంట (పుష్): మొక్కజొన్నలో 2 వరుసలకు ఒక వరుస బొబ్బర్లు (అలసందలు) లేదా డెస్మోడియం అంతర పంటగా వేయాలి. వీటి నుండి వెలువడే వాసన వల్ల కత్తెర పురుగులు మొక్కజొన్న దరిదాపులకు రావు. పురుగు మందుల ఖర్చు 70% వరకు తగ్గుతుంది.",
                sourceCitation = "ICAR-IIMR & FAO Agroecological FAW Management Manual 2024",
                language = "te",
                lastVerifiedDate = "2026-11-08"
            )
        )
        list.add(
            OfflineKnowledgeEntity(
                id = "icar-mze-push-en",
                collectionId = "KB04",
                crop = "Maize",
                category = "cultivation",
                title = "Maize: Push-Pull Trap Crop Strategy for Fall Armyworm",
                content = "Push-Pull Biological Pest Defense: 1) Trap Crop (Pull): Plant 3-4 dense perimeter rows of Napier grass (Pennisetum purpureum) or forage sorghum around the maize field border. Napier grass attracts female FAW moths to oviposit on it, but produces a gummy exude that traps larvae and prevents completion of lifecycle. 2) Repellent Intercrop (Push): Intercrop maize with Desmodium (silverleaf) or Cowpea (1 row for every 2 rows of maize). Desmodium emits volatile semiochemicals that repel FAW moths away from maize while suppressing striga weed and fixing soil nitrogen. Reduces chemical pesticide sprays by over 70%.",
                sourceCitation = "ICAR-IIMR & FAO Agroecological FAW Management Manual 2024",
                language = "en",
                lastVerifiedDate = "2026-11-08"
            )
        )
        list.add(
            OfflineKnowledgeEntity(
                id = "icar-mze-push-hi",
                collectionId = "KB04",
                crop = "Maize",
                category = "cultivation",
                title = "मक्का: पुश-पुल ट्रैप क्रॉप तकनीक (फॉल आर्मीवर्म रोकथाम)",
                content = "पुश-पुल ट्रैप क्रॉप विधि: 1) पुल (आकर्षक फसल): खेत के चारों ओर नेपियर घास या चारा ज्वार की 3-4 कतारें लगाएं। कीट मक्का छोड़कर नेपियर घास पर अंडे देते हैं जहां चिपचिपे रस से सुंडियां नष्ट हो जाती हैं। 2) पुश (प्रतिकर्षक फसल): मक्का के बीच 2:1 के अनुपात में लोबिया (चौलाई) की अंतरवर्तीय फसल लगाएं। इसकी गंध से कीट मक्के से दूर रहते हैं और मिट्टी में नाइट्रोजन भी बढ़ता है। कीटनाशक छिड़काव में 70% तक कमी आती है।",
                sourceCitation = "ICAR-IIMR & FAO Agroecological FAW Management Manual 2024",
                language = "hi",
                lastVerifiedDate = "2026-11-08"
            )
        )

        return list
    }
}
