import asyncio
import hashlib
import json
import logging
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

IVR_STORE_PATH = Path(__file__).resolve().parent.parent / "data" / "mock_ivr_store.json"

PROVIDER_NAME = "BOL_KISAN_MOCK_IVR"
SIMULATION_LABEL = "SIMULATED IVR (HACKATHON DEMO - NO REAL TELECOM CALL)"


def format_ivr_spoken_text(text: str) -> str:
    """
    Cleans response text to ensure it sounds natural when synthesized by Text-to-Speech (TTS):
    - Strips markdown formatting (*, #, _, `, ~, >, bullets).
    - Removes document citation tags like [KB01], (KB-DOC-02).
    - Preserves safety warnings, ticket numbers (ESC-...), and exact chemical dosages.
    - Normalizes excessive whitespace and line breaks.
    """
    if not text:
        return ""
    cleaned = re.sub(r'[*#_`~>\[\]]', '', text)
    cleaned = re.sub(r'^\s*[-•]\s*', '', cleaned, flags=re.MULTILINE)
    cleaned = re.sub(r'\(?(?:KB-?DOC-?\d+|KB\d+)\)?', '', cleaned, flags=re.IGNORECASE)
    cleaned = re.sub(r'\r\n|\r|\n', ' ', cleaned)
    cleaned = re.sub(r'\s{2,}', ' ', cleaned).strip()
    return cleaned


class IVRCallSession:
    """
    Encapsulates state, configuration, and dialogue history for an IVR call session.
    """
    def __init__(
        self,
        call_id: str,
        caller_number: str,
        language: Optional[str] = None,
        metadata: Optional[Dict[str, Any]] = None
    ):
        now_iso = datetime.now(timezone.utc).isoformat()
        self.call_id: str = call_id
        self.caller_number: str = caller_number
        self.status: str = "ringing"  # 'ringing', 'active', 'ended', 'escalated', 'failed'
        self.started_at: str = now_iso
        self.ended_at: Optional[str] = None
        self.language: str = language if language in ["te", "hi", "en"] else "te"
        self.language_selected: bool = bool(language in ["te", "hi", "en"])
        self.dtmf_history: List[Dict[str, Any]] = []
        self.conversation_history: List[Dict[str, Any]] = []
        self.context_crop: Optional[str] = None
        self.context_entity: Optional[str] = None
        self.ticket_number: Optional[str] = None
        self.ticket_id: Optional[str] = None
        self.escalation_reason: Optional[str] = None
        self.error_history: List[Dict[str, Any]] = []
        self.metadata: Dict[str, Any] = metadata or {}
        self.last_prompt: Optional[Dict[str, Any]] = None

    @property
    def duration_seconds(self) -> int:
        try:
            start_dt = datetime.fromisoformat(self.started_at)
            end_dt = datetime.fromisoformat(self.ended_at) if self.ended_at else datetime.now(timezone.utc)
            delta = end_dt - start_dt
            return max(0, int(delta.total_seconds()))
        except Exception:
            return 0

    def to_dict(self) -> Dict[str, Any]:
        return {
            "call_id": self.call_id,
            "caller_number": self.caller_number,
            "status": self.status,
            "started_at": self.started_at,
            "ended_at": self.ended_at,
            "duration_seconds": self.duration_seconds,
            "language": self.language,
            "language_selected": self.language_selected,
            "dtmf_history": list(self.dtmf_history),
            "conversation_history": list(self.conversation_history),
            "context_crop": self.context_crop,
            "context_entity": self.context_entity,
            "ticket_number": self.ticket_number,
            "ticket_id": self.ticket_id,
            "escalation_reason": self.escalation_reason,
            "error_history": list(self.error_history),
            "last_prompt": self.last_prompt,
            "metadata": self.metadata,
            "is_demo": True,
            "is_simulated": True,
            "provider": PROVIDER_NAME,
            "simulation_label": SIMULATION_LABEL
        }


