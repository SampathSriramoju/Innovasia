import sys
from pathlib import Path
import pytest
from PIL import Image
import io

backend_dir = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(backend_dir))

from app.ai.rag_service import rag_service
from app.ai.crop_vision_engine import crop_vision_engine, ImageQualityGate

def test_case4_text_only_chickpea_fusarium_wilt():
    """
    CASE 4: A text-only question about Chickpea Fusarium Wilt MUST correctly retrieve
    Chickpea Fusarium Wilt / Bengal Gram Wilt documents across English, Telugu, and Hindi.
    """
    queries = [
        "Chickpea Fusarium Wilt treatment",
        "శనగ ఎండు తెగులు నివారణ ఏమిటి?",
        "What is the treatment for fusarium wilt in bengal gram?",
        "चना उकठा रोग नियंत्रण"
    ]
    for q in queries:
        hits = rag_service.retrieve(query=q, crop="", top_k=3)
        assert len(hits) > 0, f"Expected hits for CASE 4 query: '{q}', got 0"
        
        # Verify that Bengal Gram / Chickpea Fusarium Wilt was retrieved
        found_chickpea_wilt = False
        for h in hits:
            crop_name = (h.get("crop") or "").lower()
            title = (h.get("title") or "").lower()
            content = (h.get("content") or "").lower()
            target = (h.get("target") or "").lower()
            
            is_chickpea = any(k in crop_name for k in ["bengal gram", "chickpea", "శనగ", "चना"])
            is_wilt = any(k in (title + content + target) for k in ["fusarium", "wilt", "ఎండు", "उकठा"])
            if is_chickpea and is_wilt:
                found_chickpea_wilt = True
                break
        
        assert found_chickpea_wilt, f"Chickpea Fusarium Wilt not found in retrieved results for query: '{q}'"


def test_unrelated_queries_never_return_chickpea_wilt():
    """
    When no crop or an empty string is provided, unrelated agricultural queries
    MUST NEVER return Chickpea Fusarium Wilt (or any arbitrary crop disease).
    """
    unrelated_queries = [
        "What is PM Kisan scheme?",
        "Weather forecast for next week",
        "How to test soil organic carbon?",
        "How to manage water in summer?",
        "What is MSP for wheat?",
        "What should I spray on my field?",
        "How to increase crop yield?",
        "Pest management methods",
        "Disease control general practices",
        "Tractor maintenance tips",
        "Organic farming jeevamrutha preparation",
        "ఎరువుల సమతుల్య వినియోగం ఎలా చేయాలి?",
        "వాతావరణ సమాచారం తెలపండి"
    ]
    for q in unrelated_queries:
        hits = rag_service.retrieve(query=q, crop="", top_k=3)
        for h in hits:
            crop_name = (h.get("crop") or "").lower()
            title = (h.get("title") or "").lower()
            target = (h.get("target") or "").lower()
            
            is_chickpea = any(k in crop_name for k in ["bengal gram", "chickpea", "శనగ", "चना"])
            is_wilt = any(k in (title + target) for k in ["fusarium", "wilt", "ఎండు", "उकठा"])
            
            assert not (is_chickpea and is_wilt), (
                f"Chickpea Fusarium Wilt was falsely returned for unrelated query: '{q}'. "
                f"Retrieved: [{h.get('crop')}] {h.get('title')}"
            )


@pytest.mark.asyncio
async def test_unanalyzed_or_pending_image_never_retrieves_disease():
    """
    If visual analysis is pending, inconclusive, or failed (e.g. Gemini Vision unavailable or blank/low confidence),
    RAG must NEVER retrieve or append unrelated crop disease treatments (like Chickpea Fusarium Wilt).
    """
    # 1. Synthetic leaf photo where vision is unavailable / pending
    synthetic_img = Image.new("RGB", (256, 256), color=(40, 110, 40))
    buf = io.BytesIO()
    synthetic_img.save(buf, format="JPEG")
    
    diag_res, quality = await crop_vision_engine.analyze_image(
        image_bytes=buf.getvalue(),
        crop_name=None,  # No crop provided
        affected_part="leaf",
        farmer_notes=None,
        language="en"
    )
    
    # Visual analysis is pending or escalated
    assert diag_res.status in ["escalated", "healthy_crop", "quality_rejected"]
    
    # Must NEVER have Chickpea Fusarium Wilt
    diag_title = (diag_res.primary_diagnosis or "").lower()
    assert "chickpea" not in diag_title
    assert "fusarium" not in diag_title
    assert "bengal gram" not in diag_title
    
    # If escalated, remedies must be empty (NOT populated with Chickpea treatments)
    if diag_res.status == "escalated":
        assert len(diag_res.cultural_remedies) == 0
        assert len(diag_res.biological_remedies) == 0
        assert diag_res.chemical_management is None


