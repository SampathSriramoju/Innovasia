import time
import json
import statistics
import concurrent.futures
import urllib.request
import urllib.parse
import os
import psutil
from app.core.security import create_access_token

BASE_URL = "http://127.0.0.1:8000"

def get_auth_headers():
    token = create_access_token(data={
        "sub": "b2392849-3a05-4275-b092-bae94211bf43",
        "phone": "9876543210",
        "name": "Ramu Farmer",
        "role": "farmer",
        "lang": "te"
    })
    return {
        "Authorization": f"Bearer {token}",
        "Content-Type": "application/json"
    }

def run_single_request(url, method="GET", data=None, headers=None):
    t0 = time.perf_counter()
    status_code = 0
    try:
        req = urllib.request.Request(url, method=method)
        if headers:
            for k, v in headers.items():
                req.add_header(k, v)
        if data:
            req.data = json.dumps(data).encode("utf-8")
        with urllib.request.urlopen(req, timeout=15) as resp:
            status_code = resp.status
            _ = resp.read()
    except urllib.error.HTTPError as e:
        status_code = e.code
    except Exception as e:
        status_code = 500
    t1 = time.perf_counter()
    return (t1 - t0) * 1000.0, status_code

def benchmark_endpoint(name, url, method="GET", data=None, headers=None, concurrency_levels=[10, 25, 50], total_requests=100):
    print(f"\n========================================================")
    print(f"BENCHMARKING: {name} ({method} {url})")
    print(f"========================================================")
    
    results = {}
    for conc in concurrency_levels:
        latencies = []
        status_codes = []
        t_start = time.perf_counter()
        
        with concurrent.futures.ThreadPoolExecutor(max_workers=conc) as executor:
            futures = [
                executor.submit(run_single_request, url, method, data, headers)
                for _ in range(total_requests)
            ]
            for f in concurrent.futures.as_completed(futures):
                lat, code = f.result()
                latencies.append(lat)
                status_codes.append(code)
                
        t_total = time.perf_counter() - t_start
        rps = total_requests / t_total if t_total > 0 else 0
        
        latencies.sort()
        med = statistics.median(latencies)
        p90 = latencies[int(len(latencies) * 0.90)]
        p95 = latencies[int(len(latencies) * 0.95)]
        p99 = latencies[int(len(latencies) * 0.99)]
        err_count = sum(1 for c in status_codes if c >= 400 or c == 0)
        err_rate = (err_count / total_requests) * 100.0
        
        print(f"Concurrency: {conc:2d} | Requests: {total_requests} | Duration: {t_total:.2f}s | RPS: {rps:6.1f} | Med: {med:5.1f}ms | P90: {p90:5.1f}ms | P95: {p95:5.1f}ms | P99: {p99:5.1f}ms | Error: {err_rate:.1f}%")
        
        results[conc] = {
            "concurrency": conc,
            "requests": total_requests,
            "duration_sec": round(t_total, 2),
            "rps": round(rps, 1),
            "median_ms": round(med, 1),
            "p90_ms": round(p90, 1),
            "p95_ms": round(p95, 1),
            "p99_ms": round(p99, 1),
            "error_rate_pct": round(err_rate, 1)
        }
    return results

