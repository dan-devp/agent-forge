import { useEffect, useRef, useState } from 'react'
import { Link, useParams, useSearchParams } from 'react-router'
import {
  agentsApi,
  runsApi,
  trainingsApi,
  type Run,
  type Score,
  type Session,
  type SessionStatus,
  type TrainingStep,
} from '../api/client'
import { diffLines, isOpen, journey } from './training'

const POLL_MS = 1500

const STATUS: Record<SessionStatus, { label: string; tone: string }> = {
  RUNNING: { label: 'läuft', tone: 'open' },
  WAITING: { label: 'wartet auf dich', tone: 'accent' },
  DONE: { label: 'fertig', tone: 'pass' },
  STOPPED: { label: 'angehalten', tone: '' },
  BUDGET_EXCEEDED: { label: 'Budget aufgebraucht', tone: 'open' },
  FAILED: { label: 'Fehler', tone: 'fail' },
}

export function SessionStatusPill({ status }: { status: SessionStatus }) {
  const s = STATUS[status]
  return <span className={`pill ${s.tone}`}>{s.label}</span>
}

const ratio = (passed: number, total: number) => `${passed}/${total}`
const rate = (passed: number, total: number) => (total === 0 ? null : passed / total)

/** What happened to a step, in one word — the only place where colour means something. */
function outcome(step: TrainingStep, session: Session): { label: string; tone: string } {
  if (step.seq === 0) return step.score ? { label: 'Start', tone: '' } : { label: 'misst …', tone: 'open' }
  if (step.decision === 'ACCEPTED') return { label: 'angenommen', tone: 'pass' }
  if (step.decision === 'REJECTED') return { label: 'verworfen', tone: 'fail' }
  if (step.score && session.status === 'WAITING') return { label: 'wartet auf dich', tone: 'accent' }
  if (step.agentVersion === null) return { label: 'Coach schreibt …', tone: 'open' }
  return { label: 'misst …', tone: 'open' }
}

/** The best train rate after each step: it only rises, and it is what the training achieved. */
function bestRates(steps: TrainingStep[]) {
  const out: (number | null)[] = []
  let best: number | null = null
  for (const st of steps) {
    const r = st.score ? rate(st.score.trainPassed, st.score.trainTotal) : null
    if (r !== null && (st.seq === 0 || st.decision === 'ACCEPTED')) best = Math.max(best ?? 0, r)
    out.push(best)
  }
  return out
}

/** The training at a glance, for lists: the best-so-far line, nothing else. */
export function Sparkline({ session }: { session: Session }) {
  const values = bestRates(session.steps).filter((v): v is number => v !== null)
  if (values.length === 0) return <span className="spark empty" />
  const W = 72
  const H = 22
  const x = (i: number) => (values.length === 1 ? W / 2 : 3 + ((W - 6) * i) / (values.length - 1))
  const y = (v: number) => 3 + (H - 6) * (1 - v)
  const d = values.map((v, i) => `${i === 0 ? 'M' : 'L'} ${x(i)} ${y(v)}`).join(' ')
  return (
    <svg className="spark" viewBox={`0 0 ${W} ${H}`} width={W} height={H} aria-hidden="true">
      <path d={d} />
      <circle cx={x(values.length - 1)} cy={y(values[values.length - 1])} r={2.5} />
    </svg>
  )
}

/**
 * One session on its own page. A sentence with the result on top, the curve as the way to
 * pick a step, and below it exactly one step: what was proposed, what it measured, what was
 * decided. The raw material — diff, failed runs, the coach's input — waits behind buttons.
 */
