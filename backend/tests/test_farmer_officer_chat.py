import pytest
from starlette.testclient import TestClient
from app.database import models

def test_demo_accounts_seed_and_login(client: TestClient, db_session):
    """Verify single demo farmer and single demo extension officer login cleanly with dev OTPs."""
    # 1. Demo Farmer login with 9876543210
    farmer_res = client.post(
        "/api/v1/auth/verify-otp",
        json={"phone_number": "9876543210", "otp": "123456"}
    )
    assert farmer_res.status_code == 200, farmer_res.text
    farmer_data = farmer_res.json()
    assert "access_token" in farmer_data
    assert farmer_data["user"]["role"] == "farmer"
    assert "Kishan" in farmer_data["user"]["full_name"] or "Ramu" in farmer_data["user"]["full_name"]

    # 1b. Demo Farmer login with +919876543210 format
    farmer_res_intl = client.post(
        "/api/v1/auth/verify-otp",
        json={"phone_number": "+919876543210", "otp": "123456"}
    )
    assert farmer_res_intl.status_code == 200
    assert "access_token" in farmer_res_intl.json()

    # 2. Demo Extension Officer login with AGRI-TS-042 / 9123456789 / 654321
    officer_res = client.post(
        "/api/v1/auth/officer-login",
        json={
            "officer_code": "AGRI-TS-042",
            "phone_number": "9123456789",
            "otp": "654321"
        }
    )
    assert officer_res.status_code == 200, officer_res.text
    officer_data = officer_res.json()
    assert "access_token" in officer_data
    officer_info = officer_data["officer"]
    assert officer_info["officer_code"] == "AGRI-TS-042"
    assert "Mandal Agricultural Officer" in officer_info["designation"]
    assert "Geesugonda" in officer_info["assigned_mandals"]

