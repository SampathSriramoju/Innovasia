# Crop Context & Voice API Changes

## 1. Motivation

Historically, `VoiceQueryRequest` defined `crop_name: str`, treating crop selection as a mandatory precondition. In Android, `BolKisanRepository.askQuestion` always populated this field with whatever crop was active in user preferences (defaulting to `"Cotton"`).

This created two critical flaws:
1. When a farmer asked about Rice or Tomato, the client still sent `crop_name="Cotton"`.
2. Multi-turn conversational questions lost their context because the client could not pass preceding message turns to the server.

---

## 2. Updated API Schemas

### 2.1 Backend Endpoint: `POST /api/v1/voice/query`

#### Request Schema: `VoiceQueryRequest`
Located in `backend/app/api/v1/endpoints/voice.py`:

```python
class VoiceQueryRequest(BaseModel):
    query_text: str = Field(..., description="Transcribed voice or typed text from the farmer")
    language: Optional[str] = Field("te", description="Language code: 'te', 'hi', or 'en'")
    crop_name: Optional[str] = Field(None, description="Optional explicit crop name. If omitted, automatically identified.")
    conversation_history: Optional[List[Dict[str, Any]]] = Field(
        None,
        description="List of prior conversation turns [{'query': '...', 'answer': '...', 'crop': '...'}]"
    )
    user_location: Optional[Dict[str, str]] = Field(None, description="District, Mandal, State")
```

#### Response Schema: `VoiceQueryResponse`
```python
class VoiceQueryResponse(BaseModel):
    query_text: str
    language: str
    crop_identified: Optional[str] = None
    crop_source: Optional[str] = None # 'detected', 'history_inferred', 'explicit', 'unknown'
    is_ambiguous: bool = False
    clarification_prompt: Optional[str] = None
    status: str # 'answered', 'needs_clarification', 'escalated', 'answered_deterministic_fallback'
    is_safe: bool = True
    response_text: str
    citations: Optional[List[str]] = None
    warnings: Optional[List[str]] = None
    follow_up_suggestions: Optional[List[str]] = None
    ticket_number: Optional[str] = None
    audio_url: Optional[str] = None
```

---

## 3. Android Remote DTOs

Located in `apps/farmer-android/.../data/remote/models/Models.kt`:

### `VoiceQueryDto`
```kotlin
data class VoiceQueryDto(
    @SerializedName("query_text") val queryText: String,
    @SerializedName("language") val language: String = "te",
    @SerializedName("crop_name") val cropName: String? = null, // Optional!
    @SerializedName("conversation_history") val conversationHistory: List<Map<String, String>>? = null,
    @SerializedName("user_location") val userLocation: Map<String, String>? = null
)
```

### `VoiceAnswerDto`
```kotlin
data class VoiceAnswerDto(
    @SerializedName("status") val status: String,
    @SerializedName("is_safe") val isSafe: Boolean = true,
    @SerializedName("response_text") val responseText: String,
    @SerializedName("citations") val citations: List<String>? = null,
    @SerializedName("warnings") val warnings: List<String>? = null,
    @SerializedName("ticket_number") val ticketNumber: String? = null,
    @SerializedName("audio_url") val audioUrl: String? = null,
    @SerializedName("crop_identified") val cropIdentified: String? = null,
    @SerializedName("crop_source") val cropSource: String? = null,
    @SerializedName("is_ambiguous") val isAmbiguous: Boolean = false,
    @SerializedName("clarification_prompt") val clarificationPrompt: String? = null,
    @SerializedName("follow_up_suggestions") val followUpSuggestions: List<String>? = null
)
```

---

## 4. Multi-Turn History Integration

In `backend/app/ai/gemini_service.py`, `generate_agricultural_response` now digests the past conversational turns:

```python
if conversation_history:
    history_lines = []
    for turn in conversation_history[-4:]: # Keep last 4 turns for context window efficiency
        q = turn.get("query", "")
        a = turn.get("answer", "")
        c = turn.get("crop", "")
        if q and a:
            history_lines.append(f"Farmer: {q} (Crop: {c})\nAssistant: {a[:150]}...")
    if history_lines:
        conversation_context = "\nPREVIOUS CONVERSATION CONTEXT:\n" + "\n".join(history_lines) + "\n"
```

This context is injected into the Gemini system prompt:
```
PREVIOUS CONVERSATION CONTEXT:
Farmer: వరిలో అగ్గి తెగులు నివారణ మందులు ఏమిటి? (Crop: Rice)
Assistant: వరిలో అగ్గి తెగులు నివారణకు ట్రైసైక్లాజోల్ 75% WP 0.6 గ్రా/లీటరు...

CURRENT FARMER QUESTION:
మరి ఎరువుల మోతాదు ఎంత వేయాలి?
```

Gemini immediately understands that *"మరి ఎరువుల మోతాదు ఎంత వేయాలి?"* refers to fertilizer management for **Rice**, without requiring the farmer to re-state "వరి" in every message.

---

## 5. Backward Compatibility

All API changes are 100% backward compatible:
- `crop_name` is optional (`None` default); older clients passing `crop_name` continue to work without error (`crop_source = "explicit"`).
- `conversation_history` is optional (`None` default).
- Response fields added are non-breaking additions in JSON serialization.
