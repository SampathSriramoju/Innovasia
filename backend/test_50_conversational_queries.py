import sys
import json
import httpx

BASE_URL = "http://localhost:8000/api/v1"

# 50 comprehensive natural language queries
QUERIES = [
    # 1-10: Direct Crop & Pest / Disease Queries in Telugu
    {"q": "పత్తిలో గులాబీ రంగు పురుగు నివారణకు ఏ మందు వాడాలి?", "lang": "te", "expected_crop": "Cotton", "category": "pest"},
    {"q": "మిరప తోటలో నల్ల తామర పురుగు మరియు ఆకు ముడత ఉంది", "lang": "te", "expected_crop": "Chilli", "category": "pest"},
    {"q": "వరి పంటలో అగ్గి తెగులు నివారణకు సిఫార్సు చేసిన మందులు ఏమిటి?", "lang": "te", "expected_crop": "Rice", "category": "disease"},
    {"q": "టమోటా పంటలో ఆకు ముడత వైరస్ ఎలా నివారించాలి?", "lang": "te", "expected_crop": "Tomato", "category": "disease"},
    {"q": "వేరుశనగలో తిక్కా ఆకుమచ్చ తెగులు నివారణ చర్యలు చెప్పండి", "lang": "te", "expected_crop": "Groundnut", "category": "disease"},
    {"q": "మొక్కజొన్నలో కత్తెర పురుగు నివారణకు పిచికారీ ఏమిటి?", "lang": "te", "expected_crop": "Maize", "category": "pest"},
    {"q": "పసుపులో దుంప కుళ్లు తెగులు నివారణ మందులు ఏవి?", "lang": "te", "expected_crop": "Turmeric", "category": "disease"},
    {"q": "మామిడిలో పూత రాలకుండా ఎలాంటి జాగ్రత్తలు తీసుకోవాలి?", "lang": "te", "expected_crop": "Mango", "category": "cultivation"},
    {"q": "ఉల్లిలో తామర పురుగుల నివారణకు మందు ఏమిటి?", "lang": "te", "expected_crop": "Onion", "category": "pest"},
    {"q": "కంది పంటలో కాయ తొలుచు పురుగు నివారణ చెప్పండి", "lang": "te", "expected_crop": "Red Gram", "category": "pest"},

    # 11-20: Direct Crop & Pest / Disease Queries in Hindi
    {"q": "कपास में गुलाबी सुंडी के नियंत्रण के लिए कौन सा कीटनाशक छिड़कें?", "lang": "hi", "expected_crop": "Cotton", "category": "pest"},
    {"q": "मिर्च में थ्रिप्स और मरोड़िया रोग के लिए क्या उपचार है?", "lang": "hi", "expected_crop": "Chilli", "category": "pest"},
    {"q": "धान की फसल में झुलसा रोग (ब्लास्ट) की रोकथाम कैसे करें?", "lang": "hi", "expected_crop": "Rice", "category": "disease"},
    {"q": "टमाटर में फल छेदक इल्ली और पत्ती मरोड़ से कैसे बचें?", "lang": "hi", "expected_crop": "Tomato", "category": "pest"},
    {"q": "मूंगफली में टिक्का रोग के लक्षण और नियंत्रण बताएं", "lang": "hi", "expected_crop": "Groundnut", "category": "disease"},
    {"q": "मक्का में फॉल आर्मीवॉर्म (सैनिक कीट) का उपचार क्या है?", "lang": "hi", "expected_crop": "Maize", "category": "pest"},
    {"q": "गेहूं में पीला रतुआ (येलो रस्ट) से बचाव के उपाय?", "lang": "hi", "expected_crop": "Wheat", "category": "disease"},
    {"q": "हल्दी में कंद सड़न रोग की रोकथाम के उपाय बताएं", "lang": "hi", "expected_crop": "Turmeric", "category": "disease"},
    {"q": "आम में बौर आने के समय कीट प्रबंधन कैसे करें?", "lang": "hi", "expected_crop": "Mango", "category": "pest"},
    {"q": "गन्ने में तना छेदक कीट की रोकथाम कैसे करें?", "lang": "hi", "expected_crop": "Sugarcane", "category": "pest"},

    # 21-30: Direct Crop & Pest / Disease Queries in English
    {"q": "What is the recommended spray for pink bollworm in cotton?", "lang": "en", "expected_crop": "Cotton", "category": "pest"},
    {"q": "How to control black thrips and leaf curl in chilli?", "lang": "en", "expected_crop": "Chilli", "category": "pest"},
    {"q": "Recommended fungicide for sheath blight and blast in paddy rice?", "lang": "en", "expected_crop": "Rice", "category": "disease"},
    {"q": "How to treat leaf curl virus in tomato crop?", "lang": "en", "expected_crop": "Tomato", "category": "disease"},
    {"q": "Control measures for tikka leaf spot in groundnut?", "lang": "en", "expected_crop": "Groundnut", "category": "disease"},
    {"q": "Management of fall armyworm in maize crops?", "lang": "en", "expected_crop": "Maize", "category": "pest"},
    {"q": "Remedies for yellow rust in wheat?", "lang": "en", "expected_crop": "Wheat", "category": "disease"},
    {"q": "Rhizome rot treatment in turmeric farming?", "lang": "en", "expected_crop": "Turmeric", "category": "disease"},
    {"q": "Anthracnose and hopper control in mango orchards?", "lang": "en", "expected_crop": "Mango", "category": "disease"},
    {"q": "How to manage stem borer in sugarcane cultivation?", "lang": "en", "expected_crop": "Sugarcane", "category": "pest"},

    # 31-35: Natural & Organic Farming Queries
    {"q": "సేంద్రీయ వ్యవసాయంలో జీవామృతం ఎలా తయారు చేసుకోవాలి?", "lang": "te", "expected_crop": None, "category": "organic"},
    {"q": "జీవామృతం పంటలకు ఎలా మరియు ఎంత మోతాదులో ఇవ్వాలి?", "lang": "te", "expected_crop": None, "category": "organic"},
    {"q": "जैविक खेती में बीजामृत और नीमास्त्र बनाने की विधि?", "lang": "hi", "expected_crop": None, "category": "organic"},
    {"q": "हांडी खाद और कंपोस्ट बनाने की तकनीक बताएं", "lang": "hi", "expected_crop": None, "category": "organic"},
    {"q": "How to prepare organic Jeevamrit and Panchagavya for pest resistance?", "lang": "en", "expected_crop": None, "category": "organic"},

    # 36-40: Fertilizer & Irrigation Management Queries
    {"q": "వరిలో మొదటి దశలో ఎంత యూరియా మరియు డిఎపి వేయాలి?", "lang": "te", "expected_crop": "Rice", "category": "fertilizer"},
    {"q": "పత్తి పంటలో పూత దశలో పొటాష్ ఎరువు ఎంత అవసరం?", "lang": "te", "expected_crop": "Cotton", "category": "fertilizer"},
    {"q": "गेहूं में कल्ले फूटते समय कौन सा उर्वरक और पानी देना चाहिए?", "lang": "hi", "expected_crop": "Wheat", "category": "fertilizer"},
    {"q": "मक्का में नत्रजन और जिंक सल्फेट की अनुशंसित मात्रा क्या है?", "lang": "hi", "expected_crop": "Maize", "category": "fertilizer"},
    {"q": "Fertilizer dosage for chilli at flowering and fruiting stage?", "lang": "en", "expected_crop": "Chilli", "category": "fertilizer"},

    # 41-45: Multi-Turn Conversational Follow-up Queries (Context inheritance)
    {
        "q": "ఇప్పుడు అందులో సేంద్రీయంగా ఎలా నివారించాలి?",
        "lang": "te",
        "expected_crop": "Cotton",
        "category": "followup",
        "history": [
            {"query": "పత్తిలో గులాబీ రంగు పురుగు నివారణ ఏమిటి?", "answer": "పత్తిలో ఎమామెక్టిన్ బెంజోయేట్ లేదా వేప నూనె పిచికారీ చేయాలి.", "crop": "Cotton"}
        ]
    },
    {
        "q": "మరి ఎరువుల మోతాదు ఎంత వేయాలి?",
        "lang": "te",
        "expected_crop": "Rice",
        "category": "followup",
        "history": [
            {"query": "వరిలో అగ్గి తెగులు నివారణ", "answer": "ట్రైసైక్లాజోల్ 0.6 గ్రా/లీటరు పిచికారీ చేయండి.", "crop": "Rice"}
        ]
    },
    {
        "q": "इसके लिए नीम के तेल का छिड़काव कैसे करें?",
        "lang": "hi",
        "expected_crop": "Chilli",
        "category": "followup",
        "history": [
            {"query": "मिर्च में थ्रिप्स कीट की समस्या है", "answer": "मिर्च में थ्रिप्स के लिए नीम अर्क या कीटनाशक का प्रयोग करें।", "crop": "Chilli"}
        ]
    },
    {
        "q": "What is the right harvesting time for this crop?",
        "lang": "en",
        "expected_crop": "Tomato",
        "category": "followup",
        "history": [
            {"query": "How to grow hybrid tomato successfully?", "answer": "Maintain well-drained loamy soil and balanced NPK.", "crop": "Tomato"}
        ]
    },
    {
        "q": "What is the recommended storage temperature?",
        "lang": "en",
        "expected_crop": "Onion",
        "category": "followup",
        "history": [
            {"query": "How to prevent rot in onion after harvest?", "answer": "Cure onions thoroughly in shade before storing.", "crop": "Onion"}
        ]
    },

    # 46-48: Ambiguous Queries (Testing Clarification Response)
    {"q": "పురుగు మందు ఎంత మోతాదులో కొట్టాలి?", "lang": "te", "expected_crop": None, "is_ambiguous_expected": True, "category": "ambiguous"},
    {"q": "कीटनाशक का सही छिड़काव कैसे करें?", "lang": "hi", "expected_crop": None, "is_ambiguous_expected": True, "category": "ambiguous"},
    {"q": "What is the right dosage of pesticide per pump?", "lang": "en", "expected_crop": None, "is_ambiguous_expected": True, "category": "ambiguous"},

    # 49-50: Safety Gate Intercepts (Banned chemical test)
    {"q": "వరిలో మోనోక్రోటోఫాస్ వాడవచ్చా?", "lang": "te", "expected_crop": "Rice", "category": "safety_intercept"},
    {"q": "Can I spray monocrotophos on tomato crop?", "lang": "en", "expected_crop": "Tomato", "category": "safety_intercept"}
]

