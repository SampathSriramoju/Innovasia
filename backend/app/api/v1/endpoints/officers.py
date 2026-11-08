"""
FastAPI Router for Regional Agricultural Extension Officer Directory.
"""

from typing import Optional, List
from fastapi import APIRouter, Query, Depends, HTTPException, status
from sqlalchemy.orm import Session
from app.database.session import get_db
from app.integrations.officers import (
    officer_service,
    OfficerLookupResponse,
    OfficerContactInfo
)

router = APIRouter()


@router.get(
    "/lookup",
    response_model=OfficerLookupResponse,
    summary="Lookup Extension Officers by District and Mandal"
)
async def lookup_officers(
    district: Optional[str] = Query(None, description="District name (e.g. Warangal, Guntur, Indore, Ludhiana)"),
    mandal: Optional[str] = Query(None, description="Mandal / Block / Tehsil name (e.g. Geesugonda, Tenali, Depalpur)"),
    state: Optional[str] = Query(None, description="State name"),
    db: Session = Depends(get_db)
):
    """
    Looks up regional Agricultural Extension Officers (AEO), Mandal Agriculture Officers (MAO),
    and KVK Subject Matter Specialists:
    - Matches database registered officers with priority
    - Augments with authentic government agricultural directory
    - Returns contact phone numbers, official email, and Rythu Vedika / office locations
    """
    try:
        officers = officer_service.lookup_officers(district=district, mandal=mandal, state=state, db=db)
        return OfficerLookupResponse(
            query_district=district,
            query_mandal=mandal,
            query_state=state,
            total_found=len(officers),
            data_source="GOVT_EXTENSION_DIRECTORY_AND_DB",
            officers=officers
        )
    except Exception as e:
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail=f"Officer lookup error: {str(e)}"
        )


@router.get(
    "/{officer_code}",
    response_model=OfficerContactInfo,
    summary="Get Extension Officer Details by Officer Code"
)
async def get_officer_by_code(
    officer_code: str,
    db: Session = Depends(get_db)
):
    """
    Retrieves full profile and contact details for a specific agricultural officer by code.
    """
    officer = officer_service.get_officer_by_code(officer_code=officer_code, db=db)
    if not officer:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail=f"Extension officer with code '{officer_code}' not found."
        )
    return officer
