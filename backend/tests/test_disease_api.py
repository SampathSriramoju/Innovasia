import io
import os
from pathlib import Path
import pytest
from PIL import Image, ImageDraw

def create_synthetic_leaf(color=(35, 125, 45), add_lesions=True, is_blurry=False, crop="Cotton") -> bytes:
    """Returns a realistic crop leaf photo for vision tests, or a synthetic image for blur testing."""
    if is_blurry:
        img = Image.new("RGB", (300, 300), color=color)
        buf = io.BytesIO()
        img.save(buf, format="JPEG")
        return buf.getvalue()

    base_dir = Path(__file__).resolve().parent.parent.parent
    crop_lower = crop.lower()
    if "chilli" in crop_lower or "chili" in crop_lower:
        img_name = "case5_chilli_mite_curling.jpg"
    elif "rice" in crop_lower or "వరి" in crop_lower or "paddy" in crop_lower:
        img_name = "case6_rice_blast.jpg"
    elif "tomato" in crop_lower:
        img_name = "case4_tomato_early_blight.jpg"
    elif not add_lesions:
        img_name = "case2_cotton_healthy_leaf.jpg"
    else:
        img_name = "case10_cotton_bacterial_blight_2.jpg"

    real_img_path = base_dir / "knowledge-base" / "evaluation" / "test_images" / img_name
    if real_img_path.exists():
        with open(real_img_path, "rb") as f:
            return f.read()

    img = Image.new("RGB", (300, 300), color=color)
    draw = ImageDraw.Draw(img)
    draw.line([150, 10, 150, 290], fill=(20, 80, 25), width=3)
    draw.line([150, 80, 80, 140], fill=(25, 90, 30), width=2)
    draw.line([150, 150, 220, 210], fill=(25, 90, 30), width=2)
    draw.line([150, 220, 90, 270], fill=(25, 90, 30), width=2)

    if add_lesions:
        draw.ellipse([80, 100, 150, 180], fill=(90, 50, 20), outline=(50, 20, 10))
        draw.ellipse([100, 120, 130, 160], fill=(200, 200, 200))
        draw.ellipse([180, 160, 240, 220], fill=(100, 60, 25), outline=(60, 30, 15))

    buf = io.BytesIO()
    img.save(buf, format="JPEG")
    return buf.getvalue()


def test_diagnose_high_confidence_leaf_spot(client, farmer_token):
    """
    Test disease endpoint with high-confidence crop leaf image.
    Confirms disease identification, symptoms, and ICAR IPM non-chemical remedies.
    """
    leaf_bytes = create_synthetic_leaf()
    file_data = io.BytesIO(leaf_bytes)

    res = client.post(
        "/api/v1/disease/diagnose",
        headers={"Authorization": f"Bearer {farmer_token}"},
        files={"file": ("cotton_leaf.jpg", file_data, "image/jpeg")},
        data={
            "crop_name": "Cotton",
            "plant_part": "leaf",
            "farmer_notes": "Small water-soaked angular leaf spots bounded by veins on cotton leaf"
        }
    )

    assert res.status_code == 200
    data = res.json()
    assert data["status"] == "diagnosed"
    assert data["is_escalated"] is False
    assert data["confidence"] >= 0.78
    assert "Bacterial Blight" in data["disease_name"] or "Angular Leaf Spot" in data["disease_name"]
    assert "Bacterial" in data["pathology_type"]
    assert len(data["cultural_remedies"]) > 0
    assert len(data["biological_remedies"]) > 0
    assert len(data["citations"]) > 0
    assert data["image_url"].startswith("/api/v1/disease/images/") or data["image_url"].startswith("/static/uploads/diseases/")


