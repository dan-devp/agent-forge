import type { Score, Session, SessionStatus, TrainingStep } from '../api/client'

export function isOpen(s: { status: SessionStatus }) {
  return s.status === 'RUNNING' || s.status === 'WAITING'
}

/**
 * The most a session can cost: the baseline and every proposal measure all train and
 * holdout tasks n times, and each proposal is one coach call. It may stop earlier.
 */
export function trainingCost(t: { train: string[]; holdout: string[]; repetitions: number; maxSteps: number }) {
  const perMeasure = (t.train.length + t.holdout.length) * t.repetitions
  return { perMeasure, runs: perMeasure * (1 + t.maxSteps), coachCalls: t.maxSteps }
}

const pct = (passed: number, total: number) => (total === 0 ? '–' : `${Math.round((100 * passed) / total)}%`)
const share = (passed: number, total: number) => `${pct(passed, total)} (${passed}/${total})`

/**
 * The gate's decision as a sentence, from the measurements: what the candidate was compared
 * with (the best version before it) and how train and holdout moved. Null when the numbers
 * are not there; the caller then shows the stored reason.
 */
export function decisionSentence(step: TrainingStep, steps: TrainingStep[]): string | null {
  const c = step.score
  if (!c || step.seq === 0) return null
  const before = steps.filter((s) => s.seq < step.seq && s.score && (s.seq === 0 || s.decision === 'ACCEPTED')).at(-1)
  const b: Score | null = before?.score ?? null
  if (!b) return null
  // Compare rates without dividing: a/b against c/d as a*d against c*b.
  const trend = (cp: number, ct: number, bp: number, bt: number) => Math.sign(cp * bt - bp * ct)
  const moved = (what: string, t: number, cp: number, ct: number, bp: number, bt: number) =>
    t > 0
      ? `${what} rose from ${share(bp, bt)} to ${share(cp, ct)}`
      : t < 0
        ? `${what} fell from ${share(bp, bt)} to ${share(cp, ct)}`
        : `${what} stayed at ${share(cp, ct)}`

  const trainTrend = trend(c.trainPassed, c.trainTotal, b.trainPassed, b.trainTotal)
  const parts = [moved('Train', trainTrend, c.trainPassed, c.trainTotal, b.trainPassed, b.trainTotal)]
  const notes: string[] = []
  if (trainTrend === 0) notes.push('A tie counts as chance, not as an improvement.')
  if (c.holdoutTotal > 0 && b.holdoutTotal > 0) {
    const holdoutTrend = trend(c.holdoutPassed, c.holdoutTotal, b.holdoutPassed, b.holdoutTotal)
    parts.push(moved('Holdout', holdoutTrend, c.holdoutPassed, c.holdoutTotal, b.holdoutPassed, b.holdoutTotal))
    if (holdoutTrend < 0) notes.push('When the Holdout drops, the improvement was only memorized.')
  }
  const versus = before?.agentVersion != null ? `Compared with v${before.agentVersion}.` : ''
  return [`${parts.join(', ')}.`, ...notes, versus].filter(Boolean).join(' ')
}

/** Where a session started and where it got: the baseline and the last accepted measurement. */
export function journey(s: Session) {
  const baseline = s.steps.find((st) => st.seq === 0)?.score ?? null
  const best = s.steps.filter((st) => st.score && st.decision === 'ACCEPTED').at(-1)?.score ?? null
  return { baseline, best }
}

export type DiffLine = { kind: 'same' | 'add' | 'del'; text: string }

/** Line diff by longest common subsequence: prompts are short, the quadratic table is fine. */
export function diffLines(before: string, after: string): DiffLine[] {
  const a = before.split('\n')
  const b = after.split('\n')
  const lcs: number[][] = Array.from({ length: a.length + 1 }, () => new Array<number>(b.length + 1).fill(0))
  for (let i = a.length - 1; i >= 0; i--) {
    for (let j = b.length - 1; j >= 0; j--) {
      lcs[i][j] = a[i] === b[j] ? lcs[i + 1][j + 1] + 1 : Math.max(lcs[i + 1][j], lcs[i][j + 1])
    }
  }
  const out: DiffLine[] = []
  let i = 0
  let j = 0
  while (i < a.length && j < b.length) {
    if (a[i] === b[j]) {
      out.push({ kind: 'same', text: a[i] })
      i++
      j++
    } else if (lcs[i + 1][j] >= lcs[i][j + 1]) {
      out.push({ kind: 'del', text: a[i++] })
    } else {
      out.push({ kind: 'add', text: b[j++] })
    }
  }
  while (i < a.length) out.push({ kind: 'del', text: a[i++] })
  while (j < b.length) out.push({ kind: 'add', text: b[j++] })
  return out
}
