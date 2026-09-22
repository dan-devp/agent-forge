/** The areas of the workspace. Separate from the component so fast refresh keeps working. */
export type Area = 'models' | 'agents' | 'tasks' | 'runs'

export const AREAS: { id: Area; label: string; hint: string; ready: boolean }[] = [
  { id: 'models', label: 'Modelle', hint: 'Endpunkt, Modell-Id, Schlüssel', ready: true },
  { id: 'agents', label: 'Agenten', hint: 'Modell + Systemprompt', ready: false },
  { id: 'tasks', label: 'Aufgaben', hint: 'Eingabe + Evals', ready: false },
  { id: 'runs', label: 'Läufe', hint: 'Starten, Trace, Urteile', ready: false },
]
