export type Status = {
  dataDir: string
  models: number
  agents: number
  tasks: number
  tools: number
  runs: number
  evals: number
  trainings: number
}

export type ProviderKey = 'openai' | 'anthropic' | 'custom' | 'local' | 'typesafe'

/** chat: writes text, drives agents. decision: answers typed questions, only judges. */
export type ModelKind = 'chat' | 'decision'

export type ProviderInfo = {
  key: ProviderKey
  label: string
  fixedBaseUrl: string | null
  defaultKeyEnv: string | null
  local: boolean
  kind: ModelKind
}

export type ModelView = {
  id: string
  version: number
  name: string
  description: string | null
  provider: ProviderKey
  baseUrl: string
  modelId: string
  apiKeyEnv: string | null
  keySet: boolean
  temperature: number
  local: boolean
  kind: ModelKind
  configured: boolean
  problem: string | null
}

export type ModelDetail = {
  model: ModelView
  versions: number[]
  runs: number
  usedByAgents: string[]
}

export type ModelRequest = {
  id?: string
  name: string
  description: string | null
  provider: ProviderKey
  baseUrl: string | null
  modelId: string
  apiKeyEnv: string | null
  temperature: number
}

export type SecretNames = {
  prefix: string
  names: string[]
}

type Problem = { title?: string; detail?: string }

async function request<T>(url: string, init?: RequestInit): Promise<T> {
  const res = await fetch(url, {
    ...init,
    headers: { 'Content-Type': 'application/json', ...(init?.headers ?? {}) },
  })
  if (!res.ok) {
    let message = `${res.status} ${res.statusText}`
    try {
      const problem = (await res.json()) as Problem
      if (problem.detail) message = problem.detail
      else if (problem.title) message = problem.title
    } catch {
      // No ProblemDetail body — the status line is all we have.
    }
    throw new Error(message)
  }
  if (res.status === 204) return undefined as T
  const text = await res.text()
  if (!text) return undefined as T
  const type = res.headers.get('content-type') ?? ''
  return (type.includes('json') ? JSON.parse(text) : text) as T
}

export const api = {
  status: () => request<Status>('/api/status'),

  models: () => request<ModelView[]>('/api/models'),
  model: (id: string) => request<ModelDetail>(`/api/models/${encodeURIComponent(id)}`),
  modelVersion: (id: string, version: number) =>
    request<ModelView>(`/api/models/${encodeURIComponent(id)}/versions/${version}`),
  modelSource: (id: string, version: number) =>
    request<string>(`/api/models/${encodeURIComponent(id)}/versions/${version}/source`),
  createModel: (body: ModelRequest) => request<ModelView>('/api/models', { method: 'POST', body: JSON.stringify(body) }),
  updateModel: (id: string, body: ModelRequest) =>
    request<ModelView>(`/api/models/${encodeURIComponent(id)}`, { method: 'PUT', body: JSON.stringify(body) }),
  deleteModel: (id: string) => request<void>(`/api/models/${encodeURIComponent(id)}`, { method: 'DELETE' }),
  reloadModels: () => request<ModelView[]>('/api/models/reload', { method: 'POST' }),

  secrets: () => request<SecretNames>('/api/secrets'),
  providers: () => request<ProviderInfo[]>('/api/providers'),
}

export type ToolInfo = { name: string; description: string; source: 'builtin' | 'mcp' }

export type ToolList = {
  tools: ToolInfo[]
  servers: { id: string; label: string; transport: string; target: string; enabled: boolean; connected: boolean; toolCount: number; error: string | null }[]
}

/** `tools` is "all", "none" or a list of names, as in the file. */
export type ToolAccess = 'all' | 'none' | string[]

export type AgentView = {
  id: string
  version: number
  name: string
  description: string | null
  model: string
  modelExists: boolean
  tools: ToolAccess
  effectiveTools: string[]
  missingTools: string[]
  systemPrompt: string
}

export type AgentDetail = {
  agent: AgentView
  versions: number[]
  runs: number
}

export type AgentRequest = {
  id?: string
  name: string
  description: string | null
  model: string
  tools: ToolAccess
  systemPrompt: string
}

export const agentsApi = {
  list: () => request<AgentView[]>('/api/agents'),
  get: (id: string) => request<AgentDetail>(`/api/agents/${encodeURIComponent(id)}`),
  version: (id: string, version: number) => request<AgentView>(`/api/agents/${encodeURIComponent(id)}/versions/${version}`),
  source: (id: string, version: number) =>
    request<string>(`/api/agents/${encodeURIComponent(id)}/versions/${version}/source`),
  create: (body: AgentRequest) => request<AgentView>('/api/agents', { method: 'POST', body: JSON.stringify(body) }),
  update: (id: string, body: AgentRequest) =>
    request<AgentView>(`/api/agents/${encodeURIComponent(id)}`, { method: 'PUT', body: JSON.stringify(body) }),
  remove: (id: string) => request<void>(`/api/agents/${encodeURIComponent(id)}`, { method: 'DELETE' }),
  reload: () => request<AgentView[]>('/api/agents/reload', { method: 'POST' }),
  tools: () => request<ToolList>('/api/tools'),
}

