import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, useLocation } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { getExams, startExamAttempt } from '../api/exams'
import { getPlacementSummary, startPlacementRun } from '../api/placement'
import { examListFixture, placementSummaryFixture } from '../test-fixtures'
import type { SessionView } from '@/features/practice'
import Scr020ExamListPage from './Scr020ExamListPage'

vi.mock('../api/exams', () => ({ getExams: vi.fn(), startExamAttempt: vi.fn() }))
vi.mock('../api/placement', () => ({ getPlacementSummary: vi.fn(), startPlacementRun: vi.fn() }))

const session: SessionView = {
  sessionId: 9901,
  source: 'MOCK_EXAM',
  status: 'IN_PROGRESS',
  feedback: 'DEFERRED',
  items: [],
  answered: [],
  refId: 1201,
  servedAt: '2026-11-04T00:00:00Z',
  expiresAt: '2026-11-05T00:00:00Z',
  meta: {},
}

function Location() {
  return <output aria-label="Vị trí">{useLocation().pathname}</output>
}
function renderPage() {
  return render(
    <MemoryRouter initialEntries={['/learn/exams']}>
      <Scr020ExamListPage />
      <Location />
    </MemoryRouter>,
  )
}

describe('SCR-020 exam list FR-024 / AT-25', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    vi.mocked(getExams).mockResolvedValue(examListFixture)
    vi.mocked(getPlacementSummary).mockResolvedValue({ active: null, last: null })
    vi.mocked(startExamAttempt).mockResolvedValue(session)
    vi.mocked(startPlacementRun).mockResolvedValue({
      runId: 9901,
      session: { ...session, source: 'PLACEMENT' },
    })
  })

  it('FR024_AT25_shows cards, zero versus null score and no prices', async () => {
    renderPage()
    const card = await screen.findByRole('article', { name: examListFixture[0]!.title })
    expect(within(card).getByText('10')).toBeVisible()
    expect(within(card).getByText('Số phần')).toBeVisible()
    expect(within(card).getByText('1')).toBeVisible()
    expect(within(card).getByText('2')).toBeVisible()
    expect(within(card).getByText('0%')).toBeVisible()
    const other = screen.getByRole('article', { name: examListFixture[1]!.title })
    expect(within(other).getByText('Chưa có điểm')).toBeVisible()
    expect(within(other).getByRole('link', { name: 'Tiếp tục' })).toHaveAttribute(
      'href',
      '/learn/exams/sessions/2202',
    )
    expect(screen.queryByText(/giá|miễn phí|VNĐ|₫/i)).not.toBeInTheDocument()
    expect(getExams).toHaveBeenCalledWith(1, expect.any(AbortSignal))
  })

  it('FR024_AT25_new placement starts a run and navigates', async () => {
    const user = userEvent.setup()
    renderPage()
    const placement = await screen.findByRole('region', { name: 'Kiểm tra trình độ' })
    await user.click(within(placement).getByRole('button', { name: 'Bắt đầu' }))
    await waitFor(() =>
      expect(screen.getByLabelText('Vị trí')).toHaveTextContent('/learn/exams/sessions/9901'),
    )
    expect(startPlacementRun).toHaveBeenCalledOnce()
  })

  it('FR024_AT25_active placement takes priority over last result', async () => {
    vi.mocked(getPlacementSummary).mockResolvedValue({
      ...placementSummaryFixture,
      active: { runId: 8801, sessionId: 8802 },
    })
    renderPage()
    const placement = await screen.findByRole('region', { name: 'Kiểm tra trình độ' })
    expect(within(placement).getByRole('link', { name: 'Tiếp tục' })).toHaveAttribute(
      'href',
      '/learn/exams/sessions/8802',
    )
    expect(within(placement).queryByText(/Kết quả gần nhất/)).not.toBeInTheDocument()
    expect(within(placement).queryByRole('button', { name: 'Làm lại' })).not.toBeInTheDocument()
  })

  it('FR024_AT25_last result can be viewed and retaken', async () => {
    vi.mocked(getPlacementSummary).mockResolvedValue(placementSummaryFixture)
    const user = userEvent.setup()
    renderPage()
    const placement = await screen.findByRole('region', { name: 'Kiểm tra trình độ' })
    expect(within(placement).getByText(/Kết quả gần nhất: HSK 2/)).toBeVisible()
    expect(within(placement).getByRole('link', { name: 'Xem kết quả' })).toHaveAttribute(
      'href',
      '/learn/placement/3301',
    )
    await user.click(within(placement).getByRole('button', { name: 'Làm lại' }))
    await waitFor(() => expect(startPlacementRun).toHaveBeenCalledOnce())
  })

  it('FR024_AT25_only levels one to three, loads the selected level and empty text', async () => {
    vi.mocked(getExams).mockImplementation(async (level) => (level === 1 ? examListFixture : []))
    const user = userEvent.setup()
    renderPage()
    const levels = await screen.findByRole('group', { name: 'Chọn cấp HSK' })
    expect(
      within(levels)
        .getAllByRole('button')
        .map((button) => button.textContent),
    ).toEqual(['HSK 1', 'HSK 2', 'HSK 3'])
    await user.click(within(levels).getByRole('button', { name: 'HSK 3' }))
    expect(await screen.findByText('Đề HSK 3 đang được cập nhật')).toBeVisible()
    expect(getExams).toHaveBeenLastCalledWith(3, expect.any(AbortSignal))
    expect(screen.queryByText(examListFixture[0]!.title)).not.toBeInTheDocument()
    expect(screen.getByRole('region', { name: 'Kiểm tra trình độ' })).toBeVisible()
  })

  it('FR024_AT25_starting an exam navigates to returned session', async () => {
    const user = userEvent.setup()
    renderPage()
    const card = await screen.findByRole('article', { name: examListFixture[0]!.title })
    await user.click(within(card).getByRole('button', { name: 'Bắt đầu' }))
    await waitFor(() =>
      expect(screen.getByLabelText('Vị trí')).toHaveTextContent('/learn/exams/sessions/9901'),
    )
    expect(startExamAttempt).toHaveBeenCalledWith(1201)
  })

  it('FR024_AT25_loading has no premature empty state', () => {
    vi.mocked(getExams).mockReturnValue(new Promise(() => undefined))
    renderPage()
    expect(screen.getByRole('status', { name: 'Đang tải đề thi' })).toBeVisible()
    expect(screen.queryByText(/đang được cập nhật/)).not.toBeInTheDocument()
  })

  it('FR024_AT25_query failure supports retry', async () => {
    vi.mocked(getExams).mockRejectedValueOnce(new Error('Không thể tải đề thi.'))
    const user = userEvent.setup()
    renderPage()
    expect(await screen.findByRole('alert')).toBeVisible()
    await user.click(screen.getByRole('button', { name: 'Thử lại' }))
    expect(await screen.findByText(examListFixture[0]!.title)).toBeVisible()
  })

  it('FR024_AT25_failed start displays error and allows retry', async () => {
    vi.mocked(startExamAttempt).mockRejectedValueOnce(new Error('Không thể bắt đầu.'))
    const user = userEvent.setup()
    renderPage()
    const card = await screen.findByRole('article', { name: examListFixture[0]!.title })
    await user.click(within(card).getByRole('button', { name: 'Bắt đầu' }))
    expect(await screen.findByRole('alert')).toBeVisible()
    expect(screen.getByLabelText('Vị trí')).toHaveTextContent('/learn/exams')
    await user.click(within(card).getByRole('button', { name: 'Bắt đầu' }))
    await waitFor(() =>
      expect(screen.getByLabelText('Vị trí')).toHaveTextContent('/learn/exams/sessions/9901'),
    )
  })
})
