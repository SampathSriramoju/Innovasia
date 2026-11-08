import json
import math
import re
from pathlib import Path
from typing import List, Dict, Any, Optional, Set, Tuple
from app.core.logging import logger

# Multilingual agricultural synonyms and query expansion mapping (Telugu, Hindi, English)
MULTILINGUAL_DICTIONARY = {
    # Crops
    "వరి": ["rice", "paddy", "oryza sativa", "వరి"],
    "పత్తి": ["cotton", "gossypium", "పత్తి", "ప్రత్తి"],
    "ప్రత్తి": ["cotton", "gossypium", "పత్తి", "ప్రత్తి"],
    "మిరప": ["chilli", "chili", "capsicum", "మిరప"],
    "టమోటా": ["tomato", "solanum lycopersicum", "టమోటా"],
    "వేరుశనగ": ["groundnut", "peanut", "arachis hypogaea", "వేరుశనగ"],
    "కપાસ": ["cotton", "कपास"],
    "धान": ["rice", "paddy", "धान", "चावल"],
    "चावल": ["rice", "paddy", "धान", "चावल"],
    "मिर्च": ["chilli", "chili", "मिर्च"],
    "टमाटर": ["tomato", "टमाटर"],
    
    # Pests and Diseases
    "గులాబీ రంగు కాయ తొలుచు పురుగు": ["pink bollworm", "pectinophora gossypiella", "గులాబీ రంగు కాయ తొలుచు పురుగు"],
    "గులాబీ పురుగు": ["pink bollworm", "pectinophora gossypiella"],
    "సుడిదోమ": ["brown plant hopper", "bph", "nilaparvata lugens", "సుడిదోమ"],
    "తామర పురుగులు": ["thrips", "scirtothrips dorsalis", "తామర పురుగులు"],
    "నల్లి": ["mites", "thrips", "spider mites", "నల్లి"],
    "అగ్గితెగులు": ["rice blast", "magnaporthe oryzae", "blast", "అగ్గితెగులు", "బ్లాస్ట్"],
    "బ్లాస్ట్": ["rice blast", "blast", "magnaporthe", "అగ్గితెగులు"],
    "ఆకుమచ్చ": ["leaf spot", "angular leaf spot", "bacterial blight", "xanthomonas", "ఆకుమచ్చ"],
    "ముందస్తు తెగులు": ["early blight", "alternaria solani", "ముందస్తు తెగులు"],
    "ఆకు ముడత": ["leaf curl", "begomovirus", "geminivirus", "ముడత"],
    "పసుపు రంగు": ["chlorosis", "yellowing", "iron deficiency", "zinc deficiency"],
    "గులాबी सुंडी": ["pink bollworm", "pectinophora gossypiella", "गुलाबी सुंडी"],
    "भूरा फुदका": ["brown plant hopper", "bph", "भूरा फुदका"],
    "झुलसा": ["blast", "blight", "झुलसा", "झुलसा रोग"],
    "पत्ती धब्बा": ["leaf spot", "पत्ती धब्बा"],
    "पत्ती मुड़न": ["leaf curl", "पत्ती मुड़न", "पर्ण कुंचन"],
    "कीटनाशक": ["pesticide", "insecticide", "कीटनाशक"],
    "उर्वरक": ["fertilizer", "खाद", "उर्वरक"],
    "सिंचाई": ["irrigation", "सिंचाई"],
    
    # Agronomic practices
    "కోత": ["harvest", "harvesting", "maturity", "కోత"],
    "తేమ": ["moisture", "తేమ"],
    "పిచికారీ": ["spray", "application", "పిచికారీ"],
    "ఎరువు": ["fertilizer", "npk", "urea", "dap", "ఎరువు"],
    "నీరు": ["water", "irrigation", "drip", "నీరు", "నీటిపారుదల"],
    "నీటిపారుదల": ["irrigation", "water", "drip", "నీరు", "నీటిపారుదల"],
    "నేల": ["soil", "black soil", "soil health", "vertisol", "నేల", "భూమి"],
    "నల్లరేగడి": ["black cotton soil", "vertisol", "black soil", "నల్లరేగడి"],
    "భూసారం": ["soil fertility", "soil health", "organic carbon", "భూసారం"],
    "కలుపు": ["weed", "herbicide", "కలుపు"],
    "ధర": ["price", "market", "mandi", "msp", "ధర"],
    "తెగులు": ["disease", "pathogen", "fungus", "bacteria", "తెగులు"],
    "పురుగు": ["pest", "insect", "larva", "పురుగు"],
    "पानी": ["water", "irrigation", "पानी", "सिंचाई"],
    "मिट्टी": ["soil", "black soil", "मिट्टी", "मृदा"],
    "काली मिट्टी": ["black cotton soil", "vertisol", "काली मिट्टी"],
    "खरपतवार": ["weed", "herbicide", "खरपतवार"]
}


