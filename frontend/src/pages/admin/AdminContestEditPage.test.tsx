import { afterEach, describe, expect, it, vi } from 'vitest'
import { fireEvent, screen, waitFor } from '@testing-library/react'
import { Route, Routes } from 'react-router-dom'
import { AuthProvider } from '../../auth/AuthContext'
import { AdminContestEditPage } from './AdminContestEditPage'
import { renderWithProviders } from '../../test/renderWithProviders'

const DETAIL = {
  id: 1, name: 'Spring Cup', description: 'Annual contest',
  startTime: '2026-03-01T10:00:00Z', endTime: '2026-03-01T12:00:00Z',
  isPrivate: false, freezeMinutes: 60, status: 'UPCOMING', problemsPublishedAt: null,
}

function renderPage() {
  renderWithProviders(
    <AuthProvider>
      <Routes><Route path="/admin/contests/:id" element={<AdminContestEditPage />} /></Routes>
    </AuthProvider>,
    { route: '/admin/contests/1' },
  )
}

describe('AdminContestEditPage', () => {
  afterEach(() => {
    localStorage.clear()
    vi.restoreAllMocks()
  })

  it('loads and submits metadata', async () => {
    const fetchMock = vi.fn(async (_input: RequestInfo | URL, init?: RequestInit) => {
      if (init?.method === 'PUT') {
        return new Response(null, { status: 204 })
      }
      return new Response(JSON.stringify(DETAIL), {
        status: 200, headers: { 'Content-Type': 'application/json' },
      })
    })
    vi.stubGlobal('fetch', fetchMock)
    renderPage()

    const name = await screen.findByLabelText('Name')
    expect(name).toHaveValue('Spring Cup')

    fireEvent.change(name, { target: { value: 'Spring Cup 2' } })
    fireEvent.click(screen.getByRole('button', { name: /save metadata/i }))

    await waitFor(() => {
      const put = fetchMock.mock.calls.find((call) => call[1]?.method === 'PUT')
      expect(put).toBeTruthy()
      expect(JSON.parse(String(put![1]!.body))).toMatchObject({ name: 'Spring Cup 2' })
    })
  })

  it('disables the form once the contest has started', async () => {
    vi.stubGlobal('fetch', vi.fn(async () =>
      new Response(JSON.stringify({ ...DETAIL, status: 'RUNNING' }), {
        status: 200, headers: { 'Content-Type': 'application/json' },
      })))
    renderPage()

    expect(await screen.findByText('This contest has started and can no longer be edited.')).toBeInTheDocument()
    expect(screen.getByLabelText('Name')).toBeDisabled()
    expect(screen.getByRole('button', { name: /save metadata/i })).toBeDisabled()
  })
})
