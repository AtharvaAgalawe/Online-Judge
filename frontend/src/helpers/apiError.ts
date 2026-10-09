import { ApiError } from '../api/client'

export function messageOf(error: unknown): string {
  if (error instanceof ApiError) return error.detail || error.title
  if (error instanceof Error) return error.message
  return 'Something went wrong'
}
