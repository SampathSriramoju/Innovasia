# BOL KISAN – Multilingual Agricultural Vocabulary & Agrochemical Safety Protocol
**System:** Bol Kisan (बोल किसान / బోల్ కిసాన్)  
**Date:** November 2026  
**Auditor:** Indic NLP Lexicography & Agricultural Safety Team  
**Coverage:** 8 Major Indian Crops, 42 Plant Diseases, 30 Common Insect Pests, 25 CIBRC Approved Formulations, Regional Dialects & Ambiguity Protocols

---

## 1. Executive Summary

Rural Indian agriculture employs an intricate linguistic lexicon blending native Indic terms, English transliterations of commercial brand names, regional colloquialisms, and technical formulation codes. Standard commercial ASR models struggle with this vocabulary because:
1. Agrochemical active ingredients are multisyllabic Latin/chemical compound words (e.g. *Chlorantraniliprole*, *Emamectin Benzoate*, *Tricyclazole*).
2. Farmers frequently ask for commercial brand names phonetically adapted into regional phonology (e.g. *Coragen* $\to$ కోరాజెన్ / कोराजन, *Curacron* $\to$ కురాక్రాన్ / क्यूराक्रॉन).
3. Minor phonetic variations can transform an insecticidal recommendation into an unapproved or lethal application (e.g. confusing 5% SG with 50% EC or mistaking *Coragen* for *Curacron*).

This report registers the complete verified multilingual vocabulary for Bol Kisan, maps regional dialects, and establishes the **Mandatory Chemical Ambiguity Confirmation Protocol**.

---

## 2. Crop Master Register (8 Major Indian Crops)

| # | Crop English Name | Botanical Binomial | Telugu (తెలుగు) | Hindi (हिन्दी) | Common Regional Dialect Names |
| :-: | :--- | :--- | :--- | :--- | :--- |
| **1** | **Rice / Paddy** | *Oryza sativa* | **వరి / ధాన్యం** | **धान / चावल** | వరిచేను, నరి, చావల, బాస్మతి |
| **2** | **Maize / Corn** | *Zea mays* | **మొక్కజొన్న** | **मक्का / भुट्टा** | మక్కజొన్న, మక్కలు, బుట్టా, జోన |
| **3** | **Cotton** | *Gossypium hirsutum* | **ప్రత్తి / పత్తి** | **कपास / रूई** | దూది, పత్తిచేను, కాటన్ |
| **4** | **Chilli / Pepper** | *Capsicum annuum* | **మిరప** | **मिर्च** | మిర్చి, పచ్చిమిరప, ఎండుమిర్చి |
| **5** | **Groundnut / Peanut**| *Arachis hypogaea* | **వేరుశనగ** | **मूंगफली** | పల్లీలు, భూతొడ, బుక్కలు |
| **6** | **Redgram / Pigeonpea**| *Cajanus cajan* | **కందులు** | **अरहर / तूर** | కందిపంట, తువర్, రాహర్ |
| **7** | **Soybean** | *Glycine max* | **సోయాబీన్** | **सोयाबीन** | సోయా, భట్ |
| **8** | **Tomato** | *Solanum lycopersicum* | **టమోటా** | **टमाटर** | రామ్ టమోటా, వెలగపండు |

---

## 3. Crop Disease Master Register (42 Verified Diseases)

