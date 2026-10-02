import type { AgentView, Decision, ToolAccess, TrainingDetail } from '../api/client'
import { diffLines } from './training'

/** Where a version came from, if a coach wrote it: which training, and what the gate made of it. */
export type Origin = { trainingId: string; sessionId: string; decision: Decision }

/** Every version a coach proposed, from all sessions that trained this agent. */
export function originsOf(agentId: string, trainings: TrainingDetail[]): Record<number, Origin> {
  const out: Record<number, Origin> = {}
  for (const t of trainings) {
    for (const s of t.sessions) {
      if (s.agentId !== agentId) continue
      for (const st of s.steps) {
        // Step 0 measures what was there; only later steps are the coach's versions.
        if (st.seq > 0 && st.agentVersion !== null) out[st.agentVersion] = { trainingId: t.training.id, sessionId: s.id, decision: st.decision }
      }
    }
  }
  return out
}

function toolsLabel(t: ToolAccess) {
  if (t === 'all') return 'alle'
  if (t === 'none') return 'keine'
  return t.length === 0 ? 'keine' : t.join(', ')
}

const lines = (n: number) => `${n} Zeile${n === 1 ? '' : 'n'}`

/**
 * What a version changed against the one before, in a few words: the prompt, the model,
 * the tools. A prompt that equals an older one says so — a training that ends on a rejected
 * attempt writes the best prompt again, and that should not look like news.
 */
export function changeOf(cur: AgentView, prev: AgentView | null, all: AgentView[]): string[] {
  if (!prev) return ['erste Version']
  const out: string[] = []
  if (cur.systemPrompt !== prev.systemPrompt) {
    const same = all.find((a) => a.version < cur.version && a.version !== prev.version && a.systemPrompt === cur.systemPrompt)
    if (same) {
      out.push(`Prompt wie in v${same.version}`)
    } else {
      const diff = diffLines(prev.systemPrompt, cur.systemPrompt)
      const added = diff.filter((l) => l.kind === 'add' && l.text.trim() !== '').length
      const removed = diff.filter((l) => l.kind === 'del' && l.text.trim() !== '').length
      out.push(
        removed === 0 ? `Prompt um ${lines(added)} ergänzt` : added === 0 ? `Prompt um ${lines(removed)} gekürzt` : 'Prompt umgeschrieben',
      )
    }
  }
  if (cur.model !== prev.model) out.push(`Model ${prev.model} → ${cur.model}`)
  if (JSON.stringify(cur.tools) !== JSON.stringify(prev.tools)) out.push(`Tools ${toolsLabel(prev.tools)} → ${toolsLabel(cur.tools)}`)
  return out.length > 0 ? out : ['nur Beschreibung geändert']
}

/** The version in full, for a tooltip: model, tools, and the start of the prompt. */
export function versionTitle(a: AgentView) {
  const prompt = a.systemPrompt.length > 400 ? a.systemPrompt.slice(0, 399) + '…' : a.systemPrompt
  return `v${a.version} · Model ${a.model} · Tools ${toolsLabel(a.tools)}\n\n${prompt}`
}
