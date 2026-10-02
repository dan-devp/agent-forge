---
id: rookie-learns
version: 1
name: Rookie lernt
description: Der Rookie startet fast ohne Prompt. Der Coach sieht time-and-division und no-tool, division bleibt Holdout.
agent: rookie
coach: coach
train:
  - time-and-division
  - no-tool
holdout:
  - division
repetitions: 3
max-steps: 4
max-tokens: 50000
approval: gate
---
Der Agent soll Tools nutzen, wo sie die Antwort sicher machen, und sie weglassen, wo sie nichts
beitragen. Antworten sind knapp und greifen die Begriffe der Frage auf.
