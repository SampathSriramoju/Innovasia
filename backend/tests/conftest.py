import pytest
from fastapi.testclient import TestClient
from sqlalchemy import create_engine
from sqlalchemy.orm import sessionmaker
from app.database.base import Base
from app.database.session import get_db
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
def farmer_token():
    payload = {
        "sub": "user-test-farmer-01",
        "phone": "9876543210",
        "name": "Test Farmer",
        "role": "farmer",
        "lang": "te"
    }
    return create_access_token(data=payload)

@pytest.fixture
def officer_token():
    payload = {
        "sub": "user-test-officer-01",
        "officer_code": "AGRI-TS-042",
        "name": "Dr. K. Srinivas Rao",
        "role": "extension_officer",
        "district": "Warangal",
        "mandals": ["Geesugonda", "Wardhannapet"]
    }
    return create_access_token(data=payload)

@pytest.fixture
def admin_token():
    payload = {
        "sub": "user-test-admin-01",
        "phone": "9999999999",
        "name": "State Agriculture Administrator",
        "role": "admin"
    }
    return create_access_token(data=payload)
