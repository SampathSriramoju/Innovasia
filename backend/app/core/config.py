from typing import List, Optional
from pydantic_settings import BaseSettings, SettingsConfigDict
from pydantic import Field, model_validator

class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=".env", env_file_encoding="utf-8", extra="ignore")

    PROJECT_NAME: str = "Bol Kisan Agricultural AI Assistant"
    API_V1_STR: str = "/api/v1"
    ENVIRONMENT: str = "development"
    DEBUG: bool = True
    PORT: int = 8000
    HOST: str = "0.0.0.0"

    # Security
    SECRET_KEY: str = Field(default="bol-kisan-super-secret-key-change-in-production-32chars!", description="JWT secret key")
    ALGORITHM: str = "HS256"
    ACCESS_TOKEN_EXPIRE_MINUTES: int = 60 * 24 # 24 hours
    ALLOWED_ORIGINS: str = "*"

    # Development Demo Controls (Must strictly be False in production)
    ENABLE_DEV_OTP: bool = True

    # Database
    DATABASE_URL: str = "sqlite+aiosqlite:///./bolkisan.db"
    SYNC_DATABASE_URL: str = "sqlite:///./bolkisan.db"

    # AI & LLM
    GEMINI_API_KEY: Optional[str] = None
    GEMINI_MODEL: str = "gemini-3.8-flash"
    GEMINI_TEMPERATURE: float = 0.1
    GEMINI_MAX_OUTPUT_TOKENS: int = 1024

    # Speech Services
    BHASHINI_USER_ID: Optional[str] = None
    BHASHINI_API_KEY: Optional[str] = None
    BHASHINI_PIPELINE_ID: Optional[str] = None

    # Offline SLM Engine Evaluation Flags
    ENABLE_OFFLINE_LLM: bool = False
    OFFLINE_LLM_RUNTIME: str = "litert-lm" # or "llama.cpp"
    OFFLINE_LLM_MODEL_PATH: str = "./models/agriparam-q4.bin"
    MIN_FREE_RAM_MB: int = 1200

    # Safety
    ENFORCE_STRICT_CIBRC_DOSAGE: bool = True
    AUTO_ESCALATE_UNVERIFIED_QUERIES: bool = True
    MAX_ESCALATION_SLA_HOURS: int = 24
    URGENT_ESCALATION_SLA_HOURS: int = 4

    @model_validator(mode="after")
    def validate_production_security(self) -> "Settings":
        env = self.ENVIRONMENT.lower().strip()
        if env == "production":
            # 1. DEBUG must be False in production
            if self.DEBUG:
                raise ValueError("Security violation: DEBUG mode must be disabled (False) in production.")

            # 2. SECRET_KEY must be configured, non-default, and >= 32 chars
            default_key = "bol-kisan-super-secret-key-change-in-production-32chars!"
            if not self.SECRET_KEY or self.SECRET_KEY == default_key or "change-in-production" in self.SECRET_KEY.lower():
                raise ValueError(
                    "Security violation: SECRET_KEY is missing, unchanged from development default, or contains insecure placeholders."
                )
            if len(self.SECRET_KEY) < 32:
                raise ValueError("Security violation: SECRET_KEY must be at least 32 characters in production.")

            # 3. Explicit CORS origins required in production
            if self.ALLOWED_ORIGINS.strip() == "*":
                raise ValueError("Security violation: Wildcard ALLOWED_ORIGINS ('*') is prohibited in production. Configure explicit domains.")

            # 4. Dev OTP strictly disabled in production
            self.ENABLE_DEV_OTP = False

        return self

    @property
    def cors_origins(self) -> List[str]:
        if self.ALLOWED_ORIGINS == "*":
            return ["*"]
        return [origin.strip() for origin in self.ALLOWED_ORIGINS.split(",") if origin.strip()]

settings = Settings()
