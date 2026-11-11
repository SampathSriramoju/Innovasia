"""
FastAPI Router for AGMARKNET Mandi Market Prices and APMC Commodity Rates.
"""

from datetime import datetime
from typing import Optional, List, Dict, Any
from pydantic import BaseModel
from fastapi import APIRouter, Query, HTTPException, status, Depends
from sqlalchemy.orm import Session
from app.database.session import get_db
from app.services.mandi_price_service import mandi_price_service
from app.integrations.mandi import (
    mandi_service,
    MandiPricesResponse,
    CommodityPriceSummary
)

class MandiSyncStatusResponse(BaseModel):
    status: str = "healthy"
    last_synced_at: str
    total_records: int
    data_source: str
    is_fallback: bool

router = APIRouter()
market_prices_router = APIRouter()



@router.get(
    "/prices",
    response_model=MandiPricesResponse,
    summary="Get APMC Mandi Market Prices from AGMARKNET"
)
async def get_mandi_prices(
    commodity: Optional[str] = Query(None, description="Crop name (e.g. Cotton, Paddy, Chilli, Tomato, Wheat, Maize, Soybean, Chickpea)"),
    district: Optional[str] = Query(None, description="District filter"),
    state: Optional[str] = Query(None, description="State filter"),
    market: Optional[str] = Query(None, description="APMC market name filter"),
    limit: int = Query(50, ge=1, le=200, description="Maximum number of records to return")
):
    """
    Fetches real APMC mandi market price records from AGMARKNET:
    - Commodity, Variety, APMC Market, District, State
    - Daily arrival volume (in quintals)
    - Minimum, Maximum, and Modal Price (INR per quintal)
    - Minimum Support Price (MSP) comparison and price trend
    - Seamless fallback to authentic verified market dataset on network timeout
    """
    try:
        return await mandi_service.get_mandi_prices(
            commodity=commodity,
            district=district,
            state=state,
            market=market,
            limit=limit
        )
    except Exception as e:
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail=f"Unable to fetch mandi prices: {str(e)}"
        )


@router.get(
    "/commodity/{commodity_name}",
    response_model=CommodityPriceSummary,
    summary="Get Detailed Commodity Price Summary & APMC Market Comparisons"
)
async def get_commodity_price_summary(
    commodity_name: str,
    district: Optional[str] = Query(None, description="Optional district filter"),
    state: Optional[str] = Query(None, description="Optional state filter")
):
    """
    Provides aggregated price intelligence for a major crop:
    - Average modal price across all active APMC mandis
    - Price range (lowest min price to highest max price)
    - Total arrivals across markets
    - Comparison against Government Minimum Support Price (MSP)
    - Top performing APMC market and farmer marketing advisory
    """
    try:
        return await mandi_service.get_commodity_price_summary(
            commodity_name=commodity_name,
            district=district,
            state=state
        )
    except Exception as e:
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail=f"Unable to retrieve commodity price summary: {str(e)}"
        )


@router.get(
    "/commodities",
    summary="List all tracked commodities with MSP status"
)
async def list_tracked_commodities():
    """
    Returns all agricultural commodities actively monitored in the APMC mandi integration.
    """
    commodities = mandi_service.list_available_commodities()
    return {
        "count": len(commodities),
        "commodities": commodities
    }


@router.get(
    "/sync-status",
    response_model=MandiSyncStatusResponse,
    summary="Get Mandi Prices Synchronization Status"
)
async def get_mandi_sync_status():
    prices = await mandi_service.get_mandi_prices(limit=200)
    return MandiSyncStatusResponse(
        status="healthy",
        last_synced_at=datetime.utcnow().isoformat() + "Z",
        total_records=prices.total_records,
        data_source=prices.data_source,
        is_fallback=prices.is_fallback
    )


@market_prices_router.get(
    "",
    summary="Get APMC Mandi Market Prices from AGMARKNET (Direct Endpoint)"
)
async def get_market_prices(
    commodity: Optional[str] = Query(None, description="Crop name"),
    district: Optional[str] = Query(None, description="District filter"),
    state: Optional[str] = Query(None, description="State filter"),
    market: Optional[str] = Query(None, description="APMC market name filter"),
    variety: Optional[str] = Query(None, description="Variety filter"),
    page: int = Query(1, ge=1, description="Page number"),
    offset: Optional[int] = Query(None, ge=0, description="Offset for pagination"),
    limit: int = Query(50, ge=1, le=200, description="Maximum number of records to return"),
    db: Session = Depends(get_db)
):
    # Query database first
    res = mandi_price_service.get_market_prices(
        state=state,
        district=district,
        market=market,
        commodity=commodity,
        variety=variety,
        page=page,
        offset=offset,
        limit=limit,
        db=db
    )
    # If no records in database, fallback to authentic verified dataset
    if res["total"] == 0:
        fallback_res = await mandi_service.get_mandi_prices(
            commodity=commodity,
            district=district,
            state=state,
            market=market,
            limit=limit
        )
        records = fallback_res.records
        if variety:
            records = [r for r in records if variety.lower() in r.variety.lower()]
        
        # Apply offset and limit to fallback records if needed
        calc_offset = offset if offset is not None else (page - 1) * limit
        paginated_recs = records[calc_offset : calc_offset + limit]

        return {
            "total": len(records),
            "total_records": len(records),
            "page": page,
            "limit": limit,
            "total_pages": (len(records) + limit - 1) // limit if records else 1,
            "data_source": fallback_res.data_source,
            "is_fallback": fallback_res.is_fallback,
            "as_of_date": fallback_res.as_of_date,
            "filters_applied": {
                "commodity": commodity,
                "district": district,
                "state": state,
                "market": market,
                "variety": variety,
                "limit": limit
            },
            "records": [r.dict() if hasattr(r, "dict") else r.model_dump() for r in paginated_recs]
        }

    return res


@market_prices_router.get(
    "/sync-status",
    summary="Get Mandi Prices Synchronization Status (Direct Endpoint)"
)
async def get_market_prices_sync_status(db: Session = Depends(get_db)):
    status_data = mandi_price_service.get_sync_status(db=db)
    status_str = "healthy" if status_data["status"] in ("SUCCESS", "IDLE") else status_data["status"].lower()
    return {
        "status": status_str,
        "sync_status": status_data["status"],
        "last_synced_at": status_data["last_synced_at"] or status_data["last_completed_at"] or datetime.utcnow().isoformat() + "Z",
        "total_records": status_data["total_records_count"],
        "total_records_count": status_data["total_records_count"],
        "data_source": "data.gov.in AGMARKNET PostgreSQL Store",
        "is_fallback": not status_data["cached_data_available"],
        "cached_data_available": status_data["cached_data_available"],
        "latest_arrival_date": status_data["latest_arrival_date"],
        "error_message": status_data["error_message"]
    }

