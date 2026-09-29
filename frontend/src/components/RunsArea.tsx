import { useEffect, useRef, useState } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router'
import {
  agentsApi,
  catalogApi,
  runsApi,
  tasksApi,
  type AgentView,
  type EvalType,
  type Matrix,
  type Pair,
  type SeriesRun,
  type Task,
} from '../api/client'
import { HistoryView, TryView } from './RunViews'

type Props = {
  onError: (message: string) => void
  onChanged: () => void
}

const POLL_MS = 2000

function isOpen(r: SeriesRun) {
  return r.status === 'QUEUED' || r.status === 'RUNNING'
}

/**
 * One sign per run. An error or a blown budget still counts as not passed, but it says
 * something else than a wrong answer: a 502 is no verdict on the prompt, a loop is.
 */
function mark(r: SeriesRun) {
  if (isOpen(r)) return '·'
  if (r.status === 'CANCELLED') return '–'
  if (r.status === 'FAILED') return '!'
  if (r.status === 'BUDGET_EXCEEDED') return '↻'
  return r.passed ? '✓' : '✗'
}

const STATUS_TEXT: Record<string, string> = {
  QUEUED: 'wartet',
  RUNNING: 'läuft',
  CANCELLED: 'abgebrochen',
  FAILED: 'Fehler',
  BUDGET_EXCEEDED: 'Budget überschritten',
}

function statusText(r: SeriesRun) {
  return STATUS_TEXT[r.status] ?? (r.passed ? 'bestanden' : 'nicht bestanden')
}

/** What k/n is made of, for the cell's tooltip. */
function breakdown(runs: SeriesRun[]) {
  if (runs.length === 0) return 'noch kein Run'
  const counts = new Map<string, number>()
  runs.forEach((r) => counts.set(statusText(r), (counts.get(statusText(r)) ?? 0) + 1))
  return [...counts].map(([k, v]) => `${v} ${k}`).join(' · ')
}

function when(at: string | null) {
  if (!at) return ''
  return new Date(at).toLocaleString('de-DE', { day: '2-digit', month: '2-digit', hour: '2-digit', minute: '2-digit', second: '2-digit' })
}

/** Cell colour for a pair's run series: all passed sage, none rosé, some sand; anything still open makes it busy. */
function tone(pair: Pair) {
  const done = pair.runs.filter((r) => !isOpen(r) && r.status !== 'CANCELLED')
  if (pair.runs.some(isOpen)) return 'busy'
  if (done.length === 0) return ''
  const passed = done.filter((r) => r.passed).length
  if (passed === done.length) return 'pass'
  if (passed === 0) return 'fail'
  return 'mixed'
}

/**
 * The picture of the training camp: one row per agent version, one column per task,
 * each cell a pair with its run series. Change the prompt, save, get a new row, run it,
 * compare with the row above.
 */
