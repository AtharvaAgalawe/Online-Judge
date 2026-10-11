import type { Verdict } from '../api/types'
import { VERDICT_LABELS } from './verdictLabels'

const CLASS: Record<Verdict, string> = {
  ACCEPTED: 'verdict--ok',
  WRONG_ANSWER: 'verdict--bad',
  TIME_LIMIT_EXCEEDED: 'verdict--warn',
  MEMORY_LIMIT_EXCEEDED: 'verdict--warn',
  COMPILATION_ERROR: 'verdict--bad',
  RUNTIME_ERROR: 'verdict--bad',
  SYSTEM_ERROR: 'verdict--bad',
}

const ICON: Record<Verdict, string> = {
  ACCEPTED: '✓',
  WRONG_ANSWER: '✗',
  TIME_LIMIT_EXCEEDED: '!',
  MEMORY_LIMIT_EXCEEDED: '!',
  COMPILATION_ERROR: '✗',
  RUNTIME_ERROR: '▲',
  SYSTEM_ERROR: '▲',
}

export function VerdictBadge({ verdict }: { verdict: Verdict | null }) {
  if (!verdict) {
    return (
      <span className="verdict verdict--pending">
        <span className="verdict__icon" aria-hidden="true">
          …
        </span>
        <span>Pending</span>
      </span>
    )
  }
  return (
    <span className={`verdict ${CLASS[verdict]}`}>
      <span className="verdict__icon" aria-hidden="true">
        {ICON[verdict]}
      </span>
      <span>{VERDICT_LABELS[verdict]}</span>
    </span>
  )
}
