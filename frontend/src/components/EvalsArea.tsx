import { useEffect, useState } from 'react'
import { catalogApi, type EvalParam, type EvalType } from '../api/client'

type Props = { onError: (message: string) => void }

/** Grouping is display only: the server knows types, the reader wants neighbourhoods. */
const GROUPS: { title: string; types: string[] }[] = [
  { title: 'Antworttext', types: ['contains', 'not-contains', 'equals', 'regex', 'not-regex', 'length'] },
  { title: 'Tools im Trace', types: ['tool-called', 'tool-not-called', 'max-tool-calls'] },
  { title: 'JSON', types: ['json-valid', 'json-path'] },
  { title: 'Mit Judge-Model', types: ['llm-judge'] },
]

const PARAM_TYPE: Record<EvalParam['type'], string> = {
  STRING: 'Text',
  TEXT: 'Text, mehrzeilig',
  NUMBER: 'Zahl',
  BOOLEAN: 'ja/nein',
  MODEL: 'Model',
  TOOL: 'Tool',
}

/**
 * The eval catalogue: what the system can check, and what each check takes. Read-only —
 * an eval is code; configuring one happens inside a task.
 */
export function EvalsArea(p: Props) {
  const [evals, setEvals] = useState<EvalType[] | null>(null)

  useEffect(() => {
    catalogApi
      .evals()
      .then(setEvals)
      .catch((e: Error) => p.onError(e.message))
  }, []) // eslint-disable-line react-hooks/exhaustive-deps

  const grouped = GROUPS.map((g) => ({
    ...g,
    items: (evals ?? []).filter((e) => g.types.includes(e.type)),
  }))
  const rest = (evals ?? []).filter((e) => !GROUPS.some((g) => g.types.includes(e.type)))

  return (
    <section className="card">
      <div className="card-head">
        <h2>Evals</h2>
        <span className="sub">Jedes Eval sagt bestanden oder nicht. Konfiguriert wird es im Task.</span>
        <div className="actions">
          <span className="pill ghost">vom System</span>
        </div>
      </div>
      {evals === null ? (
        <p className="empty">lädt …</p>
      ) : (
        <>
          {[...grouped, { title: 'Weitere', types: [], items: rest }]
            .filter((g) => g.items.length > 0)
            .map((g) => (
              <div key={g.title} className="form">
                <div className="group-title">{g.title}</div>
                <div className="catalog">
                  {g.items.map((e) => (
                    <Entry key={e.type} eval={e} />
                  ))}
                </div>
              </div>
            ))}
          <p className="hint">
            Runs ohne Antwort (Budget, Fehler) scheitern an allen Text-Evals, auch an den verneinten: wer nichts
            geliefert hat, hat nichts richtig gemacht.
          </p>
        </>
      )}
    </section>
  )
}

function Entry({ eval: e }: { eval: EvalType }) {
  return (
    <article className="entry">
      <div className="entry-head">
        <h3>{e.label}</h3>
        <span className="pill mono">{e.type}</span>
      </div>
      <p className="desc">{e.description}</p>
      {e.params.length === 0 ? (
        <span className="hint">keine Parameter</span>
      ) : (
        <div className="params">
          {e.params.map((pm) => (
            <div key={pm.name} className="param">
              <span className="name">{pm.name}</span>
              <span className="meta">
                <span className="pill ghost">{PARAM_TYPE[pm.type]}</span>
                {pm.required ? (
                  <span className="pill accent">Pflicht</span>
                ) : pm.defaultValue !== null && pm.defaultValue !== undefined ? (
                  <span className="pill mono">= {String(pm.defaultValue)}</span>
                ) : (
                  <span className="pill">optional</span>
                )}
              </span>
              <span className="help">
                {pm.label}. {pm.description}
              </span>
            </div>
          ))}
        </div>
      )}
    </article>
  )
}
