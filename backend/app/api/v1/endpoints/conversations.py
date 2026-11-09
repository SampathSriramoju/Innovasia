import uuid
from datetime import datetime, timezone
from fastapi import APIRouter, Depends, HTTPException, status
from pydantic import BaseModel, Field
from typing import List, Optional, Dict, Any
from sqlalchemy.orm import Session
from app.database.session import get_db
from app.database.models import User, Conversation, ConversationMessage, AgriculturalTicket
from app.core.security import get_current_user_payload
from app.safety.safety_gate import safety_gate
from app.ai.gemini_service import gemini_service
from app.ai.rag_service import rag_service

router = APIRouter()

def generate_ticket_number() -> str:
    hex_suffix = uuid.uuid4().hex[:4].upper()
    return f"BK-2026-{hex_suffix}"

class ConversationCreate(BaseModel):
    title: str = Field(..., min_length=1, max_length=200)
    crop: str = "Maize"
    language: str = "te" # 'te', 'hi', 'en'

class MessagePost(BaseModel):
    query_text: str = Field(..., min_length=1)
    transcript: Optional[str] = None
    language: Optional[str] = "te"
    crop: Optional[str] = "Maize"
    pest_or_disease: Optional[str] = None
    chemical_mentioned: Optional[str] = None
    force_escalation: Optional[bool] = False

@router.get("/")
async def list_conversations(
    current_user: Dict[str, Any] = Depends(get_current_user_payload),
    db: Session = Depends(get_db)
):
    user_id = current_user.get("sub")
    convs = db.query(Conversation).filter(
        Conversation.farmer_id == user_id
    ).order_by(Conversation.updated_at.desc()).all()

    return [
        {
            "id": c.id,
            "title": c.title,
            "crop": c.crop,
            "language": c.language,
            "message_count": c.message_count,
            "escalation_status": c.escalation_status,
            "created_at": c.created_at.isoformat() if c.created_at else None,
            "updated_at": c.updated_at.isoformat() if c.updated_at else None
        }
        for c in convs
    ]

@router.post("/")
async def create_conversation(
    payload: ConversationCreate,
    current_user: Dict[str, Any] = Depends(get_current_user_payload),
    db: Session = Depends(get_db)
):
    user_id = current_user.get("sub")
    conv = Conversation(
        farmer_id=user_id,
        title=payload.title,
        crop=payload.crop,
        language=payload.language
    )
    db.add(conv)
    db.commit()
    db.refresh(conv)

    return {
        "id": conv.id,
        "title": conv.title,
        "crop": conv.crop,
        "language": conv.language,
        "message_count": conv.message_count,
        "escalation_status": conv.escalation_status,
        "created_at": conv.created_at.isoformat()
    }

