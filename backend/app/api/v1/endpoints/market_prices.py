"""
FastAPI Router for PostgreSQL-backed AGMARKNET Mandi Market Prices.
Provides filtered querying, pagination, sync freshness monitoring,
and manual/admin synchronization triggers.
"""

from typing import Optional, List, Dict, Any
from fastapi import APIRouter, Depends, Query, HTTPException, status
from pydantic import BaseModel, Field
from sqlalchemy.orm import Session

from app.database.session import get_db
from app.services.mandi_price_service import mandi_price_service
from app.services.mandi_scheduler import mandi_scheduler

router = APIRouter()


# -----------------------------------------------------------------------------
# Pydantic Response & Request Models
# -----------------------------------------------------------------------------

class MandiRecordItem(BaseModel):
    id: str
    state: str
    district: str
    market: str
    commodity: str
    variety: Optional[str] = "Standard"
    grade: Optional[str] = "FAQ"
    arrival_date: str
    min_price: float
    max_price: float
    modal_price: float
    arrival_quantity_tonnes: float = 0.0
    source_portal: str
    last_synced_at: Optional[str] = None


class MarketPricesResponse(BaseModel):
    total: int
    page: int
    limit: int
    total_pages: int
    data_source: str
    latest_arrival_date: Optional[str] = None
    last_synced_at: Optional[str] = None
    records: List[MandiRecordItem]


class SyncStatusResponse(BaseModel):
    status: str
    last_sync_type: Optional[str] = None
    last_started_at: Optional[str] = None
    last_completed_at: Optional[str] = None
    last_successful_sync: Optional[str] = None
    latest_arrival_date: Optional[str] = None
    last_synced_at: Optional[str] = None
    total_records_count: int
    total_records: int = 0
    cached_data_available: bool
    error_message: Optional[str] = None


class SyncTriggerRequest(BaseModel):
    state: Optional[str] = Field(None, description="Optional state filter for sync")
    district: Optional[str] = Field(None, description="Optional district filter for sync")
    commodity: Optional[str] = Field(None, description="Optional commodity name filter for sync")


class SyncTriggerResponse(BaseModel):
    status: str
    message: str
    details: Optional[Dict[str, Any]] = None


# -----------------------------------------------------------------------------
# Endpoints
# -----------------------------------------------------------------------------

@router.get(
    "",
    response_model=MarketPricesResponse,
    summary="Get APMC Mandi Market Prices from PostgreSQL Cache"
)
async def get_market_prices(
    state: Optional[str] = Query(None, description="Filter by state (e.g. Telangana, Andhra Pradesh)"),
    district: Optional[str] = Query(None, description="Filter by district (e.g. Warangal, Adilabad)"),
    market: Optional[str] = Query(None, description="Filter by APMC market name"),
    commodity: Optional[str] = Query(None, description="Filter by commodity (e.g. Cotton, Paddy, Chilli)"),
    variety: Optional[str] = Query(None, description="Filter by crop variety"),
    page: int = Query(1, ge=1, description="Page number (1-indexed)"),
    limit: int = Query(50, ge=1, le=200, description="Items per page"),
    db: Session = Depends(get_db)
):
    """
    Retrieves synchronized Mandi commodity rates from PostgreSQL cache.
    Includes arrival dates, price ranges (min/max/modal), quality grades, and source freshness.
    """
    try:
        return mandi_price_service.get_market_prices(
            state=state,
            district=district,
            market=market,
            commodity=commodity,
            variety=variety,
            page=page,
            limit=limit,
            db=db
        )
    except Exception as exc:
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail=f"Failed to query market prices: {str(exc)}"
        )


@router.get(
    "/sync-status",
    response_model=SyncStatusResponse,
    summary="Get Mandi Sync & PostgreSQL Cache Freshness Status"
)
async def get_sync_status(db: Session = Depends(get_db)):
    """
    Returns sync telemetry including:
    - Status of the latest sync (SUCCESS, IN_PROGRESS, FAILED, IDLE)
    - Last successful sync timestamp
    - Latest commodity arrival date in the database
    - Total record count cached in PostgreSQL
    """
    try:
        return mandi_price_service.get_sync_status(db=db)
    except Exception as exc:
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail=f"Failed to retrieve sync status: {str(exc)}"
        )


@router.post(
    "/sync",
    response_model=SyncTriggerResponse,
    summary="Trigger Manual / Admin Mandi Price Synchronization"
)
async def trigger_manual_sync(
    payload: Optional[SyncTriggerRequest] = None,
    db: Session = Depends(get_db)
):
    """
    Triggers an immediate synchronization from data.gov.in AGMARKNET.
    Protected by single-instance mutex lock to prevent concurrent sync collisions.
    """
    try:
        state = payload.state if payload else None
        district = payload.district if payload else None
        commodity = payload.commodity if payload else None

        if mandi_scheduler.is_locked:
            return SyncTriggerResponse(
                status="ALREADY_RUNNING",
                message="A Mandi price synchronization job is already running in background.",
                details=None
            )

        sync_result = await mandi_price_service.sync_mandi_prices(
            state=state,
            district=district,
            commodity=commodity,
            sync_type="manual",
            db=db
        )

        return SyncTriggerResponse(
            status=sync_result.get("status", "SUCCESS"),
            message="Mandi price synchronization completed successfully." if sync_result.get("status") == "SUCCESS" else "Mandi sync failed.",
            details=sync_result
        )
    except Exception as exc:
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail=f"Manual sync failed: {str(exc)}"
        )
