import { useState } from 'react'
import { api, type AgentRun, type ExperimentDetail, type TrainingDetail } from '../api/client'
import { Badge, ExperimentStatusBadge, TrainingStatusBadge } from './Badge'
import { RunDetail } from './RunDetail'
import { RunStrip } from './RunStrip'
import { TrainingDetailView } from './TrainingDetailView'
import { RawJson } from './RawJson'
import { mean, pct } from './format'

type Props = {
  detail: ExperimentDetail
  busy: boolean
  perform: (action: () => Promise<void>) => void
  onCancel: () => void
}

/** One execution: the trainings side by side, and everything underneath on demand. */
export function ExperimentView(p: Props) {
  const [training, setTraining] = useState<TrainingDetail | null>(null)
  const [run, setRun] = useState<AgentRun | null>(null)
  const detail = p.detail
  const baseline = detail.trainings.find((a) => a.training.profileId === 'baseline') ?? detail.trainings[0]

  const openTraining = (trainingId: string) =>
    p.perform(async () => {
      setRun(null)
      setTraining(await api.training(trainingId))
    })

  return (
    <>
      <section className="panel">
        <div className="panel-head">
          <h2>Vergleich{detail.experiment.name ? ` · ${detail.experiment.name}` : ''}</h2>
          <ExperimentStatusBadge status={detail.experiment.status} />
          {detail.experiment.status === 'RUNNING' && (
            <button className="btn sm" onClick={p.onCancel} disabled={p.busy}>
              stoppen
            </button>
          )}
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
            {detail.trainings.map((a) => {
              const isBaseline = a.training.id === baseline?.training.id
              const delta = baseline && !isBaseline ? a.statistics.passRate - baseline.statistics.passRate : null
              return (
                <tr
                  key={a.training.id}
                  className={`clickable ${training?.training.id === a.training.id ? 'selected' : ''}`}
                  onClick={() => openTraining(a.training.id)}
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
          <RawJson label={`GET /api/experiments/${detail.experiment.id}`} value={detail} />
        </div>
      </section>

      {training && (
        <section className="panel">
          <div className="panel-head">
            <h2>Training · Profil {training.training.profileId}</h2>
            <button className="btn ghost sm" onClick={() => setTraining(null)}>
              schließen
            </button>
          </div>
          <TrainingDetailView
            detail={training}
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
