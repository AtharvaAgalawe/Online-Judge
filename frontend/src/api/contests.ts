import { apiFetch } from './client'
import type { Difficulty, PagedResponse, SampleTestCase } from './types'

export type ContestStatus = 'UPCOMING' | 'RUNNING' | 'ENDED'

export interface ContestSummary {
  id: number
  name: string
  startTime: string
  endTime: string
  status: ContestStatus
  participantCount: number
  problemCount: number
}

export interface ContestProblemSummary {
  problemId: number
  slug: string
  title: string
  difficulty: Difficulty
  displayOrder: number
  points: number
  solvedByMe: boolean
}

export interface ContestDetail {
  id: number
  name: string
  description: string | null
  startTime: string
  endTime: string
  status: ContestStatus
  freezeMinutes: number
  participantCount: number
  registered: boolean
  registrationOpen: boolean
  problemsRevealed: boolean
  problems: ContestProblemSummary[] | null
}

export interface ContestProblemDetail {
  problemId: number
  slug: string
  title: string
  statement: string
  difficulty: Difficulty
  timeLimitMs: number
  memoryLimitKb: number
  points: number
  contestId: number
  contestName: string
  contestStatus: ContestStatus
  sampleTestCases: SampleTestCase[]
}

export interface StandingsProblemCell {
  problemId: number
  solved: boolean
  wrongAttempts: number
  acceptedAtMinute: number | null
}

export interface StandingsRow {
  rank: number
  username: string
  totalPoints: number
  penalty: number
  problems: StandingsProblemCell[]
}

export interface StandingsResponse {
  frozen: boolean
  frozenAt: string | null
  rows: StandingsRow[]
}

export interface ContestFilters {
  status?: ContestStatus | ''
  page?: number
  size?: number
}

export function fetchContests(filters: ContestFilters): Promise<PagedResponse<ContestSummary>> {
  const params = new URLSearchParams()
  if (filters.status) params.set('status', filters.status)
  if (filters.page !== undefined) params.set('page', String(filters.page))
  if (filters.size !== undefined) params.set('size', String(filters.size))
  return apiFetch<PagedResponse<ContestSummary>>(`/api/v1/contests?${params.toString()}`)
}

export function fetchContest(id: number): Promise<ContestDetail> {
  return apiFetch<ContestDetail>(`/api/v1/contests/${id}`)
}

export function registerForContest(id: number): Promise<{ registered: boolean }> {
  return apiFetch(`/api/v1/contests/${id}/register`, { method: 'POST' })
}

export function unregisterFromContest(id: number): Promise<void> {
  return apiFetch(`/api/v1/contests/${id}/register`, { method: 'DELETE' })
}

export function fetchContestProblem(id: number, slug: string): Promise<ContestProblemDetail> {
  return apiFetch<ContestProblemDetail>(
    `/api/v1/contests/${id}/problems/${encodeURIComponent(slug)}`,
  )
}

export function fetchStandings(id: number): Promise<StandingsResponse> {
  return apiFetch<StandingsResponse>(`/api/v1/contests/${id}/standings`)
}
