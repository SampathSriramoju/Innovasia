from fastapi import APIRouter
from app.api.v1.endpoints import (
    auth, voice, crops, tickets, weather, mandi, market_prices, officers, disease, conversations, sms, ivr
)

api_router = APIRouter()

api_router.include_router(auth.router, prefix="/auth", tags=["Authentication & User Management"])
api_router.include_router(conversations.router, prefix="/conversations", tags=["Farmer Conversations & History"])
api_router.include_router(voice.router, prefix="/voice", tags=["Voice Assistant & AI Query"])
api_router.include_router(crops.router, prefix="/crops", tags=["Agricultural Crops"])
api_router.include_router(tickets.router, prefix="/tickets", tags=["Escalated Agricultural Tickets"])
api_router.include_router(disease.router, prefix="/disease", tags=["Multimodal Disease Diagnosis"])
# Top-level direct alias for image analysis (/api/v1/analyze-image)
api_router.add_api_route("/analyze-image", disease.diagnose_crop_disease, methods=["POST"], tags=["Multimodal Disease Diagnosis"])
api_router.include_router(weather.router, prefix="/weather", tags=["IMD Weather Advisories & Agromet"])
api_router.include_router(mandi.router, prefix="/mandi", tags=["AGMARKNET APMC Mandi Prices"])
api_router.include_router(market_prices.router, prefix="/market-prices", tags=["PostgreSQL Cached Mandi Market Prices"])
api_router.include_router(officers.router, prefix="/officers", tags=["Agricultural Extension Officers"])
api_router.include_router(sms.router, prefix="/sms", tags=["SMS Fallback (Mock Demo)"])
api_router.include_router(ivr.router, prefix="/ivr", tags=["IVR Fallback (Mock Demo)"])


