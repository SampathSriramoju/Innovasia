import pytest
from fastapi.testclient import TestClient
from app.main import app
from app.services.ivr_service import ivr_service, format_ivr_spoken_text

client = TestClient(app)

@pytest.fixture(autouse=True)
def reset_ivr_state():
    ivr_service.reset()
    yield
    ivr_service.reset()


def test_req_1_multilingual_call_flows():
    """
    1. Multilingual Call Flows:
    - Language selection: Press 1 -> Telugu (te), Press 2 -> Hindi (hi), Press 3 -> English (en)
    - Welcome audio prompts in Telugu, Hindi, and English
    - Spoken answers in the selected language
    """
    # Start call with Telugu default
    res = client.post("/api/v1/ivr/call/start", json={"caller_number": "+919848012345"})
    assert res.status_code == 200
    call_id = res.json()["call_id"]

    # Welcome audio prompt contains instructions for 1 (Telugu), 2 (Hindi), 3 (English)
    welcome_text = res.json()["prompt"]["text"]
    assert "తెలుగు కొరకు 1" in welcome_text
    assert "हिंदी के लिए 2" in welcome_text
    assert "3" in welcome_text

    # Press 1 -> Telugu
    dtmf_1 = client.post("/api/v1/ivr/call/dtmf", json={"call_id": call_id, "digit": "1"})
    assert dtmf_1.status_code == 200
    assert dtmf_1.json()["session"]["language"] == "te"
    assert "తెలుగు" in dtmf_1.json()["prompt"]["text"]

    # Press 2 -> Hindi
    dtmf_2 = client.post("/api/v1/ivr/call/dtmf", json={"call_id": call_id, "digit": "2"})
    assert dtmf_2.status_code == 200
    assert dtmf_2.json()["session"]["language"] == "hi"
    assert "हिंदी" in dtmf_2.json()["prompt"]["text"]

    # Press 3 -> English
    dtmf_3 = client.post("/api/v1/ivr/call/dtmf", json={"call_id": call_id, "digit": "3"})
    assert dtmf_3.status_code == 200
    assert dtmf_3.json()["session"]["language"] == "en"
    assert "English" in dtmf_3.json()["prompt"]["text"]


def test_req_2_language_persistence():
    """
    2. Language Persistence:
    - Ensure the selected language persists across all turns of the call.
    """
    res = client.post("/api/v1/ivr/call/start", json={"caller_number": "+919848012345"})
    call_id = res.json()["call_id"]

    # Select Hindi via DTMF '2'
    client.post("/api/v1/ivr/call/dtmf", json={"call_id": call_id, "digit": "2"})

    # Turn 1
    t1 = client.post("/api/v1/ivr/call/voice", json={
        "call_id": call_id,
        "speech_transcript": "कपास में गुलाबी सुंडी का उपचार क्या है?"
    })
    assert t1.status_code == 200
    assert t1.json()["language"] == "hi"

    # Turn 2
    t2 = client.post("/api/v1/ivr/call/voice", json={
        "call_id": call_id,
        "speech_transcript": "इसकी खुराक क्या है?"
    })
    assert t2.status_code == 200
    assert t2.json()["language"] == "hi"

    # Verify session lookup shows persisted language
    sess = client.get(f"/api/v1/ivr/call/{call_id}").json()
    assert sess["language"] == "hi"


def test_req_3_multi_turn_context_retention():
    """
    3. Multi-Turn Context Retention:
    - Farmer: 'My cotton leaves are turning yellow.'
    - Follow-up query inheriting crop (Cotton) and pest/disease context.
    """
    res = client.post("/api/v1/ivr/call/start", json={"caller_number": "+919848012345"})
    call_id = res.json()["call_id"]

    # Turn 1: Farmer mentions crop and symptom
    t1 = client.post("/api/v1/ivr/call/voice", json={
        "call_id": call_id,
        "speech_transcript": "My cotton leaves are turning yellow."
    })
    assert t1.status_code == 200
    data1 = t1.json()
    assert data1["crop_identified"] == "Cotton"

    # Turn 2: Follow-up query without explicitly repeating 'cotton'
    t2 = client.post("/api/v1/ivr/call/voice", json={
        "call_id": call_id,
        "speech_transcript": "What should I spray for this pest attack?"
    })
    assert t2.status_code == 200
    data2 = t2.json()
    # Retains crop from turn 1
    assert data2["crop_identified"] == "Cotton"


