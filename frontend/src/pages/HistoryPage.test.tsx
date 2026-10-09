import { afterEach, describe, expect, it, vi } from 'vitest'
import { screen } from '@testing-library/react'
import { Route, Routes } from 'react-router-dom'
import { AuthProvider } from '../auth/AuthContext'
import { HistoryPage } from './HistoryPage'
import { renderWithProviders } from '../test/renderWithProviders'

const PAGE = {
  content: [
    {
      id: 42,
      problemId: 1,
      problemSlug: 'two-sum',
      languageName: 'Python 3.12',
      status: 'COMPLETED',
      verdict: 'ACCEPTED',
      timeUsedMs: 10,
      memoryUsedKb: 2048,
      submittedAt: '2026-01-01T00:00:00Z',
    },
  ],
  page: 0,
  size: 20,
  totalElements: 1,
  totalPages: 1,
}

describe('HistoryPage', () => {
  afterEach(() => {
    localStorage.clear()
    vi.restoreAllMocks()
  })

  it('lists the caller submissions', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async () =>
        new Response(JSON.stringify(PAGE), {
          status: 200,
          headers: { 'Content-Type': 'application/json' },
        }),
      ),
    )
    renderWithProviders(
      <AuthProvider>
        <Routes>
          <Route path="/submissions" element={<HistoryPage />} />
        </Routes>
      </AuthProvider>,
      { route: '/submissions' },
    )
    expect(await screen.findByText('Accepted')).toBeInTheDocument()
    expect(screen.getByText('two-sum')).toBeInTheDocument()
  })
})
