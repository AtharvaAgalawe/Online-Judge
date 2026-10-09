import { useMemo } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { fetchAdminSubmissions } from '../../api/admin'
import { ErrorBanner } from '../../components/ErrorBanner'
import { Pagination } from '../../components/Pagination'
import { Spinner } from '../../components/Spinner'
import { VerdictBadge } from '../../components/VerdictBadge'
import type { SubmissionStatus, Verdict } from '../../api/types'

const STATUSES: SubmissionStatus[] = [
  'SUBMITTED', 'QUEUED', 'PICKED_UP', 'COMPILING', 'RUNNING', 'EVALUATING',
  'COMPLETED', 'COMPILATION_ERROR', 'TIME_LIMIT_EXCEEDED', 'MEMORY_LIMIT_EXCEEDED',
  'RUNTIME_ERROR', 'SYSTEM_ERROR',
]
const VERDICTS: Verdict[] = [
  'ACCEPTED', 'WRONG_ANSWER', 'TIME_LIMIT_EXCEEDED', 'MEMORY_LIMIT_EXCEEDED',
  'COMPILATION_ERROR', 'RUNTIME_ERROR', 'SYSTEM_ERROR',
]

export function AdminSubmissionsPage() {
  const [params, setParams] = useSearchParams()
  const filters = useMemo(
    () => ({
      problemId: params.get('problemId') ? Number(params.get('problemId')) : undefined,
      userId: params.get('userId') ? Number(params.get('userId')) : undefined,
      status: (params.get('status') as SubmissionStatus | null) ?? undefined,
      verdict: (params.get('verdict') as Verdict | null) ?? undefined,
      page: Number(params.get('page') ?? '0'),
      size: 20,
    }),
    [params],
  )

  const query = useQuery({
    queryKey: ['adminSubmissions', filters],
    queryFn: () => fetchAdminSubmissions(filters),
  })

  function apply(patch: Record<string, string>) {
    const next = new URLSearchParams(params)
    for (const [key, value] of Object.entries(patch)) {
      if (value === '') next.delete(key)
      else next.set(key, value)
    }
    setParams(next)
  }

  return (
    <section>
      <h1>Admin · Submissions</h1>
      <div className="filters">
        <label>Problem ID
          <input value={params.get('problemId') ?? ''}
                 onChange={(e) => apply({ problemId: e.target.value, page: '0' })} />
        </label>
        <label>User ID
          <input value={params.get('userId') ?? ''}
                 onChange={(e) => apply({ userId: e.target.value, page: '0' })} />
        </label>
        <label>Status
          <select value={params.get('status') ?? ''} onChange={(e) => apply({ status: e.target.value, page: '0' })}>
            <option value="">All</option>
            {STATUSES.map((status) => <option key={status} value={status}>{status}</option>)}
          </select>
        </label>
        <label>Verdict
          <select value={params.get('verdict') ?? ''} onChange={(e) => apply({ verdict: e.target.value, page: '0' })}>
            <option value="">All</option>
            {VERDICTS.map((verdict) => <option key={verdict} value={verdict}>{verdict}</option>)}
          </select>
        </label>
      </div>

      {query.isPending && <Spinner label="Loading submissions" />}
      {query.isError && <ErrorBanner error={query.error} />}
      {query.isSuccess && query.data.content.length === 0 && <p>No submissions found.</p>}
      {query.isSuccess && query.data.content.length > 0 && (
        <>
          <table className="problem-table">
            <thead><tr><th>#</th><th>Problem</th><th>Language</th><th>Verdict</th><th>Time</th></tr></thead>
            <tbody>
              {query.data.content.map((submission) => (
                <tr key={submission.id}>
                  <td><Link to={`/submissions/${submission.id}`}>{submission.id}</Link></td>
                  <td>{submission.problemSlug}</td>
                  <td>{submission.languageName}</td>
                  <td><VerdictBadge verdict={submission.verdict} /></td>
                  <td>{submission.timeUsedMs ?? '-'}</td>
                </tr>
              ))}
            </tbody>
          </table>
          <Pagination page={filters.page} totalPages={query.data.totalPages}
                      onPageChange={(page) => apply({ page: String(page) })} />
        </>
      )}
    </section>
  )
}
