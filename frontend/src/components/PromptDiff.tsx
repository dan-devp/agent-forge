import { diffLines } from './training'

/** A prompt against the one before, line by line: added sage, removed rose, the rest muted. */
export function PromptDiff(p: { before: string; after: string; from: number | null; to: number | null }) {
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
