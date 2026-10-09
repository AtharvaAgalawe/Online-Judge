import { useState } from 'react'
import type { FormEvent } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { ErrorBanner } from '../components/ErrorBanner'
import { useAuth } from '../auth/AuthContext'
import { AutoLoginFailedError } from '../auth/errors'

export function RegisterPage() {
  const { register } = useAuth()
  const navigate = useNavigate()
  const [username, setUsername] = useState('')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<unknown>(null)
  const [busy, setBusy] = useState(false)

  async function onSubmit(event: FormEvent) {
    event.preventDefault()
    setBusy(true)
    setError(null)
    try {
      await register({ username, email, password })
      navigate('/', { replace: true })
    } catch (caught) {
      if (caught instanceof AutoLoginFailedError) {
        navigate('/login', { state: { message: 'Account created. Please log in.' } })
        return
      }
      setError(caught)
    } finally {
      setBusy(false)
    }
  }

  return (
    <section>
      <h1>Register</h1>
      {error !== null && <ErrorBanner error={error} />}
      <form onSubmit={onSubmit}>
        <label>
          Username
          <input
            value={username}
            onChange={(e) => setUsername(e.target.value)}
            required
            minLength={3}
            maxLength={30}
            pattern="[a-zA-Z0-9]+"
          />
        </label>
        <label>
          Email
          <input
            type="email"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            required
          />
        </label>
        <label>
          Password
          <input
            type="password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            required
            minLength={8}
            maxLength={72}
          />
        </label>
        <button type="submit" disabled={busy}>
          {busy ? 'Creating account…' : 'Create account'}
        </button>
      </form>
      <p>
        Already registered? <Link to="/login">Log in</Link>
      </p>
    </section>
  )
}
