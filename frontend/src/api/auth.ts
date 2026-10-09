import { apiFetch, parseError } from './client'
import type {
  AccessTokenResponse,
  AuthTokensResponse,
  Registration,
  UserResponse,
} from './types'

interface Credentials {
  username: string
  password: string
}

async function rawPost<T>(path: string, body: unknown): Promise<T> {
  const response = await fetch(path, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  })
  if (!response.ok) throw await parseError(response)
  return (await response.json()) as T
}

export function login(credentials: Credentials): Promise<AuthTokensResponse> {
  return rawPost<AuthTokensResponse>('/api/v1/auth/login', credentials)
}

export function register(registration: Registration): Promise<UserResponse> {
  return rawPost<UserResponse>('/api/v1/auth/register', registration)
}

export function refresh(refreshToken: string): Promise<AccessTokenResponse> {
  return rawPost<AccessTokenResponse>('/api/v1/auth/refresh', { refreshToken })
}

export function logout(): Promise<void> {
  return apiFetch<void>('/api/v1/auth/logout', { method: 'POST' })
}
