import { useEffect, useState } from 'react'
import {
  api,
  type AgentListResponse,
  type ExperimentDetail,
  type Plan,
  type PlanBody,
  type PlanDetail,
  type ProfileListResponse,
  type StatusResponse,
  type TaskSummary,
} from '../api/client'
import { Badge, ExperimentStatusBadge } from './Badge'
import { ExperimentView } from './ExperimentView'
import { PlanWizard } from './PlanWizard'
import { RawJson } from './RawJson'

type Props = {
  tasks: TaskSummary[] | null
  profiles: ProfileListResponse | null
  agents: AgentListResponse | null
  status: StatusResponse | null
  busy: boolean
  perform: (action: () => Promise<void>) => void
  onError: (message: string) => void
}

/**
 * Plans and their executions. Saving and starting are two acts: the form is behind "Neu", and
 * what it produces is a plan, not a running experiment. Starting it a second time is then a
 * single click — and that is the point, because a comparison repeated after a change is the
 * only way to see whether the change did anything.
 */
export function PlansPanel(p: Props) {
  const [plans, setPlans] = useState<Plan[]>([])
  const [detail, setDetail] = useState<PlanDetail | null>(null)
  const [experiment, setExperiment] = useState<ExperimentDetail | null>(null)
  const [wizard, setWizard] = useState<{ plan: Plan | null } | null>(null)

  const loadPlans = () =>
    api
      .plans()
      .then(setPlans)
      .catch((e: Error) => p.onError(e.message))

  const openPlan = (id: string) =>
    api
      .plan(id)
      .then(setDetail)
      .catch((e: Error) => p.onError(e.message))

  useEffect(() => {
    loadPlans()
  }, []) // eslint-disable-line react-hooks/exhaustive-deps

  // Poll while an execution is running: the trainings are trained one after another.
  useEffect(() => {
    if (!experiment || experiment.experiment.status !== 'RUNNING') return
    const id = experiment.experiment.id
    const timer = setTimeout(() => {
      api
        .experiment(id)
        .then((d) => {
          setExperiment(d)
          if (d.experiment.status !== 'RUNNING' && detail) openPlan(detail.plan.id)
        })
        .catch((e: Error) => p.onError(e.message))
    }, 2000)
    return () => clearTimeout(timer)
  }, [experiment]) // eslint-disable-line react-hooks/exhaustive-deps

  const save = (body: PlanBody) =>
    p.perform(async () => {
      const saved = wizard?.plan ? await api.updatePlan(wizard.plan.id, body) : await api.createPlan(body)
      setWizard(null)
      await loadPlans()
      await openPlan(saved.id)
    })

  const start = (plan: Plan) =>
    p.perform(async () => {
      const created = await api.startPlan(plan.id)
      setExperiment(await api.experiment(created.id))
      await openPlan(plan.id)
    })

  const remove = (plan: Plan) =>
    p.perform(async () => {
      await api.deletePlan(plan.id)
      if (detail?.plan.id === plan.id) setDetail(null)
      await loadPlans()
    })

  const openExperiment = (id: string) =>
    p.perform(async () => {
      setExperiment(await api.experiment(id))
    })

  const cancel = () =>
    p.perform(async () => {
      if (!experiment) return
      await api.cancelExperiment(experiment.experiment.id)
      setExperiment(await api.experiment(experiment.experiment.id))
      if (detail) await openPlan(detail.plan.id)
    })

  const taskName = (id: string) => p.tasks?.find((t) => t.id === id)?.name ?? id

  return (
    <>
      <section className="panel">
        <div className="panel-head">
          <h2>Pläne</h2>
          <button className="btn primary sm" onClick={() => setWizard({ plan: null })}>
            Neu
          </button>
        </div>
        {plans.length === 0 ? (
          <div className="panel-body">
            <p className="hint">
              Noch kein Plan. Ein Plan hält fest, was zwischen zwei Messungen gleich bleiben muss — Agent, Aufgabe,
              Trainings, Umfang. Gestartet wird er danach, so oft du willst.
            </p>
          </div>
        ) : (
          <table className="table">
            <thead>
              <tr>
                <th>Plan</th>
                <th>Aufgabe</th>
                <th>Agent</th>
                <th>Trainings</th>
                <th className="num">N</th>
                <th />
              </tr>
            </thead>
            <tbody>
              {plans.map((plan) => (
                <tr
                  key={plan.id}
                  className={`clickable ${detail?.plan.id === plan.id ? 'selected' : ''}`}
                  onClick={() => openPlan(plan.id)}
                >
                  <td>
                    <strong>{plan.name}</strong>
                    {plan.description && <div className="small faint">{plan.description}</div>}
                  </td>
                  <td className="small">{taskName(plan.taskId)}</td>
                  <td className="small">{plan.agentId ?? '–'}</td>
                  <td>
                    <span className="criteria">
                      {plan.includeBaseline && <Badge tone="neutral">baseline</Badge>}
                      {plan.profiles.map((x) => (
                        <Badge key={x} tone="accent">
                          {x}
                        </Badge>
                      ))}
                    </span>
                  </td>
                  <td className="num">{plan.repetitions}</td>
                  <td>
                    <span className="toolbar" onClick={(e) => e.stopPropagation()}>
                      <button className="btn primary sm" onClick={() => start(plan)} disabled={p.busy}>
                        Starten
                      </button>
                      <button className="btn sm" onClick={() => setWizard({ plan })} disabled={p.busy}>
                        Bearbeiten
                      </button>
                      <button className="btn ghost sm" onClick={() => remove(plan)} disabled={p.busy}>
                        Löschen
                      </button>
                    </span>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </section>

      {detail && (
        <section className="panel">
          <div className="panel-head">
            <h2>Durchläufe · {detail.plan.name}</h2>
            <span className="small muted">
              {detail.experiments.length === 0 ? 'noch nie gestartet' : `${detail.experiments.length} Start(s)`}
            </span>
          </div>
          {detail.experiments.length === 0 ? (
            <div className="panel-body">
              <p className="hint">
                Dieser Plan wurde noch nicht ausgeführt. Jeder Start erzeugt ein eigenes Experiment — zwei Starts
                desselben Plans sind zwei Messungen derselben Frage, und ihr Unterschied ist die Streuung.
              </p>
            </div>
          ) : (
            <table className="table">
              <thead>
                <tr>
                  <th>Start</th>
                  <th>Status</th>
                  <th className="num">N</th>
                  <th>Agent</th>
                  <th>Aufgabe</th>
                </tr>
              </thead>
              <tbody>
                {detail.experiments.map((e) => (
                  <tr
                    key={e.id}
                    className={`clickable ${experiment?.experiment.id === e.id ? 'selected' : ''}`}
                    onClick={() => openExperiment(e.id)}
                  >
                    <td className="small muted">{new Date(e.createdAt).toLocaleString('de-DE')}</td>
                    <td>
                      <ExperimentStatusBadge status={e.status} />
                    </td>
                    <td className="num">{e.repetitions}</td>
                    <td className="small">
                      {e.agentId} {e.agentVersion != null && `v${e.agentVersion}`}
                    </td>
                    <td className="small">
                      {e.taskId} v{e.taskVersion}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
          <div className="panel-body">
            <RawJson label={`GET /api/plans/${detail.plan.id}`} value={detail} />
          </div>
        </section>
      )}

      {experiment && <ExperimentView detail={experiment} busy={p.busy} perform={p.perform} onCancel={cancel} />}

      {wizard && (
        <PlanWizard
          plan={wizard.plan}
          agents={p.agents}
          status={p.status}
          tasks={p.tasks}
          profiles={p.profiles}
          busy={p.busy}
          onSave={save}
          onClose={() => setWizard(null)}
        />
      )}
    </>
  )
}
