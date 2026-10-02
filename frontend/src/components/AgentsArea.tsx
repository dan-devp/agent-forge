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
  if (t === 'all') return 'alle Tools'
  if (t === 'none') return 'keine Tools'
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
  const [view, setView] = useState<{ agent: AgentView; source: string | null } | null>(null)
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
        setView({ agent: d.agent, source: null })
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

  function showVersion(version: number) {
    if (!detail) return
    if (version === detail.agent.version) {
      setView({ agent: detail.agent, source: null })
      return
    }
    Promise.all([agentsApi.version(detail.agent.id, version), agentsApi.source(detail.agent.id, version)])
      .then(([agent, source]) => setView({ agent, source }))
      .catch(fail)
  }

  function showSource() {
    if (!view) return
    agentsApi
      .source(view.agent.id, view.agent.version)
      .then((source) => setView({ agent: view.agent, source }))
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
        setView({ agent: d.agent, source: null })
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
        setView({ agent: d.agent, source: null })
      }
    })

  const isExisting = draft ? agents?.some((a) => a.id === draft.id) : false
  const modelName = (id: string) => models.find((m) => m.id === id)?.name ?? id

  return (
    <>
      <section className="card kind t-agent">
        <div className="card-head">
          <h2>Agents</h2>
          <span className="sub">Ein Model mit Anweisungen und den Tools, die es benutzen darf</span>
          <div className="actions">
            <button className="btn quiet sm" onClick={reload} disabled={busy} title="Dateien neu einlesen">
              neu laden
            </button>
            <button
              className="btn primary sm"
              onClick={() => setDraft({ ...EMPTY, model: models[0]?.id ?? '' })}
              disabled={busy || models.length === 0}
              title={models.length === 0 ? 'zuerst ein Model anlegen' : undefined}
            >
              neuer Agent
            </button>
          </div>
        </div>
        {agents === null ? (
          <p className="empty">lädt …</p>
        ) : agents.length === 0 ? (
          <p className="empty">Noch kein Agent. Ein Agent ist ein Model plus ein Systemprompt.</p>
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
                    <span className="pill fail">Model {a.model} fehlt</span>
                  )}
                  <span className="muted small">{accessLabel(a.tools)}</span>
                </div>
                <div className="line">
                  {a.missingTools.length > 0 ? (
                    <span className="pill fail" title={a.missingTools.join(', ')}>
                      unbekanntes Tool
                    </span>
                  ) : a.modelExists ? (
                    <span className="pill pass">bereit</span>
                  ) : (
                    <span className="pill fail">Model fehlt</span>
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
                  title={v === shownDetail.agent.version ? 'aktuelle Version' : 'ältere Version ansehen'}
                >
                  v{v}
                </button>
              ))}
            </div>
            <span className="sub mono small">{shownDetail.agent.id}</span>
            <div className="actions">
              <button className="btn quiet danger sm" onClick={() => setConfirmDelete(true)} disabled={busy}>
                löschen
              </button>
              <button className="btn sm" onClick={() => setDraft(toDraft(shownDetail.agent))} disabled={busy}>
                bearbeiten
              </button>
            </div>
          </div>

          <dl className="facts">
            <div>
              <dt>Model</dt>
              <dd className="line">
                <Ref kind="model" id={displayed.model} link={displayed.modelExists} />
                {!displayed.modelExists && <span className="pill fail">fehlt</span>}
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
                  <span key={t} className="pill mono fail" title="kein registriertes Tool mit diesem Namen">
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
                <dt>Beschreibung</dt>
                <dd>{displayed.description}</dd>
              </div>
            )}
          </dl>

          <div className="field">
            <span>Systemprompt</span>
            <pre className="prose">{displayed.systemPrompt}</pre>
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
        <Modal kind="agent" title={isExisting ? `${draft.name || draft.id} — neue Version` : 'Neuer Agent'} onClose={() => setDraft(null)}>
          <form
            className="form guided"
            onSubmit={(e) => {
              e.preventDefault()
              save()
            }}
          >
            <GuideStep no="①" title="Wer antwortet?">
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
                        {m.configured ? '' : ' (nicht nutzbar)'}
                      </option>
                    ))}
                </select>
                <span className="hint">Das Model schreibt die Antworten. Decision-Models urteilen nur und fehlen hier deshalb.</span>
              </label>
            </GuideStep>

            <GuideStep
              no="②"
              title="Wie soll er sich verhalten?"
              hint="Der Systemprompt steht vor jeder Frage: Rolle, Regeln, Ton. Ihn schreibt im Training der Coach um."
            >
              <label className="field">
                <span>Systemprompt</span>
                <textarea
                  className="prompt"
                  value={draft.systemPrompt}
                  onChange={(e) => setDraft({ ...draft, systemPrompt: e.target.value })}
                  required
                  placeholder="Du bist …"
                />
              </label>
            </GuideStep>

            <GuideStep
              no="③"
              title="Welche Tools darf er benutzen?"
              hint="Nur angebotene Tools kann das Model aufrufen. Von einem Tool, das nicht auf der Liste steht, weiß es nichts."
            >
              <div className="segment">
                {(['all', 'none', 'list'] as Mode[]).map((m) => (
                  <button key={m} type="button" className={draft.mode === m ? 'on' : ''} onClick={() => setDraft({ ...draft, mode: m })}>
                    {m === 'all' ? 'alle' : m === 'none' ? 'keine' : 'Auswahl'}
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
                      <label key={n} className="check-pill on" title="nicht registriert — bleibt in der Liste">
                        <input type="checkbox" checked onChange={() => setDraft({ ...draft, picked: draft.picked.filter((x) => x !== n) })} />
                        {n} ?
                      </label>
                    ))}
                  {tools.length === 0 && <span className="hint">Kein Tool registriert.</span>}
                </div>
              )}
            </GuideStep>

            <Advanced summary={draft.description ? 'Beschreibung gesetzt' : 'Beschreibung, für Menschen'}>
              <label className="field">
                <span>Beschreibung</span>
                <textarea rows={2} value={draft.description} onChange={(e) => setDraft({ ...draft, description: e.target.value })} />
              </label>
            </Advanced>

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
        <Modal kind="agent" title="Agent löschen?" onClose={() => setConfirmDelete(false)}>
          <p>
            <strong>{shownDetail.agent.name}</strong> <span className="mono muted">({shownDetail.agent.id})</span> wird
            mit allen {shownDetail.versions.length} Version(en) ins Archiv verschoben. Die Id wird frei.
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
