import { useState } from 'react'
import type { AgentRun } from '../api/client'
import { PassBadge, RunStatusBadge, StepBadge } from './Badge'

/** One run in full: parameters, answer, scores, trace. Shared by run, history and training. */
export function RunDetail({ run, compact = false }: { run: AgentRun; compact?: boolean }) {
  const [promptOpen, setPromptOpen] = useState(false)

  return (
    <div className="panel-body">
      <div className="run-head">
        <RunStatusBadge status={run.status} />
        <span>{run.taskId ? `Aufgabe ${run.taskId} v${run.taskVersion}` : 'freier Auftrag'}</span>
        <span className="dot">
          SOUL {run.soulId} v{run.soulVersion}
        </span>
        <span className="dot">
          Profil {run.profileId ?? '–'} v{run.profileVersion ?? '–'}
        </span>
        <span className="dot">
          {run.providerId} · {run.model ?? 'unbekannt'}
          {run.temperature != null && ` · T ${run.temperature}`}
        </span>
        <span className="dot">
          {run.steps} Schritte · {run.durationMs} ms
          {run.totalTokens != null && ` · ${run.totalTokens} Tokens`}
        </span>
      </div>

      <div className="run-head">
        <span className="faint">Tools:</span>
        {run.tools.length === 0 ? (
          <span className="faint">keine angeboten</span>
        ) : (
          run.tools.map((t) => (
            <code key={t} className="small">
              {t}
            </code>
          ))
        )}
        {run.systemPrompt && (
          <button className="link small" onClick={() => setPromptOpen(!promptOpen)}>
            {promptOpen ? 'System-Prompt verbergen' : 'System-Prompt zeigen'}
          </button>
        )}
      </div>
      {promptOpen && run.systemPrompt && <pre className="prompt-box">{run.systemPrompt}</pre>}

      {run.answer && <div className="answer">{run.answer}</div>}
      {run.error && <div className="error">{run.error}</div>}

      {run.scores.length > 0 && (
        <>
          <div className="section-title">Bewertung</div>
          <div className="scores">
            {run.scores.map((s) => (
              <div key={s.grader} className={`score ${s.passed ? '' : 'failed'}`}>
                <PassBadge passed={s.passed} />
                <span className="small muted">
                  {s.grader} · {Math.round(s.score * 100)} %
                </span>
                <div className="bar">
                  <span style={{ width: `${Math.round(s.score * 100)}%` }} />
                </div>
                {s.rationale && <pre>{s.rationale}</pre>}
              </div>
            ))}
          </div>
        </>
      )}

      {!compact && run.trace.length > 0 && (
        <>
          <div className="section-title">Trace</div>
          <ol className="trace">
            {run.trace.map((s) => (
              <li key={s.seq} className="step">
                <span className="seq">{s.seq}</span>
                <StepBadge type={s.type} />
                <div className="body">
                  <span className="name">
                    {s.name}
                    {s.durationMs != null && <span className="faint"> · {s.durationMs} ms</span>}
                    {s.tokens != null && <span className="faint"> · {s.tokens} Tokens</span>}
                  </span>
                  {s.input && (
                    <div className="io">
                      <b>{s.type === 'VERIFY' ? 'abgelehnt' : 'ein'}</b>
                      {s.input}
                    </div>
                  )}
                  {s.output && (
                    <div className="io">
                      <b>{s.type === 'VERIFY' ? 'Feedback' : 'aus'}</b>
                      {s.output}
                    </div>
                  )}
                  {s.error && <div className="io err">Fehler: {s.error}</div>}
                </div>
              </li>
            ))}
          </ol>
        </>
      )}
    </div>
  )
}
