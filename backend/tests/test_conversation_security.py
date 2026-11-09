import pytest
from starlette.testclient import TestClient
from app.core.security import create_access_token
from app.database.models import User, FarmerProfile, ExtensionOfficer, Conversation, ConversationMessage, AgriculturalTicket

@pytest.fixture
def setup_test_users(db_session):
    db = db_session

    # Farmer 1 (Warangal / Geesugonda)
    farmer1 = db.query(User).filter(User.phone_number == "9700000001").first()
    if not farmer1:
        farmer1 = User(
            phone_number="9700000001",
            full_name="Farmer One",
            role="farmer",
            preferred_lang="te",
            is_active=True,
            is_verified=True
        )
        db.add(farmer1)
        db.commit()
        db.refresh(farmer1)
        p1 = FarmerProfile(
            user_id=farmer1.id,
            state="Telangana",
            district="Warangal",
            mandal="Geesugonda",
            village="Dharmaram"
        )
        db.add(p1)
        db.commit()

    # Farmer 2 (Karimnagar / Choppadandi)
    farmer2 = db.query(User).filter(User.phone_number == "9700000002").first()
    if not farmer2:
        farmer2 = User(
            phone_number="9700000002",
            full_name="Farmer Two",
            role="farmer",
            preferred_lang="te",
            is_active=True,
            is_verified=True
        )
        db.add(farmer2)
        db.commit()
        db.refresh(farmer2)
        p2 = FarmerProfile(
            user_id=farmer2.id,
            state="Telangana",
            district="Karimnagar",
            mandal="Choppadandi",
            village="Rampur"
        )
        db.add(p2)
        db.commit()

    # Officer 1: Assigned to Geesugonda, Warangal
    off_user1 = db.query(User).filter(User.phone_number == "9700000011").first()
    if not off_user1:
        off_user1 = User(
            phone_number="9700000011",
            full_name="Officer Warangal",
            role="extension_officer",
            preferred_lang="te",
            is_active=True,
            is_verified=True
        )
        db.add(off_user1)
        db.commit()
        db.refresh(off_user1)
        off_prof1 = ExtensionOfficer(
            user_id=off_user1.id,
            officer_code="AGRI-TG-0011",
            designation="MAO",
            department="Agriculture",
            assigned_state="Telangana",
            assigned_district="Warangal",
            assigned_mandals=["Geesugonda"],
            approval_status="APPROVED"
        )
        db.add(off_prof1)
        db.commit()

    # Officer 2: Assigned to Khammam (outside Warangal)
    off_user2 = db.query(User).filter(User.phone_number == "9700000012").first()
    if not off_user2:
        off_user2 = User(
            phone_number="9700000012",
            full_name="Officer Khammam",
            role="extension_officer",
            preferred_lang="te",
            is_active=True,
            is_verified=True
        )
        db.add(off_user2)
        db.commit()
        db.refresh(off_user2)
        off_prof2 = ExtensionOfficer(
            user_id=off_user2.id,
            officer_code="AGRI-TG-0012",
            designation="MAO",
            department="Agriculture",
            assigned_state="Telangana",
            assigned_district="Khammam",
            assigned_mandals=["Khammam_Rural"],
            approval_status="APPROVED"
        )
        db.add(off_prof2)
        db.commit()

    # Officer 3: Empty jurisdiction
    off_user3 = db.query(User).filter(User.phone_number == "9700000013").first()
    if not off_user3:
        off_user3 = User(
            phone_number="9700000013",
            full_name="Officer Unassigned",
            role="extension_officer",
            preferred_lang="te",
            is_active=True,
            is_verified=True
        )
        db.add(off_user3)
        db.commit()
        db.refresh(off_user3)
        off_prof3 = ExtensionOfficer(
            user_id=off_user3.id,
            officer_code="AGRI-TG-0013",
            designation="MAO",
            department="Agriculture",
            assigned_state="Telangana",
            assigned_district="Warangal",
            assigned_mandals=[],
            approval_status="APPROVED"
        )
        db.add(off_prof3)
        db.commit()

    tokens = {
        "farmer1": create_access_token(data={"sub": farmer1.id, "phone": farmer1.phone_number, "role": "farmer"}),
        "farmer2": create_access_token(data={"sub": farmer2.id, "phone": farmer2.phone_number, "role": "farmer"}),
        "officer_warangal": create_access_token(data={
            "sub": off_user1.id, "phone": off_user1.phone_number, "role": "extension_officer",
            "state": "Telangana", "district": "Warangal", "mandals": ["Geesugonda"]
        }),
        "officer_khammam": create_access_token(data={
            "sub": off_user2.id, "phone": off_user2.phone_number, "role": "extension_officer",
            "state": "Telangana", "district": "Khammam", "mandals": ["Khammam_Rural"]
        }),
        "officer_empty": create_access_token(data={
            "sub": off_user3.id, "phone": off_user3.phone_number, "role": "extension_officer",
            "state": "Telangana", "district": "Warangal", "mandals": []
        })
    }
    users = {
        "farmer1": farmer1,
        "farmer2": farmer2,
        "officer_warangal": off_user1,
        "officer_khammam": off_user2,
        "officer_empty": off_user3
    }
    return tokens, users


