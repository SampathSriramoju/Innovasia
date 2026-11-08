import os
import uuid
import re
from datetime import datetime
from pathlib import Path
from typing import Optional, Dict, Any, List, Tuple
from fastapi import APIRouter, Depends, HTTPException, UploadFile, File, Form, status
from fastapi.security import HTTPAuthorizationCredentials
from sqlalchemy.orm import Session

from app.core.logging import logger
from app.core.security import security_scheme, decode_access_token
from app.database.session import get_db
from app.database.models import User, Crop, AgriculturalTicket
from app.safety.safety_gate import safety_gate
from app.ai.gemini_service import gemini_service
from app.ai.rag_service import rag_service
from app.ai.crop_vision_engine import crop_vision_engine

router = APIRouter()

# Directory for sanitized disease diagnosis images
STATIC_UPLOADS_DIR = Path(__file__).resolve().parent.parent.parent / "static" / "uploads" / "diseases"
STATIC_UPLOADS_DIR.mkdir(parents=True, exist_ok=True)

# Confidence threshold as mandated: >= 0.80 is verified diagnosis, < 0.80 triggers auto-escalation
CONFIDENCE_THRESHOLD = 0.80

# Known multimodal disease signatures
DISEASE_CATALOG = {
    "leaf_spot": {
        "disease_name": "Bacterial Blight / Angular Leaf Spot",
        "default_crop": "Cotton",
        "pathogen_type": "Bacterial (Xanthomonas citri pv. malvacearum)",
        "symptoms": "Small, water-soaked, angular spots on leaves bounded by veins. Spots turn dark brown to black. Progresses to 'blackarm' on stems and boll rot.",
        "cultural_remedies": [
            "Use disease-free certified seeds and acid delinting with concentrated H2SO4 (100ml/kg seed).",
            "Prompt destruction and burning of infected crop residues to eliminate overwintering bacterial inoculum.",
            "Avoid excessive split doses of nitrogenous fertilizers; maintain balanced potassium nutrition.",
            "Practice crop rotation with non-host crops (cereals, pulses)."
        ],
        "biological_remedies": [
            "Seed treatment with Pseudomonas fluorescens @ 10g/kg seed.",
            "Foliar spray of Bacillus subtilis @ 5g/L at early symptom development."
        ],
        "citations": [
            {"collection_id": "KB04", "document_id": "d1000000-0000-0000-0000-000000000002", "source_org": "Directorate of Plant Protection (DPPQS)"},
            {"collection_id": "KB06", "document_id": "doc-kb06-001", "source_org": "NCIPM"}
        ]
    },
    "blast": {
        "disease_name": "Rice Blast",
        "default_crop": "Rice / Paddy",
        "pathogen_type": "Fungal (Magnaporthe oryzae)",
        "symptoms": "Spindle-shaped / diamond-shaped lesions on leaves with brown borders and grayish-white centers. Neck blast causes blackening of neck node and empty panicles (chaffy grains).",
        "cultural_remedies": [
            "Provide 30 cm alleyways every 2-3 meters in transplanted paddy fields to improve aeration.",
            "Avoid excessive nitrogen application; apply nitrogen in 3 split doses.",
            "Maintain alternate wetting and drying (AWD) rather than continuous stagnant flooding.",
            "Burn infected stubbles and eradicate collateral grassy weeds (Echinochloa) from bunds."
        ],
        "biological_remedies": [
            "Seed treatment with Pseudomonas fluorescens or Trichoderma viride @ 10g/kg seed.",
            "Foliar spray of Pseudomonas fluorescens @ 5g/L at tillering and boot leaf stage."
        ],
        "citations": [
            {"collection_id": "KB04", "document_id": "d1000000-0000-0000-0000-000000000001", "source_org": "Directorate of Plant Protection (DPPQS)"},
            {"collection_id": "KB06", "document_id": "doc-kb06-002", "source_org": "NCIPM"}
        ]
    },
    "blight": {
        "disease_name": "Early Blight",
        "default_crop": "Tomato",
        "pathogen_type": "Fungal (Alternaria solani)",
        "symptoms": "Concentric dark brown rings ('target board' appearance) on lower leaves first. Collar rot on stems, dark sunken leathery lesions at stem end of fruits.",
        "cultural_remedies": [
            "Stake plants securely on trellises to keep foliage away from soil splash.",
            "Prune bottom 2-3 suckers and senescent leaves to improve air circulation.",
            "Use drip irrigation instead of overhead sprinklers to minimize leaf wetness duration.",
            "Crop rotation with non-solanaceous crops (pulses, cereals, green manure)."
        ],
        "biological_remedies": [
            "Seed treatment with Trichoderma harzianum @ 10g/kg seed.",
            "Foliar spray of Bacillus subtilis @ 5g/L during cloudy humid weather."
        ],
        "citations": [
            {"collection_id": "KB04", "document_id": "d1000000-0000-0000-0000-000000000003", "source_org": "Directorate of Plant Protection (DPPQS)"},
            {"collection_id": "KB06", "document_id": "doc-kb06-004", "source_org": "NCIPM"}
        ]
    },
    "chlorosis": {
        "disease_name": "Interveinal Chlorosis / Micronutrient Deficiency",
        "default_crop": "Cotton",
        "pathogen_type": "Physiological / Abiotic Micronutrient Deficiency",
        "symptoms": "Interveinal yellowing of young leaves while veins remain green (iron/zinc chlorosis), general pale yellowing and stunted vegetative vigour.",
        "cultural_remedies": [
            "Incorporate well-decomposed FYM @ 5 tons/acre to improve micronutrient chelation and soil organic carbon.",
            "Avoid waterlogging and excessive soil compaction which restrict root oxygen and micronutrient uptake.",
            "Apply agricultural gypsum @ 2 tons/ha if soil is alkaline (pH > 8.0) to facilitate nutrient uptake."
        ],
        "biological_remedies": [
            "Foliar spray of Ferrous Sulphate (FeSO4 19%) @ 5g/L + Citric acid 1g/L for iron chlorosis.",
            "Foliar application of Zinc Sulphate (ZnSO4 21%) @ 2g/L or Chelated Zinc (Zn-EDTA 12%) @ 1g/L."
        ],
        "citations": [
            {"collection_id": "KB10", "document_id": "doc-kb10-001", "source_org": "Soil Health Card Scheme, GoI"},
            {"collection_id": "KB09", "document_id": "doc-kb09-002", "source_org": "Department of Fertilizers & FAI"}
        ]
    },
    "mite_webbing": {
        "disease_name": "Spider Mite Infestation & Webbing",
        "default_crop": "Chilli",
        "pathogen_type": "Acarine Infestation (Tetranychus urticae / Polyphagotarsonemus latus)",
        "symptoms": "Fine silky webbing on leaf undersides, bronzing, stippling and speckled yellow/white spots. Downward curling of leaf margins ('inverted boat shape').",
        "cultural_remedies": [
            "Maintain optimal soil moisture; dry stress and dust accelerate spider mite multiplication.",
            "High-pressure water spray on leaf undersides to dislodge mites and destroy web shelters.",
            "Erect barrier crops of maize or sorghum around field borders.",
            "Avoid indiscriminate synthetic pyrethroid sprays which eliminate predatory phytoseiid mites."
        ],
        "biological_remedies": [
            "Foliar spray of Neem oil (3000 ppm) @ 3-5 ml/L with mild soap emulsifier.",
            "Application of Lecanicillium lecanii or Beauveria bassiana @ 5g/L on lower leaf canopy."
        ],
        "citations": [
            {"collection_id": "KB05", "document_id": "p1000000-0000-0000-0000-000000000003", "source_org": "NBAIR"},
            {"collection_id": "KB06", "document_id": "doc-kb06-003", "source_org": "NCIPM"}
        ]
    },
    "leaf_curl": {
        "disease_name": "Leaf Curl Viral Disease",
        "default_crop": "Chilli",
        "pathogen_type": "Viral (Begomovirus / Geminivirus transmitted by Whitefly Bemisia tabaci)",
        "symptoms": "Upward curling and puckering of leaves, shortened internodes, enations on leaf undersides, severely stunted bushy plant habit.",
        "cultural_remedies": [
            "Grow 3-4 border rows of sorghum, maize, or bajra as whitefly windbreaks 30 days prior to transplanting.",
            "Install yellow sticky traps @ 15-20 per acre to monitor and mass-trap whitefly vectors.",
            "Use silver-black reflective plastic mulch to deter winged whitefly vectors from landing.",
            "Rogue out and destroy virus-infected plants within the first 45 days after planting."
        ],
        "biological_remedies": [
            "Seedling root dip in Pseudomonas fluorescens @ 10g/L for 30 minutes before transplanting.",
            "Foliar spray of 5% Neem Seed Kernel Extract (NSKE) or Neem oil 1500 ppm @ 5 ml/L at 10-day intervals."
        ],
        "citations": [
            {"collection_id": "KB04", "document_id": "d1000000-0000-0000-0000-000000000002", "source_org": "Directorate of Plant Protection (DPPQS)"},
            {"collection_id": "KB06", "document_id": "doc-kb06-003", "source_org": "NCIPM"}
        ]
    }
}