/** MODEL: chat models only. JUDGE_MODEL: any model that can judge. LIST: one entry per line. */
export type EvalParamType = 'STRING' | 'TEXT' | 'NUMBER' | 'BOOLEAN' | 'MODEL' | 'JUDGE_MODEL' | 'LIST' | 'TOOL'

export type EvalParam = {
  name: string
  label: string
  type: EvalParamType
  required: boolean
  defaultValue: string | number | boolean | null
  description: string
}

export type EvalType = {
  type: string
  label: string
  description: string
  params: EvalParam[]
}

export const catalogApi = {
  evals: () => request<EvalType[]>('/api/evals'),
  tools: () => request<ToolList>('/api/tools'),
  reloadTools: () => request<ToolList>('/api/tools/reload', { method: 'POST' }),
}

export type EvalConfig = { type: string; params: Record<string, unknown> }

export type Task = {
  id: string
  version: number
  name: string
  description: string | null
  maxToolCalls: number | null
  evals: EvalConfig[]
  input: string
}

export type TaskDetail = { task: Task; versions: number[]; runs: number }

export type TaskRequest = {
  id?: string
  name: string
  description: string | null
  maxToolCalls: number | null
  evals: EvalConfig[]
  input: string
}

export const tasksApi = {
  list: () => request<Task[]>('/api/tasks'),
  get: (id: string) => request<TaskDetail>(`/api/tasks/${encodeURIComponent(id)}`),
  version: (id: string, version: number) => request<Task>(`/api/tasks/${encodeURIComponent(id)}/versions/${version}`),
  source: (id: string, version: number) =>
    request<string>(`/api/tasks/${encodeURIComponent(id)}/versions/${version}/source`),
  create: (body: TaskRequest) => request<Task>('/api/tasks', { method: 'POST', body: JSON.stringify(body) }),
  update: (id: string, body: TaskRequest) =>
    request<Task>(`/api/tasks/${encodeURIComponent(id)}`, { method: 'PUT', body: JSON.stringify(body) }),
  remove: (id: string) => request<void>(`/api/tasks/${encodeURIComponent(id)}`, { method: 'DELETE' }),
  reload: () => request<Task[]>('/api/tasks/reload', { method: 'POST' }),
}

export type RunStatus = 'QUEUED' | 'RUNNING' | 'DONE' | 'BUDGET_EXCEEDED' | 'FAILED' | 'CANCELLED'

export type RunStep = {
  seq: number
  type: 'MODEL' | 'TOOL'
  name: string | null
  input: string | null
  output: string | null
  tokens: number | null
  durationMs: number | null
  error: string | null
  createdAt: string
}

export type RunEval = {
  index: number
  type: string
  /** the part this verdict is about, e.g. a judge's question */
  subject: string | null
  passed: boolean
  rationale: string | null
  model: string | null
  tokens: number | null
  durationMs: number | null
  createdAt: string
}

export type Run = {
  id: string
  batchId: string | null
  taskId: string | null
  taskVersion: number | null
  agentId: string
  agentVersion: number
  modelId: string
  modelVersion: number
  modelName: string | null
  temperature: number
  systemPrompt: string | null
  input: string | null
  tools: string[]
  status: RunStatus
  answer: string | null
  error: string | null
  steps: number
  totalTokens: number | null
  durationMs: number | null
  passed: boolean | null
  queuedAt: string | null
  startedAt: string | null
  finishedAt: string | null
  trace: RunStep[]
  evals: RunEval[]
}

export type SeriesRun = {
  id: string
  batchId: string | null
  status: RunStatus
  passed: boolean | null
  taskVersion: number | null
  totalTokens: number | null
  durationMs: number | null
  /** start, or queueing time while it waits */
  at: string | null
  /** start of the answer, or the error */
  preview: string | null
  verdicts: Verdict[]
}

export type Verdict = { index: number; type: string; subject: string | null; passed: boolean; rationale: string | null }

/** Agent version x task; runs is its run series. */
export type Pair = { taskId: string; taskVersions: number[]; modelVersions: number[]; runs: SeriesRun[] }
export type MatrixRow = { agentVersion: number; pairs: Pair[] }
export type Matrix = { agentId: string; currentVersion: number; modelId: string; tasks: string[]; rows: MatrixRow[] }

export type Batch = {
  id: string
  agentId: string
  agentVersion: number
  taskIds: string[]
  repetitions: number
  runIds: string[]
}

