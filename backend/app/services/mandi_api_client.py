"""
AGMARKNET / Data.gov.in Mandi Dataset API Client.

Integrates with:
- Resource ID: 9ef84268-d588-465a-a308-a864a43d0070
- Base URL: https://api.data.gov.in/resource/9ef84268-d588-465a-a308-a864a43d0070
- Environment variable: DATA_GOV_IN_API_KEY
- Fields: state, district, market, commodity, variety, grade, arrival_date, min_price, max_price, modal_price
"""

import asyncio
import logging
import os
import random
import re
from datetime import datetime
from typing import Any, Dict, List, Optional, Tuple, Union

import httpx
from pydantic import BaseModel, Field, field_validator

logger = logging.getLogger(__name__)

# Resource configuration
DEFAULT_RESOURCE_ID = "9ef84268-d588-465a-a308-a864a43d0070"
DEFAULT_BASE_URL = f"https://api.data.gov.in/resource/{DEFAULT_RESOURCE_ID}"
DEFAULT_USER_AGENT = "BolKisan-MandiClient/1.0 (Agricultural AI Assistant; contact@bolkisan.gov.in)"
DEFAULT_TIMEOUT_SECONDS = 35.0
DEFAULT_MAX_RETRIES = 3
DEFAULT_BACKOFF_FACTOR = 1.5


# ============================================================================
# Exceptions
# ============================================================================

class MandiAPIError(Exception):
    """Base exception for all Mandi API client errors."""

    def __init__(self, message: str, status_code: Optional[int] = None, details: Optional[Any] = None):
        super().__init__(message)
        self.message = message
        self.status_code = status_code
        self.details = details


class MandiAPIAuthError(MandiAPIError):
    """Raised when API key is missing, invalid, or unauthorized (401, 403)."""
    pass


class MandiAPIRateLimitError(MandiAPIError):
    """Raised when data.gov.in rate limit is exceeded (HTTP 429)."""
    pass


class MandiAPITimeoutError(MandiAPIError):
    """Raised when external connection or read times out."""
    pass


class MandiAPIServerError(MandiAPIError):
    """Raised when data.gov.in returns 5xx server errors."""
    pass


class MandiAPIResponseError(MandiAPIError):
    """Raised when response body cannot be parsed or lacks expected structure."""
    pass


# ============================================================================
# Data Models
# ============================================================================

class AgmarknetPriceRecord(BaseModel):
    """
    Validated APMC Mandi commodity market record from data.gov.in.
    """
    state: str = Field(..., description="State name (e.g., Telangana, Andhra Pradesh)")
    district: str = Field(..., description="District name (e.g., Warangal, NTR)")
    market: str = Field(..., description="APMC Market name (e.g., Warangal APMC)")
    commodity: str = Field(..., description="Commodity name (e.g., Cotton, Paddy)")
    variety: str = Field(default="Other", description="Crop variety")
    grade: str = Field(default="FAQ", description="Commodity quality grade (FAQ, Non-FAQ, etc.)")
    arrival_date: str = Field(..., description="Arrival date normalized to YYYY-MM-DD")
    raw_arrival_date: Optional[str] = Field(None, description="Original raw date string from API")
    min_price: float = Field(..., description="Minimum modal price (INR / quintal)")
    max_price: float = Field(..., description="Maximum modal price (INR / quintal)")
    modal_price: float = Field(..., description="Modal (most frequent) price (INR / quintal)")

    @field_validator("state", "district", "market", "commodity", "variety", "grade", mode="before")
    @classmethod
    def sanitize_string_fields(cls, v: Any) -> str:
        if v is None:
            return ""
        return str(v).strip()

    @field_validator("min_price", "max_price", "modal_price", mode="before")
    @classmethod
    def parse_numeric_price(cls, v: Any) -> float:
        if v is None or v == "":
            raise ValueError("Price field cannot be empty")
        if isinstance(v, (int, float)):
            return float(v)
        if isinstance(v, str):
            cleaned = re.sub(r"[^\d.]", "", v.strip())
            if not cleaned:
                raise ValueError(f"Invalid numeric price string: '{v}'")
            return float(cleaned)
        raise ValueError(f"Unsupported price type: {type(v)}")

    @classmethod
    def normalize_arrival_date(cls, date_str: Any) -> Tuple[str, str]:
        """
        Normalizes various date formats to ISO YYYY-MM-DD.
        Data.gov.in commonly returns DD/MM/YYYY or DD-MM-YYYY.
        """
        raw = str(date_str).strip() if date_str is not None else ""
        if not raw:
            today_iso = datetime.now().date().isoformat()
            return today_iso, raw

        formats = [
            "%d/%m/%Y",  # Standard data.gov.in format (e.g., 09/11/2026)
            "%d-%m-%Y",
            "%Y-%m-%d",  # ISO format
            "%Y/%m/%d",
            "%d.%m.%Y",
            "%m/%d/%Y",
        ]
        for fmt in formats:
            try:
                dt = datetime.strptime(raw, fmt)
                return dt.date().isoformat(), raw
            except ValueError:
                continue

        logger.warning("Could not parse arrival_date '%s', falling back to today", raw)
        return datetime.now().date().isoformat(), raw