| # | Crop | Disease English Name | Pathogen / Causative Agent | Telugu Name | Hindi Name |
| :-: | :--- | :--- | :--- | :--- | :--- |
| **1** | Rice | **Blast** | *Magnaporthe oryzae* | **అగ్గితెగులు** | **झुलसा रोग** |
| **2** | Rice | **Sheath Blight** | *Rhizoctonia solani* | **పాముపొడ తెగులు** | **शीथ ब्लाइट** |
| **3** | Rice | **Bacterial Leaf Blight** | *Xanthomonas oryzae* | **బాక్టీరియా ఆకు ఎండు తెగులు** | **जीवाणु पत्ती झुलसा** |
| **4** | Rice | **Brown Spot** | *Bipolaris oryzae* | **గోధుమరంగు ఆకుమచ్చ** | **भूरा धब्बा रोग** |
| **5** | Rice | **False Smut** | *Ustilaginoidea virens* | **కాటుక తెగులు** | **झूठा कंडुआ** |
| **6** | Rice | **Sheath Rot** | *Sarocladium oryzae* | **పొట్టకుళ్లు తెగులు** | **शीथ रॉट** |
| **7** | Maize | **Turcicum Leaf Blight** | *Exserohilum turcicum* | **తుర్సికం ఆకుమచ్చ తెగులు** | **तुर्सिकम पत्ती झुलसा** |
| **8** | Maize | **Maydis Leaf Blight** | *Bipolaris maydis* | **మైడిస్ ఆకుమచ్చ తెగులు** | **मायडिस पत्ती झुलसा** |
| **9** | Maize | **Common Rust** | *Puccinia sorghi* | **తుప్పు తెగులు** | **गेरुआ / रतुआ** |
| **10**| Maize | **Banded Leaf & Sheath Blight**| *Rhizoctonia solani* | **బట్టల తెగులు / పట్టీల తెగులు** | **बैंडेड पत्ती झुलसा** |
| **11**| Maize | **Charcoal Rot** | *Macrophomina phaseolina* | **బొగ్గు కుళ్లు తెగులు** | **चारकोल सड़न** |
| **12**| Maize | **Post Flowering Stalk Rot**| *Fusarium verticillioides* | **కాండం కుళ్లు తెగులు** | **तना सड़न रोग** |
| **13**| Cotton| **Bacterial Blight / Angular Leaf Spot**| *Xanthomonas citri pv. malvacearum*| **బాక్టీరియా ఆకుమచ్చ తెగులు** | **जीवाणु झुलसा** |
| **14**| Cotton| **Alternaria Leaf Spot** | *Alternaria macrospora* | **ఆల్టర్నేరియా ఆకుమచ్చ** | **अल्टरनेरिया धब्बा** |
| **15**| Cotton| **Cercospora Leaf Spot** | *Cercospora gossypina* | **సర్కోస్పోరా ఆకుమచ్చ** | **टिक्का / पत्ती धब्बा** |
| **16**| Cotton| **Fusarium Wilt** | *Fusarium oxysporum f. sp. vasinfectum*| **ఎండు తెగులు** | **उकठा / विल्ट** |
| **17**| Cotton| **Grey Mildew / Dahiya** | *Ramularia gossypii* | **దూది తెగులు / బూడిద తెగులు** | **दहिया रोग** |
| **18**| Cotton| **Root Rot** | *Rhizoctonia bataticola* | **వేరు కుళ్లు తెగులు** | **जड़ सड़न** |
| **19**| Chilli| **Anthracnose / Fruit Rot / Dieback**| *Colletotrichum capsici* | **కొమ్మ ఎండు మరియు కాయ కుళ్లు** | **फल सड़न / डाईबैक** |
| **20**| Chilli| **Powdery Mildew** | *Leveillula taurica* | **బూడిద తెగులు** | **चूर्णिल आसिता** |
| **21**| Chilli| **Cercospora Leaf Spot** | *Cercospora capsici* | **ఆకుమచ్చ తెగులు** | **पत्ती धब्बा रोग** |
| **22**| Chilli| **Bacterial Leaf Spot** | *Xanthomonas campestris pv. vesicatoria*| **బాక్టీరియా ఆకుమచ్చ తెగులు** | **जीवाणु पत्ती धब्बा** |
| **23**| Chilli| **Chilli Leaf Curl Virus (ChiLCV)**| *Begomovirus* (Whitefly vector) | **ఆకుముడత తెగులు (జెమిని వైరస్)**| **पत्ती मरोड़ रोग** |
| **24**| Chilli| **Fusarium Wilt** | *Fusarium solani* | **ఎండు తెగులు** | **उकठा रोग** |
| **25**| Groundnut| **Early Leaf Spot (Tikka)**| *Cercospora arachidicola* | **ముందస్తు తిక్కా ఆకుమచ్చ** | **अगेती टिक्का रोग** |
| **26**| Groundnut| **Late Leaf Spot (Tikka)** | *Phaeoisariopsis personata* | **ఆలస్యపు తిక్కా ఆకుమచ్చ** | **पछेती टिक्का रोग** |
| **27**| Groundnut| **Rust** | *Puccinia arachidis* | **తుప్పు తెగులు** | **गेरुआ रोग** |
| **28**| Groundnut| **Collar Rot** | *Aspergillus niger* | **మొలక కుళ్లు తెగులు** | **कॉलर रॉट** |
| **29**| Groundnut| **Stem Rot / Sclerotium Wilt**| *Sclerotium rolfsii* | **కాండం కుళ్లు తెగులు** | **तना सड़न रोग** |
| **30**| Groundnut| **Peanut Bud Necrosis Disease**| *Orthotospovirus* (Thrips vector) | **మొగ్గ కుళ్లు తెగులు** | **बड नेक्रोसिस** |
| **31**| Redgram| **Fusarium Wilt** | *Fusarium udum* | **ఎండు తెగులు** | **उकठा रोग** |
| **32**| Redgram| **Sterility Mosaic Disease** | *Pigeonpea sterility mosaic emaravirus*| **వెర్రి తెగులు / వంధ్యత్వ తెగులు** | **बांझपन मोज़ेक** |
| **33**| Redgram| **Phytophthora Blight** | *Phytophthora cajani* | **ఫైటోఫ్తోరా ఎండు తెగులు** | **फाइटोफ्थोरा झुलसा** |
| **34**| Redgram| **Cercospora Leaf Spot** | *Cercospora indica* | **ఆకుమచ్చ తెగులు** | **पत्ती धब्बा** |
| **35**| Soybean| **Yellow Mosaic Virus (YMV)** | *Soybean yellow mosaic virus* | **పసుపు పచ్చ తెగులు** | **पीला मोज़ेक** |
| **36**| Soybean| **Rust** | *Phakopsora pachyrhizi* | **తుప్పు తెగులు** | **रतुआ रोग** |
| **37**| Soybean| **Charcoal Rot** | *Macrophomina phaseolina* | **బొగ్గు కుళ్లు తెగులు** | **चारकोल सड़न** |
| **38**| Soybean| **Anthracnose / Pod Blight**| *Colletotrichum truncatum* | **కాయ కుళ్లు తెగులు** | **फली झुलसा** |
| **39**| Tomato | **Early Blight** | *Alternaria solani* | **ముందస్తు ఆకు ఎండు తెగులు** | **अगेती झुलसा** |
| **40**| Tomato | **Late Blight** | *Phytophthora infestans* | **ఆలస్యపు ఆకు ఎండు తెగులు** | **पछेती झुलसा** |
| **41**| Tomato | **Tomato Leaf Curl Virus (ToLCV)**| *Begomovirus* (Whitefly vector) | **ఆకుముడత తెగులు** | **पत्ती मरोड़ रोग** |
| **42**| Tomato | **Bacterial Wilt** | *Ralstonia solanacearum* | **బాక్టీరియా ఎండు తెగులు** | **जीवाणु उकठा** |

