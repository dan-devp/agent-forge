"""Minimal OpenAI-compatible server for testing the local provider.

Start:  python tools/mock-openai-server.py          (port 1234)
Use:    LOCAL_AI_BASE_URL=http://localhost:1234/v1  (local)
        LOCAL_AI_BASE_URL=http://host.docker.internal:1234/v1  (from the container)
        LOCAL_AI_MODEL=mock-local-7b

Replaces LM Studio / Ollama / vLLM in tests: same route
POST /v1/chat/completions, same response shape. Logs every request so it is
visible which path and which model actually arrive.
"""
import json
import sys
import time
from http.server import BaseHTTPRequestHandler, HTTPServer


class Handler(BaseHTTPRequestHandler):
    def _json(self, code, payload):
        body = json.dumps(payload).encode()
        self.send_response(code)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def do_GET(self):
        if self.path.rstrip("/").endswith("/models"):
            self._json(200, {"object": "list", "data": [
                {"id": "mock-local-7b", "object": "model", "created": 0, "owned_by": "mock"}]})
        else:
            self._json(404, {"error": {"message": "not found"}})

    def do_POST(self):
        length = int(self.headers.get("Content-Length", 0))
        req = json.loads(self.rfile.read(length) or b"{}")
        print("REQ path=%s model=%s msgs=%s" % (
            self.path, req.get("model"), len(req.get("messages", []))), flush=True)
        self._json(200, {
            "id": "chatcmpl-mock",
            "object": "chat.completion",
            "created": int(time.time()),
            "model": req.get("model", "mock-local-7b"),
            "choices": [{
                "index": 0,
                "message": {"role": "assistant",
                            "content": "Antwort vom lokalen Mock-Modell."},
                "finish_reason": "stop",
            }],
            "usage": {"prompt_tokens": 11, "completion_tokens": 7, "total_tokens": 18},
        })

    def log_message(self, *args):
        pass


if __name__ == "__main__":
    port = int(sys.argv[1]) if len(sys.argv) > 1 else 1234
    print("Mock OpenAI server on http://localhost:%d/v1" % port, flush=True)
    HTTPServer(("0.0.0.0", port), Handler).serve_forever()
