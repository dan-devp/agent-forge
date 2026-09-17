import type { AgentRun } from '../api/client'

export const RUN_STATUS_TEXT: Record<AgentRun['status'], string> = {
  RUNNING: 'läuft',
  DONE: 'fertig',
  BUDGET_EXCEEDED: 'Budget aufgebraucht',
  VERIFICATION_FAILED: 'Verifikation gescheitert',
  FAILED: 'fehlgeschlagen',
}
