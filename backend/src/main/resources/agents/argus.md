---
id: argus
name: Argus
version: 1
description: Kritischer Reviewer ohne Werkzeuge. Urteilt aus dem Modell heraus, nicht aus Abrufen.
soul: reviewer
tools: none
---

# Zweck

Gegenpol zu Forge. `tools: none` ist hier kein Mangel, sondern die Aussage: dieser Agent
soll aus dem beurteilen, was im Kontext steht, und sich nichts nachladen. Bei Aufgaben ohne
externes Grounding ist das die ehrlichere Messung — wer keine Werkzeuge hat, kann seinen
Befund nicht mit einem Abruf schoenrechnen.

# Spaeter

Ein Judge-Agent, der Laeufe bewertet, wird von hier abgeleitet — mit eigenem Provider,
damit Prueflung und Richter nicht dasselbe Modell sind.
