---
id: division
version: 1
name: Division mit Rest
description: Eine Rechnung, die man mit dem Tool sicher und im Kopf leicht falsch loest. Taugt als Holdout fuer Trainings auf time-and-division.
max-tool-calls: 3
evals:
  - type: tool-called
    tool: divide
  - type: regex
    pattern: '\b30\b'
---
Was ergibt 365 geteilt durch 12, mit Rest? Antworte in einem Satz.
