---
id: time-and-division
version: 1
name: Zeit und Rechnung
description: Zwei eingebaute Tools in einer Runde. Prueft, ob der Agent Tools nutzt, statt zu raten.
max-tool-calls: 4
evals:
  - type: tool-called
    tool: currentTime
  - type: tool-called
    tool: divide
  - type: regex
    pattern: '\b14\b'
---
Wie spaet ist es gerade in Berlin? Und was ergibt 100 geteilt durch 7, mit Rest?
Nenne beides in einem kurzen Satz.
