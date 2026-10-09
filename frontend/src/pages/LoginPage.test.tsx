import { afterEach, describe, expect, it, vi } from 'vitest'
import { fireEvent, screen, waitFor } from '@testing-library/react'
import { Route, Routes } from 'react-router-dom'
import { AuthProvider } from '../auth/AuthContext'
import { LoginPage } from './LoginPage'
import { renderWithProviders } from '../test/renderWithProviders'

function encodeSegment(value: object): string {
  return btoa(JSON.stringify(value)).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '')
}

describe('LoginPage', () => {
  afterEach(() => {
    localStorage.clear()
    vi.restoreAllMocks()
  })

  it('logs in and navigates home', async () => {
    const access = `h.${encodeSegment({ sub: 'alice', uid: 1, roles: [] })}.s`
    vi.stubGlobal(
      'fetch',
      vi.fn(async () =>
        new Response(JSON.stringify({ accessToken: access, refreshToken: 'r', expiresIn: 900 }), {
          status: 200,
          headers: { 'Content-Type': 'application/json' },
        }),
      ),
    )
    renderWithProviders(
      <AuthProvider>
        <Routes>
          <Route path="/login" element={<LoginPage />} />
          <Route path="/" element={<div>home page</div>} />
        </Routes>
      </AuthProvider>,
      { route: '/login' },
    )
    fireEvent.change(screen.getByLabelText('Username'), { target: { value: 'alice' } })
    fireEvent.change(screen.getByLabelText('Password'), { target: { value: 'secret123' } })
    fireEvent.click(screen.getByRole('button', { name: 'Log in' }))
    await waitFor(() => expect(screen.getByText('home page')).toBeInTheDocument())
  })
})
