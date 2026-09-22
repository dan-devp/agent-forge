---
id: beispiele
name: Beispiele
version: 1
description: Legt anerkannte Antworten frueherer Laeufe als Muster in den naechsten Prompt.
strategies:
  - id: beispiele
    max-examples: 2
    max-chars: 600
---

# Zweck

Das positive Gegenstueck zur Reflexion: nicht was schiefging, sondern was anerkannt wurde.
Welche der beiden Richtungen traegt, ist nicht offensichtlich — deshalb sind es zwei Profile
und nicht eine schlaue Strategie, die beides mischt.

# Auswahl

Aufgenommen wird nur, was **jeder** Grader bestanden hat. Eine halb anerkannte Antwort als
Muster wuerde die falsche Haelfte gleich mitlehren.

# Erwartung

Am Anfang eines Trainings passiert nichts: ohne bestandenen Lauf gibt es kein Beispiel. Der
Effekt kann also frueh nicht auftreten — bei einer Aufgabe, die nie gelingt, nie.
