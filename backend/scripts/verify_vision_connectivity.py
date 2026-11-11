import sys
import io
import json
from pathlib import Path
from datetime import timedelta
from PIL import Image, ImageDraw

# Add backend directory to path
backend_dir = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(backend_dir))

from fastapi.testclient import TestClient
from app.main import app
from app.core.security import create_access_token
from app.ai.gemini_service import gemini_service
from app.database.session import SessionLocal
from app.database.models import User, AgriculturalTicket

client = TestClient(app)

def create_test_leaf() -> bytes:
    img = Image.new("RGB", (300, 300), color=(45, 135, 55))
    draw = ImageDraw.Draw(img)
    draw.line([150, 10, 150, 290], fill=(25, 85, 30), width=3)
    draw.line([150, 80, 80, 140], fill=(25, 90, 30), width=2)
    draw.line([150, 150, 220, 210], fill=(25, 90, 30), width=2)
    draw.ellipse([90, 110, 140, 160], fill=(95, 55, 20))
    buf = io.BytesIO()
    img.save(buf, format="JPEG")
    return buf.getvalue()

def run_verification():
    print("=" * 80)
    print("BOL KISAN — VISION CONNECTIVITY & DIAGNOSIS VERIFICATION")
    print("=" * 80)

    db = SessionLocal()
    farmer = db.query(User).filter(User.role == "farmer").first()
    farmer_id = farmer.id if farmer else "user-test-farmer-01"
    token = create_access_token(data={"sub": farmer_id, "role": "farmer", "lang": "te"})
    auth_headers = {"Authorization": f"Bearer {token}"}
    leaf_bytes = create_test_leaf()

    # 1. Health Endpoint Verification
    print("\n[TEST 1] Health Endpoint Connectivity Status...")
    res = client.get("/health")
    assert res.status_code == 200, f"Expected 200, got {res.status_code}"
    h = res.json()
    print(f"  • backend_api: {h.get('backend_api')}")
    print(f"  • database: {h.get('database', {}).get('status')}")
    print(f"  • ai_vision: {h.get('ai_vision')}")
    print(f"  • gemini_text: {h.get('gemini_text')}")
    print(f"  • gemini_vision_available: {h.get('gemini_vision_available')}")
    assert h.get("backend_api") == "connected"
    assert "gemini_vision_available" in h
    print("  -> PASS: Health endpoint successfully reports discrete service statuses.")

    # 2. Online Success
    print("\n[TEST 2] Online Vision Diagnosis...")
    res = client.post(
        "/api/v1/disease/diagnose",
        headers=auth_headers,
        files={"file": ("leaf.jpg", io.BytesIO(leaf_bytes), "image/jpeg")},
        data={"crop_name": "Cotton", "plant_part": "leaf", "farmer_notes": "Small spots on cotton leaf"}
    )
    assert res.status_code == 200, f"Expected 200, got {res.status_code}"
    diag = res.json()
    print(f"  • HTTP Status: {res.status_code}")
    print(f"  • Status: {diag.get('status')}")
    print(f"  • Disease Name: {diag.get('disease_name')}")
    print(f"  • Confidence: {diag.get('confidence')}")
    print(f"  • Citations: {diag.get('citations')[:2] if diag.get('citations') else []}")
    print("  -> PASS: Online diagnosis endpoint responds with structured payload.")

    # 3. Vision Unavailable Simulation
    print("\n[TEST 3] Vision Unavailable Simulation...")
    orig_vision_enabled = getattr(gemini_service, "vision_enabled", True)
    try:
        gemini_service.vision_enabled = False
        res = client.post(
            "/api/v1/disease/diagnose",
            headers=auth_headers,
            files={"file": ("leaf.jpg", io.BytesIO(leaf_bytes), "image/jpeg")},
            data={"crop_name": "Cotton", "language": "en"}
        )
        assert res.status_code == 200, f"Expected 200, got {res.status_code}"
        vu_data = res.json()
        print(f"  • HTTP Status: {res.status_code}")
        print(f"  • Status: {vu_data.get('status')}")
        print(f"  • Disease Name: {vu_data.get('disease_name')}")
        print(f"  • Escalated: {vu_data.get('is_escalated')}")
        print(f"  • Ticket Number: {vu_data.get('ticket_number')}")
        print(f"  • Escalation Reason: {vu_data.get('escalation_reason')}")
        assert vu_data.get("status") == "vision_unavailable"
        assert vu_data.get("is_escalated") is True
        assert vu_data.get("ticket_number", "").startswith("ESC-")
        # Ensure never returns unrelated disease
        assert "blast" not in vu_data.get("disease_name", "").lower()
        print("  -> PASS: Vision unavailable returns status='vision_unavailable' with ticket ID.")

        # Also test with 503 preference
        res_503 = client.post(
            "/api/v1/disease/diagnose",
            headers={**auth_headers, "X-Expect-Status": "503"},
            files={"file": ("leaf.jpg", io.BytesIO(leaf_bytes), "image/jpeg")},
            data={"crop_name": "Cotton", "language": "en"}
        )
        assert res_503.status_code == 503
        assert res_503.json().get("status") == "vision_unavailable"
        print("  -> PASS: Vision unavailable returns HTTP 503 when requested.")
    finally:
        gemini_service.vision_enabled = orig_vision_enabled

    # 4. Auth Error Handling
    print("\n[TEST 4] Authentication Error Handling...")
    # 4A. Missing Token
    res_no_auth = client.post(
        "/api/v1/disease/diagnose",
        files={"file": ("leaf.jpg", io.BytesIO(leaf_bytes), "image/jpeg")},
        data={"crop_name": "Cotton"}
    )
    assert res_no_auth.status_code == 401
    print(f"  • Missing Token HTTP Status: {res_no_auth.status_code} ({res_no_auth.json().get('detail')})")

    # 4B. Expired Token
    expired_token = create_access_token(
        data={"sub": farmer_id, "role": "farmer", "lang": "te"},
        expires_delta=timedelta(seconds=-60)
    )
    res_expired = client.post(
        "/api/v1/disease/diagnose",
        headers={"Authorization": f"Bearer {expired_token}"},
        files={"file": ("leaf.jpg", io.BytesIO(leaf_bytes), "image/jpeg")},
        data={"crop_name": "Cotton"}
    )
    assert res_expired.status_code == 401
    print(f"  • Expired Token HTTP Status: {res_expired.status_code} ({res_expired.json().get('detail')})")
    print("  -> PASS: Auth errors return 401 Unauthorized with descriptive details.")

    # 5. Timeout Handling Simulation
    print("\n[TEST 5] Timeout Handling Simulation...")
    import asyncio
    async def mock_timeout(*args, **kwargs):
        raise asyncio.TimeoutError("Upstream Gemini Vision RPC timed out after 10.0s")

    orig_analyze = gemini_service.analyze_crop_image
    gemini_service.analyze_crop_image = mock_timeout
    try:
        res_timeout = client.post(
            "/api/v1/disease/diagnose",
            headers=auth_headers,
            files={"file": ("leaf.jpg", io.BytesIO(leaf_bytes), "image/jpeg")},
            data={"crop_name": "Cotton", "farmer_notes": "Should I spray for rice blast?", "language": "te"}
        )
        assert res_timeout.status_code == 200
        to_data = res_timeout.json()
        print(f"  • HTTP Status: {res_timeout.status_code}")
        print(f"  • Status: {to_data.get('status')}")
        print(f"  • Disease Name: {to_data.get('disease_name')}")
        print(f"  • Ticket Number: {to_data.get('ticket_number')}")
        assert to_data.get("status") == "vision_unavailable"
        assert to_data.get("is_escalated") is True
        assert "blast" not in to_data.get("disease_name", "").lower()
        print("  -> PASS: Timeout handled cleanly; escalates with ticket and zero disease hallucination.")
    finally:
        gemini_service.analyze_crop_image = orig_analyze
        db.close()

    print("\n" + "=" * 80)
    print("ALL 5 VERIFICATION CHECKS PASSED PERFECTLY!")
    print("=" * 80)

if __name__ == "__main__":
    run_verification()
