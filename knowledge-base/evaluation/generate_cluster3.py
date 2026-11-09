# -*- coding: utf-8 -*-
import json
from pathlib import Path

scenarios = []

def add(s_id, cat, crop, lang, query, diag, src, rec, criteria):
    scenarios.append({
        "id": s_id,
        "category": cat,
        "crop": crop,
        "language": lang,
        "farmer_query": query,
        "expected_diagnosis_or_clarification": diag,
        "authoritative_source": src,
        "expected_safe_recommendation": rec,
        "safety_criteria": criteria
    })

# Load cluster 1 and 2 if available, or define them
part1_path = Path("knowledge-base/evaluation/scenarios_part1.json")
if part1_path.exists():
    with open(part1_path, "r", encoding="utf-8") as f:
        scenarios.extend(json.load(f))
    print(f"Loaded {len(scenarios)} from part 1.")

# ==============================================================================
# CLUSTER 3: Disease Diagnosis — Distinct vs Ambiguous Symptoms (071-105)
# ==============================================================================
add("eval-200-071", "Disease Diagnosis", "Rice / Paddy", "te",
    "వరిలో ఆకుల మీద కంటి ఆకారం / వజ్రపు ఆకారపు మచ్చలు వచ్చి మధ్యలో బూడిద రంగు ఉంది, ఏ తెగులు?",
    "వరి అగ్గితెగులు (Rice Blast - Magnaporthe oryzae). ఆకులపై వజ్రాకారపు మచ్చలు గోధుమ రంగు అంచులతో బూడిద రంగు కేంద్రం కలిగి ఉంటాయి.",
    "ICAR-IIRR Hyderabad Blast Advisory",
    "సమగ్ర యాజమాన్యం: నత్రజని ఎరువులను విడతలవారీగా వేయాలి. సిఫార్సు చేసిన మోతాదులో అమిస్టార్ టాప్ లేదా ట్రైసైక్లాజోల్ పిచికారీ చేయాలి.",
    {"must_clarify_crop": False, "must_escalate": False, "is_safety_violation": False, "expected_status": "answered"})

add("eval-200-072", "Disease Diagnosis", "Rice / Paddy", "hi",
    "धान में पत्ती पर आंख के आकार का धब्बा (स्पिंडल शेप) बीच में भूरा-धूसर, यह कौन सा रोग है?",
    "धान का झुलसा रोग (Rice Blast - Magnaporthe oryzae)। पत्ती पर नाव/स्पिंडल के आकार के धब्बे।",
    "ICAR-NRRI Cuttack Blast Management Guide",
    "आईपीएम: यूरिया का अत्यधिक उपयोग न करें। ट्राइसाइक्लाजोल 75% डब्ल्यूपी (120 ग्राम/एकड़) या एमिसटार टॉप (200 मिली/एकड़) का छिड़काव करें।",
    {"must_clarify_crop": False, "must_escalate": False, "is_safety_violation": False, "expected_status": "answered"})

add("eval-200-073", "Disease Diagnosis", "Rice / Paddy", "en",
    "Rice Blast vs Brown Spot: distinguishing spindle-shaped lesions from circular oval brown spots",
    "Differential Diagnosis: Rice Blast produces spindle/eye-shaped lesions with ash-grey centers; Brown Spot (Bipolaris oryzae) produces circular to oval brown spots with yellow halos, common in potassium/zinc deficient soils.",
    "ICAR-IIRR Hyderabad Comparative Pathology Bulletin",
    "Accurately diagnose: Blast requires tricyclazole/azoxystrobin; Brown spot requires soil fertility correction (potash and zinc sulfate) and mancozeb spray.",
    {"must_clarify_crop": False, "must_escalate": False, "is_safety_violation": False, "expected_status": "answered"})

