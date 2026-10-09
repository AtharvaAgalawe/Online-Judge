import { apiFetch } from './client'
import type {
  Difficulty,
  PagedResponse,
  ProblemDetail,
  ProblemStats,
  ProblemSummary,
} from './types'

export interface ProblemFilters {
  page?: number
  size?: number
  difficulty?: Difficulty | ''
  tag?: string
  search?: string
}

export function fetchProblems(
  filters: ProblemFilters,
): Promise<PagedResponse<ProblemSummary>> {
  const params = new URLSearchParams()
  if (filters.page !== undefined) params.set('page', String(filters.page))
  if (filters.size !== undefined) params.set('size', String(filters.size))
  if (filters.difficulty) params.set('difficulty', filters.difficulty)
  if (filters.tag) params.set('tag', filters.tag)
  if (filters.search) params.set('search', filters.search)
  return apiFetch<PagedResponse<ProblemSummary>>(`/api/v1/problems?${params.toString()}`)
}

export function fetchProblem(slug: string): Promise<ProblemDetail> {
  return apiFetch<ProblemDetail>(`/api/v1/problems/${encodeURIComponent(slug)}`)
}

export function fetchProblemStats(slug: string): Promise<ProblemStats> {
  return apiFetch<ProblemStats>(`/api/v1/problems/${encodeURIComponent(slug)}/stats`)
}
