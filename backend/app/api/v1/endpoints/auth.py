import uuid
from datetime import datetime, timezone
from fastapi import APIRouter, Depends, HTTPException, status
from pydantic import BaseModel, Field
from typing import Optional, List, Dict, Any
from sqlalchemy.orm import Session
from app.database.session import get_db
from app.database.models import User, FarmerProfile, ExtensionOfficer
from app.core.security import create_access_token, get_current_user_payload, RoleChecker

router = APIRouter()

# In-memory OTP store for development & testing
DEV_OTP_STORE: Dict[str, str] = {
    "9876543210": "123456",
    "9123456789": "654321",
    "9000000001": "123456",
    "9000000002": "123456"
}

class OTPRequest(BaseModel):
    phone_number: str = Field(..., pattern=r"^\+?[0-9]{10,13}$", description="10-12 digit mobile phone number")

class OTPVerify(BaseModel):
    phone_number: str
    otp: str
    preferred_lang: Optional[str] = "te" # 'te', 'hi', 'en'
    full_name: Optional[str] = None
    state: Optional[str] = "Telangana"
    district: Optional[str] = "Warangal"
    mandal: Optional[str] = "Geesugonda"
    village: Optional[str] = "Dharmaram"

class FarmerRegisterRequest(BaseModel):
    phone_number: str = Field(..., pattern=r"^\+?[0-9]{10,13}$")
    full_name: str
    preferred_lang: Optional[str] = "te"
    state: str = "Telangana"
    district: str = "Warangal"
    mandal: str = "Geesugonda"
    village: str = "Dharmaram"
    land_size_acres: Optional[float] = 3.5
    soil_type: Optional[str] = "Black Cotton"
    irrigation_source: Optional[str] = "Borewell"
    primary_crops: Optional[List[str]] = ["Maize", "Cotton"]

class OfficerRegisterRequest(BaseModel):
    phone_number: str = Field(..., pattern=r"^\+?[0-9]{10,13}$")
    full_name: str
    designation: str = "Mandal Agricultural Officer (MAO)"
    department: str = "Department of Agriculture, Telangana"
    official_email: str
    employee_id: str
    assigned_state: str = "Telangana"
    assigned_district: str = "Warangal"
    assigned_mandals: List[str] = ["Geesugonda", "Wardhannapet", "Atmakur"]

class OfficerLoginRequest(BaseModel):
    officer_code: str
    phone_number: str
    otp: str

@router.post("/request-otp")
async def request_otp(payload: OTPRequest):
    phone = payload.phone_number
    otp = DEV_OTP_STORE.get(phone, "123456")
    DEV_OTP_STORE[phone] = otp

    return {
        "status": "success",
        "message": f"OTP sent to {phone}",
        "dev_otp": otp
    }

@router.post("/verify-otp")
async def verify_otp(payload: OTPVerify, db: Session = Depends(get_db)):
    phone = payload.phone_number
    expected_otp = DEV_OTP_STORE.get(phone, "123456")

    if payload.otp != expected_otp:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="Invalid OTP code. Please check and try again."
        )

    user = db.query(User).filter(User.phone_number == phone).first()
    if not user:
        full_name = payload.full_name or f"Farmer {phone[-4:]}"
        user = User(
            phone_number=phone,
            full_name=full_name,
            role="farmer",
            preferred_lang=payload.preferred_lang or "te",
            is_active=True,
            is_verified=True
        )
        db.add(user)
        db.commit()
        db.refresh(user)

        profile = FarmerProfile(
            user_id=user.id,
            state=payload.state or "Telangana",
            district=payload.district or "Warangal",
            mandal=payload.mandal or "Geesugonda",
            village=payload.village or "Dharmaram",
            land_size_acres=3.5,
            soil_type="Black Cotton",
            irrigation_source="Borewell"
        )
        db.add(profile)
        db.commit()
    else:
        if payload.preferred_lang:
            user.preferred_lang = payload.preferred_lang
            db.commit()

    token_data = {
        "sub": user.id,
        "phone": user.phone_number,
        "name": user.full_name,
        "role": user.role,
        "lang": user.preferred_lang
    }
    access_token = create_access_token(data=token_data)

    return {
        "access_token": access_token,
        "token_type": "bearer",
        "user": {
            "id": user.id,
            "phone_number": user.phone_number,
            "full_name": user.full_name,
            "role": user.role,
            "preferred_lang": user.preferred_lang
        }
    }