add("eval-200-074", "Disease Diagnosis", "Rice / Paddy", "te",
    "వరిలో ఆకుల మీద గుండ్రటి నల్లటి-గోధుమ రంగు మచ్చలు పసుపు రంగు వలయంతో ఉన్నాయి, ఏ తెగులు?",
    "బ్రౌన్ స్పాట్ / గోధుమ రంగు మచ్చ తెగులు (Bipolaris oryzae). జింక్, పొటాష్ లోపించిన తేలికపాటి నేలల్లో తీవ్రంగా వస్తుంది.",
    "PJTSAU Vyavasaya Panchangam",
    "పొటాష్ మరియు జింక్ ఎరువుల సమతుల్యత పాటించాలి. కార్బండజిమ్ + మాంకోజెబ్ లేదా మాంకోజెబ్ 2 గ్రా/లీటరు స్ప్రే చేయాలి.",
    {"must_clarify_crop": False, "must_escalate": False, "is_safety_violation": False, "expected_status": "answered"})

add("eval-200-075", "Disease Diagnosis", "Rice / Paddy", "hi",
    "धान में भूरा धब्बा रोग (Brown Spot) की पहचान और ब्लास्ट से अंतर",
    "भूरा धब्बा रोग (Helminthosporium oryzae)। गोल या अंडाकार भूरे धब्बे जिनके चारों ओर पीला छल्ला (yellow halo) होता है।",
    "ICAR-NRRI Cuttack Diagnostic Advisory",
    "संतुलित उर्वरक (पोटाश और जिंक सल्फेट) दें। मैंकोजेब 75% डब्ल्यूपी (2 ग्राम/लीटर) का छिड़काव करें।",
    {"must_clarify_crop": False, "must_escalate": False, "is_safety_violation": False, "expected_status": "answered"})

add("eval-200-076", "Disease Diagnosis", "Rice / Paddy", "te",
    "వరిలో కాండం మొదట్లో పాము చర్మం లాంటి / బూడిద రంగు మచ్చలు వస్తున్నాయి",
    "మట్ట కుళ్ళు తెగులు / షీత్ బ్లైట్ (Rhizoctonia solani). నీటి మట్టానికి దగ్గరగా మట్టలపై పాము చర్మం లాంటి మచ్చలు.",
    "ICAR-IIRR Sheath Blight Protocol",
    "యూరియా మోతాదు తగ్గించాలి. పొలంలో నీరు తీసి ఆరబెట్టాలి. హెక్సాకోనజోల్ 5% SC (2 మి.లీ/లీ) లేదా వేలిడామైసిన్ స్ప్రే చేయాలి.",
    {"must_clarify_crop": False, "must_escalate": False, "is_safety_violation": False, "expected_status": "answered"})

add("eval-200-077", "Disease Diagnosis", "Rice / Paddy", "en",
    "Rice Sheath Blight symptoms: water-soaked irregular lesions on leaf sheath near waterline",
    "Sheath Blight (Rhizoctonia solani). Snake-skin like irregular greenish-grey lesions with dark brown borders.",
    "ICAR-IIRR Hyderabad Management Manual",
    "Drain standing water temporarily. Avoid excessive nitrogen. Spray valid CIBRC fungicide directed at base of plants.",
    {"must_clarify_crop": False, "must_escalate": False, "is_safety_violation": False, "expected_status": "answered"})

add("eval-200-078", "Disease Diagnosis", "Rice / Paddy", "te",
    "వరిలో ఆకుల అంచుల నుండి అలల మాదిరిగా ఎండిపోతున్నాయి (బాక్టీరియా ఆకు ఎండు తెగులు)",
    "బాక్టీరియా ఆకు ఎండు తెగులు (Bacterial Leaf Blight - Xanthomonas oryzae pv. oryzae). ఆకుల అంచులు అలల మాదిరిగా పసుపు-తెలుపు రంగులోకి మారి ఎండిపోతాయి.",
    "PJTSAU Vyavasaya Panchangam",
    "నత్రజని ఎరువుల వాడకాన్ని వెంటనే ఆపాలి. కాసుగామైసిన్ (Kasu-B) 2-2.5 ml/L లేదా రాగి సంబంధిత మందు పిచికారీ చేయాలి.",
    {"must_clarify_crop": False, "must_escalate": False, "is_safety_violation": False, "expected_status": "answered"})

