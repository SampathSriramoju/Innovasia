import os
import sys
import json
import psycopg2
from app.core.config import settings
from app.safety.safety_gate import safety_gate

sys.stdout.reconfigure(encoding='utf-8')

def audit_pesticides():
    print("=" * 70)
    print("INTERNAL PESTICIDE SOURCE-VERIFICATION AUDIT")
    print("NOTE: This is an internal technical verification of CIBRC compendiums,")
    print("NOT a statutory government certification.")
    print("=" * 70)

    db_url = settings.DATABASE_URL.replace("+asyncpg", "")
    conn = psycopg2.connect(db_url)
    cur = conn.cursor()

    query = """
    SELECT 
        p.id,
        p.trade_name,
        p.manufacturer,
        p.registration_number,
        p.formulation_type,
        p.toxicity_band,
        p.cibrc_status,
        l.active_ingredient,
        l.concentration,
        l.cibrc_approval_number,
        l.official_leaflet_url,
        l.is_current_valid,
        u.crop_id,
        u.pest_id,
        u.disease_id,
        u.dosage_per_acre,
        u.dosage_per_hectare,
        u.dilution_water_liters,
        u.pre_harvest_interval_days,
        u.application_method,
        u.safety_precautions,
        u.verification_status
    FROM pesticide_products p
    JOIN pesticide_labels l ON l.product_id = p.id
    JOIN approved_pesticide_uses u ON u.label_id = l.id
    ORDER BY p.trade_name;
    """
    cur.execute(query)
    rows = cur.fetchall()
    cur.close()
    conn.close()

    print(f"[✓] Joined 3-table statutory records count: {len(rows)}")

    audited_catalogue = []
    verified_count = 0
    missing_fields_count = 0

    for r in rows:
        (p_id, trade_name, manufacturer, reg_no, form_type, tox_band, cibrc_status,
         active_ing, conc, cibrc_approval, leaflet_url, is_valid,
         crop, pest, disease, dose_acre, dose_ha, dilution, phi, app_method, safety_prec, ver_status) = r

        # Mandatory statutory check
        missing = []
        if not trade_name: missing.append("trade_name")
        if not active_ing: missing.append("active_ingredient")
        if not manufacturer: missing.append("manufacturer")
        if not dose_acre: missing.append("dosage_per_acre")
        if not dilution: missing.append("dilution_water_liters")
        if phi is None: missing.append("pre_harvest_interval_days")
        if not leaflet_url: missing.append("official_leaflet_url")

        is_verified = len(missing) == 0 and (cibrc_status or "").lower() == "approved" and is_valid is True
        if is_verified:
            verified_count += 1
        else:
            missing_fields_count += 1

        entry = {
            "id": p_id,
            "trade_name": trade_name,
            "manufacturer": manufacturer,
            "active_ingredient": f"{active_ing} {conc}".strip(),
            "formulation_type": form_type,
            "cibrc_registration_number": reg_no or cibrc_approval,
            "target_crop": crop,
            "target_pest_or_disease": pest or disease,
            "dosage_per_acre": dose_acre,
            "dosage_per_hectare": dose_ha,
            "dilution_water_liters": dilution,
            "pre_harvest_interval_days": phi,
            "application_method": app_method or "Foliar Spray with Knapsack Sprayer",
            "safety_precautions": safety_prec,
            "toxicity_band": tox_band,
            "official_source_document": leaflet_url,
            "internal_audit_status": "VERIFIED" if is_verified else "PENDING_DOCUMENTATION",
            "missing_fields": missing
        }
        audited_catalogue.append(entry)

    # Test SafetyGate: ensure unverified chemical dosages and banned chemicals are blocked
    print("\n[SafetyGate Enforcement Verification]")
    test_unsafe_query = "Can I spray 2 liters of unbranded chemical for unknown worm in cotton?"
    dec_unsafe, meta_unsafe = safety_gate.evaluate_query(
        query_text=test_unsafe_query,
        crop="Cotton"
    )
    print(f"  Unsafe query is_safe: {dec_unsafe.is_safe} (Needs Escalation: {dec_unsafe.needs_escalation}, Reason: {dec_unsafe.escalation_reason})")
    assert not dec_unsafe.is_safe or dec_unsafe.needs_escalation, "SafetyGate failed to intercept unverified dosage!"

    test_banned_query = "How to use monocrotophos on tomato for fruit borer?"
    dec_banned, meta_banned = safety_gate.evaluate_query(
        query_text=test_banned_query,
        crop="Tomato"
    )
    print(f"  Banned chemical is_safe: {dec_banned.is_safe} (Severity: {dec_banned.severity}, Banned Chemical: {dec_banned.banned_chemical_detected})")
    assert not dec_banned.is_safe, "SafetyGate failed to intercept banned chemical!"

    out = {
        "disclaimer": "INTERNAL SOURCE-VERIFICATION AUDIT ONLY — NOT STATUTORY GOVERNMENT CERTIFICATION",
        "total_products_audited": len(audited_catalogue),
        "verified_count": verified_count,
        "pending_count": missing_fields_count,
        "products": audited_catalogue
    }

    with open("audit_pesticides_results.json", "w", encoding="utf-8") as f:
        json.dump(out, f, indent=2, ensure_ascii=False)

    print(f"\nAudit complete: {verified_count}/{len(audited_catalogue)} products strictly verified with complete CIBRC parameters.")
    print("Saved results to audit_pesticides_results.json")

if __name__ == "__main__":
    audit_pesticides()
