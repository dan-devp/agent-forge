import { useEffect, useState } from 'react'
import { Link, useLocation, useParams } from 'react-router'
import { catalogApi, runsApi, type EvalType, type Run, type RunStep } from '../api/client'
import { Fold } from './forms'
import { Ref } from './Ref'

type Props = { onError: (message: string) => void }

const STATUS: Record<Run['status'], { text: string; tone: string }> = {
  QUEUED: { text: 'wartet', tone: '' },
  RUNNING: { text: 'läuft', tone: 'open' },
  DONE: { text: 'fertig', tone: '' },
  BUDGET_EXCEEDED: { text: 'Budget überschritten', tone: 'fail' },
  FAILED: { text: 'fehlgeschlagen', tone: 'fail' },
  CANCELLED: { text: 'abgebrochen', tone: '' },
}

/** The model asks for tools in one round; the trace stores that round under this prefix. */
const TOOL_REQUEST = 'requests tools: '

function fmtMs(ms: number | null) {
  if (ms === null) return '–'
  return ms < 1000 ? `${ms} ms` : `${(ms / 1000).toFixed(1).replace('.', ',')} s`
}

function fmtTime(iso: string | null) {
  if (!iso) return '–'
  return new Date(iso).toLocaleString('de-DE')
}

/** The answer to "did it pass, and if not, why?" in one sentence, coloured by the outcome. */
function verdict(run: Run): { tone: string; title: string; detail: string | null } {
  const failed = run.evals.filter((e) => !e.passed)
  const firstReason = failed[0] ? [failed[0].subject, failed[0].rationale].filter(Boolean).join(': ') : null
  const more = failed.length > 1 ? ` (und ${failed.length - 1} weitere Prüfung${failed.length > 2 ? 'en' : ''})` : ''
  if (run.status === 'QUEUED') return { tone: '', title: 'Wartet in der Warteschlange', detail: 'Runs laufen nacheinander. Die Seite aktualisiert sich.' }
  if (run.status === 'RUNNING') return { tone: 'open', title: 'Läuft gerade', detail: 'Die Seite aktualisiert sich.' }
  if (run.status === 'CANCELLED') return { tone: '', title: 'Abgebrochen', detail: 'Der Run wurde gestoppt, bevor er fertig war.' }
  if (run.status === 'FAILED') return { tone: 'fail', title: 'Fehlgeschlagen', detail: run.error ?? 'Der Aufruf ist gescheitert.' }
  if (run.status === 'BUDGET_EXCEEDED')
    return { tone: 'fail', title: 'Budget überschritten', detail: 'Der Agent hat mehr Tools aufgerufen, als der Task erlaubt.' }
  if (run.passed === null) return { tone: '', title: 'Fertig', detail: 'Freie Eingabe ohne Prüfung: hier gibt es kein Urteil, nur die Antwort.' }
  if (run.passed) {
    const n = run.evals.length
    return { tone: 'pass', title: 'Bestanden', detail: n === 1 ? 'Die eine Prüfung hält.' : `Alle ${n} Prüfungen halten.` }
  }
  return { tone: 'fail', title: 'Nicht bestanden', detail: firstReason ? firstReason + more : null }
}

/**
 * One run, in the order of the questions one has: did it pass and why not, what did it
 * answer, what did each check say, how did it get there. What only matters when digging —
 * the prompt the model saw, temperature, batch — is folded away. Polls while open.
 */
