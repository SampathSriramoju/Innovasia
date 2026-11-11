import hashlib
import uuid
from datetime import datetime, timezone
from fastapi import APIRouter, Depends, HTTPException, UploadFile, File, Form
from pydantic import BaseModel
from typing import Optional, List, Dict, Any
from sqlalchemy import func
from sqlalchemy.orm import Session
from app.database.session import get_db
from app.database.models import User, AgriculturalTicket, TicketMessage, Crop
from app.core.security import get_current_user_payload
from app.safety.safety_gate import safety_gate
from app.ai.gemini_service import gemini_service
from app.ai.rag_service import rag_service

from app.ai.query_understanding import QueryUnderstanding

router = APIRouter()

class VoiceQueryRequest(BaseModel):
    query_text: str
    language: Optional[str] = "te" # 'te', 'hi', 'en'
    crop_name: Optional[str] = None # Optional confirmed/inferred crop
    pest_or_disease: Optional[str] = None
    chemical_mentioned: Optional[str] = None
    conversation_history: Optional[List[Dict[str, Any]]] = None # Multi-turn conversation context
    state: Optional[str] = "Telangana"
    district: Optional[str] = "Warangal"
    mandal: Optional[str] = "Geesugonda"

MANDI_DISTRICT_PATTERNS = [
    ("Warangal", ["warangal", "వరంగల్", "वारंगल"]),
    ("Adilabad", ["adilabad", "అదిలాబాద్", "ఆదిలాబాద్", "आदिलाबाद"]),
    ("Khammam", ["khammam", "ఖమ్మం", "खम्मम"]),
    ("Suryapet", ["suryapet", "సూర్యాపేట", "सूर्యాपेट"]),
    ("Nizamabad", ["nizamabad", "నిజామాబాద్", "निजामाबाद"]),
    ("Karimnagar", ["karimnagar", "కరీంనగర్", "करीमनगर"]),
    ("Nalgonda", ["nalgonda", "నల్గొండ", "నల్లగొండ", "नलगोंडा"]),
    ("Mahbubnagar", ["mahbubnagar", "మహబూబ్ నగర్", "महबूबनगर"]),
    ("Guntur", ["guntur", "గుంటూరు", "गुंटूर"]),
    ("Kurnool", ["kurnool", "కర్నూలు", "కర్నూల్", "कर्नूल"]),
    ("Krishna", ["krishna", "కృష్ణా", "కృష్ణ"]),
    ("Anantapur", ["anantapur", "అనంతపురం", "अनंतपुर"]),
    ("Tirupati", ["tirupati", "తిరుపతి", "तिरुपति"]),
]

MANDI_COMMODITY_PATTERNS = [
    ("Cotton", ["cotton", "kapas", "patti", "pratti", "పత్తి", "ప్రత్తి", "కపాస్", "कपास"]),
    ("Paddy", ["paddy", "rice", "vari", "dhan", "వరి", "ధాన్యం", "వరిపంట", "వడ్లు", "धान", "चावल"]),
    ("Chilli", ["chilli", "chili", "mirchi", "mirapa", "మిరప", "మిర్చి", "మిరపకాయ", "मिर्च", "मिर्ची"]),
    ("Tomato", ["tomato", "tamatar", "టమోటా", "టమాటా", "टमाटर"]),
    ("Maize", ["maize", "corn", "makka", "mokkajonna", "మొక్కజొన్న", "మక్క", "मक्का", "मक्के"]),
    ("Wheat", ["wheat", "gehun", "godhumalu", "గోధుమ", "గేహూ", "गेहूं"]),
    ("Soybean", ["soybean", "soya", "సోయాబీన్", "సోయా", "सोयाबीन"]),
    ("Chickpea", ["chickpea", "bengal gram", "chana", "senagalu", "శనగ", "చన", "चना"]),
    ("Groundnut", ["groundnut", "peanut", "palli", "వేరుశనగ", "పల్లీ", "मूंगफली"]),
    ("Turmeric", ["turmeric", "pasupu", "haldi", "పసుపు", "हल्दी"]),
    ("Onion", ["onion", "ulli", "pyaz", "ఉల్లి", "ఉల్లిపాయ", "प्याज"]),
]

def extract_mandi_entities(query: str, fallback_district: Optional[str] = None, fallback_crop: Optional[str] = None) -> Tuple[Optional[str], Optional[str]]:
    import re
    q_lower = query.lower()
    detected_crop = fallback_crop
    if not detected_crop:
        for canonical_crop, keywords in MANDI_COMMODITY_PATTERNS:
            for kw in keywords:
                if ord(kw[0]) > 127:
                    if kw in q_lower:
                        detected_crop = canonical_crop
                        break
                else:
                    if re.search(r"\b" + re.escape(kw) + r"\b", q_lower):
                        detected_crop = canonical_crop
                        break
            if detected_crop:
                break

    detected_district = None
    for canonical_dist, keywords in MANDI_DISTRICT_PATTERNS:
        for kw in keywords:
            if ord(kw[0]) > 127:
                if kw in q_lower:
                    detected_district = canonical_dist
                    break
            else:
                if re.search(r"\b" + re.escape(kw) + r"\b", q_lower):
                    detected_district = canonical_dist
                    break
        if detected_district:
            break

    if not detected_district:
        detected_district = fallback_district

    return detected_crop, detected_district


