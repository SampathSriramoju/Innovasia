"""
FastAPI Router for IMD Agromet Weather Advisories and Agricultural Risk Alerts.
"""

from typing import Optional, List
from fastapi import APIRouter, Query, HTTPException, status
from app.integrations.weather import (
    weather_service,
    DistrictWeatherResponse,
    WeatherAdvisoryResponse
)

router = APIRouter()


@router.get(
    "/district/{district_name}",
    response_model=DistrictWeatherResponse,
    summary="Get 5-Day Agromet Forecast & Dynamic Risk Alerts by District"
)
async def get_district_weather(
    district_name: str,
    state: Optional[str] = Query(None, description="Optional State name (e.g. Telangana, Andhra Pradesh)")
):
    """
    Returns authentic IMD Agromet 5-day weather forecast, including:
    - Daily rainfall (mm), min/max temperature (°C), morning & evening relative humidity (%)
    - Dynamic Agricultural Risk Alerts (Fungal disease risk, spray delays, drift hazard, heat stress)
    - District crop stage advisories
    - Resilient offline fallback if IMD network is unavailable
    """
    try:
        return await weather_service.get_district_weather(district_name=district_name, state=state)
    except Exception as e:
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail=f"Unable to retrieve district weather data: {str(e)}"
        )


@router.get(
    "/advisory",
    response_model=WeatherAdvisoryResponse,
    summary="Get Targeted Agromet Advisory by Crop and District"
)
async def get_weather_advisory(
    district: Optional[str] = Query("Warangal", description="District name"),
    state: Optional[str] = Query(None, description="State name"),
    crop: Optional[str] = Query(None, description="Target crop name (e.g. Cotton, Paddy, Chilli, Tomato, Wheat)")
):
    """
    Retrieves practical agricultural weather advisories tailored to a specific crop:
    - Weather summary and active meteorological risk alerts
    - Recommended spraying, fertilizer, and irrigation schedules
    - Authentic AMFU / KVK plant protection measures
    """
    try:
        return await weather_service.get_agromet_advisory(district=district, state=state, crop=crop)
    except Exception as e:
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail=f"Unable to retrieve agromet advisory: {str(e)}"
        )


@router.get(
    "/districts",
    summary="List all supported agricultural districts with authentic Agromet bulletins"
)
async def list_supported_districts():
    """
    Returns the catalog of all districts covered by the IMD Agromet integration.
    """
    return {
        "count": len(weather_service.list_supported_districts()),
        "districts": weather_service.list_supported_districts()
    }
