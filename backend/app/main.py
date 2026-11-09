from contextlib import asynccontextmanager
from pathlib import Path
from fastapi import FastAPI, Request, Response, status
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse
from fastapi.staticfiles import StaticFiles
from app.core.config import settings
from app.core.logging import setup_logging, logger
from app.api.v1.router import api_router
from app.database.session import init_db

setup_logging()

@asynccontextmanager
async def lifespan(app: FastAPI):
    logger.info("Initializing Bol Kisan Database schema...")
    try:
        init_db()
        logger.info("Database tables initialized successfully.")
    except Exception as e:
        logger.error(f"Error during database initialization: {e}")
    yield

app = FastAPI(
    title=settings.PROJECT_NAME,
    description="Multilingual AI-Powered Agricultural Voice Assistant & Extension Operations Platform",
    version="1.0.0",
    docs_url="/docs",
    redoc_url="/redoc",
    openapi_url="/openapi.json",
    lifespan=lifespan
)

# CORS Configuration - W3C compliant
cors_origins = settings.cors_origins
allow_creds = False if "*" in cors_origins else True
app.add_middleware(
    CORSMiddleware,
    allow_origins=cors_origins,
    allow_credentials=allow_creds,
    allow_methods=["*"],
    allow_headers=["*"],
)

@app.get("/health", tags=["System"])
@app.get(f"{settings.API_V1_STR}/health", tags=["System"])
async def health_check(response: Response):
    # Database connectivity check
    db_status = "connected"
    db_engine = "sqlite" if "sqlite" in settings.SYNC_DATABASE_URL else "postgresql"
    db_error = None
    try:
        from sqlalchemy import text
        from app.database.session import engine
        with engine.connect() as conn:
            conn.execute(text("SELECT 1"))
    except Exception as e:
        db_status = "error"
        db_error = str(e)
        response.status_code = status.HTTP_503_SERVICE_UNAVAILABLE

    # AI Provider configuration status (distinguishes configured vs reachable, zero credentials exposed, no paid calls)
    gemini_key = settings.GEMINI_API_KEY
    is_key_configured = bool(
        gemini_key and len(gemini_key.strip()) > 8 and not gemini_key.startswith("your-") and "placeholder" not in gemini_key.lower()
    )

    is_client_ready = False
    try:
        from app.ai.gemini_service import gemini_service
        is_client_ready = gemini_service.client is not None
    except Exception:
        is_client_ready = False

    if is_client_ready:
        ai_status = "configured_and_ready"
    elif is_key_configured:
        ai_status = "key_present_client_uninitialized"
    else:
        ai_status = "unconfigured_icar_fallback"

    return {
        "status": "healthy" if db_status == "connected" else "degraded",
        "service": "Bol Kisan Backend API",
        "version": "1.0.0",
        "online_model": settings.GEMINI_MODEL,
        "environment": settings.ENVIRONMENT,
        "database": {
            "status": db_status,
            "engine": db_engine,
            "error": db_error
        },
        "ai_provider": {
            "provider": "Google Gemini",
            "model": settings.GEMINI_MODEL,
            "is_configured": is_key_configured,
            "client_ready": is_client_ready,
            "status": ai_status
        },
        "safety_gate": {
            "status": "active",
            "enforcement": "CIBRC statutory compliance + knapsack validation"
        }
    }

@app.get("/", tags=["System"])
async def root():
    return {
        "project": "BOL KISAN",
        "description": "Multilingual AI Agricultural Voice Assistant",
        "version": "1.0.0",
        "docs": "/docs",
        "api_v1": settings.API_V1_STR
    }

# Private uploads directory ensured (served exclusively through authenticated API endpoints)
uploads_dir = Path(__file__).resolve().parent / "static" / "uploads" / "diseases"
uploads_dir.mkdir(parents=True, exist_ok=True)

app.include_router(api_router, prefix=settings.API_V1_STR)
