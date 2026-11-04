import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError } from '@/shared/api'
import { getPlacementResult, startPlacementRun } from '../api/placement'
import { placementAttemptFixture, placementReviewFixture } from '../test-fixtures'
import Scr022ResultRoute from './Scr022ResultRoute'

vi.mock('../api/placement', () => ({ getPlacementResult: vi.fn(), startPlacementRun: vi.fn() }))

function Location() {
  return <output aria-label="Vị trí">{useLocation().pathname}</output>
}
function open(id = '5201') {
  return render(
    <MemoryRouter initialEntries={['/learn/placement/' + id]}>
      <Routes>
        <Route path="/learn/placement/:runId" element={<Scr022ResultRoute />} />
        <Route path="/learn/exams/sessions/:sessionId" element={<p>Lượt làm bài</p>} />
      </Routes>
      <Location />
    </MemoryRouter>,
  )
}

describe('SCR-022 placement result FR-026 / AT-27', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    vi.mocked(getPlacementResult).mockResolvedValue(placementReviewFixture)
  })
  it('shows estimated level, per-level scores, all domains and tentative labels', async () => {
    vi.mocked(getPlacementResult).mockResolvedValue({
      ...placementReviewFixture,
      domains: placementReviewFixture.domains.map((domain) =>
        domain.domain === 'NA' ? { ...domain, percent: 99 } : domain,
      ),
    })
    open()
    expect(await screen.findByText('Cấp ước lượng: HSK 2')).toBeVisible()
    expect(screen.getByText('Kết quả tạm')).toBeVisible()
    expect(
      within(screen.getByRole('table', { name: 'Kết quả theo cấp' })).getByText('40%'),
    ).toBeVisible()
    const profile = screen.getByRole('region', { name: 'Hồ sơ sáu miền' })
    expect(within(profile).getAllByRole('listitem')).toHaveLength(6)
    expect(within(profile).getAllByText('Chưa đo')).toHaveLength(4)
    expect(within(profile).queryByText(/99%/)).not.toBeInTheDocument()
    expect(within(profile).getByText('· tạm')).toBeVisible()
    expect(screen.getByRole('link', { name: 'Luyện nút này' })).toHaveAttribute(
      'href',
      '/learn/practice/targeted?kid=NP1-101',
    )
    expect(screen.queryByText(/đỗ|trượt/i)).not.toBeInTheDocument()
    expect(getPlacementResult).toHaveBeenCalledWith(5201, expect.any(AbortSignal))
  })
  it('shows sure confidence and opens the returned session on retake', async () => {
    vi.mocked(getPlacementResult).mockResolvedValue({
      ...placementReviewFixture,
      confidence: 'SURE',
    })
    vi.mocked(startPlacementRun).mockResolvedValue({
      runId: 5301,
      session: { ...placementAttemptFixture, sessionId: 5302 },
    })
    const user = userEvent.setup()
    open()
    expect(await screen.findByText('Chắc chắn')).toBeVisible()
    await user.click(screen.getByRole('button', { name: 'Làm lại' }))
    expect(await screen.findByText('Lượt làm bài')).toBeVisible()
    expect(screen.getByLabelText('Vị trí')).toHaveTextContent('/learn/exams/sessions/5302')
    expect(startPlacementRun).toHaveBeenCalledOnce()
  })
  it('shows bank-insufficient error on the result page', async () => {
    vi.mocked(startPlacementRun).mockRejectedValue(
      new ApiError({
        status: 422,
        errorCode: 'PLACEMENT_BANK_INSUFFICIENT',
        message: 'Ngân hàng câu hỏi chưa đủ.',
      }),
    )
    const user = userEvent.setup()
    open()
    await user.click(await screen.findByRole('button', { name: 'Làm lại' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('Ngân hàng câu hỏi chưa đủ.')
    expect(screen.getByLabelText('Vị trí')).toHaveTextContent('/learn/placement/5201')
    await waitFor(() => expect(screen.getByRole('button', { name: 'Làm lại' })).toBeEnabled())
  })
  it('rejects invalid runId without loading', () => {
    open('0')
    expect(screen.getByRole('alert')).toHaveTextContent('Không tìm thấy kết quả')
    expect(getPlacementResult).not.toHaveBeenCalled()
  })
})
