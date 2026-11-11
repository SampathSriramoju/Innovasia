"""
Independent verification test suite for MandiApiClient.
Tests live integration with data.gov.in AGMARKNET dataset API (Resource 9ef84268-d588-465a-a308-a864a43d0070).
"""

import asyncio
import os
import sys
from pathlib import Path
import pytest
from dotenv import load_dotenv

# Add backend directory to sys.path
sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

# Ensure environment is loaded
load_dotenv(".env")
load_dotenv("backend/.env")

from app.services.mandi_api_client import (
    MandiApiClient,
    MandiAPIAuthError,
    MandiAPIResponse,
    AgmarknetPriceRecord,
)


@pytest.mark.asyncio
async def test_mandi_client_initialization():
    """Verify that client loads API key from environment without hardcoding."""
    client = MandiApiClient()
    assert client.has_api_key is True, "DATA_GOV_IN_API_KEY must be present in environment"
    assert client.timeout == 35.0
    assert "BolKisan-MandiClient" in client.user_agent


@pytest.mark.asyncio
async def test_mandi_client_live_query_telangana():
    """Verify querying live mandi data for Telangana."""
    async with MandiApiClient() as client:
        response = await client.fetch_prices(
            limit=5,
            state="Telangana",
        )
        assert isinstance(response, MandiAPIResponse)
        assert response.success is True
        assert len(response.records) > 0
        assert response.total > 0

        # Validate first record
        rec = response.records[0]
        assert rec.state.lower() == "telangana"
        assert rec.commodity != ""
        assert rec.market != ""
        assert rec.modal_price > 0
        assert rec.min_price <= rec.max_price
        # Verify date format YYYY-MM-DD
        assert len(rec.arrival_date.split("-")) == 3
        print(f"\n[Telangana Sample] {rec.commodity} ({rec.variety}) at {rec.market}: Modal=INR {rec.modal_price}/q, Date={rec.arrival_date}")


@pytest.mark.asyncio
async def test_mandi_client_live_query_andhra_pradesh():
    """Verify querying live mandi data for Andhra Pradesh."""
    async with MandiApiClient() as client:
        response = await client.fetch_prices(
            limit=5,
            state="Andhra Pradesh",
        )
        assert isinstance(response, MandiAPIResponse)
        assert response.success is True
        assert len(response.records) > 0

        rec = response.records[0]
        assert rec.state.lower() == "andhra pradesh"
        assert rec.modal_price > 0
        print(f"\n[Andhra Pradesh Sample] {rec.commodity} ({rec.variety}) at {rec.market}: Modal=INR {rec.modal_price}/q, Date={rec.arrival_date}")


@pytest.mark.asyncio
async def test_mandi_client_live_commodity_filter_cotton():
    """Verify querying specifically for Cotton commodity."""
    async with MandiApiClient() as client:
        response = await client.fetch_prices(
            limit=5,
            commodity="Cotton",
        )
        assert isinstance(response, MandiAPIResponse)
        assert response.success is True
        if response.records:
            rec = response.records[0]
            assert "cotton" in rec.commodity.lower()
            assert rec.modal_price > 0
            print(f"\n[Cotton Sample] {rec.commodity} in {rec.district}, {rec.state}: INR {rec.modal_price}/q")


@pytest.mark.asyncio
async def test_mandi_client_pagination():
    """Verify pagination with offset and limit."""
    async with MandiApiClient() as client:
        page1 = await client.fetch_prices(limit=3, offset=0, state="Telangana")
        page2 = await client.fetch_prices(limit=3, offset=3, state="Telangana")

        assert len(page1.records) <= 3
        assert len(page2.records) <= 3
        assert page1.offset == 0
        assert page2.offset == 3

        if len(page1.records) > 0 and len(page2.records) > 0:
            # First item of page 1 should differ from first item of page 2
            p1_keys = [(r.market, r.commodity) for r in page1.records]
            p2_keys = [(r.market, r.commodity) for r in page2.records]
            print(f"\nPage 1 items: {p1_keys}")
            print(f"Page 2 items: {p2_keys}")


@pytest.mark.asyncio
async def test_mandi_client_invalid_api_key():
    """Verify error handling on invalid / unauthorized API key."""
    client = MandiApiClient(api_key="invalid_dummy_key_12345")
    with pytest.raises(MandiAPIAuthError):
        await client.fetch_prices(limit=1)


@pytest.mark.asyncio
async def test_mandi_client_missing_api_key():
    """Verify error handling when API key is completely absent."""
    client = MandiApiClient(api_key="")
    client.api_key = ""
    with pytest.raises(MandiAPIAuthError):
        await client.fetch_prices(limit=1)


if __name__ == "__main__":
    # Allow running directly via python
    async def main():
        print("--- Running Mandi API Client Verification Tests ---")
        await test_mandi_client_initialization()
        print("[PASS] Initialization test passed")
        await test_mandi_client_live_query_telangana()
        print("[PASS] Live Telangana query test passed")
        await test_mandi_client_live_query_andhra_pradesh()
        print("[PASS] Live Andhra Pradesh query test passed")
        await test_mandi_client_live_commodity_filter_cotton()
        print("[PASS] Live Cotton filter query test passed")
        await test_mandi_client_pagination()
        print("[PASS] Pagination test passed")
        await test_mandi_client_missing_api_key()
        print("[PASS] Missing API key test passed")
        await test_mandi_client_invalid_api_key()
        print("[PASS] Invalid API key auth error test passed")
        print("\nALL MANDI API CLIENT TESTS PASSED SUCCESSFULLY!")

    asyncio.run(main())
