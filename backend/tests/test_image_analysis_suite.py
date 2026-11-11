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
from app.database.models import User

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
        "name": "Cotton Bacterial Blight 1 (Adversarial Text Test)",
        "file": "case1_cotton_bacterial_blight_1.jpg",
        "crop_hint": None, # Intentionally null to test visual crop discovery
        "notes": "టమోటా పంటలో విల్ట్ తెగులు ఉందా? (Misleading question: asking about tomato wilt)",
        "expected_crop": "Cotton",
        "expected_status": ["diagnosed", "escalated"],
        "expected_disease_keywords": ["bacterial", "blight", "angular", "బాక్టీరియల్"],
        "forbidden_diagnoses": ["wilt", "fusarium", "టమోటా"], # Must NOT infer from text question
        "expect_rejection": False
    },
    {
        "id": "case_2",
        "name": "Cotton Healthy Leaf (Adversarial Spray Question Test)",
        "file": "case2_cotton_healthy_leaf.jpg",
        "crop_hint": "Cotton",
        "notes": "బ్లైట్ తెగులు నివారణకు మందు స్ప్రే చేయాలా? (Misleading: asking for blight spray on healthy leaf)",
        "expected_crop": "Cotton",
        "expected_status": ["healthy_crop"],
        "expected_disease_keywords": ["healthy", "ఆరోగ్య", "నిరీక్షణ", "Foliage"],
        "forbidden_diagnoses": ["blight", "bacterial"], # Must NOT diagnose blight just because farmer asked to spray for blight
        "expect_rejection": False
    },
    {
        "id": "case_3",
        "name": "Cotton Sucking Pest Damage (Neutral Question)",
        "file": "case3_cotton_sucking_pest.jpg",
        "crop_hint": "Cotton",
        "notes": "ఆకుల పరిస్థితి ఏమిటి? ఏం గమనించారు?",
        "expected_crop": "Cotton",
        "expected_status": ["diagnosed", "escalated"],
        "expected_disease_keywords": ["sucking", "pest", "thrips", "mite", "curling", "పురుగుల", "ముడత", "stippling"],
        "forbidden_diagnoses": [],
        "expect_rejection": False
    },
    {
        "id": "case_4",
        "name": "Tomato Early Blight (Adversarial Rice Question Test)",
        "file": "case4_tomato_early_blight.jpg",
        "crop_hint": None, # No hint
        "notes": "వరి పంటలో అగ్గి తెగులు లక్షణాలా ఇవి? (Misleading: asking about rice blast on tomato)",
        "expected_crop": "Tomato",
        "expected_status": ["diagnosed", "escalated"],
        "expected_disease_keywords": ["early blight", "alternaria", "blight", "టమోటా", "target"],
        "forbidden_diagnoses": ["blast", "వరి", "rice"], # Must NOT diagnose rice blast from text
        "expect_rejection": False
    },
    {
        "id": "case_5",
        "name": "Chilli Spider Mite / Curling (Adversarial Wheat Question)",
        "file": "case5_chilli_mite_curling.jpg",
        "crop_hint": None,
        "notes": "గోధుమలో రస్ట్ తెగులు నివారణ ఎలా? (Misleading: asking about wheat rust on chilli)",
        "expected_crop": "Chilli",
        "expected_status": ["diagnosed", "escalated"],
        "expected_disease_keywords": ["mite", "spider", "curling", "ముడత", "నల్లి", "boat"],
        "forbidden_diagnoses": ["rust", "wheat", "గోధుమ"], # Must NOT diagnose wheat rust
        "expect_rejection": False
    },
    {
        "id": "case_6",
        "name": "Rice Leaf Blast (Adversarial Cotton Question)",
        "file": "case6_rice_blast.jpg",
        "crop_hint": None,
        "notes": "పత్తిలో గులాబీ రంగు పురుగు ఉందా? (Misleading: asking about pink bollworm on rice leaf)",
        "expected_crop": "Rice",
        "expected_status": ["diagnosed", "escalated"],
        "expected_disease_keywords": ["blast", "rice blast", "అగ్గి", "వరి", "spindle"],
        "forbidden_diagnoses": ["bollworm", "పత్తి", "cotton"], # Must NOT diagnose cotton pest
        "expect_rejection": False
    },
    {
        "id": "case_7",
        "name": "Barren Soil (Non-Plant OOD Rejection)",
        "file": "case7_soil_barren_ground.jpg",
        "crop_hint": "Cotton", # Farmer claims it's cotton
        "notes": "నా పత్తి పంట తెగులును గుర్తించండి (Claims it's cotton crop)",
        "expected_crop": "None",
        "expected_status": ["non_plant_rejected", "quality_rejected"],
        "expected_disease_keywords": ["non-plant", "soil", "మొక్క లేని", "నేల", "Quality Rejected"],
        "forbidden_diagnoses": ["bacterial blight", "cercospora", "healthy"], # Must reject non-plant
        "expect_rejection": True
    },
    {
        "id": "case_8",
        "name": "Unrelated Machine/Tool (Non-Plant Hardware Rejection)",
        "file": "case8_unrelated_tractor_tool.jpg",
        "crop_hint": None,
        "notes": "ఈ ఫోటోను చూసి వ్యాధి చెప్పండి",
        "expected_crop": "None",
        "expected_status": ["non_plant_rejected", "quality_rejected"],
        "expected_disease_keywords": ["non-plant", "object", "మొక్క లేని", "Quality Rejected"],
        "forbidden_diagnoses": [],
        "expect_rejection": True
    },
    {
        "id": "case_9",
        "name": "Blurred Crop Photo (Laplacian Blur Quality Gate Rejection)",
        "file": "case9_blurred_crop_photo.jpg",
        "crop_hint": "Cotton",
        "notes": "దయచేసి నిర్ధారించండి",
        "expected_crop": "Cotton",
        "expected_status": ["quality_rejected"],
        "expected_disease_keywords": ["blurry", "quality", "నాణ్యత", "అస్పష్ట", "Clear"],
        "forbidden_diagnoses": [],
        "expect_rejection": True
    },
    {
        "id": "case_10",
        "name": "Cotton Bacterial Blight 2 (Distinct Photo of Same Pathology)",
        "file": "case10_cotton_bacterial_blight_2.jpg",
        "crop_hint": "Cotton",
        "notes": "ఆకులపై నల్లటి కోణీయ మచ్చలు విస్తరిస్తున్నాయి",
        "expected_crop": "Cotton",
        "expected_status": ["diagnosed", "escalated"],
        "expected_disease_keywords": ["bacterial", "blight", "angular", "బాక్టీరియల్"],
        "forbidden_diagnoses": [],
        "expect_rejection": False
    }
]

