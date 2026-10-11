export function Spinner({ label }: { label?: string }) {
  return (
    <div className="spinner" role="status" aria-live="polite">
      {label ?? 'Loading'}…
    </div>
  )
}
