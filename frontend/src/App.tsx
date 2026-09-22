import { useEffect, useState } from 'react'
import { BrowserRouter, Link, Route, Routes, useLocation } from 'react-router'
import { api, type Status } from './api/client'
import { AREAS } from './components/areas'
import { Home } from './components/Home'
import { AreaShell } from './components/AreaShell'
import { ModelsArea } from './components/ModelsArea'
import './App.css'

function Topbar({ status }: { status: Status | null }) {
  const location = useLocation()
  const area = AREAS.find((a) => location.pathname.startsWith(a.path))
  return (
    <header className="topbar">
      <Link to="/" className="brand">
        <span className="dot" />
        agent forge
      </Link>
      {area && (
        <nav className="crumbs" aria-label="Pfad">
          <span className="sep">/</span>
          <span className="current">{area.label}</span>
        </nav>
      )}
      <span className="spacer" />
      {status && (
        <div className="meta">
          <span className="pill mono ghost" title="DATA_DIR">
            {status.dataDir}
          </span>
        </div>
      )}
    </header>
  )
}

function App() {
  const [status, setStatus] = useState<Status | null>(null)
  const [error, setError] = useState<string | null>(null)

  const refreshStatus = () => api.status().then(setStatus).catch((e: Error) => setError(e.message))

  useEffect(() => {
    refreshStatus()
  }, [])

  return (
    <BrowserRouter>
      <div className="shell">
        <Topbar status={status} />
        <Routes>
          <Route path="/" element={<Home status={status} />} />
          <Route element={<AreaShell status={status} error={error} onDismissError={() => setError(null)} />}>
            <Route path="/models" element={<ModelsArea onError={setError} onChanged={refreshStatus} />} />
            <Route path="/models/:id" element={<ModelsArea onError={setError} onChanged={refreshStatus} />} />
            <Route path="/agents/*" element={null} />
            <Route path="/tasks/*" element={null} />
            <Route path="/runs/*" element={null} />
          </Route>
        </Routes>
      </div>
    </BrowserRouter>
  )
}

export default App
