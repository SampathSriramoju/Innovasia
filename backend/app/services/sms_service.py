import asyncio
import json
import logging
import math
import os
import re
import uuid
from datetime import datetime, timezone
from pathlib import Path
from typing import Dict, Any, List, Optional, Tuple, Set

from app.core.logging import logger
from app.ai.query_understanding import QueryUnderstanding
from app.safety.safety_gate import safety_gate
from app.ai.rag_service import rag_service
from app.ai.gemini_service import gemini_service

# GSM 03.38 Basic and Extended Character Sets
# Basic characters take 1 septet (7 bits)
GSM7_BASIC_CHARS: Set[str] = set(
    "@£$¥èéùìòÇ\nØø\rÅåΔ_ΦΓΛΩΠΨΣΘΞ\x1bÆæßÉ !\"#¤%&'()*+,-./0123456789:;<=>?¡"
    "ABCDEFGHIJKLMNOPQRSTUVWXYZÄÖÑÜ§¿abcdefghijklmnopqrstuvwxyzäöñüà"
)
# Extended characters require a GSM 0x1B escape code, taking 2 septets
GSM7_EXTENDED_CHARS: Set[str] = set("^{}\\[~]|€")


def get_sms_segment_details(text: str) -> Dict[str, Any]:
    """
    Computes rigorous telecom SMS segmentation and encoding per 3GPP TS 23.040 and ETSI GSM 03.38.

    Rules:
    1. GSM-7:
       - Single segment: Up to 160 septets.
       - Concatenated (multi-part): 153 septets per segment (160 - 7 septets for 6-byte UDH).
       - Basic characters = 1 septet; Extended characters (^{}\[~]|€) = 2 septets.
       - Example: 320 septets = ceil(320 / 153) = 3 segments (NOT 2 segments!).

    2. UCS-2 (Unicode - Telugu, Hindi, Devanagari, Emoji, etc.):
       - Triggered whenever ANY character is outside the GSM-7 alphabet.
       - Single segment: Up to 70 UTF-16 code units (140 bytes / 2 bytes per unit).
       - Concatenated (multi-part): 67 UTF-16 code units per segment (134 bytes / 2 bytes per unit, 6-byte UDH).
       - Example: A 320-character Telugu or Hindi text = ceil(320 / 67) = 5 segments (NOT 2 segments!).
    """
    if not text:
        return {
            "char_count": 0,
            "encoding": "GSM-7",
            "is_unicode": False,
            "units": 0,
            "segments": 0,
            "max_single": 160,
            "max_multipart": 153,
            "explanation": "Empty message (0 segments)"
        }

    # Test if text strictly contains only GSM-7 characters
    is_gsm7 = True
    septet_count = 0
    for ch in text:
        if ch in GSM7_BASIC_CHARS:
            septet_count += 1
        elif ch in GSM7_EXTENDED_CHARS:
            septet_count += 2
        else:
            is_gsm7 = False
            break

    if is_gsm7:
        units = septet_count
        if units <= 160:
            segments = 1 if units > 0 else 0
        else:
            segments = math.ceil(units / 153)
        return {
            "char_count": len(text),
            "encoding": "GSM-7",
            "is_unicode": False,
            "units": units,
            "segments": segments,
            "max_single": 160,
            "max_multipart": 153,
            "explanation": f"GSM-7 encoding: {units} septets -> {segments} segment(s) (160 single / 153 multi-part)"
        }
    else:
        # UCS-2 Unicode (Telugu, Hindi, Emoji, etc.)
        # Count UTF-16 code units (2 bytes per unit)
        ucs2_units = len(text.encode("utf-16-be")) // 2
        if ucs2_units <= 70:
            segments = 1 if ucs2_units > 0 else 0
        else:
            segments = math.ceil(ucs2_units / 67)
        return {
            "char_count": len(text),
            "encoding": "UCS-2 (Unicode)",
            "is_unicode": True,
            "units": ucs2_units,
            "segments": segments,
            "max_single": 70,
            "max_multipart": 67,
            "explanation": f"UCS-2 Unicode encoding: {ucs2_units} code units -> {segments} segment(s) (70 single / 67 multi-part)"
        }


def calculate_sms_segments(text: str) -> int:
    """
    Returns standard telecom SMS segment count for a given text.
    Handles GSM-7 (160/153) and Unicode (70/67) accurately.
    """
    return get_sms_segment_details(text)["segments"]


