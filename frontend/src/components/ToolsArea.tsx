import { useEffect, useState } from 'react'
import { catalogApi, type ToolList } from '../api/client'

type Props = { onError: (message: string) => void; onChanged: () => void }

/**
 * The tool catalogue: what an agent can be offered, and which MCP servers are behind it.
 * Read-only here — the allowlist lives on the agent, the servers in the configuration.
 */
export function ToolsArea(p: Props) {
  const [list, setList] = useState<ToolList | null>(null)
  const [busy, setBusy] = useState(false)

  const fail = (e: Error) => p.onError(e.message)

  useEffect(() => {
    catalogApi.tools().then(setList).catch(fail)
  }, []) // eslint-disable-line react-hooks/exhaustive-deps

  const reload = async () => {
    setBusy(true)
    try {
      setList(await catalogApi.reloadTools())
      p.onChanged()
    } catch (e) {
      fail(e as Error)
    } finally {
      setBusy(false)
    }
  }

  const builtin = list?.tools.filter((t) => t.source === 'builtin') ?? []
  const mcp = list?.tools.filter((t) => t.source === 'mcp') ?? []

  return (
    <>
      <section className="card">
        <div className="card-head">
          <h2>Werkzeuge</h2>
          <span className="sub">Was ein Agent angeboten bekommen kann. Die Positivliste steht am Agenten.</span>
          <div className="actions">
            <button className="btn quiet sm" onClick={reload} disabled={busy} title="MCP-Server neu verbinden">
              neu verbinden
            </button>
            <span className="pill ghost">vom System</span>
          </div>
        </div>
        {list === null ? (
          <p className="empty">lädt …</p>
        ) : (
          <>
            <div className="group-title">Eingebaut</div>
            <div className="catalog">
              {builtin.map((t) => (
                <article key={t.name} className="entry">
                  <div className="entry-head">
                    <h3 className="mono">{t.name}</h3>
                  </div>
                  <p className="desc">{t.description}</p>
                </article>
              ))}
              {builtin.length === 0 && <p className="empty">Eingebaute Werkzeuge sind abgeschaltet.</p>}
            </div>
            <div className="group-title">Über MCP</div>
            {mcp.length === 0 ? (
              <p className="empty">Kein MCP-Werkzeug verbunden.</p>
            ) : (
              <div className="catalog">
                {mcp.map((t) => (
                  <article key={t.name} className="entry">
                    <div className="entry-head">
                      <h3 className="mono">{t.name}</h3>
                      <span className="pill">MCP</span>
                    </div>
                    <p className="desc">{t.description}</p>
                  </article>
                ))}
              </div>
            )}
          </>
        )}
      </section>

      {list && list.servers.length > 0 && (
        <section className="card">
          <div className="card-head">
            <h2>MCP-Server</h2>
            <span className="sub">
              Registriert in <code>app.tools.mcp.*</code>. Ein nicht erreichbarer Server verhindert den Start nicht.
            </span>
          </div>
          <div className="rows">
            {list.servers.map((s) => (
              <div key={s.id} className="row" style={{ cursor: 'default' }}>
                <div>
                  <div className="title">{s.label}</div>
                  <div className="line small">
                    <span className="mono">{s.id}</span>
                    <span className="pill mono">{s.transport}</span>
                  </div>
                </div>
                <div className="line">
                  <span className="mono small faint">{s.target}</span>
                </div>
                <div className="line">
                  {!s.enabled ? (
                    <span className="pill">aus</span>
                  ) : s.connected ? (
                    <span className="pill pass">
                      verbunden · {s.toolCount} Werkzeug{s.toolCount === 1 ? '' : 'e'}
                    </span>
                  ) : (
                    <span className="pill fail" title={s.error ?? undefined}>
                      nicht erreichbar
                    </span>
                  )}
                </div>
              </div>
            ))}
          </div>
        </section>
      )}
    </>
  )
}