---

## 4. Insect Pest Master Register (30 Common Pests)

| # | Crop | Common Name | Scientific Binomial | Telugu Name | Hindi Name |
| :-: | :--- | :--- | :--- | :--- | :--- |
| **1** | Maize | **Fall Armyworm** | *Spodoptera frugiperda* | **కత్తెర పురుగు** | **फॉल आर्मीवर्म / लश्करी सुंडी** |
| **2** | Maize | **Stem Borer** | *Chilo partellus* | **కాండం తొలుచు పురుగు** | **तना छेदक** |
| **3** | Maize | **Corn Earworm / Cob Borer** | *Helicoverpa armigera* | **కంకి పురుగు** | **भुट्टा छेदक** |
| **4** | Rice | **Brown Plant Hopper (BPH)** | *Nilaparvata lugens* | **సుడిదోమ** | **भूरा फुदका** |
| **5** | Rice | **Yellow Stem Borer** | *Scirpophaga incertulas* | **కాండం తొలుచు పురుగు** | **पीला तना छेदक** |
| **6** | Rice | **Gall Midge** | *Orseolia oryzae* | **ఉల్లికోడు / పిక్క తెగులు** | **गॉल मिज** |
| **7** | Rice | **Leaf Folder / Leaf Roller** | *Cnaphalocrocis medinalis* | **ఆకు చుట్టు పురుగు** | **पत्ता लपेटक** |
| **8** | Rice | **Green Leaf Hopper (GLH)** | *Nephotettix virescens* | **పచ్చదోమ** | **हरा फुदका** |
| **9** | Rice | **Gundhi Bug** | *Leptocorisa acuta* | **వెన్ను పురుగు / గంధి బగ్** | **गंधी बग** |
| **10**| Cotton| **Pink Bollworm** | *Pectinophora gossypiella* | **గులాబీ రంగు పురుగు** | **गुलाबी सुंडी** |
| **11**| Cotton| **American Bollworm** | *Helicoverpa armigera* | **శనగ పచ్చ పురుగు** | **अमेरिकन सुंडी** |
| **12**| Cotton| **Spotted Bollworm** | *Earias vittella* | **మచ్చల పురుగు** | **चित्तीदार सुंडी** |
| **13**| Cotton| **Whitefly** | *Bemisia tabaci* | **తెల్లదోమ** | **सफेद मक्खी** |
| **14**| Cotton| **Thrips** | *Thrips tabaci* | **తామర పురుగులు** | **थ्रिप्स** |
| **15**| Cotton| **Aphids** | *Aphis gossypii* | **పేనుబంక** | **माहू / चेपा** |
| **16**| Cotton| **Jassids / Leafhopper** | *Amrasca biguttula biguttula* | **పచ్చదోమ** | **हरा तेला** |
| **17**| Cotton| **Mealybug** | *Phenacoccus solenopsis* | **పిండి పురుగు** | **मिलीबग** |
| **18**| Chilli| **Black Thrips** | *Thrips parvispinus* | **నల్ల తామర పురుగు** | **काला थ्रिप्स** |
| **19**| Chilli| **Yellow Mites** | *Polyphagotarsonemus latus*| **నల్లి / తెల్ల నల్లి** | **पीली मकड़ी** |
| **20**| Chilli| **Fruit Borer / Tobacco Caterpillar**| *Spodoptera litura* | **లద్దె పురుగు / కాయ తొలుచు పురుగు**| **तम्बाकू इल्ली** |
| **21**| Chilli| **Aphids** | *Myzus persicae* | **పేనుబంక** | **माहू** |
| **22**| Groundnut| **Leaf Miner** | *Aproaerema modicella* | **ఆకుమచ్చ పురుగు / సురంగ పురుగు**| **पत्ती सुरंगक कीट** |
| **23**| Groundnut| **White Grub** | *Holotrichia consanguinea* | **వేరు పురుగు** | **सफेद लट** |
| **24**| Groundnut| **Red Hairy Caterpillar** | *Amsacta albistriga* | **ఎర్ర గొంగళి పురుగు** | **लाल बालों वाली इल्ली** |
| **25**| Redgram| **Pod Borer** | *Helicoverpa armigera* | **కాయ తొలుచు పురుగు** | **फली छेदक** |
| **26**| Redgram| **Pod Fly** | *Melanagromyza obtusa* | **కాయ ఈగ** | **फली मक्खी** |
| **27**| Soybean| **Girdle Beetle** | *Obereopsis brevis* | **రింగ్ కట్టర్ / గిర్డిల్ బీటిల్** | **गर्डल बीटल / चक्र भृंग**|
| **28**| Soybean| **Tobacco Caterpillar** | *Spodoptera litura* | **లద్దె పురుగు** | **तम्बाकू की इल्ली** |
| **29**| Tomato | **Fruit Borer** | *Helicoverpa armigera* | **కాయ తొలుచు పురుగు** | **फल छेदक** |
| **30**| Tomato | **Serpentine Leaf Miner** | *Liriomyza trifolii* | **పాము పొడ పురుగు** | **सर्पिलाकार पत्ती सुरंगक**|