export function SessionPage(p: { onError: (m: string) => void; onChanged: () => void }) {
  const { id: trainingId, sid } = useParams()
  const [params, setParams] = useSearchParams()
  const [session, setSession] = useState<Session | null>(null)
  const [tick, setTick] = useState(0)
  const [busy, setBusy] = useState(false)
  const timer = useRef<number | null>(null)
  const wasOpen = useRef(false)

  const load = (id: string) =>
    trainingsApi
      .session(id)
      .then((s) => {
        setSession(s)
        setTick((t) => t + 1)
        if (isOpen(s)) {
          wasOpen.current = true
          timer.current = window.setTimeout(() => load(id), POLL_MS)
        } else if (wasOpen.current) {
          wasOpen.current = false
          p.onChanged()
        }
      })
      .catch((e: Error) => p.onError(e.message))

  useEffect(() => {
    if (!sid) return
    load(sid)
    return () => {
      if (timer.current) window.clearTimeout(timer.current)
    }
  }, [sid]) // eslint-disable-line react-hooks/exhaustive-deps

  async function perform(action: () => Promise<unknown>) {
    if (!sid) return
    setBusy(true)
    try {
      await action()
      if (timer.current) window.clearTimeout(timer.current)
      await load(sid)
    } catch (e) {
      p.onError((e as Error).message)
    } finally {
      setBusy(false)
    }
  }

  if (!session || session.id !== sid) {
    return (
      <section className="card">
        <p className="empty">lädt …</p>
      </section>
    )
  }

  const s = session
  const { baseline, best } = journey(s)
  const waiting = s.steps.find((st) => st.decision === 'PENDING' && st.score && s.status === 'WAITING')
  const asked = params.get('step')
  const selected =
    s.steps.find((st) => String(st.seq) === asked) ?? waiting ?? s.steps.at(-1) ?? null
  const select = (seq: number) => setParams({ step: String(seq) }, { replace: true })
  const proposals = Math.max(0, s.steps.length - 1)

  return (
    <>
      <nav className="back">
        <Link to={`/trainings/${trainingId}`} className="link">
          ← {trainingId}
        </Link>
      </nav>

      <section className="card">
        <div className="headline">
          <div className="headline-main">
            <h2>
              <span className="mono">{s.agentId}</span>
              {baseline && (
                <>
                  : Train <span className="mono">{ratio(baseline.trainPassed, baseline.trainTotal)}</span>
                  {best && best !== baseline && (
                    <>
                      {' '}
                      → <span className="mono">{ratio(best.trainPassed, best.trainTotal)}</span>
                    </>
                  )}
                  {baseline.holdoutTotal > 0 && (
                    <span className="holdout-part">
                      , Holdout <span className="mono">{ratio(baseline.holdoutPassed, baseline.holdoutTotal)}</span>
                      {best && best !== baseline && (
                        <>
                          {' '}
                          → <span className="mono">{ratio(best.holdoutPassed, best.holdoutTotal)}</span>
                        </>
                      )}
                    </span>
                  )}
                </>
              )}
            </h2>
            <p className="muted">
              {proposals} von {s.maxSteps} Schritten · {s.totalTokens.toLocaleString('de-DE')} Tokens
              {s.maxTokens !== null && ` von ${s.maxTokens.toLocaleString('de-DE')}`}
              {s.finalVersion !== null && ` · Prompt v${s.finalVersion} ist jetzt aktuell`}
              {s.finalVersion === null && s.bestVersion !== null && ` · beste Version bisher v${s.bestVersion}`}
            </p>
            {s.note && <p className="small muted">{s.note}</p>}
            {s.error && <p className="error">{s.error}</p>}
          </div>
          <div className="headline-side">
            <SessionStatusPill status={s.status} />
            <div className="toolbar">
              <Link className="btn quiet sm" to={`/runs?agent=${encodeURIComponent(s.agentId)}`}>
                Matrix
              </Link>
              {isOpen(s) && (
                <button className="btn quiet danger sm" onClick={() => perform(() => trainingsApi.stop(s.id))} disabled={busy}>
                  anhalten
                </button>
              )}
            </div>
          </div>
        </div>

        <Curve session={s} selected={selected?.seq ?? null} onSelect={select} />

        {selected && (
          <StepPanel
            key={selected.seq}
            step={selected}
            session={s}
            tick={tick}
            busy={busy}
            onDecide={(accept) => perform(() => trainingsApi.decide(s.id, selected.seq, accept))}
            onError={p.onError}
          />
        )}

        <p className="small faint">
          Gestartet {new Date(s.startedAt).toLocaleString('de-DE')} · Training v{s.trainingVersion} · Coach {s.coachId} v{s.coachVersion} · n=
          {s.repetitions} · Freigabe {s.approval === 'MANUAL' ? 'von Hand' : 'durch das Gate'}
        </p>
      </section>
    </>
  )
}

/**
 * The curve doubles as navigation: one column per step, the thick line is the best train
 * rate so far, dots are the measured candidates (filled accepted, hollow rejected), squares
 * the holdout. Below each column the version and what became of it; a click selects it.
 */
