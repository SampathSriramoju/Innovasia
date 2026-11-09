import logging
import re
import sys

class SensitiveDataRedactor(logging.Filter):
    """
    Redacts JWT tokens, authorization headers, OTP codes, and credentials from logs.
    """
    AUTH_HEADER_PATTERN = re.compile(r"(Bearer\s+)[A-Za-z0-9\-_\.]+", re.IGNORECASE)
    OTP_PATTERN = re.compile(r"(['\"]?otp['\"]?\s*[:=]\s*['\"]?)\d{4,8}(['\"]?)", re.IGNORECASE)
    TOKEN_PATTERN = re.compile(r"(['\"]?token['\"]?\s*[:=]\s*['\"]?)[A-Za-z0-9\-_\.]+(['\"]?)", re.IGNORECASE)
    SECRET_PATTERN = re.compile(r"(['\"]?(?:secret|password|api_key)['\"]?\s*[:=]\s*['\"]?)[^'\",\s]+(['\"]?)", re.IGNORECASE)

    def filter(self, record: logging.LogRecord) -> bool:
        if isinstance(record.msg, str):
            record.msg = self.AUTH_HEADER_PATTERN.sub(r"\1[REDACTED]", record.msg)
            record.msg = self.OTP_PATTERN.sub(r"\1[REDACTED_OTP]\2", record.msg)
            record.msg = self.TOKEN_PATTERN.sub(r"\1[REDACTED_TOKEN]\2", record.msg)
            record.msg = self.SECRET_PATTERN.sub(r"\1[REDACTED]\2", record.msg)
        return True

def setup_logging():
    if sys.platform == "win32" and hasattr(sys.stdout, "reconfigure"):
        try:
            sys.stdout.reconfigure(encoding="utf-8", errors="replace")
        except Exception:
            pass

    handler = logging.StreamHandler(sys.stdout)
    handler.addFilter(SensitiveDataRedactor())
    logging.basicConfig(
        level=logging.INFO,
        format="%(asctime)s [%(levelname)s] [%(name)s]: %(message)s",
        handlers=[handler]
    )

logger = logging.getLogger("bolkisan")
logger.addFilter(SensitiveDataRedactor())
