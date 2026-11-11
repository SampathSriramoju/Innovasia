from typing import Generator
from sqlalchemy import create_engine
from sqlalchemy.orm import sessionmaker, Session
from app.core.config import settings
from app.database.base import Base

# Support SQLite or PostgreSQL seamlessly
connect_args = {}
if "sqlite" in settings.SYNC_DATABASE_URL:
    connect_args = {"check_same_thread": False}

engine = create_engine(
    settings.SYNC_DATABASE_URL,
    connect_args=connect_args,
    pool_pre_ping=True
)

SessionLocal = sessionmaker(autocommit=False, autoflush=False, bind=engine)

def get_db() -> Generator[Session, None, None]:
    db = SessionLocal()
    try:
        yield db
    except Exception:
        db.rollback()
        raise
    finally:
        db.close()

def init_db():
    from app.database import models  # Ensure all model tables are registered on Base.metadata
    from sqlalchemy import inspect, text
    Base.metadata.create_all(bind=engine)

    inspector = inspect(engine)
    with engine.connect() as conn:
        # Schema migration and backfill for mandi_price_records
        if inspector.has_table("mandi_price_records"):
            existing_cols = {c["name"] for c in inspector.get_columns("mandi_price_records")}
            new_cols = [
                ("market", "VARCHAR(150)"),
                ("commodity", "VARCHAR(100)"),
                ("grade", "VARCHAR(50) DEFAULT 'FAQ'"),
                ("arrival_date", "VARCHAR(40)"),
                ("min_price", "NUMERIC(10, 2) DEFAULT 0"),
                ("max_price", "NUMERIC(10, 2) DEFAULT 0"),
                ("modal_price", "NUMERIC(10, 2) DEFAULT 0"),
                ("last_synced_at", "TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP"),
            ]
            for col_name, col_type in new_cols:
                if col_name not in existing_cols:
                    try:
                        conn.execute(text(f"ALTER TABLE mandi_price_records ADD COLUMN {col_name} {col_type}"))
                    except Exception:
                        pass

            # Backfill any nulls from legacy columns
            try:
                conn.execute(text("""
                    UPDATE mandi_price_records SET
                        market = COALESCE(market, mandi_name, 'Warangal APMC'),
                        commodity = COALESCE(commodity, commodity_name, 'Cotton'),
                        grade = COALESCE(grade, 'FAQ'),
                        arrival_date = COALESCE(arrival_date, price_date, '2026-11-09'),
                        min_price = COALESCE(min_price, min_price_per_q, 0),
                        max_price = COALESCE(max_price, max_price_per_q, 0),
                        modal_price = COALESCE(modal_price, modal_price_per_q, 0),
                        variety = COALESCE(variety, 'Standard'),
                        last_synced_at = COALESCE(last_synced_at, created_at, CURRENT_TIMESTAMP)
                    WHERE market IS NULL OR commodity IS NULL OR arrival_date IS NULL
                """))
            except Exception:
                pass

            if engine.dialect.name == "postgresql":
                for col in ["mandi_id", "mandi_name", "commodity_name", "min_price_per_q", "max_price_per_q", "modal_price_per_q", "price_date"]:
                    try:
                        conn.execute(text(f"ALTER TABLE mandi_price_records ALTER COLUMN {col} DROP NOT NULL"))
                    except Exception:
                        pass
                try:
                    conn.execute(text("""
                        CREATE UNIQUE INDEX IF NOT EXISTS uq_mandi_price_records_market_commodity_date
                        ON mandi_price_records (market, commodity, variety, grade, arrival_date)
                    """))
                except Exception:
                    pass

        if "sqlite" in settings.SYNC_DATABASE_URL:
            inspector = inspect(engine)
            for table_name, table in Base.metadata.tables.items():
                if inspector.has_table(table_name):
                    existing_cols = {c["name"] for c in inspector.get_columns(table_name)}
                    for col in table.columns:
                        if col.name not in existing_cols:
                            try:
                                col_type = col.type.compile(engine.dialect)
                                conn.execute(text(f"ALTER TABLE {table_name} ADD COLUMN {col.name} {col_type}"))
                            except Exception:
                                pass
        conn.commit()

    # Seed core crops if table is empty
    with SessionLocal() as db:
        if db.query(models.Crop).count() == 0:
            seed_crops = [
                models.Crop(scientific_name="Gossypium hirsutum", name_en="Cotton", name_te="ప్రత్తి (పత్తి)", name_hi="कपास", category="Commercial Fiber", season="Kharif"),
                models.Crop(scientific_name="Oryza sativa", name_en="Rice / Paddy", name_te="వరి", name_hi="धान / चावल", category="Cereal Food Grain", season="Kharif / Rabi"),
                models.Crop(scientific_name="Capsicum annuum", name_en="Chilli", name_te="మిరప", name_hi="मिर्च", category="Spices", season="Kharif / Rabi"),
                models.Crop(scientific_name="Solanum lycopersicum", name_en="Tomato", name_te="టమోటా", name_hi="टमाटर", category="Vegetable", season="Year-round"),
            ]
            db.add_all(seed_crops)
            db.commit()

        # Seed demo farmer and demo extension officer if missing
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