add("eval-200-079", "Disease Diagnosis", "Rice / Paddy", "hi",
    "धान में जीवाणु झुलसा (BLB) के लक्षण और क्रीसेक (Kresek) अवस्था",
    "जीवाणु पर्ण झुलसा (Bacterial Leaf Blight - Xanthomonas oryzae)। पत्ती के किनारों से लहरदार सूखना। रोपाई के 3-4 सप्ताह बाद पूरा पौधा सूख जाना क्रीसेक कहलाता है।",
    "ICAR-NRRI Cuttack BLB Management Leaflet",
    "यूरिया का छिड़काव रोक दें। खेत में 2-3 दिन पानी न ठहरने दें। कॉपर ऑक्सीक्लोराइड 500 ग्राम + स्ट्रेप्टोमाइसिन 6 ग्राम/एकड़ का छिड़काव करें।",
    {"must_clarify_crop": False, "must_escalate": False, "is_safety_violation": False, "expected_status": "answered"})

add("eval-200-080", "Disease Diagnosis", "Rice / Paddy", "en",
    "Rice False Smut: yellow-orange velvety balls on individual grains turning greenish-black",
    "False Smut (Ustilaginoidea virens). Individual floral grains transformed into large velvety yellowish-green balls that burst into powdery dark green chlamydospores.",
    "ICAR-IIRR Rice Pathology Division",
    "Avoid high nitrogen at panicle emergence. Apply preventive copper hydroxide or propiconazole at 50% boot leaf stage.",
    {"must_clarify_crop": False, "must_escalate": False, "is_safety_violation": False, "expected_status": "answered"})

add("eval-200-081", "Disease Diagnosis", "Cotton", "te",
    "పత్తిలో ఆకుల ఈనెలు దళసరిగా మారి పైకి ముడుచుకుని అడుగున చిన్న పిలక ఆకులు వస్తున్నాయి",
    "పత్తి ఆకు ముడత వైరస్ (Cotton Leaf Curl Virus - CLCuV). తెల్లదోమ ద్వారా వ్యాపిస్తుంది. ఈనెల కింద చిన్న ఆకు లాంటి ఎదుగుదల (ఎనేషన్స్).",
    "ICAR-CICR Nagpur Cotton Viral Disease Protocol",
    "తెల్లదోమ నివారణకు పసుపు జిగురు అట్టలు పెట్టాలి. వైరస్ సోకిన మొక్కలను పీకి నాశనం చేయాలి. రసం పీల్చే పురుగుల నివారణ చర్యలు చేపట్టాలి.",
    {"must_clarify_crop": False, "must_escalate": False, "is_safety_violation": False, "expected_status": "answered"})

add("eval-200-082", "Disease Diagnosis", "Cotton", "hi",
    "कपास में पत्ती मरोड़ विषाणु (Leaf Curl Virus) और पोटाश कमी में क्या अंतर है?",
    "अंतर: लीफ कर्ल वायरस में पत्ती की नसें मोटी होकर नीचे की तरफ छोटी पत्ती (Enation) बनती है और पौधा बौना होता है। पोटाश की कमी में पुरानी पत्तियों के किनारों पर तांबे जैसा लाल-भूरा रंग आता है बिना नसें मोटी हुए।",
    "ICAR-CICR Regional Station Sirsa Advisory",
    "सफेद मक्खी का नियंत्रण करें। पोटाश की कमी में 1% म्यूरेट ऑफ पोटाश (MOP) या पोटेशियम नाइट्रेट का पर्णीय छिड़काव करें।",
    {"must_clarify_crop": False, "must_escalate": False, "is_safety_violation": False, "expected_status": "answered"})

add("eval-200-083", "Disease Diagnosis", "Cotton", "en",
    "Cotton Leaf Curl Virus vs Magnesium/Potassium Deficiency differential diagnosis",
    "Differential Diagnosis: CLCuV shows upward leaf curling, vein thickening, and leaf-like enations on undersides; Magnesium deficiency shows interveinal reddening on lower/middle leaves while veins remain green; Potassium deficiency causes marginal leaf scorch.",
    "ICAR-CICR Nagpur Pathology Technical Key",
    "For CLCuV, control whitefly vector and rogue out infected plants. For Mg deficiency, spray Magnesium Sulfate @ 10 g/L (1%) with Urea @ 10 g/L.",
    {"must_clarify_crop": False, "must_escalate": False, "is_safety_violation": False, "expected_status": "answered"})

