/** The areas of the workspace. Separate from the component so fast refresh keeps working. */
export type Area = 'training' | 'runs' | 'agents' | 'tasks' | 'strategies' | 'settings'

export const AREAS: { id: Area; label: string; hint: string }[] = [
  { id: 'training', label: 'Training', hint: 'Experimente und Trainings' },
  { id: 'runs', label: 'Läufe', hint: 'Einzellauf, Historie, Chat' },
  { id: 'agents', label: 'Agenten', hint: 'Modell, SOUL, Werkzeuge' },
  { id: 'tasks', label: 'Aufgaben', hint: 'Auftrag und Erfolgskriterien' },
  { id: 'strategies', label: 'Strategien', hint: 'Katalog und Profile' },
  { id: 'settings', label: 'Einstellungen', hint: 'Modelle, Werkzeuge, SOULs' },
]
