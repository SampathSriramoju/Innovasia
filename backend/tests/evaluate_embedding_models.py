import json
import sys
import time
from pathlib import Path
sys.path.insert(0, str(Path(__file__).resolve().parent.parent))
from typing import List, Dict, Any, Tuple
import numpy as np

# Ground truth evaluation queries with expected crop and collection
EVAL_DATASET = [
    # --- TELUGU QUERIES ---
    {"query": "పత్తిలో గులాబీ రంగు పురుగు నివారణకు ఏ మందు వాడాలి?", "lang": "te", "crop": "Cotton", "expected_col": "KB05", "expected_target": "pink bollworm"},
    {"query": "వరిలో అగ్గితెగులు లక్షణాలు మరియు మందులు ఏమిటి?", "lang": "te", "crop": "Rice", "expected_col": "KB04", "expected_target": "rice blast"},
    {"query": "మిరపలో నల్ల తామర పురుగు నివారణ ఎలా చేయాలి?", "lang": "te", "crop": "Chilli", "expected_col": "KB05", "expected_target": "thrips"},
    {"query": "పత్తి పంటకు ఎన్ని రోజులకు నీరు పెట్టాలి?", "lang": "te", "crop": "Cotton", "expected_col": "KB11", "expected_target": None},
    {"query": "వరి పంటలో యూరియా ఎరువు ఎప్పుడు వేయాలి?", "lang": "te", "crop": "Rice", "expected_col": "KB09", "expected_target": None},
    {"query": "మొక్కజొన్నలో కత్తెర పురుగు నివారణ చర్యలు చెప్పండి", "lang": "te", "crop": "Maize", "expected_col": "KB05", "expected_target": "fall armyworm"},
    {"query": "టమోటాలో ఆకు ముడత తెగులు నివారణ ఎలా?", "lang": "te", "crop": "Tomato", "expected_col": "KB04", "expected_target": "leaf curl"},
    {"query": "పత్తి పంటలో కలుపు నివారణకు ఏ మందు వాడాలి?", "lang": "te", "crop": "Cotton", "expected_col": "KB13", "expected_target": None},
    {"query": "వరంగల్ మార్కెట్లో పత్తి క్వింటాల్ ధర ఎంత ఉంది?", "lang": "te", "crop": "Cotton", "expected_col": "KB18", "expected_target": None},
    {"query": "నల్లరేగడి నేలలో సేంద్రియ కర్బనం ఎలా పెంచాలి?", "lang": "te", "crop": None, "expected_col": "KB10", "expected_target": None},

    # --- HINDI QUERIES ---
    {"query": "कपास में गुलाबी सुंडी का नियंत्रण कैसे करें?", "lang": "hi", "crop": "Cotton", "expected_col": "KB05", "expected_target": "pink bollworm"},
    {"query": "धान में झुलसा रोग के लक्षण और रोकथाम बताएं", "lang": "hi", "crop": "Rice", "expected_col": "KB04", "expected_target": "blast"},
    {"query": "मिर्च में थ्रिप्स कीट की रोकथाम के उपाय क्या हैं?", "lang": "hi", "crop": "Chilli", "expected_col": "KB05", "expected_target": "thrips"},
    {"query": "कपास की फसल में सिंचाई कब और कितनी करनी चाहिए?", "lang": "hi", "crop": "Cotton", "expected_col": "KB11", "expected_target": None},
    {"query": "धान में यूरिया और डीएपी खाद की सही मात्रा क्या है?", "lang": "hi", "crop": "Rice", "expected_col": "KB09", "expected_target": None},
    {"query": "मक्का में फॉल आर्मीवर्म का जैविक और रासायनिक नियंत्रण", "lang": "hi", "crop": "Maize", "expected_col": "KB05", "expected_target": "fall armyworm"},
    {"query": "टमाटर में पत्ती मुड़न रोग का उपचार क्या है?", "lang": "hi", "crop": "Tomato", "expected_col": "KB04", "expected_target": "leaf curl"},
    {"query": "कपास में खरपतवार नियंत्रण के लिए कौन सी दवा डालें?", "lang": "hi", "crop": "Cotton", "expected_col": "KB13", "expected_target": None},
    {"query": "इंदौर मंडी में सोयाबीन का आज का ताजा भाव क्या है?", "lang": "hi", "crop": "Soybean", "expected_col": "KB18", "expected_target": None},
    {"query": "काली मिट्टी में जल निकास और सुधार के उपाय बताएं", "lang": "hi", "crop": None, "expected_col": "KB10", "expected_target": None},

    # --- ENGLISH QUERIES ---
    {"query": "How to control pink bollworm in cotton crop?", "lang": "en", "crop": "Cotton", "expected_col": "KB05", "expected_target": "pink bollworm"},
    {"query": "What are the symptoms and fungicide for blast in paddy?", "lang": "en", "crop": "Rice", "expected_col": "KB04", "expected_target": "blast"},
    {"query": "Which insecticide is effective against black thrips in chilli?", "lang": "en", "crop": "Chilli", "expected_col": "KB05", "expected_target": "thrips"},
    {"query": "What is the irrigation schedule and water requirement for cotton?", "lang": "en", "crop": "Cotton", "expected_col": "KB11", "expected_target": None},
    {"query": "Recommended fertilizer dose of NPK for rice cultivation", "lang": "en", "crop": "Rice", "expected_col": "KB09", "expected_target": None},
    {"query": "How to manage fall armyworm infestation in maize?", "lang": "en", "crop": "Maize", "expected_col": "KB05", "expected_target": "fall armyworm"},
    {"query": "Remedy for tomato leaf curl virus transmitted by whitefly", "lang": "en", "crop": "Tomato", "expected_col": "KB04", "expected_target": "leaf curl"},
    {"query": "What herbicide should be used for weed control in cotton?", "lang": "en", "crop": "Cotton", "expected_col": "KB13", "expected_target": None},
    {"query": "What is the current mandi price of cotton per quintal?", "lang": "en", "crop": "Cotton", "expected_col": "KB18", "expected_target": None},
    {"query": "How to manage and improve soil organic carbon in black cotton soil?", "lang": "en", "crop": None, "expected_col": "KB10", "expected_target": None}
]

