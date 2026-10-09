import type { ReactElement } from 'react'
import { Navigate, useLocation } from 'react-router-dom'
import { Spinner } from '../components/Spinner'
import { useAuth } from './AuthContext'

export function RequireAuth({ children }: { children: ReactElement }) {
  const { status } = useAuth()
  const location = useLocation()
  if (status === 'loading') return <Spinner label="Checking session" />
  if (status !== 'authenticated') {
    return <Navigate to="/login" state={{ from: location.pathname }} replace />
  }
  return children
}
