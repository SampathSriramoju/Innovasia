import pytest
from datetime import datetime, timezone, timedelta
from app.core.security import create_access_token
from app.database.models import User, ExtensionOfficer, AgriculturalTicket

def test_unrequested_phone_verification_rejected(client):
    """
    Verify that an attacker cannot use universal OTP '123456' on an unrequested phone number.
    Must return 400 Bad Request.
    """
    res = client.post("/api/v1/auth/verify-otp", json={
        "phone_number": "9999888877",
        "otp": "123456"
    })
    assert res.status_code == 400
    assert "No active OTP request found" in res.json()["detail"]

def test_account_takeover_rejected_on_wrong_otp(client):
    """
    Request OTP for a farmer, then verify with incorrect OTP.
    Must return 400 Bad Request.
    """
    req = client.post("/api/v1/auth/request-otp", json={"phone_number": "9876543210"})
    assert req.status_code == 200

    res = client.post("/api/v1/auth/verify-otp", json={
        "phone_number": "9876543210",
        "otp": "000000"
    })
    assert res.status_code == 400
    assert "Invalid OTP code" in res.json()["detail"]

def test_arbitrary_phone_registration_without_otp_rejected(client):
    """
    Attempt to register a farmer with an arbitrary phone number without providing a valid OTP.
    Must return 401 Unauthorized.
    """
    res = client.post("/api/v1/auth/register-farmer", json={
        "phone_number": "9988776655",
        "full_name": "Attacker Farmer",
        "state": "Telangana",
        "district": "Warangal",
        "mandal": "Geesugonda",
        "village": "Dharmaram"
    })
    assert res.status_code == 401
    assert "Genuine OTP verification is required" in res.json()["detail"]

def test_invalid_and_expired_tokens_rejected(client):
    """
    Test that invalid signatures and expired tokens are rejected with 401 Unauthorized.
    """
    # 1. Tampered token
    tampered_headers = {"Authorization": "Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.tampered.signature"}
    res1 = client.get("/api/v1/auth/me", headers=tampered_headers)
    assert res1.status_code == 401
    assert "Invalid or expired" in res1.json()["detail"]

    # 2. Expired token
    expired_token = create_access_token(
        data={"sub": "user-test-farmer-01", "role": "farmer"},
        expires_delta=timedelta(seconds=-60)
    )
    res2 = client.get("/api/v1/auth/me", headers={"Authorization": f"Bearer {expired_token}"})
    assert res2.status_code == 401
    assert "Invalid or expired" in res2.json()["detail"]

def test_disease_diagnosis_requires_authentication(client):
    """
    Verify that unauthenticated disease diagnosis requests are rejected with 401 Unauthorized
    and never associated with the first farmer in the database.
    """
    import io
    dummy_file = io.BytesIO(b"not an image but testing auth first")
    res = client.post(
        "/api/v1/disease/diagnose",
        files={"file": ("leaf.jpg", dummy_file, "image/jpeg")},
        data={"crop_name": "Cotton"}
    )
    assert res.status_code == 401
    assert "Authentication token is missing" in res.json()["detail"]

def test_officer_empty_jurisdiction_denies_ticket_access(client, db_session):
    """
    Test that an extension officer with an empty jurisdiction (assigned_mandals = [])
    is denied access to view or respond to tickets rather than granting access to all tickets.
    """
    # Create an approved officer with empty mandals
    user = User(
        id="officer-empty-mandal-user",
        phone_number="9111222333",
        full_name="Officer No Mandals",
        role="extension_officer",
        preferred_lang="te",
        is_active=True,
        is_verified=True
    )
    officer = ExtensionOfficer(
        id="officer-empty-mandal",
        user_id=user.id,
        officer_code="AGRI-TS-EMPTY",
        designation="MAO",
        department="Agri",
        assigned_state="Telangana",
        assigned_district="Warangal",
        assigned_mandals=[], # Empty jurisdiction
        approval_status="APPROVED"
    )
    db_session.add(user)
    db_session.add(officer)
    db_session.commit()

    token = create_access_token(data={
        "sub": user.id,
        "officer_code": "AGRI-TS-EMPTY",
        "role": "extension_officer",
        "mandals": []
    })

    # Listing tickets should return empty list (0 tickets), not all tickets
    res = client.get("/api/v1/tickets/", headers={"Authorization": f"Bearer {token}"})
    assert res.status_code == 200
    assert len(res.json()) == 0

