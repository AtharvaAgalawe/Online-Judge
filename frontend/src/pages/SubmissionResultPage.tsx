import { useParams } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { fetchSubmission } from '../api/submissions'
import { isTerminalStatus } from '../api/types'
import { ErrorBanner } from '../components/ErrorBanner'
import { Spinner } from '../components/Spinner'
import { StatusTimeline } from '../components/StatusTimeline'
import { VerdictBadge } from '../components/VerdictBadge'
import { VERDICT_LABELS } from '../components/verdictLabels'

export function SubmissionResultPage() {
  const { id = '' } = useParams()
  const submissionId = Number(id)
  const validId = /^\d+$/.test(id)

  const query = useQuery({
    queryKey: ['submission', submissionId],
    queryFn: () => fetchSubmission(submissionId),
    enabled: validId,
    refetchInterval: (query) => {
      const status = query.state.data?.status
      return status && isTerminalStatus(status) ? false : 1000
    },
  })

  if (!validId) return <p>Submission not found.</p>
  if (query.isPending) return <Spinner label="Loading submission" />
  if (query.isError) return <ErrorBanner error={query.error} />

  const submission = query.data
  return (
    <section>
      <h1>Submission #{submission.id}</h1>
      <p className="problem-meta">
        {submission.verdict ? VERDICT_LABELS[submission.verdict] : 'Pending'} ·{' '}
        {submission.languageName} · {submission.problemSlug}
      </p>
      <StatusTimeline status={submission.status} />
      {!isTerminalStatus(submission.status) && <p>Judging… this page updates automatically.</p>}
      {submission.timeUsedMs !== null && <p>Time: {submission.timeUsedMs} ms</p>}
      {submission.memoryUsedKb !== null && (
        <p>Memory: {Math.round(submission.memoryUsedKb / 1024)} MB</p>
      )}
      {submission.failedTestCase && (
        <p>
          Failed on test case #{submission.failedTestCase.index}
          {submission.failedTestCase.isSample ? ' (sample)' : ''}
        </p>
      )}
      {submission.errorMessage && <pre className="error-detail">{submission.errorMessage}</pre>}

      {submission.results.length > 0 && (
        <table className="problem-table">
          <thead>
            <tr>
              <th>#</th>
              <th>Verdict</th>
              <th>Time (ms)</th>
            </tr>
          </thead>
          <tbody>
            {submission.results.map((result) => (
              <tr key={result.index}>
                <td>{result.index}</td>
                <td>
                  <VerdictBadge verdict={result.verdict} />
                </td>
                <td>{result.timeUsedMs ?? '-'}</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </section>
  )
}
