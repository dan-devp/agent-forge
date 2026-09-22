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
import { Modal } from './Modal'

type Props = {
  onError: (message: string) => void
  onChanged: () => void
}

type Draft = {
  id: string
  name: string
  description: string
  maxToolCalls: string
  evals: EvalConfig[]
  input: string
}

const EMPTY: Draft = { id: '', name: '', description: '', maxToolCalls: '', evals: [], input: '' }

function toDraft(t: Task): Draft {
  return {
    id: t.id,
    name: t.name,
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
    if (spec?.type === 'NUMBER' && typeof v === 'string') {
      const n = Number(v)
      out[k] = Number.isNaN(n) ? v : n
    } else {
      out[k] = v
    }
  }
  return out
}

/**
 * Tasks: input + evals. The eval editor renders each entry's form from the schema the
 * server publishes — a new eval type needs no UI change.
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
      setDraft(null)
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
  const removeEval = (i: number) => draft && setDraft({ ...draft, evals: draft.evals.filter((_, k) => k !== i) })
  const moveEval = (i: number, dir: -1 | 1) => {
    if (!draft) return
    const j = i + dir
    if (j < 0 || j >= draft.evals.length) return
    const evals = [...draft.evals]
    ;[evals[i], evals[j]] = [evals[j], evals[i]]
    setDraft({ ...draft, evals })
  }

  return (
    <>
      <section className="card">
        <div className="card-head">
          <h2>Aufgaben</h2>
          <span className="sub">Eingabe wird die Nachricht an den Agenten, Evals entscheiden über bestanden</span>
          <div className="actions">
            <button className="btn quiet sm" onClick={reload} disabled={busy} title="Dateien neu einlesen">
              neu laden
            </button>
            <button className="btn primary sm" onClick={() => setDraft({ ...EMPTY })} disabled={busy}>
              neue Aufgabe
            </button>
          </div>
        </div>
        {tasks === null ? (
          <p className="empty">lädt …</p>
        ) : tasks.length === 0 ? (
          <p className="empty">Noch keine Aufgabe.</p>
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
                <div className="line">
                  {t.evals.map((e, i) => (
                    <span key={i} className="pill mono">
                      {e.type}
                    </span>
                  ))}
                  {t.evals.length === 0 && <span className="muted small">keine Evals</span>}
                </div>
                <div className="line">
                  {t.maxToolCalls !== null && (
                    <span className="pill" title="Werkzeugbudget">
                      ≤ {t.maxToolCalls} Aufrufe
                    </span>
                  )}
                </div>
              </div>
            ))}
          </div>
        )}
      </section>

      {shownDetail && displayed && (
        <section className="card">
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
              <button className="btn sm" onClick={() => setDraft(toDraft(shownDetail.task))} disabled={busy}>
                bearbeiten
              </button>
              <button className="btn quiet danger sm" onClick={() => setConfirmDelete(true)} disabled={busy}>
                löschen
              </button>
            </div>
          </div>

          <dl className="facts">
            <div>
              <dt>Werkzeugbudget</dt>
              <dd className="mono">{displayed.maxToolCalls === null ? 'Vorgabe' : displayed.maxToolCalls}</dd>
            </div>
            <div>
              <dt>Läufe</dt>
              <dd className="mono">{shownDetail.runs}</dd>
            </div>
            {displayed.description && (
              <div style={{ gridColumn: '1 / -1' }}>
                <dt>Beschreibung</dt>
                <dd>{displayed.description}</dd>
              </div>
            )}
          </dl>

          <div className="field">
            <span>Eingabe</span>
            <pre className="prose">{displayed.input}</pre>
          </div>

          <div className="field">
            <span>Evals ({displayed.evals.length})</span>
            {displayed.evals.length === 0 ? (
              <p className="hint">Ohne Evals wird ein Lauf nicht bewertet.</p>
            ) : (
              <div className="eval-list">
                {displayed.evals.map((e, i) => (
                  <div key={i} className="eval-row">
                    <span className="mono muted">{i + 1}</span>
                    <span className="pill mono accent">{e.type}</span>
                    <span className="muted small">{typeOf(e.type)?.label}</span>
                    {Object.entries(e.params).map(([k, v]) => (
                      <span key={k} className="kv">
                        {k}=<b>{String(v)}</b>
                      </span>
                    ))}
                  </div>
                ))}
              </div>
            )}
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
        <Modal title={isExisting ? `${draft.name || draft.id} — neue Version` : 'Neue Aufgabe'} onClose={() => setDraft(null)}>
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
              <label className="field span-2">
                <span>Eingabe (Nachricht an den Agenten)</span>
                <textarea
                  className="prompt"
                  value={draft.input}
                  onChange={(e) => setDraft({ ...draft, input: e.target.value })}
                  required
                  placeholder="Wie spät ist es gerade in Berlin?"
                />
              </label>
              <label className="field">
                <span>Werkzeugbudget (leer = Vorgabe)</span>
                <input
                  type="number"
                  min="0"
                  value={draft.maxToolCalls}
                  onChange={(e) => setDraft({ ...draft, maxToolCalls: e.target.value })}
                />
              </label>
              <label className="field">
                <span>Beschreibung</span>
                <input value={draft.description} onChange={(e) => setDraft({ ...draft, description: e.target.value })} />
              </label>

              <div className="field span-2">
                <span>Evals</span>
                <div className="eval-list">
                  {draft.evals.map((e, i) => {
                    const type = typeOf(e.type)
                    return (
                      <div key={i} className="eval-item">
                        <div className="eval-head">
                          <span className="idx">{i + 1}</span>
                          <select
                            value={e.type}
                            onChange={(ev) => {
                              const next = typeOf(ev.target.value)
                              if (next) setEval(i, freshEval(next))
                            }}
                          >
                            {types.map((t) => (
                              <option key={t.type} value={t.type}>
                                {t.label} · {t.type}
                              </option>
                            ))}
                          </select>
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
                        {type && <span className="desc">{type.description}</span>}
                        {type && type.params.length > 0 && (
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
                    )
                  })}
                </div>
                <div className="toolbar">
                  <select
                    value=""
                    onChange={(ev) => {
                      const t = typeOf(ev.target.value)
                      if (t) setDraft({ ...draft, evals: [...draft.evals, freshEval(t)] })
                    }}
                  >
                    <option value="">+ Eval hinzufügen …</option>
                    {types.map((t) => (
                      <option key={t.type} value={t.type}>
                        {t.label} · {t.type}
                      </option>
                    ))}
                  </select>
                </div>
                <span className="hint">Alle Evals müssen bestehen. Reihenfolge ist nur für die Anzeige.</span>
              </div>
            </div>
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

      {confirmDelete && shownDetail && (
        <Modal title="Aufgabe löschen?" onClose={() => setConfirmDelete(false)}>
          <p>
            <strong>{shownDetail.task.name}</strong> <span className="mono muted">({shownDetail.task.id})</span> wird mit
            allen {shownDetail.versions.length} Version(en) ins Archiv verschoben. Die Id wird frei.
            {shownDetail.runs > 0 && ` ${shownDetail.runs} Lauf/Läufe verweisen darauf und behalten Id und Version.`}
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
  const wide = spec.type === 'TEXT'
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
    case 'NUMBER':
      return (
        <label className={cls} title={spec.description}>
          {label}
          <input type="number" value={String(value ?? '')} onChange={(e) => onChange(e.target.value)} required={spec.required} />
        </label>
      )
    case 'MODEL':
      return (
        <label className={cls} title={spec.description}>
          {label}
          <select value={String(value ?? '')} onChange={(e) => onChange(e.target.value)} required={spec.required}>
            <option value="">— Modell wählen —</option>
            {models.map((m) => (
              <option key={m.id} value={m.id}>
                {m.name} · {m.modelId}
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
            placeholder="Werkzeugname oder *"
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
