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
    expect(screen.getByText('…')).toHaveAttribute('aria-hidden', 'true')
  })

  it('prefixes the verdict with a symbol hidden from assistive tech', () => {
    render(<VerdictBadge verdict="ACCEPTED" />)
    const icon = screen.getByText('✓')
    expect(icon).toHaveAttribute('aria-hidden', 'true')
    expect(screen.getByText('Accepted')).toBeInTheDocument()
    render(<VerdictBadge verdict="WRONG_ANSWER" />)
    expect(screen.getByText('✗')).toHaveAttribute('aria-hidden', 'true')
    expect(screen.getByText('Wrong Answer')).toBeInTheDocument()
  })
})
