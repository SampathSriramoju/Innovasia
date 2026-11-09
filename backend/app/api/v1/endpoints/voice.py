import hashlib
import uuid
from datetime import datetime, timezone
from fastapi import APIRouter, Depends, HTTPException, UploadFile, File, Form
from pydantic import BaseModel
from typing import Optional, List, Dict, Any
from sqlalchemy.orm import Session
from app.database.session import get_db
from app.database.models import User, AgriculturalTicket
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
        ticket = AgriculturalTicket(
            ticket_number=ticket_num,
            farmer_id=user_id,
            state=payload.state or "Telangana",
            district=payload.district or "Warangal",
            mandal=payload.mandal or "Geesugonda",
            status="open",
            priority="high",
            escalation_reason=safety_decision.escalation_reason or "Chemical safety ambiguity",
            original_question_transcript=query_text
        )
        db.add(ticket)
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
