import { useState } from 'react'
import type { ProfileListResponse, SoulDetail, SoulListResponse, StatusResponse, ToolListResponse } from '../api/client'
import { Badge } from './Badge'

export type Selection = {
  provider: string
  soul: string
  profile: string
}

type Props = {
  status: StatusResponse | null
  souls: SoulListResponse | null
  soulDetail: SoulDetail | null
  profiles: ProfileListResponse | null
  tools: ToolListResponse | null
  selection: Selection
  busy: boolean
  onChange: (next: Partial<Selection>) => void
  onReloadProfiles: () => void
  onReloadTools: () => void
  onReloadSouls: () => void
}

/** Everything a run is configured with. Stays put while the main area changes. */
export function Sidebar(p: Props) {
  const [promptOpen, setPromptOpen] = useState(false)
  const selectedProvider = p.status?.providers.find((x) => x.id === p.selection.provider)
  const selectedProfile = p.profiles?.profiles.find((x) => x.id === p.selection.profile)
  const gates = p.soulDetail?.soul.gates ?? []

  return (
    <aside className="sidebar">
      <section className="panel">
        <div className="panel-head">
          <h2>Modell</h2>
          {selectedProvider && (
            <Badge tone={selectedProvider.configured ? (selectedProvider.local ? 'ok' : 'info') : 'bad'}>
              {selectedProvider.configured ? (selectedProvider.local ? 'lokal' : 'cloud') : 'nicht konfiguriert'}
            </Badge>
          )}
        </div>
        <div className="panel-body">
          <div className="field">
            <label>Provider</label>
            <select
              value={p.selection.provider}
              onChange={(e) => p.onChange({ provider: e.target.value })}
              disabled={!p.status}
            >
              {p.status?.providers.map((x) => (
                <option key={x.id} value={x.id}>
                  {x.label} · {x.model}
                  {x.configured ? '' : ' (nicht konfiguriert)'}
                </option>
              ))}
            </select>
          </div>
          <p className="hint">Vorerst ein Modell für alle Rollen. Judge und Reflektor bekommen später eigene Provider.</p>
        </div>
      </section>

      <section className="panel">
        <div className="panel-head">
          <h2>SOUL</h2>
          <button className="btn ghost sm" onClick={p.onReloadSouls} disabled={p.busy}>
            neu laden
          </button>
        </div>
        <div className="panel-body">
          <div className="field">
            <label>Identität</label>
            <select value={p.selection.soul} onChange={(e) => p.onChange({ soul: e.target.value })} disabled={!p.souls}>
              {p.souls?.souls.map((s) => (
                <option key={s.id} value={s.id}>
                  {s.name}
                  {s.role ? ` · ${s.role}` : ''} · v{s.version}
                </option>
              ))}
            </select>
          </div>
          {p.soulDetail?.description && <p className="hint">{p.soulDetail.description}</p>}
          {gates.length > 0 && (
            <ul className="gates">
              {gates.map((g) => (
                <li key={g.id}>
                  <Badge tone={g.blocking ? 'warn' : 'neutral'}>{g.blocking ? 'Gate' : 'Hinweis'}</Badge>
                  <span>{g.question}</span>
                </li>
              ))}
            </ul>
          )}
          {p.soulDetail && (
            <button className="link small" onClick={() => setPromptOpen(!promptOpen)}>
              {promptOpen ? 'Prompt verbergen' : 'Gerenderten Prompt zeigen'}
            </button>
          )}
          {promptOpen && p.soulDetail && <pre className="prompt-box">{p.soulDetail.prompt}</pre>}
        </div>
      </section>

      <section className="panel">
        <div className="panel-head">
          <h2>Strategie-Profil</h2>
          <button className="btn ghost sm" onClick={p.onReloadProfiles} disabled={p.busy}>
            neu laden
          </button>
        </div>
        <div className="panel-body">
          <div className="field">
            <label>Profil</label>
            <select
              value={p.selection.profile}
              onChange={(e) => p.onChange({ profile: e.target.value })}
              disabled={!p.profiles}
            >
              {p.profiles?.profiles.map((x) => (
                <option key={x.id} value={x.id}>
                  {x.name} · v{x.version}
                  {x.isDefault ? ' · Default' : ''}
                </option>
              ))}
            </select>
          </div>
          {selectedProfile?.description && <p className="hint">{selectedProfile.description}</p>}
          <div className="criteria">
            {selectedProfile && selectedProfile.strategies.length === 0 && (
              <Badge tone="neutral">keine Strategie · Baseline</Badge>
            )}
            {selectedProfile?.strategies.map((s) => (
              <Badge key={s.id} tone="accent" mono>
                {s.id}
                {Object.entries(s.params)
                  .map(([k, v]) => ` ${k}=${String(v)}`)
                  .join('')}
              </Badge>
            ))}
          </div>
          {p.profiles && (
            <p className="hint">
              Registriert: {p.profiles.availableStrategies.length === 0 ? 'keine' : p.profiles.availableStrategies.join(', ')}
            </p>
          )}
        </div>
      </section>

      <section className="panel">
        <div className="panel-head">
          <h2>Werkzeuge</h2>
          <button className="btn ghost sm" onClick={p.onReloadTools} disabled={p.busy}>
            MCP neu verbinden
          </button>
        </div>
        <div className="panel-body">
          {p.tools?.servers.length === 0 && (
            <p className="hint">
              Kein MCP-Server registriert. Server unter <code>app.tools.mcp.&lt;id&gt;</code> eintragen.
            </p>
          )}
          {p.tools && p.tools.servers.length > 0 && (
            <ul className="tool-list">
              {p.tools.servers.map((s) => (
                <li key={s.id}>
                  <Badge tone={!s.enabled ? 'neutral' : s.connected ? 'ok' : 'bad'}>
                    {!s.enabled ? 'aus' : s.connected ? `${s.toolCount} Tools` : 'nicht erreichbar'}
                  </Badge>
                  <span>
                    {s.label} · {s.transport}
                    {s.error && <span className="faint"> — {s.error}</span>}
                  </span>
                </li>
              ))}
            </ul>
          )}
          {p.tools && p.tools.tools.length > 0 && (
            <ul className="tool-list">
              {p.tools.tools.map((t) => (
                <li key={t.name}>
                  <Badge tone={t.source === 'mcp' ? 'info' : 'neutral'} mono>
                    {t.source}
                  </Badge>
                  <span>
                    <code>{t.name}</code>
                    {t.description && <span className="faint"> — {t.description}</span>}
                  </span>
                </li>
              ))}
            </ul>
          )}
        </div>
      </section>
    </aside>
  )
}
