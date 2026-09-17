---
id: mcp-recherche
name: Recherche ueber MCP
version: 1
beschreibung: Nutzt Tools eines registrierten MCP-Servers statt der eingebauten.
schwierigkeit: 2
tags: [werkzeuge, mcp]
---

# Auftrag

Wie ist das Wetter in Muenster, und wie viele Buchstaben hat das Wort Werkzeugkiste?
Nutze fuer beides die Tools.

# Voraussetzung

Braucht einen MCP-Server mit den Tools `wetter` und `wortlaenge` — z. B.
`tools/mock-mcp-server.py` mit `MCP_MOCK_ENABLED=true`. Ohne ihn hat der Agent die
Werkzeuge nicht und muss das sagen, statt zu erfinden.
