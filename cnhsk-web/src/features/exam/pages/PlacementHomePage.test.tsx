import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, useLocation } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import type { SessionView } from '@/features/practice'
import { ApiError } from '@/shared/api'

import { getPlacementSummary, startPlacementRun } from '../api/placement'
import { placementSummaryFixture } from '../test-fixtures'
import PlacementHomePage from './PlacementHomePage'

vi.mock('../api/placement', () => ({ getPlacementSummary: vi.fn(), startPlacementRun: vi.fn() }))

const session: SessionView = {
  sessionId: 9901,
  source: 'PLACEMENT',
  status: 'IN_PROGRESS',
  feedback: 'DEFERRED',
  items: [],
  answered: [],
  refId: null,
  servedAt: '2026-11-04T00:00:00Z',
  expiresAt: '2026-11-05T00:00:00Z',
  meta: {},
}

function Location() {
  return <output aria-label="Vị trí">{useLocation().pathname}</output>
}
function renderPage() {
  return render(
    <MemoryRouter initialEntries={['/learn/placement']}>
      <PlacementHomePage />
      <Location />
    </MemoryRouter>,
  )
}

describe('entrance test page, apart from the mock exams', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    vi.mocked(getPlacementSummary).mockResolvedValue({ active: null, last: null })
    vi.mocked(startPlacementRun).mockResolvedValue({ runId: 9901, session })
  })

  it('starts a run and opens it', async () => {
    const user = userEvent.setup()
    renderPage()
    expect(screen.getByRole('heading', { name: 'Kiểm tra đầu vào' })).toBeVisible()
    const card = await screen.findByRole('region', { name: 'Kiểm tra đầu vào' })
    expect(within(card).getByText(/30 câu trộn HSK 1–3/)).toBeVisible()
    await user.click(within(card).getByRole('button', { name: 'Bắt đầu' }))
    await waitFor(() =>
      expect(screen.getByLabelText('Vị trí')).toHaveTextContent('/learn/exams/sessions/9901'),
    )
    expect(startPlacementRun).toHaveBeenCalledOnce()
  })

  it('resumes a running test before showing the last result', async () => {
    vi.mocked(getPlacementSummary).mockResolvedValue({
      ...placementSummaryFixture,
      active: { runId: 8801, sessionId: 8802 },
    })
    renderPage()
    const card = await screen.findByRole('region', { name: 'Kiểm tra đầu vào' })
    expect(within(card).getByRole('link', { name: 'Tiếp tục' })).toHaveAttribute(
      'href',
      '/learn/exams/sessions/8802',
    )
    expect(within(card).queryByRole('button', { name: 'Làm lại' })).not.toBeInTheDocument()
  })

  it('shows the last result and lets the learner retake it', async () => {
    vi.mocked(getPlacementSummary).mockResolvedValue(placementSummaryFixture)
    const user = userEvent.setup()
    renderPage()
    const card = await screen.findByRole('region', { name: 'Kiểm tra đầu vào' })
    expect(within(card).getByText(/Kết quả gần nhất: HSK 2/)).toBeVisible()
    expect(within(card).getByRole('link', { name: 'Xem kết quả' })).toHaveAttribute(
      'href',
      '/learn/placement/3301',
    )
    await user.click(within(card).getByRole('button', { name: 'Làm lại' }))
    await waitFor(() => expect(startPlacementRun).toHaveBeenCalledOnce())
  })

  it('says the test is being prepared when no form is published yet', async () => {
    vi.mocked(startPlacementRun).mockRejectedValue(
      new ApiError({
        status: 422,
        errorCode: 'PLACEMENT_BANK_INSUFFICIENT',
        message: 'Bài kiểm tra trình độ đang được chuẩn bị.',
      }),
    )
    const user = userEvent.setup()
    renderPage()
    const card = await screen.findByRole('region', { name: 'Kiểm tra đầu vào' })
    await user.click(within(card).getByRole('button', { name: 'Bắt đầu' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('đang được chuẩn bị')
  })
})
