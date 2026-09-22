import { NavLink, Outlet, useLocation } from 'react-router'
import type { Status } from '../api/client'
import { AREAS } from './areas'

type Props = {
  status: Status | null
  error: string | null
  onDismissError: () => void
}

/** Inside an area: sidebar with one pill per area, the active one terracotta, content to the right. */
export function AreaShell(p: Props) {
  const location = useLocation()
  const current = AREAS.find((a) => location.pathname.startsWith(a.path))

  return (
    <div className="area">
      <aside className="sidebar" aria-label="Bereiche">
        {AREAS.map((a) => (
          <NavLink
            key={a.id}
            to={a.path}
            className={({ isActive }) => `nav-item ${isActive ? 'active' : ''} ${a.ready ? '' : 'disabled'}`}
          >
            <span>{a.label}</span>
            <span className="count">{p.status ? a.count(p.status) : ''}</span>
          </NavLink>
        ))}
      </aside>
      <main className="content">
        {p.error && (
          <div className="banner" role="alert">
            <span>{p.error}</span>
            <button className="btn sm" onClick={p.onDismissError}>
              ok
            </button>
          </div>
        )}
        {current && !current.ready ? (
          <section className="card">
            <div className="card-head">
              <h2>{current.label}</h2>
            </div>
            <p className="empty">Dieser Bereich kommt als Nächstes. Die API dafür steht bereits.</p>
          </section>
        ) : (
          <Outlet />
        )}
      </main>
    </div>
  )
}
