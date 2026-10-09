import { afterEach, describe, expect, it, vi } from 'vitest'
import { fireEvent, screen, waitFor } from '@testing-library/react'
import { Route, Routes } from 'react-router-dom'
import { AuthProvider } from '../../auth/AuthContext'
import { AdminProblemCreatePage } from './AdminProblemCreatePage'
import { renderWithProviders } from '../../test/renderWithProviders'

describe('AdminProblemCreatePage', () => {
  afterEach(() => {
    localStorage.clear()
    vi.restoreAllMocks()
  })

  it('posts the problem and navigates to its edit page', async () => {
    const fetchMock = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const url = String(input)
      if (url.includes('/admin/problems') && init?.method === 'POST') {
        return new Response(JSON.stringify({ id: 5, slug: 'new-problem' }), {
          status: 201,
          headers: { 'Content-Type': 'application/json' },
        })
      }
      return new Response('{}', { status: 200, headers: { 'Content-Type': 'application/json' } })
    })
    vi.stubGlobal('fetch', fetchMock)

    renderWithProviders(
      <AuthProvider>
        <Routes>
          <Route path="/admin/problems/new" element={<AdminProblemCreatePage />} />
          <Route path="/admin/problems/:id" element={<div>edit page</div>} />
        </Routes>
      </AuthProvider>,
      { route: '/admin/problems/new' },
    )

    fireEvent.change(screen.getByLabelText('Title'), { target: { value: 'New Problem' } })
    fireEvent.change(screen.getByLabelText('Statement'), { target: { value: 'Statement body' } })
    fireEvent.click(screen.getByRole('button', { name: /create/i }))

    await waitFor(() => expect(screen.getByText('edit page')).toBeInTheDocument())
    const post = fetchMock.mock.calls.find((call) => call[1]?.method === 'POST')
    expect(post).toBeTruthy()
    expect(JSON.parse(String(post![1]!.body))).toMatchObject({ title: 'New Problem' })
  })
})
