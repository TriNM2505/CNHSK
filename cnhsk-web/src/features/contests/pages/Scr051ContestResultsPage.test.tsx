import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { getContestResults } from '../api/contests'
import { publishedFixture, unpublishedFixture } from '../test-fixtures'
import Scr051ContestResultsPage from './Scr051ContestResultsPage'

vi.mock('../api/contests', () => ({ getContestResults: vi.fn() }))
function mount() {
  return render(
    <MemoryRouter initialEntries={['/contests/2101/results']}>
      <Routes>
        <Route path="/contests/:id/results" element={<Scr051ContestResultsPage />} />
      </Routes>
    </MemoryRouter>,
  )
}

describe('SCR-051 FR016', () => {
  beforeEach(() => vi.resetAllMocks())
  it('gates every score and rank by publication even if an unexpected response contains them', async () => {
    vi.mocked(getContestResults).mockResolvedValue({
      ...unpublishedFixture,
      mine: { ...unpublishedFixture.mine!, score: 987654, rank: 876543 },
      ranking: {
        ...publishedFixture.ranking!,
        items: [{ ...publishedFixture.ranking!.items[0]!, score: 765432 }],
      },
    })
    mount()
    expect(await screen.findByText('Đang chờ công bố kết quả')).toBeVisible()
    for (const value of ['987654', '876543', '765432', 'Điểm của bạn', 'Bảng xếp hạng']) {
      expect(screen.queryByText(value)).not.toBeInTheDocument()
    }
    expect(screen.queryByText(/Đáp án|Giải thích/)).not.toBeInTheDocument()
  })
  it('shows published personal results and the server ranking page without exposing email', async () => {
    vi.mocked(getContestResults).mockResolvedValue(publishedFixture)
    mount()
    expect(await screen.findByText('#2')).toBeVisible()
    expect(screen.getByRole('list', { name: 'Bảng xếp hạng cuộc thi' })).toBeVisible()
    expect(screen.getByText('An Nhiên')).toBeVisible()
    expect(screen.getByText('Trang 1 · 3 người trong bảng xếp hạng')).toBeVisible()
    expect(screen.queryByRole('button', { name: 'Sau' })).not.toBeInTheDocument()
    expect(
      screen.getByText('Ban tổ chức sẽ liên hệ người đạt giải qua email đăng ký.'),
    ).toBeVisible()
    expect(screen.queryByText(/@/)).not.toBeInTheDocument()
    expect(getContestResults).toHaveBeenCalledWith(2101, expect.any(AbortSignal))
  })
  it('distinguishes zero score, excluded entry and an empty ranking', async () => {
    vi.mocked(getContestResults).mockResolvedValue({
      ...publishedFixture,
      mine: { ...publishedFixture.mine!, score: 0, excluded: true, rank: null },
      ranking: { ...publishedFixture.ranking!, items: [], totalElements: 0 },
    })
    mount()
    expect(await screen.findByText('0 / 30')).toBeVisible()
    expect(screen.getByText('Lượt của bạn không được tính vào bảng xếp hạng.')).toBeVisible()
    expect(screen.getByText('Chưa có lượt thi được tính vào bảng xếp hạng.')).toBeVisible()
  })
  it('supports loading and retry without displaying a successful result prematurely', async () => {
    vi.mocked(getContestResults)
      .mockRejectedValueOnce(new Error('offline'))
      .mockResolvedValue(unpublishedFixture)
    const user = userEvent.setup()
    mount()
    expect(screen.getByRole('status')).toHaveTextContent('Đang tải kết quả')
    expect(await screen.findByRole('alert')).toBeVisible()
    await user.click(screen.getByRole('button', { name: 'Thử lại' }))
    expect(await screen.findByText('Đang chờ công bố kết quả')).toBeVisible()
  })
})
