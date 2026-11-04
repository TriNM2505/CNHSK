import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { getExamResult, getExamWeakPoints } from '../api/exams'
import { examReviewFixture } from '../test-fixtures'
import Scr022ResultRoute from './Scr022ResultRoute'

vi.mock('../api/exams', () => ({ getExamResult: vi.fn(), getExamWeakPoints: vi.fn() }))

function open(id: string = '5101') {
  return render(
    <MemoryRouter initialEntries={['/learn/exams/results/' + id]}>
      <Routes>
        <Route path="/learn/exams/results/:sessionId" element={<Scr022ResultRoute />} />
      </Routes>
    </MemoryRouter>,
  )
}

describe('SCR-022 exam result FR-026 / AT-27', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    vi.mocked(getExamResult).mockResolvedValue(examReviewFixture)
  })
  it('shows weighted scores, first-attempt result and keeps weak points off', async () => {
    open()
    expect(await screen.findByText('Điểm trọng số: 2/5 (40%)')).toBeVisible()
    expect(screen.getByText('Số câu đúng: 1/3')).toBeVisible()
    expect(screen.getByText('Đọc hiểu: 2/5')).toBeVisible()
    expect(screen.queryByText(/Lần trước|Điểm yếu cần luyện|đỗ|trượt/i)).not.toBeInTheDocument()
    expect(getExamResult).toHaveBeenCalledWith(5101, expect.any(AbortSignal))
    expect(getExamWeakPoints).not.toHaveBeenCalled()
  })
  it('filters wrong and blank separately, shows answers and links only where a KID exists', async () => {
    const user = userEvent.setup()
    open()
    const filters = await screen.findByRole('group', { name: 'Lọc câu hỏi' })
    expect(screen.getAllByRole('article')).toHaveLength(3)
    await user.click(within(filters).getByRole('button', { name: 'Sai' }))
    const wrong = screen.getByRole('article', { name: 'Câu 2' })
    expect(within(wrong).getByText('Câu 2 · Sai')).toBeVisible()
    expect(within(wrong).getByText('Đáp án đúng: A. Phương án một')).toBeVisible()
    expect(within(wrong).getByRole('link', { name: 'Luyện nút này' })).toHaveAttribute(
      'href',
      '/learn/practice/targeted?kid=NP1-101',
    )
    expect(screen.queryByRole('article', { name: 'Câu 3' })).not.toBeInTheDocument()
    await user.click(within(filters).getByRole('button', { name: 'Bỏ trống' }))
    const blank = screen.getByRole('article', { name: 'Câu 3' })
    expect(within(blank).getByText('Câu 3 · Bỏ trống')).toBeVisible()
    expect(within(blank).getByText('Lời giải đang được cập nhật')).toBeVisible()
    expect(within(blank).queryByRole('link')).not.toBeInTheDocument()
    expect(screen.queryByRole('article', { name: 'Câu 2' })).not.toBeInTheDocument()
    const group = screen.getByRole('region', { name: 'Câu sai theo KID' })
    expect(within(group).getByRole('link')).toHaveAttribute(
      'href',
      '/learn/practice/targeted?kid=NP1-101',
    )
  })
  it('shows the previous score when available', async () => {
    vi.mocked(getExamResult).mockResolvedValue({
      ...examReviewFixture,
      previous: {
        score: 1,
        maxScore: 5,
        percent: 20,
        submittedAt: '2026-11-03T00:00:00Z',
      },
    })
    open()
    expect(await screen.findByText('Lần trước: 1/5 (20%)')).toBeVisible()
  })
  it.each(['x', '0', '-1', '1.2', '1e2'])('rejects invalid sessionId %s without loading', (id) => {
    open(id)
    expect(screen.getByRole('alert')).toHaveTextContent('Không tìm thấy kết quả')
    expect(getExamResult).not.toHaveBeenCalled()
  })
})
