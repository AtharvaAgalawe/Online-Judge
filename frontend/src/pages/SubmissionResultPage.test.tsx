import { afterEach, describe, expect, it, vi } from 'vitest'
import { act, screen } from '@testing-library/react'
import { Route, Routes } from 'react-router-dom'
import { AuthProvider } from '../auth/AuthContext'
import { SubmissionResultPage } from './SubmissionResultPage'
import { renderWithProviders } from '../test/renderWithProviders'

const DETAIL = {
  id: 1,
  problemId: 1,
  problemSlug: 'two-sum',
  languageName: 'Python 3.12',
  status: 'COMPLETED',
  verdict: 'WRONG_ANSWER',
  timeUsedMs: 12,
  memoryUsedKb: 4096,
  errorMessage: null,
  sourceCode: 'print(1)',
  submittedAt: '2026-01-01T00:00:00Z',
  judgedAt: '2026-01-01T00:00:01Z',
  failedTestCase: { index: 2, isSample: false },
  results: [{ index: 2, isSample: false, verdict: 'WRONG_ANSWER', timeUsedMs: 12, memoryUsedKb: 4096 }],
}

describe('SubmissionResultPage', () => {
  afterEach(() => {
    localStorage.clear()
    vi.useRealTimers()
    vi.restoreAllMocks()
  })

  it('shows the verdict and the failed test case index only', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async () =>
        new Response(JSON.stringify(DETAIL), {
          status: 200,
          headers: { 'Content-Type': 'application/json' },
        }),
      ),
    )

    renderWithProviders(
      <AuthProvider>
        <Routes>
          <Route path="/submissions/:id" element={<SubmissionResultPage />} />
        </Routes>
      </AuthProvider>,
      { route: '/submissions/1' },
    )

    expect(await screen.findByText('Wrong Answer')).toBeInTheDocument()
    expect(screen.getByText(/test case #2/i)).toBeInTheDocument()
  })

  it('stops polling once a terminal status is reached', async () => {
    vi.useFakeTimers()
    let calls = 0
    vi.stubGlobal(
      'fetch',
      vi.fn(async () => {
        calls += 1
        const body =
          calls === 1
            ? { ...DETAIL, status: 'RUNNING', verdict: null, results: [] }
            : DETAIL
        return new Response(JSON.stringify(body), {
          status: 200,
          headers: { 'Content-Type': 'application/json' },
        })
      }),
    )

    renderWithProviders(
      <AuthProvider>
        <Routes>
          <Route path="/submissions/:id" element={<SubmissionResultPage />} />
        </Routes>
      </AuthProvider>,
      { route: '/submissions/1' },
    )

    await vi.waitFor(() => expect(calls).toBe(1))
    await act(async () => {
      await vi.advanceTimersByTimeAsync(1000)
    })
    await vi.waitFor(() => expect(calls).toBe(2))

    await act(async () => {
      await vi.advanceTimersByTimeAsync(5000)
    })
    expect(calls).toBe(2)
  })
})
