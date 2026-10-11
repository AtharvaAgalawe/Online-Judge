import { Link } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'

export function LandingPage() {
  const { status } = useAuth()
  const authenticated = status === 'authenticated'

  return (
    <div className="landing">
      <section className="hero">
        <h1 className="hero__title">
          {'Where Your Code Meets Its Judge'}
          <span className="hero__title-faded">{"And 'TLE' Wasn't Your Fault"}</span>
        </h1>
        <p className="hero__sub">
          Compiled, sandboxed, and run in isolated containers — verdicts in seconds, not excuses.
        </p>
        <div className="hero__actions">
          <Link to="/problems" className="btn btn--solid">
            Browse Problems
          </Link>
          {authenticated ? (
            <Link to="/submissions" className="btn btn--outline">
              My Submissions
            </Link>
          ) : (
            <Link to="/register" className="btn btn--outline">
              Create Account
            </Link>
          )}
        </div>
      </section>
    </div>
  )
}
