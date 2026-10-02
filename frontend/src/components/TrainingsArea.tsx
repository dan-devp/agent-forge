import { useEffect, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router'
import {
  agentsApi,
  tasksApi,
  trainingsApi,
  type AgentView,
  type Session,
  type Task,
  type Training,
  type TrainingDetail,
  type TrainingRequest,
  type TrainingView,
} from '../api/client'
import { Modal } from './Modal'
import { SessionStatusPill, Sparkline } from './TrainingSession'
import { isOpen, journey } from './training'

type Props = {
  onError: (message: string) => void
  onChanged: () => void
}

type Role = '' | 'train' | 'holdout'

type Draft = {
  id: string
  name: string
  description: string
  agent: string
  coach: string
  roles: Record<string, Role>
  repetitions: string
  maxSteps: string
  maxTokens: string
  approval: 'gate' | 'manual'
  goal: string
}

function emptyDraft(agents: AgentView[]): Draft {
  const coach = agents.find((a) => a.id === 'coach')
  return {
    id: '',
    name: '',
    description: '',
    agent: agents.find((a) => a.id !== coach?.id)?.id ?? '',
    coach: coach?.id ?? '',
    roles: {},
    repetitions: '3',
    maxSteps: '3',
    maxTokens: '',
    approval: 'gate',
    goal: '',
  }
}

function toDraft(t: Training): Draft {
  const roles: Record<string, Role> = {}
  t.train.forEach((id) => (roles[id] = 'train'))
  t.holdout.forEach((id) => (roles[id] = 'holdout'))
  return {
    id: t.id,
    name: t.name,
    description: t.description ?? '',
    agent: t.agentId,
    coach: t.coachId,
    roles,
    repetitions: String(t.repetitions),
    maxSteps: String(t.maxSteps),
    maxTokens: t.maxTokens === null ? '' : String(t.maxTokens),
    approval: t.approval === 'MANUAL' ? 'manual' : 'gate',
    goal: t.goal ?? '',
  }
}

function when(at: string) {
  return new Date(at).toLocaleString('de-DE', { day: '2-digit', month: '2-digit', hour: '2-digit', minute: '2-digit' })
}

function ratio(score: { trainPassed: number; trainTotal: number }) {
  return `${score.trainPassed}/${score.trainTotal}`
}

/** "0/6 → 6/6": where the last session started and where it got, train only. */
function Progress({ session }: { session: Session }) {
  const { baseline, best } = journey(session)
  if (!baseline) return null
  return (
    <span className="mono">
      {ratio(baseline)}
      {best && best !== baseline && <> → {ratio(best)}</>}
    </span>
  )
}

/** The recipe as one sentence: who learns from whom, on what, within which limits. */
function Recipe({ t }: { t: Training }) {
  return (
    <p className="recipe">
      <span className="mono strong">{t.agentId}</span> lernt bei <span className="mono strong">{t.coachId}</span> · Train{' '}
      {t.train.map((id, i) => (
        <span key={id}>
          {i > 0 && ', '}
          <Link to={`/tasks/${id}`} className="mono">
            {id}
          </Link>
        </span>
      ))}
      {t.holdout.length > 0 && (
        <>
          {' '}
          · Holdout{' '}
          {t.holdout.map((id, i) => (
            <span key={id}>
              {i > 0 && ', '}
              <Link to={`/tasks/${id}`} className="mono">
                {id}
              </Link>
            </span>
          ))}
        </>
      )}
      <span className="muted">
        {' '}
        · n={t.repetitions} · ≤ {t.maxSteps} Schritte · {t.maxTokens === null ? 'ohne Token-Grenze' : `≤ ${t.maxTokens.toLocaleString('de-DE')} tok`} ·{' '}
        {t.approval === 'MANUAL' ? 'Freigabe von Hand' : 'Gate entscheidet'}
      </span>
    </p>
  )
}

/**
 * Trainings: which there are (list), what one does and how often it ran (detail). A session
 * has its own page; here it is one row each.
 */
export function TrainingsArea(p: Props) {
  const { id: selectedId } = useParams()
  const navigate = useNavigate()

  const [list, setList] = useState<TrainingView[] | null>(null)
  const [agents, setAgents] = useState<AgentView[]>([])
  const [tasks, setTasks] = useState<Task[]>([])
  const [detail, setDetail] = useState<TrainingDetail | null>(null)
  const [source, setSource] = useState<string | null>(null)
  const [draft, setDraft] = useState<Draft | null>(null)
  const [busy, setBusy] = useState(false)
  const [confirmDelete, setConfirmDelete] = useState(false)

  const fail = (e: Error) => p.onError(e.message)
  const loadList = () => trainingsApi.list().then(setList).catch(fail)
  const loadDetail = (id: string) =>
    trainingsApi
      .get(id)
      .then(setDetail)
      .catch((e: Error) => {
        fail(e)
        navigate('/trainings', { replace: true })
      })

  useEffect(() => {
    loadList()
    agentsApi.list().then(setAgents).catch(fail)
    tasksApi.list().then(setTasks).catch(fail)
  }, []) // eslint-disable-line react-hooks/exhaustive-deps

  useEffect(() => {
    if (!selectedId) return
    let stale = false
    trainingsApi
      .get(selectedId)
      .then((d) => {
        if (!stale) setDetail(d)
      })
      .catch((e: Error) => {
        if (stale) return
        fail(e)
        navigate('/trainings', { replace: true })
      })
    return () => {
      stale = true
    }
  }, [selectedId]) // eslint-disable-line react-hooks/exhaustive-deps

  const shown = selectedId && detail?.training.id === selectedId ? detail : null
  const running = list?.some((v) => v.lastSession && isOpen(v.lastSession)) ?? false

  async function perform(action: () => Promise<void>) {
    setBusy(true)
    try {
      await action()
    } catch (e) {
      p.onError((e as Error).message)
    } finally {
      setBusy(false)
    }
  }

  const start = () =>
    perform(async () => {
      if (!shown) return
      const s = await trainingsApi.start(shown.training.id)
      navigate(`/trainings/${shown.training.id}/sessions/${s.id}`)
    })

  const save = () =>
    perform(async () => {
      if (!draft) return
      const ids = (role: Role) => tasks.map((t) => t.id).filter((id) => draft.roles[id] === role)
      const body: TrainingRequest = {
        name: draft.name,
        description: draft.description || null,
        agent: draft.agent,
        coach: draft.coach,
        train: ids('train'),
        holdout: ids('holdout'),
        repetitions: Number(draft.repetitions),
        maxSteps: Number(draft.maxSteps),
        maxTokens: draft.maxTokens === '' ? null : Number(draft.maxTokens),
        approval: draft.approval,
        goal: draft.goal || null,
      }
      const isNew = !list?.some((v) => v.training.id === draft.id)
      const saved = isNew ? await trainingsApi.create({ ...body, id: draft.id }) : await trainingsApi.update(draft.id, body)
      setDraft(null)
      setSource(null)
      await loadList()
      p.onChanged()
      if (saved.id === selectedId) await loadDetail(saved.id)
      else navigate(`/trainings/${saved.id}`)
    })

  const remove = () =>
    perform(async () => {
      if (!shown) return
      await trainingsApi.remove(shown.training.id)
      setConfirmDelete(false)
      setDetail(null)
      await loadList()
      p.onChanged()
      navigate('/trainings')
    })

  const reload = () =>
    perform(async () => {
      setList(await trainingsApi.reload())
      if (selectedId) await loadDetail(selectedId)
    })

  const toggleSource = () => {
    if (!draft) return
    if (source !== null) {
      setSource(null)
      return
    }
    const current = list?.find((v) => v.training.id === draft.id)?.training
    if (!current) return
    trainingsApi.source(current.id, current.version).then(setSource).catch(fail)
  }

  const isExisting = draft ? list?.some((v) => v.training.id === draft.id) : false
  const t = shown?.training

  return (
    <>
      <section className="card">
        <div className="card-head">
          <h2>Trainings</h2>
          <span className="sub">Ein Coach schreibt aus gescheiterten Runs die nächste Agent-Version, das Gate misst, ob sie besser ist</span>
          <div className="actions">
            <button className="btn quiet sm" onClick={reload} disabled={busy} title="Dateien neu einlesen">
              neu laden
            </button>
            <button className="btn primary sm" onClick={() => setDraft(emptyDraft(agents))} disabled={busy}>
              neues Training
            </button>
          </div>
        </div>
        {list === null ? (
          <p className="empty">lädt …</p>
        ) : list.length === 0 ? (
          <p className="empty">Noch kein Training.</p>
        ) : (
          <div className="rows">
            {list.map(({ training: tr, lastSession: last }) => (
              <div
                key={tr.id}
                className={`row training-row ${selectedId === tr.id ? 'selected' : ''}`}
                onClick={() => navigate(`/trainings/${tr.id}`)}
                role="button"
                tabIndex={0}
                onKeyDown={(e) => e.key === 'Enter' && navigate(`/trainings/${tr.id}`)}
              >
                <div>
                  <div className="title">{tr.name}</div>
                  <div className="small muted">
                    <span className="mono">{tr.agentId}</span> lernt bei <span className="mono">{tr.coachId}</span>
                  </div>
                </div>
                <div className="line">
                  {last ? (
                    <>
                      <Sparkline session={last} />
                      <Progress session={last} />
                    </>
                  ) : (
                    <span className="muted small">noch nie trainiert</span>
                  )}
                </div>
                <div className="line">{last && <SessionStatusPill status={last.status} />}</div>
              </div>
            ))}
          </div>
        )}
      </section>

      {shown && t && (
        <section className="card">
          <div className="card-head">
            <h2>{t.name}</h2>
            <span className="sub mono small">
              {t.id} · v{t.version}
            </span>
            <div className="actions">
              <button className="btn quiet danger sm" onClick={() => setConfirmDelete(true)} disabled={busy}>
                löschen
              </button>
              <button className="btn sm" onClick={() => setDraft(toDraft(t))} disabled={busy}>
                bearbeiten
              </button>
              <button
                className="btn primary"
                onClick={start}
                disabled={busy || running}
                title={running ? 'es läuft schon eine Session - eine zur Zeit' : 'Ausgangslage messen, dann Schritt für Schritt verbessern'}
              >
                trainieren
              </button>
            </div>
          </div>

          <Recipe t={t} />
          {t.goal && (
            <p className="goal">
              <span className="muted small">Ziel für den Coach · </span>
              {t.goal}
            </p>
          )}

          <div className="field">
            <span>Sessions</span>
            {shown.sessions.length === 0 ? (
              <p className="hint">Noch keine. „trainieren" misst zuerst die Ausgangslage, dann schreibt der Coach Version um Version.</p>
            ) : (
              <div className="rows">
                {shown.sessions.map((s) => (
                  <Link key={s.id} to={`/trainings/${t.id}/sessions/${s.id}`} className="row session-row">
                    <span className="mono small">{when(s.startedAt)}</span>
                    <span className="line">
                      <Sparkline session={s} />
                      <Progress session={s} />
                      <span className="small muted">
                        v{s.startVersion} → v{s.finalVersion ?? s.bestVersion ?? s.startVersion} · {Math.max(0, s.steps.length - 1)} Schritt(e) ·{' '}
                        {s.totalTokens.toLocaleString('de-DE')} tok
                      </span>
                    </span>
                    <SessionStatusPill status={s.status} />
                  </Link>
                ))}
              </div>
            )}
          </div>
        </section>
      )}

      {draft && (
        <Modal title={isExisting ? `${draft.name || draft.id} — neue Version` : 'Neues Training'} onClose={() => { setDraft(null); setSource(null) }}>
          <form
            className="form"
            onSubmit={(e) => {
              e.preventDefault()
              save()
            }}
          >
            <div className="form-grid">
              <label className="field">
                <span>Id</span>
                <input
                  value={draft.id}
                  onChange={(e) => setDraft({ ...draft, id: e.target.value })}
                  disabled={isExisting}
                  placeholder="kleinbuchstaben-und-bindestriche"
                  pattern="[a-z0-9][a-z0-9-]*"
                  required
                  className="mono"
                />
              </label>
              <label className="field">
                <span>Name</span>
                <input value={draft.name} onChange={(e) => setDraft({ ...draft, name: e.target.value })} required />
              </label>
              <label className="field">
                <span>Agent (lernt)</span>
                <select value={draft.agent} onChange={(e) => setDraft({ ...draft, agent: e.target.value })} required>
                  <option value="">— Agent wählen —</option>
                  {agents.map((a) => (
                    <option key={a.id} value={a.id}>
                      {a.name} · {a.id} v{a.version}
                    </option>
                  ))}
                </select>
              </label>
              <label className="field">
                <span>Coach (schreibt die Vorschläge)</span>
                <select value={draft.coach} onChange={(e) => setDraft({ ...draft, coach: e.target.value })} required>
                  <option value="">— Agent wählen —</option>
                  {agents
                    .filter((a) => a.id !== draft.agent)
                    .map((a) => (
                      <option key={a.id} value={a.id}>
                        {a.name} · {a.id} · {a.model}
                      </option>
                    ))}
                </select>
              </label>

              <div className="field span-2">
                <span>Tasks</span>
                <div className="task-roles">
                  {tasks.map((task) => {
                    const role = draft.roles[task.id] ?? ''
                    return (
                      <div key={task.id} className="task-role">
                        <span className="mono">{task.id}</span>
                        <span className="muted small">{task.name}</span>
                        <span className="segment">
                          {(['', 'train', 'holdout'] as Role[]).map((r) => (
                            <button
                              key={r || 'none'}
                              type="button"
                              className={role === r ? 'on' : ''}
                              onClick={() => setDraft({ ...draft, roles: { ...draft.roles, [task.id]: r } })}
                            >
                              {r === '' ? '—' : r === 'train' ? 'Train' : 'Holdout'}
                            </button>
                          ))}
                        </span>
                      </div>
                    )
                  })}
                </div>
                <span className="hint">
                  Train: der Coach sieht die gescheiterten Runs. Holdout: wird mitgemessen, der Coach sieht ihn nie — fällt er, war die Verbesserung
                  auswendig gelernt.
                </span>
              </div>

              <label className="field">
                <span>Wiederholungen n je Task</span>
                <input type="number" min={1} max={20} value={draft.repetitions} onChange={(e) => setDraft({ ...draft, repetitions: e.target.value })} required />
              </label>
              <label className="field">
                <span>Schritte höchstens</span>
                <input type="number" min={1} max={20} value={draft.maxSteps} onChange={(e) => setDraft({ ...draft, maxSteps: e.target.value })} required />
              </label>
              <label className="field">
                <span>Token-Budget (leer = keins)</span>
                <input type="number" min={1} value={draft.maxTokens} onChange={(e) => setDraft({ ...draft, maxTokens: e.target.value })} />
              </label>
              <div className="field">
                <span>Freigabe</span>
                <span className="segment">
                  <button type="button" className={draft.approval === 'gate' ? 'on' : ''} onClick={() => setDraft({ ...draft, approval: 'gate' })}>
                    Gate entscheidet
                  </button>
                  <button type="button" className={draft.approval === 'manual' ? 'on' : ''} onClick={() => setDraft({ ...draft, approval: 'manual' })}>
                    von Hand
                  </button>
                </span>
              </div>
              <label className="field span-2">
                <span>Ziel (liest der Coach, optional)</span>
                <textarea
                  rows={3}
                  value={draft.goal}
                  onChange={(e) => setDraft({ ...draft, goal: e.target.value })}
                  placeholder="Der Agent soll Tools nutzen, wo sie die Antwort sicher machen."
                />
              </label>
              <label className="field span-2">
                <span>Beschreibung</span>
                <input value={draft.description} onChange={(e) => setDraft({ ...draft, description: e.target.value })} />
              </label>
            </div>
            {isExisting && (
              <>
                <button type="button" className="link" onClick={toggleSource}>
                  {source !== null ? 'Datei ausblenden' : 'Datei der aktuellen Version anzeigen'}
                </button>
                {source !== null && <pre className="source mono">{source}</pre>}
              </>
            )}
            <div className="toolbar end">
              <button type="button" className="btn quiet" onClick={() => setDraft(null)} disabled={busy}>
                abbrechen
              </button>
              <button type="submit" className="btn primary" disabled={busy}>
                {isExisting ? 'als neue Version speichern' : 'anlegen'}
              </button>
            </div>
          </form>
        </Modal>
      )}

      {confirmDelete && shown && (
        <Modal title="Training löschen?" onClose={() => setConfirmDelete(false)}>
          <p>
            <strong>{shown.training.name}</strong> <span className="mono muted">({shown.training.id})</span> wird mit allen{' '}
            {shown.versions.length} Version(en) ins Archiv verschoben. Die {shown.sessions.length} Session(s) bleiben in der Datenbank, die
            Agent-Versionen, die der Coach geschrieben hat, auch.
          </p>
          <div className="toolbar end">
            <button className="btn quiet" onClick={() => setConfirmDelete(false)} disabled={busy}>
              abbrechen
            </button>
            <button className="btn primary" onClick={remove} disabled={busy}>
              ins Archiv
            </button>
          </div>
        </Modal>
      )}
    </>
  )
}
