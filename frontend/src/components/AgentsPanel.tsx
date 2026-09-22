import type { AgentListResponse, AgentView } from '../api/client'
import { Badge } from './Badge'

type Props = {
  agents: AgentListResponse | null
  agent: string
  onAgent: (id: string) => void
  busy: boolean
  onReload: () => void
}

function toolBadge(a: AgentView) {
  // Ungoverned is the state worth noticing, not the allowlist.
  if (a.toolMode === 'ALL') return <Badge tone="warn">alle Werkzeuge</Badge>
  if (a.toolMode === 'NONE') return <Badge tone="neutral">keine Werkzeuge</Badge>
  return <Badge tone="solid">Positivliste</Badge>
}

/** The catalogue of workers: model, identity and tool allowlist per agent. */
export function AgentsPanel(p: Props) {
  return (
    <section className="panel">
      <div className="panel-head">
        <h2>Agenten</h2>
        <button className="btn ghost sm" onClick={p.onReload} disabled={p.busy}>
          neu laden
        </button>
      </div>
      <div className="panel-body">
        <p className="hint">
          Ein Agent ist eine versionierte Komposition aus <strong>Modell</strong>, <strong>SOUL</strong> und einer{' '}
          <strong>Werkzeug-Positivliste</strong>. Wie er lernt, steht nicht hier, sondern im Strategie-Profil eines Trainings —
          nur so misst ein Vergleich die Strategie und nicht den Agenten. Bearbeitet wird in den Dateien, damit die Historie im
          Git bleibt.
        </p>

        <div className="cards">
          {p.agents?.agents.map((a) => (
            <article
              key={a.id}
              className={`card clickable ${a.id === p.agent ? 'selected' : ''}`}
              onClick={() => p.onAgent(a.id)}
            >
              <div className="card-head">
                <h3>{a.name}</h3>
                <div className="criteria">
                  <Badge tone="neutral" mono>
                    {a.id}
                  </Badge>
                  <Badge tone="solid">v{a.version}</Badge>
                  {a.id === p.agents?.defaultAgent && <Badge tone="ok">Standard</Badge>}
                </div>
              </div>
              {a.description && <p className="small">{a.description}</p>}

              <dl className="facts">
                <div>
                  <dt>Modell</dt>
                  <dd>{a.provider ?? 'Vorauswahl'}</dd>
                </div>
                <div>
                  <dt>SOUL</dt>
                  <dd>{a.soul ?? 'aktive SOUL'}</dd>
                </div>
                <div>
                  <dt>Werkzeuge</dt>
                  <dd>{toolBadge(a)}</dd>
                </div>
              </dl>

              {a.effectiveTools.length > 0 && (
                <div className="criteria">
                  {a.effectiveTools.map((t) => (
                    <Badge key={t} mono>
                      {t}
                    </Badge>
                  ))}
                </div>
              )}
              {a.missingTools.length > 0 && (
                <p className="error small">
                  Verlangt, aber nicht registriert: {a.missingTools.join(', ')} — MCP-Server nicht verbunden oder Tippfehler.
                </p>
              )}
              {a.toolMode === 'ALL' && (
                <p className="hint faint">
                  Ohne Positivliste wächst die Werkzeugmenge mit jedem neu verbundenen MCP-Server mit.
                </p>
              )}
              <p className="hint faint">{a.source}</p>

              {/* What the API actually answers — the card is an interpretation, this is the record. */}
              <details className="raw" onClick={(e) => e.stopPropagation()}>
                <summary>JSON · GET /api/agents/{a.id}</summary>
                <pre>{JSON.stringify(a, null, 2)}</pre>
              </details>
            </article>
          ))}
        </div>
      </div>
    </section>
  )
}
