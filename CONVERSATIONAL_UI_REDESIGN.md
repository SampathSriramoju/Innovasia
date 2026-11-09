# Bol Kisan — Conversational UI Redesign

## 1. Executive Summary

The previous Bol Kisan farmer home screen suffered from **feature fragmentation and cognitive friction**:
1. It forced farmers to pick a specific crop from a dropdown menu before asking any agricultural question.
2. It presented a cluttered dashboard with separate buttons for "Disease Camera", "Pesticides", "Mandi Prices", "Weather Advisory", and "Voice Assistant".
3. The disease diagnosis workflow operated in an isolated camera screen detached from the conversation history.
4. The top bar was burdened with redundant technical badges (`Gemini 2.5 Flash Connected`, `AI-Connected`, `Extension Network Active`), overwhelming low-literacy rural users.

This redesign transforms Bol Kisan into **ONE unified, chat-first conversational agricultural assistant**. Farmers can now ask any question in Telugu, Hindi, or English about any crop, pest, fertilizer, irrigation practice, natural farming recipe, or weather advisory directly from a single conversational interface.

---

## 2. Architecture & Design Principles

```
┌────────────────────────────────────────────────────────────────────────┐
│ TOP BAR: "BOL KISAN"  [ONLINE ● / OFFLINE ●]  [Language] [Offline] [Tickets]│
├────────────────────────────────────────────────────────────────────────┤
│ CHAT STREAM (LazyColumn):                                              │
│                                                                        │
│  [User Bubble (Right)]: "వరిలో అగ్గి తెగులు నివారణ మందులు ఏమిటి?"        │
│                                                                        │
│  [Bol Kisan Card (Left)]:                                              │
│   • Avatar: 🌱 Bol Kisan  [🌾 Rice]  [🟢 Online AI]  [🔊 Play TTS]      │
│   • Response: "వరిలో అగ్గి తెగులు నివారణకు ట్రైసైక్లాజోల్ 75% WP..."   │
│   • Source: 📚 ICAR-IIRR Hyderabad & CIBRC Certified                  │
│   • Action: [📞 Escalate to Human Agricultural Officer]               │
│   • Follow-ups: [వరిలో ఎరువుల మోతాదు?] [సేంద్రీయ నివారణ మార్గాలు?]     │
│                                                                        │
│  [User Bubble with Photo]: [📷 Leaf Preview] "ఆకుపై నల్లటి మచ్చలు..."  │
│                                                                        │
│  [Bol Kisan Inline Diagnosis]:                                         │
│   • Avatar: 🌱 Bol Kisan  [🌾 Chilli]  [🟢 Online AI]                 │
│   • Disease: Chilli Anthracnose / Die-back (Colletotrichum)            │
│   • Treatment & CIBRC Spray Recommendations                            │
│                                                                        │
├────────────────────────────────────────────────────────────────────────┤
│ ATTACHMENT PREVIEW (if photo chosen): [📷 Thumbnail] Ready  [❌ Remove] │
├────────────────────────────────────────────────────────────────────────┤
│ BOTTOM COMPOSER:                                                       │
│  [ + ]  [ Type your farming question... ]  [ 🎤 Mic ]  [ ➤ Send ]      │
└────────────────────────────────────────────────────────────────────────┘
```

---

## 3. Top Status Area

### 3.1 Strict Minimalist Connectivity Indicator
- **Online State:**
  - Compact badge displaying: `ONLINE`
  - Pill background: `Color(0xFFDCFCE7)` (soft mint), Border: `Color(0xFF86EFAC)`
  - Active glowing green dot: `Color(0xFF16A34A)`
- **Offline State:**
  - Compact badge displaying: `OFFLINE`
  - Pill background: `Color(0xFFFEF3C7)` (warm amber), Border: `Color(0xFFFDE68A)`
  - Amber dot: `Color(0xFFD97706)`
- **Removed Badges:**
  - Redundant technical labels like `Gemini 2.5 Flash`, `Server 127.0.0.1:8000`, `Active CIBRC Enforcement`, and `Extension Network Synced` have been eliminated from the header.

### 3.2 1-Tap Language Switcher
- Compact language pill in the top header displaying the current language name:
  - `తెలుగు` (Telugu)
  - `हिन्दी` (Hindi)
  - `English` (English)
- Tapping cycles immediately between `te -> hi -> en -> te`.
- Instant application-wide recomposition via observable `BolKisanRepository.languageFlow`.
- Zero logout, zero OTP prompts, and zero loss of conversation context.

---

## 4. Conversational Chat Stream