---

## 5. CIBRC Approved Agrochemical Master Register (25 Formulations)

| # | Active Ingredient | Concentration & Formulation | Common Commercial Brand Names | Target Crop | Target Pest / Disease | Approved Dosage (per Acre) | Water Volume | Waiting Period (PHI) |
| :-: | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :---: |
| **1** | **Chlorantraniliprole** | 18.5% SC | **Coragen, Vesticor** | Maize | Fall Armyworm (*Spodoptera*) | **60 ml** (0.3 ml/L) | 200 L | 14 days |
| **2** | **Emamectin Benzoate** | 5% SG | **Proclaim, Safari** | Maize | Fall Armyworm | **88 g** (0.44 g/L) | 200 L | 14 days |
| **3** | **Spinetoram** | 11.7% SC | **Delegate** | Maize | Fall Armyworm | **160 ml** (0.8 ml/L) | 200 L | 14 days |
| **4** | **Flubendiamide** | 39.35% SC | **Fame** | Cotton | American & Pink Bollworm | **50 ml** (0.25 ml/L) | 200 L | 30 days |
| **5** | **Profenophos** | 50% EC | **Curacron, Carina** | Cotton | Pink Bollworm, Thrips | **400 – 500 ml** (2 ml/L) | 200 L | 30 days |
| **6** | **Indoxacarb** | 14.5% SC | **Avaunt** | Cotton | Bollworm complex | **200 ml** (1 ml/L) | 200 L | 14 days |
| **7** | **Diafenthiuron** | 50% WP | **Pegasus, Polo** | Cotton | Whitefly, Thrips | **240 g** (1.2 g/L) | 200 L | 30 days |
| **8** | **Pyriproxyfen** | 10% EC | **Lano, Tiger** | Cotton | Whitefly nymphs | **400 ml** (2 ml/L) | 200 L | 30 days |
| **9** | **Tricyclazole** | 75% WP | **Beam, Sivic** | Rice | Blast (*Magnaporthe*) | **120 g** (0.6 g/L) | 200 L | 30 days |
| **10**| **Pymetrozine** | 50% WG | **Chess, Plenum** | Rice | Brown Plant Hopper (BPH) | **120 g** (0.6 g/L) | 200 L | 19 days |
| **11**| **Trifloxystrobin + Tebuconazole**| 75% WG (25%+50%) | **Nativo** | Rice | Sheath Blight & Blast | **80 g** (0.4 g/L) | 200 L | 21 days |
| **12**| **Hexaconazole** | 5% SC | **Contaf, Sitara** | Rice | Sheath Blight | **400 ml** (2 ml/L) | 200 L | 30 days |
| **13**| **Cartap Hydrochloride** | 50% SP | **Padan, Caldan** | Rice | Stem Borer, Leaf Folder | **400 g** (2 g/L) | 200 L | 21 days |
| **14**| **Dinotefuran** | 20% SG | **Token, Oshin** | Rice | BPH (Brown Plant Hopper) | **80 g** (0.4 g/L) | 200 L | 21 days |
| **15**| **Azoxystrobin + Difenoconazole** | 32.5% SC (18.2%+11.4%)| **Amistar Top** | Chilli | Anthracnose / Fruit Rot | **200 ml** (1 ml/L) | 200 L | 5 days |
| **16**| **Fipronil** | 5% SC | **Regent, Fax** | Chilli | Thrips, Stem Borer | **400 ml** (2 ml/L) | 200 L | 7 days |
| **17**| **Spinosad** | 45% SC | **Tracer** | Chilli | Thrips, Fruit Borer | **64 ml** (0.32 ml/L) | 200 L | 3 days |
| **18**| **Cyantraniliprole** | 10.26% OD | **Benevia** | Chilli | Whitefly, Thrips | **240 – 360 ml** (1.5 ml/L) | 200 L | 5 days |
| **19**| **Mancozeb** | 75% WP | **Dithane M-45, Indofil M-45**| Groundnut | Tikka Leaf Spot | **400 g** (2 g/L) | 200 L | 14 days |
| **20**| **Tebuconazole** | 25.9% EC | **Folicur** | Groundnut | Rust & Tikka Leaf Spot | **200 – 300 ml** (1.5 ml/L) | 200 L | 14 days |
| **21**| **Chlorpyrifos** | 20% EC | **Dursban, Radar** | Groundnut | White Grub (Soil drenching)| **800 – 1000 ml** | 400 L | Soil |
| **22**| **Acephate** | 75% SP | **Asataf, Tremor** | Cotton | Aphids, Jassids | **300 g** (1.5 g/L) | 200 L | 15 days |
| **23**| **Thiamethoxam** | 25% WG | **Actara** | Cotton / Chilli| Jassids, Whitefly | **40 – 80 g** (0.4 g/L) | 200 L | 14 days |
| **24**| **Carbendazim + Mancozeb** | 75% WP (12%+63%) | **Saaf, Sixer** | Tomato | Early & Late Blight | **400 g** (2 g/L) | 200 L | 7 days |
| **25**| **Copper Oxychloride (COC)**| 50% WP | **Blitox, Fytolan** | Tomato / Chilli| Bacterial Blight, Dieback | **500 g** (2.5 g/L) | 200 L | 10 days |

