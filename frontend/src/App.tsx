import { useEffect, useState } from 'react'
import {
  api,
  type ProfileListResponse,
  type SoulDetail,
  type SoulListResponse,
  type StatusResponse,
  type TaskSummary,
  type ToolListResponse,
} from './api/client'
import { Sidebar, type Selection } from './components/Sidebar'
import { RunPanel } from './components/RunPanel'
import { TrainingPanel } from './components/TrainingPanel'
import { HistoryPanel } from './components/HistoryPanel'
import { ChatPanel } from './components/ChatPanel'
import './App.css'

type Tab = 'run' | 'training' | 'history' | 'chat'

const TABS: { id: Tab; label: string }[] = [
  { id: 'run', label: 'Lauf' },
  { id: 'training', label: 'Training' },
  { id: 'history', label: 'Historie' },
  { id: 'chat', label: 'Chat' },
]

function App() {
  const [status, setStatus] = useState<StatusResponse | null>(null)
  const [souls, setSouls] = useState<SoulListResponse | null>(null)
  const [soulDetail, setSoulDetail] = useState<SoulDetail | null>(null)
  const [profiles, setProfiles] = useState<ProfileListResponse | null>(null)
  const [tools, setTools] = useState<ToolListResponse | null>(null)
  const [tasks, setTasks] = useState<TaskSummary[] | null>(null)
  const [task, setTask] = useState('')
  const [selection, setSelection] = useState<Selection>({ provider: '', soul: '', profile: '' })
  const [tab, setTab] = useState<Tab>('run')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const fail = (e: Error) => setError(e.message)

  useEffect(() => {
    api
      .status()
      .then((s) => {
        setStatus(s)
        // Prefer the default, otherwise the first usable provider.
        const preferred =
          s.providers.find((p) => p.isDefault && p.configured) ?? s.providers.find((p) => p.configured) ?? s.providers[0]
        setSelection((sel) => ({ ...sel, provider: preferred?.id ?? '' }))
      })
      .catch(fail)

    api
      .souls()
      .then((s) => {
        setSouls(s)
        setSelection((sel) => ({ ...sel, soul: s.activeSoul }))
      })
      .catch(fail)

    api
      .profiles()
      .then((p) => {
        setProfiles(p)
        setSelection((sel) => ({ ...sel, profile: p.defaultProfile }))
      })
      .catch(fail)

    api.tools().then(setTools).catch(fail)

    api
      .tasks()
      .then((t) => {
        setTasks(t)
        setTask(t[0]?.id ?? '')
      })
      .catch(fail)
  }, [])

  // Always load the detail (incl. rendered prompt) for the currently selected SOUL.
  useEffect(() => {
    if (!selection.soul) return
    api.soul(selection.soul).then(setSoulDetail).catch(fail)
  }, [selection.soul])

  async function perform(action: () => Promise<void>) {
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

  const reloadTasks = () =>
    perform(async () => {
      const list = await api.reloadTasks()
      setTasks(list)
      if (!list.some((t) => t.id === task)) setTask(list[0]?.id ?? '')
    })

  const reloadProfiles = () =>
    perform(async () => {
      const p = await api.reloadProfiles()
      setProfiles(p)
      if (!p.profiles.some((x) => x.id === selection.profile)) setSelection((s) => ({ ...s, profile: p.defaultProfile }))
    })

  const reloadSouls = () =>
    perform(async () => {
      const s = await api.reloadSouls()
      setSouls(s)
      if (!s.souls.some((x) => x.id === selection.soul)) setSelection((sel) => ({ ...sel, soul: s.activeSoul }))
    })

  const reloadTools = () =>
    perform(async () => {
      setTools(await api.reloadTools())
    })

  return (
    <div className="shell">
      <header className="topbar">
        <div className="brand">
          <h1>Agent Forge</h1>
          <span>Trainingslager für Agenten</span>
        </div>
        <div className="chips">
          <span className="chip">
            <strong>{status?.providers.length ?? '…'}</strong> Provider
          </span>
          <span className="chip">
            <strong>{souls?.souls.length ?? '…'}</strong> SOULs
          </span>
          <span className="chip">
            <strong>{tasks?.length ?? '…'}</strong> Aufgaben
          </span>
          <span className="chip">
            <strong>{profiles?.profiles.length ?? '…'}</strong> Profile
          </span>
          <span className="chip">
            <strong>{tools?.tools.length ?? '…'}</strong> Tools
          </span>
        </div>
      </header>

      <div className="layout">
        <Sidebar
          status={status}
          souls={souls}
          soulDetail={soulDetail}
          profiles={profiles}
          tools={tools}
          selection={selection}
          busy={busy}
          onChange={(next) => setSelection((s) => ({ ...s, ...next }))}
          onReloadProfiles={reloadProfiles}
          onReloadTools={reloadTools}
          onReloadSouls={reloadSouls}
        />

        <main className="main">
          <nav className="tabs" aria-label="Bereiche">
            {TABS.map((t) => (
              <button key={t.id} className={`tab ${tab === t.id ? 'active' : ''}`} onClick={() => setTab(t.id)}>
                {t.label}
              </button>
            ))}
          </nav>

          {error && (
            <div className="banner">
              <span>{error}</span>
              <button className="btn ghost sm" onClick={() => setError(null)}>
                schließen
              </button>
            </div>
          )}

          {tab === 'run' && (
            <RunPanel
              tasks={tasks}
              task={task}
              onTaskChange={setTask}
              selection={selection}
              busy={busy}
              perform={perform}
              onReloadTasks={reloadTasks}
              onError={(m) => setError(m)}
            />
          )}
          {tab === 'training' && (
            <TrainingPanel
              tasks={tasks}
              task={task}
              onTaskChange={setTask}
              selection={selection}
              busy={busy}
              perform={perform}
              onError={(m) => setError(m)}
            />
          )}
          {tab === 'history' && <HistoryPanel perform={perform} onError={(m) => setError(m)} />}
          {tab === 'chat' && <ChatPanel selection={selection} busy={busy} perform={perform} />}
        </main>
      </div>
    </div>
  )
}

export default App
