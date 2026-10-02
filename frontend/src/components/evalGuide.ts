import type { EvalConfig, EvalType } from '../api/client'

/**
 * Evals by intent: first what should be checked, then the two to four evals that do it. The
 * server's catalogue stays the source of every type, label and parameter; this only orders
 * it and adds an example. A type the guide does not know lands under "Weitere".
 */
export type EvalGroup = { id: string; label: string; hint: string; types: string[] }

export const GROUPS: EvalGroup[] = [
  {
    id: 'content',
    label: 'Inhalt der Antwort',
    hint: 'Was in der Antwort stehen muss oder nicht stehen darf.',
    types: ['contains', 'not-contains', 'equals', 'regex', 'not-regex', 'length'],
  },
  {
    id: 'tools',
    label: 'Tool-Nutzung',
    hint: 'Ob und wie oft der Agent Tools aufruft — geprüft am Trace, nicht an der Antwort.',
    types: ['tool-called', 'tool-not-called', 'max-tool-calls'],
  },
  {
    id: 'structure',
    label: 'Form und Struktur',
    hint: 'Ob die Antwort gültiges JSON ist und was in einem Feld steht.',
    types: ['json-valid', 'json-path'],
  },
  {
    id: 'judge',
    label: 'Urteil durch einen Judge',
    hint: 'Ein Model bewertet die Antwort. Kostet je Run einen Aufruf.',
    types: ['judge-questions', 'llm-judge'],
  },
]

export const EXAMPLES: Record<string, string> = {
  contains: 'Die Antwort muss „Hauptstadt“ enthalten.',
  'not-contains': 'Kein „Ich hoffe, das hilft“ in der Antwort.',
  equals: 'Die Antwort ist genau „Paris“ und sonst nichts.',
  regex: 'Die 14 steht als eigenes Wort da: \\b14\\b',
  'not-regex': 'Keine Uhrzeit mit Sekunden: \\d{2}:\\d{2}:\\d{2}',
  length: 'Höchstens 40 Wörter.',
  'tool-called': 'Für die Uhrzeit muss currentTime aufgerufen werden.',
  'tool-not-called': 'Bei einer Wissensfrage kein Tool: *',
  'max-tool-calls': 'Höchstens 2 Tool-Aufrufe.',
  'json-valid': 'Die Antwort ist gültiges JSON.',
  'json-path': 'Im JSON steht items[0].price = 12.5',
  'judge-questions': '„Nennt die Antwort die Uhrzeit in Berlin?“ – ein Urteil je Frage.',
  'llm-judge': 'Ein Kriterium in Prosa, z. B. „Die Begründung ist schlüssig.“',
}

export function groupOf(type: string): EvalGroup | null {
  return GROUPS.find((g) => g.types.includes(type)) ?? null
}

/** The catalogue sorted into the groups, unknown types in a last one. */
export function groupedTypes(types: EvalType[]): { group: EvalGroup; types: EvalType[] }[] {
  const known = GROUPS.map((group) => ({
    group,
    types: group.types.map((t) => types.find((x) => x.type === t)).filter((t): t is EvalType => t !== undefined),
  }))
  const rest = types.filter((t) => !groupOf(t.type))
  return rest.length === 0
    ? known
    : [...known, { group: { id: 'other', label: 'Weitere', hint: 'Evals, die die Anleitung noch nicht kennt.', types: rest.map((t) => t.type) }, types: rest }]
}

/** Required parameters without a value; a non-empty list means the eval cannot be saved yet. */
export function missingParams(config: EvalConfig, type: EvalType | undefined): string[] {
  if (!type) return []
  return type.params
    .filter((p) => p.required)
    .filter((p) => {
      const v = config.params[p.name]
      if (v === undefined || v === null) return true
      if (Array.isArray(v)) return v.length === 0
      return String(v).trim() === ''
    })
    .map((p) => p.label)
}

/** List parameters arrive as an array from the server and as text while being edited. */
export function listValue(v: unknown): string[] {
  if (Array.isArray(v)) return v.map(String).filter((x) => x.trim() !== '')
  if (typeof v === 'string') return v.split('\n').map((x) => x.trim()).filter(Boolean)
  return []
}
