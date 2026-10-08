import { useEffect, useState } from 'react'
import { useNavigate, useParams } from 'react-router'
import {
  agentsApi,
  api,
  type AgentDetail,
  type AgentRequest,
  type AgentView,
  type ModelView,
  type ToolAccess,
  type ToolInfo,
} from '../api/client'
import { Advanced, GuideStep, NameField } from './forms'
import { Modal } from './Modal'
import { PromptDiff } from './PromptDiff'
import { changeOf } from './versions'
import { Ref } from './Ref'

type Props = {
  onError: (message: string) => void
  onChanged: () => void
}

type Mode = 'all' | 'none' | 'list'

type Draft = {
  id: string
  name: string
  idTouched: boolean
  description: string
  model: string
  mode: Mode
  picked: string[]
  systemPrompt: string
}

const EMPTY: Draft = {
  id: '',
  name: '',
  idTouched: false,
  description: '',
  model: '',
  mode: 'all',
  picked: [],
  systemPrompt: '',
}

function toDraft(a: AgentView): Draft {
  const list = Array.isArray(a.tools)
  return {
    id: a.id,
    name: a.name,
    idTouched: true,
    description: a.description ?? '',
    model: a.model,
    mode: list ? 'list' : (a.tools as Mode),
    picked: list ? (a.tools as string[]) : [],
    systemPrompt: a.systemPrompt,
  }
}

function toAccess(d: Draft): ToolAccess {
  return d.mode === 'list' ? d.picked : d.mode
}

function accessLabel(t: ToolAccess) {
  if (t === 'all') return 'all Tools'
  if (t === 'none') return 'no Tools'
  return t.length === 1 ? '1 Tool' : `${t.length} Tools`
}

/**
 * Agents: model + system prompt + tool allowlist. The prompt is the file's body and is
 * shown as prose; the allowlist is shown as what it means right now (effective tools) and
 * where it points at nothing (missing tools, usually a typo).
 */
