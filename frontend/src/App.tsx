import { useEffect, useState } from 'react'
import {
  api,
  type AgentRun,
  type ChatResult,
  type SoulDetail,
  type SoulListResponse,
  type StatusResponse,
  type ToolListResponse,
} from './api/client'
import './App.css'

const STATUS_TEXT: Record<AgentRun['status'], string> = {
  RUNNING: 'läuft',
  DONE: 'fertig',
  BUDGET_EXCEEDED: 'Budget aufgebraucht',
  FAILED: 'fehlgeschlagen',
}

function App() {
  const [status, setStatus] = useState<StatusResponse | null>(null)
  const [provider, setProvider] = useState('')
  const [souls, setSouls] = useState<SoulListResponse | null>(null)
  const [soul, setSoul] = useState('')
  const [soulDetail, setSoulDetail] = useState<SoulDetail | null>(null)
  const [promptOffen, setPromptOffen] = useState(false)
  const [tools, setTools] = useState<ToolListResponse | null>(null)
  const [goal, setGoal] = useState('Welcher Wochentag ist heute, und wie oft passt 7 in 100?')
  const [run, setRun] = useState<AgentRun | null>(null)
  const [message, setMessage] = useState('Stell dich in einem Satz vor.')
  const [result, setResult] = useState<ChatResult | null>(null)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    api
      .status()
      .then((s) => {
        setStatus(s)
        // Bevorzugt den Default, sonst den ersten nutzbaren Provider.
        const preferred =
          s.providers.find((p) => p.isDefault && p.configured) ??
          s.providers.find((p) => p.configured) ??
          s.providers[0]
        setProvider(preferred?.id ?? '')
      })
      .catch((e: Error) => setError(e.message))

    api
      .souls()
      .then((s) => {
        setSouls(s)
        setSoul(s.activeSoul)
      })
      .catch((e: Error) => setError(e.message))

    api.tools().then(setTools).catch((e: Error) => setError(e.message))
  }, [])

  // Detail (inkl. gerendertem Prompt) immer zur aktuell gewaehlten SOUL nachladen.
  useEffect(() => {
    if (!soul) return
    api
      .soul(soul)
      .then(setSoulDetail)
      .catch((e: Error) => setError(e.message))
  }, [soul])

  const selected = status?.providers.find((p) => p.id === provider)
  const gates = soulDetail?.soul.gates ?? []

  async function lauf(action: () => Promise<void>) {
    setBusy(true)
    setError(null)
    try {
      await action()
    } catch (e) {
      setError((e as Error).message)
    } finally {
      setBusy(false)
    }
  }

  const askAi = () =>
    lauf(async () => {
      setResult(null)
      setResult(await api.chat(message, provider, soul))
    })

  const startRun = () =>
    lauf(async () => {
      setRun(null)
      setRun(await api.startRun(goal, provider, soul))
    })

  const reloadTools = () =>
    lauf(async () => {
      setTools(await api.reloadTools())
    })

  return (
    <main className="app">
      <header>
        <h1>KI-Mitarbeiter — Prototyp</h1>
        <p className="status">
          {status ? `${status.providers.length} Provider` : 'Backend wird abgefragt …'}
          {souls && ` · ${souls.souls.length} SOUL(s)`}
          {tools && ` · ${tools.tools.length} Tool(s)`}
        </p>
      </header>

      <section className="card">
        <h2>1 · SOUL</h2>

        <div className="row providers">
          <select value={soul} onChange={(e) => setSoul(e.target.value)} disabled={!souls}>
            {souls?.souls.map((s) => (
              <option key={s.id} value={s.id}>
                {s.name}
                {s.rolle ? ` · ${s.rolle}` : ''} · v{s.version}
              </option>
            ))}
          </select>
          <select value={provider} onChange={(e) => setProvider(e.target.value)} disabled={!status}>
            {status?.providers.map((p) => (
              <option key={p.id} value={p.id}>
                {p.label} · {p.model}
                {p.configured ? '' : ' (nicht konfiguriert)'}
              </option>
            ))}
          </select>
          {selected && (
            <span className={selected.local ? 'badge local' : 'badge cloud'}>
              {selected.local ? 'lokal' : 'cloud'}
            </span>
          )}
        </div>

        {soulDetail && (
          <>
            {soulDetail.beschreibung && <p className="meta">{soulDetail.beschreibung}</p>}

            {gates.length > 0 && (
              <ul className="gates">
                {gates.map((g) => (
                  <li key={g.id}>
                    <span className={g.blockiert ? 'badge cloud' : 'badge local'}>
                      {g.blockiert ? 'blockierend' : 'Hinweis'}
                    </span>{' '}
                    {g.frage}
                  </li>
                ))}
              </ul>
            )}

            <button onClick={() => setPromptOffen(!promptOffen)}>
              {promptOffen ? 'Prompt verbergen' : 'Gerenderten Prompt zeigen'}
            </button>
            {promptOffen && <pre className="output prompt">{soulDetail.prompt}</pre>}
          </>
        )}
      </section>

      <section className="card">
        <h2>2 · Werkzeuge</h2>

        <div className="row">
          <button onClick={reloadTools} disabled={busy}>
            MCP-Server neu verbinden
          </button>
        </div>

        {tools?.servers.length === 0 && (
          <p className="meta">
            Kein MCP-Server registriert. Server unter <code>app.tools.mcp.&lt;id&gt;</code> eintragen.
          </p>
        )}

        {tools && tools.servers.length > 0 && (
          <ul className="gates">
            {tools.servers.map((s) => (
              <li key={s.id}>
                <span className={s.connected ? 'badge local' : 'badge cloud'}>
                  {s.enabled ? (s.connected ? `${s.toolCount} Tools` : 'nicht erreichbar') : 'aus'}
                </span>{' '}
                {s.label} · {s.transport} · <code>{s.target}</code>
                {s.error && <span className="fehler"> — {s.error}</span>}
              </li>
            ))}
          </ul>
        )}

        {tools && tools.tools.length > 0 && (
          <ul className="gates">
            {tools.tools.map((t) => (
              <li key={t.name}>
                <span className="badge">{t.source}</span> <code>{t.name}</code>
                {t.description && <span className="meta"> — {t.description}</span>}
              </li>
            ))}
          </ul>
        )}
      </section>

      <section className="card">
        <h2>3 · Lauf mit Werkzeugen</h2>

        <textarea
          value={goal}
          onChange={(e) => setGoal(e.target.value)}
          rows={3}
          placeholder="Auftrag an den Agenten"
        />
        <button onClick={startRun} disabled={busy || !goal.trim() || !provider}>
          {busy ? 'Agent arbeitet …' : 'Lauf starten'}
        </button>

        {run && (
          <>
            <p className="meta">
              <span className={run.status === 'DONE' ? 'badge local' : 'badge cloud'}>
                {STATUS_TEXT[run.status]}
              </span>{' '}
              SOUL {run.soulId} v{run.soulVersion} · {run.providerId} · {run.model ?? 'unbekannt'} ·{' '}
              {run.steps} Schritte · {run.durationMs} ms
              {run.totalTokens != null && ` · ${run.totalTokens} Tokens`}
            </p>

            {run.answer && <p className="output">{run.answer}</p>}
            {run.error && <p className="error">{run.error}</p>}

            <ol className="trace">
              {run.trace.map((s) => (
                <li key={s.nr}>
                  <span className={s.type === 'TOOL' ? 'badge cloud' : 'badge local'}>{s.type}</span>{' '}
                  <code>{s.name}</code>
                  {s.input && <div className="meta">ein: {s.input}</div>}
                  {s.output && <div className="meta">aus: {s.output}</div>}
                  {s.error && <div className="fehler">Fehler: {s.error}</div>}
                </li>
              ))}
            </ol>
          </>
        )}
      </section>

      <section className="card">
        <h2>4 · Einzelner Modellaufruf (ohne Werkzeuge)</h2>

        <textarea
          value={message}
          onChange={(e) => setMessage(e.target.value)}
          rows={2}
          placeholder="Nachricht an den Agenten"
        />
        <button onClick={askAi} disabled={busy || !message.trim() || !provider}>
          {busy ? 'Läuft …' : 'Fragen'}
        </button>

        {result && (
          <>
            <p className="output">{result.answer}</p>
            <p className="meta">
              {result.provider} · {result.model ?? 'unbekannt'} · SOUL {result.soul} v{result.soulVersion} ·{' '}
              {result.durationMs} ms
              {result.totalTokens != null && ` · ${result.totalTokens} Tokens`}
            </p>
          </>
        )}
      </section>

      {error && <p className="error">{error}</p>}
    </main>
  )
}

export default App
