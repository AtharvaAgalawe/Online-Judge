import { describe, expect, it } from 'vitest'
import { render, screen } from '@testing-library/react'
import { VerdictBadge } from './VerdictBadge'

describe('VerdictBadge', () => {
  it('renders a human-readable label for a known verdict', () => {
    render(<VerdictBadge verdict="WRONG_ANSWER" />)
    expect(screen.getByText('Wrong Answer')).toBeInTheDocument()
  })

  it('renders a pending state when the verdict is null', () => {
    render(<VerdictBadge verdict={null} />)
    expect(screen.getByText('Pending')).toBeInTheDocument()
  })
})
