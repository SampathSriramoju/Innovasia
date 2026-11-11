import pytest
from fastapi.testclient import TestClient
from app.main import app
from app.services.ivr_service import ivr_service, format_ivr_spoken_text

client = TestClient(app)


@pytest.fixture(autouse=True)
def reset_ivr_state():
    """Ensure clean IVR state before and after each test."""
    ivr_service.reset()
    yield
    ivr_service.reset()


def test_format_ivr_spoken_text():
    """Verifies that markdown, asterisks, brackets, and citations are stripped for TTS."""
    raw = "**Warning**: Apply [KB01] Chlorantraniliprole 18.5% SC @ 60ml/acre (KB-DOC-02).\n\n- Wear gloves."
    clean = format_ivr_spoken_text(raw)
    assert "**" not in clean
    assert "[KB01]" not in clean
    assert "(KB-DOC-02)" not in clean
    assert "Chlorantraniliprole" in clean
    assert "60ml/acre" in clean


def test_ivr_call_start_and_initial_menu():
    """Verifies starting an IVR call session returns multilingual menu and simulation tags."""
    resp = client.post("/api/v1/ivr/call/start", json={
        "caller_number": "+919876543210",
        "metadata": {"network": "Airtel 4G", "region": "Warangal Rural"}
    })
    assert resp.status_code == 200
    data = resp.json()

    assert data["is_demo"] is True
    assert data["is_simulated"] is True
    assert data["provider"] == "BOL_KISAN_MOCK_IVR"
    assert "SIMULATED IVR" in data["simulation_label"]

    call_id = data["call_id"]
    assert call_id.startswith("IVR-")
    session = data["session"]
    assert session["status"] == "active"
    assert session["caller_number"] == "+919876543210"

    prompt = data["prompt"]
    assert prompt["prompt_type"] == "initial_language_menu"
    assert "తెలుగు కొరకు 1" in prompt["text"]
    assert "हिंदी के लिए 2" in prompt["text"]
    assert "press 3" in prompt["text"].lower()
    assert "/api/v1/voice/tts-stream" in prompt["audio_url"]


def test_ivr_dtmf_language_selection():
    """Verifies selecting language via DTMF 1 (Telugu), 2 (Hindi), 3 (English)."""
    start_resp = client.post("/api/v1/ivr/call/start", json={"caller_number": "9876543210"})
    call_id = start_resp.json()["call_id"]

    # Select Telugu (1)
    dtmf_1 = client.post("/api/v1/ivr/call/dtmf", json={"call_id": call_id, "digit": "1"})
    assert dtmf_1.status_code == 200
    d1 = dtmf_1.json()
    assert d1["action"] == "language_selected_te"
    assert d1["session"]["language"] == "te"
    assert "తెలుగు" in d1["prompt"]["text"]

    # Switch to Hindi (2)
    dtmf_2 = client.post("/api/v1/ivr/call/dtmf", json={"call_id": call_id, "digit": "2"})
    assert dtmf_2.status_code == 200
    d2 = dtmf_2.json()
    assert d2["action"] == "language_selected_hi"
    assert d2["session"]["language"] == "hi"
    assert "हिंदी" in d2["prompt"]["text"]

    # Switch to English (3)
    dtmf_3 = client.post("/api/v1/ivr/call/dtmf", json={"call_id": call_id, "digit": "3"})
    assert dtmf_3.status_code == 200
    d3 = dtmf_3.json()
    assert d3["action"] == "language_selected_en"
    assert d3["session"]["language"] == "en"
    assert "English" in d3["prompt"]["text"]


def test_ivr_dtmf_escalate_to_mao():
    """Verifies pressing 9 immediately escalates the call to MAO and generates ESC- ticket."""
    start_resp = client.post("/api/v1/ivr/call/start", json={"caller_number": "+919876543210"})
    call_id = start_resp.json()["call_id"]

    resp = client.post("/api/v1/ivr/call/dtmf", json={"call_id": call_id, "digit": "9"})
    assert resp.status_code == 200
    data = resp.json()

    assert data["action"] == "escalate_to_mao"
    assert data["status"] == "escalated"
    assert data["ticket_number"].startswith("ESC-")
    assert data["ticket_number"] in data["prompt"]["text"]
    assert data["is_simulated"] is True


