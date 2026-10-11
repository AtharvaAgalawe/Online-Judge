import { afterEach, describe, expect, it, vi } from 'vitest'
import { screen } from '@testing-library/react'
import { Route, Routes } from 'react-router-dom'
import { AuthProvider } from '../../auth/AuthContext'
import { AdminSubmissionsPage } from './AdminSubmissionsPage'
import { renderWithProviders } from '../../test/renderWithProviders'

const PAGE = {
  content: [{
    id: 9, problemId: 1, problemSlug: 'two-sum', languageName: 'Python 3.12',
    status: 'COMPLETED', verdict: 'ACCEPTED', timeUsedMs: 10, memoryUsedKb: 2048,
    submittedAt: '2026-01-01T00:00:00Z',
  }],
  page: 0, size: 20, totalElements: 1, totalPages: 1,
}

describe('AdminSubmissionsPage', () => {
  afterEach(() => {
    localStorage.clear()
    vi.restoreAllMocks()
  })

  it('renders submissions across users', async () => {
    vi.stubGlobal('fetch', vi.fn(async () =>
      new Response(JSON.stringify(PAGE), { status: 200, headers: { 'Content-Type': 'application/json' } })))
    renderWithProviders(
      <AuthProvider>
        <Routes><Route path="/admin/submissions" element={<AdminSubmissionsPage />} /></Routes>
      </AuthProvider>,
      { route: '/admin/submissions' },
    )
    expect(await screen.findByText('two-sum')).toBeInTheDocument()
    expect(screen.getByText('Accepted')).toBeInTheDocument()
  })
})
