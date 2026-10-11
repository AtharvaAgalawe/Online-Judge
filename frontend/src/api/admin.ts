import { apiFetch } from './client'
import type {
  Difficulty,
  PagedResponse,
  ProblemDetail,
  SubmissionStatus,
  SubmissionSummary,
  Verdict,
} from './types'

export interface AdminProblemSummary {
  id: number
  slug: string
  title: string
  difficulty: Difficulty
  published: boolean
  testCaseCount: number
  tags: string[]
  createdAt: string
  updatedAt: string
}

export interface AdminProblemDetail {
  id: number
  slug: string
  title: string
  statement: string
  difficulty: Difficulty
  timeLimitMs: number
  memoryLimitKb: number
  published: boolean
  createdBy: string | null
  testCaseCount: number
  tags: string[]
  createdAt: string
  updatedAt: string
}

export interface AdminProblemFilters {
  published?: boolean
  search?: string
  page?: number
  size?: number
}

export interface CreateProblemBody {
  title: string
  statement: string
  difficulty: Difficulty
  timeLimitMs?: number
  memoryLimitKb?: number
  tags?: string[]
}

export interface UpdateProblemBody {
  title?: string
  statement?: string
  difficulty?: Difficulty
  timeLimitMs?: number
  memoryLimitKb?: number
  tags?: string[]
  published?: boolean
}

export interface TestCase {
  id: number
  input: string
  expectedOutput: string
  isSample: boolean
  order: number
  points: number
}

export interface TestCaseInput {
  input: string
  expectedOutput: string
  isSample?: boolean
  order?: number
  points?: number
}

export interface AdminSubmissionFilters {
  problemId?: number
  userId?: number
  status?: SubmissionStatus
  verdict?: Verdict
  page?: number
  size?: number
}

export interface StaleJob {
  submissionId: number
  status: SubmissionStatus
  lockedBy: string | null
  leaseExpiresAt: string
  retryCount: number
  ageSeconds: number
}

export interface QueueStatus {
  queued: number
  activeLease: number
  staleLease: number
  retried: number
  oldestQueuedAgeSeconds: number | null
  stale: StaleJob[]
}

function pagedQuery(params: Record<string, string | number | boolean | undefined>): string {
  const query = new URLSearchParams()
  for (const [key, value] of Object.entries(params)) {
    if (value !== undefined && value !== '') query.set(key, String(value))
  }
  return query.toString()
}

export function fetchAdminProblems(filters: AdminProblemFilters): Promise<PagedResponse<AdminProblemSummary>> {
  return apiFetch(`/api/v1/admin/problems?${pagedQuery({ ...filters })}`)
}

export function fetchAdminProblem(id: number): Promise<AdminProblemDetail> {
  return apiFetch(`/api/v1/admin/problems/${id}`)
}

export function createProblem(body: CreateProblemBody): Promise<{ id: number; slug: string }> {
  return apiFetch('/api/v1/admin/problems', { method: 'POST', body: JSON.stringify(body) })
}

export function updateProblem(id: number, body: UpdateProblemBody): Promise<ProblemDetail> {
  return apiFetch(`/api/v1/admin/problems/${id}`, { method: 'PUT', body: JSON.stringify(body) })
}

export function fetchTestCases(problemId: number): Promise<TestCase[]> {
  return apiFetch(`/api/v1/admin/problems/${problemId}/test-cases`)
}

export function addTestCase(problemId: number, body: TestCaseInput): Promise<TestCase> {
  return apiFetch(`/api/v1/admin/problems/${problemId}/test-cases`, {
    method: 'POST',
    body: JSON.stringify(body),
  })
}

export function updateTestCase(
  problemId: number,
  testCaseId: number,
  body: Partial<TestCaseInput>,
): Promise<TestCase> {
  return apiFetch(`/api/v1/admin/problems/${problemId}/test-cases/${testCaseId}`, {
    method: 'PUT',
    body: JSON.stringify(body),
  })
}

export function deleteTestCase(problemId: number, testCaseId: number): Promise<void> {
  return apiFetch(`/api/v1/admin/problems/${problemId}/test-cases/${testCaseId}`, {
    method: 'DELETE',
  })
}

