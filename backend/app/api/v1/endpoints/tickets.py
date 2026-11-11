import uuid
from datetime import datetime, timezone
from fastapi import APIRouter, Depends, HTTPException, status
from pydantic import BaseModel, Field
from typing import List, Optional, Dict, Any
from sqlalchemy import func
from sqlalchemy.orm import Session
from app.database.session import get_db
from app.database.models import (
    AgriculturalTicket, User, FarmerProfile, TicketMessage,
    ConversationMessage, Conversation, ExtensionOfficer, Crop
)
from app.core.security import get_current_user_payload

router = APIRouter()

def generate_ticket_number() -> str:
    hex_suffix = uuid.uuid4().hex[:12].upper()
    return f"BK-2026-{hex_suffix}"

class TicketMessageCreate(BaseModel):
    content: str = Field(..., min_length=1)
    msg_type: Optional[str] = "text"
    media_url: Optional[str] = None

class TicketStatusUpdate(BaseModel):
    status: str

class TicketCreate(BaseModel):
    crop_name: Optional[str] = "Cotton"
    state: Optional[str] = "Telangana"
    district: Optional[str] = "Warangal"
    mandal: Optional[str] = "Geesugonda"
    village: Optional[str] = "Dharmaram"
    escalation_reason: str
    original_question_transcript: str
    conversation_id: Optional[str] = None
    audio_recording_url: Optional[str] = None
    attached_image_urls: Optional[List[str]] = []
    priority: Optional[str] = "high"

class TicketRespond(BaseModel):
    prescription: str
    officer_notes: Optional[str] = None
    action_type: Optional[str] = "chemical"
    status: Optional[str] = "resolved" # 'resolved', 'RESPONDED', 'RESOLVED', 'WAITING_FOR_FARMER'

    def normalized_status(self) -> str:
        value = (self.status or "RESOLVED").strip().upper()
        allowed = {"PENDING", "UNDER_REVIEW", "RESPONDED", "WAITING_FOR_FARMER", "RESOLVED", "CLOSED"}
        if value not in allowed:
            raise HTTPException(status_code=422, detail="Unsupported ticket status.")
        return value

class OfflineSyncItem(BaseModel):
    client_uuid: str
    question_text: str
    crop: Optional[str] = None
    crop_name: Optional[str] = None
    language: Optional[str] = "te"
    image_url: Optional[str] = None
    created_at_epoch: Optional[int] = None
    conversation_id: Optional[str] = None

class OfflineSyncRequest(BaseModel):
    pending_tickets: List[OfflineSyncItem]