### 4.1 Welcome State
When no prior messages exist, a friendly welcome card provides immediate guidance:
- Title: *"నమస్కారం! బోల్ కిసాన్ కి స్వాగతం"* / *"नमस्ते! बोल किसान में आपका स्वागत है"* / *"Welcome to Bol Kisan!"*
- Subtitle: Clear instructions that the farmer can ask about any crop, pest, disease, fertilizer, or organic practice, or attach a photo.
- Starter suggestion chips covering varied domains (Cotton, Paddy, Tomato, Natural Farming, Weather). Tapping any chip immediately submits the question.

### 4.2 Farmer Message Bubbles
- Right-aligned chat bubble (`Color(0xFFDCFCE7)`).
- If a photo was attached, displays a rounded thumbnail (`140.dp` height) directly above the query text.
- Supports multi-turn questions without forcing crop re-selection.

### 4.3 Bol Kisan Response Cards
Each response card delivers complete agricultural intelligence:
1. **Header:**
   - Identity avatar (`🌱`).
   - Automatically identified crop badge (e.g. `🌾 Cotton`, `🌾 Rice`, `🌾 Tomato`).
   - Grounding origin badge:
     - `🟢 Online AI`: Generated via Gemini 2.5 Flash grounded on CIBRC & ICAR vector context.
     - `⚡ Offline SLM`: On-device Gemma-2B quantized edge inference.
     - `📦 Verified ICAR`: Deterministic Room SQLite / FTS5 retrieval.
     - `⏳ Outbox Queued`: Offline pending ticket saved for background sync.
     - `❓ Clarification`: Ambiguity prompt asking the farmer to specify their crop.
   - Text-to-Speech (TTS) button with active speaking state animation and Stop control.
2. **Body:**
   - Structured, clean agricultural advice.
   - Safety Alert Banner (red border) if unsafe or unapproved chemical detected.
3. **Verified Citations:**
   - Displayed clearly with `📚 ICAR / CIBRC Certified Guidelines`.
4. **Human Extension Officer Escalation:**
   - Built-in button: *"Escalate to Human Officer"* / *"అధికారికి పంపు"*.
   - When tapped, calls `escalateToHumanOfficer(queryText, cropName)`, creates a ticket in the backend database, and updates the card inline with the assigned ticket number.
5. **Dynamic Follow-Up Suggestion Chips:**
   - Interactive chips beneath each response (e.g., *"సేంద్రీయ నివారణ మార్గాలు?"*, *"ఎరువుల మోతాదు ఎంత?"*).
   - Tapping any chip submits that question into the active multi-turn thread.

---

## 5. Unified Chat Composer & Attachment Workflow

### 5.1 Composer Layout
Located at the bottom of the screen with touch targets exceeding 48dp for field accessibility:
```
[ + ] [ Type your farming question... ] [ 🎤 Mic ] [ ➤ Send ]
```

### 5.2 The `+` Attachment Button & Bottom Sheet
- The `+` button is situated **inside the chat composer** on the bottom left.
- Tapping it opens a clean `ModalBottomSheet` with two simple options:
  1. **Take Photo (`📷`):** Launches `ActivityResultContracts.TakePicturePreview()` to capture leaf, pest, or crop symptoms using the camera.
  2. **Choose from Gallery (`🖼️`):** Launches `ActivityResultContracts.GetContent()` (`image/*`) to select an existing photo.

### 5.3 Inline Photo Attachment & Removal
- When a photo is captured or selected, an attachment preview strip renders directly above the input bar:
  - Displays a 46x46dp cropped thumbnail.
  - Displays status: *"Ready for disease diagnosis"*.
  - A red `[ ❌ ]` button allows instant removal or replacement.
- The farmer can type an optional question (e.g. *"ఆకులపై మచ్చలు ఉన్నాయి, ఏమి చేయాలి?"*) or speak via voice.
- Tapping **Send** submits both image and text to `BolKisanRepository.askQuestionWithAttachment`.
- The multimodal disease diagnosis runs, returns structured symptoms, severity, CIBRC treatment, and preventive measures, and **displays the diagnosis directly inside the active conversation**.
- The farmer never leaves the chat screen.

---

## 6. Implementation Verification

| Component | Source File | Status |
| :--- | :--- | :--- |
| Unified Chat Screen | `apps/farmer-android/.../presentation/home/HomeScreen.kt` | Verified & Built |
| Optional Crop & Multi-turn API | `backend/app/api/v1/endpoints/voice.py` | Verified & Passing |
| Multi-turn Repository | `apps/farmer-android/.../data/repository/BolKisanRepository.kt` | Verified & Built |
| Android APK Build | `release/bolkisan-latest.apk` (20.5 MB) | Built Successfully |