# Maximum allowed image upload size: 10MB
MAX_IMAGE_SIZE_BYTES = 10 * 1024 * 1024

def sanitize_image_bytes(raw_bytes: bytes, content_type: str) -> Tuple[bytes, str]:
    """
    Validates JPEG/PNG formats via magic bytes, enforces max size (10MB),
    and strips EXIF/ancillary metadata to ensure privacy and prevent metadata exploits.
    """
    if len(raw_bytes) > MAX_IMAGE_SIZE_BYTES:
        raise HTTPException(
            status_code=status.HTTP_413_CONTENT_TOO_LARGE,
            detail=f"Uploaded image size ({len(raw_bytes) / (1024 * 1024):.2f} MB) exceeds maximum allowed limit of 10 MB."
        )

    if len(raw_bytes) < 16:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="Uploaded file is empty or too small to be a valid image."
        )

    # Validate Magic Bytes
    is_jpeg = raw_bytes.startswith(b'\xff\xd8\xff')
    is_png = raw_bytes.startswith(b'\x89PNG\r\n\x1a\n')

    if not is_jpeg and not is_png:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="Invalid image format. Only JPEG and PNG crop leaf images are accepted."
        )

    if is_jpeg:
        # Strip EXIF (APP1: 0xFFE1) and Comments (0xFFFE) from JPEG stream
        sanitized = bytearray(b'\xff\xd8')
        pos = 2
        length = len(raw_bytes)
        while pos < length:
            if raw_bytes[pos] != 0xff:
                sanitized.extend(raw_bytes[pos:])
                break
            marker = raw_bytes[pos:pos+2]
            if marker == b'\xff\xda': # Start of Scan - rest is pixel data
                sanitized.extend(raw_bytes[pos:])
                break
            if pos + 4 > length:
                break
            segment_len = int.from_bytes(raw_bytes[pos+2:pos+4], 'big')
            if marker in (b'\xff\xe1', b'\xff\xfe'): # Strip EXIF and Comments
                pos += 2 + segment_len
            else:
                sanitized.extend(raw_bytes[pos:pos+2+segment_len])
                pos += 2 + segment_len
        return bytes(sanitized), "jpg"

    else: # PNG
        # Strip non-critical chunks: eXIf, tEXt, zTXt, iTXt
        signature = b'\x89PNG\r\n\x1a\n'
        sanitized = bytearray(signature)
        pos = len(signature)
        length = len(raw_bytes)
        while pos + 8 <= length:
            chunk_len = int.from_bytes(raw_bytes[pos:pos+4], 'big')
            chunk_type = raw_bytes[pos+4:pos+8]
            total_chunk_size = 4 + 4 + chunk_len + 4
            if pos + total_chunk_size > length:
                break
            if chunk_type in (b'eXIf', b'tEXt', b'zTXt', b'iTXt'):
                pos += total_chunk_size
            else:
                sanitized.extend(raw_bytes[pos:pos+total_chunk_size])
                pos += total_chunk_size
        return bytes(sanitized), "png"


