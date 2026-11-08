import pytest
from starlette.testclient import TestClient

def test_farmer_registration_and_login(client: TestClient):
    res = client.post(
        "/api/v1/auth/register-farmer",
        json={
            "phone_number": "9000000099",
            "full_name": "Ravi Kumar",
            "preferred_lang": "te",
            "state": "Telangana",
            "district": "Warangal",
            "mandal": "Geesugonda",
            "village": "Dharmaram",
            "land_size_acres": 4.5,
            "soil_type": "Black Cotton"
        }
    )
    assert res.status_code == 200
    data = res.json()
    assert "access_token" in data
    assert data["user"]["role"] == "farmer"
    assert data["user"]["preferred_lang"] == "te"

def test_officer_approval_workflow_and_rbac(client: TestClient, admin_token: str):
    # 1. Register new officer
    reg_res = client.post(
        "/api/v1/auth/register-officer",
        json={
            "phone_number": "9000000088",
            "full_name": "Dr. Ananya Reddy",
            "designation": "Assistant Agricultural Officer (AAO)",
            "department": "Department of Agriculture, Telangana",
            "official_email": "ananya.reddy@agri.telangana.gov.in",
            "employee_id": "TS-AGRI-9988",
            "assigned_state": "Telangana",
            "assigned_district": "Warangal",
            "assigned_mandals": ["Geesugonda", "Nekkonda"]
        }
    )
    assert reg_res.status_code == 200
    reg_data = reg_res.json()
    assert reg_data["approval_status"] == "PENDING_APPROVAL"
    officer_id = reg_data["officer_id"]
    officer_code = reg_data["officer_code"]

    # 2. Unapproved officer attempts to login -> HTTP 403 Forbidden
    login_res = client.post(
        "/api/v1/auth/officer-login",
        json={
            "officer_code": officer_code,
            "phone_number": "9000000088",
            "otp": "123456"
        }
    )
    assert login_res.status_code == 403
    assert "pending administrator approval" in login_res.json()["detail"].lower()

    # 3. Admin lists pending officers (requires admin bearer token)
    list_res = client.get(
        "/api/v1/auth/admin/pending-officers",
        headers={"Authorization": f"Bearer {admin_token}"}
    )
    assert list_res.status_code == 200
    pending_list = list_res.json()
    assert any(o["id"] == officer_id for o in pending_list)

    # 4. Admin approves officer
    approve_res = client.post(
        f"/api/v1/auth/admin/approve-officer/{officer_id}",
        headers={"Authorization": f"Bearer {admin_token}"}
    )
    assert approve_res.status_code == 200
    assert approve_res.json()["status"] == "APPROVED"

    # 5. Approved officer now logs in successfully -> HTTP 200 OK with JWT
    login_success_res = client.post(
        "/api/v1/auth/officer-login",
        json={
            "officer_code": officer_code,
            "phone_number": "9000000088",
            "otp": "123456"
        }
    )
    assert login_success_res.status_code == 200
    login_data = login_success_res.json()
    assert "access_token" in login_data
    assert login_data["officer"]["approval_status"] == "APPROVED"

def test_conversation_lifecycle_and_farmer_isolation(client: TestClient, farmer_token: str, officer_token: str):
    # 1. Create a conversation thread
    create_res = client.post(
        "/api/v1/conversations/",
        headers={"Authorization": f"Bearer {farmer_token}"},
        json={
            "title": "Maize Fall Armyworm Consultation",
            "crop": "Maize",
            "language": "te"
        }
    )
    assert create_res.status_code == 200
    conv_id = create_res.json()["id"]

    # 2. Post a general agronomy question to the conversation
    msg_res = client.post(
        f"/api/v1/conversations/{conv_id}/messages",
        headers={"Authorization": f"Bearer {farmer_token}"},
        json={
            "query_text": "మొక్కజొన్న సాగులో విత్తే సమయం మరియు నేల తయారీ విధానం ఏమిటి?",
            "language": "te",
            "crop": "Maize"
        }
    )
    assert msg_res.status_code == 200
    msg_data = msg_res.json()
    assert msg_data["is_safe"] is True
    assert len(msg_data["response_text"]) > 10

    # 3. Retrieve conversation history
    detail_res = client.get(
        f"/api/v1/conversations/{conv_id}",
        headers={"Authorization": f"Bearer {farmer_token}"}
    )
    assert detail_res.status_code == 200
    detail_data = detail_res.json()
    assert len(detail_data["messages"]) >= 2 # Farmer msg + AI response

    # 4. Farmer Data Isolation: Another farmer cannot see this conversation
    other_farmer_res = client.post(
        "/api/v1/auth/verify-otp",
        json={"phone_number": "9111111111", "otp": "123456"}
    )
    other_token = other_farmer_res.json()["access_token"]
    unauth_get = client.get(
        f"/api/v1/conversations/{conv_id}",
        headers={"Authorization": f"Bearer {other_token}"}
    )
    assert unauth_get.status_code == 404

def test_human_officer_escalation_workflow(client: TestClient, farmer_token: str, officer_token: str):
    # 1. Create a conversation
    conv_res = client.post(
        "/api/v1/conversations/",
        headers={"Authorization": f"Bearer {farmer_token}"},
        json={"title": "Severe Pest Crisis", "crop": "Cotton", "language": "te"}
    )
    conv_id = conv_res.json()["id"]

    # 2. Farmer asks question requiring human officer escalation
    esc_msg_res = client.post(
        f"/api/v1/conversations/{conv_id}/messages",
        headers={"Authorization": f"Bearer {farmer_token}"},
        json={
            "query_text": "I need help from agricultural extension officer immediately, mysterious worms destroying crop",
            "language": "en",
            "force_escalation": True
        }
    )
    assert esc_msg_res.status_code == 200
    esc_data = esc_msg_res.json()
    assert esc_data["is_escalated"] is True
    assert "ticket_number" in esc_data
    ticket_num = esc_data["ticket_number"]
    assert ticket_num.startswith("BK-2026-")

    # 3. Farmer views my tickets
    my_tickets_res = client.get(
        "/api/v1/tickets/my",
        headers={"Authorization": f"Bearer {farmer_token}"}
    )
    assert my_tickets_res.status_code == 200
    my_tickets = my_tickets_res.json()
    assert any(t["ticket_number"] == ticket_num for t in my_tickets)

    # 4. Officer resolves the ticket
    ticket_id = esc_data["ticket_id"]
    officer_resolve_res = client.post(
        f"/api/v1/tickets/{ticket_id}/respond",
        headers={"Authorization": f"Bearer {officer_token}"},
        json={
            "prescription": "Inspected symptoms. Recommended Emamectin Benzoate 5% SG @ 80g/acre in 200L water. Field visit scheduled.",
            "status": "RESPONDED"
        }
    )
    assert officer_resolve_res.status_code == 200

    # 5. Check that the conversation thread received the Officer Prescription message
    conv_after_res = client.get(
        f"/api/v1/conversations/{conv_id}",
        headers={"Authorization": f"Bearer {farmer_token}"}
    )
    conv_after = conv_after_res.json()
    officer_msgs = [m for m in conv_after["messages"] if m["sender_type"] == "OFFICER"]
    assert len(officer_msgs) == 1
    assert "Emamectin Benzoate" in officer_msgs[0]["response"]