@router.get("/")
async def list_tickets(
    status_filter: Optional[str] = None,
    current_user: Dict[str, Any] = Depends(get_current_user_payload),
    db: Session = Depends(get_db)
):
    role = current_user.get("role")
    user_id = current_user.get("sub")

    query = db.query(AgriculturalTicket)

    if role == "farmer":
        query = query.filter(AgriculturalTicket.farmer_id == user_id)
    elif role == "extension_officer":
        assigned_mandals = current_user.get("mandals") or []
        assigned_district = current_user.get("district")
        assigned_state = current_user.get("state")
        if not assigned_mandals:
            # Empty jurisdiction assignments must deny access, not allow access to all tickets
            return []
        query = query.filter(AgriculturalTicket.mandal.in_(assigned_mandals))
        if assigned_district:
            query = query.filter(AgriculturalTicket.district == assigned_district)
        if assigned_state:
            query = query.filter(AgriculturalTicket.state == assigned_state)
    elif role == "admin":
        pass
    else:
        raise HTTPException(
            status_code=status.HTTP_403_FORBIDDEN,
            detail="Access denied: Unauthorized role."
        )

    if status_filter:
        sf = status_filter.strip().upper()
        if sf == "OPEN":
            query = query.filter(func.upper(AgriculturalTicket.status).notin_(["RESOLVED", "CLOSED"]))
        elif sf in ["RESOLVED", "CLOSED"]:
            query = query.filter(func.upper(AgriculturalTicket.status).in_(["RESOLVED", "CLOSED"]))
        else:
            query = query.filter(func.upper(AgriculturalTicket.status) == sf)

    tickets = query.order_by(AgriculturalTicket.created_at.desc()).all()

    results = []
    for t in tickets:
        farmer_user = db.query(User).filter(User.id == t.farmer_id).first()
        crop_name = None
        if t.crop_id:
            c_obj = db.query(Crop).filter(Crop.id == t.crop_id).first()
            if c_obj:
                crop_name = c_obj.name_en
        if not crop_name:
            # Fallback heuristics
            reason_low = (t.escalation_reason or "").lower()
            q_low = (t.original_question_transcript or "").lower()
            if "cotton" in reason_low or "ప్రత్తి" in q_low or "పత్తి" in q_low or "కపాస్" in q_low:
                crop_name = "Cotton"
            elif "chilli" in reason_low or "మిరప" in q_low or "మిర్చి" in q_low:
                crop_name = "Chilli"
            elif "rice" in reason_low or "paddy" in reason_low or "వరి" in q_low or "చావల్" in q_low:
                crop_name = "Rice"
            elif "tomato" in reason_low or "టమోటా" in q_low or "టమాటా" in q_low:
                crop_name = "Tomato"
            elif "maize" in reason_low or "మొక్కజొన్న" in q_low:
                crop_name = "Maize"
            elif t.conversation and t.conversation.crop:
                crop_name = t.conversation.crop
            else:
                crop_name = "Cotton"

        first_img = t.attached_image_urls[0] if (t.attached_image_urls and len(t.attached_image_urls) > 0) else None

        results.append({
            "id": t.id,
            "ticket_number": t.ticket_number,
            "farmer_id": t.farmer_id,
            "farmer_name": farmer_user.full_name if farmer_user else "Farmer",
            "farmer_phone": farmer_user.phone_number if farmer_user else "",
            "state": t.state,
            "district": t.district,
            "mandal": t.mandal,
            "village": t.village or "Dharmaram",
            "status": t.status,
            "priority": t.priority,
            "crop_name": crop_name,
            "escalation_reason": t.escalation_reason,
            "original_question_transcript": t.original_question_transcript,
            "resolution_prescription": t.resolution_prescription,
            "officer_response": t.officer_response,
            "photo_url": first_img,
            "attached_image_urls": t.attached_image_urls or [],
            "has_photo": bool(first_img),
            "audio_recording_url": t.audio_recording_url,
            "has_audio": bool(t.audio_recording_url),
            "created_at": t.created_at.isoformat() if t.created_at else None,
            "resolved_at": t.resolved_at.isoformat() if t.resolved_at else None
        })

    return results

@router.get("/my")
async def get_farmer_tickets(
    current_user: Dict[str, Any] = Depends(get_current_user_payload),
    db: Session = Depends(get_db)
):
    user_id = current_user.get("sub")
    tickets = db.query(AgriculturalTicket).filter(
        AgriculturalTicket.farmer_id == user_id
    ).order_by(AgriculturalTicket.created_at.desc()).all()

    return [
        {
            "id": t.id,
            "ticket_number": t.ticket_number,
            "status": t.status,
            "priority": t.priority,
            "escalation_reason": t.escalation_reason,
            "original_question_transcript": t.original_question_transcript,
            "officer_response": t.officer_response or t.resolution_prescription,
            "created_at": t.created_at.isoformat() if t.created_at else None,
            "resolved_at": t.resolved_at.isoformat() if t.resolved_at else None
        }
        for t in tickets
    ]

