import { afterEach, describe, expect, it, vi } from 'vitest'
import { screen } from '@testing-library/react'
import { AuthProvider } from '../auth/AuthContext'
import { LandingPage } from './LandingPage'
import { renderWithProviders } from '../test/renderWithProviders'

function encodeSegment(value: object): string {
  return btoa(JSON.stringify(value)).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '')
}

const ACCESS = `h.${encodeSegment({ sub: 'alice', uid: 1, roles: [] })}.s`

describe('LandingPage', () => {
  afterEach(() => {
    localStorage.clear()
    vi.restoreAllMocks()
  })

  it('shows the hero and links to the problem list and registration', () => {
    renderWithProviders(
      <AuthProvider>
        <LandingPage />
      </AuthProvider>,
    )
    expect(
      screen.getByRole('heading', { name: /where your code meets its judge/i }),
    ).toBeInTheDocument()
    expect(screen.getByRole('link', { name: /browse problems/i })).toHaveAttribute(
      'href',
      '/problems',
    )
    expect(screen.getByRole('link', { name: /create account/i })).toHaveAttribute(
      'href',
      '/register',
    )
  })

  it('links to submissions instead of registration when authenticated', async () => {
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
    renderWithProviders(
      <AuthProvider>
        <LandingPage />
      </AuthProvider>,
    )
    expect(await screen.findByRole('link', { name: /my submissions/i })).toHaveAttribute(
      'href',
      '/submissions',
    )
    expect(screen.queryByRole('link', { name: /create account/i })).not.toBeInTheDocument()
  })

  it('shows the mock editor window', () => {
    renderWithProviders(
      <AuthProvider>
        <LandingPage />
      </AuthProvider>,
    )
    expect(screen.getByText('Main.java — two-sum')).toBeInTheDocument()
    expect(screen.getByText('Main.java')).toBeInTheDocument()
    expect(screen.getByText('solution.py')).toBeInTheDocument()
  })
})
