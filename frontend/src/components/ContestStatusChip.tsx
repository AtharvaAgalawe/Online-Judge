import type { ContestStatus } from '../api/contests'

const LABELS: Record<ContestStatus, string> = {
  UPCOMING: 'Upcoming',
  RUNNING: 'Running',
  ENDED: 'Ended',
}

export function ContestStatusChip({ status }: { status: ContestStatus }) {
  return (
    <span className={`contest-chip contest-chip--${status.toLowerCase()}`}>{LABELS[status]}</span>
  )
}
