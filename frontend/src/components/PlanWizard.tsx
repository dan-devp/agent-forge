import { useState } from 'react'
import type { AgentListResponse, Plan, PlanBody, ProfileListResponse, StatusResponse, TaskSummary } from '../api/client'
import { Badge } from './Badge'
import { Modal } from './Modal'

type Props = {
  plan: Plan | null
  agents: AgentListResponse | null
  status: StatusResponse | null
  tasks: TaskSummary[] | null
  profiles: ProfileListResponse | null
  busy: boolean
  onSave: (body: PlanBody) => void
  onClose: () => void
}

const STEPS = ['Agent', 'Modell', 'Aufgabe', 'Trainings', 'Umfang'] as const

/**
 * Five steps, one decision each, and Save at the end — deliberately not a start button. What
 * is worth running twice is worth writing down first; starting is a separate act on the plan.
 */
export function PlanWizard(p: Props) {
  const [step, setStep] = useState(0)
  const [agent, setAgent] = useState(p.plan?.agentId ?? p.agents?.defaultAgent ?? '')
  const [provider, setProvider] = useState(p.plan?.providerId ?? '')
  const [task, setTask] = useState(p.plan?.taskId ?? '')
  const [profiles, setProfiles] = useState<string[]>(p.plan?.profiles ?? [])
  const [includeBaseline, setIncludeBaseline] = useState(p.plan?.includeBaseline ?? true)
  const [repetitions, setRepetitions] = useState(p.plan?.repetitions ?? 5)
  const [name, setName] = useState(p.plan?.name ?? '')
  const [description, setDescription] = useState(p.plan?.description ?? '')

  const selectedAgent = p.agents?.agents.find((a) => a.id === agent)
  const selectedTask = p.tasks?.find((t) => t.id === task)
  const trainings = profiles.length + (includeBaseline ? 1 : 0)
  const complete = task !== '' && trainings > 0 && repetitions >= 1

  const toggle = (id: string) => setProfiles((s) => (s.includes(id) ? s.filter((x) => x !== id) : [...s, id]))

  const save = () =>
    p.onSave({
      name: name || `${selectedTask?.name ?? task} · ${profiles.join(', ') || 'nur Baseline'}`,
      description: description || undefined,
      task,
      agent: agent || undefined,
      provider: provider || undefined,
      profiles,
      includeBaseline,
      repetitions,
    })

  return (
    <Modal title={p.plan ? 'Plan bearbeiten' : 'Neuer Plan'} onClose={p.onClose}>
      <div className="steps">
        {STEPS.map((label, i) => (
          <button key={label} className={`step-chip ${i === step ? 'on' : ''}`} onClick={() => setStep(i)}>
            <span className="step-no">{i + 1}</span>
            {label}
          </button>
        ))}
      </div>

      <div className="panel-body">
        {step === 0 && (
          <>
            <div className="field">
              <label>Wer arbeitet</label>
              <select value={agent} onChange={(e) => setAgent(e.target.value)} disabled={!p.agents}>
                {p.agents?.agents.map((a) => (
                  <option key={a.id} value={a.id}>
                    {a.name} · v{a.version}
                  </option>
                ))}
              </select>
            </div>
            {selectedAgent && (
              <div className="criteria">
                <Badge tone="neutral">SOUL {selectedAgent.soul ?? 'aktive'}</Badge>
                <Badge tone={selectedAgent.toolMode === 'ALL' ? 'warn' : 'neutral'}>
                  {selectedAgent.effectiveTools.length} Werkzeug(e)
                </Badge>
              </div>
            )}
            <p className="hint">
              Der Agent bleibt über alle Trainings gleich — sonst misst der Vergleich zwei Unterschiede auf einmal.
            </p>
          </>
        )}

        {step === 1 && (
          <>
            <div className="field">
              <label>Modell</label>
              <select value={provider} onChange={(e) => setProvider(e.target.value)} disabled={!p.status}>
                <option value="">Vorgabe des Agenten</option>
                {p.status?.providers.map((x) => (
                  <option key={x.id} value={x.id}>
                    {x.label} · {x.model}
                    {x.configured ? '' : ' (nicht konfiguriert)'}
                  </option>
                ))}
              </select>
            </div>
            <p className="hint">
              Leer heißt: was der Agent in seiner Datei vorgibt. Ein hier gesetztes Modell überschreibt ihn — für alle
              Trainings, und es steht später an jedem Lauf.
            </p>
          </>
        )}

        {step === 2 && (
          <>
            <div className="field">
              <label>Aufgabe</label>
              <select value={task} onChange={(e) => setTask(e.target.value)} disabled={!p.tasks}>
                <option value="">— wählen —</option>
                {p.tasks?.map((t) => (
                  <option key={t.id} value={t.id}>
                    {t.name} · v{t.version} · Stufe {t.difficulty}
                  </option>
                ))}
              </select>
            </div>
            <p className="hint">
              Nur eine benannte Aufgabe hat Version und Erfolgskriterien — ohne beides gäbe es nichts zu messen.
            </p>
          </>
        )}

        {step === 3 && (
          <>
            <div className="section-title">Ein Training je Profil</div>
            <div className="criteria">
              {p.profiles?.profiles
                .filter((x) => x.id !== p.profiles?.defaultProfile)
                .map((x) => (
                  <button key={x.id} className={`chip-toggle ${profiles.includes(x.id) ? 'on' : ''}`} onClick={() => toggle(x.id)}>
                    {x.name} · v{x.version}
                  </button>
                ))}
              <button className={`chip-toggle ${includeBaseline ? 'on' : ''}`} onClick={() => setIncludeBaseline(!includeBaseline)}>
                Baseline als Bezugslinie
              </button>
            </div>
            <p className="hint">
              Ohne Baseline misst der Vergleich nichts: eine steigende Quote kann genauso gut Modellstreuung sein.
            </p>
          </>
        )}

        {step === 4 && (
          <>
            <div className="field">
              <label>Wiederholungen je Training</label>
              <input type="number" min={1} max={50} value={repetitions} onChange={(e) => setRepetitions(Number(e.target.value))} />
            </div>
            <div className="field">
              <label>Name</label>
              <input value={name} onChange={(e) => setName(e.target.value)} placeholder="z. B. Reflexion gegen Baseline" />
            </div>
            <div className="field">
              <label>Notiz (optional)</label>
              <textarea value={description} onChange={(e) => setDescription(e.target.value)} rows={2} placeholder="Was soll dieser Vergleich zeigen?" />
            </div>
            <p className="hint">
              {trainings} Training(s) × {repetitions} Läufe = <strong>{trainings * repetitions} Modellaufrufe</strong> je Start,
              nacheinander.
            </p>
          </>
        )}

        <div className="toolbar">
          <button className="btn" onClick={() => setStep(Math.max(0, step - 1))} disabled={step === 0}>
            zurück
          </button>
          {step < STEPS.length - 1 ? (
            <button className="btn" onClick={() => setStep(step + 1)}>
              weiter
            </button>
          ) : null}
          <span className="spacer" />
          <button className="btn primary" onClick={save} disabled={p.busy || !complete}>
            Plan speichern
          </button>
        </div>
        {!complete && <p className="hint faint">Zum Speichern fehlen: {[!task && 'Aufgabe', trainings === 0 && 'mindestens ein Training'].filter(Boolean).join(', ')}.</p>}
      </div>
    </Modal>
  )
}
