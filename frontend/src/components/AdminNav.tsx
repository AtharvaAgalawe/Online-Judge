import { Link } from 'react-router-dom'

const LINKS = [
  { to: '/admin/problems', label: 'Problems' },
  { to: '/admin/contests', label: 'Contests' },
  { to: '/admin/submissions', label: 'Submissions' },
  { to: '/admin/queue', label: 'Queue' },
]

export function AdminNav() {
  return (
    <nav className="contest-tabs" aria-label="Admin sections">
      {LINKS.map((link) => (
        <Link key={link.to} to={link.to} className="btn btn--outline">
          {link.label}
        </Link>
      ))}
    </nav>
  )
}
