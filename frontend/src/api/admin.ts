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
