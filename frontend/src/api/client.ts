export type Status = {
  dataDir: string
  models: number
  agents: number
  tasks: number
  tools: number
}

export type ModelView = {
  id: string
  version: number
  name: string
  description: string | null
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
  baseUrl: string
  modelId: string
  apiKeyEnv: string | null
  temperature: number
  local: boolean
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
}
