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
        <span className="nav__mark" aria-hidden="true">
          OJ
        </span>
        Online Judge
      </Link>
      <Link to="/problems">Problems</Link>
      <Link to="/contests">Contests</Link>
      {status === 'authenticated' && <Link to="/submissions">My Submissions</Link>}
      {status === 'authenticated' && user?.roles.includes('ROLE_ADMIN') && (
        <Link to="/admin/problems">Admin</Link>
      )}
      <span className="nav__spacer" />
      {status === 'authenticated' ? (
        <>
          <span className="nav__user">{user?.username}</span>
          <button type="button" className="btn btn--outline" onClick={onLogout}>
            Log out
          </button>
        </>
      ) : (
        <>
          <Link to="/login" className="btn btn--outline">
            Sign in
          </Link>
          <Link to="/register" className="btn btn--solid">
            Register
          </Link>
        </>
      )}
    </nav>
  )
}
