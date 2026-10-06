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
import { EvalSentence } from './EvalSentence'
import { Advanced, GuideStep, NameField } from './forms'
import { Modal } from './Modal'
import { SessionStatusPill, Sparkline } from './TrainingSession'
import { isOpen, journey, trainingCost } from './training'
import { Ref } from './Ref'
import { originsOf } from './versions'

type Props = {
  onError: (message: string) => void
  onChanged: () => void
}

type Role = '' | 'train' | 'holdout'

type Draft = {
  id: string
  name: string
  idTouched: boolean
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
    idTouched: false,
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
    idTouched: true,
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

/** "Train 0/6 → 6/6": where a session started and where it got, train only. */
function Progress({ session }: { session: Session }) {
  const { baseline, best } = journey(session)
  if (!baseline) return null
  return (
    <span className="mono" title="bestandene Train-Runs: Ausgangslage → beste Version">
      <span className="muted">Train </span>
      {ratio(baseline)}
      {best && best !== baseline && <> → {ratio(best)}</>}
    </span>
  )
}

/** How much a session may cost, said before it starts. */
function CostLine({ t }: { t: { train: string[]; holdout: string[]; repetitions: number; maxSteps: number } }) {
  const c = trainingCost(t)
  if (c.perMeasure === 0) return <span className="cost">Noch kein Task gewählt — ohne Train-Task kann nichts gemessen werden.</span>
  return (
    <span className="cost" title={`${c.perMeasure} Runs je Messung × (Ausgangslage + ${t.maxSteps} Vorschläge); hört auf, wenn nichts mehr zu lernen ist`}>
      kostet bis zu <strong>{c.runs} Runs</strong> und <strong>{c.coachCalls} Coach-Aufruf{c.coachCalls === 1 ? '' : 'e'}</strong>
    </span>
  )
}

function approvalText(a: 'GATE' | 'MANUAL' | 'gate' | 'manual') {
  return a === 'MANUAL' || a === 'manual' ? 'du entscheidest von Hand' : 'das Gate entscheidet'
}

/**
 * A task in full, inside the training editor: the question as the agent reads it and every
 * check, judge questions listed. Deciding train or holdout needs to know what is measured.
 */
function TaskPeek({ task }: { task: Task }) {
  return (
    <div className="task-peek">
      {task.description && <p className="small muted">{task.description}</p>}
      <div className="field">
        <span>Frage an den Agent</span>
        <pre className="prose">{task.input.trim()}</pre>
      </div>
      <div className="field">
        <span>Bestanden, wenn {task.evals.length === 1 ? 'diese Prüfung hält' : `alle ${task.evals.length} Prüfungen halten`}</span>
        {task.evals.length === 0 ? (
          <p className="small muted">Keine Prüfung — dieser Task kann nicht scheitern und taugt nicht fürs Training.</p>
        ) : (
          <ol className="task-peek-checks">
            {task.evals.map((e, i) => (
              <li key={i}>
                <EvalSentence config={e} />
              </li>
            ))}
          </ol>
        )}
      </div>
      <span className="small muted">
        {task.maxToolCalls === null ? 'Tool-Budget wie voreingestellt' : `höchstens ${task.maxToolCalls} Tool-Aufrufe je Run`} · Id{' '}
        <span className="mono">{task.id}</span> v{task.version} ·{' '}
        <a href={`/tasks/${encodeURIComponent(task.id)}`} target="_blank" rel="noreferrer" className="link">
          in neuem Tab öffnen
        </a>
      </span>
    </div>
  )
}

/** The recipe as labelled sentences: who learns from whom, on what, against what, how much. */
function Recipe({ t }: { t: Training }) {
  return (
    <dl className="recipe">
      <div>
        <dt>Wer</dt>
        <dd>
          <Ref kind="agent" id={t.agentId} /> lernt, <Ref kind="agent" id={t.coachId} /> schreibt als Coach die Vorschläge.
        </dd>
      </div>
      <div>
        <dt>Gemessen an</dt>
        <dd>
          <span className="line">
            {t.train.map((id) => (
              <Ref key={id} kind="task" id={id} />
            ))}
            <span className="muted small">Train: der Coach sieht, woran diese Tasks scheitern.</span>
          </span>
        </dd>
      </div>
      {t.holdout.length > 0 && (
        <div>
          <dt>Kontrolle</dt>
          <dd>
            <span className="line">
              {t.holdout.map((id) => (
                <Ref key={id} kind="task" id={id} />
              ))}
              <span className="muted small">Holdout: wird mitgemessen, der Coach sieht ihn nie.</span>
            </span>
          </dd>
        </div>
      )}
      <div>
        <dt>Umfang</dt>
        <dd>
          Je Task {t.repetitions} Run{t.repetitions === 1 ? '' : 's'}, höchstens {t.maxSteps} Vorschl{t.maxSteps === 1 ? 'ag' : 'äge'},{' '}
          {t.maxTokens === null ? 'ohne Token-Grenze' : `höchstens ${t.maxTokens.toLocaleString('de-DE')} Tokens`}; {approvalText(t.approval)}.
        </dd>
      </div>
    </dl>
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
  // The task opened in the editor, to read it in full before giving it a role.
  const [peek, setPeek] = useState<string | null>(null)
  // The agent's versions, to start a session from one of them; the current one unless picked.
  const [agentVersions, setAgentVersions] = useState<{ agent: string; current: number; versions: number[] } | null>(null)
  const [startPick, setStartPick] = useState<{ training: string; version: number } | null>(null)
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

  // The agent's versions for the start choice; fetched again when a session added some.
  const shownAgent = shown?.training.agentId
  const sessionCount = shown?.sessions.length ?? 0
  useEffect(() => {
    if (!shownAgent) return
    let stale = false
    agentsApi
      .get(shownAgent)
      .then((d) => {
        if (!stale) setAgentVersions({ agent: d.agent.id, current: d.agent.version, versions: d.versions })
      })
      .catch(() => {})
    return () => {
      stale = true
    }
  }, [shownAgent, sessionCount])
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
      const from = startPick?.training === shown.training.id ? startPick.version : undefined
      const s = await trainingsApi.start(shown.training.id, from)
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
        // Folded fields carry no `required`: an emptied one falls back to its default.
        repetitions: Number(draft.repetitions) || 3,
        maxSteps: Number(draft.maxSteps) || 3,
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
  const versionsOfAgent = t && agentVersions?.agent === t.agentId ? agentVersions : null
  const startVersion = !t || !versionsOfAgent ? null : startPick?.training === t.id ? startPick.version : versionsOfAgent.current
  // Which versions a coach wrote in this training's sessions, to tell them apart in the list.
  const coachOrigins = t && shown ? originsOf(t.agentId, [shown]) : {}

  return (
    <>
      <section className="card kind t-training">
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
                  <div className="line small muted">
                    <Ref kind="agent" id={tr.agentId} /> lernt bei <Ref kind="agent" id={tr.coachId} />
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
        <section className="card kind t-training">
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
            </div>
          </div>

          {t.description && <p className="muted">{t.description}</p>}
          <Recipe t={t} />
          {t.goal && (
            <dl className="recipe">
              <div>
                <dt>Ziel</dt>
                <dd className="goal">{t.goal}</dd>
              </div>
            </dl>
          )}

          <div className="run-action">
            <button
              className="btn primary"
              onClick={start}
              disabled={busy || running || t.train.length === 0}
              title={running ? 'es läuft schon eine Session - eine zur Zeit' : 'Ausgangslage messen, dann Schritt für Schritt verbessern'}
            >
              trainieren
            </button>
            {versionsOfAgent && (
              <label className="start-from">
                ab
                <select
                  value={startVersion ?? ''}
                  onChange={(e) => setStartPick({ training: t.id, version: Number(e.target.value) })}
                  title="Mit welcher Version des Agents die Session beginnt"
                >
                  {[...versionsOfAgent.versions]
                    .sort((a, b) => b - a)
                    .map((v) => {
                      const o = coachOrigins[v]
                      return (
                        <option key={v} value={v}>
                          v{v}
                          {v === versionsOfAgent.current ? ' · aktuell' : ''}
                          {o ? ` · Coach, ${o.decision === 'ACCEPTED' ? 'angenommen' : o.decision === 'REJECTED' ? 'verworfen' : 'offen'}` : ''}
                        </option>
                      )
                    })}
                </select>
              </label>
            )}
            <CostLine t={t} />
            {running && <span className="pill open">eine Session läuft schon</span>}
          </div>
          {versionsOfAgent && startVersion !== null && startVersion !== versionsOfAgent.current && (
            <p className="hint">
              Startet bei v{startVersion} statt bei der aktuellen v{versionsOfAgent.current}. Was die Session schreibt, kommt als neue Version
              obendrauf; findet sie nichts Besseres als v{startVersion}, bleibt v{versionsOfAgent.current} aktuell.
            </p>
          )}

          <div className="field">
            <span>Sessions</span>
            {shown.sessions.length === 0 ? (
              <p className="hint">Noch keine. „trainieren“ misst zuerst die Ausgangslage, dann schreibt der Coach Version um Version.</p>
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
        <Modal kind="training" title={isExisting ? `${draft.name || draft.id} — neue Version` : 'Neues Training'} onClose={() => { setDraft(null); setSource(null) }}>
          <form
            className="form guided"
            onSubmit={(e) => {
              e.preventDefault()
              save()
            }}
          >
            <GuideStep no="①" title="Wer lernt, wer coacht?">
              <NameField
                naming={draft}
                existing={!!isExisting}
                placeholder="Rookie lernt Tools"
                onChange={(n) => setDraft({ ...draft, ...n })}
              />
              <div className="form-grid">
                <label className="field">
                  <span>Agent, der lernt</span>
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
                  <span>Coach, der umschreibt</span>
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
              </div>
            </GuideStep>

            <GuideStep
              no="②"
              title="Woran wird gemessen?"
              hint="Train: der Coach sieht die gescheiterten Runs dieser Tasks. Holdout: wird mitgemessen, der Coach sieht ihn nie — fällt er, war die Verbesserung auswendig gelernt."
            >
              <div className="task-roles">
                {tasks.map((task) => {
                  const role = draft.roles[task.id] ?? ''
                  const open = peek === task.id
                  return (
                    <div key={task.id} className={`task-role-group ${open ? 'open' : ''}`}>
                    <div className="task-role">
                      <button
                        type="button"
                        className="task-role-name"
                        onClick={() => setPeek(open ? null : task.id)}
                        aria-expanded={open}
                        title={open ? 'zuklappen' : 'Frage und Prüfungen ansehen'}
                      >
                        <span className="task-role-caret" aria-hidden="true">
                          {open ? '▾' : '▸'}
                        </span>
                        <span className="task-role-title">{task.name}</span>
                        <span className="task-role-question">{task.input.trim()}</span>
                      </button>
                      <span className="segment">
                        {(['', 'train', 'holdout'] as Role[]).map((r) => (
                          <button
                            key={r || 'none'}
                            type="button"
                            className={role === r ? (r === '' ? 'on neutral' : 'on') : ''}
                            onClick={() => setDraft({ ...draft, roles: { ...draft.roles, [task.id]: r } })}
                          >
                            {r === '' ? 'nicht dabei' : r === 'train' ? 'Train' : 'Holdout'}
                          </button>
                        ))}
                      </span>
                    </div>
                    {open && <TaskPeek task={task} />}
                    </div>
                  )
                })}
              </div>
            </GuideStep>

            <GuideStep no="③" title={<>Wohin soll es gehen? <span className="muted small">optional</span></>}>
              <label className="field">
                <span>Ziel, liest der Coach</span>
                <textarea
                  rows={3}
                  value={draft.goal}
                  onChange={(e) => setDraft({ ...draft, goal: e.target.value })}
                  placeholder="Der Agent soll Tools nutzen, wo sie die Antwort sicher machen."
                />
              </label>
            </GuideStep>

            <Advanced
              summary={`je Task ${draft.repetitions || 3}× · höchstens ${draft.maxSteps || 3} Vorschläge · ${
                draft.maxTokens ? `${Number(draft.maxTokens).toLocaleString('de-DE')} Tokens` : 'ohne Token-Grenze'
              } · ${approvalText(draft.approval)}`}
            >
              <div className="form-grid">
                <label className="field">
                  <span>Runs je Task und Messung</span>
                  <input type="number" min={1} max={20} value={draft.repetitions} onChange={(e) => setDraft({ ...draft, repetitions: e.target.value })} />
                  <span className="hint">Mehr Runs, weniger Zufall — und mehr Kosten.</span>
                </label>
                <label className="field">
                  <span>Vorschläge höchstens</span>
                  <input type="number" min={1} max={20} value={draft.maxSteps} onChange={(e) => setDraft({ ...draft, maxSteps: e.target.value })} />
                  <span className="hint">So oft schreibt der Coach den Prompt höchstens um.</span>
                </label>
                <label className="field">
                  <span>Token-Budget (leer = keins)</span>
                  <input type="number" min={1} value={draft.maxTokens} onChange={(e) => setDraft({ ...draft, maxTokens: e.target.value })} />
                </label>
                <div className="field">
                  <span>Wer entscheidet</span>
                  <span className="segment">
                    <button type="button" className={draft.approval === 'gate' ? 'on' : ''} onClick={() => setDraft({ ...draft, approval: 'gate' })}>
                      das Gate
                    </button>
                    <button type="button" className={draft.approval === 'manual' ? 'on' : ''} onClick={() => setDraft({ ...draft, approval: 'manual' })}>
                      ich, von Hand
                    </button>
                  </span>
                </div>
                <label className="field span-2">
                  <span>Beschreibung, für Menschen</span>
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
            </Advanced>

            <div className="toolbar end">
              <CostLine
                t={{
                  train: tasks.filter((x) => draft.roles[x.id] === 'train').map((x) => x.id),
                  holdout: tasks.filter((x) => draft.roles[x.id] === 'holdout').map((x) => x.id),
                  repetitions: Number(draft.repetitions) || 3,
                  maxSteps: Number(draft.maxSteps) || 3,
                }}
              />
              <span className="spacer" />
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
        <Modal kind="training" title="Training löschen?" onClose={() => setConfirmDelete(false)}>
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
