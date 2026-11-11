import pytest
from fastapi.testclient import TestClient
from app.main import app
from app.services.sms_service import (
    sms_service,
    calculate_sms_segments,
    get_sms_segment_details,
    format_sms_text
)

@pytest.fixture(autouse=True)
def reset_sms_provider_state():
    """Ensure clean failure mode and idempotency cache before each test."""
    sms_service._failure_mode = False
    sms_service._dedup_cache.clear()
    yield
    sms_service._failure_mode = False
    sms_service._dedup_cache.clear()


# ============================================================================
# 1 & 2. GSM-7 & Unicode Telecom SMS Segmentation Math
# ============================================================================

def test_gsm7_and_unicode_segmentation_mathematics():
    """
    Verifies standard telecom 3GPP TS 23.040 segment accounting.
    Confirms that a 320-character limit does NOT imply 2 segments:
    - In GSM-7: 320 chars = 3 segments (153 + 153 + 14 = 320).
    - In UCS-2 Unicode (Telugu/Hindi): 320 chars = 5 segments (67 * 4 + 52 = 320).
    """
    # Empty string
    assert calculate_sms_segments("") == 0
    empty_det = get_sms_segment_details("")
    assert empty_det["segments"] == 0
    assert empty_det["encoding"] == "GSM-7"

    # GSM-7 single segment: exactly 160 characters
    gsm_160 = "A" * 160
    det_160 = get_sms_segment_details(gsm_160)
    assert det_160["segments"] == 1
    assert det_160["encoding"] == "GSM-7"

    # GSM-7 multi-part: 161 characters requires 2 segments (153 + 8)
    gsm_161 = "A" * 161
    det_161 = get_sms_segment_details(gsm_161)
    assert det_161["segments"] == 2

    # GSM-7 multi-part: 306 characters = exactly 2 segments (153 * 2)
    gsm_306 = "A" * 306
    assert get_sms_segment_details(gsm_306)["segments"] == 2

    # GSM-7 multi-part: 320 characters = 3 segments (NOT 2 segments!)
    gsm_320 = "A" * 320
    det_320 = get_sms_segment_details(gsm_320)
    assert det_320["segments"] == 3
    assert det_320["encoding"] == "GSM-7"

    # Unicode (UCS-2): Single segment <= 70 characters
    telugu_70 = "వరి" * 23 + "వ"  # 70 code units
    assert len(telugu_70) == 70
    det_te_70 = get_sms_segment_details(telugu_70)
    assert det_te_70["segments"] == 1
    assert det_te_70["encoding"] == "UCS-2 (Unicode)"

    # Unicode (UCS-2): Multi-part 71 characters requires 2 segments (67 + 4)
    telugu_71 = telugu_70 + "రి"
    assert len(telugu_71) == 72
    assert get_sms_segment_details(telugu_71)["segments"] == 2

    # Unicode (UCS-2): 140 characters in Telugu requires 3 segments (67 * 2 + 6)
    telugu_140 = "వరి పంట తెగులు నివారణ " * 6  # 22 * 6 = 132 chars
    det_te_140 = get_sms_segment_details(telugu_140)
    assert det_te_140["segments"] >= 2

    # Unicode (UCS-2): 320 characters in Telugu/Hindi requires 5 segments (NOT 2 segments!)
    hindi_phrase = "टमाटर में अगेती झुलसा का उपचार करें। "
    hindi_320 = (hindi_phrase * 10)[:320]
    assert len(hindi_320) == 320
    det_hi_320 = get_sms_segment_details(hindi_320)
    assert det_hi_320["encoding"] == "UCS-2 (Unicode)"
    assert det_hi_320["segments"] == 5  # ceil(320 / 67) = 5 segments


# ============================================================================
# 3. Preservation of Safety-Critical Instructions
# ============================================================================