@router.post("/")
@router.post("/escalate")
async def create_escalated_ticket(
    payload: TicketCreate,
    current_user: Dict[str, Any] = Depends(get_current_user_payload),
    db: Session = Depends(get_db)
):
    user_id = current_user.get("sub")
    farmer_user = db.query(User).filter(User.id == user_id).first()
    farmer_profile = farmer_user.farmer_profile if farmer_user else None

    # Routing is identity data.  A farmer cannot direct their ticket into a
    # different officer's jurisdiction through request fields.
    state = farmer_profile.state if farmer_profile else "Telangana"
    district = farmer_profile.district if farmer_profile else "Warangal"
    mandal = farmer_profile.mandal if farmer_profile else "Geesugonda"
    village = farmer_profile.village if farmer_profile else "Dharmaram"

    ticket_num = generate_ticket_number()

    crop_obj = None
    if payload.crop_name:
        crop_obj = db.query(Crop).filter(
            (func.lower(Crop.name_en) == payload.crop_name.lower()) |
            (func.lower(Crop.name_te) == payload.crop_name.lower()) |
            (func.lower(Crop.name_hi) == payload.crop_name.lower())
        ).first()

    ticket = AgriculturalTicket(
        ticket_number=ticket_num,
        farmer_id=user_id,
        crop_id=crop_obj.id if crop_obj else None,
        conversation_id=payload.conversation_id,
        state=state,
        district=district,
        mandal=mandal,
        village=village,
        status="PENDING",
        priority=(payload.priority or "high").lower() if (payload.priority or "high").lower() in {"low", "medium", "high", "emergency"} else "high",
        escalation_reason=payload.escalation_reason,
        original_question_transcript=payload.original_question_transcript,
        audio_recording_url=payload.audio_recording_url,
        attached_image_urls=payload.attached_image_urls or []
    )
    db.add(ticket)
    db.flush()

    # Seed initial message in TicketMessage thread
    first_img = payload.attached_image_urls[0] if (payload.attached_image_urls and len(payload.attached_image_urls) > 0) else None
    init_msg = TicketMessage(
        ticket_id=ticket.id,
        sender_id=user_id,
        msg_type="image" if (first_img and not payload.original_question_transcript) else "text",
        content=payload.original_question_transcript or "Escalated agricultural inquiry",
        media_url=first_img
    )
    db.add(init_msg)

    if payload.conversation_id:
        conv = db.query(Conversation).filter(Conversation.id == payload.conversation_id).first()
        if conv:
            conv.escalation_status = "ESCALATED"
            conv.updated_at = datetime.now(timezone.utc)

    db.commit()
    db.refresh(ticket)

    return {
        "status": "created",
        "id": ticket.id,
        "ticket_id": ticket.id,
        "ticket_number": ticket.ticket_number,
        "mandal": ticket.mandal,
        "priority": ticket.priority,
        "message": f"Question officially escalated to Regional Extension Officer as {ticket_num}."
    }

@router.post("/offline-sync")
async def sync_offline_tickets(
    payload: OfflineSyncRequest,
    current_user: Dict[str, Any] = Depends(get_current_user_payload),
    db: Session = Depends(get_db)
):
    user_id = current_user.get("sub")
    farmer_user = db.query(User).filter(User.id == user_id).first()
    farmer_profile = farmer_user.farmer_profile if farmer_user else None

    state = farmer_profile.state if farmer_profile else "Telangana"
    district = farmer_profile.district if farmer_profile else "Warangal"
    mandal = farmer_profile.mandal if farmer_profile else "Geesugonda"
    village = farmer_profile.village if farmer_profile else "Dharmaram"

    synced = []
    synced_client_uuids = []
    for item in payload.pending_tickets:
        crop_name = item.crop_name or item.crop or "General"
        # Check idempotency using client_uuid in escalation_reason
        existing = db.query(AgriculturalTicket).filter(
            AgriculturalTicket.farmer_id == user_id,
            AgriculturalTicket.escalation_reason.contains(item.client_uuid)
        ).first()

        if existing:
            synced.append({
                "client_uuid": item.client_uuid,
                "ticket_id": existing.id,
                "ticket_number": existing.ticket_number
            })
            synced_client_uuids.append(item.client_uuid)
            continue

        ticket_num = generate_ticket_number()

        crop_obj = None
        if crop_name and crop_name != "General":
            crop_obj = db.query(Crop).filter(
                (func.lower(Crop.name_en) == crop_name.lower()) |
                (func.lower(Crop.name_te) == crop_name.lower()) |
                (func.lower(Crop.name_hi) == crop_name.lower())
            ).first()

        conv_id = getattr(item, "conversation_id", None)
        if not conv_id:
            latest_conv = db.query(Conversation).filter(
                Conversation.user_id == user_id
            ).order_by(Conversation.created_at.desc()).first()
            if latest_conv:
                conv_id = latest_conv.id

        ticket = AgriculturalTicket(
            ticket_number=ticket_num,
            farmer_id=user_id,
            crop_id=crop_obj.id if crop_obj else None,
            conversation_id=conv_id,
            state=state,
            district=district,
            mandal=mandal,
            village=village,
            status="PENDING",
            priority="high",
            escalation_reason=f"Offline Field Escalation (UUID:{item.client_uuid}) [Crop:{crop_name}] [Lang:{item.language or 'te'}]",
            original_question_transcript=item.question_text,
            attached_image_urls=[item.image_url] if item.image_url else []
        )
        db.add(ticket)
        db.flush()

        # Seed initial TicketMessage so officer dashboard chat thread has the inquiry
        first_img = item.image_url
        init_msg = TicketMessage(
            ticket_id=ticket.id,
            sender_id=user_id,
            msg_type="image" if (first_img and not item.question_text) else "text",
            content=item.question_text or "Offline escalated inquiry",
            media_url=first_img
        )
        db.add(init_msg)

        if conv_id:
            conv = db.query(Conversation).filter(Conversation.id == conv_id).first()
            if conv:
                conv.escalation_status = "ESCALATED"
                conv.updated_at = datetime.now(timezone.utc)

        db.commit()
        db.refresh(ticket)
        synced.append({
            "client_uuid": item.client_uuid,
            "ticket_id": ticket.id,
            "ticket_number": ticket_num
        })
        synced_client_uuids.append(item.client_uuid)

    return {
        "status": "synced",
        "synced_count": len(synced),
        "synced_client_uuids": synced_client_uuids,
        "tickets": synced
    }