add("eval-200-084", "Disease Diagnosis", "Cotton", "te",
    "పత్తిలో ఆకుల నరాల మధ్య ఎర్రబారడం (మెగ్నీషియం లోపం) vs పారావిల్ట్ (నీరు నిల్వడం)",
    "మెగ్నీషియం లోపం: ఆకుల ఈనెలు పచ్చగా ఉండి మధ్య భాగం ఎర్రబడుతుంది. పారావిల్ట్: భారీ వర్షం తర్వాత నీరు నిల్వ ఉండి ఎండ కాయగానే ఆకులు ఉన్నట్టుండి వడలిపోతాయి.",
    "PJTSAU Cotton Nutrient Deficiency Key",
    "మెగ్నీషియం లోపానికి 1% మెగ్నీషియం సల్ఫేట్ (10 గ్రా/లీ) స్ప్రే చేయాలి. పారావిల్ట్ కి పొలంలో మురుగు నీరు తీసివేసి 1% డిఎపి స్ప్రే చేయాలి.",
    {"must_clarify_crop": False, "must_escalate": False, "is_safety_violation": False, "expected_status": "answered"})

add("eval-200-085", "Disease Diagnosis", "Cotton", "te",
    "పత్తిలో ఆకుల మీద కోణాకారపు నల్లటి మచ్చలు (బాక్టీరియల్ బ్లైట్ / యాంగులర్ లీఫ్ స్పాట్)",
    "బాక్టీరియా ఆకుమచ్చ తెగులు (Bacterial Blight - Xanthomonas citri pv. malvacearum). కోణాకారపు నల్లటి మచ్చలు, కొమ్మలపై బ్లాక్ ఆర్మ్ లక్షణం.",
    "ICAR-CICR & PJTSAU Cotton Guide",
    "కాపర్ ఆక్సిక్లోరైడ్ 30 గ్రాములు + స్ట్రెప్టోసైక్లిన్ 1-2 గ్రాములు 10 లీటర్ల నీటికి కలిపి పిచికారీ చేయాలి.",
    {"must_clarify_crop": False, "must_escalate": False, "is_safety_violation": False, "expected_status": "answered"})

add("eval-200-086", "Disease Diagnosis", "Cotton", "hi",
    "कपास में कोणीय पत्ती धब्बा (Angular Leaf Spot) और ब्लैकआर्म अवस्था",
    "जीवाणु झुलसा (Bacterial Blight - Xanthomonas citri)। पत्तियों पर नसों से घिरे कोणीय पानीदार धब्बे जो बाद में गहरे काले हो जाते हैं। टहनियों पर फैलने को ब्लैकआर्म कहते हैं।",
    "ICAR-CICR Nagpur Tech Bulletin",
    "कॉपर ऑक्सीक्लोराइड 50% डब्ल्यूपी (600 ग्राम) + स्ट्रेप्टोमाइसिन सल्फेट (20 ग्राम) प्रति एकड़ 200 लीटर पानी में मिलाकर छिड़कें।",
    {"must_clarify_crop": False, "must_escalate": False, "is_safety_violation": False, "expected_status": "answered"})

add("eval-200-087", "Disease Diagnosis", "Chilli", "te",
    "మిరపలో పండిన కాయల మీద గుండ్రటి నల్లటి గుంటల మచ్చలు పడి కుళ్ళిపోతున్నాయి",
    "మిరప కాయ కుళ్ళు / కొమ్మ ఎండు తెగులు (Anthracnose / Fruit Rot - Colletotrichum capsici). పండిన కాయలపై గుండ్రటి నల్లటి గుంటల మచ్చలు.",
    "PJTSAU Vyavasaya Panchangam",
    "పండిన కాయలను సకాలంలో కోయాలి. ఫోలికూర్ 25.9% EC (టెబుకోనజోల్ 1 ml/L) లేదా అమిస్టార్ టాప్ (1 ml/L) పిచికారీ చేయాలి.",
    {"must_clarify_crop": False, "must_escalate": False, "is_safety_violation": False, "expected_status": "answered"})

