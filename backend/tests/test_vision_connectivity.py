import io
import pytest
from datetime import timedelta
from PIL import Image, ImageDraw
from fastapi.testclient import TestClient

from app.core.security import create_access_token
from app.ai.gemini_service import gemini_service
from app.database.models import User, AgriculturalTicket


def create_sample_leaf_bytes() -> bytes:
    img = Image.new("RGB", (300, 300), color=(40, 140, 50))
    draw = ImageDraw.Draw(img)
    draw.line([150, 10, 150, 290], fill=(20, 80, 25), width=3)
    draw.line([150, 80, 80, 140], fill=(25, 90, 30), width=2)
    draw.line([150, 150, 220, 210], fill=(25, 90, 30), width=2)
    draw.ellipse([80, 100, 140, 160], fill=(90, 50, 20))
    buf = io.BytesIO()
    img.save(buf, format="JPEG")
    return buf.getvalue()


def test_health_endpoint_reports_discrete_services(client: TestClient):
    """
    Verify health endpoint returns discrete connectivity statuses:
    - backend_api: connected
    - database: connected
    - ai_vision: available / unavailable
    - gemini_text: available / unavailable
    - gemini_vision_available: boolean flag
    """
    res = client.get("/health")
    assert res.status_code == 200
    data = res.json()

    assert data["backend_api"] == "connected"
    assert data["database"]["status"] == "connected"
    assert "ai_vision" in data
    assert "gemini_text" in data
    assert "gemini_vision_available" in data
    assert isinstance(data["gemini_vision_available"], bool)
    assert data["ai_provider"]["gemini_vision_available"] == data["gemini_vision_available"]
    assert data["ai_provider"]["gemini_text_available"] == (data["gemini_text"] == "available")


def test_health_endpoint_simulated_vision_unavailable(client: TestClient):
    """
    Verify health endpoint specifically flags vision as unavailable when vision is disabled or unreachable,
    without implying text connectivity is broken.
    """
    orig_vision_enabled = getattr(gemini_service, "vision_enabled", True)
    try:
        gemini_service.vision_enabled = False

        res = client.get("/api/v1/health")
        assert res.status_code == 200
        data = res.json()

        assert data["backend_api"] == "connected"
        assert data["ai_vision"] == "unavailable"
        assert data["gemini_vision_available"] is False
        assert data["ai_provider"]["gemini_vision_available"] is False
    finally:
        gemini_service.vision_enabled = orig_vision_enabled


def test_diagnose_vision_unavailable_simulation_returns_explicit_ticket(client: TestClient, farmer_token: str, db_session):
    """
    Verify that when Gemini Vision is unavailable:
    1. Returns structured response with status: "vision_unavailable" (HTTP 200)
    2. Creates an explicit AgriculturalTicket in DB with ticket_id & ticket_number
    3. NEVER returns an unrelated disease or fabricated claim
    4. Includes clear escalation guidance in farmer's language
    """
    orig_vision_enabled = getattr(gemini_service, "vision_enabled", True)
    try:
        gemini_service.vision_enabled = False

        leaf_bytes = create_sample_leaf_bytes()
        res = client.post(
            "/api/v1/disease/diagnose",
            headers={"Authorization": f"Bearer {farmer_token}"},
            files={"file": ("leaf.jpg", io.BytesIO(leaf_bytes), "image/jpeg")},
            data={
                "crop_name": "Cotton",
                "farmer_notes": "Leaf shows small spots. Is this bacterial blight?",
                "language": "en"
            }
        )

        assert res.status_code == 200
        data = res.json()
        assert data["status"] == "vision_unavailable"
        assert data["is_escalated"] is True
        assert data["confidence"] == 0.0
        assert "ticket_number" in data
        assert data["ticket_number"].startswith("ESC-")
        assert "ticket_id" in data
        assert "Vision" in data["disease_name"] or "Unavailable" in data["disease_name"]

        # Confirm the escalation ticket is persisted in database
        ticket = db_session.query(AgriculturalTicket).filter(
            AgriculturalTicket.ticket_number == data["ticket_number"]
        ).first()
        assert ticket is not None
        assert ticket.status == "open"
        assert ticket.priority == "high"
        assert "AI Vision" in ticket.escalation_reason or "Unavailable" in ticket.escalation_reason

    finally:
        gemini_service.vision_enabled = orig_vision_enabled


