---
id: require-text-demo
name: Verifikator-Demo
version: 1
description: Prueft die beforeFinish-Schleife, keine Lernstrategie. Weist Antworten ohne das Wort "Rest" zurueck.
strategies:
  - id: require-text
    text: Rest
    max-rounds: 2
---

# Zweck

Fixture fuer die Rueckweisungsschleife: der Verifikator lehnt jede Antwort ab, die den
Text nicht enthaelt, gibt Feedback zurueck, und der Agent bekommt bis zu `max-rounds`
weitere Versuche. Danach endet der Lauf als `VERIFICATION_FAILED` mit vollstaendigem Trace.

Gegen den Mock-Provider (feste Antwort ohne "Rest") ist das Ergebnis vorhersagbar: der
erste Versuch plus `max-rounds` Wiederholungen werden abgelehnt, also `max-rounds + 1`
`VERIFY`-Schritte, dann Abbruch. Genau so laesst sich die Mechanik pruefen, ohne ein Modell
zu bezahlen.
