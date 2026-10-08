import { useEffect, useRef, type ReactNode } from 'react'
import { KINDS, type Kind } from './kinds'

/**
 * A dialog for work that should not happen by accident. Closes on Escape and on a click on the
 * backdrop, never on its own content — half-entered configuration is worth more than a tidy screen.
 */
export function Modal({
  title,
  kind,
  onClose,
  children,
}: {
  title: string
  /** The building block the dialog edits: stripe and sign in its colour. */
  kind?: Kind
  onClose: () => void
  children: ReactNode
}) {
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') onClose()
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [onClose])

  // Close only on a click that starts and ends on the backdrop. Selecting text in a field and
  // releasing the button outside the dialog is a click on the backdrop too, for the browser.
  const pressedOnBackdrop = useRef(false)

  return (
    <div
      className="backdrop"
      onMouseDown={(e) => (pressedOnBackdrop.current = e.target === e.currentTarget)}
      onClick={(e) => {
        if (pressedOnBackdrop.current && e.target === e.currentTarget) onClose()
        pressedOnBackdrop.current = false
      }}
    >
      <div className={`modal ${kind ? `kind t-${KINDS[kind].tone}` : ''}`} role="dialog" aria-modal="true" aria-label={title}>
        <div className="card-head">
          <h2>{title}</h2>
          <button className="btn quiet sm" onClick={onClose} style={{ marginLeft: 'auto' }}>
            close
          </button>
        </div>
        {children}
      </div>
    </div>
  )
}