---

## 6. Agrochemical Ambiguity Safety Protocol & Confirmation UI

### 6.1 High-Risk Ambiguity Modes
Acoustic distortions in field speech create severe risks of product and formulation misidentification:
1. **Phonetic Brand Similarity:**
   - *"Coragen"* (Chlorantraniliprole 18.5% SC – targeted lepidopteran insecticide, 60 ml/acre) sounds nearly identical to *"Curacron"* (Profenophos 50% EC – organophosphate, 400 ml/acre). Applying 400 ml of Coragen causes severe chemical phytotoxicity and financial devastation (\$80+ USD lost per acre); applying 60 ml of Curacron fails to control Fall Armyworm.
2. **Formulation Abbreviation Distortions:**
   - Distinguishing **SG** (Soluble Granule), **SC** (Suspension Concentrate), **EC** (Emulsifiable Concentrate), and **WP** (Wettable Powder) over low-SNR speech:
     - Telugu: `ఎస్ జి` vs `ఎస్ సి` vs `డబ్ల్యు పి`
     - Hindi: `एस जी` vs `एस सी` vs `डब्ल्यूपी`
3. **Dilution Rate Truncation:**
   - ASR dropping the decimal point in "6.6 ml per 15L pump" transforms the dosage into "66 ml", which causes catastrophic crop leaf scorching.

