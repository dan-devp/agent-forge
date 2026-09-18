import type { TrainingStatistics } from '../api/client'

/** Shared formatting for statistics — kept out of the components so fast refresh keeps working. */

export function pct(v: number | null | undefined) {
  return v === null || v === undefined ? '–' : `${Math.round(v * 100)} %`
}

export function mean(a: TrainingStatistics['tokens'], unit = '') {
  return a.count === 0 ? '–' : `${Math.round(a.mean)}${unit}`
}

export function range(a: TrainingStatistics['tokens']) {
  return a.count === 0 ? '' : `${a.min} – ${a.max} · n=${a.count}`
}