def test_revoked_officer_token_rejected_by_active_user_revalidation(client, db_session):
    """
    Test that if an extension officer's approval status is set to REJECTED,
    even an active unexpired token is rejected with 403 Forbidden.
    """
    user = User(
        id="officer-revoked-user",
        phone_number="9444555666",
        full_name="Revoked Officer",
        role="extension_officer",
        preferred_lang="te",
        is_active=True,
        is_verified=True
    )
    officer = ExtensionOfficer(
        id="officer-revoked",
        user_id=user.id,
        officer_code="AGRI-TS-REVOKED",
        designation="MAO",
        department="Agri",
        assigned_state="Telangana",
        assigned_district="Warangal",
        assigned_mandals=["Geesugonda"],
        approval_status="REJECTED" # Revoked
    )
    db_session.add(user)
    db_session.add(officer)
    db_session.commit()

    token = create_access_token(data={
        "sub": user.id,
        "officer_code": "AGRI-TS-REVOKED",
        "role": "extension_officer",
        "mandals": ["Geesugonda"]
    })

    # Attempting to access tickets must return 403 Forbidden
    res = client.get("/api/v1/tickets/", headers={"Authorization": f"Bearer {token}"})
    assert res.status_code == 403
    assert "not approved or has been revoked" in res.json()["detail"]

def test_officer_cannot_respond_to_ticket_outside_jurisdiction(client, db_session, farmer_token):
    """
    Test that an extension officer cannot respond to a ticket outside their assigned mandals.
    """
    # 1. Create a farmer ticket in Atmakur mandal
    ticket = AgriculturalTicket(
        ticket_number="BK-TEST-ATMAKUR",
        farmer_id="user-test-farmer-01",
        state="Telangana",
        district="Warangal",
        mandal="Atmakur",
        village="Atmakur Village",
        status="PENDING",
        priority="high",
        escalation_reason="Test outside mandal",
        original_question_transcript="Cotton pest question"
    )
    db_session.add(ticket)
    db_session.commit()

    # 2. Create officer whose jurisdiction is strictly Geesugonda
    user = User(
        id="officer-geesugonda-user",
        phone_number="9777888999",
        full_name="Officer Geesugonda Only",
        role="extension_officer",
        preferred_lang="te",
        is_active=True,
        is_verified=True
    )
    officer = ExtensionOfficer(
        id="officer-geesugonda",
        user_id=user.id,
        officer_code="AGRI-TS-GEESUGONDA",
        designation="MAO",
        department="Agri",
        assigned_state="Telangana",
        assigned_district="Warangal",
        assigned_mandals=["Geesugonda"], # NOT Atmakur
        approval_status="APPROVED"
    )
    db_session.add(user)
    db_session.add(officer)
    db_session.commit()

    officer_token = create_access_token(data={
        "sub": user.id,
        "officer_code": "AGRI-TS-GEESUGONDA",
        "role": "extension_officer",
        "mandals": ["Geesugonda"]
    })

    # Responding to Atmakur ticket must be denied with 403 Forbidden
    res = client.post(
        f"/api/v1/tickets/{ticket.id}/respond",
        headers={"Authorization": f"Bearer {officer_token}"},
        json={"prescription": "Apply pesticide", "status": "resolved"}
    )
    assert res.status_code == 403
    assert "Extension officers can only respond to tickets within their assigned mandals" in res.json()["detail"]
