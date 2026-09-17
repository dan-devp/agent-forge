package com.devp.agent.run;

/** Art eines Trace-Schritts. */
public enum StepType {

	/** Eine Modellrunde: entweder Tool-Anforderung oder die Abschlussantwort. */
	MODEL,

	/** Ein ausgefuehrter Tool-Aufruf inkl. Argumenten und Ergebnis. */
	TOOL
}
