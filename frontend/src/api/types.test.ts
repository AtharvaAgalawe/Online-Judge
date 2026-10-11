import { describe, expect, it } from 'vitest'
import { isTerminalStatus } from './types'
import type { SubmissionStatus } from './types'

const TERMINAL_STATUSES: SubmissionStatus[] = [
  'COMPLETED',
  'COMPILATION_ERROR',
  'TIME_LIMIT_EXCEEDED',
  'MEMORY_LIMIT_EXCEEDED',
  'RUNTIME_ERROR',
  'SYSTEM_ERROR',
]

const NON_TERMINAL_STATUSES: SubmissionStatus[] = [
  'SUBMITTED',
  'QUEUED',
  'PICKED_UP',
  'COMPILING',
  'RUNNING',
  'EVALUATING',
]

describe('isTerminalStatus', () => {
  it.each(TERMINAL_STATUSES)('treats %s as terminal', (status) => {
    expect(isTerminalStatus(status)).toBe(true)
  })

  it.each(NON_TERMINAL_STATUSES)('treats %s as non-terminal', (status) => {
    expect(isTerminalStatus(status)).toBe(false)
  })
})
