import { useEffect, useState } from 'react'
import { api, type AgentRun, type Series, type SeriesDetail, type SeriesStatistics, type TaskSummary } from '../api/client'
import type { Selection } from './Sidebar'
import { PassBadge, RunStatusBadge, SeriesStatusBadge } from './Badge'
import { RUN_STATUS_TEXT } from './status'
import { RunDetail } from './RunDetail'

type Props = {
  tasks: TaskSummary[] | null
  task: string
  onTaskChange: (id: string) => void
  selection: Selection
  busy: boolean
  perform: (action: () => Promise<void>) => void
  onError: (message: string) => void
}

function fmt(a: SeriesStatistics['tokens'], unit = '') {
  if (a.count === 0) return '–'
  return `${Math.round(a.mean)}${unit}`
}

function range(a: SeriesStatistics['tokens']) {
  if (a.count === 0) return ''
  return `${a.min} – ${a.max} · n=${a.count}`
}

/** N runs of one task, statistics computed by the backend on every read. */
export function SeriesPanel(p: Props) {
  const [repetitions, setRepetitions] = useState(5)
  const [detail, setDetail] = useState<SeriesDetail | null>(null)
  const [history, setHistory] = useState<Series[]>([])
  const [selectedRun, setSelectedRun] = useState<AgentRun | null>(null)

  const loadHistory = () => api.seriesList().then(setHistory).catch((e: Error) => p.onError(e.message))

  useEffect(() => {
    loadHistory()
  }, []) // eslint-disable-line react-hooks/exhaustive-deps

  // Poll a running series every two seconds until it has a final state.
  useEffect(() => {
    if (!detail || detail.series.status !== 'RUNNING') return
    const id = detail.series.id
    const timer = setTimeout(() => {
      api
        .series(id)
        .then((d) => {
          setDetail(d)
          if (d.series.status !== 'RUNNING') loadHistory()
        })
        .catch((e: Error) => p.onError(e.message))
    }, 2000)
    return () => clearTimeout(timer)
  }, [detail]) // eslint-disable-line react-hooks/exhaustive-deps

  const start = () =>
    p.perform(async () => {
      setDetail(null)
      setSelectedRun(null)
      const created = await api.startSeries({ task: p.task, repetitions, ...p.selection })
      setDetail(await api.series(created.id))
      await loadHistory()
    })

  const open = (id: string) =>
    p.perform(async () => {
      setSelectedRun(null)
      setDetail(await api.series(id))
    })

  const openRun = (id: string) =>
    p.perform(async () => {
      setSelectedRun(await api.run(id))
    })

  const s = detail?.statistics
  const done = detail?.series.status !== 'RUNNING'

  return (
    <>
      <section className="panel">
        <div className="panel-head">
          <h2>Serie</h2>
          <span className="small muted">dieselbe Aufgabe N-mal, gleiche Einstellungen</span>
        </div>
        <div className="panel-body">
          <div className="toolbar">
            <select className="grow" value={p.task} onChange={(e) => p.onTaskChange(e.target.value)} disabled={!p.tasks}>
              <option value="">— Aufgabe wählen —</option>
              {p.tasks?.map((t) => (
                <option key={t.id} value={t.id}>
                  {t.name} · v{t.version}
                </option>
              ))}
            </select>
            <input type="number" min={1} max={50} value={repetitions} onChange={(e) => setRepetitions(Number(e.target.value))} />
            <button className="btn primary" onClick={start} disabled={p.busy || !p.task || !p.selection.provider}>
              Serie starten
            </button>
          </div>
          <p className="hint">
            Ein Lauf ist eine Anekdote. Erst die Wiederholung zeigt, ob ein Ergebnis Können oder Glück war. Läufe laufen
            nacheinander im Hintergrund; die Anzeige aktualisiert sich alle zwei Sekunden.
          </p>
        </div>
      </section>

      {detail && s && (
        <section className="panel">
          <div className="panel-head">
            <h2>Auswertung</h2>
            <div className="run-head">
              <SeriesStatusBadge series={detail.series} />
              <span>
                {detail.series.taskId} v{detail.series.taskVersion}
              </span>
              <span className="dot">SOUL {detail.series.soulId}</span>
              <span className="dot">Profil {detail.series.profileId}</span>
              <span className="dot">{detail.series.providerId}</span>
            </div>
          </div>
          <div className="panel-body">
            {!done && (
              <div className="progress">
                <span style={{ width: `${(detail.series.completed / detail.series.repetitions) * 100}%` }} />
              </div>
            )}
            {detail.series.error && <div className="error">{detail.series.error}</div>}

            <div className="kpis">
              <div className="kpi">
                <div className="label">bestanden</div>
                <div className="value">
                  {s.passed}/{s.graded}
                </div>
                <div className="sub">
                  {Math.round(s.passRate * 100)} % · 95 %: {Math.round(s.passRateLower * 100)}–{Math.round(s.passRateUpper * 100)} %
                </div>
              </div>
              <div className="kpi">
                <div className="label">pass@k · pass^k</div>
                <div className="value">
                  {s.passAtK ? '✓' : '✗'} · {s.passHatK ? '✓' : '✗'}
                </div>
                <div className="sub">{s.flaky ? 'flaky — manchmal ja, manchmal nein' : s.passHatK ? 'zuverlässig' : s.passAtK ? 'kann, aber nicht immer' : 'nie bestanden'}</div>
              </div>
              <div className="kpi">
                <div className="label">Tokens Ø</div>
                <div className="value">{fmt(s.tokens)}</div>
                <div className="sub">{range(s.tokens)}</div>
              </div>
              <div className="kpi">
                <div className="label">Dauer Ø</div>
                <div className="value">{fmt(s.durationMs, ' ms')}</div>
                <div className="sub">{range(s.durationMs)}</div>
              </div>
              <div className="kpi">
                <div className="label">Tool-Aufrufe Ø</div>
                <div className="value">{fmt(s.toolCalls)}</div>
                <div className="sub">{range(s.toolCalls)}</div>
              </div>
              <div className="kpi">
                <div className="label">Status</div>
                <div className="value">{s.completed}/{s.repetitions}</div>
                <div className="sub">
                  {Object.entries(s.statusCounts)
                    .map(([k, v]) => `${RUN_STATUS_TEXT[k as AgentRun['status']] ?? k} ${v}`)
                    .join(' · ') || '–'}
                </div>
              </div>
            </div>

            {detail.runs.length > 0 && (
              <table className="table">
                <thead>
                  <tr>
                    <th>#</th>
                    <th>Status</th>
                    <th>Bewertung</th>
                    <th className="num">Tokens</th>
                    <th className="num">ms</th>
                    <th className="num">Schritte</th>
                  </tr>
                </thead>
                <tbody>
                  {detail.runs.map((r, i) => (
                    <tr key={r.id} className={`clickable ${selectedRun?.id === r.id ? 'selected' : ''}`} onClick={() => openRun(r.id)}>
                      <td className="num">{i + 1}</td>
                      <td>
                        <RunStatusBadge status={r.status} />
                      </td>
                      <td>
                        {r.scores.length === 0 ? (
                          <span className="faint">–</span>
                        ) : (
                          r.scores.map((sc) => (
                            <span key={sc.grader}>
                              <PassBadge passed={sc.passed} /> <span className="small muted">{Math.round(sc.score * 100)} %</span>
                            </span>
                          ))
                        )}
                      </td>
                      <td className="num">{r.totalTokens ?? '–'}</td>
                      <td className="num">{r.durationMs ?? '–'}</td>
                      <td className="num">{r.steps}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            )}
          </div>
        </section>
      )}

      {selectedRun && (
        <section className="panel">
          <div className="panel-head">
            <h2>Lauf im Detail</h2>
            <button className="btn ghost sm" onClick={() => setSelectedRun(null)}>
              schließen
            </button>
          </div>
          <RunDetail run={selectedRun} />
        </section>
      )}

      <section className="panel">
        <div className="panel-head">
          <h2>Bisherige Serien</h2>
          <button className="btn ghost sm" onClick={loadHistory}>
            aktualisieren
          </button>
        </div>
        {history.length === 0 ? (
          <div className="empty">Noch keine Serie gestartet.</div>
        ) : (
          <table className="table">
            <thead>
              <tr>
                <th>Aufgabe</th>
                <th>SOUL</th>
                <th>Profil</th>
                <th className="num">N</th>
                <th>Status</th>
                <th>gestartet</th>
              </tr>
            </thead>
            <tbody>
              {history.map((h) => (
                <tr key={h.id} className={`clickable ${detail?.series.id === h.id ? 'selected' : ''}`} onClick={() => open(h.id)}>
                  <td>
                    {h.taskId} <span className="faint">v{h.taskVersion}</span>
                  </td>
                  <td>{h.soulId}</td>
                  <td>{h.profileId}</td>
                  <td className="num">{h.repetitions}</td>
                  <td>
                    <SeriesStatusBadge series={h} />
                  </td>
                  <td className="small muted">{new Date(h.createdAt).toLocaleString('de-DE')}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </section>
    </>
  )
}
