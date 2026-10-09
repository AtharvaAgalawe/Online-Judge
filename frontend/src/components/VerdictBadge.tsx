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

export function VerdictBadge({ verdict }: { verdict: Verdict | null }) {
  if (!verdict) return <span className="verdict verdict--pending">Pending</span>
  return <span className={`verdict ${CLASS[verdict]}`}>{VERDICT_LABELS[verdict]}</span>
}
