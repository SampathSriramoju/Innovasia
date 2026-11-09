# Chat Attachment Implementation & Multimodal Disease Diagnosis

## 1. Overview

In the earlier architecture, disease diagnosis was housed in a disconnected screen (`DiseaseCameraScreen`), requiring farmers to leave the voice assistant, navigate to a separate camera, select plant parts, and view isolated diagnosis cards that were not recorded in the main conversation history.

The unified conversational interface consolidates this workflow:
- The camera is no longer a separate standalone shortcut.
- A **`+` Attachment Button** is integrated directly into the chat composer.
- Photos captured or selected from the device are displayed inline in the active conversation.
- The diagnosis runs automatically and responds in the same chat thread.

---

## 2. In-Composer Attachment Architecture

```
                  ┌──────────────────────────────────────────────┐
                  │             Chat Composer Area               │
                  │   [ + ]  [ Text Field ]  [ 🎤 Mic ]  [ ➤ ]   │
                  └──────────────────────┬───────────────────────┘
                                         │
                                   Taps [ + ]
                                         │
                                         ▼
                         ┌───────────────────────────────┐
                         │       Modal Bottom Sheet      │
                         │  📷 Take Photo                │
                         │  🖼️ Choose from Gallery       │
                         └───────────────┬───────────────┘
                                         │
                                   Selects Photo
                                         │
                                         ▼
                         ┌───────────────────────────────┐
                         │   Inline Attachment Preview   │
                         │  [📷 Thumbnail] Ready  [❌]    │
                         │  [ Optional farmer text... ]  │
                         └───────────────┬───────────────┘
                                         │
                                    Taps Send
                                         │
                                         ▼
                     `askQuestionWithAttachment(query, photo)`
                                         │
                   ┌─────────────────────┴─────────────────────┐
                   ▼                                           ▼
          [Online Diagnosis]                         [Offline Outbox]
       POST /api/v1/disease/diagnose         Saved in filesDir + Pending Ticket
                   │                                           │
                   ▼                                           ▼
      Rendered Inline in Chat Stream              Rendered with Verified Offline Card
```

---

## 3. Component Details

### 3.1 Modal Bottom Sheet
Implemented in `HomeScreen.kt`:
```kotlin
if (showAttachmentSheet) {
    ModalBottomSheet(
        onDismissRequest = { showAttachmentSheet = false },
        containerColor = CardWhite,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
    ) {
        // Option 1: Take Photo -> ActivityResultContracts.TakePicturePreview()
        // Option 2: Choose Gallery -> ActivityResultContracts.GetContent() ("image/*")
    }
}
```

### 3.2 Thumbnail Preview & Removal
- If `attachedPhotoBitmap != null`, an attachment preview bar appears directly above the input box.
- The farmer can tap `[ ❌ ]` to cancel or replace the photo before sending.
- Accepts optional voice input or typed description to accompany the image.

### 3.3 Seamless Diagnosis in Chat History
- When submitted, `BolKisanRepository.askQuestionWithAttachment` runs.
- The user's bubble displays the attached photo thumbnail.
- Bol Kisan's response bubble presents the diagnosis:
  - **Identified Crop & Disease:** e.g. `Chilli Thrips & Murda Virus`.
  - **Severity:** e.g. `Moderate (78% confidence)`.
  - **Symptoms:** Physical presentation on leaves and shoots.
  - **Treatment:** CIBRC approved formulation (e.g. `Fipronil 5% SC @ 2ml/L` or `5% Neem Seed Kernel Extract`).
  - **Prevention:** Crop rotation and clean seedling nursery management.
  - **Citations:** `📚 ICAR-IIHR Bangalore Plant Pathology Package`.
  - **Follow-up Chips:** *"సేంద్రీయ నివారణ మార్గాలు?"*, *"పురుగు మందు మోతాదు ఎంత?"*.
