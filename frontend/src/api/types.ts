export type SubmissionStatus =
  | 'SUBMITTED'
  | 'QUEUED'
  | 'PICKED_UP'
  | 'COMPILING'
  | 'RUNNING'
  | 'EVALUATING'
  | 'COMPLETED'
  | 'COMPILATION_ERROR'
  | 'TIME_LIMIT_EXCEEDED'
  | 'MEMORY_LIMIT_EXCEEDED'
  | 'RUNTIME_ERROR'
  | 'SYSTEM_ERROR'

export const TERMINAL_STATUSES: readonly SubmissionStatus[] = [
  'COMPLETED',
  'COMPILATION_ERROR',
  'TIME_LIMIT_EXCEEDED',
  'MEMORY_LIMIT_EXCEEDED',
  'RUNTIME_ERROR',
  'SYSTEM_ERROR',
]

export function isTerminalStatus(status: SubmissionStatus): boolean {
  return TERMINAL_STATUSES.includes(status)
}

export type Verdict =
  | 'ACCEPTED'
  | 'WRONG_ANSWER'
  | 'TIME_LIMIT_EXCEEDED'
  | 'MEMORY_LIMIT_EXCEEDED'
  | 'COMPILATION_ERROR'
  | 'RUNTIME_ERROR'
  | 'SYSTEM_ERROR'

export type Difficulty = 'EASY' | 'MEDIUM' | 'HARD'

export interface UserResponse {
  id: number
  username: string
  email: string
}

export interface Registration {
  username: string
  email: string
  password: string
}

export interface AuthTokensResponse {
  accessToken: string
  refreshToken: string
  expiresIn: number
}

export interface AccessTokenResponse {
  accessToken: string
  expiresIn: number
}

export interface ProblemSummary {
  id: number
  slug: string
  title: string
  difficulty: Difficulty
  tags: string[]
  acceptanceRate: number
}

export interface SampleTestCase {
  input: string
  expectedOutput: string
  order: number
}

export interface ProblemDetail {
  id: number
  slug: string
  title: string
  statement: string
  difficulty: Difficulty
  timeLimitMs: number
  memoryLimitKb: number
  tags: string[]
  sampleTestCases: SampleTestCase[]
}

export interface ProblemStats {
  totalSubmissions: number
  acceptedCount: number
  acceptanceRate: number
}

export interface Language {
  id: number
  name: string
}

export interface CreateSubmission {
  problemId: number
  languageId: number
  sourceCode: string
}

export interface SubmissionAccepted {
  submissionId: number
  status: SubmissionStatus
}

export interface SubmissionStatusResponse {
  status: SubmissionStatus
  verdict: Verdict | null
}

export interface FailedTestCase {
  index: number
  isSample: boolean
}

export interface CaseResult {
  index: number
  isSample: boolean
  verdict: Verdict
  timeUsedMs: number | null
  memoryUsedKb: number | null
}

export interface SubmissionDetail {
  id: number
  problemId: number
  problemSlug: string
  languageName: string
  status: SubmissionStatus
  verdict: Verdict | null
  timeUsedMs: number | null
  memoryUsedKb: number | null
  errorMessage: string | null
  sourceCode: string
  submittedAt: string
  judgedAt: string | null
  failedTestCase: FailedTestCase | null
  results: CaseResult[]
}

export interface SubmissionSummary {
  id: number
  problemId: number
  problemSlug: string
  languageName: string
  status: SubmissionStatus
  verdict: Verdict | null
  timeUsedMs: number | null
  memoryUsedKb: number | null
  submittedAt: string
}

export interface PagedResponse<T> {
  content: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}