def test_cross_farmer_conversation_isolation(client: TestClient, setup_test_users):
    tokens, users = setup_test_users

    # Farmer 1 creates conversation
    res = client.post("/api/v1/conversations/", json={"title": "Cotton Leaf Spot", "crop": "Cotton", "language": "te"},
                      headers={"Authorization": f"Bearer {tokens['farmer1']}"})
    assert res.status_code == 200
    conv_id = res.json()["id"]

    # Farmer 1 accesses own conversation -> Success
    res_own = client.get(f"/api/v1/conversations/{conv_id}", headers={"Authorization": f"Bearer {tokens['farmer1']}"})
    assert res_own.status_code == 200

    # Farmer 2 attempts to access Farmer 1's conversation -> 404 Not Found (IDOR prevention)
    res_other = client.get(f"/api/v1/conversations/{conv_id}", headers={"Authorization": f"Bearer {tokens['farmer2']}"})
    assert res_other.status_code == 404


def test_officer_cannot_view_conversation_without_associated_ticket(client: TestClient, setup_test_users):
    tokens, users = setup_test_users

    # Farmer creates unescalated conversation
    res = client.post("/api/v1/conversations/", json={"title": "Routine Irrigation", "crop": "Maize", "language": "te"},
                      headers={"Authorization": f"Bearer {tokens['farmer1']}"})
    assert res.status_code == 200
    conv_id = res.json()["id"]

    # Officer attempts to view conversation without associated escalated ticket -> 403 Forbidden
    res_off = client.get(f"/api/v1/conversations/{conv_id}", headers={"Authorization": f"Bearer {tokens['officer_warangal']}"})
    assert res_off.status_code == 403
    assert "associated" in res_off.json()["detail"].lower()


