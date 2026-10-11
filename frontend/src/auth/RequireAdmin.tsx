import type { ReactElement } from 'react'
import { useAuth } from './AuthContext'

export function RequireAdmin({ children }: { children: ReactElement }) {
  const { user } = useAuth()
  if (!user?.roles.includes('ROLE_ADMIN')) {
    return (
      <section>
        <h1>Forbidden</h1>
        <p>This area is for administrators only.</p>
      </section>
    )
  }
  return children
}