export function RunsArea(p: Props) {
  const [params, setParams] = useSearchParams()
  const navigate = useNavigate()
  const agentId = params.get('agent') ?? ''
  const view = params.get('view') ?? 'matrix'

  const [agents, setAgents] = useState<AgentView[]>([])
  const [tasks, setTasks] = useState<Task[]>([])
  const [matrix, setMatrix] = useState<Matrix | null>(null)
  const [n, setN] = useState(3)
  const [selected, setSelected] = useState<{ version: number; task: string } | null>(null)
  const [busy, setBusy] = useState(false)
  const [evalTypes, setEvalTypes] = useState<EvalType[]>([])
  const timer = useRef<number | null>(null)

  const fail = (e: Error) => p.onError(e.message)

  useEffect(() => {
    agentsApi
      .list()
      .then((list) => {
        setAgents(list)
        if (!agentId && list.length > 0) setParams({ agent: list[0].id, view }, { replace: true })
      })
      .catch(fail)
    tasksApi.list().then(setTasks).catch(fail)
    catalogApi.evals().then(setEvalTypes).catch(fail)
  }, []) // eslint-disable-line react-hooks/exhaustive-deps

  // Load the matrix for the chosen agent; keep polling while anything is open.
  useEffect(() => {
    if (!agentId) return
    let stale = false
    const load = () =>
      runsApi
        .matrix(agentId)
        .then((m) => {
          if (stale) return
          setMatrix(m)
          const open = m.rows.some((r) => r.pairs.some((c) => c.runs.some(isOpen)))
          if (open) timer.current = window.setTimeout(load, POLL_MS)
        })
        .catch((e: Error) => {
          if (!stale) fail(e)
        })
    load()
    return () => {
      stale = true
      if (timer.current) window.clearTimeout(timer.current)
    }
  }, [agentId]) // eslint-disable-line react-hooks/exhaustive-deps

  const refresh = () => {
    if (!agentId) return
    if (timer.current) window.clearTimeout(timer.current)
    runsApi
      .matrix(agentId)
      .then((m) => {
        setMatrix(m)
        const open = m.rows.some((r) => r.pairs.some((c) => c.runs.some(isOpen)))
        if (open) timer.current = window.setTimeout(refresh, POLL_MS)
      })
      .catch(fail)
  }

  async function perform(action: () => Promise<void>) {
    setBusy(true)
    try {
      await action()
      p.onChanged()
      refresh()
    } catch (e) {
      p.onError((e as Error).message)
    } finally {
      setBusy(false)
    }
  }

  const runRow = (version: number) =>
    perform(async () => {
      await runsApi.batch({ agent: agentId, agentVersion: version, repetitions: n })
    })

  const runPair = (version: number, task: string) =>
    perform(async () => {
      await runsApi.batch({ agent: agentId, agentVersion: version, tasks: [task], repetitions: n })
    })

  const openBatches = matrix
    ? [...new Set(matrix.rows.flatMap((r) => r.pairs.flatMap((c) => c.runs.filter(isOpen).map((x) => x.batchId))))].filter(
        (b): b is string => b !== null,
      )
    : []
  const openCount = matrix ? matrix.rows.reduce((s, r) => s + r.pairs.reduce((t, c) => t + c.runs.filter(isOpen).length, 0), 0) : 0

  const cancelAll = () =>
    perform(async () => {
      for (const b of openBatches) await runsApi.cancelBatch(b)
    })

  const agent = agents.find((a) => a.id === agentId)
  const taskName = (id: string) => tasks.find((t) => t.id === id)?.name ?? ''
  const selectedPair =
    selected && matrix
      ? matrix.rows.find((r) => r.agentVersion === selected.version)?.pairs.find((c) => c.taskId === selected.task) ?? null
      : null

  const switcher = (
    <nav className="view-switch" aria-label="Ansicht">
      {(['matrix', 'history', 'try'] as const).map((v) => (
        <Link key={v} to={`/runs?agent=${encodeURIComponent(agentId)}&view=${v}`} className={view === v ? 'on' : ''}>
          {v === 'matrix' ? 'Matrix' : v === 'history' ? 'Historie' : 'Ausprobieren'}
        </Link>
      ))}
    </nav>
  )

  if (view === 'history') {
    return (
      <>
        {switcher}
        <HistoryView agents={agents} tasks={tasks} onError={p.onError} />
      </>
    )
  }
  if (view === 'try') {
    return (
      <>
        {switcher}
        <TryView agents={agents} onError={p.onError} onChanged={p.onChanged} />
      </>
    )
  }

  return (
    <>
      {switcher}
      <section className="card">
        <div className="card-head">
          <h2>Runs</h2>
          <select value={agentId} onChange={(e) => setParams({ agent: e.target.value, view })} style={{ width: 'auto' }}>
            {agents.map((a) => (
              <option key={a.id} value={a.id}>
                {a.name} · {a.id}
              </option>
            ))}
          </select>
          {matrix && (
            <>
              <span className="pill mono accent">v{matrix.currentVersion}</span>
              <span className="sub mono small">
                {matrix.tasks.length} Tasks · {matrix.modelId}
              </span>
            </>
          )}
          <div className="actions">
            {openCount > 0 && (
              <>
                <span className="pill open">{openCount} offen</span>
                <button className="btn quiet danger sm" onClick={cancelAll} disabled={busy}>
                  Warteschlange leeren
                </button>
              </>
            )}
            <label className="n-field">
              n
              <input type="number" min={1} max={20} value={n} onChange={(e) => setN(Math.max(1, Number(e.target.value) || 1))} />
            </label>
          </div>
        </div>

        {!matrix ? (
          <p className="empty">{agents.length === 0 ? 'Noch kein Agent.' : 'lädt …'}</p>
        ) : matrix.tasks.length === 0 ? (
          <p className="empty">Noch kein Task. Ohne Spalten keine Matrix.</p>
        ) : (
          <div className="matrix-wrap">
            <div className="matrix" style={{ gridTemplateColumns: `auto repeat(${matrix.tasks.length}, minmax(9.5rem, 1fr))` }}>
              <div className="corner" />
              {matrix.tasks.map((t) => (
                <div key={t} className="col-head">
                  <Link to={`/tasks/${t}`} className="id">
                    {t}
                  </Link>
                  <span className="name">{taskName(t) || 'archiviert'}</span>
                </div>
              ))}

              {matrix.rows.map((row) => (
                <RowView
                  key={row.agentVersion}
                  row={row}
                  current={row.agentVersion === matrix.currentVersion}
                  agentId={agentId}
                  selected={selected}
                  busy={busy}
                  onRunRow={() => runRow(row.agentVersion)}
                  onRunPair={(t) => runPair(row.agentVersion, t)}
                  onSelect={(t) =>
                    setSelected(selected?.version === row.agentVersion && selected.task === t ? null : { version: row.agentVersion, task: t })
                  }
                />
              ))}
            </div>
          </div>
        )}
        <p className="hint">
          Zelle = ein Pair (Agent-Version × Task) mit seiner Run Series: bestanden/gesamt, ein Zeichen je Run. „laufen lassen" startet
          die ganze Zeile mit n Wiederholungen je Task, nacheinander. {agent && `Prompt ändern und speichern ergibt eine neue Zeile.`}
          <br />
          <span className="mono">✓</span> bestanden · <span className="mono">✗</span> nicht bestanden · <span className="mono">!</span> Fehler ·{' '}
          <span className="mono">↻</span> Budget überschritten · <span className="mono">·</span> offen — der älteste Run links.
        </p>
      </section>

      {selected && selectedPair && (
        <SeriesCard
          pair={selectedPair}
          version={selected.version}
          evalTypes={evalTypes}
          onOpen={(id) => navigate(`/runs/${id}`)}
          onClose={() => setSelected(null)}
        />
      )}
    </>
  )
}