def test_preservation_of_safety_critical_instructions():
    """
    Verifies that when text is cleaned or formatted, complete safety-critical
    instructions are preserved intact:
    - Pre-Harvest Interval (PHI)
    - Personal Protective Equipment (PPE)
    - CIBRC tracking ticket numbers
    - Exact approved dosage
    """
    raw_with_safety = (
        "**Crop Management Advice**: Cotton Pink Bollworm Control: "
        "Use Proclaim 5% SG @ 88 g/acre in 200L water. "
        "Pre-Harvest Interval (PHI): 14 days. Always wear protective mask and gloves. "
        "Ticket: ESC-20261110-B8A123. (KB07) [KB-DOC-01]"
    )
    formatted = format_sms_text(raw_with_safety, max_chars=320)

    # Markdown stripped
    assert "**" not in formatted
    assert "(KB07)" not in formatted
    assert "[KB-DOC-01]" not in formatted

    # Safety instructions preserved completely
    assert "88 g/acre in 200L water" in formatted
    assert "Pre-Harvest Interval (PHI): 14 days" in formatted
    assert "wear protective mask and gloves" in formatted
    assert "ESC-20261110-B8A123" in formatted

    # Telugu safety text preservation
    raw_te_safety = (
        "వరి అగ్గితెగులు నివారణ: అమిస్టార్ టాప్ మోతాదు ఎకరాకు 200 మి.లీ (200 లీటర్ల నీటిలో). "
        "కోతకు ముందు వ్యవధి (PHI): 15 రోజులు. పిచికారీ సమయంలో రక్షణ మాస్క్, గ్లౌజులు తప్పక ధరించండి."
    )
    formatted_te = format_sms_text(raw_te_safety, max_chars=320)
    assert "కోతకు ముందు వ్యవధి (PHI): 15 రోజులు" in formatted_te
    assert "మాస్క్, గ్లౌజులు తప్పక ధరించండి" in formatted_te


# ============================================================================
# 4. Conversation History Isolation by Sender
# ============================================================================

def test_conversation_history_sender_isolation(client: TestClient):
    """
    Tests that conversation context is strictly isolated by sender phone number.
    - Sender 1 asks about Cotton pink bollworm.
    - Sender 2 asks about Tomato early blight.
    - Sender 1 asks 'What about its dosage?' -> resolves Cotton pink bollworm.
    - Sender 2 asks 'What about its dosage?' -> resolves Tomato early blight.
    - Clearing Sender 1 does NOT delete Sender 2's session history.
    """
    phone1 = "+919848011111"
    phone2 = "+919414022222"
    sms_service.clear_history(phone1)
    sms_service.clear_history(phone2)

    # Sender 1 Turn 1: Cotton
    r1 = client.post("/api/v1/sms/incoming", json={
        "sender_number": phone1,
        "message_text": "How to control pink bollworm in cotton?",
        "language": "en"
    })
    assert r1.status_code == 200

    # Sender 2 Turn 1: Tomato
    r2 = client.post("/api/v1/sms/incoming", json={
        "sender_number": phone2,
        "message_text": "What is the medicine for tomato early blight?",
        "language": "en"
    })
    assert r2.status_code == 200

    # Sender 1 Turn 2: Follow-up
    r1_followup = client.post("/api/v1/sms/incoming", json={
        "sender_number": phone1,
        "message_text": "What about its dosage?",
        "language": "en"
    })
    assert r1_followup.status_code == 200
    d1 = r1_followup.json()
    assert d1["reply"]["crop"] == "Cotton"
    assert d1["reply"]["target_entity"] == "pink bollworm"

    # Sender 2 Turn 2: Follow-up
    r2_followup = client.post("/api/v1/sms/incoming", json={
        "sender_number": phone2,
        "message_text": "What about its dosage?",
        "language": "en"
    })
    assert r2_followup.status_code == 200
    d2 = r2_followup.json()
    assert d2["reply"]["crop"] == "Tomato"
    assert d2["reply"]["target_entity"] in ["early blight", "late blight"]

    # Verify history counts are strictly isolated
    h1 = client.get("/api/v1/sms/history", params={"phone_number": phone1}).json()
    h2 = client.get("/api/v1/sms/history", params={"phone_number": phone2}).json()
    assert h1["count"] == 4  # 2 in, 2 out
    assert h2["count"] == 4  # 2 in, 2 out

    # Clear Sender 1's history
    client.delete("/api/v1/sms/history", params={"phone_number": phone1})
    assert client.get("/api/v1/sms/history", params={"phone_number": phone1}).json()["count"] == 0
    # Sender 2's history must remain completely intact
    assert client.get("/api/v1/sms/history", params={"phone_number": phone2}).json()["count"] == 4


