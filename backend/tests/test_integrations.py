"""
Unit & Integration Tests for Agricultural Integrations:
1. IMD Agromet Weather Advisories & Risk Alerts
2. AGMARKNET Mandi Market Prices & Commodity Summaries
3. Regional Agricultural Extension Officer Directory
"""

import pytest
from app.integrations.weather import weather_service, WeatherIntegrationService
from app.integrations.mandi import mandi_service, MandiIntegrationService
from app.integrations.officers import officer_service


# =====================================================================
# Weather Integration Endpoint Tests
# =====================================================================

def test_get_district_weather_warangal(client):
    """Verify district weather endpoint returns 200 and complete 5-day forecast for Warangal."""
    res = client.get("/api/v1/weather/district/Warangal")
    assert res.status_code == 200
    data = res.json()

    assert data["district"] == "Warangal"
    assert data["state"] == "Telangana"
    assert "Central Telangana" in data["agro_climatic_zone"]
    assert data["is_fallback"] is True  # Authentic offline IMD AAS bulletin
    assert "IMD" in data["data_source"]

    # Current conditions
    assert "temperature_c" in data["current_conditions"]
    assert "humidity_pct" in data["current_conditions"]

    # 5-day forecast
    forecast = data["forecast_5day"]
    assert len(forecast) == 5
    for day in forecast:
        assert "date" in day
        assert "day_name" in day
        assert "rainfall_mm" in day
        assert "temp_max_c" in day
        assert "temp_min_c" in day
        assert "humidity_morning_pct" in day
        assert "wind_speed_kmh" in day
        assert "weather_condition" in day
        assert day["temp_max_c"] >= day["temp_min_c"]

    # Dynamic Agro Risk Alerts (Warangal humidity 88% triggers fungal risk; rain 24mm triggers spray delay)
    alerts = data["active_risk_alerts"]
    assert len(alerts) >= 1
    categories = [a["category"] for a in alerts]
    assert "fungal_disease" in categories or "spray_delay" in categories

    # Crop Stage Advisories
    crop_advisories = data["crop_advisories"]
    assert len(crop_advisories) >= 2
    crop_names = [ca["crop_name"] for ca in crop_advisories]
    assert "Cotton" in crop_names


def test_get_district_weather_all_states(client):
    """Verify authentic Agromet coverage across all 5 target states."""
    test_cases = [
        ("Warangal", "Telangana"),
        ("Guntur", "Andhra Pradesh"),
        ("Indore", "Madhya Pradesh"),
        ("Ludhiana", "Punjab"),
        ("Nashik", "Maharashtra"),
    ]

    for district, expected_state in test_cases:
        res = client.get(f"/api/v1/weather/district/{district}")
        assert res.status_code == 200
        data = res.json()
        assert data["district"].lower() == district.lower()
        assert data["state"] == expected_state
        assert len(data["forecast_5day"]) == 5
        assert len(data["crop_advisories"]) > 0


def test_get_weather_advisory_cotton_warangal(client):
    """Verify targeted crop agromet advisory endpoint."""
    res = client.get("/api/v1/weather/advisory?district=Warangal&crop=Cotton")
    assert res.status_code == 200
    data = res.json()

    assert data["district"] == "Warangal"
    assert data["crop"] == "Cotton"
    assert "weather_summary" in data
    assert len(data["risk_alerts"]) >= 1
    assert len(data["crop_specific_advisories"]) >= 1

    cotton_adv = data["crop_specific_advisories"][0]
    assert "Cotton" in cotton_adv["crop_name"]
    assert "plant_protection" in cotton_adv
    assert len(data["general_advisories"]) >= 1


def test_list_supported_weather_districts(client):
    """Verify catalog of supported districts."""
    res = client.get("/api/v1/weather/districts")
    assert res.status_code == 200
    data = res.json()
    assert data["count"] >= 10
    districts = [d["district"] for d in data["districts"]]
    assert "Warangal" in districts
    assert "Indore" in districts
    assert "Ludhiana" in districts


# =====================================================================
# AGMARKNET Mandi Integration Endpoint Tests
# =====================================================================

def test_get_mandi_prices_all(client):
    """Verify APMC mandi prices endpoint returns structured data."""
    res = client.get("/api/v1/mandi/prices")
    assert res.status_code == 200
    data = res.json()

    assert data["total_records"] > 0
    assert "AGMARKNET" in data["data_source"]
    assert len(data["records"]) > 0

    first_record = data["records"][0]
    assert "commodity" in first_record
    assert "variety" in first_record
    assert "market" in first_record
    assert "district" in first_record
    assert "state" in first_record
    assert "min_price_inr" in first_record
    assert "max_price_inr" in first_record
    assert "modal_price_inr" in first_record
    assert first_record["modal_price_inr"] >= first_record["min_price_inr"]
    assert first_record["max_price_inr"] >= first_record["modal_price_inr"]


