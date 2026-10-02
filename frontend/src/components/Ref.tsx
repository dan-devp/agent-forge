import type { MouseEvent } from 'react'
import { Link } from 'react-router'
import { KINDS, type Kind } from './kinds'

/** The sign of a building block in its colour, e.g. before a heading. */
export function Sign({ kind }: { kind: Kind }) {
  const k = KINDS[kind]
  return (
    <span className={`sign t-${k.tone}`} title={k.label} aria-label={k.label}>
      {k.sign}
    </span>
  )
}

/**
 * A mention of a building block: sign + id (+ version) in the block's colour, a link to it.
 * One component everywhere, so a task looks like a task in the matrix, in a training and in
 * a run. `link={false}` inside elements that are links already.
 */
export function Ref(p: { kind: Kind; id: string; version?: number | null; label?: string; link?: boolean; title?: string }) {
  const k = KINDS[p.kind]
  const body = (
    <>
      <span className="ref-sign" aria-hidden="true">
        {k.sign}
      </span>
      <span className="ref-id">{p.label ?? p.id}</span>
      {p.version != null && <span className="ref-version">v{p.version}</span>}
    </>
  )
  const className = `ref t-${k.tone}`
  const title = p.title ?? `${k.label} ${p.id}${p.version != null ? ` v${p.version}` : ''}`
  if (p.link === false) {
    return (
      <span className={className} title={title}>
        {body}
      </span>
    )
  }
  // A ref inside a clickable row must not trigger the row as well.
  const stop = (e: MouseEvent) => e.stopPropagation()
  return (
    <Link to={k.href(p.id)} className={className} title={title} onClick={stop}>
      {body}
    </Link>
  )
}