function Curve(p: { session: Session; selected: number | null; onSelect: (seq: number) => void }) {
  const steps = p.session.steps
  const W = 680
  const H = 200
  const pad = { l: 40, r: 20, t: 14, b: 52 }
  const cols = Math.max(steps.length, 2)
  const colW = (W - pad.l - pad.r) / (cols - 1)
  const x = (i: number) => pad.l + colW * i
  const y = (r: number) => pad.t + (H - pad.t - pad.b) * (1 - r)
  const best = bestRates(steps)

  const bestPath = best
    .map((v, i) => (v === null ? null : { v, i }))
    .filter((pt): pt is { v: number; i: number } => pt !== null)
    .flatMap((pt, k, all) => {
      const here = `${k === 0 ? 'M' : 'L'} ${x(pt.i)} ${y(pt.v)}`
      const next = all[k + 1]
      return next ? [here, `L ${x(next.i)} ${y(pt.v)}`] : [here]
    })
    .join(' ')
  const holdoutPath = steps
    .map((st, i) => (st.score && st.score.holdoutTotal > 0 ? { i, v: st.score.holdoutPassed / st.score.holdoutTotal } : null))
    .filter((pt): pt is { i: number; v: number } => pt !== null)
    .map((pt, k) => `${k === 0 ? 'M' : 'L'} ${x(pt.i)} ${y(pt.v)}`)
    .join(' ')

  return (
    <figure className="curve">
      <svg viewBox={`0 0 ${W} ${H}`} role="img" aria-label="Trainingskurve">
        {steps.map((st, i) =>
          p.selected === st.seq ? (
            <rect key={`sel-${st.seq}`} x={x(i) - colW / 2 + 4} y={4} width={colW - 8} height={H - 8} rx={12} className="col-selected" />
          ) : null,
        )}
        {[0, 0.5, 1].map((r) => (
          <g key={r}>
            <line x1={pad.l} x2={W - pad.r} y1={y(r)} y2={y(r)} className="grid" />
            <text x={pad.l - 8} y={y(r) + 4} textAnchor="end" className="axis">
              {Math.round(r * 100)}%
            </text>
          </g>
        ))}
        {holdoutPath && <path d={holdoutPath} className="holdout-line" />}
        {bestPath && <path d={bestPath} className="best-line" />}
        {steps.map((st, i) => {
          const o = outcome(st, p.session)
          const sc = st.score as Score | null
          return (
            <g key={st.seq}>
              {sc && sc.holdoutTotal > 0 && (
                <rect x={x(i) - 4} y={y(sc.holdoutPassed / sc.holdoutTotal) - 4} width={8} height={8} className="holdout-dot" />
              )}
              {sc && (
                <circle cx={x(i)} cy={y(sc.trainPassed / Math.max(1, sc.trainTotal))} r={6} className={`train-dot ${st.decision.toLowerCase()}`} />
              )}
              <text x={x(i)} y={H - pad.b + 20} textAnchor="middle" className="axis mono">
                {st.agentVersion !== null ? `v${st.agentVersion}` : '–'}
              </text>
              <text x={x(i)} y={H - pad.b + 36} textAnchor="middle" className={`axis outcome ${o.tone}`}>
                {o.label}
              </text>
              <rect
                x={x(i) - colW / 2}
                y={0}
                width={colW}
                height={H}
                className="col-hit"
                onClick={() => p.onSelect(st.seq)}
                role="button"
                aria-label={`Schritt ${st.seq}`}
              >
                <title>
                  {st.agentVersion !== null ? `v${st.agentVersion}` : `Schritt ${st.seq}`} – {o.label}
                  {sc ? ` · Train ${ratio(sc.trainPassed, sc.trainTotal)}` : ''}
                  {sc && sc.holdoutTotal > 0 ? ` · Holdout ${ratio(sc.holdoutPassed, sc.holdoutTotal)}` : ''}
                </title>
              </rect>
            </g>
          )
        })}
      </svg>
      <figcaption className="small muted">
        <span className="legend best" /> beste Train-Quote · <span className="legend dot" /> angenommen · <span className="legend hollow" />{' '}
        verworfen · <span className="legend square" /> Holdout, sieht der Coach nie · Spalte anklicken für Details
      </figcaption>
    </figure>
  )
}

type TaskTally = { taskId: string; holdout: boolean; passed: number; total: number; open: number }

