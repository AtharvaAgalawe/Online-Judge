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
    expect(screen.getByRole('columnheader', { name: '#' })).toBeInTheDocument()
    expect(screen.getByRole('columnheader', { name: 'ID' })).toBeInTheDocument()
    expect(screen.getByRole('cell', { name: '1' })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: '42' })).toHaveAttribute(
      'href',
      '/submissions/42',
    )
    expect(screen.getByText('two-sum')).toBeInTheDocument()
  })

  it('continues row numbering across pages', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async () =>
        new Response(JSON.stringify({ ...PAGE, page: 2 }), {
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
      { route: '/submissions?page=2' },
    )
    expect(await screen.findByRole('cell', { name: '41' })).toBeInTheDocument()
  })
})