def test_farmer_officer_ticket_chat_full_flow(client: TestClient, db_session):
    """
    Complete lifecycle test:
    1. Farmer logs in and creates a ticket with an attached image.
    2. Farmer lists tickets to verify PENDING state.
    3. Officer logs in, inspects ticket details and initial root message.
    4. Officer replies with prescription -> status changes to RESPONDED.
    5. Farmer lists messages and sees officer reply.
    6. Farmer sends follow-up with inspection image attachment -> status changes to IN_REVIEW.
    7. Officer sees all 3 messages in sequence.
    8. Officer marks ticket RESOLVED -> both farmer and officer see RESOLVED status with timestamp.
    """
    # Step 1: Farmer login
    farmer_auth = client.post(
        "/api/v1/auth/verify-otp",
        json={"phone_number": "9876543210", "otp": "123456"}
    ).json()
    farmer_token = farmer_auth["access_token"]
    farmer_headers = {"Authorization": f"Bearer {farmer_token}"}

    # Step 2: Farmer creates ticket from question
    create_payload = {
        "crop_name": "Cotton",
        "state": "Telangana",
        "district": "Warangal",
        "mandal": "Geesugonda",
        "village": "Dharmaram",
        "escalation_reason": "Severe pink bollworm infestation not responding to standard sprays",
        "original_question_transcript": "నా ప్రత్తి చేనులో గులాబీ రంగు పురుగు తీవ్రంగా ఉంది. ఆకులు రాలిపోతున్నాయి. తక్షణ సలహా కావాలి.",
        "attached_image_urls": ["http://example.com/static/cotton_pest_01.jpg"],
        "priority": "high"
    }
    create_res = client.post("/api/v1/tickets/", headers=farmer_headers, json=create_payload)
    assert create_res.status_code == 200, create_res.text
    ticket_id = create_res.json()["ticket_id"]
    ticket_num = create_res.json()["ticket_number"]

    # Step 3: Farmer lists tickets
    my_tickets_res = client.get("/api/v1/tickets/my", headers=farmer_headers)
    assert my_tickets_res.status_code == 200
    my_tickets = my_tickets_res.json()
    created_ticket = next((t for t in my_tickets if t["id"] == ticket_id), None)
    assert created_ticket is not None
    assert created_ticket["ticket_number"] == ticket_num
    assert created_ticket["status"] == "PENDING"

    # Step 4: Officer logs in
    officer_auth = client.post(
        "/api/v1/auth/officer-login",
        json={
            "officer_code": "AGRI-TS-042",
            "phone_number": "9123456789",
            "otp": "654321"
        }
    ).json()
    officer_token = officer_auth["access_token"]
    officer_headers = {"Authorization": f"Bearer {officer_token}"}

    # Step 5: Officer views ticket detail
    detail_res = client.get(f"/api/v1/tickets/{ticket_id}", headers=officer_headers)
    assert detail_res.status_code == 200
    detail_data = detail_res.json()
    assert detail_data["ticket_number"] == ticket_num
    assert detail_data["mandal"] == "Geesugonda"
    assert len(detail_data["attached_image_urls"]) == 1

    # Officer checks initial ticket messages (should include root farmer message)
    initial_msgs_res = client.get(f"/api/v1/tickets/{ticket_id}/messages", headers=officer_headers)
    assert initial_msgs_res.status_code == 200
    initial_msgs = initial_msgs_res.json()
    assert len(initial_msgs) == 1
    root_msg = initial_msgs[0]
    assert root_msg["sender_type"] == "FARMER"
    assert "గులాబీ రంగు పురుగు" in root_msg["content"]
    assert root_msg["media_url"] == "http://example.com/static/cotton_pest_01.jpg"

    # Step 6: Officer replies to ticket
    officer_reply_payload = {
        "content": "నమస్కారం కిషన్ రావు గారు. గులాబీ రంగు పురుగు నివారణకు ప్రొఫెనోఫాస్ 400 మి.లీ ఎకరాకు 200 లీటర్ల నీటిలో కలిపి పిచికారీ చేయండి.",
        "msg_type": "prescription"
    }
    reply_res = client.post(
        f"/api/v1/tickets/{ticket_id}/messages",
        headers=officer_headers,
        json=officer_reply_payload
    )
    assert reply_res.status_code == 200, reply_res.text
    reply_data = reply_res.json()
    assert reply_data["sender_type"] == "OFFICER"
    assert reply_data["msg_type"] == "prescription"
    assert "ప్రొఫెనోఫాస్" in reply_data["content"]

    # Step 7: Farmer lists messages and verifies officer response
    farmer_msgs_res = client.get(f"/api/v1/tickets/{ticket_id}/messages", headers=farmer_headers)
    assert farmer_msgs_res.status_code == 200
    farmer_msgs = farmer_msgs_res.json()
    assert len(farmer_msgs) == 2
    assert farmer_msgs[0]["sender_type"] == "FARMER"
    assert farmer_msgs[1]["sender_type"] == "OFFICER"
    assert "ప్రొఫెనోఫాస్" in farmer_msgs[1]["content"]

    # Ticket status should now reflect officer response
    ticket_status_check = client.get(f"/api/v1/tickets/{ticket_id}", headers=farmer_headers).json()
    assert ticket_status_check["status"] == "RESPONDED"

    # Step 8: Farmer posts follow-up message with image attachment
    farmer_followup_payload = {
        "content": "ధన్యవాదాలు సార్, మందు పిచికారీ చేసిన తర్వాత ఆకుల స్థితి ఇక్కడ పంపుతున్నాను.",
        "msg_type": "image",
        "media_url": "http://example.com/static/cotton_followup_leaf.jpg"
    }
    followup_res = client.post(
        f"/api/v1/tickets/{ticket_id}/messages",
        headers=farmer_headers,
        json=farmer_followup_payload
    )
    assert followup_res.status_code == 200
    followup_data = followup_res.json()
    assert followup_data["sender_type"] == "FARMER"
    assert followup_data["media_url"] == "http://example.com/static/cotton_followup_leaf.jpg"

    # Status should transition to IN_REVIEW upon farmer follow-up
    ticket_status_check2 = client.get(f"/api/v1/tickets/{ticket_id}", headers=farmer_headers).json()
    assert ticket_status_check2["status"] == "IN_REVIEW"

    # Step 9: Officer inspects full message thread
    officer_msgs_res = client.get(f"/api/v1/tickets/{ticket_id}/messages", headers=officer_headers)
    assert officer_msgs_res.status_code == 200
    all_msgs = officer_msgs_res.json()
    assert len(all_msgs) == 3
    assert all_msgs[0]["sender_type"] == "FARMER"
    assert all_msgs[1]["sender_type"] == "OFFICER"
    assert all_msgs[2]["sender_type"] == "FARMER"
    assert all_msgs[2]["media_url"] == "http://example.com/static/cotton_followup_leaf.jpg"

    # Step 10: Officer marks ticket resolved
    status_update_res = client.post(
        f"/api/v1/tickets/{ticket_id}/status",
        headers=officer_headers,
        json={"status": "RESOLVED"}
    )
    assert status_update_res.status_code == 200
    status_data = status_update_res.json()
    assert status_data["status"] == "RESOLVED"
    assert status_data["resolved_at"] is not None

    # Step 11: Both farmer and officer see resolved status
    farmer_view = client.get(f"/api/v1/tickets/{ticket_id}", headers=farmer_headers).json()
    assert farmer_view["status"] == "RESOLVED"
    assert farmer_view["resolved_at"] is not None

    officer_view = client.get(f"/api/v1/tickets/{ticket_id}", headers=officer_headers).json()
    assert officer_view["status"] == "RESOLVED"
    assert officer_view["resolved_at"] is not None