function tallyByTask(session: Session, runs: Run[]): TaskTally[] {
  const all = [...session.train.map((t) => ({ t, h: false })), ...session.holdout.map((t) => ({ t, h: true }))]
  return all.map(({ t, h }) => {
    const mine = runs.filter((r) => r.taskId === t && r.status !== 'CANCELLED')
    const open = mine.filter((r) => r.status === 'QUEUED' || r.status === 'RUNNING').length
    return { taskId: t, holdout: h, passed: mine.filter((r) => r.passed).length, total: mine.length - open, open }
  })
}

type Drawer = 'diff' | 'failed' | 'coach' | null

/** One step in a fixed order: ① proposal, ② measurement, ③ decision. Raw material behind buttons. */
function StepPanel(p: {
  step: TrainingStep
  session: Session
  tick: number
  busy: boolean
  onDecide: (accept: boolean) => void
  onError: (m: string) => void
}) {
  const st = p.step
  const s = p.session
  const o = outcome(st, s)
  const [runs, setRuns] = useState<{ batch: string; list: Run[] } | null>(null)
  const [drawer, setDrawer] = useState<Drawer>(null)
  const [diff, setDiff] = useState<{ before: string; after: string } | null>(null)

  // The measurement per task comes from the step's batch; reloaded with every poll while it runs.
  const measuring = st.batchId !== null && st.score === null
  useEffect(() => {
    if (!st.batchId) return
    let stale = false
    runsApi
      .batchRuns(st.batchId)
      .then((list) => {
        if (!stale) setRuns({ batch: st.batchId as string, list })
      })
      .catch((e: Error) => p.onError(e.message))
    return () => {
      stale = true
    }
  }, [st.batchId, measuring ? p.tick : 0]) // eslint-disable-line react-hooks/exhaustive-deps

  const batchRuns = runs && runs.batch === st.batchId ? runs.list : null
  const tallies = batchRuns ? tallyByTask(s, batchRuns) : null
  const failed = batchRuns ? batchRuns.filter((r) => r.status !== 'CANCELLED' && r.passed === false) : []
  const waiting = s.status === 'WAITING' && st.decision === 'PENDING' && st.score !== null
  const hasDiff = st.baseVersion !== null && st.agentVersion !== null
  const coachTokens = st.coachTokens !== null ? `Coach ${st.coachTokens} tok, ${((st.coachDurationMs ?? 0) / 1000).toFixed(1)} s` : null

  function open(next: Drawer) {
    if (drawer === next) {
      setDrawer(null)
      return
    }
    if (next === 'diff' && !diff && hasDiff) {
      Promise.all([agentsApi.version(s.agentId, st.baseVersion as number), agentsApi.version(s.agentId, st.agentVersion as number)])
        .then(([a, b]) => {
          setDiff({ before: a.systemPrompt, after: b.systemPrompt })
          setDrawer('diff')
        })
        .catch((e: Error) => p.onError(e.message))
      return
    }
    setDrawer(next)
  }

  return (
    <div className={`step-panel ${waiting ? 'waiting' : ''}`}>
      <div className="step-panel-head">
        <h3>
          {st.agentVersion !== null ? <span className="mono">v{st.agentVersion}</span> : <span className="mono">Schritt {st.seq}</span>}
        </h3>
        <span className={`pill ${o.tone}`}>{o.label}</span>
        {st.baseVersion !== null && <span className="muted">aus v{st.baseVersion}</span>}
        <span className="spacer" />
        <span className="small faint mono">
          {[coachTokens, st.runTokens !== null ? `Messung ${st.runTokens} tok` : null].filter(Boolean).join(' · ')}
        </span>
      </div>

      <div className="phase">
        <span className="phase-no">①</span>
        <span className="phase-name">Vorschlag</span>
        <div className="phase-body">
          {st.seq === 0 ? (
            <p className="muted">Ausgangslage: der Prompt von v{st.agentVersion}, so wie er war.</p>
          ) : st.rationale ? (
            <pre className="rationale">{st.rationale}</pre>
          ) : st.coachOutput === null ? (
            <p className="muted">
              <span className="spinner" /> Der Coach schreibt einen Vorschlag …
            </p>
          ) : (
            <p className="muted">Der Coach hat keine Begründung geschrieben.</p>
          )}
        </div>
      </div>

      <div className="phase">
        <span className="phase-no">②</span>
        <span className="phase-name">Messung</span>
        <div className="phase-body">
          {st.agentVersion === null && st.seq > 0 ? (
            <p className="muted">nicht gemessen</p>
          ) : !tallies ? (
            <p className="muted">
              <span className="spinner" /> wartet auf die Runs …
            </p>
          ) : (
            <div className="task-tallies">
              {tallies.map((t) => (
                <div key={t.taskId} className="task-tally">
                  <span className={`dot ${t.open > 0 ? 'open' : t.total === 0 ? '' : t.passed === t.total ? 'pass' : 'fail'}`} />
                  <span className="mono">{t.taskId}</span>
                  {t.holdout && <span className="small faint">Holdout</span>}
                  <span className="mono num">
                    {t.passed}/{t.total}
                    {t.open > 0 && <span className="faint"> +{t.open} offen</span>}
                  </span>
                </div>
              ))}
            </div>
          )}
        </div>
      </div>

      <div className="phase">
        <span className="phase-no">③</span>
        <span className="phase-name">Entscheidung</span>
        <div className="phase-body">
          {st.seq === 0 ? (
            <p className="muted">Der Startpunkt, an dem sich jeder Vorschlag messen muss.</p>
          ) : st.reason ? (
            <p>
              {waiting && <span className="muted">Gate empfiehlt {st.gate === 'ACCEPTED' ? 'annehmen' : 'verwerfen'}: </span>}
              {st.reason}
            </p>
          ) : (
            <p className="muted">steht noch aus</p>
          )}
          {waiting && (
            <div className="toolbar">
              <button className="btn primary sm" onClick={() => p.onDecide(true)} disabled={p.busy}>
                annehmen
              </button>
              <button className="btn sm" onClick={() => p.onDecide(false)} disabled={p.busy}>
                verwerfen
              </button>
            </div>
          )}
        </div>
      </div>

      <div className="drawers">
        {hasDiff && (
          <button className={`btn quiet sm ${drawer === 'diff' ? 'on' : ''}`} onClick={() => open('diff')}>
            Prompt-Diff
          </button>
        )}
        {failed.length > 0 && (
          <button className={`btn quiet sm ${drawer === 'failed' ? 'on' : ''}`} onClick={() => open('failed')}>
            Gescheiterte Runs ({failed.length})
          </button>
        )}
        {(st.coachInput || st.coachOutput) && (
          <button className={`btn quiet sm ${drawer === 'coach' ? 'on' : ''}`} onClick={() => open('coach')}>
            Was der Coach sah
          </button>
        )}
      </div>

      {drawer === 'diff' && diff && <PromptDiff before={diff.before} after={diff.after} from={st.baseVersion} to={st.agentVersion} />}
      {drawer === 'failed' && (
        <div className="failed-runs">
          {failed.map((r) => (
            <Link key={r.id} to={`/runs/${r.id}`} className="failed-run">
              <span className="mono">{r.taskId}</span>
              {s.holdout.includes(r.taskId ?? '') && <span className="small faint">Holdout</span>}
              <span className="small muted preview">{r.answer ?? r.error ?? r.status}</span>
            </Link>
          ))}
        </div>
      )}
      {drawer === 'coach' && (
        <div className="coach-io">
          {st.coachInput && (
            <div className="step-block">
              <span className="lbl">Eingabe an den Coach</span>
              <pre className="source mono">{st.coachInput}</pre>
            </div>
          )}
          {st.coachOutput && (
            <div className="step-block">
              <span className="lbl">Antwort des Coach{st.coachModel ? ` (${st.coachModel})` : ''}</span>
              <pre className="source mono">{st.coachOutput}</pre>
            </div>
          )}
        </div>
      )}
    </div>
  )
}

function PromptDiff(p: { before: string; after: string; from: number | null; to: number | null }) {
  const lines = diffLines(p.before, p.after)
  return (
    <div className="diff">
      <div className="diff-head small muted mono">
        v{p.from} → v{p.to}
      </div>
      {lines.map((l, i) => (
        <div key={i} className={`diff-line ${l.kind}`}>
          <span className="sign">{l.kind === 'add' ? '+' : l.kind === 'del' ? '−' : ' '}</span>
          <span>{l.text || ' '}</span>
        </div>
      ))}
    </div>
  )
}
