import uuid
from datetime import datetime, timezone
from fastapi import APIRouter, Depends, HTTPException, status
from pydantic import BaseModel, Field
from typing import List, Optional, Dict, Any
from sqlalchemy.orm import Session
from app.database.session import get_db
from app.database.models import AgriculturalTicket, User, FarmerProfile, TicketMessage, ConversationMessage, Conversation
from app.core.security import get_current_user_payload

router = APIRouter()

def generate_ticket_number() -> str:
    hex_suffix = uuid.uuid4().hex[:12].upper()
    return f"BK-2026-{hex_suffix}"

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
        query = query.filter(AgriculturalTicket.status == status_filter)

    tickets = query.order_by(AgriculturalTicket.created_at.desc()).all()

    results = []
    for t in tickets:
        farmer_user = db.query(User).filter(User.id == t.farmer_id).first()
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
            "escalation_reason": t.escalation_reason,
            "original_question_transcript": t.original_question_transcript,
            "resolution_prescription": t.resolution_prescription,
            "officer_response": t.officer_response,
            "attached_image_urls": t.attached_image_urls or [],
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

    ticket = AgriculturalTicket(
        ticket_number=ticket_num,
        farmer_id=user_id,
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

    if payload.conversation_id:
        conv = db.query(Conversation).filter(Conversation.id == payload.conversation_id).first()
        if conv:
            conv.escalation_status = "ESCALATED"
            conv.updated_at = datetime.now(timezone.utc)

    db.commit()
    db.refresh(ticket)

    return {
        "status": "created",
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
        ticket = AgriculturalTicket(
            ticket_number=ticket_num,
            farmer_id=user_id,
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
    ticket = db.query(AgriculturalTicket).filter(AgriculturalTicket.id == ticket_id).first()
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

    return {
        "id": ticket.id,
        "ticket_number": ticket.ticket_number,
        "farmer": {
            "id": farmer_user.id if farmer_user else None,
            "name": farmer_user.full_name if farmer_user else "Farmer",
            "phone": farmer_user.phone_number if farmer_user else "",
            "village": ticket.village or (farmer_profile.village if farmer_profile else "Dharmaram"),
            "mandal": ticket.mandal,
            "district": ticket.district,
            "state": ticket.state,
            "land_size_acres": float(farmer_profile.land_size_acres or 0) if farmer_profile else 3.5,
            "soil_type": farmer_profile.soil_type if farmer_profile else "Black Cotton"
        },
        "status": ticket.status,
        "priority": ticket.priority,
        "escalation_reason": ticket.escalation_reason,
        "original_question_transcript": ticket.original_question_transcript,
        "audio_recording_url": ticket.audio_recording_url,
        "attached_image_urls": ticket.attached_image_urls or [],
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

    ticket = db.query(AgriculturalTicket).filter(AgriculturalTicket.id == ticket_id).first()
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