@router.get("/{ticket_id}")
async def get_ticket_detail(
    ticket_id: str,
    current_user: Dict[str, Any] = Depends(get_current_user_payload),
    db: Session = Depends(get_db)
):
    ticket = db.query(AgriculturalTicket).filter(
        (AgriculturalTicket.id == ticket_id) | (AgriculturalTicket.ticket_number == ticket_id)
    ).first()
    if not ticket:
        raise HTTPException(status_code=404, detail="Ticket not found")

    role = current_user.get("role")
    user_id = current_user.get("sub")

    # Scope authorization
    if role == "farmer":
        if ticket.farmer_id != user_id:
            raise HTTPException(
                status_code=status.HTTP_403_FORBIDDEN,
                detail="Access denied: Farmers can only view their own tickets."
            )
    elif role == "extension_officer":
        assigned_mandals = current_user.get("mandals") or []
        assigned_district = current_user.get("district")
        assigned_state = current_user.get("state")
        if not assigned_mandals:
            raise HTTPException(
                status_code=status.HTTP_403_FORBIDDEN,
                detail="Access denied: Extension officers can only view tickets within their assigned mandals. Extension officer has no assigned jurisdiction."
            )
        mandal_ok = (ticket.mandal in assigned_mandals)
        district_ok = (not assigned_district) or (ticket.district == assigned_district)
        state_ok = (not assigned_state) or (ticket.state == assigned_state)
        assigned_to_me = (ticket.assigned_officer_id == user_id)
        if not (assigned_to_me or (mandal_ok and district_ok and state_ok)):
            raise HTTPException(
                status_code=status.HTTP_403_FORBIDDEN,
                detail="Access denied: Extension officers can only view tickets within their assigned mandals and jurisdiction."
            )
    elif role != "admin":
        raise HTTPException(
            status_code=status.HTTP_403_FORBIDDEN,
            detail="Access denied: Unauthorized role."
        )

    farmer_user = db.query(User).filter(User.id == ticket.farmer_id).first()
    farmer_profile = farmer_user.farmer_profile if farmer_user else None

    crop_name = None
    if ticket.crop_id:
        c_obj = db.query(Crop).filter(Crop.id == ticket.crop_id).first()
        if c_obj:
            crop_name = c_obj.name_en
    if not crop_name:
        reason_low = (ticket.escalation_reason or "").lower()
        q_low = (ticket.original_question_transcript or "").lower()
        if "cotton" in reason_low or "ప్రత్తి" in q_low or "పత్తి" in q_low or "కపాస్" in q_low:
            crop_name = "Cotton"
        elif "chilli" in reason_low or "మిరప" in q_low or "మిర్చి" in q_low:
            crop_name = "Chilli"
        elif "rice" in reason_low or "paddy" in reason_low or "వరి" in q_low:
            crop_name = "Rice"
        elif "tomato" in reason_low or "టమోటా" in q_low:
            crop_name = "Tomato"
        elif "maize" in reason_low or "మొక్కజొన్న" in q_low:
            crop_name = "Maize"
        elif ticket.conversation and ticket.conversation.crop:
            crop_name = ticket.conversation.crop
        else:
            crop_name = "Cotton"

    first_img = ticket.attached_image_urls[0] if (ticket.attached_image_urls and len(ticket.attached_image_urls) > 0) else None

    return {
        "id": ticket.id,
        "ticket_number": ticket.ticket_number,
        "state": ticket.state,
        "district": ticket.district,
        "mandal": ticket.mandal,
        "village": ticket.village or (farmer_profile.village if farmer_profile else "Dharmaram"),
        "crop_name": crop_name,
        "farmer": {
            "id": farmer_user.id if farmer_user else None,
            "name": farmer_user.full_name if farmer_user else "Farmer",
            "phone": farmer_user.phone_number if farmer_user else "",
            "village": ticket.village or (farmer_profile.village if farmer_profile else "Dharmaram"),
            "mandal": ticket.mandal,
            "district": ticket.district,
            "state": ticket.state,
            "land_size_acres": float(farmer_profile.land_size_acres or 0) if farmer_profile else 3.5,
            "soil_type": farmer_profile.soil_type if farmer_profile else "Black Cotton",
            "primary_crop": crop_name
        },
        "status": ticket.status,
        "priority": ticket.priority,
        "escalation_reason": ticket.escalation_reason,
        "original_question_transcript": ticket.original_question_transcript,
        "audio_recording_url": ticket.audio_recording_url,
        "attached_image_urls": ticket.attached_image_urls or [],
        "photo_url": first_img,
        "has_photo": bool(first_img),
        "has_audio": bool(ticket.audio_recording_url),
        "resolution_prescription": ticket.resolution_prescription,
        "officer_response": ticket.officer_response,
        "created_at": ticket.created_at.isoformat() if ticket.created_at else None,
        "resolved_at": ticket.resolved_at.isoformat() if ticket.resolved_at else None
    }

