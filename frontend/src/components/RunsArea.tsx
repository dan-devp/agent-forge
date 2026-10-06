import { useEffect, useRef, useState } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router'
import {
  agentsApi,
  catalogApi,
  runsApi,
  tasksApi,
  trainingsApi,
  type AgentView,
  type Decision,
  type EvalType,
  type Matrix,
  type Pair,
  type SeriesRun,
  type Task,
  type TrainingDetail,
} from '../api/client'
import { EvalSentence } from './EvalSentence'
import { HistoryView, TryView } from './RunViews'
import { Ref } from './Ref'
import { changeOf, originsOf, versionTitle, type Origin } from './versions'

type Props = {
  onError: (message: string) => void
  onChanged: () => void
}

const POLL_MS = 2000

function isOpen(r: SeriesRun) {
  return r.status === 'QUEUED' || r.status === 'RUNNING'
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
  const view = params.get('view') ?? 'matrix'
  // The address carries the agent once chosen; the matrix needs one, so it falls back to the first.
  const chosenAgent = params.get('agent') ?? ''

  const [agents, setAgents] = useState<AgentView[]>([])
  const [tasks, setTasks] = useState<Task[]>([])
  const [matrix, setMatrix] = useState<Matrix | null>(null)
  const [n, setN] = useState(3)
  const [selected, setSelected] = useState<{ version: number; task: string } | null>(null)
  const [busy, setBusy] = useState(false)
  const [evalTypes, setEvalTypes] = useState<EvalType[]>([])
  const [showChecks, setShowChecks] = useState(false)
  // What each row is: the version's recipe, and whether a coach wrote it. Kept per agent, so a
  // switch never shows the previous agent's versions.
  const [versions, setVersions] = useState<{ agentId: string; map: Record<number, AgentView> }>({ agentId: '', map: {} })
  const [coach, setCoach] = useState<{ agentId: string; map: Record<number, Origin> }>({ agentId: '', map: {} })
  const timer = useRef<number | null>(null)
  const agentId = chosenAgent || agents[0]?.id || ''

  const fail = (e: Error) => p.onError(e.message)

  useEffect(() => {
    agentsApi.list().then(setAgents).catch(fail)
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

  // New rows appear when a version is saved; their recipes are fetched once per set of rows.
  const versionKey = matrix && matrix.agentId === agentId ? matrix.rows.map((r) => r.agentVersion).join(',') : ''
  useEffect(() => {
    if (!agentId || !versionKey) return
    let stale = false
    const numbers = versionKey.split(',').map(Number)
    Promise.all(numbers.map((v) => agentsApi.version(agentId, v).catch(() => null))).then((list) => {
      if (stale) return
      const map: Record<number, AgentView> = {}
      list.forEach((a) => {
        if (a) map[a.version] = a
      })
      setVersions({ agentId, map })
    })
    trainingsApi
      .list()
      .then((list) => Promise.all(list.map((v) => trainingsApi.get(v.training.id).catch(() => null))))
      .then((details) => {
        if (!stale) setCoach({ agentId, map: originsOf(agentId, details.filter((d): d is TrainingDetail => d !== null)) })
      })
      .catch(() => {
        if (!stale) setCoach({ agentId, map: {} })
      })
    return () => {
      stale = true
    }
  }, [agentId, versionKey])

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

  const taskOf = (id: string) => tasks.find((t) => t.id === id)
  const selectedPair =
    selected && matrix
      ? matrix.rows.find((r) => r.agentVersion === selected.version)?.pairs.find((c) => c.taskId === selected.task) ?? null
      : null
  // A batch without a task list runs every current task; that is what the count says.
  const rowRuns = tasks.length * n
  const infos = versions.agentId === agentId ? versions.map : {}
  const origins = coach.agentId === agentId ? coach.map : {}
  const allInfos = Object.values(infos)
  // Changes are read against the row above, so they wait until every version is there.
  const allLoaded = matrix !== null && matrix.rows.every((r) => infos[r.agentVersion] !== undefined)

  const switcher = (
    <nav className="view-switch" aria-label="Ansicht">
      {(['matrix', 'history', 'try'] as const).map((v) => (
        <Link key={v} to={`/runs?${chosenAgent ? `agent=${encodeURIComponent(chosenAgent)}&` : ''}view=${v}`} className={view === v ? 'on' : ''}>
          {v === 'matrix' ? 'Matrix' : v === 'history' ? 'Historie' : 'Ausprobieren'}
        </Link>
      ))}
    </nav>
  )

  if (view === 'history') {
    return (
      <>
        {switcher}
        <HistoryView
          agents={agents}
          tasks={tasks}
          agent={chosenAgent}
          onAgent={(id) => setParams(id ? { agent: id, view } : { view })}
          onError={p.onError}
        />
      </>
    )
  }
  if (view === 'try') {
    return (
      <>
        {switcher}
        <TryView agents={agents} agent={chosenAgent} onError={p.onError} onChanged={p.onChanged} />
      </>
    )
  }

  return (
    <>
      {switcher}
      <section className="card kind t-run">
        <div className="card-head">
          <h2>Matrix</h2>
          <select value={agentId} onChange={(e) => setParams({ agent: e.target.value, view })} style={{ width: 'auto' }}>
            {agents.map((a) => (
              <option key={a.id} value={a.id}>
                {a.name} · {a.id}
              </option>
            ))}
          </select>
          <span className="sub">Wie oft besteht jede Version des Agents jeden Task?</span>
          {openCount > 0 && (
            <div className="actions">
              <span className="pill open">{openCount} offen</span>
              <button className="btn quiet danger sm" onClick={cancelAll} disabled={busy}>
                Warteschlange leeren
              </button>
            </div>
          )}
        </div>

        {matrix && matrix.tasks.length > 0 && (
          <div className="run-action">
            <button
              className="btn primary"
              onClick={() => runRow(matrix.currentVersion)}
              disabled={busy || rowRuns === 0}
              title={`jeder Run ruft das Model ${matrix.modelId} auf, die Runs laufen nacheinander`}
            >
              v{matrix.currentVersion} laufen lassen
            </button>
            <label className="n-field">
              <input type="number" min={1} max={20} value={n} onChange={(e) => setN(Math.max(1, Number(e.target.value) || 1))} />
              Wiederholungen je Task
            </label>
            <span className="cost">
              = <strong>{rowRuns} Runs</strong> ({tasks.length} Task{tasks.length === 1 ? '' : 's'} × {n}) mit <Ref kind="model" id={matrix.modelId} />
            </span>
          </div>
        )}

        {!matrix ? (
          <p className="empty">{agents.length === 0 ? 'Noch kein Agent.' : 'lädt …'}</p>
        ) : matrix.tasks.length === 0 ? (
          <p className="empty">Noch kein Task. Ohne Spalten keine Matrix.</p>
        ) : (
          <>
            <div className="legend-row">
              <span>
                <span className="swatch pass" />
                alle bestanden
              </span>
              <span>
                <span className="swatch mixed" />
                teils
              </span>
              <span>
                <span className="swatch fail" />
                keiner
              </span>
              <span className="faint">Zahl = bestandene Runs von allen · Zelle anklicken zeigt, warum</span>
              <button className="btn quiet sm legend-toggle" onClick={() => setShowChecks(!showChecks)}>
                {showChecks ? 'Prüfungen ausblenden' : 'Prüfungen je Task zeigen'}
              </button>
            </div>
            <div className="matrix-wrap">
              <div className="matrix" style={{ gridTemplateColumns: `minmax(12rem, 15rem) repeat(${matrix.tasks.length}, minmax(9.5rem, 1fr))` }}>
                <div className="corner">
                  <span>Version ↓</span>
                  <span>Task →</span>
                </div>
                {matrix.tasks.map((t) => (
                  <TaskHead key={t} id={t} task={taskOf(t)} showChecks={showChecks} />
                ))}

                {matrix.rows.map((row, i) => {
                  const info = infos[row.agentVersion] ?? null
                  const prev = matrix.rows
                    .slice(0, i)
                    .map((r) => infos[r.agentVersion])
                    .filter((a): a is AgentView => a !== undefined)
                    .at(-1)
                  return (
                    <RowView
                      key={row.agentVersion}
                      row={row}
                      current={row.agentVersion === matrix.currentVersion}
                      info={info}
                      change={info && allLoaded ? changeOf(info, i === 0 ? null : prev ?? null, allInfos) : []}
                      origin={origins[row.agentVersion] ?? null}
                      selected={selected}
                      busy={busy}
                      runs={rowRuns}
                      onRunRow={() => runRow(row.agentVersion)}
                      onSelect={(t) =>
                        setSelected(selected?.version === row.agentVersion && selected.task === t ? null : { version: row.agentVersion, task: t })
                      }
                    />
                  )
                })}
              </div>
            </div>
          </>
        )}
      </section>

      {selected && selectedPair && (
        <SeriesCard
          pair={selectedPair}
          version={selected.version}
          taskName={taskOf(selectedPair.taskId)?.name ?? selectedPair.taskId}
          evalTypes={evalTypes}
          n={n}
          busy={busy}
          canRun={tasks.some((t) => t.id === selectedPair.taskId)}
          onRun={() => runPair(selected.version, selectedPair.taskId)}
          onOpen={(id) => navigate(`/runs/${id}`)}
          onClose={() => setSelected(null)}
        />
      )}
    </>
  )
}

/**
 * A column says what it asks: the task's name, the question itself, and on demand the
 * checks a run must pass. The id is for files and links; it stays in the tooltip.
 */
function TaskHead({ id, task, showChecks }: { id: string; task: Task | undefined; showChecks: boolean }) {
  if (!task) {
    return (
      <div className="col-head" title={`Task ${id}`}>
        <span className="task-name">{id}</span>
        <span className="task-question faint">archivierter Task</span>
      </div>
    )
  }
  return (
    <div className="col-head" title={`Task ${task.id} v${task.version}\n\n${task.input}`}>
      <Link to={`/tasks/${encodeURIComponent(task.id)}`} className="task-name">
        {task.name}
      </Link>
      <q className="task-question">{task.input.trim()}</q>
      {showChecks ? (
        <ul className="task-checks">
          {task.evals.map((e, i) => (
            <li key={i}>
              <EvalSentence config={e} />
            </li>
          ))}
        </ul>
      ) : (
        <span className="task-checks-count">
          {task.evals.length === 0 ? 'ohne Prüfung' : `${task.evals.length} Prüfung${task.evals.length === 1 ? '' : 'en'}`}
        </span>
      )}
    </div>
  )
}

/** What a series has besides verdicts: runs still open, errors, blown budgets. */
function issues(runs: SeriesRun[]) {
  const open = runs.filter(isOpen).length
  const failed = runs.filter((r) => r.status === 'FAILED').length
  const budget = runs.filter((r) => r.status === 'BUDGET_EXCEEDED').length
  return [open && `${open} offen`, failed && `${failed} Fehler`, budget && `${budget} Budget`].filter(Boolean).join(' · ')
}

const DECISION: Record<Decision, { text: string; tone: string }> = {
  ACCEPTED: { text: 'Coach · angenommen', tone: 'pass' },
  REJECTED: { text: 'Coach · verworfen', tone: 'fail' },
  PENDING: { text: 'Coach · offen', tone: 'open' },
}

function RowView(p: {
  row: Matrix['rows'][number]
  current: boolean
  info: AgentView | null
  change: string[]
  origin: Origin | null
  selected: { version: number; task: string } | null
  busy: boolean
  runs: number
  onRunRow: () => void
  onSelect: (task: string) => void
}) {
  const rejected = p.origin?.decision === 'REJECTED'
  const ran = p.row.pairs.some((pair) => pair.runs.some((r) => r.status !== 'CANCELLED'))
  return (
    <>
      <div className={`row-head ${rejected ? 'rejected' : ''}`} title={p.info ? versionTitle(p.info) : undefined}>
        <div className="row-head-top">
          <span className={`pill mono ${p.current ? 'accent' : ''}`}>v{p.row.agentVersion}</span>
          {p.current && <span className="now">aktuell</span>}
          {p.origin && (
            <Link
              to={`/trainings/${encodeURIComponent(p.origin.trainingId)}/sessions/${p.origin.sessionId}`}
              className={`pill sm ${DECISION[p.origin.decision].tone}`}
              title="vom Coach geschrieben — zur Session"
            >
              {DECISION[p.origin.decision].text}
            </Link>
          )}
        </div>
        {p.change.length > 0 && <span className="row-change">{p.change.join(' · ')}</span>}
        {!p.current && (
          <button className="btn sm go" onClick={p.onRunRow} disabled={p.busy} title={`diese ältere Version noch einmal: ${p.runs} Runs`}>
            laufen lassen
          </button>
        )}
      </div>
      {!ran ? (
        <div className="cell empty-row" style={{ gridColumn: `span ${p.row.pairs.length}` }}>
          Mit dieser Version lief noch kein Run.
        </div>
      ) : (
        p.row.pairs.map((pair) => {
          const runs = pair.runs.filter((r) => r.status !== 'CANCELLED')
          const done = runs.filter((r) => !isOpen(r))
          const passed = done.filter((r) => r.passed).length
          const isSel = p.selected?.version === p.row.agentVersion && p.selected.task === pair.taskId
          const extra = issues(runs)
          return (
            <div
              key={pair.taskId}
              className={`cell ${runs.length === 0 ? 'none' : tone(pair)} ${isSel ? 'selected' : ''} ${rejected ? 'faded' : ''}`}
              onClick={() => p.onSelect(pair.taskId)}
              onKeyDown={(e) => e.key === 'Enter' && p.onSelect(pair.taskId)}
              role="button"
              tabIndex={0}
              title={breakdown(pair.runs)}
            >
              {(pair.taskVersions.length > 1 || pair.modelVersions.length > 1) && (
                <span className="mixed-versions" title="Die Runs dieser Zelle liefen mit verschiedenen Task- oder Model-Versionen">
                  gemischt
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
                  {extra && <span className="cell-extra">{extra}</span>}
                </>
              )}
            </div>
          )
        })
      )}
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

type Tally = {
  index: number
  type: string
  subject: string | null
  passed: number
  total: number
  reason: string | null
  reasonCount: number
}

/** Per eval over the series: how often it held, and the failure reason seen most often. */
function tally(runs: SeriesRun[]): Tally[] {
  const byEval = new Map<
    string,
    { index: number; type: string; subject: string | null; passed: number; total: number; reasons: Map<string, number> }
  >()
  for (const r of runs) {
    for (const v of r.verdicts) {
      // A judge's questions share the eval's index; each one is tallied on its own.
      const key = `${v.index}:${v.type}:${v.subject ?? ''}`
      const t = byEval.get(key) ?? {
        index: v.index,
        type: v.type,
        subject: v.subject,
        passed: 0,
        total: 0,
        reasons: new Map<string, number>(),
      }
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
      return {
        index: t.index,
        type: t.type,
        subject: t.subject,
        passed: t.passed,
        total: t.total,
        reason: top?.[0] ?? null,
        reasonCount: top?.[1] ?? 0,
      }
    })
}

function shorten(text: string, max: number) {
  return text.length <= max ? text : text.slice(0, max - 1) + '…'
}

function avg(values: (number | null)[]) {
  const xs = values.filter((v): v is number => v !== null)
  return xs.length === 0 ? null : xs.reduce((a, b) => a + b, 0) / xs.length
}

/**
 * The run series of one pair: first which eval failed and why, then the runs themselves,
 * newest on top. Enough to decide what to change in the prompt without opening every run.
 */
function SeriesCard(p: {
  pair: Pair
  version: number
  taskName: string
  evalTypes: EvalType[]
  n: number
  busy: boolean
  canRun: boolean
  onRun: () => void
  onOpen: (id: string) => void
  onClose: () => void
}) {
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
    <section className="card kind t-run">
      <div className="card-head">
        <h2>Run Series</h2>
        <span className="sub">
          <span className="pill mono">v{p.version}</span> auf „{p.taskName}“ · {runs.length} Run{runs.length === 1 ? '' : 's'}
          {cancelled > 0 && ` · ${cancelled} abgebrochen`}
          {tokens !== null && ` · Ø ${Math.round(tokens).toLocaleString('de-DE')} Tokens`}
          {seconds !== null && ` · Ø ${(seconds / 1000).toFixed(1).replace('.', ',')} s`}
          {p.pair.taskVersions.length > 1 && ` · Task-Versionen ${p.pair.taskVersions.join(', ')}`}
          {p.pair.modelVersions.length > 1 && ` · Model-Versionen ${p.pair.modelVersions.join(', ')}`}
        </span>
        <div className="actions">
          <button
            className="btn sm"
            onClick={p.onRun}
            disabled={p.busy || !p.canRun}
            title={p.canRun ? `v${p.version} auf diesem Task, ${p.n}× nacheinander` : 'Task ist archiviert'}
          >
            {p.n} weitere Run{p.n === 1 ? '' : 's'}
          </button>
          <button className="btn quiet sm" onClick={p.onClose}>
            schließen
          </button>
        </div>
      </div>

      {runs.length === 0 ? (
        <p className="empty">Noch kein Run für dieses Paar. „{p.n} weitere Runs“ startet sie.</p>
      ) : (
        <>
          {tallies.length > 0 && (
            <div className="tallies">
              {tallies.map((t) => (
                <div key={`${t.index}:${t.type}:${t.subject ?? ''}`} className="tally">
                  <span className={`pill mono ${t.passed === t.total ? 'pass' : t.passed === 0 ? 'fail' : 'open'}`}>
                    {t.passed}/{t.total}
                  </span>
                  <span className="what" title={`Prüfung ${t.index}: ${t.type}`}>
                    {t.subject ?? label(t.type)}
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
              <span>Ergebnis</span>
              <span>gescheitert an</span>
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
                    {failed.map((v, k) => (
                      <span key={k} className="pill fail" title={[v.subject, v.rationale].filter(Boolean).join(' — ')}>
                        {shorten(v.subject ?? label(v.type), 28)}
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