@router.get("/{conversation_id}")
async def get_conversation(
    conversation_id: str,
    current_user: Dict[str, Any] = Depends(get_current_user_payload),
    db: Session = Depends(get_db)
):
    role = current_user.get("role")
    user_id = current_user.get("sub")

    if role == "farmer":
        conv = db.query(Conversation).filter(
            Conversation.id == conversation_id,
            Conversation.farmer_id == user_id
        ).first()
        if not conv:
            raise HTTPException(status_code=404, detail="Conversation not found")
    elif role == "extension_officer":
        assigned_mandals = current_user.get("mandals") or []
        assigned_district = current_user.get("district")
        assigned_state = current_user.get("state")

        if not assigned_mandals:
            raise HTTPException(
                status_code=status.HTTP_403_FORBIDDEN,
                detail="Access denied: Extension officer has no assigned jurisdiction."
            )

        ticket = db.query(AgriculturalTicket).filter(AgriculturalTicket.conversation_id == conversation_id).first()
        if not ticket:
            raise HTTPException(
                status_code=status.HTTP_403_FORBIDDEN,
                detail="Access denied: Extension officers can only retrieve conversations that have an associated escalated ticket."
            )

        mandal_ok = (ticket.mandal in assigned_mandals)
        district_ok = (not assigned_district) or (ticket.district == assigned_district)
        state_ok = (not assigned_state) or (ticket.state == assigned_state)
        assigned_to_me = (ticket.assigned_officer_id == user_id)

        if not (assigned_to_me or (mandal_ok and district_ok and state_ok)):
            raise HTTPException(
                status_code=status.HTTP_403_FORBIDDEN,
                detail="Access denied: Conversation ticket is outside officer's assigned jurisdiction."
            )

        conv = db.query(Conversation).filter(Conversation.id == conversation_id).first()
    elif role == "admin":
        conv = db.query(Conversation).filter(Conversation.id == conversation_id).first()
    else:
        raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="Access denied: Unauthorized role.")

    if not conv:
        raise HTTPException(status_code=404, detail="Conversation not found or access denied")

    messages = db.query(ConversationMessage).filter(
        ConversationMessage.conversation_id == conv.id
    ).order_by(ConversationMessage.timestamp.asc()).all()

    return {
        "id": conv.id,
        "title": conv.title,
        "crop": conv.crop,
        "language": conv.language,
        "message_count": conv.message_count,
        "escalation_status": conv.escalation_status,
        "created_at": conv.created_at.isoformat() if conv.created_at else None,
        "messages": [
            {
                "id": m.id,
                "sender_type": m.sender_type,
                "original_query": m.original_query,
                "transcript": m.transcript,
                "response": m.response,
                "source_citations": m.source_citations or [],
                "language": m.language,
                "is_safe": m.is_safe,
                "is_escalated": m.is_escalated,
                "ticket_id": m.ticket_id,
                "timestamp": m.timestamp.isoformat() if m.timestamp else None
            }
            for m in messages
        ]
    }

