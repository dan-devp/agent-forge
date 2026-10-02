import { useEffect, useState } from 'react'
import { useNavigate, useParams } from 'react-router'
import {
  api,
  type ModelDetail,
  type ModelRequest,
  type ModelView,
  type ProviderInfo,
  type ProviderKey,
  type SecretNames,
} from '../api/client'
import { Modal } from './Modal'

type Props = {
  onError: (message: string) => void
  onChanged: () => void
}

type Draft = ModelRequest & { id: string }

const EMPTY: Draft = {
  id: '',
  name: '',
  description: '',
  provider: 'openai',
  baseUrl: null,
  modelId: '',
  apiKeyEnv: 'API_KEY_OPENAI',
  temperature: 0.7,
}

const LOCAL_URL = 'http://host.docker.internal:1234/v1'

function toDraft(m: ModelView): Draft {
  return {
    id: m.id,
    name: m.name,
    description: m.description ?? '',
    provider: m.provider,
    baseUrl: m.baseUrl,
    modelId: m.modelId,
    apiKeyEnv: m.apiKeyEnv,
    temperature: m.temperature,
  }
}

/** Switching the provider fills in what it fixes and clears what it does not need. */
function withProvider(d: Draft, key: ProviderKey, providers: ProviderInfo[]): Draft {
  const p = providers.find((x) => x.key === key)
  return {
    ...d,
    provider: key,
    baseUrl: p?.fixedBaseUrl ?? (key === 'local' ? LOCAL_URL : ''),
    apiKeyEnv: p?.local ? null : p?.defaultKeyEnv ?? d.apiKeyEnv,
  }
}

/**
 * Models: the list as rows, the selected model (by URL) with its versions, a form for
 * create and save. Saving never edits a file — it writes the next version. The key is
 * never here: the form offers the names of environment variables, the server says whether
 * the chosen one is set.
 */
