package com.bolkisan.farmer.data.local

import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.bolkisan.farmer.data.local.entities.OfflineKnowledgeEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Pre-populates the Room SQLite Database with authentic ICAR / CIBRC offline packages of practice
 * across major Indian crops (Cotton, Rice, Chilli, Tomato, Groundnut, Soybean, Red Gram, Chickpea,
 * Maize, Wheat, Turmeric, Onion, Natural Farming) in Telugu, Hindi, and English.
 */
object KnowledgePrepopulator {

    fun getDatabaseCallback(databaseProvider: () -> AppDatabase): RoomDatabase.Callback {
        return object : RoomDatabase.Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                CoroutineScope(Dispatchers.IO).launch {
                    val appDb = databaseProvider()
                    seedDatabase(appDb.offlineKnowledgeDao())
                    MandiPrepopulator.seedMandiPricesIfEmpty(appDb.mandiPriceDao())
                }
            }
        }
    }

    suspend fun seedDatabase(dao: OfflineKnowledgeDao) {
        val currentCount = dao.getKnowledgeCount()
        if (currentCount < 50) {
            val allPackages = getPrepopulatedKnowledge()
            dao.insertKnowledge(allPackages)
        }
    }

    fun getPrepopulatedKnowledge(): List<OfflineKnowledgeEntity> {
        val list = mutableListOf<OfflineKnowledgeEntity>()
        list.add(
            OfflineKnowledgeEntity(
                id = "icar-bgm-rhizo-en",
                collectionId = "KB02",
                crop = "Bengal Gram",
                category = "cultivation",
                title = "Bengal Gram / Chickpea: Dual Seed Inoculation & Wilt Defense",
                content = "Dual Seed Inoculation Protocol: 1) Fungicidal / Biocontrol treatment: Treat seed with Trichoderma viride @ 10 g/kg seed or Carbendazim + Thiram (1:2) @ 2.5 g/kg seed to prevent seedling rot and Fusarium wilt. 2) Bio-fertilizers: Post-fungicide drying, coat seeds with Chickpea-specific Rhizobium culture @ 200 g/acre seed + PSB (Phosphate Solubilizing Bacteria) @ 200 g/acre using 10% jaggery solution. Dry under shade before sowing. Saves 20-25 kg chemical nitrogen/ha.",
                sourceCitation = "ICAR-IIPR & PJTSAU Rabi Pulses Package of Practices 2024",
                language = "en",
                lastVerifiedDate = "2026-11-09"
            )
        )
        list.add(
            OfflineKnowledgeEntity(
                id = "icar-bgm-rhizo-hi",
                collectionId = "KB02",
                crop = "Bengal Gram",
                category = "cultivation",
                title = "चना: दोहरा बीज उपचार एवं राइजोबियम संवर्धन विधि",
                content = "चना बीज शोधन: 1) फफूंदनाशी: 10 ग्राम ट्राइकोडर्मा विरिडी या 2 ग्राम थीरम + 1 ग्राम कार्बेन्डाजिम प्रति किलो बीज। 2) जैव उर्वरक: फफूंदनाशी उपचार के बाद 1 एकड़ के बीज के लिए 200 ग्राम चना राइजोबियम + 200 ग्राम पीएसबी (PSB) को गुड़ के पानी में घोलकर बीजों पर अच्छी तरह लेप करें और छाया में सुखाएं। इससे नाइट्रोजन की उपलब्धता बढ़ती है और जड़ गलन से सुरक्षा मिलती है।",
                sourceCitation = "ICAR-IIPR & PJTSAU Rabi Pulses Package of Practices 2024",
                language = "hi",
                lastVerifiedDate = "2026-11-09"
            )
        )
        list.add(
            OfflineKnowledgeEntity(
                id = "icar-bgm-rhizo-te",
                collectionId = "KB02",
                crop = "Bengal Gram",
                category = "cultivation",
                title = "శనగ: విత్తన శుద్ధి మరియు రైజోబియం సంవర్ధనం",
                content = "శనగ విత్తన శుద్ధి ప్రాముఖ్యత: ఎండుతెగులు, వేరుకుళ్ళు నివారణకు మరియు నత్రజని స్థిరీకరణకు విత్తన శుద్ధి కీలకం. విధానం: 1) శిలీంధ్రనాశిని: కిలో విత్తనానికి 2 గ్రాముల థైరమ్ + 1 గ్రాము కార్బెండజిమ్ లేదా 10 గ్రాముల ట్రైకోడెర్మా విరిడే కలపాలి. 2) జీవ ఎరువులు: శిలీంధ్రనాశిని శుద్ధి తర్వాత 200 గ్రాముల శనగ రైజోబియం సంవర్ధనం (కల్చర్) + 200 గ్రాముల PSB ని బెల్లం ద్రావణంలో కలిపి విత్తనాలకు పట్టించి నీడలో ఆరబెట్టాలి. దీనివల్ల 25% యూరియా వాడకం తగ్గుతుంది.",
                sourceCitation = "ICAR-IIPR & PJTSAU Rabi Pulses Package of Practices 2024",
                language = "te",
                lastVerifiedDate = "2026-11-09"
            )
        )
        list.add(
            OfflineKnowledgeEntity(
                id = "icar-chk-pbw-en",
                collectionId = "KB05",
                crop = "Bengal Gram",
                category = "pest_management",
                title = "Chickpea: Gram Pod Borer IPM, CIBRC Controls & Shoot Nipping Agronomy",
                content = "Crop & Practices: Chickpea / Bengal Gram (Cicer arietinum) - Gram Pod Borer (Helicoverpa armigera) IPM & Terminal Nipping Agronomy. Pod Borer Symptoms & ETL: Vegetative to pod filling (35-90 DAS). Larvae feed on tender leaves, defoliate shoots, and bore round circular holes into green pods consuming seeds. ETL: 1 larva/meter row or 2-3 damaged pods/plant. Terminal Shoot Nipping Practice (30-35 DAS): Pinching/nipping off top 2-3 cm of terminal growing shoots at 30-35 DAS before flowering is a statutory agronomic recommendation. Benefits: 1) Breaks apical dominance, stimulating profuse secondary and tertiary lateral branching (increasing pod clusters by 30-45%). 2) Mechanically removes Helicoverpa egg batches and young instars hiding in shoot tips. IPM: 1) Install 20 T-shaped bird perches/acre. 2) Erect 5 pheromone traps/acre (Helilure). 3) Spray 5% NSKE @ 10 kg/acre or HaNPV @ 200 LE/acre at flower initiation. CIBRC Approved Chemical Control: Emamectin Benzoate 5% SG (Proclaim) @ 88 g/acre (approx 6.6 g per 15L backpack pump in 200L water), Pre-Harvest Interval (PHI): 14 days; OR Chlorantraniliprole 18.5% SC (Coragen) @ 50 ml/acre (approx 3.75 ml per 15L pump), PHI: 11 days; OR Flubendiamide 39.35% SC (Fame) @ 40 ml/acre (approx 3.0 ml per 15L pump), PHI: 10 days. Safety & PPE: Wear chemical-resistant gloves, respirator mask, and protective goggles.",
                sourceCitation = "ICAR-IIPR Kanpur Chickpea Production Technology & CIBRC 2024",
                language = "en",
                lastVerifiedDate = "2026-11-09"
            )
        )
        list.add(
            OfflineKnowledgeEntity(
                id = "icar-chk-pbw-hi",
                collectionId = "KB05",
                crop = "Bengal Gram",
                category = "pest_management",
                title = "चना: फली छेदक (हेलिकोवर्पा) रोकथाम, कीटनाशक एवं शीर्ष खूंटाई (निपिंग) तकनीक",
                content = "फसल व तकनीक: चना (बंगाल ग्राम) - फली छेदक सुंडी (हेलिकोवर्पा आर्मिगेरा) एवं शीर्ष खूंटाई (निपिंग)। लक्षण व ईटीएल: वानस्पतिक से फली बनने तक (35-90 दिन)। सुंडी कोमल पत्तियों को खाती है तथा फलियों में गोल छेद बनाकर दाने चट कर जाती है। ईटीएल: 1 सुंडी प्रति मीटर कतार या 2-3 क्षतिग्रस्त फलियां प्रति पौधा। शीर्ष खूंटाई (निपिंग - 30-35 दिन): बुवाई के 30-35 दिन बाद (फूल आने से पहले) पौधों के ऊपरी 2-3 सेमी सिरे को चुटकियों से तोड़ना चाहिए। लाभ: 1) मुख्य तने की बढ़वार रुकती है और पार्श्व शाखाएं अधिक निकलती हैं जिससे पैदावार 30-45% बढ़ती है। 2) ऊपरी शाखाओं पर लगे सुंडी के अंडे व छोटी इल्लियां स्वतः नष्ट हो जाती हैं। समेकित प्रबंधन (IPM): 1) प्रति एकड़ 20 टी-आकार के पक्षी बसेरे लगाएं। 2) प्रति एकड़ 5 फेरोमोन ट्रैप लगाएं। 3) फूल शुरू होने पर 5% नीम बीज अर्क (एनएसकेई) का छिड़काव करें। CIBRC पंजीकृत रासायनिक नियंत्रण: एमामेक्टिन बेंजोएट 5% एसजी (प्रोक्लेम) 88 ग्राम प्रति एकड़ (लगभग 6.6 ग्राम प्रति 15 लीटर नैपसैक पंप; 200 लीटर पानी में), प्रतीक्षा अवधि (PHI): 14 दिन; अथवा क्लोरेंट्रानिलिप्रोल 18.5% एससी 50 मिली प्रति एकड़ (लगभग 3.75 मिली प्रति 15 लीटर पंप), प्रतीक्षा अवधि: 11 दिन; अथवा फ्लूबेंडियामाइड 39.35% एससी 40 मिली प्रति एकड़ (3.0 मिली प्रति 15 लीटर पंप), प्रतीक्षा अवधि: 10 दिन। सुरक्षा सावधानियां: छिड़काव के समय दस्ताने व चश्मा अवश्य पहनें।",
                sourceCitation = "भाकृअनुप-भारतीय दलहन अनुसंधान संस्थान (ICAR-IIPR) कानपुर एवं CIBRC 2024",
                language = "hi",
                lastVerifiedDate = "2026-11-09"
            )
        )
        list.add(
            OfflineKnowledgeEntity(
                id = "icar-chk-pbw-te",
                collectionId = "KB05",
                crop = "Bengal Gram",
                category = "pest_management",
                title = "శనగ: శనగ పచ్చ పురుగు సమగ్ర నివారణ, రసాయనాలు మరియు చిగుళ్లు తుంచడం (నిప్పింగ్) ప్రాముఖ్యత",
                content = "పంట మరియు యాజమాన్యం: శనగ (బెంగాల్ గ్రామ్) - శనగ పచ్చ పురుగు (Helicoverpa armigera) నివారణ మరియు చిగుళ్లు తుంచే (Nipping) పద్ధతి. పురుగు లక్షణాలు & ETL: శాఖీయ దశ నుండి కాయ దశ వరకు (35-90 రోజులు). లద్దె పురుగులు లేత ఆకులను తిని, కాయలకు గుండ్రటి రంధ్రాలు చేసి లోపలి గింజలను తింటాయి. ETL: వరుస మీటరుకు 1 పురుగు లేదా మొక్కకు 2-3 దెబ్బతిన్న కాయలు. చిగుళ్లు తుంచడం (Nipping - 30-35 రోజులు): విత్తిన 30-35 రోజులకు పూత రాకముందే పై చిగుళ్లను (2-3 సెం.మీ) చేతితో లేదా కొడవలితో తుంచాలి. లాభాలు: 1) కొమ్మలు ఎక్కువగా చీలి పక్క కొమ్మల సంఖ్య పెరిగి కాయల దిగుబడి 30-45% పెరుగుతుంది. 2) చిగుళ్లపై ఉన్న శనగ పచ్చ పురుగు గుడ్లు, చిన్న పురుగులు యాంత్రికంగా తొలగిపోతాయి. సమగ్ర నివారణ (IPM): 1) ఎకరాకు 20 పక్షి స్థావరాలు (T కర్రలు) నాటాలి. 2) ఎకరాకు 5 లింగాకర్షక బుట్టలు పెట్టాలి. 3) పూత ప్రారంభంలో 5% వేప గింజల కషాయం పిచికారీ చేయాలి. CIBRC ఆమోదిత రసాయన నివారణ: ఎమామెక్టిన్ బెంజోయేట్ 5% SG (ప్రోక్లైమ్) ఎకరాకు 88 గ్రా (15 లీటర్ల చేతి పంపుకు 6.6 గ్రా; 200 లీటర్ల నీటిలో), వేచి ఉండే కాలం (PHI): 14 రోజులు; లేదా క్లోరాంట్రానిలిప్రోల్ 18.5% SC (కొరాజెన్) ఎకరాకు 50 మి.లీ (15 లీటర్ల పంపుకు 3.75 మి.లీ), వేచి ఉండే కాలం: 11 రోజులు; లేదా ఫ్లూబెండియమైడ్ 39.35% SC ఎకరాకు 40 మి.లీ (15 లీటర్ల పంపుకు 3.0 మి.లీ), వేచి ఉండే కాలం: 10 రోజులు. జాగ్రత్తలు: చేతి తొడుగులు మరియు మాస్క్ ధరించాలి.",
                sourceCitation = "ICAR-IIPR కాన్పూర్ & ప్రొఫెసర్ జయశంకర్ తెలంగాణ వ్యవసాయ విశ్వవిద్యాలయం (PJTSAU) 2024",
                language = "te",
                lastVerifiedDate = "2026-11-09"
            )
        )
        list.add(
            OfflineKnowledgeEntity(
                id = "icar-chk-wilt-en",
                collectionId = "KB04",
                crop = "Bengal Gram",
                category = "disease",
                title = "Chickpea: Fusarium Wilt & Dry Root Rot Symptoms, Soil Health & Seed Treatments",
                content = "Crop & Diseases: Chickpea - Fusarium Wilt (Fusarium oxysporum f. sp. ciceris) & Dry Root Rot (Rhizoctonia bataticola / Macrophomina phaseolina). Growth Stages & Symptoms: Seedling stage (3-5 weeks) and podding stage (6-10 weeks). Wilt: Sudden drooping of petioles and leaflets, dull green foliage turning dark brown; internal xylem vessels show dark brown to black longitudinal vascular discoloration when taproot is split. Dry Root Rot: Favored by moisture stress and warm temperatures (>30°C); roots become brittle, dry and black with shredded bark devoid of lateral rootlets. IPM: 1) Deep summer ploughing. 2) Sow at optimum depth (8-10 cm in moist zone) and avoid early sowing in hot soils (delay till second fortnight of October). 3) Grow wilt-tolerant cultivars: JG 11, JAKI 9218, NBeG 3, RVG 202. 4) Bio-seed treatment with Trichoderma asperellum @ 10 g/kg seed. CIBRC Approved Chemical Control: Fungicidal seed treatment is MANDATORY prior to sowing: Carboxin 37.5% + Thiram 37.5% DS (Vitavax Power) @ 2.0-2.5 g/kg seed; OR Carbendazim 50% WP (Bavistin) @ 2.0 g/kg seed. Mix seed with fungicide in seed dresser 24 hours before sowing. PPE: Wear protective rubber gloves during seed treatment.",
                sourceCitation = "ICAR-IIPR Kanpur Chickpea Pathology Compendium & PJTSAU 2024",
                language = "en",
                lastVerifiedDate = "2026-11-09"
            )
        )
        list.add(
            OfflineKnowledgeEntity(
                id = "icar-chk-wilt-hi",
                collectionId = "KB04",
                crop = "Bengal Gram",
                category = "disease",
                title = "चना: उकठा रोग (विल्ट) एवं शुष्क जड़ सड़न पहचान, बीजोपचार एवं रोकथाम",
                content = "फसल व रोग: चना - उकठा रोग (फ्यूजेरियम ऑक्सीस्पोरम) एवं शुष्क जड़ सड़न (राइजोक्टोनिया)। अवस्थाएं एवं लक्षण: अंकुरण के 3-5 सप्ताह बाद एवं फली बनते समय। उकठा में पौधे की पत्तियां अचानक लटककर सूख जाती हैं; जड़ को लंबाई में चीरने पर अंदर की वाहिकाएं गहरी भूरी या काली दिखती हैं। जड़ सड़न में गर्मी और सूखे के समय जड़ें सूखकर काली हो जाती हैं और छाल छिल जाती है। समेकित प्रबंधन (IPM): 1) गर्मियों में गहरी जुताई करें। 2) अक्टूबर के दूसरे पखवाड़े में पर्याप्त नमी में 8-10 सेमी गहराई पर बोएं। 3) प्रतिरोधी किस्में (जेजी 11, जाकी 9218, एनबीईजी 3) लगाएं। 4) ट्राइकोडर्मा एस्परेलम 10 ग्राम/किग्रा बीज से जैविक बीजोपचार करें। CIBRC अनुमोदित रासायनिक बीजोपचार: बुवाई से पहले बीजोपचार अनिवार्य है: कार्बोक्सिन 37.5% + थीरम 37.5% डीएस (विटावैक्स पावर) 2.0-2.5 ग्राम प्रति किग्रा बीज; अथवा कार्बेन्डाजिम 50% डब्ल्यूपी 2.0 ग्राम प्रति किग्रा बीज से उपचारित करें। सुरक्षा सावधानियां: बीजोपचार के समय रबर के दस्ताने पहनें।",
                sourceCitation = "भाकृअनुप-भारतीय दलहन अनुसंधान संस्थान (ICAR-IIPR) कानपुर एवं CIBRC 2024",
                language = "hi",
                lastVerifiedDate = "2026-11-09"
            )
        )
        list.add(
            OfflineKnowledgeEntity(
                id = "icar-chk-wilt-te",
                collectionId = "KB04",
                crop = "Bengal Gram",
                category = "disease",
                title = "శనగ: ఎండు తెగులు (విల్ట్) మరియు వేరుకుళ్ళు తెగులు గుర్తింపు, విత్తనశుద్ధి మరియు నివారణ",
                content = "పంట మరియు వ్యాధులు: శనగ - ఎండు తెగులు (Fusarium oxysporum) మరియు ఎండు వేరుకుళ్ళు (Dry Root Rot). దశలు మరియు లక్షణాలు: మొలక దశ (3-5 వారాలు) మరియు కాయ దశ (6-10 వారాలు). ఎండు తెగులులో ఆకులు వాడి రంగు కోల్పోయి వేగంగా ఎండిపోతాయి; ప్రధాన వేరును నిలువుగా చీల్చి చూస్తే లోపలి నాళాలు నల్లగా మారి ఉంటాయి. వేరుకుళ్ళు తెగులులో నేలలో తేమ లేక వేడి ఎక్కువైనప్పుడు వేర్లు ఎండిపోయి నల్లబడి నారలా విడిపోతాయి. సమగ్ర నివారణ (IPM): 1) లోతు వేసవి దుక్కులు చేయాలి. 2) విత్తనాన్ని సరైన తేమలో (8-10 సెం.మీ లోతున) అక్టోబర్ రెండవ పక్షంలో విత్తాలి. 3) తెగులును తట్టుకునే రకాలు (JG 11, JAKI 9218, NBeG 3) సాగు చేయాలి. 4) కిలో విత్తనానికి ట్రైకోడెర్మా ఆస్పరెల్లమ్ 10 గ్రా కలిపి విత్తనశుద్ధి చేయాలి. రసాయన విత్తనశుద్ధి: విత్తే ముందు తప్పనిసరిగా విత్తనశుద్ధి చేయాలి: కిలో విత్తనానికి కార్బాక్సిన్ 37.5% + థైరమ్ 37.5% DS (విటావాక్స్ పవర్) 2.0-2.5 గ్రా లేదా కార్బండజిమ్ 50% WP 2.0 గ్రా కలిపి శుద్ధి చేయాలి. జాగ్రత్తలు: విత్తనశుద్ధి సమయంలో చేతి తొడుగులు తప్పనిసరిగా ధరించాలి.",
                sourceCitation = "ICAR-IIPR కాన్పూర్ & ప్రొఫెసర్ జయశంకర్ తెలంగాణ వ్యవసాయ విశ్వవిద్యాలయం (PJTSAU) 2024",
                language = "te",
                lastVerifiedDate = "2026-11-09"
            )
        )
        list.add(
            OfflineKnowledgeEntity(
                id = "icar-chl-anth-en",
                collectionId = "KB04",
                crop = "Chilli",
                category = "disease",
                title = "Chilli: Anthracnose, Fruit Rot & Die-Back Symptoms, Cultural Care & Fungicides",
                content = "Crop & Disease: Chilli (Capsicum annuum) - Anthracnose / Fruit Rot / Die-Back (Colletotrichum capsici / Colletotrichum gloeosporioides). Growth Stages & Symptoms: Flowering to fruit ripening stage (60-130 DAS). Die-back: Tender twigs dry from tip downwards, turning dirty white/ashy grey with scattered black dots (acervuli), twigs snap easily. Fruit rot: Circular sunken water-soaked spots appear on green and ripening red chilli pods; spots expand along the fruit axis with concentric rings of dark acervuli; infected pods bleach straw-colored, dry up prematurely, and drop. Favorable Triggers: Temperatures 28-32°C, relative humidity >85%, cloudy overcast skies, unseasonal rains during fruit maturation. IPM: 1) Collect and destroy infected fruits and twigs post-harvest. 2) Seed treatment with Trichoderma viride @ 10 g/kg seed or Captan @ 3 g/kg seed. 3) Provide excellent field drainage to prevent waterlogging. 4) Use disease-free certified seeds. CIBRC Approved Chemical Control: Tebuconazole 25.9% m/m EC (Folicur) @ 250 ml/acre (approx 18.5 ml per 15L backpack pump; 1.25 ml/L in 200L water), Pre-Harvest Interval (PHI): 5 days; OR Azoxystrobin 18.2% + Difenoconazole 11.4% SC (Amistar Top) @ 200 ml/acre (approx 15 ml per 15L pump; 1.0 ml/L), PHI: 5 days; OR Mancozeb 75% WP @ 600 g/acre (approx 45 g per 15L pump), PHI: 7 days. Spray at first sign of twig dieback or flower drop and repeat after 12-15 days. Safety & PPE: Wear chemical-resistant gloves, apron, eye protection, and boots.",
                sourceCitation = "ICAR-Indian Institute of Horticultural Research (IIHR) Bengaluru & PJTSAU 2024",
                language = "en",
                lastVerifiedDate = "2026-11-09"
            )
        )
        list.add(
            OfflineKnowledgeEntity(
                id = "icar-chl-anth-hi",
                collectionId = "KB04",
                crop = "Chilli",
                category = "disease",
                title = "मिर्च: फल सड़न एवं डाई-बैक (एंथ्रेक्नोज) लक्षण, रोकथाम एवं अनुशंसित कवकनाशी",
                content = "फसल व रोग: मिर्च - फल सड़न एवं टहनी सूखना (डाई-बैक / एंथ्रेक्नोज - कोलेटोट्राइकम कैप्सीकी)। अवस्थाएं एवं लक्षण: फूल आने से फल पकने तक (60-130 दिन)। डाई-बैक में ऊपरी टहनियां ऊपर से नीचे की ओर सूखने लगती हैं और उन पर काले दाने जैसे उभार बनते हैं। फल सड़न में हरी व लाल पकी मिर्च पर गोल धंसे हुए पानीदार धब्बे बनते हैं जिन पर काले छल्लेदार घेरे दिखते हैं; फल सूखकर पुआल जैसे सफेद होकर गिर जाते हैं। अनुकूल मौसम: तापमान 28-32°C, 85% से अधिक नमी व फल पकते समय बेमौसम बारिश। समेकित प्रबंधन (IPM): 1) रोगग्रस्त फल व सूखी टहनियां एकत्र कर नष्ट करें। 2) ट्राइकोडर्मा विरिडी 10 ग्राम/किग्रा बीज से बीजोपचार करें। 3) खेत में जलभराव न होने दें। CIBRC अनुमोदित रासायनिक नियंत्रण: टेबुकोनाजोल 25.9% ईसी (फोलिकर) 250 मिली प्रति एकड़ (लगभग 18.5 मिली प्रति 15 लीटर नैपसैक पंप; 200 लीटर पानी में), प्रतीक्षा अवधि (PHI): 5 दिन; अथवा एजोक्सीस्ट्रोबिन 18.2% + डाइफेनोकोनाजोल 11.4% एससी (एमिस्टार टॉप) 200 मिली प्रति एकड़ (लगभग 15 मिली प्रति 15 लीटर पंप), प्रतीक्षा अवधि: 5 दिन; अथवा मैंकोजेब 75% डब्ल्यूपी 600 ग्राम प्रति एकड़ (45 ग्राम प्रति 15 लीटर पंप), प्रतीक्षा अवधि: 7 दिन। पीपीई: रबर के दस्ताने एवं सुरक्षात्मक चश्मा अवश्य लगाएं।",
                sourceCitation = "भाकृअनुप-भारतीय बागवानी अनुसंधान संस्थान (ICAR-IIHR) एवं CIBRC 2024",
                language = "hi",
                lastVerifiedDate = "2026-11-09"
            )
        )
        list.add(
            OfflineKnowledgeEntity(
                id = "icar-chl-anth-te",
                collectionId = "KB04",
                crop = "Chilli",
                category = "disease",
                title = "మిరప: కొమ్మ ఎండు తెగులు మరియు కాయ కుళ్ళు (ఆంథ్రాక్నోస్) గుర్తింపు, నివారణ మరియు మందులు",
                content = "పంట మరియు వ్యాధి: మిరప - కొమ్మ ఎండు మరియు కాయ కుళ్ళు తెగులు (Anthracnose / Fruit Rot - కొల్లెటోట్రైకమ్ క్యాప్సిసి). దశలు మరియు లక్షణాలు: పూత మరియు కాయ పక్వానికి వచ్చే దశ (60-130 రోజులు). కొమ్మ ఎండు దశలో లేత కొమ్మలు పై నుండి క్రిందికి ఎండిపోతూ బూడిద రంగులోకి మారుతాయి; కొమ్మలపై నల్లటి చుక్కలు ఏర్పడతాయి. కాయ కుళ్ళు దశలో పండిన ఎర్ర కాయలు మరియు పచ్చికాయలపై గుండ్రటి నొక్కుకుపోయిన నీటి మచ్చలు ఏర్పడి వాటిపై ఏకకేంద్రక వలయాలలో నల్లటి శిలీంధ్ర చుక్కలు వస్తాయి; కాయలు రంగు కోల్పోయి గడ్డి రంగులోకి మారి రాలిపోతాయి. అనుకూల వాతావరణం: ఉష్ణోగ్రత 28-32°C, 85% పైగా గాలిలో తేమ, ఆకాశం మేఘావృతమై ఉండటం. సమగ్ర నివారణ (IPM): 1) తెగులు సోకిన కాయలు మరియు ఎండిన కొమ్మలను సేకరించి నాశనం చేయాలి. 2) విత్తనశుద్ధి: కిలో విత్తనానికి ట్రైకోడెర్మా విరిడి 10 గ్రా లేదా కాప్టాన్ 3 గ్రా తో శుద్ధి చేయాలి. 3) పొలంలో నీరు నిల్వ ఉండకుండా మురుగు కాలువలు ఏర్పాటు చేయాలి. CIBRC ఆమోదిత రసాయన నివారణ: టెబుకొనజోల్ 25.9% EC (ఫోలిక్యూర్) ఎకరాకు 250 మి.లీ (15 లీటర్ల చేతి పంపుకు 18.5 మి.లీ, లీటరు నీటికి 1.25 మి.లీ; 200 లీటర్ల నీటిలో), వేచి ఉండే కాలం (PHI): 5 రోజులు; లేదా అజాక్సిస్ట్రోబిన్ 18.2% + డైఫెనోకొనజోల్ 11.4% SC (అమిస్టార్ టాప్) ఎకరాకు 200 మి.లీ (15 లీటర్ల పంపుకు 15 మి.లీ), వేచి ఉండే కాలం: 5 రోజులు; లేదా మ్యాంకోజెబ్ 75% WP ఎకరాకు 600 గ్రా (15 లీటర్ల పంపుకు 45 గ్రా), వేచి ఉండే కాలం: 7 రోజులు. జాగ్రత్తలు: చేతి తొడుగులు, మాస్క్, కళ్లజోడు తప్పనిసరి.",
                sourceCitation = "ICAR-IIHR బెంగళూరు & ప్రొఫెసర్ జయశంకర్ తెలంగాణ వ్యవసాయ విశ్వవిద్యాలయం (PJTSAU) 2024",
                language = "te",
                lastVerifiedDate = "2026-11-09"
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
                lastVerifiedDate = "2026-11-09"
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
                lastVerifiedDate = "2026-11-09"
            )
        )
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
                lastVerifiedDate = "2026-11-09"
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
                lastVerifiedDate = "2026-11-09"
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
                lastVerifiedDate = "2026-11-09"
            )
        )
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
                lastVerifiedDate = "2026-11-09"
            )
        )
        list.add(
            OfflineKnowledgeEntity(
                id = "icar-cot-blight-en",
                collectionId = "KB04",
                crop = "Cotton",
                category = "disease",
                title = "Cotton: Bacterial Blight (Angular Leaf Spot / Black Arm) Symptoms, Prevention & Control",
                content = "Crop & Disease: Cotton - Bacterial Blight (Xanthomonas citri pv. malvacearum). Growth Stages & Symptoms: Seedling to boll development (20-110 DAS). Four distinct phases: 1) Seedling blight: Water-soaked circular spots on cotyledons causing seedling death. 2) Angular leaf spot: Water-soaked polygonal spots restricted by leaf veinlets turning dark reddish-brown. 3) Black arm: Black elongated cankers girdling stems causing terminal shoot dieback and lodging. 4) Boll rot: Round dark greasy sunken lesions on green bolls staining lint and rotting seeds. Favorable Weather: Warm temperatures (30-35°C), high relative humidity (>85%), wind-driven rains. IPM: 1) Acid delinting of seed with concentrated Sulphuric acid (100 ml/kg seed) or hot water treatment at 56°C for 10 minutes. 2) Deep summer ploughing and destruction of infected crop debris. 3) Balanced nitrogen application. CIBRC Chemical Control: Foliar spray of Copper Oxychloride 50% WP (Blitox) @ 500-600 g/acre (approx 40 g per 15L backpack pump in 200L water) PLUS Streptocycline (Streptomycin sulphate 90% + Tetracycline hydrochloride 10%) @ 20-30 g/acre (approx 2 g per 15L pump). Waiting Period (PHI): 15 days. Repeat after 15 days if rainy conditions persist. PPE: Wear rubber gloves and protective goggles.",
                sourceCitation = "ICAR-CICR Nagpur Technical Bulletin on Cotton Pathogens & ANGRAU 2024",
                language = "en",
                lastVerifiedDate = "2026-11-09"
            )
        )
        list.add(
            OfflineKnowledgeEntity(
                id = "icar-cot-blight-hi",
                collectionId = "KB04",
                crop = "Cotton",
                category = "disease",
                title = "कपास: जीवाणु झुलसा (कोणीय पर्ण चित्ती / ब्लैक आर्म) लक्षण एवं प्रमाणित रोकथाम",
                content = "फसल व रोग: कपास - जीवाणु झुलसा / ब्लैक आर्म (जैंथोमोनास सिट्राई)। अवस्थाएं व लक्षण: अंकुरण से टिंडे बनने तक (20-110 दिन)। पत्तियों की नसों के बीच पानीदार कोणीय (तिकोने) धब्बे बनते हैं जो बाद में कत्थई हो जाते हैं। तनों पर काले लंबे घाव बन जाते हैं जिससे शाखाएं टूटकर लटक जाती हैं (ब्लैक आर्म)। टिंडों पर गोल धब्बे पड़कर रुई सड़ जाती है। अनुकूल मौसम: तापमान 30-35°C तथा 85% से अधिक आर्द्रता। समेकित प्रबंधन (IPM): 1) 100 मिली सल्फ्यूरिक एसिड प्रति किग्रा बीज से बीजोपचार कर रेशा हटाएं अथवा 56°C गर्म पानी में 10 मिनट उपचारित करें। 2) फसल अवशेषों को नष्ट करें। CIBRC अनुमोदित रासायनिक नियंत्रण: कॉपर ऑक्सीक्लोराइड 50% डब्ल्यूपी (ब्लाईटॉक्स) 500-600 ग्राम प्रति एकड़ (लगभग 40 ग्राम प्रति 15 लीटर नैपसैक पंप; 200 लीटर पानी में) + स्ट्रेप्टोसाइक्लिन 20-30 ग्राम प्रति एकड़ (लगभग 2 ग्राम प्रति 15 लीटर पंप) का घोल बनाकर 15 दिन के अंतराल पर छिड़काव करें। प्रतीक्षा अवधि (PHI): 15 दिन। पीपीई: रबर के दस्ताने व चश्मा पहनें।",
                sourceCitation = "भाकृअनुप-केंद्रीय कपास अनुसंधान संस्थान (ICAR-CICR) एवं CIBRC 2024",
                language = "hi",
                lastVerifiedDate = "2026-11-09"
            )
        )
        list.add(
            OfflineKnowledgeEntity(
                id = "icar-cot-blight-te",
                collectionId = "KB04",
                crop = "Cotton",
                category = "disease",
                title = "ప్రత్తి: బాక్టీరియా ఆకుమచ్చ తెగులు (బ్లాక్ ఆర్మ్ / కాయ కుళ్ళు) గుర్తింపు మరియు నివారణ",
                content = "పంట మరియు వ్యాధి: ప్రత్తి - బాక్టీరియా ఆకుమచ్చ తెగులు (Angular Leaf Spot / Black Arm - జాంథోమోనాస్). దశలు మరియు లక్షణాలు: మొలక దశ నుండి కాయ దశ వరకు (20-110 రోజులు). 1) ఆకులపై ఈనెల మధ్య కోణాకారపు నీటి మచ్చలు ఏర్పడి గోధుమ రంగులోకి మారడం. 2) కొమ్మలు మరియు కాండంపై నల్లటి చారలు ఏర్పడి ఎండి విరిగిపోవడం ('బ్లాక్ ఆర్మ్' దశ). 3) పచ్చికాయలపై గుండ్రటి నల్లని నొక్కుకుపోయిన నీటి మచ్చలు ఏర్పడి లోపలి దూది కుళ్ళిపోవడం. అనుకూల వాతావరణం: వేడి (30-35°C), 85% పైగా తేమ, వర్షపు జల్లులు. సమగ్ర నివారణ (IPM): 1) విత్తనశుద్ధి: కిలో విత్తనానికి 100 మి.లీ గాఢ సల్ఫ్యూరిక్ ఆమ్లంతో విత్తన పొట్టు తీసివేయడం. 2) మునుపటి పంట వ్యర్థాలను కాల్చివేయడం లేదా దుక్కిలో కలిపివేయడం. 3) నత్రజని ఎరువులను అధికంగా వాడకపోవడం. రసాయన నివారణ: కాపర్ ఆక్సీక్లోరైడ్ 50% WP (బ్లైటాక్స్) ఎకరాకు 500-600 గ్రా (15 లీటర్ల చేతి పంపుకు 40 గ్రా) + స్ట్రెప్టోసైక్లిన్ ఎకరాకు 20-30 గ్రా (15 లీటర్ల చేతి పంపుకు 2 గ్రా) 200 లీటర్ల నీటిలో కలిపి 15 రోజుల వ్యవధిలో రెండుసార్లు పిచికారీ చేయాలి. వేచి ఉండే కాలం (PHI): 15 రోజులు. జాగ్రత్తలు: మందు పిచికారీ సమయంలో చేతి తొడుగులు, మాస్క్ ధరించాలి.",
                sourceCitation = "ICAR-CICR నాగ్‌పూర్ & ఆచార్య ఎన్.జి.రంగా వ్యవసాయ విశ్వవిద్యాలయం (ANGRAU) 2024",
                language = "te",
                lastVerifiedDate = "2026-11-09"
            )
        )
        list.add(
            OfflineKnowledgeEntity(
                id = "icar-cot-pbw-en",
                collectionId = "KB05",
                crop = "Cotton",
                category = "pest_management",
                title = "Cotton: Pink Bollworm (PBW) Symptoms, ETL, IPM & CIBRC Chemical Control",
                content = "Crop & Target: Cotton (Gossypium hirsutum) - Pink Bollworm (Pectinophora gossypiella). Growth Stages & Symptoms: Flowering to boll maturity (60-120 DAS). Rosette flowers (petals twist together in rosette pattern preventing opening), premature green boll opening, brown stained lint, bored seeds (double seed symptom). ETL Threshold: 8 moths/trap/night for 3 consecutive nights OR 10% damaged green bolls / rosette flowers. IPM & Non-Chemical Prevention: 1) Install 5 pheromone traps (Gossyplure septa)/acre for monitoring and 18-20/acre for mass trapping. 2) Release Trichogramma bactrae egg parasitoid @ 60,000/acre at weekly intervals 3 times starting 45 DAS. 3) Spray 5% Neem Seed Kernel Extract (NSKE) or Neem Oil 1500 ppm @ 5 ml/L at squaring stage. 4) Strictly avoid ratoon cotton; destroy crop residues with rotavator. CIBRC Approved Chemical Control: Emamectin Benzoate 5% SG (Proclaim) @ 88 g/acre (approx 6.6 g per 15L backpack pump; 0.44 g/L in 200L water), Pre-Harvest Interval (PHI): 14 days; OR Chlorantraniliprole 18.5% SC (Coragen) @ 60 ml/acre (approx 4.5 ml per 15L pump; 0.3 ml/L in 200L water), PHI: 9 days. Safety & PPE: Wear chemical-resistant rubber gloves, N95 vapor respirator, and eye goggles. Do not spray against wind. Highly toxic to honeybees; avoid spraying during morning pollination.",
                sourceCitation = "ICAR-Central Institute for Cotton Research (CICR) Nagpur & CIBRC Reg CIR-11425/2014, 2024",
                language = "en",
                lastVerifiedDate = "2026-11-09"
            )
        )
        list.add(
            OfflineKnowledgeEntity(
                id = "icar-cot-pbw-hi",
                collectionId = "KB05",
                crop = "Cotton",
                category = "pest_management",
                title = "कपास: गुलाबी सुंडी (पिंक बोलवर्म) लक्षण, ईटीएल, समेकित प्रबंधन एवं अनुशंसित कीटनाशक",
                content = "फसल व समस्या: कपास - गुलाबी सुंडी (पेक्टिनोफोरा गॉसिपिएला)। अवस्थाएं एवं लक्षण: फूल एवं टिंडे बनने की अवस्था (60-120 दिन)। फूल आपस में जुड़कर गुलाब के फूल जैसे (रोसेट फ्लावर) बन जाते हैं, हरे टिंडों में सूक्ष्म छेद, बिनौले अंदर से खोखले व आपस में जुड़े हुए (डबल सीड) मिलते हैं, रुई पर भूरे धब्बे पड़ते हैं। आर्थिक क्षति स्तर (ETL): लगातार 3 रातों तक 8 नर पतंगे/ट्रैप/रात या 10% क्षतिग्रस्त हरे टिंडे। समेकित कीट प्रबंधन (IPM): 1) प्रति एकड़ 5 फेरोमोन ट्रैप (गॉसिप्लूर ल्योर) निगरानी हेतु व 18-20 ट्रैप मास ट्रैपिंग हेतु लगाएं। 2) बुवाई के 45 दिन बाद ट्राइकोग्रामा बैक्ट्रे 60,000 प्रति एकड़ की दर से 3 बार एक-एक सप्ताह के अंतराल पर छोड़ें। 3) कली अवस्था में 5% नीम बीज अर्क (एनएसकेई) या नीम तेल (1500 पीपीएम @ 5 मिली/लीटर) का छिड़काव करें। 4) खूंटी (रतून) फसल न रखें। CIBRC पंजीकृत रासायनिक नियंत्रण: एमामेक्टिन बेंजोएट 5% एसजी (प्रोक्लेम) 88 ग्राम प्रति एकड़ (लगभग 6.6 ग्राम प्रति 15 लीटर नैपसैक पंप; 200 लीटर पानी में), प्रतीक्षा अवधि (PHI): 14 दिन; अथवा क्लोरेंट्रानिलिप्रोल 18.5% एससी (कोराजन) 60 मिली प्रति एकड़ (लगभग 4.5 मिली प्रति 15 लीटर पंप), प्रतीक्षा अवधि: 9 दिन। सुरक्षा एवं पीपीई: रबर के दस्ताने, एन95 मास्क एवं चश्मे का उपयोग करें। हवा के विपरीत दिशा में छिड़काव न करें। मधुमक्खियों के समय छिड़काव न करें।",
                sourceCitation = "भाकृअनुप-केंद्रीय कपास अनुसंधान संस्थान (ICAR-CICR) नागपुर एवं CIBRC 2024",
                language = "hi",
                lastVerifiedDate = "2026-11-09"
            )
        )
        list.add(
            OfflineKnowledgeEntity(
                id = "icar-cot-pbw-te",
                collectionId = "KB05",
                crop = "Cotton",
                category = "pest_management",
                title = "ప్రత్తి: గులాబీ రంగు పురుగు లక్షణాలు, ఆర్థిక నష్ట పరిమితి (ETL), సమగ్ర నివారణ మరియు రసాయనాలు",
                content = "పంట మరియు సమస్య: ప్రత్తి - గులాబీ రంగు కాయ తొలుచు పురుగు (Pink Bollworm). దశలు మరియు లక్షణాలు: పూత మరియు కాయ దశ (60-120 రోజులు). పూత దశలో పువ్వులు ముద్దగా ముడుచుకుపోవడం (రోసెట్ ఫ్లవర్స్/ముద్ద పువ్వులు), పచ్చికాయలు త్వరగా పగలడం, దూది నల్లబడి గింజలు ఒకదానికొకటి అంటుకోవడం. ఆర్థిక నష్ట పరిమితి (ETL): వరుసగా 3 రాత్రులు 8 రెక్కల పురుగులు/బుట్ట/రాత్రి లేదా 10% దెబ్బతిన్న కాయలు. రసాయన రహిత సమగ్ర నివారణ (IPM): 1) ఎకరాకు 5 లింగాకర్షక బుట్టలు (గోసిప్లూర్) నిఘా కోసం, 18-20 బుట్టలు సామూహిక ట్రాపింగ్ కోసం అమర్చాలి. 2) విత్తిన 45 రోజుల నుండి ట్రైకోగ్రామా బాక్ట్రే గుడ్ల పరాన్నజీవిని ఎకరాకు 60,000 చొప్పున వారం వ్యవధిలో 3 సార్లు వదలాలి. 3) మొగ్గ దశలో 5% వేప గింజల కషాయం (NSKE) లేదా వేప నూనె 5 మి.లీ/లీటరు పిచికారీ చేయాలి. 4) మోడు ప్రత్తి సాగు నివారించాలి. CIBRC ఆమోదిత రసాయన నివారణ: ఎమామెక్టిన్ బెంజోయేట్ 5% SG (ప్రోక్లైమ్) ఎకరాకు 88 గ్రా (15 లీటర్ల చేతి పంపుకు 6.6 గ్రా, లీటరు నీటికి 0.44 గ్రా; 200 లీటర్ల నీటిలో), వేచి ఉండే కాలం (PHI): 14 రోజులు; లేదా క్లోరాంట్రానిలిప్రోల్ 18.5% SC (కొరాజెన్) ఎకరాకు 60 మి.లీ (15 లీటర్ల చేతి పంపుకు 4.5 మి.లీ), వేచి ఉండే కాలం: 9 రోజులు. రక్షణ జాగ్రత్తలు: రబ్బరు చేతి తొడుగులు, మాస్క్, కళ్లజోడు ధరించాలి. తేనెటీగలకు హానికరం కావున సాయంత్రం వేళల్లోనే పిచికారీ చేయాలి.",
                sourceCitation = "ICAR-CICR నాగ్‌పూర్ & ప్రొఫెసర్ జయశంకర్ తెలంగాణ వ్యవసాయ విశ్వవిద్యాలయం (PJTSAU) 2024",
                language = "te",
                lastVerifiedDate = "2026-11-09"
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
                lastVerifiedDate = "2026-11-09"
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
                lastVerifiedDate = "2026-11-09"
            )
        )
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
                lastVerifiedDate = "2026-11-09"
            )
        )
        list.add(
            OfflineKnowledgeEntity(
                id = "icar-cot-suck-en",
                collectionId = "KB05",
                crop = "Cotton",
                category = "pest_management",
                title = "Cotton: Whitefly & Leafhopper Symptoms, ETL, IPM & CIBRC Chemical Control",
                content = "Crop & Target: Cotton - Sucking Pest Complex: Whitefly (Bemisia tabaci) and Leafhopper / Jassid (Amrasca biguttula). Growth Stages & Symptoms: Vegetative to early flowering (30-80 DAS). Nymphs and adults suck sap from ventral leaf surfaces. Jassids cause edge yellowing ('hopper burn') curling leaves downward. Whiteflies excrete honeydew leading to sooty mold (Capnodium) coating leaves black and transmit Cotton Leaf Curl Virus (CLCuV). ETL: Whitefly: 6-8 adults/leaf; Jassid: 2 nymphs/leaf or margin yellowing. IPM: 1) Install 15-20 yellow sticky traps/acre at canopy height. 2) Border cropping with 2-4 rows of sorghum/bajra as barrier. 3) Spray 5% NSKE or Neem oil 1500 ppm @ 5 ml/L. 4) Conserve predators (Chrysoperla carnea, ladybird beetles). CIBRC Approved Chemical: Flonicamid 50% WG (Ulala) @ 80 g/acre (approx 6.0 g per 15L backpack pump in 200L water), PHI: 20 days; OR Imidacloprid 17.8% SL (Confidor) @ 50 ml/acre (approx 3.75 ml per 15L pump), PHI: 40 days; OR Diafenthiuron 50% WP (Pegasus) @ 240 g/acre (18.0 g per 15L pump), PHI: 21 days. Safety: Highly toxic to beneficials; never spray during active bee foraging. Wear nitrile gloves and face protection.",
                sourceCitation = "ICAR-CICR Nagpur Technical Bulletin on Sucking Pests & PJTSAU 2024",
                language = "en",
                lastVerifiedDate = "2026-11-09"
            )
        )
        list.add(
            OfflineKnowledgeEntity(
                id = "icar-cot-suck-hi",
                collectionId = "KB05",
                crop = "Cotton",
                category = "pest_management",
                title = "कपास: सफेद मक्खी एवं हरा तेला (जैसिड) लक्षण, ईटीएल, रोकथाम एवं अनुमोदित कीटनाशक",
                content = "फसल व कीट: कपास - रस चूसक कीट: सफेद मक्खी (बेमिसिया टैबेकाई) एवं हरा फुदका (जैसिड)। अवस्थाएं एवं लक्षण: वानस्पतिक एवं कली बनने की अवस्था (30-80 दिन)। पत्तियों की निचली सतह से रस चूसते हैं जिससे पत्तियां किनारों से पीली पड़कर नीचे मुड़ जाती हैं (हॉपर बर्न)। सफेद मक्खी के मीठे चिपचिपे स्राव पर काली फफूंद (काली मसी) जम जाती है और लीफ कर्ल वायरस फैलता है। आर्थिक क्षति स्तर (ETL): 6-8 सफेद मक्खी प्रति पत्ती; 2 हरा तेला प्रति पत्ती। समेकित प्रबंधन (IPM): 1) खेत में 15-20 पीले चिपचिपे ट्रैप प्रति एकड़ लगाएं। 2) खेत की मेड़ों पर 2-4 कतारें ज्वार या बाजरा की बाड़ लगाएं। 3) नीम का तेल (1500 पीपीएम @ 5 मिली/लीटर) या 5% एनएसकेई का छिड़काव करें। CIBRC अनुशंसित रासायनिक उपचार: फ्लोनिकामाइड 50% डब्ल्यूजी (उलाला) 80 ग्राम प्रति एकड़ (लगभग 6.0 ग्राम प्रति 15 लीटर नैपसैक पंप; 200 लीटर पानी में), प्रतीक्षा अवधि (PHI): 20 दिन; अथवा इमिडाक्लोप्रिड 17.8% एसएल (कॉन्फिडोर) 50 मिली प्रति एकड़ (लगभग 3.75 मिली प्रति 15 लीटर पंप), प्रतीक्षा अवधि: 40 दिन; अथवा डायफेंथियूरॉन 50% डब्ल्यूपी 240 ग्राम प्रति एकड़ (18.0 ग्राम प्रति 15 लीटर पंप), प्रतीक्षा अवधि: 21 दिन। सुरक्षा सावधानियां: कीटनाशक छिड़कते समय चेहरे पर मास्क व दस्ताने अवश्य पहनें।",
                sourceCitation = "भाकृअनुप-केंद्रीय कपास अनुसंधान संस्थान (ICAR-CICR) एवं CIBRC 2024",
                language = "hi",
                lastVerifiedDate = "2026-11-09"
            )
        )
        list.add(
            OfflineKnowledgeEntity(
                id = "icar-cot-suck-te",
                collectionId = "KB05",
                crop = "Cotton",
                category = "pest_management",
                title = "ప్రత్తి: తెల్లదోమ మరియు పచ్చదోమ (జాసిడ్స్) లక్షణాలు, ETL, సమగ్ర యాజమాన్యం మరియు రసాయనాలు",
                content = "పంట మరియు సమస్య: ప్రత్తి - రసం పీల్చే పురుగులు: తెల్లదోమ (Whitefly) మరియు పచ్చదోమ (Jassids). దశలు మరియు లక్షణాలు: శాఖీయ మరియు పూత దశ (30-80 రోజులు). ఆకుల అడుగు భాగాన చేరి రసం పీల్చడం వల్ల ఆకుల అంచులు పసుపు రంగులోకి మారి కిందికి ముడుచుకుపోతాయి (హాపర్ బర్న్). తెల్లదోమ విసర్జించే తేనె లాంటి జిగురుపై నల్లటి బూజు (మసి తెగులు) ఏర్పడి ఆకులు నల్లబడతాయి; ఆకుముడుత వైరస్ వ్యాపిస్తుంది. ఆర్థిక నష్ట పరిమితి (ETL): ఆకుకు 6-8 తెల్లదోమలు; పచ్చదోమ ఆకుకు 2 పురుగులు. సమగ్ర నివారణ (IPM): 1) ఎకరాకు 15-20 పసుపు జిగురు అట్టలు అమర్చాలి. 2) ప్రత్తి చుట్టూ 2-4 వరుసల జొన్న లేదా సజ్జ రక్షణ పంటగా వేయాలి. 3) 5% వేప గింజల కషాయం లేదా వేప నూనె 5 మి.లీ/లీటరు పిచికారీ చేయాలి. 4) అక్షింతల పురుగులను రక్షించాలి. CIBRC ఆమోదిత రసాయనాలు: ఫ్లోనికామిడ్ 50% WG (ఉలాలా) ఎకరాకు 80 గ్రా (15 లీటర్ల పంపుకు 6.0 గ్రా, 200 లీటర్ల నీటిలో), వేచి ఉండే కాలం: 20 రోజులు; లేదా ఇమిడాక్లోప్రిడ్ 17.8% SL (కాన్ఫిడార్) ఎకరాకు 50 మి.లీ (15 లీటర్ల పంపుకు 3.75 మి.లీ), వేచి ఉండే కాలం: 40 రోజులు; లేదా డయాఫెంథియురాన్ 50% WP ఎకరాకు 240 గ్రా (15 లీటర్ల పంపుకు 18 గ్రా), వేచి ఉండే కాలం: 21 రోజులు. జాగ్రత్తలు: చేతి తొడుగులు, మాస్క్ తప్పనిసరిగా ధరించాలి.",
                sourceCitation = "ICAR-CICR & ప్రొఫెసర్ జయశంకర్ తెలంగాణ వ్యవసాయ విశ్వవిద్యాలయం (PJTSAU) 2024",
                language = "te",
                lastVerifiedDate = "2026-11-09"
            )
        )
        list.add(
            OfflineKnowledgeEntity(
                id = "icar-gnd-gyp-en",
                collectionId = "KB09",
                crop = "Groundnut",
                category = "fertilizer",
                title = "Groundnut: Gypsum Application at Pegging Stage for Pod Filling",
                content = "Gypsum Importance: Calcium and sulphur are critical during pod development to ensure kernel fullness, oil content, and avoid empty pods ('pops'). Application: Apply Gypsum @ 200 kg/acre at 40-45 DAS (pegging stage) near the root zone and gently earth up. Ensure adequate soil moisture during peg penetration. Calcium nutrition directly hardens pod shells and suppresses Aspergillus aflatoxin contamination.",
                sourceCitation = "ANGRAU & PJTSAU Groundnut Agronomy Guidelines 2024",
                language = "en",
                lastVerifiedDate = "2026-11-09"
            )
        )
        list.add(
            OfflineKnowledgeEntity(
                id = "icar-gnd-gyp-hi",
                collectionId = "KB09",
                crop = "Groundnut",
                category = "fertilizer",
                title = "मूंगफली: खूंटी (पेगिंग) बनते समय जिप्सम प्रबंधन",
                content = "जिप्सम का महत्व: मूंगफली में दानों के भराव, तेल की मात्रा बढ़ाने एवं खोखली फलियों (पॉप्स) की रोकथाम हेतु कैल्शियम व सल्फर जरूरी हैं। प्रयोग विधि: बुवाई के 40-45 दिन बाद पेगिंग अवस्था में 200 किग्रा जिप्सम प्रति एकड़ पौधों की जड़ों के पास डालकर हल्की मिट्टी चढ़ाएं। मिट्टी में पर्याप्त नमी होना आवश्यक है। इससे एफ्लाटॉक्सिन कवक का प्रकोप भी कम होता है।",
                sourceCitation = "ANGRAU & PJTSAU Groundnut Agronomy Guidelines 2024",
                language = "hi",
                lastVerifiedDate = "2026-11-09"
            )
        )
        list.add(
            OfflineKnowledgeEntity(
                id = "icar-gnd-gyp-te",
                collectionId = "KB09",
                crop = "Groundnut",
                category = "fertilizer",
                title = "వేరుశనగ: కాయ ఊరే దశలో జిప్సం యాజమాన్యం మరియు పల్లీల నాణ్యత",
                content = "జిప్సం ప్రాముఖ్యత: వేరుశనగలో కాయలు గట్టిపడటానికి, నూనె శాతం పెరగడానికి మరియు ఊడు కాయలు (పాప్స్) నివారించడానికి కాల్షియం, సల్ఫర్ అత్యంత ఆవశ్యకం. మోతాదు మరియు సమయం: విత్తిన 40-45 రోజులకు (ఊడలు దిగే దశ - Pegging stage) ఎకరాకు 200 కిలోల జిప్సం మొక్కల మొదళ్ల వద్ద వేసి మట్టి ఎగదోయాలి. నేలలో తగినంత తేమ ఉండాలి. జిప్సం వేయడం వల్ల అఫ్లాటాక్సిన్ శిలీంధ్రాల దాడి కూడా తగ్గుతుంది.",
                sourceCitation = "ANGRAU & PJTSAU Groundnut Agronomy Guidelines 2024",
                language = "te",
                lastVerifiedDate = "2026-11-09"
            )
        )
        list.add(
            OfflineKnowledgeEntity(
                id = "icar-gnd-tikka-en",
                collectionId = "KB04",
                crop = "Groundnut",
                category = "disease",
                title = "Groundnut: Tikka Leaf Spot & Collar Rot Integrated Management",
                content = "Tikka Leaf Spot (Cercospora arachidicola) symptoms: Dark circular necrotic leaf spots with chlorotic halos causing severe premature defoliation. Management: 1) Seed treatment: Treat seed kernels with Mancozeb 75% WP @ 3 g/kg seed or Trichoderma viride @ 10 g/kg. 2) Biological control: Foliar spray of Pseudomonas fluorescens @ 10 g/L or 5% NSKE at first symptom appearance. 3) Chemical control: Spray Carbendazim 12% + Mancozeb 63% WP (Saaf) @ 300 g/acre or Hexaconazole 5% EC @ 400 ml/acre diluted in 200 L water. Pre-Harvest Interval (PHI): 30 days.",
                sourceCitation = "ICAR-Directorate of Groundnut Research (DGR) Junagadh & PJTSAU Vyavasaya Panchangam",
                language = "en",
                lastVerifiedDate = "2026-11-09"
            )
        )
        list.add(
            OfflineKnowledgeEntity(
                id = "icar-gnd-tikka-hi",
                collectionId = "KB04",
                crop = "Groundnut",
                category = "disease",
                title = "मूंगफली: टिक्का पत्ती धब्बा रोग एवं कॉलर रॉट प्रबंधन",
                content = "टिक्का रोग लक्षण: पत्तियों पर गहरे भूरे-काले गोल धब्बे बनते हैं और पत्तियां झड़ने लगती हैं। नियंत्रण: 1) बीज उपचार: कार्बेन्डाजिम 2 ग्राम या ट्राइकोडर्मा विरिडी 10 ग्राम प्रति किलो बीज। 2) जैविक उपाय: 5% नीम गिरी अर्क (NSKE) या स्यूडोमोनास 10 ग्राम/लीटर का छिड़काव करें। 3) रासायनिक नियंत्रण: कार्बेन्डाजिम 12% + मैंकोजेब 63% WP (साफ) 300 ग्राम प्रति एकड़ अथवा हेक्साकोनाजोल 5% EC 400 मिली प्रति एकड़ 200 लीटर पानी में मिलाकर छिड़कें। तुड़ाई पूर्व प्रतीक्षा अवधि (PHI) 30 दिन।",
                sourceCitation = "ICAR-Directorate of Groundnut Research (DGR) Junagadh & PJTSAU Vyavasaya Panchangam",
                language = "hi",
                lastVerifiedDate = "2026-11-09"
            )
        )
        list.add(
            OfflineKnowledgeEntity(
                id = "icar-gnd-tikka-te",
                collectionId = "KB04",
                crop = "Groundnut",
                category = "disease",
                title = "వేరుశనగ: టిక్కా ఆకుమచ్చ తెగులు మరియు గ్రీవకుళ్ళు నివారణ",
                content = "టిక్కా ఆకుమచ్చ తెగులు లక్షణాలు: ఆకులపై నల్లటి వలయాకార మచ్చలు ఏర్పడి ఆకులు రాలిపోతాయి. నివారణ: 1) విత్తన శుద్ధి: కిలో విత్తనానికి 3 గ్రాముల మ్యాంకోజెబ్ లేదా కాప్టాన్ తో శుద్ధి చేయాలి. 2) సేంద్రీయ పద్ధతి: 5% వేప గింజల కషాయం (NSKE) లేదా సూడోమోనాస్ ఫ్లోరోసెన్స్ 10 గ్రా/లీటరు పిచికారీ చేయాలి. 3) రసాయన సస్యరక్షణ: తెగులు ప్రారంభ దశలో కార్బెండజిమ్ 12% + మ్యాంకోజెబ్ 63% WP (సాఫ్) ఎకరాకు 300 గ్రాములు లేదా హెక్సాకోనజోల్ 5% EC 400 మి.లీ 200 లీటర్ల నీటిలో కలిపి పిచికారీ చేయాలి. నిరీక్షణ కాలం (PHI) 30 రోజులు.",
                sourceCitation = "ICAR-Directorate of Groundnut Research (DGR) Junagadh & PJTSAU Vyavasaya Panchangam",
                language = "te",
                lastVerifiedDate = "2026-11-09"
            )
        )
        list.add(
            OfflineKnowledgeEntity(
                id = "icar-gnut-gyp-en",
                collectionId = "KB09",
                crop = "Groundnut",
                category = "fertilizer",
                title = "Groundnut: Collar Rot Prevention & Gypsum Pegging Management",
                content = "Crop & Practices: Groundnut - Collar Rot (Aspergillus niger) Prevention & Critical Gypsum Application at Pegging. Collar Rot Symptoms & Control: Seedling emergence (10-30 DAS). Hypocotyl near soil line turns brown, rots, and gets covered with dense black sooty spore masses; seedlings wilt and topple over. Control: Seed treatment with Mancozeb 75% WP @ 3 g/kg seed or Trichoderma harzianum @ 10 g/kg seed; ensure light soil covering (<5 cm depth). Gypsum Application at Pegging Stage (40-45 DAS): Applying Gypsum (Calcium Sulphate) @ 200-250 kg/acre (500 kg/ha) at flowering and peg penetration stage is MANDATORY in groundnut. 1) Direct Calcium Absorption: Groundnut shells and developing kernels directly absorb calcium from the soil in the pod zone (roots cannot transport calcium to pods). 2) Eliminates 'Pops': Calcium deficiency causes 'pops' (empty pods devoid of kernels) and blackened plumules. 3) Sulphur Enrichment: Provides 16-19% Sulphur which boosts oil synthesis and protein content. Method of Application: Broadcast 200-250 kg gypsum/acre uniformly around plant bases at 40-45 DAS, incorporate lightly into top 5 cm soil, and ensure adequate soil moisture. Never delay past 50 DAS.",
                sourceCitation = "ICAR-DGR Junagadh Groundnut Production Technology & PJTSAU 2024",
                language = "en",
                lastVerifiedDate = "2026-11-09"
            )
        )
        list.add(
            OfflineKnowledgeEntity(
                id = "icar-gnut-gyp-hi",
                collectionId = "KB09",
                crop = "Groundnut",
                category = "fertilizer",
                title = "मूंगफली: कॉलर सड़न रोकथाम एवं खूंटी बनते समय जिप्सम प्रयोग का महत्व",
                content = "फसल व तकनीक: मूंगफली - कॉलर सड़न (एस्परगिलस नाइजर) रोकथाम एवं खूंटी (पेग) बनते समय जिप्सम का सही प्रयोग। कॉलर सड़न लक्षण व रोकथाम: बुवाई के 10-30 दिन बाद जमीन की सतह के पास तने पर काले फफूंद के बीजाणु बनते हैं और पौधा गलकर गिर जाता है। रोकथाम: मैंकोजेब 3 ग्राम/किग्रा बीज या ट्राइकोडर्मा 10 ग्राम/किग्रा से बीजोपचार करें; बीज 5 सेमी से गहरा न बोएं। खूंटी (पेगिंग) बनते समय जिप्सम (40-45 दिन): फूल आने और खूंटी जमीन में घुसते समय प्रति एकड़ 200-250 किग्रा जिप्सम डालना अनिवार्य है। 1) कैल्शियम की सीधी पूर्ति: मूंगफली की फली कैल्शियम सीधे मिट्टी से सोखती है (जड़ें फलियों तक कैल्शियम नहीं पहुंचा पातीं)। 2) 'पोप्स' (खोखली फली) की रोकथाम: कैल्शियम की कमी से फलियों में दाने नहीं बनते। 3) सल्फर की उपलब्धता: जिप्सम में मौजूद सल्फर से दानों में तेल का प्रतिशत बढ़ता है। प्रयोग विधि: बुवाई के 40-45 दिन पर पौधों की जड़ों के पास समान रूप से बिखेरें और हल्की गुड़ाई कर मिट्टी चढ़ाएं; मिट्टी में पर्याप्त नमी रखें।",
                sourceCitation = "भाकृअनुप-मूंगफली अनुसंधान निदेशालय (ICAR-DGR) जूनागढ़ एवं PJTSAU 2024",
                language = "hi",
                lastVerifiedDate = "2026-11-09"
            )
        )
        list.add(
            OfflineKnowledgeEntity(
                id = "icar-gnut-gyp-te",
                collectionId = "KB09",
                crop = "Groundnut",
                category = "fertilizer",
                title = "వేరుశనగ: మొలక కుళ్ళు నివారణ మరియు ఊడలు దిగే దశలో జిప్సం ఎరువు వేత ప్రాముఖ్యత",
                content = "పంట మరియు యాజమాన్యం: వేరుశనగ - మొలక కుళ్ళు / మెడ కుళ్ళు (Collar Rot) నివారణ మరియు ఊడలు దిగే దశలో జిప్సం వేత. మొలక కుళ్ళు లక్షణాలు & నివారణ: విత్తిన 10-30 రోజుల్లో మొక్కల మొదళ్ల వద్ద కాండం నల్లబడి కుళ్ళిపోతుంది; నల్లటి బూజు ఏర్పడి మొక్కలు చనిపోతాయి. నివారణ: కిలో విత్తనానికి మ్యాంకోజెబ్ 3 గ్రా లేదా ట్రైకోడెర్మా 10 గ్రా కలిపి విత్తనశుద్ధి చేయాలి; విత్తనాన్ని మరీ లోతుగా (>5 సెం.మీ) నాటకూడదు. ఊడలు దిగే దశలో జిప్సం వేత (40-45 రోజులు): పూత పూసి ఊడలు నేలలోకి దిగే దశలో ఎకరాకు 200-250 కిలోల జిప్సం వేయడం అత్యంత ముఖ్యం. 1) కాయలు కాల్షియంను నేరుగా భూమి నుండే గ్రహిస్తాయి (వేర్లు కాయలకు కాల్షియంను చేరవేయలేవు). 2) జిప్సం వేయకపోతే తాలు కాయలు (గింజ లేని పొల్లు కాయలు) ఏర్పడతాయి. 3) జిప్సంలోని గంధకం (సల్ఫర్) నూనె శాతాన్ని గణనీయంగా పెంచుతుంది. వేసే పద్ధతి: విత్తిన 40-45 రోజులకు మొక్కల మొదళ్ల వద్ద జిప్సం వెదజల్లి తేలికగా మట్టిని ఎగదోయాలి; నేలలో తగినంత తేమ ఉండేలా చూడాలి.",
                sourceCitation = "ICAR-DGR జునాగఢ్ & ప్రొఫెసర్ జయశంకర్ తెలంగాణ వ్యవసాయ విశ్వవిద్యాలయం (PJTSAU) 2024",
                language = "te",
                lastVerifiedDate = "2026-11-09"
            )
        )
        list.add(
            OfflineKnowledgeEntity(
                id = "icar-gnut-tikka-en",
                collectionId = "KB04",
                crop = "Groundnut",
                category = "disease",
                title = "Groundnut: Tikka Leaf Spot (Early & Late Cercospora) Symptoms, IPM & Fungicides",
                content = "Crop & Disease: Groundnut / Peanut (Arachis hypogaea) - Tikka Disease: Early Leaf Spot (Cercospora arachidicola) & Late Leaf Spot (Phaeoisariopsis personata). Growth Stages & Symptoms: Vegetative to pod maturation (40-95 DAS). Early spot: Circular reddish-brown lesions on upper leaf surface with a prominent yellow chlorotic halo. Late spot: Smaller, dark brown to carbon-black circular lesions mostly on lower leaf surface without yellow halo, appearing dusty with fungal conidia; causes extensive premature defoliation leaving bare stems, reducing pod yield by 40-50% and degrading haulm quality. Favorable Triggers: Temperatures 25-30°C, prolonged relative humidity (>85%), intermittent rainy spells and heavy dews. IPM: 1) Deep summer ploughing to bury pathogen-infected crop residues. 2) Crop rotation with cereals (pearl millet, maize, sorghum). 3) Seed treatment with Trichoderma viride @ 10 g/kg seed or Mancozeb @ 3 g/kg seed. 4) Foliar spray of 5% NSKE at initial leaf spot appearance. CIBRC Approved Chemical Control: Carbendazim 12% + Mancozeb 63% WP (Saaf 75% WP) @ 400 g/acre (approx 30.0 g per 15L backpack pump in 200L water), Pre-Harvest Interval (PHI): 30 days; OR Tebuconazole 25.9% EC (Folicur) @ 250 ml/acre (approx 18.5 ml per 15L pump), PHI: 21 days; OR Hexaconazole 5% SC (Contaf) @ 400 ml/acre (approx 30.0 ml per 15L pump), PHI: 30 days. Spray at first sign of leaf spots and repeat after 15 days. Safety & PPE: Wear chemical-resistant gloves and face mask.",
                sourceCitation = "ICAR-Directorate of Groundnut Research (DGR) Junagadh & PJTSAU 2024",
                language = "en",
                lastVerifiedDate = "2026-11-09"
            )
        )
        list.add(
            OfflineKnowledgeEntity(
                id = "icar-gnut-tikka-hi",
                collectionId = "KB04",
                crop = "Groundnut",
                category = "disease",
                title = "मूंगफली: टिक्का रोग (अगेती एवं पछेती पर्ण चित्ती) लक्षण, रोकथाम एवं फफूंदनाशक",
                content = "फसल व रोग: मूंगफली - टिक्का रोग (अगेती एवं पछेती पर्ण चित्ती - सर्कोस्पोरा)। अवस्थाएं एवं लक्षण: वानस्पतिक से फली पकने तक (40-95 दिन)। अगेती पत्ती धब्बा में पत्तियों की ऊपरी सतह पर कत्थई गोल धब्बे बनते हैं जिनके चारों ओर स्पष्ट पीला घेरा होता है। पछेती पत्ती धब्बा में पत्तियों की निचली सतह पर काले गोल धब्बे बनते हैं (पीला घेरा नहीं होता)। पत्तियां समय से पहले झड़ जाती हैं जिससे पैदावार 40-50% घट जाती है। अनुकूल मौसम: तापमान 25-30°C, 85% से अधिक आर्द्रता एवं लगातार बारिश। समेकित प्रबंधन (IPM): 1) गर्मियों में गहरी जुताई करें। 2) ज्वार/बाजरा/मक्का के साथ फसल चक्र अपनाएं। 3) ट्राइकोडर्मा विरिडी 10 ग्राम/किग्रा बीज या मैंकोजेब 3 ग्राम/किग्रा से बीजोपचार करें। 4) 5% नीम बीज अर्क का छिड़काव करें। CIBRC अनुमोदित रासायनिक नियंत्रण: कार्बेन्डाजिम 12% + मैंकोजेब 63% डब्ल्यूपी (साफ 75% डब्ल्यूपी) 400 ग्राम प्रति एकड़ (लगभग 30 ग्राम प्रति 15 लीटर नैपसैक पंप; 200 लीटर पानी में), प्रतीक्षा अवधि (PHI): 30 दिन; अथवा टेबुकोनाजोल 25.9% ईसी 250 मिली प्रति एकड़ (लगभग 18.5 मिली प्रति 15 लीटर पंप), प्रतीक्षा अवधि: 21 दिन; अथवा हेक्साकोनाजोल 5% एससी 400 मिली प्रति एकड़ (30 मिली प्रति 15 लीटर पंप), प्रतीक्षा अवधि: 30 दिन। सुरक्षा सावधानियां: छिड़काव के समय दस्ताने व मास्क का प्रयोग करें।",
                sourceCitation = "भाकृअनुप-मूंगफली अनुसंधान निदेशालय (ICAR-DGR) जूनागढ़ एवं CIBRC 2024",
                language = "hi",
                lastVerifiedDate = "2026-11-09"
            )
        )
        list.add(
            OfflineKnowledgeEntity(
                id = "icar-gnut-tikka-te",
                collectionId = "KB04",
                crop = "Groundnut",
                category = "disease",
                title = "వేరుశనగ: టిక్కా ఆకుమచ్చ తెగులు (ముందస్తు మరియు ఆలస్యపు మచ్చలు) గుర్తింపు మరియు నివారణ",
                content = "పంట మరియు వ్యాధి: వేరుశనగ - టిక్కా ఆకుమచ్చ తెగులు (Tikka Disease - సెర్కోస్పోరా). దశలు మరియు లక్షణాలు: శాఖీయ దశ నుండి కాయ ఊరే దశ వరకు (40-95 రోజులు). ముందస్తు ఆకుమచ్చ: ఆకుల పైభాగంలో గుండ్రటి గోధుమ రంగు మచ్చలు ఏర్పడి చుట్టూ స్పష్టమైన పసుపు రంగు వలయం ఉంటుంది. ఆలస్యపు ఆకుమచ్చ: ఆకుల అడుగు భాగాన ముదురు గోధుమ లేదా నల్లటి మచ్చలు ఏర్పడి పసుపు వలయం ఉండదు; ఆకులు విపరీతంగా రాలిపోయి కేవలం కాండాలు మాత్రమే మిగులుతాయి. దీనివల్ల కాయ దిగుబడి 40-50% పడిపోతుంది. అనుకూల వాతావరణం: ఉష్ణోగ్రత 25-30°C, 85% పైగా గాలిలో తేమ, జల్లులు పడటం. సమగ్ర నివారణ (IPM): 1) లోతు వేసవి దుక్కులు చేయాలి. 2) సజ్జ లేదా మొక్కజొన్నతో పంట మార్పిడి చేయాలి. 3) విత్తనశుద్ధి: కిలో విత్తనానికి ట్రైకోడెర్మా విరిడి 10 గ్రా లేదా మ్యాంకోజెబ్ 3 గ్రా తో శుద్ధి చేయాలి. 4) ప్రారంభంలో 5% వేప గింజల కషాయం పిచికారీ చేయాలి. CIBRC ఆమోదిత రసాయన నివారణ: కార్బండజిమ్ 12% + మ్యాంకోజెబ్ 63% WP (సాఫ్ 75% WP) ఎకరాకు 400 గ్రా (15 లీటర్ల చేతి పంపుకు 30 గ్రా; 200 లీటర్ల నీటిలో), వేచి ఉండే కాలం (PHI): 30 రోజులు; లేదా టెబుకొనజోల్ 25.9% EC ఎకరాకు 250 మి.లీ (15 లీటర్ల పంపుకు 18.5 మి.లీ), వేచి ఉండే కాలం: 21 రోజులు; లేదా హెక్సాకొనజోల్ 5% SC ఎకరాకు 400 మి.లీ (15 లీటర్ల పంపుకు 30 మి.లీ), వేచి ఉండే కాలం: 30 రోజులు. జాగ్రత్తలు: చేతి తొడుగులు మరియు మాస్క్ ధరించాలి.",
                sourceCitation = "ICAR-DGR జునాగఢ్ & ప్రొఫెసర్ జయశంకర్ తెలంగాణ వ్యవసాయ విశ్వవిద్యాలయం (PJTSAU) 2024",
                language = "te",
                lastVerifiedDate = "2026-11-09"
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
                lastVerifiedDate = "2026-11-09"
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
                lastVerifiedDate = "2026-11-09"
            )
        )
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
                lastVerifiedDate = "2026-11-09"
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
                lastVerifiedDate = "2026-11-09"
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
                lastVerifiedDate = "2026-11-09"
            )
        )
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
                lastVerifiedDate = "2026-11-09"
            )
        )
        list.add(
            OfflineKnowledgeEntity(
                id = "icar-mze-tlb-en",
                collectionId = "KB04",
                crop = "Maize",
                category = "disease",
                title = "Maize: Turcicum Leaf Blight (TLB) Symptoms, Weather Triggers & Fungicides",
                content = "Crop & Disease: Maize (Zea mays) - Turcicum Leaf Blight (TLB / Northern Corn Leaf Blight - Exserohilum turcicum). Growth Stages & Symptoms: Knee-high stage to tasseling and silking (40-75 DAS). Long elliptical spindle-shaped greyish-green lesions (5-15 cm long) parallel to leaf veins; lesions expand and turn straw-tan colored with dark olivaceous sooty fungal spores, giving the field a burnt appearance and reducing grain yield by 25-50%. Favorable Triggers: Temperatures 18-27°C, high relative humidity (>90%), prolonged heavy dew and overcast weather. IPM: 1) Grow resistant/tolerant single cross hybrids (e.g. DHM 117, DHM 121, PMH 1). 2) Crop rotation with non-graminaceous crops (pulses, soybean). 3) Deep ploughing to bury crop stubbles. CIBRC Approved Chemical Control: Azoxystrobin 18.2% + Difenoconazole 11.4% SC (Amistar Top) @ 200 ml/acre (approx 15.0 ml per 15L backpack pump in 200L water), Pre-Harvest Interval (PHI): 20 days; OR Mancozeb 75% WP @ 600 g/acre (approx 45 g per 15L pump), PHI: 14 days. Apply at first appearance of spindle lesions and repeat after 15 days if cloudy humid weather persists. PPE: Wear gloves, protective glasses, and respirator mask.",
                sourceCitation = "ICAR-Indian Institute of Maize Research (IIMR) Ludhiana & PJTSAU 2024",
                language = "en",
                lastVerifiedDate = "2026-11-09"
            )
        )
        list.add(
            OfflineKnowledgeEntity(
                id = "icar-mze-tlb-hi",
                collectionId = "KB04",
                crop = "Maize",
                category = "disease",
                title = "मक्का: टर्सिकम पर्ण झुलसा (पत्ती झुलसा रोग) लक्षण, अनुकूल मौसम एवं कवकनाशी",
                content = "फसल व रोग: मक्का - टर्सिकम पत्ती झुलसा (नॉर्दर्न कॉर्न लीफ ब्लाइट - एक्सरोहिलम टर्सिकम)। अवस्थाएं एवं लक्षण: घुटने की ऊंचाई से भुट्टा निकलने तक (40-75 दिन)। पत्तियों पर नसों के समानांतर 5-15 सेमी लंबे नाव या तकली के आकार के भूरे-हरे धब्बे बनते हैं। धब्बे सूखकर पुआल जैसे कत्थई हो जाते हैं और उन पर फफूंद के काले बीजाणु दिखते हैं; खेत झुलसा हुआ दिखता है और 25-50% उपज घट जाती है। अनुकूल मौसम: तापमान 18-27°C, 90% से अधिक आर्द्रता एवं लगातार ओस। समेकित प्रबंधन (IPM): 1) सहनशील संकर किस्में (डीएचएम 117, डीएचएम 121) लगाएं। 2) दलहनी फसलों के साथ फसल चक्र अपनाएं। 3) फसल अवशेषों को मिट्टी में दबाएं। CIBRC अनुमोदित रासायनिक नियंत्रण: एजोक्सीस्ट्रोबिन 18.2% + डाइफेनोकोनाजोल 11.4% एससी (एमिस्टार टॉप) 200 मिली प्रति एकड़ (लगभग 15 मिली प्रति 15 लीटर नैपसैक पंप; 200 लीटर पानी में), प्रतीक्षा अवधि (PHI): 20 दिन; अथवा मैंकोजेब 75% डब्ल्यूपी 600 ग्राम प्रति एकड़ (लगभग 45 ग्राम प्रति 15 लीटर पंप), प्रतीक्षा अवधि: 14 दिन। सुरक्षा सावधानियां: दस्ताने और मास्क का उपयोग करें।",
                sourceCitation = "भाकृअनुप-भारतीय मक्का अनुसंधान संस्थान (ICAR-IIMR) लुधियाना एवं CIBRC 2024",
                language = "hi",
                lastVerifiedDate = "2026-11-09"
            )
        )
        list.add(
            OfflineKnowledgeEntity(
                id = "icar-mze-tlb-te",
                collectionId = "KB04",
                crop = "Maize",
                category = "disease",
                title = "మొక్కజొన్న: ఆకు ఎండు తెగులు (టర్సికమ్ లీఫ్ బ్లైట్) గుర్తింపు, వాతావరణం మరియు నివారణ",
                content = "పంట మరియు వ్యాధి: మొక్కజొన్న - ఆకు ఎండు తెగులు (Turcicum Leaf Blight - ఎక్సరోహైలమ్ టర్సికమ్). దశలు మరియు లక్షణాలు: మోకాలి లోతు దశ నుండి కంకి దశ వరకు (40-75 రోజులు). ఆకులపై ఈనెలకు సమాంతరంగా పొడవైన కండె ఆకారపు (5-15 సెం.మీ) బూడిద-ఆకుపచ్చ మచ్చలు ఏర్పడతాయి. మచ్చలు పెద్దవై ఎండుగడ్డి రంగులోకి మారి నల్లటి శిలీంధ్ర పుప్పొడితో నిండిపోతాయి; మొత్తం ఆకులన్నీ కాలిపోయినట్లు ఎండిపోయి దిగుబడి 25-50% పడిపోతుంది. అనుకూల వాతావరణం: ఉష్ణోగ్రత 18-27°C, 90% పైగా గాలిలో తేమ, దట్టమైన పొగమంచు. సమగ్ర నివారణ (IPM): 1) తెగులును తట్టుకునే హైబ్రిడ్ రకాలు (DHM 117, DHM 121) సాగు చేయాలి. 2) సోయాబీన్ లేదా పప్పుధాన్యాలతో పంట మార్పిడి చేయాలి. 3) మునుపటి పంట మోళ్లను లోతుగా దున్నాలి. CIBRC ఆమోదిత రసాయన నివారణ: అజాక్సిస్ట్రోబిన్ 18.2% + డైఫెనోకొనజోల్ 11.4% SC (అమిస్టార్ టాప్) ఎకరాకు 200 మి.లీ (15 లీటర్ల చేతి పంపుకు 15 మి.లీ; 200 లీటర్ల నీటిలో), వేచి ఉండే కాలం (PHI): 20 రోజులు; లేదా మ్యాంకోజెబ్ 75% WP ఎకరాకు 600 గ్రా (15 లీటర్ల పంపుకు 45 గ్రా), వేచి ఉండే కాలం: 14 రోజులు. జాగ్రత్తలు: చేతి తొడుగులు మరియు మాస్క్ ధరించాలి.",
                sourceCitation = "ICAR-IIMR లూధియానా & ప్రొఫెసర్ జయశంకర్ తెలంగాణ వ్యవసాయ విశ్వవిద్యాలయం (PJTSAU) 2024",
                language = "te",
                lastVerifiedDate = "2026-11-09"
            )
        )
        list.add(
            OfflineKnowledgeEntity(
                id = "icar-oni-blotch-en",
                collectionId = "KB04",
                crop = "Onion",
                category = "disease",
                title = "Onion: Purple Blotch & Thrips Integrated Control Strategy",
                content = "Onion Protection Protocol: 1) Onion Thrips (Thrips tabaci): Vector for iris yellow spot virus. Erect 20 blue/yellow sticky traps/acre. Spray Fipronil 5% SC @ 400 ml/acre when counts exceed 15-20 thrips/plant. 2) Purple Blotch (Alternaria porri): Sunken purple-brown elliptical lesions on foliage. Spray Mancozeb 75% WP @ 600 g/acre or Tebuconazole 25.9% EC @ 250 ml/acre diluted in 200 L water with wetting sticker (0.5 ml/L). Pre-Harvest Interval (PHI): 15 days.",
                sourceCitation = "ICAR-Directorate of Onion and Garlic Research (DOGR) Pune & PJTSAU",
                language = "en",
                lastVerifiedDate = "2026-11-09"
            )
        )
        list.add(
            OfflineKnowledgeEntity(
                id = "icar-oni-blotch-hi",
                collectionId = "KB04",
                crop = "Onion",
                category = "disease",
                title = "प्याज: बैंगनी धब्बा (पर्पल ब्लॉच) रोग एवं थ्रिप्स प्रबंधन",
                content = "प्याज संरक्षण विधि: 1) थ्रिप्स (चूर्दा): पत्तियों से रस चूसते हैं जिससे चांदी जैसे सफेद धब्बे बनते हैं। 20 नीले व पीले चिपचिपे जाल लगाएं। फिप्रोनिल 5% एससी 400 मिली प्रति एकड़ छिड़कें। 2) बैंगनी धब्बा रोग: पत्तियों पर अंडाकार बैंगनी-भूरे धब्बे बनते हैं। नियंत्रण हेतु मैंकोजेब 75% डब्ल्यूपी 600 ग्राम या टेबुकोनाजोल 25.9% ईसी 250 मिली प्रति एकड़ 200 लीटर पानी में चिपकने वाले घोल के साथ मिलाकर छिड़कें। प्रतीक्षा अवधि 15 दिन।",
                sourceCitation = "ICAR-Directorate of Onion and Garlic Research (DOGR) Pune & PJTSAU",
                language = "hi",
                lastVerifiedDate = "2026-11-09"
            )
        )
        list.add(
            OfflineKnowledgeEntity(
                id = "icar-oni-blotch-te",
                collectionId = "KB04",
                crop = "Onion",
                category = "disease",
                title = "ఉల్లి: ఊదా మచ్చ తెగులు (పర్పుల్ బ్లాచ్) మరియు తామర పురుగుల నివారణ",
                content = "ఉల్లి సస్యరక్షణ: 1) తామర పురుగులు (Thrips): ఆకుల రసం పీల్చి తెల్లటి మచ్చలు చేస్తాయి. నివారణకు ఎకరాకు 20 పసుపు, నీలి జిగురు అట్టలు పెట్టాలి. ఫిప్రోనిల్ 5% SC ఎకరాకు 400 మి.లీ పిచికారీ చేయాలి. 2) ఊదా మచ్చ తెగులు (Purple blotch): ఆకులపై ఊదా రంగు మచ్చలు ఏర్పడి ఆకులు ఎండిపోతాయి. నివారణ: మాంకోజెబ్ 75% WP ఎకరాకు 600 గ్రాములు లేదా టెబుకొనజోల్ 25.9% EC ఎకరాకు 250 మి.లీ 200 లీటర్ల నీటిలో జిగురు (సందర్శిని) కలిపి పిచికారీ చేయాలి. నిరీక్షణ కాలం 15 రోజులు.",
                sourceCitation = "ICAR-Directorate of Onion and Garlic Research (DOGR) Pune & PJTSAU",
                language = "te",
                lastVerifiedDate = "2026-11-09"
            )
        )
        list.add(
            OfflineKnowledgeEntity(
                id = "icar-org-beej-en",
                collectionId = "KB06",
                crop = "Organic",
                category = "cultivation",
                title = "Organic Protection: Beejamrit Seed Treatment & 5% Neem Kernel Extract (NSKE)",
                content = "1) Beejamrit Seed Inoculant: 20 L water, 5 kg desi cow dung tied in cloth bundle, 5 L cow urine, 50 g slaked lime, handful undisturbed soil. Steep overnight, squeeze essence, coat seeds gently and dry under shade. Imparts resistance to seed-borne pathogens. 2) Neem Seed Kernel Extract (NSKE 5%): Soak 5 kg crushed dry neem seed kernels in 100 L water overnight. Filter through muslin cloth, add 100 g soap powder as surfactant, and spray immediately. Acts as potent oviposition deterrent, antifeedant, and growth disruptor against aphids, whiteflies, and early lepidopteran caterpillars.",
                sourceCitation = "National Institute of Plant Health Management (NIPHM) Hyderabad & NCONF",
                language = "en",
                lastVerifiedDate = "2026-11-09"
            )
        )
        list.add(
            OfflineKnowledgeEntity(
                id = "icar-org-beej-hi",
                collectionId = "KB06",
                crop = "Organic",
                category = "cultivation",
                title = "प्राकृतिक रक्षा: बीजामृत से बीज शोधन एवं 5% नीम गिरी काढ़ा (NSKE) निर्माण",
                content = "1) बीजामृत बीज उपचार: 20 लीटर पानी, 5 किग्रा देशी गाय का गोबर पोटली में, 5 लीटर गोमूत्र, 50 ग्राम चूना, मुट्ठी भर उपजाऊ मिट्टी। रातभर भिगोकर सुबह घोल बनाएं। बीजों पर छिड़ककर छाया में सुखाएं। बीज जनित रोगों से सुरक्षा मिलती है। 2) नीम गिरी अर्क (NSKE 5%): 5 किग्रा कुटी हुई सूखी नीम की निंबोली को 100 लीटर पानी में रातभर भिगोएं। मलमल के कपड़े से छानकर 100 ग्राम कपड़े धोने का पाउडर मिलाएं और तुरंत स्प्रे करें। रसचूसक कीटों एवं छोटी सुंडियों के विरुद्ध उत्तम प्राकृतिक कीटनाशक है।",
                sourceCitation = "National Institute of Plant Health Management (NIPHM) Hyderabad & NCONF",
                language = "hi",
                lastVerifiedDate = "2026-11-09"
            )
        )
        list.add(
            OfflineKnowledgeEntity(
                id = "icar-org-beej-te",
                collectionId = "KB06",
                crop = "Organic",
                category = "cultivation",
                title = "సేంద్రియ విత్తన శుద్ధి: బీజామృతం మరియు 5% వేప గింజల కషాయం (NSKE) తయారీ",
                content = "1) బీజామృతం తయారీ: 20 లీటర్ల నీరు, 5 కిలోల ఆవు పేడ, 5 లీటర్ల ఆవు మూత్రం, 50 గ్రాముల సున్నం, గుప్పెడు పుట్టమట్టి. రాత్రంతా నానబెట్టి, ఉదయం బాగా కలిపి విత్తనాలకు పట్టించి నీడలో ఆరబెట్టాలి. ఇది మొలక శాతాన్ని పెంచి నేల ద్వారా వ్యాపించే తెగుళ్లను అరికడుతుంది. 2) వేప గింజల కషాయం (NSKE 5%): 5 కిలోల బాగా ఎండిన వేప గింజల పిండిని 100 లీటర్ల నీటిలో రాత్రంతా నానబెట్టి, ఉదయం వడబోసి 100 గ్రాముల సబ్బు పొడి కలిపి పిచికారీ చేయాలి. రసం పీల్చే పురుగులు, ఆకుతొలుచు పురుగుల గుడ్లు, చిన్న లద్దె పురుగులను సమర్థవంతంగా నివారిస్తుంది.",
                sourceCitation = "National Institute of Plant Health Management (NIPHM) Hyderabad & NCONF",
                language = "te",
                lastVerifiedDate = "2026-11-09"
            )
        )
        list.add(
            OfflineKnowledgeEntity(
                id = "icar-org-jeevamrit-en",
                collectionId = "KB06",
                crop = "Organic",
                category = "cultivation",
                title = "Natural Farming: Liquid Jeevamrit Preparation Protocol & Application",
                content = "Liquid Jeevamrit Formulation (For 1 Acre): 1) Ingredients: 200 L water, 10 kg fresh indigenous cow dung, 5-10 L indigenous cow urine, 2 kg organic jaggery, 2 kg pulse flour (gram/pigeonpea), handful of uncultivated undisturbed forest/bund soil. 2) Preparation: Mix thoroughly in a barrel using a wooden stick in clockwise direction. Cover with jute sack and ferment in shade for 48-72 hours, stirring twice daily. 3) Field Application: Feed 200 L/acre through irrigation water (flood or venturi) or foliar spray filtered at 10-20% dilution. Multiplies beneficial soil rhizosphere microflora exponentially.",
                sourceCitation = "National Centre of Organic and Natural Farming (NCONF) Ghaziabad & PJTSAU Natural Farming Guidelines",
                language = "en",
                lastVerifiedDate = "2026-11-09"
            )
        )
        list.add(
            OfflineKnowledgeEntity(
                id = "icar-org-jeevamrit-hi",
                collectionId = "KB06",
                crop = "Organic",
                category = "cultivation",
                title = "प्राकृतिक खेती: देशी गाय आधारित जीवामृत निर्माण विधि एवं प्रयोग",
                content = "जीवामृत निर्माण (1 एकड़ हेतु): 1) सामग्री: 200 लीटर पानी, 10 किग्रा ताजा देशी गाय का गोबर, 5-10 लीटर गोमूत्र, 2 किग्रा पुराना गुड़, 2 किग्रा बेसन (चना/उड़द), मुट्ठी भर मेड़ या बरगद के नीचे की सजीव मिट्टी। 2) विधि: प्लास्टिक ड्रम में सभी सामग्री डालकर लकड़ी के डंडे से दक्षिणावर्त घुमाएं। बोरी से ढककर 48-72 घंटे छाया में किण्वित करें, सुबह-शाम 2 मिनट चलाएं। 3) उपयोग: सिंचाई जल के साथ 200 लीटर प्रति एकड़ दें या 10-20% घोल बनाकर फसलों पर छिड़कें। भूमि में सूक्ष्मजीवों की संख्या तेजी से बढ़ती है।",
                sourceCitation = "National Centre of Organic and Natural Farming (NCONF) Ghaziabad & PJTSAU Natural Farming Guidelines",
                language = "hi",
                lastVerifiedDate = "2026-11-09"
            )
        )
        list.add(
            OfflineKnowledgeEntity(
                id = "icar-org-jeevamrit-te",
                collectionId = "KB06",
                crop = "Organic",
                category = "cultivation",
                title = "సేంద్రియ వ్యవసాయం: దేశీ ఆవు ఆధారిత ఘన/ద్రవ జీవామృతం తయారీ విధానం",
                content = "ద్రవ జీవామృతం తయారీ (1 ఎకరాకు): 1) కావలసిన పదార్థాలు: 200 లీటర్ల నీరు, 10 కిలోల తాజా దేశీ ఆవు పేడ, 5-10 లీటర్ల దేశీ ఆవు మూత్రం, 2 కిలోల నల్ల బెల్లం, 2 కిలోల పప్పు పిండి (శనగ/మినుము), గుప్పెడు పొలం గట్టు లేదా అడవి మట్టి. 2) తయారీ విధానం: డ్రమ్ములో అన్నింటిని వేసి కర్రతో గడియారం ముల్లు తిరిగే దిశలో కలియదిప్పాలి. గోనెసంచితో కప్పి నీడలో 48-72 గంటలు పులియబెట్టాలి. రోజుకు రెండుసార్లు కలపాలి. 3) వినియోగం: నీటి పారుదల ద్వారా ఎకరాకు 200 లీటర్లు అందించాలి లేదా 10-20% ద్రావణాన్ని పంటలపై పిచికారీ చేయాలి. నేలలో ఉపయోగకర సూక్ష్మజీవుల సంఖ్యను విశేషంగా పెంచుతుంది.",
                sourceCitation = "National Centre of Organic and Natural Farming (NCONF) Ghaziabad & PJTSAU Natural Farming Guidelines",
                language = "te",
                lastVerifiedDate = "2026-11-09"
            )
        )
        list.add(
            OfflineKnowledgeEntity(
                id = "icar-rdg-borer-en",
                collectionId = "KB04",
                crop = "Red Gram",
                category = "pest_management",
                title = "Red Gram: Gram Pod Borer (Helicoverpa armigera) IPM Strategy",
                content = "Gram Pod Borer IPM Strategy: 1) Bird perches: Install 15-20 T-shaped wooden bird perches per acre to promote natural avian predation. 2) Pheromone traps: Erect 4-5 Helilure traps per acre for ETL monitoring. 3) Biological control: Spray HaNPV @ 200 LE/acre with 0.1% jaggery or 5% NSKE at flower initiation. 4) CIBRC Approved Chemicals: When larval counts exceed 1 larva/meter row, spray Chlorantraniliprole 18.5% SC @ 60 ml/acre or Emamectin Benzoate 5% SG @ 100 g/acre diluted in 200 L water. Pre-Harvest Interval (PHI): 15 days.",
                sourceCitation = "ICAR-IIPR Kanpur & NIPHM Pulses IPM Protocol",
                language = "en",
                lastVerifiedDate = "2026-11-09"
            )
        )
        list.add(
            OfflineKnowledgeEntity(
                id = "icar-rdg-borer-hi",
                collectionId = "KB04",
                crop = "Red Gram",
                category = "pest_management",
                title = "अरहर: फली छेदक (हेलिकोवर्पा) एकीकृत कीट प्रबंधन (IPM)",
                content = "फली छेदक रोकथाम: 1) पक्षी मचान: प्रति एकड़ 15-20 टी-आकार की खूंटियां लगाएं ताकि पक्षी सुंडियों को खा सकें। 2) फेरोमोन ट्रैप: 4-5 हेलील्यूर ट्रैप लगाएं। 3) जैविक उपचार: 5% नीम गिरी काढ़ा (NSKE) या HaNPV वायरस 200 मिली प्रति एकड़ शाम को छिड़कें। 4) रासायनिक छिड़काव: प्रकोप अधिक होने पर क्लोरेंट्रानिलिप्रोल 18.5% एससी 60 मिली या एमामेक्टिन बेंजोएट 5% एसजी 100 ग्राम प्रति एकड़ 200 लीटर पानी में मिलाकर छिड़कें। प्रतीक्षा अवधि 15 दिन।",
                sourceCitation = "ICAR-IIPR Kanpur & NIPHM Pulses IPM Protocol",
                language = "hi",
                lastVerifiedDate = "2026-11-09"
            )
        )
        list.add(
            OfflineKnowledgeEntity(
                id = "icar-rdg-borer-te",
                collectionId = "KB04",
                crop = "Red Gram",
                category = "pest_management",
                title = "కంది: కాయ తొలుచు పురుగు (హెలికోనెర్పా) సమగ్ర సస్యరక్షణ",
                content = "కాయ తొలుచు పురుగు నివారణ: 1) ఎర పంటలు: కంది చుట్టూ బంతి లేదా జొన్న పంటలు వేయాలి. 2) పక్షి స్థావరాలు: ఎకరాకు 15-20 పక్షి స్థావరాలు (T-ఆకారపు కర్రలు) ఏర్పాటు చేయాలి. 3) లింగాకర్షక బుట్టలు: ఎకరాకు 4-5 హెలిలూర్ బుట్టలు అమర్చాలి. 4) జీవ నియంత్రణ: హెచ్.ఎన్.పి.వి (HaNPV) వైరస్ ద్రావణం 200 మి.లీ లేదా 5% వేప గింజల కషాయం పిచికారీ చేయాలి. 5) రసాయన చికిత్స: పురుగు ఉధృతి ఎక్కువగా ఉంటే క్లోరాంట్రానిలిప్రోల్ 18.5% SC ఎకరాకు 60 మి.లీ లేదా ఎమామెక్టిన్ బెంజోయేట్ 5% SG ఎకరాకు 100 గ్రాములు 200 లీటర్ల నీటిలో కలిపి పిచికారీ చేయాలి. నిరీక్షణ కాలం 15 రోజులు.",
                sourceCitation = "ICAR-IIPR Kanpur & NIPHM Pulses IPM Protocol",
                language = "te",
                lastVerifiedDate = "2026-11-09"
            )
        )
        list.add(
            OfflineKnowledgeEntity(
                id = "icar-rdg-wilt-en",
                collectionId = "KB04",
                crop = "Red Gram",
                category = "disease",
                title = "Red Gram: Fusarium Wilt & Root Rot Integrated Management",
                content = "Fusarium Wilt (Fusarium udum) symptoms: Drooping of leaves, sudden drying of plants, and brown-black discoloration of xylem vessels inside the stem. Management: 1) Seed treatment: Inoculate seed with Trichoderma viride @ 10 g/kg seed or Carbendazim 50% WP @ 2 g/kg seed. 2) Cultural control: Rotate with sorghum, pearl millet, or maize for 3 years; maintain good drainage. 3) Soil amendment: Enrich 2 tonnes FYM/acre with 2 kg Trichoderma formulation and incorporate during final land preparation.",
                sourceCitation = "ICAR-Indian Institute of Pulses Research (IIPR) Kanpur & PJTSAU Advisory",
                language = "en",
                lastVerifiedDate = "2026-11-09"
            )
        )
        list.add(
            OfflineKnowledgeEntity(
                id = "icar-rdg-wilt-hi",
                collectionId = "KB04",
                crop = "Red Gram",
                category = "disease",
                title = "अरहर/तुअर: उकठा रोग (मुरझान) एवं जड़ सड़न प्रबंधन",
                content = "उकठा रोग (फ्यूजेरियम विल्ट) लक्षण: पत्तियां पीली पड़कर लटक जाती हैं और पौधा सूख जाता है। तने को चीरने पर अंदर काली धारियां दिखती हैं। नियंत्रण: 1) बीज उपचार: 10 ग्राम ट्राइकोडर्मा विरिडी या 2 ग्राम कार्बेन्डाजिम प्रति किलो बीज। 2) फसल चक्र: ज्वार या मक्का के साथ 3 वर्षीय फसल चक्र अपनाएं। 3) मृदा उपचार: प्रति एकड़ 2 क्विंटल गोबर की खाद में 2 किग्रा ट्राइकोडर्मा मिलाकर अंतिम जुताई में मिट्टी में मिलाएं।",
                sourceCitation = "ICAR-Indian Institute of Pulses Research (IIPR) Kanpur & PJTSAU Advisory",
                language = "hi",
                lastVerifiedDate = "2026-11-09"
            )
        )
        list.add(
            OfflineKnowledgeEntity(
                id = "icar-rdg-wilt-te",
                collectionId = "KB04",
                crop = "Red Gram",
                category = "disease",
                title = "కంది: ఎండుతెగులు (ఫ్యుసేరియం విల్ట్) నివారణ పద్ధతులు",
                content = "కంది ఎండుతెగులు లక్షణాలు: మొక్కలు ఒక్కసారిగా పచ్చదనం కోల్పోయి వాడిపోయి ఎండుతాయి. కాండం చీల్చి చూస్తే నల్లటి చారలు కనిపిస్తాయి. నివారణ: 1) విత్తన శుద్ధి: కిలో విత్తనానికి 10 గ్రాముల ట్రైకోడెర్మా విరిడే లేదా 2 గ్రాముల కార్బెండజిమ్ తో శుద్ధి చేయాలి. 2) పంట మార్పిడి: జొన్న లేదా మొక్కజొన్నతో 3 సంవత్సరాలు పంట మార్పిడి చేయాలి. 3) సేంద్రీయ ఎరువులు: ఎకరాకు 2 టన్నుల పశువుల ఎరువులో 2 కిలోల ట్రైకోడెర్మా కలిపి ఆఖరి దుక్కిలో వేయాలి. తెగులు వచ్చిన మొక్కలను పీకి నాశనం చేయాలి.",
                sourceCitation = "ICAR-Indian Institute of Pulses Research (IIPR) Kanpur & PJTSAU Advisory",
                language = "te",
                lastVerifiedDate = "2026-11-09"
            )
        )
        list.add(
            OfflineKnowledgeEntity(
                id = "icar-red-pbw-en",
                collectionId = "KB05",
                crop = "Red Gram",
                category = "pest_management",
                title = "Red Gram: Pod Borer Complex (Helicoverpa & Maruca) Symptoms, ETL, IPM & CIBRC Controls",
                content = "Crop & Pests: Red Gram / Pigeonpea (Cajanus cajan) - Pod Borer Complex: Gram Pod Borer (Helicoverpa armigera) & Spotted Pod Borer (Maruca vitrata). Growth Stages & Symptoms: Flowering to pod development (85-150 DAS). Maruca: Larvae web together flowers, leaves and tender pods with silky threads and feed inside the webs, causing premature flower drop. Helicoverpa: Larvae bore single large circular holes into green pods and feed with half body thrust inside, hollowing out developing seeds. ETL: 1 larva/plant or 5-10% damaged pods or 5 moths/pheromone trap/night for 3 consecutive nights. IPM: 1) Intercrop with Sorghum or Pearl Millet (4:2 ratio) to encourage predatory insects. 2) Install 20 T-shaped bird perches/acre. 3) Erect 5 pheromone traps/acre for Helicoverpa. 4) Spray 5% NSKE (10 kg neem powder in 200L water) or HaNPV @ 200 LE/acre at 50% flowering. 5) Shake plants over plastic sheets to dislodge Maruca/Helicoverpa larvae in morning hours. CIBRC Approved Chemical Control: Chlorantraniliprole 18.5% SC (Coragen) @ 60 ml/acre (approx 4.5 ml per 15L backpack pump in 200L water), Pre-Harvest Interval (PHI): 29 days; OR Emamectin Benzoate 5% SG (Proclaim) @ 88 g/acre (approx 6.6 g per 15L pump), PHI: 14 days; OR Indoxacarb 14.5% SC @ 140 ml/acre (approx 10.5 ml per 15L pump), PHI: 15 days. Spray at 50% flowering and repeat 15 days later during podding. Safety & PPE: Wear protective overalls, nitrile gloves, and face shield. Maintain statutory 29-day PHI.",
                sourceCitation = "ICAR-Indian Institute of Pulses Research (IIPR) Kanpur & PJTSAU 2024",
                language = "en",
                lastVerifiedDate = "2026-11-09"
            )
        )
        list.add(
            OfflineKnowledgeEntity(
                id = "icar-red-pbw-hi",
                collectionId = "KB05",
                crop = "Red Gram",
                category = "pest_management",
                title = "अरहर: फली छेदक कीट (हेलिकोवर्पा एवं मारूका) लक्षण, ईटीएल, रोकथाम एवं कीटनाशक",
                content = "फसल व कीट: अरहर (तुअर) - फली छेदक सुंडी (हेलिकोवर्पा आर्मिगेरा) एवं चित्तीदार फली छेदक (मारूका विट्राटा)। अवस्थाएं एवं लक्षण: फूल आने से फली बनने तक (85-150 दिन)। मारूका सुंडी फूलों, पत्तियों व फलियों को रेशमी जाले से बांधकर अंदर छुपकर खाती है जिससे फूल झड़ जाते हैं। हेलिकोवर्पा सुंडी फलियों में बड़े गोल छेद बनाकर आधा शरीर अंदर डालकर दानों को खा जाती है। आर्थिक क्षति स्तर (ETL): 1 सुंडी प्रति पौधा या 5-10% क्षतिग्रस्त फलियां। समेकित प्रबंधन (IPM): 1) ज्वार या बाजरा के साथ 4:2 अनुपात में अंतरवर्तीय फसल लगाएं। 2) प्रति एकड़ 20 पक्षी बसेरे लगाएं। 3) प्रति एकड़ 5 फेरोमोन ट्रैप लगाएं। 4) 50% फूल आने पर 5% नीम बीज अर्क (एनएसकेई) या एचएएनपीवी का छिड़काव करें। 5) सुबह के समय पौधों को चादर पर हिलाकर गिरी हुई सुंडियों को नष्ट करें। CIBRC पंजीकृत रासायनिक नियंत्रण: क्लोरेंट्रानिलिप्रोल 18.5% एससी (कोराजन) 60 मिली प्रति एकड़ (लगभग 4.5 मिली प्रति 15 लीटर नैपसैक पंप; 200 लीटर पानी में), प्रतीक्षा अवधि (PHI): 29 दिन; अथवा एमामेक्टिन बेंजोएट 5% एसजी 88 ग्राम प्रति एकड़ (लगभग 6.6 ग्राम प्रति 15 लीटर पंप), प्रतीक्षा अवधि: 14 दिन; अथवा इंडोक्साकार्ब 14.5% एससी 140 मिली प्रति एकड़ (10.5 मिली प्रति 15 लीटर पंप), प्रतीक्षा अवधि: 15 दिन। सुरक्षा सावधानियां: छिड़काव के समय दस्ताने और मास्क पहनें। तुड़ाई से पहले 29 दिन की प्रतीक्षा अवधि का पालन करें।",
                sourceCitation = "भाकृअनुप-भारतीय दलहन अनुसंधान संस्थान (ICAR-IIPR) कानपुर एवं CIBRC 2024",
                language = "hi",
                lastVerifiedDate = "2026-11-09"
            )
        )
        list.add(
            OfflineKnowledgeEntity(
                id = "icar-red-pbw-te",
                collectionId = "KB05",
                crop = "Red Gram",
                category = "pest_management",
                title = "కందులు: శనగ పచ్చ పురుగు (హెలికోవెర్ప) మరియు మారుక కాయ తొలుచు పురుగు సమగ్ర యాజమాన్యం",
                content = "పంట మరియు పురుగులు: కందులు (తువర్) - కాయ తొలుచు పురుగులు: శనగ పచ్చ పురుగు (Helicoverpa) మరియు మారుక మచ్చల పురుగు (Maruca vitrata). దశలు మరియు లక్షణాలు: పూత మరియు కాయ దశ (85-150 రోజులు). మారుక లద్దె పురుగులు పూతగుత్తులు, ఆకులు మరియు లేత పిందెలను దారాలతో గూడుగా అల్లి లోపల ఉండి తింటాయి; పూత రాలిపోతుంది. హెలికోవెర్ప పురుగులు కాయలకు గుండ్రటి రంధ్రాలు చేసి సగం శరీరాన్ని లోపలికి దూర్చి గింజలను తిని డొల్ల చేస్తాయి. ఆర్థిక నష్ట పరిమితి (ETL): మొక్కకు 1 లద్దె పురుగు లేదా 5-10% దెబ్బతిన్న కాయలు. సమగ్ర నివారణ (IPM): 1) కందిలో జొన్న లేదా సజ్జను 4:2 నిష్పత్తిలో అంతర పంటగా వేయాలి. 2) ఎకరాకు 20 పక్షి స్థావరాలు (T కర్రలు) అమర్చాలి. 3) ఎకరాకు 5 లింగాకర్షక బుట్టలు పెట్టాలి. 4) 50% పూత దశలో 5% వేప గింజల కషాయం లేదా HaNPV పిచికారీ చేయాలి. 5) ఉదయం వేళల్లో మొక్కలను పాలిథిన్ షీట్లపై కుదిపి రాలిన పురుగులను నాశనం చేయాలి. CIBRC ఆమోదిత రసాయన నివారణ: క్లోరాంట్రానిలిప్రోల్ 18.5% SC (కొరాజెన్) ఎకరాకు 60 మి.లీ (15 లీటర్ల చేతి పంపుకు 4.5 మి.లీ; 200 లీటర్ల నీటిలో), వేచి ఉండే కాలం (PHI): 29 రోజులు; లేదా ఎమామెక్టిన్ బెంజోయేట్ 5% SG (ప్రోక్లైమ్) ఎకరాకు 88 గ్రా (15 లీటర్ల పంపుకు 6.6 గ్రా), వేచి ఉండే కాలం: 14 రోజులు; లేదా ఇండోక్సాకార్బ్ 14.5% SC ఎకరాకు 140 మి.లీ (15 లీటర్ల పంపుకు 10.5 మి.లీ), వేచి ఉండే కాలం: 15 రోజులు. జాగ్రత్తలు: కాయలు కోసే ముందు కనీసం 29 రోజుల వేచి ఉండే వ్యవధిని తప్పనిసరిగా పాటించాలి.",
                sourceCitation = "ICAR-IIPR కాన్పూర్ & ప్రొఫెసర్ జయశంకర్ తెలంగాణ వ్యవసాయ విశ్వవిద్యాలయం (PJTSAU) 2024",
                language = "te",
                lastVerifiedDate = "2026-11-09"
            )
        )
        list.add(
            OfflineKnowledgeEntity(
                id = "icar-red-wilt-en",
                collectionId = "KB04",
                crop = "Red Gram",
                category = "disease",
                title = "Red Gram: Fusarium Wilt & Sterility Mosaic Disease (SMD) Symptoms & Care",
                content = "Crop & Diseases: Red Gram - Fusarium Wilt (Fusarium udum) & Sterility Mosaic Disease (SMD - Pigeonpea sterility mosaic virus transmitted by eriophyid mite Aceria cajani). Symptoms & Growth Stages: Wilt (Vegetative to flowering, 45-120 DAS): Drooping and yellowing of leaves along one branch first (partial wilting) spreading to entire plant; vascular xylem vessels show purple/dark brown streaks when root and stem are split longitudinally. Sterility Mosaic (Vegetative, 30-90 DAS): Excessive bushy branching, leaves show light green and dark green mosaic mottle, complete suppression of flowering ('sterility' - no pods form), crop field appears patchily stunted and bushy. IPM: 1) Cultivate dual-resistant varieties: ICPL 87119 ('Asha'), PRG 176, WRG 65, BSMR 736. 2) Crop rotation with non-host crops (Sorghum, Tobacco) for 3 years; intercropping with sorghum reduces soil wilt inocula. 3) Treat seed with Trichoderma viride @ 10 g/kg seed. 4) Rogue out initial SMD bushes early. Chemical Control: Seed treatment with Carbendazim 50% WP @ 2 g/kg seed. For SMD eriophyid mite vector: Spray Fenazaquin 10% EC @ 300 ml/acre or Wettable Sulphur 80% WDG @ 600 g/acre (approx 45 g per 15L pump) at vegetative stage. PPE: Wear gloves and protective mask.",
                sourceCitation = "ICAR-IIPR Kanpur & PJTSAU Red Gram Package of Practices 2024",
                language = "en",
                lastVerifiedDate = "2026-11-09"
            )
        )
        list.add(
            OfflineKnowledgeEntity(
                id = "icar-red-wilt-hi",
                collectionId = "KB04",
                crop = "Red Gram",
                category = "disease",
                title = "अरहर: उकठा रोग (विल्ट) एवं बांझपन मोज़ेक (एसएमडी) लक्षण, रोकथाम व उपचार",
                content = "फसल व रोग: अरहर - उकठा रोग (फ्यूजेरियम उडम) एवं बांझपन मोज़ेक (स्टरिलिटी मोज़ेक - माइट द्वारा प्रसारित)। अवस्थाएं एवं लक्षण: उकठा रोग (45-120 दिन): पहले पौधे की एक तरफ की शाखाएं मुरझाकर पीली पड़ती हैं और फिर पूरा पौधा सूख जाता है; तने को बीच से चीरने पर अंदर की वाहिकाएं काली/कत्थई दिखती हैं। बांझपन मोज़ेक (30-90 दिन): पत्तियां छोटी व हल्की पीली चित्तीदार हो जाती हैं, पौधा अत्यधिक झाड़ीदार बन जाता है तथा फूल नहीं आते (बांझपन)। समेकित प्रबंधन (IPM): 1) रोगरोधी किस्में (आशा - ICPL 87119, बीएसएमआर 736) लगाएं। 2) ज्वार के साथ 3 वर्ष का फसल चक्र अपनाएं। 3) ट्राइकोडर्मा विरिडी 10 ग्राम/किग्रा बीज से बीजोपचार करें। 4) बांझपन ग्रसित पौधों को शुरू में ही उखाड़ दें। रासायनिक नियंत्रण: कार्बेन्डाजिम 50% डब्ल्यूपी 2 ग्राम/किग्रा बीज से बीजोपचार करें। बांझपन फैलाने वाली मकड़ी (माइट) नियंत्रण हेतु: फेनाजाक्विन 10% ईसी 300 मिली प्रति एकड़ अथवा घुलनशील गंधक (सल्फर 80% डब्ल्यूडीजी) 600 ग्राम प्रति एकड़ (लगभग 45 ग्राम प्रति 15 लीटर नैपसैक पंप) का छिड़काव करें। सुरक्षा सावधानियां: बीजोपचार व छिड़काव के समय दस्ताने पहनें।",
                sourceCitation = "भाकृअनुप-भारतीय दलहन अनुसंधान संस्थान (ICAR-IIPR) कानपुर एवं CIBRC 2024",
                language = "hi",
                lastVerifiedDate = "2026-11-09"
            )
        )
        list.add(
            OfflineKnowledgeEntity(
                id = "icar-red-wilt-te",
                collectionId = "KB04",
                crop = "Red Gram",
                category = "disease",
                title = "కందులు: ఎండు తెగులు (విల్ట్) మరియు గొడ్రాలితనం తెగులు (SMD) గుర్తింపు మరియు నివారణ",
                content = "పంట మరియు వ్యాధులు: కందులు - ఎండు తెగులు (Fusarium udum) మరియు గొడ్రాలితనం తెగులు (Sterility Mosaic Disease - ఎరియోఫైడ్ నల్లి ద్వారా వ్యాప్తి). దశలు మరియు లక్షణాలు: ఎండు తెగులు (శాఖీయ మరియు పూత దశ, 45-120 రోజులు): మొక్క యొక్క ఒక వైపు కొమ్మలు వాడి పసుపు రంగులోకి మారి ఎండిపోవడం మొదలవుతుంది; కాండం చీల్చి చూస్తే లోపలి నాళాలు నల్లగా మారి ఉంటాయి. గొడ్రాలితనం తెగులు (30-90 రోజులు): ఆకులు లేత పసుపు మరియు ఆకుపచ్చ మచ్చలతో మొజాయిక్ గా మారతాయి; మొక్కలు గుబురుగా పొదలా మారి పూత పూయక గొడ్రాలితనంగా మారతాయి. సమగ్ర నివారణ (IPM): 1) తెగుళ్లను తట్టుకునే రకాలు (ICPL 87119 'ఆశ', PRG 176, WRG 65) సాగు చేయాలి. 2) జొన్నతో పంట మార్పిడి లేదా అంతర పంట వేయడం వల్ల నేలలోని శిలీంధ్రం తగ్గుతుంది. 3) కిలో విత్తనానికి ట్రైకోడెర్మా విరిడి 10 గ్రా కలిపి విత్తనశుద్ధి చేయాలి. 4) గొడ్రాలితనం సోకిన మొక్కలను ప్రారంభంలోనే పీకి నాశనం చేయాలి. రసాయన నివారణ: కిలో విత్తనానికి కార్బండజిమ్ 50% WP 2 గ్రా తో విత్తనశుద్ధి చేయాలి. గొడ్రాలితనం వ్యాప్తి చేసే నల్లి నివారణకు: ఫెనాజాక్విన్ 10% EC ఎకరాకు 300 మి.లీ లేదా సల్ఫర్ 80% WDG ఎకరాకు 600 గ్రా (15 లీటర్ల పంపుకు 45 గ్రా) పిచికారీ చేయాలి. జాగ్రత్తలు: చేతి తొడుగులు మరియు మాస్క్ ధరించాలి.",
                sourceCitation = "ICAR-IIPR కాన్పూర్ & ప్రొఫెసర్ జయశంకర్ తెలంగాణ వ్యవసాయ విశ్వవిద్యాలయం (PJTSAU) 2024",
                language = "te",
                lastVerifiedDate = "2026-11-09"
            )
        )
        list.add(
            OfflineKnowledgeEntity(
                id = "icar-ric-blast-en",
                collectionId = "KB04",
                crop = "Rice / Paddy",
                category = "disease",
                title = "Rice: Leaf & Neck Blast Symptoms, Weather Triggers, IPM & CIBRC Chemical Control",
                content = "Crop & Disease: Rice / Paddy (Oryza sativa) - Blast: Leaf Blast, Node Blast & Neck Blast (Magnaporthe oryzae / Pyricularia oryzae). Growth Stages & Symptoms: Tillering to panicle emergence (35-90 DAS). Leaf blast: Spindle-shaped / diamond-shaped lesions with dark brown margins and ashy grey centers; lesions coalesce, scorching entire leaves. Neck blast: Girdling blackish-brown lesion at the rachis node directly below the panicle; neck shrivels and snaps, earheads turn completely white and empty ('chaffy panicles'). Favorable Triggers: Night temperatures 19-23°C, high relative humidity (>90%), heavy morning dew (>8 hours), overcast skies, excessive split doses of Urea (>120 kg N/ha). IPM: 1) Seed treatment with Pseudomonas fluorescens @ 10 g/kg or Tricyclazole 75% WP @ 2 g/kg seed. 2) Avoid dense planting; split nitrogen doses and never apply urea during cloudy weather. 3) Grow blast-tolerant varieties (e.g. MTU 1010, BPT 5204 tolerant derivatives). CIBRC Approved Chemical Control: Tricyclazole 75% WP (Beam) @ 120-160 g/acre (approx 10-12 g per 15L backpack pump in 200L water), Pre-Harvest Interval (PHI): 30 days; OR Azoxystrobin 18.2% + Difenoconazole 11.4% SC (Amistar Top) @ 200 ml/acre (approx 15 ml per 15L pump; 1.0 ml/L), PHI: 15 days; OR Isoprothiolane 40% EC @ 300 ml/acre (approx 22.5 ml per 15L pump), PHI: 30 days. Spray first at leaf spot onset and MANDATORILY repeat at 5-10% panicle emergence to prevent neck blast. PPE: Wear rubber gloves and face mask.",
                sourceCitation = "ICAR-Indian Institute of Rice Research (IIRR) Hyderabad & PJTSAU 2024",
                language = "en",
                lastVerifiedDate = "2026-11-09"
            )
        )
        list.add(
            OfflineKnowledgeEntity(
                id = "icar-ric-blast-hi",
                collectionId = "KB04",
                crop = "Rice / Paddy",
                category = "disease",
                title = "धान: झुलसा रोग (लीफ ब्लास्ट एवं नेक ब्लास्ट) पहचान, मौसम, रोकथाम एवं अनुशंसित फफूंदनाशक",
                content = "फसल व रोग: धान - झुलसा रोग: लीफ ब्लास्ट एवं गर्दन तोड़ (नेक ब्लास्ट - पाइरीकुलेरिया ओराइजी)। अवस्थाएं एवं लक्षण: कल्ले फूटने से बाली निकलने तक (35-90 दिन)। लीफ ब्लास्ट में पत्तियों पर आंख या नाव के आकार के धब्बे बनते हैं जिनके किनारे कत्थई और केंद्र राख जैसे भूरे होते हैं। नेक ब्लास्ट में बाली की गर्दन पर काला धब्बा बनकर मुड़ जाती है और पूरी बाली सफेद खोखली (सफेद बाली) रह जाती है। अनुकूल मौसम: रात का तापमान 19-23°C, 90% से अधिक आर्द्रता, रात में ओस एवं यूरिया का अत्यधिक उपयोग। समेकित प्रबंधन (IPM): 1) स्यूडोमोनास फ्लोरोसेंस 10 ग्राम/किग्रा या ट्राईसाइक्लाजोल 2 ग्राम/किग्रा बीज से बीजोपचार करें। 2) नत्रजन को 3-4 भागों में बांटकर डालें; बीमारी के समय यूरिया न डालें। 3) मेड़ों को खरपतवार मुक्त रखें। CIBRC पंजीकृत रासायनिक नियंत्रण: ट्राईसाइक्लाजोल 75% डब्ल्यूपी (बीम) 120-160 ग्राम प्रति एकड़ (लगभग 10-12 ग्राम प्रति 15 लीटर नैपसैक पंप; 200 लीटर पानी में), प्रतीक्षा अवधि (PHI): 30 दिन; अथवा एजोक्सीस्ट्रोबिन 18.2% + डाइफेनोकोनाजोल 11.4% एससी (एमिस्टार टॉप) 200 मिली प्रति एकड़ (लगभग 15 मिली प्रति 15 लीटर पंप), प्रतीक्षा अवधि: 15 दिन; अथवा आइसोप्रोपियोलेन 40% ईसी 300 मिली प्रति एकड़ (22.5 मिली प्रति 15 लीटर पंप), प्रतीक्षा अवधि: 30 दिन। बाली 5-10% निकलने पर दूसरा छिड़काव अति आवश्यक है। पीपीई: छिड़काव के दौरान मास्क एवं दस्तानों का उपयोग करें।",
                sourceCitation = "भाकृअनुप-भारतीय चावल अनुसंधान संस्थान (ICAR-IIRR) हैदराबाद एवं CIBRC 2024",
                language = "hi",
                lastVerifiedDate = "2026-11-09"
            )
        )
        list.add(
            OfflineKnowledgeEntity(
                id = "icar-ric-blast-te",
                collectionId = "KB04",
                crop = "Rice / Paddy",
                category = "disease",
                title = "వరి: అగ్గితెగులు (ఆకు, కణుపు మరియు మెడవిరుపు) గుర్తింపు, వాతావరణం, సమగ్ర నివారణ మరియు రసాయనాలు",
                content = "పంట మరియు వ్యాధి: వరి - అగ్గితెగులు: ఆకు అగ్గితెగులు, కణుపు తెగులు మరియు మెడవిరుపు తెగులు (పైరిక్యులేరియా ఒరైజే). దశలు మరియు లక్షణాలు: పిలక దశ నుండి వెన్ను ఈనే దశ వరకు (35-90 రోజులు). ఆకు అగ్గితెగులులో నూలు కండె (కంటి) ఆకారపు మచ్చలు ఏర్పడి అంచులు గోధుమ రంగులో, నడిమి భాగం బూడిద రంగులో ఉంటాయి. మెడవిరుపు దశలో వెన్ను కింద మెడ కణుపు నల్లబడి విరిగిపోతుంది; గింజలు పాలు పోసుకోక తెల్ల కంకులుగా మారి తాలు అవుతాయి. అనుకూల వాతావరణం: రాత్రి చలి (19-23°C), 90% పైగా గాలిలో తేమ, పొగమంచు, అధిక యూరియా వాడకం. సమగ్ర నివారణ (IPM): 1) విత్తనశుద్ధి: కిలో విత్తనానికి సూడోమోనాస్ ఫ్లోరోసెన్స్ 10 గ్రా లేదా ట్రైసైక్లజోల్ 2 గ్రా కలిపి శుద్ధి చేయాలి. 2) యూరియాను ఒకేసారి కాకుండా దఫాలుగా వేయాలి; తెగులు ఉధృతి ఉన్నప్పుడు యూరియా తాత్కాలికంగా ఆపాలి. 3) పొలంలో గట్లపై కలుపు మొక్కలను నిర్మూలించాలి. CIBRC ఆమోదిత రసాయన నివారణ: ట్రైసైక్లజోల్ 75% WP (బీమ్) ఎకరాకు 120-160 గ్రా (15 లీటర్ల చేతి పంపుకు 10-12 గ్రా; 200 లీటర్ల నీటిలో), వేచి ఉండే కాలం (PHI): 30 రోజులు; లేదా అజాక్సిస్ట్రోబిన్ 18.2% + డైఫెనోకొనజోల్ 11.4% SC (అమిస్టార్ టాప్) ఎకరాకు 200 మి.లీ (15 లీటర్ల పంపుకు 15 మి.లీ), వేచి ఉండే కాలం: 15 రోజులు; లేదా ఐసోప్రోథియోలేన్ 40% EC ఎకరాకు 300 మి.లీ (15 లీటర్ల పంపుకు 22.5 మి.లీ), వేచి ఉండే కాలం: 30 రోజులు. వెన్ను 5-10% బయటకు వచ్చే సమయంలో తప్పనిసరిగా రెండోసారి పిచికారీ చేయాలి. జాగ్రత్తలు: చేతి తొడుగులు మరియు మాస్క్ ధరించాలి.",
                sourceCitation = "ICAR-IIRR హైదరాబాద్ & ప్రొఫెసర్ జయశంకర్ తెలంగాణ వ్యవసాయ విశ్వవిద్యాలయం (PJTSAU) 2024",
                language = "te",
                lastVerifiedDate = "2026-11-09"
            )
        )
        list.add(
            OfflineKnowledgeEntity(
                id = "icar-ric-blb-en",
                collectionId = "KB04",
                crop = "Rice / Paddy",
                category = "disease",
                title = "Rice: Bacterial Leaf Blight (BLB / Kresek) Symptoms, Cultural Management & Bactericides",
                content = "Crop & Disease: Rice - Bacterial Leaf Blight (BLB - Xanthomonas oryzae pv. oryzae). Growth Stages & Symptoms: Maximum tillering to flowering (45-75 DAS). Kresek phase: Early systemic wilt of transplanted seedlings within 3-4 weeks. Leaf blight phase: Tiny translucent water-soaked streaks starting at leaf tips and margins, progressing downwards into wavy yellowish-white stripes with irregular margins; amber bacterial ooze beads appear in morning dew; foliage turns straw-bleached and wilts completely. Favorable Triggers: Flooding/stagnant water, typhoons/cyclones, windy rains, temperatures 25-34°C, excessive nitrogen top-dressing. IPM: 1) Drain standing water completely for 3-4 days to arrest bacterial spread. 2) Immediately halt split urea top-dressing; apply extra Muriate of Potash (MOP @ 15-20 kg/acre) to harden plant cell walls. 3) Plant resistant varieties (e.g. Improved Samba Mahsuri - RP Bio-226, MTU 1061). 4) Avoid clipping seedling tips during transplanting. CIBRC Approved Chemical Control: Copper Hydroxide 53.8% DF (Kocide) @ 400 g/acre (approx 30 g per 15L backpack pump in 200L water) OR Kasugamycin 3% SL (Kasu-B) @ 400 ml/acre (approx 30 ml per 15L pump; 2.0 ml/L), Pre-Harvest Interval (PHI): 30 days; OR Copper Oxychloride 50% WP @ 500 g/acre + Streptocycline @ 20 g/acre. PPE: Wear gloves and protective boots.",
                sourceCitation = "ICAR-IIRR Hyderabad Technical Compendium on Bacterial Diseases & PJTSAU 2024",
                language = "en",
                lastVerifiedDate = "2026-11-09"
            )
        )
        list.add(
            OfflineKnowledgeEntity(
                id = "icar-ric-blb-hi",
                collectionId = "KB04",
                crop = "Rice / Paddy",
                category = "disease",
                title = "धान: जीवाणु पत्ती झुलसा (बीएलबी / क्रेसेक) लक्षण, जल प्रबंधन एवं अनुशंसित जीवाणुनाशक",
                content = "फसल व रोग: धान - जीवाणु पत्ती झुलसा (बैक्टीरियल लीफ ब्लाइट - जैंथोमोनास ओराइजी)। अवस्थाएं एवं लक्षण: कल्ले फूटने से फूल आने तक (45-75 दिन)। पत्तियों के सिरों एवं किनारों से पानीदार धारियां शुरू होकर नीचे की ओर लहरदार पीली-सफेद पट्टियों में बदल जाती हैं। सुबह के समय पत्तियों पर पीले जीवाणु स्राव की बूंदें दिखती हैं। पत्तियां सूखकर पुआल जैसी सफेद हो जाती हैं। अनुकूल मौसम: लगातार बारिश, तेज हवाएं, खेत में पानी भरा रहना व यूरिया की अधिक मात्रा। समेकित प्रबंधन (IPM): 1) खेत से पानी निकालकर 3-4 दिनों तक सूखा रखें। 2) यूरिया का छिड़काव तुरंत बंद करें तथा 15-20 किग्रा पोटाश प्रति एकड़ डालें। 3) प्रतिरोधी किस्में (इम्प्रूव्ड सांभा महसूरी) लगाएं। 4) रोपाई के समय पत्तियों के ऊपरी सिरे न काटें। CIBRC पंजीकृत रासायनिक नियंत्रण: कासुगामाइसिन 3% एसएल (कासु-बी) 400 मिली प्रति एकड़ (लगभग 30 मिली प्रति 15 लीटर नैपसैक पंप; 200 लीटर पानी में), प्रतीक्षा अवधि (PHI): 30 दिन; अथवा कॉपर हाइड्रॉक्साइड 53.8% डीएफ 400 ग्राम प्रति एकड़ (लगभग 30 ग्राम प्रति 15 लीटर पंप); अथवा कॉपर ऑक्सीक्लोराइड 50% डब्ल्यूपी 500 ग्राम + स्ट्रेप्टोसाइक्लिन 20 ग्राम प्रति एकड़ का छिड़काव करें। सुरक्षा सावधानियां: छिड़काव के समय दस्ताने और मास्क पहनें।",
                sourceCitation = "भाकृअनुप-भारतीय चावल अनुसंधान संस्थान (ICAR-IIRR) एवं CIBRC 2024",
                language = "hi",
                lastVerifiedDate = "2026-11-09"
            )
        )
        list.add(
            OfflineKnowledgeEntity(
                id = "icar-ric-blb-te",
                collectionId = "KB04",
                crop = "Rice / Paddy",
                category = "disease",
                title = "వరి: బాక్టీరియా ఎండు తెగులు (BLB / క్రెసెక్) గుర్తింపు, నీటి యాజమాన్యం మరియు నివారణ",
                content = "పంట మరియు వ్యాధి: వరి - బాక్టీరియా ఎండు తెగులు (Bacterial Leaf Blight - జాంథోమోనాస్ ఒరైజే). దశలు మరియు లక్షణాలు: పిలక దశ నుండి చిరుపొట్ట దశ వరకు (45-75 రోజులు). ఆకు చివర్లు మరియు అంచుల వెంబడి నీటి చారలు మొదలై పసుపు-తెలుపు రంగు అలల వంటి చారలుగా క్రిందికి విస్తరిస్తాయి. తెల్లవారుజామున ఆకులపై పసుపు రంగు బాక్టీరియా ద్రవపు బిందువులు (బాక్టీరియల్ ఊజ్) కనిపిస్తాయి. ఆకులు ఎండిపోయి ఎండుగడ్డి రంగులోకి మారుతాయి. అనుకూల వాతావరణం: వర్షపు జల్లులు, ఈదురు గాలులు, పొలంలో నీరు నిల్వ ఉండటం, అధిక యూరియా వాడకం. సమగ్ర నివారణ (IPM): 1) పొలంలో నిల్వ ఉన్న నీటిని 3-4 రోజుల పాటు పూర్తిగా తీసివేసి ఆరబెట్టాలి. 2) యూరియా వాడకాన్ని వెంటనే నిలిపివేయాలి; ఎకరాకు 15-20 కిలోల మ్యూరేట్ ఆఫ్ పొటాష్ (MOP) ఎరువు వేసి కాండాన్ని గట్టిపరచాలి. 3) తెగులును తట్టుకునే రకాలు (ఇంప్రూవ్డ్ సాంబ మసూరి - RP Bio-226) సాగు చేయాలి. 4) నాట్లు వేసేటప్పుడు ఆకు చివర్లు తుంచరాదు. రసాయన నివారణ: కసుగామైసిన్ 3% SL (కసు-బి) ఎకరాకు 400 మి.లీ (15 లీటర్ల చేతి పంపుకు 30 మి.లీ, లీటరు నీటికి 2 మి.లీ; 200 లీటర్ల నీటిలో), వేచి ఉండే కాలం (PHI): 30 రోజులు; లేదా కాపర్ హైడ్రాక్సైడ్ 53.8% DF ఎకరాకు 400 గ్రా (15 లీటర్ల పంపుకు 30 గ్రా); లేదా కాపర్ ఆక్సీక్లోరైడ్ 50% WP 500 గ్రా + స్ట్రెప్టోసైక్లిన్ 20 గ్రా కలిపి పిచికారీ చేయాలి. జాగ్రత్తలు: చేతి తొడుగులు మరియు బూట్లు ధరించాలి.",
                sourceCitation = "ICAR-IIRR & ప్రొఫెసర్ జయశంకర్ తెలంగాణ వ్యవసాయ విశ్వవిద్యాలయం (PJTSAU) 2024",
                language = "te",
                lastVerifiedDate = "2026-11-09"
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
                lastVerifiedDate = "2026-11-09"
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
                lastVerifiedDate = "2026-11-09"
            )
        )
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
                lastVerifiedDate = "2026-11-09"
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
                lastVerifiedDate = "2026-11-09"
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
                lastVerifiedDate = "2026-11-09"
            )
        )
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
                lastVerifiedDate = "2026-11-09"
            )
        )
        list.add(
            OfflineKnowledgeEntity(
                id = "icar-soy-loop-en",
                collectionId = "KB05",
                crop = "Soybean",
                category = "pest_management",
                title = "Soybean: Semilooper & Defoliator Caterpillars IPM & CIBRC Controls",
                content = "Crop & Pests: Soybean - Green Semilooper (Chrysodeixis acuta) & Tobacco Caterpillar (Spodoptera litura). Growth Stages & Symptoms: Vegetative and flowering stages (25-60 DAS). Semilooper makes characteristic loops while crawling; larvae defoliate leaves from margin inwards leaving only primary veins. Spodoptera larvae feed gregariously on leaf epidermis, skeletonizing foliage into paper-white mesh. ETL: Semilooper: 3-4 larvae/meter row; Spodoptera: 2-3 egg masses or skeletonized leaves per meter row. IPM: 1) Install 20 T-shaped bird perches/acre for predatory birds. 2) Erect 5 pheromone traps/acre for Spodoptera. 3) Spray SlNPV @ 200 LE/acre or Bacillus thuringiensis (Bt) @ 400 g/acre during early instars. 4) Intercrop with Pigeonpea (4:2 ratio). CIBRC Approved Chemical Control: Flubendiamide 39.35% SC (Fame) @ 60 ml/acre (approx 4.5 ml per 15L backpack pump in 200L water), Pre-Harvest Interval (PHI): 30 days; OR Chlorantraniliprole 18.5% SC (Coragen) @ 60 ml/acre (approx 4.5 ml per 15L pump), PHI: 22 days; OR Emamectin Benzoate 5% SG (Proclaim) @ 88 g/acre (approx 6.6 g per 15L pump), PHI: 14 days. Safety: Never spray against wind. Wear rubber gloves and face shield.",
                sourceCitation = "ICAR-IISR Indore IPM Guidelines & CIBRC 2024",
                language = "en",
                lastVerifiedDate = "2026-11-09"
            )
        )
        list.add(
            OfflineKnowledgeEntity(
                id = "icar-soy-loop-hi",
                collectionId = "KB05",
                crop = "Soybean",
                category = "pest_management",
                title = "सोयाबीन: सेमीलूपर एवं तम्बाकू सुंडी समेकित कीट प्रबंधन एवं अनुशंसित कीटनाशक",
                content = "फसल व कीट: सोयाबीन - सेमीलूपर (क्राइसोडेक्सिस एक्यूटा) एवं तम्बाकू की सुंडी (स्पोडोप्टेरा लिटूरा)। अवस्थाएं एवं लक्षण: वानस्पतिक एवं फूल अवस्था (25-60 दिन)। सेमीलूपर सुंडी चलने पर छल्ला बनाती है और पत्तियों को किनारों से खाकर केवल मुख्य नसें छोड़ती है। तम्बाकू सुंडी समूह में पत्तियों की निचली सतह को खुरच कर छलनी जैसा बना देती है। आर्थिक क्षति स्तर (ETL): 3-4 सुंडी प्रति मीटर कतार। समेकित प्रबंधन (IPM): 1) प्रति एकड़ 20 टी-आकार के पक्षी बसेरे लगाएं। 2) प्रति एकड़ 5 फेरोमोन ट्रैप लगाएं। 3) छोटी सुंडियों पर बीटी पाउडर 400 ग्राम/एकड़ या एसएलएनपीवी का छिड़काव करें। 4) अरहर के साथ 4:2 अनुपात में अंतरवर्तीय फसल लगाएं। CIBRC अनुशंसित रासायनिक नियंत्रण: फ्लूबेंडियामाइड 39.35% एससी (फेम) 60 मिली प्रति एकड़ (लगभग 4.5 मिली प्रति 15 लीटर नैपसैक पंप; 200 लीटर पानी में), प्रतीक्षा अवधि (PHI): 30 दिन; अथवा क्लोरेंट्रानिलिप्रोल 18.5% एससी (कोराजन) 60 मिली प्रति एकड़ (लगभग 4.5 मिली प्रति 15 लीटर पंप), प्रतीक्षा अवधि: 22 दिन; अथवा एमामेक्टिन बेंजोएट 5% एसजी 88 ग्राम प्रति एकड़ (6.6 ग्राम प्रति 15 लीटर पंप), प्रतीक्षा अवधि: 14 दिन। सुरक्षा: कीटनाशक छिड़काव के समय दस्ताने व चश्मा पहनें।",
                sourceCitation = "भाकृअनुप-भारतीय सोयाबीन अनुसंधान संस्थान (ICAR-IISR) इंदौर एवं CIBRC 2024",
                language = "hi",
                lastVerifiedDate = "2026-11-09"
            )
        )
        list.add(
            OfflineKnowledgeEntity(
                id = "icar-soy-loop-te",
                collectionId = "KB05",
                crop = "Soybean",
                category = "pest_management",
                title = "సోయాబీన్: పచ్చ లద్దె పురుగు (సెమీలూపర్) మరియు పొగాకు లద్దె పురుగు సమగ్ర నివారణ, మందులు",
                content = "పంట మరియు పురుగులు: సోయాబీన్ - సెమీలూపర్ (Semilooper) మరియు పొగాకు లద్దె పురుగు (Spodoptera litura). దశలు మరియు లక్షణాలు: శాఖీయ మరియు పూత దశ (25-60 రోజులు). సెమీలూపర్ పురుగులు నడుస్తున్నప్పుడు వెనుక భాగాన్ని పైకి లేపుతూ వంపులు తిరుగుతూ ఆకులను కొరికి ఈనెలు మాత్రమే మిగులుస్తాయి. పొగాకు లద్దె పురుగులు గుంపులుగా చేరి ఆకు పచ్చటి పొరను గీకి తినడం వల్ల ఆకులు జల్లెడలా మారతాయి. ఆర్థిక నష్ట పరిమితి (ETL): వరుస మీటరుకు 3-4 సెమీలూపర్ పురుగులు. సమగ్ర నివారణ (IPM): 1) ఎకరాకు 20 పక్షి స్థావరాలు (T ఆకారపు కర్రలు) నాటాలి. 2) ఎకరాకు 5 లింగాకర్షక బుట్టలు అమర్చాలి. 3) చిన్న పురుగుల దశలో Bt పౌడర్ ఎకరాకు 400 గ్రా లేదా SlNPV ద్రావణం పిచికారీ చేయాలి. 4) కందితో 4:2 నిష్పత్తిలో అంతర పంట వేయాలి. CIBRC ఆమోదిత రసాయన నివారణ: ఫ్లూబెండియమైడ్ 39.35% SC (ఫేమ్) ఎకరాకు 60 మి.లీ (15 లీటర్ల చేతి పంపుకు 4.5 మి.లీ; 200 లీటర్ల నీటిలో), వేచి ఉండే కాలం (PHI): 30 రోజులు; లేదా క్లోరాంట్రానిలిప్రోల్ 18.5% SC ఎకరాకు 60 మి.లీ (15 లీటర్ల పంపుకు 4.5 మి.లీ), వేచి ఉండే కాలం: 22 రోజులు; లేదా ఎమామెక్టిన్ బెంజోయేట్ 5% SG ఎకరాకు 88 గ్రా (15 లీటర్ల పంపుకు 6.6 గ్రా), వేచి ఉండే కాలం: 14 రోజులు. జాగ్రత్తలు: చేతి తొడుగులు మరియు మాస్క్ ధరించాలి.",
                sourceCitation = "ICAR-IISR ఇండోర్ & ప్రొఫెసర్ జయశంకర్ తెలంగాణ వ్యవసాయ విశ్వవిద్యాలయం (PJTSAU) 2024",
                language = "te",
                lastVerifiedDate = "2026-11-09"
            )
        )
        list.add(
            OfflineKnowledgeEntity(
                id = "icar-soy-ymv-en",
                collectionId = "KB04",
                crop = "Soybean",
                category = "disease",
                title = "Soybean: Yellow Mosaic Virus (YMV) & Soybean Rust Identification, Prevention & CIBRC Controls",
                content = "Crop & Diseases: Soybean (Glycine max) - Yellow Mosaic Virus (YMV - Begomovirus) & Soybean Rust (Phakopsora pachyrhizi). Symptoms & Growth Stages: YMV (Vegetative to pod formation, 20-60 DAS): Bright yellow patches alternating with green areas on leaves, progressing to complete golden chlorosis, leaves turn brittle, plants stunted, pods few, small and curved with aborted seeds; transmitted by Whitefly (Bemisia tabaci). Soybean Rust (Flowering to seed development, 45-80 DAS): Tiny polygonal tan/brown lesions with volcanic eruptive pustules on lower leaf surfaces; severe rust triggers sudden massive leaf shedding, slashing seed yield by up to 80%. IPM: 1) Plant YMV-resistant cultivars (e.g. JS 97-52, JS 20-34, NRC 37). 2) Seed treatment with Thiamethoxam 30% FS @ 10 ml/kg seed for early whitefly protection. 3) Yellow sticky traps @ 15/acre. 4) Rogue out initial YMV virus plants. CIBRC Approved Chemical Control: For Whitefly/YMV Vector: Flonicamid 50% WG @ 80 g/acre (approx 6.0 g per 15L backpack pump in 200L water), Pre-Harvest Interval (PHI): 20 days; OR Thiamethoxam 25% WG @ 40 g/acre (approx 3.0 g per 15L pump), PHI: 21 days. For Soybean Rust: Hexaconazole 5% SC (Contaf) @ 400 ml/acre (approx 30.0 ml per 15L pump), PHI: 30 days; OR Propiconazole 25% EC (Tilt) @ 200 ml/acre (approx 15.0 ml per 15L pump), PHI: 26 days. Safety: Wear rubber gloves and face mask.",
                sourceCitation = "ICAR-Indian Institute of Soybean Research (IISR) Indore Technical Bulletin & CIBRC 2024",
                language = "en",
                lastVerifiedDate = "2026-11-09"
            )
        )
        list.add(
            OfflineKnowledgeEntity(
                id = "icar-soy-ymv-hi",
                collectionId = "KB04",
                crop = "Soybean",
                category = "disease",
                title = "सोयाबीन: पीला मोज़ेक वायरस (YMV) एवं गेरुआ (रस्ट) रोग पहचान, रोकथाम व कीटनाशक",
                content = "फसल व रोग: सोयाबीन - पीला मोज़ेक वायरस (YMV) एवं गेरुआ रोग (सोयाबीन रस्ट)। अवस्थाएं एवं लक्षण: पीला मोज़ेक (20-60 दिन): पत्तियों पर चमकीले पीले व हरे धब्बे बनते हैं, बाद में पूरी पत्ती सुनहरी पीली होकर सूख जाती है, फलियां मुड़ जाती हैं और दाने नहीं बनते; यह सफेद मक्खी द्वारा फैलता है। गेरुआ रोग (45-80 दिन): पत्तियों की निचली सतह पर कत्थई रंग के उभरे हुए फफोले बनते हैं और पत्तियां तेजी से झड़ जाती हैं जिससे 80% तक नुकसान होता है। समेकित प्रबंधन (IPM): 1) प्रतिरोधी किस्में (जेएस 97-52, जेएस 20-34) लगाएं। 2) थायमेथोक्सम 30% एफएस 10 मिली/किग्रा बीज से बीजोपचार करें। 3) प्रति एकड़ 15 पीले चिपचिपे कार्ड लगाएं। CIBRC अनुशंसित रासायनिक नियंत्रण: सफेद मक्खी/YMV हेतु: फ्लोनिकामाइड 50% डब्ल्यूजी 80 ग्राम प्रति एकड़ (लगभग 6.0 ग्राम प्रति 15 लीटर नैपसैक पंप; 200 लीटर पानी में), प्रतीक्षा अवधि (PHI): 20 दिन; अथवा थायमेथोक्सम 25% डब्ल्यूजी 40 ग्राम प्रति एकड़ (लगभग 3.0 ग्राम प्रति 15 लीटर पंप), प्रतीक्षा अवधि: 21 दिन। गेरुआ रोग हेतु: हेक्साकोनाजोल 5% एससी 400 मिली प्रति एकड़ (लगभग 30 मिली प्रति 15 लीटर पंप), प्रतीक्षा अवधि: 30 दिन; अथवा प्रोपिकोनाजोल 25% ईसी 200 मिली प्रति एकड़ (15 मिली प्रति 15 लीटर पंप), प्रतीक्षा अवधि: 26 दिन। सुरक्षा सावधानियां: दस्ताने एवं मास्क पहनकर ही छिड़काव करें।",
                sourceCitation = "भाकृअनुप-भारतीय सोयाबीन अनुसंधान संस्थान (ICAR-IISR) इंदौर एवं CIBRC 2024",
                language = "hi",
                lastVerifiedDate = "2026-11-09"
            )
        )
        list.add(
            OfflineKnowledgeEntity(
                id = "icar-soy-ymv-te",
                collectionId = "KB04",
                crop = "Soybean",
                category = "disease",
                title = "సోయాబీన్: పసుపు పచ్చ వైరస్ (YMV) మరియు తుప్పు తెగులు గుర్తింపు, నివారణ మరియు రసాయనాలు",
                content = "పంట మరియు వ్యాధులు: సోయాబీన్ - పసుపు పచ్చ వైరస్ తెగులు (Yellow Mosaic Virus) మరియు తుప్పు తెగులు (Soybean Rust). లక్షణాలు మరియు దశలు: పసుపు పచ్చ వైరస్ (విత్తిన 20-60 రోజులు): ఆకులపై పసుపు మరియు పచ్చని మచ్చలు ఏర్పడి క్రమంగా ఆకులన్నీ బంగారు పసుపు రంగులోకి మారి పెళుసుగా మారతాయి; కాయలు చిన్నవిగా వంకరలు తిరిగి గింజలు ఏర్పడవు; ఇది తెల్లదోమ ద్వారా వ్యాపిస్తుంది. తుప్పు తెగులు (పూత మరియు కాయ దశ, 45-80 రోజులు): ఆకుల అడుగు భాగాన గోధుమ రంగు పొక్కులు ఏర్పడి పుప్పొడి రాలుతుంది; ఆకులు రాలిపోయి దిగుబడి 40-80% తగ్గుతుంది. సమగ్ర నివారణ (IPM): 1) తెగులును తట్టుకునే రకాలు (JS 97-52, JS 20-34) సాగు చేయాలి. 2) కిలో విత్తనానికి థయామిథాక్సామ్ 30% FS 10 మి.లీ తో విత్తనశుద్ధి చేయాలి. 3) ఎకరాకు 15 పసుపు జిగురు అట్టలు అమర్చాలి. CIBRC ఆమోదిత రసాయన నివారణ: తెల్లదోమ/YMV నివారణకు: ఫ్లోనికామిడ్ 50% WG ఎకరాకు 80 గ్రా (15 లీటర్ల చేతి పంపుకు 6.0 గ్రా; 200 లీటర్ల నీటిలో), వేచి ఉండే కాలం (PHI): 20 రోజులు; లేదా థయామిథాక్సామ్ 25% WG ఎకరాకు 40 గ్రా (15 లీటర్ల పంపుకు 3.0 గ్రా), వేచి ఉండే కాలం: 21 రోజులు. తుప్పు తెగులు నివారణకు: హెక్సాకొనజోల్ 5% SC ఎకరాకు 400 మి.లీ (15 లీటర్ల పంపుకు 30 మి.లీ), వేచి ఉండే కాలం: 30 రోజులు; లేదా ప్రొపికోనజోల్ 25% EC (టిల్ట్) ఎకరాకు 200 మి.లీ (15 లీటర్ల పంపుకు 15 మి.లీ), వేచి ఉండే కాలం: 26 రోజులు. జాగ్రత్తలు: చేతి తొడుగులు, మాస్క్ ధరించాలి.",
                sourceCitation = "ICAR-IISR ఇండోర్ & ప్రొఫెసర్ జయశంకర్ తెలంగాణ వ్యవసాయ విశ్వవిద్యాలయం (PJTSAU) 2024",
                language = "te",
                lastVerifiedDate = "2026-11-09"
            )
        )
        list.add(
            OfflineKnowledgeEntity(
                id = "icar-syb-pest-en",
                collectionId = "KB04",
                crop = "Soybean",
                category = "pest_management",
                title = "Soybean: Girdle Beetle & Defoliator Caterpillar Management",
                content = "Soybean Insect Defense: 1) Girdle Beetle (Obereopsis brevis): Females make two parallel circular girdles on stems; prune and destroy girdled petioles early. 2) Semiloopers & Spodoptera: Install pheromone traps @ 5/acre. Chemical intervention: Spray Chlorantraniliprole 18.5% SC @ 60 ml/acre or Flubendiamide 39.35% SC @ 40 ml/acre diluted in 200 L water. Pre-Harvest Interval (PHI): 21 days. Do not exceed 2 applications per crop cycle.",
                sourceCitation = "ICAR-Indian Institute of Soybean Research (IISR) Indore & PJTSAU",
                language = "en",
                lastVerifiedDate = "2026-11-09"
            )
        )
        list.add(
            OfflineKnowledgeEntity(
                id = "icar-syb-pest-hi",
                collectionId = "KB04",
                crop = "Soybean",
                category = "pest_management",
                title = "सोयाबीन: गर्डल बीटल (चक्र भृंग) एवं सेमीलूपर कीट नियंत्रण",
                content = "सोयाबीन कीट नियंत्रण: 1) चक्र भृंग (गर्डल बीटल): मादा तने या डंठल पर दो छल्लेनुमा चक्र बनाती है; ग्रसित शाखाओं को काटकर नष्ट करें। 2) सेमीलूपर व तंबाकू सुंडी: फेरोमोन ट्रैप 5 प्रति एकड़ लगाएं। रासायनिक उपाय: क्लोरेंट्रानिलिप्रोल 18.5% एससी 60 मिली अथवा फ्लूबेन्डियामाइड 39.35% एससी 40 मिली प्रति एकड़ 200 लीटर पानी में घोलकर छिड़कें। तुड़ाई पूर्व प्रतीक्षा अवधि (PHI) 21 दिन।",
                sourceCitation = "ICAR-Indian Institute of Soybean Research (IISR) Indore & PJTSAU",
                language = "hi",
                lastVerifiedDate = "2026-11-09"
            )
        )
        list.add(
            OfflineKnowledgeEntity(
                id = "icar-syb-pest-te",
                collectionId = "KB04",
                crop = "Soybean",
                category = "pest_management",
                title = "సోయాబీన్: చక్రపు పురుగు మరియు లద్దె పురుగుల సమగ్ర నివారణ",
                content = "సోయాబీన్ సస్యరక్షణ: 1) చక్రపు పురుగు (Girdle beetle): కొమ్మలపై వలయాకారపు గాట్లు పెట్టి గుడ్లు పెడుతుంది. గాయపడిన కొమ్మలను తుంచి నాశనం చేయాలి. 2) లద్దె పురుగులు (Semilooper & Spodoptera): ఆకులను జల్లెడలా మారుస్తాయి. నివారణ: ఎకరాకు 4-5 లింగాకర్షక బుట్టలు పెట్టాలి. రసాయన చికిత్స: క్లోరాంట్రానిలిప్రోల్ 18.5% SC ఎకరాకు 60 మి.లీ లేదా ఫ్లూబెండియమైడ్ 39.35% SC ఎకరాకు 40 మి.లీ 200 లీటర్ల నీటిలో కలిపి పిచికారీ చేయాలి. నిరీక్షణ కాలం 21 రోజులు.",
                sourceCitation = "ICAR-Indian Institute of Soybean Research (IISR) Indore & PJTSAU",
                language = "te",
                lastVerifiedDate = "2026-11-09"
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
                lastVerifiedDate = "2026-11-09"
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
                lastVerifiedDate = "2026-11-09"
            )
        )
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
                lastVerifiedDate = "2026-11-09"
            )
        )
        list.add(
            OfflineKnowledgeEntity(
                id = "icar-tom-borer-en",
                collectionId = "KB05",
                crop = "Tomato",
                category = "pest_management",
                title = "Tomato: Fruit Borer (Helicoverpa) & Pinworm (Tuta absoluta) IPM & CIBRC Controls",
                content = "Crop & Pests: Tomato - Fruit Borer (Helicoverpa armigera) & South American Pinworm (Tuta absoluta). Growth Stages & Symptoms: Flowering to fruit harvesting (40-100 DAS). Helicoverpa: Larvae feed on foliage, bore circular holes into green/ripe tomatoes, feeding with head inside fruit causing secondary fungal rot. Tuta absoluta: Larvae create blotchy translucent leaf mines, bore pinholes into stem buds and enter green tomatoes right beneath the calyx, producing frass-filled necrotic cavities. ETL: Helicoverpa: 1 larva/meter row or 5% damaged fruits; Tuta: 3-4 moths/trap/day. IPM: 1) African Marigold (Tagetes erecta) trap cropping: Plant 1 row of marigold for every 16 rows of tomato; borers oviposit preferentially on marigold flowers. 2) Pheromone traps @ 5/acre (Helilure / Tuta lure) for monitoring. 3) Release egg parasitoid Trichogramma pretiosum @ 50,000/acre weekly 4 times from flowering. 4) Spray 5% NSKE or Bacillus thuringiensis (Bt) @ 400 g/acre. CIBRC Approved Chemical Control: Chlorantraniliprole 18.5% SC (Coragen) @ 60 ml/acre (approx 4.5 ml per 15L backpack pump in 200L water), Pre-Harvest Interval (PHI): 3 days; OR Spinosad 45% SC (Tracer) @ 64 ml/acre (approx 4.8 ml per 15L pump), PHI: 3 days; OR Flubendiamide 39.35% SC (Fame) @ 48 ml/acre (approx 3.6 ml per 15L pump), PHI: 5 days. Safety: Strictly observe 3-day PHI before harvesting fresh market tomatoes.",
                sourceCitation = "ICAR-IIHR Bengaluru & CIBRC Approved Vegetable Label Register 2024",
                language = "en",
                lastVerifiedDate = "2026-11-09"
            )
        )
        list.add(
            OfflineKnowledgeEntity(
                id = "icar-tom-borer-hi",
                collectionId = "KB05",
                crop = "Tomato",
                category = "pest_management",
                title = "टमाटर: फल छेदक (हेलिकोवर्पा) एवं पिनवर्म समेकित प्रबंधन, गेंदा ट्रैप फसल एवं कीटनाशक",
                content = "फसल व कीट: टमाटर - फल छेदक सुंडी (हेलिकोवर्पा आर्मिगेरा) एवं पिनवर्म (टूटा एब्सोल्यूटा)। अवस्थाएं एवं लक्षण: फूल आने से फल तुड़ाई तक (40-100 दिन)। फल छेदक सुंडी फलों में गोल छेद बनाकर आधा शरीर अंदर घुसाकर गूदा खाती है। पिनवर्म पत्तियों में पारदर्शी सुरंगें बनाता है तथा डंठल के पास छेद कर फलों को सड़ा देता है। आर्थिक क्षति स्तर (ETL): 1 सुंडी प्रति मीटर कतार या 5% क्षतिग्रस्त फल। समेकित प्रबंधन (IPM): 1) गेंदा ट्रैप फसल: टमाटर की प्रत्येक 16 कतारों के बाद 1 कतार अफ्रीकन गेंदे की लगाएं; कीट गेंदे के फूलों पर अंडे देते हैं। 2) 5 फेरोमोन ट्रैप प्रति एकड़ लगाएं। 3) ट्राइकोग्रामा प्रिटिओसम 50,000 प्रति एकड़ की दर से फूल आने पर 4 बार छोड़ें। 4) 5% नीम बीज अर्क (एनएसकेई) का छिड़काव करें। CIBRC अनुशंसित रासायनिक नियंत्रण: क्लोरेंट्रानिलिप्रोल 18.5% एससी (कोराजन) 60 मिली प्रति एकड़ (लगभग 4.5 मिली प्रति 15 लीटर नैपसैक पंप; 200 लीटर पानी में), प्रतीक्षा अवधि (PHI): 3 दिन; अथवा स्पिनोसैड 45% एससी (ट्रेसर) 64 मिली प्रति एकड़ (लगभग 4.8 मिली प्रति 15 लीटर पंप), प्रतीक्षा अवधि: 3 दिन; अथवा फ्लूबेंडियामाइड 39.35% एससी (फेम) 48 मिली प्रति एकड़ (लगभग 3.6 मिली प्रति 15 लीटर पंप), प्रतीक्षा अवधि: 5 दिन। सुरक्षा: फल तुड़ाई से पहले 3 दिन की प्रतीक्षा अवधि (PHI) का कड़ाई से पालन करें।",
                sourceCitation = "भाकृअनुप-भारतीय बागवानी अनुसंधान संस्थान (ICAR-IIHR) एवं CIBRC 2024",
                language = "hi",
                lastVerifiedDate = "2026-11-09"
            )
        )
        list.add(
            OfflineKnowledgeEntity(
                id = "icar-tom-borer-te",
                collectionId = "KB05",
                crop = "Tomato",
                category = "pest_management",
                title = "టమోటా: కాయ తొలుచు పురుగు (హెలికోవెర్ప) మరియు పిన్ వార్మ్ సమగ్ర నివారణ, బంతి ఆకర్షణ పంట",
                content = "పంట మరియు సమస్య: టమోటా - కాయ తొలుచు పురుగు (Helicoverpa armigera) మరియు పిన్ వార్మ్ (Tuta absoluta). దశలు మరియు లక్షణాలు: పూత నుండి కాయ పక్వానికి వచ్చే దశ (40-100 రోజులు). హెలికోవెర్ప లద్దె పురుగులు కాయలపై గుండ్రటి రంధ్రాలు చేసి సగం శరీరాన్ని లోపలికి చొప్పించి గుజ్జును తింటాయి. పిన్ వార్మ్ ఆకులపై తెల్లటి పొరల వంటి మచ్చలు చేసి, కాయ తొడిమల కింద సన్నటి రంధ్రాలు చేసి లోపలికి ప్రవేశిస్తాయి. ఆర్థిక నష్ట పరిమితి (ETL): వరుస మీటరుకు 1 పురుగు లేదా 5% దెబ్బతిన్న కాయలు. సమగ్ర నివారణ (IPM): 1) బంతి పూల ఆకర్షణ పంట: ప్రతి 16 వరుసల టమోటాకు ఒక వరుస ఎర్ర బంతి మొక్కలు నాటాలి; పురుగులు బంతి పూలపై గుడ్లు పెడతాయి. 2) ఎకరాకు 5 లింగాకర్షక బుట్టలు అమర్చాలి. 3) పూత దశ నుండి ట్రైకోగ్రామా పరాన్నజీవులను ఎకరాకు 50,000 చొప్పున 4 సార్లు వదలాలి. 4) 5% వేప గింజల కషాయం పిచికారీ చేయాలి. CIBRC ఆమోదిత రసాయన నివారణ: క్లోరాంట్రానిలిప్రోల్ 18.5% SC (కొరాజెన్) ఎకరాకు 60 మి.లీ (15 లీటర్ల చేతి పంపుకు 4.5 మి.లీ; 200 లీటర్ల నీటిలో), వేచి ఉండే కాలం (PHI): 3 రోజులు; లేదా స్పైనోసాడ్ 45% SC (ట్రేసర్) ఎకరాకు 64 మి.లీ (15 లీటర్ల పంపుకు 4.8 మి.లీ), వేచి ఉండే కాలం: 3 రోజులు; లేదా ఫ్లూబెండియమైడ్ 39.35% SC (ఫేమ్) ఎకరాకు 48 మి.లీ (15 లీటర్ల పంపుకు 3.6 మి.లీ), వేచి ఉండే కాలం: 5 రోజులు. జాగ్రత్తలు: కాయలు కోసే ముందు కనీసం 3 రోజుల వేచి ఉండే కాలం పాటించాలి.",
                sourceCitation = "ICAR-IIHR బెంగళూరు & ప్రొఫెసర్ జయశంకర్ తెలంగాణ వ్యవసాయ విశ్వవిద్యాలయం (PJTSAU) 2024",
                language = "te",
                lastVerifiedDate = "2026-11-09"
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
                lastVerifiedDate = "2026-11-09"
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
                lastVerifiedDate = "2026-11-09"
            )
        )
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
                lastVerifiedDate = "2026-11-09"
            )
        )
        list.add(
            OfflineKnowledgeEntity(
                id = "icar-tom-tolcv-en",
                collectionId = "KB04",
                crop = "Tomato",
                category = "disease",
                title = "Tomato: Leaf Curl Virus (ToLCV) & Whitefly Vector Management",
                content = "Crop & Disease: Tomato (Solanum lycopersicum) - Tomato Leaf Curl Virus (ToLCV - Begomovirus) transmitted by Whitefly (Bemisia tabaci). Growth Stages & Symptoms: Nursery to vegetative stage (15-50 DAT). Severe stunting of plants, downward/upward curling, crinkling and puckering of leaves, vein clearing and marginal chlorosis (yellowing), thickened leathery leaves, shortened internodes creating a bushy dwarf appearance; complete failure of flower set or fruit drop. Vector Dynamics: Transmitted persistently by whitefly adults within 30 minutes of feeding; hot and dry weather favors whitefly multiplication. IPM: 1) Raise nursery under 40-mesh nylon insect-proof netting. 2) Plant 3 barrier border rows of maize or sorghum 20 days prior to transplanting. 3) Install 20 yellow sticky traps/acre at canopy height. 4) Rogue out and deeply bury virus-infected seedlings within first 30 days. 5) Mulching with silver-reflective plastic sheets repels whiteflies. CIBRC Chemical Vector Control: Cyantraniliprole 10.26% OD (Benevia) @ 360 ml/acre (approx 27 ml per 15L backpack pump in 200L water), Pre-Harvest Interval (PHI): 3 days; OR Diafenthiuron 50% WP (Pegasus) @ 240 g/acre (approx 18 g per 15L pump), PHI: 7 days; OR Imidacloprid 17.8% SL @ 60 ml/acre (approx 4.5 ml per 15L pump), PHI: 5 days. PPE: Wear face shield and nitrile gloves.",
                sourceCitation = "ICAR-IIHR Bengaluru Tomato Protection Guide & PJTSAU 2024",
                language = "en",
                lastVerifiedDate = "2026-11-09"
            )
        )
        list.add(
            OfflineKnowledgeEntity(
                id = "icar-tom-tolcv-hi",
                collectionId = "KB04",
                crop = "Tomato",
                category = "disease",
                title = "टमाटर: पर्ण कुंचन विषाणु (लीफ कर्ल वायरस / ToLCV) एवं सफेद मक्खी नियंत्रण",
                content = "फसल व रोग: टमाटर - लीफ कर्ल वायरस (पत्ती मरोड़ रोग - बेगोमोवायरस) एवं वाहक सफेद मक्खी। अवस्थाएं एवं लक्षण: पौधशाला से 15-50 दिन तक। पौधे बौने रह जाते हैं, पत्तियां ऊपर-नीचे मुड़कर ऐंठ जाती हैं और खुरदरी हो जाती हैं, नसों के बीच पीलापन आ जाता है, फूल नहीं बनते या फल बनने से पहले गिर जाते हैं। रोग वाहक: यह रोग सफेद मक्खी द्वारा फैलता है। समेकित प्रबंधन (IPM): 1) पौधशाला को 40-जाली (मेश) वाले नायलॉन नेट से ढककर तैयार करें। 2) खेत की मेड़ों पर मक्का या ज्वार की 3 कतारें लगाएं। 3) प्रति एकड़ 20 पीले चिपचिपे कार्ड लगाएं। 4) शुरुआती ग्रसित पौधों को उखाड़कर जमीन में गहरा दबा दें। 5) खेत में सिल्वर मल्चिंग शीट बिछाएं। CIBRC अनुशंसित रासायनिक नियंत्रण: साएंट्रानिलिप्रोल 10.26% ओडी (बेनेविया) 360 मिली प्रति एकड़ (लगभग 27 मिली प्रति 15 लीटर नैपसैक पंप; 200 लीटर पानी में), प्रतीक्षा अवधि (PHI): 3 दिन; अथवा डायफेंथियूरॉन 50% डब्ल्यूपी 240 ग्राम प्रति एकड़ (लगभग 18 ग्राम प्रति 15 लीटर पंप), प्रतीक्षा अवधि: 7 दिन; अथवा इमिडाक्लोप्रिड 17.8% एसएल 60 मिली प्रति एकड़ (4.5 मिली प्रति 15 लीटर पंप), प्रतीक्षा अवधि: 5 दिन। सुरक्षा सावधानियां: दस्ताने और चेहरे का मास्क पहनें।",
                sourceCitation = "भाकृअनुप-भारतीय बागवानी अनुसंधान संस्थान (ICAR-IIHR) एवं CIBRC 2024",
                language = "hi",
                lastVerifiedDate = "2026-11-09"
            )
        )
        list.add(
            OfflineKnowledgeEntity(
                id = "icar-tom-tolcv-te",
                collectionId = "KB04",
                crop = "Tomato",
                category = "disease",
                title = "టమోటా: ఆకుముడుత వైరస్ తెగులు (ToLCV) మరియు తెల్లదోమ సమగ్ర నివారణ పద్ధతులు",
                content = "పంట మరియు వ్యాధి: టమోటా - టమోటా ఆకుముడుత వైరస్ (ToLCV) మరియు తెల్లదోమ (Bemisia tabaci). దశలు మరియు లక్షణాలు: నారుమడి నుండి నాటిన 15-50 రోజుల వరకు. మొక్కల పెరుగుదల ఆగిపోయి గిడసబారడం, ఆకులు పైకి కిందికి ముడుచుకుపోయి ముడతలు పడటం, ఈనెల మధ్య పసుపు రంగులోకి మారడం, ఆకులు మందంగా మారి రబ్బరులా మారడం, పూత పిందె రాలిపోయి కాయలు కాయకపోవడం. వ్యాప్తి: తెల్లదోమ రసం పీల్చడం ద్వారా వైరస్ ను ఒక మొక్క నుండి మరొక మొక్కకు వ్యాపింపజేస్తుంది. సమగ్ర నివారణ (IPM): 1) నారుమడిని 40 మెష్ నైలాన్ రక్షణ వల కింద పెంచాలి. 2) ప్రధాన పొలం చుట్టూ 3 వరుసల మొక్కజొన్న లేదా జొన్న రక్షణ పంటగా నాటాలి. 3) ఎకరాకు 20 పసుపు జిగురు అట్టలు అమర్చాలి. 4) తెగులు సోకిన మొక్కలను ప్రారంభ దశలోనే పీకి భూమిలో పాతిపెట్టాలి. 5) సిల్వర్ రంగు ప్లాస్టిక్ మల్చింగ్ వాడటం వల్ల తెల్లదోమలు రావు. CIBRC ఆమోదిత రసాయన నివారణ: సయాంట్రానిలిప్రోల్ 10.26% OD (బెనెవియా) ఎకరాకు 360 మి.లీ (15 లీటర్ల చేతి పంపుకు 27 మి.లీ; 200 లీటర్ల నీటిలో), వేచి ఉండే కాలం (PHI): 3 రోజులు; లేదా డయాఫెంథియురాన్ 50% WP ఎకరాకు 240 గ్రా (15 లీటర్ల పంపుకు 18 గ్రా), వేచి ఉండే కాలం: 7 రోజులు; లేదా ఇమిడాక్లోప్రిడ్ 17.8% SL ఎకరాకు 60 మి.లీ (15 లీటర్ల పంపుకు 4.5 మి.లీ), వేచి ఉండే కాలం: 5 రోజులు. జాగ్రత్తలు: చేతి తొడుగులు, మాస్క్ ధరించాలి.",
                sourceCitation = "ICAR-IIHR బెంగళూరు & ప్రొఫెసర్ జయశంకర్ తెలంగాణ వ్యవసాయ విశ్వవిద్యాలయం (PJTSAU) 2024",
                language = "te",
                lastVerifiedDate = "2026-11-09"
            )
        )
        list.add(
            OfflineKnowledgeEntity(
                id = "icar-tur-rot-en",
                collectionId = "KB04",
                crop = "Turmeric",
                category = "disease",
                title = "Turmeric: Rhizome Rot (Pythium) Integrated Management",
                content = "Turmeric Rhizome Rot Management: 1) Drainage: Plant on raised beds or ridges to avoid water stagnation. 2) Rhizome seed treatment: Soak seed rhizomes in Mancozeb 75% WP @ 3 g/L or Copper Oxychloride 50% WP @ 3 g/L water for 30 minutes before planting. 3) Soil application: Mix 2 kg Trichoderma harzianum with 200 kg neem cake per acre and apply near root zones. 4) Drenching: At first sign of yellowing, drench base with Metalaxyl 8% + Mancozeb 64% WP @ 2 g/L water. PHI: 40 days.",
                sourceCitation = "ICAR-Indian Institute of Spices Research (IISR) Kozhikode & PJTSAU Turmeric Advisory",
                language = "en",
                lastVerifiedDate = "2026-11-09"
            )
        )
        list.add(
            OfflineKnowledgeEntity(
                id = "icar-tur-rot-hi",
                collectionId = "KB04",
                crop = "Turmeric",
                category = "disease",
                title = "हल्दी: कंद सड़न (राइजोम रॉट) रोग नियंत्रण एवं बीज कंद उपचार",
                content = "हल्दी कंद सड़न (पिथियम) प्रबंधन: 1) जल निकासी: मेड़ या ऊंची क्यारियों पर बुवाई करें ताकि पानी न ठहरे। 2) कंद उपचार: बुवाई से पूर्व कंदों को मैंकोजेब 3 ग्राम/लीटर या कॉपर ऑक्सीक्लोराइड 3 ग्राम/लीटर पानी के घोल में 30 मिनट डुबोकर सुखाएं। 3) जैविक उपाय: प्रति एकड़ 200 किग्रा नीम की खली में 2 किग्रा ट्राइकोडर्मा मिलाकर जड़ों के पास डालें। 4) ड्रेंचिंग: लक्षण दिखते ही मेटालैक्सिल 8% + मैंकोजेब 64% WP (रिडोमिल) 2 ग्राम प्रति लीटर पानी का पौधों की जड़ों में छिड़काव व जमाव (ड्रेंचिंग) करें।",
                sourceCitation = "ICAR-Indian Institute of Spices Research (IISR) Kozhikode & PJTSAU Turmeric Advisory",
                language = "hi",
                lastVerifiedDate = "2026-11-09"
            )
        )
        list.add(
            OfflineKnowledgeEntity(
                id = "icar-tur-rot-te",
                collectionId = "KB04",
                crop = "Turmeric",
                category = "disease",
                title = "పసుపు: దుంపకుళ్ళు తెగులు నివారణ మరియు విత్తన దుంపల శుద్ధి",
                content = "పసుపు దుంపకుళ్ళు తెగులు (పిథియం): నీరు నిలిచే నేలల్లో ఆకులు పసుపుబారి మొదళ్లు మెత్తబడి కుళ్లిపోతాయి. నివారణ: 1) మురుగునీటి పారుదల: బోదెలు, సాళ్ళ పద్ధతిలో సాగు చేసి నీరు నిల్వకుండా చూడాలి. 2) విత్తన దుంపల శుద్ధి: నాటేముందు దుంపలను మాంకోజెబ్ 3 గ్రా/లీ లేదా కాపర్ ఆక్సిక్లోరైడ్ 3 గ్రా/లీ నీటిలో 30 నిమిషాలు నానబెట్టి ఆరబెట్టాలి. 3) జీవ నియంత్రణ: ఎకరాకు 2 కిలోల ట్రైకోడెర్మా హార్జియానమ్ ను 200 కిలోల వేపపిండితో కలిపి దుంపల వద్ద వేయాలి. తెగులు వస్తే మెటలాక్సిల్ 8% + మ్యాంకోజెబ్ 64% WP (రిడోమిల్) 2 గ్రా/లీ నీటితో దుంపల మొదళ్ళ వద్ద తడపాలి (Drenching).",
                sourceCitation = "ICAR-Indian Institute of Spices Research (IISR) Kozhikode & PJTSAU Turmeric Advisory",
                language = "te",
                lastVerifiedDate = "2026-11-09"
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
                lastVerifiedDate = "2026-11-09"
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
                lastVerifiedDate = "2026-11-09"
            )
        )
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
                lastVerifiedDate = "2026-11-09"
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
                lastVerifiedDate = "2026-11-09"
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
                lastVerifiedDate = "2026-11-09"
            )
        )
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
                lastVerifiedDate = "2026-11-09"
            )
        )
        list.add(
            OfflineKnowledgeEntity(
                id = "icar-wht-weed-en",
                collectionId = "KB13",
                crop = "Wheat",
                category = "weed_management",
                title = "Wheat: Phalaris minor (Canary Grass / Mandusi) Weed Management & CIBRC Herbicides",
                content = "Crop & Target Weed: Wheat (Triticum aestivum) - Canary Grass / Little Seed Canary Grass (Phalaris minor / Mandusi / Gulli Danda). Critical Competition Period & Nature of Damage: 20 to 45 DAS. Phalaris minor is an aggressive grass weed that closely mimics wheat seedlings in early vegetative stages (can be identified by its reddish-pink basal stem pigmentation when uprooted). Produces 2,000-3,000 seeds per plant; causes severe lodging and 30-70% reduction in grain yield in rice-wheat cropping systems. Cultural & Agronomic IPM: 1) Adopt Zero Tillage / Happy Seeder sowing directly into rice stubble; zero-till drastically suppresses Phalaris seed emergence. 2) Criss-cross sowing at closer row spacing (18 cm) for rapid crop canopy closure. 3) Crop rotation with Mustard, Potato, Berseem or Sunflower to break weed seed bank. CIBRC Approved Chemical Control: Clodinafop-propargyl 15% WP (Topik) @ 160 g/acre (approx 12.0 g per 15L backpack pump in 150L water), Pre-Harvest Interval (PHI): 60 days; OR Sulfosulfuron 75% WG @ 13.5 g/acre (approx 1.35 g per 15L pump), PHI: 60 days; OR Pinoxaden 5.1% EC @ 400 ml/acre (approx 40 ml per 15L pump), PHI: 60 days. Apply strictly at 30-35 DAS (2 to 3 weed leaf stage) using flat fan nozzle in moist soil. Safety: Never apply under severe soil drought. Wear rubber boots and eye goggles.",
                sourceCitation = "ICAR-Indian Institute of Wheat and Barley Research (IIWBR) Karnal & ICAR-DWR Jabalpur 2024",
                language = "en",
                lastVerifiedDate = "2026-11-09"
            )
        )
        list.add(
            OfflineKnowledgeEntity(
                id = "icar-wht-weed-hi",
                collectionId = "KB13",
                crop = "Wheat",
                category = "weed_management",
                title = "गेहूं: गुल्ली डंडा (मंडूसी / फैलेरिस माइनर) खरपतवार नियंत्रण एवं CIBRC शाकनाशी",
                content = "फसल व खरपतवार: गेहूं - मंडूसी / गुल्ली डंडा / गेहूं का मामा (फैलेरिस माइनर)। क्रांतिक अवस्था एवं नुकसान: बुवाई के 20-45 दिन। यह खरपतवार प्रारंभिक अवस्था में बिल्कुल गेहूं जैसा दिखता है (जड़ के पास तने का रंग गुलाबी देखकर पहचाना जाता है)। एक पौधा 2,000-3,000 बीज बनाता है और पैदावार में 30-70% तक की भारी गिरावट लाता है। सस्य क्रियाएं (IPM): 1) धान के अवशेषों में जीरो टिलेज (हैप्पी सीडर) से बुवाई करने पर मंडूसी का जमाव बहुत कम होता है। 2) संकरी कतारों (18 सेमी) में सघन बुवाई करें। 3) सरसों, आलू अथवा बरसीम के साथ फसल चक्र अपनाएं। CIBRC पंजीकृत शाकनाशी उपचार: क्लोडिनाफॉप-प्रोपारगिल 15% डब्ल्यूपी (टॉपिक) 160 ग्राम प्रति एकड़ (लगभग 12.0 ग्राम प्रति 15 लीटर नैपसैक पंप; 150 लीटर पानी में), प्रतीक्षा अवधि (PHI): 60 दिन; अथवा सल्फोसल्फ्यूरॉन 75% डब्ल्यूजी 13.5 ग्राम प्रति एकड़ (लगभग 1.35 ग्राम प्रति 15 लीटर पंप), प्रतीक्षा अवधि: 60 दिन; अथवा पिनोक्साडेन 5.1% ईसी 400 मिली प्रति एकड़ (40 मिली प्रति 15 लीटर पंप), प्रतीक्षा अवधि: 60 दिन। बुवाई के 30-35 दिन बाद 2-3 पत्ती अवस्था में फ्लैट फैन नोजल से पर्याप्त नमी में छिड़कें। सुरक्षा सावधानियां: सूखे खेत में कभी न छिड़कें; दस्ताने व चश्मा पहनें।",
                sourceCitation = "भाकृअनुप-भारतीय गेहूं एवं जौ अनुसंधान संस्थान (ICAR-IIWBR) करनाल एवं खरपतवार अनुसंधान निदेशालय (DWR) 2024",
                language = "hi",
                lastVerifiedDate = "2026-11-09"
            )
        )
        list.add(
            OfflineKnowledgeEntity(
                id = "icar-wht-weed-te",
                collectionId = "KB13",
                crop = "Wheat",
                category = "weed_management",
                title = "గోధుమ: గుల్లీ దండా (ఫలారిస్ మైనర్) గడ్డి కలుపు యాజమాన్యం మరియు కలుపు మందులు",
                content = "పంట మరియు కలుపు సమస్య: గోధుమ - గుల్లీ దండా / చిన్న విత్తనాల కెనరీ గడ్డి (Phalaris minor). కీలక దశ & నష్టం: విత్తిన 20-45 రోజులు. ఈ గడ్డి గోధుమ మొక్కల మాదిరిగానే ఉండి మొదట్లో గుర్తించడం కష్టం (వేళ్లతో సహా పీకితే కాండం మొదలు గులాబీ రంగులో ఉంటుంది). ఒక మొక్క 2,000-3,000 విత్తనాలను ఉత్పత్తి చేసి పంట దిగుబడిని 30-70% తగ్గిస్తుంది. యాజమాన్య పద్ధతులు (IPM): 1) జీరో టిల్లేజ్ / హ్యాపీ సీడర్ పద్ధతిలో వరి కొయ్యల్లో నేరుగా విత్తడం వల్ల ఈ కలుపు మొలకెత్తడం తగ్గుతుంది. 2) అడ్డు-నిలువుగా దగ్గరి వరుసల్లో (18 సెం.మీ) విత్తాలి. 3) ఆవాలు లేదా బంగాళాదుంపతో పంట మార్పిడి చేయాలి. CIBRC ఆమోదిత కలుపు మందులు: క్లోడినాఫాప్-ప్రొపార్గిల్ 15% WP (టోపిక్) ఎకరాకు 160 గ్రా (15 లీటర్ల చేతి పంపుకు 12 గ్రా; 150 లీటర్ల నీటిలో), వేచి ఉండే కాలం (PHI): 60 రోజులు; లేదా సల్ఫోసల్ఫ్యూరాన్ 75% WG ఎకరాకు 13.5 గ్రా (15 లీటర్ల పంపుకు 1.35 గ్రా), వేచి ఉండే కాలం: 60 రోజులు; లేదా పినాక్సాడెన్ 5.1% EC ఎకరాకు 400 మి.లీ (15 లీటర్ల పంపుకు 40 మి.లీ), వేచి ఉండే కాలం: 60 రోజులు. విత్తిన 30-35 రోజులకు నేలలో తగినంత తేమ ఉన్నప్పుడు ఫ్లాట్ ఫ్యాన్ నాజిల్‌తో పిచికారీ చేయాలి. జాగ్రత్తలు: కళ్లజోడు మరియు చేతి తొడుగులు ధరించాలి.",
                sourceCitation = "ICAR-IIWBR కర్నాల్ & ICAR-డైరెక్టరేట్ ఆఫ్ వీడ్ రీసెర్చ్ (DWR) జబల్‌పూర్ 2024",
                language = "te",
                lastVerifiedDate = "2026-11-09"
            )
        )
        return list
    }
}