def test_officer_cross_jurisdiction_and_empty_jurisdiction_denial(client: TestClient, setup_test_users):
    tokens, users = setup_test_users

    # Farmer creates conversation
    res = client.post("/api/v1/conversations/", json={"title": "Pest Outbreak", "crop": "Chilli", "language": "te"},
                      headers={"Authorization": f"Bearer {tokens['farmer1']}"})
    assert res.status_code == 200
    conv_id = res.json()["id"]

    # Escalate conversation to ticket in Geesugonda, Warangal
    esc_res = client.post(
        "/api/v1/tickets/escalate",
        json={
            "conversation_id": conv_id,
            "crop_name": "Chilli",
            "state": "Telangana",
            "district": "Warangal",
            "mandal": "Geesugonda",
            "village": "Dharmaram",
            "escalation_reason": "Severe mite webbing",
            "original_question_transcript": "మిరపలో ఆకు ముడత మరియు నల్లి సమస్య తీవ్రంగా ఉంది"
        },
        headers={"Authorization": f"Bearer {tokens['farmer1']}"}
    )
    assert esc_res.status_code == 200
    ticket_id = esc_res.json()["ticket_id"]

    # Officer with empty jurisdiction attempts to access conversation -> 403 Forbidden
    res_empty = client.get(f"/api/v1/conversations/{conv_id}", headers={"Authorization": f"Bearer {tokens['officer_empty']}"})
    assert res_empty.status_code == 403

    # Officer with empty jurisdiction attempts to access ticket -> 403 Forbidden
    res_empty_ticket = client.get(f"/api/v1/tickets/{ticket_id}", headers={"Authorization": f"Bearer {tokens['officer_empty']}"})
    assert res_empty_ticket.status_code == 403

    # Officer in Khammam (different district/mandal) attempts to access conversation -> 403 Forbidden
    res_khammam = client.get(f"/api/v1/conversations/{conv_id}", headers={"Authorization": f"Bearer {tokens['officer_khammam']}"})
    assert res_khammam.status_code == 403

    # Officer in Khammam attempts to access ticket -> 403 Forbidden
    res_khammam_ticket = client.get(f"/api/v1/tickets/{ticket_id}", headers={"Authorization": f"Bearer {tokens['officer_khammam']}"})
    assert res_khammam_ticket.status_code == 403

    # Authorized Officer (Warangal / Geesugonda) views conversation -> 200 OK
    res_auth = client.get(f"/api/v1/conversations/{conv_id}", headers={"Authorization": f"Bearer {tokens['officer_warangal']}"})
    assert res_auth.status_code == 200


def test_officer_reply_appears_in_original_farmer_conversation(client: TestClient, setup_test_users):
    tokens, users = setup_test_users

    # Farmer creates conversation
    res = client.post("/api/v1/conversations/", json={"title": "Paddy Stem Borer", "crop": "Rice", "language": "te"},
                      headers={"Authorization": f"Bearer {tokens['farmer1']}"})
    assert res.status_code == 200
    conv_id = res.json()["id"]

    # Escalate ticket
    esc_res = client.post(
        "/api/v1/tickets/escalate",
        json={
            "conversation_id": conv_id,
            "crop_name": "Rice",
            "state": "Telangana",
            "district": "Warangal",
            "mandal": "Geesugonda",
            "village": "Dharmaram",
            "escalation_reason": "Dead heart symptoms in paddy",
            "original_question_transcript": "వరిలో కాండం తొలుచు పురుగు నివారణకు సరైన మందు చెప్పండి"
        },
        headers={"Authorization": f"Bearer {tokens['farmer1']}"}
    )
    assert esc_res.status_code == 200
    ticket_id = esc_res.json()["ticket_id"]

    # Authorized officer responds to ticket
    prescription = "కార్టాప్ హైడ్రోక్లోరైడ్ 50% SP 2 గ్రాములు లీటరు నీటికి కలిపి పిచికారీ చేయండి."
    resp_res = client.post(
        f"/api/v1/tickets/{ticket_id}/respond",
        json={"prescription": prescription, "status": "RESOLVED"},
        headers={"Authorization": f"Bearer {tokens['officer_warangal']}"}
    )
    assert resp_res.status_code == 200

    # Farmer retrieves original conversation
    conv_res = client.get(f"/api/v1/conversations/{conv_id}", headers={"Authorization": f"Bearer {tokens['farmer1']}"})
    assert conv_res.status_code == 200
    conv_data = conv_res.json()
    assert conv_data["escalation_status"] == "RESOLVED"

    # Verify officer reply is present in messages
    officer_messages = [m for m in conv_data["messages"] if m["sender_type"] == "OFFICER"]
    assert len(officer_messages) >= 1
    assert prescription in officer_messages[0]["response"]
