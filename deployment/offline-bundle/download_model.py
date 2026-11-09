#!/usr/bin/env python3
"""
Bol Kisan - Offline SLM Model Downloader and Verifier
Downloads authentic LiteRT-LM models from Hugging Face with SHA-256 verification.
"""

import sys
import os
import json
import hashlib
import urllib.request

MANIFEST_PATH = os.path.join(os.path.dirname(os.path.abspath(__file__)), "model_manifest.json")

def format_bytes(size):
    for unit in ['B', 'KB', 'MB', 'GB']:
        if size < 1024.0:
            return f"{size:.2f} {unit}"
        size /= 1024.0
    return f"{size:.2f} TB"

def download_with_progress(url, dest_path, expected_size):
    temp_path = dest_path + ".download"
    print(f"Connecting to: {url}")
    
    headers = {'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) BolKisan/1.0'}
    req = urllib.request.Request(url, headers=headers)
    
    with urllib.request.urlopen(req) as response, open(temp_path, 'wb') as out_file:
        total_size = int(response.info().get('Content-Length', expected_size))
        downloaded = 0
        chunk_size = 128 * 1024
        
        print(f"Downloading {format_bytes(total_size)}...")
        while True:
            chunk = response.read(chunk_size)
            if not chunk:
                break
            out_file.write(chunk)
            downloaded += len(chunk)
            percent = (downloaded / total_size) * 100 if total_size > 0 else 0
            sys.stdout.write(f"\rProgress: [{downloaded}/{total_size}] ({percent:.1f}%) {format_bytes(downloaded)}")
            sys.stdout.flush()
    print("\nDownload complete. Calculating SHA-256 checksum...")
    return temp_path

def verify_checksum(file_path, expected_hash):
    sha = hashlib.sha256()
    with open(file_path, 'rb') as f:
        while chunk := f.read(128 * 1024):
            sha.update(chunk)
    calculated = sha.hexdigest().lower()
    return calculated == expected_hash.lower(), calculated

def main():
    if not os.path.exists(MANIFEST_PATH):
        print(f"Error: Manifest not found at {MANIFEST_PATH}")
        sys.exit(1)
        
    with open(MANIFEST_PATH, 'r', encoding='utf-8') as f:
        manifest = json.load(f)
        
    default_model_id = manifest.get("default_model_id", "qwen3-0.6b-litertlm")
    models = manifest.get("models", [])
    model_entry = next((m for m in models if m.get("model_id") == default_model_id), models[0])
    
    print("=" * 60)
    print("  Bol Kisan Offline Model Downloader")
    print(f"  Model:        {model_entry.get('name')}")
    print(f"  Parameters:   {model_entry.get('parameters')}")
    print(f"  Quantization: {model_entry.get('quantization')}")
    print(f"  Expected:     {format_bytes(model_entry.get('file_size_bytes', 0))}")
    print("=" * 60)
    
    url = model_entry["download_url"]
    expected_sha = model_entry["sha256"]
    expected_size = model_entry["file_size_bytes"]
    dest_path = os.path.join(os.path.dirname(os.path.abspath(__file__)), "qwen3_0_6b.litertlm")
    
    if os.path.exists(dest_path):
        print(f"Existing model file found at: {dest_path}")
        print("Verifying integrity...")
        valid, calc_hash = verify_checksum(dest_path, expected_sha)
        if valid:
            print(f"[OK] Existing model is verified (SHA-256: {calc_hash}). Ready for packaging!")
            return dest_path
        else:
            print("[WARN] Existing file checksum mismatch. Re-downloading...")
            os.remove(dest_path)
            
    temp_file = download_with_progress(url, dest_path, expected_size)
    valid, calc_hash = verify_checksum(temp_file, expected_sha)
    
    if valid:
        os.rename(temp_file, dest_path)
        print(f"[SUCCESS] SHA-256 Verified: {calc_hash}")
        print(f"Saved verified model to: {dest_path}")
        return dest_path
    else:
        if os.path.exists(temp_file):
            os.remove(temp_file)
        print(f"[ERROR] SHA-256 Checksum Mismatch!")
        print(f"Expected:   {expected_sha}")
        print(f"Calculated: {calc_hash}")
        sys.exit(1)

if __name__ == "__main__":
    main()
