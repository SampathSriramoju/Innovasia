import pytest
from starlette.testclient import TestClient
from app.core.security import create_access_token
from app.database.models import User, FarmerProfile, AgriculturalTicket

@pytest.fixture
def auth_farmer_token(db_session):
    # Create test farmer in isolated test database session
    farmer = db_session.query(User).filter(User.phone_number == "9888877771").first()
    if not farmer:
        farmer = User(
            phone_number="9888877771",
            full_name="Ramesh Kumar",
            role="farmer",
            preferred_lang="te",
            is_active=True,
            is_verified=True
        )
        db_session.add(farmer)
        db_session.commit()
        db_session.refresh(farmer)

        profile = FarmerProfile(
            user_id=farmer.id,
            state="Telangana",
            district="Warangal",
            mandal="Geesugonda",
            village="Dharmaram",
            land_size_acres=4.0
        )
        db_session.add(profile)
        db_session.commit()

    farmer_id = farmer.id

    token = create_access_token(data={
        "sub": farmer_id,
        "phone": "9888877771",
        "name": "Ramesh Kumar",
        "role": "farmer",
        "lang": "te"
    })
    return token, farmer_id


def test_batch_offline_sync_text_tickets(client: TestClient, auth_farmer_token):
    token, farmer_id = auth_farmer_token
    headers = {"Authorization": f"Bearer {token}"}

    payload = {
        "pending_tickets": [
            {
                "client_uuid": "sync-test-uuid-001",
                "crop_name": "Cotton",
                "question_text": "ఆకుమచ్చ తెగులు లక్షణాలు కనిపిస్తున్నాయి, ఏమి చేయాలి?",
                "image_url": None,
                "language": "te",
                "created_at_epoch": 1700000000
            },
            {
                "client_uuid": "sync-test-uuid-002",
                "crop_name": "Chilli",
                "question_text": "మిరపలో ఆకు ముడత నివారణకు సిఫార్సు ఏమిటి?",
                "image_url": None,
                "language": "te",
                "created_at_epoch": 1700000100
            }
        ]
    }

    res = client.post("/api/v1/tickets/offline-sync", json=payload, headers=headers)
    assert res.status_code == 200
    data = res.json()
    assert data["status"] == "synced"
    assert data["synced_count"] == 2
    assert "sync-test-uuid-001" in data["synced_client_uuids"]
    assert "sync-test-uuid-002" in data["synced_client_uuids"]
    assert len(data["tickets"]) == 2


def test_offline_sync_with_image_url(client: TestClient, db_session, auth_farmer_token):
    token, farmer_id = auth_farmer_token
    headers = {"Authorization": f"Bearer {token}"}

    payload = {
        "pending_tickets": [
            {
                "client_uuid": "sync-img-uuid-003",
                "crop_name": "Rice",
                "question_text": "వరి ఆకుపై మచ్చలు కనిపించాయి ఫోటో జతపరచబడింది",
                "image_url": "/api/v1/disease/images/leaf_sample_001.jpg",
                "language": "te",
                "created_at_epoch": 1700000200
            }
        ]
    }

    res = client.post("/api/v1/tickets/offline-sync", json=payload, headers=headers)
    assert res.status_code == 200
    data = res.json()
    assert data["synced_count"] == 1
    assert "sync-img-uuid-003" in data["synced_client_uuids"]
    ticket_id = data["tickets"][0]["ticket_id"]

    # Verify in DB that image_url was stored
    ticket = db_session.query(AgriculturalTicket).filter(AgriculturalTicket.id == ticket_id).first()
    assert ticket is not None
    assert "/api/v1/disease/images/leaf_sample_001.jpg" in ticket.attached_image_urls


def test_offline_sync_idempotency_duplicate_requests(client: TestClient, db_session, auth_farmer_token):
    token, farmer_id = auth_farmer_token
    headers = {"Authorization": f"Bearer {token}"}

    payload = {
        "pending_tickets": [
            {
                "client_uuid": "sync-idemp-uuid-004",
                "crop_name": "Tomato",
                "question_text": "టమోటా కాయ కుళ్లు తెగులు నివారణ",
                "image_url": None,
                "language": "te",
                "created_at_epoch": 1700000300
            }
        ]
    }

    # First sync
    res1 = client.post("/api/v1/tickets/offline-sync", json=payload, headers=headers)
    assert res1.status_code == 200
    data1 = res1.json()
    ticket_id1 = data1["tickets"][0]["ticket_id"]
    ticket_number1 = data1["tickets"][0]["ticket_number"]

    # Duplicate / Retry sync with exact same client_uuid
    res2 = client.post("/api/v1/tickets/offline-sync", json=payload, headers=headers)
    assert res2.status_code == 200
    data2 = res2.json()
    assert data2["synced_count"] == 1
    assert "sync-idemp-uuid-004" in data2["synced_client_uuids"]
    ticket_id2 = data2["tickets"][0]["ticket_id"]
    ticket_number2 = data2["tickets"][0]["ticket_number"]

    # Must be identical ticket, no duplicate ticket created in DB
    assert ticket_id1 == ticket_id2
    assert ticket_number1 == ticket_number2

    matching_tickets = db_session.query(AgriculturalTicket).filter(
        AgriculturalTicket.farmer_id == farmer_id,
        AgriculturalTicket.escalation_reason.contains("sync-idemp-uuid-004")
    ).all()
    assert len(matching_tickets) == 1