def test_ivr_dtmf_end_call():
    """Verifies pressing 0 or * gracefully ends the call and computes duration."""
    start_resp = client.post("/api/v1/ivr/call/start", json={"caller_number": "+919876543210"})
    call_id = start_resp.json()["call_id"]

    resp = client.post("/api/v1/ivr/call/dtmf", json={"call_id": call_id, "digit": "0"})
    assert resp.status_code == 200
    data = resp.json()

    assert data["action"] == "end_call"
    assert data["status"] == "ended"
    assert data["session"]["ended_at"] is not None
    assert data["session"]["duration_seconds"] >= 0


def test_ivr_dtmf_unrecognized_key():
    """Verifies pressing an unmapped key plays prompt guiding user to press 1, 2, 3, 9, or 0."""
    start_resp = client.post("/api/v1/ivr/call/start", json={"caller_number": "+919876543210"})
    call_id = start_resp.json()["call_id"]

    resp = client.post("/api/v1/ivr/call/dtmf", json={"call_id": call_id, "digit": "7"})
    assert resp.status_code == 200
    data = resp.json()

    assert data["action"] == "unrecognized_dtmf"
    assert data["status"] == "active"


def test_ivr_voice_input_safe_recommendation():
    """Verifies safe farmer query yields grounded recommendation with TTS stream URL."""
    start_resp = client.post("/api/v1/ivr/call/start", json={"caller_number": "+919876543210"})
    call_id = start_resp.json()["call_id"]

    # Select Telugu
    client.post("/api/v1/ivr/call/dtmf", json={"call_id": call_id, "digit": "1"})

    # Speak question about Cotton pink bollworm
    voice_resp = client.post("/api/v1/ivr/call/voice", json={
        "call_id": call_id,
        "speech_transcript": "నా పత్తి పంటలో గులాబీ రంగు పురుగు నివారణ ఏమిటి?"
    })
    assert voice_resp.status_code == 200
    data = voice_resp.json()

    assert data["is_safe"] is True
    assert data["is_escalated"] is False
    assert data["crop_identified"] == "Cotton"
    assert data["target_entity_identified"].lower() == "pink bollworm"
    assert len(data["response_text"]) > 10
    assert "/api/v1/voice/tts-stream" in data["audio_playback_url"]
    assert data["is_simulated"] is True


def test_ivr_voice_input_unsafe_chemical_escalation():
    """
    CRITICAL STATUTORY SAFETY TEST:
    Verifies that asking for an unverified/hazardous chemical triggers instant CIBRC safety escalation
    with a ticket starting with ESC- and localized safety warning.
    """
    start_resp = client.post("/api/v1/ivr/call/start", json={"caller_number": "+919876543210"})
    call_id = start_resp.json()["call_id"]

    # Speak question with unapproved/hazardous chemical dosage
    voice_resp = client.post("/api/v1/ivr/call/voice", json={
        "call_id": call_id,
        "speech_transcript": "Can I spray 500ml chlorpyrifos on paddy at flowering stage?"
    })
    assert voice_resp.status_code == 200
    data = voice_resp.json()

    assert data["is_safe"] is False
    assert data["is_escalated"] is True
    assert data["ticket_number"] is not None
    assert data["ticket_number"].startswith("ESC-")
    assert "ESC-" in data["response_text"] or "CIBRC" in data["response_text"] or "భద్రతా" in data["response_text"] or "Safety" in data["response_text"]


