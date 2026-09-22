---
id: reflexion
name: Reflexion
version: 1
description: Schreibt nach jedem misslungenen Lauf eine Lektion und legt die letzten fuenf in den naechsten Prompt.
strategies:
  - id: reflexion
    max-lessons: 5
    only-on-failure: true
    include-rationale: false
---

# Zweck

Die verbreitetste Form des Lernens ohne Gewichtsupdate: der Agent traegt mit, was schiefging,
und liest es beim naechsten Versuch. Gegen die flache Baseline derselben Aufgabe ist der
Effekt sofort sichtbar — oder eben nicht.

# Woher die Lektion stammt

Aus dem eigenen Trace: Status, benutzte Werkzeuge, Fehlertext, abgegebene Antwort. **Nicht**
aus der Begruendung des Graders. Die nennt die Kriterien, und wer die liest, lernt den
Loesungsschluessel statt der Aufgabe — die Quote steigt, ohne dass etwas dahintersteht.
`include-rationale: true` schaltet das bewusst um; dann gehoert eine zurueckgehaltene Aufgabe
zur Messung, sonst ist das Ergebnis wertlos.

# Kosten

Jede Lektion kostet in jedem folgenden Lauf Prompt-Tokens. `max-lessons` deckelt das; die
Kostenachse im Vergleich zeigt, ob der Aufschlag sich lohnt.
