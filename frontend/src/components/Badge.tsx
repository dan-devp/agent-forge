import type { AgentRun, RunStep, Series } from '../api/client'
import { RUN_STATUS_TEXT } from './status'

type Tone = 'ok' | 'warn' | 'bad' | 'info' | 'accent' | 'neutral'

export function Badge({ tone = 'neutral', mono, children }: { tone?: Tone; mono?: boolean; children: React.ReactNode }) {
  return <span className={`badge ${tone === 'neutral' ? '' : tone} ${mono ? 'mono' : ''}`.trim()}>{children}</span>
}

const RUN_STATUS_TONE: Record<AgentRun['status'], Tone> = {
  RUNNING: 'info',
  DONE: 'ok',
  BUDGET_EXCEEDED: 'warn',
  VERIFICATION_FAILED: 'warn',
  FAILED: 'bad',
}

export function RunStatusBadge({ status }: { status: AgentRun['status'] }) {
  return <Badge tone={RUN_STATUS_TONE[status]}>{RUN_STATUS_TEXT[status]}</Badge>
}

export function SeriesStatusBadge({ series }: { series: Series }) {
  if (series.status === 'RUNNING') {
    return (
      <Badge tone="info">
        läuft · {series.completed}/{series.repetitions}
      </Badge>
    )
  }
  return <Badge tone={series.status === 'DONE' ? 'ok' : 'bad'}>{series.status === 'DONE' ? 'fertig' : 'fehlgeschlagen'}</Badge>
}

const STEP_TONE: Record<RunStep['type'], Tone> = {
  MODEL: 'neutral',
  TOOL: 'info',
  VERIFY: 'warn',
}

export function StepBadge({ type }: { type: RunStep['type'] }) {
  return (
    <Badge tone={STEP_TONE[type]} mono>
      {type}
    </Badge>
  )
}

export function PassBadge({ passed }: { passed: boolean }) {
  return <Badge tone={passed ? 'ok' : 'bad'}>{passed ? 'bestanden' : 'nicht bestanden'}</Badge>
}
