import type { Status } from '../api/client'

/** The areas of the workspace: tiles on the home page, pills in the sidebar. */
export type Area = 'models' | 'agents' | 'tasks' | 'runs' | 'evals' | 'tools'

export const AREAS: {
  id: Area
  path: string
  label: string
  hint: string
  ready: boolean
  /** Provided by the system: visible, not editable. */
  readonly?: boolean
  count: (s: Status) => number
}[] = [
  { id: 'models', path: '/models', label: 'Modelle', hint: 'Endpunkt, Modell-Id, Schlüsselvariable', ready: true, count: (s) => s.models },
  { id: 'agents', path: '/agents', label: 'Agenten', hint: 'Modell + Systemprompt + Werkzeuge', ready: true, count: (s) => s.agents },
  { id: 'tasks', path: '/tasks', label: 'Aufgaben', hint: 'Eingabe + Evals', ready: true, count: (s) => s.tasks },
  { id: 'runs', path: '/runs', label: 'Läufe', hint: 'Matrix: Agent-Versionen × Aufgaben', ready: true, count: (s) => s.runs },
  { id: 'evals', path: '/evals', label: 'Evals', hint: 'Prüfungen, die eine Aufgabe nutzen kann', ready: true, readonly: true, count: (s) => s.evals },
  { id: 'tools', path: '/tools', label: 'Werkzeuge', hint: 'Eingebaut und über MCP-Server', ready: true, readonly: true, count: (s) => s.tools },
]