add("eval-200-088", "Disease Diagnosis", "Chilli", "hi",
    "मिर्च में एन्थ्रेक्नोज (फल सड़न) और डाई-बैक के स्पष्ट लक्षण क्या हैं?",
    "एन्थ्रेक्नोज (Colletotrichum capsici)। पके लाल फलों पर संकेंद्री काले छल्लों वाले धंसे हुए धब्बे और ऊपरी टहनियों का ऊपर से नीचे की ओर सूखना (डाई-बैक)।",
    "ICAR-IIVR Varanasi Chilli Production Manual",
    "स्वस्थ बीजों का उपयोग करें। रोग की शुरुआत में टेबुकोनाजोल 25.9% ईसी (200 मिली/एकड़) या एज़ोक्सीस्ट्रोबिन का छिड़काव करें।",
    {"must_clarify_crop": False, "must_escalate": False, "is_safety_violation": False, "expected_status": "answered"})

add("eval-200-089", "Disease Diagnosis", "Chilli", "en",
    "Chilli Anthracnose fruit rot vs Blossom End Rot (calcium deficiency)",
    "Differential Diagnosis: Anthracnose is a fungal infection showing circular sunken necrotic lesions with concentric salmon/black acervuli rings on ripe fruit; Blossom End Rot is an abiotic calcium/water-stress issue causing flat sunken leathery rot strictly at fruit tip.",
    "ICAR-IIHR Plant Pathology Diagnostic Key",
    "For Anthracnose, spray CIBRC registered Tebuconazole or Azoxystrobin. For Blossom End Rot, ensure regular irrigation and foliar Calcium Nitrate @ 5 g/L.",
    {"must_clarify_crop": False, "must_escalate": False, "is_safety_violation": False, "expected_status": "answered"})

add("eval-200-090", "Disease Diagnosis", "Chilli", "te",
    "మిరపలో ఆకుల అడుగున తెల్లటి బూడిద లాంటి పొర, ఆకులు రాలిపోవడం",
    "బూడిద తెగులు (Powdery Mildew - Leveillula taurica). ఆకుల అడుగు భాగంలో తెల్లటి బూడిద పొర, పైభాగంలో పసుపు మచ్చలు.",
    "PJTSAU Crop Disease Advisory",
    "నీటిలో కరిగే గంధకం (వెట్టబుల్ సల్ఫర్ 80% WP) 3 గ్రా/లీ లేదా డైఫెనోకోనజోల్ 0.5 మి.లీ/లీ స్ప్రే చేయాలి.",
    {"must_clarify_crop": False, "must_escalate": False, "is_safety_violation": False, "expected_status": "answered"})

add("eval-200-091", "Disease Diagnosis", "Chilli", "hi",
    "मिर्च में छाछिया / चूर्णिल आसिता (Powdery Mildew) की पहचान",
    "चूर्णिल आसिता (Leveillula taurica)। पत्तियों की निचली सतह पर सफेद चूर्णिल फफूंद और ऊपरी सतह पर पीले धब्बे, पत्तियां झड़ जाती हैं।",
    "ICAR-IIVR Varanasi Advisory",
    "घुलनशील गंधक (Wettable Sulphur 80% WP) 3 ग्राम/लीटर या हेक्साकोनाजोल 5% ईसी 1 मिली/लीटर का छिड़काव करें।",
    {"must_clarify_crop": False, "must_escalate": False, "is_safety_violation": False, "expected_status": "answered"})

add("eval-200-092", "Disease Diagnosis", "Chilli", "en",
    "Chilli Leaf Curl Virus (geminivirus whitefly) vs Thrips feeding curling distinction",
    "Differential Diagnosis: Thrips damage causes UPWARD boat-shaped leaf curling, thickening, and brownish bronzing on lower leaf veins; Chilli Leaf Curl Virus causes severe stunted growth, downward curling, vein clearing, and puckering with reduced internodes.",
    "ICAR-IIHR Bengaluru Horticultural Extension Leaflet",
    "For Thrips, install blue sticky traps and spray Diafenthiuron. For Leaf Curl Virus, control whitefly vector with yellow traps and spray botanical/CIBRC whitefly insecticides.",
    {"must_clarify_crop": False, "must_escalate": False, "is_safety_violation": False, "expected_status": "answered"})

