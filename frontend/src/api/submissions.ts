import { apiFetch } from './client'
import type {
  CreateSubmission,
  PagedResponse,
  SubmissionAccepted,
  SubmissionDetail,
  SubmissionSummary,
} from './types'

export function createSubmission(
  input: CreateSubmission,
  idempotencyKey: string,
): Promise<SubmissionAccepted> {
  return apiFetch<SubmissionAccepted>('/api/v1/submissions', {
    method: 'POST',
    headers: { 'Idempotency-Key': idempotencyKey },
    body: JSON.stringify(input),
  })
}

export function fetchSubmission(id: number): Promise<SubmissionDetail> {
  return apiFetch<SubmissionDetail>(`/api/v1/submissions/${id}`)
}

export function fetchHistory(params: {
  problemId?: number
  page?: number
  size?: number
}): Promise<PagedResponse<SubmissionSummary>> {
  const query = new URLSearchParams()
  if (params.problemId !== undefined) query.set('problemId', String(params.problemId))
  if (params.page !== undefined) query.set('page', String(params.page))
  if (params.size !== undefined) query.set('size', String(params.size))
  return apiFetch<PagedResponse<SubmissionSummary>>(`/api/v1/submissions?${query.toString()}`)
}
