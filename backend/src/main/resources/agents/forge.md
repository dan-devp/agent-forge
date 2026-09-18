---
id: forge
name: Forge
version: 1
description: Standard-Agent. Identitaet Forge, alle registrierten Werkzeuge, Modell aus der Vorauswahl.
soul: forge
tools: all
---

# Zweck

Der Agent, mit dem gearbeitet wird, solange nichts anderes gesagt ist. Er setzt kein
eigenes Modell durch (`provider` fehlt) — es gilt, was der Aufruf oder die Vorauswahl sagt.
So laesst sich derselbe Agent gegen ein lokales und ein Cloud-Modell fahren, ohne die Datei
zu aendern.

# Werkzeuge

`tools: all` heisst: jedes registrierte Werkzeug, auch neu verbundene MCP-Server. Das ist
der Zustand vor jeder Governance und fuer den Standard-Agenten Absicht. Ein Agent, der mit
echten Rechten arbeiten soll, bekommt stattdessen eine Positivliste.
