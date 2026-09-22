export type Status = {
  dataDir: string
  models: number
  agents: number
  tasks: number
  tools: number
  runs: number
}

export type ProviderKey = 'openai' | 'anthropic' | 'custom' | 'local'

export type ProviderInfo = {
  key: ProviderKey
  label: string
  fixedBaseUrl: string | null
  defaultKeyEnv: string | null
  local: boolean
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
