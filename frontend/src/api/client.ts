export class ApiError extends Error {
  readonly status: number
  readonly title: string
  readonly detail: string

  constructor(status: number, title: string, detail: string) {
    super(detail || title)
    this.name = 'ApiError'
    this.status = status
    this.title = title
    this.detail = detail
  }
}

interface AuthHooks {
  getAccessToken: () => string | null
  refreshAccessToken: () => Promise<string | null>
  onSessionExpired: () => void
}

let hooks: AuthHooks = {
  getAccessToken: () => null,
  refreshAccessToken: async () => null,
  onSessionExpired: () => {},
}

export function configureAuthHooks(next: AuthHooks): void {
  hooks = next
}

let refreshInFlight: Promise<string | null> | null = null

function refreshOnce(): Promise<string | null> {
  if (!refreshInFlight) {
    refreshInFlight = hooks.refreshAccessToken().finally(() => {
      refreshInFlight = null
    })
  }
  return refreshInFlight
}

export async function parseError(response: Response): Promise<ApiError> {
  let title = 'Request failed'
  let detail = `HTTP ${response.status}`
  try {
    const body = (await response.json()) as { title?: string; detail?: string }
    if (typeof body.title === 'string') title = body.title
    if (typeof body.detail === 'string') detail = body.detail
  } catch {
    // Non-JSON error body; keep HTTP-derived defaults.
  }
  return new ApiError(response.status, title, detail)
}

function send(path: string, init: RequestInit, token: string | null): Promise<Response> {
  const headers = new Headers(init.headers)
  if (init.body !== undefined && !headers.has('Content-Type')) {
    headers.set('Content-Type', 'application/json')
  }
  if (token) headers.set('Authorization', `Bearer ${token}`)
  return fetch(path, { ...init, headers })
}

async function parseBody<T>(response: Response): Promise<T> {
  if (response.status === 204) return undefined as T
  return (await response.json()) as T
}

export async function apiFetch<T>(
  path: string,
  init: RequestInit = {},
  allowRetry = true,
): Promise<T> {
  const response = await send(path, init, hooks.getAccessToken())

  if (response.status === 401 && allowRetry) {
    const refreshed = await refreshOnce()
    if (refreshed) {
      const retryResponse = await send(path, init, refreshed)
      if (!retryResponse.ok) throw await parseError(retryResponse)
      return parseBody<T>(retryResponse)
    }
    hooks.onSessionExpired()
    throw await parseError(response)
  }
  if (!response.ok) throw await parseError(response)
  return parseBody<T>(response)
}
