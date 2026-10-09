import { useState } from 'react'
import type { FormEvent } from 'react'
import { Link, useLocation, useNavigate } from 'react-router-dom'
import { ErrorBanner } from '../components/ErrorBanner'
import { useAuth } from '../auth/AuthContext'

export function LoginPage() {
  const { login } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<unknown>(null)
  const [busy, setBusy] = useState(false)

  const locationState = location.state as { from?: string; message?: string } | null
  const from = locationState?.from ?? '/'
  const message = locationState?.message

  async function onSubmit(event: FormEvent) {
    event.preventDefault()
    setBusy(true)
    setError(null)
    try {
      await login(username, password)
      navigate(from, { replace: true })
    } catch (caught) {
      setError(caught)
    } finally {
      setBusy(false)
    }
  }

  return (
    <section>
      <h1>Log in</h1>
      {message && (
        <p className="notice" role="status">
          {message}
        </p>
      )}
      {error !== null && <ErrorBanner error={error} />}
      <form onSubmit={onSubmit}>
        <label>
          Username
          <input value={username} onChange={(e) => setUsername(e.target.value)} required />
        </label>
        <label>
          Password
          <input
            type="password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            required
          />
        </label>
        <button type="submit" disabled={busy}>
          {busy ? 'Logging in…' : 'Log in'}
        </button>
      </form>
      <p>
        No account? <Link to="/register">Register</Link>
      </p>
    </section>
  )
}
