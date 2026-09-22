import type { AgentListResponse, StatusResponse } from '../api/client'
import { Badge } from './Badge'

type Props = {
  agents: AgentListResponse | null
  agent: string
  onAgent: (id: string) => void
  status: StatusResponse | null
  provider: string
  onProvider: (id: string) => void
}

/**
 * Who works and on which model — next to the button that starts something, not in a rail on
 * the other side of the screen. The selection itself lives in the app, so switching areas
 * does not reset it; only the control sits here.
 *
 * An empty model means: whatever the agent's file says. That is a real choice, not a missing
 * value, so it is an option of its own instead of a preselected provider.
 */
export function AgentPicker(p: Props) {
  const selected = p.agents?.agents.find((a) => a.id === p.agent)

  return (
    <>
      <select value={p.agent} onChange={(e) => p.onAgent(e.target.value)} disabled={!p.agents}>
        {p.agents?.agents.map((a) => (
          <option key={a.id} value={a.id}>
            {a.name} · v{a.version}
          </option>
        ))}
      </select>
      <select value={p.provider} onChange={(e) => p.onProvider(e.target.value)} disabled={!p.status}>
        <option value="">Modell: Vorgabe des Agenten</option>
        {p.status?.providers.map((x) => (
          <option key={x.id} value={x.id}>
            {x.label} · {x.model}
            {x.configured ? '' : ' (nicht konfiguriert)'}
          </option>
        ))}
      </select>
      {selected && (
        <span className="criteria small">
          <Badge tone="neutral">SOUL {selected.soul ?? 'aktive'}</Badge>
          <Badge tone={selected.toolMode === 'ALL' ? 'warn' : 'neutral'}>
            {selected.toolMode === 'ALL'
              ? `alle Werkzeuge (${selected.effectiveTools.length})`
              : selected.toolMode === 'NONE'
                ? 'keine Werkzeuge'
                : `${selected.effectiveTools.length} von ${selected.tools.length} erlaubt`}
          </Badge>
          {selected.missingTools.length > 0 && <Badge tone="bad">fehlt: {selected.missingTools.join(', ')}</Badge>}
        </span>
      )}
    </>
  )
}
