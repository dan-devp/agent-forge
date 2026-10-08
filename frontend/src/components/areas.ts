import type { Status } from '../api/client'
import type { Kind } from './kinds'

/** The areas of the workspace: steps on the home page, pills in the sidebar. */
export type Area = 'models' | 'agents' | 'tasks' | 'trainings' | 'runs' | 'evals' | 'tools'

/** Build the blocks, measure them, look things up: the order a newcomer works in. */
export type AreaGroup = 'build' | 'measure' | 'lookup'

export const GROUP_LABEL: Record<AreaGroup, string> = {
  build: 'Build',
  measure: 'Measure',
  lookup: 'Reference',
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
    hint: 'Where the answers come from: a provider like OpenAI or a local Model.',
    action: 'Create a Model',
    ready: true,
    count: (s) => s.models,
  },
  {
    id: 'agents',
    kind: 'agent',
    path: '/agents',
    label: 'Agents',
    group: 'build',
    hint: 'A Model with instructions (system prompt) and the Tools it may use.',
    action: 'Build an Agent',
    ready: true,
    count: (s) => s.agents,
  },
  {
    id: 'tasks',
    kind: 'task',
    path: '/tasks',
    label: 'Tasks',
    group: 'build',
    hint: 'A question for the Agent and the checks that tell a good answer.',
    action: 'Write a Task',
    ready: true,
    count: (s) => s.tasks,
  },
  {
    id: 'runs',
    kind: 'run',
    path: '/runs',
    label: 'Runs',
    group: 'measure',
    hint: 'Let the Agent solve the Tasks and see which version passes how often.',
    action: 'Start Runs',
    ready: true,
    count: (s) => s.runs,
  },
  {
    id: 'trainings',
    kind: 'training',
    path: '/trainings',
    label: 'Trainings',
    group: 'measure',
    hint: 'A Coach rewrites the system prompt, a Gate keeps only what is measurably better.',
    action: 'Create a Training',
    ready: true,
    count: (s) => s.trainings,
  },
  {
    id: 'evals',
    kind: 'eval',
    path: '/evals',
    label: 'Evals',
    group: 'lookup',
    hint: 'Which checks a Task can use.',
    action: 'Browse Evals',
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
    hint: 'What an Agent can call: built in or from MCP servers.',
    action: 'Browse Tools',
    ready: true,
    readonly: true,
    count: (s) => s.tools,
  },
]