@router.post("/{ticket_id}/respond")
@router.post("/{ticket_id}/resolve")
async def respond_to_ticket(
    ticket_id: str,
    payload: TicketRespond,
    current_user: Dict[str, Any] = Depends(get_current_user_payload),
    db: Session = Depends(get_db)
):
    role = current_user.get("role")
    user_id = current_user.get("sub")
    if role not in ["extension_officer", "admin"]:
        raise HTTPException(status_code=403, detail="Only verified Agricultural Extension Officers can respond to tickets.")

    ticket = db.query(AgriculturalTicket).filter(
        (AgriculturalTicket.id == ticket_id) | (AgriculturalTicket.ticket_number == ticket_id)
    ).first()
    if not ticket:
        raise HTTPException(status_code=404, detail="Ticket not found")

    if role == "extension_officer":
        assigned_mandals = current_user.get("mandals") or []
        assigned_district = current_user.get("district")
        assigned_state = current_user.get("state")
        if not assigned_mandals:
            raise HTTPException(
                status_code=status.HTTP_403_FORBIDDEN,
                detail="Access denied: Extension officers can only respond to tickets within their assigned mandals. Extension officer has no assigned jurisdiction."
            )
        mandal_ok = (ticket.mandal in assigned_mandals)
        district_ok = (not assigned_district) or (ticket.district == assigned_district)
        state_ok = (not assigned_state) or (ticket.state == assigned_state)
        assigned_to_me = (ticket.assigned_officer_id == user_id)
        if not (assigned_to_me or (mandal_ok and district_ok and state_ok)):
            raise HTTPException(
                status_code=status.HTTP_403_FORBIDDEN,
                detail="Access denied: Extension officers can only respond to tickets within their assigned mandals and jurisdiction."
            )

    new_status = payload.normalized_status()
    ticket.status = new_status.lower()
    ticket.resolution_prescription = payload.prescription
    ticket.officer_response = payload.prescription
    ticket.resolved_at = datetime.now(timezone.utc) if new_status in {"RESOLVED", "CLOSED"} else None
    ticket.assigned_officer_id = current_user.get("sub")

    # Save to TicketMessage
    msg = TicketMessage(
        ticket_id=ticket.id,
        sender_id=current_user.get("sub"),
        msg_type="prescription",
        content=payload.prescription
    )
    db.add(msg)

    # If linked to a conversation, also post an OFFICER message to conversation_messages
    if ticket.conversation_id:
        conv = db.query(Conversation).filter(Conversation.id == ticket.conversation_id).first()
        if conv:
            prefix = (
                "👨‍🌾 [అధికారి సిఫార్సు]" if conv.language == "te"
                else ("👨‍🌾 [कृषि अधिकारी सलाह]" if conv.language == "hi"
                else "👨‍🌾 [Officer Prescription]")
            )
            conv_msg = ConversationMessage(
                conversation_id=conv.id,
                sender_type="OFFICER",
                original_query=None,
                transcript=None,
                response=f"{prefix}: {payload.prescription}",
                source_citations=["Regional Mandal Agricultural Officer (MAO)"],
                language=conv.language,
                is_safe=True,
                is_escalated=False,
                ticket_id=ticket.id
            )
            db.add(conv_msg)
            conv.escalation_status = "RESOLVED" if new_status in {"RESOLVED", "CLOSED"} else "ESCALATED"
            conv.message_count += 1
            conv.updated_at = datetime.now(timezone.utc)

    db.commit()

    return {
        "status": new_status.lower(),
        "ticket_id": ticket.id,
        "ticket_number": ticket.ticket_number,
        "resolved_at": ticket.resolved_at.isoformat() if ticket.resolved_at else None,
        "message": f"Official response recorded. Status updated to {new_status}."
    }

