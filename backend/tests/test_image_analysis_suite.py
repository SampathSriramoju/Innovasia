import os
import sys
import json
import hashlib
from pathlib import Path
from typing import Dict, Any, List

from dotenv import load_dotenv
load_dotenv(Path(__file__).resolve().parent.parent / ".env")

# Ensure backend root is on path
backend_dir = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(backend_dir))

from fastapi.testclient import TestClient
from app.main import app
from app.core.security import create_access_token
from app.database.session import SessionLocal
from app.database.models import User, FarmerProfile

# Query existing farmer user from DB
db = SessionLocal()
try:
    farmer = db.query(User).filter(User.role == "farmer").first()
    farmer_id = farmer.id if farmer else "765697cd-254b-4ee7-828f-a4fc63d658ed"
finally:
    db.close()

test_client = TestClient(app)

# Generate a test farmer JWT token using verified DB user ID
test_token = create_access_token(data={"sub": farmer_id, "role": "farmer", "lang": "te"})
auth_headers = {"Authorization": f"Bearer {test_token}"}

TEST_CASES = [
    {
        "id": "case_1",
        "name": "Cotton Bacterial Blight 1",
        "file": "case1_cotton_bacterial_blight_1.jpg",
        "crop_hint": "Cotton",
        "notes": "ఆకులపై నల్లటి మచ్చలు కనిపిస్తున్నాయి",
        "expected_crop": "Cotton",
        "expected_status": ["diagnosed", "escalated"],
        "expected_disease_keywords": ["bacterial", "blight", "angular", "బాక్టీరియల్"],
        "expect_rejection": False
    },
    {
        "id": "case_2",
        "name": "Cotton Healthy Leaf",
        "file": "case2_cotton_healthy_leaf.jpg",
        "crop_hint": "Cotton",
        "notes": "పంట ఆకులు ఎలా ఉన్నాయి?",
        "expected_crop": "Cotton",
        "expected_status": ["healthy_crop"],
        "expected_disease_keywords": ["healthy", "ఆరోగ్య", "నిరీక్షణ"],
        "expect_rejection": False
    },
    {
        "id": "case_3",
        "name": "Cotton Sucking Pest Damage",
        "file": "case3_cotton_sucking_pest.jpg",
        "crop_hint": "Cotton",
        "notes": "ఆకులు కిందికి ముడుచుకుని పసుపు రంగు మచ్చలు వస్తున్నాయి",
        "expected_crop": "Cotton",
        "expected_status": ["diagnosed", "escalated"],
        "expected_disease_keywords": ["sucking", "pest", "thrips", "mite", "curling", "పురుగుల", "ముడత"],
        "expect_rejection": False
    },
    {
        "id": "case_4",
        "name": "Tomato Early Blight",
        "file": "case4_tomato_early_blight.jpg",
        "crop_hint": "Tomato",
        "notes": "టమోటా ఆకులపై వలయాకార మచ్చలు",
        "expected_crop": "Tomato",
        "expected_status": ["diagnosed", "escalated"],
        "expected_disease_keywords": ["early blight", "alternaria", "blight", "టమోటా"],
        "expect_rejection": False
    },
    {
        "id": "case_5",
        "name": "Chilli Spider Mite / Curling",
        "file": "case5_chilli_mite_curling.jpg",
        "crop_hint": "Chilli",
        "notes": "మిర్చి ఆకులు కిందికి ముడుచుకుంటున్నాయి",
        "expected_crop": "Chilli",
        "expected_status": ["diagnosed", "escalated"],
        "expected_disease_keywords": ["mite", "spider", "curling", "ముడత", "నల్లి"],
        "expect_rejection": False
    },
    {
        "id": "case_6",
        "name": "Rice Leaf Blast",
        "file": "case6_rice_blast.jpg",
        "crop_hint": "Rice / Paddy",
        "notes": "వరి ఆకులపై కండె ఆకారపు మచ్చలు",
        "expected_crop": "Rice / Paddy",
        "expected_status": ["diagnosed", "escalated"],
        "expected_disease_keywords": ["blast", "rice blast", "అగ్గి", "వరి"],
        "expect_rejection": False
    },
    {
        "id": "case_7",
        "name": "Barren Soil (Non-Plant)",
        "file": "case7_soil_barren_ground.jpg",
        "crop_hint": None,
        "notes": "నేల ఫోటో",
        "expected_crop": "None",
        "expected_status": ["non_plant_rejected", "quality_rejected"],
        "expected_disease_keywords": ["non-plant", "soil", "మొక్క లేని", "నేల"],
        "expect_rejection": True
    },
    {
        "id": "case_8",
        "name": "Unrelated Machine/Tool (Non-Plant)",
        "file": "case8_unrelated_tractor_tool.jpg",
        "crop_hint": None,
        "notes": "పనిముట్టు",
        "expected_crop": "None",
        "expected_status": ["non_plant_rejected", "quality_rejected"],
        "expected_disease_keywords": ["non-plant", "object", "మొక్క లేని"],
        "expect_rejection": True
    },
    {
        "id": "case_9",
        "name": "Blurred Crop Photo (Unusable Quality)",
        "file": "case9_blurred_crop_photo.jpg",
        "crop_hint": "Cotton",
        "notes": "మసకగా ఉన్న ఫోటో",
        "expected_crop": "Cotton",
        "expected_status": ["quality_rejected"],
        "expected_disease_keywords": ["blurry", "quality", "నాణ్యత", "అస్పష్ట"],
        "expect_rejection": True
    },
    {
        "id": "case_10",
        "name": "Cotton Bacterial Blight 2 (Distinct Photograph)",
        "file": "case10_cotton_bacterial_blight_2.jpg",
        "crop_hint": "Cotton",
        "notes": "పత్తిలో తీవ్రమైన బాక్టీరియల్ మచ్చలు",
        "expected_crop": "Cotton",
        "expected_status": ["diagnosed", "escalated"],
        "expected_disease_keywords": ["bacterial", "blight", "angular", "బాక్టీరియల్"],
        "expect_rejection": False
    }
]