export function ModelsArea(p: Props) {
  const { id: selectedId } = useParams()
  const navigate = useNavigate()

  const [models, setModels] = useState<ModelView[] | null>(null)
  const [secrets, setSecrets] = useState<SecretNames | null>(null)
  const [providers, setProviders] = useState<ProviderInfo[]>([])
  const [detail, setDetail] = useState<ModelDetail | null>(null)
  // Which version of the selected model is shown, and its file if requested.
  const [view, setView] = useState<{ model: ModelView; source: string | null } | null>(null)
  const [draft, setDraft] = useState<Draft | null>(null)
  const [busy, setBusy] = useState(false)
  const [confirmDelete, setConfirmDelete] = useState(false)

  const fail = (e: Error) => p.onError(e.message)

  const loadList = () => api.models().then(setModels).catch(fail)

  useEffect(() => {
    loadList()
    api.secrets().then(setSecrets).catch(fail)
    api.providers().then(setProviders).catch(fail)
  }, []) // eslint-disable-line react-hooks/exhaustive-deps

  // The URL decides what is selected; loading follows it.
  useEffect(() => {
    if (!selectedId) return
    let stale = false
    api
      .model(selectedId)
      .then((d) => {
        if (stale) return
        setDetail(d)
        setView({ model: d.model, source: null })
      })
      .catch((e: Error) => {
        if (stale) return
        fail(e)
        navigate('/models', { replace: true })
      })
    return () => {
      stale = true
    }
  }, [selectedId]) // eslint-disable-line react-hooks/exhaustive-deps

  const shownDetail = selectedId ? detail : null
  const displayed = selectedId ? view?.model ?? null : null

  function showVersion(version: number) {
    if (!detail) return
    if (version === detail.model.version) {
      setView({ model: detail.model, source: null })
      return
    }
    Promise.all([api.modelVersion(detail.model.id, version), api.modelSource(detail.model.id, version)])
      .then(([model, source]) => setView({ model, source }))
      .catch(fail)
  }

  function showSource() {
    if (!view) return
    api
      .modelSource(view.model.id, view.model.version)
      .then((source) => setView({ model: view.model, source }))
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
      const body: ModelRequest = {
        name: draft.name,
        description: draft.description || null,
        provider: draft.provider,
        baseUrl: draft.baseUrl || null,
        modelId: draft.modelId,
        apiKeyEnv: draft.provider === 'local' ? null : draft.apiKeyEnv,
        temperature: draft.temperature,
      }
      const isNew = !models?.some((m) => m.id === draft.id)
      const saved = isNew ? await api.createModel({ ...body, id: draft.id }) : await api.updateModel(draft.id, body)
      setDraft(null)
      await loadList()
      p.onChanged()
      if (saved.id === selectedId) {
        const d = await api.model(saved.id)
        setDetail(d)
        setView({ model: d.model, source: null })
      } else {
        navigate(`/models/${saved.id}`)
      }
    })

  const remove = () =>
    perform(async () => {
      if (!detail) return
      await api.deleteModel(detail.model.id)
      setConfirmDelete(false)
      setDetail(null)
      setView(null)
      await loadList()
      p.onChanged()
      navigate('/models')
    })

  const reload = () =>
    perform(async () => {
      setModels(await api.reloadModels())
      if (selectedId) {
        const d = await api.model(selectedId)
        setDetail(d)
        setView({ model: d.model, source: null })
      }
    })

  const isExisting = draft ? models?.some((m) => m.id === draft.id) : false

  return (
    <>
      <section className="card">
        <div className="card-head">
          <h2>Models</h2>
          <span className="sub">Endpunkt und Model-Id, Schlüssel nur als Variablenname</span>
          <div className="actions">
            <button className="btn quiet sm" onClick={reload} disabled={busy} title="Dateien neu einlesen">
              neu laden
            </button>
            <button className="btn primary sm" onClick={() => setDraft({ ...EMPTY })} disabled={busy}>
              neues Model
            </button>
          </div>
        </div>
        {models === null ? (
          <p className="empty">lädt …</p>
        ) : models.length === 0 ? (
          <p className="empty">Noch kein Model. Lege eines an — Endpunkt, Model-Id, Schlüsselvariable.</p>
        ) : (
          <div className="rows">
            {models.map((m) => (
              <div
                key={m.id}
                className={`row ${selectedId === m.id ? 'selected' : ''}`}
                onClick={() => navigate(`/models/${m.id}`)}
                role="button"
                tabIndex={0}
                onKeyDown={(e) => e.key === 'Enter' && navigate(`/models/${m.id}`)}
              >
                <div>
                  <div className="title">{m.name}</div>
                  <div className="line small">
                    <span className="mono">{m.id}</span>
                    <span className="pill mono">v{m.version}</span>
                  </div>
                </div>
                <div className="line">
                  <span className="pill">{providerLabel(m.provider, providers)}</span>
                  {m.kind === 'decision' && <span className="pill info">Decision</span>}
                  <span className="mono">{m.modelId}</span>
                  {!isCloud(m.provider) && <span className="faint small">{m.baseUrl}</span>}
                </div>
                <div className="line">
                  <StatusPill m={m} />
                </div>
              </div>
            ))}
          </div>
        )}
      </section>

      {shownDetail && displayed && (
        <section className="card">
          <div className="card-head">
            <h2>{shownDetail.model.name}</h2>
            <div className="versions">
              {shownDetail.versions.map((v) => (
                <button
                  key={v}
                  className={`pill mono btn ${displayed.version === v ? 'accent' : ''}`}
                  onClick={() => showVersion(v)}
                  title={v === shownDetail.model.version ? 'aktuelle Version' : 'ältere Version ansehen'}
                >
                  v{v}
                </button>
              ))}
            </div>
            <span className="sub mono small">{shownDetail.model.id}</span>
            <div className="actions">
              <button className="btn sm" onClick={() => setDraft(toDraft(shownDetail.model))} disabled={busy}>
                bearbeiten
              </button>
              <button
                className="btn quiet danger sm"
                onClick={() => setConfirmDelete(true)}
                disabled={busy || shownDetail.usedByAgents.length > 0}
                title={
                  shownDetail.usedByAgents.length > 0
                    ? `wird von ${shownDetail.usedByAgents.join(', ')} genutzt`
                    : 'ins Archiv verschieben'
                }
              >
                löschen
              </button>
            </div>
          </div>

          <dl className="facts">
            <div>
              <dt>Provider</dt>
              <dd>{providerLabel(displayed.provider, providers)}</dd>
            </div>
            <div>
              <dt>Endpunkt</dt>
              <dd className="mono">{displayed.baseUrl}</dd>
            </div>
            <div>
              <dt>Model-Id</dt>
              <dd className="mono">{displayed.modelId}</dd>
            </div>
            <div>
              <dt>Temperatur</dt>
              <dd className="mono">{displayed.temperature}</dd>
            </div>
            <div>
              <dt>Schlüssel</dt>
              <dd className="line">
                {displayed.local ? (
                  <span className="muted">lokal, keiner nötig</span>
                ) : displayed.apiKeyEnv ? (
                  <>
                    <span className="mono">{displayed.apiKeyEnv}</span>
                    <span className={`pill ${displayed.keySet ? 'pass' : 'fail'}`}>
                      {displayed.keySet ? 'gesetzt' : 'fehlt'}
                    </span>
                  </>
                ) : (
                  <span className="pill fail">keine Variable</span>
                )}
              </dd>
            </div>
            <div>
              <dt>Runs</dt>
              <dd className="mono">{shownDetail.runs}</dd>
            </div>
            {shownDetail.usedByAgents.length > 0 && (
              <div>
                <dt>Genutzt von</dt>
                <dd className="mono">{shownDetail.usedByAgents.join(', ')}</dd>
              </div>
            )}
            {displayed.description && (
              <div style={{ gridColumn: '1 / -1' }}>
                <dt>Beschreibung</dt>
                <dd>{displayed.description}</dd>
              </div>
            )}
          </dl>

          {!displayed.configured && <p className="error">{displayed.problem}</p>}
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
        <Modal title={isExisting ? `${draft.name || draft.id} — neue Version` : 'Neues Model'} onClose={() => setDraft(null)}>
          <form
            className="form"
            onSubmit={(e) => {
              e.preventDefault()
              save()
            }}
          >
            <div className="form-grid">
              <label className="field span-2">
                <span>Provider</span>
                <select
                  value={draft.provider}
                  onChange={(e) => setDraft(withProvider(draft, e.target.value as ProviderKey, providers))}
                >
                  {providers.map((p) => (
                    <option key={p.key} value={p.key}>
                      {p.label}
                    </option>
                  ))}
                </select>
                <span className="hint">
                  {draft.provider === 'anthropic' && 'Über den OpenAI-kompatiblen Endpunkt von Anthropic. Chat und Tools gehen, Anthropic-eigene Funktionen nicht.'}
                  {draft.provider === 'custom' && 'Gateway (LiteLLM, OpenRouter, …) oder eigener Server mit Schlüssel.'}
                  {draft.provider === 'local' && 'LM Studio, Ollama, vLLM oder der Mock. Aus dem Container heißt der Host host.docker.internal.'}
                  {draft.provider === 'typesafe' &&
                    'Decision-Model (Jev): beantwortet Ja/Nein-Fragen mit einem Wert zwischen 0 und 1, schreibt keinen Text. Nur als Judge in judge-questions, nicht für Agents. Model-Id z. B. jev-latest.'}
                </span>
              </label>
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
              {!isCloud(draft.provider) && (
                <label className="field span-2">
                  <span>Basis-URL (inkl. /v1)</span>
                  <input
                    value={draft.baseUrl ?? ''}
                    onChange={(e) => setDraft({ ...draft, baseUrl: e.target.value })}
                    required
                    className="mono"
                  />
                </label>
              )}
              <label className="field">
                <span>Model-Id am Endpunkt</span>
                <input
                  value={draft.modelId}
                  onChange={(e) => setDraft({ ...draft, modelId: e.target.value })}
                  required
                  className="mono"
                />
              </label>
              {draft.provider !== 'typesafe' && (
              <label className="field">
                <span>Temperatur</span>
                <input
                  type="number"
                  step="0.1"
                  min="0"
                  max="2"
                  value={draft.temperature}
                  onChange={(e) => setDraft({ ...draft, temperature: Number(e.target.value) })}
                />
              </label>
              )}
              {draft.provider !== 'local' && (
                <label className="field span-2">
                  <span>Schlüssel aus Umgebungsvariable</span>
                  <select
                    className="mono"
                    value={draft.apiKeyEnv ?? ''}
                    onChange={(e) => setDraft({ ...draft, apiKeyEnv: e.target.value || null })}
                    required
                  >
                    <option value="">— wählen —</option>
                    {secrets?.names.map((n) => (
                      <option key={n} value={n}>
                        {n}
                      </option>
                    ))}
                    {draft.apiKeyEnv && !secrets?.names.includes(draft.apiKeyEnv) && (
                      <option value={draft.apiKeyEnv}>{draft.apiKeyEnv} (nicht gesetzt)</option>
                    )}
                  </select>
                  <span className="hint">
                    Nur der Name landet in der Datei. Angeboten werden Variablen mit Präfix{' '}
                    <code>{secrets?.prefix ?? 'API_KEY_'}</code> aus der Umgebung des Backends.
                    {secrets && secrets.names.length === 0 && ' Derzeit ist keine gesetzt.'}
                  </span>
                </label>
              )}
              <label className="field span-2">
                <span>Beschreibung</span>
                <textarea
                  rows={2}
                  value={draft.description ?? ''}
                  onChange={(e) => setDraft({ ...draft, description: e.target.value })}
                />
              </label>
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
        <Modal title="Model löschen?" onClose={() => setConfirmDelete(false)}>
          <p>
            <strong>{shownDetail.model.name}</strong> <span className="mono muted">({shownDetail.model.id})</span> wird
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

function StatusPill({ m }: { m: ModelView }) {
  if (!m.configured) return <span className="pill fail">nicht nutzbar</span>
  return <span className={`pill ${m.local ? 'open' : 'pass'}`}>{m.local ? 'lokal' : 'bereit'}</span>
}

function isCloud(key: ProviderKey) {
  return key === 'openai' || key === 'anthropic' || key === 'typesafe'
}

function providerLabel(key: ProviderKey, providers: ProviderInfo[]) {
  return providers.find((p) => p.key === key)?.label ?? key
}
