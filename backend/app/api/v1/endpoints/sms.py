from typing import Optional, Dict, Any, List
from fastapi import APIRouter, Query, Body, HTTPException, status
from pydantic import BaseModel, Field

from app.services.sms_service import sms_service, get_sms_segment_details

router = APIRouter()

class SMSIncomingRequest(BaseModel):
    sender_number: str = Field(..., description="Sender phone number (e.g. '+919876543210')")
    message_text: str = Field(..., min_length=1, description="Incoming SMS message text")
    language: Optional[str] = Field(None, description="Preferred language code ('te', 'hi', 'en' or auto-detected)")
    message_id: Optional[str] = Field(None, description="Optional unique message ID for network idempotency deduplication")
    idempotency_key: Optional[str] = Field(None, description="Optional idempotency key for network retry protection")
    simulate_failure: Optional[bool] = Field(None, description="Per-request failure simulation override")

class ToggleFailureRequest(BaseModel):
    simulate_failure: Optional[bool] = Field(None, description="Explicit failure state (or null to toggle)")

@router.post("/incoming", summary="Simulate Incoming Farmer SMS Ingestion")
async def incoming_sms(payload: SMSIncomingRequest):
    """
    Ingests simulated incoming SMS from farmer handset.
    Executes statutory CIBRC safety validation, conversational continuity resolution,
    RAG retrieval, and returns an ultra-concise plain-text SMS response.
    Visibly tags every message with 'SIMULATED' and preserves safety critical instructions.
    """
    result = await sms_service.process_incoming_sms(
        sender_number=payload.sender_number,
        message_text=payload.message_text,
        language=payload.language,
        message_id=payload.message_id,
        idempotency_key=payload.idempotency_key,
        simulate_failure=payload.simulate_failure
    )

    # Attach convenience accessors for diverse frontend clients
    reply_obj = result.get("reply", {})
    inbound_obj = result.get("inbound", {})
    result["reply_text"] = reply_obj.get("message_text", "")
    result["outbound_message"] = reply_obj
    result["inbound_message"] = inbound_obj
    result["safety_alert"] = not reply_obj.get("is_safe", True)
    result["ticket_escalated"] = bool(reply_obj.get("ticket_number"))
    result["ticket_id"] = reply_obj.get("ticket_number")

    return result

@router.get("/segment-calc", summary="Calculate SMS Telecom Segments")
async def calculate_segments(text: str = Query(..., description="Message text to inspect")):
    """
    Computes exact telecom SMS segment accounting for GSM-7 and Unicode (UCS-2) per 3GPP TS 23.040.
    """
    return get_sms_segment_details(text)

@router.get("/history", summary="Fetch SMS Conversation History by Phone Number")
async def get_sms_history(phone_number: str = Query(..., description="Phone number to query")):
    """
    Retrieves chronological SMS message history for a farmer's mobile number.
    Strictly isolated to this sender's conversation history.
    """
    clean_phone = sms_service._normalize_phone(phone_number)
    history = sms_service.get_history(clean_phone)
    return {
        "phone_number": clean_phone,
        "count": len(history),
        "messages": history,
        "is_demo": True,
        "is_simulated": True
    }

@router.delete("/history", summary="Clear SMS Conversation History for Phone Number")
async def clear_sms_history(phone_number: str = Query(..., description="Phone number to clear")):
    """
    Clears SMS message session history for a specified mobile number.
    """
    clean_phone = sms_service._normalize_phone(phone_number)
    cleared = sms_service.clear_history(clean_phone)
    return {
        "phone_number": clean_phone,
        "cleared": cleared,
        "message": "SMS conversation history cleared successfully." if cleared else "No existing session found."
    }

@router.post("/toggle-failure", summary="Toggle Gateway Failure Simulation")
async def toggle_failure(payload: Optional[ToggleFailureRequest] = Body(None)):
    """
    Toggles or sets the simulated telecom carrier gateway failure state.
    Allows testing network dropouts, carrier rejects, and offline fallback behavior.
    """
    sim_val = payload.simulate_failure if payload else None
    new_state = sms_service.toggle_failure_mode(sim_val)
    return {
        "provider": sms_service.PROVIDER_NAME,
        "simulate_failure": new_state,
        "status": "simulated_carrier_failure" if new_state else "operational",
        "is_demo": True,
        "is_simulated": True,
        "message": f"Simulated carrier failure {'ENABLED (deliveries will fail)' if new_state else 'DISABLED (normal operation)'}"
    }

@router.get("/status", summary="Mock SMS Gateway Telemetry and Health Status")
async def get_gateway_status():
    """
    Returns gateway operational health, telemetry statistics, and active phone sessions.
    """
    return sms_service.get_status()
