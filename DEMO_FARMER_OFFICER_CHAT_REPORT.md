# Bol Kisan — Farmer–Extension Officer Communication System Report

**Date:** 2026-11-10  
**Status:** FULLY IMPLEMENTED & VERIFIED  
**Target:** Bol Kisan Hackathon Demonstration  

---

## 1. Executive Summary & Architecture Overview

To provide an end-to-end human escalation pathway when AI safety gates flag complex pest or disease outbreaks, we implemented a lightweight, resilient **Two-Account Farmer–Extension Officer Communication System**. 

The system links the Android Mobile Application (`apps/farmer-android`) directly with the Web Officer Dashboard (`apps/officer-dashboard`) via the FastAPI Backend (`backend/app/api/v1/endpoints/tickets.py`).

### Architectural Topology

```
┌─────────────────────────────────┐                    ┌─────────────────────────────────┐
│     Farmer Mobile Android       │                    │    Extension Officer Dashboard  │
│  (Kotlin / Jetpack Compose)     │                    │    (React / TypeScript / Vite)  │
│                                 │                    │                                 │
│  • HomeScreen / OfficerTickets  │                    │  • TicketDetailModal Console    │
│  • Room DB (TicketMessageDao)   │                    │  • Officer Support Inbox        │
│  • OkHttp / Retrofit Polling    │                    │  • REST Polling (4s Interval)   │
└────────────────┬────────────────┘                    └────────────────┬────────────────┘
                 │                                                      │
                 │ POST /api/v1/tickets/escalate                        │ GET /api/v1/tickets/
                 │ GET  /api/v1/tickets/{id}/messages                   │ GET /api/v1/tickets/{id}/messages
                 │ POST /api/v1/tickets/{id}/messages                   │ POST /api/v1/tickets/{id}/messages
                 │                                                      │ POST /api/v1/tickets/{id}/status
                 └───────────────────────┬──────────────────────────────┘
                                         │
                                         ▼
                 ┌──────────────────────────────────────────────┐
                 │          FastAPI Backend & DB Engine         │
                 │                                              │
                 │  • AgriculturalTicket Entity                 │
                 │  • TicketMessage (Chronological Thread)      │
                 │  • Dual-Sync to ConversationMessage          │
                 │  • RBAC & Jurisdictional Mandal Routing      │
                 │  • Safe Fallback (No WebSocket Complexity)   │
                 └──────────────────────────────────────────────┘
```

### Key Architectural Decisions
1. **Lightweight Polling over WebSockets:** Avoids unstable socket states across intermittent 2G/3G rural networks; uses periodic HTTP GET polling (4 seconds) with 304/delta awareness.
2. **Dual Synchronized Persistence:** When an officer replies via `/api/v1/tickets/{ticket_id}/messages`, the backend persists a `TicketMessage` AND mirrors it as a `ConversationMessage` with an officer badge (`👨‍🌾 [అధికారి]: ...`). This ensures the farmer sees officer responses in both their active AI conversation thread and their dedicated Officer Support Ticket inbox.
3. **Local Room DB Caching:** On the Android device, `TicketMessageEntity` and `TicketMessageDao` cache incoming advice locally so farmers can re-read dosage and application recommendations even when operating completely offline.
4. **Jurisdictional Routing Enforcement:** Farmers are automatically mapped to their registered mandal (`Geesugonda`) and cannot tamper with routing; extension officers only see tickets within their assigned mandals.

---

## 2. Dedicated Demo Accounts

No fake bypasses or mock tokens were used. Authentication uses authentic JWT tokens signed via the backend's `/api/v1/auth/verify-otp` endpoint with deterministic development OTP seeds.

| Attribute | Demo Farmer Account | Demo Extension Officer Account |
| :--- | :--- | :--- |
| **Identity Name** | Ramu Farmer / Kishan Rao | Dr. K. Srinivas Rao, MAO |
| **Phone Number** | `9876543210` | `9123456789` |
| **Login OTP** | `123456` | `654321` |
| **Officer Code** | *N/A* | `AGRI-TS-042` |
| **Role / Tier** | `farmer` | `extension_officer` |
| **Designation** | Registered Farmer | Mandal Agricultural Officer (MAO) |
| **Jurisdiction** | Geesugonda, Warangal, Telangana | Warangal (Geesugonda, Wardhannapet, Atmakur) |
| **User UUID** | `a7933d82-9219-4560-9ec7-154d7754383a` | `e9f8cd9b-fa0e-4043-ba9e-ced4a9395f76` |