def test_diagnose_high_confidence_with_cibrc_chemical_management(client, farmer_token):
    """
    Test high-confidence rice blast diagnosis where chemical management is asked.
    Confirms SafetyGate integration verifies CIBRC approved label (Amistar Top).
    """
    leaf_bytes = create_synthetic_leaf(crop="Rice / Paddy")
    file_data = io.BytesIO(leaf_bytes)

    res = client.post(
        "/api/v1/disease/diagnose",
        headers={"Authorization": f"Bearer {farmer_token}"},
        files={"file": ("rice_blast_leaf.jpg", file_data, "image/jpeg")},
        data={
            "crop_name": "Rice / Paddy",
            "plant_part": "leaf",
            "farmer_notes": "Spindle-shaped lesions with brown borders and grayish centers. What chemical pesticide spray and dosage should I apply?"
        }
    )

    assert res.status_code == 200
    data = res.json()
    assert data["status"] == "diagnosed"
    assert data["is_escalated"] is False
    assert data["confidence"] >= 0.78
    assert "Rice Blast" in data["disease_name"]
    assert data["chemical_management"] is not None
    assert data["chemical_management"]["is_approved"] is True
    assert "Amistar Top" in data["chemical_management"]["product_trade_name"]
    assert "200 ml" in data["chemical_management"]["dosage_per_acre"]
    assert "CIBRC" in data["chemical_management"]["citation"]


def test_diagnose_quality_gate_rejection_blurry(client, farmer_token):
    """
    Test quality gate rejecting blurry / featureless images before running diagnostic inferences.
    """
    blurry_bytes = create_synthetic_leaf(is_blurry=True)
    file_data = io.BytesIO(blurry_bytes)

    res = client.post(
        "/api/v1/disease/diagnose",
        headers={"Authorization": f"Bearer {farmer_token}"},
        files={"file": ("blurry_crop.jpg", file_data, "image/jpeg")},
        data={"crop_name": "Cotton"}
    )

    assert res.status_code == 200
    data = res.json()
    assert data["status"] == "quality_rejected"
    assert data["error_code"] == "IMAGE_BLURRY"
    assert data["blur_score"] < 80.0
    assert "message" in data


def test_diagnose_quality_gate_rejection_non_plant(client, farmer_token):
    """
    Test quality gate rejecting non-plant out-of-distribution photos (e.g. solid blue).
    """
    blue_img = Image.new("RGB", (300, 300), color=(15, 30, 220))
    buf = io.BytesIO()
    blue_img.save(buf, format="JPEG")

    res = client.post(
        "/api/v1/disease/diagnose",
        headers={"Authorization": f"Bearer {farmer_token}"},
        files={"file": ("blue_screen.jpg", io.BytesIO(buf.getvalue()), "image/jpeg")},
        data={"crop_name": "Rice / Paddy"}
    )

    assert res.status_code == 200
    data = res.json()
    assert data["status"] == "quality_rejected"
    assert data["error_code"] == "NON_PLANT_IMAGE"
    assert data["foliage_ratio"] < 0.08


def test_diagnose_low_confidence_auto_escalation(client, farmer_token, monkeypatch):
    """
    Test low-confidence / ambiguous crop photo triggering auto-escalation ticket creation.
    Confirms ticket is created with attached image URL and reason.
    """
    from app.ai.gemini_service import gemini_service

    async def mock_low_conf(*args, **kwargs):
        return {
            "success": True,
            "status": "escalated",
            "primary_diagnosis": "Ambiguous Leaf Blight / Heat Stress",
            "confidence": 0.65,
            "entropy": 0.75,
            "crop_name": "Cotton",
            "visual_observations": "Ambiguous necrotic discoloration and spots on leaf",
            "farmer_explanation": "Symptoms are ambiguous, requiring manual field inspection by MAO.",
            "pathology_type": "Uncertain",
            "citations": [],
            "follow_up_questions": []
        }

    monkeypatch.setattr(gemini_service, "analyze_crop_image", mock_low_conf)

    leaf_bytes = create_synthetic_leaf(add_lesions=False)
    file_data = io.BytesIO(leaf_bytes)

    res = client.post(
        "/api/v1/disease/diagnose",
        headers={"Authorization": f"Bearer {farmer_token}"},
        files={"file": ("unknown_mildew.jpg", file_data, "image/jpeg")},
        data={
            "crop_name": "Cotton",
            "farmer_notes": "Very unusual symptoms not sure if heat stress, chemical injury or nutrient deficiency"
        }
    )

    assert res.status_code == 200
    data = res.json()
    assert data["status"] == "escalated"
    assert data["is_escalated"] is True
    assert data["confidence"] < 0.78
    assert "ticket_number" in data
    assert data["ticket_number"].startswith("ESC-")
    assert "ticket_id" in data

    # Verify that the ticket can be retrieved via the /tickets endpoint
    ticket_id = data["ticket_id"]
    ticket_res = client.get(
        f"/api/v1/tickets/{ticket_id}",
        headers={"Authorization": f"Bearer {farmer_token}"}
    )
    assert ticket_res.status_code == 200
    ticket_data = ticket_res.json()
    assert ticket_data["status"] == "open"
    assert data["image_url"] in ticket_data["attached_image_urls"]


