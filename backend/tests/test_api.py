import pytest

def test_health_check(client):
    res = client.get("/health")
    assert res.status_code == 200
    data = res.json()
    assert data["status"] == "healthy"
    assert data["online_model"] == "gemini-3.8-flash"

def test_crops_endpoint(client):
    res = client.get("/api/v1/crops/")
    assert res.status_code == 200
    crops = res.json()
    assert len(crops) >= 4
    names = [c["name_en"] for c in crops]
    assert "Cotton" in names
    assert "Rice / Paddy" in names

def test_voice_query_safe_verified(client, farmer_token):
    res = client.post(
        "/api/v1/voice/query",
        headers={"Authorization": f"Bearer {farmer_token}"},
        json={
            "query_text": "What pesticide and dose should I spray for pink bollworm in cotton?",
            "crop_name": "Cotton",
            "pest_or_disease": "Pink Bollworm",
            "chemical_mentioned": "Proclaim",
            "language": "te"
        }
    )
    assert res.status_code == 200
    data = res.json()
    assert data["status"] == "answered"
    assert data["is_safe"] is True
    assert "88" in data["response_text"]  # Verified CIBRC dosage per acre (88 g / 88 గ్రాములు)
    assert ("CIBRC" in data["response_text"] or "సిఐబిఆర్సి" in data["response_text"])

def test_voice_query_unapproved_chemical_auto_escalates(client, farmer_token):
    res = client.post(
        "/api/v1/voice/query",
        headers={"Authorization": f"Bearer {farmer_token}"},
        json={
            "query_text": "Can I spray 2 liters of unbranded chemical for unknown worm in cotton?",
            "crop_name": "Cotton",
            "pest_or_disease": "unknown worm",
            "chemical_mentioned": "unbranded toxic chemical",
            "language": "te"
        }
    )
    assert res.status_code == 200
    data = res.json()
    assert data["status"] == "escalated"
    assert data["is_safe"] is False
    assert "ticket_number" in data
    assert data["ticket_number"].startswith("ESC-")
    assert "భద్రతా హెచ్చరిక" in data["response_text"] # Localized Telugu safety warning

def test_ticket_lifecycle_and_resolution(client, farmer_token, officer_token):
    # 1. Farmer creates ticket
    create_res = client.post(
        "/api/v1/tickets/",
        headers={"Authorization": f"Bearer {farmer_token}"},
        json={
            "crop_name": "Cotton",
            "mandal": "Geesugonda",
            "escalation_reason": "Leaves turning completely reddish yellow with white flies",
            "original_question_transcript": "ఆకులు ఎర్రగా మారి రాలిపోతున్నాయి, ఏం చేయాలి?",
            "priority": "high"
        }
    )
    assert create_res.status_code == 200
    ticket_id = create_res.json()["ticket_id"]
    ticket_num = create_res.json()["ticket_number"]

    # 2. Officer views ticket queue
    officer_list_res = client.get(
        "/api/v1/tickets/",
        headers={"Authorization": f"Bearer {officer_token}"}
    )
    assert officer_list_res.status_code == 200
    tickets = officer_list_res.json()
    ticket_numbers = [t["ticket_number"] for t in tickets]
    assert ticket_num in ticket_numbers

    # 3. Officer resolves ticket with verified prescription
    resolve_res = client.post(
        f"/api/v1/tickets/{ticket_id}/resolve",
        headers={"Authorization": f"Bearer {officer_token}"},
        json={
            "prescription": "Spray Diafenthiuron 50% WP at 240g/acre diluted in 200L water. Install 5 yellow sticky traps per acre.",
            "action_type": "chemical"
        }
    )
    assert resolve_res.status_code == 200
    assert resolve_res.json()["status"] == "resolved"

    # 4. Farmer verifies ticket status is now resolved
    detail_res = client.get(
        f"/api/v1/tickets/{ticket_id}",
        headers={"Authorization": f"Bearer {farmer_token}"}
    )
    assert detail_res.status_code == 200
    assert detail_res.json()["status"] == "resolved"
    assert "Diafenthiuron" in detail_res.json()["resolution_prescription"]
