import { useEffect, useState } from 'react'
import { catalogApi, type EvalParam, type EvalType } from '../api/client'
import { EXAMPLES, groupedTypes } from './evalGuide'

type Props = { onError: (message: string) => void }

const PARAM_TYPE: Record<EvalParam['type'], string> = {
  STRING: 'Text',
  TEXT: 'Text, multiple lines',
  NUMBER: 'Number',
  BOOLEAN: 'yes/no',
  MODEL: 'Chat Model',
  JUDGE_MODEL: 'Chat or Decision Model',
  LIST: 'List, one line per entry',
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
        <span className="sub">Each check says passed or failed. You pick and set it up in the Task.</span>
        <div className="actions">
          <span className="pill ghost">from the system</span>
        </div>
      </div>
      {evals === null ? (
        <p className="empty">loading …</p>
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
            Runs without an answer (budget, error) fail every text Eval, the negated ones too: whoever delivered nothing did nothing right.
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
      {EXAMPLES[e.type] && <p className="small choice-example">e.g. {EXAMPLES[e.type]}</p>}
      {e.params.length === 0 ? (
        <span className="hint">no settings</span>
      ) : (
        <details className="params-fold">
          <summary>
            {e.params.length} setting{e.params.length === 1 ? '' : 's'}: {e.params.map((pm) => pm.label).join(', ')}
          </summary>
          <div className="params">
            {e.params.map((pm) => (
              <div key={pm.name} className="param">
                <span className="name">{pm.name}</span>
                <span className="meta">
                  <span className="pill ghost">{PARAM_TYPE[pm.type]}</span>
                  {pm.required ? (
                    <span className="pill">required</span>
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
