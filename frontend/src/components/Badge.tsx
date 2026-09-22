import type { AgentRun, Experiment, RunStep, Training } from '../api/client'
import { RUN_STATUS_TEXT } from './status'

type Tone = 'ok' | 'warn' | 'bad' | 'info' | 'accent' | 'solid' | 'neutral'

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

const TRAINING_STATUS: Record<Training['status'], { tone: Tone; text: string }> = {
  QUEUED: { tone: 'neutral', text: 'wartet' },
  RUNNING: { tone: 'info', text: 'läuft' },
  DONE: { tone: 'ok', text: 'fertig' },
  CANCELLED: { tone: 'warn', text: 'gestoppt' },
  FAILED: { tone: 'bad', text: 'fehlgeschlagen' },
}

export function TrainingStatusBadge({ training }: { training: Training }) {
  const s = TRAINING_STATUS[training.status]
  if (training.status === 'RUNNING' || training.status === 'CANCELLED') {
    return (
      <Badge tone={s.tone}>
        {s.text} · {training.completed}/{training.repetitions}
      </Badge>
    )
  }
  return <Badge tone={s.tone}>{s.text}</Badge>
}

const EXPERIMENT_STATUS: Record<Experiment['status'], { tone: Tone; text: string }> = {
  RUNNING: { tone: 'info', text: 'läuft' },
  DONE: { tone: 'ok', text: 'fertig' },
  CANCELLED: { tone: 'warn', text: 'gestoppt' },
  FAILED: { tone: 'bad', text: 'fehlgeschlagen' },
}

export function ExperimentStatusBadge({ status }: { status: Experiment['status'] }) {
  return <Badge tone={EXPERIMENT_STATUS[status].tone}>{EXPERIMENT_STATUS[status].text}</Badge>
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