### 6.2 Mandatory Safety Gate Rules
Whenever a spoken query is recognized:
1. **Uncertainty Score Calculation:**
   - If ASR confidence for a recognized chemical name is $< 0.88$, OR
   - If the transcript matches more than one commercial brand in the phonetic proximity dictionary (Levenshtein distance $\le 2$), OR
   - If the numeric dilution unit is unconfirmed:
2. **Execution Block & Modal Trigger:**
   - The application **strictly blocks automated execution**.
   - It presents the **Agrochemical Confirmation Dialog** before generating dosage guidance:
     ```
     ┌────────────────────────────────────────────────────────────┐
     │ ⚠️ రసాయన నిర్ధారణ (Confirm Chemical)                     │
     ├────────────────────────────────────────────────────────────┤
     │ మీరు అడిగిన రసాయనం:                                      │
     │ [ Coragen / క్లోరాంట్రానిలిప్రోల్ 18.5% SC ]             │
     │                                                            │
     │ పంట: మొక్కజొన్న (Maize)                                    │
     │ పురుగు: కత్తెర పురుగు (Fall Armyworm)                      │
     │                                                            │
     │ ఇది సరైనదేనా?                                              │
     ├──────────────────────────┬─────────────────────────────────┤
     │   [ మార్చండి / Edit ]     │  [ అవును, సరైనదే / Confirm ]    │
     └──────────────────────────┴─────────────────────────────────┘
     ```
3. **Banned Substance Immediate Interception:**
   - Mentions of **Endosulfan**, **Monocrotophos** (on vegetables/fruits), or **Phorate** immediately trigger safety escalation:
     - An automated red warning card displays CIBRC gazette cancellation notices.
     - An Agricultural Escalation Ticket (`AgriculturalTicket`) is created and dispatched to the local Mandal Agricultural Officer (MAO).