export function AgentsArea(p: Props) {
  const { id: selectedId } = useParams()
  const navigate = useNavigate()

  const [agents, setAgents] = useState<AgentView[] | null>(null)
  const [models, setModels] = useState<ModelView[]>([])
  const [tools, setTools] = useState<ToolInfo[]>([])
  const [detail, setDetail] = useState<AgentDetail | null>(null)
  // The version shown, its file if asked for, and the version before it (undefined while loading).
  const [view, setView] = useState<{ agent: AgentView; source: string | null; prev: AgentView | null | undefined } | null>(null)
  const [draft, setDraft] = useState<Draft | null>(null)
  const [busy, setBusy] = useState(false)
  const [confirmDelete, setConfirmDelete] = useState(false)

  const fail = (e: Error) => p.onError(e.message)

  const loadList = () => agentsApi.list().then(setAgents).catch(fail)

  useEffect(() => {
    loadList()
    api.models().then(setModels).catch(fail)
    agentsApi
      .tools()
      .then((t) => setTools(t.tools))
      .catch(fail)
  }, []) // eslint-disable-line react-hooks/exhaustive-deps

  useEffect(() => {
    if (!selectedId) return
    let stale = false
    agentsApi
      .get(selectedId)
      .then((d) => {
        if (stale) return
        setDetail(d)
        show(d, d.agent.version)
      })
      .catch((e: Error) => {
        if (stale) return
        fail(e)
        navigate('/agents', { replace: true })
      })
    return () => {
      stale = true
    }
  }, [selectedId]) // eslint-disable-line react-hooks/exhaustive-deps

  const shownDetail = selectedId ? detail : null
  const displayed = selectedId ? view?.agent ?? null : null

  /**
   * Shows one version together with the one before it, so every step through the versions
   * reads as a change. An older version brings its file along; the current one shows it on demand.
   */
  function show(d: AgentDetail, version: number) {
    const older = [...d.versions].sort((a, b) => a - b).filter((v) => v < version)
    const before = older.length > 0 ? older[older.length - 1] : null
    const current = version === d.agent.version
    // The current version is known already: show it now, the change follows when the one before arrives.
    if (current) setView({ agent: d.agent, source: null, prev: undefined })
    Promise.all([
      current ? Promise.resolve(d.agent) : agentsApi.version(d.agent.id, version),
      current ? Promise.resolve(null) : agentsApi.source(d.agent.id, version),
      before === null ? Promise.resolve(null) : agentsApi.version(d.agent.id, before),
    ])
      .then(([agent, source, prev]) => setView({ agent, source, prev }))
      .catch(fail)
  }

  function showVersion(version: number) {
    if (detail) show(detail, version)
  }

  function showSource() {
    if (!view) return
    agentsApi
      .source(view.agent.id, view.agent.version)
      .then((source) => setView({ ...view, source }))
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
      const body: AgentRequest = {
        name: draft.name,
        description: draft.description || null,
        model: draft.model,
        tools: toAccess(draft),
        systemPrompt: draft.systemPrompt,
      }
      const isNew = !agents?.some((a) => a.id === draft.id)
      const saved = isNew ? await agentsApi.create({ ...body, id: draft.id }) : await agentsApi.update(draft.id, body)
      setDraft(null)
      await loadList()
      p.onChanged()
      if (saved.id === selectedId) {
        const d = await agentsApi.get(saved.id)
        setDetail(d)
        show(d, d.agent.version)
      } else {
        navigate(`/agents/${saved.id}`)
      }
    })

  const remove = () =>
    perform(async () => {
      if (!detail) return
      await agentsApi.remove(detail.agent.id)
      setConfirmDelete(false)
      setDetail(null)
      setView(null)
      await loadList()
      p.onChanged()
      navigate('/agents')
    })

  const reload = () =>
    perform(async () => {
      setAgents(await agentsApi.reload())
      if (selectedId) {
        const d = await agentsApi.get(selectedId)
        setDetail(d)
        show(d, d.agent.version)
      }
    })

  const isExisting = draft ? agents?.some((a) => a.id === draft.id) : false
  const modelName = (id: string) => models.find((m) => m.id === id)?.name ?? id

  return (
    <>
      <section className="card kind t-agent">
        <div className="card-head">
          <h2>Agents</h2>
          <span className="sub">A Model with instructions and the Tools it may use</span>
          <div className="actions">
            <button className="btn quiet sm" onClick={reload} disabled={busy} title="Read the files again">
              reload
            </button>
            <button
              className="btn primary sm"
              onClick={() => setDraft({ ...EMPTY, model: models[0]?.id ?? '' })}
              disabled={busy || models.length === 0}
              title={models.length === 0 ? 'create a Model first' : undefined}
            >
              new Agent
            </button>
          </div>
        </div>
        {agents === null ? (
          <p className="empty">loading …</p>
        ) : agents.length === 0 ? (
          <p className="empty">No Agent yet. An Agent is a Model plus a system prompt.</p>
        ) : (
          <div className="rows">
            {agents.map((a) => (
              <div
                key={a.id}
                className={`row ${selectedId === a.id ? 'selected' : ''}`}
                onClick={() => navigate(`/agents/${a.id}`)}
                role="button"
                tabIndex={0}
                onKeyDown={(e) => e.key === 'Enter' && navigate(`/agents/${a.id}`)}
              >
                <div>
                  <div className="title">{a.name}</div>
                  <div className="line small">
                    <span className="mono">{a.id}</span>
                    <span className="pill mono">v{a.version}</span>
                  </div>
                </div>
                <div className="line">
                  {a.modelExists ? (
                    <Ref kind="model" id={a.model} title={modelName(a.model)} />
                  ) : (
                    <span className="pill fail">Model {a.model} missing</span>
                  )}
                  <span className="muted small">{accessLabel(a.tools)}</span>
                </div>
                <div className="line">
                  {a.missingTools.length > 0 ? (
                    <span className="pill fail" title={a.missingTools.join(', ')}>
                      unknown Tool
                    </span>
                  ) : a.modelExists ? (
                    <span className="pill pass">ready</span>
                  ) : (
                    <span className="pill fail">Model missing</span>
                  )}
                </div>
              </div>
            ))}
          </div>
        )}
      </section>

      {shownDetail && displayed && (
        <section className="card kind t-agent">
          <div className="card-head">
            <h2>{shownDetail.agent.name}</h2>
            <div className="versions">
              {shownDetail.versions.map((v) => (
                <button
                  key={v}
                  className={`pill mono btn ${displayed.version === v ? 'accent' : ''}`}
                  onClick={() => showVersion(v)}
                  title={v === shownDetail.agent.version ? 'current version' : 'view this older version'}
                >
                  v{v}
                </button>
              ))}
            </div>
            <span className="sub mono small">{shownDetail.agent.id}</span>
            <div className="actions">
              <button className="btn quiet danger sm" onClick={() => setConfirmDelete(true)} disabled={busy}>
                delete
              </button>
              <button className="btn sm" onClick={() => setDraft(toDraft(shownDetail.agent))} disabled={busy}>
                edit
              </button>
            </div>
          </div>

          <dl className="facts">
            <div>
              <dt>Model</dt>
              <dd className="line">
                <Ref kind="model" id={displayed.model} link={displayed.modelExists} />
                {!displayed.modelExists && <span className="pill fail">missing</span>}
              </dd>
            </div>
            <div>
              <dt>Tools</dt>
              <dd className="line">
                <span>{accessLabel(displayed.tools)}</span>
                {displayed.effectiveTools.map((t) => (
                  <span key={t} className="pill mono">
                    {t}
                  </span>
                ))}
                {displayed.missingTools.map((t) => (
                  <span key={t} className="pill mono fail" title="no registered Tool with this name">
                    {t}
                  </span>
                ))}
              </dd>
            </div>
            <div>
              <dt>Runs</dt>
              <dd className="mono">{shownDetail.runs}</dd>
            </div>
            {displayed.description && (
              <div style={{ gridColumn: '1 / -1' }}>
                <dt>Description</dt>
                <dd>{displayed.description}</dd>
              </div>
            )}
          </dl>

          <VersionChange agent={displayed} prev={view?.prev} />

          {view?.source != null ? (
            <pre className="source mono">{view.source}</pre>
          ) : (
            <button className="link" onClick={showSource}>
              Show file
            </button>
          )}
        </section>
      )}

      {draft && (
        <Modal kind="agent" title={isExisting ? `${draft.name || draft.id} — new version` : 'New Agent'} onClose={() => setDraft(null)}>
          <form
            className="form guided"
            onSubmit={(e) => {
              e.preventDefault()
              save()
            }}
          >
            <GuideStep no="①" title="Who answers?">
              <NameField naming={draft} existing={!!isExisting} placeholder="Rookie" onChange={(n) => setDraft({ ...draft, ...n })} />
              <label className="field">
                <span>Model</span>
                <select value={draft.model} onChange={(e) => setDraft({ ...draft, model: e.target.value })} required>
                  {/* Decision models only judge; they cannot drive an agent. */}
                  {models
                    .filter((m) => m.kind === 'chat' || m.id === draft.model)
                    .map((m) => (
                      <option key={m.id} value={m.id}>
                        {m.name} · {m.modelId}
                        {m.configured ? '' : ' (not usable)'}
                      </option>
                    ))}
                </select>
                <span className="hint">The Model writes the answers. Decision Models only judge, so they are not listed here.</span>
              </label>
            </GuideStep>

            <GuideStep
              no="②"
              title="How should it behave?"
              hint="The system prompt comes before every question: role, rules, tone. In a Training, the Coach rewrites it."
            >
              <label className="field">
                <span>System prompt</span>
                <textarea
                  className="prompt"
                  value={draft.systemPrompt}
                  onChange={(e) => setDraft({ ...draft, systemPrompt: e.target.value })}
                  required
                  placeholder="You are …"
                />
              </label>
            </GuideStep>

            <GuideStep
              no="③"
              title="Which Tools may it use?"
              hint="The Model can only call the Tools it is offered. It knows nothing of a Tool that is not on the list."
            >
              <div className="segment">
                {(['all', 'none', 'list'] as Mode[]).map((m) => (
                  <button key={m} type="button" className={draft.mode === m ? 'on' : ''} onClick={() => setDraft({ ...draft, mode: m })}>
                    {m === 'all' ? 'all' : m === 'none' ? 'none' : 'selection'}
                  </button>
                ))}
              </div>
              {draft.mode === 'list' && (
                <div className="checks">
                  {tools.map((t) => {
                    const on = draft.picked.includes(t.name)
                    return (
                      <label key={t.name} className={`check-pill ${on ? 'on' : ''}`} title={t.description}>
                        <input
                          type="checkbox"
                          checked={on}
                          onChange={(e) =>
                            setDraft({
                              ...draft,
                              picked: e.target.checked ? [...draft.picked, t.name] : draft.picked.filter((n) => n !== t.name),
                            })
                          }
                        />
                        {t.name}
                      </label>
                    )
                  })}
                  {draft.picked
                    .filter((n) => !tools.some((t) => t.name === n))
                    .map((n) => (
                      <label key={n} className="check-pill on" title="not registered — stays on the list">
                        <input type="checkbox" checked onChange={() => setDraft({ ...draft, picked: draft.picked.filter((x) => x !== n) })} />
                        {n} ?
                      </label>
                    ))}
                  {tools.length === 0 && <span className="hint">No Tool registered.</span>}
                </div>
              )}
            </GuideStep>

            <Advanced summary={draft.description ? 'description set' : 'description, for people'}>
              <label className="field">
                <span>Description</span>
                <textarea rows={2} value={draft.description} onChange={(e) => setDraft({ ...draft, description: e.target.value })} />
              </label>
            </Advanced>

            <div className="toolbar end">
              <button type="button" className="btn quiet" onClick={() => setDraft(null)} disabled={busy}>
                cancel
              </button>
              <button type="submit" className="btn primary" disabled={busy}>
                {isExisting ? 'save as new version' : 'create'}
              </button>
            </div>
          </form>
        </Modal>
      )}

      {confirmDelete && shownDetail && (
        <Modal kind="agent" title="Delete Agent?" onClose={() => setConfirmDelete(false)}>
          <p>
            <strong>{shownDetail.agent.name}</strong> <span className="mono muted">({shownDetail.agent.id})</span> moves to
            the archive with all {shownDetail.versions.length} version(s). The id becomes free.
            {shownDetail.runs > 0 && ` ${shownDetail.runs} Run(s) refer to it and keep their id and version.`}
          </p>
          <div className="toolbar end">
            <button className="btn quiet" onClick={() => setConfirmDelete(false)} disabled={busy}>
              cancel
            </button>
            <button className="btn primary" onClick={remove} disabled={busy}>
              archive
            </button>
          </div>
        </Modal>
      )}
    </>
  )
}

/**
 * The prompt of a version as a change against the one before: what changed in a few words
 * (prompt, model, tools), then the prompt line by line with additions and removals marked.
 * The first version has nothing before it and shows its prompt as it is.
 */
function VersionChange({ agent, prev }: { agent: AgentView; prev: AgentView | null | undefined }) {
  if (prev === undefined) {
    return (
      <div className="field">
        <span>System prompt</span>
        <pre className="prose">{agent.systemPrompt}</pre>
      </div>
    )
  }
  if (prev === null) {
    return (
      <div className="field">
        <span>System prompt · first version</span>
        <pre className="prose">{agent.systemPrompt}</pre>
      </div>
    )
  }
  const change = changeOf(agent, prev, [prev])
  return (
    <div className="field version-change">
      <span>System prompt · changes since v{prev.version}</span>
      <span className="line">
        {change.map((c) => (
          <span key={c} className="pill sm">
            {c}
          </span>
        ))}
      </span>
      {agent.systemPrompt === prev.systemPrompt ? (
        <pre className="prose">{agent.systemPrompt}</pre>
      ) : (
        <PromptDiff before={prev.systemPrompt} after={agent.systemPrompt} from={prev.version} to={agent.version} />
      )}
    </div>
  )
}
