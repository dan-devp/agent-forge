import { useEffect, useState } from 'react'
import { useNavigate, useParams } from 'react-router'
import {
  api,
  catalogApi,
  tasksApi,
  type EvalConfig,
  type EvalParam,
  type EvalType,
  type ModelView,
  type Task,
  type TaskDetail,
  type TaskRequest,
  type ToolInfo,
} from '../api/client'
import { EvalSentence } from './EvalSentence'
import { EXAMPLES, groupOf, groupedTypes, missingParams } from './evalGuide'
import { Advanced, GuideStep, NameField } from './forms'
import { Modal } from './Modal'

type Props = {
  onError: (message: string) => void
  onChanged: () => void
}

type Draft = {
  id: string
  name: string
  idTouched: boolean
  description: string
  maxToolCalls: string
  evals: EvalConfig[]
  input: string
}

const EMPTY: Draft = { id: '', name: '', idTouched: false, description: '', maxToolCalls: '', evals: [], input: '' }

function toDraft(t: Task): Draft {
  return {
    id: t.id,
    name: t.name,
    idTouched: true,
    description: t.description ?? '',
    maxToolCalls: t.maxToolCalls === null ? '' : String(t.maxToolCalls),
    evals: t.evals.map((e) => ({ type: e.type, params: { ...e.params } })),
    input: t.input,
  }
}

/** A fresh eval of this type with the schema's defaults filled in. */
function freshEval(type: EvalType): EvalConfig {
  const params: Record<string, unknown> = {}
  for (const p of type.params) {
    if (p.defaultValue !== null && p.defaultValue !== undefined) params[p.name] = p.defaultValue
  }
  return { type: type.type, params }
}

/** Drop blanks so the file only carries what the author set. Numbers become numbers. */
function cleanParams(config: EvalConfig, type: EvalType | undefined): Record<string, unknown> {
  const out: Record<string, unknown> = {}
  for (const [k, v] of Object.entries(config.params)) {
    if (v === '' || v === null || v === undefined) continue
    const spec = type?.params.find((p) => p.name === k)
    if (spec?.type === 'LIST') {
      const items = (Array.isArray(v) ? v.map(String) : String(v).split('\n')).map((x) => x.trim()).filter(Boolean)
      if (items.length > 0) out[k] = items
    } else if (spec?.type === 'NUMBER' && typeof v === 'string') {
      const n = Number(v)
      out[k] = Number.isNaN(n) ? v : n
    } else {
      out[k] = v
    }
  }
  return out
}

/**
 * "Bestanden, wenn …": every eval as a sentence, numbered, because all of them must hold. With
 * detail, a judge's questions are listed, not just counted.
 */
function PassSummary({ evals, types, detail = false }: { evals: EvalConfig[]; types: EvalType[]; detail?: boolean }) {
  if (evals.length === 0) {
    return (
      <div className="pass-summary empty-summary">
        <strong>Noch keine Prüfung.</strong> Ohne Prüfung wird ein Run nicht bewertet — er zählt weder als bestanden noch als gescheitert.
      </div>
    )
  }
  const incomplete = evals.filter((e) => missingParams(e, types.find((t) => t.type === e.type)).length > 0).length
  return (
    <div className="pass-summary">
      <span className="lbl">Bestanden, wenn {evals.length === 1 ? 'diese Prüfung hält' : `alle ${evals.length} Prüfungen halten`}:</span>
      <ol>
        {evals.map((e, i) => (
          <li key={i}>
            <EvalSentence config={e} detail={detail} />
          </li>
        ))}
      </ol>
      {incomplete > 0 && <span className="small error">{incomplete} Prüfung(en) unvollständig — „…“ markiert, was fehlt.</span>}
    </div>
  )
}

/** Group pills for a list row: what kind of checks a task has, at a glance. */
function GroupCounts({ evals }: { evals: EvalConfig[] }) {
  const counts = new Map<string, number>()
  evals.forEach((e) => {
    const label = groupOf(e.type)?.label ?? 'Weitere'
    counts.set(label, (counts.get(label) ?? 0) + 1)
  })
  if (counts.size === 0) return <span className="muted small">keine Prüfung</span>
  return (
    <>
      {[...counts].map(([label, n]) => (
        <span key={label} className="small muted">
          {label} <span className="mono">{n}</span>
        </span>
      ))}
    </>
  )
}

/**
 * Tasks, guided by three questions: what is asked, how a good answer is recognised, which
 * limits apply. Evals are chosen by intent and shown as sentences; the summary at the end
 * says when a run passes — so what was set is what was meant.
 */
