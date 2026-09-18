import type { AgentListResponse, StatusResponse } from '../api/client'
import { AREAS, type Area } from './areas'
import { Badge } from './Badge'

type Props = {
  area: Area
  onArea: (area: Area) => void
  agents: AgentListResponse | null
  agent: string
  onAgent: (id: string) => void
  status: StatusResponse | null
  provider: string
  onProvider: (id: string) => void
}

/**
 * Navigation plus the two settings that apply to everything that gets started: who works
 * and on which model. Everything else belongs to the area that uses it.
 */
export function NavSidebar(p: Props) {
  const selected = p.agents?.agents.find((a) => a.id === p.agent)
  const provider = p.status?.providers.find((x) => x.id === p.provider)

  return (
    <aside className="sidebar">
      <nav className="nav" aria-label="Bereiche">
        {AREAS.map((a) => (
          <button key={a.id} className={`nav-item ${p.area === a.id ? 'active' : ''}`} onClick={() => p.onArea(a.id)}>
            <span className="nav-label">{a.label}</span>
            <span className="nav-hint">{a.hint}</span>
          </button>
        ))}
      </nav>

      <section className="panel">
        <div className="panel-head">
          <h2>Aktiver Agent</h2>
          {selected && <Badge tone="solid">v{selected.version}</Badge>}
        </div>
        <div className="panel-body">
          <div className="field">
            <label>Agent</label>
            <select value={p.agent} onChange={(e) => p.onAgent(e.target.value)} disabled={!p.agents}>
              {p.agents?.agents.map((a) => (
                <option key={a.id} value={a.id}>
                  {a.name}
                </option>
              ))}
            </select>
          </div>
          {selected && (
            <div className="criteria">
              <Badge tone="neutral">SOUL {selected.soul ?? 'aktive'}</Badge>
              <Badge tone={selected.toolMode === 'ALL' ? 'warn' : 'neutral'}>
                {selected.toolMode === 'ALL'
                  ? `alle Werkzeuge (${selected.effectiveTools.length})`
                  : selected.toolMode === 'NONE'
                    ? 'keine Werkzeuge'
                    : `${selected.effectiveTools.length} von ${selected.tools.length} erlaubt`}
              </Badge>
              {selected.missingTools.length > 0 && <Badge tone="bad">fehlt: {selected.missingTools.join(', ')}</Badge>}
            </div>
          )}

          <div className="field">
            <label>Modell</label>
            <select value={p.provider} onChange={(e) => p.onProvider(e.target.value)} disabled={!p.status}>
              <option value="">— Vorgabe des Agenten —</option>
              {p.status?.providers.map((x) => (
                <option key={x.id} value={x.id}>
                  {x.label} · {x.model}
                  {x.configured ? '' : ' (nicht konfiguriert)'}
                </option>
              ))}
            </select>
          </div>
          <p className="hint">
            {provider
              ? provider.configured
                ? provider.local
                  ? 'Lokaler Endpunkt — kostet nichts.'
                  : 'Cloud-Endpunkt — jeder Lauf kostet echtes Kontingent.'
                : 'Nicht konfiguriert: Basis-URL, Modell oder Schlüssel fehlen.'
              : `Es gilt, was der Agent vorgibt${selected?.provider ? ` (${selected.provider})` : ''} — sonst der Standard.`}
          </p>
        </div>
      </section>
    </aside>
  )
}
