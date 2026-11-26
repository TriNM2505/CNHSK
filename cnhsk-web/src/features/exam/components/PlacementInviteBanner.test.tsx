import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { getPlacementSummary } from '../api/placement'
import { PlacementInviteBanner } from './PlacementInviteBanner'

vi.mock('../api/placement', () => ({ getPlacementSummary: vi.fn() }))

function renderBanner() {
  return render(
    <MemoryRouter>
      <PlacementInviteBanner />
    </MemoryRouter>,
  )
}

beforeEach(() => {
  window.localStorage.clear()
  vi.mocked(getPlacementSummary).mockReset().mockResolvedValue({ active: null, last: null })
})

describe('placement invitation', () => {
  it('invites a learner who never took the test and links to it', async () => {
    renderBanner()
    expect(await screen.findByText('Chưa biết mình ở trình độ nào?')).toBeVisible()
    expect(screen.getByRole('link', { name: 'Làm bài kiểm tra' })).toHaveAttribute(
      'href',
      '/learn/placement',
    )
  })

  it('stays hidden after a finished or running test', async () => {
    vi.mocked(getPlacementSummary).mockResolvedValue({
      active: { runId: 3, sessionId: 3 },
      last: null,
    })
    renderBanner()
    await waitFor(() => expect(getPlacementSummary).toHaveBeenCalled())
    expect(screen.queryByText('Chưa biết mình ở trình độ nào?')).not.toBeInTheDocument()
  })

  it('remembers "later" so the invitation does not come back', async () => {
    const first = renderBanner()
    fireEvent.click(await screen.findByRole('button', { name: 'Để sau' }))
    expect(screen.queryByText('Chưa biết mình ở trình độ nào?')).not.toBeInTheDocument()
    first.unmount()
    renderBanner()
    await waitFor(() => expect(getPlacementSummary).toHaveBeenCalledTimes(2))
    expect(screen.queryByText('Chưa biết mình ở trình độ nào?')).not.toBeInTheDocument()
  })
})
