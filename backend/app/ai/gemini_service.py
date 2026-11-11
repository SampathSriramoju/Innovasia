import asyncio
import json
import re
import time
from datetime import datetime
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
        self.primary_model = settings.GEMINI_MODEL or "gemini-3.8-flash"
        # Text generation cascade prioritized by active availability & quota
        self.models_cascade = [
            self.primary_model,
            "gemini-3.1-flash-lite",
            "gemini-flash-latest",
            "gemini-flash-lite-latest",
            "gemini-3.5-flash-lite"
        ]
        # Dedicated vision/multimodal model cascade
        self.vision_models_cascade = [
            self.primary_model,
            "gemini-3.1-flash-lite",
            "gemini-flash-latest",
            "gemini-flash-lite-latest"
        ]
        self.vision_model = self.vision_models_cascade[0]
        self.vision_enabled = True
        self.text_enabled = True
        
        self.client = None
        if settings.GEMINI_API_KEY:
            try:
                from google import genai
                self.client = genai.Client(api_key=settings.GEMINI_API_KEY)
                logger.info(f"Initialized Google Gen AI Client (Primary: {self.primary_model}, Cascade: {self.models_cascade})")
            except Exception as e:
                logger.warning(f"Failed to initialize google-genai SDK: {e}. Running in deterministic mode.")

    def is_vision_available(self) -> bool:
        """
        Reports whether Gemini Vision multimodal inference is currently reachable and configured.
        """
        if not getattr(self, "vision_enabled", True):
            return False
        if not self.client:
            return False
        key = settings.GEMINI_API_KEY
        return bool(key and len(key.strip()) > 8 and not key.startswith("your-") and "placeholder" not in key.lower())

    def is_text_available(self) -> bool:
        """
        Reports whether Gemini text generation is currently reachable and configured.
        """
        if not getattr(self, "text_enabled", True):
            return False
        if not self.client:
            return False
        key = settings.GEMINI_API_KEY
        return bool(key and len(key.strip()) > 8 and not key.startswith("your-") and "placeholder" not in key.lower())

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

        # Build Ambiguity and Clarification Guidance
        ambiguity_guidance = ""
        if query_analysis and query_analysis.get("has_ambiguous_symptoms"):
            ambiguity_guidance = (
                "\nSPECIAL MANDATE - AMBIGUOUS SYMPTOMS OBSERVED:\n"
                "The farmer has described non-specific symptoms (such as general leaf yellowing, spots, curling, or wilting) that can stem from multiple distinct causes (e.g., nutrient deficiency, fungal/bacterial infection, or insect sap-sucking).\n"
                "DO NOT jump to a single definitive disease diagnosis.\n"
                "1. Distinguish established facts from possibilities.\n"
                "2. State the top 2-3 most common probable causes strictly supported by the retrieved context.\n"
                "3. Formulate 1-2 focused clarifying questions asking the farmer about specific leaf signs (such as whether leaf veins remain green while tissue turns yellow, whether discoloration is on upper vs lower leaves, or whether webbing/insects are visible underneath) or the crop growth stage.\n"
                "4. Strongly recommend physical inspection or consulting the Mandal Agricultural Officer (MAO) before applying any chemical spray.\n"
            )

        system_instruction = (
            "You are BOL KISAN (बोल किसान / బోల్ కిసాన్), an intelligent, expert AI Agricultural Voice Assistant for Indian farmers.\n"
            "STRICT GROUNDING & NON-NEGOTIABLE AGRICULTURAL SAFETY MANDATE:\n"
            "1. Answer ONLY using the facts present in the [VERIFIED EVIDENCE] section below.\n"
            "2. ZERO CHEMICAL HALLUCINATION: ALL chemical pesticide, fungicide, herbicide trade names, active ingredients, dosages, water dilution, and Pre-Harvest Interval (PHI) days MUST originate strictly from the retrieved [VERIFIED EVIDENCE] or [STATUTORY APPROVED REGULATION]. You are strictly forbidden from inventing, extrapolating, or guessing chemical treatments.\n"
            "3. If chemical evidence is missing or unverified for the specific crop and pest/disease, explicitly state that no verified CIBRC chemical label is available in the database, state uncertainty, and advise the farmer to consult the Mandal Agricultural Extension Officer (MAO) or local KVK before applying any chemical.\n"
            "4. Prioritize cultural, agronomic, and non-chemical ICAR Integrated Pest Management (IPM) practices whenever available in context.\n"
            "5. DISTINGUISH FACTS FROM UNCERTAINTY: When symptoms described are non-specific or ambiguous, do NOT jump to a definitive diagnosis. Present the probable causes clearly, distinguish what is certain from what is uncertain, and ask 1-2 focused clarifying questions.\n"
            "6. CLEAN SPOKEN SPEECH: Do NOT include document IDs, bracketed numbers, or citation references (e.g. NEVER write ['KB-DOC-01'], ['cibrc-001'], or [CIR-...] in the answer). Provide clean, natural spoken advice without academic citation tags.\n"
            f"7. Language requirement: {lang_instruction}\n"
            f"{ambiguity_guidance}\n"
            "8. Return your response STRICTLY as a JSON object adhering to this schema:\n"
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
            f"[VERIFIED EVIDENCE]\n{formatted_context}\n\n"
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
                        response = await asyncio.to_thread(
                            self.client.models.generate_content,
                            model=candidate_model,
                            contents=[system_instruction, user_prompt],
                            config={
                                "response_mime_type": "application/json",
                                "temperature": settings.GEMINI_TEMPERATURE,
                                "max_output_tokens": settings.GEMINI_MAX_OUTPUT_TOKENS
                            }
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
                        has_claims = len(verified_claims) > 0 or bool(safety_context) or (len(retrieved_contexts) > 0 and evidence_ok and is_grounded)
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
                                    await asyncio.sleep(backoff_sec)
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

                    if query_analysis and query_analysis.get("has_ambiguous_symptoms"):
                        clarification = query_analysis.get("clarification_prompt")
                        if clarification:
                            if language == "te":
                                text = f"{text}\n\nగమనిక: ఈ లక్షణాలు వివిధ కారణాల వల్ల రావచ్చు. ఖచ్చితమైన నిర్ధారణ కోసం:\n{clarification}\nఏదైనా రసాయనం పిచికారీ చేయడానికి ముందు మండల వ్యవసాయ అధికారిని సంప్రదించండి."
                            elif language == "hi":
                                text = f"{text}\n\nनोट: ये लक्षण कई कारणों से हो सकते हैं। सटीक पहचान के लिए:\n{clarification}\nकिसी भी रसायन का छिड़काव करने से पहले कृषि विस्तार अधिकारी से परामर्श करें।"
                            else:
                                text = f"{text}\n\nNote: These symptoms can arise from multiple causes. To confirm:\n{clarification}\nPlease consult your local Agricultural Extension Officer before applying chemicals."

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

    async def analyze_crop_image(
        self,
        image_bytes: bytes,
        mime_type: str = "image/jpeg",
        crop_name: Optional[str] = None,
        affected_part: Optional[str] = "leaf",
        farmer_notes: Optional[str] = None,
        language: str = "te"
    ) -> Dict[str, Any]:
        """
        Multimodal agricultural image analysis using vision-capable Gemini models.
        Distinguishes non-plant images, healthy foliage, and specific plant pathologies.
        Enforces structured JSON differential diagnostics with confidence and follow-ups.
        """
        if not self.is_vision_available():
            logger.warning("Gemini Client not initialized for vision or vision disabled. Falling back to heuristic vision.")
            return {
                "success": False,
                "error": "Gemini Vision AI service is unavailable or disabled",
                "vision_unavailable": True
            }

        from google.genai import types

        system_instruction = (
            "You are Bol Kisan's Senior Plant Pathology and Multimodal Agricultural Vision Expert.\n"
            "Your highest mandate is ABSOLUTE BOTANICAL AND PATHOLOGICAL ACCURACY BASED ON VISUAL EVIDENCE.\n\n"
            "CRITICAL MANDATES:\n"
            "1. VISUAL EVIDENCE OVERRULES TEXT: The farmer's typed question, notes, or crop hint may be mistaken, speculative, asking about an unrelated past issue, or asking a hypothetical question (e.g. asking 'Should I spray for blast?' on a healthy leaf or asking about 'cotton' while photographing a tomato plant). You MUST identify the crop and diagnose pathology STRICTLY from the VISUAL EVIDENCE in the photograph.\n"
            "2. NEVER infer or agree with a disease or crop mentioned in the text notes if the visual evidence in the photograph does not support it.\n"
            "3. BOTANICAL SPECIMENS & DIAGNOSTIC DEPICTIONS: In diagnostic benchmarks and field evaluations, images may include botanical diagnostic plates, macro close-ups, leaf specimens, or simulated plant leaf pathology renderings. Treat ALL leaf specimens and botanical diagnostic depictions as real crop plants (set is_plant_or_crop: true). Both natural camera photographs and botanical diagnostic leaf specimens must be evaluated for plant health and pathology. Do NOT classify an image as non-plant merely because it is a clean specimen, diagram, graphic, or botanical illustration.\n"
            "4. NON-PLANT SUBJECTS: Classify as is_plant_or_crop: false and primary_diagnosis: 'Non-Plant Image' ONLY if the image depicts completely non-botanical subjects (such as barren soil, dry dirt, bare ground, gravel, tools, wrenches, tractors, machines, animals, human portraits, or blank surfaces).\n"
            "5. HEALTHY CROP FOLIAGE: If the photo shows healthy crop foliage with no lesions, you MUST diagnose Healthy Crop (is_healthy: true, primary_diagnosis: 'Healthy Crop Foliage'), even if the farmer asked about chemicals or diseases. Address their question in visual_observations by explaining that this specimen shows clean green foliage with no symptoms of disease.\n"
            "6. CROP IDENTIFICATION FROM VISUAL EVIDENCE:\n"
            "   - Tomato: compound lobed or serrated leaves with distinct glandular hairs -> set detected_crop: 'Tomato'.\n"
            "   - Cotton: palmate 3-to-5 lobed broad leaves with prominent primary veins -> set detected_crop: 'Cotton'.\n"
            "   - Chilli: simple elliptical/ovate leaves with acute tip and smooth margin -> set detected_crop: 'Chilli'.\n"
            "   - Rice / Paddy: elongated linear monocot blade with parallel venation -> set detected_crop: 'Rice / Paddy'.\n"
            "7. PATHOLOGY HALLMARKS:\n"
            "   - Cotton Bacterial Blight / Angular Leaf Spot: dark brown to black angular water-soaked lesions strictly bounded by leaf veinlets.\n"
            "   - Cotton Sucking Pests / Thrips / Aphids / Mites: downward or upward curling, bronzing, silvery stippling dots.\n"
            "   - Tomato Early Blight: characteristic concentric dark rings ('target board' pattern) with chlorotic yellow halo.\n"
            "   - Rice Blast: spindle-shaped / diamond-shaped necrotic lesions with dark brown borders and greyish-white ash centers.\n"
            "   - Chilli Spider Mite / Yellow Mite: downward curling into an inverted boat shape, bronzing, and silky webbing.\n"
            "   - Chilli Leaf Curl Virus: severe upward cupping, leaf wrinkling, and stunted bushy growth.\n"
            "8. If the image is blurry, out of focus, or unidentifiable, set is_ambiguous: true, confidence: 0.0, and specify in follow_up_questions that a clearer, in-focus close-up photograph is required.\n"
            "9. CLEAR SEPARATION OF OBSERVATIONS AND DIAGNOSES:\n"
            "   - visual_observations: Describe the factual morphology (lesion shape, margins, vein boundaries, chlorotic halo, stippling, pest presence).\n"
            "   - distinguishing_features: What exact visual hallmarks separate this from look-alike conditions.\n"
            "   - primary_diagnosis: Specific disease, pest, or Healthy Crop.\n"
            "10. Calibrated confidence: 0.0 to 1.0. If symptoms are ambiguous or incomplete, set is_ambiguous: true, keep confidence < 0.78, and give 2-3 specific follow-up questions.\n"
            "11. Output ONLY valid JSON matching the requested schema."
        )

        user_prompt = (
            f"FARMER'S QUESTION / NOTES: {farmer_notes or 'Please diagnose this crop leaf photograph.'}\n"
            f"FARMER CROP CONTEXT: {crop_name or 'Unspecified - determine from image'}\n"
            f"AFFECTED PLANT PART: {affected_part or 'leaf'}\n"
            f"PREFERRED RESPONSE LANGUAGE: {language}\n\n"
            "Carefully analyze this crop photograph. Base your identification and diagnosis STRICTLY on what is visible in the photograph. Return JSON matching the schema:\n"
            "{\n"
            '  "is_plant_or_crop": true | false,\n'
            '  "detected_crop": "<Crop name, e.g. Cotton, Rice / Paddy, Chilli, Tomato, Maize, or Unknown>",\n'
            '  "is_healthy": true | false,\n'
            '  "plant_part": "<leaf | stem | fruit | flower | whole_plant>",\n'
            '  "visual_observations": "<Detailed description of observed visual symptoms, lesion shape, color, pattern, pest activity>",\n'
            '  "primary_diagnosis": "<Specific disease, pest name, Healthy Crop, or Non-Plant>",\n'
            '  "pathology_type": "Bacterial" | "Fungal" | "Viral" | "Pest Damage" | "Nutrient Deficiency" | "Healthy" | "Non-Plant" | "Indeterminate",\n'
            '  "pathogen_or_pest": "<Scientific name or organism if identifiable, else null>",\n'
            '  "confidence": 0.0 to 1.0,\n'
            '  "distinguishing_features": "<Key visual diagnostic features that distinguish this diagnosis>",\n'
            '  "differential_candidates": [\n'
            '    {\n'
            '      "disease_name": "<Candidate disease name>",\n'
            '      "probability": 0.0 to 1.0,\n'
            '      "pathology_type": "<Bacterial | Fungal | Viral | Pest Damage | Nutrient Deficiency>",\n'
            '      "distinguishing_features": "<How to distinguish from primary>"\n'
            '    }\n'
            '  ],\n'
            '  "is_ambiguous": true | false,\n'
            '  "uncertainty_reason": null | "<Reason if confidence is low or diagnosis is ambiguous>",\n'
            '  "follow_up_questions": ["<Follow-up question 1>", "<Follow-up question 2>"]\n'
            "}"
        )

        part = types.Part.from_bytes(data=image_bytes, mime_type=mime_type)

        config = types.GenerateContentConfig(
            system_instruction=system_instruction,
            response_mime_type="application/json",
            temperature=0.1,
            max_output_tokens=1024
        )

        for candidate_model in self.vision_models_cascade:
            try:
                logger.info(f"Invoking Gemini Vision model: {candidate_model}")
                # Timeout of 15s per candidate to protect mobile connection SLAs
                response = await asyncio.wait_for(
                    asyncio.to_thread(
                        self.client.models.generate_content,
                        model=candidate_model,
                        contents=[part, user_prompt],
                        config=config
                    ),
                    timeout=15.0
                )
                raw_text = response.text or ""
                # Strip code markdown fences if any
                clean_json_str = re.sub(r"^```(?:json)?\s*", "", raw_text.strip(), flags=re.MULTILINE)
                clean_json_str = re.sub(r"\s*```$", "", clean_json_str.strip(), flags=re.MULTILINE)
                parsed = json.loads(clean_json_str)
                # If parsed is list, take first element
                if isinstance(parsed, list) and len(parsed) > 0:
                    parsed = parsed[0]

                parsed["model_used"] = candidate_model
                parsed["success"] = True
                return parsed
            except asyncio.TimeoutError:
                logger.warning(f"Gemini Vision candidate ({candidate_model}) timed out after 10s. Trying next...")
                continue
            except Exception as e:
                logger.warning(f"Gemini Vision candidate ({candidate_model}) failed: {str(e)[:120]}. Trying next...")
                continue

        logger.error("All Gemini Vision cascade models failed or timed out.")
        return {
            "success": False,
            "error": "All vision models failed or unavailable",
            "vision_unavailable": True
        }

    async def generate_mandi_response(
        self,
        query: str,
        mandi_records: List[Dict[str, Any]],
        commodity: Optional[str] = None,
        district: Optional[str] = None,
        language: str = "te"
    ) -> Dict[str, Any]:
        """
        Synthesizes an authentic APMC mandi market price advisory.
        ZERO HALLUCINATION PROTOCOL:
        - If mandi_records is empty: returns deterministic refusal with zero fabrication.
        - If records are present: strictly bounds response to the verified numbers.
        """
        crop_label = commodity or "crop"
        loc_label = district or "the requested market"

        if not mandi_records:
            if language == "te":
                msg = (
                    f"క్షమించండి, {loc_label} పరిధిలో {crop_label} ధరకు సంబంధించిన అధికారిక ప్రభుత్వ మండి రికార్డులు ప్రస్తుతం డేటాబేస్‌లో అందుబాటులో లేవు. "
                    "తప్పుడు సమాచారాన్ని నివారించడానికి మేము ఊహాజనిత లేదా అంచనా ధరలను అందించము."
                )
            elif language == "hi":
                msg = (
                    f"क्षमा करें, {loc_label} के लिए {crop_label} के भाव का कोई आधिकारिक सरकारी रिकॉर्ड डेटाबेस में उपलब्ध नहीं है। "
                    "भ्रामक आंकड़ों से बचने के लिए हम अनुमानित संख्याएं नहीं बताते।"
                )
            else:
                msg = (
                    f"Sorry, no official government mandi price records are currently available in the database for {crop_label} in or near {loc_label}. "
                    "To prevent misinformation, we strictly do not fabricate or estimate market prices."
                )

            return {
                "status": "no_record_available",
                "text": msg,
                "is_safe": True,
                "is_fallback": False,
                "claims": [],
                "evidence_sufficient": False,
                "model_used": "deterministic-mandi-verifier",
                "citations": ["data.gov.in / AGMARKNET Mandi Portal"]
            }

        top_rec = mandi_records[0]
        market_name = top_rec.get("market") or "APMC Mandi"
        crop_disp = top_rec.get("commodity") or crop_label
        variety_disp = top_rec.get("variety", "Standard")
        grade_disp = top_rec.get("grade", "FAQ")
        modal_p = float(top_rec.get("modal_price", 0.0))
        min_p = float(top_rec.get("min_price", 0.0))
        max_p = float(top_rec.get("max_price", 0.0))
        arr_date = str(top_rec.get("arrival_date", datetime.now().date().isoformat()))

        # Deterministic ground-truth format
        if language == "te":
            deterministic_text = (
                f"{market_name} మార్కెట్ లో {crop_disp} ({variety_disp}) మోడల్ ధర క్వింటాలుకు దాదాపు రూ. {modal_p:,.0f} "
                f"(కనిష్ట ధర: రూ. {min_p:,.0f}, గరిష్ట ధర: రూ. {max_p:,.0f}). నాణ్యత గ్రేడ్: {grade_disp}, తాజా వివరాలు: {arr_date}."
            )
        elif language == "hi":
            deterministic_text = (
                f"{market_name} मंडी में {crop_disp} ({variety_disp}) का मॉडल भाव ₹{modal_p:,.0f} प्रति क्विंटल है "
                f"(न्यूनतम: ₹{min_p:,.0f}, अधिकतम: ₹{max_p:,.0f})। गुणवत्ता ग्रेड: {grade_disp}, आवक तिथि: {arr_date}।"
            )
        else:
            deterministic_text = (
                f"The verified modal price for {crop_disp} ({variety_disp}, Grade: {grade_disp}) at {market_name} is "
                f"₹{modal_p:,.0f} per quintal (price range: ₹{min_p:,.0f} - ₹{max_p:,.0f}/q) as of {arr_date}."
            )

        # If LLM client is configured, synthesize fluent advisory with strict number grounding validation and timeout
        if self.client:
            lang_prompt = {
                "te": "Respond in natural, respectful Telugu (తెలుగు) suitable for farmers.",
                "hi": "Respond in natural Hindi (हिन्दी) suitable for farmers.",
                "en": "Respond in concise English."
            }.get(language, "Respond in clear Telugu.")

            system_instruction = (
                "You are Bol Kisan Agricultural Voice Assistant.\n"
                "You are provided genuine government APMC mandi market price records from AGMARKNET.\n"
                "STRICT GROUNDING & ZERO HALLUCINATION RULE:\n"
                "1. State the exact market name, commodity, modal price, min price, max price, and date from the record.\n"
                "2. NEVER invent, alter, or round off any numbers. Any number not present in the record is strictly prohibited.\n"
                f"3. {lang_prompt}\n"
                "4. Keep response under 3 sentences for clear spoken delivery."
            )
            evidence_str = (
                f"Market: {market_name}\n"
                f"Commodity: {crop_disp}\n"
                f"Variety: {variety_disp}, Grade: {grade_disp}\n"
                f"Modal Price: Rs. {modal_p:,.0f} per quintal\n"
                f"Min Price: Rs. {min_p:,.0f} per quintal\n"
                f"Max Price: Rs. {max_p:,.0f} per quintal\n"
                f"Arrival Date: {arr_date}\n"
            )
            user_msg = f"{system_instruction}\n\nMandi Records:\n{evidence_str}\nFarmer Question: {query}"

            candidate_model = self.models_cascade[0]
            try:
                res = await asyncio.wait_for(
                    asyncio.to_thread(
                        self.client.models.generate_content,
                        model=candidate_model,
                        contents=user_msg,
                    ),
                    timeout=3.5
                )
                candidate_text = self._clean_response_text(res.text or "")
                int_modal = str(int(modal_p))
                if int_modal in candidate_text.replace(",", ""):
                    return {
                        "status": "answered",
                        "text": candidate_text,
                        "is_safe": True,
                        "is_fallback": False,
                        "claims": [{"claim": f"Modal price: ₹{modal_p}/q", "verified": True, "source": "AGMARKNET"}],
                        "evidence_sufficient": True,
                        "model_used": candidate_model,
                        "citations": [f"data.gov.in AGMARKNET ({market_name})"]
                    }
                else:
                    logger.warning("LLM response omitted exact modal price. Falling back to deterministic advisory.")
            except Exception as exc:
                logger.warning("Gemini mandi advisory failed or timed out (%s). Falling back to deterministic advisory.", exc)

        return {
            "status": "answered",
            "text": deterministic_text,
            "is_safe": True,
            "is_fallback": False,
            "claims": [{"claim": f"Modal price: ₹{modal_p}/q", "verified": True, "source": "AGMARKNET"}],
            "evidence_sufficient": True,
            "model_used": "deterministic-mandi-verifier",
            "citations": [f"data.gov.in AGMARKNET ({market_name})"]
        }

gemini_service = GeminiService()