export const runsApi = {
  list: (filter: { task?: string; agent?: string } = {}) => {
    const q = new URLSearchParams()
    if (filter.task) q.set('task', filter.task)
    if (filter.agent) q.set('agent', filter.agent)
    const s = q.toString()
    return request<Run[]>(`/api/runs${s ? '?' + s : ''}`)
  },
  get: (id: string) => request<Run>(`/api/runs/${id}`),
  start: (body: { agent: string; agentVersion?: number; task?: string; input?: string }) =>
    request<Run>('/api/runs', { method: 'POST', body: JSON.stringify(body) }),
  batch: (body: { agent: string; agentVersion?: number; tasks?: string[]; repetitions: number }) =>
    request<Batch>('/api/runs/batch', { method: 'POST', body: JSON.stringify(body) }),
  batchRuns: (id: string) => request<Run[]>(`/api/runs/batch/${id}`),
  cancelBatch: (id: string) => request<{ cancelled: number }>(`/api/runs/batch/${id}/cancel`, { method: 'POST' }),
  cancel: (id: string) => request<{ cancelled: boolean }>(`/api/runs/${id}/cancel`, { method: 'POST' }),
  matrix: (agent: string) => request<Matrix>(`/api/runs/matrix?agent=${encodeURIComponent(agent)}`),
}

export type Approval = 'GATE' | 'MANUAL'

export type Training = {
  id: string
  version: number
  name: string
  description: string | null
  agentId: string
  coachId: string
  train: string[]
  holdout: string[]
  repetitions: number
  maxSteps: number
  maxTokens: number | null
  approval: Approval
  /** what the coach should aim for, read by the coach */
  goal: string | null
}

export type SessionStatus = 'RUNNING' | 'WAITING' | 'DONE' | 'STOPPED' | 'BUDGET_EXCEEDED' | 'FAILED'
export type Decision = 'PENDING' | 'ACCEPTED' | 'REJECTED'

export type Score = { trainPassed: number; trainTotal: number; holdoutPassed: number; holdoutTotal: number }

/** Step 0 is the baseline; every later step is one coach proposal, measured and decided. */
export type TrainingStep = {
  seq: number
  baseVersion: number | null
  agentVersion: number | null
  batchId: string | null
  score: Score | null
  runTokens: number | null
  coachInput: string | null
  coachOutput: string | null
  rationale: string | null
  coachModel: string | null
  coachTokens: number | null
  coachDurationMs: number | null
  gate: Decision | null
  decision: Decision
  decidedBy: string | null
  reason: string | null
  createdAt: string
  finishedAt: string | null
}

export type Session = {
  id: string
  trainingId: string
  trainingVersion: number
  agentId: string
  coachId: string
  coachVersion: number
  approval: Approval
  repetitions: number
  maxSteps: number
  maxTokens: number | null
  train: string[]
  holdout: string[]
  status: SessionStatus
  startVersion: number
  bestVersion: number | null
  finalVersion: number | null
  totalTokens: number
  error: string | null
  note: string | null
  startedAt: string
  finishedAt: string | null
  steps: TrainingStep[]
}

export type TrainingView = { training: Training; lastSession: Session | null }
export type TrainingDetail = { training: Training; versions: number[]; sessions: Session[] }

export type TrainingRequest = {
  id?: string
  name: string
  description: string | null
  agent: string
  coach: string
  train: string[]
  holdout: string[]
  repetitions: number
  maxSteps: number
  maxTokens: number | null
  approval: 'gate' | 'manual'
  goal: string | null
}

export const trainingsApi = {
  list: () => request<TrainingView[]>('/api/trainings'),
  get: (id: string) => request<TrainingDetail>(`/api/trainings/${encodeURIComponent(id)}`),
  version: (id: string, version: number) => request<Training>(`/api/trainings/${encodeURIComponent(id)}/versions/${version}`),
  source: (id: string, version: number) =>
    request<string>(`/api/trainings/${encodeURIComponent(id)}/versions/${version}/source`),
  create: (body: TrainingRequest) => request<Training>('/api/trainings', { method: 'POST', body: JSON.stringify(body) }),
  update: (id: string, body: TrainingRequest) =>
    request<Training>(`/api/trainings/${encodeURIComponent(id)}`, { method: 'PUT', body: JSON.stringify(body) }),
  remove: (id: string) => request<void>(`/api/trainings/${encodeURIComponent(id)}`, { method: 'DELETE' }),
  reload: () => request<TrainingView[]>('/api/trainings/reload', { method: 'POST' }),
  /** Without a start version from the agent's current one. */
  start: (id: string, startVersion?: number) =>
    request<Session>(`/api/trainings/${encodeURIComponent(id)}/sessions`, {
      method: 'POST',
      body: JSON.stringify(startVersion === undefined ? {} : { startVersion }),
    }),
  session: (id: string) => request<Session>(`/api/sessions/${id}`),
  stop: (id: string) => request<{ stopping: boolean }>(`/api/sessions/${id}/stop`, { method: 'POST' }),
  decide: (id: string, seq: number, accept: boolean) =>
    request<TrainingStep>(`/api/sessions/${id}/steps/${seq}/decision`, { method: 'POST', body: JSON.stringify({ accept }) }),
}
