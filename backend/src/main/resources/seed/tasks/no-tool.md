---
id: no-tool
version: 1
name: Ohne Werkzeug
description: Reine Textaufgabe. Prueft, dass der Agent kein Werkzeug anfasst, wenn keines noetig ist.
evals:
  - type: tool-not-called
    tool: '*'
  - type: contains
    text: Hauptstadt
---
Was ist die Hauptstadt von Frankreich? Antworte in einem Satz, der das Wort "Hauptstadt" enthaelt.
