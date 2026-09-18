import { useEffect, useState } from 'react'
import { api, type SoulDetail, type SoulListResponse, type StatusResponse, type ToolListResponse } from '../api/client'
import { Badge } from './Badge'

type Props = {
  status: StatusResponse | null
  souls: SoulListResponse | null
  tools: ToolListResponse | null
  busy: boolean
  onReloadSouls: () => void
  onReloadTools: () => void
  onError: (message: string) => void
}

/**
 * The stock an agent is assembled from: models, SOUL templates, tools. Read-only on purpose —
 * models come from the configuration because keys do not belong in a database, SOULs from
 * files because their history belongs in Git.
 */
export function SettingsPanel(p: Props) {
  const [chosenSoul, setSoul] = useState<string | null>(null)
  const soul = chosenSoul ?? p.souls?.activeSoul ?? ''
  const [detail, setDetail] = useState<SoulDetail | null>(null)
  const [promptOpen, setPromptOpen] = useState(false)

  useEffect(() => {
    if (!soul) return
    api
      .soul(soul)
      .then(setDetail)
      .catch((e: Error) => p.onError(e.message))
  }, [soul]) // eslint-disable-line react-hooks/exhaustive-deps

  return (
    <>
      <section className="panel">
        <div className="panel-head">
          <h2>Modelle</h2>
          <span className="small muted">aus der Konfiguration, nicht aus der Datenbank</span>
        </div>
        <table className="table">
          <thead>
            <tr>
              <th>Provider</th>
              <th>Modell</th>
              <th>Endpunkt</th>
              <th>Status</th>
            </tr>
          </thead>
          <tbody>
            {p.status?.providers.map((x) => (
              <tr key={x.id}>
                <td>
                  <strong>{x.label}</strong>
                  <div className="small faint">{x.id}</div>
                </td>
                <td className="mono small">{x.model}</td>
                <td className="small faint">{x.baseUrl}</td>
                <td>
                  <Badge tone={x.configured ? (x.local ? 'ok' : 'info') : 'bad'}>
                    {x.configured ? (x.local ? 'lokal' : 'cloud') : 'nicht konfiguriert'}
                  </Badge>
                  {x.isDefault && <Badge tone="neutral">Standard</Badge>}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
        <div className="panel-body">
          <p className="hint">
            Schlüssel und Basis-URLs stehen in <code>.env</code>. Ein Provider gilt als nicht konfiguriert, solange ein
            Platzhalter-Schlüssel eingetragen ist — sonst fiele der Fehler erst beim ersten Modellaufruf als 401 auf.
          </p>
        </div>
      </section>

      <section className="panel">
        <div className="panel-head">
          <h2>SOUL-Vorlagen</h2>
          <button className="btn ghost sm" onClick={p.onReloadSouls} disabled={p.busy}>
            neu laden
          </button>
        </div>
        <div className="panel-body">
          <div className="field">
            <label>Identität</label>
            <select value={soul} onChange={(e) => setSoul(e.target.value)} disabled={!p.souls}>
              {p.souls?.souls.map((s) => (
                <option key={s.id} value={s.id}>
                  {s.name}
                  {s.role ? ` · ${s.role}` : ''} · v{s.version}
                </option>
              ))}
            </select>
          </div>
          {detail?.description && <p className="hint">{detail.description}</p>}
          {detail && detail.soul.gates.length > 0 && (
            <ul className="gates">
              {detail.soul.gates.map((g) => (
                <li key={g.id}>
                  <Badge tone={g.blocking ? 'warn' : 'neutral'}>{g.blocking ? 'Gate' : 'Hinweis'}</Badge>
                  <span>{g.question}</span>
                </li>
              ))}
            </ul>
          )}
          <p className="hint">
            Die SOUL <em>ist</em> die Systemprompt-Vorlage — es gibt daneben keine zweite. Was eine Strategie beisteuert, wird
            beim Lauf unten angehängt und dort eingefroren (<code>agent_run.system_prompt</code>).
          </p>
          {detail && (
            <button className="link small" onClick={() => setPromptOpen(!promptOpen)}>
              {promptOpen ? 'Prompt verbergen' : 'Gerenderten Prompt zeigen'}
            </button>
          )}
          {promptOpen && detail && <pre className="prompt-box">{detail.prompt}</pre>}
          {detail && <p className="hint faint">{detail.source}</p>}
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
          <p className="hint">
            Wer welches Werkzeug aufrufen darf, entscheidet die Positivliste des Agenten — hier steht nur, was überhaupt
            registriert ist.
          </p>
        </div>
      </section>
    </>
  )
}
