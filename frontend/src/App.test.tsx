import { afterEach, describe, expect, it, vi } from 'vitest'
import { screen } from '@testing-library/react'
import App from './App'
import { renderWithProviders } from './test/renderWithProviders'
import { AuthProvider } from './auth/AuthContext'

describe('App shell', () => {
  afterEach(() => {
    localStorage.clear()
    vi.restoreAllMocks()
  })

  it('renders the brand and the landing page', () => {
    renderWithProviders(
      <AuthProvider>
        <App />
      </AuthProvider>,
    )
    expect(screen.getByRole('link', { name: /online judge/i })).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: /problems/i })).toBeInTheDocument()
  })
})
