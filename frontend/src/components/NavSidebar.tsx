import { AREAS, type Area } from './areas'

type Props = {
  area: Area
  onArea: (area: Area) => void
}

/**
 * Navigation, nothing else. Who works and on which model is decided where a run is started —
 * a setting far away from its button gets used without being read.
 */
export function NavSidebar(p: Props) {
  return (
    <aside className="sidebar">
      <nav className="nav" aria-label="Bereiche">
        {AREAS.map((a) => (
          <button key={a.id} className={`nav-item ${p.area === a.id ? 'active' : ''}`} onClick={() => p.onArea(a.id)}>
            <span className="nav-label">{a.label}</span>
            <span className="nav-hint">{a.hint}</span>
          </button>
        ))}
      </nav>
    </aside>
  )
}