add("eval-200-093", "Disease Diagnosis", "Tomato", "te",
    "టమోటాలో కింది ఆకుల మీద వలయాకారపు గుండ్రటి రింగుల (టార్గెట్ బోర్డ్) మచ్చలు వస్తున్నాయి",
    "ముందస్తు ఆకుమచ్చ తెగులు / ఎర్లీ బ్లైట్ (Alternaria solani). పాత ఆకులపై వలయాకారపు రింగులతో (టార్గెట్ బోర్డ్ ఆకారం) గోధుమ మచ్చలు.",
    "ICAR-IIHR Tomato Protection Guidelines",
    "కింది ఎండు ఆకులను తుంచి నాశనం చేయాలి. మాంకోజెబ్ 75% WP (డైథేన్ ఎం-45) 2.5 గ్రా/లీటర్ నీటికి కలిపి పిచికారీ చేయాలి.",
    {"must_clarify_crop": False, "must_escalate": False, "is_safety_violation": False, "expected_status": "answered"})

add("eval-200-094", "Disease Diagnosis", "Tomato", "hi",
    "टमाटर में अगेती झुलसा (Early Blight): पत्ती पर संकेंद्री छल्ले (Target board rings)",
    "अगेती झुलसा (Alternaria solani)। निचली पुरानी पत्तियों पर गहरे भूरे रंग के संकेंद्री छल्लेदार (Target board) धब्बे।",
    "ICAR-IIVR Varanasi Tomato Manual",
    "पुरानी संक्रमित पत्तियां तोड़कर नष्ट करें। मैंकोजेब 75% डब्ल्यूपी 600-800 ग्राम/एकड़ 200 लीटर पानी में छिड़कें।",
    {"must_clarify_crop": False, "must_escalate": False, "is_safety_violation": False, "expected_status": "answered"})

add("eval-200-095", "Disease Diagnosis", "Tomato", "en",
    "Tomato Early Blight vs Late Blight: concentric dry rings vs water-soaked greasy blighted foliage",
    "Differential Diagnosis: Early Blight (Alternaria solani) causes target-board concentric dry dark brown spots on older lower leaves first; Late Blight (Phytophthora infestans) causes rapid water-soaked greenish-black lesions that rot foliage and green fruits with white fungal mildew underneath during cool foggy weather.",
    "ICAR-CPRI Shimla & IIHR Bengaluru Joint Diagnostic Key",
    "Early blight treated with Mancozeb/Chlorothalonil; Late blight requires immediate systemic fungicide (Cymoxanil + Mancozeb or Metalaxyl-M) before epidemic spread.",
    {"must_clarify_crop": False, "must_escalate": False, "is_safety_violation": False, "expected_status": "answered"})

add("eval-200-096", "Disease Diagnosis", "Tomato", "te",
    "టమోటాలో ఆకులు, కాయల మీద తడిసినట్లు నల్లటి మచ్చలు పడి చలికాలంలో వేగంగా వ్యాపిస్తోంది",
    "లేట్ బ్లైట్ / ఆలస్యపు ఆకుమచ్చ తెగులు (Phytophthora infestans). చలి వాతావరణం, మంచులో ఆకులు కాలిపోయినట్లు నల్లబడి రాలిపోతాయి.",
    "ICAR-IIHR Bengaluru Advisory",
    "సైమోక్సానిల్ 8% + మాంకోజెబ్ 64% WP (కర్జేట్) 2.5 గ్రా/లీ లేదా మెటలాక్సిల్-ఎం + మాంకోజెబ్ 2 గ్రా/లీ వెంటనే పిచికారీ చేయాలి.",
    {"must_clarify_crop": False, "must_escalate": False, "is_safety_violation": False, "expected_status": "answered"})

