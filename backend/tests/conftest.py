import pytest
from fastapi.testclient import TestClient
from sqlalchemy import create_engine
from sqlalchemy.orm import sessionmaker
from app.database.base import Base
from app.database.session import get_db
from app.database.models import User, FarmerProfile, ExtensionOfficer
from app.main import app
from app.core.security import create_access_token

# Test SQLite in-memory database
TEST_DATABASE_URL = "sqlite:///:memory:"

engine = create_engine(TEST_DATABASE_URL, connect_args={"check_same_thread": False})
TestingSessionLocal = sessionmaker(autocommit=False, autoflush=False, bind=engine)

@pytest.fixture(scope="session")
def db_engine():
    Base.metadata.create_all(bind=engine)
    yield engine
    Base.metadata.drop_all(bind=engine)

@pytest.fixture(scope="function")
def db_session(db_engine):
    connection = db_engine.connect()
    transaction = connection.begin()
    session = TestingSessionLocal(bind=connection)

    yield session

    session.close()
    transaction.rollback()
    connection.close()

@pytest.fixture(scope="function")
def client(db_session):
    def override_get_db():
        try:
            yield db_session
        finally:
            pass

    app.dependency_overrides[get_db] = override_get_db
    with TestClient(app) as test_client:
        yield test_client
    app.dependency_overrides.clear()

@pytest.fixture
def farmer_token(db_session):
    user = db_session.query(User).filter(User.id == "user-test-farmer-01").first()
    if not user:
        user = User(
            id="user-test-farmer-01",
            phone_number="9876543210",
            full_name="Test Farmer",
            role="farmer",
            preferred_lang="te",
            is_active=True,
            is_verified=True
        )
        db_session.add(user)
        profile = FarmerProfile(
            user_id=user.id,
            state="Telangana",
            district="Warangal",
            mandal="Geesugonda",
            village="Dharmaram",
            land_size_acres=3.5
        )
        db_session.add(profile)
        db_session.commit()
    payload = {
        "sub": "user-test-farmer-01",
        "phone": "9876543210",
        "name": "Test Farmer",
        "role": "farmer",
        "lang": "te"
    }
    return create_access_token(data=payload)

@pytest.fixture
def officer_token(db_session):
    user = db_session.query(User).filter(User.id == "user-test-officer-01").first()
    if not user:
        user = User(
            id="user-test-officer-01",
            phone_number="9123456789",
            full_name="Dr. K. Srinivas Rao",
            role="extension_officer",
            preferred_lang="te",
            is_active=True,
            is_verified=True
        )
        db_session.add(user)
        officer = ExtensionOfficer(
            id="officer-test-01",
            user_id=user.id,
            officer_code="AGRI-TS-042",
            designation="Mandal Agricultural Officer (MAO)",
            department="Department of Agriculture, Telangana",
            assigned_state="Telangana",
            assigned_district="Warangal",
            assigned_mandals=["Geesugonda", "Wardhannapet", "Atmakur"],
            approval_status="APPROVED",
            employee_id="TS-AGRI-10842"
        )
        db_session.add(officer)
        db_session.commit()
    payload = {
        "sub": "user-test-officer-01",
        "officer_code": "AGRI-TS-042",
        "name": "Dr. K. Srinivas Rao",
        "role": "extension_officer",
        "district": "Warangal",
        "mandals": ["Geesugonda", "Wardhannapet", "Atmakur"]
    }
    return create_access_token(data=payload)

@pytest.fixture
def admin_token(db_session):
    user = db_session.query(User).filter(User.id == "user-test-admin-01").first()
    if not user:
        user = User(
            id="user-test-admin-01",
            phone_number="9999999999",
            full_name="State Agriculture Administrator",
            role="admin",
            preferred_lang="te",
            is_active=True,
            is_verified=True
        )
        db_session.add(user)
        db_session.commit()
    payload = {
        "sub": "user-test-admin-01",
        "phone": "9999999999",
        "name": "State Agriculture Administrator",
        "role": "admin"
    }
    return create_access_token(data=payload)