def test_req_4_audio_input_and_spoken_output():
    """
    4. Audio Input and Spoken Output:
    - Web Speech API integration + clearly labelled '(Simulated Speech Transcript Input)'
    - Browser Web Speech Synthesis audio playback + TTS stream endpoints
    """
    # Start call and speak question
    res = client.post("/api/v1/ivr/call/start", json={"caller_number": "+919848012345"})
    call_id = res.json()["call_id"]

    v_res = client.post("/api/v1/ivr/call/voice", json={
        "call_id": call_id,
        "speech_transcript": "What is the recommended dosage of Chlorantraniliprole in rice?"
    })
    assert v_res.status_code == 200
    vdata = v_res.json()

    # Spoken output URL points to TTS stream endpoint
    assert "audio_playback_url" in vdata
    assert "/api/v1/voice/tts-stream" in vdata["audio_playback_url"]

    # Verify formatting strips markdown & citations for natural speech synthesis
    raw_markdown = "**Warning**: Apply [KB01] Chlorantraniliprole 18.5% SC @ 60ml/acre (KB-DOC-02)."
    clean = format_ivr_spoken_text(raw_markdown)
    assert "**" not in clean
    assert "[KB01]" not in clean
    assert "(KB-DOC-02)" not in clean
    assert "Chlorantraniliprole 18.5% SC @ 60ml/acre" in clean

    # Verify simulation labels are present
    assert vdata["is_simulated"] is True
    assert "SIMULATED IVR" in vdata["simulation_label"]


def test_req_5_safety_refusals_and_cibrc_enforcement():
    """
    5. Safety Refusals & CIBRC Enforcement:
    - Unapproved agrochemicals (e.g. Endosulfan, unapproved cocktails) must be rejected
    - Immediate escalation to Mandal Agricultural Officer with ticket starting with ESC-
    - Spoken safety advisory warning against crop damage
    """
    res = client.post("/api/v1/ivr/call/start", json={"caller_number": "+919848012345"})
    call_id = res.json()["call_id"]

    # Query asking for banned chemical Endosulfan
    v_res = client.post("/api/v1/ivr/call/voice", json={
        "call_id": call_id,
        "speech_transcript": "Can I spray Endosulfan at 500ml per acre for cotton bollworm?"
    })
    assert v_res.status_code == 200
    data = v_res.json()

    assert data["is_safe"] is False
    assert data["is_escalated"] is True
    assert data["ticket_number"] is not None
    assert data["ticket_number"].startswith("ESC-")
    assert any(term in data["response_text"] for term in ["ESC-", "Safety", "నిషేధించబడిన", "CIBRC", "చట్టవిరుద్ధం", "భద్రతా"])


def test_req_6_error_handling_and_recovery():
    """
    6. Error Handling & Recovery:
    - Speech recognition failure simulation (garbled audio)
    - 10s silence timeout simulation
    - Backend 503 failure simulation and graceful fallback
    """
    res = client.post("/api/v1/ivr/call/start", json={"caller_number": "+919848012345"})
    call_id = res.json()["call_id"]

    # 1. Speech recognition failure (garbled audio)
    err1 = client.post("/api/v1/ivr/call/simulate-error", json={
        "call_id": call_id,
        "error_type": "speech_recognition_failure"
    })
    assert err1.status_code == 200
    assert err1.json()["error_type"] == "speech_recognition_failure"
    assert len(err1.json()["recovery_prompt"]) > 5

    # 2. 10s silence timeout
    err2 = client.post("/api/v1/ivr/call/simulate-error", json={
        "call_id": call_id,
        "error_type": "silence_timeout"
    })
    assert err2.status_code == 200
    assert err2.json()["error_type"] == "silence_timeout"
    assert len(err2.json()["recovery_prompt"]) > 5

    # 3. Backend 503 failure simulation -> auto escalates with ESC- ticket
    err3 = client.post("/api/v1/ivr/call/simulate-error", json={
        "call_id": call_id,
        "error_type": "backend_failure"
    })
    assert err3.status_code == 200
    d3 = err3.json()
    assert d3["error_type"] == "backend_failure"
    assert d3["status"] == "escalated"
    assert d3["ticket_number"].startswith("ESC-")


def test_req_7_call_termination_and_session_cleanup():
    """
    7. Call Termination & Session Cleanup:
    - Pressing DTMF '0' or '*' or clicking End Call terminates the call
    - Duration calculation and session resource cleanup
    """
    # Test termination via End Call endpoint
    res1 = client.post("/api/v1/ivr/call/start", json={"caller_number": "+919848012345"})
    call_id1 = res1.json()["call_id"]

    end_res = client.post("/api/v1/ivr/call/end", json={"call_id": call_id1, "reason": "farmer_ended"})
    assert end_res.status_code == 200
    assert end_res.json()["status"] == "ended"

    sess1 = client.get(f"/api/v1/ivr/call/{call_id1}").json()
    assert sess1["status"] == "ended"
    assert sess1["duration_seconds"] >= 0
    assert sess1["ended_at"] is not None

    # Test termination via DTMF '0'
    res2 = client.post("/api/v1/ivr/call/start", json={"caller_number": "+919848012345"})
    call_id2 = res2.json()["call_id"]

    dtmf_0 = client.post("/api/v1/ivr/call/dtmf", json={"call_id": call_id2, "digit": "0"})
    assert dtmf_0.status_code == 200
    assert dtmf_0.json()["action"] == "end_call"
    assert dtmf_0.json()["status"] == "ended"

    sess2 = client.get(f"/api/v1/ivr/call/{call_id2}").json()
    assert sess2["status"] == "ended"
    assert sess2["ended_at"] is not None
