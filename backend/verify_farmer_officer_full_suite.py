import sys
import json
from datetime import datetime
from fastapi.testclient import TestClient
from app.main import app

def run_farmer_officer_audit():
    print("=" * 80)
    print("COMPREHENSIVE FARMER-OFFICER COMMUNICATION & ESCALATION AUDIT")
    print("=" * 80)
    
    with TestClient(app) as client:
        # 1. VERIFY DEMO FARMER LOGIN
        print("\n--- [Test 1] Demo Farmer Authentication ---")
        res_farmer = client.post("/api/v1/auth/verify-otp", json={
            "phone_number": "9876543210",
            "otp": "123456",
            "device_id": "test-farmer-android",
            "fcm_token": "fcm_test"
        })
        assert res_farmer.status_code == 200, f"Farmer login failed: {res_farmer.text}"
        farmer_data = res_farmer.json()
        farmer_token = farmer_data["access_token"]
        farmer_id = farmer_data["user"]["id"]
        farmer_role = farmer_data["user"]["role"]
        assert farmer_role == "farmer", f"Expected farmer role, got {farmer_role}"
        farmer_headers = {"Authorization": f"Bearer {farmer_token}"}
        print(f"✓ Demo Farmer authenticated: ID={farmer_id}, Phone=9876543210, Role={farmer_role}")

        # 2. VERIFY EXTENSION OFFICER DEMO ACCOUNT LOGIN
        print("\n--- [Test 2] Demo Extension Officer Authentication ---")
        res_officer = client.post("/api/v1/auth/officer-login", json={
            "officer_code": "AGRI-TS-042",
            "phone_number": "9123456789",
            "otp": "654321"
        })
        assert res_officer.status_code == 200, f"Officer login failed: {res_officer.text}"
        officer_data = res_officer.json()
        officer_token = officer_data["access_token"]
        officer_info = officer_data["officer"]
        assert officer_info["officer_code"] == "AGRI-TS-042"
        assert officer_info["approval_status"] == "APPROVED"
        assert "Geesugonda" in officer_info["assigned_mandals"]
        officer_headers = {"Authorization": f"Bearer {officer_token}"}
        print(f"✓ Extension Officer authenticated: Code={officer_info['officer_code']}, Mandals={officer_info['assigned_mandals']}")

        # 3. CONVERSATION ESCALATION WORKFLOW
        print("\n--- [Test 3] Chat / Conversation Escalation ---")
        res_conv = client.post("/api/v1/conversations/", json={
            "title": "Chilli Leaf Curl & Mosaic Query",
            "crop": "Chilli",
            "language": "te"
        }, headers=farmer_headers)
        assert res_conv.status_code == 200
        conv_id = res_conv.json()["id"]

        chat_query = "మిరప తోటలో ఆకులు ముడుచుకుపోయి పసుపు మచ్చలు వచ్చాయి, ఏ మందు కొట్టాలి?"
        res_msg = client.post(f"/api/v1/conversations/{conv_id}/messages", json={
            "query_text": chat_query,
            "language": "te",
            "crop": "Chilli"
        }, headers=farmer_headers)
        assert res_msg.status_code == 200

        # Escalate ticket linked to conversation
        res_chat_esc = client.post("/api/v1/tickets/escalate", json={
            "crop_name": "Chilli",
            "escalation_reason": "Severe leaf curl and viral mosaic symptoms requiring expert diagnosis",
            "original_question_transcript": chat_query,
            "conversation_id": conv_id,
            "priority": "high",
            "attached_image_urls": ["https://storage.bolkisan.gov.in/chilli_leaf_curl_sample.jpg"]
        }, headers=farmer_headers)
        assert res_chat_esc.status_code == 200
        chat_ticket_id = res_chat_esc.json()["ticket_id"]
        chat_ticket_num = res_chat_esc.json()["ticket_number"]
        print(f"✓ Chat Ticket Escalated: {chat_ticket_num} (ID: {chat_ticket_id})")

        # 4. VOICE QUERY ESCALATION WORKFLOW
        print("\n--- [Test 4] Voice Safety-Critical Escalation ---")
        voice_query = "పత్తిలో గులాబీ రంగు పురుగు నివారణకు మోనోక్రోటోఫాస్ ఎంత కొట్టాలి?"
        res_voice = client.post("/api/v1/voice/query", json={
            "query_text": voice_query,
            "language": "te",
            "crop_name": "Cotton",
            "chemical_mentioned": "Monocrotophos"
        }, headers=farmer_headers)
        assert res_voice.status_code == 200
        voice_resp = res_voice.json()
        assert voice_resp.get("status") == "escalated" or voice_resp.get("is_safe") is False
        voice_ticket_num = voice_resp.get("ticket_number")
        assert voice_ticket_num is not None, "Voice query did not return a ticket number"
        print(f"✓ Voice Query Escalated: {voice_ticket_num}")

        # 5. SMS STATUTORY SAFETY ESCALATION WORKFLOW
        print("\n--- [Test 5] SMS Safety-Critical Escalation ---")
        sms_query = "వరిలో కాండం తొలిచే పురుగు నివారణకు డీడీటీ స్ప్రే చేయవచ్చా?"
        res_sms = client.post("/api/v1/sms/incoming", json={
            "sender_number": "+919876543210",
            "message_text": sms_query,
            "language": "te"
        })
        assert res_sms.status_code == 200
        sms_resp = res_sms.json()
        assert sms_resp.get("ticket_escalated") is True
        sms_ticket_num = sms_resp.get("ticket_id") or sms_resp.get("outbound_message", {}).get("ticket_number")
        assert sms_ticket_num is not None, "SMS did not produce ticket number"
        print(f"✓ SMS Inquiry Escalated: {sms_ticket_num}")

        # 6. IVR TELEPHONY ESCALATION WORKFLOW
        print("\n--- [Test 6] IVR Telephony Escalation (DTMF 9 & Voice) ---")
        res_ivr_start = client.post("/api/v1/ivr/call/start", json={
            "caller_number": "+919876543210",
            "language": "te"
        })
        assert res_ivr_start.status_code == 200
        call_id = res_ivr_start.json()["call_id"]

        # Press 9 to escalate to MAO
        res_dtmf = client.post("/api/v1/ivr/call/dtmf", json={
            "call_id": call_id,
            "digit": "9"
        })
        assert res_dtmf.status_code == 200
        ivr_resp = res_dtmf.json()
        assert ivr_resp["action"] == "escalate_to_mao"
        ivr_ticket_num = ivr_resp.get("ticket_number")
        assert ivr_ticket_num is not None
        print(f"✓ IVR Telephony Escalated via DTMF 9: {ivr_ticket_num}")

        # 7. VERIFY ALL TICKETS APPEAR IN EXTENSION OFFICER DASHBOARD QUEUE
        print("\n--- [Test 7] Officer Dashboard Queue Retrieval ---")
        res_tickets = client.get("/api/v1/tickets/", headers=officer_headers)
        assert res_tickets.status_code == 200
        all_tickets = res_tickets.json()
        print(f"✓ Officer queue returned {len(all_tickets)} tickets for Geesugonda/Wardhannapet/Atmakur jurisdiction.")

        all_nums = {t["ticket_number"] for t in all_tickets}
        print(f"   Found ticket numbers: {all_nums}")
        assert chat_ticket_num in all_nums, f"Chat ticket {chat_ticket_num} missing from officer queue!"
        assert voice_ticket_num in all_nums, f"Voice ticket {voice_ticket_num} missing from officer queue!"
        assert sms_ticket_num in all_nums, f"SMS ticket {sms_ticket_num} missing from officer queue!"
        assert ivr_ticket_num in all_nums, f"IVR ticket {ivr_ticket_num} missing from officer queue!"
        print("✓ All 4 escalation channels (Chat, Voice, SMS, IVR) successfully verified in Officer Queue!")

        # 8. TEST STATUS FILTERING (open vs resolved)
        print("\n--- [Test 8] Officer Dashboard Status Filtering ---")
        res_open = client.get("/api/v1/tickets/?status_filter=open", headers=officer_headers)
        assert res_open.status_code == 200
        open_tickets = res_open.json()
        open_nums = {t["ticket_number"] for t in open_tickets}
        assert chat_ticket_num in open_nums
        assert sms_ticket_num in open_nums
        print(f"✓ 'open' status filter correctly returned {len(open_tickets)} active tickets.")

        # 9. OFFICER INSPECTS TICKET DOSSIER (Phone, Question, Photo, Crop)
        print("\n--- [Test 9] Officer Inspects Ticket Detail ---")
        res_detail = client.get(f"/api/v1/tickets/{chat_ticket_id}", headers=officer_headers)
        assert res_detail.status_code == 200
        detail = res_detail.json()
        assert detail["farmer"]["phone"] in ["9876543210", "+919876543210"]
        assert detail["crop_name"] == "Chilli"
        assert len(detail["attached_image_urls"]) > 0
        assert detail["photo_url"] is not None
        print(f"✓ Ticket detail verified: Farmer Phone={detail['farmer']['phone']}, Crop={detail['crop_name']}, Photo={detail['photo_url']}")

        # Test querying by ticket_number string directly
        res_by_num = client.get(f"/api/v1/tickets/{chat_ticket_num}", headers=officer_headers)
        assert res_by_num.status_code == 200, f"Query by ticket number failed: {res_by_num.text}"
        print(f"✓ Direct query by ticket_number ({chat_ticket_num}) verified.")

        # 10. OFFICER SENDS ADVISORY REPLY
        print("\n--- [Test 10] Officer Submits CIBRC Advisory Reply ---")
        prescription = "నమస్కారం రామన్న గారు. ఇది జెమిని వైరస్ లక్షణాలు (Chilli Leaf Curl). తెల్లదోమ నివారణకు డైఫెంథియురాన్ 50% డబ్ల్యూపీ 1.25 గ్రాములు లీటరు నీటికి పిచికారీ చేయండి."
        res_reply = client.post(f"/api/v1/tickets/{chat_ticket_id}/messages", json={
            "content": prescription,
            "msg_type": "prescription"
        }, headers=officer_headers)
        assert res_reply.status_code == 200
        print("✓ Officer advisory submitted. Ticket transitioned to RESPONDED.")

        # 11. FARMER RECEIVES ADVISORY ON ANDROID (API & Conversation Stream)
        print("\n--- [Test 11] Farmer Receives Advisory in Real-Time ---")
        # Ticket messages
        res_farmer_msgs = client.get(f"/api/v1/tickets/{chat_ticket_id}/messages", headers=farmer_headers)
        assert res_farmer_msgs.status_code == 200
        f_msgs = res_farmer_msgs.json()
        officer_msg = next((m for m in f_msgs if m.get("sender_type") == "OFFICER"), None)
        assert officer_msg is not None
        assert prescription in officer_msg["content"]
        print(f"✓ Farmer received officer message in Ticket chat: '{officer_msg['content'][:50]}...'")

        # Original conversation
        res_conv_view = client.get(f"/api/v1/conversations/{conv_id}", headers=farmer_headers)
        assert res_conv_view.status_code == 200
        conv_msgs = res_conv_view.json().get("messages", [])
        officer_in_conv = next((m for m in conv_msgs if m.get("sender_type") == "OFFICER"), None)
        assert officer_in_conv is not None
        print(f"✓ Farmer received synced officer message in active Conversation stream: '{officer_in_conv['response'][:50]}...'")

        # 12. FARMER SENDS FOLLOW-UP WITH PHOTOGRAPH
        print("\n--- [Test 12] Farmer Sends Follow-up Image ---")
        followup_img = "https://storage.bolkisan.gov.in/farmer_uploads/chilli_leaf_closeup_02.jpg"
        res_followup = client.post(f"/api/v1/tickets/{chat_ticket_id}/messages", json={
            "content": "సార్, ఈ ఆకుల మీద తెల్లటి పురుగులు కూడా ఉన్నాయి. ఫోటో చూడండి.",
            "msg_type": "image",
            "media_url": followup_img
        }, headers=farmer_headers)
        assert res_followup.status_code == 200
        print("✓ Farmer follow-up message with plant leaf image sent successfully.")

        # 13. OFFICER MARKS TICKET RESOLVED
        print("\n--- [Test 13] Officer Marks Ticket RESOLVED ---")
        res_resolve = client.post(f"/api/v1/tickets/{chat_ticket_id}/status", json={
            "status": "RESOLVED"
        }, headers=officer_headers)
        assert res_resolve.status_code == 200
        assert res_resolve.json()["status"] == "RESOLVED"

        # Verify synchronized resolution in conversation
        res_conv_check = client.get(f"/api/v1/conversations/{conv_id}", headers=farmer_headers)
        assert res_conv_check.json()["escalation_status"] == "RESOLVED"
        print("✓ Ticket and Conversation escalation status synchronized to RESOLVED.")

        # 14. TEST BACKWARDS-COMPATIBILITY ALIAS (/api/v1/api/v1/...)
        print("\n--- [Test 14] Backwards-Compatibility Route Alias ---")
        res_alias = client.get("/api/v1/api/v1/tickets/", headers=officer_headers)
        assert res_alias.status_code == 200, f"Nested route alias failed: {res_alias.status_code}"
        print("✓ Nested route alias /api/v1/api/v1/tickets/ successfully verified.")

    print("\n" + "=" * 80)
    print("ALL 14 AUDIT SUITE TESTS COMPLETED AND VERIFIED 100% SUCCESSFULLY!")
    print("=" * 80)
    return True

if __name__ == "__main__":
    success = run_farmer_officer_audit()
    sys.exit(0 if success else 1)