---

## 3. End-to-End 12-Step Verification Flow

The end-to-end workflow was verified via `backend/verify_12_step_chat.py`. All 12 criteria passed with 100% compliance:

| Step # | Verified Action | Component | Test Assertion & Outcome |
| :---: | :--- | :---: | :--- |
| **1** | **Farmer Authentication** | Backend / Android | Phone `9876543210`, OTP `123456` verified. JWT token issued. **PASSED** |
| **2** | **Farmer Asks Question** | Backend / AI | Farmer asks: *"మిరప తోటలో ఆకులు ముడుచుకుపోయి పసుపు మచ్చలు వచ్చాయి, ఏ మందు కొట్టాలి?"* Conversation initialized. **PASSED** |
| **3** | **Contact Officer (Escalate)** | Android UI | Farmer taps "Contact Agricultural Officer". Created Ticket `BK-2026-51A6C7F677D8` linked to conversation. **PASSED** |
| **4** | **Ticket Appears in Queue** | Officer Dashboard | Ticket discovered in Geesugonda mandal queue with status `PENDING`. **PASSED** |
| **5** | **Officer Authentication** | Dashboard Auth | Phone `9123456789`, Code `AGRI-TS-042`, OTP `654321` verified. Role `extension_officer` confirmed. **PASSED** |
| **6** | **Officer Inspects Ticket** | Dashboard UI | Officer opens ticket modal; views transcript, farmer phone, and attached plant leaf photos. **PASSED** |
| **7** | **Officer Sends Reply** | Backend / API | Officer submits CIBRC advisory for Chilli Leaf Curl. Status auto-transitions to `RESPONDED`. **PASSED** |
| **8** | **Farmer Receives Advisory** | Android / Conversation | Advisory verified in `/api/v1/tickets/{id}/messages` and original conversation stream `/api/v1/conversations/{id}`. **PASSED** |
| **9** | **Farmer Follow-up with Image** | Android Chat | Farmer sends follow-up with photo (`chilli_leaf_closeup_02.jpg`). Ticket status shifts to `IN_REVIEW`. **PASSED** |
| **10** | **Officer Receives Image** | Officer Dashboard | Image thumbnail and follow-up message rendered in Officer modal within 4s poll. **PASSED** |
| **11** | **App Restart & Persistence** | Room DB / Relational | App restart simulated; all 4 messages re-fetched with strict chronological timestamp integrity. **PASSED** |
| **12** | **Resolution Synchronization** | Android & Dashboard | Officer marks ticket `RESOLVED`; both Ticket and Conversation escalation status reflect `RESOLVED`. **PASSED** |

---

## 4. UI Implementation Details

### A. Android Client (`apps/farmer-android`)
- **Interactive Escalation Banner:** Integrated into `HomeScreen.kt`. Whenever AI detects unverified chemical hazards, off-label combinations, or complex symptoms, a warning card renders with a bold **"Contact Agricultural Officer"** button.
- **Dedicated Officer Chat Screen:** In `OfficerTicketsScreen.kt`, tapping any active or resolved ticket launches an interactive Compose chat interface:
  - Localized chat bubbles distinguishing Farmer messages (emerald green) from Officer messages (dark navy blue with an officer badge).
  - Image attachment support using Android Photo Picker.
  - Image thumbnail previews with tap-to-expand.
  - Live 4-second polling with error retry banner and connection health indicator.
- **Offline Room Database Schema v3:**
  ```kotlin
  @Entity(tableName = "ticket_messages")
  data class TicketMessageEntity(
      @PrimaryKey val id: String,
      val ticketId: String,
      val senderId: String,
      val senderType: String,
      val senderName: String?,
      val msgType: String,
      val content: String,
      val mediaUrl: String?,
      val createdAt: String
  )
  ```

### B. Officer Dashboard (`apps/officer-dashboard`)
- **Farmer Support Inbox & Modal:** Located in `src/components/TicketDetailModal.tsx`.
- **Chronological Message Stream:** Renders conversation history, AI context, attached farmer images, and officer replies with distinct badges.
- **Quick Advisory Presets:** Pre-fills common CIBRC-approved formulations (e.g., *Neem Oil 10,000 ppm*, *Diafenthiuron 50% WP*, *Acetamiprid 20% SP*) to accelerate officer workflow.
- **Status Controls:** One-click **"Mark as Resolved"** and **"Reopen Ticket"** actions with immediate UI feedback and backend sync.
- **Production Build:** Built cleanly with zero TypeScript errors via Vite.

