import type { AgentRun, TrainingDetail } from '../api/client'
import { Badge, PassBadge, RunStatusBadge, TrainingStatusBadge } from './Badge'
import { RUN_STATUS_TEXT } from './status'
import { LearningCurve } from './LearningCurve'
import { mean, pct, range } from './format'
import { RunStrip } from './RunStrip'
import { RawJson } from './RawJson'

type Props = {
  detail: TrainingDetail
  selectedRunId: string | null
  onOpenRun: (id: string) => void
}

/** One training in full: curve, key figures, its runs and what its strategies remembered. */
export function TrainingDetailView({ detail, selectedRunId, onOpenRun }: Props) {
  const s = detail.statistics
  const t = detail.training

  return (
    <div className="panel-body">
      <div className="run-head">
        <TrainingStatusBadge training={t} />
        <span>
          {t.taskId} v{t.taskVersion}
        </span>
        <span className="dot">Profil {t.profileId}</span>
        <span className="dot">Agent {t.agentId ?? '–'}</span>
        <span className="dot">SOUL {t.soulId}</span>
        <span className="dot">{t.providerId}</span>
      </div>

      {t.status === 'RUNNING' && (
        <div className="progress">
          <span style={{ width: `${(t.completed / t.repetitions) * 100}%` }} />
        </div>
      )}
      {t.error && <div className="error">{t.error}</div>}

      <RunStrip points={s.timeline} repetitions={s.repetitions} />
      <LearningCurve points={s.timeline} repetitions={s.repetitions} />

      <div className="kpis">
        <div className="kpi">
          <div className="label">bestanden</div>
          <div className="value">
            {s.passed}/{s.graded}
          </div>
          <div className="sub">
            {pct(s.passRate)} · 95 %: {Math.round(s.passRateLower * 100)}–{Math.round(s.passRateUpper * 100)} %
          </div>
        </div>
        <div className="kpi">
          <div className="label">1. → 2. Hälfte</div>
          <div className="value">
            {pct(s.firstHalfPassRate)} → {pct(s.secondHalfPassRate)}
          </div>
          <div className="sub">{s.firstPassAt ? `erster Treffer bei Lauf ${s.firstPassAt}` : 'kein Treffer'}</div>
        </div>
        <div className="kpi">
          <div className="label">pass@k · pass^k</div>
          <div className="value">
            {s.passAtK ? '✓' : '✗'} · {s.passHatK ? '✓' : '✗'}
          </div>
          <div className="sub">
            {s.flaky ? 'flaky' : s.passHatK ? 'zuverlässig' : s.passAtK ? 'kann, aber nicht immer' : 'nie bestanden'}
          </div>
        </div>
        <div className="kpi">
          <div className="label">Tokens Ø</div>
          <div className="value">{mean(s.tokens)}</div>
          <div className="sub">{range(s.tokens)}</div>
        </div>
        <div className="kpi">
          <div className="label">Dauer Ø</div>
          <div className="value">{mean(s.durationMs, ' ms')}</div>
          <div className="sub">{range(s.durationMs)}</div>
        </div>
        <div className="kpi">
          <div className="label">Tool-Aufrufe Ø</div>
          <div className="value">{mean(s.toolCalls)}</div>
          <div className="sub">
            {Object.entries(s.statusCounts)
              .map(([k, v]) => `${RUN_STATUS_TEXT[k as AgentRun['status']] ?? k} ${v}`)
              .join(' · ') || '–'}
          </div>
        </div>
      </div>

      {detail.runs.length > 0 && (
        <table className="table">
          <thead>
            <tr>
              <th>#</th>
              <th>Status</th>
              <th>Bewertung</th>
              <th className="num">Tokens</th>
              <th className="num">ms</th>
              <th className="num">Schritte</th>
            </tr>
          </thead>
          <tbody>
            {detail.runs.map((r, i) => (
              <tr key={r.id} className={`clickable ${selectedRunId === r.id ? 'selected' : ''}`} onClick={() => onOpenRun(r.id)}>
                <td className="num">{i + 1}</td>
                <td>
                  <RunStatusBadge status={r.status} />
                </td>
                <td>
                  {r.scores.length === 0 ? (
                    <span className="faint">–</span>
                  ) : (
                    r.scores.map((sc) => (
                      <span key={sc.grader}>
                        <PassBadge passed={sc.passed} /> <span className="small muted">{Math.round(sc.score * 100)} %</span>
                      </span>
                    ))
                  )}
                </td>
                <td className="num">{r.totalTokens ?? '–'}</td>
                <td className="num">{r.durationMs ?? '–'}</td>
                <td className="num">{r.steps}</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}

      <div className="section-title">
        <span>Gelernt · {detail.memory.length} Einträge</span>
      </div>
      <RawJson label={`GET /api/trainings/${t.id}`} value={detail} />

      {detail.memory.length === 0 ? (
        <p className="hint">Nichts. Bei Baseline erwartet — keine Strategie schreibt hier.</p>
      ) : (
        <ul className="memory">
          {detail.memory.map((m) => {
            const runIndex = detail.runs.findIndex((r) => r.id === m.runId)
            return (
              <li key={m.id}>
                <Badge tone="accent" mono>
                  {m.strategyId}
                </Badge>
                <Badge tone="neutral">{m.kind}</Badge>
                <span className="small faint">{runIndex >= 0 ? `Lauf ${runIndex + 1}` : 'außerhalb'}</span>
                <div className="content">{m.content}</div>
              </li>
            )
          })}
        </ul>
      )}
    </div>
  )
}
