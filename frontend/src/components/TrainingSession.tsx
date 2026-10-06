import { useEffect, useRef, useState } from 'react'
import { Link, useLocation, useParams, useSearchParams } from 'react-router'
import {
  agentsApi,
  catalogApi,
  runsApi,
  tasksApi,
  trainingsApi,
  type EvalType,
  type Run,
  type Score,
  type Session,
  type SessionStatus,
  type Task,
  type TrainingStep,
} from '../api/client'
import { PromptDiff } from './PromptDiff'
import { decisionSentence, isOpen, journey } from './training'
import { Ref } from './Ref'

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
const pct = (v: number) => `${Math.round(v * 100)} %`
const finished = (r: Run) => r.status !== 'QUEUED' && r.status !== 'RUNNING'

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

/** The training at a glance, for lists: the best-so-far line, nothing else. One point is no line. */
export function Sparkline({ session }: { session: Session }) {
  const values = bestRates(session.steps).filter((v): v is number => v !== null)
  if (values.length < 2) return null
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

/** The runs of the step being measured right now, as far as they got. */
type Live = { batch: string; runs: Run[] }

/** A step that was just accepted, and by how much it lifted the best train rate. */
type Celebration = { seq: number; delta: number }

type LiveState = { kind: 'start' | 'coach' | 'measure' | 'wait'; text: string; done: number; total: number }

/** Where a running session stands, for the status line: who is at work, and how far. */
function liveState(s: Session, live: Live | null): LiveState | null {
  if (!isOpen(s)) return null
  const step = s.steps.at(-1)
  if (!step) return { kind: 'start', text: 'Die Session startet …', done: 0, total: 0 }
  if (s.status === 'WAITING') return { kind: 'wait', text: `v${step.agentVersion} ist gemessen und wartet auf deine Entscheidung.`, done: 0, total: 0 }
  if (step.seq > 0 && step.agentVersion === null) {
    return { kind: 'coach', text: `Der Coach liest die gescheiterten Runs und schreibt Vorschlag ${step.seq} …`, done: 0, total: 0 }
  }
  const runs = live && live.batch === step.batchId ? live.runs.filter((r) => r.status !== 'CANCELLED') : []
  const done = runs.filter(finished).length
  const what = step.seq === 0 ? `die Ausgangslage v${step.agentVersion}` : `Vorschlag v${step.agentVersion}`
  return {
    kind: 'measure',
    text: runs.length === 0 ? `Misst ${what} …` : `Misst ${what}: ${done} von ${runs.length} Runs fertig`,
    done,
    total: runs.length,
  }
}

/** The status line while a session runs: a pulse, what is happening, a bar for how far. */
function LiveBar({ state }: { state: LiveState }) {
  const share = state.total > 0 ? (100 * state.done) / state.total : null
  return (
    <div className={`live-bar ${state.kind}`} role="status" aria-live="polite">
      <span className="live-pulse" aria-hidden="true" />
      <span className="live-text">{state.text}</span>
      {state.kind !== 'wait' && (
        <span className="progress" aria-hidden="true">
          {share === null ? <span className="indeterminate" /> : <span style={{ width: `${share}%` }} />}
        </span>
      )}
    </div>
  )
}

/**
 * One session on its own page. A sentence with the result on top, the curve as the way to
 * pick a step, and below it exactly one step: what was proposed, what it measured, what was
 * decided. While it runs, the page follows along: the runs of the step being measured fill
 * in, the curve shows the rate so far, and an accepted version gets its moment.
 */
export function SessionPage(p: { onError: (m: string) => void; onChanged: () => void }) {
  const { id: trainingId, sid } = useParams()
  const [params, setParams] = useSearchParams()
  const [session, setSession] = useState<Session | null>(null)
  const [tick, setTick] = useState(0)
  const [busy, setBusy] = useState(false)
  // Names for what the steps measure: a task reads as its name, a check as its label.
  const [tasks, setTasks] = useState<Task[]>([])
  const [evalTypes, setEvalTypes] = useState<EvalType[]>([])
  const [live, setLive] = useState<Live | null>(null)
  const [celebrate, setCelebrate] = useState<Celebration | null>(null)
  const timer = useRef<number | null>(null)
  const wasOpen = useRef(false)
  // The decisions seen at the last poll, to notice the moment a step gets accepted.
  const decided = useRef<Map<number, string> | null>(null)

  useEffect(() => {
    tasksApi.list().then(setTasks).catch(() => setTasks([]))
    catalogApi.evals().then(setEvalTypes).catch(() => setEvalTypes([]))
  }, [])

  /** A step that turns accepted while the page is open gets a moment; history does not. */
  function noticeAccepted(s: Session) {
    const before = decided.current
    decided.current = new Map(s.steps.map((st) => [st.seq, st.decision]))
    if (!before) return
    const fresh = s.steps.find((st) => st.seq > 0 && st.decision === 'ACCEPTED' && before.get(st.seq) !== 'ACCEPTED')
    if (!fresh) return
    const rates = bestRates(s.steps)
    const i = s.steps.indexOf(fresh)
    setCelebrate({ seq: fresh.seq, delta: (rates[i] ?? 0) - (rates[i - 1] ?? 0) })
    window.setTimeout(() => setCelebrate((c) => (c?.seq === fresh.seq ? null : c)), 4500)
  }

  const load = (id: string) =>
    trainingsApi
      .session(id)
      .then((s) => {
        noticeAccepted(s)
        setSession(s)
        setTick((t) => t + 1)
        // The step being measured: its runs as far as they got, for the live view.
        const step = s.steps.at(-1)
        if (isOpen(s) && step?.batchId && step.score === null) {
          const batch = step.batchId
          runsApi
            .batchRuns(batch)
            .then((runs) => setLive({ batch, runs }))
            .catch(() => {})
        }
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
    decided.current = null
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
      <section className="card kind t-training">
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
  // The opened task is part of the address, so the way back from a run lands on it again.
  const openTask = params.get('task')
  const toggleTask = (task: string) =>
    selected &&
    setParams(openTask === task ? { step: String(selected.seq) } : { step: String(selected.seq), task }, { replace: true })
  const proposals = Math.max(0, s.steps.length - 1)
  const state = liveState(s, live)
  // Ended on the baseline because it already passed everything: one point, nothing to draw.
  const nothingToLearn =
    !isOpen(s) && s.steps.length === 1 && baseline !== null && baseline.trainTotal > 0 && baseline.trainPassed === baseline.trainTotal

  return (
    <>
      <nav className="back">
        <Link to={`/trainings/${trainingId}`} className="link">
          ← {trainingId}
        </Link>
      </nav>

      <section className="card kind t-training">
        <div className="headline">
          <div className="headline-main">
            <h2>
              <Ref kind="agent" id={s.agentId} />
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
              {proposals} von {s.maxSteps} Vorschlägen · {s.totalTokens.toLocaleString('de-DE')} Tokens
              {s.maxTokens !== null && ` von ${s.maxTokens.toLocaleString('de-DE')}`}
              {s.finalVersion !== null && ` · Prompt v${s.finalVersion} ist jetzt aktuell`}
              {s.finalVersion === null && s.bestVersion !== null && ` · beste Version bisher v${s.bestVersion}`}
            </p>
            {s.note && !nothingToLearn && <p className="small muted">{s.note}</p>}
            {s.error && <p className="error">{s.error}</p>}
          </div>
          <div className="headline-side">
            <SessionStatusPill status={s.status} />
            <div className="toolbar">
              <Link className="btn quiet sm" to={`/runs?agent=${encodeURIComponent(s.agentId)}`}>
                zur Matrix
              </Link>
              {isOpen(s) && (
                <button className="btn quiet danger sm" onClick={() => perform(() => trainingsApi.stop(s.id))} disabled={busy}>
                  anhalten
                </button>
              )}
            </div>
          </div>
        </div>

        {state && <LiveBar state={state} />}

        {nothingToLearn && baseline ? (
          <div className="verdict pass session-callout">
            <strong>Nichts zu lernen</strong>
            <span>
              Schon die Ausgangslage v{s.startVersion} besteht alle {baseline.trainTotal} Train-Runs. Der Coach musste nichts verbessern —
              deshalb gibt es hier nur einen Messpunkt und keine Kurve.
            </span>
            <span className="small">
              Eine Kurve entsteht, wenn der Agent anfangs scheitert: ein Agent ohne Vorwissen, schwierigere Prüfungen oder Tasks, die er noch nicht
              kennt.
            </span>
          </div>
        ) : (
          <Curve session={s} selected={selected?.seq ?? null} live={live} celebrate={celebrate} onSelect={select} />
        )}

        {selected && (
          <StepPanel
            key={selected.seq}
            step={selected}
            session={s}
            tick={tick}
            busy={busy}
            tasks={tasks}
            evalTypes={evalTypes}
            live={live}
            celebrating={celebrate?.seq === selected.seq}
            openTask={openTask}
            onToggleTask={toggleTask}
            onDecide={(accept) => perform(() => trainingsApi.decide(s.id, selected.seq, accept))}
            onError={p.onError}
          />
        )}

        <p className="small faint">
          Gestartet {new Date(s.startedAt).toLocaleString('de-DE')} · Training v{s.trainingVersion} · Coach <Ref kind="agent" id={s.coachId} version={s.coachVersion} /> · je
          Task {s.repetitions} Runs · Freigabe {s.approval === 'MANUAL' ? 'von Hand' : 'durch das Gate'}
        </p>
      </section>
    </>
  )
}

/**
 * The curve doubles as navigation: one column per step, the thick line is the best train
 * rate so far with the ground below it filled, dots are the measured candidates (filled
 * accepted, hollow rejected) with their rate, squares the holdout. The step being measured
 * shows a pulsing dot at the rate so far; an accepted step pops and says how much it gained.
 */
function Curve(p: {
  session: Session
  selected: number | null
  live: Live | null
  celebrate: Celebration | null
  onSelect: (seq: number) => void
}) {
  const steps = p.session.steps
  const W = 680
  const H = 220
  const pad = { l: 44, r: 4, t: 26, b: 52 }
  // Each step owns a column and sits in its middle, so the first and last label fit as well.
  const colW = (W - pad.l - pad.r) / Math.max(steps.length, 1)
  const x = (i: number) => pad.l + colW * (i + 0.5)
  const y = (r: number) => pad.t + (H - pad.t - pad.b) * (1 - r)
  const best = bestRates(steps)

  const bestPts = best
    .map((v, i) => (v === null ? null : { v, i }))
    .filter((pt): pt is { v: number; i: number } => pt !== null)
    .flatMap((pt, k, all) => {
      const here = [x(pt.i), y(pt.v)] as const
      const next = all[k + 1]
      return next ? [here, [x(next.i), y(pt.v)] as const] : [here]
    })
  const bestPath = bestPts.map(([px, py], k) => `${k === 0 ? 'M' : 'L'} ${px} ${py}`).join(' ')
  const area = bestPts.length > 1 ? `${bestPath} L ${bestPts[bestPts.length - 1][0]} ${y(0)} L ${bestPts[0][0]} ${y(0)} Z` : ''
  const holdoutPath = steps
    .map((st, i) => (st.score && st.score.holdoutTotal > 0 ? { i, v: st.score.holdoutPassed / st.score.holdoutTotal } : null))
    .filter((pt): pt is { i: number; v: number } => pt !== null)
    .map((pt, k) => `${k === 0 ? 'M' : 'L'} ${x(pt.i)} ${y(pt.v)}`)
    .join(' ')
  // The line draws itself anew when a measurement lands, not on every poll.
  const scored = steps.filter((st) => st.score).length

  // The step under measurement: train runs done so far, and their rate.
  const last = steps.at(-1)
  const lastIndex = steps.length - 1
  const measuring = isOpen(p.session) && last !== undefined && last.score === null && last.batchId !== null
  const liveTrain =
    measuring && p.live && p.live.batch === last.batchId
      ? p.live.runs.filter((r) => r.status !== 'CANCELLED' && p.session.train.includes(r.taskId ?? ''))
      : []
  const liveDone = liveTrain.filter(finished)
  const livePassed = liveDone.filter((r) => r.passed).length
  const liveRate = liveDone.length > 0 ? livePassed / liveDone.length : null
  const working = isOpen(p.session) && last !== undefined && last.score === null

  return (
    <figure className="curve">
      <svg viewBox={`0 0 ${W} ${H}`} role="img" aria-label="Trainingskurve">
        {steps.map((st, i) =>
          p.selected === st.seq ? (
            <rect key={`sel-${st.seq}`} x={x(i) - colW / 2 + 3} y={3} width={colW - 6} height={H - 6} rx={12} className="col-selected" />
          ) : null,
        )}
        {working && <rect x={x(lastIndex) - colW / 2 + 3} y={3} width={colW - 6} height={H - 6} rx={12} className="col-live" />}
        {[0, 0.5, 1].map((r) => (
          <g key={r}>
            <line x1={pad.l} x2={W - pad.r} y1={y(r)} y2={y(r)} className="grid" />
            <text x={pad.l - 8} y={y(r) + 4} textAnchor="end" className="axis">
              {Math.round(r * 100)}%
            </text>
          </g>
        ))}
        {area && <path key={`area-${scored}`} d={area} className="best-area" />}
        {holdoutPath && <path d={holdoutPath} className="holdout-line" />}
        {bestPath && <path key={`line-${scored}`} d={bestPath} className="best-line" pathLength={1} />}
        {steps.map((st, i) => {
          const o = outcome(st, p.session)
          const sc = st.score as Score | null
          const trainRate = sc ? sc.trainPassed / Math.max(1, sc.trainTotal) : null
          const party = p.celebrate?.seq === st.seq
          return (
            <g key={st.seq}>
              {sc && sc.holdoutTotal > 0 && (
                <rect x={x(i) - 4} y={y(sc.holdoutPassed / sc.holdoutTotal) - 4} width={8} height={8} className="holdout-dot" />
              )}
              {trainRate !== null && (
                <>
                  {party && <circle cx={x(i)} cy={y(trainRate)} r={6} className="party-ring" />}
                  <circle
                    key={`${st.seq}-${st.decision}`}
                    cx={x(i)}
                    cy={y(trainRate)}
                    r={6}
                    className={`train-dot ${st.decision.toLowerCase()} ${party ? 'party' : ''}`}
                  />
                  <text x={x(i)} y={y(trainRate) - 12} textAnchor="middle" className="value">
                    {pct(trainRate)}
                  </text>
                  {party && p.celebrate && p.celebrate.delta > 0 && (
                    // Above the dot, unless that leaves the chart: at the top it goes below.
                    <text x={x(i)} y={y(trainRate) - 30 < 12 ? y(trainRate) + 30 : y(trainRate) - 30} textAnchor="middle" className="delta">
                      +{Math.round(p.celebrate.delta * 100)} %
                    </text>
                  )}
                </>
              )}
              {i === lastIndex && measuring && liveRate !== null && (
                <>
                  <circle cx={x(i)} cy={y(liveRate)} r={6} className="live-ring" />
                  <circle cx={x(i)} cy={y(liveRate)} r={6} className="live-point" />
                  <text x={x(i)} y={y(liveRate) - 12} textAnchor="middle" className="value live">
                    {livePassed}/{liveDone.length}
                  </text>
                </>
              )}
              <text x={x(i)} y={H - pad.b + 20} textAnchor="middle" className="axis mono">
                {st.agentVersion !== null ? `v${st.agentVersion}` : '–'}
              </text>
              <text x={x(i)} y={H - pad.b + 36} textAnchor="middle" className={`axis outcome ${o.tone} ${sc ? '' : 'pulse'}`}>
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

type TaskTally = { taskId: string; holdout: boolean; passed: number; total: number; open: number; runs: Run[] }

function tallyByTask(session: Session, runs: Run[]): TaskTally[] {
  const all = [...session.train.map((t) => ({ t, h: false })), ...session.holdout.map((t) => ({ t, h: true }))]
  return all.map(({ t, h }) => {
    const mine = runs.filter((r) => r.taskId === t && r.status !== 'CANCELLED')
    const open = mine.filter((r) => !finished(r)).length
    return { taskId: t, holdout: h, passed: mine.filter((r) => r.passed).length, total: mine.length - open, open, runs: mine }
  })
}

/** One tile per run: waiting, running, passed or failed. A new state remounts it, so it pops. */
function RunTiles({ runs }: { runs: Run[] }) {
  return (
    <span className="tiles" aria-hidden="true">
      {runs.map((r) => {
        const tone =
          r.status === 'QUEUED' ? 'queued' : r.status === 'RUNNING' ? 'running' : r.status === 'DONE' && r.passed ? 'pass' : 'fail'
        return <span key={`${r.id}-${r.status}`} className={`tile ${tone}`} />
      })}
    </span>
  )
}

type Drawer = 'diff' | 'coach' | null

/** One step in a fixed order: ① proposal, ② measurement, ③ decision. Raw material behind buttons. */
function StepPanel(p: {
  step: TrainingStep
  session: Session
  tick: number
  busy: boolean
  tasks: Task[]
  evalTypes: EvalType[]
  live: Live | null
  celebrating: boolean
  openTask: string | null
  onToggleTask: (task: string) => void
  onDecide: (accept: boolean) => void
  onError: (m: string) => void
}) {
  const st = p.step
  const s = p.session
  const o = outcome(st, s)
  const [runs, setRuns] = useState<{ batch: string; list: Run[] } | null>(null)
  const [drawer, setDrawer] = useState<Drawer>(null)
  const [diff, setDiff] = useState<{ before: string; after: string } | null>(null)

  // While a step is measured, the page polls its runs; once measured, they are loaded once here.
  const measuring = st.batchId !== null && st.score === null
  useEffect(() => {
    if (!st.batchId || measuring) return
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
  }, [st.batchId, measuring]) // eslint-disable-line react-hooks/exhaustive-deps

  const batchRuns =
    measuring && p.live && p.live.batch === st.batchId ? p.live.runs : runs && runs.batch === st.batchId ? runs.list : null
  const tallies = batchRuns ? tallyByTask(s, batchRuns) : null
  const waiting = s.status === 'WAITING' && st.decision === 'PENDING' && st.score !== null
  const hasDiff = st.baseVersion !== null && st.agentVersion !== null
  const sentence = decisionSentence(st, s.steps)
  const decisionWord =
    st.decision === 'ACCEPTED'
      ? 'Angenommen'
      : st.decision === 'REJECTED'
        ? 'Verworfen'
        : waiting
          ? `Das Gate empfiehlt ${st.gate === 'ACCEPTED' ? 'annehmen' : 'verwerfen'}`
          : null
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
    <div className={`step-panel ${waiting ? 'waiting' : ''} ${p.celebrating ? 'celebrate' : ''}`}>
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
            <pre className="rationale appear">{st.rationale}</pre>
          ) : st.coachOutput === null ? (
            <div className="coach-writing">
              <span className="muted">Der Coach schreibt einen Vorschlag …</span>
              <span className="shimmer" />
              <span className="shimmer short" />
            </div>
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
            <p className="muted">noch nicht gemessen</p>
          ) : !tallies ? (
            <p className="muted">
              <span className="spinner" /> wartet auf die Runs …
            </p>
          ) : (
            <div className="task-tallies">
              {tallies.map((t) => {
                const open = p.openTask === t.taskId
                return (
                  <div key={t.taskId} className={`task-tally-group ${open ? 'open' : ''}`}>
                    <button type="button" className="task-tally" onClick={() => p.onToggleTask(t.taskId)} aria-expanded={open}>
                      <span className={`dot ${t.open > 0 ? 'open' : t.total === 0 ? '' : t.passed === t.total ? 'pass' : 'fail'}`} />
                      <span className="task-tally-name">{p.tasks.find((x) => x.id === t.taskId)?.name ?? t.taskId}</span>
                      <span className="small faint">{t.holdout ? 'Holdout' : ''}</span>
                      <span className="mono num">
                        {t.passed}/{t.total}
                      </span>
                      <RunTiles runs={t.runs} />
                      <span className="task-tally-toggle">{open ? 'zuklappen' : 'Runs ansehen'}</span>
                    </button>
                    {open && batchRuns && (
                      <TaskRuns
                        taskId={t.taskId}
                        runs={batchRuns.filter((r) => r.taskId === t.taskId && r.status !== 'CANCELLED')}
                        evalTypes={p.evalTypes}
                        onError={p.onError}
                      />
                    )}
                  </div>
                )
              })}
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
          ) : st.reason || sentence ? (
            <p title={st.reason ?? undefined} className="appear">
              {decisionWord && (
                <strong>
                  {decisionWord}
                  {st.decidedBy === 'user' && ' (von dir entschieden)'}:{' '}
                </strong>
              )}
              {sentence ?? st.reason}
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
        {(st.coachInput || st.coachOutput) && (
          <button className={`btn quiet sm ${drawer === 'coach' ? 'on' : ''}`} onClick={() => open('coach')}>
            Was der Coach sah
          </button>
        )}
      </div>

      {drawer === 'diff' && diff && <PromptDiff before={diff.before} after={diff.after} from={st.baseVersion} to={st.agentVersion} />}
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

/**
 * The runs of one task in one step, loaded with their verdicts when opened: what each run
 * answered and which checks it missed. A run opens its detail, which leads back here.
 */
function TaskRuns(p: { taskId: string; runs: Run[]; evalTypes: EvalType[]; onError: (m: string) => void }) {
  const location = useLocation()
  const [details, setDetails] = useState<Record<string, Run>>({})
  const ids = p.runs.map((r) => r.id).join(',')

  useEffect(() => {
    if (!ids) return
    let stale = false
    Promise.all(ids.split(',').map((id) => runsApi.get(id)))
      .then((list) => {
        if (!stale) setDetails(Object.fromEntries(list.map((r) => [r.id, r])))
      })
      .catch((e: Error) => p.onError(e.message))
    return () => {
      stale = true
    }
  }, [ids]) // eslint-disable-line react-hooks/exhaustive-deps

  const label = (type: string) => p.evalTypes.find((t) => t.type === type)?.label ?? type
  const back = { from: location.pathname + location.search, label: 'zurück zur Session' }

  return (
    <div className="task-runs">
      {p.runs.length === 0 && <p className="small muted">Kein Run in diesem Schritt.</p>}
      {p.runs.map((r, i) => {
        const d = details[r.id]
        const missed = d ? d.evals.filter((e) => !e.passed) : []
        return (
          <Link key={r.id} to={`/runs/${r.id}`} state={back} className="task-run">
            <span className="mono muted">{i + 1}</span>
            <span>
              {r.status === 'DONE' ? (
                <span className={`pill sm ${r.passed ? 'pass' : 'fail'}`}>{r.passed ? 'bestanden' : 'nicht bestanden'}</span>
              ) : (
                <span className="pill sm fail">{r.status === 'BUDGET_EXCEEDED' ? 'Budget' : r.status === 'FAILED' ? 'Fehler' : r.status}</span>
              )}
            </span>
            <span className="task-run-body">
              {missed.map((e, k) => (
                <span key={k} className="task-run-missed">
                  ✗ {e.subject ? `${e.subject} ${e.rationale ?? ''}` : e.rationale ?? label(e.type)}
                </span>
              ))}
              <span className="task-run-answer">{(r.answer ?? r.error ?? '').trim() || 'keine Antwort'}</span>
            </span>
          </Link>
        )
      })}
      <Link to={`/tasks/${encodeURIComponent(p.taskId)}`} className="link small">
        Task-Definition ansehen
      </Link>
    </div>
  )
}
