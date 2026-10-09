import { useMemo, useState } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { fetchAdminProblems } from '../../api/admin'
import { ErrorBanner } from '../../components/ErrorBanner'
import { Pagination } from '../../components/Pagination'
import { Spinner } from '../../components/Spinner'

export function AdminProblemListPage() {
  const [params, setParams] = useSearchParams()
  const navigate = useNavigate()
  const [search, setSearch] = useState(params.get('search') ?? '')

  const filters = useMemo(
    () => ({
      search: params.get('search') ?? undefined,
      published: params.get('published') ? params.get('published') === 'true' : undefined,
      page: Number(params.get('page') ?? '0'),
      size: 20,
    }),
    [params],
  )

  const query = useQuery({
    queryKey: ['adminProblems', filters],
    queryFn: () => fetchAdminProblems(filters),
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
      <h1>Admin · Problems</h1>
      <div className="filters">
        <label>
          Search
          <input value={search} onChange={(e) => setSearch(e.target.value)} />
        </label>
        <label>
          Published
          <select value={params.get('published') ?? ''}
                  onChange={(e) => apply({ published: e.target.value, page: '0' })}>
            <option value="">All</option>
            <option value="true">Published</option>
            <option value="false">Draft</option>
          </select>
        </label>
        <button type="button" onClick={() => apply({ search, page: '0' })}>Search</button>
        <Link to="/admin/problems/new">New problem</Link>
      </div>

      {query.isPending && <Spinner label="Loading problems" />}
      {query.isError && <ErrorBanner error={query.error} />}
      {query.isSuccess && (
        <>
          <table className="problem-table">
            <thead>
              <tr><th>Title</th><th>Difficulty</th><th>Status</th><th>Tests</th><th>Updated</th></tr>
            </thead>
            <tbody>
              {query.data.content.map((problem) => (
                <tr key={problem.id}>
                  <td>
                    <button type="button" className="linklike"
                            onClick={() => navigate(`/admin/problems/${problem.id}`)}>
                      {problem.title}
                    </button>
                  </td>
                  <td>{problem.difficulty}</td>
                  <td>{problem.published ? 'Published' : 'Draft'}</td>
                  <td>{problem.testCaseCount}</td>
                  <td>{new Date(problem.updatedAt).toLocaleString()}</td>
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
