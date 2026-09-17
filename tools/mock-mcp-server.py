"""Minimaler MCP-Server (Streamable HTTP) zum Testen der Tool-Registrierung.

Start:  python tools/mock-mcp-server.py            (Port 3333)
Nutzen: app.tools.mcp.mock.transport=HTTP
        app.tools.mcp.mock.url=http://localhost:3333/mcp
        Aus dem Container: http://host.docker.internal:3333/mcp

Ersetzt einen echten MCP-Server im Test: gleiche JSON-RPC-Methoden
(initialize, tools/list, tools/call), gleiche Antwortform. Loggt jeden
Request, damit sichtbar ist, was der Agent wirklich anfordert.

Bietet zwei Tools:
  wetter(ort)        - feste Antwort, beweist Argumentdurchreichung
  wortlaenge(wort)   - rechnet wirklich, damit die Antwort pruefbar ist
"""
import json
import sys
from http.server import BaseHTTPRequestHandler, HTTPServer

PROTOCOL_VERSION = "2025-06-18"

TOOLS = [
    {
        "name": "wetter",
        "description": "Liefert das Wetter fuer einen Ort. Nutze dieses Tool bei Wetterfragen.",
        "inputSchema": {
            "type": "object",
            "properties": {"ort": {"type": "string", "description": "Name des Ortes"}},
            "required": ["ort"],
        },
    },
    {
        "name": "wortlaenge",
        "description": "Zaehlt die Buchstaben eines Wortes. Nutze dieses Tool, statt selbst zu zaehlen.",
        "inputSchema": {
            "type": "object",
            "properties": {"wort": {"type": "string", "description": "Das zu zaehlende Wort"}},
            "required": ["wort"],
        },
    },
]


def call_tool(name, args):
    if name == "wetter":
        ort = args.get("ort", "unbekannt")
        return f"In {ort} sind es 17 Grad, bewoelkt, Wind aus West."
    if name == "wortlaenge":
        wort = args.get("wort", "")
        return f"Das Wort '{wort}' hat {len(wort)} Buchstaben."
    raise ValueError(f"Unbekanntes Tool: {name}")


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
        # Kein serverseitiger Push noetig - der Client faellt auf reines POST zurueck.
        self.send_response(405)
        self.send_header("Content-Length", "0")
        self.end_headers()

    def do_DELETE(self):
        self._send(204)

    def do_POST(self):
        laenge = int(self.headers.get("Content-Length", 0))
        nachricht = json.loads(self.rfile.read(laenge) or b"{}")

        methode = nachricht.get("method")
        anfrage_id = nachricht.get("id")
        print(f"REQ method={methode} id={anfrage_id}", flush=True)

        # Notifications haben keine Id und bekommen keine Antwort.
        if anfrage_id is None:
            self._send(202)
            return

        try:
            self._send(200, {"jsonrpc": "2.0", "id": anfrage_id, "result": self._result(methode, nachricht)})
        except Exception as fehler:  # noqa: BLE001 - Fehler gehoert als JSON-RPC-Error zurueck
            self._send(200, {
                "jsonrpc": "2.0",
                "id": anfrage_id,
                "error": {"code": -32603, "message": str(fehler)},
            })

    def _result(self, methode, nachricht):
        if methode == "initialize":
            angefragt = nachricht.get("params", {}).get("protocolVersion", PROTOCOL_VERSION)
            print(f"    Client spricht Protokoll {angefragt}", flush=True)
            return {
                "protocolVersion": angefragt,
                "capabilities": {"tools": {"listChanged": False}},
                "serverInfo": {"name": "mock-mcp-server", "version": "1.0.0"},
            }
        if methode == "tools/list":
            return {"tools": TOOLS}
        if methode == "tools/call":
            params = nachricht.get("params", {})
            name = params.get("name")
            args = params.get("arguments", {})
            print(f"    tools/call {name} {args}", flush=True)
            return {"content": [{"type": "text", "text": call_tool(name, args)}], "isError": False}
        if methode == "ping":
            return {}
        raise ValueError(f"Unbekannte Methode: {methode}")

    def log_message(self, *args):
        pass  # eigenes Logging oben


if __name__ == "__main__":
    port = int(sys.argv[1]) if len(sys.argv) > 1 else 3333
    print(f"Mock-MCP-Server auf http://localhost:{port}/mcp", flush=True)
    HTTPServer(("0.0.0.0", port), Handler).serve_forever()
