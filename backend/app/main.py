from pathlib import Path
from fastapi import FastAPI, Request, status
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse
from fastapi.staticfiles import StaticFiles
from app.core.config import settings
from app.core.logging import setup_logging, logger
from app.api.v1.router import api_router
from app.database.session import init_db

setup_logging()

app = FastAPI(
    title=settings.PROJECT_NAME,
    description="Multilingual AI-Powered Agricultural Voice Assistant & Extension Operations Platform",
    version="1.0.0",
    docs_url="/docs",
    redoc_url="/redoc",
    openapi_url="/openapi.json"
)

# CORS Configuration
app.add_middleware(
    CORSMiddleware,
    allow_origins=settings.cors_origins,
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

@app.on_event("startup")
async def startup_event():
    logger.info("Initializing Bol Kisan Database schema...")
    try:
        init_db()
        logger.info("Database tables initialized successfully.")
    except Exception as e:
        logger.error(f"Error during database initialization: {e}")

@app.get("/health", tags=["System"])
async def health_check():
    return {
        "status": "healthy",
        "service": "Bol Kisan Backend API",
        "version": "1.0.0",
        "online_model": settings.GEMINI_MODEL,
        "safety_gate": "active_cibrc_enforcement"
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

# Mount static files directory for disease diagnosis uploads
static_dir = Path(__file__).resolve().parent / "static"
static_dir.mkdir(parents=True, exist_ok=True)
app.mount("/static", StaticFiles(directory=str(static_dir)), name="static")

app.include_router(api_router, prefix=settings.API_V1_STR)
