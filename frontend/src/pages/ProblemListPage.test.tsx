import { afterEach, describe, expect, it, vi } from 'vitest'
import { fireEvent, screen, waitFor } from '@testing-library/react'
import { ProblemListPage } from './ProblemListPage'
import { renderWithProviders } from '../test/renderWithProviders'

const PAGE = {
  content: [
    {
      id: 1,
      slug: 'two-sum',
      title: 'Two Sum',
      difficulty: 'EASY',
      tags: ['array'],
      acceptanceRate: 72.5,
    },
  ],
  page: 0,
  size: 20,
  totalElements: 1,
  totalPages: 1,
}

describe('ProblemListPage', () => {
  afterEach(() => vi.restoreAllMocks())

  it('renders problems and includes the difficulty filter in the request', async () => {
    const fetchMock = vi.fn<(input: RequestInfo | URL) => Promise<Response>>(async () =>
      new Response(JSON.stringify(PAGE), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      }),
    )
    vi.stubGlobal('fetch', fetchMock)

    renderWithProviders(<ProblemListPage />)
    expect(await screen.findByText('Two Sum')).toBeInTheDocument()

    fireEvent.change(screen.getByLabelText('Difficulty'), { target: { value: 'EASY' } })
    await waitFor(() => {
      const calledWithEasy = fetchMock.mock.calls.some((call) =>
        String(call[0]).includes('difficulty=EASY'),
      )
      expect(calledWithEasy).toBe(true)
    })
  })
})
