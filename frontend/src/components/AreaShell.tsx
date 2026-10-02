import { NavLink, Outlet, useLocation } from 'react-router'
import type { Status } from '../api/client'
import { AREAS, GROUP_LABEL, type AreaGroup } from './areas'
import { KINDS } from './kinds'

type Props = {
  status: Status | null
  error: string | null
  onDismissError: () => void
}

const GROUPS: AreaGroup[] = ['build', 'measure', 'lookup']

/** Inside an area: sidebar grouped by what one does there, the active pill terracotta, content to the right. */
export function AreaShell(p: Props) {
  const location = useLocation()
  const current = AREAS.find((a) => location.pathname.startsWith(a.path))

  return (
    <div className="area">
      <aside className="sidebar" aria-label="Bereiche">
        {GROUPS.map((g) => (
          <div key={g} className="nav-group">
            <span className="nav-group-label">{GROUP_LABEL[g]}</span>
            {AREAS.filter((a) => a.group === g).map((a) => (
              <NavLink
                key={a.id}
                to={a.path}
                className={({ isActive }) => `nav-item ${isActive ? 'active' : ''} ${a.ready ? '' : 'disabled'}`}
                title={a.hint}
              >
                <span className="nav-label">
                  <span className={`nav-sign t-${KINDS[a.kind].tone}`} aria-hidden="true">
                    {KINDS[a.kind].sign}
                  </span>
                  {a.label}
                </span>
                <span className="count">{p.status ? a.count(p.status) : ''}</span>
              </NavLink>
            ))}
          </div>
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
