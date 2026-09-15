import { useEffect, useState } from 'react'
import { api, type ChatResult, type SoulDetail, type SoulListResponse, type StatusResponse } from './api/client'
import './App.css'

function App() {
  const [status, setStatus] = useState<StatusResponse | null>(null)
  const [provider, setProvider] = useState('')
  const [souls, setSouls] = useState<SoulListResponse | null>(null)
  const [soul, setSoul] = useState('')
  const [soulDetail, setSoulDetail] = useState<SoulDetail | null>(null)
  const [promptOffen, setPromptOffen] = useState(false)
  const [name, setName] = useState('Daniel')
  const [greeting, setGreeting] = useState<string | null>(null)
  const [message, setMessage] = useState('Stell dich in einem Satz vor.')
  const [result, setResult] = useState<ChatResult | null>(null)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    api
      .status()
      .then((s) => {
        setStatus(s)
        // Bevorzugt den Default, sonst den ersten nutzbaren Provider.
        const preferred =
          s.providers.find((p) => p.isDefault && p.configured) ??
          s.providers.find((p) => p.configured) ??
          s.providers[0]
        setProvider(preferred?.id ?? '')
      })
      .catch((e: Error) => setError(e.message))
  }, [])

  useEffect(() => {
    api
      .souls()
      .then((s) => {
        setSouls(s)
        setSoul(s.activeSoul)
      })
      .catch((e: Error) => setError(e.message))
  }, [])

  // Detail (inkl. gerendertem Prompt) immer zur aktuell gewaehlten SOUL nachladen.
  useEffect(() => {
    if (!soul) return
    api
      .soul(soul)
      .then(setSoulDetail)
      .catch((e: Error) => setError(e.message))
  }, [soul])

  const selected = status?.providers.find((p) => p.id === provider)
  const gates = soulDetail?.soul.gates ?? []

  async function run(action: () => Promise<void>) {
    setBusy(true)
    setError(null)
    try {
      await action()
    } catch (e) {
      setError((e as Error).message)
    } finally {
      setBusy(false)
    }
  }

  const sayHello = () =>
    run(async () => {
      const response = await api.hello(name)
      setGreeting(response.message)
    })

  const askAi = () =>
    run(async () => {
      setResult(null)
      setResult(await api.chat(message, provider, soul))
    })

  // Dateien aendern sich ausserhalb der App — neu einlesen ohne Neustart.
  const reloadSouls = () =>
    run(async () => {
      const liste = await api.reloadSouls()
      setSouls(liste)
      setSoulDetail(await api.soul(liste.souls.some((s) => s.id === soul) ? soul : liste.activeSoul))
      if (!liste.souls.some((s) => s.id === soul)) setSoul(liste.activeSoul)
    })

  return (
    <main className="app">
      <header>
        <h1>KI-Mitarbeiter — Prototyp</h1>
        <p className="status">
          {status ? `${status.providers.length} Provider registriert` : 'Backend wird abgefragt …'}
          {souls && ` · ${souls.souls.length} SOUL(s)`}
        </p>
      </header>

      <section className="card">
        <h2>1 · Hello World (ohne Modell)</h2>
        <div className="row">
          <input value={name} onChange={(e) => setName(e.target.value)} placeholder="Name" />
          <button onClick={sayHello} disabled={busy}>
            Sagen
          </button>
        </div>
        {greeting && <p className="output">{greeting}</p>}
      </section>

      <section className="card">
        <h2>2 · SOUL</h2>

        <div className="row providers">
          <select value={soul} onChange={(e) => setSoul(e.target.value)} disabled={!souls}>
            {souls?.souls.map((s) => (
              <option key={s.id} value={s.id}>
                {s.name}
                {s.rolle ? ` · ${s.rolle}` : ''} · v{s.version}
              </option>
            ))}
          </select>
          <button onClick={reloadSouls} disabled={busy}>
            Neu laden
          </button>
        </div>

        {soulDetail && (
          <>
            {soulDetail.beschreibung && <p className="meta">{soulDetail.beschreibung}</p>}

            {gates.length > 0 && (
              <ul className="gates">
                {gates.map((g) => (
                  <li key={g.id}>
                    <span className={g.blockiert ? 'badge cloud' : 'badge local'}>
                      {g.blockiert ? 'blockierend' : 'Hinweis'}
                    </span>{' '}
                    {g.frage}
                  </li>
                ))}
              </ul>
            )}

            <button onClick={() => setPromptOffen(!promptOffen)}>
              {promptOffen ? 'Prompt verbergen' : 'Gerenderten Prompt zeigen'}
            </button>
            {promptOffen && <pre className="output prompt">{soulDetail.prompt}</pre>}
            <p className="meta">Quelle: {soulDetail.quelle}</p>
          </>
        )}
      </section>

      <section className="card">
        <h2>3 · Modellaufruf</h2>

        <div className="row providers">
          <select value={provider} onChange={(e) => setProvider(e.target.value)} disabled={!status}>
            {status?.providers.map((p) => (
              <option key={p.id} value={p.id}>
                {p.label} · {p.model}
                {p.configured ? '' : ' (nicht konfiguriert)'}
              </option>
            ))}
          </select>
          {selected && (
            <span className={selected.local ? 'badge local' : 'badge cloud'}>
              {selected.local ? 'lokal' : 'cloud'}
            </span>
          )}
        </div>

        <textarea
          value={message}
          onChange={(e) => setMessage(e.target.value)}
          rows={3}
          placeholder="Nachricht an den Agenten"
        />
        <button onClick={askAi} disabled={busy || !message.trim() || !provider}>
          {busy ? 'Läuft …' : 'Fragen'}
        </button>

        {result && (
          <>
            <p className="output">{result.answer}</p>
            <p className="meta">
              {result.provider} · {result.model ?? 'unbekannt'} · SOUL {result.soul} v{result.soulVersion} ·{' '}
              {result.durationMs} ms
              {result.totalTokens != null && ` · ${result.totalTokens} Tokens`}
            </p>
          </>
        )}
      </section>

      {error && <p className="error">{error}</p>}
    </main>
  )
}

export default App
