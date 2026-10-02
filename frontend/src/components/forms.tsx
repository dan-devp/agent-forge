import { useState, type ReactNode } from 'react'
import { slugify, validId, type Naming } from './ids'

/**
 * The name, and below it the id the file will get. A new block takes its id from the name
 * until someone edits the id; an existing one keeps it. The id field only shows when asked
 * for, or when the name gives no usable id.
 */
export function NameField(p: { naming: Naming; existing: boolean; placeholder?: string; onChange: (next: Naming) => void }) {
  const [editing, setEditing] = useState(false)
  const { name, id, idTouched } = p.naming
  const showInput = !p.existing && (editing || (name.trim() !== '' && !validId(id)))

  return (
    <div className="name-field">
      <label className="field">
        <span>Name</span>
        <input
          value={name}
          required
          placeholder={p.placeholder}
          onChange={(e) => p.onChange({ name: e.target.value, id: p.existing || idTouched ? id : slugify(e.target.value), idTouched })}
        />
      </label>
      <span className="id-line">
        Id <span className="mono">{id || '…'}</span>
        {p.existing ? (
          <span className="faint"> · bleibt fest, Läufe verweisen darauf</span>
        ) : (
          !showInput && (
            <button type="button" className="link" onClick={() => setEditing(true)}>
              ändern
            </button>
          )
        )}
      </span>
      {showInput && (
        <label className="field">
          <span>Id (Dateiname, kleinbuchstaben-und-bindestriche)</span>
          <input
            className="mono"
            value={id}
            required
            pattern="[a-z0-9][a-z0-9-]*"
            onChange={(e) => p.onChange({ name, id: e.target.value, idTouched: true })}
          />
        </label>
      )}
    </div>
  )
}

/** One question of a guided editor: a number, the question, an optional hint, the fields. */
export function GuideStep(p: { no: string; title: ReactNode; hint?: ReactNode; children: ReactNode }) {
  return (
    <section className="guide-step">
      <h3>
        <span className="phase-no">{p.no}</span> {p.title}
      </h3>
      {p.hint && <p className="hint">{p.hint}</p>}
      {p.children}
    </section>
  )
}

/**
 * Settings with a sensible default, folded away. The summary says what they are set to, so
 * nothing hides unnoticed. No required fields inside: a closed section cannot show an error.
 */
export function Advanced(p: { summary: ReactNode; children: ReactNode }) {
  return (
    <details className="advanced">
      <summary>
        <span className="advanced-title">Weitere Einstellungen</span>
        <span className="advanced-summary">{p.summary}</span>
      </summary>
      <div className="advanced-body">{p.children}</div>
    </details>
  )
}

/** A card section that stays folded until asked for: for what only matters when digging. */
export function Fold(p: { title: string; hint?: ReactNode; open?: boolean; className?: string; children: ReactNode }) {
  return (
    <details className={`card fold ${p.className ?? ''}`} open={p.open}>
      <summary className="card-head">
        <h2>{p.title}</h2>
        {p.hint && <span className="sub">{p.hint}</span>}
        <span className="fold-toggle" aria-hidden="true" />
      </summary>
      <div className="fold-body">{p.children}</div>
    </details>
  )
}