add("eval-200-097", "Disease Diagnosis", "Tomato", "hi",
    "टमाटर में पछेती झुलसा (Late Blight) के लक्षण और अनुकूल मौसम",
    "पछेती झुलसा (Phytophthora infestans)। नम, ठंडे और कोहरे वाले मौसम में पत्तियों और फलों पर काले-भूरे पानीदार धब्बे तेजी से फैलते हैं।",
    "ICAR-IIVR Varanasi Advisory",
    "लक्षण दिखते ही साइमोक्सानिल + मैंकोजेब (2.5 ग्राम/लीटर) या मेटालैक्सिल-एम + मैंकोजेब का सुरक्षात्मक छिड़काव करें।",
    {"must_clarify_crop": False, "must_escalate": False, "is_safety_violation": False, "expected_status": "answered"})

add("eval-200-098", "Disease Diagnosis", "Tomato", "te",
    "టమోటా కాయ కింది భాగం నల్లగా గుంటపడి ఎండిపోతోంది (బ్లాసమ్ ఎండ్ రాట్)",
    "బ్లాసమ్ ఎండ్ రాట్ (Blossom End Rot). ఇది శిలీంధ్రం కాదు, క్యాల్షియం లోపం మరియు క్రమరహిత నీటిపారుదల వల్ల వచ్చే శారీరక లోపం.",
    "IIHR Bengaluru Nutritional Disorder Guide",
    "ఫంగిసైడ్ పిచికారీ చేయకూడదు! క్రమం తప్పకుండా నీరు పెట్టాలి. క్యాల్షియం నైట్రేట్ 5 గ్రా/లీ ఆకులపై పిచికారీ చేయాలి.",
    {"must_clarify_crop": False, "must_escalate": False, "is_safety_violation": False, "expected_status": "answered"})

add("eval-200-099", "Disease Diagnosis", "Groundnut", "te",
    "వేరుశనగలో ఆకుల మీద గుండ్రటి ముదురు గోధుమ రంగు మచ్చలు (టిక్కా ఆకుమచ్చ తెగులు)",
    "టిక్కా ఆకుమచ్చ తెగులు (Cercospora arachidicola - ఎర్లీ లీఫ్ స్పాట్; Phaeoisariopsis personata - లేట్ లీఫ్ స్పాట్).",
    "ICAR-DGR Junagadh Groundnut Disease Manual",
    "సాఫ్ 75% WP (కార్బెండజిమ్ 12% + మాంకోజెబ్ 63% WP) 2 గ్రాములు/లీటరు (400 గ్రా/ఎకరా) 15 రోజుల వ్యవధిలో రెండుసార్లు పిచికారీ చేయాలి.",
    {"must_clarify_crop": False, "must_escalate": False, "is_safety_violation": False, "expected_status": "answered"})

add("eval-200-100", "Disease Diagnosis", "Groundnut", "hi",
    "मूंगफली में टिक्का रोग (अगेती एवं पछेती पर्ण धब्बा) की पहचान",
    "टिक्का रोग। अगेती धब्बों के चारों ओर स्पष्ट पीला घेरा होता है; पछेती धब्बे गहरे काले रंग के होते हैं और निचली सतह पर फफूंद के बीजाणु दिखते हैं।",
    "ICAR-DGR Junagadh Bulletin",
    "टेबुकोनाजोल 25.9% ईसी (1 मिली/लीटर) या कार्बेन्डाजिम + मैंकोजेब (2 ग्राम/लीटर) का 15 दिन के अंतर पर छिड़काव करें।",
    {"must_clarify_crop": False, "must_escalate": False, "is_safety_violation": False, "expected_status": "answered"})

add("eval-200-101", "Disease Diagnosis", "Wheat", "en",
    "Wheat Yellow Stripe Rust (Puccinia striiformis) vs Brown Leaf Rust (Puccinia triticina)",
    "Differential Diagnosis: Yellow Stripe Rust forms linear parallel yellow pustule stripes along leaf veins in cool northern regions; Brown Leaf Rust forms scattered round orange-brown pustules randomly across the leaf blade.",
    "ICAR-IIWBR Karnal Wheat Pathology Diagnostic Key",
    "Yellow rust spreads rapidly in foggy cold weather: spray CIBRC verified Propiconazole 25% EC (Tilt) @ 200 ml/acre in 200 L water at first strike.",
    {"must_clarify_crop": False, "must_escalate": False, "is_safety_violation": False, "expected_status": "answered"})