export function RunDetail(p: Props) {
  const { id } = useParams()
  // Opened from a session step, the run knows the way back there.
  const back = useLocation().state as { from?: string; label?: string } | null
  const [run, setRun] = useState<Run | null>(null)
  const [types, setTypes] = useState<EvalType[]>([])

  useEffect(() => {
    catalogApi
      .evals()
      .then(setTypes)
      .catch(() => setTypes([]))
  }, [])

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
      <section className="card kind t-run">
        <p className="empty">lädt …</p>
      </section>
    )
  }

  const status = STATUS[run.status]
  const v = verdict(run)
  const evalsPassed = run.evals.filter((e) => e.passed).length
  const judgeTokens = run.evals.reduce((s, e) => s + (e.tokens ?? 0), 0)
  const label = (type: string) => types.find((t) => t.type === type)?.label ?? type
  const answer = run.answer?.trim() ?? ''

  return (
    <>
      {back?.from && (
        <nav className="back">
          <Link to={back.from} className="link">
            ← {back.label ?? 'zurück'}
          </Link>
        </nav>
      )}
      <section className="card kind t-run">
        <div className="card-head">
          <h2>Run</h2>
          {run.taskId ? <Ref kind="task" id={run.taskId} version={run.taskVersion} /> : <span className="muted">freie Eingabe</span>}
          <span className="muted">von</span>
          <Ref kind="agent" id={run.agentId} version={run.agentVersion} />
          <div className="actions">
            <Link to={`/runs?agent=${encodeURIComponent(run.agentId)}`} className="btn quiet sm">
              zur Matrix
            </Link>
          </div>
        </div>

        <div className={`verdict ${v.tone}`}>
          <strong>{v.title}</strong>
          {v.detail && <span>{v.detail}</span>}
        </div>

        <p className="facts-line">
          <span>
            <span className="muted">Model</span> <Ref kind="model" id={run.modelId} version={run.modelVersion} />
          </span>
          <span>
            <span className="muted">Tokens</span> <span className="mono">{run.totalTokens?.toLocaleString('de-DE') ?? '–'}</span>
            {judgeTokens > 0 && <span className="muted"> + {judgeTokens.toLocaleString('de-DE')} Judge</span>}
          </span>
          <span>
            <span className="muted">Dauer</span> <span className="mono">{fmtMs(run.durationMs)}</span>
          </span>
          <span className="muted">{fmtTime(run.startedAt ?? run.queuedAt)}</span>
          {run.status !== 'DONE' && <span className={`pill ${status.tone}`}>{status.text}</span>}
        </p>
      </section>

      <section className="card kind t-run">
        <div className="card-head">
          <h2>Antwort</h2>
        </div>
        {answer ? <pre className="prose answer">{answer}</pre> : <p className="empty">Keine Antwort.</p>}
      </section>

      {run.evals.length > 0 && (
        <section className="card kind t-run">
          <div className="card-head">
            <h2>Urteile</h2>
            <span className="sub">
              {evalsPassed} von {run.evals.length} bestanden
            </span>
          </div>
          <div className="eval-list">
            {run.evals.map((e, i) => (
              <div key={i} className={`verdict-row ${e.passed ? 'pass' : 'fail'}`}>
                <span className={`pill ${e.passed ? 'pass' : 'fail'}`}>{e.passed ? '✓' : '✗'}</span>
                <span className="verdict-what">
                  <span className="verdict-label">{e.subject ?? label(e.type)}</span>
                  {e.rationale && <span className="muted"> — {e.rationale}</span>}
                </span>
                <span className="kv" title={`Prüfung ${e.index}: ${e.type}`}>
                  {/* An eval with several verdicts books its one call on the first. */}
                  {e.model && (e.tokens !== null || e.durationMs !== null) ? `${e.model} · ${e.tokens ?? '?'} tok · ` : ''}
                  {e.type}
                </span>
              </div>
            ))}
          </div>
        </section>
      )}

      <Trace run={run} />

      <Fold title="Was das Model sah" hint="Systemprompt und Eingabe, wörtlich" className="kind t-run">
        <div className="field">
          <span>Systemprompt</span>
          <pre className="prose">{run.systemPrompt}</pre>
        </div>
        <div className="field">
          <span>Eingabe</span>
          <pre className="prose">{run.input}</pre>
        </div>
      </Fold>

      <Fold title="Technische Details" hint="Tools, Temperatur, Batch, Zeiten" className="kind t-run">
        <dl className="facts">
          <div>
            <dt>Model-Name beim Anbieter</dt>
            <dd className="mono">{run.modelName ?? '–'}</dd>
          </div>
          <div>
            <dt>Tools angeboten</dt>
            <dd className="line">
              {run.tools.length === 0 ? <span className="muted">keine</span> : run.tools.map((t) => <Ref key={t} kind="tool" id={t} />)}
            </dd>
          </div>
          <div>
            <dt>Temperatur</dt>
            <dd className="mono">{String(run.temperature).replace('.', ',')}</dd>
          </div>
          <div>
            <dt>Status</dt>
            <dd className="mono">{run.status}</dd>
          </div>
          <div>
            <dt>Eingereiht · gestartet · fertig</dt>
            <dd className="mono small">
              {fmtTime(run.queuedAt)}
              <br />
              {fmtTime(run.startedAt)}
              <br />
              {fmtTime(run.finishedAt)}
            </dd>
          </div>
          <div>
            <dt>Run · Batch</dt>
            <dd className="mono small">
              {run.id}
              <br />
              {run.batchId ?? 'ohne Batch'}
            </dd>
          </div>
        </dl>
      </Fold>
    </>
  )
}