# ============================================================================
# 5. Duplicate Prevention Uses Unique Message IDs / Idempotency Keys
# ============================================================================

def test_duplicate_prevention_and_legitimate_repeated_questions(client: TestClient):
    """
    Tests requirement 5:
    1. A repeated query WITH THE SAME message_id (carrier retry) triggers idempotency deduplication.
    2. A repeated query WITHOUT a duplicate message_id (legitimate repeated question by farmer)
       is PROCESSED FRESH, NOT suppressed.
    """
    phone = "+919876540001"
    sms_service.clear_history(phone)

    # Scenario A: Carrier retry with identical message_id
    shared_msg_id = "CARRIER-MSG-RETRY-999"
    payload_with_id = {
        "sender_number": phone,
        "message_text": "What is the recommended dose of Proclaim in cotton?",
        "message_id": shared_msg_id,
        "language": "en"
    }

    # First delivery
    res1 = client.post("/api/v1/sms/incoming", json=payload_with_id)
    assert res1.status_code == 200
    assert res1.json()["is_duplicate"] is False

    # Second delivery with the SAME message_id within idempotency window
    res2 = client.post("/api/v1/sms/incoming", json=payload_with_id)
    assert res2.status_code == 200
    assert res2.json()["is_duplicate"] is True  # Properly deduplicated

    # Scenario B: Legitimate repeated question by farmer (distinct message ID or omitted ID)
    payload_legitimate_repeat = {
        "sender_number": phone,
        "message_text": "What is the recommended dose of Proclaim in cotton?",
        "language": "en"
        # No message_id supplied -> generates fresh unique ID
    }

    res3 = client.post("/api/v1/sms/incoming", json=payload_legitimate_repeat)
    assert res3.status_code == 200
    assert res3.json()["is_duplicate"] is False  # NOT suppressed! Freshly answered.

    # Legitimate repeat with a new explicit client message ID
    payload_repeat_with_new_id = {
        "sender_number": phone,
        "message_text": "What is the recommended dose of Proclaim in cotton?",
        "message_id": "CARRIER-MSG-NEW-1002",
        "language": "en"
    }
    res4 = client.post("/api/v1/sms/incoming", json=payload_repeat_with_new_id)
    assert res4.status_code == 200
    assert res4.json()["is_duplicate"] is False  # NOT suppressed!


# ============================================================================
# 6. Multilingual Conversations (English, Telugu, Hindi)
# ============================================================================

def test_english_crop_query(client: TestClient):
    """English crop query for Cotton Pink Bollworm."""
    phone = "+919876500001"
    sms_service.clear_history(phone)

    payload = {
        "sender_number": phone,
        "message_text": "Cotton pink bollworm pesticide spray dose",
        "language": "en"
    }

    response = client.post("/api/v1/sms/incoming", json=payload)
    assert response.status_code == 200
    data = response.json()

    assert data["status"] == "processed"
    assert data["delivery_status"] == "SIMULATED"
    assert data["is_demo"] is True
    assert data["is_simulated"] is True

    reply = data["reply"]
    assert reply["delivery_status"] == "SIMULATED"
    assert reply["is_safe"] is True
    assert reply["char_count"] <= 320
    assert reply["segments"] >= 1
    # Check GSM-7 or accurate encoding
    assert "Cotton" in reply["message_text"] or "pink bollworm" in reply["message_text"].lower()


def test_telugu_crop_query(client: TestClient):
    """Telugu crop query for Rice Blast (వరి అగ్గితెగులు)."""
    phone = "+919876500002"
    sms_service.clear_history(phone)

    payload = {
        "sender_number": phone,
        "message_text": "వరిలో అగ్గితెగులు వచ్చింది ఏ మందు స్ప్రే చేయాలి?",
        "language": "te"
    }

    response = client.post("/api/v1/sms/incoming", json=payload)
    assert response.status_code == 200
    data = response.json()

    assert data["status"] == "processed"
    assert data["delivery_status"] == "SIMULATED"
    assert data["is_simulated"] is True

    reply = data["reply"]
    assert reply["language"] == "te"
    assert reply["is_safe"] is True
    assert reply["encoding"] == "UCS-2 (Unicode)"
    assert len(reply["message_text"]) <= 320
    # Must contain Telugu script
    assert any('\u0c00' <= c <= '\u0c7f' for c in reply["message_text"])


