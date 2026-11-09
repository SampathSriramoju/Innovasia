import secrets
import uuid
from datetime import datetime, timezone, timedelta
from typing import Optional, List, Dict, Any
from fastapi import APIRouter, Depends, HTTPException, status
from fastapi.security import HTTPAuthorizationCredentials
from pydantic import BaseModel, Field
from sqlalchemy.orm import Session

from app.core.config import settings
from app.database.session import get_db
from app.database.models import User, FarmerProfile, ExtensionOfficer
from app.core.security import (
    create_access_token,
    decode_access_token,
    get_current_user_payload,
    security_scheme,
    RoleChecker
)

router = APIRouter()

# Structured in-memory OTP store with expiration & attempt tracking
class OTPRecord:
    def __init__(self, otp: str, expires_at: datetime, attempts: int = 0):
        self.otp = otp
        self.expires_at = expires_at
        self.attempts = attempts

# Live OTP Store: phone -> OTPRecord
LIVE_OTP_STORE: Dict[str, OTPRecord] = {}

# Development deterministic seeds (ONLY used if settings.ENVIRONMENT == "development" and settings.ENABLE_DEV_OTP)
DEV_SEED_OTPS: Dict[str, str] = {
    "9876543210": "123456",
    "+919876543210": "123456",
    "9123456789": "654321",
    "+919123456789": "654321",
    "9000000001": "123456",
    "9000000002": "123456",
    "9111111111": "123456",
    "9000000088": "123456",
    "9000000099": "123456"
}

def normalize_phone(phone: str) -> str:
    cleaned = phone.strip()
    if cleaned.startswith("+91") and len(cleaned) == 13:
        return cleaned[3:]
    return cleaned

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
    otp: Optional[str] = None
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
    otp: Optional[str] = None
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
    raw_phone = payload.phone_number
    phone = normalize_phone(raw_phone)

    # Disable production OTP login if SMS gateway is unconfigured
    if settings.ENVIRONMENT == "production" and not settings.SMS_GATEWAY_API_KEY:
        raise HTTPException(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            detail="Production SMS delivery gateway is not configured. OTP delivery is unavailable."
        )

    # In dev mode with dev OTP enabled, allow deterministic seed if configured, else random
    if settings.ENVIRONMENT == "development" and settings.ENABLE_DEV_OTP and phone in DEV_SEED_OTPS:
        otp = DEV_SEED_OTPS[phone]
    else:
        # Cryptographically secure random 6-digit OTP
        otp = str(secrets.randbelow(900000) + 100000)

    # Store with 5-minute expiry
    expires_at = datetime.now(timezone.utc) + timedelta(minutes=5)
    record = OTPRecord(otp=otp, expires_at=expires_at, attempts=0)
    LIVE_OTP_STORE[phone] = record
    LIVE_OTP_STORE[raw_phone] = record

    response: Dict[str, Any] = {
        "status": "success",
        "message": f"OTP sent to {raw_phone}"
    }

    # CRITICAL SECURITY: Never return OTP in production API responses
    if settings.ENVIRONMENT == "development" and settings.ENABLE_DEV_OTP:
        response["dev_otp"] = otp

    return response

