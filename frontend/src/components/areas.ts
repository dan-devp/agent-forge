import type { Status } from '../api/client'
import type { Kind } from './kinds'

/** The areas of the workspace: steps on the home page, pills in the sidebar. */
export type Area = 'models' | 'agents' | 'tasks' | 'trainings' | 'runs' | 'evals' | 'tools'

/** Build the blocks, measure them, look things up: the order a newcomer works in. */
export type AreaGroup = 'build' | 'measure' | 'lookup'

export const GROUP_LABEL: Record<AreaGroup, string> = {
  build: 'Bauen',
  measure: 'Messen',
  lookup: 'Nachschlagen',
}

export const AREAS: {
  id: Area
  kind: Kind
  path: string
  label: string
  group: AreaGroup
  /** What it is, in one sentence a newcomer understands. */
  hint: string
  /** What to do there first, for the home page's next step. */
  action: string
  ready: boolean
  /** Provided by the system: visible, not editable. */
  readonly?: boolean
  count: (s: Status) => number
}[] = [
  {
    id: 'models',
    kind: 'model',
    path: '/models',
    label: 'Models',
    group: 'build',
    hint: 'Woher die Antworten kommen: ein Anbieter wie OpenAI oder ein lokales Model.',
    action: 'Model anlegen',
    ready: true,
    count: (s) => s.models,
  },
  {
    id: 'agents',
    kind: 'agent',
    path: '/agents',
    label: 'Agents',
    group: 'build',
    hint: 'Ein Model mit Anweisungen (Systemprompt) und den Tools, die es benutzen darf.',
    action: 'Agent bauen',
    ready: true,
    count: (s) => s.agents,
  },
  {
    id: 'tasks',
    kind: 'task',
    path: '/tasks',
    label: 'Tasks',
    group: 'build',
    hint: 'Eine Frage an den Agent und die Prüfungen, an denen man eine gute Antwort erkennt.',
    action: 'Task schreiben',
    ready: true,
    count: (s) => s.tasks,
  },
  {
    id: 'runs',
    kind: 'run',
    path: '/runs',
    label: 'Runs',
    group: 'measure',
    hint: 'Den Agent die Tasks lösen lassen und sehen, welche Version wie oft besteht.',
    action: 'Runs starten',
    ready: true,
    count: (s) => s.runs,
  },
  {
    id: 'trainings',
    kind: 'training',
    path: '/trainings',
    label: 'Trainings',
    group: 'measure',
    hint: 'Ein Coach schreibt den Systemprompt um, ein Gate behält nur, was messbar besser ist.',
    action: 'Training anlegen',
    ready: true,
    count: (s) => s.trainings,
  },
  {
    id: 'evals',
    kind: 'eval',
    path: '/evals',
    label: 'Evals',
    group: 'lookup',
    hint: 'Welche Prüfungen ein Task benutzen kann.',
    action: 'Evals ansehen',
    ready: true,
    readonly: true,
    count: (s) => s.evals,
  },
  {
    id: 'tools',
    kind: 'tool',
    path: '/tools',
    label: 'Tools',
    group: 'lookup',
    hint: 'Was ein Agent aufrufen kann: eingebaut oder über MCP-Server.',
    action: 'Tools ansehen',
    ready: true,
    readonly: true,
    count: (s) => s.tools,
  },
]
