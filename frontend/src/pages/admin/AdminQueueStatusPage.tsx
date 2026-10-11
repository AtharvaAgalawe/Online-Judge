import { useQuery } from '@tanstack/react-query'
import { fetchQueueStatus } from '../../api/admin'
import { ErrorBanner } from '../../components/ErrorBanner'
import { Spinner } from '../../components/Spinner'

export function AdminQueueStatusPage() {
  const query = useQuery({
    queryKey: ['adminQueueStatus'],
    queryFn: fetchQueueStatus,
    refetchInterval: 5000,
  })

  if (query.isPending) return <Spinner label="Loading queue status" />
  if (query.isError) return <ErrorBanner error={query.error} />

  const status = query.data
  return (
    <section>
      <h1>Admin · Queue</h1>
      <p className="problem-meta">Database-derived lease state (not the broker queue depth).</p>
      <ul className="counter-list">
        <li>Queued: {status.queued}</li>
        <li>Active leases: {status.activeLease}</li>
        <li>Stale leases: {status.staleLease}</li>
        <li>Retried jobs: {status.retried}</li>
        <li>Oldest queued: {status.oldestQueuedAgeSeconds === null ? '—' : `${status.oldestQueuedAgeSeconds}s`}</li>
      </ul>

      <h2>Stale jobs</h2>
      {status.stale.length === 0 ? (
        <p>No stale jobs.</p>
      ) : (
        <table className="problem-table">
          <thead><tr><th>Submission</th><th>Status</th><th>Worker</th><th>Overdue (s)</th><th>Retries</th></tr></thead>
          <tbody>
            {status.stale.map((job) => (
              <tr key={job.submissionId}>
                <td>{job.submissionId}</td>
                <td>{job.status}</td>
                <td>{job.lockedBy ?? '—'}</td>
                <td>{job.ageSeconds}</td>
                <td>{job.retryCount}</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </section>
  )
}