def benchmark_rag_direct():
    print(f"\n========================================================")
    print(f"BENCHMARKING: Direct RAG Retrieval Engine (pgvector)")
    print(f"========================================================")
    from app.ai.rag_service import rag_service
    
    queries = [
        ("వరిలో అగ్గి తెగులు లక్షణాలు మరియు నివారణ", "Rice"),
        ("పత్తిలో గులాబీ రంగు పురుగు నివారణ పద్ధతులు", "Cotton"),
        ("మిరపలో ఆకుముడత మరియు తామర పురుగుల నివారణ", "Chilli"),
        ("వేరుశనగలో తిక్కా తెగులు నియంత్రణ", "Groundnut"),
        ("మొక్కజొన్నలో కత్తెర పురుగు నివారణ మందులు", "Maize"),
        ("Cotton pink bollworm chemical IPM management", "Cotton"),
        ("धान में भूरा फुदका (BPH) कीट प्रबंधन", "Rice"),
        ("టమాటోలో ఎర్లీ బ్లైట్ తెగులు మరియు కాపర్ ఆక్సీక్లోరైడ్", "Tomato")
    ]
    
    latencies = []
    chunk_counts = []
    
    for i in range(50):
        q, crop = queries[i % len(queries)]
        t0 = time.perf_counter()
        res = rag_service.retrieve(query=q, crop=crop, top_k=3)
        t1 = time.perf_counter()
        latencies.append((t1 - t0) * 1000.0)
        chunk_counts.append(len(res))
        
    latencies.sort()
    med = statistics.median(latencies)
    p90 = latencies[int(len(latencies) * 0.90)]
    p95 = latencies[int(len(latencies) * 0.95)]
    avg_chunks = statistics.mean(chunk_counts)
    
    print(f"RAG Queries: 50 | Median: {med:.1f}ms | P90: {p90:.1f}ms | P95: {p95:.1f}ms | Avg Chunks Returned: {avg_chunks:.1f}")
    return {
        "queries": 50,
        "median_ms": round(med, 1),
        "p90_ms": round(p90, 1),
        "p95_ms": round(p95, 1),
        "avg_chunks": round(avg_chunks, 1)
    }

def main():
    process = psutil.Process()
    mem_before_mb = process.memory_info().rss / (1024 * 1024)
    print(f"Process Memory Before Load: {mem_before_mb:.1f} MB")
    
    headers = get_auth_headers()
    
    all_benchmarks = {}
    
    # 1. Health Endpoint
    all_benchmarks["health"] = benchmark_endpoint(
        "Health Endpoint",
        f"{BASE_URL}/health",
        concurrency_levels=[10, 25, 50],
        total_requests=100
    )
    
    # 2. Tickets List (/my)
    all_benchmarks["tickets_my"] = benchmark_endpoint(
        "Farmer Ticket Retrieval (/api/v1/tickets/my)",
        f"{BASE_URL}/api/v1/tickets/my",
        headers=headers,
        concurrency_levels=[10, 25, 50],
        total_requests=50
    )
    
    # 3. Ticket Escalation POST
    escalate_payload = {
        "crop_name": "Cotton",
        "state": "Telangana",
        "district": "Warangal",
        "mandal": "Geesugonda",
        "village": "Dharmaram",
        "escalation_reason": "Severe leaf reddening and boll shedding observed.",
        "original_question_transcript": "పత్తిలో ఆకులు ఎర్రబడి రాలిపోతున్నాయి, నివారణ చెప్పండి",
        "priority": "high"
    }
    all_benchmarks["ticket_escalate"] = benchmark_endpoint(
        "Ticket Escalation (/api/v1/tickets/escalate)",
        f"{BASE_URL}/api/v1/tickets/escalate",
        method="POST",
        data=escalate_payload,
        headers=headers,
        concurrency_levels=[10, 25],
        total_requests=30
    )
    
    # 4. RAG direct vector retrieval
    all_benchmarks["rag_retrieval"] = benchmark_rag_direct()
    
    # 5. Multilingual voice query
    voice_payload = {
        "query_text": "వరి పంటలో అగ్గి తెగులు లక్షణాలు మరియు నివారణ ఏమిటి?",
        "language": "te",
        "crop_name": "Rice"
    }
    all_benchmarks["voice_query"] = benchmark_endpoint(
        "Voice Query Endpoint (/api/v1/voice/query)",
        f"{BASE_URL}/api/v1/voice/query",
        method="POST",
        data=voice_payload,
        headers=headers,
        concurrency_levels=[5, 10],
        total_requests=20
    )
    
    mem_after_mb = process.memory_info().rss / (1024 * 1024)
    print(f"\nProcess Memory After Load: {mem_after_mb:.1f} MB (Delta: +{mem_after_mb - mem_before_mb:.1f} MB)")
    
    with open("backend_performance_data.json", "w", encoding="utf-8") as f:
        json.dump(all_benchmarks, f, indent=2)
    print("Performance data saved to backend_performance_data.json")

if __name__ == "__main__":
    main()
