import type { EvalConfig, EvalType } from '../api/client'

/**
 * Evals by intent: first what should be checked, then the two to four evals that do it. The
 * server's catalogue stays the source of every type, label and parameter; this only orders
 * it and adds an example. A type the guide does not know lands under "More".
 */
export type EvalGroup = { id: string; label: string; hint: string; types: string[] }

export const GROUPS: EvalGroup[] = [
  {
    id: 'content',
    label: 'Content of the answer',
    hint: 'What the answer must contain or must not contain.',
    types: ['contains', 'not-contains', 'equals', 'regex', 'not-regex', 'length'],
  },
  {
    id: 'tools',
    label: 'Tool use',
    hint: 'Whether and how often the Agent calls Tools — checked on the Trace, not on the answer.',
    types: ['tool-called', 'tool-not-called', 'max-tool-calls'],
  },
  {
    id: 'structure',
    label: 'Form and structure',
    hint: 'Whether the answer is valid JSON and what a field holds.',
    types: ['json-valid', 'json-path'],
  },
  {
    id: 'judge',
    label: 'Verdict by a Judge',
    hint: 'A Model rates the answer. Costs one call per Run.',
    types: ['judge-questions', 'llm-judge'],
  },
]

export const EXAMPLES: Record<string, string> = {
  contains: 'The answer must contain "capital".',
  'not-contains': 'No "I hope this helps" in the answer.',
  equals: 'The answer is exactly "Paris" and nothing else.',
  regex: 'The 14 stands as a word of its own: \\b14\\b',
  'not-regex': 'No time with seconds: \\d{2}:\\d{2}:\\d{2}',
  length: 'At most 40 words.',
  'tool-called': 'For the time, currentTime must be called.',
  'tool-not-called': 'No Tool for a knowledge question: *',
  'max-tool-calls': 'At most 2 Tool calls.',
  'json-valid': 'The answer is valid JSON.',
  'json-path': 'The JSON has items[0].price = 12.5',
  'judge-questions': '"Does the answer give the time in Berlin?" – one verdict per question.',
  'llm-judge': 'A criterion in prose, e.g. "The reasoning is sound."',
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
    : [...known, { group: { id: 'other', label: 'More', hint: 'Evals the guide does not know yet.', types: rest.map((t) => t.type) }, types: rest }]
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
