import io
import sys
from PIL import Image, ImageDraw
import numpy as np

sys.stdout.reconfigure(encoding='utf-8')

from app.ai.crop_vision_engine import crop_vision_engine, ImageQualityGate

def test_engine():
    print("=" * 70)
    print("TESTING CROP VISION DIAGNOSTIC & DIFFERENTIAL ENGINE")
    print("=" * 70)

    # 1. Test Blurry Image Quality Rejection
    blurry_img = Image.new("RGB", (300, 300), color=(50, 140, 50))
    # Fill with uniform color (zero variance -> extreme blur)
    buf_blur = io.BytesIO()
    blurry_img.save(buf_blur, format="JPEG")
    res_blur, q_blur = crop_vision_engine.analyze_image(buf_blur.getvalue(), crop_name="Cotton")
    print(f"[Blur Test] Acceptable: {q_blur.is_acceptable} | Blur score: {q_blur.blur_score} | Error: {q_blur.error_code}")
    print(f"            Farmer Guidance (TE): {q_blur.farmer_guidance_te}")
    assert q_blur.is_blurry or not q_blur.is_acceptable, "Blur check failed!"

    # 2. Test Non-Plant OOD Rejection (White / Blue image)
    non_plant = Image.new("RGB", (300, 300), color=(20, 40, 200)) # Solid blue
    buf_np = io.BytesIO()
    non_plant.save(buf_np, format="JPEG")
    res_np, q_np = crop_vision_engine.analyze_image(buf_np.getvalue(), crop_name="Rice / Paddy")
    print(f"\n[Non-Plant OOD Test] Acceptable: {q_np.is_acceptable} | Foliage ratio: {q_np.foliage_ratio} | Error: {q_np.error_code}")
    assert q_np.is_non_plant or not q_np.is_acceptable, "Non-plant check failed!"

    # 3. Test High-Confidence Rice Blast Image with Symptoms
    valid_leaf = Image.new("RGB", (300, 300), color=(35, 125, 45))
    draw = ImageDraw.Draw(valid_leaf)
    # Add spindle lesions with brown borders and grey center
    draw.ellipse([80, 100, 160, 200], fill=(90, 50, 20), outline=(50, 20, 10))
    draw.ellipse([100, 130, 140, 170], fill=(210, 210, 210))
    # Add veins
    draw.line([120, 20, 120, 280], fill=(20, 80, 25), width=3)
    buf_leaf = io.BytesIO()
    valid_leaf.save(buf_leaf, format="JPEG")

    res_diag, q_diag = crop_vision_engine.analyze_image(
        image_bytes=buf_leaf.getvalue(),
        crop_name="Rice / Paddy",
        affected_part="leaf",
        farmer_notes="Spindle-shaped blast spots with grey centers on paddy leaves",
        language="te"
    )
    print(f"\n[Rice Blast Test] Status: {res_diag.status} | Confidence: {res_diag.confidence} | Entropy: {res_diag.entropy}")
    print(f"                 Primary: {res_diag.primary_diagnosis} ({res_diag.pathology_type})")
    print(f"                 Pathogen: {res_diag.pathogen}")
    print(f"                 Top Differential: {res_diag.differential_candidates[0].disease_name} ({res_diag.differential_candidates[0].probability})")
    print(f"                 Remedies: {len(res_diag.cultural_remedies)} cultural, {len(res_diag.biological_remedies)} biological")
    assert res_diag.status == "diagnosed" and res_diag.confidence >= 0.78, "High-confidence diagnosis failed!"

    # 4. Test Chemical Management Lookup with CIBRC Verification
    res_chem, _ = crop_vision_engine.analyze_image(
        image_bytes=buf_leaf.getvalue(),
        crop_name="Rice / Paddy",
        affected_part="leaf",
        farmer_notes="Rice blast severe infection. What chemical fungicide spray dose should I apply?",
        language="en"
    )
    print(f"\n[Chemical Lookup Test] Has chemical mgmt: {res_chem.chemical_management is not None}")
    if res_chem.chemical_management:
        cm = res_chem.chemical_management
        print(f"                      Approved Product: {cm['product_trade_name']} ({cm['active_ingredient']})")
        print(f"                      Dose/Acre: {cm['dosage_per_acre']} | 15L Pump: {cm['dosage_per_15l_knapsack']}")
        print(f"                      CIBRC Reg: {cm['cibrc_registration_number']} | PHI: {cm['pre_harvest_interval_days']} days")
        assert cm["is_approved"] is True

    print("\n[✓] ALL CROP VISION ENGINE UNIT TESTS PASSED!")

if __name__ == "__main__":
    test_engine()
