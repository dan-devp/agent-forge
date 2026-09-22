import { useEffect, useState } from 'react'
import { api, type ModelDetail, type ModelRequest, type ModelView, type SecretNames } from '../api/client'
import { Badge } from './Badge'
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
  baseUrl: 'http://host.docker.internal:1234/v1',
  modelId: '',
  apiKeyEnv: null,
  temperature: 0.7,
  local: true,
}

function toDraft(m: ModelView): Draft {
  return {
    id: m.id,
    name: m.name,
    description: m.description ?? '',
    baseUrl: m.baseUrl,
    modelId: m.modelId,
    apiKeyEnv: m.apiKeyEnv,
    temperature: m.temperature,
    local: m.local,
  }
}

/**
 * Models: the list, one selected model with its versions, a form for create and save.
 *
 * Saving never edits a file — it writes the next version. The key is never here: the form
 * offers the names of environment variables, the server says whether the chosen one is set.
 */
export function ModelsPanel(p: Props) {
  const [models, setModels] = useState<ModelView[] | null>(null)
  const [secrets, setSecrets] = useState<SecretNames | null>(null)
  const [selectedId, setSelectedId] = useState<string | null>(null)
  const [detail, setDetail] = useState<ModelDetail | null>(null)
  // Which version of the selected model is displayed, and its file if requested.
  const [view, setView] = useState<{ model: ModelView; source: string | null } | null>(null)
  const [draft, setDraft] = useState<Draft | null>(null)
  const [busy, setBusy] = useState(false)
  const [confirmDelete, setConfirmDelete] = useState(false)

  const fail = (e: Error) => p.onError(e.message)

  const load = () =>
    api
      .models()
      .then((list) => {
        setModels(list)
        if (selectedId && !list.some((m) => m.id === selectedId)) select(null)
      })
      .catch(fail)

  useEffect(() => {
    load()
    api.secrets().then(setSecrets).catch(fail)
  }, []) // eslint-disable-line react-hooks/exhaustive-deps

  /** Selecting loads the detail and shows its current version. */
  function select(id: string | null) {
    setSelectedId(id)
    setDetail(null)
    setView(null)
    if (!id) return
    api
      .model(id)
      .then((d) => {
        setDetail(d)
        setView({ model: d.model, source: null })
      })
      .catch(fail)
  }

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

  const displayed = view?.model ?? null

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
        baseUrl: draft.baseUrl,
        modelId: draft.modelId,
        apiKeyEnv: draft.local ? null : draft.apiKeyEnv,
        temperature: draft.temperature,
        local: draft.local,
      }
      const isNew = !models?.some((m) => m.id === draft.id)
      const saved = isNew ? await api.createModel({ ...body, id: draft.id }) : await api.updateModel(draft.id, body)
      setDraft(null)
      await load()
      select(saved.id)
      p.onChanged()
    })

  const remove = () =>
    perform(async () => {
      if (!detail) return
      await api.deleteModel(detail.model.id)
      setConfirmDelete(false)
      select(null)
      await load()
      p.onChanged()
    })

  const reload = () =>
    perform(async () => {
      setModels(await api.reloadModels())
      if (selectedId) select(selectedId)
    })

  return (
    <>
      <section className="panel">
        <div className="panel-head">
          <h2>Modelle</h2>
          <div className="toolbar">
            <button className="btn ghost sm" onClick={reload} disabled={busy} title="Dateien neu einlesen">
              neu laden
            </button>
            <button className="btn primary sm" onClick={() => setDraft({ ...EMPTY })} disabled={busy}>
              neues Modell
            </button>
          </div>
        </div>
        {models === null ? (
          <div className="panel-body">
            <p className="muted">lädt …</p>
          </div>
        ) : models.length === 0 ? (
          <div className="panel-body">
            <p className="empty">Noch kein Modell. Lege eines an — Endpunkt, Modell-Id, Schlüsselvariable.</p>
          </div>
        ) : (
          <table className="table">
            <thead>
              <tr>
                <th>Modell</th>
                <th>Endpunkt</th>
                <th>Schlüssel</th>
                <th>Status</th>
              </tr>
            </thead>
            <tbody>
              {models.map((m) => (
                <tr
                  key={m.id}
                  className={`clickable ${selectedId === m.id ? 'selected' : ''}`}
                  onClick={() => select(m.id)}
                >
                  <td>
                    <strong>{m.name}</strong>
                    <div className="small faint mono">
                      {m.id} · v{m.version}
                    </div>
                  </td>
                  <td>
                    <div className="mono small">{m.modelId}</div>
                    <div className="small faint">{m.baseUrl}</div>
                  </td>
                  <td className="small">
                    {m.local ? (
                      <span className="faint">nicht nötig</span>
                    ) : m.apiKeyEnv ? (
                      <span className="mono">{m.apiKeyEnv}</span>
                    ) : (
                      <span className="faint">—</span>
                    )}
                  </td>
                  <td>
                    <StatusBadge m={m} />
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </section>

      {detail && (
        <section className="panel">
          <div className="panel-head">
            <div>
              <h2>{detail.model.name}</h2>
              <div className="small faint mono">
                {detail.model.id} · aktuell v{detail.model.version} · {detail.runs} Lauf/Läufe
              </div>
            </div>
            <div className="toolbar">
              <button className="btn ghost sm" onClick={() => setDraft(toDraft(detail.model))} disabled={busy}>
                bearbeiten
              </button>
              <button
                className="btn ghost sm"
                onClick={() => setConfirmDelete(true)}
                disabled={busy || detail.usedByAgents.length > 0}
                title={
                  detail.usedByAgents.length > 0
                    ? `wird von ${detail.usedByAgents.join(', ')} genutzt`
                    : 'ins Archiv verschieben'
                }
              >
                löschen
              </button>
            </div>
          </div>
          <div className="panel-body">
            <div className="tabs">
              {detail.versions.map((v) => (
                <button
                  key={v}
                  className={`tab ${displayed?.version === v ? 'active' : ''}`}
                  onClick={() => showVersion(v)}
                >
                  v{v}
                </button>
              ))}
            </div>
            {displayed && (
              <dl className="facts">
                <div>
                  <dt>Endpunkt</dt>
                  <dd className="mono">{displayed.baseUrl}</dd>
                </div>
                <div>
                  <dt>Modell-Id</dt>
                  <dd className="mono">{displayed.modelId}</dd>
                </div>
                <div>
                  <dt>Temperatur</dt>
                  <dd className="mono">{displayed.temperature}</dd>
                </div>
                <div>
                  <dt>Schlüssel</dt>
                  <dd>
                    {displayed.local ? (
                      <span className="faint">lokal, kein Schlüssel nötig</span>
                    ) : displayed.apiKeyEnv ? (
                      <>
                        <span className="mono">{displayed.apiKeyEnv}</span>{' '}
                        <Badge tone={displayed.keySet ? 'ok' : 'bad'}>{displayed.keySet ? 'gesetzt' : 'fehlt'}</Badge>
                      </>
                    ) : (
                      <Badge tone="bad">keine Variable</Badge>
                    )}
                  </dd>
                </div>
                {displayed.description && (
                  <div>
                    <dt>Beschreibung</dt>
                    <dd>{displayed.description}</dd>
                  </div>
                )}
                {detail.usedByAgents.length > 0 && (
                  <div>
                    <dt>Genutzt von</dt>
                    <dd className="mono">{detail.usedByAgents.join(', ')}</dd>
                  </div>
                )}
              </dl>
            )}
            {displayed && !displayed.configured && <p className="error">{displayed.problem}</p>}
            {view?.source != null ? (
              <pre className="prompt-box mono">{view.source}</pre>
            ) : (
              <button className="link" onClick={showSource}>
                Datei anzeigen
              </button>
            )}
          </div>
        </section>
      )}

      {draft && (
        <Modal
          title={models?.some((m) => m.id === draft.id) ? `${draft.name || draft.id} — neue Version` : 'Neues Modell'}
          onClose={() => setDraft(null)}
        >
          <form
            className="panel-body form"
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
                  disabled={models?.some((m) => m.id === draft.id) && draft.id !== ''}
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
                <span>Basis-URL (inkl. /v1)</span>
                <input
                  value={draft.baseUrl}
                  onChange={(e) => setDraft({ ...draft, baseUrl: e.target.value })}
                  required
                  className="mono"
                />
              </label>
              <label className="field">
                <span>Modell-Id am Endpunkt</span>
                <input
                  value={draft.modelId}
                  onChange={(e) => setDraft({ ...draft, modelId: e.target.value })}
                  required
                  className="mono"
                />
              </label>
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
              <label className="field check span-2">
                <input
                  type="checkbox"
                  checked={draft.local}
                  onChange={(e) => setDraft({ ...draft, local: e.target.checked })}
                />
                <span>lokaler Server — braucht keinen Schlüssel</span>
              </label>
              {!draft.local && (
                <label className="field span-2">
                  <span>Schlüssel aus Umgebungsvariable</span>
                  <select
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
              <button type="button" className="btn ghost" onClick={() => setDraft(null)} disabled={busy}>
                abbrechen
              </button>
              <button type="submit" className="btn primary" disabled={busy}>
                {models?.some((m) => m.id === draft.id) ? 'als neue Version speichern' : 'anlegen'}
              </button>
            </div>
          </form>
        </Modal>
      )}

      {confirmDelete && detail && (
        <Modal title="Modell löschen?" onClose={() => setConfirmDelete(false)}>
          <div className="panel-body">
            <p>
              <strong>{detail.model.name}</strong> <span className="mono faint">({detail.model.id})</span> wird mit
              allen {detail.versions.length} Version(en) ins Archiv verschoben. Die Id wird frei.
              {detail.runs > 0 && ` ${detail.runs} Lauf/Läufe verweisen darauf und behalten Id und Version.`}
            </p>
            <div className="toolbar end">
              <button className="btn ghost" onClick={() => setConfirmDelete(false)} disabled={busy}>
                abbrechen
              </button>
              <button className="btn primary" onClick={remove} disabled={busy}>
                ins Archiv
              </button>
            </div>
          </div>
        </Modal>
      )}
    </>
  )
}

function StatusBadge({ m }: { m: ModelView }) {
  if (!m.configured) return <Badge tone="bad">nicht nutzbar</Badge>
  return <Badge tone={m.local ? 'ok' : 'info'}>{m.local ? 'lokal' : 'bereit'}</Badge>
}
