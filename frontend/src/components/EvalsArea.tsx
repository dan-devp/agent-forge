import { useEffect, useState } from 'react'
import { catalogApi, type EvalParam, type EvalType } from '../api/client'
import { EXAMPLES, groupedTypes } from './evalGuide'

type Props = { onError: (message: string) => void }

const PARAM_TYPE: Record<EvalParam['type'], string> = {
  STRING: 'Text',
  TEXT: 'Text, mehrzeilig',
  NUMBER: 'Zahl',
  BOOLEAN: 'ja/nein',
  MODEL: 'Chat-Model',
  JUDGE_MODEL: 'Chat- oder Decision-Model',
  LIST: 'Liste, eine Zeile je Eintrag',
  TOOL: 'Tool',
}

/**
 * The eval catalogue: what the system can check, in the same groups as the task editor, each
 * with an example. The parameters wait behind a toggle — configuring happens inside a task.
 */
export function EvalsArea(p: Props) {
  const [evals, setEvals] = useState<EvalType[] | null>(null)

  useEffect(() => {
    catalogApi
      .evals()
      .then(setEvals)
      .catch((e: Error) => p.onError(e.message))
  }, []) // eslint-disable-line react-hooks/exhaustive-deps

  return (
    <section className="card kind t-catalog">
      <div className="card-head">
        <h2>Evals</h2>
        <span className="sub">Jede Prüfung sagt bestanden oder nicht. Ausgewählt und eingestellt wird sie im Task.</span>
        <div className="actions">
          <span className="pill ghost">vom System</span>
        </div>
      </div>
      {evals === null ? (
        <p className="empty">lädt …</p>
      ) : (
        <>
          {groupedTypes(evals)
            .filter((g) => g.types.length > 0)
            .map(({ group, types }) => (
              <div key={group.id} className="form">
                <div>
                  <div className="group-title">{group.label}</div>
                  <p className="hint">{group.hint}</p>
                </div>
                <div className="catalog">
                  {types.map((e) => (
                    <Entry key={e.type} eval={e} />
                  ))}
                </div>
              </div>
            ))}
          <p className="hint">
            Runs ohne Antwort (Budget, Fehler) scheitern an allen Text-Evals, auch an den verneinten: wer nichts geliefert hat, hat nichts richtig
            gemacht.
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
      {EXAMPLES[e.type] && <p className="small choice-example">z. B. {EXAMPLES[e.type]}</p>}
      {e.params.length === 0 ? (
        <span className="hint">keine Einstellungen</span>
      ) : (
        <details className="params-fold">
          <summary>
            {e.params.length} Einstellung{e.params.length === 1 ? '' : 'en'}: {e.params.map((pm) => pm.label).join(', ')}
          </summary>
          <div className="params">
            {e.params.map((pm) => (
              <div key={pm.name} className="param">
                <span className="name">{pm.name}</span>
                <span className="meta">
                  <span className="pill ghost">{PARAM_TYPE[pm.type]}</span>
                  {pm.required ? (
                    <span className="pill">Pflicht</span>
                  ) : pm.defaultValue !== null && pm.defaultValue !== undefined ? (
                    <span className="pill mono">= {String(pm.defaultValue)}</span>
                  ) : (
                    <span className="pill ghost">optional</span>
                  )}
                </span>
                <span className="help">
                  {pm.label}. {pm.description}
                </span>
              </div>
            ))}
          </div>
        </details>
      )}
    </article>
  )
}
