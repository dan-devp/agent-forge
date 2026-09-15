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
  totalTokens: number | null
  durationMs: number
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
      // Antwort war kein JSON - Statuszeile reicht.
    }
    throw new Error(message)
  }

  return (await response.json()) as T
}

export const api = {
  hello: (name: string) => request<HelloResponse>(`/api/hello?name=${encodeURIComponent(name)}`),
  status: () => request<StatusResponse>('/api/status'),
  chat: (message: string, provider: string) =>
    request<ChatResult>('/api/chat', {
      method: 'POST',
      body: JSON.stringify({ message, provider }),
    }),
}
