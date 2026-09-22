import type { ReactNode } from 'react'

export type Tone = 'ok' | 'warn' | 'bad' | 'info' | 'accent' | 'solid' | 'neutral'

export function Badge({ tone = 'neutral', mono, children }: { tone?: Tone; mono?: boolean; children: ReactNode }) {
  return <span className={`badge ${tone === 'neutral' ? '' : tone} ${mono ? 'mono' : ''}`.trim()}>{children}</span>
}
