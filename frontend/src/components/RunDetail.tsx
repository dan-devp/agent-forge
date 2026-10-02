import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router'
import { runsApi, type Run, type RunStep } from '../api/client'

type Props = { onError: (message: string) => void }

const STATUS: Record<Run['status'], { text: string; tone: string }> = {
  QUEUED: { text: 'wartet', tone: '' },
  RUNNING: { text: 'läuft', tone: 'open' },
  DONE: { text: 'fertig', tone: 'pass' },
  BUDGET_EXCEEDED: { text: 'Budget erschöpft', tone: 'fail' },
  FAILED: { text: 'fehlgeschlagen', tone: 'fail' },
  CANCELLED: { text: 'abgebrochen', tone: '' },
}

function fmtMs(ms: number | null) {
  if (ms === null) return '–'
  return ms < 1000 ? `${ms} ms` : `${(ms / 1000).toFixed(1)} s`
}

function fmtTime(iso: string | null) {
  if (!iso) return '–'
  return new Date(iso).toLocaleString('de-DE')
}

/**
 * One run, top to bottom: who and what, the verdicts, the answer, the trace, and folded
 * away what the model saw. Polls while the run is still open.
 */
export function RunDetail(p: Props) {
  const { id } = useParams()
  const [run, setRun] = useState<Run | null>(null)
  const [showPrompt, setShowPrompt] = useState(false)

  useEffect(() => {
    if (!id) return
    let stale = false
    let timer: number | null = null
    const load = () =>
      runsApi
        .get(id)
        .then((r) => {
          if (stale) return
          setRun(r)
          if (r.status === 'QUEUED' || r.status === 'RUNNING') timer = window.setTimeout(load, 2000)
        })
        .catch((e: Error) => {
          if (!stale) p.onError(e.message)
        })
    load()
    return () => {
      stale = true
      if (timer) window.clearTimeout(timer)
    }
  }, [id]) // eslint-disable-line react-hooks/exhaustive-deps

  if (!run) {
    return (
      <section className="card">
        <p className="empty">lädt …</p>
      </section>
    )
  }

  const status = STATUS[run.status]
  const evalsPassed = run.evals.filter((e) => e.passed).length
  const judgeTokens = run.evals.reduce((s, e) => s + (e.tokens ?? 0), 0)

  return (
    <>
      <section className="card">
        <div className="card-head">
          <h2>{run.taskId ? <span className="mono">{run.taskId}</span> : 'Freie Eingabe'}</h2>
          {run.taskId && run.taskVersion !== null && <span className="pill mono">Task v{run.taskVersion}</span>}
          <span className={`pill ${status.tone}`}>{status.text}</span>
          {run.passed !== null && (
            <span className={`pill ${run.passed ? 'pass' : 'fail'}`}>
              {run.passed ? 'bestanden' : 'nicht bestanden'} · {evalsPassed}/{run.evals.length}
            </span>
          )}
          <div className="actions">
            <Link to={`/runs?agent=${run.agentId}`} className="btn quiet sm">
              zur Matrix
            </Link>
          </div>
        </div>

        <dl className="facts">
          <div>
            <dt>Agent</dt>
            <dd className="line">
              <Link to={`/agents/${run.agentId}`} className="mono">
                {run.agentId}
              </Link>
              <span className="pill mono">v{run.agentVersion}</span>
            </dd>
          </div>
          <div>
            <dt>Model</dt>
            <dd className="line">
              <Link to={`/models/${run.modelId}`} className="mono">
                {run.modelId}
              </Link>
              <span className="pill mono">v{run.modelVersion}</span>
              {run.modelName && <span className="faint small mono">{run.modelName}</span>}
            </dd>
          </div>
          <div>
            <dt>Tokens</dt>
            <dd className="mono">
              {run.totalTokens ?? '–'}
              {judgeTokens > 0 && <span className="faint"> + {judgeTokens} Judge</span>}
            </dd>
          </div>
          <div>
            <dt>Dauer</dt>
            <dd className="mono">{fmtMs(run.durationMs)}</dd>
          </div>
          <div>
            <dt>Gestartet</dt>
            <dd className="mono small">{fmtTime(run.startedAt)}</dd>
          </div>
          <div>
            <dt>Tools angeboten</dt>
            <dd className="line">
              {run.tools.length === 0 ? <span className="muted">keine</span> : run.tools.map((t) => <span key={t} className="pill mono">{t}</span>)}
            </dd>
          </div>
          <div>
            <dt>Temperatur</dt>
            <dd className="mono">{run.temperature}</dd>
          </div>
          {run.batchId && (
            <div>
              <dt>Batch</dt>
              <dd className="mono small">{run.batchId}</dd>
            </div>
          )}
        </dl>

        {run.error && <p className="error">{run.error}</p>}
      </section>

      {run.evals.length > 0 && (
        <section className="card">
          <div className="card-head">
            <h2>Urteile</h2>
            <span className="sub">
              {evalsPassed} von {run.evals.length} bestanden
            </span>
          </div>
          <div className="eval-list">
            {run.evals.map((e, i) => (
              <div key={i} className="eval-row">
                <span className="mono muted">{e.index}</span>
                <span className={`pill ${e.passed ? 'pass' : 'fail'}`}>{e.passed ? '✓' : '✗'}</span>
                <span className="pill mono">{e.type}</span>
                <span style={{ flex: 1 }}>
                  {e.subject && <b>{e.subject} </b>}
                  {e.rationale}
                </span>
                {/* An eval with several verdicts books its one call on the first. */}
                {e.model && (e.tokens !== null || e.durationMs !== null) && (
                  <span className="kv">
                    {e.model} · {e.tokens ?? '?'} tok · {fmtMs(e.durationMs)}
                  </span>
                )}
              </div>
            ))}
          </div>
        </section>
      )}

      <section className="card">
        <div className="card-head">
          <h2>Antwort</h2>
        </div>
        {run.answer ? <pre className="prose answer">{run.answer}</pre> : <p className="empty">Keine Antwort.</p>}
      </section>

      <section className="card">
        <div className="card-head">
          <h2>Trace</h2>
          <span className="sub">
            {run.trace.length} Schritt{run.trace.length === 1 ? '' : 'e'}
          </span>
        </div>
        {run.trace.length === 0 ? (
          <p className="empty">Kein Schritt aufgezeichnet.</p>
        ) : (
          <div className="steps">
            {run.trace.map((s) => (
              <Step key={s.seq} step={s} />
            ))}
          </div>
        )}
      </section>

      <section className="card">
        <div className="card-head">
          <h2>Was das Model sah</h2>
          <div className="actions">
            <button className="btn quiet sm" onClick={() => setShowPrompt(!showPrompt)}>
              {showPrompt ? 'einklappen' : 'anzeigen'}
            </button>
          </div>
        </div>
        {showPrompt && (
          <>
            <div className="field">
              <span>Systemprompt</span>
              <pre className="prose">{run.systemPrompt}</pre>
            </div>
            <div className="field">
              <span>Eingabe</span>
              <pre className="prose">{run.input}</pre>
            </div>
          </>
        )}
      </section>
    </>
  )
}