---

## 5. API Payload Specifications

### 1. Officer Sends Advisory Message
`POST /api/v1/tickets/{ticket_id}/messages`
```json
{
  "content": "నమస్కారం రామన్న గారు. ఇది జెమిని వైరస్ లక్షణాలు (Chilli Leaf Curl). తెల్లదోమ నివారణకు డైఫెంథియురాన్ 50% డబ్ల్యూపీ 1.25 గ్రాములు లేదా ఎసిటామిప్రిడ్ 20% ఎస్పీ 0.2 గ్రాములు లీటరు నీటికి కలిపి పిచికారీ చేయండి.",
  "msg_type": "prescription"
}
```

**Response (`200 OK`):**
```json
{
  "id": "322192c7-883e-43b9-a2db-8df0830810d3",
  "ticket_id": "ab51f9f8-fc4b-42bb-af79-7a521e18bf73",
  "sender_id": "e9f8cd9b-fa0e-4043-ba9e-ced4a9395f76",
  "sender_type": "OFFICER",
  "sender_name": "Dr. K. Srinivas Rao, MAO",
  "msg_type": "prescription",
  "content": "నమస్కారం రామన్న గారు...",
  "media_url": null,
  "created_at": "2026-11-10T18:24:38.936000Z"
}
```

### 2. Farmer Sends Follow-up with Image
`POST /api/v1/tickets/{ticket_id}/messages`
```json
{
  "content": "సార్, వేప నూనె కూడా కలపవచ్చా? తాజా ఆకుల ఫోటో పంపిస్తున్నాను.",
  "msg_type": "image",
  "media_url": "https://storage.bolkisan.gov.in/farmer_uploads/chilli_leaf_closeup_02.jpg"
}
```

**Response (`200 OK`):**
```json
{
  "id": "b6da7fb6-c303-4ca5-801b-f7535a522478",
  "ticket_id": "ab51f9f8-fc4b-42bb-af79-7a521e18bf73",
  "sender_id": "a7933d82-9219-4560-9ec7-154d7754383a",
  "sender_type": "FARMER",
  "sender_name": "Ramu Farmer",
  "msg_type": "image",
  "content": "సార్, వేప నూనె కూడా కలపవచ్చా? తాజా ఆకుల ఫోటో పంపిస్తున్నాను.",
  "media_url": "https://storage.bolkisan.gov.in/farmer_uploads/chilli_leaf_closeup_02.jpg",
  "created_at": "2026-11-10T18:24:38.980000Z"
}
```

### 3. Update Ticket Status
`POST /api/v1/tickets/{ticket_id}/status`
```json
{
  "status": "RESOLVED"
}
```

**Response (`200 OK`):**
```json
{
  "ticket_id": "ab51f9f8-fc4b-42bb-af79-7a521e18bf73",
  "ticket_number": "BK-2026-51A6C7F677D8",
  "status": "RESOLVED",
  "resolved_at": "2026-11-10T18:24:39.083000Z",
  "message": "Ticket status successfully updated to RESOLVED."
}
```

---

## 6. Verification & Test Metrics

1. **Farmer-Officer Chat Suite:**
   `pytest backend/tests/test_farmer_officer_chat.py` → **4 passed** (100%)
2. **12-Step End-to-End Verification:**
   `python backend/verify_12_step_chat.py` → **12/12 Steps Passed** (100%)
3. **Conversations, Security & RBAC:**
   `pytest backend/tests/test_conversations_and_rbac.py backend/tests/test_conversation_security.py` → **8 passed** (100%)
4. **IVR Fallback Demonstration Suite:**
   `pytest backend/tests/test_ivr_fallback.py` → **12 passed** (100%)
5. **SMS Fallback Demonstration Suite:**
   `pytest backend/tests/test_sms_fallback.py` → **13 passed** (100%)
6. **Agricultural Stage & CIBRC Safety Suite:**
   `pytest backend/tests/test_agricultural_stage_safety.py` → **13 passed** (100%)
7. **Officer Dashboard Production Build:**
   `npm --prefix apps/officer-dashboard run build` → **0 errors, 1,584 modules transformed, Vite bundle ready**
8. **Android Build Verification:**
   `./gradlew assembleDebug --no-daemon` → **BUILD SUCCESSFUL**, packaged to `app-debug.apk`.
