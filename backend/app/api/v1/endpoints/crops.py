from fastapi import APIRouter, Depends, HTTPException
from typing import List, Optional
from sqlalchemy.orm import Session
from pydantic import BaseModel
from app.database.session import get_db
from app.database.models import Crop, Disease, Pest

router = APIRouter()

class CropOut(BaseModel):
    id: str
    scientific_name: str
    name_en: str
    name_te: str
    name_hi: str
    category: str
    season: Optional[str] = None

@router.get("/", response_model=List[CropOut])
async def list_crops(db: Session = Depends(get_db)):
    """
    Returns all registered agricultural crops with Telugu, Hindi, and English names.
    Seeds default crops if table is empty.
    """
    crops = db.query(Crop).filter(Crop.is_active == True).all()
    if not crops:
        seed_crops = [
            Crop(scientific_name="Gossypium hirsutum", name_en="Cotton", name_te="ప్రత్తి (పత్తి)", name_hi="कपास", category="Commercial Fiber", season="Kharif"),
            Crop(scientific_name="Oryza sativa", name_en="Rice / Paddy", name_te="వరి", name_hi="धान / चावल", category="Cereal Food Grain", season="Kharif / Rabi"),
            Crop(scientific_name="Capsicum annuum", name_en="Chilli", name_te="మిరప", name_hi="मिर्च", category="Spices", season="Kharif / Rabi"),
            Crop(scientific_name="Solanum lycopersicum", name_en="Tomato", name_te="టమోటా", name_hi="टमाटर", category="Vegetable", season="Year-round"),
        ]
        db.add_all(seed_crops)
        db.commit()
        crops = db.query(Crop).all()

    return crops

@router.get("/{crop_id}")
async def get_crop_detail(crop_id: str, db: Session = Depends(get_db)):
    crop = db.query(Crop).filter(Crop.id == crop_id).first()
    if not crop:
        raise HTTPException(status_code=404, detail="Crop not found")

    diseases = db.query(Disease).filter(Disease.crop_id == crop.id).all()
    pests = db.query(Pest).filter(Pest.crop_id == crop.id).all()

    return {
        "id": crop.id,
        "scientific_name": crop.scientific_name,
        "name_en": crop.name_en,
        "name_te": crop.name_te,
        "name_hi": crop.name_hi,
        "category": crop.category,
        "season": crop.season,
        "diseases": [
            {"id": d.id, "name_en": d.name_en, "name_te": d.name_te, "name_hi": d.name_hi, "symptoms": d.symptoms_description}
            for d in diseases
        ],
        "pests": [
            {"id": p.id, "name_en": p.name_en, "name_te": p.name_te, "name_hi": p.name_hi, "etl": p.economic_threshold_level, "symptoms": p.damage_symptoms}
            for p in pests
        ]
    }
