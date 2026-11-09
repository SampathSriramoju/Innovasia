import io
import pytest
from starlette.testclient import TestClient
from app.database.models import User, AgriculturalTicket, FarmerProfile, ExtensionOfficer
from app.core.security import create_access_token

def test_admin_endpoint_authorization_enforcement(client: TestClient, farmer_token: str, officer_token: str, admin_token: str):
    # 1. Without any token -> 401 Unauthorized
    res_no_auth = client.get("/api/v1/auth/admin/pending-officers")
    assert res_no_auth.status_code == 401

    # 2. With farmer token -> 403 Forbidden
    res_farmer = client.get(
        "/api/v1/auth/admin/pending-officers",
        headers={"Authorization": f"Bearer {farmer_token}"}
    )
    assert res_farmer.status_code == 403

    # 3. With officer token -> 403 Forbidden
    res_officer = client.get(
        "/api/v1/auth/admin/pending-officers",
        headers={"Authorization": f"Bearer {officer_token}"}
    )
    assert res_officer.status_code == 403

    # 4. With admin token -> 200 OK
    res_admin = client.get(
        "/api/v1/auth/admin/pending-officers",
        headers={"Authorization": f"Bearer {admin_token}"}
    )
    assert res_admin.status_code == 200

def test_ticket_scope_idor_prevention(client: TestClient, db_session, farmer_token: str, admin_token: str):
    # Farmer 1
    farmer1 = User(
        id="user-farmer-01",
        phone_number="9876543201",
        full_name="Farmer One",
        role="farmer",
        is_active=True
    )
    # Farmer 2
    farmer2 = User(
        id="user-farmer-02",
        phone_number="9876543202",
        full_name="Farmer Two",
        role="farmer",
        is_active=True
    )
    db_session.add(farmer1)
    db_session.add(farmer2)
    db_session.commit()

    # Ticket owned by Farmer 2
    ticket = AgriculturalTicket(
        id="ticket-farmer2-secret",
        ticket_number="BK-2026-F2SC",
        farmer_id="user-farmer-02",
        state="Telangana",
        district="Warangal",
        mandal="Geesugonda",
        village="Dharmaram",
        status="PENDING",
        priority="high",
        escalation_reason="Pest infestation",
        original_question_transcript="పత్తిలో గులాబీ రంగు పురుగు తీవ్రత ఎక్కువగా ఉంది."
    )
    db_session.add(ticket)
    db_session.commit()

    # Farmer 1 tries to access Farmer 2's ticket -> HTTP 403 Forbidden
    farmer1_jwt = create_access_token(data={"sub": "user-farmer-01", "role": "farmer", "name": "Farmer One"})
    res = client.get(
        f"/api/v1/tickets/{ticket.id}",
        headers={"Authorization": f"Bearer {farmer1_jwt}"}
    )
    assert res.status_code == 403
    assert "access denied" in res.json()["detail"].lower()

    # Farmer 2 accesses their own ticket -> HTTP 200 OK
    farmer2_jwt = create_access_token(data={"sub": "user-farmer-02", "role": "farmer", "name": "Farmer Two"})
    res2 = client.get(
        f"/api/v1/tickets/{ticket.id}",
        headers={"Authorization": f"Bearer {farmer2_jwt}"}
    )
    assert res2.status_code == 200
    assert res2.json()["ticket_number"] == "BK-2026-F2SC"

    # Admin accesses the ticket -> HTTP 200 OK
    res_admin = client.get(
        f"/api/v1/tickets/{ticket.id}",
        headers={"Authorization": f"Bearer {admin_token}"}
    )
    assert res_admin.status_code == 200

