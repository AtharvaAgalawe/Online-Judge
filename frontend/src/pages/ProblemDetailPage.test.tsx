import { afterEach, describe, expect, it, vi } from 'vitest'
import { fireEvent, screen, waitFor } from '@testing-library/react'
import { Route, Routes } from 'react-router-dom'
import { ProblemDetailPage } from './ProblemDetailPage'
import { AuthProvider } from '../auth/AuthContext'
import { renderWithProviders } from '../test/renderWithProviders'

vi.mock('../components/CodeEditor', async () => {
  const React = await import('react')
  return {
    BOILERPLATE: { java: 'java boilerplate', python: 'python boilerplate' },
    CodeEditor: (props: { value: string; onChange: (value: string) => void }) =>
      React.createElement('textarea', {
        'aria-label': 'Source code',
        value: props.value,
        onChange: (event: { target: { value: string } }) => props.onChange(event.target.value),
      }),
  }
})

const DETAIL = {
  id: 1,
  slug: 'two-sum',
  title: 'Two Sum',
  statement: 'Add two numbers.',
  difficulty: 'EASY',
  timeLimitMs: 2000,
  memoryLimitKb: 262144,
  tags: ['array'],
  sampleTestCases: [{ input: '1 2', expectedOutput: '3', order: 1 }],
}

function json(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}

function encodeSegment(value: object): string {
  return btoa(JSON.stringify(value)).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '')
}

const ACCESS = `h.${encodeSegment({ sub: 'alice', uid: 1, roles: ['ROLE_USER'] })}.s`

describe('ProblemDetailPage', () => {
  afterEach(() => vi.restoreAllMocks())

  it('renders the statement and sample cases', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async (input: RequestInfo | URL) => {
        const url = String(input)
        const body = url.includes('/stats')
          ? { totalSubmissions: 10, acceptedCount: 5, acceptanceRate: 50 }
          : DETAIL
        return new Response(JSON.stringify(body), {
          status: 200,
          headers: { 'Content-Type': 'application/json' },
        })
      }),
    )

    renderWithProviders(
      <AuthProvider>
        <Routes>
          <Route path="/problems/:slug" element={<ProblemDetailPage />} />
        </Routes>
      </AuthProvider>,
      { route: '/problems/two-sum' },
    )

    expect(await screen.findByRole('heading', { name: 'Two Sum' })).toBeInTheDocument()
    expect(screen.getByText('Add two numbers.')).toBeInTheDocument()
    expect(screen.getByText('1 2')).toBeInTheDocument()
    expect(screen.getByText('50.0%')).toBeInTheDocument()
  })

  it('submits with an Idempotency-Key header and navigates to the result page', async () => {
    localStorage.setItem('oj.refreshToken', 'refresh-1')
    vi.stubGlobal(
      'fetch',
      vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
        const url = String(input)
        const method = init?.method ?? 'GET'
        if (url.includes('/auth/refresh')) return json({ accessToken: ACCESS, expiresIn: 900 })
        if (url.includes('/stats'))
          return json({ totalSubmissions: 10, acceptedCount: 5, acceptanceRate: 50 })
        if (url.includes('/languages')) return json([{ id: 1, name: 'Python 3.12' }])
        if (url.includes('/api/v1/submissions') && method === 'POST')
          return json({ submissionId: 7, status: 'SUBMITTED' }, 202)
        return json(DETAIL)
      }),
    )

    renderWithProviders(
      <AuthProvider>
        <Routes>
          <Route path="/problems/:slug" element={<ProblemDetailPage />} />
          <Route path="/submissions/:id" element={<div>result page</div>} />
        </Routes>
      </AuthProvider>,
      { route: '/problems/two-sum' },
    )

    const languageSelect = await screen.findByLabelText('Language')
    fireEvent.change(languageSelect, { target: { value: '1' } })
    const submitButton = screen.getByRole('button', { name: 'Submit' })
    await waitFor(() => expect(submitButton).toBeEnabled())
    fireEvent.click(submitButton)

    await waitFor(() => expect(screen.getByText('result page')).toBeInTheDocument())

    const mockFetch = vi.mocked(fetch)
    const postCall = mockFetch.mock.calls.find(
      ([input, init]) =>
        String(input).includes('/api/v1/submissions') && init?.method === 'POST',
    )
    expect(postCall).toBeDefined()
    const headers = postCall![1]!.headers as Headers
    expect(headers.get('Idempotency-Key')).toMatch(
      /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i,
    )
  })
})
