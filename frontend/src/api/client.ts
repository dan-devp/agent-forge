export type HelloResponse = {
  message: string
  timestamp: string
}

export type ProviderInfo = {
  id: string
  label: string
  model: string
  baseUrl: string | null
  local: boolean
  configured: boolean
  isDefault: boolean
}

export type StatusResponse = {
  defaultProvider: string
  providers: ProviderInfo[]
}

export type ChatResult = {
  answer: string
  provider: string
  model: string | null
  soul: string
  soulVersion: number
  totalTokens: number | null
  durationMs: number
}

export type SoulGate = {
  id: string
  question: string
  blocking: boolean
}

export type SoulSummary = {
  id: string
  name: string
  role: string | null
  version: number
  gates: SoulGate[]
  isActive: boolean
}

export type SoulListResponse = {
  activeSoul: string
  souls: SoulSummary[]
}

export type SoulDetail = {
  soul: SoulSummary
  description: string | null
  sections: Record<string, string>
  source: string
  prompt: string
}

export type RunStep = {
  seq: number
  type: 'MODEL' | 'TOOL' | 'VERIFY'
  name: string | null
  input: string | null
  output: string | null
  tokens: number | null
  durationMs: number | null
  error: string | null
  createdAt: string
}

export type RunScore = {
  grader: string
  score: number
  passed: boolean
  rationale: string | null
  createdAt: string
}

export type RunStatus = 'RUNNING' | 'DONE' | 'BUDGET_EXCEEDED' | 'VERIFICATION_FAILED' | 'FAILED'

export type AgentRun = {
  id: string
  goal: string
  taskId: string | null
  taskVersion: number | null
  agentId: string | null
  agentVersion: number | null
  soulId: string
  soulVersion: number
  profileId: string | null
  profileVersion: number | null
  trainingId: string | null
  providerId: string
  model: string | null
  temperature: number | null
  systemPrompt: string | null
  tools: string[]
  status: RunStatus
  answer: string | null
  error: string | null
  steps: number
  totalTokens: number | null
  durationMs: number | null
  startedAt: string
  finishedAt: string | null
  trace: RunStep[]
  scores: RunScore[]
}

export type TaskSummary = {
  id: string
  name: string
  version: number
  description: string | null
  difficulty: number
  tags: string[]
  soul: string | null
  provider: string | null
  profile: string | null
}

export type Criterion = {
  type: string
  value: string
  description: string | null
}

export type Task = TaskSummary & {
  goal: string
  success: Criterion[]
  maxToolCalls: number | null
  maxDurationMs: number | null
  sections: Record<string, string>
  source: string
}

export type StrategyConfig = {
  id: string
  params: Record<string, unknown>
}

export type ProfileSummary = {
  id: string
  name: string
  version: number
  description: string | null
  strategies: StrategyConfig[]
  isDefault: boolean
}

export type ProfileListResponse = {
  defaultProfile: string
  profiles: ProfileSummary[]
  availableStrategies: string[]
}

export type Training = {
  id: string
  experimentId: string | null
  taskId: string
  taskVersion: number
  agentId: string | null
  agentVersion: number | null
  soulId: string | null
  profileId: string | null
  providerId: string | null
  repetitions: number
  status: 'QUEUED' | 'RUNNING' | 'DONE' | 'CANCELLED' | 'FAILED'
  completed: number
  error: string | null
  createdAt: string
  finishedAt: string | null
}

export type Aggregate = {
  count: number
  min: number
  mean: number
  max: number
}

export type RunPoint = {
  index: number
  runId: string
  status: RunStatus
  passed: boolean | null
  score: number | null
  tokens: number | null
  durationMs: number | null
  toolCalls: number
}

export type TrainingStatistics = {
  repetitions: number
  completed: number
  graded: number
  passed: number
  passRate: number
  passRateLower: number
  passRateUpper: number
  passAtK: boolean
  passHatK: boolean
  flaky: boolean
  firstHalfPassRate: number | null
  secondHalfPassRate: number | null
  firstPassAt: number | null
  timeline: RunPoint[]
  statusCounts: Record<string, number>
  tokens: Aggregate
  durationMs: Aggregate
  toolCalls: Aggregate
}

export type TrainingMemory = {
  id: number
  trainingId: string
  strategyId: string
  runId: string | null
  kind: string
  content: string
  createdAt: string
}

export type TrainingDetail = {
  training: Training
  statistics: TrainingStatistics
  runs: AgentRun[]
  memory: TrainingMemory[]
}

/** An agent: model, identity and tool allowlist as one versioned entity. */
export type AgentView = {
  id: string
  name: string
  version: number
  description: string | null
  provider: string | null
  soul: string | null
  toolMode: 'ALL' | 'NONE' | 'ALLOWLIST'
  tools: string[]
  effectiveTools: string[]
  missingTools: string[]
  sections: Record<string, string>
  source: string
}

export type AgentListResponse = {
  defaultAgent: string
  agents: AgentView[]
}

/** One agent, one task, several profiles — the comparison that makes a training measurable. */
/** The saved configuration. Starting it is a separate act, and possible many times. */
export type Plan = {
  id: string
  name: string
  description: string | null
  taskId: string
  agentId: string | null
  providerId: string | null
  profiles: string[]
  includeBaseline: boolean
  repetitions: number
  createdAt: string
  updatedAt: string
}

export type PlanDetail = {
  plan: Plan
  experiments: Experiment[]
}

export type PlanBody = {
  name: string
  description?: string
  task: string
  agent?: string
  provider?: string
  profiles: string[]
  includeBaseline: boolean
  repetitions: number
}

