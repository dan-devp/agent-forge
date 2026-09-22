import type { ProfileListResponse } from '../api/client'
import { Badge } from './Badge'
import { RawJson } from './RawJson'

type Props = {
  profiles: ProfileListResponse | null
  busy: boolean
  onReload: () => void
}

/**
 * Two things that are easy to confuse: a strategy is code with five hooks into the run loop,
 * a profile is the Markdown file that switches strategies on and parameterises them. Only
 * profiles can be edited — a new strategy is a new bean.
 */
export function StrategiesPanel(p: Props) {
  return (
    <>
      <section className="panel">
        <div className="panel-head">
          <h2>Strategie-Katalog</h2>
          <span className="small muted">im Code registriert, nicht editierbar</span>
        </div>
        <div className="panel-body">
          <div className="criteria">
            {p.profiles?.availableStrategies.map((id) => (
              <Badge key={id} tone="accent" mono>
                {id}
              </Badge>
            ))}
          </div>
          <p className="hint">
            Jede Strategie hängt sich in fünf Punkte des Laufs: <strong>beforeRun</strong> (Prompt erweitern, Werkzeuge
            anbieten), <strong>afterToolRound</strong> (beobachten), <strong>beforeFinish</strong> (Antwort zurückweisen),{' '}
            <strong>afterRun</strong> (Gelerntes schreiben), <strong>betweenRuns</strong> (konsolidieren — noch ohne Auslöser).
            Was sie behält, liegt im Speicher des Trainings, nie in der SOUL: eine Identität, die sich mitten im Training
            ändert, macht den Vergleich wertlos.
          </p>
        </div>
      </section>

      <section className="panel">
        <div className="panel-head">
          <h2>Profile</h2>
          <button className="btn ghost sm" onClick={p.onReload} disabled={p.busy}>
            neu laden
          </button>
        </div>
        <div className="panel-body">
          <div className="cards">
            {p.profiles?.profiles.map((profile) => (
              <article key={profile.id} className="card">
                <div className="card-head">
                  <h3>{profile.name}</h3>
                  <div className="criteria">
                    <Badge tone="neutral" mono>
                      {profile.id}
                    </Badge>
                    <Badge tone="solid">v{profile.version}</Badge>
                    {profile.isDefault && <Badge tone="ok">Standard</Badge>}
                  </div>
                </div>
                {profile.description && <p className="small">{profile.description}</p>}
                {profile.strategies.length === 0 ? (
                  <p className="hint">Keine Strategie — die Bezugslinie jedes Vergleichs.</p>
                ) : (
                  <ul className="memory">
                    {profile.strategies.map((s) => (
                      <li key={s.id}>
                        <Badge tone="accent" mono>
                          {s.id}
                        </Badge>
                        <div className="content mono small">
                          {Object.entries(s.params).length === 0
                            ? 'ohne Parameter'
                            : Object.entries(s.params)
                                .map(([k, v]) => `${k}: ${String(v)}`)
                                .join(' · ')}
                        </div>
                      </li>
                    ))}
                  </ul>
                )}
              </article>
            ))}
          </div>
          <RawJson label="GET /api/profiles" value={p.profiles} />
        </div>
      </section>
    </>
  )
}
