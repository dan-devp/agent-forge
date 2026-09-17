"""Minimal MCP server (streamable HTTP) for testing the tool registration.

Start:  python tools/mock-mcp-server.py            (port 3333)
Use:    app.tools.mcp.mock.transport=HTTP
        app.tools.mcp.mock.url=http://localhost:3333/mcp
        From inside the container: http://host.docker.internal:3333/mcp

Replaces a real MCP server in tests: same JSON-RPC methods (initialize,
tools/list, tools/call), same response shape. Logs every request so it is
visible what the agent actually asks for.

Offers two tools (descriptions and results are German — the model reads them):
  weather(location)   - fixed answer, proves argument pass-through
  wordLength(word)    - really counts, so the answer is checkable
"""
import json
import sys
from http.server import BaseHTTPRequestHandler, HTTPServer

PROTOCOL_VERSION = "2025-06-18"

TOOLS = [
    {
        "name": "weather",
        "description": "Liefert das Wetter fuer einen Ort. Nutze dieses Tool bei Wetterfragen.",
        "inputSchema": {
            "type": "object",
            "properties": {"location": {"type": "string", "description": "Name des Ortes"}},
            "required": ["location"],
        },
    },
    {
        "name": "wordLength",
        "description": "Zaehlt die Buchstaben eines Wortes. Nutze dieses Tool, statt selbst zu zaehlen.",
        "inputSchema": {
            "type": "object",
            "properties": {"word": {"type": "string", "description": "Das zu zaehlende Wort"}},
            "required": ["word"],
        },
    },
]


def call_tool(name, args):
    if name == "weather":
        location = args.get("location", "unbekannt")
        return f"In {location} sind es 17 Grad, bewoelkt, Wind aus West."
    if name == "wordLength":
        word = args.get("word", "")
        return f"Das Wort '{word}' hat {len(word)} Buchstaben."
    raise ValueError(f"Unknown tool: {name}")


class Handler(BaseHTTPRequestHandler):
    def _send(self, code, payload=None):
        body = b"" if payload is None else json.dumps(payload).encode()
        self.send_response(code)
        if body:
            self.send_header("Content-Type", "application/json")
            self.send_header("Content-Length", str(len(body)))
        self.send_header("Mcp-Session-Id", "mock-session")
        self.end_headers()
        if body:
            self.wfile.write(body)

    def do_GET(self):
        # No server-side push needed — the client falls back to plain POST.
        self.send_response(405)
        self.send_header("Content-Length", "0")
        self.end_headers()

    def do_DELETE(self):
        self._send(204)

    def do_POST(self):
        length = int(self.headers.get("Content-Length", 0))
        message = json.loads(self.rfile.read(length) or b"{}")

        method = message.get("method")
        request_id = message.get("id")
        print(f"REQ method={method} id={request_id}", flush=True)

        # Notifications have no id and get no response.
        if request_id is None:
            self._send(202)
            return

        try:
            self._send(200, {"jsonrpc": "2.0", "id": request_id, "result": self._result(method, message)})
        except Exception as error:  # noqa: BLE001 - the error belongs in a JSON-RPC error response
            self._send(200, {
                "jsonrpc": "2.0",
                "id": request_id,
                "error": {"code": -32603, "message": str(error)},
            })

    def _result(self, method, message):
        if method == "initialize":
            requested = message.get("params", {}).get("protocolVersion", PROTOCOL_VERSION)
            print(f"    client speaks protocol {requested}", flush=True)
            return {
                "protocolVersion": requested,
                "capabilities": {"tools": {"listChanged": False}},
                "serverInfo": {"name": "mock-mcp-server", "version": "1.0.0"},
            }
        if method == "tools/list":
            return {"tools": TOOLS}
        if method == "tools/call":
            params = message.get("params", {})
            name = params.get("name")
            args = params.get("arguments", {})
            print(f"    tools/call {name} {args}", flush=True)
            return {"content": [{"type": "text", "text": call_tool(name, args)}], "isError": False}
        if method == "ping":
            return {}
        raise ValueError(f"Unknown method: {method}")

    def log_message(self, *args):
        pass  # own logging above


if __name__ == "__main__":
    port = int(sys.argv[1]) if len(sys.argv) > 1 else 3333
    print(f"Mock MCP server on http://localhost:{port}/mcp", flush=True)
    HTTPServer(("0.0.0.0", port), Handler).serve_forever()