def test_extension_officer_mandal_jurisdiction(client: TestClient, db_session):
    # Setup farmer and ticket in Mandal 'Geesugonda'
    farmer = User(
        id="user-farmer-03",
        phone_number="9876543203",
        full_name="Farmer Three",
        role="farmer",
        is_active=True
    )
    # Setup out-of-bounds officer in Khammam
    user_khammam = User(
        id="user-officer-khammam",
        phone_number="9876543204",
        full_name="Officer Khammam",
        role="extension_officer",
        is_active=True
    )
    officer_khammam = ExtensionOfficer(
        id="officer-khammam-01",
        user_id=user_khammam.id,
        officer_code="AGRI-TS-KHM",
        designation="MAO",
        department="Agri",
        assigned_state="Telangana",
        assigned_district="Khammam",
        assigned_mandals=["Kusumanchi", "Tirumalayapalem"],
        approval_status="APPROVED"
    )
    # Setup in-bounds officer in Geesugonda
    user_geesu = User(
        id="user-officer-geesugonda",
        phone_number="9876543205",
        full_name="Officer Geesugonda",
        role="extension_officer",
        is_active=True
    )
    officer_geesu = ExtensionOfficer(
        id="officer-geesu-01",
        user_id=user_geesu.id,
        officer_code="AGRI-TS-GEESU",
        designation="MAO",
        department="Agri",
        assigned_state="Telangana",
        assigned_district="Warangal",
        assigned_mandals=["Geesugonda", "Wardhannapet"],
        approval_status="APPROVED"
    )
    db_session.add(farmer)
    db_session.add(user_khammam)
    db_session.add(officer_khammam)
    db_session.add(user_geesu)
    db_session.add(officer_geesu)
    db_session.commit()

    ticket_geesu = AgriculturalTicket(
        id="ticket-mandal-geesu",
        ticket_number="BK-2026-GEES",
        farmer_id="user-farmer-03",
        state="Telangana",
        district="Warangal",
        mandal="Geesugonda",
        village="Dharmaram",
        status="PENDING",
        priority="high",
        escalation_reason="Cotton wilt",
        original_question_transcript="పత్తి ఆకులు రాలిపోతున్నాయి"
    )
    db_session.add(ticket_geesu)
    db_session.commit()

    # Officer from 'Khammam / Kusumanchi' (No jurisdiction in Geesugonda)
    officer_out_of_bounds_jwt = create_access_token(data={
        "sub": "user-officer-khammam",
        "role": "extension_officer",
        "district": "Khammam",
        "mandals": ["Kusumanchi", "Tirumalayapalem"]
    })

    # Out-of-bounds officer attempts to read ticket -> HTTP 403 Forbidden
    res_read = client.get(
        f"/api/v1/tickets/{ticket_geesu.id}",
        headers={"Authorization": f"Bearer {officer_out_of_bounds_jwt}"}
    )
    assert res_read.status_code == 403
    assert "jurisdiction" in res_read.json()["detail"].lower() or "assigned mandals" in res_read.json()["detail"].lower()

    # Out-of-bounds officer attempts to respond to ticket -> HTTP 403 Forbidden
    res_respond = client.post(
        f"/api/v1/tickets/{ticket_geesu.id}/respond",
        headers={"Authorization": f"Bearer {officer_out_of_bounds_jwt}"},
        json={
            "prescription": "Apply Carbendazim 50% WP @ 1g/L",
            "action_type": "chemical",
            "status": "resolved"
        }
    )
    assert res_respond.status_code == 403
    assert "assigned mandals" in res_respond.json()["detail"].lower()

    # In-jurisdiction officer (covers Geesugonda)
    officer_in_bounds_jwt = create_access_token(data={
        "sub": "user-officer-geesugonda",
        "role": "extension_officer",
        "district": "Warangal",
        "mandals": ["Geesugonda", "Wardhannapet"]
    })

    # In-jurisdiction officer reads ticket -> HTTP 200 OK
    res_read_ok = client.get(
        f"/api/v1/tickets/{ticket_geesu.id}",
        headers={"Authorization": f"Bearer {officer_in_bounds_jwt}"}
    )
    assert res_read_ok.status_code == 200

    # In-jurisdiction officer responds to ticket -> HTTP 200 OK
    res_resp_ok = client.post(
        f"/api/v1/tickets/{ticket_geesu.id}/respond",
        headers={"Authorization": f"Bearer {officer_in_bounds_jwt}"},
        json={
            "prescription": "Drench roots with Copper Oxychloride 50% WP @ 3g/L. Ensure proper soil drainage.",
            "action_type": "chemical",
            "status": "resolved"
        }
    )
    assert res_resp_ok.status_code == 200
    assert res_resp_ok.json()["status"] == "resolved"

def test_disease_upload_security_protections(client: TestClient, farmer_token: str):
    # 1. Reject non-image file (e.g. .exe / shell script masquerading as image)
    fake_exe_bytes = b"MZ\x90\x00\x03\x00\x00\x00\x04\x00\x00\x00\xff\xff\x00\x00echo exploit"
    res_bad_format = client.post(
        "/api/v1/disease/diagnose",
        headers={"Authorization": f"Bearer {farmer_token}"},
        files={"file": ("malicious.exe", fake_exe_bytes, "application/octet-stream")},
        data={"crop_name": "Cotton"}
    )
    assert res_bad_format.status_code == 400
    assert "invalid image format" in res_bad_format.json()["detail"].lower()

    # 2. Reject oversized image payload (>10MB) -> HTTP 413
    oversized_jpeg = b"\xff\xd8\xff\xe0" + b"\x00" * (11 * 1024 * 1024)
    res_oversized = client.post(
        "/api/v1/disease/diagnose",
        headers={"Authorization": f"Bearer {farmer_token}"},
        files={"file": ("giant_leaf.jpg", oversized_jpeg, "image/jpeg")},
        data={"crop_name": "Cotton"}
    )
    assert res_oversized.status_code == 413
    assert "exceeds maximum allowed limit" in res_oversized.json()["detail"].lower()