export function TasksArea(p: Props) {
  const { id: selectedId } = useParams()
  const navigate = useNavigate()

  const [tasks, setTasks] = useState<Task[] | null>(null)
  const [types, setTypes] = useState<EvalType[]>([])
  const [models, setModels] = useState<ModelView[]>([])
  const [tools, setTools] = useState<ToolInfo[]>([])
  const [detail, setDetail] = useState<TaskDetail | null>(null)
  const [view, setView] = useState<{ task: Task; source: string | null } | null>(null)
  const [draft, setDraft] = useState<Draft | null>(null)
  const [editing, setEditing] = useState<number | null>(null)
  const [picking, setPicking] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)
  const [confirmDelete, setConfirmDelete] = useState(false)

  const fail = (e: Error) => p.onError(e.message)
  const loadList = () => tasksApi.list().then(setTasks).catch(fail)

  useEffect(() => {
    loadList()
    catalogApi.evals().then(setTypes).catch(fail)
    api.models().then(setModels).catch(fail)
    catalogApi
      .tools()
      .then((t) => setTools(t.tools))
      .catch(fail)
  }, []) // eslint-disable-line react-hooks/exhaustive-deps

  useEffect(() => {
    if (!selectedId) return
    let stale = false
    tasksApi
      .get(selectedId)
      .then((d) => {
        if (stale) return
        setDetail(d)
        setView({ task: d.task, source: null })
      })
      .catch((e: Error) => {
        if (stale) return
        fail(e)
        navigate('/tasks', { replace: true })
      })
    return () => {
      stale = true
    }
  }, [selectedId]) // eslint-disable-line react-hooks/exhaustive-deps

  const shownDetail = selectedId ? detail : null
  const displayed = selectedId ? view?.task ?? null : null
  const typeOf = (key: string) => types.find((t) => t.type === key)

  function openEditor(next: Draft) {
    setDraft(next)
    setEditing(null)
    setPicking(next.evals.length === 0 ? 'content' : null)
  }

  function closeEditor() {
    setDraft(null)
    setEditing(null)
    setPicking(null)
  }

  function showVersion(version: number) {
    if (!detail) return
    if (version === detail.task.version) {
      setView({ task: detail.task, source: null })
      return
    }
    Promise.all([tasksApi.version(detail.task.id, version), tasksApi.source(detail.task.id, version)])
      .then(([task, source]) => setView({ task, source }))
      .catch(fail)
  }

  function showSource() {
    if (!view) return
    tasksApi
      .source(view.task.id, view.task.version)
      .then((source) => setView({ task: view.task, source }))
      .catch(fail)
  }

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

  const save = () =>
    perform(async () => {
      if (!draft) return
      const body: TaskRequest = {
        name: draft.name,
        description: draft.description || null,
        maxToolCalls: draft.maxToolCalls === '' ? null : Number(draft.maxToolCalls),
        evals: draft.evals.map((e) => ({ type: e.type, params: cleanParams(e, typeOf(e.type)) })),
        input: draft.input,
      }
      const isNew = !tasks?.some((t) => t.id === draft.id)
      const saved = isNew ? await tasksApi.create({ ...body, id: draft.id }) : await tasksApi.update(draft.id, body)
      closeEditor()
      await loadList()
      p.onChanged()
      if (saved.id === selectedId) {
        const d = await tasksApi.get(saved.id)
        setDetail(d)
        setView({ task: d.task, source: null })
      } else {
        navigate(`/tasks/${saved.id}`)
      }
    })

  const remove = () =>
    perform(async () => {
      if (!detail) return
      await tasksApi.remove(detail.task.id)
      setConfirmDelete(false)
      setDetail(null)
      setView(null)
      await loadList()
      p.onChanged()
      navigate('/tasks')
    })

  const reload = () =>
    perform(async () => {
      setTasks(await tasksApi.reload())
      if (selectedId) {
        const d = await tasksApi.get(selectedId)
        setDetail(d)
        setView({ task: d.task, source: null })
      }
    })

  const isExisting = draft ? tasks?.some((t) => t.id === draft.id) : false

  // --- eval editor helpers -------------------------------------------------------------
  const setEval = (i: number, next: EvalConfig) =>
    draft && setDraft({ ...draft, evals: draft.evals.map((e, k) => (k === i ? next : e)) })
  const removeEval = (i: number) => {
    if (!draft) return
    setDraft({ ...draft, evals: draft.evals.filter((_, k) => k !== i) })
    setEditing(null)
  }
  const moveEval = (i: number, dir: -1 | 1) => {
    if (!draft) return
    const j = i + dir
    if (j < 0 || j >= draft.evals.length) return
    const evals = [...draft.evals]
    ;[evals[i], evals[j]] = [evals[j], evals[i]]
    setDraft({ ...draft, evals })
    setEditing(editing === i ? j : editing === j ? i : editing)
  }
  const addEval = (type: EvalType) => {
    if (!draft) return
    setDraft({ ...draft, evals: [...draft.evals, freshEval(type)] })
    setEditing(draft.evals.length)
    setPicking(null)
  }

  return (
    <>
      <section className="card kind t-task">
        <div className="card-head">
          <h2>Tasks</h2>
          <span className="sub">Was der Agent gefragt wird und woran man eine gute Antwort erkennt</span>
          <div className="actions">
            <button className="btn quiet sm" onClick={reload} disabled={busy} title="Dateien neu einlesen">
              neu laden
            </button>
            <button className="btn primary sm" onClick={() => openEditor({ ...EMPTY })} disabled={busy}>
              neuer Task
            </button>
          </div>
        </div>
        {tasks === null ? (
          <p className="empty">lädt …</p>
        ) : tasks.length === 0 ? (
          <p className="empty">Noch kein Task.</p>
        ) : (
          <div className="rows">
            {tasks.map((t) => (
              <div
                key={t.id}
                className={`row ${selectedId === t.id ? 'selected' : ''}`}
                onClick={() => navigate(`/tasks/${t.id}`)}
                role="button"
                tabIndex={0}
                onKeyDown={(e) => e.key === 'Enter' && navigate(`/tasks/${t.id}`)}
              >
                <div>
                  <div className="title">{t.name}</div>
                  <div className="line small">
                    <span className="mono">{t.id}</span>
                    <span className="pill mono">v{t.version}</span>
                  </div>
                </div>
                <div className="line small muted task-question">{t.input}</div>
                <div className="line">
                  <GroupCounts evals={t.evals} />
                </div>
              </div>
            ))}
          </div>
        )}
      </section>

      {shownDetail && displayed && (
        <section className="card kind t-task">
          <div className="card-head">
            <h2>{shownDetail.task.name}</h2>
            <div className="versions">
              {shownDetail.versions.map((v) => (
                <button
                  key={v}
                  className={`pill mono btn ${displayed.version === v ? 'accent' : ''}`}
                  onClick={() => showVersion(v)}
                >
                  v{v}
                </button>
              ))}
            </div>
            <span className="sub mono small">{shownDetail.task.id}</span>
            <div className="actions">
              <button className="btn quiet danger sm" onClick={() => setConfirmDelete(true)} disabled={busy}>
                löschen
              </button>
              <button className="btn sm" onClick={() => openEditor(toDraft(shownDetail.task))} disabled={busy}>
                bearbeiten
              </button>
            </div>
          </div>

          {displayed.description && <p className="muted">{displayed.description}</p>}

          <div className="phase">
            <span className="phase-no">①</span>
            <span className="phase-name">Frage</span>
            <div className="phase-body">
              <pre className="prose">{displayed.input}</pre>
            </div>
          </div>

          <div className="phase">
            <span className="phase-no">②</span>
            <span className="phase-name">Prüfungen</span>
            <div className="phase-body">
              <PassSummary evals={displayed.evals} types={types} detail />
            </div>
          </div>

          <div className="phase">
            <span className="phase-no">③</span>
            <span className="phase-name">Grenzen</span>
            <div className="phase-body">
              <p>
                {displayed.maxToolCalls === null ? (
                  <span className="muted">Tool-Budget wie voreingestellt</span>
                ) : (
                  <>
                    höchstens <span className="mono">{displayed.maxToolCalls}</span> Tool-Aufrufe je Run
                  </>
                )}
                <span className="muted"> · {shownDetail.runs} Run(s) bisher</span>
              </p>
            </div>
          </div>

          {view?.source != null ? (
            <pre className="source mono">{view.source}</pre>
          ) : (
            <button className="link" onClick={showSource}>
              Datei anzeigen
            </button>
          )}
        </section>
      )}

      {draft && (
        <Modal kind="task" title={isExisting ? `${draft.name || draft.id} — neue Version` : 'Neuer Task'} onClose={closeEditor}>
          <form
            className="form guided"
            onSubmit={(e) => {
              e.preventDefault()
              save()
            }}
          >
            <GuideStep
              no="①"
              title="Was fragst du den Agent?"
              hint="Die Eingabe wird die Nachricht an den Agent, wörtlich. Was hier steht, muss der Agent nicht selbst wissen — soll er eine Regel lernen, gehört sie nicht hierher, sondern in die Prüfungen."
            >
              <label className="field">
                <span>Eingabe</span>
                <textarea
                  className="prompt"
                  value={draft.input}
                  onChange={(e) => setDraft({ ...draft, input: e.target.value })}
                  required
                  placeholder="Wie spät ist es gerade in Berlin?"
                />
              </label>
              <NameField naming={draft} existing={!!isExisting} placeholder="Uhrzeit in Berlin" onChange={(n) => setDraft({ ...draft, ...n })} />
            </GuideStep>

            <GuideStep
              no="②"
              title="Woran erkennst du eine gute Antwort?"
              hint="Jede Prüfung ist eine Bedingung, alle müssen halten. Endet ein Run ohne Antwort (Fehler, Budget), scheitern auch die verneinten."
            >

              {draft.evals.length > 0 && (
                <div className="guided-evals">
                  {draft.evals.map((e, i) => {
                    const type = typeOf(e.type)
                    const missing = missingParams(e, type)
                    const open = editing === i
                    return (
                      <div key={i} className={`guided-eval ${open ? 'open' : ''} ${missing.length > 0 ? 'incomplete' : ''}`}>
                        <div className="guided-eval-head">
                          <span className="mono muted">{i + 1}</span>
                          <button type="button" className="sentence-btn" onClick={() => setEditing(open ? null : i)} title="bearbeiten">
                            <EvalSentence config={e} />
                          </button>
                          <span className="spacer" />
                          <button type="button" className="btn quiet sm" onClick={() => setEditing(open ? null : i)}>
                            {open ? 'fertig' : 'ändern'}
                          </button>
                          <button type="button" className="btn quiet sm" onClick={() => moveEval(i, -1)} disabled={i === 0} title="nach oben">
                            ↑
                          </button>
                          <button
                            type="button"
                            className="btn quiet sm"
                            onClick={() => moveEval(i, 1)}
                            disabled={i === draft.evals.length - 1}
                            title="nach unten"
                          >
                            ↓
                          </button>
                          <button type="button" className="btn quiet danger sm" onClick={() => removeEval(i)} title="entfernen">
                            ×
                          </button>
                        </div>
                        {open && type && (
                          <div className="guided-eval-body">
                            <span className="small muted">
                              <span className="mono">{type.type}</span> · {type.description}
                            </span>
                            {type.params.length > 0 && (
                              <div className="form-grid">
                                {type.params.map((pm) => (
                                  <ParamField
                                    key={pm.name}
                                    spec={pm}
                                    value={e.params[pm.name]}
                                    models={models}
                                    tools={tools}
                                    onChange={(v) => setEval(i, { ...e, params: { ...e.params, [pm.name]: v } })}
                                  />
                                ))}
                              </div>
                            )}
                          </div>
                        )}
                      </div>
                    )
                  })}
                </div>
              )}

              <div className="eval-picker">
                <span className="lbl">{draft.evals.length === 0 ? 'Was soll geprüft werden?' : 'Weitere Prüfung: was soll geprüft werden?'}</span>
                <div className="segment wrap">
                  {groupedTypes(types).map(({ group }) => (
                    <button
                      key={group.id}
                      type="button"
                      className={picking === group.id ? 'on' : ''}
                      onClick={() => setPicking(picking === group.id ? null : group.id)}
                    >
                      {group.label}
                    </button>
                  ))}
                </div>
                {groupedTypes(types)
                  .filter(({ group }) => group.id === picking)
                  .map(({ group, types: groupTypes }) => (
                    <div key={group.id} className="eval-choices">
                      <span className="small muted">{group.hint}</span>
                      {groupTypes.map((t) => (
                        <button key={t.type} type="button" className="eval-choice" onClick={() => addEval(t)}>
                          <span className="choice-label">{t.label}</span>
                          <span className="small muted">{t.description}</span>
                          {EXAMPLES[t.type] && <span className="small choice-example">z. B. {EXAMPLES[t.type]}</span>}
                        </button>
                      ))}
                    </div>
                  ))}
              </div>
            </GuideStep>

            <Advanced
              summary={[
                draft.maxToolCalls === '' ? 'Tool-Budget wie voreingestellt' : `höchstens ${draft.maxToolCalls} Tool-Aufrufe`,
                draft.description ? 'Beschreibung gesetzt' : 'Beschreibung',
              ].join(' · ')}
            >
              <div className="form-grid">
                <label className="field">
                  <span>Tool-Budget je Run (leer = Vorgabe)</span>
                  <input type="number" min="0" value={draft.maxToolCalls} onChange={(e) => setDraft({ ...draft, maxToolCalls: e.target.value })} />
                  <span className="hint">Wird es überschritten, endet der Run als „Budget überschritten“ und scheitert.</span>
                </label>
                <label className="field">
                  <span>Beschreibung, für Menschen</span>
                  <input
                    value={draft.description}
                    onChange={(e) => setDraft({ ...draft, description: e.target.value })}
                    placeholder="wofür der Task da ist"
                  />
                </label>
              </div>
            </Advanced>

            <PassSummary evals={draft.evals} types={types} />

            <div className="toolbar end">
              <button type="button" className="btn quiet" onClick={closeEditor} disabled={busy}>
                abbrechen
              </button>
              <button type="submit" className="btn primary" disabled={busy}>
                {isExisting ? 'als neue Version speichern' : 'anlegen'}
              </button>
            </div>
          </form>
        </Modal>
      )}

      {confirmDelete && shownDetail && (
        <Modal kind="task" title="Task löschen?" onClose={() => setConfirmDelete(false)}>
          <p>
            <strong>{shownDetail.task.name}</strong> <span className="mono muted">({shownDetail.task.id})</span> wird mit
            allen {shownDetail.versions.length} Version(en) ins Archiv verschoben. Die Id wird frei.
            {shownDetail.runs > 0 && ` ${shownDetail.runs} Run(s) verweisen darauf und behalten Id und Version.`}
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

/** One parameter field, rendered by its schema type. */
function ParamField({
  spec,
  value,
  models,
  tools,
  onChange,
}: {
  spec: EvalParam
  value: unknown
  models: ModelView[]
  tools: ToolInfo[]
  onChange: (v: unknown) => void
}) {
  const label = (
    <span>
      {spec.label}
      {spec.required && <span className="faint"> *</span>}
    </span>
  )
  const wide = spec.type === 'TEXT' || spec.type === 'LIST'
  const cls = `field ${wide ? 'span-2' : ''}`

  switch (spec.type) {
    case 'BOOLEAN':
      return (
        <label className={`field check ${wide ? 'span-2' : ''}`} title={spec.description}>
          <input type="checkbox" checked={Boolean(value)} onChange={(e) => onChange(e.target.checked)} />
          <span>{spec.label}</span>
        </label>
      )
    case 'TEXT':
      return (
        <label className={cls} title={spec.description}>
          {label}
          <textarea rows={3} value={String(value ?? '')} onChange={(e) => onChange(e.target.value)} required={spec.required} />
        </label>
      )
    case 'LIST':
      return (
        <label className={cls} title={spec.description}>
          {label}
          <textarea
            rows={4}
            value={Array.isArray(value) ? value.join('\n') : String(value ?? '')}
            onChange={(e) => onChange(e.target.value)}
            required={spec.required}
            placeholder="eine Frage je Zeile"
          />
          <span className="hint">{spec.description}</span>
        </label>
      )
    case 'NUMBER':
      return (
        <label className={cls} title={spec.description}>
          {label}
          <input type="number" value={String(value ?? '')} onChange={(e) => onChange(e.target.value)} required={spec.required} />
        </label>
      )
    case 'MODEL':
    case 'JUDGE_MODEL':
      // MODEL takes chat models only; a judge that answers typed questions may be either kind.
      return (
        <label className={cls} title={spec.description}>
          {label}
          <select value={String(value ?? '')} onChange={(e) => onChange(e.target.value)} required={spec.required}>
            <option value="">— Model wählen —</option>
            {models
              .filter((m) => spec.type === 'JUDGE_MODEL' || m.kind === 'chat' || m.id === value)
              .map((m) => (
                <option key={m.id} value={m.id}>
                  {m.name} · {m.modelId}
                  {m.kind === 'decision' ? ' · Decision' : ''}
                </option>
              ))}
          </select>
        </label>
      )
    case 'TOOL':
      return (
        <label className={cls} title={spec.description}>
          {label}
          <input
            list={`tools-${spec.name}`}
            value={String(value ?? '')}
            onChange={(e) => onChange(e.target.value)}
            required={spec.required}
            className="mono"
            placeholder="Tool-Name oder *"
          />
          <datalist id={`tools-${spec.name}`}>
            {tools.map((t) => (
              <option key={t.name} value={t.name} />
            ))}
            <option value="*" />
          </datalist>
        </label>
      )
    default:
      return (
        <label className={cls} title={spec.description}>
          {label}
          <input
            value={String(value ?? '')}
            onChange={(e) => onChange(e.target.value)}
            required={spec.required}
            className={spec.name === 'pattern' ? 'mono' : undefined}
            placeholder={spec.description}
          />
        </label>
      )
  }
}
