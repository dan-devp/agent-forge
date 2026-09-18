import { useEffect, useState } from 'react'
import {
  api,
  type AgentRun,
  type ProfileListResponse,
  type TaskSummary,
  type Training,
  type TrainingDetail,
  type TrainingStatistics,
} from '../api/client'
import { Badge, TrainingStatusBadge } from './Badge'
import { RunDetail } from './RunDetail'
import { TrainingDetailView } from './TrainingDetailView'
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

/** A single training: one agent, one profile, one task, N runs in order — without a comparison arm. */
export function TrainingPanel(p: Props) {
  // Derived, not stored: the default only exists once the profiles are loaded.
  const [chosenProfile, setProfile] = useState<string | null>(null)
  const profile = chosenProfile ?? p.profiles?.defaultProfile ?? ''
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
        // The comparison needs the statistics of every finished training of this task.
        const stats: Record<string, TrainingStatistics> = {}
        await Promise.all(
          list
            .filter((t) => t.status === 'DONE' || t.status === 'FAILED')
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
    if (!detail || (detail.training.status !== 'RUNNING' && detail.training.status !== 'QUEUED')) return
    const id = detail.training.id
    const timer = setTimeout(() => {
      api
        .training(id)
        .then((d) => {
          setDetail(d)
          if (d.training.status === 'DONE' || d.training.status === 'FAILED') loadList(p.task)
        })
        .catch((e: Error) => p.onError(e.message))
    }, 2000)
    return () => clearTimeout(timer)
  }, [detail]) // eslint-disable-line react-hooks/exhaustive-deps

  const start = () =>
    p.perform(async () => {
      setDetail(null)
      setSelectedRun(null)
      const created = await api.startTraining({
        task: p.task,
        repetitions,
        agent: p.agent || undefined,
        provider: p.provider || undefined,
        profile: profile || undefined,
      })
      setDetail(await api.training(created.id))
      await loadList(p.task)
    })

  const open = (id: string) =>
    p.perform(async () => {
      setSelectedRun(null)
      setDetail(await api.training(id))
    })

  const taskName = p.tasks?.find((t) => t.id === p.task)?.name

  return (
    <>
      <section className="panel">
        <div className="panel-head">
          <h2>Einzeltraining</h2>
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
            <select value={profile} onChange={(e) => setProfile(e.target.value)} disabled={!p.profiles}>
              {p.profiles?.profiles.map((x) => (
                <option key={x.id} value={x.id}>
                  {x.name} · v{x.version}
                </option>
              ))}
            </select>
            <input type="number" min={1} max={50} value={repetitions} onChange={(e) => setRepetitions(Number(e.target.value))} />
            <button className="btn primary" onClick={start} disabled={p.busy || !p.task}>
              Training starten
            </button>
          </div>
          <p className="hint">
            Profil <strong>{profile || '–'}</strong> auf Aufgabe <strong>{taskName ?? '–'}</strong>, {repetitions} Läufe
            nacheinander. Jeder Lauf sieht, was die Strategien aus den vorigen Läufen behalten haben. Für eine Aussage fehlt
            hier die Bezugslinie — dafür ist das Experiment da.
          </p>
        </div>
      </section>

      {p.task && trainings.length > 0 && (
        <section className="panel">
          <div className="panel-head">
            <h2>Trainings · {taskName}</h2>
            <span className="small muted">alle Trainings dieser Aufgabe, neueste zuerst</span>
          </div>
          <table className="table">
            <thead>
              <tr>
                <th>Profil</th>
                <th>Agent</th>
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
                      <Badge tone={t.profileId === p.profiles?.defaultProfile ? 'neutral' : 'accent'}>{t.profileId}</Badge>
                      {t.experimentId && <Badge tone="info">Experiment</Badge>}
                    </td>
                    <td className="small">{t.agentId ?? '–'}</td>
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

      {detail && (
        <section className="panel">
          <div className="panel-head">
            <h2>Lernkurve</h2>
          </div>
          <TrainingDetailView
            detail={detail}
            selectedRunId={selectedRun?.id ?? null}
            onOpenRun={(id) => p.perform(async () => setSelectedRun(await api.run(id)))}
          />
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