@router.post("/register-farmer")
async def register_farmer(payload: FarmerRegisterRequest, db: Session = Depends(get_db)):
    user = db.query(User).filter(User.phone_number == payload.phone_number).first()
    if not user:
        user = User(
            phone_number=payload.phone_number,
            full_name=payload.full_name,
            role="farmer",
            preferred_lang=payload.preferred_lang or "te",
            is_active=True,
            is_verified=True
        )
        db.add(user)
        db.commit()
        db.refresh(user)

        profile = FarmerProfile(
            user_id=user.id,
            state=payload.state,
            district=payload.district,
            mandal=payload.mandal,
            village=payload.village,
            land_size_acres=payload.land_size_acres or 3.5,
            soil_type=payload.soil_type or "Black Cotton",
            irrigation_source=payload.irrigation_source or "Borewell"
        )
        db.add(profile)
        db.commit()
    else:
        user.full_name = payload.full_name
        user.preferred_lang = payload.preferred_lang or user.preferred_lang
        if user.farmer_profile:
            user.farmer_profile.state = payload.state
            user.farmer_profile.district = payload.district
            user.farmer_profile.mandal = payload.mandal
            user.farmer_profile.village = payload.village
        db.commit()

    token_data = {
        "sub": user.id,
        "phone": user.phone_number,
        "name": user.full_name,
        "role": "farmer",
        "lang": user.preferred_lang
    }
    access_token = create_access_token(data=token_data)

    return {
        "access_token": access_token,
        "token_type": "bearer",
        "user": {
            "id": user.id,
            "phone_number": user.phone_number,
            "full_name": user.full_name,
            "role": "farmer",
            "preferred_lang": user.preferred_lang,
            "profile": {
                "state": payload.state,
                "district": payload.district,
                "mandal": payload.mandal,
                "village": payload.village,
                "land_size_acres": payload.land_size_acres
            }
        }
    }

@router.post("/register-officer")
async def register_officer(payload: OfficerRegisterRequest, db: Session = Depends(get_db)):
    # Check if user already exists
    user = db.query(User).filter(User.phone_number == payload.phone_number).first()
    if user:
        if user.officer_profile:
            return {
                "status": "already_registered",
                "officer_code": user.officer_profile.officer_code,
                "approval_status": user.officer_profile.approval_status,
                "message": f"Officer account already exists with status: {user.officer_profile.approval_status}."
            }

    officer_code = f"AGRI-{payload.assigned_state[:2].upper()}-{uuid.uuid4().hex[:4].upper()}"

    if not user:
        user = User(
            phone_number=payload.phone_number,
            full_name=payload.full_name,
            role="extension_officer",
            preferred_lang="te",
            is_active=True,
            is_verified=False # Pending admin approval
        )
        db.add(user)
        db.commit()
        db.refresh(user)

    officer = ExtensionOfficer(
        user_id=user.id,
        officer_code=officer_code,
        designation=payload.designation,
        department=payload.department,
        assigned_state=payload.assigned_state,
        assigned_district=payload.assigned_district,
        assigned_mandals=payload.assigned_mandals,
        approval_status="PENDING_APPROVAL",
        employee_id=payload.employee_id,
        official_email=payload.official_email
    )
    db.add(officer)
    db.commit()
    db.refresh(officer)

    return {
        "status": "pending_approval",
        "officer_id": officer.id,
        "officer_code": officer.officer_code,
        "approval_status": "PENDING_APPROVAL",
        "message": (
            "Registration submitted successfully. Extension Officer accounts require administrative verification. "
            "Your credentials will be activated upon approval by the State Department of Agriculture."
        )
    }

@router.post("/officer-login")
async def officer_login(payload: OfficerLoginRequest, db: Session = Depends(get_db)):
    officer = db.query(ExtensionOfficer).filter(
        ExtensionOfficer.officer_code == payload.officer_code
    ).first()

    # Pre-seeded approved demo officer
    if not officer and payload.officer_code == "AGRI-TS-042":
        user = db.query(User).filter(User.phone_number == payload.phone_number).first()
        if not user:
            user = User(
                phone_number=payload.phone_number,
                full_name="Dr. K. Srinivas Rao, MAO",
                role="extension_officer",
                preferred_lang="te",
                is_active=True,
                is_verified=True
            )
            db.add(user)
            db.commit()
            db.refresh(user)

        officer = ExtensionOfficer(
            user_id=user.id,
            officer_code="AGRI-TS-042",
            designation="Mandal Agricultural Officer (MAO)",
            department="Department of Agriculture, Telangana",
            assigned_state="Telangana",
            assigned_district="Warangal",
            assigned_mandals=["Geesugonda", "Wardhannapet", "Atmakur"],
            approval_status="APPROVED",
            employee_id="TS-AGRI-10842",
            official_email="srinivas.rao@agri.telangana.gov.in"
        )
        db.add(officer)
        db.commit()
        db.refresh(officer)

    if not officer:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="Invalid officer credentials or unverified officer code."
        )

    # Enforce Approval Status Invariant
    if officer.approval_status == "PENDING_APPROVAL":
        raise HTTPException(
            status_code=status.HTTP_403_FORBIDDEN,
            detail="Officer account is pending administrator approval. Please contact the Agricultural Department."
        )
    elif officer.approval_status == "REJECTED":
        raise HTTPException(
            status_code=status.HTTP_403_FORBIDDEN,
            detail="Officer registration application has been rejected by the administrator."
        )

    token_data = {
        "sub": officer.user_id,
        "officer_code": officer.officer_code,
        "role": "extension_officer",
        "district": officer.assigned_district,
        "mandals": officer.assigned_mandals
    }
    access_token = create_access_token(data=token_data)

    return {
        "access_token": access_token,
        "token_type": "bearer",
        "officer": {
            "id": officer.id,
            "officer_code": officer.officer_code,
            "designation": officer.designation,
            "assigned_district": officer.assigned_district,
            "assigned_mandals": officer.assigned_mandals,
            "approval_status": officer.approval_status
        }
    }

