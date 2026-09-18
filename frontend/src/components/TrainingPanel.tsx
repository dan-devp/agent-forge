import { useEffect, useState } from 'react'
import { api, type AgentRun, type TaskSummary, type Training, type TrainingDetail, type TrainingStatistics } from '../api/client'
import type { Selection } from './Sidebar'
import { Badge, PassBadge, RunStatusBadge, TrainingStatusBadge } from './Badge'
import { RUN_STATUS_TEXT } from './status'
import { RunDetail } from './RunDetail'
import { LearningCurve } from './LearningCurve'

type Props = {
  tasks: TaskSummary[] | null
  task: string
  onTaskChange: (id: string) => void
  selection: Selection
  busy: boolean
  perform: (action: () => Promise<void>) => void
  onError: (message: string) => void
}

function pct(v: number | null | undefined) {
  return v === null || v === undefined ? '–' : `${Math.round(v * 100)} %`
}

function mean(a: TrainingStatistics['tokens'], unit = '') {
  return a.count === 0 ? '–' : `${Math.round(a.mean)}${unit}`
}

function range(a: TrainingStatistics['tokens']) {
  return a.count === 0 ? '' : `${a.min} – ${a.max} · n=${a.count}`
}

/** A training: one profile on one task, N runs in order. Trainings of the same task are compared side by side. */
export function TrainingPanel(p: Props) {
  const [repetitions, setRepetitions] = useState(5)
  const [detail, setDetail] = useState<TrainingDetail | null>(null)
  const [trainings, setTrainings] = useState<Training[]>([])
  const [compare, setCompare] = useState<Record<string, TrainingStatistics>>({})
  const [selectedRun, setSelectedRun] = useState<AgentRun | null>(null)

  const loadList = (taskId: string) =>
    api
      .trainings(taskId || undefined)
      .then(async (list) => {
        setTrainings(list)
        // Comparison needs the statistics of every finished training of this task.
        const stats: Record<string, TrainingStatistics> = {}
        await Promise.all(
          list
            .filter((t) => t.status !== 'RUNNING')
            .slice(0, 12)
            .map(async (t) => {
              stats[t.id] = (await api.training(t.id)).statistics
            }),
        )
        setCompare(stats)
      })
      .catch((e: Error) => p.onError(e.message))

  useEffect(() => {
    loadList(p.task)
  }, [p.task]) // eslint-disable-line react-hooks/exhaustive-deps

  // Poll a running training every two seconds until it has a final state.
  useEffect(() => {
    if (!detail || detail.training.status !== 'RUNNING') return
    const id = detail.training.id
    const timer = setTimeout(() => {
      api
        .training(id)
        .then((d) => {
          setDetail(d)
          if (d.training.status !== 'RUNNING') loadList(p.task)
        })
        .catch((e: Error) => p.onError(e.message))
    }, 2000)
    return () => clearTimeout(timer)
  }, [detail]) // eslint-disable-line react-hooks/exhaustive-deps

  const start = () =>
    p.perform(async () => {
      setDetail(null)
      setSelectedRun(null)
      const created = await api.startTraining({ task: p.task, repetitions, ...p.selection })
      setDetail(await api.training(created.id))
      await loadList(p.task)
    })

  const open = (id: string) =>
    p.perform(async () => {
      setSelectedRun(null)
      setDetail(await api.training(id))
    })

  const openRun = (id: string) =>
    p.perform(async () => {
      setSelectedRun(await api.run(id))
    })

  const s = detail?.statistics
  const taskName = p.tasks?.find((t) => t.id === p.task)?.name

  return (
    <>
      <section className="panel">
        <div className="panel-head">
          <h2>Training</h2>
          <span className="small muted">ein Profil, eine Aufgabe, N Läufe in Folge</span>
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
              Training starten
            </button>
          </div>
          <p className="hint">
            Profil <strong>{p.selection.profile || '–'}</strong> auf Aufgabe <strong>{taskName ?? '–'}</strong>, {repetitions} Läufe
            nacheinander. Jeder Lauf sieht, was die Strategien aus den vorigen Läufen behalten haben. Baseline bleibt flach —
            steigt sie, ist es Zufall, nicht Lernen.
          </p>
        </div>
      </section>

      {p.task && trainings.length > 0 && (
        <section className="panel">
          <div className="panel-head">
            <h2>Vergleich · {taskName}</h2>
            <span className="small muted">Trainings dieser Aufgabe, neueste zuerst</span>
          </div>
          <table className="table">
            <thead>
              <tr>
                <th>Profil</th>
                <th>SOUL</th>
                <th className="num">N</th>
                <th>Status</th>
                <th className="num">bestanden</th>
                <th className="num">1. Hälfte</th>
                <th className="num">2. Hälfte</th>
                <th className="num">Tokens Ø</th>
                <th>gestartet</th>
              </tr>
            </thead>
            <tbody>
              {trainings.map((t) => {
                const c = compare[t.id]
                return (
                  <tr key={t.id} className={`clickable ${detail?.training.id === t.id ? 'selected' : ''}`} onClick={() => open(t.id)}>
                    <td>
                      <Badge tone={t.profileId === 'baseline' ? 'neutral' : 'accent'}>{t.profileId}</Badge>
                    </td>
                    <td>{t.soulId}</td>
                    <td className="num">{t.repetitions}</td>
                    <td>
                      <TrainingStatusBadge training={t} />
                    </td>
                    <td className="num">{c ? `${c.passed}/${c.graded}` : '–'}</td>
                    <td className="num">{c ? pct(c.firstHalfPassRate) : '–'}</td>
                    <td className="num">{c ? pct(c.secondHalfPassRate) : '–'}</td>
                    <td className="num">{c ? mean(c.tokens) : '–'}</td>
                    <td className="small muted">{new Date(t.createdAt).toLocaleString('de-DE')}</td>
                  </tr>
                )
              })}
            </tbody>
          </table>
        </section>
      )}

      {detail && s && (
        <section className="panel">
          <div className="panel-head">
            <h2>Lernkurve</h2>
            <div className="run-head">
              <TrainingStatusBadge training={detail.training} />
              <span>
                {detail.training.taskId} v{detail.training.taskVersion}
              </span>
              <span className="dot">Profil {detail.training.profileId}</span>
              <span className="dot">SOUL {detail.training.soulId}</span>
              <span className="dot">{detail.training.providerId}</span>
            </div>
          </div>
          <div className="panel-body">
            {detail.training.status === 'RUNNING' && (
              <div className="progress">
                <span style={{ width: `${(detail.training.completed / detail.training.repetitions) * 100}%` }} />
              </div>
            )}
            {detail.training.error && <div className="error">{detail.training.error}</div>}

            <LearningCurve points={s.timeline} repetitions={s.repetitions} />

            <div className="kpis">
              <div className="kpi">
                <div className="label">bestanden</div>
                <div className="value">
                  {s.passed}/{s.graded}
                </div>
                <div className="sub">
                  {pct(s.passRate)} · 95 %: {Math.round(s.passRateLower * 100)}–{Math.round(s.passRateUpper * 100)} %
                </div>
              </div>
              <div className="kpi">
                <div className="label">1. → 2. Hälfte</div>
                <div className="value">
                  {pct(s.firstHalfPassRate)} → {pct(s.secondHalfPassRate)}
                </div>
                <div className="sub">
                  {s.firstPassAt ? `erster Treffer bei Lauf ${s.firstPassAt}` : 'kein Treffer'}
                </div>
              </div>
              <div className="kpi">
                <div className="label">pass@k · pass^k</div>
                <div className="value">
                  {s.passAtK ? '✓' : '✗'} · {s.passHatK ? '✓' : '✗'}
                </div>
                <div className="sub">
                  {s.flaky ? 'flaky' : s.passHatK ? 'zuverlässig' : s.passAtK ? 'kann, aber nicht immer' : 'nie bestanden'}
                </div>
              </div>
              <div className="kpi">
                <div className="label">Tokens Ø</div>
                <div className="value">{mean(s.tokens)}</div>
                <div className="sub">{range(s.tokens)}</div>
              </div>
              <div className="kpi">
                <div className="label">Dauer Ø</div>
                <div className="value">{mean(s.durationMs, ' ms')}</div>
                <div className="sub">{range(s.durationMs)}</div>
              </div>
              <div className="kpi">
                <div className="label">Tool-Aufrufe Ø</div>
                <div className="value">{mean(s.toolCalls)}</div>
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

            <div className="section-title">
              <span>Gelernt · {detail.memory.length} Einträge</span>
            </div>
            {detail.memory.length === 0 ? (
              <p className="hint">Nichts. Bei Baseline erwartet — keine Strategie schreibt hier.</p>
            ) : (
              <ul className="memory">
                {detail.memory.map((m) => {
                  const runIndex = detail.runs.findIndex((r) => r.id === m.runId)
                  return (
                    <li key={m.id}>
                      <Badge tone="accent" mono>
                        {m.strategyId}
                      </Badge>
                      <Badge tone="neutral">{m.kind}</Badge>
                      <span className="small faint">{runIndex >= 0 ? `Lauf ${runIndex + 1}` : 'außerhalb'}</span>
                      <div className="content">{m.content}</div>
                    </li>
                  )
                })}
              </ul>
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
    </>
  )
}
