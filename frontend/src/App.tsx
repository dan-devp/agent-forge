import { useEffect, useState } from 'react'
import { api, type Status } from './api/client'
import { NavSidebar } from './components/NavSidebar'
import type { Area } from './components/areas'
import { ModelsPanel } from './components/ModelsPanel'
import './App.css'

function App() {
  const [status, setStatus] = useState<Status | null>(null)
  const [area, setArea] = useState<Area>('models')
  const [error, setError] = useState<string | null>(null)

  const refreshStatus = () => api.status().then(setStatus).catch((e: Error) => setError(e.message))

  useEffect(() => {
    refreshStatus()
  }, [])

  return (
    <div className="shell">
      <header className="topbar">
        <div className="brand">
          <h1>Agent Forge</h1>
          <span>Trainingslager für Agenten</span>
        </div>
        <div className="chips">
          <span className="chip">
            <strong>{status?.models ?? '…'}</strong> Modelle
          </span>
          <span className="chip">
            <strong>{status?.agents ?? '…'}</strong> Agenten
          </span>
          <span className="chip">
            <strong>{status?.tasks ?? '…'}</strong> Aufgaben
          </span>
          <span className="chip">
            <strong>{status?.tools ?? '…'}</strong> Werkzeuge
          </span>
          {status && (
            <span className="chip" title="DATA_DIR">
              <span className="mono small">{status.dataDir}</span>
            </span>
          )}
        </div>
      </header>

      <div className="layout">
        <NavSidebar area={area} onArea={setArea} />
        <main className="main">
          {error && (
            <div className="banner">
              <span>{error}</span>
              <button className="btn ghost sm" onClick={() => setError(null)}>
                ok
              </button>
            </div>
          )}
          {area === 'models' && <ModelsPanel onError={setError} onChanged={refreshStatus} />}
        </main>
      </div>
    </div>
  )
}

export default App