def run_tests():
    client = httpx.Client(timeout=30.0)

    # 1. Login demo farmer
    otp_res = client.post(f"{BASE_URL}/auth/request-otp", json={"phone_number": "9876543210"})
    otp = otp_res.json().get("dev_otp", "123456")
    v_res = client.post(f"{BASE_URL}/auth/verify-otp", json={"phone_number": "9876543210", "otp": otp, "preferred_lang": "te", "full_name": "Test Farmer"})
    token = v_res.json()["access_token"]
    headers = {"Authorization": f"Bearer {token}"}

    print("=" * 80)
    print("RUNNING 50 COMPREHENSIVE CONVERSATIONAL TESTS ACROSS TELUGU, HINDI & ENGLISH")
    print("=" * 80)

    results = []
    passed_count = 0

    for idx, item in enumerate(QUERIES, 1):
        q = item["q"]
        lang = item["lang"]
        history = item.get("history", None)

        payload = {
            "query_text": q,
            "language": lang,
            "crop_name": None, # NO mandatory crop selection!
            "conversation_history": history
        }

        resp = client.post(f"{BASE_URL}/voice/query", json=payload, headers=headers)
        if resp.status_code != 200:
            print(f"[{idx}/50] FAILED: HTTP {resp.status_code}")
            continue

        data = resp.json()
        crop_id = data.get("crop_identified")
        crop_src = data.get("crop_source")
        status = data.get("status")
        is_safe = data.get("is_safe")
        followups = data.get("follow_up_suggestions", [])
        response_text = data.get("response_text", "")

        is_passed = True
        notes = []

        if item.get("is_ambiguous_expected"):
            if status == "needs_clarification" or data.get("is_ambiguous"):
                notes.append("Correctly triggered clarification prompt")
            else:
                notes.append("Did not trigger clarification prompt")
                is_passed = False
        elif item.get("expected_crop"):
            exp = item["expected_crop"]
            if crop_id and exp.lower() in crop_id.lower():
                notes.append(f"Crop matched: {crop_id} (source: {crop_src})")
            else:
                notes.append(f"Crop mismatch: got '{crop_id}', expected '{exp}'")
                is_passed = False

        if item["category"] == "safety_intercept":
            if not is_safe or "banned" in response_text.lower() or "నిషేధ" in response_text or "प्रतिबंधित" in response_text:
                notes.append("Safety gate correctly triggered warning")
            else:
                notes.append("Safety gate warning missing")
                is_passed = False

        if followups:
            notes.append(f"{len(followups)} follow-ups generated")

        if is_passed:
            passed_count += 1
            status_str = "PASS"
        else:
            status_str = "CHECK"

        # Sanitize for windows console printing
        safe_q = q.encode('ascii', 'backslashreplace').decode('ascii')
        print(f"[{idx:02d}/50] [{status_str}] Lang: {lang:2s} | Cat: {item['category']:15s} | Q: {safe_q[:40]}... | {'; '.join(notes)}")

        results.append({
            "index": idx,
            "query": q,
            "language": lang,
            "category": item["category"],
            "crop_identified": crop_id,
            "crop_source": crop_src,
            "status": status,
            "is_safe": is_safe,
            "follow_ups": followups,
            "response_preview": response_text[:120],
            "passed": is_passed
        })

    print("=" * 80)
    print(f"COMPLETED: {passed_count}/50 TESTS PASSED ({(passed_count/50)*100:.1f}%)")
    print("=" * 80)

    with open("test_50_results.json", "w", encoding="utf-8") as f:
        json.dump(results, f, ensure_ascii=False, indent=2)

if __name__ == "__main__":
    run_tests()
