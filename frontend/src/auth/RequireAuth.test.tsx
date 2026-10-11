import { afterEach, describe, expect, it, vi } from 'vitest'
import { screen, waitFor } from '@testing-library/react'
import { Route, Routes } from 'react-router-dom'
import { AuthProvider } from './AuthContext'
import { RequireAuth } from './RequireAuth'
import { renderWithProviders } from '../test/renderWithProviders'

describe('RequireAuth', () => {
  afterEach(() => {
    localStorage.clear()
    vi.restoreAllMocks()
  })

  it('redirects anonymous users to the login page', async () => {
    renderWithProviders(
      <AuthProvider>
        <Routes>
          <Route
            path="/secret"
            element={
              <RequireAuth>
                <div>secret</div>
              </RequireAuth>
            }
          />
          <Route path="/login" element={<div>login page</div>} />
        </Routes>
      </AuthProvider>,
      { route: '/secret' },
    )
    await waitFor(() => expect(screen.getByText('login page')).toBeInTheDocument())
  })
})