export type Experiment = {
  id: string
  planId: string | null
  name: string | null
  taskId: string
  taskVersion: number
  agentId: string | null
  agentVersion: number | null
  providerId: string | null
  repetitions: number
  status: 'RUNNING' | 'DONE' | 'CANCELLED' | 'FAILED'
  error: string | null
  createdAt: string
  finishedAt: string | null
}

export type ExperimentTraining = {
  training: Training
  statistics: TrainingStatistics
  memoryEntries: number
}

export type ExperimentDetail = {
  experiment: Experiment
  trainings: ExperimentTraining[]
}

export type ToolInfo = {
  name: string
  description: string | null
  source: string
}

export type McpServerInfo = {
  id: string
  label: string
  transport: 'STDIO' | 'SSE' | 'HTTP'
  target: string | null
  enabled: boolean
  connected: boolean
  toolCount: number
  error: string | null
}

export type ToolListResponse = {
  tools: ToolInfo[]
  servers: McpServerInfo[]
}

type ProblemDetail = {
  title?: string
  detail?: string
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(path, {
    headers: { 'Content-Type': 'application/json' },
    ...init,
  })

  if (!response.ok) {
    let message = `HTTP ${response.status}`
    try {
      const problem = (await response.json()) as ProblemDetail
      message = problem.detail ?? problem.title ?? message
    } catch {
      // Response was not JSON — the status line is enough.
    }
    throw new Error(message)
  }

  return (await response.json()) as T
}

export const api = {
  hello: (name: string) => request<HelloResponse>(`/api/hello?name=${encodeURIComponent(name)}`),
  status: () => request<StatusResponse>('/api/status'),
  chat: (message: string, provider: string, soul: string) =>
    request<ChatResult>('/api/chat', {
      method: 'POST',
      body: JSON.stringify({ message, provider, soul }),
    }),
  souls: () => request<SoulListResponse>('/api/souls'),
  soul: (id: string) => request<SoulDetail>(`/api/souls/${encodeURIComponent(id)}`),
  reloadSouls: () => request<SoulListResponse>('/api/souls/reload', { method: 'POST' }),
  tools: () => request<ToolListResponse>('/api/tools'),
  reloadTools: () => request<ToolListResponse>('/api/tools/reload', { method: 'POST' }),
  agents: () => request<AgentListResponse>('/api/agents'),
  reloadAgents: () => request<AgentListResponse>('/api/agents/reload', { method: 'POST' }),
  startRun: (body: { goal?: string; task?: string; agent?: string; provider?: string; soul?: string; profile?: string }) =>
    request<AgentRun>('/api/runs', { method: 'POST', body: JSON.stringify(body) }),
  profiles: () => request<ProfileListResponse>('/api/profiles'),
  reloadProfiles: () => request<ProfileListResponse>('/api/profiles/reload', { method: 'POST' }),
  tasks: () => request<TaskSummary[]>('/api/tasks'),
  task: (id: string) => request<Task>(`/api/tasks/${encodeURIComponent(id)}`),
  reloadTasks: () => request<TaskSummary[]>('/api/tasks/reload', { method: 'POST' }),
  runs: () => request<AgentRun[]>('/api/runs'),
  run: (id: string) => request<AgentRun>(`/api/runs/${encodeURIComponent(id)}`),
  startTraining: (body: { task: string; repetitions: number; agent?: string; provider?: string; soul?: string; profile?: string }) =>
    request<Training>('/api/trainings', { method: 'POST', body: JSON.stringify(body) }),
  training: (id: string) => request<TrainingDetail>(`/api/trainings/${encodeURIComponent(id)}`),
  trainings: (task?: string) => request<Training[]>(task ? `/api/trainings?task=${encodeURIComponent(task)}` : '/api/trainings'),
  startExperiment: (body: {
    name?: string
    task: string
    repetitions: number
    agent?: string
    provider?: string
    profiles: string[]
    includeBaseline: boolean
  }) => request<Experiment>('/api/experiments', { method: 'POST', body: JSON.stringify(body) }),
  experiments: (task?: string) =>
    request<Experiment[]>(task ? `/api/experiments?task=${encodeURIComponent(task)}` : '/api/experiments'),
  experiment: (id: string) => request<ExperimentDetail>(`/api/experiments/${encodeURIComponent(id)}`),
  cancelExperiment: (id: string) =>
    request<Experiment>(`/api/experiments/${encodeURIComponent(id)}/cancel`, { method: 'POST' }),
  cancelTraining: (id: string) =>
    request<Training>(`/api/trainings/${encodeURIComponent(id)}/cancel`, { method: 'POST' }),
  plans: () => request<Plan[]>('/api/plans'),
  plan: (id: string) => request<PlanDetail>(`/api/plans/${encodeURIComponent(id)}`),
  createPlan: (body: PlanBody) => request<Plan>('/api/plans', { method: 'POST', body: JSON.stringify(body) }),
  updatePlan: (id: string, body: PlanBody) =>
    request<Plan>(`/api/plans/${encodeURIComponent(id)}`, { method: 'PUT', body: JSON.stringify(body) }),
  startPlan: (id: string) => request<Experiment>(`/api/plans/${encodeURIComponent(id)}/start`, { method: 'POST' }),
  deletePlan: (id: string) =>
    fetch(`/api/plans/${encodeURIComponent(id)}`, { method: 'DELETE' }).then((r) => {
      if (!r.ok) throw new Error(`HTTP ${r.status}`)
    }),
}
