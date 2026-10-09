import { afterEach, describe, expect, it, vi } from 'vitest'
import { fireEvent, screen, waitFor } from '@testing-library/react'
import { Route, Routes } from 'react-router-dom'
import { AuthProvider } from '../auth/AuthContext'
import { RegisterPage } from './RegisterPage'
import { LoginPage } from './LoginPage'
import { renderWithProviders } from '../test/renderWithProviders'

describe('RegisterPage', () => {
  afterEach(() => {
    localStorage.clear()
    vi.restoreAllMocks()
  })

  it('redirects to login with a message when auto-login after registration fails', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async (input: RequestInfo | URL) => {
        const url = String(input)
        if (url.includes('/auth/register')) {
          return new Response(JSON.stringify({ id: 1, username: 'alice', email: 'a@b.co' }), {
            status: 201,
            headers: { 'Content-Type': 'application/json' },
          })
        }
        if (url.includes('/auth/login')) {
          return new Response(JSON.stringify({ title: 'Unauthorized', detail: 'Bad credentials' }), {
            status: 401,
            headers: { 'Content-Type': 'application/json' },
          })
        }
        return new Response('{}', {
          status: 200,
          headers: { 'Content-Type': 'application/json' },
        })
      }),
    )

    renderWithProviders(
      <AuthProvider>
        <Routes>
          <Route path="/register" element={<RegisterPage />} />
          <Route path="/login" element={<LoginPage />} />
        </Routes>
      </AuthProvider>,
      { route: '/register' },
    )

    fireEvent.change(screen.getByLabelText('Username'), { target: { value: 'alice' } })
    fireEvent.change(screen.getByLabelText('Email'), { target: { value: 'a@b.co' } })
    fireEvent.change(screen.getByLabelText('Password'), { target: { value: 'secret123' } })
    fireEvent.click(screen.getByRole('button', { name: 'Create account' }))

    await waitFor(() =>
      expect(screen.getByText('Account created. Please log in.')).toBeInTheDocument(),
    )
  })
})
