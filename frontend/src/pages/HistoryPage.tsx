import { Link, useSearchParams } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { fetchHistory } from '../api/submissions'
import { ErrorBanner } from '../components/ErrorBanner'
import { Pagination } from '../components/Pagination'
import { Spinner } from '../components/Spinner'
import { VerdictBadge } from '../components/VerdictBadge'
import { toIntParam, toOptionalIntParam } from '../helpers/queryParams'

export function HistoryPage() {
  const [params, setParams] = useSearchParams()
  const page = toIntParam(params.get('page'), 0)
  const problemId = toOptionalIntParam(params.get('problemId'))

  const query = useQuery({
    queryKey: ['history', { problemId, page }],
    queryFn: () => fetchHistory({ problemId, page, size: 20 }),
  })

  function setPage(next: number) {
    const updated = new URLSearchParams(params)
    updated.set('page', String(next))
    setParams(updated)
  }

  return (
    <section>
      <h1>My Submissions</h1>
      {query.isPending && <Spinner label="Loading submissions" />}
      {query.isError && <ErrorBanner error={query.error} />}
      {query.isSuccess && query.data.content.length === 0 && <p>No submissions yet.</p>}
      {query.isSuccess && query.data.content.length > 0 && (
        <>
          <table className="problem-table">
            <thead>
              <tr>
                <th>#</th>
                <th>Problem</th>
                <th>Language</th>
                <th>Verdict</th>
                <th>Time (ms)</th>
              </tr>
            </thead>
            <tbody>
              {query.data.content.map((submission) => (
                <tr key={submission.id}>
                  <td>
                    <Link to={`/submissions/${submission.id}`}>{submission.id}</Link>
                  </td>
                  <td>{submission.problemSlug}</td>
                  <td>{submission.languageName}</td>
                  <td>
                    <VerdictBadge verdict={submission.verdict} />
                  </td>
                  <td>{submission.timeUsedMs ?? '-'}</td>
                </tr>
              ))}
            </tbody>
          </table>
          <Pagination page={page} totalPages={query.data.totalPages} onPageChange={setPage} />
        </>
      )}
    </section>
  )
}
