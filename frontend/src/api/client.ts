export type HelloResponse = {
  message: string
  timestamp: string
}

export type ProviderInfo = {
  id: string
  label: string
  model: string
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

export type AgentRun = {
  id: string
  goal: string
  taskId: string | null
  taskVersion: number | null
  soulId: string
  soulVersion: number
  profileId: string | null
  profileVersion: number | null
  providerId: string
  model: string | null
  temperature: number | null
  systemPrompt: string | null
  tools: string[]
  status: 'RUNNING' | 'DONE' | 'BUDGET_EXCEEDED' | 'VERIFICATION_FAILED' | 'FAILED'
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
  startRun: (body: { goal?: string; task?: string; provider: string; soul: string; profile: string }) =>
    request<AgentRun>('/api/runs', { method: 'POST', body: JSON.stringify(body) }),
  profiles: () => request<ProfileListResponse>('/api/profiles'),
  reloadProfiles: () => request<ProfileListResponse>('/api/profiles/reload', { method: 'POST' }),
  tasks: () => request<TaskSummary[]>('/api/tasks'),
  task: (id: string) => request<Task>(`/api/tasks/${encodeURIComponent(id)}`),
  reloadTasks: () => request<TaskSummary[]>('/api/tasks/reload', { method: 'POST' }),
  runs: () => request<AgentRun[]>('/api/runs'),
  run: (id: string) => request<AgentRun>(`/api/runs/${encodeURIComponent(id)}`),
}
