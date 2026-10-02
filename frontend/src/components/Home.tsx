import { Link } from 'react-router'
import type { Status } from '../api/client'
import { AREAS } from './areas'

/** The root: one tile per area. Nothing is selected here, so no terracotta. */
export function Home({ status }: { status: Status | null }) {
  return (
    <div className="home">
      <div className="home-head">
        <h1>Trainingslager</h1>
        <p>Models, Agents und Tasks zusammenstellen. Runs aufzeichnen und bewerten. Einen Coach den Agent trainieren lassen.</p>
      </div>
      <div className="tiles">
        {AREAS.map((a) => (
          <Link key={a.id} to={a.path} className={`tile ${a.ready ? '' : 'disabled'} ${a.readonly ? 'readonly' : ''}`}>
            <div className="tile-head">
              <h2>{a.label}</h2>
              <span className="count">{status ? a.count(status) : '…'}</span>
            </div>
            <p className="hint">{a.hint}</p>
            <div className="foot">
              {!a.ready && <span className="pill ghost">kommt als Nächstes</span>}
              {a.readonly && <span className="pill ghost">vom System, nur ansehen</span>}
            </div>
          </Link>
        ))}
      </div>
    </div>
  )
}
