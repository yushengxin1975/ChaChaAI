import os
import urllib.request
import json
import base64

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
PROJECT_DIR = os.path.dirname(SCRIPT_DIR)
LIBS_ARM_DIR = os.path.join(PROJECT_DIR, "libs", "armeabi-v7a")
LIBS_ARM_LEGACY_DIR = os.path.join(PROJECT_DIR, "libs", "armeabi")
ASSETS_DIR = os.path.join(PROJECT_DIR, "assets")

os.makedirs(LIBS_ARM_DIR, exist_ok=True)
os.makedirs(LIBS_ARM_LEGACY_DIR, exist_ok=True)
os.makedirs(ASSETS_DIR, exist_ok=True)

def download_blob(sha, target_path):
    print(f"Downloading blob {sha} to {target_path}...")
    url = f"https://api.github.com/repos/DataBackups/PinyinIme/git/blobs/{sha}"
    req = urllib.request.Request(url, headers={"User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64)"})
    with urllib.request.urlopen(req, timeout=30) as resp:
        data = json.loads(resp.read().decode("utf-8"))
        raw = base64.b64decode(data["content"])
        with open(target_path, "wb") as f:
            f.write(raw)
        print(f"Successfully saved {target_path} ({len(raw)} bytes)")

# 1. libjni_pinyinime.so
so_sha = "5fa8fb1efdbea96713070a5cbfaa4dc831d6e096"
so_path_v7a = os.path.join(LIBS_ARM_DIR, "libjni_pinyinime.so")
download_blob(so_sha, so_path_v7a)

# Copy to legacy armeabi directory as well
so_path_legacy = os.path.join(LIBS_ARM_LEGACY_DIR, "libjni_pinyinime.so")
with open(so_path_v7a, "rb") as src, open(so_path_legacy, "wb") as dst:
    dst.write(src.read())
print(f"Copied to {so_path_legacy}")

# 2. dict_pinyin.dat
dict_sha = "1be3f9c79feacf9c6d33f62950c5ef1e8283dadb"
dict_path = os.path.join(ASSETS_DIR, "dict_pinyin.dat")
download_blob(dict_sha, dict_path)
print("All files downloaded successfully!")
