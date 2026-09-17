---
id: mcp-research
name: Recherche ueber MCP
version: 1
description: Nutzt Tools eines registrierten MCP-Servers statt der eingebauten.
difficulty: 2
tags: [tools, mcp]
success:
  - type: tool-called
    value: weather
  - type: tool-called
    value: wordLength
  - type: regex
    value: '\b13\b'
    description: Werkzeugkiste hat 13 Buchstaben
---

# Goal

Wie ist das Wetter in Muenster, und wie viele Buchstaben hat das Wort Werkzeugkiste?
Nutze fuer beides die Tools.

# Prerequisite

Braucht einen MCP-Server mit den Tools `weather` und `wordLength` — z. B.
`tools/mock-mcp-server.py` mit `MCP_MOCK_ENABLED=true`. Ohne ihn hat der Agent die
Werkzeuge nicht und muss das sagen, statt zu erfinden.