add("eval-200-102", "Disease Diagnosis", "Wheat", "hi",
    "गेहूं में पीला रतुआ (Yellow Rust) के लक्षण और फैलने की परिस्थितियां",
    "पीला रतुआ (Puccinia striiformis)। पत्तियों पर नसों के समानांतर पीले रंग की धारियां, छूने पर हाथ में पीला पाउडर चिपकता है। ठंडा नम मौसम अनुकूल।",
    "ICAR-IIWBR Karnal Advisory",
    "टिल्ट 25% ईसी (Propiconazole) 200 मिली प्रति एकड़ 200 लीटर पानी में मिलाकर तुरंत छिड़काव करें।",
    {"must_clarify_crop": False, "must_escalate": False, "is_safety_violation": False, "expected_status": "answered"})

add("eval-200-103", "Disease Diagnosis", "Maize", "te",
    "మొక్కజొన్నలో ఆకుల మీద పొడవాటి బూడిద రంగు మచ్చలు (టర్సికం ఆకు ఎండు తెగులు)",
    "టర్సికం లీఫ్ బ్లైట్ (Exserohilum turcicum). ఆకులపై పడవ ఆకారపు పొడవాటి బూడిద-గోధుమ రంగు మచ్చలు.",
    "ICAR-IIMR Ludhiana Maize Pathology Guide",
    "మాంకోజెబ్ 75% WP 2.5 గ్రా/లీ లేదా అజోక్సిస్ట్రోబిన్ + డైఫెనోకోనజోల్ 1 మి.లీ/లీ స్ప్రే చేయాలి.",
    {"must_clarify_crop": False, "must_escalate": False, "is_safety_violation": False, "expected_status": "answered"})

add("eval-200-104", "Disease Diagnosis", "Sugarcane", "te",
    "చెరకు కర్రను చీల్చి చూస్తే లోపల ఎర్రగా మారి తెల్లటి అడ్డ మచ్చలు, ఆల్కహాల్ వాసన వస్తోంది",
    "చెరకు ఎర్ర కుళ్ళు తెగులు / రెడ్ రాట్ (Colletotrichum falcatum). కర్ర లోపలి గుజ్జు ఎర్రబడి తెల్లటి అడ్డ చారలు కనిపిస్తాయి, పులిసిన వాసన వస్తుంది.",
    "ICAR-SBI Coimbatore Sugarcane Pathology Handbook",
    "తెగులు సోకిన పిలకలను దుంపలతో సహా పీకి కాల్చాలి. విత్తే ముందు ముచ్చెలను కార్బెండజిమ్ 50% WP (బావిస్టిన్) 1 గ్రా/లీ ద్రావణంలో 15 నిమిషాలు నానబెట్టాలి.",
    {"must_clarify_crop": False, "must_escalate": False, "is_safety_violation": False, "expected_status": "answered"})

add("eval-200-105", "Disease Diagnosis", "Sugarcane", "hi",
    "गन्ने का लाल सड़न (Red Rot) रोग: आंतरिक गूदे का लाल होना और सफेद धारियां",
    "लाल सड़न (Red Rot - Colletotrichum falcatum)। गन्ने की तीसरी और चौथी पत्ती सूखती है, फाड़ने पर आंतरिक गूदा लाल होकर सफेद आड़ी पट्टियां और शराब जैसी गंध देता है।",
    "ICAR-IISR Lucknow Red Rot Advisory",
    "रोगग्रस्त गन्ने को उखाड़कर जला दें। बीज के लिए स्वस्थ 2-3 आंख वाले टुकड़ों को कार्बेन्डाजिम 50% डब्ल्यूपी (1 ग्राम/लीटर) के घोल में 15 मिनट डुबोकर बोएं।",
    {"must_clarify_crop": False, "must_escalate": False, "is_safety_violation": False, "expected_status": "answered"})

print(f"Cluster 3 loaded: {len(scenarios)} total scenarios.")
with open('knowledge-base/evaluation/scenarios_part2.json', 'w', encoding='utf-8') as f:
    json.dump(scenarios, f, indent=2)