function RowView(p: {
  row: Matrix['rows'][number]
  current: boolean
  agentId: string
  selected: { version: number; task: string } | null
  busy: boolean
  onRunRow: () => void
  onRunPair: (task: string) => void
  onSelect: (task: string) => void
}) {
  return (
    <>
      <div className="row-head">
        <Link to={`/agents/${p.agentId}`} className={`pill mono lg ${p.current ? 'accent' : ''}`} title={p.current ? 'aktuelle Version' : 'ältere Version'}>
          v{p.row.agentVersion}
        </Link>
        <button className="btn sm go" onClick={p.onRunRow} disabled={p.busy} title="alle Tasks, n Wiederholungen">
          laufen lassen
        </button>
      </div>
      {p.row.pairs.map((pair) => {
        const runs = pair.runs.filter((r) => r.status !== 'CANCELLED')
        const done = runs.filter((r) => !isOpen(r))
        const passed = done.filter((r) => r.passed).length
        const isSel = p.selected?.version === p.row.agentVersion && p.selected.task === pair.taskId
        return (
          <div
            key={pair.taskId}
            className={`cell ${tone(pair)} ${isSel ? 'selected' : ''}`}
            onClick={() => p.onSelect(pair.taskId)}
            role="button"
            tabIndex={0}
            title={breakdown(pair.runs)}
          >
            {(pair.taskVersions.length > 1 || pair.modelVersions.length > 1) && (
              <span className="mixed-versions" title="gemischte Versionen in dieser Run Series">
                {pair.taskVersions.length > 1 && `t${pair.taskVersions.join('/')}`}
                {pair.taskVersions.length > 1 && pair.modelVersions.length > 1 && ' '}
                {pair.modelVersions.length > 1 && `m${pair.modelVersions.join('/')}`}
              </span>
            )}
            {runs.length === 0 ? (
              <span className="num faint">–</span>
            ) : (
              <>
                <span className="num">
                  {passed}
                  <small>/{runs.length}</small>
                </span>
                <span className="marks">
                  {runs.map((r, i) => (
                    <span key={r.id} title={`#${i + 1} · ${when(r.at)} · ${statusText(r)}`}>
                      {mark(r)}
                    </span>
                  ))}
                </span>
              </>
            )}
            <button
              className="btn sm cell-go"
              onClick={(e) => {
                e.stopPropagation()
                p.onRunPair(pair.taskId)
              }}
              disabled={p.busy}
              title="nur dieser Task, n Wiederholungen"
            >
              nur diese
            </button>
          </div>
        )
      })}
    </>
  )
}

