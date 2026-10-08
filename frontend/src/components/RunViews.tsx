import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router'
import { runsApi, type AgentView, type Run, type Task } from '../api/client'
import { Ref } from './Ref'

const OPEN_TEXT: Partial<Record<Run['status'], { text: string; tone: string }>> = {
  QUEUED: { text: 'queued', tone: '' },
  RUNNING: { text: 'running', tone: 'open' },
  BUDGET_EXCEEDED: { text: 'over budget', tone: 'fail' },
  FAILED: { text: 'error', tone: 'fail' },
  CANCELLED: { text: 'cancelled', tone: '' },
}

/** One pill per run: the verdict if there is one, otherwise what became of it. */
function RunPill({ r }: { r: Run }) {
  const s = OPEN_TEXT[r.status]
  if (s) return <span className={`pill ${s.tone}`}>{s.text}</span>
  if (r.passed === null) return <span className="pill">no checks</span>
  return <span className={`pill ${r.passed ? 'pass' : 'fail'}`}>{r.passed ? 'passed' : 'failed'}</span>
}

const isOpen = (r: Run) => r.status === 'QUEUED' || r.status === 'RUNNING'

function when(r: Run) {
  return new Date(r.queuedAt ?? r.startedAt ?? '').toLocaleString('en-US', {
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  })
}

/** Runs started together stay together: one group per batch, a single run on its own. */
type Group = { key: string; batch: boolean; runs: Run[] }

function groupByBatch(runs: Run[]): Group[] {
  const out: Group[] = []
  for (const r of runs) {
    const last = out.at(-1)
    if (r.batchId && last?.batch && last.key === r.batchId) last.runs.push(r)
    else out.push({ key: r.batchId ?? r.id, batch: r.batchId !== null, runs: [r] })
  }
  return out
}

/**
 * The flat list, newest first, filtered by agent and task, folded by batch: a start of
 * twelve runs is one line until opened. Polls while anything is open.
 */
export function HistoryView(p: {
  agents: AgentView[]
  tasks: Task[]
  agent: string
  onAgent: (id: string) => void
  onError: (m: string) => void
}) {
  const navigate = useNavigate()
  const [task, setTask] = useState('')
  const [runs, setRuns] = useState<Run[] | null>(null)
  const [opened, setOpened] = useState<Set<string>>(new Set())

  useEffect(() => {
    let stale = false
    let timer: number | null = null
    const load = () =>
      runsApi
        .list({ agent: p.agent || undefined, task: task || undefined })
        .then((list) => {
          if (stale) return
          setRuns(list)
          if (list.some(isOpen)) timer = window.setTimeout(load, 2000)
        })
        .catch((e: Error) => {
          if (!stale) p.onError(e.message)
        })
    load()
    return () => {
      stale = true
      if (timer) window.clearTimeout(timer)
    }
  }, [p.agent, task]) // eslint-disable-line react-hooks/exhaustive-deps

  const toggle = (key: string) => {
    const next = new Set(opened)
    if (next.has(key)) next.delete(key)
    else next.add(key)
    setOpened(next)
  }

  const groups = runs ? groupByBatch(runs) : []

  return (
    <section className="card kind t-run">
      <div className="card-head">
        <h2>History</h2>
        <select value={p.agent} onChange={(e) => p.onAgent(e.target.value)} style={{ width: 'auto' }}>
          <option value="">all Agents</option>
          {p.agents.map((a) => (
            <option key={a.id} value={a.id}>
              {a.name}
            </option>
          ))}
        </select>
        <select value={task} onChange={(e) => setTask(e.target.value)} style={{ width: 'auto' }}>
          <option value="">all Tasks</option>
          {p.tasks.map((t) => (
            <option key={t.id} value={t.id}>
              {t.name}
            </option>
          ))}
        </select>
        {runs && (
          <span className="sub small">
            {runs.length} Runs in {groups.length} start{groups.length === 1 ? '' : 's'}
          </span>
        )}
      </div>
      {runs === null ? (
        <p className="empty">loading …</p>
      ) : runs.length === 0 ? (
        <p className="empty">No Run yet.</p>
      ) : (
        <div className="rows">
          {groups.map((g) =>
            g.batch && g.runs.length > 1 ? (
              <BatchGroup key={g.key} group={g} open={opened.has(g.key)} onToggle={() => toggle(g.key)} onOpen={(id) => navigate(`/runs/${id}`)} />
            ) : (
              <RunRow key={g.key} r={g.runs[0]} onOpen={(id) => navigate(`/runs/${id}`)} />
            ),
          )}
        </div>
      )}
    </section>
  )
}

