---
id: forge-tools
name: Forge (Positivliste)
version: 1
description: Identitaet Forge, aber nur Uhrzeit und Division. Beispiel fuer eine Werkzeug-Positivliste.
soul: forge
tools:
  - currentTime
  - divide
---

# Zweck

Zeigt, was eine Positivliste bedeutet: derselbe Kopf wie `forge`, aber nur die beiden
eingebauten Werkzeuge. Verbindet sich ein MCP-Server neu, waechst die Werkzeugmenge dieses
Agenten **nicht** mit — genau das ist der Punkt.

# Wirkung auf den Vergleich

Die angebotene Werkzeugmenge steht am Lauf (`agent_run.tools`). Zwei Trainings mit
unterschiedlicher Werkzeugmenge sind deshalb als solche erkennbar und nicht versehentlich
vergleichbar.