# ==============================================================================
# Admin Officer Approval Endpoints
# ==============================================================================

@router.get("/admin/pending-officers")
async def list_pending_officers(
    current_admin: Dict[str, Any] = Depends(RoleChecker(["admin"])),
    db: Session = Depends(get_db)
):
    pending = db.query(ExtensionOfficer).filter(
        ExtensionOfficer.approval_status == "PENDING_APPROVAL"
    ).all()

    return [
        {
            "id": o.id,
            "officer_code": o.officer_code,
            "full_name": o.user.full_name if o.user else "Officer",
            "phone_number": o.user.phone_number if o.user else "",
            "designation": o.designation,
            "department": o.department,
            "official_email": o.official_email,
            "employee_id": o.employee_id,
            "assigned_state": o.assigned_state,
            "assigned_district": o.assigned_district,
            "assigned_mandals": o.assigned_mandals,
            "approval_status": o.approval_status,
            "created_at": o.created_at.isoformat() if o.created_at else None
        }
        for o in pending
    ]

@router.post("/admin/approve-officer/{officer_id}")
async def approve_officer(
    officer_id: str,
    current_admin: Dict[str, Any] = Depends(RoleChecker(["admin"])),
    db: Session = Depends(get_db)
):
    officer = db.query(ExtensionOfficer).filter(ExtensionOfficer.id == officer_id).first()
    if not officer:
        raise HTTPException(status_code=404, detail="Officer not found")

    officer.approval_status = "APPROVED"
    if officer.user:
        officer.user.is_verified = True
    db.commit()

    return {
        "status": "APPROVED",
        "officer_id": officer.id,
        "officer_code": officer.officer_code,
        "message": f"Officer {officer.officer_code} approved successfully. Officer can now log in."
    }

@router.post("/admin/reject-officer/{officer_id}")
async def reject_officer(
    officer_id: str,
    current_admin: Dict[str, Any] = Depends(RoleChecker(["admin"])),
    db: Session = Depends(get_db)
):
    officer = db.query(ExtensionOfficer).filter(ExtensionOfficer.id == officer_id).first()
    if not officer:
        raise HTTPException(status_code=404, detail="Officer not found")

    officer.approval_status = "REJECTED"
    db.commit()

    return {
        "status": "REJECTED",
        "officer_id": officer.id,
        "officer_code": officer.officer_code,
        "message": f"Officer {officer.officer_code} application has been rejected."
    }

@router.get("/me")
async def get_current_user_profile(
    current_user: Dict[str, Any] = Depends(get_current_user_payload),
    db: Session = Depends(get_db)
):
    user_id = current_user.get("sub")
    user = db.query(User).filter(User.id == user_id).first()
    if not user:
        raise HTTPException(status_code=404, detail="User not found")

    profile_data = None
    if user.farmer_profile:
        fp = user.farmer_profile
        profile_data = {
            "state": fp.state,
            "district": fp.district,
            "mandal": fp.mandal,
            "village": fp.village,
            "land_size_acres": float(fp.land_size_acres or 0),
            "soil_type": fp.soil_type,
            "irrigation_source": fp.irrigation_source
        }

    officer_data = None
    if user.officer_profile:
        op = user.officer_profile
        officer_data = {
            "officer_code": op.officer_code,
            "designation": op.designation,
            "department": op.department,
            "approval_status": op.approval_status,
            "assigned_district": op.assigned_district,
            "assigned_mandals": op.assigned_mandals
        }

    return {
        "id": user.id,
        "phone_number": user.phone_number,
        "full_name": user.full_name,
        "role": user.role,
        "preferred_lang": user.preferred_lang,
        "profile": profile_data,
        "officer_profile": officer_data
    }