/** What a model step did, in words: asked for tools, or answered. */
function modelAction(step: RunStep) {
  if (step.output?.startsWith(TOOL_REQUEST)) return 'ruft Tools auf'
  return 'antwortet'
}

/**
 * How the run got to its answer: a line of steps (model, tools, model) that says it at a
 * glance, the full record with arguments and results one click away.
 */
function Trace({ run }: { run: Run }) {
  const [open, setOpen] = useState(false)
  const steps = run.trace
  const failed = steps.some((s) => s.error)
  return (
    <section className="card kind t-run">
      <div className="card-head">
        <h2>Ablauf</h2>
        <span className="sub">
          {steps.length} Schritt{steps.length === 1 ? '' : 'e'} im Trace
        </span>
        {steps.length > 0 && (
          <div className="actions">
            <button className="btn quiet sm" onClick={() => setOpen(!open)}>
              {open ? 'einklappen' : 'alle Details'}
            </button>
          </div>
        )}
      </div>
      {steps.length === 0 ? (
        <p className="empty">Kein Schritt aufgezeichnet.</p>
      ) : (
        <ol className="timeline">
          {steps.map((s) => (
            <li key={s.seq} className={`timeline-step ${s.type === 'TOOL' ? 'tool' : 'model'} ${s.error ? 'errored' : ''}`}>
              <span className="timeline-name">{s.type === 'TOOL' ? <span className="mono">{s.name}</span> : 'Model'}</span>
              <span className="timeline-what">{s.error ? 'Fehler' : s.type === 'TOOL' ? 'Tool' : modelAction(s)}</span>
            </li>
          ))}
        </ol>
      )}
      {failed && !open && <p className="hint">Ein Schritt ist mit einem Fehler geendet — „alle Details“ zeigt ihn.</p>}
      {open && (
        <div className="steps">
          {steps.map((s) => (
            <Step key={s.seq} step={s} />
          ))}
        </div>
      )}
    </section>
  )
}

function Step({ step }: { step: RunStep }) {
  const isTool = step.type === 'TOOL'
  const requested = step.output?.startsWith(TOOL_REQUEST) ? step.output.slice(TOOL_REQUEST.length) : null
  return (
    <div className={`step ${isTool ? 'tool' : 'model'} ${step.error ? 'errored' : ''}`}>
      <div className="step-head">
        <span className="mono muted">{step.seq}</span>
        <span className={`pill block-pill t-${isTool ? 'catalog' : 'model'}`}>{isTool ? 'Tool' : 'Model'}</span>
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
              <pre>{step.input.trim()}</pre>
            </div>
          )}
          {requested !== null ? (
            <div className="step-block">
              <span className="lbl">Ruft Tools auf</span>
              <code>{requested}</code>
            </div>
          ) : (
            step.output && (
              <div className="step-block">
                <span className="lbl">Antwort</span>
                <pre>{step.output.trim()}</pre>
              </div>
            )
          )}
        </>
      )}
      {step.error && <p className="error">{step.error}</p>}
    </div>
  )
}
