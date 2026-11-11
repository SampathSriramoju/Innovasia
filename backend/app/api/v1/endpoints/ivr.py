from typing import Optional, Dict, Any, List
from fastapi import APIRouter, Query, Body, HTTPException, status
from pydantic import BaseModel, Field

from app.services.ivr_service import ivr_service

router = APIRouter()


class StartCallRequest(BaseModel):
    caller_number: str = Field(..., description="Caller's phone number (e.g. '+919876543210')")
    language: Optional[str] = Field(None, description="Optional pre-selected language code ('te', 'hi', 'en')")
    metadata: Optional[Dict[str, Any]] = Field(None, description="Optional call metadata (e.g. tower_id, location)")


class SendDTMFRequest(BaseModel):
    call_id: str = Field(..., description="Active IVR Call Session ID")
    digit: str = Field(..., min_length=1, max_length=2, description="DTMF key pressed ('1', '2', '3', '9', '0', '*')")


class VoiceInputRequest(BaseModel):
    call_id: str = Field(..., description="Active IVR Call Session ID")
    speech_transcript: str = Field(..., min_length=1, description="Farmer spoken audio transcript from ASR/STT")
    language: Optional[str] = Field(None, description="Optional language override ('te', 'hi', 'en')")


class SimulateErrorRequest(BaseModel):
    call_id: str = Field(..., description="Active IVR Call Session ID")
    error_type: str = Field(
        ...,
        description="Error type to simulate: 'silence_timeout', 'speech_recognition_failure', 'backend_failure', 'gemini_failure', 'unrecognized_dtmf'"
    )


class EndCallRequest(BaseModel):
    call_id: str = Field(..., description="Active IVR Call Session ID")
    reason: Optional[str] = Field("caller_hangup", description="Reason for ending the call")


@router.post("/call/start", summary="Initiate Simulated IVR Call Session")
async def start_call(payload: StartCallRequest):
    """
    Simulates an incoming or initiated phone call into the Bol Kisan IVR Fallback gateway.
    Returns the initialized call session and initial multilingual voice menu:
    - Press 1 for Telugu (తెలుగు)
    - Press 2 for Hindi (हिंदी)
    - Press 3 for English
    - Press 9 for Mandal Agricultural Officer (MAO)
    - Press 0 to end call
    """
    try:
        result = ivr_service.start_call(
            caller_number=payload.caller_number,
            language=payload.language,
            metadata=payload.metadata
        )
        return result
    except Exception as e:
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail=f"Failed to initiate IVR call: {str(e)}"
        )


@router.post("/call/dtmf", summary="Send DTMF Keypad Tone to IVR Call")
async def send_dtmf(payload: SendDTMFRequest):
    """
    Processes farmer DTMF keypad navigation:
    - '1': Select Telugu language
    - '2': Select Hindi language
    - '3': Select English language
    - '9': Automatic escalation to Mandal Agricultural Officer (MAO) with ESC- ticket
    - '0' or '*': Graceful call termination
    - Other keys: Plays localized menu reminder
    """
    try:
        result = ivr_service.send_dtmf(
            call_id=payload.call_id,
            digit=payload.digit
        )
        return result
    except KeyError as e:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail=str(e)
        )
    except Exception as e:
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail=f"Error processing DTMF input: {str(e)}"
        )


@router.post("/call/voice", summary="Process Farmer Spoken Query via IVR Speech Pipeline")
async def process_voice_query(payload: VoiceInputRequest):
    """
    Ingests farmer spoken query transcript via IVR voice channel.
    Executes full Bol Kisan zero-hallucination conversational pipeline:
    1. Query understanding & contextual continuity resolution across conversational turns.
    2. CIBRC Statutory Safety Gate verification (NEVER invent pesticide dosages, NEVER recommend unverified chemicals).
    3. RAG retrieval from ICAR/CIBRC knowledge base collections.
    4. Gemini synthesis with post-generation safety check and deterministic CIBRC/POP fallback.
    5. Returns spoken response text, TTS streaming audio URL, and simulation metadata.
    """
    try:
        result = await ivr_service.process_voice_input(
            call_id=payload.call_id,
            speech_transcript=payload.speech_transcript,
            language=payload.language
        )
        return result
    except KeyError as e:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail=str(e)
        )
    except ValueError as e:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail=str(e)
        )
    except Exception as e:
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail=f"Error processing IVR voice input: {str(e)}"
        )


@router.post("/call/simulate-error", summary="Simulate Telecom & AI Failures on Call")
async def simulate_call_error(payload: SimulateErrorRequest):
    """
    Simulates telecom dropouts, carrier timeouts, ASR failures, and AI outages:
    - 'silence_timeout': No speech detected for 5-8 seconds.
    - 'speech_recognition_failure': Garbled farmer audio / low ASR confidence.
    - 'backend_failure': Internal error -> triggers automated MAO escalation with ESC- ticket.
    - 'gemini_failure': LLM outage -> triggers verified deterministic CIBRC/POP fallback.
    - 'unrecognized_dtmf': Invalid key pressed on phone keypad.
    """
    try:
        result = ivr_service.simulate_error(
            call_id=payload.call_id,
            error_type=payload.error_type
        )
        return result
    except KeyError as e:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail=str(e)
        )
    except Exception as e:
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail=f"Error simulating IVR failure: {str(e)}"
        )


@router.post("/call/end", summary="Gracefully Terminate IVR Call Session")
async def end_call(payload: EndCallRequest):
    """
    Terminates the IVR call session, computes total duration, outputs dialogue summary,
    and returns localized goodbye prompt.
    """
    try:
        result = ivr_service.end_call(
            call_id=payload.call_id,
            reason=payload.reason
        )
        return result
    except KeyError as e:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail=str(e)
        )
    except Exception as e:
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail=f"Error terminating IVR call: {str(e)}"
        )


@router.get("/call/{call_id}", summary="Get IVR Call Session Details")
async def get_call_session(call_id: str):
    """
    Retrieves complete state, duration, DTMF history, conversation turns,
    and escalation ticket details for a given call ID.
    """
    session_data = ivr_service.get_call_session(call_id)
    if not session_data:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail=f"IVR Call session '{call_id}' not found."
        )
    return session_data


@router.get("/status", summary="IVR Gateway Health, Telemetry, and Active Calls")
async def get_ivr_status():
    """
    Returns IVR gateway operational status, call volume telemetry, active call IDs,
    supported DTMF menu mapping, and simulation metadata.
    """
    return ivr_service.get_status()


@router.get("/calls", summary="List All Active and Historical IVR Calls")
async def list_ivr_calls(active_only: bool = Query(False, description="Filter for active/escalated calls only")):
    """
    Lists calls recorded in the simulated IVR provider.
    """
    if active_only:
        calls = ivr_service.list_active_calls()
    else:
        calls = ivr_service.list_all_calls()
    return {
        "count": len(calls),
        "calls": calls,
        "is_demo": True,
        "is_simulated": True,
        "provider": ivr_service.PROVIDER_NAME,
        "simulation_label": ivr_service.SIMULATION_LABEL
    }
