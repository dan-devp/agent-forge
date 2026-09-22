import { useEffect, useRef, useState } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router'
import { agentsApi, runsApi, tasksApi, type AgentView, type Cell, type CellRun, type Matrix, type Task } from '../api/client'

type Props = {
  onError: (message: string) => void
  onChanged: () => void
}

const POLL_MS = 2000

function isOpen(r: CellRun) {
  return r.status === 'QUEUED' || r.status === 'RUNNING'
}

function mark(r: CellRun) {
  if (isOpen(r)) return '·'
  if (r.status === 'CANCELLED') return '–'
  return r.passed ? '✓' : '✗'
}

/** Cell colour: all passed sage, none rosé, some sand; anything still open makes it busy. */
function tone(cell: Cell) {
  const done = cell.runs.filter((r) => !isOpen(r) && r.status !== 'CANCELLED')
  if (cell.runs.some(isOpen)) return 'busy'
  if (done.length === 0) return ''
  const passed = done.filter((r) => r.passed).length
  if (passed === done.length) return 'pass'
  if (passed === 0) return 'fail'
  return 'mixed'
}

/**
 * The picture of the training camp: one row per agent version, one column per task,
 * each cell the runs of that pair. Change the prompt, save, get a new row, run it,
 * compare with the row above.
 */
