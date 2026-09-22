import type { Status } from '../api/client'

/** The areas of the workspace: tiles on the home page, pills in the sidebar. */
export type Area = 'models' | 'agents' | 'tasks' | 'runs'

export const AREAS: {
  id: Area
  path: string
  label: string
  hint: string
  ready: boolean
  count: (s: Status) => number
}[] = [
  { id: 'models', path: '/models', label: 'Modelle', hint: 'Endpunkt, Modell-Id, Schlüsselvariable', ready: true, count: (s) => s.models },
  { id: 'agents', path: '/agents', label: 'Agenten', hint: 'Modell + Systemprompt + Werkzeuge', ready: false, count: (s) => s.agents },
  { id: 'tasks', path: '/tasks', label: 'Aufgaben', hint: 'Eingabe + Evals', ready: false, count: (s) => s.tasks },
  { id: 'runs', path: '/runs', label: 'Läufe', hint: 'Starten, Trace, Urteile', ready: false, count: (s) => s.runs },
]
