import { Link, useNavigate } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'

export function NavBar() {
  const { status, user, logout } = useAuth()
  const navigate = useNavigate()

  async function onLogout() {
    await logout()
    navigate('/')
  }

  return (
    <nav className="nav">
      <Link to="/" className="nav__brand">
        Online Judge
      </Link>
      <Link to="/">Problems</Link>
      {status === 'authenticated' && <Link to="/submissions">My Submissions</Link>}
      <span className="nav__spacer" />
      {status === 'authenticated' ? (
        <>
          <span className="nav__user">{user?.username}</span>
          <button type="button" onClick={onLogout}>
            Log out
          </button>
        </>
      ) : (
        <>
          <Link to="/login">Log in</Link>
          <Link to="/register">Register</Link>
        </>
      )}
    </nav>
  )
}