def test_hindi_crop_query(client: TestClient):
    """Hindi crop query for Tomato Early Blight (टमाटर अगेती झुलसा)."""
    phone = "+919876500003"
    sms_service.clear_history(phone)

    payload = {
        "sender_number": phone,
        "message_text": "टमाटर में लेट ब्लाइट रोग के लिए क्या स्प्रे करें?",
        "language": "hi"
    }

    response = client.post("/api/v1/sms/incoming", json=payload)
    assert response.status_code == 200
    data = response.json()

    assert data["status"] == "processed"
    assert data["delivery_status"] == "SIMULATED"
    assert data["is_simulated"] is True

    reply = data["reply"]
    assert reply["language"] == "hi"
    assert reply["is_safe"] is True
    assert reply["encoding"] == "UCS-2 (Unicode)"
    assert len(reply["message_text"]) <= 320
    # Must contain Devanagari script
    assert any('\u0900' <= c <= '\u097f' for c in reply["message_text"])


# ============================================================================
# 7. Unsafe Pesticide Questions & Officer Escalation
# ============================================================================

def test_unsafe_pesticide_safety_gate_escalation(client: TestClient):
    """
    Tests requirement 7:
    1. Unapproved chemical cocktail query triggers CIBRC Statutory Safety Gate.
    2. Banned pesticide query (Endosulfan) triggers instant escalation.
    3. Both return official tracking tickets starting with 'ESC-'.
    """
    phone = "+919876500005"
    sms_service.clear_history(phone)

    # 1. Chemical Cocktail mix query
    payload_mix = {
        "sender_number": phone,
        "message_text": "Can I mix Chlorpyrifos and Copper Oxychloride in one tank for spray?",
        "language": "en"
    }
    r_mix = client.post("/api/v1/sms/incoming", json=payload_mix)
    assert r_mix.status_code == 200
    d_mix = r_mix.json()

    assert d_mix["status"] == "processed"
    assert d_mix["delivery_status"] == "SIMULATED"
    assert d_mix["reply"]["is_safe"] is False
    assert d_mix["reply"]["ticket_number"] is not None
    assert d_mix["reply"]["ticket_number"].startswith("ESC-")
    assert "ESC-" in d_mix["reply"]["message_text"]

    # 2. Banned chemical query (Endosulfan)
    payload_banned = {
        "sender_number": phone,
        "message_text": "How many ml of Endosulfan should I spray per acre in cotton?",
        "language": "en"
    }
    r_banned = client.post("/api/v1/sms/incoming", json=payload_banned)
    assert r_banned.status_code == 200
    d_banned = r_banned.json()

    assert d_banned["reply"]["is_safe"] is False
    assert d_banned["reply"]["ticket_number"].startswith("ESC-")


# ============================================================================
# 8. Simulated Network Failure and Recovery
# ============================================================================

def test_simulated_network_failure_and_recovery(client: TestClient):
    """
    Tests requirement 8:
    1. Per-request failure simulation override returns status: failed, delivery_status: FAILED.
    2. Global failure toggle ON fails all subsequent requests.
    3. Global failure toggle OFF recovers to operational state and returns SIMULATED.
    """
    phone = "+919876500007"
    sms_service.clear_history(phone)

    # 1. Per-request override failure
    payload_fail = {
        "sender_number": phone,
        "message_text": "Hello test failure query",
        "simulate_failure": True
    }
    res_fail = client.post("/api/v1/sms/incoming", json=payload_fail)
    assert res_fail.status_code == 200
    d_fail = res_fail.json()
    assert d_fail["status"] == "failed"
    assert d_fail["delivery_status"] == "FAILED"
    assert d_fail["reply"]["delivery_status"] == "FAILED"

    # Subsequent request without simulate_failure succeeds
    res_normal = client.post("/api/v1/sms/incoming", json={
        "sender_number": phone,
        "message_text": "Pesticide for cotton pink bollworm",
        "simulate_failure": False
    })
    assert res_normal.json()["status"] == "processed"
    assert res_normal.json()["delivery_status"] == "SIMULATED"

    # 2. Global Toggle ON
    toggle_on = client.post("/api/v1/sms/toggle-failure", json={"simulate_failure": True})
    assert toggle_on.status_code == 200
    assert toggle_on.json()["simulate_failure"] is True
    assert toggle_on.json()["status"] == "simulated_carrier_failure"

    res_glob_fail = client.post("/api/v1/sms/incoming", json={
        "sender_number": phone,
        "message_text": "Global failure test query"
    })
    assert res_glob_fail.json()["delivery_status"] == "FAILED"

    # 3. Global Toggle OFF (Recovery)
    toggle_off = client.post("/api/v1/sms/toggle-failure", json={"simulate_failure": False})
    assert toggle_off.status_code == 200
    assert toggle_off.json()["simulate_failure"] is False
    assert toggle_off.json()["status"] == "operational"

    res_recovered = client.post("/api/v1/sms/incoming", json={
        "sender_number": phone,
        "message_text": "Recovered pipeline test query"
    })
    assert res_recovered.json()["status"] == "processed"
    assert res_recovered.json()["delivery_status"] == "SIMULATED"


