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
    crop_name: Optional[str] = None
    pest_or_disease: Optional[str] = None
    chemical_mentioned: Optional[str] = None
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
    Core Voice & Agricultural Query Processing Pipeline.
    Strictly enforces: NEVER GENERATE UNSUPPORTED AGRICULTURAL ADVICE.
    """
    user_id = current_user.get("sub")
    query_text = payload.query_text
    lang = payload.language or QueryUnderstanding.detect_language(query_text)

    # Step 0: Intelligent Query Understanding & Entity Extraction
    analysis = QueryUnderstanding.analyze_query(query_text, context_crop=payload.crop_name)
    crop = analysis.get("crop")
    target_entity = payload.pest_or_disease or analysis.get("target_entity")

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
        query_analysis=analysis
    )

    generated_text = ai_result["text"]

    # Step 5: Post-Generation Deterministic Safety Validation
    is_post_safe = safety_gate.validate_llm_response(generated_text, safety_decision)
    if not is_post_safe:
        # LLM hallucinated unapproved numbers -> Fallback directly to verified CIBRC database record
        p = safety_decision.approved_product
        if lang == "te":
            generated_text = f"CIBRC అధికారిక మోతాదు: ఎకరాకు {p.get('dosage_per_acre')} ({p.get('dosage_per_hectare')}). {p.get('safety_precautions')}"
        else:
            generated_text = f"Official CIBRC Dosage: {p.get('dosage_per_acre')} ({p.get('dosage_per_hectare')}). {p.get('safety_precautions')}"

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
        "topic_identified": analysis.get("intent"),
        "model_used": ai_result.get("model"),
        "provider": ai_result.get("provider"),
        "claims": ai_result.get("claims", []),
        "evidence_sufficient": ai_result.get("evidence_sufficient", True),
        "citations": [c.get("citation") for c in retrieved_chunks] if retrieved_chunks else [safety_decision.source_citation],
        "warnings": safety_decision.warnings,
        "tts_playback_url": f"/api/v1/voice/tts-stream?text_hash={hash(generated_text)}"
    }
