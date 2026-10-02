/**
 * The building blocks as the UI marks them: a colour (tokens in index.css) and a sign, so a
 * mention says what it is before anyone reads the id. Eval and tool share the grey of the
 * catalogues: provided by the system, not authored.
 */
export type Kind = 'model' | 'agent' | 'task' | 'training' | 'run' | 'eval' | 'tool'

export const KINDS: Record<Kind, { label: string; sign: string; tone: string; href: (id: string) => string }> = {
  model: { label: 'Model', sign: '◆', tone: 'model', href: (id) => `/models/${encodeURIComponent(id)}` },
  agent: { label: 'Agent', sign: '●', tone: 'agent', href: (id) => `/agents/${encodeURIComponent(id)}` },
  task: { label: 'Task', sign: '■', tone: 'task', href: (id) => `/tasks/${encodeURIComponent(id)}` },
  training: { label: 'Training', sign: '▲', tone: 'training', href: (id) => `/trainings/${encodeURIComponent(id)}` },
  run: { label: 'Run', sign: '▸', tone: 'run', href: (id) => `/runs/${encodeURIComponent(id)}` },
  eval: { label: 'Eval', sign: '○', tone: 'catalog', href: () => '/evals' },
  tool: { label: 'Tool', sign: '○', tone: 'catalog', href: () => '/tools' },
}