def test_get_mandi_prices_filtered_by_crop(client):
    """Verify mandi price filtering for all 8 required major crops."""
    required_crops = [
        "Cotton",
        "Paddy",
        "Chilli",
        "Tomato",
        "Wheat",
        "Maize",
        "Soybean",
        "Chickpea"
    ]

    for crop in required_crops:
        res = client.get(f"/api/v1/mandi/prices?commodity={crop}")
        assert res.status_code == 200
        data = res.json()
        assert data["total_records"] > 0, f"No mandi records found for {crop}"
        for r in data["records"]:
            assert r["commodity"].lower() == crop.lower()
            assert r["modal_price_inr"] > 0
            assert r["arrival_quantity_quintals"] > 0


def test_get_mandi_prices_state_and_district_filter(client):
    """Verify mandi price geographic filtering."""
    res = client.get("/api/v1/mandi/prices?state=Telangana&district=Warangal")
    assert res.status_code == 200
    data = res.json()
    assert data["total_records"] >= 2
    for r in data["records"]:
        assert r["district"] == "Warangal"
        assert r["state"] == "Telangana"


def test_get_commodity_price_summary_cotton(client):
    """Verify aggregated commodity price intelligence."""
    res = client.get("/api/v1/mandi/commodity/Cotton")
    assert res.status_code == 200
    data = res.json()

    assert data["commodity"] == "Cotton"
    assert data["average_modal_price_inr"] > 6000.0  # Real MSP is ~7121
    assert data["lowest_min_price_inr"] > 0
    assert data["highest_max_price_inr"] >= data["average_modal_price_inr"]
    assert data["total_arrival_quintals"] > 0
    assert data["msp_inr"] is not None
    assert data["top_market"] is not None
    assert data["market_count"] >= 3
    assert len(data["markets"]) >= 3
    assert "advisory_comment" in data


def test_get_commodity_price_summary_chilli(client):
    """Verify Chilli market intelligence (Guntur, Khammam, Warangal, Byadagi)."""
    res = client.get("/api/v1/mandi/commodity/Chilli")
    assert res.status_code == 200
    data = res.json()

    assert data["commodity"] == "Chilli"
    assert data["average_modal_price_inr"] > 15000.0  # Spices command high quintal rate
    assert data["market_count"] >= 3


def test_list_tracked_commodities(client):
    """Verify commodity catalog endpoint."""
    res = client.get("/api/v1/mandi/commodities")
    assert res.status_code == 200
    data = res.json()
    assert data["count"] >= 8
    commodities = [c["commodity"] for c in data["commodities"]]
    for crop in ["Cotton", "Paddy", "Chilli", "Tomato", "Wheat", "Maize", "Soybean", "Chickpea"]:
        assert crop in commodities


# =====================================================================
# Extension Officer Directory Integration Endpoint Tests
# =====================================================================

def test_lookup_officers_warangal_geesugonda(client):
    """Verify extension officer directory lookup by district and mandal."""
    res = client.get("/api/v1/officers/lookup?district=Warangal&mandal=Geesugonda")
    assert res.status_code == 200
    data = res.json()

    assert data["total_found"] >= 1
    officer = data["officers"][0]
    assert "Geesugonda" in officer["mandals"]
    assert officer["district"] == "Warangal"
    assert officer["state"] == "Telangana"
    assert "+91-" in officer["phone_number"]
    assert "@" in officer["email"]


def test_lookup_officers_guntur(client):
    """Verify extension officer lookup for Andhra Pradesh."""
    res = client.get("/api/v1/officers/lookup?district=Guntur")
    assert res.status_code == 200
    data = res.json()
    assert data["total_found"] >= 1
    assert any(o["state"] == "Andhra Pradesh" for o in data["officers"])


def test_get_officer_by_code(client):
    """Verify single officer detail lookup by code."""
    res = client.get("/api/v1/officers/AGRI-TS-WGL-042")
    assert res.status_code == 200
    data = res.json()
    assert data["officer_code"] == "AGRI-TS-WGL-042"
    assert data["name"] == "Dr. K. Srinivas Rao"
    assert "Mandal Agriculture Officer" in data["designation"]


def test_get_officer_by_invalid_code_returns_404(client):
    """Verify 404 response on unknown officer code."""
    res = client.get("/api/v1/officers/NONEXISTENT-CODE-999")
    assert res.status_code == 404


# =====================================================================
# Service Resilience & Fallback Tests (Never Fabricate Data)
# =====================================================================

@pytest.mark.asyncio
async def test_weather_service_graceful_timeout_fallback():
    """Verify service handles external timeouts safely without fabricating data."""
    # Instantiating service with ultra-low timeout
    service = WeatherIntegrationService(external_timeout_seconds=0.001)
    res = await service.get_district_weather("Warangal")

    assert res.is_fallback is True
    assert res.data_source == "IMD_AAS_OFFLINE_VERIFIED"
    assert res.district == "Warangal"
    assert len(res.forecast_5day) == 5
    assert len(res.active_risk_alerts) > 0


@pytest.mark.asyncio
async def test_mandi_service_graceful_timeout_fallback():
    """Verify mandi service returns authentic baseline records on network timeout."""
    service = MandiIntegrationService(external_timeout_seconds=0.001)
    res = await service.get_mandi_prices(commodity="Cotton")

    assert res.is_fallback is True
    assert res.data_source == "AGMARKNET_OFFLINE_VERIFIED"
    assert res.total_records >= 4
    for rec in res.records:
        assert rec.commodity == "Cotton"
        assert rec.modal_price_inr > 0
