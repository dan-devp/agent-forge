---
id: time-and-division
name: Zeit und Rechnung
version: 1
description: Zwei eingebaute Tools in einer Runde. Prueft, ob der Agent Werkzeuge nutzt, statt zu raten.
difficulty: 1
tags: [tools, builtin]
max-tool-calls: 4
success:
  - type: tool-called
    value: currentTime
    description: Uhrzeit kommt aus dem Tool, nicht aus dem Kopf
  - type: tool-called
    value: divide
    description: Rechnung kommt aus dem Tool
  - type: regex
    value: '\b14\b'
    description: Ganzzahliger Quotient steht in der Antwort
---

# Goal

Wie spaet ist es gerade in UTC, und was ergibt 100 geteilt durch 7?
Nutze fuer beides die Tools und rate nicht.

# Background

Beide Tools liefern ein pruefbares Ergebnis. Ein Agent, der die Uhrzeit erfindet oder im
Kopf rechnet, faellt hier auf: die Kriterien verlangen beide Tool-Aufrufe im Trace. Die
Uhrzeit selbst laesst sich nicht deterministisch pruefen (Wanduhr), der Quotient schon.
