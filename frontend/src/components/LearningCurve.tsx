import type { RunPoint } from '../api/client'

/**
 * One column per run, in order. Height is the grader's score, colour the verdict; ungraded
 * runs are hollow. The line is the running pass rate — flat for a baseline, rising if a
 * strategy actually learns.
 */
export function LearningCurve({ points, repetitions }: { points: RunPoint[]; repetitions: number }) {
  const n = Math.max(repetitions, points.length, 1)
  const width = 640
  const height = 140
  const pad = { l: 28, r: 8, t: 8, b: 22 }
  const innerW = width - pad.l - pad.r
  const innerH = height - pad.t - pad.b
  const colW = innerW / n
  const barW = Math.max(4, Math.min(28, colW * 0.6))

  let passed = 0
  let graded = 0
  const running: { x: number; y: number }[] = []
  points.forEach((p, i) => {
    if (p.passed !== null) {
      graded++
      if (p.passed) passed++
    }
    const rate = graded === 0 ? null : passed / graded
    if (rate !== null) running.push({ x: pad.l + colW * i + colW / 2, y: pad.t + innerH * (1 - rate) })
  })

  const path = running.map((pt, i) => `${i === 0 ? 'M' : 'L'}${pt.x.toFixed(1)},${pt.y.toFixed(1)}`).join(' ')

  return (
    <svg className="curve" viewBox={`0 0 ${width} ${height}`} role="img" aria-label="Lernkurve">
      {[0, 0.5, 1].map((g) => (
        <g key={g}>
          <line x1={pad.l} x2={width - pad.r} y1={pad.t + innerH * (1 - g)} y2={pad.t + innerH * (1 - g)} className="grid" />
          <text x={pad.l - 6} y={pad.t + innerH * (1 - g) + 3} className="axis" textAnchor="end">
            {Math.round(g * 100)}
          </text>
        </g>
      ))}
      {Array.from({ length: n }, (_, i) => {
        const p = points[i]
        const x = pad.l + colW * i + colW / 2 - barW / 2
        if (!p) {
          return <rect key={i} x={x} y={pad.t + innerH - 2} width={barW} height={2} className="bar pending" />
        }
        const score = p.score ?? 0
        const h = Math.max(2, innerH * score)
        const cls = p.passed === null ? 'ungraded' : p.passed ? 'pass' : 'fail'
        return (
          <g key={i}>
            <rect x={x} y={pad.t + innerH - h} width={barW} height={h} className={`bar ${cls}`}>
              <title>
                Lauf {p.index}: {p.passed === null ? 'nicht bewertet' : p.passed ? 'bestanden' : 'nicht bestanden'}
                {p.score !== null ? ` · ${Math.round(p.score * 100)} %` : ''} · {p.status}
              </title>
            </rect>
            <text x={x + barW / 2} y={height - 6} className="axis" textAnchor="middle">
              {p.index}
            </text>
          </g>
        )
      })}
      {running.length > 1 && <path d={path} className="running" />}
    </svg>
  )
}
