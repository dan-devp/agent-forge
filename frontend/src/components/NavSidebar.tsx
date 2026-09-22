import { AREAS, type Area } from './areas'

type Props = {
  area: Area
  onArea: (area: Area) => void
}

/** Navigation, nothing else. Areas not built yet are shown but disabled, so the shape of the whole is visible. */
export function NavSidebar(p: Props) {
  return (
    <aside className="sidebar">
      <nav className="nav" aria-label="Bereiche">
        {AREAS.map((a) => (
          <button
            key={a.id}
            className={`nav-item ${p.area === a.id ? 'active' : ''}`}
            onClick={() => p.onArea(a.id)}
            disabled={!a.ready}
            title={a.ready ? undefined : 'kommt als nächstes'}
          >
            <span className="nav-label">{a.label}</span>
            <span className="nav-hint">{a.ready ? a.hint : 'bald'}</span>
          </button>
        ))}
      </nav>
    </aside>
  )
}