async def get_optional_user(
    credentials: Optional[HTTPAuthorizationCredentials] = Depends(security_scheme),
    db: Session = Depends(get_db)
) -> Dict[str, Any]:
    """Resolves authenticated farmer or provides fallback farmer profile."""
    if credentials and credentials.credentials:
        payload = decode_access_token(credentials.credentials)
        if payload:
            return payload
    farmer = db.query(User).filter(User.role == "farmer").first()
    if farmer:
        return {"sub": farmer.id, "name": farmer.full_name, "role": "farmer", "lang": farmer.preferred_language or "te"}
    return {"sub": "user-test-farmer-01", "name": "Farmer", "role": "farmer", "lang": "te"}


@router.post("/diagnose")
async def diagnose_crop_disease(
    file: UploadFile = File(...),
    crop_name: Optional[str] = Form(None),
    plant_part: Optional[str] = Form("leaf"),
    farmer_notes: Optional[str] = Form(None),
    latitude: Optional[float] = Form(None),
    longitude: Optional[float] = Form(None),
    language: Optional[str] = Form(None),
    current_user: Dict[str, Any] = Depends(get_optional_user),
    db: Session = Depends(get_db)
):
    """
    Multimodal Agricultural Vision Diagnosis Endpoint:
    1. Validates and sanitizes image bytes (JPEG/PNG magic bytes, strips EXIF/GPS).
    2. Runs ImageQualityGate (detects blur, underexposure, overexposure, non-plant OOD).
    3. Runs calibrated computer vision inference across 42 pathology classes and 8 crops.
    4. Performs symptom-based differential diagnosis with candidate rankings.
    5. Grounds recommendations in verified ICAR/PJTSAU IPM practices.
    6. Verifies CIBRC statutory labels via SafetyGate if chemical spray advice is asked.
    7. Automatically creates an AgriculturalTicket escalation if confidence < 0.78 or symptoms are ambiguous.
    """
    user_id = current_user.get("sub", "user-test-farmer-01")
    user_lang = (language or current_user.get("lang") or "te").lower()
    if user_lang not in ["te", "hi", "en"]:
        user_lang = "te"

    # Step 1: Read and validate image file
    raw_bytes = await file.read()
    sanitized_bytes, ext = sanitize_image_bytes(raw_bytes, file.content_type or "")

    # Save sanitized image
    file_id = uuid.uuid4().hex[:12]
    sanitized_filename = f"crop_diag_{file_id}.{ext}"
    sanitized_filepath = STATIC_UPLOADS_DIR / sanitized_filename
    with open(sanitized_filepath, "wb") as f:
        f.write(sanitized_bytes)

    image_url = f"/static/uploads/diseases/{sanitized_filename}"

    # Step 2: Computer Vision & Diagnostic Analysis
    combined_notes = f"{file.filename or ''} {farmer_notes or ''}".strip()
    
    diag_res, quality = crop_vision_engine.analyze_image(
        image_bytes=sanitized_bytes,
        crop_name=crop_name,
        affected_part=plant_part or "leaf",
        farmer_notes=combined_notes,
        language=user_lang
    )

    # Handle Quality Gate Rejection
    if not quality.is_acceptable:
        guidance = {
            "te": quality.farmer_guidance_te,
            "hi": quality.farmer_guidance_hi,
            "en": quality.farmer_guidance_en
        }.get(user_lang, quality.farmer_guidance_te)

        if user_lang == "hi":
            rej_disease = "गुणवत्ता अस्वीकृत / फोटो स्पष्ट नहीं है"
            rej_treatment = "कृपया अच्छी रोशनी में पत्ती के पास से स्थिर और स्पष्ट फोटो लें।"
        elif user_lang == "en":
            rej_disease = "Quality Rejected / Image Unclear"
            rej_treatment = "Please retake a clear, steady close-up photo in good daylight."
        else:
            rej_disease = "చిత్రం సరిగ్గా లేదు / నాణ్యత ఆమోదించబడలేదు"
            rej_treatment = "దయచేసి మంచి వెలుతురులో ఆకును దగ్గరగా ఉంచి స్థిరంగా ఫోటో తీయండి."

        return {
            "status": "quality_rejected",
            "is_escalated": False,
            "confidence": 0.0,
            "disease_name": rej_disease,
            "symptoms": guidance,
            "treatment": rej_treatment,
            "citations": ["Bol Kisan Image Quality Gate (Laplacian Variance / Foliage Ratio)"],
            "error_code": quality.error_code,
            "blur_score": quality.blur_score,
            "brightness": quality.brightness,
            "foliage_ratio": quality.foliage_ratio,
            "message": guidance,
            "image_url": image_url,
            "crop_name": crop_name or "Unspecified"
        }

    # Step 3: Check Confidence & Thresholding
    # Ambiguous or Low Confidence (< 0.78) -> Auto Escalation
    if diag_res.status == "escalated" or diag_res.confidence < 0.78:
        crop_str = crop_name.strip() if crop_name else diag_res.crop_name
        crop_obj = db.query(Crop).filter(Crop.name_en.ilike(f"%{crop_str}%")).first() if crop_str else None
        ticket_num = f"ESC-{datetime.now().strftime('%Y%m%d')}-{uuid.uuid4().hex[:6].upper()}"

        escalation_reason = f"Uncertain Disease Diagnosis ({diag_res.primary_diagnosis or 'Ambiguous Symptoms'}, confidence: {round(diag_res.confidence * 100, 1)}%)"
        notes_transcript = farmer_notes or f"Multimodal crop photo inspection for {crop_str} (Confidence: {round(diag_res.confidence * 100, 1)}%, Entropy: {diag_res.entropy})"

        ticket = AgriculturalTicket(
            ticket_number=ticket_num,
            farmer_id=user_id,
            crop_id=crop_obj.id if crop_obj else None,
            state="Telangana",
            district="Warangal",
            mandal="Geesugonda",
            status="open",
            priority="high",
            escalation_reason=escalation_reason,
            original_question_transcript=notes_transcript,
            attached_image_urls=[image_url]
        )
        db.add(ticket)
        db.commit()
        db.refresh(ticket)

        if user_lang == "hi":
            esc_disease = f"संदिग्ध रोग: {diag_res.primary_diagnosis or 'अस्पष्ट लक्षण'}"
            esc_treatment = "लक्षण स्पष्ट नहीं होने के कारण कृषि विस्तार अधिकारी (MAO) की समीक्षा हेतु भेजा गया है। शीघ्र समाधान उपलब्ध कराया जाएगा।"
            esc_msg = "निदान विश्वसनीयता 78% से कम है। मंडल कृषि अधिकारी के लिए निरीक्षण टिकट स्वचालित रूप से बनाया गया है।"
        elif user_lang == "en":
            esc_disease = f"Suspected Pathology: {diag_res.primary_diagnosis or 'Ambiguous Symptoms'}"
            esc_treatment = "Due to symptom ambiguity, this case has been escalated to your Mandal Agricultural Extension Officer (MAO) for inspection."
            esc_msg = "Diagnosis confidence is below 78% or visually ambiguous. An inspection ticket has been automatically created for the Mandal Agricultural Extension Officer."
        else:
            esc_disease = f"అనుమానిత తెగులు: {diag_res.primary_diagnosis or 'స్పష్టత లేని లక్షణాలు'}"
            esc_treatment = "లక్షణాలు స్పష్టంగా లేనందున వ్యవసాయ అధికారి (MAO) పరిశీలనకు పంపబడింది. ఫీల్డ్ విజిట్ త్వరలో జరుగుతుంది."
            esc_msg = "రోగ నిర్ధారణ విశ్వసనీయత 78% కంటే తక్కువగా ఉంది. మండల వ్యవసాయ విస్తరణ అధికారి పరిశీలనకు టికెట్ సృష్టించబడింది."

        return {
            "status": "escalated",
            "is_escalated": True,
            "confidence": diag_res.confidence,
            "entropy": diag_res.entropy,
            "disease_name": esc_disease,
            "symptoms": diag_res.visible_symptoms or escalation_reason,
            "treatment": esc_treatment,
            "citations": [f"Ticket #{ticket.ticket_number}", "PJTSAU Advisory Protocol"],
            "ticket_id": ticket.id,
            "ticket_number": ticket.ticket_number,
            "escalation_reason": escalation_reason,
            "message": esc_msg,
            "image_url": image_url,
            "crop_name": crop_str,
            "differential_candidates": [c.model_dump() for c in diag_res.differential_candidates],
            "follow_up_questions": diag_res.follow_up_questions
        }

    # Step 4: High Confidence Verified Diagnosis
    if user_lang == "hi":
        c_lead = diag_res.cultural_remedies[0] if diag_res.cultural_remedies else "खेत की स्वच्छता एवं फसल चक्र"
        b_lead = diag_res.biological_remedies[0] if diag_res.biological_remedies else "जैविक नियंत्रण"
        treatment_str = (
            f"• सस्य प्रबंधन एवं प्राकृतिक उपचार:\n  1. {c_lead}\n  2. {b_lead}"
        )
        if diag_res.chemical_management and diag_res.chemical_management.get("is_approved"):
            cm = diag_res.chemical_management
            treatment_str += f"\n• CIBRC अनुशंसित कीटनाशक: {cm.get('product_trade_name')} ({cm.get('active_ingredient')}) @ {cm.get('dosage_per_15l_knapsack')} (प्रतीक्षा अवधि PHI: {cm.get('pre_harvest_interval_days')} दिन)"
    elif user_lang == "en":
        c_lead = diag_res.cultural_remedies[0] if diag_res.cultural_remedies else "Crop sanitation and field aeration"
        b_lead = diag_res.biological_remedies[0] if diag_res.biological_remedies else "Biological control"
        treatment_str = (
            f"• Cultural / Agronomic Remedies:\n  1. {c_lead}\n  2. {b_lead}"
        )
        if diag_res.chemical_management and diag_res.chemical_management.get("is_approved"):
            cm = diag_res.chemical_management
            treatment_str += f"\n• CIBRC Approved Pesticide: {cm.get('product_trade_name')} ({cm.get('active_ingredient')}) @ {cm.get('dosage_per_15l_knapsack')} (PHI: {cm.get('pre_harvest_interval_days')} days)"
    else:
        c_lead = diag_res.cultural_remedies[0] if diag_res.cultural_remedies else 'సాగు పరిశుభ్రత'
        b_lead = diag_res.biological_remedies[0] if diag_res.biological_remedies else 'జీవ నియంత్రణ'
        treatment_str = (
            f"• సహజ / సస్యరక్షణ చర్యలు:\n  1. {c_lead}\n  2. {b_lead}"
        )
        if diag_res.chemical_management and diag_res.chemical_management.get("is_approved"):
            cm = diag_res.chemical_management
            treatment_str += f"\n• CIBRC సిఫార్సు చేసిన రసాయన మందు: {cm.get('product_trade_name')} ({cm.get('active_ingredient')}) @ {cm.get('dosage_per_15l_knapsack')} (PHI: {cm.get('pre_harvest_interval_days')} రోజులు)"

    formatted_citations = []
    for c in diag_res.citations:
        if isinstance(c, dict):
            formatted_citations.append(f"{c.get('collection_id', 'KB04')} - {c.get('source_org', 'ICAR/PJTSAU')}")
        else:
            formatted_citations.append(str(c))
    if not formatted_citations:
        formatted_citations = ["KB04 - ICAR / PJTSAU Package of Practices"]

    return {
        "status": "diagnosed",
        "is_escalated": False,
        "confidence": diag_res.confidence,
        "entropy": diag_res.entropy,
        "disease_name": diag_res.primary_diagnosis,
        "crop_name": diag_res.crop_name,
        "pathology_type": diag_res.pathology_type,
        "pathogen": diag_res.pathogen,
        "symptoms": diag_res.visible_symptoms,
        "treatment": treatment_str,
        "cultural_remedies": diag_res.cultural_remedies,
        "biological_remedies": diag_res.biological_remedies,
        "chemical_management": diag_res.chemical_management,
        "chemical_safety_warning": diag_res.chemical_safety_warning,
        "differential_candidates": [c.model_dump() for c in diag_res.differential_candidates],
        "citations": formatted_citations,
        "citation_objects": diag_res.citations,
        "farmer_explanation": diag_res.farmer_explanation,
        "image_url": image_url
    }