def test_sqlite_room_query_empty_crop_simulation():
    """
    Simulates the exact Room SQL query from OfflineKnowledgeDao.
    Confirms that empty string, whitespace, null, or generic crop names return []
    instead of matching every row (which previously returned row 0: Bengal Gram Wilt).
    """
    import sqlite3
    con = sqlite3.connect(":memory:")
    cur = con.cursor()
    cur.execute("CREATE TABLE offline_knowledge (id TEXT, crop TEXT, category TEXT, title TEXT, language TEXT)")
    cur.execute("INSERT INTO offline_knowledge VALUES ('1', 'Bengal Gram', 'disease', 'Chickpea: Fusarium Wilt', 'en')")
    cur.execute("INSERT INTO offline_knowledge VALUES ('2', 'Cotton', 'disease', 'Cotton: Bacterial Blight', 'en')")
    cur.execute("INSERT INTO offline_knowledge VALUES ('3', 'Organic', 'cultivation', 'Jeevamrutha Preparation', 'en')")

    room_query = """
        SELECT * FROM offline_knowledge 
        WHERE :crop IS NOT NULL 
          AND TRIM(:crop) != '' 
          AND LOWER(TRIM(:crop)) NOT IN ('general', 'all', 'agri', 'farming', 'none', 'unknown', 'null', 'unspecified')
          AND (crop LIKE '%' || :crop || '%' OR :crop LIKE '%' || crop || '%') 
          AND (language = :lang OR :lang = '')
    """
    
    # 1. Empty string crop -> MUST return empty list
    res_empty = cur.execute(room_query, {"crop": "", "lang": "en"}).fetchall()
    assert len(res_empty) == 0, f"Expected empty list for crop='', got {res_empty}"

    # 2. Whitespace crop -> MUST return empty list
    res_ws = cur.execute(room_query, {"crop": "   ", "lang": "en"}).fetchall()
    assert len(res_ws) == 0, f"Expected empty list for crop='   ', got {res_ws}"

    # 3. Generic crop name -> MUST return empty list
    res_gen = cur.execute(room_query, {"crop": "general", "lang": "en"}).fetchall()
    assert len(res_gen) == 0, f"Expected empty list for crop='general', got {res_gen}"

    # 4. Valid crop name -> MUST return matching rows only
    res_cotton = cur.execute(room_query, {"crop": "Cotton", "lang": "en"}).fetchall()
    assert len(res_cotton) == 1
    assert res_cotton[0][1] == "Cotton"

    # 5. Bengal Gram -> MUST return Bengal Gram only
    res_bg = cur.execute(room_query, {"crop": "Bengal Gram", "lang": "en"}).fetchall()
    assert len(res_bg) == 1
    assert res_bg[0][1] == "Bengal Gram"


if __name__ == "__main__":
    import asyncio
    print("Testing CASE 4: Chickpea Fusarium Wilt...")
    test_case4_text_only_chickpea_fusarium_wilt()
    print("PASS: CASE 4 text-only queries correctly retrieve Chickpea Fusarium Wilt!")

    print("\nTesting Unrelated Queries...")
    test_unrelated_queries_never_return_chickpea_wilt()
    print("PASS: Unrelated queries never return Chickpea Fusarium Wilt!")

    print("\nTesting Unanalyzed / Pending Image...")
    asyncio.run(test_unanalyzed_or_pending_image_never_retrieves_disease())
    print("PASS: Unanalyzed or pending image never retrieves Chickpea Fusarium Wilt or disease treatments!")

    print("\nTesting Room SQL Simulation...")
    test_sqlite_room_query_empty_crop_simulation()
    print("PASS: Room SQL query simulation confirms empty crop never returns all rows or Bengal Gram Wilt!")

    print("\nAll validation checks passed successfully!")
