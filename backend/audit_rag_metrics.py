import os
import sys
import json
import time
import math
import hashlib
import psycopg2
from typing import List, Dict, Any

from app.core.config import settings
from app.ai.rag_service import rag_service, BM25Retriever, DenseMultilingual384Embedder

sys.stdout.reconfigure(encoding='utf-8')

def run_rag_audit():
    print("=" * 70)
    print("INDEPENDENT AUDIT: RAG METRICS & EMBEDDING BENCHMARK")
    print("=" * 70)

    # 1. Verify PostgreSQL Chunks
    db_url = settings.DATABASE_URL.replace("+asyncpg", "")
    conn = psycopg2.connect(db_url)
    cur = conn.cursor()
    cur.execute("SELECT chunk_id, document_id, title, chunk_text, crop, language FROM knowledge_chunks")
    db_chunks = cur.fetchall()
    cur.close()
    conn.close()

    total_chunks = len(db_chunks)
    print(f"[✓] Verified PostgreSQL chunks count: {total_chunks}")
    assert total_chunks == 562, f"Expected 562 chunks, found {total_chunks}"

    # Convert to document dictionaries
    docs = []
    for r in db_chunks:
        docs.append({
            "chunk_id": r[0],
            "document_id": r[1],
            "title": r[2] or "",
            "content": r[3] or "",
            "crop": r[4] or "",
            "language": r[5] or "en"
        })

    # 2. Benchmark Queries (No training leakage: colloquial natural phrasing)
    eval_queries = [
        # Telugu (8 queries)
        {"q": "మొక్కజొన్నలో కత్తెర పురుగు నివారణ మందులు", "target_crop": "maize", "target_pest": "armyworm", "lang": "te"},
        {"q": "వరిలో అగ్గితెగులు బ్లాస్ట్ నివారణ పద్ధతులు", "target_crop": "rice", "target_pest": "blast", "lang": "te"},
        {"q": "ప్రత్తిలో గులాబీ రంగు పురుగు నివారణ CIBRC మోతాదు", "target_crop": "cotton", "target_pest": "bollworm", "lang": "te"},
        {"q": "మిరప తోటలో నల్ల తామర పురుగుల నివారణ", "target_crop": "chilli", "target_pest": "thrips", "lang": "te"},
        {"q": "ఎకరాకు యూరియా మరియు డీఏపీ ఎరువుల మోతాదు", "target_crop": None, "target_pest": "urea", "lang": "te"},
        {"q": "వరిలో సుడిదోమ బిపిహెచ్ నివారణ మందు", "target_crop": "rice", "target_pest": "bph", "lang": "te"},
        {"q": "టమోటా పంటలో ముందస్తు ఆకుమచ్చ తెగులు", "target_crop": "tomato", "target_pest": "blight", "lang": "te"},
        {"q": "వేరుశనగలో తిక్కా ఆకుమచ్చ తెగులు నివారణ", "target_crop": "groundnut", "target_pest": "leaf spot", "lang": "te"},

        # Hindi (6 queries)
        {"q": "कपास में गुलाबी सुंडी के लिए अनुशंसित कीटनाशक", "target_crop": "cotton", "target_pest": "bollworm", "lang": "hi"},
        {"q": "धान में झुलसा रोग और भूरा फुदका नियंत्रण", "target_crop": "rice", "target_pest": "bph", "lang": "hi"},
        {"q": "मक्का में फॉल आर्मीवर्म का रासायनिक नियंत्रण", "target_crop": "maize", "target_pest": "armyworm", "lang": "hi"},
        {"q": "मिर्च में थ्रिप्स और पत्ती मुड़न रोग का इलाज", "target_crop": "chilli", "target_pest": "thrips", "lang": "hi"},
        {"q": "गेहूं में पीला रतुआ रोकथाम", "target_crop": "wheat", "target_pest": "rust", "lang": "hi"},
        {"q": "टमाटर में फल छेदक इल्ली नियंत्रण दवा", "target_crop": "tomato", "target_pest": "borer", "lang": "hi"},

        # English (6 queries)
        {"q": "Pink bollworm management and CIBRC approved dose for cotton", "target_crop": "cotton", "target_pest": "bollworm", "lang": "en"},
        {"q": "Fall armyworm insecticide control in maize 15L pump dose", "target_crop": "maize", "target_pest": "armyworm", "lang": "en"},
        {"q": "Rice blast chemical treatment Tricyclazole application", "target_crop": "rice", "target_pest": "blast", "lang": "en"},
        {"q": "Chilli thrips black thrips control formulation and PHI", "target_crop": "chilli", "target_pest": "thrips", "lang": "en"},
        {"q": "Fertilizer recommendation per acre for cotton in black soils", "target_crop": "cotton", "target_pest": "fertilizer", "lang": "en"},
        {"q": "Brown plant hopper management in paddy fields", "target_crop": "rice", "target_pest": "bph", "lang": "en"}
    ]

    # Pre-build indices
    doc_texts = [f"{d['title']} {d['crop']} {d['content']}".lower() for d in docs]
    
    # 1. 256-d Hashing Baseline
    def embed_hash256(text: str) -> List[float]:
        vec = [0.0] * 256
        for token in text.lower().split():
            h = int(hashlib.md5(token.encode('utf-8')).hexdigest(), 16) % 256
            vec[h] += 1.0
        norm = math.sqrt(sum(x*x for x in vec))
        return [x / norm for x in vec] if norm > 0 else vec

    # 2. 384-d Dense Agri Embedder
    embedder384 = DenseMultilingual384Embedder(384)

    # 3. BM25
    bm25 = BM25Retriever()
    corpus_tokens = [rag_service._tokenize(t) for t in doc_texts]
    bm25.fit(corpus_tokens)

    hash256_vecs = [embed_hash256(t) for t in doc_texts]
    dense384_vecs = [embedder384.embed_tokens(rag_service._tokenize(t)) for t in doc_texts]

    # Pipelines
    def retrieve_hashing(q: str, top_k=3):
        qv = embed_hash256(q)
        scores = [(embedder384.cosine_similarity(qv, dv), i) for i, dv in enumerate(hash256_vecs)]
        scores.sort(key=lambda x: x[0], reverse=True)
        return [docs[i] for _, i in scores[:top_k]]

    def retrieve_dense384(q: str, top_k=3):
        qv = embedder384.embed_tokens(rag_service._tokenize(q))
        scores = [(embedder384.cosine_similarity(qv, dv), i) for i, dv in enumerate(dense384_vecs)]
        scores.sort(key=lambda x: x[0], reverse=True)
        return [docs[i] for _, i in scores[:top_k]]

    def retrieve_bm25(q: str, top_k=3):
        q_toks = rag_service._tokenize(q)
        scores = [(bm25.score(q_toks, i), i) for i in range(len(docs))]
        scores.sort(key=lambda x: x[0], reverse=True)
        return [docs[i] for _, i in scores[:top_k]]

    def retrieve_hybrid(q: str, top_k=3):
        # 0.5 BM25 + 0.5 Dense
        q_toks = rag_service._tokenize(q)
        bm25_raw = [bm25.score(q_toks, i) for i in range(len(docs))]
        max_bm25 = max(bm25_raw) if bm25_raw and max(bm25_raw) > 0 else 1.0

        qv = embedder384.embed_tokens(q_toks)
        scores = []
        for i in range(len(docs)):
            b_score = bm25_raw[i] / max_bm25
            c_score = (embedder384.cosine_similarity(qv, dense384_vecs[i]) + 1.0) / 2.0
            hybrid_score = 0.5 * b_score + 0.5 * c_score
            scores.append((hybrid_score, i))
        scores.sort(key=lambda x: x[0], reverse=True)
        return [docs[i] for _, i in scores[:top_k]]

    pipelines = {
        "BM25 Alone": retrieve_bm25,
        "256-d Hashing (Baseline)": retrieve_hashing,
        "v2.0-agri-multilingual-384": retrieve_dense384,
        "Production Hybrid (BM25 + 384-d)": retrieve_hybrid
    }

    results = {}
    for p_name, fn in pipelines.items():
        results[p_name] = {"overall": {}, "by_language": {}}
        
        for lang_code in ["te", "hi", "en"]:
            q_subset = [q for q in eval_queries if q["lang"] == lang_code]
            p1_hits = 0
            r3_hits = 0
            latencies = []

            for eq in q_subset:
                t0 = time.perf_counter()
                ret = fn(eq["q"], top_k=3)
                elapsed_ms = (time.perf_counter() - t0) * 1000.0
                latencies.append(elapsed_ms)

                def is_match(d):
                    t = f"{d['title']} {d['crop']} {d['content']}".lower()
                    cm = (not eq["target_crop"]) or (eq["target_crop"].lower() in t)
                    pm = (not eq["target_pest"]) or (eq["target_pest"].lower() in t)
                    return cm and pm

                if ret and is_match(ret[0]):
                    p1_hits += 1
                if any(is_match(d) for d in ret[:3]):
                    r3_hits += 1

            cnt = len(q_subset)
            results[p_name]["by_language"][lang_code] = {
                "count": cnt,
                "precision_at_1": round((p1_hits / cnt) * 100.0, 2),
                "recall_at_3": round((r3_hits / cnt) * 100.0, 2),
                "avg_latency_ms": round(sum(latencies) / len(latencies), 2)
            }

        # Overall
        total_p1 = sum(results[p_name]["by_language"][l]["precision_at_1"] * results[p_name]["by_language"][l]["count"] for l in ["te", "hi", "en"]) / len(eval_queries)
        total_r3 = sum(results[p_name]["by_language"][l]["recall_at_3"] * results[p_name]["by_language"][l]["count"] for l in ["te", "hi", "en"]) / len(eval_queries)
        total_lat = sum(results[p_name]["by_language"][l]["avg_latency_ms"] * results[p_name]["by_language"][l]["count"] for l in ["te", "hi", "en"]) / len(eval_queries)

        results[p_name]["overall"] = {
            "total_queries": len(eval_queries),
            "precision_at_1": round(total_p1, 2),
            "recall_at_3": round(total_r3, 2),
            "avg_latency_ms": round(total_lat, 2)
        }

    with open("audit_rag_results.json", "w", encoding="utf-8") as f:
        json.dump(results, f, indent=2, ensure_ascii=False)

    print("\nBenchmark Summary (Precision@1 / Recall@3):")
    for p_name, data in results.items():
        ov = data["overall"]
        te = data["by_language"]["te"]
        hi = data["by_language"]["hi"]
        en = data["by_language"]["en"]
        print(f"\n[{p_name}]")
        print(f"  Overall: P@1 = {ov['precision_at_1']}% | R@3 = {ov['recall_at_3']}% | Latency = {ov['avg_latency_ms']} ms")
        print(f"  Telugu (8):  P@1 = {te['precision_at_1']}% | R@3 = {te['recall_at_3']}%")
        print(f"  Hindi (6):   P@1 = {hi['precision_at_1']}% | R@3 = {hi['recall_at_3']}%")
        print(f"  English (6): P@1 = {en['precision_at_1']}% | R@3 = {en['recall_at_3']}%")

if __name__ == "__main__":
    run_rag_audit()
