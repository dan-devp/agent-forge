/**
 * The record behind the rendering: every view interprets, this shows what the API actually
 * answered. Folded away by default — it is for checking a number that looks wrong, not for
 * reading.
 */
export function RawJson({ label, value }: { label: string; value: unknown }) {
  return (
    <details className="raw" onClick={(e) => e.stopPropagation()}>
      <summary>JSON · {label}</summary>
      <pre>{JSON.stringify(value, null, 2)}</pre>
    </details>
  )
}
