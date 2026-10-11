import { afterEach, describe, expect, it, vi } from 'vitest'
import { fireEvent, screen, waitFor } from '@testing-library/react'
import { Route, Routes } from 'react-router-dom'
import { AuthProvider } from '../../auth/AuthContext'
import { AdminContestEditPage } from './AdminContestEditPage'
import { renderWithProviders } from '../../test/renderWithProviders'

const DETAIL = {
  id: 1, name: 'Spring Cup', description: 'Annual contest',
  startTime: '2026-03-01T10:00:00Z', endTime: '2026-03-01T12:00:00Z',
  isPrivate: false, freezeMinutes: 60, status: 'UPCOMING', problemsPublishedAt: null,
  problems: [],
}

const ATTACHED = [
  { problemId: 7, slug: 'two-sum', title: 'Two Sum', difficulty: 'EASY', displayOrder: 0, points: 100, solvedByMe: false },
  { problemId: 8, slug: 'three-sum', title: 'Three Sum', difficulty: 'MEDIUM', displayOrder: 1, points: 250, solvedByMe: false },
]

function json(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status, headers: { 'Content-Type': 'application/json' },
  })
}

function renderPage() {
  renderWithProviders(
    <AuthProvider>
      <Routes>
        <Route path="/admin/contests/:id" element={<AdminContestEditPage />} />
        <Route path="/admin/contests" element={<div>admin contest list</div>} />
      </Routes>
    </AuthProvider>,
    { route: '/admin/contests/1' },
  )
}

describe('AdminContestEditPage', () => {
  afterEach(() => {
    localStorage.clear()
    vi.restoreAllMocks()
  })

  it('loads and submits metadata', async () => {
    const fetchMock = vi.fn(async (_input: RequestInfo | URL, init?: RequestInit) => {
      if (init?.method === 'PUT') {
        return new Response(null, { status: 200 })
      }
      return json(DETAIL)
    })
    vi.stubGlobal('fetch', fetchMock)
    renderPage()

    const name = await screen.findByLabelText('Name')
    expect(name).toHaveValue('Spring Cup')

    fireEvent.change(name, { target: { value: 'Spring Cup 2' } })
    fireEvent.click(screen.getByRole('button', { name: /save metadata/i }))

    await waitFor(() => {
      const put = fetchMock.mock.calls.find((call) => call[1]?.method === 'PUT')
      expect(put).toBeTruthy()
      expect(JSON.parse(String(put![1]!.body))).toMatchObject({ name: 'Spring Cup 2' })
    })
    await waitFor(() => expect(screen.queryByRole('alert')).not.toBeInTheDocument())
  })

  it('disables the form once the contest has started', async () => {
    vi.stubGlobal('fetch', vi.fn(async () =>
      json({ ...DETAIL, status: 'RUNNING' })))
    renderPage()

    expect(await screen.findByText('This contest has started and can no longer be edited.')).toBeInTheDocument()
    expect(screen.getByLabelText('Name')).toBeDisabled()
    expect(screen.getByRole('button', { name: /save metadata/i })).toBeDisabled()
  })

  it('lists attached problems with points and order', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => json({ ...DETAIL, problems: ATTACHED })))
    renderPage()

    expect(await screen.findByRole('link', { name: 'Two Sum' }))
      .toHaveAttribute('href', '/admin/problems/7')
    expect(screen.getByRole('link', { name: 'Three Sum' }))
      .toHaveAttribute('href', '/admin/problems/8')
    expect(screen.getByLabelText('Points for Two Sum')).toHaveValue(100)
    expect(screen.getByLabelText('Points for Three Sum')).toHaveValue(250)
    expect(screen.getByLabelText('Order for Two Sum')).toHaveValue(0)
    expect(screen.getByLabelText('Order for Three Sum')).toHaveValue(1)
  })

  it('attaches a problem from the picker', async () => {
    const fetchMock = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const url = String(input)
      if (url.includes('attachable-problems')) {
        return json([{ id: 7, slug: 'two-sum', title: 'Two Sum', difficulty: 'EASY' }])
      }
      if (init?.method === 'POST') return new Response(null, { status: 201 })
      return json(DETAIL)
    })
    vi.stubGlobal('fetch', fetchMock)
    renderPage()

    await screen.findByText('No problems attached yet.')
    fireEvent.click(screen.getByRole('button', { name: 'Attach problem' }))
    fireEvent.click(await screen.findByRole('button', { name: 'Attach' }))

    await waitFor(() => {
      const post = fetchMock.mock.calls.find((call) =>
        String(call[0]).includes('/admin/contests/1/problems') && call[1]?.method === 'POST')
      expect(post).toBeTruthy()
      expect(JSON.parse(String(post![1]!.body))).toEqual({ problemId: 7 })
    })
    await waitFor(() => expect(screen.queryByRole('alert')).not.toBeInTheDocument())
  })

  it('inline create links to the test case manager', async () => {
    const fetchMock = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      if (init?.method === 'POST' && String(input).includes('/problems/new')) {
        return json({ problemId: 42, slug: 'fresh-problem' }, 201)
      }
      return json(DETAIL)
    })
    vi.stubGlobal('fetch', fetchMock)
    renderPage()

    fireEvent.change(await screen.findByLabelText('Title'), { target: { value: 'Fresh Problem' } })
    fireEvent.change(screen.getByLabelText('Statement'), { target: { value: 'Add two numbers.' } })
    fireEvent.change(screen.getByLabelText('Points'), { target: { value: '150' } })
    fireEvent.click(screen.getByRole('button', { name: 'Create problem' }))

    await waitFor(() => {
      const post = fetchMock.mock.calls.find((call) =>
        String(call[0]).includes('/admin/contests/1/problems/new'))
      expect(post).toBeTruthy()
      expect(JSON.parse(String(post![1]!.body))).toMatchObject({ title: 'Fresh Problem', points: 150 })
    })
    expect(await screen.findByRole('link', { name: 'Manage test cases' }))
      .toHaveAttribute('href', '/admin/problems/42')
  })

  it('deletes the contest after confirmation', async () => {
    vi.spyOn(window, 'confirm').mockReturnValue(true)
    const fetchMock = vi.fn(async (_input: RequestInfo | URL, init?: RequestInit) => {
      if (init?.method === 'DELETE') return new Response(null, { status: 204 })
      return json(DETAIL)
    })
    vi.stubGlobal('fetch', fetchMock)
    renderPage()

    fireEvent.click(await screen.findByRole('button', { name: 'Delete contest' }))

    await waitFor(() => {
      const del = fetchMock.mock.calls.find((call) => call[1]?.method === 'DELETE')
      expect(del).toBeTruthy()
      expect(String(del![0])).toBe('/api/v1/admin/contests/1')
    })
    expect(window.confirm).toHaveBeenCalled()
    expect(await screen.findByText('admin contest list')).toBeInTheDocument()
  })

  it('disables problem editing once the contest has started', async () => {
    vi.stubGlobal('fetch', vi.fn(async () =>
      json({ ...DETAIL, status: 'RUNNING', problems: ATTACHED })))
    renderPage()

    await screen.findByRole('link', { name: 'Two Sum' })
    expect(screen.getByRole('button', { name: 'Attach problem' })).toBeDisabled()
    expect(screen.queryByRole('button', { name: 'Detach' })).not.toBeInTheDocument()
    expect(screen.queryByLabelText('Points for Two Sum')).not.toBeInTheDocument()
  })
})
