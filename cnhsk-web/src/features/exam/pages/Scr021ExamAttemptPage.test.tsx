import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { answerItem, finishSession, getSession } from '@/features/practice/api'
import { nextPlacementBlock } from '../api/placement'
import { mockAttemptFixture, placementAttemptFixture } from '../test-fixtures'
import Scr021ExamAttemptPage from './Scr021ExamAttemptPage'

vi.mock('@/features/practice/api', () => ({
  abandonSession: vi.fn(),
  answerItem: vi.fn(),
  findActiveSession: vi.fn(),
  finishSession: vi.fn(),
  getSession: vi.fn(),
}))
vi.mock('../api/placement', () => ({ nextPlacementBlock: vi.fn() }))

const draftKey = 'cnhsk:mock-exam:5101'
function Location() {
  return <output aria-label="Vị trí">{useLocation().pathname}</output>
}
function open(id = 5101) {
  return render(
    <MemoryRouter initialEntries={['/learn/exams/sessions/' + id]}>
      <Routes>
        <Route path="/learn/exams/sessions/:sessionId" element={<Scr021ExamAttemptPage />} />
      </Routes>
      <Location />
    </MemoryRouter>,
  )
}

describe('SCR-021 FR-025 / AT-26', () => {
  beforeEach(() => {
    sessionStorage.clear()
    vi.resetAllMocks()
    vi.mocked(getSession).mockResolvedValue(mockAttemptFixture)
    vi.mocked(answerItem).mockResolvedValue({
      no: 1,
      accepted: true,
      correctAnswer: 'PRIVATE_ANSWER',
      explanation: 'PRIVATE_EXPLANATION',
    })
    vi.mocked(finishSession).mockImplementation(async (sessionId) => ({
      sessionId,
      status: 'SUBMITTED',
      suspicious: false,
      items: [],
    }))
  })

  it('moves across sections, marks locally, counts answers and hides feedback and countdown', async () => {
    const user = userEvent.setup()
    open()
    const section = await screen.findByRole('group', { name: 'Ngữ pháp' })
    await user.click(within(section).getByRole('button', { name: 'Câu 3' }))
    await user.click(screen.getByRole('button', { name: 'Đánh dấu câu' }))
    expect(within(section).getByRole('button', { name: /Câu 3.*Đã đánh dấu/ })).toBeVisible()
    await user.click(screen.getByRole('button', { name: 'A. Phương án một' }))
    expect(await screen.findByText('Đã lưu')).toBeVisible()
    expect(screen.getByText('Đã làm 1/3 câu · Còn lại 2 câu')).toBeVisible()
    expect(screen.queryByText(/PRIVATE_|đếm ngược/i)).not.toBeInTheDocument()
    expect(screen.queryByRole('timer')).not.toBeInTheDocument()
    expect(answerItem).toHaveBeenCalledWith(5101, expect.objectContaining({ no: 3, answer: 'A' }))
  })

  it('persists before POST, restores after failure, confirms blanks and submits only the unsynced draft', async () => {
    vi.mocked(answerItem).mockImplementationOnce(async () => {
      expect(sessionStorage.getItem(draftKey)).toContain('"answer":"B"')
      throw new Error('Lỗi lưu thử nghiệm')
    })
    const user = userEvent.setup()
    const first = open()
    await user.click(await screen.findByRole('button', { name: 'B. Phương án hai' }))
    expect(await screen.findByText('Chưa lưu được')).toBeVisible()
    await user.click(screen.getByRole('button', { name: 'Câu 2' }))
    expect(screen.getByText('Câu tự soạn 2')).toBeVisible()
    first.unmount()
    open()
    expect(await screen.findByRole('button', { name: 'B. Phương án hai' })).toHaveAttribute(
      'aria-pressed',
      'true',
    )
    await user.click(screen.getByRole('button', { name: 'Nộp bài' }))
    expect(screen.getByText('Còn 2 câu chưa trả lời. Bạn muốn nộp bài?')).toBeVisible()
    expect(finishSession).not.toHaveBeenCalled()
    await user.click(screen.getByRole('button', { name: 'Xác nhận nộp bài' }))
    await waitFor(() =>
      expect(screen.getByLabelText('Vị trí')).toHaveTextContent('/learn/exams/results/5101'),
    )
    expect(finishSession).toHaveBeenCalledWith(5101, {
      answers: [expect.objectContaining({ no: 1, answer: 'B' })],
    })
    expect(sessionStorage.getItem(draftKey)).toBeNull()
  })

  it('skips without counting an answer, then opens a fresh placement block', async () => {
    const blockTwo = {
      ...placementAttemptFixture,
      sessionId: 5202,
      refId: 5201,
      meta: { placement: { block: 2, level: 3 } },
    }
    vi.mocked(getSession)
      .mockResolvedValueOnce(placementAttemptFixture)
      .mockResolvedValueOnce(blockTwo)
    vi.mocked(nextPlacementBlock)
      .mockResolvedValueOnce({ runId: 5201, session: blockTwo })
      .mockResolvedValueOnce({ runId: 5201, session: null, concluded: true })
    const user = userEvent.setup()
    open(5201)
    expect(await screen.findByRole('heading', { name: 'Khối 1' })).toBeVisible()
    await user.click(screen.getByRole('button', { name: 'A. Phương án một' }))
    await user.click(screen.getByRole('button', { name: 'Bỏ qua' }))
    expect(screen.getByText('Đã làm 0/5 câu · Còn lại 5 câu')).toBeVisible()
    await user.click(screen.getByRole('button', { name: 'Nộp khối' }))
    expect(await screen.findByRole('heading', { name: 'Khối 2' })).toBeVisible()
    expect(finishSession).toHaveBeenCalledWith(5201, {
      answers: [expect.objectContaining({ no: 1, answer: null, skipped: true })],
    })
    expect(nextPlacementBlock).toHaveBeenCalledWith(5201)
    expect(screen.getByText('Đã làm 0/5 câu · Còn lại 5 câu')).toBeVisible()
    await user.click(screen.getByRole('button', { name: 'Nộp khối' }))
    await waitFor(() =>
      expect(screen.getByLabelText('Vị trí')).toHaveTextContent('/learn/placement/5201'),
    )
    expect(finishSession).toHaveBeenLastCalledWith(5202, undefined)
    expect(nextPlacementBlock).toHaveBeenNthCalledWith(2, 5201)
  })

  it('retries only next after a submitted block and navigates to the placement result', async () => {
    vi.mocked(getSession).mockResolvedValue(placementAttemptFixture)
    vi.mocked(nextPlacementBlock)
      .mockRejectedValueOnce(new Error('Lỗi tải khối'))
      .mockResolvedValueOnce({ runId: 5201, session: null, concluded: true })
    const user = userEvent.setup()
    open(5201)
    await user.click(await screen.findByRole('button', { name: 'Nộp khối' }))
    expect(await screen.findByRole('alert')).toBeVisible()
    expect(nextPlacementBlock).toHaveBeenCalledTimes(1)
    expect(screen.getByRole('button', { name: 'A. Phương án một' })).toBeDisabled()
    await user.click(screen.getByRole('button', { name: 'Thử lại' }))
    await waitFor(() =>
      expect(screen.getByLabelText('Vị trí')).toHaveTextContent('/learn/placement/5201'),
    )
    expect(finishSession).toHaveBeenCalledOnce()
    expect(nextPlacementBlock).toHaveBeenCalledTimes(2)
  })

  it('continues a previously submitted block without marking it as a retry', async () => {
    vi.mocked(getSession).mockResolvedValue({ ...placementAttemptFixture, status: 'SUBMITTED' })
    open(5201)
    expect(await screen.findByRole('button', { name: 'Tiếp tục' })).toBeEnabled()
    expect(screen.queryByRole('button', { name: 'Thử lại' })).not.toBeInTheDocument()
    expect(nextPlacementBlock).not.toHaveBeenCalled()
  })
})
