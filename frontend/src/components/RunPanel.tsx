import { useEffect, useState } from 'react'
import { api, type AgentRun, type Task, type TaskSummary } from '../api/client'
import type { Selection } from './Sidebar'
import { Badge } from './Badge'
import { RunDetail } from './RunDetail'

type Props = {
  tasks: TaskSummary[] | null
  task: string
  onTaskChange: (id: string) => void
  selection: Selection
  busy: boolean
  perform: (action: () => Promise<void>) => void
  onReloadTasks: () => void
  onError: (message: string) => void
}

/** One run: pick a task or type a goal, start, read answer, scores and trace. */
export function RunPanel(p: Props) {
  const [taskDetail, setTaskDetail] = useState<Task | null>(null)
  const [goal, setGoal] = useState('Welcher Wochentag ist heute, und wie oft passt 7 in 100?')
  const [run, setRun] = useState<AgentRun | null>(null)

  // The goal text of the selected task is what the agent receives — show it.
  useEffect(() => {
    if (!p.task) return
    api
      .task(p.task)
      .then(setTaskDetail)
      .catch((e: Error) => p.onError(e.message))
  }, [p.task]) // eslint-disable-line react-hooks/exhaustive-deps

  const start = () =>
    p.perform(async () => {
      setRun(null)
      const { provider, soul, profile } = p.selection
      // Named task when one is selected — only that one can be evaluated later.
      setRun(await api.startRun(p.task ? { task: p.task, provider, soul, profile } : { goal, provider, soul, profile }))
    })

  return (
    <>
      <section className="panel">
        <div className="panel-head">
          <h2>Auftrag</h2>
          <button className="btn ghost sm" onClick={p.onReloadTasks} disabled={p.busy}>
            Aufgaben neu laden
          </button>
        </div>
        <div className="panel-body">
          <div className="toolbar">
            <select className="grow" value={p.task} onChange={(e) => p.onTaskChange(e.target.value)} disabled={!p.tasks}>
              <option value="">— freier Auftrag (nicht auswertbar) —</option>
              {p.tasks?.map((t) => (
                <option key={t.id} value={t.id}>
                  {t.name} · v{t.version} · Stufe {t.difficulty}
                </option>
              ))}
            </select>
            <button className="btn primary" onClick={start} disabled={p.busy || (!p.task && !goal.trim()) || !p.selection.provider}>
              {p.busy ? <span className="spinner" /> : null}
              {p.busy ? 'Agent arbeitet …' : 'Lauf starten'}
            </button>
          </div>

          {p.task && taskDetail?.id === p.task ? (
            <>
              {taskDetail.description && <p className="hint">{taskDetail.description}</p>}
              <div className="task-preview">{taskDetail.goal}</div>
              <div className="criteria">
                {taskDetail.tags.map((tag) => (
                  <Badge key={tag}>{tag}</Badge>
                ))}
                {taskDetail.soul && <Badge tone="neutral">SOUL-Vorgabe {taskDetail.soul}</Badge>}
                {taskDetail.profile && <Badge tone="neutral">Profil-Vorgabe {taskDetail.profile}</Badge>}
                {taskDetail.maxToolCalls != null && <Badge tone="neutral">max. {taskDetail.maxToolCalls} Tool-Aufrufe</Badge>}
              </div>
              {taskDetail.success.length > 0 && (
                <>
                  <div className="section-title">Erfolgskriterien</div>
                  <div className="criteria">
                    {taskDetail.success.map((c, i) => (
                      <Badge key={i} tone="accent" mono>
                        {c.type} "{c.value}"
                      </Badge>
                    ))}
                  </div>
                </>
              )}
              <p className="hint faint">{taskDetail.source}</p>
            </>
          ) : (
            <textarea value={goal} onChange={(e) => setGoal(e.target.value)} rows={3} placeholder="Auftrag an den Agenten" />
          )}
        </div>
      </section>

      {run && (
        <section className="panel">
          <div className="panel-head">
            <h2>Ergebnis</h2>
            <span className="small faint">{run.id}</span>
          </div>
          <RunDetail run={run} />
        </section>
      )}
    </>
  )
}
