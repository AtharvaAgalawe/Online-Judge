export interface JwtUser {
  id: number
  username: string
  roles: string[]
}

function base64UrlDecode(segment: string): string {
  const normalized = segment.replace(/-/g, '+').replace(/_/g, '/')
  const padding = normalized.length % 4 === 0 ? '' : '='.repeat(4 - (normalized.length % 4))
  return atob(normalized + padding)
}

export function decodeJwt(token: string): JwtUser | null {
  const parts = token.split('.')
  if (parts.length < 2) return null
  try {
    const payload = JSON.parse(base64UrlDecode(parts[1])) as {
      sub?: unknown
      uid?: unknown
      roles?: unknown
    }
    if (typeof payload.sub !== 'string' || typeof payload.uid !== 'number') return null
    const roles = Array.isArray(payload.roles)
      ? payload.roles.filter((role): role is string => typeof role === 'string')
      : []
    return { id: payload.uid, username: payload.sub, roles }
  } catch {
    return null
  }
}