export function RunsArea(p: Props) {
  const [params, setParams] = useSearchParams()
  const navigate = useNavigate()
  const agentId = params.get('agent') ?? ''

  const [agents, setAgents] = useState<AgentView[]>([])
  const [tasks, setTasks] = useState<Task[]>([])
  const [matrix, setMatrix] = useState<Matrix | null>(null)
  const [n, setN] = useState(3)
  const [selected, setSelected] = useState<{ version: number; task: string } | null>(null)
  const [busy, setBusy] = useState(false)
  const timer = useRef<number | null>(null)

  const fail = (e: Error) => p.onError(e.message)

  useEffect(() => {
    agentsApi
      .list()
      .then((list) => {
        setAgents(list)
        if (!agentId && list.length > 0) setParams({ agent: list[0].id }, { replace: true })
      })
      .catch(fail)
    tasksApi.list().then(setTasks).catch(fail)
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
          const open = m.rows.some((r) => r.cells.some((c) => c.runs.some(isOpen)))
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
        const open = m.rows.some((r) => r.cells.some((c) => c.runs.some(isOpen)))
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

  const runCell = (version: number, task: string) =>
    perform(async () => {
      await runsApi.batch({ agent: agentId, agentVersion: version, tasks: [task], repetitions: n })
    })

  const openBatches = matrix
    ? [...new Set(matrix.rows.flatMap((r) => r.cells.flatMap((c) => c.runs.filter(isOpen).map((x) => x.batchId))))].filter(
        (b): b is string => b !== null,
      )
    : []
  const openCount = matrix ? matrix.rows.reduce((s, r) => s + r.cells.reduce((t, c) => t + c.runs.filter(isOpen).length, 0), 0) : 0

  const cancelAll = () =>
    perform(async () => {
      for (const b of openBatches) await runsApi.cancelBatch(b)
    })

  const agent = agents.find((a) => a.id === agentId)
  const taskName = (id: string) => tasks.find((t) => t.id === id)?.name ?? ''
  const selectedCell =
    selected && matrix
      ? matrix.rows.find((r) => r.agentVersion === selected.version)?.cells.find((c) => c.taskId === selected.task) ?? null
      : null

  return (
    <>
      <section className="card">
        <div className="card-head">
          <h2>Läufe</h2>
          <select value={agentId} onChange={(e) => setParams({ agent: e.target.value })} style={{ width: 'auto' }}>
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
                {matrix.tasks.length} Aufgaben · {matrix.modelId}
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
          <p className="empty">Noch keine Aufgabe. Ohne Spalten keine Matrix.</p>
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
                  onRunCell={(t) => runCell(row.agentVersion, t)}
                  onSelect={(t) =>
                    setSelected(selected?.version === row.agentVersion && selected.task === t ? null : { version: row.agentVersion, task: t })
                  }
                />
              ))}
            </div>
          </div>
        )}
        <p className="hint">
          Zelle = Läufe dieser Agent-Version auf dieser Aufgabe: bestanden/gesamt, ein Zeichen je Lauf. „laufen lassen" startet die
          ganze Zeile mit n Wiederholungen je Aufgabe, nacheinander. {agent && `Prompt ändern und speichern ergibt eine neue Zeile.`}
        </p>
      </section>

      {selected && selectedCell && (
        <section className="card">
          <div className="card-head">
            <h2>
              <span className="mono">{selected.task}</span> · v{selected.version}
            </h2>
            <span className="sub">
              {selectedCell.runs.length} Lauf/Läufe
              {selectedCell.taskVersions.length > 1 && ` · Aufgaben-Versionen ${selectedCell.taskVersions.join(', ')}`}
              {selectedCell.modelVersions.length > 1 && ` · Modell-Versionen ${selectedCell.modelVersions.join(', ')}`}
            </span>
            <div className="actions">
              <button className="btn quiet sm" onClick={() => setSelected(null)}>
                schließen
              </button>
            </div>
          </div>
          {selectedCell.runs.length === 0 ? (
            <p className="empty">Noch kein Lauf. Zelle anklicken und „nur diese" wählen, oder die Zeile laufen lassen.</p>
          ) : (
            <div className="rows">
              {selectedCell.runs.map((r, i) => (
                <div key={r.id} className="row" onClick={() => navigate(`/runs/${r.id}`)} role="button" tabIndex={0}>
                  <div className="line">
                    <span className="mono muted">{i + 1}</span>
                    <StatusPill r={r} />
                    <span className="mono small faint">{r.id.slice(0, 8)}</span>
                  </div>
                  <div className="line small muted">
                    {r.taskVersion !== null && <span className="pill mono">Aufgabe v{r.taskVersion}</span>}
                    {r.batchId && <span className="mono faint">batch {r.batchId.slice(0, 8)}</span>}
                  </div>
                  <div className="line mono small">
                    {r.totalTokens !== null && <span>{r.totalTokens} tok</span>}
                    {r.durationMs !== null && <span>{(r.durationMs / 1000).toFixed(1)} s</span>}
                  </div>
                </div>
              ))}
            </div>
          )}
        </section>
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
  onRunCell: (task: string) => void
  onSelect: (task: string) => void
}) {
  return (
    <>
      <div className="row-head">
        <Link to={`/agents/${p.agentId}`} className={`pill mono lg ${p.current ? 'accent' : ''}`} title={p.current ? 'aktuelle Version' : 'ältere Version'}>
          v{p.row.agentVersion}
        </Link>
        <button className="btn sm go" onClick={p.onRunRow} disabled={p.busy} title="alle Aufgaben, n Wiederholungen">
          laufen lassen
        </button>
      </div>
      {p.row.cells.map((cell) => {
        const runs = cell.runs.filter((r) => r.status !== 'CANCELLED')
        const done = runs.filter((r) => !isOpen(r))
        const passed = done.filter((r) => r.passed).length
        const isSel = p.selected?.version === p.row.agentVersion && p.selected.task === cell.taskId
        return (
          <div
            key={cell.taskId}
            className={`cell ${tone(cell)} ${isSel ? 'selected' : ''}`}
            onClick={() => p.onSelect(cell.taskId)}
            role="button"
            tabIndex={0}
            title={runs.length === 0 ? 'noch kein Lauf' : `${passed} von ${done.length} bestanden`}
          >
            {(cell.taskVersions.length > 1 || cell.modelVersions.length > 1) && (
              <span className="mixed-versions" title="gemischte Versionen in dieser Zelle">
                {cell.taskVersions.length > 1 && `t${cell.taskVersions.join('/')}`}
                {cell.taskVersions.length > 1 && cell.modelVersions.length > 1 && ' '}
                {cell.modelVersions.length > 1 && `m${cell.modelVersions.join('/')}`}
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
                <span className="marks">{runs.map(mark).join('')}</span>
              </>
            )}
            <button
              className="btn sm cell-go"
              onClick={(e) => {
                e.stopPropagation()
                p.onRunCell(cell.taskId)
              }}
              disabled={p.busy}
              title="nur diese Aufgabe, n Wiederholungen"
            >
              nur diese
            </button>
          </div>
        )
      })}
    </>
  )
}

function StatusPill({ r }: { r: CellRun }) {
  if (r.status === 'QUEUED') return <span className="pill">wartet</span>
  if (r.status === 'RUNNING') return <span className="pill open">läuft</span>
  if (r.status === 'CANCELLED') return <span className="pill">abgebrochen</span>
  if (r.status === 'FAILED') return <span className="pill fail">Fehler</span>
  if (r.status === 'BUDGET_EXCEEDED') return <span className="pill fail">Budget</span>
  return <span className={`pill ${r.passed ? 'pass' : 'fail'}`}>{r.passed ? 'bestanden' : 'nicht bestanden'}</span>
}