def test_diagnose_invalid_image_format_rejected(client, farmer_token):
    """
    Test that non-image and corrupted files are rejected with HTTP 400.
    """
    corrupt_data = io.BytesIO(b"Plain text corrupted file not an image")

    res = client.post(
        "/api/v1/disease/diagnose",
        headers={"Authorization": f"Bearer {farmer_token}"},
        files={"file": ("fake_document.txt", corrupt_data, "text/plain")},
        data={"crop_name": "Cotton"}
    )

    assert res.status_code == 400
    assert "Invalid image format" in res.json()["detail"]


def test_diagnose_multilingual_telugu_query(client, farmer_token):
    """
    Test multimodal disease diagnosis with Telugu crop name and symptoms.
    """
    leaf_bytes = create_synthetic_leaf(crop="వరి")
    file_data = io.BytesIO(leaf_bytes)

    res = client.post(
        "/api/v1/disease/diagnose",
        headers={"Authorization": f"Bearer {farmer_token}"},
        files={"file": ("crop_leaf.jpg", file_data, "image/jpeg")},
        data={
            "crop_name": "వరి",
            "farmer_notes": "వరి ఆకులపై అగ్గితెగులు మచ్చలు స్పష్టంగా ఉన్నాయి"
        }
    )

    assert res.status_code == 200
    data = res.json()
    assert data["status"] == "diagnosed"
    assert data["confidence"] >= 0.78
    assert "Rice Blast" in data["disease_name"]


def test_diagnose_mite_webbing_chilli(client, farmer_token, monkeypatch):
    """
    Test diagnosis of mite webbing and stippling on chilli leaves.
    """
    from app.ai.gemini_service import gemini_service

    async def mock_mite(*args, **kwargs):
        return {
            "success": True,
            "status": "diagnosed",
            "primary_diagnosis": "Yellow Mite / Chilli Mite",
            "confidence": 0.88,
            "entropy": 0.18,
            "crop_name": "Chilli",
            "visual_observations": "Downward leaf curling, bronzing, stippling and fine silky webbing on leaf undersides",
            "farmer_explanation": "Severe yellow mite infestation causing leaf curl and webbing",
            "pathology_type": "Acarine / Mite",
            "citations": ["KB04 - ICAR Package of Practices for Chilli"],
            "cultural_remedies": ["Remove infested shoots"],
            "biological_remedies": ["Neem oil 10000 ppm @ 2ml/L"],
            "follow_up_questions": []
        }

    monkeypatch.setattr(gemini_service, "analyze_crop_image", mock_mite)

    leaf_bytes = create_synthetic_leaf(crop="Chilli")
    file_data = io.BytesIO(leaf_bytes)

    res = client.post(
        "/api/v1/disease/diagnose",
        headers={"Authorization": f"Bearer {farmer_token}"},
        files={"file": ("chilli_leaf.jpg", file_data, "image/jpeg")},
        data={
            "crop_name": "Chilli",
            "farmer_notes": "Fine silky spider mite webbing on leaf undersides with bronzing and stippling"
        }
    )

    assert res.status_code == 200
    data = res.json()
    assert data["status"] == "diagnosed"
    assert data["confidence"] >= 0.78
    assert "Mite" in data["disease_name"]
    assert "Acarine" in data["pathology_type"]