@router.post("/query")
async def process_voice_query(
    payload: VoiceQueryRequest,
    current_user: Dict[str, Any] = Depends(get_current_user_payload),
    db: Session = Depends(get_db)
):
    """
    Core Voice & Agricultural Conversational Query Processing Pipeline.
    Strictly enforces: NEVER GENERATE UNSUPPORTED AGRICULTURAL ADVICE.
    """
    user_id = current_user.get("sub")
    query_text = payload.query_text
    lang = payload.language or QueryUnderstanding.detect_language(query_text)

    # Step 0: Intelligent Query Understanding & Entity Extraction with Conversation Context
    canonical_input_crop = QueryUnderstanding.canonical_crop(payload.crop_name)
    analysis = QueryUnderstanding.analyze_query(
        query_text,
        context_crop=canonical_input_crop,
        conversation_history=payload.conversation_history
    )
    crop = analysis.get("crop")
    crop_source = analysis.get("crop_source", "unknown")
    target_entity = payload.pest_or_disease or analysis.get("target_entity")

    # Ambiguity Gate: If farmer query requires a specific crop but none is identifiable
    if analysis.get("is_ambiguous") and analysis.get("clarification_prompt"):
        clarification_msg = analysis["clarification_prompt"]
        return {
            "status": "needs_clarification",
            "is_safe": True,
            "is_fallback": False,
            "response_text": clarification_msg,
            "language": lang,
            "crop_identified": None,
            "crop_source": "unknown",
            "is_ambiguous": True,
            "clarification_prompt": clarification_msg,
            "topic_identified": analysis.get("intent"),
            "model_used": "conversational-coordinator",
            "provider": "query-understanding",
            "citations": [],
            "warnings": [],
            "follow_up_suggestions": [
                "పత్తి (Cotton)" if lang == "te" else ("कपास (Cotton)" if lang == "hi" else "Cotton"),
                "వరి (Rice)" if lang == "te" else ("धान (Rice)" if lang == "hi" else "Rice / Paddy"),
                "మిరప (Chilli)" if lang == "te" else ("मिर्च (Chilli)" if lang == "hi" else "Chilli"),
                "టమోటా (Tomato)" if lang == "te" else ("टमाटर (Tomato)" if lang == "hi" else "Tomato")
            ]
        }

    # Step 1: Deterministic Safety Gate Evaluation
    safety_decision, meta = safety_gate.evaluate_query(
        query_text=query_text,
        crop=crop,
        pest_or_disease=target_entity,
        chemical=payload.chemical_mentioned
    )

    # Step 2: If Safety Gate identifies missing, ambiguous, or unapproved chemical usage -> AUTO ESCALATE
    if safety_decision.needs_escalation:
        ticket_num = f"ESC-{datetime.now().strftime('%Y%m%d')}-{uuid.uuid4().hex[:6].upper()}"
        crop_obj = None
        if crop:
            crop_obj = db.query(Crop).filter(
                (func.lower(Crop.name_en) == crop.lower()) |
                (func.lower(Crop.name_te) == crop.lower()) |
                (func.lower(Crop.name_hi) == crop.lower())
            ).first()

        ticket = AgriculturalTicket(
            ticket_number=ticket_num,
            farmer_id=user_id,
            crop_id=crop_obj.id if crop_obj else None,
            state=payload.state or "Telangana",
            district=payload.district or "Warangal",
            mandal=payload.mandal or "Geesugonda",
            status="PENDING",
            priority="high",
            escalation_reason=safety_decision.escalation_reason or "Chemical safety ambiguity",
            original_question_transcript=query_text
        )
        db.add(ticket)
        db.flush()

        ticket_msg = TicketMessage(
            ticket_id=ticket.id,
            sender_id=user_id,
            msg_type="text",
            content=query_text
        )
        db.add(ticket_msg)
        db.commit()
        db.refresh(ticket)

        # Generate localized warning response for farmer
        if lang == "te":
            msg = (
                f"⚠️ భద్రతా హెచ్చరిక: మీరు అడిగిన రసాయన మోతాదు ధృవీకరించబడలేదు. "
                f"తప్పుడు మోతాదు పంటకు నష్టం కలిగిస్తుంది. "
                f"మీ ప్రశ్న వ్యవసాయ విస్తరణ అధికారికి (MAO) పంపబడింది. "
                f"టికెట్ సంఖ్య: {ticket_num}."
            )
        elif lang == "hi":
            msg = (
                f"⚠️ सुरक्षा चेतावनी: आपके द्वारा पूछी गई रासायनिक मात्रा CIBRC द्वारा सत्यापित नहीं है। "
                f"गलत मात्रा से फसल खराब हो सकती है। "
                f"आपका प्रश्न मंडल कृषि अधिकारी को भेज दिया गया है। "
                f"टिकट संख्या: {ticket_num}."
            )
        else:
            msg = (
                f"⚠️ Safety Warning: Chemical treatment for this query cannot be verified in official CIBRC records. "
                f"To prevent crop burn or toxic poisoning, this question has been escalated to your Mandal Extension Officer. "
                f"Ticket Number: {ticket_num}."
            )

        return {
            "status": "escalated",
            "is_safe": False,
            "response_text": msg,
            "ticket_number": ticket_num,
            "ticket_id": ticket.id,
            "reason": safety_decision.escalation_reason,
            "warnings": safety_decision.warnings,
            "source_citation": "CIBRC National Regulatory Database"
        }

    # Step 2.5: Grounded Mandi Market Price Query Pipeline (Zero Hallucination Protocol)
    is_mandi_intent = analysis.get("intent") == "mandi_market" or any(
        kw in query_text.lower() for kw in [
            "price", "mandi", "market", "rate", "modal", "msp", "quintal",
            "ధర", "రేటు", "మార్కెట్", "మండి", "క్వింటాల్", "మద్దతు ధర",
            "भाव", "मंडी", "दाम", "बाजार", "क्विंटल", "एमएसपी"
        ]
    )
    if is_mandi_intent:
        from app.services.mandi_price_service import mandi_price_service
        from app.integrations.mandi import mandi_service

        target_crop, target_district = extract_mandi_entities(
            query=query_text,
            fallback_district=payload.district or "Warangal",
            fallback_crop=crop
        )

        serialized_recs: List[Dict[str, Any]] = []
        if target_crop:
            db_records = mandi_price_service.query_db_for_advisory(
                db=db,
                commodity=target_crop,
                district=target_district
            )

            if db_records:
                for r in db_records:
                    serialized_recs.append({
                        "market": r.market,
                        "district": r.district,
                        "state": r.state,
                        "commodity": r.commodity,
                        "variety": r.variety,
                        "grade": r.grade,
                        "arrival_date": r.arrival_date,
                        "min_price": float(r.min_price),
                        "max_price": float(r.max_price),
                        "modal_price": float(r.modal_price),
                    })
            else:
                # Fallback to authentic verified AGMARKNET records if DB empty for this crop
                mandi_res = await mandi_service.get_mandi_prices(
                    commodity=target_crop,
                    district=target_district,
                    limit=5
                )
                if mandi_res and mandi_res.records:
                    for r in mandi_res.records:
                        serialized_recs.append({
                            "market": r.market,
                            "district": r.district,
                            "state": r.state,
                            "commodity": r.commodity,
                            "variety": r.variety,
                            "grade": "FAQ",
                            "arrival_date": r.arrival_date,
                            "min_price": float(r.min_price_inr),
                            "max_price": float(r.max_price_inr),
                            "modal_price": float(r.modal_price_inr),
                        })

        # Synthesize via gemini_service with strict zero-hallucination grounding
        mandi_ai_result = await gemini_service.generate_mandi_response(
            query=query_text,
            mandi_records=serialized_recs,
            commodity=target_crop,
            district=target_district,
            language=lang
        )

        ans_text = mandi_ai_result["text"]
        is_success = mandi_ai_result.get("status") == "answered"

        followups = []
        if is_success:
            if lang == "te":
                followups = ["వరి మద్దతు ధర ఎంత?", "మిరప తాజా ధరలు చెప్పండి"]
            elif lang == "hi":
                followups = ["धान का एमएसपी कितना है?", "कपास का भाव कैसा रहेगा?"]
            else:
                followups = ["What is the official MSP rate?", "Check nearby mandi prices"]

        return {
            "status": mandi_ai_result.get("status", "answered"),
            "is_safe": True,
            "is_fallback": mandi_ai_result.get("is_fallback", False),
            "response_text": ans_text,
            "language": lang,
            "crop_identified": target_crop,
            "crop_source": "mandi_entity_extractor" if target_crop else crop_source,
            "is_ambiguous": False,
            "clarification_prompt": None,
            "topic_identified": "mandi_market",
            "model_used": mandi_ai_result.get("model_used", "deterministic-mandi-verifier"),
            "provider": "gemini-mandi-grounded" if mandi_ai_result.get("model_used") != "deterministic-mandi-verifier" else "data.gov.in-agmarknet",
            "claims": mandi_ai_result.get("claims", []),
            "evidence_sufficient": mandi_ai_result.get("evidence_sufficient", False),
            "citations": mandi_ai_result.get("citations", ["data.gov.in / AGMARKNET Mandi Portal"]),
            "warnings": [],
            "follow_up_suggestions": followups,
            "tts_playback_url": f"/api/v1/voice/tts-stream?text_hash={hashlib.sha256(ans_text.encode('utf-8')).hexdigest()[:16]}"
        }

    # Step 3: Verified RAG Retrieval from 23 collections
    retrieved_chunks = rag_service.retrieve(query=query_text, crop=crop, top_k=3)

    safety_context = {
        "approved_product": safety_decision.approved_product,
        "citation": safety_decision.source_citation
    } if safety_decision.approved_product else None

    # Step 4: Synthesize via Gemini 3.8 Flash (or verified deterministic fallback)
    ai_result = await gemini_service.generate_agricultural_response(
        query=query_text,
        retrieved_contexts=retrieved_chunks,
        language=lang,
        safety_context=safety_context,
        query_analysis=analysis,
        conversation_history=payload.conversation_history
    )

    generated_text = ai_result["text"]

    # Step 5: Post-Generation Deterministic Safety Validation
    is_post_safe = safety_gate.validate_llm_response(generated_text, safety_decision)
    if not is_post_safe:
        # LLM hallucinated unapproved numbers -> Fallback directly to verified CIBRC database record
        p = safety_decision.approved_product
        if p and isinstance(p, dict):
            if lang == "te":
                generated_text = f"CIBRC అధికారిక మోతాదు: ఎకరాకు {p.get('dosage_per_acre')} ({p.get('dosage_per_hectare')}). {p.get('safety_precautions')}"
            elif lang == "hi":
                generated_text = f"CIBRC आधिकारिक मात्रा: प्रति एकड़ {p.get('dosage_per_acre')} ({p.get('dosage_per_hectare')})। {p.get('safety_precautions')}"
            else:
                generated_text = f"Official CIBRC Dosage: {p.get('dosage_per_acre')} ({p.get('dosage_per_hectare')}). {p.get('safety_precautions')}"
        else:
            if lang == "te":
                generated_text = "గమనిక: ఈ అంశానికి సంబంధించి నిర్దిష్ట CIBRC రసాయన మోతాదు ధృవీకరించబడలేదు. దయచేసి సమగ్ర సలహా కోసం స్థానిక వ్యవసాయ అధికారి (MAO) లేదా కృషి విజ్ఞాన కేంద్రాన్ని (KVK) సంప్రదించండి."
            elif lang == "hi":
                generated_text = "नोट: इस संदर्भ में कोई विशिष्ट CIBRC अनुमोदित रासायनिक मात्रा सत्यापित नहीं है। कृपया उचित मार्गदर्शन के लिए स्थानीय कृषि विस्तार अधिकारी या कृषि विज्ञान केंद्र (KVK) से संपर्क करें।"
            else:
                generated_text = "Note: No specific CIBRC approved chemical dosage is verified for this query. Please consult your local Agricultural Extension Officer or KVK for verified advice."

    # Clean all citation IDs and reference headers from farmer-facing response text
    generated_text = gemini_service._clean_response_text(generated_text)

    return {
        "status": "answered",
        "is_safe": True,
        "is_fallback": ai_result.get("is_fallback", False),
        "fallback_reason": ai_result.get("fallback_reason"),
        "response_text": generated_text,
        "language": lang,
        "crop_identified": crop,
        "crop_source": crop_source,
        "is_ambiguous": False,
        "clarification_prompt": None,
        "topic_identified": analysis.get("intent"),
        "model_used": ai_result.get("model"),
        "provider": ai_result.get("provider"),
        "claims": ai_result.get("claims", []),
        "evidence_sufficient": ai_result.get("evidence_sufficient", True),
        "citations": [c.get("citation") for c in retrieved_chunks] if retrieved_chunks else [safety_decision.source_citation],
        "warnings": safety_decision.warnings,
        "follow_up_suggestions": ai_result.get("follow_up_suggestions") or gemini_service._generate_default_followups(crop, lang),
        "tts_playback_url": f"/api/v1/voice/tts-stream?text_hash={hashlib.sha256(generated_text.encode('utf-8')).hexdigest()[:16]}"
    }

@router.get("/tts-stream")
async def tts_stream(text_hash: Optional[str] = None):
    """
    Lightweight streaming/caching endpoint for TTS playback metadata.
    On mobile/device environments, voice synthesis runs directly via native on-device TTS.
    """
    return {
        "status": "available",
        "audio_stream": None,
        "mode": "on_device_tts_preferred",
        "text_hash": text_hash
    }