def test_ivr_conversational_continuity():
    """
    Verifies conversational continuity across turns:
    Turn 1 sets crop = Cotton.
    Turn 2 follow-up asks 'what is the dosage for pink bollworm?' and inherits Cotton.
    """
    start_resp = client.post("/api/v1/ivr/call/start", json={"caller_number": "+919876543210"})
    call_id = start_resp.json()["call_id"]

    # Turn 1
    t1 = client.post("/api/v1/ivr/call/voice", json={
        "call_id": call_id,
        "speech_transcript": "I am growing cotton in Warangal district."
    })
    assert t1.status_code == 200
    assert t1.json()["crop_identified"] == "Cotton"

    # Turn 2
    t2 = client.post("/api/v1/ivr/call/voice", json={
        "call_id": call_id,
        "speech_transcript": "What is the recommended spray for pink bollworm?"
    })
    assert t2.status_code == 200
    d2 = t2.json()
    assert d2["crop_identified"] == "Cotton"
    assert d2["target_entity_identified"].lower() == "pink bollworm"


def test_ivr_simulate_errors():
    """Verifies all required simulated error types: silence, asr failure, backend failure, gemini failure."""
    start_resp = client.post("/api/v1/ivr/call/start", json={"caller_number": "+919876543210"})
    call_id = start_resp.json()["call_id"]

    # 1. Silence timeout
    r1 = client.post("/api/v1/ivr/call/simulate-error", json={
        "call_id": call_id,
        "error_type": "silence_timeout"
    })
    assert r1.status_code == 200
    assert r1.json()["error_type"] == "silence_timeout"
    assert len(r1.json()["recovery_prompt"]) > 5

    # 2. Speech recognition failure
    r2 = client.post("/api/v1/ivr/call/simulate-error", json={
        "call_id": call_id,
        "error_type": "speech_recognition_failure"
    })
    assert r2.status_code == 200
    assert r2.json()["error_type"] == "speech_recognition_failure"

    # 3. Gemini failure (deterministic POP fallback)
    r3 = client.post("/api/v1/ivr/call/simulate-error", json={
        "call_id": call_id,
        "error_type": "gemini_failure"
    })
    assert r3.status_code == 200
    assert r3.json()["error_type"] == "gemini_failure"
    assert len(r3.json()["recovery_prompt"]) > 10

    # 4. Backend failure (auto-escalates to MAO with ESC- ticket!)
    r4 = client.post("/api/v1/ivr/call/simulate-error", json={
        "call_id": call_id,
        "error_type": "backend_failure"
    })
    assert r4.status_code == 200
    d4 = r4.json()
    assert d4["error_type"] == "backend_failure"
    assert d4["status"] == "escalated"
    assert d4["ticket_number"] is not None
    assert d4["ticket_number"].startswith("ESC-")


def test_ivr_end_call_and_session_lookup():
    """Verifies ending call, calculating duration, and retrieving session via GET /call/{call_id}."""
    start_resp = client.post("/api/v1/ivr/call/start", json={"caller_number": "+919876543210"})
    call_id = start_resp.json()["call_id"]

    end_resp = client.post("/api/v1/ivr/call/end", json={
        "call_id": call_id,
        "reason": "farmer_satisfied"
    })
    assert end_resp.status_code == 200
    assert end_resp.json()["status"] == "ended"

    get_resp = client.get(f"/api/v1/ivr/call/{call_id}")
    assert get_resp.status_code == 200
    sess = get_resp.json()
    assert sess["call_id"] == call_id
    assert sess["status"] == "ended"
    assert sess["is_demo"] is True


def test_ivr_status_and_not_found():
    """Verifies /api/v1/ivr/status telemetry and 404 for invalid call IDs."""
    status_resp = client.get("/api/v1/ivr/status")
    assert status_resp.status_code == 200
    sdata = status_resp.json()
    assert sdata["status"] == "operational"
    assert "stats" in sdata
    assert "supported_dtmf_menu" in sdata

    # Invalid call ID
    bad_resp = client.get("/api/v1/ivr/call/IVR-NONEXISTENT")
    assert bad_resp.status_code == 404
