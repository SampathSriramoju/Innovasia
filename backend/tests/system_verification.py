import httpx
import time
import json
import base64

BASE_URL = "http://127.0.0.1:8000"

def test_system_e2e():
    client = httpx.Client(timeout=15.0)
    print("==================================================")
    print("BOL KISAN LIVE FULL SYSTEM VERIFICATION SUITE")
    print("==================================================")

    # 1. Health & OpenAPI Specs
    t0 = time.time()
    r = client.get(f"{BASE_URL}/health")
    latency_ms = (time.time() - t0) * 1000
    assert r.status_code == 200, f"Health check failed: {r.status_code}"
    health_data = r.json()
    print(f"[PASS] /health in {latency_ms:.1f}ms: status={health_data.get('status')}")

    # 2. Test A: Farmer OTP Authentication & Token Issuance
    t0 = time.time()
    r = client.post(f"{BASE_URL}/api/v1/auth/request-otp", json={"phone_number": "+919876543210"})
    assert r.status_code == 200, f"Request OTP failed: {r.status_code} {r.text}"
    otp_data = r.json()
    dev_otp = otp_data.get("dev_otp", "123456")
    print(f"[PASS] /auth/request-otp: OTP generated for +919876543210 (dev_otp={dev_otp})")

    r = client.post(f"{BASE_URL}/api/v1/auth/verify-otp", json={
        "phone_number": "+919876543210",
        "otp": dev_otp,
        "preferred_lang": "te",
        "full_name": "Kishan Rao"
    })
    assert r.status_code == 200, f"Verify OTP failed: {r.status_code} {r.text}"
    farmer_auth = r.json()
    farmer_token = farmer_auth["access_token"]
    assert farmer_auth["user"]["role"] == "farmer"
    print(f"[PASS] /auth/verify-otp: Farmer verified, JWT role={farmer_auth['user']['role']}")

    # 3. Test Officer Login
    r = client.post(f"{BASE_URL}/api/v1/auth/officer-login", json={
        "officer_code": "AGRI-TS-042",
        "phone_number": "9123456789",
        "otp": "654321"
    })
    assert r.status_code == 200, f"Officer login failed: {r.status_code} {r.text}"
    officer_auth = r.json()
    officer_token = officer_auth["access_token"]
    print(f"[PASS] /auth/officer-login: Officer verified ({officer_auth['officer']['officer_code']})")

    # 4. Test B: Online Voice Assistant Safe Query (Telugu & English)
    t0 = time.time()
    headers = {"Authorization": f"Bearer {farmer_token}"}
    r = client.post(f"{BASE_URL}/api/v1/voice/query", json={
        "query_text": "మిరపలో ఆకుముడత మరియు తామర పురుగుల నివారణ మందులు ఏమిటి?",
        "language": "te",
        "crop_name": "Chilli",
        "pest_or_disease": "Thrips"
    }, headers=headers)
    latency_ms = (time.time() - t0) * 1000
    assert r.status_code == 200, f"Voice query failed: {r.status_code} {r.text}"
    v_data = r.json()
    print(f"[PASS] Voice Query (Telugu Chilli Thrips) in {latency_ms:.1f}ms: status={v_data.get('status')}, safe={v_data.get('is_safe')}")

    # 5. Test D: Pesticide Safety & Banned Chemical Hard Blocking
    # Case 1: Endosulfan (Globally banned in India by Supreme Court / CIBRC)
    r = client.post(f"{BASE_URL}/api/v1/voice/query", json={
        "query_text": "Can I spray Endosulfan 35 EC on cotton for bollworm?",
        "language": "en",
        "crop_name": "Cotton",
        "pest_or_disease": "Bollworm",
        "chemical_mentioned": "Endosulfan"
    }, headers=headers)
    assert r.status_code == 200
    banned_res = r.json()
    assert banned_res.get("status") == "escalated" or banned_res.get("is_safe") is False
    print(f"[PASS] Banned Chemical Guard (Endosulfan): status={banned_res.get('status')}, reason={banned_res.get('reason')}")

    # Case 2: Monocrotophos on Vegetables (Prohibited by Order S.O. 3012(E))
    r = client.post(f"{BASE_URL}/api/v1/voice/query", json={
        "query_text": "I want to spray Monocrotophos on my chilli crop for sucking pests.",
        "language": "en",
        "crop_name": "Chilli",
        "chemical_mentioned": "Monocrotophos"
    }, headers=headers)
    assert r.status_code == 200
    veg_res = r.json()
    assert veg_res.get("status") == "escalated" or veg_res.get("is_safe") is False
    print(f"[PASS] Prohibited Chemical on Veg (Monocrotophos on Chilli): status={veg_res.get('status')}")

    # Case 3: Unapproved Tank Mixture
    r = client.post(f"{BASE_URL}/api/v1/voice/query", json={
        "query_text": "Can I mix Acephate with 2,4-D herbicide in the same spray tank?",
        "language": "en",
        "crop_name": "Cotton"
    }, headers=headers)
    assert r.status_code == 200
    mix_res = r.json()
    assert mix_res.get("status") == "escalated" or mix_res.get("is_safe") is False
    print(f"[PASS] Unapproved Tank Mixture Guard: status={mix_res.get('status')}")

    # 6. Test E: CameraX Disease Diagnosis API
    sample_jpeg = (
        b"\xff\xd8\xff\xe0\x00\x10JFIF\x00\x01\x01\x01\x00`\x00`\x00\x00\xff\xdb\x00C\x00\x08\x06\x06\x07\x06\x05\x08"
        b"\x07\x07\x07\t\t\x08\n\x0c\x14\r\x0c\x0b\x0b\x0c\x19\x12\x13\x0f\x14\x1d\x1a\x1f\x1e\x1d\x1a\x1c\x1c $.' \",#\x1c\x1c(7),01444\x1f'9=82<.342\xff\xc0"
        b"\x00\x0b\x08\x00\x01\x00\x01\x01\x01\x11\x00\xff\xc4\x00\x1f\x00\x00\x01\x05\x01\x01\x01\x01\x01\x01\x00\x00\x00\x00\x00\x00\x00\x00\x01\x02\x03\x04"
        b"\x05\x06\x07\x08\t\n\x0b\xff\xda\x00\x08\x01\x01\x00\x00?\x00\xbf\x00\xff\xd9"
    )
    t0 = time.time()
    r = client.post(
        f"{BASE_URL}/api/v1/disease/diagnose",
        headers=headers,
        files={"file": ("cotton_leaf.jpg", sample_jpeg, "image/jpeg")},
        data={
            "crop_name": "Cotton",
            "farmer_notes": "Small water-soaked angular leaf spots bounded by veins on cotton leaf"
        }
    )
    latency_ms = (time.time() - t0) * 1000
    assert r.status_code == 200, f"Disease diagnose failed: {r.status_code} {r.text}"
    diag_res = r.json()
    print(f"[PASS] /disease/diagnose in {latency_ms:.1f}ms: disease={diag_res.get('disease_name')}, confidence={diag_res.get('confidence')}")

    # 7. Test F: Human Officer Escalation & Ticket Resolution
    t0 = time.time()
    r = client.post(f"{BASE_URL}/api/v1/tickets/", json={
        "crop_name": "Cotton",
        "mandal": "Geesugonda",
        "original_question_transcript": "ఆకులపై నల్లటి మచ్చలు వచ్చి రాలిపోతున్నాయి, మందు చెప్పండి",
        "escalation_reason": "Low vision confidence; suspected bacterial blight flare-up",
        "priority": "high"
    }, headers=headers)
    assert r.status_code == 200, f"Create ticket failed: {r.status_code} {r.text}"
    tkt_data = r.json()
    tkt_id = tkt_data["ticket_id"]
    print(f"[PASS] /tickets/ (Create Ticket): ticket_number={tkt_data.get('ticket_number')}, id={tkt_id}")

    # Officer lists open tickets
    officer_headers = {"Authorization": f"Bearer {officer_token}"}
    r = client.get(f"{BASE_URL}/api/v1/tickets/", headers=officer_headers)
    assert r.status_code == 200
    all_tickets = r.json()
    print(f"[PASS] /tickets/ (Officer List): {len(all_tickets)} tickets currently open in queue")

    # Officer resolves ticket with statutory prescription
    r = client.post(f"{BASE_URL}/api/v1/tickets/{tkt_id}/resolve", json={
        "prescription": "Spray Copper Oxychloride 50% WP @ 30g + Streptocycline 1g per 15L knapsack pump. CIBRC verified.",
        "action_type": "chemical"
    }, headers=officer_headers)
    assert r.status_code == 200
    resolved_tkt = r.json()
    assert resolved_tkt["status"] == "resolved"
    print(f"[PASS] /tickets/resolve: Status={resolved_tkt['status']}, prescription recorded")

    # 8. Test G: IMD Weather & Agmarknet Mandi Feeds
    t0 = time.time()
    r = client.get(f"{BASE_URL}/api/v1/weather/district/Warangal")
    assert r.status_code == 200
    w_data = r.json()
    first_day = w_data["forecast_5day"][0]
    print(f"[PASS] /weather/district/Warangal in {(time.time()-t0)*1000:.1f}ms: district={w_data.get('district')}, day={first_day.get('day_name')}, temp_max={first_day.get('temp_max_c')}C, rain={first_day.get('rainfall_mm')}mm")

    t0 = time.time()
    r = client.get(f"{BASE_URL}/api/v1/mandi/prices?commodity=Cotton&district=Warangal")
    assert r.status_code == 200
    m_data = r.json()
    print(f"[PASS] /mandi/prices in {(time.time()-t0)*1000:.1f}ms: {len(m_data)} market price quotes returned")

    # 9. Test Local Officer Lookup
    r = client.get(f"{BASE_URL}/api/v1/officers/lookup?district=Warangal&mandal=Geesugonda")
    assert r.status_code == 200
    officer_list = r.json()
    print(f"[PASS] /officers/lookup: {len(officer_list)} extension officers found in Geesugonda, Warangal")

    print("==================================================")
    print("ALL LIVE END-TO-END SYSTEM INTEGRATION TESTS PASSED")
    print("==================================================")

if __name__ == "__main__":
    test_system_e2e()