@router.post("/verify-otp")
async def verify_otp(payload: OTPVerify, db: Session = Depends(get_db)):
    raw_phone = payload.phone_number
    phone = normalize_phone(raw_phone)

    # Look up OTP record
    record = LIVE_OTP_STORE.get(phone) or LIVE_OTP_STORE.get(raw_phone)

    # In development mode only: allow seed OTP fallback if requested
    if not record and settings.ENVIRONMENT == "development" and settings.ENABLE_DEV_OTP:
        if phone in DEV_SEED_OTPS:
            record = OTPRecord(
                otp=DEV_SEED_OTPS[phone],
                expires_at=datetime.now(timezone.utc) + timedelta(minutes=10),
                attempts=0
            )

    if not record:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="No active OTP request found for this mobile number. Please request an OTP first."
        )

    # Check expiration
    if datetime.now(timezone.utc) > record.expires_at:
        LIVE_OTP_STORE.pop(phone, None)
        LIVE_OTP_STORE.pop(raw_phone, None)
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="OTP code has expired. Please request a new OTP."
        )

    # Rate limiting on failed verification attempts
    if record.attempts >= 5:
        LIVE_OTP_STORE.pop(phone, None)
        LIVE_OTP_STORE.pop(raw_phone, None)
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="Too many failed attempts. Please request a new OTP."
        )

    # Validate OTP value
    if payload.otp != record.otp:
        record.attempts += 1
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="Invalid OTP code. Please check and try again."
        )

    # Consume OTP on successful verification to prevent replay attacks
    LIVE_OTP_STORE.pop(phone, None)
    LIVE_OTP_STORE.pop(raw_phone, None)

    user = db.query(User).filter(
        (User.phone_number == phone) | (User.phone_number == raw_phone)
    ).first()

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
async def register_farmer(
    payload: FarmerRegisterRequest,
    credentials: Optional[HTTPAuthorizationCredentials] = Depends(security_scheme),
    db: Session = Depends(get_db)
):
    raw_phone = payload.phone_number
    phone = normalize_phone(raw_phone)

    # Authentication validation: Require either a valid JWT matching the phone OR a verified OTP
    authenticated_sub = None
    if credentials and credentials.credentials:
        token_payload = decode_access_token(credentials.credentials)
        if token_payload:
            token_phone = normalize_phone(token_payload.get("phone", ""))
            if token_phone == phone:
                authenticated_sub = token_payload.get("sub")

    if not authenticated_sub:
        if settings.ENVIRONMENT == "development" and settings.ENABLE_DEV_OTP and phone in DEV_SEED_OTPS:
            # Pre-seeded development test accounts
            pass
        else:
            # Genuine OTP verification is strictly required
            record = LIVE_OTP_STORE.get(phone) or LIVE_OTP_STORE.get(raw_phone)
            valid_dev = settings.ENVIRONMENT == "development" and settings.ENABLE_DEV_OTP and payload.otp in ["123456", "654321"]
            if not record and not valid_dev:
                raise HTTPException(
                    status_code=status.HTTP_401_UNAUTHORIZED,
                    detail="Genuine OTP verification is required before farmer registration or login."
                )
            if record and (not payload.otp or payload.otp != record.otp or datetime.now(timezone.utc) > record.expires_at):
                raise HTTPException(
                    status_code=status.HTTP_401_UNAUTHORIZED,
                    detail="Invalid or expired OTP code for farmer registration."
                )
            if not valid_dev and not payload.otp:
                raise HTTPException(
                    status_code=status.HTTP_401_UNAUTHORIZED,
                    detail="Genuine OTP verification is required before farmer registration or login."
                )
            LIVE_OTP_STORE.pop(phone, None)
            LIVE_OTP_STORE.pop(raw_phone, None)

    user = db.query(User).filter(
        (User.phone_number == phone) | (User.phone_number == raw_phone)
    ).first()

    if not user:
        user = User(
            phone_number=phone,
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
    raw_phone = payload.phone_number
    phone = normalize_phone(raw_phone)

    # Verify phone ownership via genuine OTP before officer registration
    record = LIVE_OTP_STORE.get(phone) or LIVE_OTP_STORE.get(raw_phone)
    valid_dev = settings.ENVIRONMENT == "development" and settings.ENABLE_DEV_OTP and (
        (payload.otp and (payload.otp in ["123456", "654321"] or (phone in DEV_SEED_OTPS and payload.otp == DEV_SEED_OTPS[phone])))
        or (not payload.otp and phone in DEV_SEED_OTPS)
    )
    if not record and not valid_dev:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Genuine OTP verification is required to verify officer phone ownership before registration."
        )
    if record:
        if datetime.now(timezone.utc) > record.expires_at:
            LIVE_OTP_STORE.pop(phone, None)
            LIVE_OTP_STORE.pop(raw_phone, None)
            raise HTTPException(
                status_code=status.HTTP_401_UNAUTHORIZED,
                detail="OTP code has expired. Please request a new OTP."
            )
        if payload.otp != record.otp and not valid_dev:
            record.attempts += 1
            raise HTTPException(
                status_code=status.HTTP_401_UNAUTHORIZED,
                detail="Invalid OTP for officer phone verification."
            )
        LIVE_OTP_STORE.pop(phone, None)
        LIVE_OTP_STORE.pop(raw_phone, None)

    user = db.query(User).filter(User.phone_number == phone).first()
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
            phone_number=phone,
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
    raw_phone = payload.phone_number
    phone = normalize_phone(raw_phone)

    officer = db.query(ExtensionOfficer).filter(
        ExtensionOfficer.officer_code == payload.officer_code
    ).first()

    # Pre-seeded approved demo officer is STRICTLY limited to development environment
    if not officer and payload.officer_code == "AGRI-TS-042":
        if settings.ENVIRONMENT != "development" or not settings.ENABLE_DEV_OTP:
            raise HTTPException(
                status_code=status.HTTP_404_NOT_FOUND,
                detail="Officer account not found. Demonstration accounts are disabled in production."
            )

        user = db.query(User).filter(
            (User.phone_number == phone) | (User.phone_number == raw_phone)
        ).first()
        if not user:
            user = User(
                phone_number=phone,
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

    # An OTP proves control of a phone number, not control of an officer code.
    # Bind both credentials before issuing an officer token.
    officer_phone = normalize_phone(officer.user.phone_number) if officer.user else None
    if not officer_phone or officer_phone != phone:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Officer code is not registered to this mobile number."
        )

    # Enforce Approval Status Invariant FIRST
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

    # Enforce OTP verification
    record = LIVE_OTP_STORE.get(phone) or LIVE_OTP_STORE.get(raw_phone)
    if not record and settings.ENVIRONMENT == "development" and settings.ENABLE_DEV_OTP and phone in DEV_SEED_OTPS:
        # Test/demo convenience is scoped to the configured phone and still
        # passes through the officer-phone binding above.
        record = OTPRecord(DEV_SEED_OTPS[phone], datetime.now(timezone.utc) + timedelta(minutes=5))
    if settings.ENVIRONMENT == "development" and settings.ENABLE_DEV_OTP:
        valid_dev = record and payload.otp == record.otp
        if not valid_dev:
            raise HTTPException(
                status_code=status.HTTP_400_BAD_REQUEST,
                detail="Invalid OTP code for officer authentication."
            )
    else:
        if not record or payload.otp != record.otp or datetime.now(timezone.utc) > record.expires_at:
            raise HTTPException(
                status_code=status.HTTP_400_BAD_REQUEST,
                detail="Invalid or expired OTP code for officer authentication."
            )
    # OTPs are single use in every environment.
    LIVE_OTP_STORE.pop(phone, None)
    LIVE_OTP_STORE.pop(raw_phone, None)

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
            "officer_id": o.id,
            "officer_code": o.officer_code,
            "full_name": o.user.full_name if o.user else "Officer",
            "phone_number": o.user.phone_number if o.user else "",
            "designation": o.designation,
            "department": o.department,
            "assigned_state": o.assigned_state,
            "assigned_district": o.assigned_district,
            "assigned_mandals": o.assigned_mandals,
            "employee_id": o.employee_id,
            "official_email": o.official_email,
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
    officer.verified_at = datetime.now(timezone.utc)
    if officer.user:
        officer.user.is_verified = True
    db.commit()

    return {
        "status": "APPROVED",
        "officer_id": officer.id,
        "officer_code": officer.officer_code,
        "message": f"Officer {officer.officer_code} has been approved successfully."
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