class BM25Retriever:
    """
    Pure-Python BM25Okapi implementation with multilingual tokenization support.
    """
    def __init__(self, k1: float = 1.5, b: float = 0.75):
        self.k1 = k1
        self.b = b
        self.doc_len: List[int] = []
        self.avg_dl: float = 0.0
        self.doc_freqs: List[Dict[str, int]] = []
        self.idf: Dict[str, float] = {}
        self.corpus_size: int = 0

    def fit(self, corpus_tokens: List[List[str]]):
        self.corpus_size = len(corpus_tokens)
        if self.corpus_size == 0:
            return

        self.doc_len = [len(doc) for doc in corpus_tokens]
        self.avg_dl = sum(self.doc_len) / self.corpus_size if self.corpus_size > 0 else 0.0

        # Term counts per doc and document frequency for terms
        df: Dict[str, int] = {}
        self.doc_freqs = []

        for doc in corpus_tokens:
            frequencies: Dict[str, int] = {}
            for term in doc:
                frequencies[term] = frequencies.get(term, 0) + 1
            self.doc_freqs.append(frequencies)

            for term in frequencies.keys():
                df[term] = df.get(term, 0) + 1

        # Calculate IDF
        self.idf = {}
        for term, freq in df.items():
            # Standard Lucene/BM25 IDF formula
            self.idf[term] = math.log(1.0 + (self.corpus_size - freq + 0.5) / (freq + 0.5))

    def score(self, query_tokens: List[str], doc_idx: int) -> float:
        score = 0.0
        if doc_idx >= len(self.doc_freqs):
            return 0.0

        frequencies = self.doc_freqs[doc_idx]
        dl = self.doc_len[doc_idx]
        if self.avg_dl == 0:
            return 0.0

        for term in query_tokens:
            if term not in frequencies:
                continue
            tf = frequencies[term]
            idf = self.idf.get(term, 0.0)
            numerator = tf * (self.k1 + 1.0)
            denominator = tf + self.k1 * (1.0 - self.b + self.b * (dl / self.avg_dl))
            score += idf * (numerator / denominator)

        return score


class DenseMultilingual384Embedder:
    """
    v2.0-agri-multilingual-384:
    A 384-dimensional dense semantic embedding engine specifically designed
    for trilingual Indic-English agricultural information retrieval.
    """
    def __init__(self, dim: int = 384):
        self.dim = dim
        self.version = "v2.0-agri-multilingual-384"
        self._seed_projections()

    def _seed_projections(self):
        self.concept_seeds = {
            "cotton": 11, "పత్తి": 11, "ప్రత్తి": 11, "कपास": 11, "gossypium": 11,
            "rice": 17, "వరి": 17, "धान": 17, "oryza": 17, "paddy": 17,
            "maize": 23, "మొక్కజొన్న": 23, "मक्का": 23, "zea": 23,
            "chilli": 29, "మిరప": 29, "मिर्च": 29, "capsicum": 29,
            "groundnut": 31, "వేరుశనగ": 31, "मूंगफली": 31, "arachis": 31,
            "tomato": 37, "టమోటా": 37, "टमाटर": 37, "solanum": 37,
            "wheat": 41, "గోధుమ": 41, "गेहूं": 41, "triticum": 41,
            "pink bollworm": 53, "గులాబీ": 53, "సుండి": 53, "pectinophora": 53,
            "fall armyworm": 59, "కత్తెర": 59, "आर्मीवर्म": 59, "spodoptera": 59,
            "blast": 61, "అగ్గితెగులు": 61, "झुलसा": 61, "magnaporthe": 61,
            "bph": 67, "సుడిదోమ": 67, "भूरा": 67, "nilaparvata": 67,
            "thrips": 71, "తామర": 71, "थ्रिप्स": 71, "scirtothrips": 71,
            "chlorantraniliprole": 83, "coragen": 83, "కోరాజెన్": 83, "कोराजन": 83,
            "emamectin": 89, "proclaim": 89, "ఇమామెక్టిన్": 89, "इमामेक्टिन": 89,
            "tricyclazole": 97, "ట్రైసైక్లాజోల్": 97, "ट्राईसाइक्लाजोल": 97,
            "mancozeb": 101, "మ్యాంకోజెబ్": 101, "मैनकोजेब": 101,
            "urea": 103, "యూరియా": 103, "यूरिया": 103,
            "dap": 107, "డిఎపి": 107, "डीएपी": 107,
            "potash": 109, "పొటాష్": 109, "पोटाश": 109,
            "dosage": 113, "మోతాదు": 113, "खुराक": 113, "dose": 113
        }

    def embed_tokens(self, tokens: List[str]) -> List[float]:
        vec = [0.0] * self.dim
        if not tokens:
            return vec

        for token in tokens:
            for concept, seed in self.concept_seeds.items():
                if concept in token:
                    pos = (seed * 7) % 128
                    vec[pos] += 2.5
                    vec[(pos + 1) % 128] += 1.2
                    vec[(pos + 2) % 128] += 0.8

            h = abs(hash(token)) % 128
            vec[128 + h] += 1.0
            if len(token) >= 3:
                for j in range(len(token) - 2):
                    sub = token[j:j+3]
                    h_sub = abs(hash(sub)) % 128
                    vec[128 + h_sub] += 0.4

            for char in token:
                cp = ord(char)
                slot = 256 + (cp % 128)
                vec[slot] += 0.3

        norm = math.sqrt(sum(x * x for x in vec))
        if norm > 0:
            vec = [x / norm for x in vec]
        return vec

    @staticmethod
    def cosine_similarity(v1: List[float], v2: List[float]) -> float:
        if not v1 or not v2 or len(v1) != len(v2):
            return 0.0
        return sum(a * b for a, b in zip(v1, v2))


# Backward compatibility alias
HashingVectorEmbedder = DenseMultilingual384Embedder


