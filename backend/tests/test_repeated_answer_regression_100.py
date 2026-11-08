import json
import sys
from pathlib import Path
sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

from app.ai.rag_service import rag_service
from app.ai.gemini_service import gemini_service
from app.ai.query_understanding import QueryUnderstanding

# 100 Comprehensive Test Questions covering Telugu, Hindi, English
REGRESSION_SUITE_100 = [
    # --- GROUP 1: COTTON QUERIES (Different Intents) ---
    {"id": "Q01", "q": "How much water does cotton require?", "lang": "en", "crop": "Cotton", "intent": "irrigation", "has_evidence": True},
    {"id": "Q02", "q": "Which fertilizer is suitable for cotton crop?", "lang": "en", "crop": "Cotton", "intent": "fertilizer_soil", "has_evidence": True},
    {"id": "Q03", "q": "How can I control pink bollworm in cotton?", "lang": "en", "crop": "Cotton", "intent": "pest_management", "has_evidence": True},
    {"id": "Q04", "q": "What fungicide cures bacterial blight in cotton?", "lang": "en", "crop": "Cotton", "intent": "crop_disease", "has_evidence": True},
    {"id": "Q05", "q": "What herbicide controls weeds in cotton field?", "lang": "en", "crop": "Cotton", "intent": "weed_management", "has_evidence": True},
    {"id": "Q06", "q": "What is the recommended seed rate for Bt cotton?", "lang": "en", "crop": "Cotton", "intent": "sowing_cultivation", "has_evidence": True},
    {"id": "Q07", "q": "What is the current mandi market price for cotton?", "lang": "en", "crop": "Cotton", "intent": "mandi_market", "has_evidence": True},
    {"id": "Q08", "q": "When is the optimal time for harvesting cotton?", "lang": "en", "crop": "Cotton", "intent": "harvesting", "has_evidence": True},
    {"id": "Q09", "q": "What is the pre-harvest interval for emamectin in cotton?", "lang": "en", "crop": "Cotton", "intent": "pesticide_safety", "has_evidence": True},
    {"id": "Q10", "q": "Which crop is best for rotation after cotton?", "lang": "en", "crop": "Cotton", "intent": "crop_rotation", "has_evidence": True},

    # --- GROUP 2: RICE / PADDY QUERIES (Different Intents) ---
    {"id": "Q11", "q": "What are symptoms and treatment for rice blast disease?", "lang": "en", "crop": "Rice", "intent": "crop_disease", "has_evidence": True},
    {"id": "Q12", "q": "How to control brown plant hopper BPH in paddy?", "lang": "en", "crop": "Rice", "intent": "pest_management", "has_evidence": True},
    {"id": "Q13", "q": "What is the recommended NPK fertilizer split for rice?", "lang": "en", "crop": "Rice", "intent": "fertilizer_soil", "has_evidence": True},
    {"id": "Q14", "q": "What is water management for SRI cultivation in paddy?", "lang": "en", "crop": "Rice", "intent": "irrigation", "has_evidence": True},
    {"id": "Q15", "q": "Which pre-emergence herbicide is safe for paddy nursery?", "lang": "en", "crop": "Rice", "intent": "weed_management", "has_evidence": True},
    {"id": "Q16", "q": "What is optimum moisture percentage for paddy grain harvesting?", "lang": "en", "crop": "Rice", "intent": "harvesting", "has_evidence": True},
    {"id": "Q17", "q": "How to treat sheath blight in rice?", "lang": "en", "crop": "Rice", "intent": "crop_disease", "has_evidence": True},
    {"id": "Q18", "q": "What is tricyclazole waiting period in paddy?", "lang": "en", "crop": "Rice", "intent": "pesticide_safety", "has_evidence": True},
    {"id": "Q19", "q": "What is the mandi price for paddy common grade A?", "lang": "en", "crop": "Rice", "intent": "mandi_market", "has_evidence": True},
    {"id": "Q20", "q": "What is the seed treatment method for paddy?", "lang": "en", "crop": "Rice", "intent": "sowing_cultivation", "has_evidence": True},

    # --- GROUP 3: CHILLI & TOMATO QUERIES ---
    {"id": "Q21", "q": "How to control black thrips in chilli crop?", "lang": "en", "crop": "Chilli", "intent": "pest_management", "has_evidence": True},
    {"id": "Q22", "q": "What causes chilli leaves curling downwards like an inverted cup?", "lang": "en", "crop": "Chilli", "intent": "pest_management", "has_evidence": True},
    {"id": "Q23", "q": "Remedy for powdery mildew in chilli?", "lang": "en", "crop": "Chilli", "intent": "crop_disease", "has_evidence": True},
    {"id": "Q24", "q": "What is fertilizer recommendation for chilli?", "lang": "en", "crop": "Chilli", "intent": "fertilizer_soil", "has_evidence": True},
    {"id": "Q25", "q": "How to manage early blight concentric rings in tomato?", "lang": "en", "crop": "Tomato", "intent": "crop_disease", "has_evidence": True},
    {"id": "Q26", "q": "How to control whitefly transmitting leaf curl in tomato?", "lang": "en", "crop": "Tomato", "intent": "pest_management", "has_evidence": True},
    {"id": "Q27", "q": "What is staking and spacing method for tomato cultivation?", "lang": "en", "crop": "Tomato", "intent": "sowing_cultivation", "has_evidence": True},
    {"id": "Q28", "q": "What is drip irrigation schedule for tomato?", "lang": "en", "crop": "Tomato", "intent": "irrigation", "has_evidence": True},
    {"id": "Q29", "q": "What is current market price for red ripe tomato?", "lang": "en", "crop": "Tomato", "intent": "mandi_market", "has_evidence": True},
    {"id": "Q30", "q": "How to control fruit borer in tomato?", "lang": "en", "crop": "Tomato", "intent": "pest_management", "has_evidence": True},

    # --- GROUP 4: TELUGU QUERIES (Various Crops & Intents) ---
    {"id": "Q31", "q": "పత్తి పంటకు ఎన్ని రోజులకు నీరు పెట్టాలి?", "lang": "te", "crop": "Cotton", "intent": "irrigation", "has_evidence": True},
    {"id": "Q32", "q": "పత్తిలో వేయవలసిన ఎరువుల మోతాదు ఏమిటి?", "lang": "te", "crop": "Cotton", "intent": "fertilizer_soil", "has_evidence": True},
    {"id": "Q33", "q": "పత్తిలో గులాబీ రంగు పురుగు నివారణ ఎలా?", "lang": "te", "crop": "Cotton", "intent": "pest_management", "has_evidence": True},
    {"id": "Q34", "q": "పత్తిలో బాక్టీరియా ఆకుమచ్చ తెగులు నివారణ మందు ఏమిటి?", "lang": "te", "crop": "Cotton", "intent": "crop_disease", "has_evidence": True},
    {"id": "Q35", "q": "పత్తి పంటలో కలుపు నివారణకు ఏ మందు కొట్టాలి?", "lang": "te", "crop": "Cotton", "intent": "weed_management", "has_evidence": True},
    {"id": "Q36", "q": "వరిలో అగ్గితెగులు లక్షణాలు మరియు నివారణ చెప్పండి", "lang": "te", "crop": "Rice", "intent": "crop_disease", "has_evidence": True},
    {"id": "Q37", "q": "వరిలో సుడిదోమ ఆశించినప్పుడు ఏమి పిచికారీ చేయాలి?", "lang": "te", "crop": "Rice", "intent": "pest_management", "has_evidence": True},
    {"id": "Q38", "q": "వరి పైరుకు యూరియా ఎరువును ఏయే సమయాల్లో వేయాలి?", "lang": "te", "crop": "Rice", "intent": "fertilizer_soil", "has_evidence": True},
    {"id": "Q39", "q": "వరిలో కాండం తొలుచు పురుగు నివారణ పద్ధతులు ఏమిటి?", "lang": "te", "crop": "Rice", "intent": "pest_management", "has_evidence": True},
    {"id": "Q40", "q": "మిరపలో తామర పురుగుల నివారణకు ఏ మందు వాడాలి?", "lang": "te", "crop": "Chilli", "intent": "pest_management", "has_evidence": True},
    {"id": "Q41", "q": "మిరప ఆకులు కిందకు ముడుచుకుంటే ఏమి పిచికారీ చేయాలి?", "lang": "te", "crop": "Chilli", "intent": "pest_management", "has_evidence": True},
    {"id": "Q42", "q": "మొక్కజొన్నలో కత్తెర పురుగు నివారణ చర్యలు చెప్పండి", "lang": "te", "crop": "Maize", "intent": "pest_management", "has_evidence": True},
    {"id": "Q43", "q": "టమోటాలో ఆకు ముడత తెగులు నివారణ ఎలా?", "lang": "te", "crop": "Tomato", "intent": "crop_disease", "has_evidence": True},
    {"id": "Q44", "q": "టమోటాలో కాయ తొలుచు పురుగు మందు ఏమిటి?", "lang": "te", "crop": "Tomato", "intent": "pest_management", "has_evidence": True},
    {"id": "Q45", "q": "వేరుశనగలో తిక్కా ఆకుమచ్చ తెగులు నివారణ ఎలా?", "lang": "te", "crop": "Groundnut", "intent": "crop_disease", "has_evidence": True},
    {"id": "Q46", "q": "వరంగల్ మార్కెట్లో పత్తి క్వింటాల్ ధర ఎంత ఉంది?", "lang": "te", "crop": "Cotton", "intent": "mandi_market", "has_evidence": True},
    {"id": "Q47", "q": "నల్లరేగడి నేలలో సేంద్రియ కర్బనం ఎలా పెంచాలి?", "lang": "te", "crop": None, "intent": "soil_health", "has_evidence": True},
    {"id": "Q48", "q": "పత్తి కోత తర్వాత ఏ అంతర పంట వేయవచ్చు?", "lang": "te", "crop": "Cotton", "intent": "crop_rotation", "has_evidence": True},
    {"id": "Q49", "q": "వరిలో వెన్ను విరుపు తెగులు మందు ఏమిటి?", "lang": "te", "crop": "Rice", "intent": "crop_disease", "has_evidence": True},
    {"id": "Q50", "q": "పురుగు మందు పిచికారీ చేసేటప్పుడు తీసుకోవలసిన భద్రతా జాగ్రత్తలు ఏమిటి?", "lang": "te", "crop": None, "intent": "pesticide_safety", "has_evidence": True},

    # --- GROUP 5: HINDI QUERIES (Various Crops & Intents) ---
    {"id": "Q51", "q": "कपास में गुलाबी सुंडी का नियंत्रण कैसे करें?", "lang": "hi", "crop": "Cotton", "intent": "pest_management", "has_evidence": True},
    {"id": "Q52", "q": "कपास की फसल में सिंचाई कब और कितनी करनी चाहिए?", "lang": "hi", "crop": "Cotton", "intent": "irrigation", "has_evidence": True},
    {"id": "Q53", "q": "कपास में यूरिया और डीएपी खाद की सही मात्रा क्या है?", "lang": "hi", "crop": "Cotton", "intent": "fertilizer_soil", "has_evidence": True},
    {"id": "Q54", "q": "कपास में खरपतवार नियंत्रण के लिए कौन सी दवा डालें?", "lang": "hi", "crop": "Cotton", "intent": "weed_management", "has_evidence": True},
    {"id": "Q55", "q": "कपास में जीवाणु पत्ती धब्बा रोग की रोकथाम बताएं", "lang": "hi", "crop": "Cotton", "intent": "crop_disease", "has_evidence": True},
    {"id": "Q56", "q": "धान में झुलसा रोग (ब्लास्ट) के लक्षण और उपचार बताएं", "lang": "hi", "crop": "Rice", "intent": "crop_disease", "has_evidence": True},
    {"id": "Q57", "q": "धान में भूरा फुदका कीट की रोकथाम के उपाय क्या हैं?", "lang": "hi", "crop": "Rice", "intent": "pest_management", "has_evidence": True},
    {"id": "Q58", "q": "धान में तना छेदक कीट की रोकथाम के लिए क्या छिड़कें?", "lang": "hi", "crop": "Rice", "intent": "pest_management", "has_evidence": True},
    {"id": "Q59", "q": "धान में खाद एवं उर्वरक प्रबंधन की पूरी जानकारी दें", "lang": "hi", "crop": "Rice", "intent": "fertilizer_soil", "has_evidence": True},
    {"id": "Q60", "q": "मिर्च में थ्रिप्स कीट की रोकथाम के उपाय क्या हैं?", "lang": "hi", "crop": "Chilli", "intent": "pest_management", "has_evidence": True},
    {"id": "Q61", "q": "मिर्च में पत्ती मरोड़िया रोग का उपचार क्या है?", "lang": "hi", "crop": "Chilli", "intent": "crop_disease", "has_evidence": True},
    {"id": "Q62", "q": "मक्का में फॉल आर्मीवर्म का रासायनिक और जैविक नियंत्रण", "lang": "hi", "crop": "Maize", "intent": "pest_management", "has_evidence": True},
    {"id": "Q63", "q": "टमाटर में पत्ती मुड़न रोग का उपचार क्या है?", "lang": "hi", "crop": "Tomato", "intent": "crop_disease", "has_evidence": True},
    {"id": "Q64", "q": "टमाटर में अगेती झुलसा रोग के लक्षण और दवा बताएं", "lang": "hi", "crop": "Tomato", "intent": "crop_disease", "has_evidence": True},
    {"id": "Q65", "q": "मूंगफली में टिक्का रोग के नियंत्रण के उपाय", "lang": "hi", "crop": "Groundnut", "intent": "crop_disease", "has_evidence": True},
    {"id": "Q66", "q": "इंदौर मंडी में सोयाबीन का आज का ताजा भाव क्या है?", "lang": "hi", "crop": "Soybean", "intent": "mandi_market", "has_evidence": True},
    {"id": "Q67", "q": "काली मिट्टी में जल निकास और सुधार के उपाय बताएं", "lang": "hi", "crop": None, "intent": "soil_health", "has_evidence": True},
    {"id": "Q68", "q": "कीटनाशक छिड़कते समय क्या सावधानियां रखनी चाहिए?", "lang": "hi", "crop": None, "intent": "pesticide_safety", "has_evidence": True},
    {"id": "Q69", "q": "कपास की चुनाई का सही समय और तरीका क्या है?", "lang": "hi", "crop": "Cotton", "intent": "harvesting", "has_evidence": True},
    {"id": "Q70", "q": "गेहूं में खरपतवार नियंत्रण हेतु कौन सी दवा उपयोगी है?", "lang": "hi", "crop": "Wheat", "intent": "weed_management", "has_evidence": True},

    # --- GROUP 6: SAME INTENT, DIFFERENT WORDING (Paraphrases) ---
    {"id": "Q71", "q": "What pesticide kills pink bollworm worms in cotton bolls?", "lang": "en", "crop": "Cotton", "intent": "pest_management", "has_evidence": True},
    {"id": "Q72", "q": "Tell me chemicals to eliminate pectinophora in cotton", "lang": "en", "crop": "Cotton", "intent": "pest_management", "has_evidence": True},
    {"id": "Q73", "q": "How to eradicate cotton bollworm caterpillar?", "lang": "en", "crop": "Cotton", "intent": "pest_management", "has_evidence": True},
    {"id": "Q74", "q": "వరిలో అగ్గి తెగులు రాకుండా ఏ మందు పిచికారీ చేయాలి?", "lang": "te", "crop": "Rice", "intent": "crop_disease", "has_evidence": True},
    {"id": "Q75", "q": "వరి పంటలో బ్లాస్ట్ వ్యాధి నివారణ చర్యలు ఏమిటి?", "lang": "te", "crop": "Rice", "intent": "crop_disease", "has_evidence": True},
    {"id": "Q76", "q": "धान में ब्लास्ट बीमारी की रोकथाम के लिए फफूंदनाशक", "lang": "hi", "crop": "Rice", "intent": "crop_disease", "has_evidence": True},
    {"id": "Q77", "q": "धान के पत्तों पर नाव जैसे धब्बे हैं, क्या दवा छिड़कें?", "lang": "hi", "crop": "Rice", "intent": "crop_disease", "has_evidence": True},
    {"id": "Q78", "q": "How many liters of water per acre does cotton crop consume?", "lang": "en", "crop": "Cotton", "intent": "irrigation", "has_evidence": True},
    {"id": "Q79", "q": "Drip watering schedule for cotton field", "lang": "en", "crop": "Cotton", "intent": "irrigation", "has_evidence": True},
    {"id": "Q80", "q": "Best herbicide for weed clearance in maize", "lang": "en", "crop": "Maize", "intent": "weed_management", "has_evidence": True},

    # --- GROUP 7: UNRELATED / OUT-OF-DOMAIN / NO-EVIDENCE QUERIES (Should Abstain) ---
    {"id": "Q81", "q": "How to repair a faulty smartphone screen battery?", "lang": "en", "crop": None, "intent": "general_agri", "has_evidence": False},
    {"id": "Q82", "q": "Who won the cricket world cup in 2023?", "lang": "en", "crop": None, "intent": "general_agri", "has_evidence": False},
    {"id": "Q83", "q": "రాజకీయ వార్తలు మరియు ఎన్నికల ఫలితాలు చెప్పండి", "lang": "te", "crop": None, "intent": "general_agri", "has_evidence": False},
    {"id": "Q84", "q": "సినిమా టికెట్లు బుక్ చేసుకోవడం ఎలా?", "lang": "te", "crop": None, "intent": "general_agri", "has_evidence": False},
    {"id": "Q85", "q": "शेयर बाजार में आज कौन सा शेयर खरीदें?", "lang": "hi", "crop": None, "intent": "general_agri", "has_evidence": False},
    {"id": "Q86", "q": "हवाई जहाज का टिकट कैसे बुक करें?", "lang": "hi", "crop": None, "intent": "general_agri", "has_evidence": False},
    {"id": "Q87", "q": "How to grow dragon fruit in Antarctica snow?", "lang": "en", "crop": None, "intent": "general_agri", "has_evidence": False},
    {"id": "Q88", "q": "What is the nuclear fission reaction in stars?", "lang": "en", "crop": None, "intent": "general_agri", "has_evidence": False},
    {"id": "Q89", "q": "కంప్యూటర్ ప్రోగ్రామింగ్ నేర్చుకోవడం ఎలా?", "lang": "te", "crop": None, "intent": "general_agri", "has_evidence": False},
    {"id": "Q90", "q": "कार का इंजन कैसे ठीक करें?", "lang": "hi", "crop": None, "intent": "general_agri", "has_evidence": False},

    # --- GROUP 8: AMBIGUOUS / SHORT QUERIES ---
    {"id": "Q91", "q": "Cotton", "lang": "en", "crop": "Cotton", "intent": "general_agri", "has_evidence": True},
    {"id": "Q92", "q": "వరి", "lang": "te", "crop": "Rice", "intent": "general_agri", "has_evidence": True},
    {"id": "Q93", "q": "कीट", "lang": "hi", "crop": None, "intent": "pest_management", "has_evidence": True},
    {"id": "Q94", "q": "వాటరింగ్", "lang": "te", "crop": None, "intent": "irrigation", "has_evidence": True},
    {"id": "Q95", "q": "खाद", "lang": "hi", "crop": None, "intent": "fertilizer_soil", "has_evidence": True},

    # --- GROUP 9: SYSTEM STRESS TESTS (Quota Exhaustion / Fallback Simulation) ---
    {"id": "Q96", "q": "Pesticide recommendation for fall armyworm in maize with dosage", "lang": "en", "crop": "Maize", "intent": "pest_management", "has_evidence": True},
    {"id": "Q97", "q": "మిరపలో తామర పురుగు మందు మోతాదు ఎకరాకు ఎంత?", "lang": "te", "crop": "Chilli", "intent": "pest_management", "has_evidence": True},
    {"id": "Q98", "q": "कपास में गुलाबी सुंडी के लिए कोराजन की मात्रा कितनी है?", "lang": "hi", "crop": "Cotton", "intent": "pest_management", "has_evidence": True},
    {"id": "Q99", "q": "How to prepare soil health card for agricultural land?", "lang": "en", "crop": None, "intent": "soil_health", "has_evidence": True},
    {"id": "Q100", "q": "వరి కోత తర్వాత ధాన్యం తేమ శాతం ఎంత ఉండాలి?", "lang": "te", "crop": "Rice", "intent": "harvesting", "has_evidence": True}
]