class MockIVRProvider:
    """
    Simulated Telecom IVR Provider for Bol Kisan hackathon demo.
    Implements state machine for multi-lingual IVR menu, DTMF navigation,
    Voice Question Answering with CIBRC safety validation, error simulations,
    and automatic escalation to Mandal Agricultural Extension Officers (MAO).
    """
    PROVIDER_NAME = PROVIDER_NAME
    SIMULATION_LABEL = SIMULATION_LABEL

    # Standard IVR Welcome Prompts
    PROMPTS_MENU = {
        "multilingual_welcome": (
            "నమస్కారం, బోల్ కిసాన్ ఐవీఆర్ సేవలకు స్వాగతం. తెలుగు కొరకు 1 నొక్కండి. "
            "| नमस्कार, बोल किसान आईवीआर सेवा में आपका स्वागत है। हिंदी के लिए 2 दबाएं। "
            "| Welcome to Bol Kisan IVR service. For English, press 3."
        ),
        "te": {
            "welcome": "నమస్కారం, బోల్ కిసాన్ ఐవీఆర్ సేవలకు స్వాగతం. తెలుగు కొరకు 1, హిందీ కొరకు 2, ఇంగ్లీష్ కొరకు 3 నొక్కండి. అధికారికి మాట్లాడటానికి 9, కాల్ ముగించడానికి 0 నొక్కండి.",
            "language_ack": "మీరు తెలుగు భాషను ఎంచుకున్నారు. మీ పంట సమస్య, తెగులు లేదా మార్కెట్ ధరల ప్రశ్న మాట్లాడండి. అధికారికి బదిలీ కొరకు 9, కాల్ ముగించడానికి 0 నొక్కండి.",
            "unrecognized_dtmf": "చెల్లని కీ నొక్కారు. తెలుగుకు 1, హిందీకి 2, ఇంగ్లీష్‌కు 3, అధికారి కొరకు 9, లేదా కాల్ ముగించడానికి 0 నొక్కండి.",
            "silence_timeout": "మీ స్వరం వినపడలేదు. దయచేసి మీ ప్రశ్నను మరలా మాట్లాడండి లేదా 9 నొక్కి అధికారిని కలవండి.",
            "speech_recognition_failure": "క్షమించండి, మీ ప్రశ్న స్పష్టంగా అర్థం కాలేదు. దయచేసి నెమ్మదిగా మరలా మాట్లాడండి.",
            "goodbye": "బోల్ కిసాన్ ఐవీఆర్ సేవలకు కాల్ చేసినందుకు ధన్యవాదాలు. శుభదినం."
        },
        "hi": {
            "welcome": "नमस्कार, बोल किसान आईवीआर सेवा में आपका स्वागत है। हिंदी के लिए 2, तेलुगु के लिए 1, अंग्रेजी के लिए 3 दबाएं। कृषि अधिकारी से बात करने के लिए 9, कॉल समाप्त करने के लिए 0 दबाएं।",
            "language_ack": "आपने हिंदी भाषा चुनी है। अपनी फसल की समस्या, कीट या मंडी भाव का सवाल बोलें। कृषि अधिकारी से बात करने के लिए 9, कॉल समाप्त करने के लिए 0 दबाएं।",
            "unrecognized_dtmf": "अमान्य बटन दबाया गया। हिंदी के लिए 2, तेलुगु के लिए 1, अंग्रेजी के लिए 3, अधिकारी के लिए 9, या कॉल समाप्त करने के लिए 0 दबाएं।",
            "silence_timeout": "आपकी आवाज़ नहीं सुनाई दी। कृपया अपना प्रश्न दोबारा बोलें या 9 दबाकर अधिकारी से बात करें।",
            "speech_recognition_failure": "क्षमा करें, आपका प्रश्न स्पष्ट नहीं था। कृपया धीरे और स्पष्ट बोलें।",
            "goodbye": "बोल किसान आईवीआर सेवा पर कॉल करने के लिए धन्यवाद। आपका दिन शुभ हो।"
        },
        "en": {
            "welcome": "Welcome to Bol Kisan IVR service. Press 1 for Telugu, 2 for Hindi, 3 for English. Press 9 to transfer to an Agricultural Officer, or 0 to end the call.",
            "language_ack": "You have selected English. Please speak your crop disease, pest issue, or market price query. Press 9 for Extension Officer, or 0 to end call.",
            "unrecognized_dtmf": "Invalid key pressed. Press 1 for Telugu, 2 for Hindi, 3 for English, 9 for an Officer, or 0 to end call.",
            "silence_timeout": "We did not hear any speech. Please repeat your question or press 9 to speak with an officer.",
            "speech_recognition_failure": "Sorry, we could not understand your speech clearly. Please speak slowly and clearly.",
            "goodbye": "Thank you for calling Bol Kisan IVR service. Have a wonderful day."
        }
    }

    def __init__(self):
        self._sessions: Dict[str, IVRCallSession] = {}
        self._stats: Dict[str, int] = {
            "total_calls": 0,
            "active_calls": 0,
            "escalated_calls": 0,
            "ended_calls": 0,
            "failed_calls": 0,
            "dtmf_inputs": 0,
            "voice_queries": 0,
            "errors_simulated": 0
        }
        self._load_persistent_store()

    def _normalize_phone(self, phone: str) -> str:
        """Standardizes phone number format to E.164-like string."""
        cleaned = re.sub(r'[\s\-\(\)]', '', str(phone or "").strip())
        if not cleaned:
            return "+919876543210"
        if cleaned.startswith("+"):
            return cleaned
        if cleaned.startswith("91") and len(cleaned) == 12:
            return f"+{cleaned}"
        if len(cleaned) == 10:
            return f"+91{cleaned}"
        return f"+{cleaned}"

    def _generate_tts_url(self, text: str, lang: str) -> str:
        """Generates streamable TTS URL using existing Bol Kisan voice endpoint conventions."""
        h = hashlib.sha256(text.encode("utf-8")).hexdigest()[:16]
        return f"/api/v1/voice/tts-stream?text_hash={h}&lang={lang}"

    def _load_persistent_store(self):
        """Loads persistent session state if store exists on disk."""
        if not IVR_STORE_PATH.exists():
            return
        try:
            with open(IVR_STORE_PATH, "r", encoding="utf-8") as f:
                data = json.load(f)
                sessions_raw = data.get("sessions", {})
                for cid, sdict in sessions_raw.items():
                    sess = IVRCallSession(
                        call_id=sdict.get("call_id", cid),
                        caller_number=sdict.get("caller_number", "+919876543210"),
                        language=sdict.get("language", "te"),
                        metadata=sdict.get("metadata", {})
                    )
                    sess.status = sdict.get("status", "ended")
                    sess.started_at = sdict.get("started_at", sess.started_at)
                    sess.ended_at = sdict.get("ended_at")
                    sess.language_selected = sdict.get("language_selected", True)
                    sess.dtmf_history = sdict.get("dtmf_history", [])
                    sess.conversation_history = sdict.get("conversation_history", [])
                    sess.context_crop = sdict.get("context_crop")
                    sess.context_entity = sdict.get("context_entity")
                    sess.ticket_number = sdict.get("ticket_number")
                    sess.ticket_id = sdict.get("ticket_id")
                    sess.escalation_reason = sdict.get("escalation_reason")
                    sess.error_history = sdict.get("error_history", [])
                    sess.last_prompt = sdict.get("last_prompt")
                    self._sessions[cid] = sess

                stats_saved = data.get("stats", {})
                for k, v in stats_saved.items():
                    if k in self._stats:
                        self._stats[k] = v
        except Exception as e:
            logger.warning(f"Could not load IVR store: {e}")

    def _save_persistent_store(self):
        """Saves current IVR session state to disk."""
        try:
            IVR_STORE_PATH.parent.mkdir(parents=True, exist_ok=True)
            serializable_sessions = {cid: sess.to_dict() for cid, sess in self._sessions.items()}
            payload = {
                "updated_at": datetime.now(timezone.utc).isoformat(),
                "stats": self._stats,
                "sessions": serializable_sessions
            }
            with open(IVR_STORE_PATH, "w", encoding="utf-8") as f:
                json.dump(payload, f, indent=2, ensure_ascii=False)
        except Exception as e:
            logger.warning(f"Could not write IVR store: {e}")

    def _create_escalation_ticket(
        self,
        caller_number: str,
        reason: str,
        query_transcript: str,
        crop: Optional[str] = None
    ) -> Tuple[str, Optional[str]]:
        """
        Creates statutory escalation ticket starting with ESC-.
        Persists into database if database connection is available, otherwise safely generates ticket.
        """
        ticket_num = f"ESC-{datetime.now().strftime('%Y%m%d')}-{uuid.uuid4().hex[:6].upper()}"
        ticket_id = None
        try:
            from sqlalchemy import func
            from app.database.session import SessionLocal
            from app.database.models import AgriculturalTicket, TicketMessage, User, Crop
            db = SessionLocal()
            try:
                clean_digits = re.sub(r'\D', '', caller_number)
                user = db.query(User).filter(
                    (User.phone_number == caller_number) |
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
                    escalation_reason=reason or "IVR Farmer Escalation to MAO",
                    original_question_transcript=query_transcript or "IVR Voice Query",
                )
                db.add(ticket)
                db.flush()

                ticket_msg = TicketMessage(
                    ticket_id=ticket.id,
                    sender_id=farmer_id,
                    msg_type="text",
                    content=query_transcript or "IVR Voice Query"
                )
                db.add(ticket_msg)
                db.commit()
                ticket_id = ticket.id
            except Exception as e:
                db.rollback()
                logger.warning(f"MockIVRProvider: DB commit for AgriculturalTicket skipped ({e})")
            finally:
                db.close()
        except Exception as e:
            logger.warning(f"MockIVRProvider: DB session creation error ({e})")

        return ticket_num, ticket_id

    def _generate_deterministic_ivr_response(
        self,
        crop: Optional[str],
        target_entity: Optional[str],
        language: str,
        approved_product: Optional[Dict[str, Any]],
        rag_chunks: List[Dict[str, Any]]
    ) -> str:
        """
        Builds a crisp, verified CIBRC/POP spoken response for IVR speech delivery.
        Strictly preserves chemical name, dosage, PHI, and PPE requirements.
        """
        lang = language if language in ["te", "hi", "en"] else "te"

        if approved_product:
            p = approved_product
            trade = p.get("product_trade_name") or p.get("active_ingredient", "ఆమోదిత మందు")
            ai = p.get("active_ingredient", "")
            dose = p.get("dosage_per_acre") or p.get("dosage_per_hectare", "సిఫార్సు చేసిన మోతాదు")
            phi = p.get("pre_harvest_interval_days", 14)
            c_name = crop or p.get("target_crop", "")
            t_name = target_entity or p.get("target_pest") or p.get("target_disease", "")

            trade_desc = f"{trade} ({ai})" if ai and ai not in trade else trade

            if lang == "te":
                reply = (
                    f"{c_name} లో {t_name} నివారణకు: {trade_desc} ను ఎకరాకు {dose} చొప్పున 200 లీటర్ల నీటిలో కలిపి పిచికారీ చేయండి. "
                    f"కోతకు ముందు వ్యవధి {phi} రోజులు పాటించండి. తప్పనిసరిగా రక్షణ మాస్క్, గ్లౌజులు ధరించండి."
                )
            elif lang == "hi":
                reply = (
                    f"{c_name} में {t_name} नियंत्रण के लिए: {trade_desc} की मात्रा प्रति एकड़ {dose} को 200 लीटर पानी में मिलाकर छिड़काव करें। "
                    f"तुड़ाई से पहले {phi} दिन का अंतर रखें। छिड़काव के समय मास्क और दस्ताने अवश्य पहनें।"
                )
            else:
                reply = (
                    f"For {c_name} {t_name} control: Spray {trade_desc} at {dose} per acre diluted in 200 liters of water. "
                    f"Observe a pre-harvest interval of {phi} days. Always wear protective gloves and mask."
                )
            return format_ivr_spoken_text(reply)

        if rag_chunks:
            top_chunk = rag_chunks[0]
            content = top_chunk.get("content", "")
            clean_lines = [l.strip() for l in content.split("\n") if len(l.strip()) > 15]
            summary = clean_lines[0] if clean_lines else ""
            if lang == "te":
                reply = f"{crop or 'పంట'} సమగ్ర యాజమాన్యం: {summary}. రసాయన పిచికారీకి ముందు లేబుల్ క్షుణ్ణంగా చదవండి."
            elif lang == "hi":
                reply = f"{crop or 'फसल'} प्रबंधन सलाह: {summary}। छिड़काव से पहले अनुमोदित लेबल अवश्य पढ़ें।"
            else:
                reply = f"{crop or 'Crop'} Management Advisory: {summary}. Verify product label prior to spraying."
            return format_ivr_spoken_text(reply)

        # General POP fallback
        if lang == "te":
            reply = f"{crop or 'పంట'} సలహా కొరకు సమీప రైతు వేదిక లేదా మండల వ్యవసాయ అధికారిని (MAO) సంప్రదించండి. అనధికార రసాయనాల వాడకం నివారించండి."
        elif lang == "hi":
            reply = f"{crop or 'फसल'} की प्रमाणित सलाह के लिए अपने नजदीकी कृषि विस्तार अधिकारी से संपर्क करें। बिना सलाह रासायनिक छिड़काव न करें।"
        else:
            reply = f"For certified {crop or 'crop'} guidance, please consult your local Agricultural Extension Officer. Avoid unverified chemical treatments."
        return format_ivr_spoken_text(reply)

    def start_call(
        self,
        caller_number: str,
        language: Optional[str] = None,
        metadata: Optional[Dict[str, Any]] = None
    ) -> Dict[str, Any]:
        """
        Initiates a new IVR Call Session.
        Returns initial multilingual prompt asking caller to select their language:
        Press 1 for Telugu, 2 for Hindi, 3 for English.
        """
        norm_phone = self._normalize_phone(caller_number)
        call_id = f"IVR-{datetime.now().strftime('%Y%m%d%H%M%S')}-{uuid.uuid4().hex[:6].upper()}"

        session = IVRCallSession(
            call_id=call_id,
            caller_number=norm_phone,
            language=language,
            metadata=metadata
        )
        session.status = "active"

        # Initial Multilingual Prompt
        initial_prompt_text = self.PROMPTS_MENU["multilingual_welcome"]
        initial_tts_url = self._generate_tts_url(initial_prompt_text, session.language)

        prompt_obj = {
            "prompt_id": f"PRM-MENU-{uuid.uuid4().hex[:4].upper()}",
            "prompt_type": "initial_language_menu",
            "text": initial_prompt_text,
            "audio_url": initial_tts_url,
            "supported_dtmf": [
                {"digit": "1", "action": "select_telugu", "label": "తెలుగు (Telugu)"},
                {"digit": "2", "action": "select_hindi", "label": "हिंदी (Hindi)"},
                {"digit": "3", "action": "select_english", "label": "English"},
                {"digit": "9", "action": "escalate_mao", "label": "మండల వ్యవసాయ అధికారి (MAO Escalation)"},
                {"digit": "0", "action": "end_call", "label": "కాల్ ముగించు (End Call)"}
            ],
            "timestamp": datetime.now(timezone.utc).isoformat()
        }
        session.last_prompt = prompt_obj

        self._sessions[call_id] = session
        self._stats["total_calls"] += 1
        self._stats["active_calls"] += 1
        self._save_persistent_store()

        logger.info(f"MockIVRProvider: Started call {call_id} for {norm_phone}")
        return {
            "call_id": call_id,
            "session": session.to_dict(),
            "prompt": prompt_obj,
            "is_demo": True,
            "is_simulated": True,
            "provider": self.PROVIDER_NAME,
            "simulation_label": self.SIMULATION_LABEL
        }

    def send_dtmf(self, call_id: str, digit: str) -> Dict[str, Any]:
        """
        Processes DTMF keypad entry:
        - '1': Select Telugu ('te')
        - '2': Select Hindi ('hi')
        - '3': Select English ('en')
        - '9': Transfer to Mandal Agricultural Extension Officer (MAO) -> Escalation
        - '0' or '*': Gracefully end call
        - Other digits: Unrecognized DTMF guidance prompt
        """
        session = self._sessions.get(call_id)
        if not session:
            raise KeyError(f"IVR Call session '{call_id}' not found.")

        if session.status in ["ended", "failed"]:
            return {
                "call_id": call_id,
                "session": session.to_dict(),
                "action": "call_already_closed",
                "prompt": {"text": "Call has ended.", "audio_url": None},
                "is_demo": True,
                "is_simulated": True,
                "provider": self.PROVIDER_NAME,
                "simulation_label": self.SIMULATION_LABEL
            }

        clean_digit = str(digit).strip()
        now_iso = datetime.now(timezone.utc).isoformat()
        self._stats["dtmf_inputs"] += 1

        action = "unknown"
        reply_text = ""
        tts_url = ""
        ticket_number = None
        ticket_id = None

        if clean_digit == "1":
            session.language = "te"
            session.language_selected = True
            action = "language_selected_te"
            reply_text = self.PROMPTS_MENU["te"]["language_ack"]
            tts_url = self._generate_tts_url(reply_text, "te")

        elif clean_digit == "2":
            session.language = "hi"
            session.language_selected = True
            action = "language_selected_hi"
            reply_text = self.PROMPTS_MENU["hi"]["language_ack"]
            tts_url = self._generate_tts_url(reply_text, "hi")

        elif clean_digit == "3":
            session.language = "en"
            session.language_selected = True
            action = "language_selected_en"
            reply_text = self.PROMPTS_MENU["en"]["language_ack"]
            tts_url = self._generate_tts_url(reply_text, "en")

        elif clean_digit == "9":
            # Escalation to Extension Officer
            action = "escalate_to_mao"
            session.status = "escalated"
            session.escalation_reason = "Farmer requested direct MAO officer consultation via DTMF key 9"
            ticket_number, ticket_id = self._create_escalation_ticket(
                caller_number=session.caller_number,
                reason=session.escalation_reason,
                query_transcript="Farmer requested MAO extension officer transfer via IVR Key 9"
            )
            session.ticket_number = ticket_number
            session.ticket_id = ticket_id

            lang = session.language
            if lang == "te":
                reply_text = (
                    f"మీ కాల్ మండల వ్యవసాయ అధికారికి (MAO) బదిలీ చేయబడుతోంది. "
                    f"మీ సహాయ టికెట్ సంఖ్య: {ticket_number}. దయచేసి లైన్ లో వేచి ఉండండి."
                )
            elif lang == "hi":
                reply_text = (
                    f"आपकी कॉल मंडल कृषि अधिकारी (MAO) को ट्रांसफर की जा रही है। "
                    f"आपका सहायता टिकट नंबर: {ticket_number} है। कृपया लाइन पर बने रहें।"
                )
            else:
                reply_text = (
                    f"Your call is being transferred to the Mandal Agricultural Extension Officer (MAO). "
                    f"Your ticket number is: {ticket_number}. Please hold the line."
                )
            tts_url = self._generate_tts_url(reply_text, lang)
            self._stats["escalated_calls"] += 1

        elif clean_digit in ["0", "*"]:
            action = "end_call"
            session.status = "ended"
            session.ended_at = now_iso
            lang = session.language
            reply_text = self.PROMPTS_MENU.get(lang, self.PROMPTS_MENU["en"])["goodbye"]
            tts_url = self._generate_tts_url(reply_text, lang)
            self._stats["ended_calls"] += 1
            if self._stats["active_calls"] > 0:
                self._stats["active_calls"] -= 1

        else:
            action = "unrecognized_dtmf"
            lang = session.language
            reply_text = self.PROMPTS_MENU.get(lang, self.PROMPTS_MENU["en"])["unrecognized_dtmf"]
            tts_url = self._generate_tts_url(reply_text, lang)

        dtmf_record = {
            "digit": clean_digit,
            "action": action,
            "timestamp": now_iso,
            "language": session.language,
            "status_after": session.status,
            "ticket_number": ticket_number
        }
        session.dtmf_history.append(dtmf_record)

        prompt_obj = {
            "prompt_id": f"PRM-DTMF-{uuid.uuid4().hex[:4].upper()}",
            "prompt_type": f"dtmf_response_{action}",
            "text": reply_text,
            "audio_url": tts_url,
            "action": action,
            "timestamp": now_iso
        }
        session.last_prompt = prompt_obj

        self._save_persistent_store()
        logger.info(f"MockIVRProvider: Call {call_id} DTMF digit '{clean_digit}' -> action {action}")

        return {
            "call_id": call_id,
            "digit": clean_digit,
            "action": action,
            "status": session.status,
            "session": session.to_dict(),
            "prompt": prompt_obj,
            "ticket_number": ticket_number,
            "ticket_id": ticket_id,
            "is_demo": True,
            "is_simulated": True,
            "provider": self.PROVIDER_NAME,
            "simulation_label": self.SIMULATION_LABEL
        }

    async def process_voice_input(
        self,
        call_id: str,
        speech_transcript: str,
        language: Optional[str] = None
    ) -> Dict[str, Any]:
        """
        Processes spoken agricultural voice query from farmer.
        Pipeline:
        1. Contextual continuity resolution from conversation history.
        2. QueryUnderstanding extraction (crop, pest/disease, intent).
        3. CIBRC Statutory Safety Gate Verification (Instant Escalation if Unsafe).
        4. Grounded Synthesis (RAG + Gemini or Deterministic CIBRC Fallback).
        5. Spoken IVR audio generation.
        """
        session = self._sessions.get(call_id)
        if not session:
            raise KeyError(f"IVR Call session '{call_id}' not found.")

        if session.status == "ended":
            raise ValueError(f"Cannot process voice on call '{call_id}' because status is already 'ended'.")

        self._stats["voice_queries"] += 1
        query_text = (speech_transcript or "").strip()
        now_iso = datetime.now(timezone.utc).isoformat()

        # Resolve language: payload override -> session language -> detection -> default 'te'
        resolved_lang = language or session.language
        if not resolved_lang or resolved_lang not in ["te", "hi", "en"]:
            detected = QueryUnderstanding.detect_language(query_text)
            resolved_lang = detected if detected in ["te", "hi", "en"] else "te"
        session.language = resolved_lang

        # Record farmer utterance in conversation history
        session.conversation_history.append({
            "role": "user",
            "text": query_text,
            "crop": session.context_crop,
            "timestamp": now_iso,
            "language": resolved_lang
        })

        # Step 1: Query Understanding & Entity Extraction with conversational history
        formatted_history = [
            {"role": turn.get("role", "user"), "text": turn.get("text", ""), "crop": turn.get("crop")}
            for turn in session.conversation_history
        ]

        analysis = QueryUnderstanding.analyze_query(
            query_text,
            context_crop=session.context_crop,
            conversation_history=formatted_history
        )

        effective_crop = analysis.get("crop") or session.context_crop
        effective_entity = analysis.get("target_entity") or session.context_entity

        # Update session conversational context
        if effective_crop:
            session.context_crop = effective_crop
            session.conversation_history[-1]["crop"] = effective_crop
        if effective_entity:
            session.context_entity = effective_entity

        # Step 2: CIBRC Statutory Safety Gate Verification
        safety_decision, meta = safety_gate.evaluate_query(
            query_text=query_text,
            crop=effective_crop,
            pest_or_disease=effective_entity
        )

        reply_text = ""
        is_safe = safety_decision.is_safe
        ticket_number = None
        ticket_id = None

        if safety_decision.needs_escalation:
            # IMMEDIATE STATUTORY SAFETY ESCALATION
            # Strictly: NEVER invent pesticide dosages, NEVER recommend unverified chemicals.
            session.status = "escalated"
            session.escalation_reason = safety_decision.escalation_reason or "Unapproved chemical or dangerous pesticide dosage requested via IVR"
            ticket_number, ticket_id = self._create_escalation_ticket(
                caller_number=session.caller_number,
                reason=session.escalation_reason,
                query_transcript=query_text,
                crop=effective_crop
            )
            session.ticket_number = ticket_number
            session.ticket_id = ticket_id
            self._stats["escalated_calls"] += 1
            is_safe = False

            if resolved_lang == "te":
                reply_text = (
                    f"భద్రతా హెచ్చరిక: మీరు అడిగిన రసాయన మోతాదు అధికారిక సి.ఐ.బి.ఆర్.సి రికార్డుల్లో ఆమోదం పొందలేదు. "
                    f"తప్పుడు రసాయనాల వాడకం పంటను నాశనం చేస్తుంది. "
                    f"మీ ప్రశ్న మండల వ్యవసాయ అధికారికి బదిలీ చేయబడింది. "
                    f"మీ టికెట్ సంఖ్య: {ticket_number}."
                )
            elif resolved_lang == "hi":
                reply_text = (
                    f"सुरक्षा चेतावनी: यह कीटनाशक या मात्रा केंद्रीय कीटनाशक बोर्ड द्वारा अनुमोदित नहीं है। "
                    f"गलत छिड़काव से फसल नष्ट हो सकती है। "
                    f"आपका प्रश्न कृषि विस्तार अधिकारी को ट्रांसफर कर दिया गया है। "
                    f"टिकट संख्या: {ticket_number}."
                )
            else:
                reply_text = (
                    f"Safety Warning: Chemical or dosage not approved in official CIBRC statutory records. "
                    f"To prevent crop burning or pesticide poisoning, this issue has been escalated to your Extension Officer. "
                    f"Tracking Ticket: {ticket_number}."
                )
        else:
            # Step 3: Verified RAG Retrieval
            rag_chunks = rag_service.retrieve(query=query_text, crop=effective_crop, top_k=2)

            safety_context = {
                "approved_product": safety_decision.approved_product,
                "citation": safety_decision.source_citation
            } if safety_decision.approved_product else None

            # Step 4: Grounded Synthesis with graceful deterministic CIBRC/POP fallback
            gemini_succeeded = False
            try:
                if gemini_service.is_text_available():
                    ai_result = await gemini_service.generate_agricultural_response(
                        query=query_text,
                        retrieved_contexts=rag_chunks,
                        language=resolved_lang,
                        safety_context=safety_context,
                        query_analysis=analysis,
                        conversation_history=formatted_history
                    )
                    candidate = ai_result.get("text", "")
                    if safety_gate.validate_llm_response(candidate, safety_decision):
                        formatted_cand = format_ivr_spoken_text(candidate)
                        if len(formatted_cand) > 15:
                            reply_text = formatted_cand
                            gemini_succeeded = True
            except Exception as exc:
                logger.warning(f"MockIVRProvider: Gemini synthesis skipped/failed ({exc}), using deterministic CIBRC/POP fallback.")

            if not gemini_succeeded:
                reply_text = self._generate_deterministic_ivr_response(
                    crop=effective_crop,
                    target_entity=effective_entity,
                    language=resolved_lang,
                    approved_product=safety_decision.approved_product,
                    rag_chunks=rag_chunks
                )

        clean_spoken_text = format_ivr_spoken_text(reply_text)
        tts_url = self._generate_tts_url(clean_spoken_text, resolved_lang)

        # Record assistant answer in conversation history
        session.conversation_history.append({
            "role": "assistant",
            "text": clean_spoken_text,
            "crop": effective_crop,
            "timestamp": datetime.now(timezone.utc).isoformat(),
            "language": resolved_lang,
            "is_safe": is_safe,
            "ticket_number": ticket_number,
            "audio_url": tts_url
        })

        prompt_obj = {
            "prompt_id": f"PRM-VOICE-{uuid.uuid4().hex[:4].upper()}",
            "prompt_type": "voice_response",
            "text": clean_spoken_text,
            "audio_url": tts_url,
            "is_safe": is_safe,
            "crop_identified": effective_crop,
            "target_entity": effective_entity,
            "ticket_number": ticket_number,
            "timestamp": datetime.now(timezone.utc).isoformat()
        }
        session.last_prompt = prompt_obj

        self._save_persistent_store()
        logger.info(f"MockIVRProvider: Handled voice input for call {call_id} (safe={is_safe}, ticket={ticket_number})")

        return {
            "call_id": call_id,
            "farmer_speech": query_text,
            "response_text": clean_spoken_text,
            "audio_playback_url": tts_url,
            "language": resolved_lang,
            "crop_identified": effective_crop,
            "target_entity_identified": effective_entity,
            "is_safe": is_safe,
            "is_escalated": bool(session.status == "escalated"),
            "ticket_number": ticket_number,
            "ticket_id": ticket_id,
            "session": session.to_dict(),
            "prompt": prompt_obj,
            "is_demo": True,
            "is_simulated": True,
            "provider": self.PROVIDER_NAME,
            "simulation_label": self.SIMULATION_LABEL
        }

    def simulate_error(self, call_id: str, error_type: str) -> Dict[str, Any]:
        """
        Simulates telecom or AI failure modes:
        - 'silence_timeout': Caller silent for > 5-8 seconds
        - 'speech_recognition_failure': Garbled audio / low ASR confidence
        - 'backend_failure': System failure -> Auto-escalate to MAO
        - 'gemini_failure': LLM outage -> Graceful deterministic CIBRC/POP fallback
        - 'unrecognized_dtmf': Invalid digit key pressed
        """
        session = self._sessions.get(call_id)
        if not session:
            raise KeyError(f"IVR Call session '{call_id}' not found.")

        self._stats["errors_simulated"] += 1
        now_iso = datetime.now(timezone.utc).isoformat()
        lang = session.language or "te"

        error_key = str(error_type).strip().lower()
        recovery_text = ""
        action = f"simulate_{error_key}"
        ticket_number = None
        ticket_id = None

        if error_key == "silence_timeout":
            recovery_text = self.PROMPTS_MENU.get(lang, self.PROMPTS_MENU["en"])["silence_timeout"]

        elif error_key == "speech_recognition_failure":
            recovery_text = self.PROMPTS_MENU.get(lang, self.PROMPTS_MENU["en"])["speech_recognition_failure"]

        elif error_key == "backend_failure":
            # Auto escalate on critical backend failure
            session.status = "escalated"
            session.escalation_reason = "Automated IVR fallback escalation due to backend processing error"
            ticket_number, ticket_id = self._create_escalation_ticket(
                caller_number=session.caller_number,
                reason=session.escalation_reason,
                query_transcript="Simulated backend technical failure during IVR call"
            )
            session.ticket_number = ticket_number
            session.ticket_id = ticket_id
            self._stats["escalated_calls"] += 1

            if lang == "te":
                recovery_text = (
                    f"సాంకేతిక లోపం ఏర్పడింది. మీ కాల్ స్వయంచాలకంగా మండల వ్యవసాయ అధికారికి బదిలీ చేయబడుతోంది. "
                    f"మీ టికెట్ సంఖ్య: {ticket_number}. దయచేసి వేచి ఉండండి."
                )
            elif lang == "hi":
                recovery_text = (
                    f"तकनीकी त्रुटि हुई है। आपकी कॉल स्वतः कृषि अधिकारी को ट्रांसफर की जा रही है। "
                    f"टिकट संख्या: {ticket_number}। कृपया प्रतीक्षा करें।"
                )
            else:
                recovery_text = (
                    f"A backend error occurred. Your call is automatically being routed to the Agricultural Officer. "
                    f"Emergency ticket: {ticket_number}. Please remain on the line."
                )

        elif error_key == "gemini_failure":
            # Graceful deterministic CIBRC fallback
            recovery_text = self._generate_deterministic_ivr_response(
                crop=session.context_crop or "Cotton",
                target_entity=session.context_entity or "Pink Bollworm",
                language=lang,
                approved_product=None,
                rag_chunks=[]
            )

        elif error_key == "unrecognized_dtmf":
            recovery_text = self.PROMPTS_MENU.get(lang, self.PROMPTS_MENU["en"])["unrecognized_dtmf"]

        else:
            recovery_text = (
                f"Unknown error condition '{error_key}'. Please repeat your request or press 9 for assistance."
            )

        tts_url = self._generate_tts_url(recovery_text, lang)

        err_record = {
            "error_type": error_key,
            "timestamp": now_iso,
            "recovery_prompt": recovery_text,
            "ticket_number": ticket_number,
            "language": lang
        }
        session.error_history.append(err_record)

        prompt_obj = {
            "prompt_id": f"PRM-ERR-{uuid.uuid4().hex[:4].upper()}",
            "prompt_type": f"error_{error_key}",
            "text": recovery_text,
            "audio_url": tts_url,
            "error_type": error_key,
            "ticket_number": ticket_number,
            "timestamp": now_iso
        }
        session.last_prompt = prompt_obj

        self._save_persistent_store()
        logger.info(f"MockIVRProvider: Simulated error '{error_key}' on call {call_id}")

        return {
            "call_id": call_id,
            "error_type": error_key,
            "recovery_prompt": recovery_text,
            "audio_playback_url": tts_url,
            "ticket_number": ticket_number,
            "ticket_id": ticket_id,
            "status": session.status,
            "session": session.to_dict(),
            "prompt": prompt_obj,
            "is_demo": True,
            "is_simulated": True,
            "provider": self.PROVIDER_NAME,
            "simulation_label": self.SIMULATION_LABEL
        }

    def end_call(self, call_id: str, reason: Optional[str] = "caller_hangup") -> Dict[str, Any]:
        """
        Gracefully terminates an IVR call and calculates total call duration in seconds.
        """
        session = self._sessions.get(call_id)
        if not session:
            raise KeyError(f"IVR Call session '{call_id}' not found.")

        now_iso = datetime.now(timezone.utc).isoformat()
        session.status = "ended"
        session.ended_at = now_iso
        session.metadata["termination_reason"] = reason or "caller_hangup"

        lang = session.language or "te"
        goodbye_text = self.PROMPTS_MENU.get(lang, self.PROMPTS_MENU["en"])["goodbye"]
        tts_url = self._generate_tts_url(goodbye_text, lang)

        self._stats["ended_calls"] += 1
        if self._stats["active_calls"] > 0:
            self._stats["active_calls"] -= 1

        prompt_obj = {
            "prompt_id": f"PRM-END-{uuid.uuid4().hex[:4].upper()}",
            "prompt_type": "call_ended",
            "text": goodbye_text,
            "audio_url": tts_url,
            "duration_seconds": session.duration_seconds,
            "timestamp": now_iso
        }
        session.last_prompt = prompt_obj

        self._save_persistent_store()
        logger.info(f"MockIVRProvider: Ended call {call_id} after {session.duration_seconds}s (reason: {reason})")

        return {
            "call_id": call_id,
            "status": "ended",
            "duration_seconds": session.duration_seconds,
            "started_at": session.started_at,
            "ended_at": session.ended_at,
            "dtmf_count": len(session.dtmf_history),
            "turns_count": len(session.conversation_history),
            "session": session.to_dict(),
            "prompt": prompt_obj,
            "is_demo": True,
            "is_simulated": True,
            "provider": self.PROVIDER_NAME,
            "simulation_label": self.SIMULATION_LABEL
        }

    def get_call_session(self, call_id: str) -> Optional[Dict[str, Any]]:
        """Returns detailed state for an IVR call session."""
        session = self._sessions.get(call_id)
        return session.to_dict() if session else None

    def list_active_calls(self) -> List[Dict[str, Any]]:
        """Returns all currently active, ringing, or escalated IVR calls."""
        return [
            s.to_dict() for s in self._sessions.values()
            if s.status in ["ringing", "active", "escalated"]
        ]

    def list_all_calls(self) -> List[Dict[str, Any]]:
        """Returns all IVR call sessions."""
        return [s.to_dict() for s in self._sessions.values()]

    def get_status(self) -> Dict[str, Any]:
        """Returns gateway status, stats, and active sessions."""
        active = self.list_active_calls()
        return {
            "provider": self.PROVIDER_NAME,
            "status": "operational",
            "is_demo": True,
            "is_simulated": True,
            "simulation_label": self.SIMULATION_LABEL,
            "stats": {
                "total_calls": self._stats["total_calls"],
                "active_calls": len(active),
                "escalated_calls": self._stats["escalated_calls"],
                "ended_calls": self._stats["ended_calls"],
                "errors_simulated": self._stats["errors_simulated"],
                "dtmf_inputs": self._stats["dtmf_inputs"],
                "voice_queries": self._stats["voice_queries"]
            },
            "active_call_ids": [c["call_id"] for c in active],
            "supported_languages": ["te", "hi", "en"],
            "supported_dtmf_menu": [
                {"key": "1", "meaning": "Telugu"},
                {"key": "2", "meaning": "Hindi"},
                {"key": "3", "meaning": "English"},
                {"key": "9", "meaning": "MAO Officer Escalation"},
                {"key": "0", "meaning": "End Call"},
                {"key": "*", "meaning": "End Call"}
            ]
        }

    def reset(self):
        """Clears sessions for testing purposes."""
        self._sessions.clear()
        for k in self._stats:
            self._stats[k] = 0
        if IVR_STORE_PATH.exists():
            try:
                os.remove(IVR_STORE_PATH)
            except Exception:
                pass


ivr_service = MockIVRProvider()
