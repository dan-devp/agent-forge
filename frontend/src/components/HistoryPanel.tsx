import { useEffect, useState } from 'react'
import { api, type AgentRun } from '../api/client'
import { PassBadge, RunStatusBadge } from './Badge'
import { RunDetail } from './RunDetail'

type Props = {
  perform: (action: () => Promise<void>) => void
  onError: (message: string) => void
}

/** All runs, newest first; click one for its trace. */
export function HistoryPanel(p: Props) {
  const [runs, setRuns] = useState<AgentRun[] | null>(null)
  const [selected, setSelected] = useState<AgentRun | null>(null)

  const load = () => api.runs().then(setRuns).catch((e: Error) => p.onError(e.message))

  useEffect(() => {
    load()
  }, []) // eslint-disable-line react-hooks/exhaustive-deps

  const open = (id: string) =>
    p.perform(async () => {
      setSelected(await api.run(id))
    })

  return (
    <>
      <section className="panel">
        <div className="panel-head">
          <h2>Läufe</h2>
          <button className="btn ghost sm" onClick={load}>
            aktualisieren
          </button>
        </div>
        {!runs ? (
          <div className="empty">lädt …</div>
        ) : runs.length === 0 ? (
          <div className="empty">Noch kein Lauf.</div>
        ) : (
          <table className="table">
            <thead>
              <tr>
                <th>Status</th>
                <th>Aufgabe</th>
                <th>SOUL</th>
                <th>Profil</th>
                <th>Provider</th>
                <th className="num">Schritte</th>
                <th className="num">Tokens</th>
                <th className="num">ms</th>
                <th>gestartet</th>
              </tr>
            </thead>
            <tbody>
              {runs.map((r) => (
                <tr key={r.id} className={`clickable ${selected?.id === r.id ? 'selected' : ''}`} onClick={() => open(r.id)}>
                  <td>
                    <RunStatusBadge status={r.status} />
                  </td>
                  <td>
                    {r.taskId ? (
                      <>
                        {r.taskId} <span className="faint">v{r.taskVersion}</span>
                      </>
                    ) : (
                      <span className="faint">frei</span>
                    )}
                    {r.seriesId && <span className="faint"> · Serie</span>}
                  </td>
                  <td>
                    {r.soulId} <span className="faint">v{r.soulVersion}</span>
                  </td>
                  <td>{r.profileId ?? <span className="faint">–</span>}</td>
                  <td className="small">{r.providerId}</td>
                  <td className="num">{r.steps}</td>
                  <td className="num">{r.totalTokens ?? '–'}</td>
                  <td className="num">{r.durationMs ?? '–'}</td>
                  <td className="small muted">{new Date(r.startedAt).toLocaleString('de-DE')}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </section>

      {selected && (
        <section className="panel">
          <div className="panel-head">
            <h2>Lauf im Detail</h2>
            <div className="toolbar">
              {selected.scores.map((s) => (
                <PassBadge key={s.grader} passed={s.passed} />
              ))}
              <button className="btn ghost sm" onClick={() => setSelected(null)}>
                schließen
              </button>
            </div>
          </div>
          <RunDetail run={selected} />
        </section>
      )}
    </>
  )
}
