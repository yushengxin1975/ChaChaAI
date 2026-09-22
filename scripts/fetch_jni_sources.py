import os
import urllib.request
import json
import base64

DEST_DIR = "/home/admin/pinyin_src"
os.makedirs(DEST_DIR, exist_ok=True)

print("Fetching repo tree from GitHub API...")
url = "https://api.github.com/repos/DataBackups/PinyinIme/git/trees/master?recursive=1"
req = urllib.request.Request(url, headers={"User-Agent": "Mozilla/5.0"})
with urllib.request.urlopen(req, timeout=30) as resp:
    tree_data = json.loads(resp.read().decode("utf-8"))

for item in tree_data.get("tree", []):
    path = item["path"]
    if path.startswith("jni/") and item["type"] == "blob":
        blob_sha = item["sha"]
        blob_url = f"https://api.github.com/repos/DataBackups/PinyinIme/git/blobs/{blob_sha}"
        blob_req = urllib.request.Request(blob_url, headers={"User-Agent": "Mozilla/5.0"})
        with urllib.request.urlopen(blob_req, timeout=30) as b_resp:
            b_data = json.loads(b_resp.read().decode("utf-8"))
            content = base64.b64decode(b_data["content"])
            full_path = os.path.join(DEST_DIR, path)
            os.makedirs(os.path.dirname(full_path), exist_ok=True)
            with open(full_path, "wb") as f:
                f.write(content)
            print(f"Downloaded: {path} ({len(content)} bytes)")

print("All jni files downloaded successfully!")
