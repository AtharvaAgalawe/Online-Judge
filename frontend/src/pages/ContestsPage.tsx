import { useNavigate, useSearchParams } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { fetchContests } from '../api/contests'
import type { ContestStatus } from '../api/contests'
import { ContestStatusChip } from '../components/ContestStatusChip'
import { ErrorBanner } from '../components/ErrorBanner'
import { Pagination } from '../components/Pagination'
import { Spinner } from '../components/Spinner'
import { toIntParam } from '../helpers/queryParams'

const TABS: { label: string; value: ContestStatus | '' }[] = [
  { label: 'All', value: '' },
  { label: 'Upcoming', value: 'UPCOMING' },
  { label: 'Running', value: 'RUNNING' },
  { label: 'Past', value: 'ENDED' },
]

export function ContestsPage() {
  const [params, setParams] = useSearchParams()
  const navigate = useNavigate()
  const status = (params.get('status') as ContestStatus | null) ?? ''
  const page = toIntParam(params.get('page'), 0)

  const query = useQuery({
    queryKey: ['contests', { status, page }],
    queryFn: () => fetchContests({ status, page, size: 20 }),
  })

  function setParam(patch: { status?: string; page?: number }) {
    const next = new URLSearchParams(params)
    if (patch.status !== undefined) {
      if (patch.status === '') next.delete('status')
      else next.set('status', patch.status)
      next.delete('page')
    }
    if (patch.page !== undefined) next.set('page', String(patch.page))
    setParams(next)
  }

  return (
    <section>
      <h1>Contests</h1>
      <div className="contest-tabs">
        {TABS.map((tab) => (
          <button
            key={tab.label}
            type="button"
            className={status === tab.value ? 'btn btn--solid' : 'btn btn--outline'}
            onClick={() => setParam({ status: tab.value })}
          >
            {tab.label}
          </button>
        ))}
      </div>
      {query.isPending && <Spinner label="Loading contests" />}
      {query.isError && <ErrorBanner error={query.error} />}
      {query.isSuccess && query.data.content.length === 0 && <p>No contests found.</p>}
      {query.isSuccess && query.data.content.length > 0 && (
        <>
          <table className="problem-table">
            <thead>
              <tr>
                <th>Name</th>
                <th>Status</th>
                <th>Start</th>
                <th>End</th>
                <th>Participants</th>
              </tr>
            </thead>
            <tbody>
              {query.data.content.map((contest) => (
                <tr key={contest.id}>
                  <td>
                    <button
                      type="button"
                      className="linklike"
                      onClick={() => navigate(`/contests/${contest.id}`)}
                    >
                      {contest.name}
                    </button>
                  </td>
                  <td>
                    <ContestStatusChip status={contest.status} />
                  </td>
                  <td>{new Date(contest.startTime).toLocaleString()}</td>
                  <td>{new Date(contest.endTime).toLocaleString()}</td>
                  <td>{contest.participantCount}</td>
                </tr>
              ))}
            </tbody>
          </table>
          <Pagination
            page={page}
            totalPages={query.data.totalPages}
            onPageChange={(next) => setParam({ page: next })}
          />
        </>
      )}
    </section>
  )
}
