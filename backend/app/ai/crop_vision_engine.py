import os
import io
import math
import uuid
from pathlib import Path
from typing import Dict, Any, List, Optional, Tuple
from pydantic import BaseModel
from PIL import Image
import numpy as np

try:
    import cv2
except ImportError:
    cv2 = None

from app.core.logging import logger
from app.safety.safety_gate import safety_gate
from app.ai.rag_service import rag_service
from app.ai.gemini_service import gemini_service

class QualityReport(BaseModel):
    is_acceptable: bool
    blur_score: float
    is_blurry: bool
    brightness: float
    is_underexposed: bool
    is_overexposed: bool
    foliage_ratio: float
    is_non_plant: bool
    error_code: Optional[str] = None
    farmer_guidance_te: Optional[str] = None
    farmer_guidance_hi: Optional[str] = None
    farmer_guidance_en: Optional[str] = None

class DifferentialCandidate(BaseModel):
    crop: str
    disease_name: str
    pathology_type: str  # Fungal, Bacterial, Viral, Pest Damage, Nutrient Deficiency, Abiotic
    pathogen: Optional[str] = None
    probability: float
    distinguishing_features: str

class DiagnosisResult(BaseModel):
    status: str  # "diagnosed", "differential_ambiguity", "quality_rejected", "escalated", "vision_unavailable", "non_plant_rejected", "healthy_crop"
    is_escalated: bool
    confidence: float
    entropy: float
    primary_diagnosis: Optional[str] = None
    crop_name: str
    affected_part: str
    growth_stage: str = "standing_crop"  # "standing_crop", "vegetative", "flowering", "fruiting", "pre_sowing"
    management_stage: str = "active_disease"  # "active_disease", "preventive", "pre_sowing"
    pathology_type: Optional[str] = None
    pathogen: Optional[str] = None
    visible_symptoms: Optional[str] = None
    differential_candidates: List[DifferentialCandidate] = []
    follow_up_questions: List[str] = []
    standing_crop_remedies: List[str] = []  # Strictly active foliar sprays & interventions
    cultural_sanitation: List[str] = []     # Canopy pruning, aeration, debris removal
    preventive_measures: List[str] = []     # Pre-sowing, crop rotation, solarization for next season
    pre_sowing_remedies: List[str] = []     # Dedicated seed treatment / rhizome dip / solarization
    unsupported_cure_warning: Optional[str] = None
    cultural_remedies: List[str] = []       # Backwards-compatible; strictly active standing remedies
    biological_remedies: List[str] = []     # Backwards-compatible; strictly active standing remedies
    chemical_management: Optional[Dict[str, Any]] = None
    chemical_safety_warning: Optional[str] = None
    citations: List[Dict[str, str]] = []
    image_url: str
    ticket_id: Optional[str] = None
    ticket_number: Optional[str] = None
    farmer_explanation: str

class ImageQualityGate:
    """
    Pre-diagnostic quality filter:
    Detects blur, underexposure, overexposure, and non-plant out-of-distribution photos
    before wasting compute or generating misleading diagnostic claims.
    """
    BLUR_THRESHOLD = 18.0
    MIN_BRIGHTNESS = 36.0
    MAX_BRIGHTNESS = 228.0
    MIN_FOLIAGE_RATIO = 0.08

    @classmethod
    def evaluate(cls, img_bytes: bytes) -> QualityReport:
        try:
            pil_img = Image.open(io.BytesIO(img_bytes)).convert("RGB")
            np_img = np.array(pil_img)
        except Exception as e:
            return QualityReport(
                is_acceptable=False,
                blur_score=0.0,
                is_blurry=True,
                brightness=0.0,
                is_underexposed=True,
                is_overexposed=False,
                foliage_ratio=0.0,
                is_non_plant=True,
                error_code="INVALID_IMAGE_STREAM",
                farmer_guidance_te="చిత్రం పాడైంది లేదా చెల్లదు. దయచేసి స్పష్టమైన పంట ఫోటో తీయండి.",
                farmer_guidance_hi="छवि दूषित या अमान्य है। कृपया स्पष्ट फसल की तस्वीर लें।",
                farmer_guidance_en="Invalid or corrupt image format. Please capture a clear crop leaf photo."
            )

        h, w = np_img.shape[:2]
        if h < 140 or w < 140:
            return QualityReport(
                is_acceptable=False,
                blur_score=0.0,
                is_blurry=False,
                brightness=128.0,
                is_underexposed=False,
                is_overexposed=False,
                foliage_ratio=0.5,
                is_non_plant=False,
                error_code="RESOLUTION_TOO_LOW",
                farmer_guidance_te="ఫోటో పరిమాణం చాలా చిన్నదిగా ఉంది. దయచేసి దగ్గరగా నాణ్యమైన ఫోటో తీయండి.",
                farmer_guidance_hi="तस्वीर बहुत छोटी है। कृपया पास से स्पष्ट तस्वीर लें।",
                farmer_guidance_en="Image resolution too low. Please hold camera closer and retake."
            )

        # 1. Grayscale & Brightness
        gray = cv2.cvtColor(np_img, cv2.COLOR_RGB2GRAY) if cv2 else np.mean(np_img, axis=2).astype(np.uint8)
        brightness = float(np.mean(gray))

        # 2. Blur / Sharpness via Laplacian Variance
        if cv2:
            laplacian = cv2.Laplacian(gray, cv2.CV_64F)
            blur_score = float(laplacian.var())
        else:
            # Fallback simple gradient variance
            dy, dx = np.gradient(gray.astype(float))
            blur_score = float(np.var(dx) + np.var(dy))

        # 3. Non-Plant Out-of-Distribution Check (Foliage Coverage Index)
        # Foliage in HSV: Hue 25°-90°, Saturation > 25, Value > 25
        if cv2:
            hsv = cv2.cvtColor(np_img, cv2.COLOR_RGB2HSV)
            lower_green = np.array([25, 25, 25])
            upper_green = np.array([90, 255, 255])
            mask_green = cv2.inRange(hsv, lower_green, upper_green)
            green_pixels = np.count_nonzero(mask_green)
            foliage_ratio = float(green_pixels / (h * w))
        else:
            # Fallback green excess index: 2G - R - B
            r, g, b = np_img[:,:,0].astype(float), np_img[:,:,1].astype(float), np_img[:,:,2].astype(float)
            exg = 2 * g - r - b
            green_pixels = np.count_nonzero(exg > 15)
            foliage_ratio = float(green_pixels / (h * w))

        is_blurry = blur_score < cls.BLUR_THRESHOLD
        is_underexposed = brightness < cls.MIN_BRIGHTNESS
        is_overexposed = brightness > cls.MAX_BRIGHTNESS
        is_non_plant = foliage_ratio < cls.MIN_FOLIAGE_RATIO

        # Rejection determination
        is_acceptable = True
        err_code = None
        guide_te, guide_hi, guide_en = None, None, None

        if is_non_plant:
            is_acceptable = False
            err_code = "NON_PLANT_IMAGE"
            guide_te = "పంట ఆకు లేదా మొక్క భాగాలు స్పష్టంగా గుర్తించబడలేదు. దయచేసి వ్యాధి సోకిన ఆకును దగ్గరగా ఫోటో తీయండి."
            guide_hi = "फसल की पत्ती या पौधे का भाग स्पष्ट नहीं है। कृपया प्रभावित पत्ती की पास से तस्वीर लें।"
            guide_en = "No plant foliage detected. Please photograph an affected crop leaf or plant part directly."
        elif is_blurry:
            is_acceptable = False
            err_code = "IMAGE_BLURRY"
            guide_te = "ఫోటో అస్పష్టంగా (Blurry) ఉంది. దయచేసి కెమెరాను కదలకుండా నిశ్చలంగా ఉంచి ఆకుపై ఫోకస్ చేసి మళ్లీ తీయండి."
            guide_hi = "फोटो धुंधली (Blurry) है। कृपया कैमरे को स्थिर रखें और पत्ती पर फोकस करके दोबारा तस्वीर लें।"
            guide_en = "Image is blurry or out of focus. Please hold the phone steady and tap the leaf to refocus."
        elif is_underexposed:
            is_acceptable = False
            err_code = "UNDEREXPOSED_DARK"
            guide_te = "వెలుతురు చాలా తక్కువగా ఉంది. తగినంత సూర్యకాంతిలో లేదా ఫ్లాష్ ఆన్ చేసి మళ్లీ ఫోటో తీయండి."
            guide_hi = "प्रकाश बहुत कम है। कृपया पर्याप्त रोशनी में या फ्लैश चालू करके दोबारा तस्वीर लें।"
            guide_en = "Lighting is too dark. Please capture the leaf in adequate daylight or turn on flash."
        elif is_overexposed:
            is_acceptable = False
            err_code = "OVEREXPOSED_BRIGHT"
            guide_te = "కాంతి అతిగా ఉండి ఆకు వివరాలు కనిపించడం లేదు. ప్రత్యక్ష ఎండ ప్రతిబింబం లేకుండా నీడలో ఫోటో తీయండి."
            guide_hi = "अत्यधिक तेज रोशनी के कारण लक्षण स्पष्ट नहीं हैं। छाया में स्पष्ट फोटो लें।"
            guide_en = "Image is washed out due to excessive glare. Please shade the leaf and capture clearly."

        return QualityReport(
            is_acceptable=is_acceptable,
            blur_score=round(blur_score, 1),
            is_blurry=is_blurry,
            brightness=round(brightness, 1),
            is_underexposed=is_underexposed,
            is_overexposed=is_overexposed,
            foliage_ratio=round(foliage_ratio, 3),
            is_non_plant=is_non_plant,
            error_code=err_code,
            farmer_guidance_te=guide_te,
            farmer_guidance_hi=guide_hi,
            farmer_guidance_en=guide_en
        )


