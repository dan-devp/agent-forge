package com.devp.agent.run;

/** Endzustand eines Laufs. Ein abgebrochener Lauf ist kein Fehler, sondern ein Ergebnis. */
public enum RunStatus {

	/** Laeuft noch — nur waehrend der Ausfuehrung, in der DB ein Hinweis auf einen Absturz. */
	RUNNING,

	/** Modell hat geantwortet, ohne weitere Tools zu verlangen. */
	DONE,

	/** Budget aufgebraucht (Schritte, Tool-Aufrufe oder Zeit). Teilergebnis bleibt erhalten. */
	BUDGET_EXCEEDED,

	/** Aufruf fehlgeschlagen — Provider tot, Tool kaputt, Konfiguration falsch. */
	FAILED
}