def verify_ticket_access(ticket: AgriculturalTicket, current_user: Dict[str, Any], db: Session) -> str:
    """
    Verifies that the current user has permission to access the ticket.
    Returns the resolved role ('farmer', 'extension_officer', 'admin').
    Raises HTTPException 403 if unauthorized.
    """
    role = current_user.get("role")
    user_id = current_user.get("sub")

    if role == "farmer":
        if ticket.farmer_id != user_id:
            raise HTTPException(
                status_code=status.HTTP_403_FORBIDDEN,
                detail="Access denied: Farmers can only access their own tickets."
            )
        return "farmer"

    elif role == "extension_officer":
        assigned_mandals = current_user.get("mandals") or []
        assigned_district = current_user.get("district")
        assigned_state = current_user.get("state")

        if not assigned_mandals:
            officer = db.query(ExtensionOfficer).filter(ExtensionOfficer.user_id == user_id).first()
            if officer:
                assigned_mandals = officer.assigned_mandals or []
                assigned_district = assigned_district or officer.assigned_district
                assigned_state = assigned_state or officer.assigned_state

        if not assigned_mandals:
            raise HTTPException(
                status_code=status.HTTP_403_FORBIDDEN,
                detail="Access denied: Extension officer has no assigned jurisdiction."
            )

        mandal_ok = (ticket.mandal in assigned_mandals)
        district_ok = (not assigned_district) or (ticket.district == assigned_district)
        state_ok = (not assigned_state) or (ticket.state == assigned_state)
        assigned_to_me = (ticket.assigned_officer_id == user_id)

        if not (assigned_to_me or (mandal_ok and district_ok and state_ok)):
            raise HTTPException(
                status_code=status.HTTP_403_FORBIDDEN,
                detail="Access denied: Extension officers can only access tickets within their assigned jurisdiction."
            )
        return "extension_officer"

    elif role == "admin":
        return "admin"

    else:
        raise HTTPException(
            status_code=status.HTTP_403_FORBIDDEN,
            detail="Access denied: Unauthorized role."
        )