def test_diagnose_vision_unavailable_prefer_503(client: TestClient, farmer_token: str):
    """
    Verify that when client requests 503 via header or form parameter,
    the endpoint returns HTTP 503 with the exact same structured vision_unavailable payload.
    """
    orig_vision_enabled = getattr(gemini_service, "vision_enabled", True)
    try:
        gemini_service.vision_enabled = False

        leaf_bytes = create_sample_leaf_bytes()
        res = client.post(
            "/api/v1/disease/diagnose",
            headers={
                "Authorization": f"Bearer {farmer_token}",
                "X-Expect-Status": "503"
            },
            files={"file": ("leaf.jpg", io.BytesIO(leaf_bytes), "image/jpeg")},
            data={"crop_name": "Tomato", "language": "en"}
        )

        assert res.status_code == 503
        data = res.json()
        assert data["status"] == "vision_unavailable"
        assert data["is_escalated"] is True
        assert "ticket_number" in data
        assert data["ticket_number"].startswith("ESC-")
    finally:
        gemini_service.vision_enabled = orig_vision_enabled


def test_diagnose_unauthenticated_returns_401(client: TestClient):
    """
    Verify that unauthenticated requests to /diagnose return 401 with clear explanation,
    preventing unauthenticated or spoofed association.
    """
    leaf_bytes = create_sample_leaf_bytes()
    res = client.post(
        "/api/v1/disease/diagnose",
        files={"file": ("leaf.jpg", io.BytesIO(leaf_bytes), "image/jpeg")},
        data={"crop_name": "Cotton"}
    )
    assert res.status_code == 401
    assert "Authentication token is missing" in res.json()["detail"]


def test_diagnose_expired_token_returns_401(client: TestClient):
    """
    Verify that expired JWT access tokens return 401 Unauthorized, explaining why
    Android clients with stale sessions might drop to offline fallback mode.
    """
    expired_token = create_access_token(
        data={"sub": "user-test-farmer-01", "role": "farmer", "lang": "te"},
        expires_delta=timedelta(seconds=-30)  # Expired 30 seconds ago
    )
    leaf_bytes = create_sample_leaf_bytes()
    res = client.post(
        "/api/v1/disease/diagnose",
        headers={"Authorization": f"Bearer {expired_token}"},
        files={"file": ("leaf.jpg", io.BytesIO(leaf_bytes), "image/jpeg")},
        data={"crop_name": "Cotton"}
    )
    assert res.status_code == 401
    assert "Invalid or expired" in res.json()["detail"]


def test_diagnose_never_returns_unrelated_disease_on_timeout(client: TestClient, farmer_token: str, monkeypatch):
    """
    Verify that when Gemini Vision inference throws a timeout error:
    The endpoint safely falls back to status: 'vision_unavailable' with an explicit ticket ID,
    and NEVER hallucinates an unrelated disease.
    """
    import asyncio
    async def mock_timeout(*args, **kwargs):
        raise asyncio.TimeoutError("Upstream model connection timed out")

    monkeypatch.setattr(gemini_service, "analyze_crop_image", mock_timeout)

    leaf_bytes = create_sample_leaf_bytes()
    res = client.post(
        "/api/v1/disease/diagnose",
        headers={"Authorization": f"Bearer {farmer_token}"},
        files={"file": ("cotton_leaf.jpg", io.BytesIO(leaf_bytes), "image/jpeg")},
        data={
            "crop_name": "Cotton",
            "farmer_notes": "Should I apply fungicide for rice blast?", # misleading question
            "language": "te"
        }
    )

    assert res.status_code == 200
    data = res.json()
    assert data["status"] == "vision_unavailable"
    assert data["is_escalated"] is True
    # Ensure unrelated disease from farmer's text was NOT hallucinated
    assert "blast" not in data["disease_name"].lower()
    assert "అగ్గి" not in data["disease_name"]
    assert "ticket_number" in data
    assert data["ticket_number"].startswith("ESC-")