function StatusPill({ r }: { r: SeriesRun }) {
  if (r.status === 'QUEUED') return <span className="pill">wartet</span>
  if (r.status === 'RUNNING') return <span className="pill open">läuft</span>
  if (r.status === 'CANCELLED') return <span className="pill">abgebrochen</span>
  if (r.status === 'FAILED') return <span className="pill fail">Fehler</span>
  if (r.status === 'BUDGET_EXCEEDED') return <span className="pill fail">Budget</span>
  return <span className={`pill ${r.passed ? 'pass' : 'fail'}`}>{r.passed ? 'bestanden' : 'nicht bestanden'}</span>
}

type Tally = { index: number; type: string; passed: number; total: number; reason: string | null; reasonCount: number }

/** Per eval over the series: how often it held, and the failure reason seen most often. */
function tally(runs: SeriesRun[]): Tally[] {
  const byEval = new Map<string, { index: number; type: string; passed: number; total: number; reasons: Map<string, number> }>()
  for (const r of runs) {
    for (const v of r.verdicts) {
      const key = `${v.index}:${v.type}`
      const t = byEval.get(key) ?? { index: v.index, type: v.type, passed: 0, total: 0, reasons: new Map<string, number>() }
      t.total++
      if (v.passed) t.passed++
      else if (v.rationale) t.reasons.set(v.rationale, (t.reasons.get(v.rationale) ?? 0) + 1)
      byEval.set(key, t)
    }
  }
  return [...byEval.values()]
    .sort((a, b) => a.index - b.index)
    .map((t) => {
      const top = [...t.reasons].sort((a, b) => b[1] - a[1])[0]
      return { index: t.index, type: t.type, passed: t.passed, total: t.total, reason: top?.[0] ?? null, reasonCount: top?.[1] ?? 0 }
    })
}

function avg(values: (number | null)[]) {
  const xs = values.filter((v): v is number => v !== null)
  return xs.length === 0 ? null : xs.reduce((a, b) => a + b, 0) / xs.length
}

/**
 * The run series of one pair: first which eval failed and why, then the runs themselves,
 * newest on top. Enough to decide what to change in the prompt without opening every run.
 */
