---
id: selbstpruefung
name: Selbstpruefung
version: 1
description: Prueft die Antwort vor der Abgabe gegen die Erfolgskriterien der Aufgabe und weist einmal zurueck.
strategies:
  - id: selbstpruefung
    max-rounds: 1
    reveal: true
---

# Zweck

Der billigste echte Hebel: nicht "denk nochmal nach", sondern dieselbe Regel pruefen, die der
Grader anlegen wird. Reine Selbstkritik laeuft in die Kohaerenz-Falle — der Agent stimmt sich
selbst zu. Eine Pruefung gegen ein externes Kriterium tut das nicht.

# Grenzen

Geprueft wird nur, was sich an der Antwort entscheiden laesst. Ob ein Werkzeug gerufen wurde,
steht im Trace, den es waehrend des Schreibens noch nicht gibt — solche Kriterien bleiben
aussen vor, statt ein falsches Urteil zu faellen.

Das Urteil selbst bleibt beim Grader. Diese Strategie bereitet vor, sie bewertet nicht.

# Parameter

`reveal: true` nennt dem Modell das verfehlte Kriterium — das Muster "Rubrik ist bekannt".
`reveal: false` sagt nur, dass etwas fehlt. Was besser traegt, entscheidet ein Experiment,
keine Meinung.
