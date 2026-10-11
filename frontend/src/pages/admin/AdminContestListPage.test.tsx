import { afterEach, describe, expect, it, vi } from 'vitest'
import { screen } from '@testing-library/react'
import { Route, Routes } from 'react-router-dom'
import { AuthProvider } from '../../auth/AuthContext'
import type { AdminContestSummary } from '../../api/admin'
import { AdminContestListPage } from './AdminContestListPage'
import { renderWithProviders } from '../../test/renderWithProviders'

const CONTEST: AdminContestSummary = {
  id: 3, name: 'Spring Cup', startTime: '2026-03-01T10:00:00Z', endTime: '2026-03-01T12:00:00Z',
  status: 'ENDED', isPrivate: true, participantCount: 12, problemCount: 4,
  problemsPublishedAt: '2026-03-01T09:00:00Z',
}

const PAGE = {
  content: [CONTEST],
  page: 0, size: 20, totalElements: 1, totalPages: 1,
}

function stubListResponse(content: AdminContestSummary[] = PAGE.content) {
  vi.stubGlobal('fetch', vi.fn(async () =>
    new Response(JSON.stringify({ ...PAGE, content, totalElements: content.length }),
      { status: 200, headers: { 'Content-Type': 'application/json' } })))
}

function renderPage() {
  renderWithProviders(
    <AuthProvider>
      <Routes><Route path="/admin/contests" element={<AdminContestListPage />} /></Routes>
    </AuthProvider>,
    { route: '/admin/contests' },
  )
}

describe('AdminContestListPage', () => {
  afterEach(() => {
    localStorage.clear()
    vi.restoreAllMocks()
  })

  it('renders a private contest with the Private marker and Ended chip', async () => {
    stubListResponse()
    renderPage()

    expect(await screen.findByText('Spring Cup')).toBeInTheDocument()
    expect(screen.getByText('Private')).toBeInTheDocument()
    expect(screen.getByText('Ended', { selector: '.contest-chip' })).toBeInTheDocument()
  })

  it('links to the new contest page', async () => {
    stubListResponse()
    renderPage()

    expect(await screen.findByRole('link', { name: /new contest/i }))
      .toHaveAttribute('href', '/admin/contests/new')
  })

  it('shows the problems-published marker only when published', async () => {
    stubListResponse([
      { ...CONTEST, id: 4, name: 'Winter Cup', problemsPublishedAt: null },
      CONTEST,
    ])
    renderPage()

    expect(await screen.findByText('Winter Cup')).toBeInTheDocument()
    expect(screen.getByText('—')).toBeInTheDocument()
    expect(screen.getByText(/^✓/)).toBeInTheDocument()
  })
})