def main():
    print("Initializing RAG Service and Models...")
    from app.ai.rag_service import rag_service, BM25Retriever, DenseMultilingual384Embedder
    from transformers import AutoTokenizer, AutoModel
    import torch

    # 1. Prepare documents and tokens
    docs = rag_service.documents
    doc_tokens = rag_service.doc_tokens
    custom_embedder = rag_service.vector_embedder
    custom_doc_vectors = rag_service.doc_vectors

    print(f"Loaded {len(docs)} documents.")

    # 2. Load trained transformer model
    model_name = "sentence-transformers/paraphrase-multilingual-MiniLM-L12-v2"
    print(f"Loading transformer model: {model_name}...")
    tokenizer = AutoTokenizer.from_pretrained(model_name)
    hf_model = AutoModel.from_pretrained(model_name)
    hf_model.eval()

    def get_hf_embeddings_batched(texts: List[str], batch_size: int = 64) -> np.ndarray:
        all_embeddings = []
        for i in range(0, len(texts), batch_size):
            batch = texts[i:i + batch_size]
            inputs = tokenizer(batch, padding=True, truncation=True, max_length=128, return_tensors="pt")
            with torch.no_grad():
                outputs = hf_model(**inputs)
                token_embeddings = outputs[0]
                input_mask_expanded = inputs['attention_mask'].unsqueeze(-1).expand(token_embeddings.size()).float()
                sum_embeddings = torch.sum(token_embeddings * input_mask_expanded, 1)
                sum_mask = torch.clamp(input_mask_expanded.sum(1), min=1e-9)
                mean_pooled = sum_embeddings / sum_mask
                vecs = mean_pooled.cpu().numpy()
                norms = np.linalg.norm(vecs, axis=1, keepdims=True)
                norms[norms == 0] = 1.0
                all_embeddings.append(vecs / norms)
        return np.vstack(all_embeddings)

    def get_hf_embedding(text: str) -> np.ndarray:
        return get_hf_embeddings_batched([text])[0]

    print("Pre-computing trained transformer embeddings for knowledge base chunks (batched)...")
    t0 = time.time()
    chunk_texts = [f"{d.get('title', '')} {d.get('crop', '')} {d.get('content', '')}"[:256] for d in docs]
    hf_doc_vectors = get_hf_embeddings_batched(chunk_texts, batch_size=64)
    print(f"Computed {len(hf_doc_vectors)} embeddings in {time.time() - t0:.2f}s.")

    # Evaluation function
    def evaluate_method(method_name: str, retrieve_fn):
        results_by_lang = {"te": {"p1": 0, "r3": 0, "mrr": 0.0, "total": 0},
                           "hi": {"p1": 0, "r3": 0, "mrr": 0.0, "total": 0},
                           "en": {"p1": 0, "r3": 0, "mrr": 0.0, "total": 0}}
        total_p1 = 0
        total_r3 = 0
        total_mrr = 0.0

        for item in EVAL_DATASET:
            q = item["query"]
            lang = item["lang"]
            expected_col = item["expected_col"]
            expected_crop = item["crop"]
            expected_target = item["expected_target"]

            retrieved = retrieve_fn(q, top_k=3)

            # Check correctness:
            # P@1: does top 1 match expected collection (or crop + relevant topic)?
            top1_hit = False
            top3_hit = False
            reciprocal_rank = 0.0

            for rank, doc in enumerate(retrieved[:3]):
                is_hit = False
                doc_col = doc.get("collection_id")
                doc_crop = doc.get("crop")
                doc_title = (doc.get("title") or "").lower()
                doc_target = (doc.get("target") or "").lower()
                doc_content = (doc.get("content") or "").lower()

                # Hit condition:
                # 1. Collection matches expected collection (or related FAQ/Q&A)
                # 2. If expected crop is given, crop must match
                col_match = (doc_col == expected_col) or (doc_col in ["KB21", "KB22"] and expected_col in ["KB04", "KB05", "KB09", "KB11", "KB13"])
                crop_match = True
                if expected_crop:
                    crop_match = (expected_crop.lower() in (doc_crop or "").lower()) or (expected_crop.lower() in doc_title) or (expected_crop.lower() in doc_content)

                target_match = True
                if expected_target:
                    target_match = (expected_target.lower() in doc_target) or (expected_target.lower() in doc_title) or (expected_target.lower() in doc_content)

                if col_match and crop_match and target_match:
                    is_hit = True

                if is_hit:
                    if rank == 0:
                        top1_hit = True
                    top3_hit = True
                    if reciprocal_rank == 0.0:
                        reciprocal_rank = 1.0 / (rank + 1)

            results_by_lang[lang]["total"] += 1
            if top1_hit:
                results_by_lang[lang]["p1"] += 1
                total_p1 += 1
            if top3_hit:
                results_by_lang[lang]["r3"] += 1
                total_r3 += 1
            results_by_lang[lang]["mrr"] += reciprocal_rank
            total_mrr += reciprocal_rank

        summary = {}
        for l, metrics in results_by_lang.items():
            tot = metrics["total"]
            summary[l] = {
                "precision@1": round(metrics["p1"] / tot, 4),
                "recall@3": round(metrics["r3"] / tot, 4),
                "mrr": round(metrics["mrr"] / tot, 4),
                "count": tot
            }
        overall_tot = len(EVAL_DATASET)
        summary["overall"] = {
            "precision@1": round(total_p1 / overall_tot, 4),
            "recall@3": round(total_r3 / overall_tot, 4),
            "mrr": round(total_mrr / overall_tot, 4),
            "total_queries": overall_tot
        }
        return summary

    # Method 1: Pure BM25
    def bm25_retrieve(query: str, top_k: int = 3):
        tokens, _ = rag_service._expand_multilingual_query(query)
        scores = [rag_service.bm25.score(tokens, i) for i in range(len(docs))]
        ranked = sorted(enumerate(scores), key=lambda x: x[1], reverse=True)[:top_k]
        return [docs[i] for i, s in ranked]

    # Method 2: Custom Embedder Alone
    def custom_embed_retrieve(query: str, top_k: int = 3):
        tokens, _ = rag_service._expand_multilingual_query(query)
        q_vec = custom_embedder.embed_tokens(tokens)
        sims = [custom_embedder.cosine_similarity(q_vec, d_vec) for d_vec in custom_doc_vectors]
        ranked = sorted(enumerate(sims), key=lambda x: x[1], reverse=True)[:top_k]
        return [docs[i] for i, s in ranked]

    # Method 3: Trained Transformer Embedder Alone
    def hf_embed_retrieve(query: str, top_k: int = 3):
        q_vec = get_hf_embedding(query)
        sims = np.dot(hf_doc_vectors, q_vec)
        ranked = np.argsort(sims)[::-1][:top_k]
        return [docs[i] for i in ranked]

    # Method 4: Existing Uncalibrated Hybrid (with existing +1.8/+0.8 overpowering boosts)
    def existing_hybrid_retrieve(query: str, top_k: int = 3):
        return rag_service.retrieve(query, top_k=top_k)

    # Method 5: Calibrated Hybrid (BM25 + Trained Transformer + Bounded Metadata Weights)
    def calibrated_hybrid_retrieve(query: str, top_k: int = 3):
        from app.ai.query_understanding import QueryUnderstanding
        analysis = QueryUnderstanding.analyze_query(query)
        crop = analysis.get("crop")
        recommended_cols = set(analysis.get("recommended_collections", []))
        tokens, entities = rag_service._expand_multilingual_query(query)
        
        # Dense vector similarity
        q_vec = get_hf_embedding(query)
        dense_sims = np.dot(hf_doc_vectors, q_vec)

        # BM25 similarity
        bm25_raw = [rag_service.bm25.score(tokens, i) for i in range(len(docs))]
        max_b = max(bm25_raw) if bm25_raw and max(bm25_raw) > 0 else 1.0
        bm25_norm = [s / max_b for s in bm25_raw]

        combined = []
        for i, doc in enumerate(docs):
            # Base semantic score (0.0 to 1.0)
            base_score = 0.5 * dense_sims[i] + 0.5 * bm25_norm[i]

            # Bounded gentle multipliers instead of massive overpowering additions
            multiplier = 1.0
            doc_col = doc.get("collection_id")
            doc_crop = (doc.get("crop") or "").lower()

            if doc_col in recommended_cols:
                multiplier *= 1.25

            if crop:
                c_clean = crop.lower()
                if c_clean in doc_crop or doc_crop in c_clean:
                    multiplier *= 1.35
                elif doc_crop and doc_crop not in ["general", "all", "soils"]:
                    multiplier *= 0.65 # discount off-crop

            # Target entity bonus
            if analysis.get("target_entity") and analysis["target_entity"].lower() in (doc.get("target") or "").lower():
                multiplier *= 1.4

            final_score = base_score * multiplier
            combined.append((final_score, doc))

        combined.sort(key=lambda x: x[0], reverse=True)
        return [doc for score, doc in combined[:top_k]]

    print("\n--- RUNNING EVALUATION ---")
    eval_results = {}
    print("1. Evaluating Pure BM25...")
    eval_results["BM25"] = evaluate_method("BM25", bm25_retrieve)
    print("2. Evaluating Custom Pseudo-Hash Embedder...")
    eval_results["Custom_DenseMultilingual384"] = evaluate_method("Custom_Dense", custom_embed_retrieve)
    print("3. Evaluating Trained Transformer (paraphrase-multilingual-MiniLM-L12-v2)...")
    eval_results["Trained_MiniLM"] = evaluate_method("Trained_MiniLM", hf_embed_retrieve)
    print("4. Evaluating Existing Uncalibrated Hybrid...")
    eval_results["Existing_Uncalibrated_Hybrid"] = evaluate_method("Existing_Hybrid", existing_hybrid_retrieve)
    print("5. Evaluating Calibrated Multilingual Hybrid...")
    eval_results["Calibrated_Multilingual_Hybrid"] = evaluate_method("Calibrated_Hybrid", calibrated_hybrid_retrieve)

    output_path = Path("embedding_model_evaluation_results.json")
    with open(output_path, "w", encoding="utf-8") as f:
        json.dump(eval_results, f, indent=2)

    print(f"\nEvaluation complete! Results written to {output_path}")
    print(json.dumps(eval_results, indent=2))

if __name__ == "__main__":
    main()
