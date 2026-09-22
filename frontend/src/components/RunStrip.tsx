import type { RunPoint } from '../api/client'

/**
 * One square per run, in order: filled dark = passed, terracotta = failed, hollow = not run
 * yet. Small enough for a table cell, so a comparison row shows not just how often a training
 * passed but *when* — three failures then three passes is a different thing from the
 * reverse, and an aggregate hides exactly that.
 */
export function RunStrip({ points, repetitions }: { points: RunPoint[]; repetitions: number }) {
  const n = Math.max(repetitions, points.length, 1)

  return (
    <span className="runstrip" role="img" aria-label={`${points.length} von ${n} Läufen`}>
      {Array.from({ length: n }, (_, i) => {
        const p = points[i]
        const cls = !p ? 'pending' : p.passed === null ? 'ungraded' : p.passed ? 'pass' : 'fail'
        const title = !p
          ? `Lauf ${i + 1}: steht aus`
          : `Lauf ${p.index}: ${p.passed === null ? 'nicht bewertet' : p.passed ? 'bestanden' : 'nicht bestanden'} · ${p.status}`
        return <i key={i} className={cls} title={title} />
      })}
    </span>
  )
}
