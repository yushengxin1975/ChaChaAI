import os
import urllib.request
import json
import base64

OUT_DIR = "/tmp/pinyin_bin"
os.makedirs(OUT_DIR, exist_ok=True)

def download_blob(sha, out_name):
    print(f"Downloading blob {sha} to {out_name}...")
    url = f"https://api.github.com/repos/DataBackups/PinyinIme/git/blobs/{sha}"
    req = urllib.request.Request(url, headers={"User-Agent": "Mozilla/5.0"})
    with urllib.request.urlopen(req, timeout=30) as resp:
        data = json.loads(resp.read().decode("utf-8"))
        raw = base64.b64decode(data["content"])
        target = os.path.join(OUT_DIR, out_name)
        with open(target, "wb") as f:
            f.write(raw)
        print(f"Saved {target} ({len(raw)} bytes)")

download_blob("5fa8fb1efdbea96713070a5cbfaa4dc831d6e096", "libjni_pinyinime.so")
download_blob("1be3f9c79feacf9c6d33f62950c5ef1e8283dadb", "dict_pinyin.dat")
print("Done!")
