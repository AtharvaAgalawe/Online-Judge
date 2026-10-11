import type { SubmissionStatus } from '../api/types'

const STAGES: SubmissionStatus[] = [
  'SUBMITTED',
  'QUEUED',
  'PICKED_UP',
  'COMPILING',
  'RUNNING',
  'EVALUATING',
  'COMPLETED',
]

const FAILURE_STATUSES: readonly SubmissionStatus[] = [
  'COMPILATION_ERROR',
  'TIME_LIMIT_EXCEEDED',
  'MEMORY_LIMIT_EXCEEDED',
  'RUNTIME_ERROR',
  'SYSTEM_ERROR',
]

const FAILURE_LABELS: Partial<Record<SubmissionStatus, string>> = {
  COMPILATION_ERROR: 'Compilation error',
  TIME_LIMIT_EXCEEDED: 'Time limit exceeded',
  MEMORY_LIMIT_EXCEEDED: 'Memory limit exceeded',
  RUNTIME_ERROR: 'Runtime error',
  SYSTEM_ERROR: 'System error',
}

export function StatusTimeline({ status }: { status: SubmissionStatus }) {
  const activeIndex = STAGES.indexOf(status)
  const failed = FAILURE_STATUSES.includes(status)
  return (
    <>
      <ol className="timeline">
        {STAGES.map((stage, index) => (
          <li
            key={stage}
            className={
              index <= activeIndex ? 'timeline__step timeline__step--done' : 'timeline__step'
            }
          >
            {stage}
          </li>
        ))}
      </ol>
      {failed && (
        <p className="timeline__failure" role="status">
          {FAILURE_LABELS[status] ?? status}
        </p>
      )}
    </>
  )
}
