import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useRef,
  useState,
} from 'react'
import type { ReactNode } from 'react'
import { configureAuthHooks } from '../api/client'
import * as authApi from '../api/auth'
import { decodeJwt } from './decodeJwt'
import type { JwtUser } from './decodeJwt'
import { AutoLoginFailedError } from './errors'
import type { Registration } from '../api/types'

export const REFRESH_TOKEN_KEY = 'oj.refreshToken'

type AuthStatus = 'loading' | 'authenticated' | 'anonymous'

interface AuthContextValue {
  status: AuthStatus
  user: JwtUser | null
  login: (username: string, password: string) => Promise<void>
  register: (registration: Registration) => Promise<void>
  logout: () => Promise<void>
}

const AuthContext = createContext<AuthContextValue | null>(null)

export function AuthProvider({ children }: { children: ReactNode }) {
  const tokenRef = useRef<string | null>(null)
  const [user, setUser] = useState<JwtUser | null>(null)
  const [status, setStatus] = useState<AuthStatus>('loading')

  const applyToken = useCallback((token: string | null) => {
    const decoded = token ? decodeJwt(token) : null
    tokenRef.current = decoded ? token : null
    setUser(decoded)
    setStatus(decoded ? 'authenticated' : 'anonymous')
  }, [])

  const clearSession = useCallback(() => {
    localStorage.removeItem(REFRESH_TOKEN_KEY)
    applyToken(null)
  }, [applyToken])

  const refreshAccessToken = useCallback(async (): Promise<string | null> => {
    const stored = localStorage.getItem(REFRESH_TOKEN_KEY)
    if (!stored) {
      applyToken(null)
      return null
    }
    try {
      const response = await authApi.refresh(stored)
      applyToken(response.accessToken)
      return response.accessToken
    } catch {
      clearSession()
      return null
    }
  }, [applyToken, clearSession])

  useEffect(() => {
    configureAuthHooks({
      getAccessToken: () => tokenRef.current,
      refreshAccessToken,
      onSessionExpired: clearSession,
    })
  }, [refreshAccessToken, clearSession])

  useEffect(() => {
    void refreshAccessToken()
  }, [refreshAccessToken])

  const login = useCallback(
    async (username: string, password: string) => {
      const tokens = await authApi.login({ username, password })
      localStorage.setItem(REFRESH_TOKEN_KEY, tokens.refreshToken)
      applyToken(tokens.accessToken)
    },
    [applyToken],
  )

  const register = useCallback(
    async (registration: Registration) => {
      await authApi.register(registration)
      try {
        await login(registration.username, registration.password)
      } catch {
        throw new AutoLoginFailedError()
      }
    },
    [login],
  )

  const logout = useCallback(async () => {
    try {
      await authApi.logout()
    } catch {
      // Logging out locally regardless of the server response.
    }
    clearSession()
  }, [clearSession])

  const value = useMemo<AuthContextValue>(
    () => ({ status, user, login, register, logout }),
    [status, user, login, register, logout],
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export function useAuth(): AuthContextValue {
  const context = useContext(AuthContext)
  if (!context) throw new Error('useAuth must be used within an AuthProvider')
  return context
}
