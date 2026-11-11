"""
Seed script for Bol Kisan Demo Accounts.
- Farmer Account: Phone 9876543210 / +919876543210, Name Kishan Rao, OTP 123456
- Extension Officer Account: Code AGRI-TS-042, Phone 9123456789, OTP 654321,
  Designation Mandal Agricultural Officer (MAO), Jurisdiction Warangal (Geesugonda, Wardhannapet, Atmakur)
"""

import sys
from pathlib import Path

# Add backend directory to sys.path
backend_dir = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(backend_dir))

from app.database.session import SessionLocal, init_db
from app.database import models

def seed_demo_accounts():
    init_db()
    db = SessionLocal()
    try:
        # 1. Farmer Account
        farmer_user = db.query(models.User).filter(
            models.User.phone_number.in_(["9876543210", "+919876543210"])
        ).first()

        if not farmer_user:
            farmer_user = models.User(
                phone_number="+919876543210",
                full_name="Kishan Rao",
                role="farmer",
                preferred_lang="te",
                is_active=True,
                is_verified=True
            )
            db.add(farmer_user)
            db.flush()
            print("Created demo farmer user:", farmer_user.full_name, farmer_user.phone_number)
        else:
            print("Demo farmer already exists:", farmer_user.full_name, farmer_user.phone_number)

        if not farmer_user.farmer_profile:
            profile = models.FarmerProfile(
                user_id=farmer_user.id,
                state="Telangana",
                district="Warangal",
                mandal="Geesugonda",
                village="Dharmaram",
                land_size_acres=3.5,
                soil_type="Black Cotton",
                irrigation_source="Borewell"
            )
            db.add(profile)
            db.commit()
            print("Created demo farmer profile.")

        # 2. Extension Officer Account
        officer = db.query(models.ExtensionOfficer).filter(
            models.ExtensionOfficer.officer_code == "AGRI-TS-042"
        ).first()

        if not officer:
            officer_user = db.query(models.User).filter(
                models.User.phone_number.in_(["9123456789", "+919123456789"])
            ).first()

            if not officer_user:
                officer_user = models.User(
                    phone_number="9123456789",
                    full_name="Dr. K. Srinivas Rao, MAO",
                    role="extension_officer",
                    preferred_lang="te",
                    is_active=True,
                    is_verified=True
                )
                db.add(officer_user)
                db.flush()
                print("Created demo officer user:", officer_user.full_name)

            officer = models.ExtensionOfficer(
                user_id=officer_user.id,
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
            print("Created demo extension officer:", officer.officer_code)
        else:
            print("Demo officer already exists:", officer.officer_code)

        print("Seeding completed successfully.")
    finally:
        db.close()

if __name__ == "__main__":
    seed_demo_accounts()