def format_sms_text(text: str, max_chars: int = 320) -> str:
    """
    Cleans SMS response text:
    - Strips markdown formatting (bold, italic, bullets, headers, backticks).
    - Removes bracketed citations like (KB07), [KB-DOC-01].
    - Normalizes multiple spaces and newlines.
    - PRESERVES all safety-critical instructions:
      * Escalation warnings and tracking ticket numbers (ESC-...).
      * Pre-Harvest Intervals (PHI) in days.
      * Personal Protective Equipment (PPE) instructions (masks, gloves).
      * Exact approved dosages and dilution water volumes.
    """
    if not text:
        return ""

    # Strip markdown symbols
    cleaned = re.sub(r'[*#_`~>\[\]]', '', text)
    cleaned = re.sub(r'^\s*[-•]\s*', '', cleaned, flags=re.MULTILINE)
    cleaned = re.sub(r'\(?(?:KB-?DOC-?\d+|KB\d+)\)?', '', cleaned, flags=re.IGNORECASE)
    cleaned = re.sub(r'\r\n|\r|\n', ' ', cleaned)
    cleaned = re.sub(r'\s{2,}', ' ', cleaned).strip()

    # If within length, return immediately
    if len(cleaned) <= max_chars:
        return cleaned

    # Safety check: If text contains an escalation ticket or critical warning,
    # NEVER truncate away the safety instruction or ticket number!
    ticket_match = re.search(r'(?:ESC-\d{8}-[A-F0-9]{4,8}|TK-[A-Z0-9-]+)', cleaned, re.IGNORECASE)
    has_critical_safety = (
        ticket_match is not None
        or "భద్రతా హెచ్చరిక" in cleaned
        or "సురక్షా చేతావని" in cleaned
        or "सुरक्षा चेतावनी" in cleaned
        or "Safety Warning" in cleaned
        or "CIBRC" in cleaned
    )

    # Extract safety-critical statements that MUST be preserved
    safety_statements: List[str] = []

    # 1. Ticket statement
    if ticket_match:
        # Find sentence or clause containing ticket
        ticket_str = ticket_match.group(0)
        safety_statements.append(f"Ticket: {ticket_str}")

    # 2. Pre-Harvest Interval (PHI)
    phi_pattern = re.search(r'(?:(?:Pre-Harvest Interval|PHI|కోతకు ముందు వ్యవధి|తుడాయి పూర్వ ప్రతీక్ష|तुड़ाई पूर्व प्रतीक्षा)[^.]+?\d+\s*(?:రోజులు|दिन|days))', cleaned, re.IGNORECASE)
    if phi_pattern:
        safety_statements.append(phi_pattern.group(0).strip())

    # 3. PPE requirements
    ppe_pattern = re.search(r'(?:(?:మాస్క్|గ్లౌజులు|మాస్క్ మరియు గ్లౌజులు|मास्क व दस्ताने|protective mask and gloves)[^.]+?(?:ధరించండి|పహనే|wear))', cleaned, re.IGNORECASE)
    if ppe_pattern:
        safety_statements.append(ppe_pattern.group(0).strip())

    # If this is a statutory escalation message, preserve the whole message intact
    # (Safety rules strictly forbid cutting off escalation instructions or ticket references)
    if has_critical_safety and ticket_match:
        # Escalation messages are already concise (~120-180 chars); preserve completely
        return cleaned

    # Attempt intelligent punctuation truncation
    truncated = cleaned[:max_chars]
    for punct in ['. ', '। ', '? ', '! ', '.', '।', '?', '!']:
        last_idx = truncated.rfind(punct)
        if last_idx > 180:
            candidate = truncated[:last_idx + len(punct)].strip()
            # Ensure safety statements weren't dropped
            missing_safety = [s for s in safety_statements if s.lower() not in candidate.lower()]
            if not missing_safety:
                return candidate
            else:
                # Append missing safety statement(s), truncating candidate earlier if needed to stay within max_chars
                safety_suffix = " " + " ".join(missing_safety)
                allowed_candidate_len = max_chars - len(safety_suffix)
                if allowed_candidate_len > 50:
                    sub_candidate = candidate[:allowed_candidate_len]
                    for sub_punct in ['. ', '। ', '? ', '! ', '.', '।', ' ', ', ']:
                        sub_idx = sub_candidate.rfind(sub_punct)
                        if sub_idx > 40:
                            sub_candidate = sub_candidate[:sub_idx].strip()
                            break
                    combined = sub_candidate + safety_suffix
                    return combined.strip()
                combined = candidate + safety_suffix
                return combined[:max_chars].strip()

    # Space truncation fallback
    last_space = truncated.rfind(' ')
    if last_space > 200:
        candidate = truncated[:last_space].strip() + "..."
        missing_safety = [s for s in safety_statements if s.lower() not in candidate.lower()]
        if missing_safety:
            safety_suffix = " " + " ".join(missing_safety)
            allowed_candidate_len = max_chars - len(safety_suffix) - 3
            if allowed_candidate_len > 50:
                candidate = candidate[:allowed_candidate_len].strip() + "..."
            combined = candidate + safety_suffix
            return combined[:max_chars].strip()
        return candidate.strip()

    return truncated[:max_chars - 3].strip() + "..."


