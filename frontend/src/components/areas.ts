import type { Status } from '../api/client'

/** The areas of the workspace: tiles on the home page, pills in the sidebar. */
export type Area = 'models' | 'agents' | 'tasks' | 'trainings' | 'runs' | 'evals' | 'tools'

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
  { id: 'models', path: '/models', label: 'Models', hint: 'Endpunkt, Model-Id, Schlüsselvariable', ready: true, count: (s) => s.models },
  { id: 'agents', path: '/agents', label: 'Agents', hint: 'Model + Systemprompt + Tools', ready: true, count: (s) => s.agents },
  { id: 'tasks', path: '/tasks', label: 'Tasks', hint: 'Eingabe + Evals', ready: true, count: (s) => s.tasks },
  { id: 'trainings', path: '/trainings', label: 'Trainings', hint: 'Coach verbessert den Agent, Gate prüft, Kurve zeigt es', ready: true, count: (s) => s.trainings },
  { id: 'runs', path: '/runs', label: 'Runs', hint: 'Matrix: Agent-Versionen × Tasks', ready: true, count: (s) => s.runs },
  { id: 'evals', path: '/evals', label: 'Evals', hint: 'Evals, die ein Task nutzen kann', ready: true, readonly: true, count: (s) => s.evals },
  { id: 'tools', path: '/tools', label: 'Tools', hint: 'Eingebaut und über MCP-Server', ready: true, readonly: true, count: (s) => s.tools },
]