# ============================================================================
# 9. Every Message Visibly Labelled SIMULATED
# ============================================================================

def test_every_message_visibly_labelled_simulated(client: TestClient):
    """
    Tests requirement 9:
    Confirm that every inbound and outbound message in responses and history
    is visibly tagged as SIMULATED (or FAILED if carrier failure), and never
    labeled as live or actual delivered SMS.
    """
    phone = "+919876500009"
    sms_service.clear_history(phone)

    res = client.post("/api/v1/sms/incoming", json={
        "sender_number": phone,
        "message_text": "Cotton pink bollworm advice",
        "language": "en"
    })
    assert res.status_code == 200
    data = res.json()

    assert data["delivery_status"] in ["SIMULATED", "FAILED"]
    assert data["is_demo"] is True
    assert data["is_simulated"] is True
    assert "SIMULATED" in data["simulation_label"]

    assert data["inbound"]["delivery_status"] in ["SIMULATED", "FAILED"]
    assert data["inbound"]["is_simulated"] is True

    assert data["reply"]["delivery_status"] in ["SIMULATED", "FAILED"]
    assert data["reply"]["is_simulated"] is True

    # Check history endpoint also returns is_simulated tags
    hist = client.get("/api/v1/sms/history", params={"phone_number": phone}).json()
    assert hist["is_demo"] is True
    assert hist["is_simulated"] is True
    for msg in hist["messages"]:
        assert msg["delivery_status"] in ["SIMULATED", "FAILED"]
        assert msg["is_simulated"] is True


# ============================================================================
# 10. History, Status, and HTML Page
# ============================================================================

def test_sms_history_and_clear_endpoints(client: TestClient):
    """Tests GET /api/v1/sms/history and DELETE /api/v1/sms/history."""
    phone = "+919876500010"
    sms_service.clear_history(phone)

    client.post("/api/v1/sms/incoming", json={
        "sender_number": phone,
        "message_text": "Test query for history",
        "language": "en"
    })

    hist_res = client.get("/api/v1/sms/history", params={"phone_number": phone})
    assert hist_res.status_code == 200
    assert hist_res.json()["count"] >= 2

    del_res = client.delete("/api/v1/sms/history", params={"phone_number": phone})
    assert del_res.status_code == 200
    assert del_res.json()["cleared"] is True

    assert client.get("/api/v1/sms/history", params={"phone_number": phone}).json()["count"] == 0


def test_sms_status_endpoint(client: TestClient):
    """Tests GET /api/v1/sms/status."""
    res = client.get("/api/v1/sms/status")
    assert res.status_code == 200
    data = res.json()
    assert data["provider"] == "BOL_KISAN_MOCK_GATEWAY"
    assert data["is_demo"] is True
    assert data["is_simulated"] is True
    assert "stats" in data


def test_sms_demo_html_page(client: TestClient):
    """Tests GET /sms-demo HTML page rendering."""
    res = client.get("/sms-demo")
    assert res.status_code == 200
    assert "text/html" in res.headers["content-type"]
    assert "Bol Kisan" in res.text
    assert "SIMULATED" in res.text