function SeriesCard(p: { pair: Pair; version: number; evalTypes: EvalType[]; onOpen: (id: string) => void; onClose: () => void }) {
  // Cancelled runs never ran; leaving them out keeps # equal to the sign's position in the cell.
  const runs = p.pair.runs.filter((r) => r.status !== 'CANCELLED')
  const cancelled = p.pair.runs.length - runs.length
  const done = runs.filter((r) => !isOpen(r))
  const tallies = tally(done)
  const label = (type: string) => p.evalTypes.find((t) => t.type === type)?.label ?? type
  const tokens = avg(done.map((r) => r.totalTokens))
  const seconds = avg(done.map((r) => r.durationMs))
  const newestFirst = runs.map((r, i) => ({ r, pos: i + 1 })).reverse()

  return (
    <section className="card">
      <div className="card-head">
        <h2>
          <span className="mono">{p.pair.taskId}</span> · v{p.version}
        </h2>
        <span className="sub">
          Run Series · {runs.length} Run(s)
          {cancelled > 0 && ` · ${cancelled} abgebrochen`}
          {tokens !== null && ` · Ø ${Math.round(tokens)} tok`}
          {seconds !== null && ` · Ø ${(seconds / 1000).toFixed(1)} s`}
          {p.pair.taskVersions.length > 1 && ` · Task-Versionen ${p.pair.taskVersions.join(', ')}`}
          {p.pair.modelVersions.length > 1 && ` · Model-Versionen ${p.pair.modelVersions.join(', ')}`}
        </span>
        <div className="actions">
          <button className="btn quiet sm" onClick={p.onClose}>
            schließen
          </button>
        </div>
      </div>

      {runs.length === 0 ? (
        <p className="empty">Noch kein Run. Zelle anklicken und „nur diese" wählen, oder die Zeile laufen lassen.</p>
      ) : (
        <>
          {tallies.length > 0 && (
            <div className="tallies">
              {tallies.map((t) => (
                <div key={`${t.index}:${t.type}`} className="tally">
                  <span className={`pill mono ${t.passed === t.total ? 'pass' : t.passed === 0 ? 'fail' : 'open'}`}>
                    {t.passed}/{t.total}
                  </span>
                  <span className="what">
                    {label(t.type)} <span className="mono faint small">{t.type}</span>
                  </span>
                  <span className="why small muted">
                    {t.reason && (
                      <>
                        {t.reason}
                        {t.reasonCount > 1 && <span className="faint"> ({t.reasonCount}×)</span>}
                      </>
                    )}
                  </span>
                </div>
              ))}
            </div>
          )}

          <div className="series">
            <div className="series-head small faint">
              <span>#</span>
              <span>Zeit</span>
              <span>Status</span>
              <span>gescheitert</span>
              <span>Antwort</span>
              <span className="num">tok</span>
              <span className="num">s</span>
            </div>
            {newestFirst.map(({ r, pos }) => {
              const failed = r.verdicts.filter((v) => !v.passed)
              return (
                <div key={r.id} className="series-row" onClick={() => p.onOpen(r.id)} role="button" tabIndex={0} title={r.id}>
                  <span className="mono muted">{pos}</span>
                  <span className="mono small">{when(r.at)}</span>
                  <span>
                    <StatusPill r={r} />
                  </span>
                  <span className="line">
                    {failed.map((v) => (
                      <span key={v.index} className="pill mono fail" title={v.rationale ?? ''}>
                        {v.type}
                      </span>
                    ))}
                  </span>
                  <span className={`preview small ${r.status === 'FAILED' ? 'failed' : 'muted'}`}>{r.preview ?? ''}</span>
                  <span className="mono small num">{r.totalTokens ?? ''}</span>
                  <span className="mono small num">{r.durationMs !== null ? (r.durationMs / 1000).toFixed(1) : ''}</span>
                </div>
              )
            })}
          </div>
        </>
      )}
    </section>
  )
}
