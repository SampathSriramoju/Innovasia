"""
FastAPI Router for AGMARKNET Mandi Market Prices and APMC Commodity Rates.
"""

from typing import Optional, List
from fastapi import APIRouter, Query, HTTPException, status
from app.integrations.mandi import (
    mandi_service,
    MandiPricesResponse,
    CommodityPriceSummary
)

router = APIRouter()


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
