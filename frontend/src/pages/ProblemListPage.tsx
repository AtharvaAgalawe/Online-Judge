import { useMemo } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { fetchProblems } from '../api/problems'
import type { ProblemFilters } from '../api/problems'
import type { Difficulty } from '../api/types'
import { ErrorBanner } from '../components/ErrorBanner'
import { Pagination } from '../components/Pagination'
import { ProblemTable } from '../components/ProblemTable'
import { Spinner } from '../components/Spinner'
import { toIntParam } from '../helpers/queryParams'

export function ProblemListPage() {
  const [params, setParams] = useSearchParams()
  const navigate = useNavigate()

  const filters = useMemo<ProblemFilters>(
    () => ({
      page: toIntParam(params.get('page'), 0),
      size: 20,
      difficulty: (params.get('difficulty') as Difficulty | null) ?? '',
      tag: params.get('tag') ?? '',
      search: params.get('search') ?? '',
    }),
    [params],
  )

  const query = useQuery({
    queryKey: ['problems', filters],
    queryFn: () => fetchProblems(filters),
  })

  function update(patch: Record<string, string | number>) {
    const next = new URLSearchParams(params)
    for (const [key, value] of Object.entries(patch)) {
      if (value === '' || value === undefined) next.delete(key)
      else next.set(key, String(value))
    }
    if (!('page' in patch)) next.set('page', '0')
    setParams(next)
  }

  return (
    <section>
      <h1>Problems</h1>
      <div className="filters">
        <label>
          Difficulty
          <select
            value={filters.difficulty}
            onChange={(event) => update({ difficulty: event.target.value })}
          >
            <option value="">All</option>
            <option value="EASY">Easy</option>
            <option value="MEDIUM">Medium</option>
            <option value="HARD">Hard</option>
          </select>
        </label>
        <label>
          Tag
          <input
            value={filters.tag}
            onChange={(event) => update({ tag: event.target.value })}
          />
        </label>
        <label>
          Search
          <input
            value={filters.search}
            onChange={(event) => update({ search: event.target.value })}
          />
        </label>
      </div>

      {query.isPending && <Spinner label="Loading problems" />}
      {query.isError && <ErrorBanner error={query.error} />}
      {query.isSuccess && query.data.content.length === 0 && <p>No problems found.</p>}
      {query.isSuccess && query.data.content.length > 0 && (
        <>
          <ProblemTable
            problems={query.data.content}
            onSelect={(slug) => navigate(`/problems/${slug}`)}
          />
          <Pagination
            page={query.data.page}
            totalPages={query.data.totalPages}
            onPageChange={(page) => update({ page })}
          />
        </>
      )}
    </section>
  )
}
