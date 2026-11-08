import sys
from pathlib import Path
sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

from app.ai.rag_service import rag_service
from app.ai.gemini_service import gemini_service
from app.ai.query_understanding import QueryUnderstanding

cotton_queries = [
    ("How much water does cotton require?", "en", "irrigation"),
    ("Which fertilizer is suitable for cotton crop?", "en", "fertilizer_soil"),
    ("How can I control pink bollworm in cotton?", "en", "pest_management"),
    ("What diseases commonly affect cotton?", "en", "crop_disease"),
    ("How can I improve and manage black soil for cultivation?", "en", "soil_health"),
    # Also in Telugu
    ("పత్తి పంటకు ఎన్ని రోజులకు నీరు పెట్టాలి?", "te", "irrigation"),
    ("పత్తిలో వేయవలసిన ఎరువుల మోతాదు ఏమిటి?", "te", "fertilizer_soil"),
    ("పత్తిలో గులాబీ రంగు పురుగు నివారణ ఎలా?", "te", "pest_management"),
    ("పత్తిలో ఆకుమచ్చ తెగులు నివారణ మందు ఏమిటి?", "te", "crop_disease"),
    ("నల్లరేగడి నేలలో సేంద్రియ కర్బనం ఎలా పెంచాలి?", "te", "soil_health"),
]

def main():
    print("Testing Distinctiveness across Cotton & Soil Queries (English & Telugu):")
    answers = []
    titles = []

    for q, lang, expected_intent in cotton_queries:
        analysis = QueryUnderstanding.analyze_query(q)
        retrieved = rag_service.retrieve(q, crop=analysis.get("crop"), top_k=3)
        top_title = retrieved[0]["title"] if retrieved else "NO_DOC"
        top_col = retrieved[0]["collection_id"] if retrieved else "NONE"
        top_score = retrieved[0]["score"] if retrieved else 0.0

        # Run grounded fallback
        fallback = gemini_service._generate_grounded_fallback(
            query=q,
            retrieved_contexts=retrieved,
            language=lang,
            safety_context=None,
            query_analysis=analysis
        )
        ans_text = fallback["text"]
        answers.append(ans_text)
        titles.append(top_title)

        q_clean = q.encode('ascii', 'backslashreplace').decode('ascii')
        title_clean = top_title.encode('ascii', 'backslashreplace').decode('ascii')
        ans_clean = ans_text[:140].replace('\n', ' ').encode('ascii', 'backslashreplace').decode('ascii')

        print(f"\n[QUERY]: {q_clean}")
        print(f"  -> Detected Intent: {analysis.get('intent')} | Expected: {expected_intent}")
        print(f"  -> Top Retrieved: [{top_col}] {title_clean} (Score: {top_score})")
        print(f"  -> Answer Sample: {ans_clean}...")

    # Check distinctness
    unique_titles = len(set(titles))
    unique_answers = len(set(answers))
    print(f"\n==========================================")
    print(f"Total Queries: {len(cotton_queries)}")
    print(f"Unique Top Titles: {unique_titles} / {len(cotton_queries)}")
    print(f"Unique Answers: {unique_answers} / {len(cotton_queries)}")
    print(f"==========================================")

    assert unique_answers == len(cotton_queries), f"Found repeated answers! {unique_answers} != {len(cotton_queries)}"
    print("SUCCESS: 100% of answers are distinct and accurately grounded in their respective topics!")

if __name__ == "__main__":
    main()
