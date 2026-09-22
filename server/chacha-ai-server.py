# -*- coding: utf-8 -*-
#!/usr/bin/env python3
import http.server
import socketserver
import json
import subprocess
import os
import sys
import datetime
import sqlite3
import re

PORT = 8088
DEFAULT_TOKEN = os.environ.get("CHACHA_TOKEN", "your_secure_token_here")
APK_PATH = "/home/admin/ChaChaAI.apk"
SESSIONS_MAP_FILE = "/home/admin/.local/share/chacha_sessions.json"
OPENCODE_DB = "/home/admin/.local/share/opencode/opencode.db"
SECURITY_LOG_FILE = "/tmp/chacha-security.log"

env = os.environ.copy()
env["PATH"] = "/home/admin/.npm-global/bin:" + env.get("PATH", "")
env["LANG"] = "en_US.UTF-8"
env["LC_ALL"] = "en_US.UTF-8"

# ==================== 安全护栏特征检测库 ====================
DANGEROUS_PATTERNS = [
    # 1. SSH 密钥与免密登录后门
    r'authorized_keys',
    r'id_rsa',
    r'id_ed25519',
    r'id_dsa',
    r'id_ecdsa',
    r'(?:~|/root|/home/[^/\s]+)/\.ssh',
    r'/etc/ssh',
    # 2. Linux 提权与特权越权
    r'\bsudo\b',
    r'\bsu\s+-\b',
    r'\bsu\s+root\b',
    r'/etc/sudoers',
    r'/etc/shadow',
    r'/etc/passwd',
    r'/etc/pam\.d',
    # 3. 反弹 Shell 与危险端口外联
    r'bash\s+-i\s+>&',
    r'/dev/tcp/',
    r'/dev/udp/',
    r'\bnc(?:\.traditional)?\s+-[le]',
    r'\bncat\s+-[le]',
    r'\bmkfifo\b',
    r'python.*socket.*connect',
    # 4. 破坏性抹盘与系统销毁
    r'\brm\s+-[a-zA-Z]*r[a-zA-Z]*f\s+(?:/|/\*)',
    r'\bmkfs\b',
    r'dd\s+if=/dev/zero',
    r'\bshutdown\b',
    r'\breboot\b',
    r'\binit\s+0\b',
]

DANGEROUS_REGEX = re.compile('|'.join(DANGEROUS_PATTERNS), re.IGNORECASE)

def check_security_guardrail(prompt):
    if not prompt:
        return None
    match = DANGEROUS_REGEX.search(prompt)
    if match:
        return match.group(0)
    return None

def load_session_map():
    try:
        if os.path.exists(SESSIONS_MAP_FILE):
            with open(SESSIONS_MAP_FILE, "r", encoding="utf-8") as f:
                return json.load(f)
    except Exception:
        pass
    return {}

def save_session_map(m):
    try:
        os.makedirs(os.path.dirname(SESSIONS_MAP_FILE), exist_ok=True)
        with open(SESSIONS_MAP_FILE, "w", encoding="utf-8") as f:
            json.dump(m, f, ensure_ascii=False, indent=2)
    except Exception:
        pass

def get_latest_opencode_session_id():
    try:
        con = sqlite3.connect(OPENCODE_DB)
        cur = con.cursor()
        row = cur.execute("SELECT id FROM session ORDER BY time_created DESC LIMIT 1").fetchone()
        con.close()
        if row:
            return row[0]
    except Exception:
        pass
    return None

