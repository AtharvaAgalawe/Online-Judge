import { afterEach, describe, expect, it, vi } from 'vitest'
import { fireEvent, screen, waitFor } from '@testing-library/react'
import { Route, Routes } from 'react-router-dom'
import { AuthProvider } from '../auth/AuthContext'
import type { ContestDetail } from '../api/contests'
import { renderWithProviders } from '../test/renderWithProviders'
import { ContestDetailPage } from './ContestDetailPage'

function encodeSegment(value: object): string {
  return btoa(JSON.stringify(value)).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '')
}

const ACCESS = `h.${encodeSegment({ sub: 'alice', uid: 1, roles: [] })}.s`

const STANDINGS = { frozen: false, frozenAt: null, rows: [] }

function json(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}

function contestDetail(overrides: Partial<ContestDetail> = {}): ContestDetail {
  return {
    id: 1,
    name: 'Spring Cup',
    description: null,
    startTime: '2026-10-11T09:00:00Z',
    endTime: '2026-10-11T12:00:00Z',
    status: 'UPCOMING',
    freezeMinutes: 30,
    participantCount: 12,
    registered: false,
    registrationOpen: true,
    problemsRevealed: false,
    problems: null,
    ...overrides,
  }
}

function renderPage() {
  return renderWithProviders(
    <AuthProvider>
      <Routes>
        <Route path="/contests/:id" element={<ContestDetailPage />} />
        <Route path="/contests/:id/problems/:slug" element={<div>problem page</div>} />
        <Route path="/login" element={<div>login page</div>} />
      </Routes>
    </AuthProvider>,
    { route: '/contests/1' },
  )
}

describe('ContestDetailPage', () => {
  afterEach(() => {
    localStorage.clear()
    vi.restoreAllMocks()
  })

  it('shows locked problems before start', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async (input: RequestInfo | URL) => {
        if (String(input).includes('/standings')) return json(STANDINGS)
        return json(contestDetail())
      }),
    )

    renderPage()

    expect(
      await screen.findByText('Problems will be revealed when the contest starts.'),
    ).toBeInTheDocument()
    expect(screen.queryByText('Two Sum')).not.toBeInTheDocument()
  })

  it('shows problems and navigates to the contest problem', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async (input: RequestInfo | URL) => {
        if (String(input).includes('/standings')) return json(STANDINGS)
        return json(
          contestDetail({
            status: 'RUNNING',
            problemsRevealed: true,
            problems: [
              {
                problemId: 5,
                slug: 'two-sum',
                title: 'Two Sum',
                difficulty: 'EASY',
                displayOrder: 1,
                points: 100,
                solvedByMe: false,
              },
            ],
          }),
        )
      }),
    )

    renderPage()

    const problemButton = await screen.findByRole('button', { name: 'Two Sum' })
    expect(screen.getByText('Standings')).toBeInTheDocument()

    fireEvent.click(problemButton)

    expect(await screen.findByText('problem page')).toBeInTheDocument()
  })

  it('registers for the contest', async () => {
    localStorage.setItem('oj.refreshToken', 'refresh-1')
    const fetchMock = vi.fn<(input: RequestInfo | URL, init?: RequestInit) => Promise<Response>>(
      async (input) => {
        const url = String(input)
        if (url.includes('/standings')) return json(STANDINGS)
        if (url.includes('/auth/refresh')) return json({ accessToken: ACCESS, expiresIn: 900 })
        if (url.includes('/api/v1/contests/1/register')) return json({ registered: true })
        return json(contestDetail())
      },
    )
    vi.stubGlobal('fetch', fetchMock)

    renderPage()

    fireEvent.click(await screen.findByRole('button', { name: 'Register' }))

    await waitFor(() => {
      const postCall = fetchMock.mock.calls.find(
        ([input, init]) =>
          String(input).includes('/api/v1/contests/1/register') && init?.method === 'POST',
      )
      expect(postCall).toBeDefined()
    })
  })

  it('shows a countdown while running', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async (input: RequestInfo | URL) => {
        if (String(input).includes('/standings')) return json(STANDINGS)
        return json(contestDetail({ status: 'RUNNING' }))
      }),
    )

    renderPage()

    expect(await screen.findByText(/Ends in/)).toBeInTheDocument()
  })
})
