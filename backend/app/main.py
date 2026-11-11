from contextlib import asynccontextmanager
from pathlib import Path
from fastapi import FastAPI, Request, Response, status
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse, HTMLResponse
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

    # Start automated Mandi price background scheduler (~12 hours)
    from app.services.mandi_scheduler import mandi_scheduler
    try:
        await mandi_scheduler.start()
        logger.info("Mandi price sync scheduler started in lifespan.")
    except Exception as e:
        logger.error(f"Error starting mandi scheduler: {e}")

    yield

    # Shutdown background scheduler gracefully
    try:
        await mandi_scheduler.stop()
        logger.info("Mandi price sync scheduler stopped gracefully.")
    except Exception as e:
        logger.error(f"Error stopping mandi scheduler: {e}")

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
    is_vision_ready = False
    is_text_ready = False
    try:
        from app.ai.gemini_service import gemini_service
        is_client_ready = gemini_service.client is not None
        is_vision_ready = gemini_service.is_vision_available()
        is_text_ready = gemini_service.is_text_available()
    except Exception:
        is_client_ready = False
        is_vision_ready = False
        is_text_ready = False

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
        "backend_api": "connected",
        "online_model": settings.GEMINI_MODEL,
        "environment": settings.ENVIRONMENT,
        "database": {
            "status": db_status,
            "engine": db_engine,
            "error": db_error
        },
        "ai_vision": "available" if is_vision_ready else "unavailable",
        "gemini_text": "available" if is_text_ready else "unavailable",
        "gemini_vision_available": is_vision_ready,
        "ai_provider": {
            "provider": "Google Gemini",
            "model": settings.GEMINI_MODEL,
            "is_configured": is_key_configured,
            "gemini_configured": is_key_configured,
            "client_ready": is_client_ready,
            "gemini_text_available": is_text_ready,
            "gemini_vision_available": is_vision_ready,
            "ai_vision": "available" if is_vision_ready else "unavailable",
            "gemini_text": "available" if is_text_ready else "unavailable",
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

@app.get("/sms-demo", response_class=HTMLResponse, tags=["SMS Fallback (Mock Demo)"])
async def sms_demo_page():
    """
    Serves interactive HTML preview simulator for rural 2G/EDGE SMS fallback gateway.
    """
    html_file = Path(__file__).resolve().parent / "templates" / "sms_demo.html"
    if html_file.exists():
        return HTMLResponse(content=html_file.read_text(encoding="utf-8"))
    return HTMLResponse(content="<h1>Bol Kisan SMS Fallback Simulator</h1><p>Demo template not found.</p>", status_code=200)


# Private uploads directory ensured (served exclusively through authenticated API endpoints)
uploads_dir = Path(__file__).resolve().parent / "static" / "uploads" / "diseases"
uploads_dir.mkdir(parents=True, exist_ok=True)

app.include_router(api_router, prefix=settings.API_V1_STR)
# Backwards-compatible route alias for nested prefixes (e.g., Android /api/v1/api/v1/...)
app.include_router(api_router, prefix=f"{settings.API_V1_STR}{settings.API_V1_STR}", include_in_schema=False)
