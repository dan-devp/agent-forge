import { useEffect, type ReactNode } from 'react'

/**
 * A dialog for work that should not happen by accident. Closes on Escape and on the backdrop,
 * never on its own content — half-entered configuration is worth more than a tidy screen.
 */
export function Modal({ title, onClose, children }: { title: string; onClose: () => void; children: ReactNode }) {
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') onClose()
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [onClose])

  return (
    <div className="backdrop" onClick={onClose}>
      <div className="modal" role="dialog" aria-modal="true" aria-label={title} onClick={(e) => e.stopPropagation()}>
        <div className="card-head">
          <h2>{title}</h2>
          <button className="btn quiet sm" onClick={onClose} style={{ marginLeft: 'auto' }}>
            schließen
          </button>
        </div>
        {children}
      </div>
    </div>
  )
}
