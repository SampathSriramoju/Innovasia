import urllib.request
import json
import time
import io
import statistics
import concurrent.futures
from PIL import Image
from app.core.security import create_access_token

token = create_access_token(data={'sub': 'b2392849-3a05-4275-b092-bae94211bf43', 'role': 'farmer'})

# Create a small valid test jpeg
img = Image.new('RGB', (224, 224), color=(34, 139, 34))
buf = io.BytesIO()
img.save(buf, format='JPEG')
img_bytes = buf.getvalue()

boundary = '----WebKitFormBoundaryBench'
part1 = f'--{boundary}\r\nContent-Disposition: form-data; name="crop_name"\r\n\r\nCotton\r\n'.encode('utf-8')
part2 = f'--{boundary}\r\nContent-Disposition: form-data; name="file"; filename="cotton_leaf.jpg"\r\nContent-Type: image/jpeg\r\n\r\n'.encode('utf-8')
part3 = f'\r\n--{boundary}--\r\n'.encode('utf-8')
body = part1 + part2 + img_bytes + part3

headers = {
    'Authorization': f'Bearer {token}',
    'Content-Type': f'multipart/form-data; boundary={boundary}'
}

def send_one():
    t0 = time.perf_counter()
    req = urllib.request.Request('http://127.0.0.1:8000/api/v1/disease/diagnose', data=body, headers=headers)
    try:
        with urllib.request.urlopen(req, timeout=10) as r:
            _ = r.read()
            code = r.status
    except Exception as e:
        code = 500
    t1 = time.perf_counter()
    return (t1 - t0) * 1000.0, code

print("Running Disease Diagnosis Benchmark (20 requests, 5 concurrency)...")
t_start = time.perf_counter()
latencies = []
codes = []
with concurrent.futures.ThreadPoolExecutor(max_workers=5) as ex:
    futs = [ex.submit(send_one) for _ in range(20)]
    for f in concurrent.futures.as_completed(futs):
        lat, c = f.result()
        latencies.append(lat)
        codes.append(c)

t_tot = time.perf_counter() - t_start
latencies.sort()
med = statistics.median(latencies)
p90 = latencies[int(len(latencies) * 0.9)]
p95 = latencies[int(len(latencies) * 0.95)]
rps = 20 / t_tot
errs = sum(1 for c in codes if c >= 400 or c == 0)

print(f"Disease Diagnosis | Concurrency: 5 | Total: 20 | RPS: {rps:.1f} | Med: {med:.1f}ms | P90: {p90:.1f}ms | P95: {p95:.1f}ms | Errors: {errs}")