def is_pre_sowing_practice(text: str) -> bool:
    """
    Identifies whether an agricultural recommendation is strictly a pre-sowing / seed treatment practice
    (seed treatment, seedling root dip, rhizome dip, nursery solarization, acid delinting, hot water seed soaking).
    Such practices must NEVER be recommended as the immediate action for an active standing crop with foliar symptoms.
    """
    if not text:
        return False
    t = text.lower()
    keywords = [
        "seed treatment", "seedling root dip", "seedling dip", "rhizome dip",
        "acid delinting", "delinting", "hot water seed", "hot water treatment",
        "10g/kg seed", "2g/kg seed", "5g/kg seed", "g/kg seed", "ml/kg seed",
        "/kg seed", "per kg seed", "kg seed",
        "solarize nursery", "nursery soil solarization", "solarization before",
        "before sowing", "prior to sowing", "before planting", "prior to transplanting",
        "విత్తన శుద్ధి", "విత్తనశుద్ధి", "నారుమడి", "గ్రా/కిలో విత్తనానికి", "కిలో విత్తనానికి",
        "బీజ్ ఉపచార్", "बीज उपचार", "बुवाई से पहले", "बीज शोधन"
    ]
    return any(k in t for k in keywords)

class CropVisionEngine:
    """
    Production Computer Vision Diagnostic & Differential Engine:
    1. Evaluates pre-diagnostic quality gates (blur, exposure, OOD).
    2. Extracts multiscale visual features (color histograms, texture gradients, lesion geometry).
    3. Performs calibrated temperature-scaled classification across 42 pathology classes.
    4. Evaluates symptom differential diagnosis against farmer growth stage and agronomic history.
    5. Grounds recommendations in verified ICAR/PJTSAU knowledge chunks and statutory CIBRC labels.
    """

    CONFIDENCE_THRESHOLD_HIGH = 0.78
    CONFIDENCE_THRESHOLD_MED = 0.55
    TEMPERATURE = 1.35  # Calibrated scaling parameter

    PATHOLOGY_DATABASE = {
        "Rice / Paddy": [
            {
                "id": "rice_blast",
                "name": "Rice Blast (Leaf & Neck Blast)",
                "type": "Fungal",
                "pathogen": "Magnaporthe oryzae",
                "symptoms": "Spindle-shaped / diamond-shaped lesions with dark brown margins and greyish-white centers on leaves. Blackening and breaking of panicle neck node.",
                "distinguishing": "Spindle shape with grey centers distinguishes from brown spot; lack of bacterial ooze separates from BLB.",
                "standing_crop_remedies": [
                    "Foliar spray of Pseudomonas fluorescens @ 5g/L or Bacillus subtilis @ 5g/L at boot leaf stage or early lesion appearance.",
                    "Foliar spray of Tricyclazole 75% WP @ 0.6 g/L or Kasugamycin 3% SL @ 2 ml/L on standing crop canopy."
                ],
                "cultural_sanitation": [
                    "Avoid excessive split doses of Nitrogen; apply N in 3 splits (basal, tillering, panicle initiation).",
                    "Maintain alternate wetting and drying (AWD) rather than continuous stagnant flooding.",
                    "Destroy infected stubbles and remove grassy weed hosts (Echinochloa) from bunds."
                ],
                "preventive_measures": [
                    "Crop rotation with non-host crops and avoid susceptible varieties in endemic tracts.",
                    "Seed treatment with Pseudomonas fluorescens @ 10g/kg seed or Carbendazim 50% WP @ 2g/kg seed before nursery sowing next season."
                ],
                "pre_sowing": [
                    "Seed treatment with Pseudomonas fluorescens @ 10g/kg seed or Carbendazim 50% WP @ 2g/kg seed before nursery sowing next season."
                ],
                "cultural": [
                    "Avoid excessive split doses of Nitrogen; apply N in 3 splits (basal, tillering, panicle initiation).",
                    "Maintain alternate wetting and drying (AWD) rather than continuous stagnant flooding."
                ],
                "biological": [
                    "Foliar spray of Pseudomonas fluorescens @ 5g/L or Bacillus subtilis @ 5g/L at boot leaf stage."
                ],
                "cibrc_target": "Rice Blast",
                "citation": "ICAR-NRRI Cuttack & PJTSAU Rice Advisory Bulletin 2025"
            },
            {
                "id": "sheath_blight",
                "name": "Sheath Blight",
                "type": "Fungal",
                "pathogen": "Rhizoctonia solani",
                "symptoms": "Oval or elliptical greenish-grey water-soaked spots on leaf sheaths near waterline with irregular snake-skin pattern bordered by dark brown bands.",
                "distinguishing": "Snake-skin lesion pattern starting near waterline spreading upward.",
                "standing_crop_remedies": [
                    "Foliar application of Trichoderma viride @ 5g/L directed towards lower leaf sheaths and stem base.",
                    "Foliar spray of Hexaconazole 5% SC @ 2 ml/L or Validamycin 3% L @ 2.5 ml/L on lower canopy."
                ],
                "cultural_sanitation": [
                    "Provide 30 cm alleyways every 2-3 meters in transplanted paddy fields to improve airflow.",
                    "Avoid high plant density and drain stagnant water during early infection to dry lower sheath canopy."
                ],
                "preventive_measures": [
                    "Field sanitation and burning of infected stubbles post-harvest.",
                    "Seed treatment with Trichoderma viride @ 10g/kg seed before nursery sowing next season."
                ],
                "pre_sowing": [
                    "Seed treatment with Trichoderma viride @ 10g/kg seed or Pseudomonas fluorescens @ 10g/kg seed before nursery sowing next season."
                ],
                "cultural": [
                    "Provide 30 cm alleyways every 2-3 meters in transplanted paddy fields to improve airflow.",
                    "Drain field water during early infection to dry lower sheath canopy."
                ],
                "biological": [
                    "Foliar application of Trichoderma viride @ 5g/L directed on lower leaf sheaths."
                ],
                "cibrc_target": "Sheath Blight",
                "citation": "ICAR-NRRI Cuttack & PJTSAU Sheath Blight Management Bulletin"
            },
            {
                "id": "bacterial_leaf_blight",
                "name": "Bacterial Leaf Blight (BLB)",
                "type": "Bacterial",
                "pathogen": "Xanthomonas oryzae pv. oryzae",
                "symptoms": "Water-soaked streaks starting at leaf tips and margins, expanding into wavy yellowish-white lesions that turn greyish-white with bacterial ooze in early morning.",
                "distinguishing": "Wavy margins from leaf tip downward with milky bacterial ooze droplets in morning dew.",
                "standing_crop_remedies": [
                    "Foliar spray of fresh cow dung extract supernatant (20%) or Pseudomonas fluorescens @ 5g/L on foliage.",
                    "Foliar spray of Copper Oxychloride 50% WP @ 2.5g/L + Streptocycline @ 0.1g/L (1g in 10L water)."
                ],
                "cultural_sanitation": [
                    "Withhold top dressing of nitrogenous fertilizers upon first symptom appearance.",
                    "Drain stagnant water from infected plots; do not channel drain water into healthy fields."
                ],
                "preventive_measures": [
                    "Grow BLB-resistant varieties (e.g. Improved Samba Mahsuri - RP Bio-226).",
                    "Hot water seed treatment at 52-54°C for 10 minutes or soaking seed in Streptocycline (0.1g/L) for 8 hours prior to nursery sowing next season."
                ],
                "pre_sowing": [
                    "Hot water seed treatment at 52-54°C for 10 minutes or soaking seed in Streptocycline solution (0.1g/L) for 8 hours prior to nursery sowing next season."
                ],
                "cultural": [
                    "Withhold top dressing of nitrogenous fertilizers upon first symptom appearance.",
                    "Drain stagnant water from infected plots; do not channel drain water into healthy fields."
                ],
                "biological": [
                    "Foliar spray of fresh cow dung extract supernatant (20%) or Pseudomonas fluorescens @ 5g/L."
                ],
                "cibrc_target": "Bacterial Leaf Blight",
                "citation": "ICAR-IIRR Hyderabad & PJTSAU Rice Advisory 2025"
            },
            {
                "id": "bph_hopper_burn",
                "name": "Brown Plant Hopper (BPH) / Hopper Burn",
                "type": "Pest Damage",
                "pathogen": "Nilaparvata lugens",
                "symptoms": "Circular patches of drying, yellowing plants that turn brown and collapse (hopper burn) starting in circular pockets of dense field areas.",
                "distinguishing": "Circular lodging patches with numerous brown hopping insects at stem base.",
                "standing_crop_remedies": [
                    "Foliar spray of Neem oil 1500 ppm @ 5 ml/L directed towards stem base where hoppers congregate.",
                    "Foliar spray of Triflumezopyrim 10% SC @ 0.5 ml/L or Pymetrozine 50% WG @ 0.6 g/L."
                ],
                "cultural_sanitation": [
                    "Drain water from field for 3-4 days to expose hopper nymphs at stem base to sunlight.",
                    "Form alleyways (30 cm every 2 meters) to break humid microclimate."
                ],
                "preventive_measures": [
                    "Avoid excessive use of synthetic pyrethroids which induce BPH resurgence.",
                    "Maintain balanced N application (avoid excess urea) and adopt recommended plant spacing."
                ],
                "pre_sowing": [
                    "Select BPH-tolerant varieties and maintain nursery hygiene."
                ],
                "cultural": [
                    "Drain water from field for 3-4 days to expose hopper nymphs at stem base to sunlight.",
                    "Form alleyways (30 cm every 2 meters) to break humid microclimate."
                ],
                "biological": [
                    "Preserve mirid bug predators (Cyrtorhinus lividipennis) and spiders.",
                    "Foliar spray of Neem oil 1500 ppm @ 5 ml/L directed at stem base."
                ],
                "cibrc_target": "Brown Plant Hopper",
                "citation": "ICAR-IIRR Hyderabad BPH Management Protocol"
            }
        ],
        "Cotton": [
            {
                "id": "cotton_bacterial_blight",
                "name": "Bacterial Blight / Angular Leaf Spot",
                "type": "Bacterial",
                "pathogen": "Xanthomonas citri pv. malvacearum",
                "symptoms": "Angular water-soaked spots bounded by veins on leaves turning dark brown to black. Progresses along veins to black arm on stems and oily sunken spots on bolls.",
                "distinguishing": "Strict angular geometry bounded by leaf veinlets; blackarm on branches.",
                "standing_crop_remedies": [
                    "Foliar spray of Copper Oxychloride 50% WP @ 2.5-3.0 g/L mixed with Streptocycline @ 0.1 g/L (1g in 10L water) on standing crop foliage.",
                    "Foliar spray of Bacillus subtilis @ 5g/L during early vegetative to squaring stage."
                ],
                "cultural_sanitation": [
                    "Prompt collection and burning of infected foliar residues and fallen twigs.",
                    "Avoid excessive split doses of urea; maintain balanced potassium nutrition.",
                    "Ensure proper row spacing (90x60 cm) for canopy aeration."
                ],
                "preventive_measures": [
                    "Practice crop rotation with non-host crops (pulses, cereals) for the next cropping season.",
                    "Deep summer ploughing to eradicate resting bacteria in infected debris."
                ],
                "pre_sowing": [
                    "Acid delinting of cotton seed with concentrated Sulphuric acid (100 ml/kg seed) followed by seed treatment with Pseudomonas fluorescens @ 10g/kg or Carboxin + Thiram @ 2g/kg seed before sowing next season."
                ],
                "cultural": [
                    "Prompt destruction and burning of infected crop residues.",
                    "Avoid excessive split doses of urea; maintain balanced potassium nutrition."
                ],
                "biological": [
                    "Foliar spray of Bacillus subtilis @ 5g/L during early vegetative stages."
                ],
                "cibrc_target": "Bacterial Blight",
                "citation": "ICAR-CICR Nagpur & PJTSAU Cotton Pathology Advisory 2025"
            },
            {
                "id": "cotton_grey_mildew",
                "name": "Grey Mildew (Dahiya Disease)",
                "type": "Fungal",
                "pathogen": "Ramularia areola",
                "symptoms": "Angular pale translucent spots on upper leaf surface with frosty white powdery fungal growth on the underside. Causes premature defoliation from bottom upward.",
                "distinguishing": "Frosty white powdery growth on lower leaf surface restricted by veinlets.",
                "standing_crop_remedies": [
                    "Foliar spray of Wettable Sulphur 80% WP @ 3g/L or Carbendazim 50% WP @ 1g/L on leaf undersides.",
                    "Foliar spray of bio-agent Ampelomyces quisqualis @ 5g/L."
                ],
                "cultural_sanitation": [
                    "Ensure wider row spacing (90x60 cm or 120x45 cm) for good aeration.",
                    "Remove lower senescent leaves after peak boll formation."
                ],
                "preventive_measures": [
                    "Deep summer ploughing and burning of infected crop debris before next season."
                ],
                "pre_sowing": [
                    "Seed treatment with Trichoderma viride @ 10g/kg seed before sowing next season."
                ],
                "cultural": [
                    "Ensure wider row spacing (90x60 cm or 120x45 cm) for good aeration.",
                    "Remove lower senescent leaves after peak boll formation."
                ],
                "biological": [
                    "Foliar spray of Ampelomyces quisqualis or Wettable Sulphur 80% WP @ 3g/L."
                ],
                "cibrc_target": "Grey Mildew",
                "citation": "ICAR-CICR Cotton Disease Management Protocol"
            },
            {
                "id": "pink_bollworm",
                "name": "Pink Bollworm Infestation",
                "type": "Pest Damage",
                "pathogen": "Pectinophora gossypiella",
                "symptoms": "Rosette flowers (petals twisted into rosette), premature opening of green bolls, stained lint, and pinhole larval entry points on boll rind.",
                "distinguishing": "Rosette flowers and internal locule burrowing with pink caterpillars.",
                "standing_crop_remedies": [
                    "Release egg parasitoid Trichogramma bactrae @ 60,000/acre at 7-day intervals on standing crop.",
                    "Foliar spray of Neem Seed Kernel Extract (NSKE 5%) or Neem oil 1500 ppm @ 5 ml/L.",
                    "Foliar spray of Emamectin Benzoate 5% SG @ 0.44 g/L when ETL (8 moths/trap/night) is crossed."
                ],
                "cultural_sanitation": [
                    "Install pheromone traps @ 5 per acre for monitoring; 15 per acre for mass trapping.",
                    "Strict termination of cotton crop by December-January to prevent offseason carryover.",
                    "Hand collection and destruction of rosette flowers during early squaring stage."
                ],
                "preventive_measures": [
                    "Strict adherence to sowing window (June 15 - July 15) and planting non-Bt refuge borders."
                ],
                "pre_sowing": [
                    "Seed treatment with approved bio-formulations before sowing next season."
                ],
                "cultural": [
                    "Install pheromone traps @ 5 per acre for monitoring; 15 per acre for mass trapping.",
                    "Hand collection and destruction of rosette flowers during early squaring stage."
                ],
                "biological": [
                    "Release egg parasitoid Trichogramma bactrae @ 60,000/acre at 7-day intervals.",
                    "Foliar spray of Neem Seed Kernel Extract (NSKE 5%) or Neem oil 1500 ppm @ 5 ml/L."
                ],
                "cibrc_target": "Pink Bollworm",
                "citation": "ICAR-CICR Nagpur Pink Bollworm Management Strategy"
            },
            {
                "id": "cotton_chlorosis",
                "name": "Magnesium / Zinc Deficiency & Leaf Reddening",
                "type": "Nutrient Deficiency",
                "pathogen": "Abiotic Nutrient Imbalance",
                "symptoms": "Interveinal yellowing (chlorosis) or purplish-red discoloration between veins while veins stay green. Leaves turn brittle and shed prematurely during boll bursting.",
                "distinguishing": "Symmetrical interveinal discoloration without necrotic fungal rings or bacterial watersoaking.",
                "standing_crop_remedies": [
                    "Foliar spray of Magnesium Sulphate (MgSO4) @ 10g/L + Urea @ 10g/L at 60 and 90 DAS.",
                    "Foliar spray of Chelated Zinc (Zn-EDTA 12%) @ 1.5g/L on foliage."
                ],
                "cultural_sanitation": [
                    "Maintain optimal soil moisture and avoid waterlogging in deep black soils."
                ],
                "preventive_measures": [
                    "Soil test-based balanced fertilization; apply ZnSO4 @ 10 kg/acre and Magnesium Sulphate @ 15 kg/acre."
                ],
                "pre_sowing": [
                    "Basal incorporation of FYM or compost @ 5 tons/acre before sowing next season."
                ],
                "cultural": [
                    "Maintain optimal soil moisture and avoid waterlogging in deep black soils."
                ],
                "biological": [
                    "Foliar spray of Magnesium Sulphate (MgSO4) @ 10g/L + Urea @ 10g/L at 60 and 90 DAS.",
                    "Foliar spray of Chelated Zinc (Zn-EDTA 12%) @ 1.5g/L."
                ],
                "cibrc_target": "Nutrient Deficiency",
                "citation": "ANGRAU / PJTSAU Cotton Nutrient Management Bulletin"
            }
        ],
        "Chilli": [
            {
                "id": "chilli_spider_mite",
                "name": "Spider Mites / Yellow Mites Infestation",
                "type": "Acarine Infestation",
                "pathogen": "Polyphagotarsonemus latus / Tetranychus urticae",
                "symptoms": "Downwards curling of leaves (inverted boat shape), bronzing, stippling, and fine silky webbing on leaf undersides.",
                "distinguishing": "Downward curling with silky webbing distinguishes from upward thrips curling.",
                "standing_crop_remedies": [
                    "Foliar spray of Neem oil (3000 ppm) @ 3-5 ml/L with mild soap emulsifier on leaf undersides.",
                    "Foliar spray of Lecanicillium lecanii or Beauveria bassiana @ 5g/L in evening hours.",
                    "Foliar spray of Diafenthiuron 50% WP @ 1.25 g/L or Spiromesifen 22.9% SC @ 1 ml/L."
                ],
                "cultural_sanitation": [
                    "Maintain adequate soil moisture; dry dusty conditions accelerate spider mite reproduction.",
                    "Spray high-pressure water jets on leaf undersides to dislodge mites and destroy webs."
                ],
                "preventive_measures": [
                    "Plant 2-3 barrier rows of maize or sorghum around the field perimeter before transplanting."
                ],
                "pre_sowing": [
                    "Grow barrier crops around nursery beds prior to transplanting."
                ],
                "cultural": [
                    "Maintain adequate soil moisture; dry dusty conditions accelerate spider mite reproduction.",
                    "Spray high-pressure water jets on leaf undersides to dislodge mites and destroy webs."
                ],
                "biological": [
                    "Foliar spray of Neem oil (3000 ppm) @ 3-5 ml/L with mild soap emulsifier.",
                    "Foliar spray of Lecanicillium lecanii or Beauveria bassiana @ 5g/L on lower canopy."
                ],
                "cibrc_target": "Spider Mites",
                "citation": "ICAR-IIHR Bengaluru & ANGRAU Chilli IPM Guide"
            },
            {
                "id": "chilli_leaf_curl",
                "name": "Chilli Leaf Curl Virus (Murda Disease)",
                "type": "Viral",
                "pathogen": "Begomovirus (transmitted by Whitefly)",
                "symptoms": "Upward curling and puckering of leaves, shortened internodes, enations on leaf undersides, severely stunted bushy plant habit.",
                "distinguishing": "Upward cupping and puckered enations; insect vector whiteflies visible.",
                "standing_crop_remedies": [
                    "Foliar spray of Neem oil 1500 ppm @ 5 ml/L at 10-day intervals to deter whitefly vectors.",
                    "Foliar spray of Diafenthiuron 50% WP @ 1.25 g/L or Spiromesifen 22.9% SC @ 1 ml/L to suppress vector population."
                ],
                "cultural_sanitation": [
                    "Rogue out and deeply bury virus-infected plants within first 45 days after planting.",
                    "Install yellow sticky traps @ 15-20 per acre for whitefly monitoring."
                ],
                "preventive_measures": [
                    "Grow 3-4 border barrier rows of sorghum or maize 30 days prior to transplanting as vector barriers."
                ],
                "pre_sowing": [
                    "Seedling root dip in Pseudomonas fluorescens @ 10g/L for 30 minutes before transplanting next batch."
                ],
                "unsupported_cure_warning": "Viral infections cannot be cured once systemic within standing plant foliage. Do not apply curative biological or chemical sprays for viral remission. Focus immediately on roguing infected plants and managing whitefly vectors.",
                "cultural": [
                    "Rogue out and deeply bury virus-infected plants within first 45 days after planting.",
                    "Install yellow sticky traps @ 15-20 per acre for whitefly monitoring."
                ],
                "biological": [
                    "Foliar spray of Neem oil 1500 ppm @ 5 ml/L at 10-day intervals to suppress vector feeding."
                ],
                "cibrc_target": "Leaf Curl",
                "citation": "ICAR-IIHR Bengaluru & PJTSAU Chilli Virus Protocol 2025"
            },
            {
                "id": "chilli_black_thrips",
                "name": "Black Thrips (Thrips parvispinus) Damage",
                "type": "Pest Damage",
                "pathogen": "Thrips parvispinus",
                "symptoms": "Upward leaf curling, silvery-white scarring on foliage, dropping of flowers, and dark feeding streaks on fruit surface.",
                "distinguishing": "Silvery scarring with tiny black elongated thrips active inside flower blossoms.",
                "standing_crop_remedies": [
                    "Foliar spray of Beauveria bassiana or Lecanicillium lecanii @ 5g/L in evening hours.",
                    "Foliar spray of Pongamia oil or Neem oil 3000 ppm @ 3 ml/L on blooms and foliage.",
                    "Foliar spray of Spinetoram 11.7% SC @ 1 ml/L or Fipronil 5% SC @ 2 ml/L."
                ],
                "cultural_sanitation": [
                    "Install blue sticky traps @ 20 per acre for monitoring and mass trapping.",
                    "Frequent light irrigations via sprinkler to knock down thrips populations.",
                    "Avoid excessive application of synthetic pyrethroids."
                ],
                "preventive_measures": [
                    "Intercropping with maize or coriander to boost predatory anthocorid bugs."
                ],
                "pre_sowing": [
                    "Maintain clean nursery beds and install sticky traps before seedling emergence."
                ],
                "cultural": [
                    "Install blue sticky traps @ 20 per acre for monitoring and mass trapping.",
                    "Frequent light irrigations via sprinkler to knock down thrips populations."
                ],
                "biological": [
                    "Foliar spray of Beauveria bassiana or Lecanicillium lecanii @ 5g/L in evening hours.",
                    "Foliar spray of Pongamia oil or Neem oil 3000 ppm @ 3 ml/L."
                ],
                "cibrc_target": "Chilli Thrips",
                "citation": "ANGRAU Guntur Black Thrips Alert 2025"
            },
            {
                "id": "chilli_anthracnose",
                "name": "Anthracnose / Fruit Rot / Dieback",
                "type": "Fungal",
                "pathogen": "Colletotrichum capsici",
                "symptoms": "Circular to elliptical sunken necrotic spots on ripe red chillies with concentric rings of black acervuli. Twigs dry from top downward (dieback).",
                "distinguishing": "Sunken concentric black rings on ripe fruits and downward dying of twigs.",
                "standing_crop_remedies": [
                    "Foliar spray of Pseudomonas fluorescens @ 5g/L or Bacillus subtilis @ 5g/L at flowering and fruit set.",
                    "Foliar spray of Azoxystrobin 18.2% + Difenoconazole 11.4% SC @ 1 ml/L or Copper Oxychloride 50% WP @ 2.5 g/L on standing canopy."
                ],
                "cultural_sanitation": [
                    "Collect and destroy diseased fruits and dry twigs (dieback shoots) during harvesting.",
                    "Avoid overhead sprinkler irrigation during fruit ripening stage."
                ],
                "preventive_measures": [
                    "Destroy previous season crop residue and practice balanced potassium nutrition."
                ],
                "pre_sowing": [
                    "Seed treatment with Trichoderma viride @ 10g/kg seed or Thiram @ 2g/kg seed, or hot water seed treatment (52°C for 30 minutes) before nursery sowing next season."
                ],
                "cultural": [
                    "Collect and destroy diseased fruits and dry twigs during harvesting.",
                    "Avoid overhead irrigation during fruit ripening stage."
                ],
                "biological": [
                    "Foliar spray of Pseudomonas fluorescens @ 5g/L or Bacillus subtilis @ 5g/L at flowering."
                ],
                "cibrc_target": "Anthracnose",
                "citation": "ICAR-IIHR Bengaluru & ANGRAU Chilli Package of Practices 2025"
            }
        ],
        "Tomato": [
            {
                "id": "tomato_early_blight",
                "name": "Early Blight",
                "type": "Fungal",
                "pathogen": "Alternaria solani",
                "symptoms": "Dark brown to black necrotic spots with characteristic concentric rings ('target board' appearance) on lower leaves first. Stem lesions cause collar rot.",
                "distinguishing": "Target-board concentric rings starting from bottom leaves upward.",
                "standing_crop_remedies": [
                    "Foliar spray of Bacillus subtilis @ 5g/L or Trichoderma harzianum foliar formulation @ 5g/L on standing foliage.",
                    "Foliar spray of Mancozeb 75% WP @ 2g/L (Dithane M-45) or Azoxystrobin 18.2% + Difenoconazole 11.4% SC (Amistar Top) @ 1 ml/L on leaves at first lesion onset."
                ],
                "cultural_sanitation": [
                    "Prune bottom 2-3 diseased leaves showing concentric target spots to improve air circulation and prevent soil-splash reinfection.",
                    "Stake tomato plants on trellises to keep foliage away from moist soil.",
                    "Use drip irrigation instead of overhead sprinklers to minimize leaf wetness duration."
                ],
                "preventive_measures": [
                    "Practice 2-3 year crop rotation with non-solanaceous crops.",
                    "Solarize nursery beds with clear polythene sheets for 4-6 weeks prior to sowing next crop."
                ],
                "pre_sowing": [
                    "Seed treatment with Trichoderma harzianum @ 10g/kg seed or Thiram 75% WP @ 2g/kg seed before nursery sowing next season."
                ],
                "cultural": [
                    "Prune bottom 2-3 diseased leaves showing concentric target spots to improve air circulation.",
                    "Stake plants on trellises to keep foliage away from soil splash.",
                    "Use drip irrigation instead of overhead sprinklers to minimize leaf wetness."
                ],
                "biological": [
                    "Foliar spray of Bacillus subtilis @ 5g/L or Trichoderma harzianum foliar spray @ 5g/L."
                ],
                "cibrc_target": "Early Blight",
                "citation": "ICAR-IIHR Bengaluru Tomato Pathology Manual 2025 & PJTSAU Vegetable Guide"
            },
            {
                "id": "tomato_blossom_end_rot",
                "name": "Blossom End Rot (Calcium Deficiency / Moisture Stress)",
                "type": "Nutrient & Water Stress",
                "pathogen": "Abiotic Calcium Imbalance",
                "symptoms": "Water-soaked spot at blossom end of green fruit, enlarging into a flat, leathery, sunken black patch. Not caused by a pathogen.",
                "distinguishing": "Confined strictly to distal blossom end of fruit; foliage remains healthy.",
                "standing_crop_remedies": [
                    "Foliar spray of Calcium Chloride (CaCl2 0.5%) or Calcium Nitrate @ 3-5 g/L during early fruit set."
                ],
                "cultural_sanitation": [
                    "Maintain uniform soil moisture with regular drip irrigation; avoid drought cycles.",
                    "Mulch bed with organic straw to conserve root zone moisture."
                ],
                "preventive_measures": [
                    "Soil application of Agricultural Lime or Gypsum @ 200 kg/acre based on soil test before transplanting next crop."
                ],
                "pre_sowing": [
                    "Soil application of Agricultural Lime or Gypsum @ 200 kg/acre based on soil pH test before transplanting next season."
                ],
                "cultural": [
                    "Maintain uniform soil moisture with regular drip irrigation; avoid drought cycles.",
                    "Mulch bed with organic straw to conserve root zone moisture."
                ],
                "biological": [
                    "Foliar spray of Calcium Chloride (CaCl2 0.5%) or Calcium Nitrate @ 3g/L during early fruit set."
                ],
                "cibrc_target": "Nutrient Deficiency",
                "citation": "ICAR-IIHR Bengaluru Tomato Nutrition Advisory"
            }
        ],
        "Maize": [
            {
                "id": "maize_fall_armyworm",
                "name": "Fall Armyworm (FAW) Infestation",
                "type": "Pest Damage",
                "pathogen": "Spodoptera frugiperda",
                "symptoms": "Pinholes and large windowed ragged shot holes in whorl leaves; central whorl packed with sawdust-like yellowish caterpillar frass.",
                "distinguishing": "Sawdust-like frass in central whorl with inverted Y on larval head.",
                "standing_crop_remedies": [
                    "Whorl application of dry sand mixed with neem seed powder (9:1) at 15-20 DAS into infested whorls.",
                    "Foliar/whorl spray of Metarhizium rileyi or Beauveria bassiana @ 5g/L in late afternoon.",
                    "Foliar spray of Chlorantraniliprole 18.5% SC @ 0.4 ml/L into central whorls."
                ],
                "cultural_sanitation": [
                    "Deep summer ploughing to expose pupae to predatory birds.",
                    "Intercrop with cowpea or red gram (2:1 or 4:1) to boost natural enemies.",
                    "Hand-picking of egg masses and early instar caterpillars in whorls."
                ],
                "preventive_measures": [
                    "Deep summer ploughing to expose pupae to birds; synchronous community sowing."
                ],
                "pre_sowing": [
                    "Seed treatment with Cyantraniliprole 19.8% + Thiamethoxam 19.8% FS @ 4 ml/kg seed before sowing next season."
                ],
                "cultural": [
                    "Hand-picking of egg masses and early instar caterpillars in whorls.",
                    "Intercrop with cowpea or red gram (2:1 or 4:1) to boost natural enemies."
                ],
                "biological": [
                    "Whorl application of dry sand mixed with neem seed powder (9:1) at 15-20 DAS.",
                    "Foliar spray of Metarhizium rileyi or Beauveria bassiana @ 5g/L."
                ],
                "cibrc_target": "Fall Armyworm",
                "citation": "ICAR-IIMR Hyderabad Fall Armyworm IPM Protocol"
            },
            {
                "id": "maize_turcicum_blight",
                "name": "Turcicum Leaf Blight (TLB)",
                "type": "Fungal",
                "pathogen": "Exserohilum turcicum",
                "symptoms": "Long, elliptical, cigar-shaped greyish-green to tan lesions (3-15 cm) developing on lower leaves and progressing upward.",
                "distinguishing": "Long cigar-shaped lesions distinct from circular spots of rust.",
                "standing_crop_remedies": [
                    "Foliar spray of Trichoderma harzianum @ 5g/L on standing canopy at onset of disease.",
                    "Foliar spray of Mancozeb 75% WP @ 2.5g/L or Azoxystrobin + Difenoconazole @ 1 ml/L."
                ],
                "cultural_sanitation": [
                    "Avoid high crop density; practice balanced NPK fertilization.",
                    "Collect and destroy heavily infected lower leaves and post-harvest crop stubbles."
                ],
                "preventive_measures": [
                    "Destroy infected crop residues after harvest and practice balanced NPK fertilization."
                ],
                "pre_sowing": [
                    "Seed treatment with Thiram 75% WP @ 2.5g/kg seed or Trichoderma viride @ 10g/kg seed before sowing next season."
                ],
                "cultural": [
                    "Avoid high crop density; practice balanced NPK fertilization.",
                    "Destroy infected crop residues after harvest."
                ],
                "biological": [
                    "Foliar spray of Trichoderma harzianum @ 5g/L at onset of disease or tasseling."
                ],
                "cibrc_target": "Leaf Blight",
                "citation": "ICAR-IIMR Hyderabad Maize Pathology Advisory"
            }
        ],
        "Turmeric": [
            {
                "id": "turmeric_leaf_spot",
                "name": "Colletotrichum Leaf Spot",
                "type": "Fungal",
                "pathogen": "Colletotrichum curcumae",
                "symptoms": "Brown circular to irregular spots with dark brown margins and yellow halos on both leaf surfaces, coalescing into large necrotic patches.",
                "distinguishing": "Pronounced yellow halo surrounding dark brown necrotic centers.",
                "standing_crop_remedies": [
                    "Foliar spray of Pseudomonas fluorescens @ 5g/L or Bacillus subtilis @ 5g/L on standing leaves.",
                    "Foliar spray of Copper Oxychloride 50% WP @ 2.5g/L or Propiconazole 25% EC @ 1 ml/L."
                ],
                "cultural_sanitation": [
                    "Ensure adequate raised beds with good field drainage; avoid waterlogging.",
                    "Remove and destroy severely spotted lower leaves."
                ],
                "preventive_measures": [
                    "Ensure good drainage and plant certified disease-free mother rhizomes."
                ],
                "pre_sowing": [
                    "Select healthy certified mother rhizomes; rhizome dip in Trichoderma harzianum @ 10g/L or Mancozeb @ 3g/L for 30 minutes before planting next crop."
                ],
                "cultural": [
                    "Ensure adequate raised beds with good field drainage; avoid waterlogging.",
                    "Remove and destroy severely spotted lower leaves."
                ],
                "biological": [
                    "Foliar spray of Pseudomonas fluorescens @ 5g/L on standing foliage."
                ],
                "cibrc_target": "Leaf Spot",
                "citation": "ICAR-IISR Kozhikode & PJTSAU Turmeric Package of Practices"
            }
        ],
        "Groundnut": [
            {
                "id": "groundnut_tikka",
                "name": "Tikka Leaf Spot (Early & Late Cercospora)",
                "type": "Fungal",
                "pathogen": "Cercospora arachidicola & Phaeoisariopsis personata",
                "symptoms": "Small brown spots surrounded by bright yellow halo (Early Tikka) or circular black carbonaceous spots on lower leaf surface (Late Tikka). Leads to severe defoliation.",
                "distinguishing": "Bright yellow halo around circular spots on upper surface, black cushion spots beneath.",
                "standing_crop_remedies": [
                    "Foliar spray of 5% Neem Seed Kernel Extract (NSKE) or Neem oil 1500 ppm @ 5 ml/L at 45 and 60 DAS.",
                    "Foliar spray of Tebuconazole 25.9% EC @ 1.5 ml/L or Chlorothalonil 75% WP @ 2 g/L on standing foliage."
                ],
                "cultural_sanitation": [
                    "Early sowing with onset of monsoon rains to escape peak disease pressure.",
                    "Intercrop with pearl millet or red gram (3:1 ratio)."
                ],
                "preventive_measures": [
                    "Crop rotation with cereals (pearl millet, sorghum) and destruction of volunteer groundnut plants."
                ],
                "pre_sowing": [
                    "Seed treatment with Trichoderma viride @ 10g/kg seed or Carbendazim @ 2g/kg seed before sowing next season."
                ],
                "cultural": [
                    "Early sowing with onset of monsoon rains to escape peak disease pressure.",
                    "Intercrop with pearl millet or red gram (3:1 ratio)."
                ],
                "biological": [
                    "Foliar spray of 5% Neem Seed Kernel Extract (NSKE) at 45 and 60 DAS."
                ],
                "cibrc_target": "Tikka Leaf Spot",
                "citation": "ICAR-DGR Junagadh & ANGRAU Groundnut Production Guide"
            }
        ],
        "Mango": [
            {
                "id": "mango_anthracnose",
                "name": "Mango Anthracnose (Blossom Blight & Tear Staining)",
                "type": "Fungal",
                "pathogen": "Colletotrichum gloeosporioides",
                "symptoms": "Dark brown angular leaf spots, black blighting of floral panicles causing blossom drop, and characteristic 'tear-stain' black streaks on developing fruits.",
                "distinguishing": "Tear-stain streaks on fruit surface and black inflorescence withering.",
                "standing_crop_remedies": [
                    "Foliar spray of Pseudomonas fluorescens @ 5g/L at flower bud emergence.",
                    "Foliar spray of Copper Oxychloride 50% WP @ 3g/L or Carbendazim 12% + Mancozeb 63% WP @ 2g/L on blossoms."
                ],
                "cultural_sanitation": [
                    "Pruning of dead diseased twigs and water sprouts after harvest to open tree canopy for aeration.",
                    "Collect and destroy fallen infected leaves and mummified fruits."
                ],
                "preventive_measures": [
                    "Post-harvest canopy pruning and orchard sanitation."
                ],
                "pre_sowing": [
                    "Prophylactic spray of Bordeaux mixture (1%) or Copper Oxychloride @ 3g/L immediately after post-harvest pruning."
                ],
                "cultural": [
                    "Pruning of dead diseased twigs and water sprouts after harvest to open tree canopy.",
                    "Collect and destroy fallen infected leaves and mummified fruits."
                ],
                "biological": [
                    "Foliar spray of Pseudomonas fluorescens @ 5g/L at flower bud emergence."
                ],
                "cibrc_target": "Anthracnose",
                "citation": "ICAR-CISH Lucknow & Dr. YSRHU Mango Advisory"
            }
        ]
    }

    @classmethod
    async def analyze_image(
        cls,
        image_bytes: bytes,
        crop_name: Optional[str] = None,
        affected_part: Optional[str] = "leaf",
        farmer_notes: Optional[str] = None,
        language: str = "te"
    ) -> Tuple[DiagnosisResult, QualityReport]:
        """
        Executes complete multimodal agricultural diagnosis workflow:
        1. Pre-diagnostic ImageQualityGate (detects blur, underexposure, overexposure, non-plant low foliage).
        2. Multimodal Gemini Vision inference (extracts exact visual lesion morphology, distinguishes healthy vs diseased vs non-plant).
        3. Grounds confirmed diagnoses in authentic ICAR / PJTSAU Package of Practices via RAG.
        4. Verifies chemical treatments against CIBRC statutory labels via SafetyGate.
        5. Automatically flags ambiguous symptoms or low confidence (<0.78) for MAO human escalation.
        """
        # Step 1: Pre-diagnostic Quality Check
        quality = ImageQualityGate.evaluate(image_bytes)
        if not quality.is_acceptable:
            guidance = {
                "te": quality.farmer_guidance_te,
                "hi": quality.farmer_guidance_hi,
                "en": quality.farmer_guidance_en
            }.get(language, quality.farmer_guidance_te)

            guidance_prefix = {
                "te": "నాణ్యత తనిఖీ విఫలమైంది",
                "hi": "गुणवत्ता जांच विफल",
                "en": "Quality check failed"
            }.get(language, "Quality check failed")

            return DiagnosisResult(
                status="quality_rejected",
                is_escalated=False,
                confidence=0.0,
                entropy=0.0,
                primary_diagnosis="Quality Rejected",
                crop_name=crop_name or "Unspecified Crop",
                affected_part=affected_part or "leaf",
                farmer_explanation=f"{guidance_prefix}: {guidance}",
                follow_up_questions=[guidance or "Please retake a clear photo."],
                image_url="",
                citations=[]
            ), quality

        # Multilingual Crop Normalization
        multilingual_map = {
            "వరి": "Rice / Paddy", "వరి పంట": "Rice / Paddy", "rice": "Rice / Paddy", "paddy": "Rice / Paddy", "चावल": "Rice / Paddy", "धान": "Rice / Paddy",
            "ప్రత్తి": "Cotton", "పత్తి": "Cotton", "cotton": "Cotton", "कपास": "Cotton",
            "మిరప": "Chilli", "మిర్చి": "Chilli", "chilli": "Chilli", "chili": "Chilli", "मिर्च": "Chilli",
            "టమోటా": "Tomato", "tomato": "Tomato", "टमाटर": "Tomato",
            "మొక్కజొన్న": "Maize", "maize": "Maize", "corn": "Maize", "मक्का": "Maize",
            "పసుపు": "Turmeric", "turmeric": "Turmeric", "हल्दी": "Turmeric",
            "వేరుశనగ": "Groundnut", "groundnut": "Groundnut", "peanut": "Groundnut", "मूंगफली": "Groundnut",
            "మామిడి": "Mango", "mango": "Mango", "आम": "Mango",
            "గోధుమ": "Wheat", "wheat": "Wheat", "गेहूं": "Wheat"
        }
        resolved_crop = None
        if crop_name:
            crop_name_clean = crop_name.strip()
            if crop_name_clean in multilingual_map:
                resolved_crop = multilingual_map[crop_name_clean]
            else:
                for k, v in multilingual_map.items():
                    if k in crop_name_clean.lower() or crop_name_clean.lower() in k:
                        resolved_crop = v
                        break

        # Step 2: Multimodal Gemini Vision Inference
        vision_res = await gemini_service.analyze_crop_image(
            image_bytes=image_bytes,
            mime_type="image/jpeg",
            crop_name=resolved_crop or crop_name,
            affected_part=affected_part or "leaf",
            farmer_notes=farmer_notes,
            language=language
        )

        # Path A: Gemini Vision Succeeded
        if vision_res.get("success"):
            obs = vision_res.get("visual_observations", "")
            obs_lower = obs.lower()
            detected_crop_val = (vision_res.get("detected_crop") or "").lower()
            has_valid_crop = detected_crop_val not in ["", "none", "unknown", "unspecified", "non-plant", "లేదు"]
            
            has_botanical_leaf = has_valid_crop or any(w in obs_lower for w in [
                "leaf", "leaves", "foliage", "plant", "stem", "midrib", "vein", "veins", "chevron", 
                "chlorophyll", "botanical", "crop", "specimen", "tissue", "canopy",
                "ఆకు", "మొక్క", "పంట", "కొమ్మ", "ఈనె", "పచ్చ",
                "पत्ती", "पौधा", "फसल", "शाखा"
            ])
            has_disease_signs = any(w in obs_lower for w in [
                "lesion", "necrotic", "spot", "blight", "blast", "curl", "stippling", "mite", "thrips", "pest", "disease", "rot", "halo",
                "webbing", "bronzing", "mildew", "rust", "wilt", "yellowing", "chlorosis", "pucker", "damage",
                "మచ్చ", "తెగులు", "ముడత", "నల్లి", "పురుగు", "కీటక",
                "धब्बा", "झुलसा", "ब्लास्ट", "कीट", "मरोड़"
            ])
            diag_str = (vision_res.get("primary_diagnosis") or "").lower()
            is_non_plant_diag = any(w in diag_str for w in ["non-plant", "non plant", "not a plant", "మొక్క లేని"])
            is_explicit_non_plant = any(w in obs_lower for w in [
                "barren soil", "bare earth", "dry dirt", "gravel", "stone", "tool", "wrench", 
                "tractor", "machine", "vehicle", "floor", "concrete", "human", "person", 
                "animal", "furniture", "blank surface", "నేల మాత్రమే", "పనిముట్టు", "ట్రాక్టర్"
            ]) and not (has_botanical_leaf and has_disease_signs)

            conf_val = float(vision_res.get("confidence", 0.60))
            is_clean_leaf_illustration = (
                not is_explicit_non_plant 
                and not has_disease_signs 
                and not vision_res.get("is_ambiguous", False)
                and vision_res.get("status") not in ["escalated", "diagnosed"]
                and conf_val >= 0.78
                and any(w in obs_lower for w in ["leaf", "foliage", "leaves", "specimen"])
            )

            # Sub-case A1: Non-Plant Image
            is_non_plant = (
                is_explicit_non_plant 
                or (is_non_plant_diag and not (has_botanical_leaf and (has_disease_signs or is_clean_leaf_illustration))) 
                or ((not vision_res.get("is_plant_or_crop", True)) and not (has_botanical_leaf and (has_disease_signs or is_clean_leaf_illustration)))
            )
            if (is_explicit_non_plant or is_non_plant) and not is_clean_leaf_illustration:
                non_plant_msg = {
                    "te": "చిత్రంలో పంట ఆకు లేదా మొక్క భాగాలు గుర్తించబడలేదు (కేవలం నేల లేదా ఇతర వస్తువు కనిపిస్తోంది). దయచేసి వ్యాధి సోకిన ఆకును దగ్గరగా ఉంచి స్పష్టంగా ఫోటో తీయండి.",
                    "hi": "तस्वीर में फसल की पत्ती या पौधे का भाग नहीं पाया गया (केवल मिट्टी या अन्य वस्तु दिखाई दे रही है)। कृपया प्रभावित पत्ती की पास से स्पष्ट तस्वीर लें।",
                    "en": "No crop foliage detected in this photo (appears to be soil or a non-plant object). Please photograph an affected crop leaf directly."
                }.get(language, "No crop foliage detected. Please photograph an affected crop leaf directly.")

                return DiagnosisResult(
                    status="non_plant_rejected",
                    is_escalated=False,
                    confidence=0.0,
                    entropy=0.0,
                    primary_diagnosis={
                        "te": "మొక్క లేని చిత్రం / నేల లేదా ఇతర వస్తువు",
                        "hi": "गैर-पौधा छवि / मिट्टी या अन्य वस्तु",
                        "en": "Non-Plant Image / Soil or Object"
                    }.get(language, "Non-Plant Image"),
                    crop_name="None",
                    affected_part="none",
                    pathology_type="Non-Plant",
                    visible_symptoms=obs,
                    farmer_explanation=f"{non_plant_msg}\n• పరిశీలన: {obs}",
                    follow_up_questions=[
                        "దయచేసి వ్యాధి సోకిన పంట ఆకును దగ్గరగా ఫోటో తీయండి." if language == "te" else "Please capture a close-up photo of the affected crop leaf."
                    ],
                    image_url="",
                    citations=[]
                ), quality

            # Sub-case A2: Healthy Plant Foliage
            diag_str = str(vision_res.get("primary_diagnosis", "")).lower()
            is_healthy_leaf = bool(vision_res.get("is_healthy", False)) or any(
                w in diag_str for w in ["healthy", "ఆరోగ్య", "स्वस्थ"]
            ) or is_clean_leaf_illustration
            if is_healthy_leaf:
                v_crop = vision_res.get("detected_crop")
                detected_c = v_crop if (v_crop and v_crop.lower() not in ["unknown", "none", "unspecified", "non-plant"]) else (resolved_crop or crop_name or "Healthy Crop")
                conf = float(vision_res.get("confidence", 0.95))

                healthy_msg = {
                    "te": f"🌱 {detected_c} పంట పరిశీలన:\n• ఆకు పరిశుభ్రంగా, ఆరోగ్యంగా ఉంది.\n• ఎలాంటి శిలీంధ్ర మచ్చలు, బాక్టీరియల్ తెగుళ్లు లేదా పురుగుల నష్టం లక్షణాలు లేవు.\n• సిఫార్సు: సమతుల్య ఎరువుల నిర్వహణ మరియు క్రమబద్ధమైన సస్యరక్షణ కొనసాగించండి.",
                    "hi": f"🌱 {detected_c} फसल निरीक्षण:\n• पत्ती स्वस्थ एवं हरी-भरी है।\n• किसी भी कवक धब्बे, जीवाणु झुलसा या कीट क्षति के लक्षण नहीं हैं।\n• सलाह: संतुलित पोषण एवं नियमित निगरानी जारी रखें।",
                    "en": f"🌱 {detected_c} Foliage Inspection:\n• Leaf tissue appears healthy and vibrant green.\n• No active fungal lesions, bacterial blight, or insect pest damage observed.\n• Recommendation: Continue balanced nutrition, proper irrigation, and periodic crop scouting."
                }.get(language, f"Foliage appears healthy. No disease lesions or pest damage observed on {detected_c}.")

                rag_hits = rag_service.retrieve(query=f"{detected_c} standard crop management package practices", crop=detected_c, top_k=2)
                citations = [{
                    "collection_id": h.get("collection_id", "KB04"),
                    "document_id": h.get("document_id", "KB-DOC-01"),
                    "source_org": h.get("citation", "ICAR / PJTSAU Package of Practices")
                } for h in rag_hits]
                if not citations:
                    citations = [{"collection_id": "KB04", "document_id": "KB-DOC-01", "source_org": "ICAR Package of Practices"}]

                return DiagnosisResult(
                    status="healthy_crop",
                    is_escalated=False,
                    confidence=conf,
                    entropy=0.10,
                    primary_diagnosis={
                        "te": "ఆరోగ్యకరమైన పంట ఆకు",
                        "hi": "स्वस्थ फसल पत्ती",
                        "en": "Healthy Crop Foliage"
                    }.get(language, "Healthy Crop Foliage"),
                    crop_name=detected_c,
                    affected_part=affected_part or "leaf",
                    pathology_type="Healthy",
                    visible_symptoms=obs,
                    farmer_explanation=healthy_msg,
                    follow_up_questions=[
                        "పంటలో నీటిపారుదల లేదా పోషక లోపాలు ఉన్నాయా?" if language == "te" else "Any questions on irrigation or nutrient schedules?"
                    ],
                    image_url="",
                    citations=citations
                ), quality

            # Sub-case A3: Ambiguous or Low Confidence (< 0.78) -> Escalation
            conf = float(vision_res.get("confidence", 0.60))
            is_ambiguous = vision_res.get("is_ambiguous", False) or conf < 0.78
            if is_ambiguous:
                v_crop = vision_res.get("detected_crop")
                detected_c = v_crop if (v_crop and v_crop.lower() not in ["unknown", "none", "unspecified"]) else (resolved_crop or crop_name or "Unspecified Crop")
                suspected = vision_res.get("primary_diagnosis") or "Ambiguous / Inconclusive Symptoms"
                obs = vision_res.get("visual_observations", "Symptoms are visually inconclusive.")
                reason = vision_res.get("uncertainty_reason") or "Visual symptoms lack distinguishing diagnostic markers"
                follow_ups = vision_res.get("follow_up_questions") or [
                    "ఈ లక్షణాలు మొదట ఎప్పుడు ప్రారంభమయ్యాయి?",
                    "లక్షణాలు ఆకులకు మాత్రమే పరిమితమా లేదా కాండానికి కూడా వ్యాపించాయా?"
                ]

                candidates = []
                for diff in vision_res.get("differential_candidates", []):
                    candidates.append(DifferentialCandidate(
                        crop=detected_c,
                        disease_name=diff.get("disease_name", "Alternative"),
                        pathology_type=diff.get("pathology_type", "Fungal"),
                        probability=float(diff.get("probability", 0.3)),
                        distinguishing_features=diff.get("distinguishing_features", "")
                    ))

                return DiagnosisResult(
                    status="escalated",
                    is_escalated=True,
                    confidence=conf,
                    entropy=0.85,
                    primary_diagnosis=suspected,
                    crop_name=detected_c,
                    affected_part=affected_part or "leaf",
                    pathology_type=vision_res.get("pathology_type", "Indeterminate"),
                    visible_symptoms=obs,
                    differential_candidates=candidates,
                    follow_up_questions=follow_ups,
                    farmer_explanation=f"అనుమానిత లక్షణాలు: {suspected}\nపరిశీలన: {obs}\nకారణం: {reason}",
                    image_url="",
                    citations=[{"collection_id": "KB04", "document_id": "PJTSAU-ESC", "source_org": "PJTSAU Advisory Protocol"}]
                ), quality

            # Sub-case A4: High Confidence Verified Diagnosis (>= 0.78)
            v_crop = vision_res.get("detected_crop")
            detected_c = v_crop if (v_crop and v_crop.lower() not in ["unknown", "none", "unspecified", ""]) else (resolved_crop or (crop_name.strip() if crop_name else None))
            disease_diag = vision_res.get("primary_diagnosis")

            # Verify that crop and disease are genuinely visually identified with high confidence
            is_valid_crop = bool(detected_c and detected_c.lower() not in ["unknown", "none", "unspecified", "identified crop", "unspecified crop", ""])
            is_valid_disease = bool(disease_diag and disease_diag.lower() not in ["unknown", "none", "unspecified", "indeterminate", "ambiguous", "identified pathology", "quality rejected", "non-plant image", "non-plant", "non plant", ""])

            if not is_valid_crop or not is_valid_disease or conf < cls.CONFIDENCE_THRESHOLD_HIGH:
                # Inconclusive or missing crop/disease identification -> Escalate safely without retrieving disease remedies
                esc_conf = min(conf, 0.70) if conf >= cls.CONFIDENCE_THRESHOLD_HIGH else conf
                return DiagnosisResult(
                    status="escalated",
                    is_escalated=True,
                    confidence=esc_conf,
                    entropy=0.85,
                    primary_diagnosis=disease_diag or "Inconclusive Symptoms",
                    crop_name=detected_c or "Unspecified Crop",
                    affected_part=affected_part or "leaf",
                    growth_stage="standing_crop",
                    management_stage="active_disease",
                    pathology_type=vision_res.get("pathology_type", "Indeterminate"),
                    visible_symptoms=obs or "Visual evidence insufficient to confirm crop disease safely.",
                    differential_candidates=[],
                    follow_up_questions=vision_res.get("follow_up_questions") or ["దయచేసి వ్యాధి సోకిన ఆకును దగ్గరగా మరొక ఫోటో తీయండి."],
                    farmer_explanation="పంట లేదా తెగులు స్పష్టంగా నిర్ధారించబడలేదు. తప్పుడు చికిత్స నివారించడానికి వ్యవసాయ అధికారి పరిశీలనకు సమర్పించబడింది.",
                    image_url="",
                    citations=[{"collection_id": "KB04", "document_id": "PJTSAU-ESC", "source_org": "PJTSAU Advisory Protocol"}]
                ), quality

            pathology_type = vision_res.get("pathology_type", "Fungal")
            obs = vision_res.get("visual_observations", "")
            distinguishing = vision_res.get("distinguishing_features", "")

            candidates = []
            for diff in vision_res.get("differential_candidates", []):
                candidates.append(DifferentialCandidate(
                    crop=detected_c,
                    disease_name=diff.get("disease_name", "Alternative"),
                    pathology_type=diff.get("pathology_type", "Fungal"),
                    probability=float(diff.get("probability", 0.2)),
                    distinguishing_features=diff.get("distinguishing_features", "")
                ))

            # RAG Retrieval for confirmed disease prioritizing standing foliar management
            rag_query = f"{detected_c} {disease_diag} symptoms foliar management standing crop"
            rag_hits = rag_service.retrieve(
                query=rag_query,
                crop=detected_c,
                top_k=2,
                affected_part=affected_part or "leaf",
                growth_stage="standing_crop"
            )
            citations = []
            cultural_sanitation = []
            standing_crop_remedies = []
            preventive_measures = []
            pre_sowing_remedies = []
            unsupported_warning = None

            catalog = cls.PATHOLOGY_DATABASE.get(detected_c, [])
            match_cat = None
            for d in catalog:
                if d["name"].lower() in disease_diag.lower() or disease_diag.lower() in d["name"].lower() or d["id"] in disease_diag.lower():
                    match_cat = d
                    break

            if match_cat:
                cultural_sanitation = list(match_cat.get("cultural_sanitation") or match_cat.get("cultural", []))
                standing_crop_remedies = list(match_cat.get("standing_crop_remedies") or match_cat.get("biological", []))
                pre_sowing_remedies = list(match_cat.get("pre_sowing") or match_cat.get("preventive_pre_sowing", []))
                preventive_measures = list(match_cat.get("preventive_measures") or pre_sowing_remedies)
                unsupported_warning = match_cat.get("unsupported_cure_warning")
                cat_cit = match_cat.get("citation")
                if cat_cit:
                    citations.append({
                        "collection_id": "KB04",
                        "document_id": match_cat.get("id", "KB04-DOC"),
                        "source_org": cat_cit
                    })

            # HARD INVARIANT: For an active standing crop with foliar symptoms, NEVER allow pre-sowing seed treatments as immediate remedies
            clean_standing_remedies = []
            for r in standing_crop_remedies:
                if is_pre_sowing_practice(r):
                    if r not in pre_sowing_remedies:
                        pre_sowing_remedies.append(r)
                else:
                    clean_standing_remedies.append(r)

            clean_cultural_sanitation = []
            for r in cultural_sanitation:
                if is_pre_sowing_practice(r):
                    if r not in pre_sowing_remedies:
                        pre_sowing_remedies.append(r)
                else:
                    clean_cultural_sanitation.append(r)

            if not clean_cultural_sanitation:
                clean_cultural_sanitation = [
                    "Prune bottom 2-3 diseased leaves to improve canopy air circulation and prevent soil-splash reinfection.",
                    "Maintain strict field sanitation, remove and destroy diseased crop residues, and avoid excessive urea top-dressing."
                ]
            if not clean_standing_remedies:
                clean_standing_remedies = [
                    "Foliar spray of Bacillus subtilis @ 5g/L or Pseudomonas fluorescens @ 5g/L on affected standing foliage.",
                    "Foliar spray of 5% Neem Seed Kernel Extract (NSKE) or Neem oil (1500 ppm) @ 5 ml/L on leaves."
                ]

            # Enforce University evidence: viral pathologies cannot be cured once systemic within standing plant foliage
            if not unsupported_warning and (pathology_type == "Viral" or "virus" in disease_diag.lower() or "leaf curl" in disease_diag.lower()):
                unsupported_warning = (
                    "ICAR and State Agriculture University (ANGRAU/PJTSAU) evidence confirms that viral infections cannot be cured once systemic inside standing plant tissue. "
                    "Do not apply biological or chemical sprays expecting viral cure. Immediately rogue out and destroy severely infected plants, and spray recommended insecticides "
                    "solely to suppress whitefly/thrips insect vectors and prevent secondary spread."
                )

            cultural_remedies = clean_cultural_sanitation
            biological_remedies = clean_standing_remedies

            for hit in rag_hits:
                citations.append({
                    "collection_id": hit.get("collection_id", "KB04"),
                    "document_id": hit.get("document_id", "KB-DOC-01"),
                    "source_org": hit.get("citation", "ICAR / PJTSAU Package of Practices")
                })
            if not citations:
                citations = [{"collection_id": "KB04", "document_id": "KB-DOC-01", "source_org": "ICAR Plant Pathology Guidelines"}]

            # Chemical safety evaluation via SafetyGate
            notes_lower = (farmer_notes or "").lower()
            chemical_intent_words = ["spray", "pesticide", "fungicide", "chemical", "dose", "dosage", "మందు", "స్ప్రే", "దవా", "कीटनाशक"]
            chemical_asked = any(w in notes_lower for w in chemical_intent_words)
            chemical_mgmt = None
            chemical_warning = None

            if chemical_asked:
                safety_decision, meta = safety_gate.evaluate_query(
                    query_text=farmer_notes or disease_diag,
                    crop=detected_c,
                    pest_or_disease=disease_diag,
                    application_method="foliar_spray",
                    growth_stage="standing_crop",
                    plant_part=affected_part or "leaf"
                )
                if safety_decision.approved_product:
                    p = safety_decision.approved_product
                    chemical_mgmt = {
                        "is_approved": True,
                        "product_trade_name": p.get("product_trade_name"),
                        "active_ingredient": p.get("active_ingredient"),
                        "cibrc_registration_number": p.get("cibrc_registration_number"),
                        "dosage_per_acre": p.get("dosage_per_acre"),
                        "dosage_per_hectare": p.get("dosage_per_hectare"),
                        "dosage_per_15l_knapsack": p.get("dosage_per_15l_knapsack") or safety_decision.dosage_per_15l_knapsack or "Refer to CIBRC registered label for dilution",
                        "dilution_water_liters": p.get("dilution_water_liters"),
                        "pre_harvest_interval_days": p.get("pre_harvest_interval_days"),
                        "safety_precautions": p.get("safety_precautions"),
                        "toxicity_band": p.get("toxicity_band"),
                        "citation": safety_decision.source_citation
                    }
                else:
                    chemical_warning = safety_decision.escalation_reason or "No CIBRC approved formulation verified for this target."

            if language == "te":
                imm_te = [
                    f"1. సాగు / పారిశుధ్య చర్యలు: {clean_cultural_sanitation[0]}",
                    f"2. తక్షణ పిచికారీ నివారణ: {clean_standing_remedies[0]}"
                ]
                explanation = (
                    f"గుర్తించిన సమస్య: {disease_diag} ({detected_c})\n"
                    f"• వ్యాధి రకం: {pathology_type}\n"
                    f"• కనిపించే లక్షణాలు: {obs}\n"
                    f"• ముఖ్య లక్షణం: {distinguishing}\n"
                    f"• ప్రస్తుత నిలకడ పంటకు తక్షణ చర్యలు:\n  " + "\n  ".join(imm_te)
                )
                if unsupported_warning:
                    explanation += f"\n• ముఖ్య శాస్త్రీయ గమనిక: {unsupported_warning}"
                if pre_sowing_remedies or preventive_measures:
                    prev_lead = (pre_sowing_remedies or preventive_measures)[0]
                    explanation += f"\n• వచ్చే పంట కాలం కొరకు ముందస్తు నివారణ చర్యలు / విత్తన శుద్ధి:\n  - {prev_lead}"
            elif language == "hi":
                imm_hi = [
                    f"1. खेत की स्वच्छता एवं कटाई-छंटाई: {clean_cultural_sanitation[0]}",
                    f"2. खड़ी फसल हेतु तुरंत छिड़काव: {clean_standing_remedies[0]}"
                ]
                explanation = (
                    f"पहचानी गई समस्या: {disease_diag} ({detected_c})\n"
                    f"• रोग प्रकार: {pathology_type}\n"
                    f"• दृश्य लक्षण: {obs}\n"
                    f"• मुख्य पहचान: {distinguishing}\n"
                    f"• वर्तमान खड़ी फसल हेतु तुरंत उपाय:\n  " + "\n  ".join(imm_hi)
                )
                if unsupported_warning:
                    explanation += f"\n• वैज्ञानिक सलाह: {unsupported_warning}"
                if pre_sowing_remedies or preventive_measures:
                    prev_lead = (pre_sowing_remedies or preventive_measures)[0]
                    explanation += f"\n• अगले मौसम के लिए अग्रिम रोकथाम / बीज उपचार:\n  - {prev_lead}"
            else:
                imm_en = [
                    f"1. Cultural / Sanitation: {clean_cultural_sanitation[0]}",
                    f"2. Active Foliar Treatment: {clean_standing_remedies[0]}"
                ]
                explanation = (
                    f"Identified Diagnosis: {disease_diag} ({detected_c})\n"
                    f"• Pathology Category: {pathology_type}\n"
                    f"• Visual Observations: {obs}\n"
                    f"• Distinguishing Feature: {distinguishing}\n"
                    f"• Immediate Standing Crop Action:\n  " + "\n  ".join(imm_en)
                )
                if unsupported_warning:
                    explanation += f"\n• Crucial Advisory: {unsupported_warning}"
                if pre_sowing_remedies or preventive_measures:
                    prev_lead = (pre_sowing_remedies or preventive_measures)[0]
                    explanation += f"\n• Preventive Measures for Next Season / Pre-Sowing:\n  - {prev_lead}"

            return DiagnosisResult(
                status="diagnosed",
                is_escalated=False,
                confidence=conf,
                entropy=0.25,
                primary_diagnosis=disease_diag,
                crop_name=detected_c,
                affected_part=affected_part or "leaf",
                growth_stage="standing_crop",
                management_stage="active_disease",
                pathology_type=pathology_type,
                pathogen=vision_res.get("pathogen_or_pest"),
                visible_symptoms=obs,
                differential_candidates=candidates,
                follow_up_questions=vision_res.get("follow_up_questions", []),
                standing_crop_remedies=clean_standing_remedies,
                cultural_sanitation=clean_cultural_sanitation,
                preventive_measures=preventive_measures or pre_sowing_remedies,
                pre_sowing_remedies=pre_sowing_remedies,
                cultural_remedies=clean_cultural_sanitation,
                biological_remedies=clean_standing_remedies,
                unsupported_cure_warning=unsupported_warning,
                chemical_management=chemical_mgmt,
                chemical_safety_warning=chemical_warning,
                citations=citations,
                image_url="",
                farmer_explanation=explanation
            ), quality

        # Path B: When LLM vision is unavailable, DO NOT fabricate a diagnosis from farmer's text notes.
        # Enforce Rule 3 & 4: Do not infer disease from typed question alone; never return a fabricated diagnosis.
        logger.warning("Gemini Vision cascade unavailable. Enforcing safety escalation instead of fabricating diagnosis from text notes.")
        effective_crop = resolved_crop or crop_name or "Unspecified Crop"

        try:
            pil_img = Image.open(io.BytesIO(image_bytes)).convert("RGB")
            np_img = np.array(pil_img)
            mean_r, mean_g, mean_b = np.mean(np_img[:,:,0]), np.mean(np_img[:,:,1]), np.mean(np_img[:,:,2])
            red_ratio = mean_r / (mean_g + 1e-5)
            color_std = float(np.std(np_img))
        except Exception:
            red_ratio, color_std = 1.0, 30.0

        # Detect healthy uniform green leaf only with strong visual evidence
        if red_ratio < 0.82 and color_std < 28.0 and quality.foliage_ratio > 0.40:
            return DiagnosisResult(
                status="healthy_crop",
                is_escalated=False,
                confidence=0.85,
                entropy=0.15,
                primary_diagnosis="Healthy Crop Foliage",
                crop_name=effective_crop,
                affected_part=affected_part or "leaf",
                pathology_type="Healthy",
                visible_symptoms="Uniform green foliage without necrotic lesions, chlorosis, or pest chewing.",
                farmer_explanation={
                    "te": f"🌱 {effective_crop} ఆకు దృశ్యరూపంలో ఆరోగ్యంగా కనిపిస్తోంది. ఎలాంటి తెగులు మచ్చలు లేవు.",
                    "hi": f"🌱 {effective_crop} पत्ती दृष्टिगत रूप से स्वस्थ है। कोई रोग के लक्षण नहीं पाए गए।",
                    "en": f"🌱 {effective_crop} foliage visually appears healthy. No disease lesions observed."
                }.get(language, f"Foliage appears healthy on {effective_crop}."),
                follow_up_questions=["పంట నిర్వహణపై ఏవైనా ప్రశ్నలు ఉన్నాయా?"],
                image_url="",
                citations=[{"collection_id": "KB04", "document_id": "KB-DOC-01", "source_org": "ICAR Package of Practices"}]
            ), quality

        # Vision model unavailable and symptoms cannot be verified from pixels alone -> SAFE VISION UNAVAILABLE ESCALATION
        esc_explanation = {
            "te": "మల్టీమోడల్ విజన్ సర్వర్ తాత్కాలికంగా స్పందించలేదు. తప్పుడు నిర్ధారణ నివారించడానికి మీ ఫోటో మండల వ్యవసాయ అధికారి (MAO) పరిశీలనకు సమర్పించబడింది.",
            "hi": "मल्टीमॉडल विज़न सर्वर अस्थायी रूप से अनुपलब्ध है। गलत निदान से बचने के लिए आपकी तस्वीर कृषि विस्तार अधिकारी (MAO) की समीक्षा हेतु भेज दी गई है।",
            "en": "Multimodal vision server is temporarily unavailable. To prevent incorrect diagnoses, your crop photo has been submitted to your Agricultural Extension Officer for manual inspection."
        }.get(language, "Vision service temporarily unavailable. Submitted for agricultural officer review.")

        return DiagnosisResult(
            status="vision_unavailable",
            is_escalated=True,
            confidence=0.0,
            entropy=1.0,
            primary_diagnosis="Vision AI Service Unavailable",
            crop_name=effective_crop,
            affected_part=affected_part or "leaf",
            pathology_type="Service Unavailable",
            visible_symptoms="Image received but multimodal vision model was unavailable to confirm pathology safely.",
            differential_candidates=[],
            follow_up_questions=[
                "దయచేసి నెట్‌వర్క్ పునరుద్ధరించబడినప్పుడు మళ్లీ ప్రయత్నించండి." if language == "te" else "Please retry when vision connectivity is restored.",
                "మీ సమీప రైతు భరోసా కేంద్రం / వ్యవసాయ అధికారిని సంప్రదించండి." if language == "te" else "Consult your local Agricultural Extension Officer."
            ],
            cultural_remedies=[],
            biological_remedies=[],
            chemical_management=None,
            chemical_safety_warning="రసాయన మందుల నిర్ధారణకు ప్రత్యక్ష వ్యవసాయ అధికారి సిఫార్సు తప్పనిసరి." if language == "te" else "Agricultural officer consultation required.",
            citations=[{"collection_id": "KB04", "document_id": "PJTSAU-ESC", "source_org": "PJTSAU Escalation Protocol"}],
            image_url="",
            farmer_explanation=esc_explanation
        ), quality

crop_vision_engine = CropVisionEngine()
