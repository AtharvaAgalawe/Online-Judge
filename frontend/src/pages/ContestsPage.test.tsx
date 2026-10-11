import { afterEach, describe, expect, it, vi } from 'vitest'
import { fireEvent, screen, waitFor, within } from '@testing-library/react'
import { ContestsPage } from './ContestsPage'
import { renderWithProviders } from '../test/renderWithProviders'

const RUNNING_CONTEST = {
  id: 7,
  name: 'Spring Cup',
  startTime: '2026-10-11T09:00:00Z',
  endTime: '2026-10-11T12:00:00Z',
  status: 'RUNNING',
  participantCount: 12,
  problemCount: 4,
}

function jsonResponse(body: unknown) {
  return new Response(JSON.stringify(body), {
    status: 200,
    headers: { 'Content-Type': 'application/json' },
  })
}

function contestsPage(content: unknown[]) {
  return {
    content,
    page: 0,
    size: 20,
    totalElements: content.length,
    totalPages: 1,
  }
}

describe('ContestsPage', () => {
  afterEach(() => vi.restoreAllMocks())

  it('renders a running contest with the Running chip', async () => {
    const fetchMock = vi.fn<(input: RequestInfo | URL) => Promise<Response>>(async () =>
      jsonResponse(contestsPage([RUNNING_CONTEST])),
    )
    vi.stubGlobal('fetch', fetchMock)

    renderWithProviders(<ContestsPage />)

    const nameCell = await screen.findByText('Spring Cup')
    const row = nameCell.closest('tr')
    expect(row).not.toBeNull()
    expect(within(row as HTMLElement).getByText('Running')).toBeInTheDocument()
  })

  it('requests ended contests when the Past tab is clicked', async () => {
    const fetchMock = vi.fn<(input: RequestInfo | URL) => Promise<Response>>(async () =>
      jsonResponse(contestsPage([RUNNING_CONTEST])),
    )
    vi.stubGlobal('fetch', fetchMock)

    renderWithProviders(<ContestsPage />)
    await screen.findByText('Spring Cup')

    fireEvent.click(screen.getByRole('button', { name: 'Past' }))

    await waitFor(() => {
      const requestedEnded = fetchMock.mock.calls.some((call) =>
        String(call[0]).includes('status=ENDED'),
      )
      expect(requestedEnded).toBe(true)
    })
  })

  it('shows an empty message when the page has no contests', async () => {
    const fetchMock = vi.fn<(input: RequestInfo | URL) => Promise<Response>>(async () =>
      jsonResponse(contestsPage([])),
    )
    vi.stubGlobal('fetch', fetchMock)

    renderWithProviders(<ContestsPage />)

    expect(await screen.findByText('No contests found.')).toBeInTheDocument()
  })
})
