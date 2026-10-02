import { Link } from 'react-router'
import type { Status } from '../api/client'
import { AREAS } from './areas'
import { KINDS } from './kinds'

/**
 * The root: the way through the camp as five steps, each ticked off once something exists,
 * and one button for the next step. The catalogues sit below — looked up, not worked through.
 */
export function Home({ status }: { status: Status | null }) {
  const way = AREAS.filter((a) => a.group !== 'lookup')
  const lookup = AREAS.filter((a) => a.group === 'lookup')
  const next = status ? way.find((a) => a.count(status) === 0) ?? null : null

  return (
    <div className="home">
      <div className="home-head">
        <h1>Trainingslager</h1>
        <p>Ein Agent wird besser, wenn man misst, woran er scheitert. Der Weg dahin hat fünf Schritte.</p>
      </div>

      <ol className="way">
        {way.map((a, i) => {
          const n = status ? a.count(status) : null
          const done = n !== null && n > 0
          const isNext = next?.id === a.id
          return (
            <li key={a.id}>
              <Link to={a.path} className={`way-step ${done ? 'done' : ''} ${isNext ? 'next' : ''}`}>
                <span className="way-no">{i + 1}</span>
                <span className="way-title">
                  <span className={`sign t-${KINDS[a.kind].tone}`} aria-hidden="true">
                    {KINDS[a.kind].sign}
                  </span>{' '}
                  {a.label}
                </span>
                <span className="way-hint">{a.hint}</span>
                <span className="way-count">{n === null ? '…' : n === 0 ? 'noch nichts' : `✓ ${n} vorhanden`}</span>
              </Link>
            </li>
          )
        })}
      </ol>

      <div className="next-step">
        {status === null ? null : next ? (
          <>
            <span>Als Nächstes:</span>
            <Link to={next.path} className="btn primary">
              {next.action}
            </Link>
          </>
        ) : (
          <>
            <span>Alles da. Vergleiche die Versionen in der Matrix oder lass einen Coach trainieren.</span>
            <Link to="/runs" className="btn primary">
              zur Matrix
            </Link>
            <Link to="/trainings" className="btn">
              zu den Trainings
            </Link>
          </>
        )}
      </div>

      <div className="lookup">
        <span className="nav-group-label">Nachschlagen</span>
        <div className="lookup-tiles">
          {lookup.map((a) => (
            <Link key={a.id} to={a.path} className="lookup-tile">
              <span className="way-title">
                <span className={`sign t-${KINDS[a.kind].tone}`} aria-hidden="true">
                  {KINDS[a.kind].sign}
                </span>{' '}
                {a.label} <span className="mono muted">{status ? a.count(status) : '…'}</span>
              </span>
              <span className="way-hint">{a.hint} Vom System, nur ansehen.</span>
            </Link>
          ))}
        </div>
      </div>

      {status && (
        <p className="small faint">
          Dateien und Datenbank liegen in <span className="mono">{status.dataDir}</span>.
        </p>
      )}
    </div>
  )
}
