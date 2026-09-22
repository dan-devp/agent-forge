import { useEffect, useState } from 'react'
import {
  api,
  type AgentListResponse,
  type ProfileListResponse,
  type SoulListResponse,
  type StatusResponse,
  type TaskSummary,
  type ToolListResponse,
} from './api/client'
import { NavSidebar } from './components/NavSidebar'
import type { Area } from './components/areas'
import { AgentsPanel } from './components/AgentsPanel'
import { TasksPanel } from './components/TasksPanel'
import { StrategiesPanel } from './components/StrategiesPanel'
import { SettingsPanel } from './components/SettingsPanel'
import { PlansPanel } from './components/PlansPanel'
import { TrainingPanel } from './components/TrainingPanel'
import { RunPanel } from './components/RunPanel'
import { HistoryPanel } from './components/HistoryPanel'
import { ChatPanel } from './components/ChatPanel'
import './App.css'

type TrainingTab = 'experiment' | 'training'
type RunTab = 'run' | 'history' | 'chat'

function App() {
  const [status, setStatus] = useState<StatusResponse | null>(null)
  const [souls, setSouls] = useState<SoulListResponse | null>(null)
  const [agents, setAgents] = useState<AgentListResponse | null>(null)
  const [profiles, setProfiles] = useState<ProfileListResponse | null>(null)
  const [tools, setTools] = useState<ToolListResponse | null>(null)
  const [tasks, setTasks] = useState<TaskSummary[] | null>(null)

  const [area, setArea] = useState<Area>('training')
  const [trainingTab, setTrainingTab] = useState<TrainingTab>('experiment')
  const [runTab, setRunTab] = useState<RunTab>('run')

  const [agent, setAgent] = useState('')
  // Empty means: whatever the agent says, otherwise the global default.
  const [provider, setProvider] = useState('')
  const [task, setTask] = useState('')

  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const fail = (e: Error) => setError(e.message)

  useEffect(() => {
    api.status().then(setStatus).catch(fail)
    api.souls().then(setSouls).catch(fail)
    api
      .agents()
      .then((a) => {
        setAgents(a)
        setAgent(a.defaultAgent)
      })
      .catch(fail)
    api.profiles().then(setProfiles).catch(fail)
    api.tools().then(setTools).catch(fail)
    api
      .tasks()
      .then((t) => {
        setTasks(t)
        setTask(t[0]?.id ?? '')
      })
      .catch(fail)
  }, [])

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

  const reloadAgents = () =>
    perform(async () => {
      const a = await api.reloadAgents()
      setAgents(a)
      if (!a.agents.some((x) => x.id === agent)) setAgent(a.defaultAgent)
    })

  const reloadProfiles = () =>
    perform(async () => {
      setProfiles(await api.reloadProfiles())
    })

  const reloadSouls = () =>
    perform(async () => {
      setSouls(await api.reloadSouls())
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
            <strong>{agents?.agents.length ?? '…'}</strong> Agenten
          </span>
          <span className="chip">
            <strong>{tasks?.length ?? '…'}</strong> Aufgaben
          </span>
          <span className="chip">
            <strong>{profiles?.profiles.length ?? '…'}</strong> Profile
          </span>
          <span className="chip">
            <strong>{tools?.tools.length ?? '…'}</strong> Werkzeuge
          </span>
          <span className="chip">
            <strong>{status?.providers.length ?? '…'}</strong> Modelle
          </span>
        </div>
      </header>

      <div className="layout">
        <NavSidebar area={area} onArea={setArea} />

        <main className="main">
          {error && (
            <div className="banner">
              <span>{error}</span>
              <button className="btn ghost sm" onClick={() => setError(null)}>
                schließen
              </button>
            </div>
          )}

          {area === 'training' && (
            <>
              <nav className="tabs" aria-label="Training">
                <button
                  className={`tab ${trainingTab === 'experiment' ? 'active' : ''}`}
                  onClick={() => setTrainingTab('experiment')}
                >
                  Pläne und Durchläufe
                </button>
                <button
                  className={`tab ${trainingTab === 'training' ? 'active' : ''}`}
                  onClick={() => setTrainingTab('training')}
                >
                  Einzeltraining
                </button>
              </nav>
              {trainingTab === 'experiment' ? (
                <PlansPanel
                  tasks={tasks}
                  profiles={profiles}
                  agents={agents}
                  status={status}
                  busy={busy}
                  perform={perform}
                  onError={setError}
                />
              ) : (
                <TrainingPanel
                  tasks={tasks}
                  task={task}
                  onTaskChange={setTask}
                  profiles={profiles}
                  agents={agents}
                  agent={agent}
                  onAgent={setAgent}
                  status={status}
                  provider={provider}
                  onProvider={setProvider}
                  busy={busy}
                  perform={perform}
                  onError={setError}
                />
              )}
            </>
          )}

          {area === 'runs' && (
            <>
              <nav className="tabs" aria-label="Läufe">
                <button className={`tab ${runTab === 'run' ? 'active' : ''}`} onClick={() => setRunTab('run')}>
                  Einzellauf
                </button>
                <button className={`tab ${runTab === 'history' ? 'active' : ''}`} onClick={() => setRunTab('history')}>
                  Historie
                </button>
                <button className={`tab ${runTab === 'chat' ? 'active' : ''}`} onClick={() => setRunTab('chat')}>
                  Chat
                </button>
              </nav>
              {runTab === 'run' && (
                <RunPanel
                  tasks={tasks}
                  task={task}
                  onTaskChange={setTask}
                  profiles={profiles}
                  agents={agents}
                  agent={agent}
                  onAgent={setAgent}
                  status={status}
                  provider={provider}
                  onProvider={setProvider}
                  busy={busy}
                  perform={perform}
                  onReloadTasks={reloadTasks}
                  onError={setError}
                />
              )}
              {runTab === 'history' && <HistoryPanel perform={perform} onError={setError} />}
              {runTab === 'chat' && (
                <ChatPanel
                  souls={souls}
                  status={status}
                  provider={provider}
                  onProvider={setProvider}
                  busy={busy}
                  perform={perform}
                />
              )}
            </>
          )}

          {area === 'agents' && (
            <AgentsPanel agents={agents} agent={agent} onAgent={setAgent} busy={busy} onReload={reloadAgents} />
          )}

          {area === 'tasks' && (
            <TasksPanel tasks={tasks} task={task} onTask={setTask} busy={busy} onReload={reloadTasks} onError={setError} />
          )}

          {area === 'strategies' && <StrategiesPanel profiles={profiles} busy={busy} onReload={reloadProfiles} />}

          {area === 'settings' && (
            <SettingsPanel
              status={status}
              souls={souls}
              tools={tools}
              busy={busy}
              onReloadSouls={reloadSouls}
              onReloadTools={reloadTools}
              onError={setError}
            />
          )}
        </main>
      </div>
    </div>
  )
}

export default App
