import pytest
from app.core.security import create_access_token, decode_access_token

def test_request_otp(client):
    response = client.post("/api/v1/auth/request-otp", json={"phone_number": "9876543210"})
    assert response.status_code == 200
    data = response.json()
    assert data["status"] == "success"
    assert "dev_otp" in data

def test_verify_otp_and_login_farmer(client):
    req = client.post("/api/v1/auth/request-otp", json={"phone_number": "9876543210"})
    otp = req.json()["dev_otp"]

    verify_res = client.post("/api/v1/auth/verify-otp", json={
        "phone_number": "9876543210",
        "otp": otp,
        "preferred_lang": "te",
        "full_name": "Ramu Farmer"
    })
    assert verify_res.status_code == 200
    data = verify_res.json()
    assert "access_token" in data
    assert data["user"]["role"] == "farmer"
    assert data["user"]["preferred_lang"] == "te"

def test_farmer_cannot_self_assign_officer_role(client):
    req = client.post("/api/v1/auth/request-otp", json={"phone_number": "9998887776"})
    otp = req.json()["dev_otp"]

    # Even if malicious client passes role in payload, verify-otp strictly forces role='farmer'
    verify_res = client.post("/api/v1/auth/verify-otp", json={
        "phone_number": "9998887776",
        "otp": otp,
        "role": "admin" # Attempted elevation
    })
    assert verify_res.status_code == 200
    assert verify_res.json()["user"]["role"] == "farmer"

def test_officer_login_flow(client):
    res = client.post("/api/v1/auth/officer-login", json={
        "officer_code": "AGRI-TS-042",
        "phone_number": "9123456789",
        "otp": "654321"
    })
    assert res.status_code == 200
    data = res.json()
    assert "access_token" in data
    assert data["officer"]["officer_code"] == "AGRI-TS-042"
    assert "Geesugonda" in data["officer"]["assigned_mandals"]

def test_protected_me_endpoint(client):
    req = client.post("/api/v1/auth/request-otp", json={"phone_number": "9876543210"})
    otp = req.json()["dev_otp"]
    login_res = client.post("/api/v1/auth/verify-otp", json={
        "phone_number": "9876543210",
        "otp": otp,
        "full_name": "Ramu Farmer"
    })
    token = login_res.json()["access_token"]

    res = client.get("/api/v1/auth/me", headers={"Authorization": f"Bearer {token}"})
    assert res.status_code == 200
    data = res.json()
    assert data["role"] == "farmer"
    assert data["phone_number"] == "9876543210"
    assert data["profile"]["mandal"] == "Geesugonda"