@router.get("/{ticket_id}/messages")
async def get_ticket_messages(
    ticket_id: str,
    current_user: Dict[str, Any] = Depends(get_current_user_payload),
    db: Session = Depends(get_db)
):
    ticket = db.query(AgriculturalTicket).filter(
        (AgriculturalTicket.id == ticket_id) | (AgriculturalTicket.ticket_number == ticket_id)
    ).first()
    if not ticket:
        raise HTTPException(status_code=404, detail="Ticket not found")

    verify_ticket_access(ticket, current_user, db)

    farmer_user = db.query(User).filter(User.id == ticket.farmer_id).first()
    farmer_name = farmer_user.full_name if farmer_user else "Farmer"

    ticket_messages = db.query(TicketMessage).filter(
        TicketMessage.ticket_id == ticket.id
    ).order_by(TicketMessage.created_at.asc()).all()

    sender_ids = {tm.sender_id for tm in ticket_messages}
    if ticket.assigned_officer_id:
        sender_ids.add(ticket.assigned_officer_id)
    sender_ids.add(ticket.farmer_id)
    users_by_id = {u.id: u for u in db.query(User).filter(User.id.in_(sender_ids)).all()} if sender_ids else {}

    results = []

    # Include root farmer message if no ticket messages exist yet, or as the initial ticket query
    has_root_message = any(
        tm.content == ticket.original_question_transcript
        for tm in ticket_messages
    )
    if not has_root_message and ticket.original_question_transcript:
        first_img = ticket.attached_image_urls[0] if (ticket.attached_image_urls and len(ticket.attached_image_urls) > 0) else None
        root_msg = {
            "id": f"root-{ticket.id}",
            "ticket_id": ticket.id,
            "sender_id": ticket.farmer_id,
            "sender_type": "FARMER",
            "sender_name": farmer_name,
            "msg_type": "image" if (first_img and not ticket.original_question_transcript) else "text",
            "content": ticket.original_question_transcript,
            "media_url": first_img,
            "created_at": ticket.created_at.isoformat() if ticket.created_at else None
        }
        results.append(root_msg)

    for tm in ticket_messages:
        sender = users_by_id.get(tm.sender_id)
        if sender:
            s_type = "OFFICER" if sender.role in ["extension_officer", "admin"] else "FARMER"
            s_name = sender.full_name
        else:
            s_type = "FARMER" if tm.sender_id == ticket.farmer_id else "OFFICER"
            s_name = farmer_name if s_type == "FARMER" else "Extension Officer"

        results.append({
            "id": tm.id,
            "ticket_id": tm.ticket_id,
            "sender_id": tm.sender_id,
            "sender_type": s_type,
            "sender_name": s_name,
            "msg_type": tm.msg_type or "text",
            "content": tm.content,
            "media_url": tm.media_url,
            "created_at": tm.created_at.isoformat() if tm.created_at else None
        })

    # If linked to conversation, include any additional conversation messages linked to this ticket/thread
    if ticket.conversation_id:
        conv_messages = db.query(ConversationMessage).filter(
            ConversationMessage.conversation_id == ticket.conversation_id
        ).order_by(ConversationMessage.timestamp.asc()).all()

        existing_contents = {r["content"] for r in results}
        for cm in conv_messages:
            content = cm.response or cm.transcript or cm.original_query
            if not content or content in existing_contents:
                continue
            cm_sender_type = cm.sender_type.upper() if cm.sender_type else "FARMER"
            if cm_sender_type == "AI":
                continue # Skip pure AI auto-responses prior to escalation
            cm_sender_name = farmer_name if cm_sender_type == "FARMER" else "Extension Officer"
            results.append({
                "id": cm.id,
                "ticket_id": ticket.id,
                "sender_id": ticket.farmer_id if cm_sender_type == "FARMER" else (ticket.assigned_officer_id or "officer"),
                "sender_type": cm_sender_type,
                "sender_name": cm_sender_name,
                "msg_type": "text",
                "content": content,
                "media_url": None,
                "created_at": cm.timestamp.isoformat() if cm.timestamp else None
            })
            existing_contents.add(content)

    # Sort strictly chronologically by created_at ascending
    results.sort(key=lambda m: m.get("created_at") or "")

    return results

