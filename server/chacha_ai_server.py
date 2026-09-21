#!/usr/bin/env python3
"""
ChaCha AI Backend Server
Lightweight HTTP Bridge for HTC ChaCha & iOS Shortcuts
Forwards user prompts to LLM (via opencode or custom API)
"""

import http.server
import socketserver
import json
import subprocess
import os
import sys
import datetime

PORT = int(os.environ.get("CHACHA_PORT", "8088"))
DEFAULT_TOKEN = os.environ.get("CHACHA_TOKEN", "your-secure-token-here")
APK_PATH = os.environ.get("CHACHA_APK_PATH", "./ChaChaAI.apk")

env = os.environ.copy()
env["LANG"] = "en_US.UTF-8"
env["LC_ALL"] = "en_US.UTF-8"

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
            self._send_json({"status": "ok", "message": "ChaCha AI Server is online"})
        elif self.path == "/download/ChaChaAI.apk":
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
        else:
            self._send_json({"status": "running", "endpoints": ["/api/ping", "/api/chat", "/api/reset", "/download/ChaChaAI.apk"]})

    def do_POST(self):
        content_len = int(self.headers.get("Content-Length", 0))
        post_body = self.rfile.read(content_len).decode("utf-8", errors="replace")

        try:
            req_data = json.loads(post_body)
        except Exception:
            self._send_json({"error": "Invalid JSON", "reply": "【格式错误】发送的数据不是有效的 JSON！"}, 400)
            return

        token = req_data.get("token", "")
        if not token or token != DEFAULT_TOKEN:
            self._send_json({
                "error": "Unauthorized",
                "reply": "【认证失败】安全密钥不正确，请在设置中检查您的 Token！"
            }, 401)
            return

        if self.path == "/api/reset":
            self._send_json({"status": "ok", "message": "Session reset", "reply": "已清空会话上下文"})
            return

        if self.path == "/api/chat":
            prompt = req_data.get("prompt", "").strip()
            cont = req_data.get("continue", True)
            if not prompt:
                self._send_json({"error": "Empty prompt", "reply": "【提示】未收到提问内容，请确认已填入问题！"}, 400)
                return

            # Example using opencode command line runner
            # You can adapt this to directly call OpenAI, Claude, or any LLM API
            cmd = ["opencode", "run"]
            if cont:
                cmd.append("-c")
            cmd.append(prompt)

            try:
                res = subprocess.run(
                    cmd,
                    capture_output=True,
                    text=True,
                    encoding="utf-8",
                    stdin=subprocess.DEVNULL,
                    env=env,
                    timeout=90
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

                self._send_json({"status": "ok", "reply": clean_reply})
            except subprocess.TimeoutExpired:
                self._send_json({"error": "AI response timed out (90s)", "reply": "【超时】AI 思考超过 90 秒，请重试！"}, 504)
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
