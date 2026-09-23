---
id: no-tool
version: 1
name: Ohne Tool
description: Reiner Text-Task. Prueft, dass der Agent kein Tool anfasst, wenn keines noetig ist.
evals:
  - type: tool-not-called
    tool: '*'
  - type: contains
    text: Hauptstadt
---
Was ist die Hauptstadt von Frankreich? Antworte in einem Satz, der das Wort "Hauptstadt" enthaelt.