function BatchGroup(p: { group: Group; open: boolean; onToggle: () => void; onOpen: (id: string) => void }) {
  const runs = p.group.runs
  const first = runs[runs.length - 1]
  const done = runs.filter((r) => !isOpen(r) && r.status !== 'CANCELLED')
  const passed = done.filter((r) => r.passed).length
  const open = runs.filter(isOpen).length
  const agents = [...new Set(runs.map((r) => `${r.agentId}@${r.agentVersion}`))]
  const tasks = new Set(runs.map((r) => r.taskId ?? '')).size
  const tone = open > 0 ? 'open' : done.length === 0 ? '' : passed === done.length ? 'pass' : passed === 0 ? 'fail' : 'open'
  return (
    <div className="batch-group">
      <div className="row history-row" onClick={p.onToggle} role="button" tabIndex={0} onKeyDown={(e) => e.key === 'Enter' && p.onToggle()}>
        <div>
          <div className="line">
            {agents.map((a) => {
              const [id, v] = a.split('@')
              return <Ref key={a} kind="agent" id={id} version={Number(v)} />
            })}
            <span className="muted small">
              {runs.length} Runs · {tasks} Task{tasks === 1 ? '' : 's'}
            </span>
          </div>
          <div className="small faint">{when(first)}</div>
        </div>
        <div className="line">
          <span className={`pill mono ${tone}`}>
            {passed}/{done.length} passed
          </span>
          {open > 0 && <span className="pill open">{open} open</span>}
        </div>
        <span className="fold-mark muted small">{p.open ? 'collapse' : 'expand'}</span>
      </div>
      {p.open && (
        <div className="rows batch-runs">
          {runs.map((r) => (
            <RunRow key={r.id} r={r} onOpen={p.onOpen} nested />
          ))}
        </div>
      )}
    </div>
  )
}

function RunRow({ r, onOpen, nested = false }: { r: Run; onOpen: (id: string) => void; nested?: boolean }) {
  return (
    <div className={`row history-row ${nested ? 'nested' : ''}`} onClick={() => onOpen(r.id)} role="button" tabIndex={0} onKeyDown={(e) => e.key === 'Enter' && onOpen(r.id)}>
      <div>
        <div className="line">
          {r.taskId ? <Ref kind="task" id={r.taskId} version={r.taskVersion} /> : <span className="muted">free input</span>}
          {!nested && <Ref kind="agent" id={r.agentId} version={r.agentVersion} />}
        </div>
        {!nested && <div className="small faint">{when(r)}</div>}
      </div>
      <div className="line">
        <RunPill r={r} />
      </div>
      <div className="line mono small muted">
        {r.totalTokens !== null && <span>{r.totalTokens.toLocaleString('en-US')} tok</span>}
        {r.durationMs !== null && <span>{(r.durationMs / 1000).toFixed(1)} s</span>}
      </div>
    </div>
  )
}

/**
 * A free input against an agent, synchronous, no verdict. For getting a feel before a
 * task is written. The run is recorded like any other and marked as ad hoc.
 */
export function TryView(p: { agents: AgentView[]; agent: string; onError: (m: string) => void; onChanged: () => void }) {
  const navigate = useNavigate()
  const [chosen, setAgent] = useState('')
  const [input, setInput] = useState('')
  const [busy, setBusy] = useState(false)
  // No effect: until the user picks, the agent from the address (or the first one) is the default.
  const agent = chosen || p.agent || p.agents[0]?.id || ''
  const model = p.agents.find((a) => a.id === agent)?.model

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
    <section className="card kind t-run">
      <div className="card-head">
        <h2>Try it</h2>
        <span className="sub">A free question for an Agent, with no checks. The Run is still recorded.</span>
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
            <span>Input</span>
            <textarea className="prompt" value={input} onChange={(e) => setInput(e.target.value)} required placeholder="What time is it in Berlin?" />
          </label>
        </div>
        <div className="toolbar end">
          {model && (
            <span className="cost">
              <strong>1 Run</strong> with the Model <Ref kind="model" id={model} />
            </span>
          )}
          <button type="submit" className="btn primary" disabled={busy || !agent}>
            {busy ? 'running …' : 'Start Run'}
          </button>
        </div>
        {busy && <p className="hint">The Run is synchronous. Depending on the Model, it takes a few seconds to a few minutes.</p>}
      </form>
    </section>
  )
}