class MandiAPIResponse(BaseModel):
    """
    Standardized response container for data.gov.in Mandi queries.
    """
    success: bool = True
    total: int = Field(0, description="Total matching records on server")
    count: int = Field(0, description="Number of records returned in this page")
    limit: int = Field(0, description="Limit applied")
    offset: int = Field(0, description="Offset applied")
    status: Optional[str] = Field(None, description="data.gov.in status field")
    updated_date: Optional[str] = Field(None, description="Dataset last update date")
    records: List[AgmarknetPriceRecord] = Field(default_factory=list, description="Validated price records")
    raw_record_count: int = Field(0, description="Total records before validation/filtering")


# ============================================================================
# Mandi API Client
# ============================================================================

class MandiApiClient:
    """
    Production client for the data.gov.in AGMARKNET Mandi Price Dataset API.
    
    Features:
    - Dedicated authentication via `api-key` parameter.
    - Strictly loads API key from `DATA_GOV_IN_API_KEY` env var (or optional param). Never hardcoded.
    - Default timeout of 35 seconds and realistic browser/app User-Agent header.
    - Retry logic with exponential backoff and jitter for transient errors.
    - Strict response validation, date normalization, and price sanitization.
    - Safe filtering by state, district, commodity, market, and custom filters.
    """

    def __init__(
        self,
        api_key: Optional[str] = None,
        base_url: str = DEFAULT_BASE_URL,
        timeout: float = DEFAULT_TIMEOUT_SECONDS,
        max_retries: int = DEFAULT_MAX_RETRIES,
        backoff_factor: float = DEFAULT_BACKOFF_FACTOR,
        user_agent: str = DEFAULT_USER_AGENT,
    ):
        # Key MUST be read exclusively from environment variable if not explicitly passed
        self._explicit_api_key = api_key
        self.base_url = base_url.rstrip("/")
        self.timeout = timeout
        self.max_retries = max_retries
        self.backoff_factor = backoff_factor
        self.user_agent = user_agent
        self._async_client: Optional[httpx.AsyncClient] = None

    @property
    def api_key(self) -> Optional[str]:
        """Dynamically retrieves API key from explicit param, os.environ, or core settings."""
        if self._explicit_api_key is not None:
            return self._explicit_api_key
        key = os.getenv("DATA_GOV_IN_API_KEY")
        if key and key.strip():
            return key.strip()
        try:
            from app.core.config import settings
            if settings.DATA_GOV_IN_API_KEY:
                return settings.DATA_GOV_IN_API_KEY.strip()
        except Exception:
            pass
        return None

    @api_key.setter
    def api_key(self, value: Optional[str]) -> None:
        self._explicit_api_key = value

    @property
    def has_api_key(self) -> bool:
        """Returns True if a non-empty API key is present."""
        return bool(self.api_key and self.api_key.strip())

    def _get_headers(self) -> Dict[str, str]:
        return {
            "User-Agent": self.user_agent,
            "Accept": "application/json",
            "Connection": "keep-alive",
        }

    def _build_params(
        self,
        limit: int = 50,
        offset: int = 0,
        state: Optional[str] = None,
        district: Optional[str] = None,
        commodity: Optional[str] = None,
        market: Optional[str] = None,
        filters: Optional[Dict[str, str]] = None,
    ) -> Dict[str, Any]:
        """
        Builds the query parameter dictionary formatted for data.gov.in API.
        Enforces limit bounds (1 <= limit <= 1000) and offset >= 0.
        """
        if not self.has_api_key:
            raise MandiAPIAuthError(
                "DATA_GOV_IN_API_KEY is not configured. Set the environment variable DATA_GOV_IN_API_KEY."
            )

        clamped_limit = max(1, min(int(limit), 1000))
        clamped_offset = max(0, int(offset))

        params: Dict[str, Any] = {
            "api-key": self.api_key.strip(),
            "format": "json",
            "limit": clamped_limit,
            "offset": clamped_offset,
        }

        # Targeted filters
        if state and state.strip():
            params["filters[state]"] = state.strip()
        if district and district.strip():
            params["filters[district]"] = district.strip()
        if commodity and commodity.strip():
            params["filters[commodity]"] = commodity.strip()
        if market and market.strip():
            params["filters[market]"] = market.strip()

        # Custom arbitrary filters
        if filters:
            for k, v in filters.items():
                if v and str(v).strip():
                    key = k if k.startswith("filters[") else f"filters[{k}]"
                    params[key] = str(v).strip()

        return params

    def _parse_records(self, raw_records: List[Dict[str, Any]]) -> List[AgmarknetPriceRecord]:
        """
        Parses and validates raw records from data.gov.in.
        Corrupted or non-parseable records are logged as warnings and skipped,
        ensuring downstream stability.
        """
        valid_records: List[AgmarknetPriceRecord] = []
        for idx, item in enumerate(raw_records):
            if not isinstance(item, dict):
                logger.warning("Skipping non-dict record at index %d: %s", idx, item)
                continue

            try:
                # Required core fields
                state = item.get("state") or item.get("State")
                district = item.get("district") or item.get("District")
                market = item.get("market") or item.get("Market")
                commodity = item.get("commodity") or item.get("Commodity")

                if not (state and district and market and commodity):
                    logger.debug("Skipping record %d due to missing required location/crop identifiers", idx)
                    continue

                # Variety & Grade
                variety = item.get("variety") or item.get("Variety") or "Other"
                grade = item.get("grade") or item.get("Grade") or "FAQ"

                # Date parsing & normalization
                raw_date = item.get("arrival_date") or item.get("Arrival_Date") or item.get("arrival_date_str")
                norm_date, raw_date_str = AgmarknetPriceRecord.normalize_arrival_date(raw_date)

                # Prices
                min_p = item.get("min_price") if item.get("min_price") is not None else item.get("Min_Price")
                max_p = item.get("max_price") if item.get("max_price") is not None else item.get("Max_Price")
                modal_p = item.get("modal_price") if item.get("modal_price") is not None else item.get("Modal_Price")

                # If modal price is missing but min/max exist, estimate modal as midpoint
                if modal_p is None and min_p is not None and max_p is not None:
                    try:
                        modal_p = (float(min_p) + float(max_p)) / 2.0
                    except (ValueError, TypeError):
                        pass

                rec = AgmarknetPriceRecord(
                    state=str(state),
                    district=str(district),
                    market=str(market),
                    commodity=str(commodity),
                    variety=str(variety),
                    grade=str(grade),
                    arrival_date=norm_date,
                    raw_arrival_date=raw_date_str,
                    min_price=min_p,
                    max_price=max_p,
                    modal_price=modal_p,
                )
                valid_records.append(rec)

            except (ValueError, TypeError) as exc:
                logger.warning("Validation failed for record %d (%s): %s", idx, item, exc)
                continue

        return valid_records

    async def _get_client(self) -> httpx.AsyncClient:
        import asyncio
        current_loop = asyncio.get_running_loop()
        if (
            self._async_client is None
            or self._async_client.is_closed
            or getattr(self, "_client_loop", None) != current_loop
        ):
            self._client_loop = current_loop
            self._async_client = httpx.AsyncClient(
                headers=self._get_headers(),
                timeout=httpx.Timeout(self.timeout, connect=15.0),
                follow_redirects=True,
            )
        return self._async_client

    async def close(self) -> None:
        """Closes the underlying HTTP client session."""
        if self._async_client is not None and not self._async_client.is_closed:
            await self._async_client.aclose()
            self._async_client = None

    async def __aenter__(self) -> "MandiApiClient":
        return self

    async def __aexit__(self, exc_type, exc_val, exc_tb) -> None:
        await self.close()

    async def fetch_prices(
        self,
        limit: int = 50,
        offset: int = 0,
        state: Optional[str] = None,
        district: Optional[str] = None,
        commodity: Optional[str] = None,
        market: Optional[str] = None,
        filters: Optional[Dict[str, str]] = None,
    ) -> MandiAPIResponse:
        """
        Fetches commodity price records from data.gov.in asynchronously.
        Implements exponential backoff retries for transient errors.
        """
        params = self._build_params(
            limit=limit,
            offset=offset,
            state=state,
            district=district,
            commodity=commodity,
            market=market,
            filters=filters,
        )

        client = await self._get_client()
        last_exception: Optional[Exception] = None

        for attempt in range(1, self.max_retries + 1):
            try:
                logger.debug(
                    "Querying data.gov.in Mandi API (attempt %d/%d): filters=(state=%s, district=%s, commodity=%s)",
                    attempt,
                    self.max_retries,
                    state,
                    district,
                    commodity,
                )

                response = await client.get(self.base_url, params=params)

                # HTTP Status Validation
                if response.status_code == 200:
                    try:
                        data = response.json()
                    except Exception as json_err:
                        raise MandiAPIResponseError(
                            f"Failed to parse JSON response from data.gov.in: {json_err}",
                            status_code=200,
                            details=response.text[:500],
                        )

                    if not isinstance(data, dict):
                        raise MandiAPIResponseError("Unexpected response format: top-level object is not a JSON dict")

                    # Handle data.gov.in error payload wrapped in 200 OK
                    if data.get("status") == "failed" or data.get("status") == "error":
                        msg = data.get("message") or "Unknown error reported by data.gov.in"
                        raise MandiAPIError(f"data.gov.in API returned error: {msg}", details=data)

                    raw_records = data.get("records") or []
                    parsed_records = self._parse_records(raw_records)

                    return MandiAPIResponse(
                        success=True,
                        total=int(data.get("total") or len(parsed_records)),
                        count=len(parsed_records),
                        limit=int(data.get("limit") or limit),
                        offset=int(data.get("offset") or offset),
                        status=data.get("status"),
                        updated_date=data.get("updated_date"),
                        records=parsed_records,
                        raw_record_count=len(raw_records),
                    )

                elif response.status_code in (401, 403):
                    # Authentication failure: do NOT retry
                    raise MandiAPIAuthError(
                        f"Authentication failed with status {response.status_code}: {response.text[:300]}",
                        status_code=response.status_code,
                    )

                elif response.status_code == 429:
                    # Rate limit exceeded: retry with backoff if attempts remaining
                    logger.warning("Mandi API rate limit (429) hit on attempt %d/%d", attempt, self.max_retries)
                    if attempt == self.max_retries:
                        raise MandiAPIRateLimitError(
                            "Rate limit exceeded on data.gov.in Mandi API after retries.",
                            status_code=429,
                        )

                elif response.status_code in (500, 502, 503, 504):
                    logger.warning(
                        "data.gov.in server error %d on attempt %d/%d",
                        response.status_code,
                        attempt,
                        self.max_retries,
                    )
                    if attempt == self.max_retries:
                        raise MandiAPIServerError(
                            f"data.gov.in server error {response.status_code}: {response.text[:300]}",
                            status_code=response.status_code,
                        )

                else:
                    raise MandiAPIError(
                        f"Unexpected HTTP status {response.status_code}: {response.text[:300]}",
                        status_code=response.status_code,
                    )

            except (httpx.TimeoutException, httpx.ConnectTimeout, httpx.ReadTimeout) as timeout_exc:
                logger.warning("Mandi API timeout on attempt %d/%d: %s", attempt, self.max_retries, timeout_exc)
                last_exception = MandiAPITimeoutError(
                    f"Connection to data.gov.in timed out after {self.timeout}s: {timeout_exc}"
                )
                if attempt == self.max_retries:
                    raise last_exception

            except (httpx.NetworkError, httpx.RemoteProtocolError) as net_exc:
                logger.warning("Mandi API network error on attempt %d/%d: %s", attempt, self.max_retries, net_exc)
                last_exception = MandiAPIError(f"Network error querying data.gov.in: {net_exc}")
                if attempt == self.max_retries:
                    raise last_exception

            except (MandiAPIAuthError, MandiAPIResponseError):
                # Non-retryable errors
                raise

            except MandiAPIError:
                if attempt == self.max_retries:
                    raise

            # Sleep with exponential backoff and jitter before retrying
            backoff_sleep = (self.backoff_factor ** attempt) + random.uniform(0.1, 0.5)
            logger.info("Retrying Mandi API in %.2f seconds...", backoff_sleep)
            await asyncio.sleep(backoff_sleep)

        if last_exception:
            raise last_exception
        raise MandiAPIError("Mandi API query failed after maximum retry attempts")

    def fetch_prices_sync(
        self,
        limit: int = 50,
        offset: int = 0,
        state: Optional[str] = None,
        district: Optional[str] = None,
        commodity: Optional[str] = None,
        market: Optional[str] = None,
        filters: Optional[Dict[str, str]] = None,
    ) -> MandiAPIResponse:
        """
        Synchronous wrapper around fetch_prices for scripts, CLI utilities,
        or synchronous worker contexts.
        """
        return asyncio.run(
            self.fetch_prices(
                limit=limit,
                offset=offset,
                state=state,
                district=district,
                commodity=commodity,
                market=market,
                filters=filters,
            )
        )


# Singleton instance configured with standard environment settings
mandi_api_client = MandiApiClient()
