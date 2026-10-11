import { afterEach, describe, expect, it, vi } from 'vitest'
import { screen, within } from '@testing-library/react'
import App from './App'
import { renderWithProviders } from './test/renderWithProviders'
import { AuthProvider } from './auth/AuthContext'

describe('App shell', () => {
  afterEach(() => {
    localStorage.clear()
    vi.restoreAllMocks()
  })

  it('renders the landing page at / with navigation to the problem list', () => {
    renderWithProviders(
      <AuthProvider>
        <App />
      </AuthProvider>,
    )
    expect(
      screen.getByRole('heading', { name: /where your code meets its judge/i }),
    ).toBeInTheDocument()
    expect(screen.getByRole('link', { name: /online judge/i })).toHaveAttribute('href', '/')
    expect(
      within(screen.getByRole('navigation')).getByRole('link', { name: 'Problems' }),
    ).toHaveAttribute('href', '/problems')
  })
})
