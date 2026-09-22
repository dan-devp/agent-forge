import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router'
import { runsApi, type AgentView, type Run, type Task } from '../api/client'

const STATUS_TEXT: Record<Run['status'], { text: string; tone: string }> = {
  QUEUED: { text: 'wartet', tone: '' },
  RUNNING: { text: 'läuft', tone: 'open' },
  DONE: { text: 'fertig', tone: 'pass' },
  BUDGET_EXCEEDED: { text: 'Budget', tone: 'fail' },
  FAILED: { text: 'Fehler', tone: 'fail' },
  CANCELLED: { text: 'abgebrochen', tone: '' },
}

/** The flat list, newest first, filtered by agent and task. Polls while anything is open. */
export function HistoryView(p: { agents: AgentView[]; tasks: Task[]; onError: (m: string) => void }) {
  const navigate = useNavigate()
  const [agent, setAgent] = useState('')
  const [task, setTask] = useState('')
  const [runs, setRuns] = useState<Run[] | null>(null)

  useEffect(() => {
    let stale = false
    let timer: number | null = null
    const load = () =>
      runsApi
        .list({ agent: agent || undefined, task: task || undefined })
        .then((list) => {
          if (stale) return
          setRuns(list)
          if (list.some((r) => r.status === 'QUEUED' || r.status === 'RUNNING')) timer = window.setTimeout(load, 2000)
        })
        .catch((e: Error) => {
          if (!stale) p.onError(e.message)
        })
    load()
    return () => {
      stale = true
      if (timer) window.clearTimeout(timer)
    }
  }, [agent, task]) // eslint-disable-line react-hooks/exhaustive-deps

  return (
    <section className="card">
      <div className="card-head">
        <h2>Historie</h2>
        <select value={agent} onChange={(e) => setAgent(e.target.value)} style={{ width: 'auto' }}>
          <option value="">alle Agenten</option>
          {p.agents.map((a) => (
            <option key={a.id} value={a.id}>
              {a.name}
            </option>
          ))}
        </select>
        <select value={task} onChange={(e) => setTask(e.target.value)} style={{ width: 'auto' }}>
          <option value="">alle Aufgaben</option>
          {p.tasks.map((t) => (
            <option key={t.id} value={t.id}>
              {t.name}
            </option>
          ))}
        </select>
        {runs && <span className="sub mono small">{runs.length} Läufe</span>}
      </div>
      {runs === null ? (
        <p className="empty">lädt …</p>
      ) : runs.length === 0 ? (
        <p className="empty">Noch kein Lauf.</p>
      ) : (
        <div className="rows">
          {runs.map((r) => {
            const s = STATUS_TEXT[r.status]
            return (
              <div key={r.id} className="row" onClick={() => navigate(`/runs/${r.id}`)} role="button" tabIndex={0}>
                <div>
                  <div className="line">
                    <span className="mono">{r.taskId ?? 'freie Eingabe'}</span>
                    {r.taskVersion !== null && <span className="pill mono">v{r.taskVersion}</span>}
                  </div>
                  <div className="line small muted">
                    <span className="mono">{r.agentId}</span>
                    <span className="pill mono">v{r.agentVersion}</span>
                    <span className="faint">{new Date(r.queuedAt ?? r.startedAt ?? '').toLocaleString('de-DE')}</span>
                  </div>
                </div>
                <div className="line">
                  <span className={`pill ${s.tone}`}>{s.text}</span>
                  {r.passed !== null && (
                    <span className={`pill ${r.passed ? 'pass' : 'fail'}`}>{r.passed ? 'bestanden' : 'nicht bestanden'}</span>
                  )}
                </div>
                <div className="line mono small">
                  {r.totalTokens !== null && <span>{r.totalTokens} tok</span>}
                  {r.durationMs !== null && <span>{(r.durationMs / 1000).toFixed(1)} s</span>}
                </div>
              </div>
            )
          })}
        </div>
      )}
    </section>
  )
}

/**
 * A free input against an agent, synchronous, no verdict. For getting a feel before a
 * task is written. The run is recorded like any other and marked as ad hoc.
 */
export function TryView(p: { agents: AgentView[]; onError: (m: string) => void; onChanged: () => void }) {
  const navigate = useNavigate()
  const [chosen, setAgent] = useState('')
  const [input, setInput] = useState('')
  const [busy, setBusy] = useState(false)
  // No effect: until the user picks, the first agent is the default.
  const agent = chosen || p.agents[0]?.id || ''

  const start = async () => {
    setBusy(true)
    try {
      const run = await runsApi.start({ agent, input })
      p.onChanged()
      navigate(`/runs/${run.id}`)
    } catch (e) {
      p.onError((e as Error).message)
    } finally {
      setBusy(false)
    }
  }

  return (
    <section className="card">
      <div className="card-head">
        <h2>Ausprobieren</h2>
        <span className="sub">Freie Eingabe, ein Lauf, kein Urteil. Wird trotzdem aufgezeichnet.</span>
      </div>
      <form
        className="form"
        onSubmit={(e) => {
          e.preventDefault()
          start()
        }}
      >
        <div className="form-grid">
          <label className="field span-2">
            <span>Agent</span>
            <select value={agent} onChange={(e) => setAgent(e.target.value)} required>
              {p.agents.map((a) => (
                <option key={a.id} value={a.id}>
                  {a.name} · {a.id}
                </option>
              ))}
            </select>
          </label>
          <label className="field span-2">
            <span>Eingabe</span>
            <textarea className="prompt" value={input} onChange={(e) => setInput(e.target.value)} required placeholder="Wie spät ist es?" />
          </label>
        </div>
        <div className="toolbar end">
          <button type="submit" className="btn primary" disabled={busy || !agent}>
            {busy ? 'läuft …' : 'starten'}
          </button>
        </div>
        {busy && <p className="hint">Der Lauf läuft synchron. Je nach Modell dauert das ein paar Sekunden bis Minuten.</p>}
      </form>
    </section>
  )
}