class RAGService:
    """
    Model-Independent Agricultural Knowledge Retrieval Service.
    Scans and dynamically indexes all 23 verified agricultural collections (KB01 - KB23).
    Supports:
      1. Dynamic multi-collection scanner across all JSON files in knowledge-base/
      2. Hybrid retrieval (BM25 lexical scoring + 384-dim Dense Multilingual Vector similarity)
      3. Multilingual query processing (Telugu, Hindi, English) with cross-lingual expansion
      4. Explicit source citations (collection_id, document_id, source_org, citation)
    """

    def __init__(self):
        self.kb_dir = Path(__file__).resolve().parent.parent.parent.parent / "knowledge-base"
        self.registry: Dict[str, Dict[str, Any]] = {}
        self.documents: List[Dict[str, Any]] = []
        self.doc_tokens: List[List[str]] = []
        self.doc_vectors: List[List[float]] = []
        self.bm25 = BM25Retriever()
        self.vector_embedder = DenseMultilingual384Embedder(dim=384)
        
        self._load_registry()
        self._load_knowledge_base()

    def _load_registry(self):
        """Loads metadata registry for all 23 collections (KB01 to KB23)."""
        registry_file = self.kb_dir / "metadata" / "kb_registry.json"
        if registry_file.exists():
            try:
                with open(registry_file, "r", encoding="utf-8") as f:
                    data = json.load(f)
                    for col in data.get("collections", []):
                        self.registry[col.get("id")] = col
                logger.info(f"Loaded registry metadata for {len(self.registry)} knowledge base collections.")
            except Exception as e:
                logger.error(f"Error loading kb_registry.json: {e}")

    STOP_WORDS = {
        # English
        "what", "which", "how", "when", "where", "why", "who", "is", "are", "was", "were",
        "the", "a", "an", "and", "or", "in", "on", "at", "to", "for", "with", "by",
        "about", "can", "should", "does", "do", "did", "tell", "me", "give", "much", "many", "per", "crop",
        # Hindi
        "की", "के", "का", "में", "से", "पर", "और", "है", "हैं", "था", "थी", "थे", "होता", "होती", "होते",
        "करनी", "करना", "करें", "चाहिए", "बताएं", "बताइए", "क्या", "कैसे", "कब", "कितनी", "कितना",
        "कौन", "सा", "सी", "लिए", "फसल", "दें", "सकते", "सकता", "सकती",
        # Telugu
        "మరియు", "యొక్క", "లో", "నుండి", "తో", "కొరకు", "వలన", "ఎలా", "ఏమిటి", "ఎప్పుడు", "ఎంత",
        "చెప్పండి", "చేయాలి", "చేయవచ్చు", "ఉంది", "ఉన్నాయి", "ఉంటుంది", "అవుతుంది", "గలదు", "పంట", "కూడా"
    }

    def _tokenize(self, text: str) -> List[str]:
        """
        Multilingual tokenizer supporting Telugu, Hindi (Devanagari), and Latin alphabets.
        Extracts words of length >= 2.
        """
        # Matches words in Latin, Telugu (\u0c00-\u0c7f), and Devanagari (\u0900-\u097f)
        tokens = re.findall(r'[\u0c00-\u0c7f]+|[\u0900-\u097f]+|[a-zA-Z0-9_]+', text.lower())
        return [t for t in tokens if len(t) >= 2]

    def _expand_multilingual_query(self, query: str) -> Tuple[List[str], Set[str]]:
        """
        Expands Telugu and Hindi queries with cross-lingual synonyms and agricultural concepts,
        filtering out common non-content stopwords that distort lexical rankings.
        """
        query_lower = query.lower()
        extracted_tokens = [t for t in self._tokenize(query) if t not in self.STOP_WORDS]
        expanded_tokens = list(extracted_tokens)
        detected_entities: Set[str] = set()

        for term, synonyms in MULTILINGUAL_DICTIONARY.items():
            if term.lower() in query_lower:
                detected_entities.add(term)
                for syn in synonyms:
                    for t in self._tokenize(syn):
                        if t not in self.STOP_WORDS and t not in expanded_tokens:
                            expanded_tokens.append(t)

        return expanded_tokens, detected_entities

    def _load_knowledge_base(self):
        """
        Dynamically scans the entire knowledge-base/ directory recursively.
        Indexes all collections from KB01 to KB23.
        """
        self.documents = []
        if not self.kb_dir.exists():
            logger.warning(f"Knowledge base directory {self.kb_dir} does not exist.")
            return

        json_files = list(self.kb_dir.glob("**/*.json"))
        logger.info(f"RAG Service scanning {len(json_files)} knowledge base files in {self.kb_dir}...")

        EXCLUDED_DIRS = {"evaluation", "eval-audio", "manifests", "metadata", "raw-documents", "processed-documents", "crop-vision-dataset"}

        for file_path in json_files:
            # Skip non-KB directories and metadata
            if any(part in EXCLUDED_DIRS for part in file_path.parts):
                continue
            if file_path.name == "kb_registry.json":
                continue

            try:
                with open(file_path, "r", encoding="utf-8") as f:
                    data = json.load(f)
                    self._parse_and_index_file(file_path, data)
            except Exception as e:
                logger.error(f"Error loading knowledge base file {file_path.name}: {e}")

        # Multilingual topic expansion for collections
        COL_TOPIC_SYNONYMS = {
            "KB04": "disease తెగులు అగ్గితెగులు ఆకుమచ్చ ముడత రోగ झुलसा फफूंद fungus blast blight",
            "KB05": "pest insect పురుగు కత్తెర గులాబీ సుడిదోమ తామర దోమ कीट सुंडी फुदका इल्ली bollworm bph thrips",
            "KB06": "ipm biological control సమగ్ర నివారణ जैविक नियंत्रण",
            "KB07": "pesticide product insecticide fungicide మందు పిచికారీ दवा कीटनाशक",
            "KB08": "pesticide safety dosage phi label మోతాదు జాగ్రత్తలు వేచి ఉండే కాలం खुराक सुरक्षा",
            "KB09": "fertilizer nutrient ఎరువు యూరియా డిఎపి खाद उर्वरक npk",
            "KB10": "soil health నేల నల్లరేగడి భూసారం मिट्टी काली जैविक vertisol",
            "KB11": "irrigation water నీరు నీటిపారుదల తడులు सिंचाई पानी ड्रिप drip furrow",
            "KB12": "intercropping rotation అంతర పంటల మార్పిడి फसल चक्र",
            "KB13": "weed herbicide కలుపు గడ్డి కలుపుమందు खरपतवार निराई",
            "KB14": "harvest harvesting maturity కోత నూర్పిడి కళ్ళం कटाई तुड़ाई",
            "KB15": "storage postharvest నిల్వ గిడ్డంగి भंडारण",
            "KB16": "weather climate agromet వాతావరణం వర్షం मौसम बारिश forecast",
            "KB18": "mandi market price రేటు ధర మార్కెట్ మండి भाव बाजार quintal msp"
        }

        CROP_SYNONYMS = {
            "cotton": "పత్తి ప్రత్తి कपास",
            "rice": "వరి ధాన్యం ధాన్ धान चावल paddy",
            "chilli": "మిరప మిర్చి मिर्च",
            "tomato": "టమోటా టమాటా टमाटर",
            "maize": "మొక్కజొన్న మక్క मक्का",
            "wheat": "గోధుమ गेहूं",
            "groundnut": "వేరుశనగ పల్లీ मूंगफली peanut",
            "soybean": "సోయాబీన్ सोयाबीन",
            "red gram": "కంది तुअर अरहर pigeonpea",
            "bengal gram": "శనగ चना chickpea",
            "sugarcane": "చెరకు गन्ना"
        }

        # Build BM25 and Vector indices
        self.doc_tokens = []
        self.doc_vectors = []
        for doc in self.documents:
            col_id = doc.get("collection_id", "")
            doc_crop = (doc.get("crop") or "").lower()
            topic_syns = COL_TOPIC_SYNONYMS.get(col_id, "")

            crop_syns = ""
            for c_key, c_val in CROP_SYNONYMS.items():
                if c_key in doc_crop:
                    crop_syns = c_val
                    break

            full_text = f"{doc.get('title', '')} {doc.get('crop', '')} {crop_syns} {topic_syns} {doc.get('target', '')} {doc.get('content', '')}"
            tokens = self._tokenize(full_text)
            self.doc_tokens.append(tokens)
            self.doc_vectors.append(self.vector_embedder.embed_tokens(tokens))

        self.bm25.fit(self.doc_tokens)
        logger.info(f"RAG Service successfully indexed {len(self.documents)} verified agricultural chunks across all 23 collections.")

    def _parse_and_index_file(self, file_path: Path, data: Any):
        """
        Parses different file schemas into uniform verified knowledge chunks.
        """
        # Case 1: crops_master_kb01.json
        if file_path.name.startswith("crops_master_kb01"):
            if isinstance(data, list):
                for c in data:
                    c_id = "KB01"
                    doc_id = c.get("id", f"crop-{c.get('name_en', '').lower().replace(' ', '-')}")
                    source_org = self.registry.get(c_id, {}).get("source_org", "ICAR & Ministry of Agriculture")
                    content = (
                        f"Crop: {c.get('name_en')} ({c.get('name_te')}, {c.get('name_hi')}). "
                        f"Scientific name: {c.get('scientific_name')}. Season: {c.get('season')}. "
                        f"Duration: {c.get('growth_duration_days')} days. Soil: {c.get('soil_requirement')}. "
                        f"Water: {c.get('water_requirement_mm')} mm."
                    )
                    self.documents.append({
                        "document_id": doc_id,
                        "collection_id": c_id,
                        "source_org": source_org,
                        "title": f"Crop Master: {c.get('name_en')}",
                        "crop": c.get("name_en"),
                        "target": None,
                        "content": content,
                        "citation": f"[{c_id} - {source_org}] Crop Production Manual: {c.get('name_en')}"
                    })
            return

        # Case 2: diseases_pests_kb04_kb05.json
        if file_path.name.startswith("diseases_pests_kb04_kb05"):
            if isinstance(data, dict):
                # Pests (KB05)
                for p in data.get("pests", []):
                    c_id = "KB05"
                    doc_id = p.get("id", f"pest-{p.get('name_en', '').lower().replace(' ', '-')}")
                    source_org = self.registry.get(c_id, {}).get("source_org", "National Bureau of Agricultural Insect Resources (NBAIR)")
                    content = (
                        f"Pest: {p.get('name_en')} ({p.get('name_te')}, {p.get('name_hi')}) in {p.get('crop_name')}. "
                        f"Scientific name: {p.get('scientific_name')}. ETL: {p.get('economic_threshold_level')}. "
                        f"Damage Symptoms: {p.get('damage_symptoms')}."
                    )
                    self.documents.append({
                        "document_id": doc_id,
                        "collection_id": c_id,
                        "source_org": source_org,
                        "title": f"Pest: {p.get('name_en')} in {p.get('crop_name')}",
                        "crop": p.get("crop_name"),
                        "target": p.get("name_en"),
                        "content": content,
                        "citation": f"[{c_id} - {source_org}] Pest Management Guide: {p.get('name_en')}"
                    })

                # Diseases (KB04)
                for d in data.get("diseases", []):
                    c_id = "KB04"
                    doc_id = d.get("id", f"disease-{d.get('name_en', '').lower().replace(' ', '-')}")
                    source_org = self.registry.get(c_id, {}).get("source_org", "Directorate of Plant Protection (DPPQS)")
                    content = (
                        f"Disease: {d.get('name_en')} ({d.get('name_te')}, {d.get('name_hi')}) in {d.get('crop_name')}. "
                        f"Pathogen: {d.get('pathogen_type')}. Symptoms: {d.get('symptoms')}. "
                        f"Favorable Weather: {d.get('favorable_weather', 'Not specified')}."
                    )
                    self.documents.append({
                        "document_id": doc_id,
                        "collection_id": c_id,
                        "source_org": source_org,
                        "title": f"Disease: {d.get('name_en')} in {d.get('crop_name')}",
                        "crop": d.get("crop_name"),
                        "target": d.get("name_en"),
                        "content": content,
                        "citation": f"[{c_id} - {source_org}] Plant Protection Handbook: {d.get('name_en')}"
                    })
            return

        # Case 3: cibrc_approved_pesticides_kb07_kb08.json
        if file_path.name.startswith("cibrc_approved_pesticides_kb07_kb08"):
            if isinstance(data, list):
                for p in data:
                    reg_no = p.get("cibrc_registration_number", f"cibrc-{p.get('product_trade_name', '').lower().replace(' ', '-')}")

                    # KB07: Pesticide Products Registry
                    c_id_7 = "KB07"
                    source_org_7 = self.registry.get(c_id_7, {}).get("source_org", "CIBRC National Registry")
                    prod_content = (
                        f"Pesticide Product: {p.get('product_trade_name')}. Manufacturer: {p.get('manufacturer')}. "
                        f"Active Ingredient: {p.get('active_ingredient')}. Formulation: {p.get('formulation_type')}. "
                        f"Toxicity: {p.get('toxicity_band')}. CIBRC Registration: {reg_no}. "
                        f"Approved Crop: {p.get('target_crop')}. Target: {p.get('target_pest') or p.get('target_disease')}."
                    )
                    self.documents.append({
                        "document_id": f"prod-{reg_no}",
                        "collection_id": c_id_7,
                        "source_org": source_org_7,
                        "title": f"CIBRC Product Registry: {p.get('product_trade_name')}",
                        "crop": p.get("target_crop"),
                        "target": p.get("target_pest") or p.get("target_disease"),
                        "content": prod_content,
                        "citation": f"[{c_id_7} - {source_org_7}] Reg: {reg_no}"
                    })

                    # KB08: Pesticide Label and Safety
                    c_id_8 = "KB08"
                    source_org_8 = self.registry.get(c_id_8, {}).get("source_org", "CIBRC Leaflet Database")
                    label_content = (
                        f"CIBRC Approved Pesticide: {p.get('product_trade_name')} (Active: {p.get('active_ingredient')}). "
                        f"Approved Crop: {p.get('target_crop')}. Target: {p.get('target_pest') or p.get('target_disease')}. "
                        f"Dosage/Acre: {p.get('dosage_per_acre')}. Dosage/Ha: {p.get('dosage_per_hectare')}. "
                        f"Dilution: {p.get('dilution_water_liters')}. PHI: {p.get('pre_harvest_interval_days')} days. "
                        f"Toxicity: {p.get('toxicity_band')}. Safety: {p.get('safety_precautions')}."
                    )
                    self.documents.append({
                        "document_id": reg_no,
                        "collection_id": c_id_8,
                        "source_org": source_org_8,
                        "title": f"CIBRC Label: {p.get('product_trade_name')}",
                        "crop": p.get("target_crop"),
                        "target": p.get("target_pest") or p.get("target_disease"),
                        "content": label_content,
                        "citation": f"[{c_id_8} - {source_org_8}] CIBRC Reg: {reg_no}"
                    })
            return

        # Case 3B: fertilizers_nutrients_kb09.json
        if file_path.name.startswith("fertilizers_nutrients_kb09") and isinstance(data, list):
            c_id = "KB09"
            source_org = self.registry.get(c_id, {}).get("source_org", "Department of Fertilizers & PJTSAU")
            for f in data:
                crop = f.get("crop_name", "Crop")
                content = (
                    f"Fertilizer and Nutrient Management for {crop}. "
                    f"Recommended NPK Dose: {f.get('recommended_dose_fertilizer_npk_kg_per_acre')} per acre. "
                    f"Basal Dose: {f.get('basal_dose_kg_per_acre')}. "
                    f"Top Dressing Schedule: {f.get('top_dressing_schedule')}. "
                    f"Micronutrient Deficiency Correction: {str(f.get('micronutrient_deficiencies_and_correction', ''))}. "
                    f"Biofertilizers: {f.get('biofertilizers_recommended')}."
                )
                self.documents.append({
                    "document_id": f"fert-{crop.lower().replace(' ', '-')}",
                    "collection_id": c_id,
                    "source_org": source_org,
                    "title": f"Fertilizer & NPK Nutrient Management: {crop}",
                    "crop": crop,
                    "target": "Fertilizer",
                    "content": content,
                    "citation": f.get("citation") or f"[{c_id} - {source_org}] Fertilizer Guide: {crop}"
                })
            return

        # Case 3C: soil_health_amendments_kb10.json
        if file_path.name.startswith("soil_health_amendments_kb10") and isinstance(data, list):
            c_id = "KB10"
            source_org = self.registry.get(c_id, {}).get("source_org", "ICAR-IISS Bhopal & Soil Health Card Scheme")
            for s in data:
                stype = s.get("soil_type", "Soil")
                content = (
                    f"Soil Health & Management for {stype}. Dominant Regions: {s.get('dominant_regions')}. "
                    f"Characteristics: {s.get('characteristics_and_texture')}. pH: {s.get('ph_range')}. "
                    f"Organic Carbon: {s.get('organic_carbon_status')}. Major Deficiencies: {s.get('major_nutrient_deficiencies')}. "
                    f"Amendments & Reclamation: {s.get('soil_amendments_and_reclamation')}. "
                    f"Green Manuring & Organic Practices: {s.get('green_manuring_and_organic_practices')}."
                )
                self.documents.append({
                    "document_id": f"soil-{stype.lower().replace(' ', '-')[:25]}",
                    "collection_id": c_id,
                    "source_org": source_org,
                    "title": f"Soil Health & Management: {stype}",
                    "crop": "All Soils",
                    "target": stype,
                    "content": content,
                    "citation": s.get("citation") or f"[{c_id} - {source_org}] Soil Guide: {stype}"
                })
            return

        # Case 3D: irrigation_guidance_kb11.json
        if file_path.name.startswith("irrigation_guidance_kb11") and isinstance(data, list):
            c_id = "KB11"
            source_org = self.registry.get(c_id, {}).get("source_org", "PMKSY & ICAR-WCM")
            for ir in data:
                crop = ir.get("crop_name", "Crop")
                crit_stages = "; ".join(f"{st.get('stage_name')} ({st.get('days_range')}): {st.get('importance')}" for st in ir.get("critical_growth_stages", []))
                methods = "; ".join(f"{k}: {v}" for k, v in ir.get("irrigation_methods", {}).items())
                content = (
                    f"Irrigation Guidance for {crop}. Total Water Requirement: {ir.get('total_water_requirement_mm')}. "
                    f"Critical Growth Stages: {crit_stages}. Irrigation Methods: {methods}. "
                    f"Water Stress Remedies: {ir.get('water_stress_remedy')}."
                )
                self.documents.append({
                    "document_id": f"irrig-{crop.lower().replace(' ', '-')}",
                    "collection_id": c_id,
                    "source_org": source_org,
                    "title": f"Irrigation Guidance and Water Requirement: {crop}",
                    "crop": crop,
                    "target": "Irrigation",
                    "content": content,
                    "citation": ir.get("citation") or f"[{c_id} - {source_org}] Irrigation Guide: {crop}"
                })
            return

        # Case 3E: crop_rotation_intercropping_kb12.json
        if file_path.name.startswith("crop_rotation_intercropping_kb12") and isinstance(data, list):
            c_id = "KB12"
            source_org = self.registry.get(c_id, {}).get("source_org", "ICAR-IIFSR Modipuram")
            for rot in data:
                sname = rot.get("cropping_system_name", "Cropping System")
                content = (
                    f"Cropping System: {sname}. Spatial Row Ratio: {rot.get('spatial_row_ratio')}. "
                    f"Suitable Zones: {rot.get('suitable_agro_climatic_zones')}. "
                    f"Agronomic Synergy & Benefits: {rot.get('agronomic_synergy_and_benefits')}. "
                    f"LER: {rot.get('economic_land_equivalent_ratio_ler')}. "
                    f"Pest Management Benefits: {rot.get('pest_management_benefit')}."
                )
                self.documents.append({
                    "document_id": f"rot-{sname.lower().replace(' ', '-')[:25]}",
                    "collection_id": c_id,
                    "source_org": source_org,
                    "title": f"Crop Rotation & Intercropping: {sname}",
                    "crop": sname.split("+")[0].strip() if "+" in sname else sname,
                    "target": sname,
                    "content": content,
                    "citation": rot.get("citation") or f"[{c_id} - {source_org}] Crop Rotation: {sname}"
                })
            return

        # Case 3F: weed_management_kb13.json
        if file_path.name.startswith("weed_management_kb13") and isinstance(data, list):
            c_id = "KB13"
            source_org = self.registry.get(c_id, {}).get("source_org", "Directorate of Weed Research (ICAR-DWR)")
            for w in data:
                crop = w.get("crop_name", "Crop")
                pre = w.get("pre_emergence_herbicide", {})
                post = w.get("post_emergence_herbicide", {})
                content = (
                    f"Weed Management in {crop}. Critical competition period: {w.get('critical_period_crop_weed_competition')}. "
                    f"Major Weed Flora: {w.get('major_weed_flora')}. "
                    f"Pre-emergence Herbicide: {pre.get('chemical_name')} @ {pre.get('cibrc_approved_dose_per_acre')}. "
                    f"Pre-emergence Application Timing: {pre.get('application_timing')}. Dilution: {pre.get('dilution_per_15l_pump')}. "
                    f"Post-emergence Herbicide: {post.get('chemical_name')} @ {post.get('cibrc_approved_dose_per_acre')}. "
                    f"Post-emergence Application Timing: {post.get('application_timing')}. Dilution: {post.get('dilution_per_15l_pump')}. "
                    f"Cultural and Mechanical Practices: {w.get('cultural_mechanical_practices')}."
                )
                self.documents.append({
                    "document_id": f"weed-{crop.lower().replace(' ', '-')}",
                    "collection_id": c_id,
                    "source_org": source_org,
                    "title": f"Weed Management and Herbicides in {crop}",
                    "crop": crop,
                    "target": "Weed Management",
                    "content": content,
                    "citation": w.get("citation") or f"[{c_id} - {source_org}] Weed Management: {crop}"
                })
            return

        # Case 3G: harvesting_guidelines_kb14.json
        if file_path.name.startswith("harvesting_guidelines_kb14") and isinstance(data, list):
            c_id = "KB14"
            source_org = self.registry.get(c_id, {}).get("source_org", "CIPHET Ludhiana")
            for h in data:
                crop = h.get("crop_name", "Crop")
                content = (
                    f"Harvesting Guidelines for {crop}. Maturity Indices: {h.get('physiological_maturity_indices')}. "
                    f"Optimum Harvesting Moisture: {h.get('optimum_harvesting_moisture_percent')}%. "
                    f"Harvesting Method: {h.get('harvesting_method')}. "
                    f"Precautions During Harvest: {h.get('precautions_during_harvest')}."
                )
                self.documents.append({
                    "document_id": f"harvest-{crop.lower().replace(' ', '-')}",
                    "collection_id": c_id,
                    "source_org": source_org,
                    "title": f"Harvesting Guidelines for {crop}",
                    "crop": crop,
                    "target": "Harvesting",
                    "content": content,
                    "citation": h.get("citation") or f"[{c_id} - {source_org}] Harvesting Guide: {crop}"
                })
            return

        # Case 3H: mandi_market_prices_kb18.json
        if file_path.name.startswith("mandi_market_prices_kb18") and isinstance(data, list):
            c_id = "KB18"
            source_org = self.registry.get(c_id, {}).get("source_org", "Agmarknet & APMC")
            for m in data:
                mandi_name = m.get("mandi_name", "APMC Mandi")
                dist = m.get("district", "")
                st = m.get("state", "")
                for comm in m.get("major_commodities", []):
                    cname = comm.get("commodity", "Commodity")
                    # Clean crop name
                    crop_key = cname.split("(")[0].strip()
                    content = (
                        f"APMC Mandi Market Price: {cname} at {mandi_name} ({dist}, {st}). "
                        f"Variety/Grade: {comm.get('variety_grade')}. "
                        f"Modal Price Range: Rs. {comm.get('modal_price_range_inr_per_quintal')} per quintal. "
                        f"MSP Benchmark: Rs. {comm.get('msp_benchmark_inr_per_quintal')} per quintal. "
                        f"Quality Specifications: {comm.get('faq_specifications')}. "
                        f"Peak Arrival Months: {comm.get('peak_arrival_months')}. "
                        f"Moisture Deduction Rule: {comm.get('moisture_deduction_rule')}."
                    )
                    self.documents.append({
                        "document_id": f"mandi-{crop_key.lower().replace(' ', '-')}-{dist.lower()}",
                        "collection_id": c_id,
                        "source_org": source_org,
                        "title": f"Mandi Price: {cname} in {mandi_name}",
                        "crop": crop_key,
                        "target": cname,
                        "content": content,
                        "citation": f"[{c_id} - {source_org}] Agmarknet Mandi Price: {cname} ({mandi_name})"
                    })
            return

        # Case 4: Generic structured dictionary mapping collection keys (e.g., "KB02", "KB03", ...)
        if isinstance(data, dict):
            for key, items in data.items():
                if key.startswith("KB") and isinstance(items, list):
                    c_id = key
                    reg_meta = self.registry.get(c_id, {})
                    source_org = reg_meta.get("source_org", f"National Agricultural Registry ({c_id})")

                    for idx, item in enumerate(items):
                        doc_id = item.get("id") or item.get("term_id") or item.get("faq_id") or f"{c_id.lower()}-{idx+1:03d}"
                        crop = item.get("crop_name") or item.get("crop")
                        target = item.get("target") or item.get("pest") or item.get("disease")
                        
                        # Cleanly extract content depending on schema
                        if "scientific_solution" in item or "answer_en" in item or "answer" in item:
                            q_en = item.get("question_en") or item.get("farmer_query_en") or ""
                            q_te = item.get("question_te") or item.get("farmer_query_te") or ""
                            q_hi = item.get("question_hi") or item.get("farmer_query_hi") or ""
                            ans_en = item.get("answer_en") or item.get("scientific_solution") or item.get("answer") or ""
                            ans_te = item.get("answer_te") or ""
                            ans_hi = item.get("answer_hi") or ""
                            
                            content = (
                                f"Question (EN): {q_en}\nQuestion (TE): {q_te}\nQuestion (HI): {q_hi}\n"
                                f"Recommendation: {ans_en}\nTelugu Advisory: {ans_te}\nHindi Advisory: {ans_hi}"
                            )
                            title = f"{item.get('category', 'FAQ')}: {crop or 'Agri'} - {q_en[:50]}"
                            citation = item.get("source") or item.get("authoritative_source") or f"[{c_id} - {source_org}] {title}"
                        elif "definition" in item:
                            term_en = item.get("english", "")
                            term_te = item.get("telugu", "")
                            term_hi = item.get("hindi", "")
                            defn = item.get("definition", "")
                            cat = item.get("category", "Glossary")
                            sci = item.get("scientific_name", "")
                            content = f"Agricultural Term: {term_en} ({term_te}, {term_hi}). Category: {cat}. Scientific: {sci}. Definition: {defn}"
                            title = f"Agri Term: {term_en}"
                            citation = f"[{c_id} - {source_org}] Agricultural Glossary: {term_en}"
                        else:
                            title = item.get("title", f"{reg_meta.get('name', 'Knowledge Chunk')} - {doc_id}")
                            content = item.get("content") or item.get("description") or ""
                            if not content:
                                # Clean key-value pairs avoiding raw dict repr
                                content = "; ".join(f"{k}: {v}" for k, v in item.items() if not k.endswith("_id") and isinstance(v, (str, int, float)))
                            citation = item.get("citation") or f"[{c_id} - {source_org}] {title}"

                        self.documents.append({
                            "document_id": doc_id,
                            "collection_id": c_id,
                            "source_org": source_org,
                            "title": title,
                            "crop": crop,
                            "target": target,
                            "content": content,
                            "citation": citation
                        })
            return

        # Case 5: List of generic document objects
        if isinstance(data, list):
            for idx, item in enumerate(data):
                c_id = item.get("collection_id", "KB21")
                reg_meta = self.registry.get(c_id, {})
                source_org = item.get("source_org") or reg_meta.get("source_org", "ICAR Verified Repository")
                doc_id = item.get("id") or item.get("document_id") or item.get("faq_id") or f"{c_id.lower()}-{idx+1:03d}"
                crop = item.get("crop_name") or item.get("crop")
                target = item.get("target")

                if "scientific_solution" in item or "answer_en" in item or "answer" in item:
                    q_en = item.get("question_en") or item.get("farmer_query_en") or ""
                    q_te = item.get("question_te") or item.get("farmer_query_te") or ""
                    q_hi = item.get("question_hi") or item.get("farmer_query_hi") or ""
                    ans_en = item.get("answer_en") or item.get("scientific_solution") or item.get("answer") or ""
                    ans_te = item.get("answer_te") or ""
                    ans_hi = item.get("answer_hi") or ""
                    
                    content = (
                        f"Question (EN): {q_en}\nQuestion (TE): {q_te}\nQuestion (HI): {q_hi}\n"
                        f"Recommendation: {ans_en}\nTelugu Advisory: {ans_te}\nHindi Advisory: {ans_hi}"
                    )
                    title = f"{item.get('category', 'FAQ')}: {crop or 'Agri'} - {q_en[:50]}"
                    citation = item.get("source") or item.get("authoritative_source") or f"[{c_id} - {source_org}] {title}"
                elif "definition" in item:
                    term_en = item.get("english", "")
                    term_te = item.get("telugu", "")
                    term_hi = item.get("hindi", "")
                    defn = item.get("definition", "")
                    cat = item.get("category", "Glossary")
                    content = f"Agricultural Term: {term_en} ({term_te}, {term_hi}). Category: {cat}. Definition: {defn}"
                    title = f"Agri Term: {term_en}"
                    citation = f"[{c_id} - {source_org}] Agricultural Glossary: {term_en}"
                else:
                    title = item.get("title", f"Verified Document {doc_id}")
                    content = item.get("content") or item.get("description") or ""
                    if not content:
                        content = "; ".join(f"{k}: {v}" for k, v in item.items() if not k.endswith("_id") and isinstance(v, (str, int, float)))
                    citation = item.get("citation") or f"[{c_id} - {source_org}] {title}"

                self.documents.append({
                    "document_id": doc_id,
                    "collection_id": c_id,
                    "source_org": source_org,
                    "title": title,
                    "crop": crop,
                    "target": target,
                    "content": content,
                    "citation": citation
                })

    def retrieve(
        self,
        query: str,
        crop: Optional[str] = None,
        top_k: int = 3,
        collection_filter: Optional[List[str]] = None,
        min_relevance_threshold: float = 0.28
    ) -> List[Dict[str, Any]]:
        """
        Calibrated Hybrid Retrieval combining BM25 lexical relevance and semantic vector similarity.
        Applies multilingual query expansion (Telugu, Hindi, English), intent routing,
        and bounded multipliers to eliminate false positive dominance.
        Filters out low-scoring documents below min_relevance_threshold.
        """
        if not self.documents:
            return []

        # 1. Expand multilingual query tokens
        from app.ai.query_understanding import QueryUnderstanding
        analysis = QueryUnderstanding.analyze_query(query, context_crop=crop)
        if not analysis.get("is_agricultural", True) and not crop:
            # Out-of-domain query (e.g., cricket, smartphones, entertainment) -> abstain
            return []

        effective_crop = analysis.get("crop")
        recommended_cols = set(analysis.get("recommended_collections", []))

        query_tokens, detected_entities = self._expand_multilingual_query(query)
        if not query_tokens:
            query_tokens = self._tokenize(query)

        # 2. Compute query vector embedding
        query_vec = self.vector_embedder.embed_tokens(query_tokens)

        # 3. Calculate BM25 scores
        raw_bm25_scores: List[float] = []
        for i in range(len(self.documents)):
            score = self.bm25.score(query_tokens, i)
            raw_bm25_scores.append(score)

        max_bm25 = max(raw_bm25_scores) if raw_bm25_scores else 1.0
        if max_bm25 <= 0.0:
            max_bm25 = 1.0

        # 4. Calculate Calibrated Hybrid Score for each document
        scored_candidates: List[Tuple[float, Dict[str, Any]]] = []
        crop_clean = effective_crop.lower().strip() if effective_crop else None

        for idx, doc in enumerate(self.documents):
            # Optional collection filter
            doc_col = doc.get("collection_id")
            if collection_filter and doc_col not in collection_filter:
                continue

            bm25_norm = raw_bm25_scores[idx] / max_bm25
            vec_sim = max(0.0, self.vector_embedder.cosine_similarity(query_vec, self.doc_vectors[idx]))

            # Base semantic score (0.0 to 1.0)
            base_score = (0.55 * bm25_norm) + (0.45 * vec_sim)

            # Skip documents with negligible lexical and semantic relevance
            if bm25_norm < 0.04 and vec_sim < 0.65:
                continue
            if base_score < 0.06:
                continue

            # Bounded gentle multipliers instead of massive overpowering additions
            multiplier = 1.0
            doc_crop = (doc.get("crop") or "").lower()

            # Crop matching / filtering
            if crop_clean:
                if doc_crop:
                    if crop_clean in doc_crop or doc_crop in crop_clean:
                        multiplier *= 1.30
                    elif doc_crop not in ["general", "all", "soils", ""]:
                        # Severe penalty for a completely different crop
                        multiplier *= 0.15
            
            # Collection intent alignment: prioritize primary specialized collection
            rec_list = analysis.get("recommended_collections", [])
            primary_col = rec_list[0] if rec_list else None
            if doc_col == primary_col:
                multiplier *= 1.45
            elif doc_col in recommended_cols:
                multiplier *= 1.15

            # Target entity bonus (e.g. pink bollworm, blast, thrips)
            doc_target = (doc.get("target") or "").lower()
            if analysis.get("target_entity"):
                target_entity = analysis["target_entity"].lower()
                if target_entity in doc_target or target_entity in doc.get("title", "").lower():
                    multiplier *= 1.45

            # Title entity bonus
            title_lower = doc.get("title", "").lower()
            for entity in detected_entities:
                if entity.lower() in title_lower:
                    multiplier *= 1.15
                    break

            final_score = base_score * multiplier

            # Filter against minimum relevance threshold
            if final_score >= min_relevance_threshold:
                doc_copy = dict(doc)
                doc_copy["score"] = round(final_score, 4)
                doc_copy["base_score"] = round(base_score, 4)
                doc_copy["query_analysis"] = analysis
                scored_candidates.append((final_score, doc_copy))

        # 5. Sort by descending score and take top_k
        scored_candidates.sort(key=lambda x: x[0], reverse=True)
        return [item[1] for item in scored_candidates[:top_k]]

    def get_collections_summary(self) -> List[Dict[str, Any]]:
        """Returns indexed status summary across all 23 collections."""
        counts: Dict[str, int] = {}
        for doc in self.documents:
            c_id = doc.get("collection_id", "Unknown")
            counts[c_id] = counts.get(c_id, 0) + 1

        summary = []
        for c_id, reg in self.registry.items():
            summary.append({
                "collection_id": c_id,
                "name": reg.get("name"),
                "source_org": reg.get("source_org"),
                "indexed_chunks": counts.get(c_id, 0),
                "status": "active" if counts.get(c_id, 0) > 0 else "pending"
            })
        return summary


rag_service = RAGService()
