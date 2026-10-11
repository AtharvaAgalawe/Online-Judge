import { Link, Outlet } from 'react-router-dom'
import { NavBar } from './NavBar'

export function Layout() {
  return (
    <div className="frame">
      <NavBar />
      <main className="layout__main">
        <Outlet />
      </main>
      <footer className="footer">
        <span className="footer__brand">Online Judge</span>
        <span className="footer__spacer" />
        <Link to="/problems">Problems</Link>
      </footer>
    </div>
  )
}
