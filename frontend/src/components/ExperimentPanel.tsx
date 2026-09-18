import { useEffect, useState } from 'react'
import {
  api,
  type AgentRun,
  type Experiment,
  type ExperimentDetail,
  type ProfileListResponse,
  type TaskSummary,
  type TrainingDetail,
} from '../api/client'
import { Badge, TrainingStatusBadge } from './Badge'
import { RunDetail } from './RunDetail'
import { TrainingDetailView } from './TrainingDetailView'
import { RunStrip } from './RunStrip'
import { mean, pct } from './format'

type Props = {
  tasks: TaskSummary[] | null
  task: string
  onTaskChange: (id: string) => void
  profiles: ProfileListResponse | null
  agent: string
  provider: string
  busy: boolean
  perform: (action: () => Promise<void>) => void
  onError: (message: string) => void
}

/**
 * An experiment: the same agent on the same task under several profiles, arm after arm.
 * Everything that must stay equal is fixed once; what differs is exactly the profile.
 */
export function ExperimentPanel(p: Props) {
  const [name, setName] = useState('')
  const [repetitions, setRepetitions] = useState(5)
  const [selected, setSelected] = useState<string[]>([])
  const [includeBaseline, setIncludeBaseline] = useState(true)
  const [list, setList] = useState<Experiment[]>([])
  const [detail, setDetail] = useState<ExperimentDetail | null>(null)
  const [arm, setArm] = useState<TrainingDetail | null>(null)
  const [run, setRun] = useState<AgentRun | null>(null)

  const loadList = (taskId: string) =>
    api
      .experiments(taskId || undefined)
      .then(setList)
      .catch((e: Error) => p.onError(e.message))

  useEffect(() => {
    loadList(p.task)
  }, [p.task]) // eslint-disable-line react-hooks/exhaustive-deps

  // Poll a running experiment: the arms are trained one after another, each run costs time.
  useEffect(() => {
    if (!detail || detail.experiment.status !== 'RUNNING') return
    const id = detail.experiment.id
    const timer = setTimeout(() => {
      api
        .experiment(id)
        .then((d) => {
          setDetail(d)
          if (d.experiment.status !== 'RUNNING') loadList(p.task)
        })
        .catch((e: Error) => p.onError(e.message))
    }, 2000)
    return () => clearTimeout(timer)
  }, [detail]) // eslint-disable-line react-hooks/exhaustive-deps

  const toggle = (id: string) =>
    setSelected((s) => (s.includes(id) ? s.filter((x) => x !== id) : [...s, id]))

  const start = () =>
    p.perform(async () => {
      setDetail(null)
      setArm(null)
      setRun(null)
      const created = await api.startExperiment({
        name: name || undefined,
        task: p.task,
        repetitions,
        agent: p.agent || undefined,
        provider: p.provider || undefined,
        profiles: selected,
        includeBaseline,
      })
      setDetail(await api.experiment(created.id))
      await loadList(p.task)
    })

  const open = (id: string) =>
    p.perform(async () => {
      setArm(null)
      setRun(null)
      setDetail(await api.experiment(id))
    })

  const openArm = (trainingId: string) =>
    p.perform(async () => {
      setRun(null)
      setArm(await api.training(trainingId))
    })

  const taskName = p.tasks?.find((t) => t.id === p.task)?.name
  const baselineArm = detail?.arms.find((a) => a.training.profileId === p.profiles?.defaultProfile)
  const nonBaseline = (selected.length === 0 ? 0 : selected.length) + (includeBaseline ? 1 : 0)

  return (
    <>
      <section className="panel">
        <div className="panel-head">
          <h2>Neues Experiment</h2>
          <span className="small muted">ein Agent, eine Aufgabe, mehrere Profile</span>
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
            <input
              className="grow"
              value={name}
              onChange={(e) => setName(e.target.value)}
              placeholder="Name (optional), z. B. Reflexion gegen Baseline"
            />
            <input type="number" min={1} max={50} value={repetitions} onChange={(e) => setRepetitions(Number(e.target.value))} />
            <button className="btn primary" onClick={start} disabled={p.busy || !p.task || nonBaseline === 0}>
              Experiment starten
            </button>
          </div>

          <div className="section-title">Arme · Strategie-Profile</div>
          <div className="criteria">
            {p.profiles?.profiles
              .filter((x) => x.id !== p.profiles?.defaultProfile)
              .map((x) => (
                <button
                  key={x.id}
                  className={`chip-toggle ${selected.includes(x.id) ? 'on' : ''}`}
                  onClick={() => toggle(x.id)}
                >
                  {x.name} · v{x.version}
                </button>
              ))}
            <button className={`chip-toggle ${includeBaseline ? 'on' : ''}`} onClick={() => setIncludeBaseline(!includeBaseline)}>
              Baseline als Bezugslinie
            </button>
          </div>
          <p className="hint">
            {nonBaseline} Arm(e) × {repetitions} Läufe = <strong>{nonBaseline * repetitions} Modellaufrufe</strong>, nacheinander.
            Ohne Baseline misst der Vergleich nichts: eine steigende Quote kann genauso gut Modellstreuung sein.
          </p>
        </div>
      </section>

      {list.length > 0 && (
        <section className="panel">
          <div className="panel-head">
            <h2>Experimente{taskName ? ` · ${taskName}` : ''}</h2>
            <span className="small muted">neueste zuerst</span>
          </div>
          <table className="table">
            <thead>
              <tr>
                <th>Name</th>
                <th>Aufgabe</th>
                <th>Agent</th>
                <th className="num">N</th>
                <th>Status</th>
                <th>gestartet</th>
              </tr>
            </thead>
            <tbody>
              {list.map((e) => (
                <tr
                  key={e.id}
                  className={`clickable ${detail?.experiment.id === e.id ? 'selected' : ''}`}
                  onClick={() => open(e.id)}
                >
                  <td>{e.name ?? <span className="faint">ohne Namen</span>}</td>
                  <td className="small">
                    {e.taskId} v{e.taskVersion}
                  </td>
                  <td className="small">
                    {e.agentId} {e.agentVersion != null && `v${e.agentVersion}`}
                  </td>
                  <td className="num">{e.repetitions}</td>
                  <td>
                    <Badge tone={e.status === 'DONE' ? 'ok' : e.status === 'RUNNING' ? 'info' : 'bad'}>
                      {e.status === 'DONE' ? 'fertig' : e.status === 'RUNNING' ? 'läuft' : 'fehlgeschlagen'}
                    </Badge>
                  </td>
                  <td className="small muted">{new Date(e.createdAt).toLocaleString('de-DE')}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </section>
      )}

      {detail && (
        <section className="panel">
          <div className="panel-head">
            <h2>Vergleich{detail.experiment.name ? ` · ${detail.experiment.name}` : ''}</h2>
            <div className="run-head">
              <span>
                {detail.experiment.taskId} v{detail.experiment.taskVersion}
              </span>
              <span className="dot">Agent {detail.experiment.agentId}</span>
              <span className="dot">{detail.experiment.providerId}</span>
            </div>
          </div>
          <table className="table">
            <thead>
              <tr>
                <th>Profil</th>
                <th>Verlauf</th>
                <th>Status</th>
                <th className="num">bestanden</th>
                <th className="num">gegen Baseline</th>
                <th className="num">1. Hälfte</th>
                <th className="num">2. Hälfte</th>
                <th className="num">Tokens Ø</th>
                <th className="num">Dauer Ø</th>
                <th className="num">gelernt</th>
              </tr>
            </thead>
            <tbody>
              {detail.arms.map((a) => {
                const isBaseline = a.training.id === baselineArm?.training.id
                const delta =
                  baselineArm && !isBaseline ? a.statistics.passRate - baselineArm.statistics.passRate : null
                return (
                  <tr
                    key={a.training.id}
                    className={`clickable ${arm?.training.id === a.training.id ? 'selected' : ''}`}
                    onClick={() => openArm(a.training.id)}
                  >
                    <td>
                      <Badge tone={isBaseline ? 'neutral' : 'accent'}>{a.training.profileId}</Badge>
                    </td>
                    <td>
                      <RunStrip points={a.statistics.timeline} repetitions={a.training.repetitions} />
                    </td>
                    <td>
                      <TrainingStatusBadge training={a.training} />
                    </td>
                    <td className="num">
                      {a.statistics.passed}/{a.statistics.graded} · {pct(a.statistics.passRate)}
                    </td>
                    <td className="num">
                      {delta === null ? (
                        <span className="faint">Bezugslinie</span>
                      ) : (
                        <Badge tone={delta > 0 ? 'ok' : delta < 0 ? 'bad' : 'neutral'}>
                          {delta > 0 ? '+' : ''}
                          {Math.round(delta * 100)} Pp
                        </Badge>
                      )}
                    </td>
                    <td className="num">{pct(a.statistics.firstHalfPassRate)}</td>
                    <td className="num">{pct(a.statistics.secondHalfPassRate)}</td>
                    <td className="num">{mean(a.statistics.tokens)}</td>
                    <td className="num">{mean(a.statistics.durationMs, ' ms')}</td>
                    <td className="num">{a.memoryEntries}</td>
                  </tr>
                )
              })}
            </tbody>
          </table>
          <div className="panel-body">
            {detail.experiment.error && <div className="error">{detail.experiment.error}</div>}
            <p className="hint">
              Der Abstand zur Baseline ist die einzige Zahl, die etwas über die Strategie sagt — und er ist erst belastbar,
              wenn die Kosten danebenstehen: mehr Tokens für dieselbe Quote ist ein Verlust.
            </p>
          </div>
        </section>
      )}

      {arm && (
        <section className="panel">
          <div className="panel-head">
            <h2>Arm · Profil {arm.training.profileId}</h2>
            <button className="btn ghost sm" onClick={() => setArm(null)}>
              schließen
            </button>
          </div>
          <TrainingDetailView
            detail={arm}
            selectedRunId={run?.id ?? null}
            onOpenRun={(id) => p.perform(async () => setRun(await api.run(id)))}
          />
        </section>
      )}

      {run && (
        <section className="panel">
          <div className="panel-head">
            <h2>Lauf im Detail</h2>
            <button className="btn ghost sm" onClick={() => setRun(null)}>
              schließen
            </button>
          </div>
          <RunDetail run={run} />
        </section>
      )}
    </>
  )
}