export function fetchAdminSubmissions(
  filters: AdminSubmissionFilters,
): Promise<PagedResponse<SubmissionSummary>> {
  return apiFetch(`/api/v1/admin/submissions?${pagedQuery({ ...filters })}`)
}

export function fetchQueueStatus(): Promise<QueueStatus> {
  return apiFetch('/api/v1/admin/system/queue-status')
}

export interface AdminContestSummary {
  id: number
  name: string
  startTime: string
  endTime: string
  status: 'UPCOMING' | 'RUNNING' | 'ENDED'
  isPrivate: boolean
  participantCount: number
  problemCount: number
  problemsPublishedAt: string | null
}

export interface AdminContestDetail {
  id: number
  name: string
  description: string | null
  startTime: string
  endTime: string
  isPrivate: boolean
  freezeMinutes: number
  status: 'UPCOMING' | 'RUNNING' | 'ENDED'
  problemsPublishedAt: string | null
}

export interface CreateContestBody {
  name: string
  description?: string
  startTime: string
  endTime: string
  isPrivate: boolean
  freezeMinutes?: number
}

export type UpdateContestBody = CreateContestBody

export interface AttachableProblem {
  id: number
  slug: string
  title: string
  difficulty: Difficulty
}

export interface ContestProblemCreated {
  problemId: number
  slug: string
}

export function listAdminContests(filters: {
  search?: string
  status?: string
  page?: number
  size?: number
}): Promise<PagedResponse<AdminContestSummary>> {
  const params = new URLSearchParams()
  if (filters.search) params.set('search', filters.search)
  if (filters.status) params.set('status', filters.status)
  if (filters.page !== undefined) params.set('page', String(filters.page))
  if (filters.size !== undefined) params.set('size', String(filters.size))
  return apiFetch(`/api/v1/admin/contests?${params.toString()}`)
}

export function fetchAdminContest(id: number): Promise<AdminContestDetail> {
  return apiFetch(`/api/v1/admin/contests/${id}`)
}

export function createContest(body: CreateContestBody): Promise<{ id: number; name: string }> {
  return apiFetch('/api/v1/admin/contests', { method: 'POST', body: JSON.stringify(body) })
}

export function updateContest(id: number, body: UpdateContestBody): Promise<void> {
  return apiFetch(`/api/v1/admin/contests/${id}`, { method: 'PUT', body: JSON.stringify(body) })
}

export function deleteContest(id: number): Promise<void> {
  return apiFetch(`/api/v1/admin/contests/${id}`, { method: 'DELETE' })
}

export function fetchAttachableProblems(search?: string): Promise<AttachableProblem[]> {
  const params = new URLSearchParams()
  if (search) params.set('search', search)
  return apiFetch(`/api/v1/admin/contests/attachable-problems?${params.toString()}`)
}

export function attachContestProblem(
  contestId: number,
  body: { problemId: number; points?: number },
): Promise<void> {
  return apiFetch(`/api/v1/admin/contests/${contestId}/problems`, {
    method: 'POST',
    body: JSON.stringify(body),
  })
}

export function createContestProblemInline(
  contestId: number,
  body: {
    title: string
    statement: string
    difficulty: Difficulty
    timeLimitMs?: number
    memoryLimitKb?: number
    points?: number
  },
): Promise<ContestProblemCreated> {
  return apiFetch(`/api/v1/admin/contests/${contestId}/problems/new`, {
    method: 'POST',
    body: JSON.stringify(body),
  })
}

export function updateContestProblem(
  contestId: number,
  problemId: number,
  body: { points?: number; displayOrder?: number },
): Promise<void> {
  return apiFetch(`/api/v1/admin/contests/${contestId}/problems/${problemId}`, {
    method: 'PUT',
    body: JSON.stringify(body),
  })
}

export function detachContestProblem(contestId: number, problemId: number): Promise<void> {
  return apiFetch(`/api/v1/admin/contests/${contestId}/problems/${problemId}`, {
    method: 'DELETE',
  })
}
