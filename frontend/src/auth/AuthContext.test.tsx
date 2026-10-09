import { afterEach, describe, expect, it, vi } from 'vitest'
import { act, render, screen, waitFor } from '@testing-library/react'
import { AuthProvider, useAuth } from './AuthContext'

function encodeSegment(value: object): string {
  return btoa(JSON.stringify(value)).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '')
}

const ACCESS = `h.${encodeSegment({ sub: 'alice', uid: 1, roles: ['ROLE_USER'] })}.s`

function Probe() {
  const { status, user, login } = useAuth()
  return (
    <div>
      <span data-testid="status">{status}</span>
      <span data-testid="user">{user?.username ?? ''}</span>
      <button onClick={() => void login('alice', 'secret123')}>login</button>
    </div>
  )
}

describe('AuthContext', () => {
  afterEach(() => {
    localStorage.clear()
    vi.restoreAllMocks()
  })

  it('logins, stores the refresh token, and exposes the decoded user', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async () =>
        new Response(
          JSON.stringify({ accessToken: ACCESS, refreshToken: 'refresh-1', expiresIn: 900 }),
          { status: 200, headers: { 'Content-Type': 'application/json' } },
        ),
      ),
    )
    render(
      <AuthProvider>
        <Probe />
      </AuthProvider>,
    )
    await act(async () => {
      screen.getByRole('button', { name: 'login' }).click()
    })
    await waitFor(() => expect(screen.getByTestId('status').textContent).toBe('authenticated'))
    expect(screen.getByTestId('user').textContent).toBe('alice')
    expect(localStorage.getItem('oj.refreshToken')).toBe('refresh-1')
  })

  it('restores a session from a stored refresh token on boot', async () => {
    localStorage.setItem('oj.refreshToken', 'refresh-1')
    vi.stubGlobal(
      'fetch',
      vi.fn(async () =>
        new Response(JSON.stringify({ accessToken: ACCESS, expiresIn: 900 }), {
          status: 200,
          headers: { 'Content-Type': 'application/json' },
        }),
      ),
    )
    render(
      <AuthProvider>
        <Probe />
      </AuthProvider>,
    )
    await waitFor(() => expect(screen.getByTestId('status').textContent).toBe('authenticated'))
  })

  it('treats a malformed stored access token as anonymous', async () => {
    localStorage.setItem('oj.refreshToken', 'refresh-1')
    vi.stubGlobal(
      'fetch',
      vi.fn(async () =>
        new Response(JSON.stringify({ accessToken: 'a.!!!.c', expiresIn: 900 }), {
          status: 200,
          headers: { 'Content-Type': 'application/json' },
        }),
      ),
    )
    render(
      <AuthProvider>
        <Probe />
      </AuthProvider>,
    )
    await waitFor(() => expect(screen.getByTestId('status').textContent).toBe('anonymous'))
    expect(screen.getByTestId('user').textContent).toBe('')
  })
})
