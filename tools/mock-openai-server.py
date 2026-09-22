"""Minimal OpenAI-compatible server for testing without a real model.

Start:  python tools/mock-openai-server.py          (port 1234)
Use:    base-url http://localhost:1234/v1             (local)
        base-url http://host.docker.internal:1234/v1  (from the container)
        model    mock-local-7b

Replaces LM Studio / Ollama / vLLM in tests: same route POST /v1/chat/completions, same
response shape. Logs every request so it is visible which path and which model arrive.

Tool loop: when the request offers tools and no tool result is in the history yet, the
mock requests every offered tool once (with plausible arguments for the built-ins). Once
tool results are present it answers with them, so a task like time-and-division passes.
Without tools it answers with a fixed sentence.
"""
import json
import re
import sys
import time
from http.server import BaseHTTPRequestHandler, HTTPServer

ARGS = {
    "currentTime": {"timeZone": "Europe/Berlin"},
    "divide": {"dividend": 100, "divisor": 7},
}


def answer_from_tools(messages):
    results = [m.get("content") or "" for m in messages if m.get("role") == "tool"]
    if not results:
        return "Antwort vom lokalen Mock-Modell."
    parts = []
    for r in results:
        if re.search(r"\d{4}-\d{2}-\d{2}", r):
            parts.append("Es ist %s." % r)
        elif "geteilt" in r:
            parts.append(r + ".")
        else:
            parts.append(r)
    return " ".join(parts)


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
        messages = req.get("messages", [])
        tools = req.get("tools") or []
        has_results = any(m.get("role") == "tool" for m in messages)
        print("REQ path=%s model=%s msgs=%s tools=%s results=%s" % (
            self.path, req.get("model"), len(messages),
            [t["function"]["name"] for t in tools], has_results), flush=True)

        if tools and not has_results:
            calls = []
            for i, t in enumerate(tools):
                name = t["function"]["name"]
                calls.append({
                    "id": "call_%d" % i,
                    "type": "function",
                    "function": {"name": name, "arguments": json.dumps(ARGS.get(name, {}))},
                })
            message = {"role": "assistant", "content": None, "tool_calls": calls}
            finish = "tool_calls"
        else:
            message = {"role": "assistant", "content": answer_from_tools(messages)}
            finish = "stop"

        self._json(200, {
            "id": "chatcmpl-mock",
            "object": "chat.completion",
            "created": int(time.time()),
            "model": req.get("model", "mock-local-7b"),
            "choices": [{"index": 0, "message": message, "finish_reason": finish}],
            "usage": {"prompt_tokens": 11, "completion_tokens": 7, "total_tokens": 18},
        })

    def log_message(self, *args):
        pass


if __name__ == "__main__":
    port = int(sys.argv[1]) if len(sys.argv) > 1 else 1234
    print("Mock OpenAI server on http://localhost:%d/v1" % port, flush=True)
    HTTPServer(("0.0.0.0", port), Handler).serve_forever()