def run_image_audit():
    img_dir = Path("knowledge-base/evaluation/test_images")
    results = []
    
    print("=" * 80)
    print("BOL KISAN — COMPREHENSIVE IMAGE ANALYSIS AUDIT AND TEST SUITE (10 SCENARIOS)")
    print("=" * 80)

    distinct_diagnoses = set()

    for tc in TEST_CASES:
        img_path = img_dir / tc["file"]
        if not img_path.exists():
            print(f"ERROR: Image file not found: {img_path}")
            continue

        raw_bytes = img_path.read_bytes()
        sha256_hash = hashlib.sha256(raw_bytes).hexdigest()
        file_size = len(raw_bytes)

        from PIL import Image
        pil_im = Image.open(img_path)
        dimensions = f"{pil_im.width}x{pil_im.height}"
        mime_type = "image/jpeg"

        # Prepare multipart form upload
        files = {
            "file": (tc["file"], raw_bytes, mime_type)
        }
        data = {
            "crop_name": tc["crop_hint"] or "",
            "plant_part": "leaf",
            "farmer_notes": tc["notes"],
            "language": "te"
        }

        response = test_client.post(
            "/api/v1/disease/diagnose",
            headers=auth_headers,
            files=files,
            data=data
        )

        resp_status_code = response.status_code
        resp_json = response.json() if resp_status_code == 200 else {}

        status_returned = resp_json.get("status", "error")
        disease_name = resp_json.get("disease_name", "None")
        confidence = resp_json.get("confidence", 0.0)
        symptoms_observed = resp_json.get("symptoms", "")
        treatment = resp_json.get("treatment", "")
        citations = resp_json.get("citations", [])
        follow_ups = resp_json.get("follow_up_questions", [])
        detected_crop = resp_json.get("crop_name", "None")

        is_distinct = disease_name not in distinct_diagnoses
        if status_returned not in ["quality_rejected", "non_plant_rejected"]:
            distinct_diagnoses.add(disease_name)

        status_matched = status_returned in tc["expected_status"]
        
        audit_record = {
            "test_id": tc["id"],
            "name": tc["name"],
            "file_name": tc["file"],
            "sha256": sha256_hash,
            "dimensions": dimensions,
            "mime_type": mime_type,
            "file_size_bytes": file_size,
            "http_status": resp_status_code,
            "returned_status": status_returned,
            "status_pass": status_matched,
            "detected_crop": detected_crop,
            "primary_diagnosis": disease_name,
            "confidence": confidence,
            "symptoms_observed": symptoms_observed[:150] if symptoms_observed else "None",
            "citations": citations,
            "follow_up_questions": follow_ups,
            "rejection_handled": tc["expect_rejection"] == (status_returned in ["quality_rejected", "non_plant_rejected"])
        }
        results.append(audit_record)

        print(f"\n[{tc['id'].upper()}] {tc['name']}")
        print(f"  • SHA-256: {sha256_hash[:16]}...{sha256_hash[-8:]} ({dimensions}, {file_size} bytes)")
        print(f"  • Returned Status: {status_returned} (Pass: {status_matched})")
        print(f"  • Detected Crop: {detected_crop}")
        print(f"  • Primary Diagnosis: {disease_name} (Confidence: {round(confidence * 100, 1)}%)")
        print(f"  • Visual Observations: {symptoms_observed[:100]}...")
        print(f"  • Citations: {citations[:2]}")

    print("\n" + "=" * 80)
    print("IMAGE ANALYSIS TEST SUITE SUMMARY")
    print("=" * 80)
    total_tests = len(results)
    passed_tests = sum(1 for r in results if r["status_pass"] and r["rejection_handled"])
    print(f"Total Scenarios Tested: {total_tests}")
    print(f"Passed Scenarios: {passed_tests} / {total_tests} ({round(passed_tests / total_tests * 100, 1)}%)")
    print(f"Distinct Diseases Diagnosed Across Crops: {len(distinct_diagnoses)} (Identity collapsed: {len(distinct_diagnoses) <= 1})")

    # Save detailed JSON output for audit report
    out_json = Path("knowledge-base/evaluation/image_analysis_test_results.json")
    with open(out_json, "w", encoding="utf-8") as f:
        json.dump(results, f, indent=2, ensure_ascii=False)
    print(f"Audit log saved to: {out_json}")

    return passed_tests == total_tests

if __name__ == "__main__":
    success = run_image_audit()
    sys.exit(0 if success else 1)