def test_farmer_authorization_isolation(client: TestClient, db_session):
    """Ensure another farmer cannot read or write to other farmers' tickets."""
    # 1. Login demo farmer 1 (owner)
    f1_auth = client.post(
        "/api/v1/auth/verify-otp",
        json={"phone_number": "9876543210", "otp": "123456"}
    ).json()
    f1_headers = {"Authorization": f"Bearer {f1_auth['access_token']}"}

    # Create ticket
    t_res = client.post(
        "/api/v1/tickets/",
        headers=f1_headers,
        json={
            "crop_name": "Chilli",
            "escalation_reason": "Gemini virus black thrips",
            "original_question_transcript": "మిరప తోటలో నల్ల తామర పురుగు సమస్య ఉంది."
        }
    )
    ticket_id = t_res.json()["ticket_id"]

    # 2. Login farmer 2 (different farmer)
    f2_auth = client.post(
        "/api/v1/auth/verify-otp",
        json={"phone_number": "9000000001", "otp": "123456"}
    ).json()
    f2_headers = {"Authorization": f"Bearer {f2_auth['access_token']}"}

    # Farmer 2 cannot view ticket detail -> 403 Forbidden
    detail_unauth = client.get(f"/api/v1/tickets/{ticket_id}", headers=f2_headers)
    assert detail_unauth.status_code == 403

    # Farmer 2 cannot view ticket messages -> 403 Forbidden
    msgs_unauth = client.get(f"/api/v1/tickets/{ticket_id}/messages", headers=f2_headers)
    assert msgs_unauth.status_code == 403

    # Farmer 2 cannot post message to ticket -> 403 Forbidden
    post_unauth = client.post(
        f"/api/v1/tickets/{ticket_id}/messages",
        headers=f2_headers,
        json={"content": "Malicious unauthorized message"}
    )
    assert post_unauth.status_code == 403

    # Farmer 2 cannot change ticket status -> 403 Forbidden
    status_unauth = client.post(
        f"/api/v1/tickets/{ticket_id}/status",
        headers=f2_headers,
        json={"status": "CLOSED"}
    )
    assert status_unauth.status_code == 403

def test_ticket_conversation_thread_sync(client: TestClient, db_session):
    """
    Test that when a ticket is created with a linked conversation_id:
    - Officer ticket message also appends an OFFICER message to ConversationMessage.
    - Resolving ticket updates Conversation escalation_status to RESOLVED.
    """
    farmer_auth = client.post(
        "/api/v1/auth/verify-otp",
        json={"phone_number": "9876543210", "otp": "123456"}
    ).json()
    farmer_headers = {"Authorization": f"Bearer {farmer_auth['access_token']}"}

    officer_auth = client.post(
        "/api/v1/auth/officer-login",
        json={
            "officer_code": "AGRI-TS-042",
            "phone_number": "9123456789",
            "otp": "654321"
        }
    ).json()
    officer_headers = {"Authorization": f"Bearer {officer_auth['access_token']}"}

    # 1. Create a conversation
    conv_res = client.post(
        "/api/v1/conversations/",
        headers=farmer_headers,
        json={"title": "Tomato Wilt Consultation", "crop": "Tomato", "language": "te"}
    )
    conv_id = conv_res.json()["id"]

    # 2. Escalate to ticket with conversation_id
    ticket_res = client.post(
        "/api/v1/tickets/escalate",
        headers=farmer_headers,
        json={
            "conversation_id": conv_id,
            "crop_name": "Tomato",
            "escalation_reason": "Bacterial wilt escalation",
            "original_question_transcript": "టమోటా మొక్కలు హఠాత్తుగా వాలిపోతున్నాయి, బ్యాక్టీరియల్ విల్ట్ అనుమానం."
        }
    )
    ticket_id = ticket_res.json()["ticket_id"]

    # Verify conversation escalation status is ESCALATED
    conv_check1 = client.get(f"/api/v1/conversations/{conv_id}", headers=farmer_headers).json()
    assert conv_check1["escalation_status"] == "ESCALATED"

    # 3. Officer posts reply via ticket messages
    officer_msg_res = client.post(
        f"/api/v1/tickets/{ticket_id}/messages",
        headers=officer_headers,
        json={
            "content": "కాపర్ ఆక్సిక్లోరైడ్ 3 గ్రాములు + స్ట్రెప్టోసైక్లిన్ 1 గ్రాము 10 లీటర్ల నీటిలో కలిపి మొక్కల మొదళ్ల వద్ద తడపండి.",
            "msg_type": "prescription"
        }
    )
    assert officer_msg_res.status_code == 200

    # 4. Check conversation: should have officer message appended!
    conv_check2 = client.get(f"/api/v1/conversations/{conv_id}", headers=farmer_headers).json()
    officer_msgs = [m for m in conv_check2["messages"] if m["sender_type"] == "OFFICER"]
    assert len(officer_msgs) >= 1
    assert "కాపర్ ఆక్సిక్లోరైడ్" in officer_msgs[-1]["response"]

    # 5. Officer marks ticket status RESOLVED
    status_res = client.post(
        f"/api/v1/tickets/{ticket_id}/status",
        headers=officer_headers,
        json={"status": "RESOLVED"}
    )
    assert status_res.status_code == 200

    # Conversation escalation_status is updated to RESOLVED
    conv_check3 = client.get(f"/api/v1/conversations/{conv_id}", headers=farmer_headers).json()
    assert conv_check3["escalation_status"] == "RESOLVED"
