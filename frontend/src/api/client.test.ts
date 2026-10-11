import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError, apiFetch, configureAuthHooks } from './client'

function jsonResponse(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}

describe('apiFetch', () => {
  beforeEach(() => {
    configureAuthHooks({
      getAccessToken: () => 'token-1',
      refreshAccessToken: vi.fn(async () => null),
      onSessionExpired: vi.fn(),
    })
  })

  afterEach(() => vi.restoreAllMocks())

  it('attaches the bearer token and parses JSON', async () => {
    const fetchMock = vi.fn<typeof fetch>(async () => jsonResponse({ ok: true }))
    vi.stubGlobal('fetch', fetchMock)
    const result = await apiFetch<{ ok: boolean }>('/api/v1/ping')
    expect(result).toEqual({ ok: true })
    const [, init] = fetchMock.mock.calls[0]
    expect(new Headers(init?.headers).get('Authorization')).toBe('Bearer token-1')
  })

  it('throws ApiError parsed from problem+json', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async () =>
        jsonResponse({ title: 'Not Found', detail: 'no such problem' }, 404),
      ),
    )
    await expect(apiFetch('/api/v1/problems/none')).rejects.toMatchObject({
      status: 404,
      title: 'Not Found',
      detail: 'no such problem',
    })
  })

  it('refreshes once on 401 and retries the request', async () => {
    const refreshAccessToken = vi.fn(async () => 'token-2')
    configureAuthHooks({
      getAccessToken: () => 'expired',
      refreshAccessToken,
      onSessionExpired: vi.fn(),
    })
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(jsonResponse({}, 401))
      .mockResolvedValueOnce(jsonResponse({ ok: true }))
    vi.stubGlobal('fetch', fetchMock)

    await expect(apiFetch<{ ok: boolean }>('/api/v1/me')).resolves.toEqual({ ok: true })
    expect(refreshAccessToken).toHaveBeenCalledTimes(1)
    expect(new Headers(fetchMock.mock.calls[1][1]?.headers).get('Authorization')).toBe(
      'Bearer token-2',
    )
  })

  it('signals session expiry when refresh fails', async () => {
    const onSessionExpired = vi.fn()
    configureAuthHooks({
      getAccessToken: () => 'expired',
      refreshAccessToken: vi.fn(async () => null),
      onSessionExpired,
    })
    vi.stubGlobal('fetch', vi.fn(async () => jsonResponse({}, 401)))
    await expect(apiFetch('/api/v1/me')).rejects.toBeInstanceOf(ApiError)
    expect(onSessionExpired).toHaveBeenCalledTimes(1)
  })
})
