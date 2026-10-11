import { afterEach, describe, expect, it, vi } from 'vitest'
import { fireEvent, screen, waitFor } from '@testing-library/react'
import { Route, Routes } from 'react-router-dom'
import { AuthProvider } from '../../auth/AuthContext'
import { AdminProblemEditPage } from './AdminProblemEditPage'
import { renderWithProviders } from '../../test/renderWithProviders'

const DETAIL = {
  id: 5, slug: 'two-sum', title: 'Two Sum', statement: 'Add.', difficulty: 'EASY',
  timeLimitMs: 2000, memoryLimitKb: 262144, published: false, createdBy: 'admin',
  testCaseCount: 1, tags: ['arrays'], createdAt: '2026-01-01T00:00:00Z',
  updatedAt: '2026-01-01T00:00:00Z',
}

describe('AdminProblemEditPage', () => {
  afterEach(() => {
    localStorage.clear()
    vi.restoreAllMocks()
  })

  it('publishes via PUT with published=true', async () => {
    const fetchMock = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const url = String(input)
      if (init?.method === 'PUT') {
        return new Response(JSON.stringify({ ...DETAIL, published: true }), {
          status: 200, headers: { 'Content-Type': 'application/json' },
        })
      }
      if (url.includes('/test-cases')) {
        return new Response('[]', { status: 200, headers: { 'Content-Type': 'application/json' } })
      }
      return new Response(JSON.stringify(DETAIL), {
        status: 200, headers: { 'Content-Type': 'application/json' },
      })
    })
    vi.stubGlobal('fetch', fetchMock)

    renderWithProviders(
      <AuthProvider>
        <Routes>
          <Route path="/admin/problems/:id" element={<AdminProblemEditPage />} />
        </Routes>
      </AuthProvider>,
      { route: '/admin/problems/5' },
    )

    fireEvent.click(await screen.findByRole('button', { name: /publish/i }))
    await waitFor(() => {
      const put = fetchMock.mock.calls.find((call) => call[1]?.method === 'PUT')
      expect(put).toBeTruthy()
      expect(JSON.parse(String(put![1]!.body))).toEqual({ published: true })
    })
  })

  it('disables publishing when there are no test cases', async () => {
    const fetchMock = vi.fn(async (input: RequestInfo | URL) => {
      const url = String(input)
      if (url.includes('/test-cases')) {
        return new Response('[]', { status: 200, headers: { 'Content-Type': 'application/json' } })
      }
      return new Response(JSON.stringify({ ...DETAIL, testCaseCount: 0 }), {
        status: 200, headers: { 'Content-Type': 'application/json' },
      })
    })
    vi.stubGlobal('fetch', fetchMock)

    renderWithProviders(
      <AuthProvider>
        <Routes>
          <Route path="/admin/problems/:id" element={<AdminProblemEditPage />} />
        </Routes>
      </AuthProvider>,
      { route: '/admin/problems/5' },
    )

    expect(await screen.findByRole('button', { name: /^publish$/i })).toBeDisabled()
    expect(screen.getByText('Add at least one test case before publishing.')).toBeInTheDocument()
  })
})
