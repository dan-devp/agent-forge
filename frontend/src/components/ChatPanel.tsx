import { useState } from 'react'
import { api, type ChatResult, type SoulListResponse, type StatusResponse } from '../api/client'

type Props = {
  souls: SoulListResponse | null
  status: StatusResponse | null
  provider: string
  onProvider: (id: string) => void
  busy: boolean
  perform: (action: () => Promise<void>) => void
}

/** A single model turn without tools — to hear the SOUL speak. */
export function ChatPanel(p: Props) {
  const [message, setMessage] = useState('Stell dich in einem Satz vor.')
  const [chosenSoul, setSoul] = useState<string | null>(null)
  const soul = chosenSoul ?? p.souls?.activeSoul ?? ''
  const [result, setResult] = useState<ChatResult | null>(null)

  const ask = () =>
    p.perform(async () => {
      setResult(null)
      setResult(await api.chat(message, p.provider, soul))
    })

  return (
    <section className="panel">
      <div className="panel-head">
        <h2>Einzelner Modellaufruf</h2>
        <span className="small muted">ohne Werkzeuge, ohne Trace — nur die SOUL</span>
      </div>
      <div className="panel-body">
        <textarea value={message} onChange={(e) => setMessage(e.target.value)} rows={2} placeholder="Nachricht an den Agenten" />
        <div className="toolbar">
          <select value={p.provider} onChange={(e) => p.onProvider(e.target.value)} disabled={!p.status}>
            <option value="">Modell: Standard</option>
            {p.status?.providers.map((x) => (
              <option key={x.id} value={x.id}>
                {x.label} · {x.model}
              </option>
            ))}
          </select>
          <select value={soul} onChange={(e) => setSoul(e.target.value)} disabled={!p.souls}>
            {p.souls?.souls.map((s) => (
              <option key={s.id} value={s.id}>
                {s.name} · v{s.version}
              </option>
            ))}
          </select>
          <button className="btn primary" onClick={ask} disabled={p.busy || !message.trim()}>
            {p.busy ? 'Läuft …' : 'Fragen'}
          </button>
        </div>
        <p className="hint faint">
          Dieser Weg kennt keinen Agenten: /api/chat ist der Ein-Turn-Endpunkt von vor der Agent-Entitaet, ohne Werkzeuge,
          ohne Trace und ohne Bewertung. Zum Hoeren, wie eine SOUL klingt — nicht zum Messen.
        </p>
        {result && (
          <>
            <div className="answer">{result.answer}</div>
            <p className="hint">
              {result.provider} · {result.model ?? 'unbekannt'} · SOUL {result.soul} v{result.soulVersion} · {result.durationMs} ms
              {result.totalTokens != null && ` · ${result.totalTokens} Tokens`}
            </p>
          </>
        )}
      </div>
    </section>
  )
}