class ChaChaAIHandler(http.server.BaseHTTPRequestHandler):
    def _send_json(self, data, code=200):
        body = json.dumps(data, ensure_ascii=False).encode("utf-8")
        self.send_response(code)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.send_header("Access-Control-Allow-Origin", "*")
        self.end_headers()
        self.wfile.write(body)

    def do_OPTIONS(self):
        self.send_response(200)
        self.send_header("Access-Control-Allow-Origin", "*")
        self.send_header("Access-Control-Allow-Methods", "GET, POST, OPTIONS")
        self.send_header("Access-Control-Allow-Headers", "Content-Type, Authorization")
        self.end_headers()

    def do_GET(self):
        if self.path == "/api/ping":
            self._send_json({"status": "ok", "message": "ChaCha AI Server is online", "model": "glm-5.3-flash", "guardrail": True})
        elif self.path in ["/download/ChaChaAI.apk", "/ChaChaAI.apk", "/1", "/c", "/a"]:
            if os.path.exists(APK_PATH):
                with open(APK_PATH, "rb") as f:
                    apk_data = f.read()
                self.send_response(200)
                self.send_header("Content-Type", "application/vnd.android.package-archive")
                self.send_header("Content-Length", str(len(apk_data)))
                self.send_header("Content-Disposition", 'attachment; filename="ChaChaAI.apk"')
                self.end_headers()
                self.wfile.write(apk_data)
            else:
                self._send_json({"error": "APK not ready yet", "reply": "APK 尚未就绪"}, 404)
        elif self.path in ["/", "/index.html"]:
            html = '''<!DOCTYPE html>
<html>
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<title>ChaCha AI 下载</title>
<style>
body { font-family: sans-serif; text-align: center; padding: 40px 15px; background: #fff; color: #000; }
h1 { font-size: 26px; margin-bottom: 12px; font-weight: bold; }
.btn { display: inline-block; padding: 20px 40px; font-size: 24px; font-weight: bold; color: #fff; background: #000; text-decoration: none; border-radius: 6px; margin: 25px 0; border: 3px solid #000; }
.tip { font-size: 17px; color: #222; line-height: 1.8; margin-top: 15px; }
.box { max-width: 500px; margin: 0 auto; border: 3px solid #000; padding: 25px; border-radius: 8px; }
.code { font-family: monospace; font-size: 20px; font-weight: bold; background: #eee; padding: 4px 10px; border-radius: 4px; }
</style>
</head>
<body>
<div class="box">
  <h1>ChaCha AI 墨水屏专版</h1>
  <p class="tip">起点讯飞阅读器 & 蓝牙物理键盘专用<br>集成 AOSP 谷歌拼音原生引擎 · 百万词库 · 云端安全护栏</p>
  <a class="btn" href="/1">【点击直接下载 APK】</a>
  <p class="tip">极简短网址：<br><span class="code">your-server-ip/1</span><br>或输入 <span class="code">your-server-ip/c</span> 均可直达下载</p>
</div>
</body>
</html>'''
            body = html.encode("utf-8")
            self.send_response(200)
            self.send_header("Content-Type", "text/html; charset=utf-8")
            self.send_header("Content-Length", str(len(body)))
            self.end_headers()
            self.wfile.write(body)
        else:
            self._send_json({"status": "running", "endpoints": ["/api/ping", "/api/chat", "/api/reset", "/download/ChaChaAI.apk", "/1", "/c"]})

    def do_POST(self):
        content_len = int(self.headers.get("Content-Length", 0))
        post_body = self.rfile.read(content_len).decode("utf-8", errors="replace")
        
        try:
            with open("/tmp/chacha-access.log", "a", encoding="utf-8") as f:
                f.write(f"[{datetime.datetime.now()}] POST {self.path} body={post_body[:120]}\n")
        except Exception:
            pass

        try:
            req_data = json.loads(post_body)
        except Exception:
            self._send_json({"error": "Invalid JSON", "reply": "【格式错误】发送的数据不是有效的 JSON！"}, 400)
            return

        token = req_data.get("token", "")
        if not token or token != DEFAULT_TOKEN:
            self._send_json({
                "error": "Unauthorized",
                "reply": f"【认证失败】安全密钥不正确！您发送的 token 是 '{token}'，正确密钥应为 '{DEFAULT_TOKEN}'"
            }, 401)
            return

        if self.path == "/api/reset":
            client_sid = req_data.get("session_id", "default_chacha_client")
            smap = load_session_map()
            if client_sid in smap:
                del smap[client_sid]
                save_session_map(smap)
            self._send_json({"status": "ok", "message": "Session reset", "reply": "已清空会话上下文，建立独立新会话"})
            return

        if self.path == "/api/chat":
            prompt = req_data.get("prompt", "").strip()
            cont = req_data.get("continue", True)
            client_sid = req_data.get("session_id", "default_chacha_client")
            if not prompt:
                self._send_json({"error": "Empty prompt", "reply": "【提示】未收到提问内容，请确认 prompt 字段填入了问题！"}, 400)
                return

            # ==================== 安全护栏主动检测与阻断 ====================
            violation = check_security_guardrail(prompt)
            if violation:
                log_entry = f"[{datetime.datetime.now()}] GUARD_BLOCKED client_ip={self.client_address[0]} client_sid={client_sid} violation='{violation}' prompt={prompt[:120]}\n"
                try:
                    with open(SECURITY_LOG_FILE, "a", encoding="utf-8") as sf:
                        sf.write(log_entry)
                except Exception:
                    pass
                self._send_json({
                    "status": "blocked",
                    "reply": f"【安全护栏拦截】检测到高危系统管理或提权指令（匹配特征: '{violation}'），已被服务端安全策略阻断执行！"
                }, 200)
                return

            smap = load_session_map()
            target_opencode_sid = None
            if cont and client_sid in smap:
                target_opencode_sid = smap[client_sid]

            cmd = ["opencode", "run"]
            if target_opencode_sid:
                # 延续 ChaCha AI 该会话的专属上下文，绝对不会继承其他机器/SSH终端会话
                cmd.extend(["-s", target_opencode_sid])
            # 注意：非 target_opencode_sid 时，坚决不传 -c！从头新建独立会话，彻底隔离外部历史
            cmd.append(prompt)

            try:
                res = subprocess.run(
                    cmd,
                    capture_output=True,
                    text=True,
                    encoding="utf-8",
                    stdin=subprocess.DEVNULL,
                    env=env,
                    timeout=300
                )
                output = res.stdout.strip()
                if not output and res.stderr:
                    output = res.stderr.strip()

                lines = output.splitlines()
                clean_lines = []
                for line in lines:
                    if line.startswith("> build") or line.startswith("> run"):
                        continue
                    clean_lines.append(line)
                clean_reply = "\n".join(clean_lines).strip()
                if not clean_reply:
                    clean_reply = output

                # 如果是新启动的会话，记录 opencode 分配的 session_id
                if not target_opencode_sid:
                    new_sid = get_latest_opencode_session_id()
                    if new_sid:
                        smap[client_sid] = new_sid
                        save_session_map(smap)

                self._send_json({"status": "ok", "reply": clean_reply})
            except subprocess.TimeoutExpired:
                self._send_json({"error": "AI response timed out (300s)", "reply": "【超时】AI 思考处理超过 5 分钟，请重试！"}, 504)
            except Exception as e:
                self._send_json({"error": str(e), "reply": f"【服务器错误】{str(e)}"}, 500)
        else:
            self._send_json({"error": "Not Found", "reply": "【错误】接口不存在"}, 404)

class ThreadedHTTPServer(socketserver.ThreadingMixIn, http.server.HTTPServer):
    daemon_threads = True

if __name__ == "__main__":
    server = ThreadedHTTPServer(("0.0.0.0", PORT), ChaChaAIHandler)
    print(f"ChaCha AI Server listening on port {PORT}...")
    server.serve_forever()
