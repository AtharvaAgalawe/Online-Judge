import { useMemo, useState } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { listAdminContests } from '../../api/admin'
import { AdminNav } from '../../components/AdminNav'
import { ContestStatusChip } from '../../components/ContestStatusChip'
import { ErrorBanner } from '../../components/ErrorBanner'
import { Pagination } from '../../components/Pagination'
import { Spinner } from '../../components/Spinner'

export function AdminContestListPage() {
  const [params, setParams] = useSearchParams()
  const navigate = useNavigate()
  const [search, setSearch] = useState(params.get('search') ?? '')

  const filters = useMemo(
    () => ({
      search: params.get('search') ?? undefined,
      status: params.get('status') ?? undefined,
      page: Number(params.get('page') ?? '0'),
      size: 20,
    }),
    [params],
  )

  const query = useQuery({
    queryKey: ['adminContests', filters],
    queryFn: () => listAdminContests(filters),
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
      <h1>Admin · Contests</h1>
      <AdminNav />
      <div className="filters">
        <label>
          Search
          <input value={search} onChange={(e) => setSearch(e.target.value)} />
        </label>
        <label>
          Status
          <select value={params.get('status') ?? ''}
                  onChange={(e) => apply({ status: e.target.value, page: '0' })}>
            <option value="">All</option>
            <option value="UPCOMING">Upcoming</option>
            <option value="RUNNING">Running</option>
            <option value="ENDED">Ended</option>
          </select>
        </label>
        <button type="button" onClick={() => apply({ search, page: '0' })}>Search</button>
        <Link to="/admin/contests/new">New contest</Link>
      </div>

      {query.isPending && <Spinner label="Loading contests" />}
      {query.isError && <ErrorBanner error={query.error} />}
      {query.isSuccess && (
        <>
          <table className="problem-table">
            <thead>
              <tr>
                <th>Name</th>
                <th>Status</th>
                <th>Visibility</th>
                <th>Start</th>
                <th>End</th>
                <th>Problems</th>
                <th>Problems published</th>
                <th>Participants</th>
              </tr>
            </thead>
            <tbody>
              {query.data.content.map((contest) => (
                <tr key={contest.id}>
                  <td>
                    <button type="button" className="linklike"
                            onClick={() => navigate(`/admin/contests/${contest.id}`)}>
                      {contest.name}
                    </button>
                  </td>
                  <td><ContestStatusChip status={contest.status} /></td>
                  <td>{contest.isPrivate ? 'Private' : 'Public'}</td>
                  <td>{new Date(contest.startTime).toLocaleString()}</td>
                  <td>{new Date(contest.endTime).toLocaleString()}</td>
                  <td>{contest.problemCount}</td>
                  <td>
                    {contest.problemsPublishedAt
                      ? `✓ ${new Date(contest.problemsPublishedAt).toLocaleString()}`
                      : '—'}
                  </td>
                  <td>{contest.participantCount}</td>
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
