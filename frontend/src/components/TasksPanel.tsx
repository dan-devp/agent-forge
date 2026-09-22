import { useEffect, useState } from 'react'
import { api, type Task, type TaskSummary } from '../api/client'
import { Badge } from './Badge'
import { RawJson } from './RawJson'

type Props = {
  tasks: TaskSummary[] | null
  task: string
  onTask: (id: string) => void
  busy: boolean
  onReload: () => void
  onError: (message: string) => void
}

/** The challenges: goal text, success criteria, limits. Read-only — edited in the files. */
export function TasksPanel(p: Props) {
  const [detail, setDetail] = useState<Task | null>(null)

  useEffect(() => {
    if (!p.task) return
    api
      .task(p.task)
      .then(setDetail)
      .catch((e: Error) => p.onError(e.message))
  }, [p.task]) // eslint-disable-line react-hooks/exhaustive-deps

  return (
    <>
      <section className="panel">
        <div className="panel-head">
          <h2>Aufgaben</h2>
          <button className="btn ghost sm" onClick={p.onReload} disabled={p.busy}>
            neu laden
          </button>
        </div>
        <table className="table">
          <thead>
            <tr>
              <th>Aufgabe</th>
              <th className="num">Version</th>
              <th className="num">Stufe</th>
              <th>Kriterien</th>
              <th>Vorgaben</th>
            </tr>
          </thead>
          <tbody>
            {p.tasks?.map((t) => (
              <tr key={t.id} className={`clickable ${t.id === p.task ? 'selected' : ''}`} onClick={() => p.onTask(t.id)}>
                <td>
                  <strong>{t.name}</strong>
                  <div className="small faint">{t.id}</div>
                </td>
                <td className="num">{t.version}</td>
                <td className="num">{t.difficulty}</td>
                <td>{t.id === detail?.id ? `${detail.success.length}` : '–'}</td>
                <td className="small">
                  {[t.soul && `SOUL ${t.soul}`, t.provider && `Modell ${t.provider}`, t.profile && `Profil ${t.profile}`]
                    .filter(Boolean)
                    .join(' · ') || '—'}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </section>

      {detail && detail.id === p.task && (
        <section className="panel">
          <div className="panel-head">
            <h2>{detail.name}</h2>
            <div className="criteria">
              <Badge tone="solid">v{detail.version}</Badge>
              {detail.tags.map((tag) => (
                <Badge key={tag}>{tag}</Badge>
              ))}
            </div>
          </div>
          <div className="panel-body">
            {detail.description && <p className="hint">{detail.description}</p>}
            <div className="section-title">Auftrag an den Agenten</div>
            <div className="task-preview">{detail.goal}</div>

            <div className="section-title">Erfolgskriterien</div>
            {detail.success.length === 0 ? (
              <p className="hint">
                Keine — diese Aufgabe lässt sich nicht bewerten. Ohne Kriterium gibt es kein Urteil und damit keine Lernkurve.
              </p>
            ) : (
              <ul className="memory">
                {detail.success.map((c, i) => (
                  <li key={i}>
                    <Badge tone="accent" mono>
                      {c.type}
                    </Badge>
                    <span className="mono small">{c.value}</span>
                    {c.description && <div className="content">{c.description}</div>}
                  </li>
                ))}
              </ul>
            )}

            <div className="criteria">
              {detail.maxToolCalls != null && <Badge tone="neutral">max. {detail.maxToolCalls} Tool-Aufrufe</Badge>}
              {detail.maxDurationMs != null && <Badge tone="neutral">max. {detail.maxDurationMs} ms</Badge>}
            </div>

            {Object.entries(detail.sections).map(([heading, text]) => (
              <div key={heading}>
                <div className="section-title">{heading}</div>
                <p className="small">{text}</p>
              </div>
            ))}
            <p className="hint faint">{detail.source}</p>
            <RawJson label={`GET /api/tasks/${detail.id}`} value={detail} />
          </div>
        </section>
      )}
    </>
  )
}
