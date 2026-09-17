import { useEffect, useState } from 'react'
import {
  api,
  type AgentRun,
  type ChatResult,
  type ProfileListResponse,
  type SoulDetail,
  type SoulListResponse,
  type StatusResponse,
  type Task,
  type TaskSummary,
  type ToolListResponse,
} from './api/client'
import './App.css'

const STATUS_TEXT: Record<AgentRun['status'], string> = {
  RUNNING: 'läuft',
  DONE: 'fertig',
  BUDGET_EXCEEDED: 'Budget aufgebraucht',
  VERIFICATION_FAILED: 'Verifikation gescheitert',
  FAILED: 'fehlgeschlagen',
}

function App() {
  const [status, setStatus] = useState<StatusResponse | null>(null)
  const [provider, setProvider] = useState('')
  const [souls, setSouls] = useState<SoulListResponse | null>(null)
  const [soul, setSoul] = useState('')
  const [soulDetail, setSoulDetail] = useState<SoulDetail | null>(null)
  const [promptOpen, setPromptOpen] = useState(false)
  const [runPromptOpen, setRunPromptOpen] = useState(false)
  const [tools, setTools] = useState<ToolListResponse | null>(null)
  const [tasks, setTasks] = useState<TaskSummary[] | null>(null)
  const [profiles, setProfiles] = useState<ProfileListResponse | null>(null)
  const [profile, setProfile] = useState('')
  const [task, setTask] = useState('')
  const [taskDetail, setTaskDetail] = useState<Task | null>(null)
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
        // Prefer the default, otherwise the first usable provider.
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

    api
      .profiles()
      .then((p) => {
        setProfiles(p)
        setProfile(p.defaultProfile)
      })
      .catch((e: Error) => setError(e.message))

    api
      .tasks()
      .then((t) => {
        setTasks(t)
        setTask(t[0]?.id ?? '')
      })
      .catch((e: Error) => setError(e.message))
  }, [])

  // Load the goal text of the selected task — it is what the agent receives.
  useEffect(() => {
    if (!task) return
    api
      .task(task)
      .then(setTaskDetail)
      .catch((e: Error) => setError(e.message))
  }, [task])

  // Always load the detail (incl. rendered prompt) for the currently selected SOUL.
  useEffect(() => {
    if (!soul) return
    api
      .soul(soul)
      .then(setSoulDetail)
      .catch((e: Error) => setError(e.message))
  }, [soul])

  const selected = status?.providers.find((p) => p.id === provider)
  const gates = soulDetail?.soul.gates ?? []

  async function perform(action: () => Promise<void>) {
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
    perform(async () => {
      setResult(null)
      setResult(await api.chat(message, provider, soul))
    })

  const startRun = () =>
    perform(async () => {
      setRun(null)
      // Named task when one is selected — only that one can be evaluated later.
      setRun(await api.startRun(task ? { task, provider, soul, profile } : { goal, provider, soul, profile }))
    })

  const reloadTasks = () =>
    perform(async () => {
      const list = await api.reloadTasks()
      setTasks(list)
      if (!list.some((t) => t.id === task)) setTask(list[0]?.id ?? '')
    })

  const reloadProfiles = () =>
    perform(async () => {
      const p = await api.reloadProfiles()
      setProfiles(p)
      if (!p.profiles.some((x) => x.id === profile)) setProfile(p.defaultProfile)
    })

  const reloadTools = () =>
    perform(async () => {
      setTools(await api.reloadTools())
    })

  return (
    <main className="app">
      <header>
        <h1>KI-Mitarbeiter — Prototyp</h1>
        <p className="status">
          {status ? `${status.providers.length} Provider` : 'Backend wird abgefragt …'}
          {souls && ` · ${souls.souls.length} SOUL(s)`}
          {tasks && ` · ${tasks.length} Aufgabe(n)`}
          {profiles && ` · ${profiles.profiles.length} Profil(e)`}
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
                {s.role ? ` · ${s.role}` : ''} · v{s.version}
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
            {soulDetail.description && <p className="meta">{soulDetail.description}</p>}

            {gates.length > 0 && (
              <ul className="gates">
                {gates.map((g) => (
                  <li key={g.id}>
                    <span className={g.blocking ? 'badge cloud' : 'badge local'}>
                      {g.blocking ? 'blockierend' : 'Hinweis'}
                    </span>{' '}
                    {g.question}
                  </li>
                ))}
              </ul>
            )}

            <button onClick={() => setPromptOpen(!promptOpen)}>
              {promptOpen ? 'Prompt verbergen' : 'Gerenderten Prompt zeigen'}
            </button>
            {promptOpen && <pre className="output prompt">{soulDetail.prompt}</pre>}
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
        <h2>3 · Lauf</h2>

        <div className="row providers">
          <select value={task} onChange={(e) => setTask(e.target.value)} disabled={!tasks}>
            <option value="">— freier Auftrag —</option>
            {tasks?.map((t) => (
              <option key={t.id} value={t.id}>
                {t.name} · v{t.version} · Stufe {t.difficulty}
              </option>
            ))}
          </select>
          <button onClick={reloadTasks} disabled={busy}>
            Aufgaben neu laden
          </button>
        </div>

        <div className="row providers">
          <select value={profile} onChange={(e) => setProfile(e.target.value)} disabled={!profiles}>
            {profiles?.profiles.map((p) => (
              <option key={p.id} value={p.id}>
                Profil {p.name} · v{p.version} ·{' '}
                {p.strategies.length === 0 ? 'keine Strategie' : p.strategies.map((s) => s.id).join(', ')}
              </option>
            ))}
          </select>
          <button onClick={reloadProfiles} disabled={busy}>
            Profile neu laden
          </button>
        </div>

        {task && taskDetail?.id === task ? (
          <>
            {taskDetail.description && <p className="meta">{taskDetail.description}</p>}
            <pre className="output prompt">{taskDetail.goal}</pre>
            <p className="meta">
              {taskDetail.tags.map((tag) => (
                <span key={tag} className="badge">
                  {tag}
                </span>
              ))}
              {taskDetail.soul && ` · SOUL-Vorgabe: ${taskDetail.soul}`}
              {taskDetail.maxToolCalls != null && ` · max. ${taskDetail.maxToolCalls} Tool-Aufrufe`}
              {' · '}
              {taskDetail.source}
            </p>
          </>
        ) : (
          <textarea
            value={goal}
            onChange={(e) => setGoal(e.target.value)}
            rows={3}
            placeholder="Auftrag an den Agenten"
          />
        )}

        <button onClick={startRun} disabled={busy || (!task && !goal.trim()) || !provider}>
          {busy ? 'Agent arbeitet …' : 'Lauf starten'}
        </button>

        {run && (
          <>
            <p className="meta">
              <span className={run.status === 'DONE' ? 'badge local' : 'badge cloud'}>
                {STATUS_TEXT[run.status]}
              </span>{' '}
              {run.taskId ? `Aufgabe ${run.taskId} v${run.taskVersion} · ` : 'freier Auftrag · '}
              SOUL {run.soulId} v{run.soulVersion} · Profil {run.profileId ?? '–'} v{run.profileVersion ?? '–'} ·{' '}
              {run.providerId} · {run.model ?? 'unbekannt'} ·{' '}
              {run.steps} Schritte · {run.durationMs} ms
              {run.totalTokens != null && ` · ${run.totalTokens} Tokens`}
            </p>
            <p className="meta">
              {run.temperature != null && `Temperatur ${run.temperature} · `}
              {run.tools.length > 0 ? `Tools: ${run.tools.join(', ')}` : 'keine Tools angeboten'}
              {run.systemPrompt && (
                <>
                  {' · '}
                  <button className="link" onClick={() => setRunPromptOpen(!runPromptOpen)}>
                    {runPromptOpen ? 'System-Prompt verbergen' : 'System-Prompt zeigen'}
                  </button>
                </>
              )}
            </p>
            {runPromptOpen && run.systemPrompt && <pre className="output prompt">{run.systemPrompt}</pre>}

            {run.answer && <p className="output">{run.answer}</p>}
            {run.error && <p className="error">{run.error}</p>}

            {run.scores.length > 0 && (
              <ul className="scores">
                {run.scores.map((s) => (
                  <li key={s.grader}>
                    <span className={s.passed ? 'badge local' : 'badge cloud'}>
                      {s.passed ? 'bestanden' : 'nicht bestanden'}
                    </span>{' '}
                    {s.grader} · {Math.round(s.score * 100)} %
                    {s.rationale && <pre className="rationale">{s.rationale}</pre>}
                  </li>
                ))}
              </ul>
            )}

            <ol className="trace">
              {run.trace.map((s) => (
                <li key={s.seq}>
                  <span className={s.type === 'MODEL' ? 'badge local' : 'badge cloud'}>{s.type}</span>{' '}
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
