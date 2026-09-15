package com.devp.agent.soul;

/**
 * Pflichtfrage, die der Agent vor einer Antwort fuer sich beantwortet.
 *
 * <p>Gates sind der Unterschied zwischen "sei praezise" als Wunsch und als Regel:
 * ein blockierendes Gate darf nicht mit einer Antwort ueberschrieben werden, die es
 * verletzt (gstack-Muster).
 *
 * @param blockiert true = bei negativer Antwort darf der Agent nicht liefern, sondern
 *                  muss nachfragen oder die Luecke benennen.
 */
public record SoulGate(String id, String frage, boolean blockiert) {
}