def run_image_audit():
    img_dir = Path("knowledge-base/evaluation/test_images")
    results = []
    
    print("=" * 85)
    print("BOL KISAN — CRITICAL IMAGE ANALYSIS AUDIT & REGRESSION SUITE (10 SCENARIOS)")
    print("Testing Visual Priority, Text Disregard for Contradictions, and Observability")
    print("=" * 85)

    seen_hashes = {}
    distinct_diagnoses = set()

    all_passed = True

    for tc in TEST_CASES:
        img_path = img_dir / tc["file"]
        if not img_path.exists():
            print(f"ERROR: Image file not found: {img_path}")
            all_passed = False
            continue

        raw_bytes = img_path.read_bytes()
        sha256_hash = hashlib.sha256(raw_bytes).hexdigest()
        file_size = len(raw_bytes)

        from PIL import Image
        pil_im = Image.open(img_path)
        dimensions = f"{pil_im.width}x{pil_im.height}"
        mime_type = "image/jpeg"

        # Check hash uniqueness against previous files (Case 10 vs Case 1 must have different hashes)
        if tc["id"] == "case_10":
            prev_hash = seen_hashes.get("case_1")
            assert sha256_hash != prev_hash, "Case 10 and Case 1 must be distinct photographs with different SHA-256!"
        seen_hashes[tc["id"]] = sha256_hash

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
        ret_sha256 = resp_json.get("image_sha256", "")
        ret_dimensions = resp_json.get("image_dimensions", "")

        if status_returned not in ["quality_rejected", "non_plant_rejected"]:
            distinct_diagnoses.add(disease_name)

        status_matched = status_returned in tc["expected_status"]

        # Check forbidden diagnoses (Ensures model did NOT infer from misleading typed question)
        forbidden_triggered = []
        for f in tc["forbidden_diagnoses"]:
            if f.lower() in disease_name.lower() or f.lower() in detected_crop.lower():
                forbidden_triggered.append(f)

        passed_forbidden_check = len(forbidden_triggered) == 0
        rejection_handled = tc["expect_rejection"] == (status_returned in ["quality_rejected", "non_plant_rejected"])

        test_passed = status_matched and passed_forbidden_check and rejection_handled and resp_status_code == 200
        if not test_passed:
            all_passed = False

        audit_record = {
            "test_id": tc["id"],
            "name": tc["name"],
            "file_name": tc["file"],
            "sha256": sha256_hash,
            "returned_sha256": ret_sha256,
            "dimensions": dimensions,
            "returned_dimensions": ret_dimensions,
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
            "forbidden_check_passed": passed_forbidden_check,
            "rejection_handled": rejection_handled,
            "test_passed": test_passed
        }
        results.append(audit_record)

        print(f"\n[{tc['id'].upper()}] {tc['name']}")
        print(f"  • Farmer Notes: '{tc['notes']}'")
        print(f"  • SHA-256: {sha256_hash[:16]}...{sha256_hash[-8:]} ({dimensions}, {file_size} bytes)")
        print(f"  • Returned Status: {status_returned} (Pass: {status_matched})")
        print(f"  • Detected Crop: {detected_crop} (Expected: {tc['expected_crop']})")
        print(f"  • Primary Diagnosis: {disease_name} (Confidence: {round(confidence * 100, 1)}%)")
        print(f"  • Forbidden text-leak detected: {forbidden_triggered if forbidden_triggered else 'None (VISUAL EVIDENCE HONORED)'}")
        print(f"  • Visual Observations: {symptoms_observed[:100]}...")
        print(f"  • Citations: {citations[:2]}")
        print(f"  • Scenario Result: {'PASS' if test_passed else 'FAIL'}")

    print("\n" + "=" * 85)
    print("AUDIT SUMMARY REPORT")
    print("=" * 85)
    total_tests = len(results)
    passed_tests = sum(1 for r in results if r["test_passed"])
    print(f"Total Scenarios Tested: {total_tests}")
    print(f"Passed Scenarios: {passed_tests} / {total_tests} ({round(passed_tests / total_tests * 100, 1)}%)")
    print(f"Distinct Diagnoses: {len(distinct_diagnoses)} (Identity collapse: {len(distinct_diagnoses) <= 1})")

    # Save detailed JSON output for audit report
    out_json = Path("knowledge-base/evaluation/image_analysis_test_results.json")
    with open(out_json, "w", encoding="utf-8") as f:
        json.dump(results, f, indent=2, ensure_ascii=False)
    print(f"Audit log saved to: {out_json}")

    return all_passed

if __name__ == "__main__":
    success = run_image_audit()
    sys.exit(0 if success else 1)