@router.post("/{conversation_id}/messages")
async def post_message_to_conversation(
    conversation_id: str,
    payload: MessagePost,
    current_user: Dict[str, Any] = Depends(get_current_user_payload),
    db: Session = Depends(get_db)
):
    user_id = current_user.get("sub")
    conv = db.query(Conversation).filter(
        Conversation.id == conversation_id,
        Conversation.farmer_id == user_id
    ).first()

    if not conv:
        raise HTTPException(status_code=404, detail="Conversation not found")

    query_text = payload.query_text
    lang = payload.language or conv.language
    crop = payload.crop or conv.crop

    from app.ai.query_understanding import QueryUnderstanding

    # Intelligent Query Understanding
    analysis = QueryUnderstanding.analyze_query(query_text, context_crop=payload.crop or conv.crop)
    query_lang = payload.language or QueryUnderstanding.detect_language(query_text) or conv.language
    crop = analysis.get("crop") or payload.crop or conv.crop
    target_entity = payload.pest_or_disease or analysis.get("target_entity")

    # Save Farmer Message
    farmer_msg = ConversationMessage(
        conversation_id=conv.id,
        sender_type="FARMER",
        original_query=query_text,
        transcript=payload.transcript or query_text,
        response="",
        language=query_lang,
        is_safe=True,
        is_escalated=False
    )
    db.add(farmer_msg)

    # Check for manual escalation request or safety violation
    is_forced = payload.force_escalation or any(kw in query_text.lower() for kw in ["officer", "help me officer", "mao", "మావో", "అధికారి", "अधिकारी"])

    safety_decision, meta = safety_gate.evaluate_query(
        query_text=query_text,
        crop=crop,
        pest_or_disease=target_entity,
        chemical=payload.chemical_mentioned
    )

    if is_forced or safety_decision.needs_escalation:
        ticket_num = generate_ticket_number()
        farmer_user = db.query(User).filter(User.id == user_id).first()
        farmer_profile = farmer_user.farmer_profile if farmer_user else None
        state = farmer_profile.state if (farmer_profile and farmer_profile.state) else "Telangana"
        district = farmer_profile.district if (farmer_profile and farmer_profile.district) else "Warangal"
        mandal = farmer_profile.mandal if (farmer_profile and farmer_profile.mandal) else "Geesugonda"
        village = farmer_profile.village if (farmer_profile and farmer_profile.village) else "Dharmaram"

        ticket = AgriculturalTicket(
            ticket_number=ticket_num,
            farmer_id=user_id,
            conversation_id=conv.id,
            crop_id=None,
            state=state,
            district=district,
            mandal=mandal,
            village=village,
            status="PENDING",
            priority="emergency" if safety_decision.needs_escalation else "high",
            escalation_reason=safety_decision.escalation_reason or "Farmer requested Human Extension Officer consultation",
            original_question_transcript=query_text
        )
        db.add(ticket)
        db.flush()

        conv.escalation_status = "ESCALATED"
        conv.message_count += 2
        conv.updated_at = datetime.now(timezone.utc)

        if lang == "te":
            resp_text = (
                f"⚠️ మీ ప్రశ్న వ్యవసాయ విస్తరణ అధికారికి (MAO) పంపబడింది. "
                f"అధికారిక రసాయన భద్రతా నిబంధనల ప్రకారం ధృవీకరించిన సమాధానం త్వరలో అందుతుంది.\n"
                f"టికెట్ సంఖ్య: {ticket_num}"
            )
        elif lang == "hi":
            resp_text = (
                f"⚠️ आपका प्रश्न मंडल कृषि विस्तार अधिकारी को भेज दिया गया है। "
                f"सीआईबीआरसी नियमों के अनुसार सत्यापित उत्तर जल्द प्राप्त होगा।\n"
                f"टिकट संख्या: {ticket_num}"
            )
        else:
            resp_text = (
                f"⚠️ Your question has been escalated to a Human Agricultural Extension Officer. "
                f"A verified prescription adhering to CIBRC standards will be provided shortly.\n"
                f"Ticket ID: {ticket_num}"
            )

        ai_msg = ConversationMessage(
            conversation_id=conv.id,
            sender_type="AI",
            original_query=query_text,
            transcript="",
            response=resp_text,
            source_citations=["CIBRC National Regulatory Database", "Human Officer Escalation Queue"],
            language=lang,
            is_safe=False if safety_decision.needs_escalation else True,
            is_escalated=True,
            ticket_id=ticket.id
        )
        db.add(ai_msg)
        db.commit()

        return {
            "status": "escalated",
            "is_safe": False if safety_decision.needs_escalation else True,
            "is_escalated": True,
            "response_text": resp_text,
            "ticket_number": ticket_num,
            "ticket_id": ticket.id,
            "citations": ["CIBRC Regulatory Database"]
        }

    # Safe Query: RAG retrieval & Gemini synthesis
    retrieved_chunks = rag_service.retrieve(query=query_text, crop=crop, top_k=3)
    safety_context = {
        "approved_product": safety_decision.approved_product,
        "citation": safety_decision.source_citation
    } if safety_decision.approved_product else None

    ai_result = await gemini_service.generate_agricultural_response(
        query=query_text,
        retrieved_contexts=retrieved_chunks,
        language=query_lang,
        safety_context=safety_context,
        query_analysis=analysis
    )
    generated_text = ai_result["text"]

    citations = [c.get("citation") for c in retrieved_chunks] if retrieved_chunks else [safety_decision.source_citation]

    conv.message_count += 2
    conv.updated_at = datetime.now(timezone.utc)

    ai_msg = ConversationMessage(
        conversation_id=conv.id,
        sender_type="AI",
        original_query=query_text,
        transcript="",
        response=generated_text,
        source_citations=citations,
        language=lang,
        is_safe=True,
        is_escalated=False
    )
    db.add(ai_msg)
    db.commit()

    return {
        "status": "answered",
        "is_safe": True,
        "is_escalated": False,
        "response_text": generated_text,
        "language": lang,
        "citations": citations
    }

@router.delete("/{conversation_id}")
async def delete_conversation(
    conversation_id: str,
    current_user: Dict[str, Any] = Depends(get_current_user_payload),
    db: Session = Depends(get_db)
):
    user_id = current_user.get("sub")
    conv = db.query(Conversation).filter(
        Conversation.id == conversation_id,
        Conversation.farmer_id == user_id
    ).first()

    if not conv:
        raise HTTPException(status_code=404, detail="Conversation not found")

    db.delete(conv)
    db.commit()
    return {"status": "deleted", "id": conversation_id}
