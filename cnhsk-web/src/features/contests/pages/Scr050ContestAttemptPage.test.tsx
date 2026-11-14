import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError } from '@/shared/api'
import * as api from '../api/contests'
import { contestFixture, contestSessionFixture } from '../test-fixtures'
import Scr050ContestAttemptPage from './Scr050ContestAttemptPage'

vi.mock('../api/contests', () => ({
  getContest: vi.fn(),
  enterContest: vi.fn(),
  getContestDraft: vi.fn(),
  saveContestAnswer: vi.fn(),
  submitContest: vi.fn(),
  reportContestTabHidden: vi.fn(),
}))
function mount() {
  return render(
    <MemoryRouter initialEntries={['/contests/2101/attempt']}>
      <Routes>
        <Route path="/contests/:id/attempt" element={<Scr050ContestAttemptPage />} />
        <Route path="/contests/:id/results" element={<h1>Đang tải trang kết quả</h1>} />
      </Routes>
    </MemoryRouter>,
  )
}
describe('SCR-050 FR016 / AT20', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    vi.mocked(api.getContest).mockResolvedValue(contestFixture)
    vi.mocked(api.enterContest).mockResolvedValue(contestSessionFixture)
    vi.mocked(api.getContestDraft).mockResolvedValue({
      sessionId: 2151,
      status: 'IN_PROGRESS',
      answered: [{ no: 1, answer: 'A' }],
    })
    vi.mocked(api.saveContestAnswer).mockResolvedValue({ no: 1, accepted: true })
    vi.mocked(api.submitContest).mockResolvedValue({ submittedAt: '2026-11-14T12:18:00Z' })
    vi.mocked(api.reportContestTabHidden).mockResolvedValue(undefined)
  })
  it('restores selection, sends a raw answer and navigates only after confirmed submission', async () => {
    const user = userEvent.setup()
    mount()
    const option = await screen.findByRole('button', { name: /A.*坐公共汽车/ })
    expect(option).toHaveAttribute('aria-pressed', 'true')
    expect(screen.getByRole('timer')).toBeVisible()
    await user.click(screen.getByRole('button', { name: /B.*骑自行车/ }))
    await waitFor(() =>
      expect(api.saveContestAnswer).toHaveBeenCalledWith(
        2151,
        expect.objectContaining({ no: 1, answer: 'B' }),
      ),
    )
    expect(screen.queryByText(/Đáp án đúng|Giải thích/)).not.toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Nộp bài' }))
    expect(api.submitContest).not.toHaveBeenCalled()
    await user.click(screen.getByRole('button', { name: 'Xác nhận nộp bài' }))
    expect(await screen.findByRole('heading', { name: 'Đang tải trang kết quả' })).toBeVisible()
    expect(api.submitContest).toHaveBeenCalledOnce()
  })
  it('keeps a failed submission visible as unsubmitted and permits an explicit retry', async () => {
    vi.mocked(api.submitContest).mockRejectedValueOnce(
      new ApiError({ status: 0, errorCode: 'NETWORK_ERROR', message: 'Mất kết nối.' }),
    )
    const user = userEvent.setup()
    mount()
    await user.click(await screen.findByRole('button', { name: 'Nộp bài' }))
    await user.click(screen.getByRole('button', { name: 'Xác nhận nộp bài' }))
    expect(await screen.findByText('Bài thi chưa nộp')).toBeVisible()
    expect(
      screen.queryByRole('heading', { name: 'Đang tải trang kết quả' }),
    ).not.toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Thử nộp lại' }))
    expect(await screen.findByRole('heading', { name: 'Đang tải trang kết quả' })).toBeVisible()
  })
  it('keeps only the latest choice selected when an older save fails and retries the failed choice', async () => {
    let failFirst!: (error: unknown) => void
    let failSecond!: (error: unknown) => void
    vi.mocked(api.saveContestAnswer)
      .mockReturnValueOnce(
        new Promise((_resolve, reject) => {
          failFirst = reject
        }),
      )
      .mockReturnValueOnce(
        new Promise((_resolve, reject) => {
          failSecond = reject
        }),
      )
    const user = userEvent.setup()
    mount()
    await user.click(await screen.findByRole('button', { name: /B.*骑自行车/ }))
    await user.click(screen.getByRole('button', { name: /C.*走路/ }))
    failFirst(new Error('Temporary save failure'))
    await waitFor(() => expect(api.saveContestAnswer).toHaveBeenCalledTimes(2))
    failSecond(new Error('Temporary save failure'))
    expect(await screen.findByText('Chưa lưu được đáp án')).toBeVisible()
    expect(screen.getByRole('button', { name: /A.*坐公共汽车/ })).toHaveAttribute(
      'aria-pressed',
      'false',
    )
    expect(screen.getByRole('button', { name: /B.*骑自行车/ })).toHaveAttribute(
      'aria-pressed',
      'false',
    )
    expect(screen.getByRole('button', { name: /C.*走路/ })).toHaveAttribute('aria-pressed', 'true')
    await user.click(screen.getByRole('button', { name: 'Lưu lại đáp án' }))
    expect(await screen.findByText('Đã lưu đáp án')).toBeVisible()
    expect(api.saveContestAnswer).toHaveBeenLastCalledWith(
      2151,
      expect.objectContaining({ no: 1, answer: 'C' }),
    )
  })
  it('warns before leaving and keeps the attempt instead of abandoning it', async () => {
    const user = userEvent.setup()
    mount()
    await user.click(await screen.findByRole('button', { name: 'Thoát' }))
    expect(screen.getByRole('region', { name: 'Xác nhận rời cuộc thi' })).toHaveTextContent(
      'Đồng hồ vẫn tiếp tục',
    )
    expect(screen.getByRole('link', { name: 'Về thông tin cuộc thi' })).toHaveAttribute(
      'href',
      '/contests/2101',
    )
    expect(api.submitContest).not.toHaveBeenCalled()
  })
})
