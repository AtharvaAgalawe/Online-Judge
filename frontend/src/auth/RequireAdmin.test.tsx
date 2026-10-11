import { describe, expect, it, vi, afterEach } from 'vitest'
import { screen, waitFor } from '@testing-library/react'
import { Route, Routes } from 'react-router-dom'
import { AuthProvider } from './AuthContext'
import { RequireAdmin } from './RequireAdmin'
import { renderWithProviders } from '../test/renderWithProviders'

function encodeSegment(value: object): string {
  return btoa(JSON.stringify(value)).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '')
}

function tokenWithRoles(roles: string[]): string {
  return `h.${encodeSegment({ sub: 'u', uid: 1, roles })}.s`
}

describe('RequireAdmin', () => {
  afterEach(() => {
    localStorage.clear()
    vi.restoreAllMocks()
  })

  it('blocks a non-admin', async () => {
    localStorage.setItem('oj.refreshToken', 'r')
    vi.stubGlobal('fetch', vi.fn(async () =>
      new Response(JSON.stringify({ accessToken: tokenWithRoles(['ROLE_USER']), expiresIn: 900 }),
        { status: 200, headers: { 'Content-Type': 'application/json' } })))
    renderWithProviders(
      <AuthProvider>
        <Routes>
          <Route path="/admin" element={<RequireAdmin><div>admin content</div></RequireAdmin>} />
        </Routes>
      </AuthProvider>,
      { route: '/admin' },
    )
    await waitFor(() => expect(screen.getByText(/forbidden/i)).toBeInTheDocument())
  })

  it('admits an admin', async () => {
    localStorage.setItem('oj.refreshToken', 'r')
    vi.stubGlobal('fetch', vi.fn(async () =>
      new Response(JSON.stringify({ accessToken: tokenWithRoles(['ROLE_ADMIN']), expiresIn: 900 }),
        { status: 200, headers: { 'Content-Type': 'application/json' } })))
    renderWithProviders(
      <AuthProvider>
        <Routes>
          <Route path="/admin" element={<RequireAdmin><div>admin content</div></RequireAdmin>} />
        </Routes>
      </AuthProvider>,
      { route: '/admin' },
    )
    await waitFor(() => expect(screen.getByText('admin content')).toBeInTheDocument())
  })
})
