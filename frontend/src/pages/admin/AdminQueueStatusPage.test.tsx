import { afterEach, describe, expect, it, vi } from 'vitest'
import { screen } from '@testing-library/react'
import { Route, Routes } from 'react-router-dom'
import { AuthProvider } from '../../auth/AuthContext'
import { AdminQueueStatusPage } from './AdminQueueStatusPage'
import { renderWithProviders } from '../../test/renderWithProviders'

describe('AdminQueueStatusPage', () => {
  afterEach(() => {
    localStorage.clear()
    vi.restoreAllMocks()
  })

  it('renders counters and stale jobs', async () => {
    vi.stubGlobal('fetch', vi.fn(async () =>
      new Response(JSON.stringify({
        queued: 3, activeLease: 1, staleLease: 1, retried: 2, oldestQueuedAgeSeconds: 12,
        stale: [{ submissionId: 77, status: 'RUNNING', lockedBy: 'w-1', leaseExpiresAt: '2026-01-01T00:00:00Z', retryCount: 1, ageSeconds: 45 }],
      }), { status: 200, headers: { 'Content-Type': 'application/json' } })))
    renderWithProviders(
      <AuthProvider>
        <Routes><Route path="/admin/queue" element={<AdminQueueStatusPage />} /></Routes>
      </AuthProvider>,
      { route: '/admin/queue' },
    )
    expect(await screen.findByText(/Queued: 3/)).toBeInTheDocument()
    expect(screen.getByText('77')).toBeInTheDocument()
  })
})