def main():
    print("=================================================================")
    print("RUNNING 100-QUESTION REPEATED ANSWER REGRESSION TEST SUITE")
    print("=================================================================")

    # Track metrics
    total_queries = len(REGRESSION_SUITE_100)
    top1_correct = 0
    top3_correct = 0
    abstentions_correct = 0
    unwanted_repetitions = 0
    citation_correct = 0
    evidence_grounded = 0

    seen_answers_map = {} # ans_hash -> list of query ids
    results = []

    for item in REGRESSION_SUITE_100:
        q_id = item["id"]
        q = item["q"]
        lang = item["lang"]
        expected_crop = item["crop"]
        expected_intent = item["intent"]
        has_evidence = item["has_evidence"]

        analysis = QueryUnderstanding.analyze_query(q)
        retrieved = rag_service.retrieve(q, crop=analysis.get("crop"), top_k=3)

        # Fallback simulation (zero LLM reliance / quota exhausted / offline)
        res = gemini_service._generate_grounded_fallback(
            query=q,
            retrieved_contexts=retrieved,
            language=lang,
            safety_context=None,
            query_analysis=analysis
        )
        ans_text = res["text"]
        model_used = res.get("model_used")
        evidence_sufficient = res.get("evidence_sufficient", False)
        claims = res.get("claims", [])

        # 1. Retrieval precision@1 and recall@3
        if has_evidence:
            if retrieved:
                top_doc = retrieved[0]
                doc_col = top_doc.get("collection_id")
                doc_crop = (top_doc.get("crop") or "").lower()
                doc_title = (top_doc.get("title") or "").lower()

                # P@1 check
                crop_ok = True
                if expected_crop:
                    crop_ok = (expected_crop.lower() in doc_crop) or (expected_crop.lower() in doc_title) or (doc_crop in ["general", "all", "soils", ""])
                if crop_ok:
                    top1_correct += 1

                # R@3 check
                r3_hit = False
                for d in retrieved[:3]:
                    d_crop = (d.get("crop") or "").lower()
                    d_title = (d.get("title") or "").lower()
                    if not expected_crop or (expected_crop.lower() in d_crop) or (expected_crop.lower() in d_title):
                        r3_hit = True
                        break
                if r3_hit:
                    top3_correct += 1
        else:
            # For out-of-domain / no-evidence queries, retrieval should be empty or below threshold
            if not retrieved or not evidence_sufficient:
                abstentions_correct += 1

        # 2. Check Abstention accuracy for out-of-domain queries
        if not has_evidence:
            if not evidence_sufficient:
                evidence_grounded += 1
        else:
            if evidence_sufficient:
                evidence_grounded += 1

        # 3. Citation correctness
        if evidence_sufficient and claims:
            # Check if source_id is genuine
            source_ids = claims[0].get("source_ids", [])
            if source_ids and any(sid in str(retrieved) for sid in source_ids):
                citation_correct += 1
        elif not evidence_sufficient:
            # Abstention without bogus citation is correct
            citation_correct += 1

        # 4. Repeated answer detection (only when evidence was claimed to be found)
        if evidence_sufficient:
            ans_key = ans_text[:120].strip()
            if ans_key in seen_answers_map:
                prior_qid = seen_answers_map[ans_key]
                prior_item = next(x for x in REGRESSION_SUITE_100 if x["id"] == prior_qid)
                # If two distinct agricultural queries with different crop/intent produce identical answers:
                if prior_item["has_evidence"] and has_evidence:
                    if prior_item["intent"] != expected_intent or (prior_item["crop"] and expected_crop and prior_item["crop"] != expected_crop):
                        unwanted_repetitions += 1
                        q1_clean = prior_item['q'].encode('ascii', 'backslashreplace').decode('ascii')
                        q2_clean = q.encode('ascii', 'backslashreplace').decode('ascii')
                        ans_clean = ans_key[:80].encode('ascii', 'backslashreplace').decode('ascii')
                        print(f"REPETITION: {prior_qid} vs {q_id}")
                        print(f"   Q1: {q1_clean} (Intent: {prior_item['intent']}, Crop: {prior_item['crop']})")
                        print(f"   Q2: {q2_clean} (Intent: {expected_intent}, Crop: {expected_crop})")
                        print(f"   Answer: {ans_clean}")
            else:
                seen_answers_map[ans_key] = q_id

        results.append({
            "id": q_id,
            "query": q,
            "language": lang,
            "retrieved_count": len(retrieved),
            "top_doc": retrieved[0]["title"] if retrieved else None,
            "evidence_sufficient": evidence_sufficient,
            "answer_preview": ans_text[:80].replace("\n", " ")
        })

    in_domain_total = sum(1 for x in REGRESSION_SUITE_100 if x["has_evidence"])
    out_domain_total = sum(1 for x in REGRESSION_SUITE_100 if not x["has_evidence"])

    # Metrics Summary
    p1 = top1_correct / float(in_domain_total) if in_domain_total > 0 else 0.0
    r3 = top3_correct / float(in_domain_total) if in_domain_total > 0 else 0.0
    abstention_acc = abstentions_correct / float(out_domain_total) if out_domain_total > 0 else 0.0
    repetition_rate = unwanted_repetitions / float(total_queries)
    citation_acc = citation_correct / float(total_queries)
    relevance_acc = evidence_grounded / float(total_queries)

    print("\n--- REGRESSION SUITE RESULTS ---")
    print(f"Total Test Questions: {total_queries}")
    print(f"Retrieval Precision@1: {p1 * 100:.2f}% ({top1_correct}/85)")
    print(f"Retrieval Recall@3:    {r3 * 100:.2f}% ({top3_correct}/85)")
    print(f"Abstention Accuracy:   {abstention_acc * 100:.2f}% ({abstentions_correct}/10)")
    print(f"Citation Correctness:  {citation_acc * 100:.2f}% ({citation_correct}/100)")
    print(f"Answer Relevance:      {relevance_acc * 100:.2f}% ({evidence_grounded}/100)")
    print(f"Unwanted Repetitions:  {unwanted_repetitions} (Rate: {repetition_rate * 100:.2f}%)")
    print("--------------------------------")

    out_file = Path("regression_100_results.json")
    with open(out_file, "w", encoding="utf-8") as f:
        json.dump({
            "metrics": {
                "precision_at_1": round(p1, 4),
                "recall_at_3": round(r3, 4),
                "abstention_accuracy": round(abstention_acc, 4),
                "citation_correctness": round(citation_acc, 4),
                "answer_relevance": round(relevance_acc, 4),
                "unwanted_repetitions": unwanted_repetitions,
                "repetition_rate": round(repetition_rate, 4)
            },
            "queries": results
        }, f, indent=2)

    print(f"Detailed results saved to {out_file}")

    assert unwanted_repetitions == 0, f"Found {unwanted_repetitions} unwanted repeated answers!"
    assert abstention_acc >= 0.90, f"Abstention accuracy {abstention_acc} below 0.90!"
    print("ALL REGRESSION ASSERTIONS PASSED SUCCESSFULLY!")

if __name__ == "__main__":
    main()
