import asyncio
import json
import os
import sys
from typing import Dict, Any, List

# Ensure backend root is on sys.path
sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from app.ai.rag_service import rag_service
from app.ai.gemini_service import gemini_service
from app.ai.query_understanding import QueryUnderstanding
from app.safety.pesticide_validator import PesticideValidator

TEST_QUESTIONS_30 = [
    # --- Cotton (5 distinct intents) ---
    {"id": "Q01", "crop": "Cotton", "intent": "irrigation", "lang": "en", "q": "How much water does cotton require and what are critical irrigation stages?"},
    {"id": "Q02", "crop": "Cotton", "intent": "fertilizer_soil", "lang": "en", "q": "Which fertilizer schedule and NPK dose is suitable for cotton?"},
    {"id": "Q03", "crop": "Cotton", "intent": "pest_management", "lang": "en", "q": "How can I control pink bollworm in cotton?"},
    {"id": "Q04", "crop": "Cotton", "intent": "crop_disease", "lang": "en", "q": "What diseases commonly affect cotton crop and how to identify bacterial blight?"},
    {"id": "Q05", "crop": "Cotton", "intent": "fertilizer_soil", "lang": "en", "q": "How can I improve black soil for cotton cultivation?"},
    
    # --- Cotton Telugu (5 distinct intents) ---
    {"id": "Q06", "crop": "Cotton", "intent": "irrigation", "lang": "te", "q": "పత్తి పంటలో నీటి యాజమాన్యం మరియు నీరు పెట్టే కీలక దశలు ఏమిటి?"},
    {"id": "Q07", "crop": "Cotton", "intent": "fertilizer_soil", "lang": "te", "q": "పత్తిలో యూరియా, డిఎపి ఎరువులు ఏ మోతాదులో ఎప్పుడు వేయాలి?"},
    {"id": "Q08", "crop": "Cotton", "intent": "pest_management", "lang": "te", "q": "ప్రత్తిలో గులాబీ రంగు పురుగు నివారణకు ఏ మందు వాడాలి?"},
    {"id": "Q09", "crop": "Cotton", "intent": "crop_disease", "lang": "te", "q": "పత్తిలో ఆకుమచ్చ తెగులు నివారణ పద్ధతులు తెలపండి."},
    {"id": "Q10", "crop": "Cotton", "intent": "weed_management", "lang": "te", "q": "పత్తి పంటలో కలుపు నివారణకు ఏ మందు కొట్టాలి?"},

    # --- Rice / Paddy (5 distinct intents) ---
    {"id": "Q11", "crop": "Rice", "intent": "crop_disease", "lang": "te", "q": "వరిలో అగ్గితెగులు (బ్లాస్ట్) వస్తే ఏ మందు స్ప్రే చేయాలి?"},
    {"id": "Q12", "crop": "Rice", "intent": "pest_management", "lang": "te", "q": "వరి పంటలో సుడిదోమ (BPH) నివారణకు సిఫార్సు చేసిన మందు ఏది?"},
    {"id": "Q13", "crop": "Rice", "intent": "irrigation", "lang": "te", "q": "వరి పంటకు ప్రత్యామ్నాయ తడి మరియు పొడి (AWD) నీటి పద్ధతి ఎలా చేయాలి?"},
    {"id": "Q14", "crop": "Rice", "intent": "fertilizer_soil", "lang": "en", "q": "What is the recommended zinc and urea dose for paddy?"},
    {"id": "Q15", "crop": "Rice", "intent": "weed_management", "lang": "en", "q": "How to control broadleaf weeds and grasses in transplanted rice?"},

    # --- Chilli / Tomato (5 questions) ---
    {"id": "Q16", "crop": "Chilli", "intent": "pest_management", "lang": "te", "q": "మిరపలో తామర పురుగులు (థ్రిప్స్) నివారణకు ఏ మందు వాడాలి?"},
    {"id": "Q17", "crop": "Chilli", "intent": "crop_disease", "lang": "te", "q": "మిరపలో ఆకు ముడత తెగులు నివారణ మార్గాలు ఏమిటి?"},
    {"id": "Q18", "crop": "Tomato", "intent": "crop_disease", "lang": "hi", "q": "टमाटर में अगेती झुलसा (Early Blight) रोग की रोकथाम कैसे करें?"},
    {"id": "Q19", "crop": "Tomato", "intent": "sowing_cultivation", "lang": "hi", "q": "टमाटर की नर्सरी तैयार करने और बीज दर क्या होनी चाहिए?"},
    {"id": "Q20", "crop": "Chilli", "intent": "mandi_market", "lang": "en", "q": "What is the modal mandi price for dry chilli in Warangal market?"},

    # --- Groundnut / Maize / Wheat (5 questions in Hindi & English) ---
    {"id": "Q21", "crop": "Groundnut", "intent": "fertilizer_soil", "lang": "en", "q": "Why is gypsum application critical for groundnut pod development?"},
    {"id": "Q22", "crop": "Groundnut", "intent": "harvesting", "lang": "en", "q": "How to properly harvest and dry groundnut pods to prevent aflatoxin contamination?"},
    {"id": "Q23", "crop": "Maize", "intent": "pest_management", "lang": "hi", "q": "मक्का में फॉल आर्मीवर्म (कतरनी सुंडी) का नियंत्रण कैसे करें?"},
    {"id": "Q24", "crop": "Wheat", "intent": "crop_disease", "lang": "hi", "q": "गेहूं में पीला रतुआ (Yellow Rust) की पहचान और उपचार क्या है?"},
    {"id": "Q25", "crop": "Wheat", "intent": "irrigation", "lang": "hi", "q": "गेहूं की फसल में सी.आर.आई (CRI) अवस्था में सिंचाई का क्या महत्व है?"},

    # --- Edge Cases & Out-of-Domain (5 questions) ---
    {"id": "Q26", "crop": "Cotton", "intent": "organic_farming", "lang": "te", "q": "పత్తిలో వేపనూనె లేదా పంచగవ్య తయారీ విధానం ఏమిటి?"},
    {"id": "Q27", "crop": None, "intent": "fertilizer_soil", "lang": "en", "q": "How do I test soil pH and electrical conductivity?"},
    {"id": "Q28", "crop": None, "intent": "weather_advisory", "lang": "te", "q": "ఈ వారం వర్ష సూచన మరియు వాతావరణ హెచ్చరికలు ఏమిటి?"},
    {"id": "Q29", "crop": None, "intent": "unrelated", "lang": "en", "q": "What is the price of Bitcoin and how does blockchain work?"},
    {"id": "Q30", "crop": None, "intent": "unrelated", "lang": "te", "q": "ఈ రోజు హైదరాబాద్ లో సినిమా టిక్కెట్లు ఎలా బుక్ చేసుకోవాలి?"}
]

