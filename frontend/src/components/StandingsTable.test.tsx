import { describe, expect, it } from 'vitest'
import { render, screen } from '@testing-library/react'
import type { StandingsProblemCell, StandingsResponse, StandingsRow } from '../api/contests'
import { StandingsTable } from './StandingsTable'

function cell(problemId: number, overrides: Partial<StandingsProblemCell> = {}): StandingsProblemCell {
  return { problemId, solved: false, wrongAttempts: 0, acceptedAtMinute: null, ...overrides }
}

function row(overrides: Partial<StandingsRow> = {}): StandingsRow {
  return { rank: 1, username: 'alice', totalPoints: 0, penalty: 0, problems: [], ...overrides }
}

function standings(overrides: Partial<StandingsResponse> = {}): StandingsResponse {
  return { frozen: false, frozenAt: null, rows: [], ...overrides }
}

describe('StandingsTable', () => {
  it('renders rank, user, points, and penalty', () => {
    const rows = [
      row({
        rank: 1,
        username: 'alice',
        totalPoints: 250,
        penalty: 40,
        problems: [
          cell(1, { solved: true, wrongAttempts: 0, acceptedAtMinute: 12 }),
          cell(2, { solved: true, wrongAttempts: 2, acceptedAtMinute: 30 }),
        ],
      }),
      row({
        rank: 2,
        username: 'bob',
        totalPoints: 100,
        penalty: 75,
        problems: [cell(1, { solved: true, wrongAttempts: 0, acceptedAtMinute: 20 })],
      }),
    ]

    render(<StandingsTable standings={standings({ rows })} />)

    expect(screen.getByText('1')).toBeInTheDocument()
    expect(screen.getByText('alice')).toBeInTheDocument()
    expect(screen.getByText('bob')).toBeInTheDocument()
    expect(screen.getByText('250')).toBeInTheDocument()
    expect(screen.getByText('40')).toBeInTheDocument()
    expect(screen.getByText('100')).toBeInTheDocument()
    expect(screen.getByText('75')).toBeInTheDocument()
  })

  it('formats solved and unsolved cells', () => {
    const rows = [
      row({
        problems: [
          cell(1, { solved: true, wrongAttempts: 2, acceptedAtMinute: 12 }),
          cell(2, { solved: false, wrongAttempts: 1 }),
          cell(3, { solved: false, wrongAttempts: 0 }),
        ],
      }),
    ]

    render(<StandingsTable standings={standings({ rows })} />)

    expect(screen.getByText('+2')).toBeInTheDocument()
    expect(screen.getByText('−1')).toBeInTheDocument()
    expect(screen.getByText('·')).toBeInTheDocument()
  })

  it('shows a frozen badge when standings are frozen', () => {
    render(<StandingsTable standings={standings({ frozen: true, rows: [row()] })} />)

    expect(screen.getByText('Standings frozen')).toBeInTheDocument()
  })
})
