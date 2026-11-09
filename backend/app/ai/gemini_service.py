import json
import re
import time
from typing import List, Dict, Any, Optional
from app.core.config import settings
from app.core.logging import logger

class GeminiService:
    """
    Google Gemini Agricultural Response Synthesis Service.
    Enforces Strict Grounding:
    - Primary model: gemini-3.5-flash-lite (fast, high-quota, verified) with cascade
    - Cascade: gemini-3.5-flash-lite -> gemini-flash-lite-latest -> gemini-3.1-flash-lite -> gemini-3.8-flash -> gemini-flash-latest
    - Clean farmer-facing speech: Zero bracketed references, zero citation IDs in user-visible text
    - Quota-aware 429 retry and backoff
    - Deterministic ICAR/PJTSAU grounding fallback when all LLMs are unreachable
    - Enforced JSON Output with claims and citation source mapping
    """

    def __init__(self):
        self.primary_model = settings.GEMINI_MODEL or "gemini-3.5-flash-lite"
        # Text generation cascade prioritized by active availability & quota
        self.models_cascade = [
            "gemini-3.5-flash-lite",
            "gemini-flash-lite-latest",
            "gemini-3.1-flash-lite",
            self.primary_model,
            "gemini-flash-latest"
        ]
        # Dedicated vision/multimodal model kept separate from text cascade
        self.vision_model = "gemini-3.1-flash-image"
        
        self.client = None
        if settings.GEMINI_API_KEY:
            try:
                from google import genai
                self.client = genai.Client(api_key=settings.GEMINI_API_KEY)
                logger.info(f"Initialized Google Gen AI Client (Primary: {self.primary_model}, Cascade: {self.models_cascade})")
            except Exception as e:
                logger.warning(f"Failed to initialize google-genai SDK: {e}. Running in deterministic mode.")

    def _clean_response_text(self, text: str) -> str:
        """
        Removes all citation IDs (e.g. ['doc-kb06-001'], [CIR-...]),
        source lines (e.g. 'ఆధారం: ...', 'Source: ...'), and bracketed reference clutter
        from farmer-facing text.
        """
        if not text:
            return ""
        # Remove any bracketed citations like ['doc-...'], [CIR-...], [cibrc-...]
        cleaned = re.sub(r"\[(?:'|\")?[^\]]*(?:'|\")?\]", "", text)
        # Remove citation attribution lines
        cleaned = re.sub(r"(?m)^\s*(?:ఆధారం|మూలం|स्रोत|Source|Citation|ఆధారాలు)\s*:.*$", "", cleaned)
        # Remove empty bullet points left behind by stripped citations
        cleaned = re.sub(r"(?m)^\s*[•\-\*]\s*$", "", cleaned)
        # Clean double spaces and excess newlines
        cleaned = re.sub(r"[ \t]+", " ", cleaned)
        cleaned = re.sub(r"\n{3,}", "\n\n", cleaned)
        return cleaned.strip()

    async def generate_agricultural_response(
        self,
        query: str,
        retrieved_contexts: List[Dict[str, Any]],
        language: str = "te",
        safety_context: Optional[Dict[str, Any]] = None,
        query_analysis: Optional[Dict[str, Any]] = None,
        conversation_history: Optional[List[Dict[str, Any]]] = None
    ) -> Dict[str, Any]:
        """
        Synthesizes agricultural answer strictly grounded in retrieved evidence.
        Output is validated for factual alignment and citation accuracy.
        """
        lang_instruction = {
            "te": "Respond in natural, respectful Telugu (తెలుగు) that rural farmers easily understand. Use colloquial agrarian terms.",
            "hi": "Respond in clear, accessible Hindi (हिन्दी) suitable for rural farmers. Use standard agricultural terminology.",
            "en": "Respond in concise, actionable English with clear bullet points."
        }.get(language, "Respond in clear, accessible Telugu (తెలుగు).")

        # Format context chunks with explicit Document IDs for claim attribution
        context_lines = []
        valid_source_ids = set()
        for idx, ctx in enumerate(retrieved_contexts):
            doc_id = ctx.get("document_id") or f"KB-DOC-{idx+1:02d}"
            title = ctx.get("title", "Agricultural Reference")
            content = ctx.get("content", "")
            citation = ctx.get("citation", f"[{doc_id}] {title}")
            valid_source_ids.add(doc_id)
            context_lines.append(f"--- DOCUMENT [{doc_id}] : {title} ---\n{content}\nCitation: {citation}\n")

        # If approved chemical product is resolved from safety gate, prepend it as authoritative context for Gemini
        if safety_context and safety_context.get("approved_product"):
            p = safety_context["approved_product"]
            cibrc_chunk = (
                f"--- STATUTORY APPROVED REGULATION [CIBRC-REG] ---\n"
                f"Approved Product: {p.get('product_trade_name')} ({p.get('active_ingredient')})\n"
                f"Target Crop: {p.get('target_crop')}, Target Pest/Disease: {p.get('target_pest') or p.get('target_disease')}\n"
                f"Prescribed Dosage: {p.get('dosage_per_acre')} per acre in {p.get('dilution_water_liters')} water (or {p.get('dosage_per_hectare')})\n"
                f"Pre-Harvest Interval (PHI): {p.get('pre_harvest_interval_days')} days\n"
                f"Safety Instructions: {p.get('safety_precautions')}\n"
            )
            context_lines.insert(0, cibrc_chunk)
            valid_source_ids.add("CIBRC-REG")

        formatted_context = "\n".join(context_lines) if context_lines else "No verified documents available."

        history_text = ""
        if conversation_history:
            turns = []
            for h in conversation_history[-6:]:
                role = "Farmer" if h.get("role") in ["user", "farmer"] else "Bol Kisan"
                msg = h.get("text") or h.get("query_text") or h.get("response_text") or ""
                if msg:
                    turns.append(f"{role}: {msg[:300]}")
            if turns:
                history_text = "PREVIOUS CONVERSATION CONTEXT:\n" + "\n".join(turns) + "\n\n"

        system_instruction = (
            "You are BOL KISAN (बोल किसान / బోల్ కిసాన్), an intelligent, expert AI Agricultural Voice Assistant for Indian farmers.\n"
            "STRICT GROUNDING & NATURAL SPEECH MANDATE:\n"
            "1. Answer ONLY using the facts present in the RETRIEVED CONTEXT below.\n"
            "2. NEVER invent dosages, chemical trade names, crop protection schedules, or unverified claims.\n"
            "3. If context contains CIBRC approved formulation, state the exact product name, dose per acre/pump, and water volume.\n"
            "4. CLEAN SPOKEN SPEECH: Do NOT include document IDs, bracketed numbers, or citation references (e.g. NEVER write ['KB-DOC-01'], ['cibrc-001'], or [CIR-...] in the answer). Provide clean, natural spoken advice without academic citation tags.\n"
            "5. If the retrieved context is insufficient to answer safely, state what information is missing and advise consulting an Agricultural Officer.\n"
            f"6. Language requirement: {lang_instruction}\n"
            "7. Return your response STRICTLY as a JSON object adhering to this schema:\n"
            "{\n"
            '  "status": "answered" | "insufficient_evidence",\n'
            '  "topic": "<Identified agricultural topic>",\n'
            '  "crop": "<Target crop>",\n'
            '  "answer": "<Clean farmer-facing response in the requested language with zero bracketed citations>",\n'
            '  "claims": [\n'
            '    {\n'
            '      "claim": "<Specific factual statement>",\n'
            '      "source_ids": ["<Document ID from context>"]\n'
            '    }\n'
            '  ],\n'
            '  "follow_up_suggestions": ["<Short relevant follow-up question 1>", "<Short relevant follow-up question 2>"],\n'
            '  "evidence_sufficient": true | false,\n'
            '  "safety_status": "verified" | "unverified",\n'
            '  "escalation_required": false | true,\n'
            '  "escalation_reason": null | "<Reason if escalation needed>"\n'
            "}"
        )

        user_prompt = (
            f"RETRIEVED CONTEXT DOCUMENTS:\n{formatted_context}\n\n"
            f"{history_text}"
            f"FARMER QUESTION: {query}\n"
            f"REQUESTED LANGUAGE CODE: {language}"
        )

        # Attempt LLM Generation through text-only model cascade with 429 quota-aware backoff
        last_failure_reason = "No GenAI client initialized"
        if self.client:
            for candidate_model in self.models_cascade:
                max_retries = 2
                for attempt in range(max_retries):
                    try:
                        response = self.client.models.generate_content(
                            model=candidate_model,
                            contents=[system_instruction, user_prompt],
                            config={"response_mime_type": "application/json"}
                        )
                        raw_text = response.text or ""
                        parsed = json.loads(raw_text)

                        # Gate 2: Claim Verification against valid source IDs
                        claims = parsed.get("claims", [])
                        verified_claims = []
                        hallucinated_sources = []
                        for c in claims:
                            c_sources = c.get("source_ids", [])
                            valid_for_c = [s for s in c_sources if s in valid_source_ids]
                            if valid_for_c:
                                verified_claims.append(c)
                            else:
                                hallucinated_sources.extend(c_sources)

                        is_grounded = len(hallucinated_sources) == 0
                        clean_answer = self._clean_response_text(parsed.get("answer", raw_text))

                        evidence_ok = parsed.get("evidence_sufficient", True) and (len(retrieved_contexts) > 0 or bool(safety_context))
                        has_claims = len(verified_claims) > 0 or bool(safety_context)
                        safe_to_answer = is_grounded and evidence_ok and has_claims
                        status_str = "answered" if safe_to_answer else "insufficient_evidence"
                        safety_str = "verified" if safe_to_answer else "unverified"
                        need_escalation = parsed.get("escalation_required", False) or not safe_to_answer

                        return {
                            "status": status_str,
                            "text": clean_answer,
                            "model": candidate_model,
                            "model_used": candidate_model,
                            "is_fallback": False,
                            "fallback_reason": None,
                            "is_grounded": is_grounded,
                            "is_safe": safe_to_answer,
                            "provider": "google-genai",
                            "topic": parsed.get("topic"),
                            "crop": parsed.get("crop"),
                            "claims": verified_claims,
                            "follow_up_suggestions": parsed.get("follow_up_suggestions") or self._generate_default_followups(parsed.get("crop") or (query_analysis or {}).get("crop"), language),
                            "evidence_sufficient": evidence_ok,
                            "safety_status": safety_str,
                            "escalation_required": need_escalation,
                            "escalation_reason": parsed.get("escalation_reason") or ("Insufficient verified evidence to answer query safely" if not safe_to_answer else None)
                        }
                    except Exception as e:
                        err_str = str(e)
                        last_failure_reason = f"Candidate {candidate_model} failed: {err_str[:120]}"
                        
                        # Handle 429 / Quota / Rate-limit awareness
                        if "429" in err_str or "RESOURCE_EXHAUSTED" in err_str:
                            # If it's a daily project quota exhaustion, retrying this model won't help; break to next candidate immediately
                            if "PerDay" in err_str or "quota" in err_str.lower():
                                logger.warning(f"Daily quota exhausted on {candidate_model}. Skipping to next cascade model.")
                                break
                            else:
                                # Transient rate-limit (RPM) - back off and retry once
                                if attempt < max_retries - 1:
                                    backoff_sec = (attempt + 1) * 1.5
                                    logger.info(f"Transient rate-limit (429) on {candidate_model}. Backing off {backoff_sec}s...")
                                    time.sleep(backoff_sec)
                                    continue
                        else:
                            # Other exceptions (404, invalid model, connection error): move to next model
                            logger.warning(f"Gemini API candidate ({candidate_model}) error: {err_str[:120]}. Trying next...")
                            break

        # Deterministic Grounded Fallback (when all API models fail, are rate-limited, or offline)
        fallback_res = self._generate_grounded_fallback(
            query=query,
            retrieved_contexts=retrieved_contexts,
            language=language,
            safety_context=safety_context,
            query_analysis=query_analysis
        )
        if "fallback_reason" not in fallback_res or not fallback_res["fallback_reason"]:
            fallback_res["fallback_reason"] = f"Cascade exhausted ({last_failure_reason}); deterministic ICAR/PJTSAU knowledge grounding activated"
        return fallback_res

    def _detect_dominant_script(self, text: str) -> str:
        te_chars = len(re.findall(r'[\u0C00-\u0C7F]', text))
        hi_chars = len(re.findall(r'[\u0900-\u097F]', text))
        latin_chars = len(re.findall(r'[a-zA-Z]', text))
        if te_chars > hi_chars and te_chars > 15:
            return "te"
        if hi_chars > te_chars and hi_chars > 15:
            return "hi"
        if latin_chars > 15:
            return "en"
        return "unknown"

    def _generate_grounded_fallback(
        self,
        query: str,
        retrieved_contexts: List[Dict[str, Any]],
        language: str,
        safety_context: Optional[Dict[str, Any]],
        query_analysis: Optional[Dict[str, Any]] = None
    ) -> Dict[str, Any]:
        """
        Dynamic Zero-Hallucination Fallback:
        Extracts verified knowledge directly from retrieved RAG context documents,
        tailored specifically to the queried crop, disease/pest, or agronomic topic.
        """
        crop = (query_analysis or {}).get("crop") or "Crop"
        topic = (query_analysis or {}).get("intent") or "Agricultural Practice"

        # Case 1: Verified Chemical Advice from SafetyGate / CIBRC
        if safety_context and safety_context.get("approved_product"):
            p = safety_context["approved_product"]
            if language == "te":
                text = (
                    f"వ్యవసాయ నిపుణుల సిఫార్సు (CIBRC సిఫార్సు):\n"
                    f"• పంట: {p.get('target_crop')}\n"
                    f"• నివారించే పురుగు/తెగులు: {p.get('target_pest') or p.get('target_disease')}\n"
                    f"• మందు పేరు: {p.get('product_trade_name')} ({p.get('active_ingredient')})\n"
                    f"• మోతాదు: ఎకరాకు {p.get('dosage_per_acre')} ({p.get('dilution_water_liters')} నీటిలో కలపాలి)\n"
                    f"• హెక్టారుకు: {p.get('dosage_per_hectare')}\n"
                    f"• జాగ్రత్తలు: {p.get('safety_precautions')}\n"
                    f"• వేచి ఉండే కాలం (PHI): కోతకు {p.get('pre_harvest_interval_days')} రోజుల ముందు వాడాలి."
                )
            elif language == "hi":
                text = (
                    f"कृषि विशेषज्ञों की सलाह:\n"
                    f"• फसल: {p.get('target_crop')}\n"
                    f"• कीट/रोग: {p.get('target_pest') or p.get('target_disease')}\n"
                    f"• उत्पाद: {p.get('product_trade_name')} ({p.get('active_ingredient')})\n"
                    f"• मात्रा: {p.get('dosage_per_acre')} प्रति एकड़ ({p.get('dilution_water_liters')} पानी में घोलें)\n"
                    f"• प्रति हेक्टेयर: {p.get('dosage_per_hectare')}\n"
                    f"• सुरक्षा सावधानी: {p.get('safety_precautions')}\n"
                    f"• प्रतीक्षा अवधि (PHI): कटाई से {p.get('pre_harvest_interval_days')} दिन पहले छिड़काव करें।"
                )
            else:
                text = (
                    f"Agronomic Expert Recommendation:\n"
                    f"• Crop: {p.get('target_crop')}\n"
                    f"• Target Pest/Disease: {p.get('target_pest') or p.get('target_disease')}\n"
                    f"• Product: {p.get('product_trade_name')} ({p.get('active_ingredient')})\n"
                    f"• Dosage: {p.get('dosage_per_acre')} per acre in {p.get('dilution_water_liters')} water\n"
                    f"• Per Hectare: {p.get('dosage_per_hectare')}\n"
                    f"• Precautions: {p.get('safety_precautions')}\n"
                    f"• Pre-Harvest Interval: {p.get('pre_harvest_interval_days')} days."
                )
            return {
                "text": self._clean_response_text(text),
                "model": "deterministic-cibrc-engine",
                "model_used": "deterministic-cibrc-engine",
                "is_fallback": True,
                "fallback_reason": "Statutory Zero-Discretion CIBRC Protocol",
                "is_grounded": True,
                "provider": "cibrc-rules",
                "topic": "pesticide_recommendation",
                "crop": p.get("target_crop"),
                "evidence_sufficient": True,
                "claims": [{"claim": f"CIBRC Approved {p.get('product_trade_name')}", "source_ids": [p.get("cibrc_registration_number") or "CIBRC-REG"]}]
            }

        # Case 2: Synthesize directly from retrieved context chunks with intent and relevance validation
        if retrieved_contexts:
            target_crop = (query_analysis or {}).get("crop")
            target_crop_lower = target_crop.lower().strip() if target_crop else None
            intent = (query_analysis or {}).get("intent") or "general_agri"
            target_entity = ((query_analysis or {}).get("target_entity") or "").lower()

            # Intent collection mapping for affinity scoring (prioritizing primary specialized collections)
            intent_cols_map = {
                "crop_disease": {"KB04", "KB06"},
                "pest_management": {"KB05", "KB06"},
                "fertilizer_soil": {"KB09", "KB10"},
                "soil_health": {"KB10"},
                "irrigation": {"KB11"},
                "weed_management": {"KB13"},
                "sowing_cultivation": {"KB01", "KB02", "KB03"},
                "pesticide_safety": {"KB08", "KB07"},
                "mandi_market": {"KB18"},
                "weather_advisory": {"KB16"},
                "harvesting": {"KB14", "KB15"},
                "crop_rotation": {"KB12"},
            }
            allowed_cols = intent_cols_map.get(intent, set())

            valid_candidates = []
            for ctx in retrieved_contexts:
                ctx_score = ctx.get("score", 0.0)
                # Minimum score floor
                if ctx_score < 0.28:
                    continue

                ctx_crop = (ctx.get("crop") or "").lower()
                # Strict crop mismatch rejection: If crop is identified in query, reject documents of other explicit crops
                if target_crop_lower and ctx_crop and ctx_crop not in ["general", "all", "soils", ""]:
                    if target_crop_lower not in ctx_crop and ctx_crop not in target_crop_lower:
                        continue

                # Intent relevance bonus: strong preference for the primary domain collection
                affinity = ctx_score
                ctx_col = ctx.get("collection_id")
                if ctx_col in allowed_cols:
                    affinity += 0.60
                elif ctx_col in ["KB21", "KB22"]:
                    affinity += 0.05

                # Entity match bonus
                if target_entity and target_entity in (ctx.get("target") or "").lower():
                    affinity += 0.40

                # Language affinity bonus
                ctx_content = ctx.get("content", "")
                ctx_lang = ctx.get("language")
                if (language == "te" and "Telugu Advisory:" in ctx_content) or (language == "hi" and "Hindi Advisory:" in ctx_content):
                    affinity += 0.50
                elif ctx_lang == language:
                    affinity += 0.50

                valid_candidates.append((affinity, ctx))

            if valid_candidates:
                # Pick the highest affinity candidate
                valid_candidates.sort(key=lambda x: x[0], reverse=True)
                top_ctx = valid_candidates[0][1]

                content = top_ctx.get("content", "")
                title = top_ctx.get("title", "ICAR Verified Practice")
                citation = top_ctx.get("citation", "[ICAR/PJTSAU Agricultural Guide]")
                doc_id = top_ctx.get("document_id", "KB-01")

                # Extract language-targeted advisory if multilingual Q&A
                extracted_body = ""
                if language == "te":
                    if "Telugu Advisory:" in content:
                        parts = content.split("Telugu Advisory:")
                        if len(parts) > 1:
                            extracted_body = parts[1].split("Hindi Advisory:")[0].strip()
                    elif self._detect_dominant_script(content) == "te":
                        extracted_body = content
                    elif "Recommendation:" in content:
                        parts = content.split("Recommendation:")
                        if len(parts) > 1:
                            extracted_body = parts[1].split("Telugu Advisory:")[0].strip()
                elif language == "hi":
                    if "Hindi Advisory:" in content:
                        parts = content.split("Hindi Advisory:")
                        if len(parts) > 1:
                            extracted_body = parts[1].strip()
                    elif self._detect_dominant_script(content) == "hi":
                        extracted_body = content
                elif language == "en":
                    if "Recommendation:" in content:
                        parts = content.split("Recommendation:")
                        if len(parts) > 1:
                            extracted_body = parts[1].split("Telugu Advisory:")[0].strip()
                    elif self._detect_dominant_script(content) == "en":
                        extracted_body = content

                if not extracted_body:
                    script = self._detect_dominant_script(content)
                    # Strictly prevent cross-language script pollution:
                    # Do not return pure Telugu script to a Hindi/English user, or pure Hindi to Telugu
                    if (language in ["hi", "en"] and script == "te") or (language == "te" and script == "hi"):
                        extracted_body = None
                    else:
                        extracted_body = content

                if extracted_body:
                    clean_content = self._clean_response_text(extracted_body)
                    body_script = self._detect_dominant_script(clean_content)
                    lang_disclaimer = ""
                    if language == "te" and body_script == "en":
                        lang_disclaimer = "(గమనిక: ధృవీకరించిన రికార్డు ఆంగ్లంలో భద్రపరచబడింది)\n"
                    elif language == "hi" and body_script == "en":
                        lang_disclaimer = "(नोट: प्रमाणित रिकॉर्ड अंग्रेजी में उपलब्ध है)\n"

                    if language == "te":
                        text = f"వ్యవసాయ సలహా ({title}):\n{lang_disclaimer}{clean_content}\n\nఆధారం: {citation}"
                    elif language == "hi":
                        text = f"कृषि सलाह ({title}):\n{lang_disclaimer}{clean_content}\n\nस्रोत: {citation}"
                    else:
                        text = f"Agricultural Advisory ({title}):\n{clean_content}\n\nSource: {citation}"

                    return {
                        "text": text,
                        "model": "grounded-rag-synthesizer",
                        "model_used": "grounded-rag-synthesizer",
                        "is_fallback": True,
                        "fallback_reason": "Offline / Deterministic Knowledge Retrieval",
                        "is_grounded": True,
                        "provider": "verified-knowledge-base",
                        "topic": topic,
                        "crop": crop,
                        "evidence_sufficient": True,
                        "claims": [{"claim": clean_content[:120], "source_ids": [doc_id]}],
                        "follow_up_suggestions": self._generate_default_followups(crop, language)
                    }

        # Case 3: No valid, relevant documents found or low relevance — ABSTAIN
        if language == "te":
            text = "ఈ ప్రశ్నకు సంబంధించిన ధృవీకరించిన వ్యవసాయ వివరాలు నాలెడ్జ్ బేస్‌లో అందుబాటులో లేవు. దయచేసి మండల వ్యవసాయ విస్తరణ అధికారిని (MAO/AEO) లేదా కిసాన్ కాల్ సెంటర్‌ను (1800-180-1551) సంప్రదించండి."
        elif language == "hi":
            text = "इस विशिष्ट प्रश्न के लिए प्रामाणिक कृषि रिकॉर्ड उपलब्ध नहीं हैं। कृपया अपने क्षेत्रीय कृषि विस्तार अधिकारी या किसान कॉल सेंटर (1800-180-1551) से संपर्क करें।"
        else:
            text = "Verified agricultural information for this specific query was not found in the official knowledge base. Please consult your local Agricultural Extension Officer or Kisan Call Center (1800-180-1551)."

        return {
            "text": text,
            "status": "insufficient_evidence",
            "model": "grounded-rag-synthesizer",
            "model_used": "grounded-rag-synthesizer",
            "is_fallback": True,
            "fallback_reason": "No Matching Knowledge Found",
            "is_grounded": False,
            "is_safe": False,
            "safety_status": "unverified",
            "escalation_required": True,
            "escalation_reason": "Insufficient verified agricultural evidence to answer query safely",
            "provider": "verified-knowledge-base",
            "topic": topic,
            "crop": crop,
            "evidence_sufficient": False,
            "claims": [],
            "follow_up_suggestions": self._generate_default_followups(crop, language)
        }

    def _generate_default_followups(self, crop: Optional[str], language: str) -> List[str]:
        c = (crop or "").lower()
        if "cotton" in c or "పత్తి" in c or "कपास" in c:
            if language == "te":
                return [
                    "ఎకరాకు ఎంత మోతాదులో మందు పిచికారీ చేయాలి?",
                    "జీవామృతం లేదా సేంద్రీయ పద్ధతులు పనిచేస్తాయా?",
                    "పత్తికి ఏ ఎరువులు ఎప్పుడు వేయాలి?"
                ]
            elif language == "hi":
                return [
                    "प्रति एकड़ कीटनाशक की सही मात्रा क्या है?",
                    "क्या प्राकृतिक खेती या जीवामृत से लाभ होगा?",
                    "कपास में कौन सी खाद किस समय डालें?"
                ]
            else:
                return [
                    "What is the exact spray dosage per acre?",
                    "Will natural farming or Jeevamrit help?",
                    "What is the fertilizer schedule for cotton?"
                ]
        elif "rice" in c or "paddy" in c or "వరి" in c or "धान" in c:
            if language == "te":
                return [
                    "నత్రజని ఎరువుల వాడకం తగ్గించాలా?",
                    "సుడిదోమ మరియు కాండం తొలిచే పురుగు నివారణ ఎలా?",
                    "వరి మండి ధర ఎంత ఉంది?"
                ]
            elif language == "hi":
                return [
                    "क्या यूरिया का प्रयोग रोक देना चाहिए?",
                    "तना छेदक और भूरा फुदका कीट की रोकथाम कैसे करें?",
                    "धान का वर्तमान मंडी भाव क्या है?"
                ]
            else:
                return [
                    "Should nitrogen application be reduced?",
                    "How to control stem borer and BPH in paddy?",
                    "What is the current mandi rate for paddy?"
                ]
        elif "chilli" in c or "మిరప" in c or "मिर्च" in c:
            if language == "te":
                return [
                    "నల్ల తామర పురుగుల నివారణకు ఏ మందు వాడాలి?",
                    "ఆకుముడత రాకుండా ఎలాంటి జాగ్రత్తలు తీసుకోవాలి?",
                    "మిరప క్వింటాల్ ధర ఎంత ఉంది?"
                ]
            elif language == "hi":
                return [
                    "काली थ्रिप्स के नियंत्रण के लिए कौन सी दवा डालें?",
                    "पत्ती मरोड़ रोग से बचाव के क्या उपाय हैं?",
                    "मिर्च का वर्तमान मंडी भाव क्या है?"
                ]
            else:
                return [
                    "What chemical is recommended for black thrips?",
                    "How to prevent leaf curl in chilli?",
                    "What is the current market price of dry chilli?"
                ]
        else:
            if language == "te":
                return [
                    "ఈ సమస్యకు సేంద్రీయ నివారణ ఉందా?",
                    "ఎకరాకు ఎరువుల మోతాదు ఎంత?",
                    "నీటిపారుదల ఎప్పుడు చేయాలి?"
                ]
            elif language == "hi":
                return [
                    "क्या इसके लिए कोई जैविक उपाय है?",
                    "प्रति एकड़ संतुलित खाद की मात्रा क्या है?",
                    "सिंचाई कब और कैसे करनी चाहिए?"
                ]
            else:
                return [
                    "Is there an organic remedy for this?",
                    "What is the fertilizer dosage per acre?",
                    "What is the recommended irrigation schedule?"
                ]

gemini_service = GeminiService()