async def run_diagnostics():
    validator = PesticideValidator()
    print("=" * 80)
    print("BOL KISAN 30-QUERY RAG & GENERATION DIAGNOSTIC SUITE")
    print("=" * 80)

    results = []
    
    for item in TEST_QUESTIONS_30:
        qid = item["id"]
        qtext = item["q"]
        lang = item["lang"]
        req_crop = item["crop"]
        expected_intent = item["intent"]

        # Step 1: Query Understanding
        analysis = QueryUnderstanding.analyze_query(qtext, context_crop=req_crop)
        detected_crop = analysis.get("crop")
        detected_intent = analysis.get("intent")
        target_entity = analysis.get("target_entity")
        recommended_cols = analysis.get("recommended_collections", [])

        # Step 2: RAG Retrieval
        retrieved_docs = rag_service.retrieve(qtext, crop=detected_crop, top_k=3)
        top_doc = retrieved_docs[0] if retrieved_docs else None
        top_doc_id = top_doc.get("document_id") if top_doc else None
        top_doc_col = top_doc.get("collection_id") if top_doc else None
        top_doc_title = top_doc.get("title") if top_doc else None
        top_doc_score = top_doc.get("score") if top_doc else 0.0

        retrieved_summary = [
            {"id": d.get("document_id"), "col": d.get("collection_id"), "title": d.get("title")[:50], "score": d.get("score")}
            for d in retrieved_docs
        ]

        # Step 3: Safety Gate validation
        safety_decision = validator.validate_pesticide_use(crop=detected_crop, pest_or_disease=target_entity)
        safety_context = {"approved_product": safety_decision.approved_product} if safety_decision and safety_decision.approved_product else None

        # Step 4: Online Deterministic Fallback simulation
        fallback_res = gemini_service._generate_grounded_fallback(
            query=qtext,
            retrieved_contexts=retrieved_docs,
            language=lang,
            safety_context=safety_context,
            query_analysis=analysis
        )
        fallback_text = fallback_res.get("text", "")
        fallback_model = fallback_res.get("model_used", "")

        # Step 5: Online Gemini Generation (Live)
        online_res = await gemini_service.generate_agricultural_response(
            query=qtext,
            retrieved_contexts=retrieved_docs,
            language=lang,
            safety_context=safety_context,
            query_analysis=analysis
        )
        online_text = online_res.get("text", "")
        online_model = online_res.get("model_used", "")
        online_is_fallback = online_res.get("is_fallback", False)

        record = {
            "id": qid,
            "query": qtext,
            "lang": lang,
            "expected_crop": req_crop,
            "detected_crop": detected_crop,
            "expected_intent": expected_intent,
            "detected_intent": detected_intent,
            "recommended_cols": recommended_cols,
            "top_doc": {
                "id": top_doc_id,
                "col": top_doc_col,
                "title": top_doc_title,
                "score": top_doc_score
            },
            "retrieved_summary": retrieved_summary,
            "fallback_answer": fallback_text,
            "fallback_model": fallback_model,
            "online_answer": online_text,
            "online_model": online_model,
            "online_is_fallback": online_is_fallback
        }
        results.append(record)

        safe_q = qtext.encode('ascii', errors='backslashreplace').decode('ascii')
        safe_fb = fallback_text.replace('\n', ' ')[:80].encode('ascii', errors='backslashreplace').decode('ascii')
        safe_on = online_text.replace('\n', ' ')[:80].encode('ascii', errors='backslashreplace').decode('ascii')

        print(f"[{qid}] {lang.upper()} | Expected: {req_crop}/{expected_intent} | Detected: {detected_crop}/{detected_intent}")
        print(f"     Top Doc: [{top_doc_col}] {top_doc_id} - {top_doc_title} (score: {top_doc_score})")
        print(f"     Fallback: ({fallback_model}) {safe_fb}...")
        print(f"     Online:   ({online_model}, fallback={online_is_fallback}) {safe_on}...")
        print("-" * 80)

    # Analyze Repeated Answers
    fallback_answers = [r["fallback_answer"] for r in results]
    online_answers = [r["online_answer"] for r in results]
    top_doc_ids = [r["top_doc"]["id"] for r in results]

    from collections import Counter
    top_doc_counts = Counter(top_doc_ids)
    fallback_counts = Counter(fallback_answers)

    print("\n" + "=" * 80)
    print("DIAGNOSTIC SUMMARY & COLLISION ANALYSIS")
    print("=" * 80)
    print("Top Retrieved Doc Frequency (Collision Detection):")
    for doc_id, count in top_doc_counts.most_common(8):
        print(f"  {doc_id}: {count} occurrences / 30 queries ({count/30*100:.1f}%)")

    print("\nFallback Answer Frequency (Collision Detection):")
    for ans, count in fallback_counts.most_common(8):
        snippet = ans.replace('\n', ' ')[:70].encode('ascii', errors='backslashreplace').decode('ascii')
        print(f"  [{count}x]: {snippet}...")

    # Save complete JSON
    out_path = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "phase1_reproduction_results.json")
    with open(out_path, "w", encoding="utf-8") as f:
        json.dump(results, f, ensure_ascii=False, indent=2)
    print(f"\nDetailed reproduction results written to: {out_path}")

if __name__ == "__main__":
    asyncio.run(run_diagnostics())