function Step({ step }: { step: RunStep }) {
  const isTool = step.type === 'TOOL'
  return (
    <div className={`step ${isTool ? 'tool' : 'model'} ${step.error ? 'errored' : ''}`}>
      <div className="step-head">
        <span className="mono muted">{step.seq}</span>
        <span className={`pill mono ${isTool ? 'info' : ''}`}>{step.type}</span>
        <span className="mono">{step.name}</span>
        <span className="spacer" />
        {step.tokens !== null && <span className="kv">{step.tokens} tok</span>}
        {step.durationMs !== null && <span className="kv">{fmtMs(step.durationMs)}</span>}
      </div>
      {isTool ? (
        <>
          {step.input && (
            <div className="step-block">
              <span className="lbl">Argumente</span>
              <code>{step.input}</code>
            </div>
          )}
          {step.output && (
            <div className="step-block">
              <span className="lbl">Ergebnis</span>
              <pre>{step.output}</pre>
            </div>
          )}
        </>
      ) : (
        <>
          {step.input && (
            <div className="step-block">
              <span className="lbl">Text</span>
              <pre>{step.input}</pre>
            </div>
          )}
          {step.output && (
            <div className="step-block">
              <pre>{step.output}</pre>
            </div>
          )}
        </>
      )}
      {step.error && <p className="error">{step.error}</p>}
    </div>
  )
}