@router.post("/{ticket_id}/messages")
async def post_ticket_message(
    ticket_id: str,
    payload: TicketMessageCreate,
    current_user: Dict[str, Any] = Depends(get_current_user_payload),
    db: Session = Depends(get_db)
):
    ticket = db.query(AgriculturalTicket).filter(
        (AgriculturalTicket.id == ticket_id) | (AgriculturalTicket.ticket_number == ticket_id)
    ).first()
    if not ticket:
        raise HTTPException(status_code=404, detail="Ticket not found")

    user_role = verify_ticket_access(ticket, current_user, db)
    user_id = current_user.get("sub")

    user_obj = db.query(User).filter(User.id == user_id).first()
    user_full_name = user_obj.full_name if user_obj else current_user.get("name")

    if user_role == "farmer":
        sender_type = "FARMER"
        sender_name = user_full_name or "Farmer"
    else:
        sender_type = "OFFICER"
        sender_name = user_full_name or "Extension Officer"
        if not ticket.assigned_officer_id:
            ticket.assigned_officer_id = user_id

    # Normalize message type
    msg_type = (payload.msg_type or "text").lower()
    if payload.media_url and msg_type == "text":
        msg_type = "image"

    msg = TicketMessage(
        ticket_id=ticket.id,
        sender_id=user_id,
        msg_type=msg_type,
        content=payload.content,
        media_url=payload.media_url
    )
    db.add(msg)

    # If ticket is linked to conversation, sync ConversationMessage
    if ticket.conversation_id:
        conv = db.query(Conversation).filter(Conversation.id == ticket.conversation_id).first()
        if conv:
            prefix = ""
            if sender_type == "OFFICER":
                prefix = (
                    "👨‍🌾 [అధికారి]: " if conv.language == "te"
                    else ("👨‍🌾 [कृषि अधिकारी]: " if conv.language == "hi"
                    else "👨‍🌾 [Officer]: ")
                )
            conv_msg = ConversationMessage(
                conversation_id=conv.id,
                sender_type=("OFFICER" if user_role in ["extension_officer", "admin"] else "FARMER"),
                original_query=payload.content if sender_type == "FARMER" else None,
                transcript=payload.content if sender_type == "FARMER" else None,
                response=f"{prefix}{payload.content}" if sender_type == "OFFICER" else payload.content,
                source_citations=["Extension Officer"] if sender_type == "OFFICER" else [],
                language=conv.language,
                is_safe=True,
                is_escalated=(ticket.status != "RESOLVED"),
                ticket_id=ticket.id
            )
            db.add(conv_msg)
            conv.message_count += 1
            conv.updated_at = datetime.now(timezone.utc)

    # Update ticket status based on sender
    if sender_type == "OFFICER":
        ticket.status = "RESPONDED"
        if msg_type == "prescription":
            ticket.resolution_prescription = payload.content
        ticket.officer_response = payload.content
    elif sender_type == "FARMER":
        ticket.status = "IN_REVIEW"

    ticket.updated_at = datetime.now(timezone.utc)
    db.commit()
    db.refresh(msg)

    return {
        "id": msg.id,
        "ticket_id": msg.ticket_id,
        "sender_id": msg.sender_id,
        "sender_type": sender_type,
        "sender_name": sender_name,
        "msg_type": msg.msg_type,
        "content": msg.content,
        "media_url": msg.media_url,
        "created_at": msg.created_at.isoformat() if msg.created_at else datetime.now(timezone.utc).isoformat()
    }

@router.post("/{ticket_id}/status")
async def update_ticket_status(
    ticket_id: str,
    payload: TicketStatusUpdate,
    current_user: Dict[str, Any] = Depends(get_current_user_payload),
    db: Session = Depends(get_db)
):
    ticket = db.query(AgriculturalTicket).filter(
        (AgriculturalTicket.id == ticket_id) | (AgriculturalTicket.ticket_number == ticket_id)
    ).first()
    if not ticket:
        raise HTTPException(status_code=404, detail="Ticket not found")

    user_role = verify_ticket_access(ticket, current_user, db)
    norm_status = payload.status.strip().upper()
    allowed_statuses = {"OPEN", "PENDING", "IN_REVIEW", "RESPONDED", "WAITING_FOR_OFFICER", "WAITING_FOR_FARMER", "RESOLVED", "REOPENED", "CLOSED"}

    if norm_status not in allowed_statuses:
        raise HTTPException(
            status_code=422,
            detail=f"Invalid status '{payload.status}'. Allowed statuses: {sorted(list(allowed_statuses))}"
        )

    if user_role == "farmer":
        if norm_status not in {"CLOSED", "RESOLVED", "REOPENED", "IN_REVIEW"}:
            raise HTTPException(
                status_code=status.HTTP_403_FORBIDDEN,
                detail=f"Farmers are not permitted to set ticket status to '{norm_status}'."
            )

    ticket.status = norm_status
    if norm_status in {"RESOLVED", "CLOSED"}:
        ticket.resolved_at = datetime.now(timezone.utc)
    else:
        ticket.resolved_at = None

    ticket.updated_at = datetime.now(timezone.utc)

    if ticket.conversation_id:
        conv = db.query(Conversation).filter(Conversation.id == ticket.conversation_id).first()
        if conv:
            conv.escalation_status = "RESOLVED" if norm_status in {"RESOLVED", "CLOSED"} else "ESCALATED"
            conv.updated_at = datetime.now(timezone.utc)

    db.commit()
    db.refresh(ticket)

    return {
        "ticket_id": ticket.id,
        "ticket_number": ticket.ticket_number,
        "status": ticket.status,
        "resolved_at": ticket.resolved_at.isoformat() if ticket.resolved_at else None,
        "message": f"Ticket status successfully updated to {ticket.status}."
    }