class MockSMSProvider:
    """
    Bol Kisan Mock SMS Gateway & Resilient Fallback Pipeline.
    Manages in-memory and persistent SMS state, phone-number-based multi-turn conversation
    memory, idempotency deduplication by unique message ID, CIBRC statutory safety verification,
    RAG retrieval, and concise plain-text SMS payload formatting.
    """

    PROVIDER_NAME = "BOL_KISAN_MOCK_GATEWAY"
    DEFAULT_STORAGE_PATH = Path(__file__).resolve().parent.parent / "data" / "mock_sms_store.json"

    def __init__(self, storage_path: Optional[Path] = None, idempotency_window_seconds: int = 60):
        self.storage_path = storage_path or self.DEFAULT_STORAGE_PATH
        self.idempotency_window_seconds = idempotency_window_seconds

        # In-memory store: normalized phone number -> list of SMS messages
        self._conversations: Dict[str, List[Dict[str, Any]]] = {}

        # Deduplication index: message_id -> { "timestamp": float, "response": dict, "sender": str }
        self._dedup_cache: Dict[str, Dict[str, Any]] = {}

        # Global simulated gateway failure toggle
        self._failure_mode: bool = False

        # Gateway operational statistics
        self._stats = {
            "total_inbound": 0,
            "total_outbound": 0,
            "total_failed": 0,
            "total_duplicates": 0
        }

        self._load_persistent_store()

    def _normalize_phone(self, phone: str) -> str:
        """
        Normalizes phone number to strict standardized format:
        e.g. '+91 98480 12345', '9848012345', '919848012345' -> '+919848012345'.
        Guarantees sender isolation and robust lookup.
        """
        if not phone:
            return "+910000000000"
        p_str = phone.strip()
        # Remove all whitespace, dashes, parentheses
        digits_only = re.sub(r'\D', '', p_str)

        if not digits_only:
            return "+910000000000"

        # If 10 digits (standard Indian mobile without country code)
        if len(digits_only) == 10:
            return f"+91{digits_only}"
        # If 12 digits starting with 91
        elif len(digits_only) == 12 and digits_only.startswith("91"):
            return f"+{digits_only}"
        # If 11 digits starting with 0
        elif len(digits_only) == 11 and digits_only.startswith("0"):
            return f"+91{digits_only[1:]}"
        else:
            return f"+{digits_only}"

    def _load_persistent_store(self):
        """Loads persistent SMS store from disk if present."""
        try:
            if self.storage_path.exists():
                with open(self.storage_path, "r", encoding="utf-8") as f:
                    data = json.load(f)
                    self._conversations = data.get("conversations", {})
                    self._stats = data.get("stats", self._stats)
                    logger.info(f"Loaded {len(self._conversations)} SMS conversation threads from {self.storage_path}")
        except Exception as e:
            logger.warning(f"Could not load persistent SMS store from {self.storage_path}: {e}")

    def _save_persistent_store(self):
        """Persists current SMS conversations to disk safely."""
        try:
            self.storage_path.parent.mkdir(parents=True, exist_ok=True)
            temp_path = self.storage_path.with_suffix(".tmp")
            data = {
                "conversations": self._conversations,
                "stats": self._stats,
                "saved_at": datetime.now(timezone.utc).isoformat()
            }
            with open(temp_path, "w", encoding="utf-8") as f:
                json.dump(data, f, ensure_ascii=False, indent=2)
            temp_path.replace(self.storage_path)
        except Exception as e:
            logger.warning(f"Failed to persist SMS store to {self.storage_path}: {e}")

    def toggle_failure_mode(self, simulate_failure: Optional[bool] = None) -> bool:
        """
        Toggles or explicitly sets simulated carrier failure mode.
        """
        if simulate_failure is not None:
            self._failure_mode = bool(simulate_failure)
        else:
            self._failure_mode = not self._failure_mode
        logger.info(f"MockSMSProvider simulated failure mode set to: {self._failure_mode}")
        return self._failure_mode

    def get_history(self, phone_number: str) -> List[Dict[str, Any]]:
        """
        Returns all incoming and outgoing SMS messages for a given phone number.
        Strictly isolated by normalized sender phone number.
        """
        norm_phone = self._normalize_phone(phone_number)
        return list(self._conversations.get(norm_phone, []))

    def clear_history(self, phone_number: str) -> bool:
        """
        Clears SMS history for a specific phone number.
        Guarantees other senders' histories are unaffected.
        """
        norm_phone = self._normalize_phone(phone_number)
        if norm_phone in self._conversations:
            del self._conversations[norm_phone]
            self._save_persistent_store()
            return True
        return False

    def get_status(self) -> Dict[str, Any]:
        """
        Returns gateway health, telemetry, stats, and active phone sessions.
        """
        active_numbers = list(self._conversations.keys())
        return {
            "provider": self.PROVIDER_NAME,
            "status": "simulated_carrier_failure" if self._failure_mode else "operational",
            "simulate_failure": self._failure_mode,
            "is_demo": True,
            "is_simulated": True,
            "stats": {
                "total_inbound": self._stats["total_inbound"],
                "total_outbound": self._stats["total_outbound"],
                "total_failed": self._stats["total_failed"],
                "total_duplicates": self._stats["total_duplicates"],
                "active_phone_sessions": len(active_numbers)
            },
            "active_phone_numbers": active_numbers
        }

    def _create_escalation_ticket(
        self,
        sender_number: str,
        reason: str,
        query_transcript: str,
        crop: Optional[str] = None
    ) -> Tuple[str, Optional[str]]:
        """
        Creates statutory escalation ticket starting with ESC- and persists into SQLite / Postgres DB.
        """
        ticket_num = f"ESC-{datetime.now().strftime('%Y%m%d')}-{uuid.uuid4().hex[:6].upper()}"
        ticket_id = None
        try:
            from sqlalchemy import func
            from app.database.session import SessionLocal
            from app.database.models import AgriculturalTicket, TicketMessage, User, Crop
            clean_digits = re.sub(r'\D', '', sender_number)
            with SessionLocal() as db:
                user = db.query(User).filter(
                    (User.phone_number == sender_number) |
                    (User.phone_number == clean_digits) |
                    (User.phone_number.endswith(clean_digits[-10:] if len(clean_digits) >= 10 else clean_digits))
                ).first()
                if not user:
                    user = db.query(User).filter(User.role == "farmer").first()
                if not user:
                    user = db.query(User).first()

                farmer_id = user.id if user else str(uuid.uuid4())
                crop_obj = None
                if crop:
                    crop_obj = db.query(Crop).filter(
                        (func.lower(Crop.name_en) == crop.lower()) |
                        (func.lower(Crop.name_te) == crop.lower()) |
                        (func.lower(Crop.name_hi) == crop.lower())
                    ).first()

                farmer_profile = user.farmer_profile if user else None
                state = farmer_profile.state if farmer_profile else "Telangana"
                district = farmer_profile.district if farmer_profile else "Warangal"
                mandal = farmer_profile.mandal if farmer_profile else "Geesugonda"
                village = farmer_profile.village if farmer_profile else "Dharmaram"

                ticket = AgriculturalTicket(
                    ticket_number=ticket_num,
                    farmer_id=farmer_id,
                    crop_id=crop_obj.id if crop_obj else None,
                    state=state,
                    district=district,
                    mandal=mandal,
                    village=village,
                    status="PENDING",
                    priority="high",
                    escalation_reason=reason or "SMS Safety Gate Escalation",
                    original_question_transcript=query_transcript,
                )
                db.add(ticket)
                db.flush()

                ticket_msg = TicketMessage(
                    ticket_id=ticket.id,
                    sender_id=farmer_id,
                    msg_type="text",
                    content=query_transcript
                )
                db.add(ticket_msg)
                db.commit()
                ticket_id = ticket.id
        except Exception as e:
            logger.warning(f"MockSMSProvider: DB commit for AgriculturalTicket skipped ({e})")

        return ticket_num, ticket_id

    def _resolve_conversation_context(
        self,
        phone_number: str,
        current_text: str
    ) -> Tuple[Optional[str], Optional[str], List[Dict[str, Any]]]:
        """
        Inspects phone conversation history to resolve continuity for follow-up questions
        (e.g., 'What about its dosage?' inheriting crop='Cotton' and entity='pink bollworm').
        Returns (inferred_crop, inferred_target_entity, formatted_history).
        Strictly isolated to this sender's conversation history.
        """
        norm_phone = self._normalize_phone(phone_number)
        history_msgs = self._conversations.get(norm_phone, [])
        formatted_history: List[Dict[str, Any]] = []

        last_crop = None
        last_entity = None

        for msg in history_msgs:
            role = "user" if msg.get("direction") == "inbound" else "assistant"
            txt = msg.get("message_text", "")
            msg_crop = msg.get("crop")
            msg_entity = msg.get("target_entity")

            if msg_crop and not last_crop:
                last_crop = msg_crop
            if msg_entity and not last_entity:
                last_entity = msg_entity

            formatted_history.append({
                "role": role,
                "text": txt,
                "query": txt,
                "crop": msg_crop,
                "target_entity": msg_entity
            })

        # Traverse backwards to find the most recent crop and pest/disease
        for turn in reversed(formatted_history):
            if not last_crop and turn.get("crop"):
                last_crop = turn["crop"]
            if not last_entity and turn.get("target_entity"):
                last_entity = turn["target_entity"]
            if last_crop and last_entity:
                break

        # Also search previous text turns if entity wasn't explicitly tagged
        if not last_entity:
            for turn in reversed(formatted_history):
                turn_txt = (turn.get("text") or "").lower()
                for canonical_name, aliases in QueryUnderstanding.TARGET_ENTITIES.items():
                    if any(alias.lower() in turn_txt for alias in aliases):
                        last_entity = canonical_name
                        break
                if last_entity:
                    break

        return last_crop, last_entity, formatted_history

    def _generate_deterministic_sms_fallback(
        self,
        crop: Optional[str],
        target_entity: Optional[str],
        language: str,
        approved_product: Optional[Dict[str, Any]],
        rag_chunks: List[Dict[str, Any]]
    ) -> str:
        """
        Builds a concise, deterministic, high-density CIBRC/POP SMS response
        when generative models are unavailable or in test mode.
        Preserves all safety-critical details (active ingredient, dosage, PHI, PPE).
        """
        lang = language if language in ["te", "hi", "en"] else "en"

        if approved_product:
            p = approved_product
            trade = p.get("product_trade_name") or p.get("active_ingredient", "Approved formulation")
            ai = p.get("active_ingredient", "")
            dose = p.get("dosage_per_acre") or p.get("dosage_per_hectare", "Recommended dose")
            phi = p.get("pre_harvest_interval_days", 14)
            c_name = crop or p.get("target_crop", "")
            t_name = target_entity or p.get("target_pest") or p.get("target_disease", "")

            trade_desc = f"{trade} ({ai})" if ai and ai not in trade else trade

            if lang == "te":
                reply = (
                    f"{c_name} {t_name} నివారణ: {trade_desc} మోతాదు ఎకరాకు {dose} (200 లీటర్ల నీటిలో). "
                    f"కోతకు ముందు వ్యవధి (PHI): {phi} రోజులు. పిచికారీ సమయంలో రక్షణ మాస్క్, గ్లౌజులు తప్పక ధరించండి."
                )
            elif lang == "hi":
                reply = (
                    f"{c_name} {t_name} नियंत्रण: {trade_desc} की अनुमोदित मात्रा {dose}/एकड़ (200L पानी में)। "
                    f"तुड़ाई पूर्व प्रतीक्षा (PHI): {phi} दिन। छिड़काव के समय मास्क व दस्ताने जरूर पहनें।"
                )
            else:
                reply = (
                    f"{c_name} {t_name} Control: Use {trade_desc} @ {dose} in 200L water/acre. "
                    f"Pre-Harvest Interval (PHI): {phi} days. Always wear protective mask and gloves."
                )
            return format_sms_text(reply, max_chars=320)

        # If RAG knowledge chunks exist, extract top recommendation
        if rag_chunks:
            top_chunk = rag_chunks[0]
            content = top_chunk.get("content", "")
            first_lines = ". ".join([l.strip() for l in content.split("\n") if len(l.strip()) > 15][:2])

            if lang == "te":
                reply = f"{crop or ''} యాజమాన్యం: {first_lines}"
            elif lang == "hi":
                reply = f"{crop or ''} कृषि सलाह: {first_lines}"
            else:
                reply = f"{crop or ''} Advisory: {first_lines}"
            return format_sms_text(reply, max_chars=320)

        # General POP fallback
        if lang == "te":
            reply = f"{crop or 'పంట'} సలహా: సమీప రైతు వేదిక లేదా మండల వ్యవసాయ అధికారిని (MAO) సంప్రదించండి. రసాయనాల తప్పుడు వాడకం నివారించండి."
        elif lang == "hi":
            reply = f"{crop or 'फसल'} सलाह: नजदीकी कृषि विज्ञान केंद्र या कृषि अधिकारी से संपर्क करें। बिना सलाह रासायनिक छिड़काव से बचें।"
        else:
            reply = f"{crop or 'Crop'} Advisory: Contact your local Agricultural Extension Officer for certified regional POP guidelines."
        return format_sms_text(reply, max_chars=320)

    async def process_incoming_sms(
        self,
        sender_number: str,
        message_text: str,
        language: Optional[str] = None,
        message_id: Optional[str] = None,
        simulate_failure: Optional[bool] = None,
        idempotency_key: Optional[str] = None
    ) -> Dict[str, Any]:
        """
        Full SMS Fallback Ingestion and Processing Pipeline:
        1. Normalization & Failure Check
        2. Idempotency & Deduplication Check (strictly by message_id / idempotency_key)
        3. Conversational Context & Query Understanding (isolated by sender phone)
        4. CIBRC Statutory Safety Gate Verification (Instant Escalation if Unsafe)
        5. Grounded Synthesis (RAG + Gemini or Deterministic CIBRC Fallback)
        6. Precise telecom GSM-7 / Unicode segment accounting and SIMULATED labels
        """
        now_ts = datetime.now(timezone.utc)
        now_iso = now_ts.isoformat()
        now_epoch = now_ts.timestamp()

        norm_phone = self._normalize_phone(sender_number)
        inbound_text = (message_text or "").strip()

        # Idempotency token: explicit message_id or idempotency_key from caller
        effective_client_id = message_id or idempotency_key
        # Internal unique message identifier
        inbound_id = effective_client_id or f"SMS-IN-{uuid.uuid4().hex[:8].upper()}"
        reply_id = f"SMS-OUT-{uuid.uuid4().hex[:8].upper()}"

        self._stats["total_inbound"] += 1

        # Check Simulated Gateway Failure Mode (per-request or global toggle)
        is_failure = bool(simulate_failure) if simulate_failure is not None else self._failure_mode
        if is_failure:
            self._stats["total_failed"] += 1
            logger.warning(f"MockSMSProvider: Simulated Gateway Failure for {norm_phone}")

            in_seg = get_sms_segment_details(inbound_text)
            inbound_record = {
                "message_id": inbound_id,
                "direction": "inbound",
                "sender_number": norm_phone,
                "recipient_number": "56161",
                "message_text": inbound_text,
                "language": language or "unknown",
                "timestamp": now_iso,
                "segments": in_seg["segments"],
                "char_count": len(inbound_text),
                "encoding": in_seg["encoding"],
                "delivery_status": "FAILED",
                "provider": self.PROVIDER_NAME,
                "is_demo": True,
                "is_simulated": True,
                "simulation_label": "SIMULATED - DELIVERY FAILED"
            }
            reply_record = {
                "message_id": reply_id,
                "direction": "outbound",
                "sender_number": "56161",
                "recipient_number": norm_phone,
                "message_text": "",
                "timestamp": now_iso,
                "segments": 0,
                "char_count": 0,
                "encoding": "GSM-7",
                "delivery_status": "FAILED",
                "error": "Simulated SMS Gateway Delivery Failure (Carrier Timeout / Rejected)",
                "provider": self.PROVIDER_NAME,
                "is_demo": True,
                "is_simulated": True,
                "is_safe": True,
                "simulation_label": "SIMULATED - DELIVERY FAILED"
            }

            if norm_phone not in self._conversations:
                self._conversations[norm_phone] = []
            self._conversations[norm_phone].append(inbound_record)
            self._conversations[norm_phone].append(reply_record)
            self._save_persistent_store()

            return {
                "status": "failed",
                "inbound": inbound_record,
                "reply": reply_record,
                "delivery_status": "FAILED",
                "is_demo": True,
                "is_simulated": True,
                "is_duplicate": False,
                "error": "Simulated SMS Gateway Delivery Failure"
            }

        # Step 2: Idempotency & Deduplication Check
        # Rule: ONLY trigger duplicate suppression if the client provided an explicit
        # message_id or idempotency_key that was previously processed within the idempotency window.
        # NEVER suppress legitimate repeated questions sent with a new or omitted message_id.
        if effective_client_id:
            dedup_lookup_key = f"{norm_phone}:{effective_client_id}"
            if dedup_lookup_key in self._dedup_cache:
                cached = self._dedup_cache[dedup_lookup_key]
                if now_epoch - cached["timestamp"] <= self.idempotency_window_seconds:
                    self._stats["total_duplicates"] += 1
                    logger.info(f"MockSMSProvider: Idempotent duplicate request detected for {dedup_lookup_key}")
                    cached_resp = dict(cached["response"])
                    cached_resp["is_duplicate"] = True
                    return cached_resp

        # Step 3: Conversation Continuity Resolution (Sender Isolated)
        history_crop, history_entity, formatted_history = self._resolve_conversation_context(
            norm_phone, inbound_text
        )

        # Analyze current query using QueryUnderstanding with sender-specific history
        analysis = QueryUnderstanding.analyze_query(
            inbound_text,
            context_crop=history_crop,
            conversation_history=formatted_history
        )

        # Resolve language: explicit payload -> detected analysis -> QueryUnderstanding detector -> default 'te'
        resolved_lang = language
        if not resolved_lang or resolved_lang not in ["te", "hi", "en"]:
            resolved_lang = analysis.get("language") or QueryUnderstanding.detect_language(inbound_text)
        if resolved_lang not in ["te", "hi", "en"]:
            resolved_lang = "te"

        effective_crop = analysis.get("crop") or history_crop
        effective_target_entity = analysis.get("target_entity") or history_entity

        # If current query is follow-up referring to dose/spray/control and entity was previously established
        if not analysis.get("target_entity") and history_entity:
            effective_target_entity = history_entity

        # Step 4: CIBRC Statutory Safety Gate Verification
        safety_decision, meta = safety_gate.evaluate_query(
            query_text=inbound_text,
            crop=effective_crop,
            pest_or_disease=effective_target_entity
        )

        ticket_number = None
        ticket_id = None
        reply_text = ""
        is_safe = safety_decision.is_safe

        if safety_decision.needs_escalation:
            # Immediate Statutory Safety Escalation with Tracking Ticket persisted to database
            ticket_number, ticket_id = self._create_escalation_ticket(
                sender_number=norm_phone,
                reason=safety_decision.escalation_reason or "SMS Safety Gate: Unapproved chemical or dosage",
                query_transcript=inbound_text,
                crop=effective_crop
            )
            is_safe = False

            if resolved_lang == "te":
                reply_text = (
                    f"⚠️ భద్రతా హెచ్చరిక: రసాయనం/మోతాదు CIBRC ఆమోదం పొందలేదు. "
                    f"మీ ప్రశ్న మండల వ్యవసాయ అధికారికి (MAO) పంపబడింది. "
                    f"టికెట్ నం: {ticket_number}. పంట నష్టం నివారించండి."
                )
            elif resolved_lang == "hi":
                reply_text = (
                    f"⚠️ सुरक्षा चेतावनी: यह कीटनाशक/मात्रा CIBRC द्वारा स्वीकृत नहीं है। "
                    f"प्रश्न कृषि अधिकारी को भेज दिया गया है। "
                    f"टिकट: {ticket_number}। गलत छिड़काव से फसल बचाएं।"
                )
            else:
                reply_text = (
                    f"⚠️ Safety Warning: Chemical or dose not approved in official CIBRC records. "
                    f"Escalated to Extension Officer. Ticket: {ticket_number}. Avoid unverified spray."
                )
            reply_text = format_sms_text(reply_text, max_chars=320)
        else:
            # Step 5: Verified Synthesis (RAG + Gemini or Deterministic CIBRC Fallback)
            rag_chunks = rag_service.retrieve(query=inbound_text, crop=effective_crop, top_k=2)

            safety_context = {
                "approved_product": safety_decision.approved_product,
                "citation": safety_decision.source_citation
            } if safety_decision.approved_product else None

            # Attempt grounded generation via Gemini with graceful fallback
            gemini_success = False
            try:
                if gemini_service.is_text_available():
                    ai_result = await gemini_service.generate_agricultural_response(
                        query=inbound_text,
                        retrieved_contexts=rag_chunks,
                        language=resolved_lang,
                        safety_context=safety_context,
                        query_analysis=analysis,
                        conversation_history=formatted_history
                    )
                    candidate_text = ai_result.get("text", "")
                    # Post generation safety validation
                    if safety_gate.validate_llm_response(candidate_text, safety_decision):
                        formatted_cand = format_sms_text(candidate_text, max_chars=320)
                        if len(formatted_cand) > 20:
                            reply_text = formatted_cand
                            gemini_success = True
            except Exception as exc:
                logger.warning(f"MockSMSProvider: Gemini synthesis skipped/failed ({exc}), activating deterministic CIBRC/POP fallback.")

            if not gemini_success:
                # Deterministic CIBRC / POP Fallback
                reply_text = self._generate_deterministic_sms_fallback(
                    crop=effective_crop,
                    target_entity=effective_target_entity,
                    language=resolved_lang,
                    approved_product=safety_decision.approved_product,
                    rag_chunks=rag_chunks
                )

        # Ensure final response formatting preserves safety critical instructions
        reply_text = format_sms_text(reply_text, max_chars=320)

        # Compute accurate telecom segmentation details
        inbound_seg = get_sms_segment_details(inbound_text)
        reply_seg = get_sms_segment_details(reply_text)

        # Prepare records with MANDATORY 'SIMULATED' badges and demo labels
        inbound_record = {
            "message_id": inbound_id,
            "direction": "inbound",
            "sender_number": norm_phone,
            "recipient_number": "56161",
            "message_text": inbound_text,
            "language": resolved_lang,
            "timestamp": now_iso,
            "segments": inbound_seg["segments"],
            "char_count": len(inbound_text),
            "encoding": inbound_seg["encoding"],
            "units": inbound_seg["units"],
            "delivery_status": "SIMULATED",
            "simulation_label": "SIMULATED (NOT DELIVERED)",
            "provider": self.PROVIDER_NAME,
            "is_demo": True,
            "is_simulated": True,
            "crop": effective_crop,
            "target_entity": effective_target_entity
        }

        reply_record = {
            "message_id": reply_id,
            "direction": "outbound",
            "sender_number": "56161",
            "recipient_number": norm_phone,
            "message_text": reply_text,
            "language": resolved_lang,
            "timestamp": now_iso,
            "segments": reply_seg["segments"],
            "char_count": len(reply_text),
            "encoding": reply_seg["encoding"],
            "units": reply_seg["units"],
            "delivery_status": "SIMULATED",
            "simulation_label": "SIMULATED (NOT DELIVERED)",
            "provider": self.PROVIDER_NAME,
            "is_demo": True,
            "is_simulated": True,
            "is_safe": is_safe,
            "ticket_number": ticket_number,
            "ticket_id": ticket_id,
            "crop": effective_crop,
            "target_entity": effective_target_entity
        }

        # Store in conversation history (Sender Isolated)
        if norm_phone not in self._conversations:
            self._conversations[norm_phone] = []
        self._conversations[norm_phone].append(inbound_record)
        self._conversations[norm_phone].append(reply_record)
        self._stats["total_outbound"] += 1
        self._save_persistent_store()

        final_response = {
            "status": "processed",
            "inbound": inbound_record,
            "reply": reply_record,
            "delivery_status": "SIMULATED",
            "simulation_label": "SIMULATED (NOT DELIVERED)",
            "is_demo": True,
            "is_simulated": True,
            "is_duplicate": False,
            "segment_details": {
                "inbound": inbound_seg,
                "outbound": reply_seg
            }
        }

        # Cache for idempotency window ONLY if caller passed a unique message_id or idempotency_key
        if effective_client_id:
            dedup_lookup_key = f"{norm_phone}:{effective_client_id}"
            self._dedup_cache[dedup_lookup_key] = {"timestamp": now_epoch, "response": final_response}

        return final_response


# Global singleton instance
sms_service = MockSMSProvider()
