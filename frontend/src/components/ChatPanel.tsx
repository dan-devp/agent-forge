import { useState } from 'react'
import { api, type ChatResult } from '../api/client'
import type { Selection } from './Sidebar'

type Props = {
  selection: Selection
  busy: boolean
  perform: (action: () => Promise<void>) => void
}

/** A single model turn without tools — to hear the SOUL speak. */
export function ChatPanel(p: Props) {
  const [message, setMessage] = useState('Stell dich in einem Satz vor.')
  const [result, setResult] = useState<ChatResult | null>(null)

  const ask = () =>
    p.perform(async () => {
      setResult(null)
      setResult(await api.chat(message, p.selection.provider, p.selection.soul))
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
          <button className="btn primary" onClick={ask} disabled={p.busy || !message.trim() || !p.selection.provider}>
            {p.busy ? 'Läuft …' : 'Fragen'}
          </button>
        </div>
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
