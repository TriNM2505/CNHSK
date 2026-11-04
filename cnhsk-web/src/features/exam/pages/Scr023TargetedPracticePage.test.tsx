import { StrictMode } from 'react'
import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { answerItem, finishSession } from '@/features/practice/api'
import { getTargetedNodeName, startTargetedPractice } from '../api/targetedPractice'
import { targetedNodeFixture, targetedSessionFixture } from '../test-fixtures'
import Scr023TargetedPracticePage from './Scr023TargetedPracticePage'

vi.mock('../api/targetedPractice', () => ({
  getTargetedNodeName: vi.fn(),
  startTargetedPractice: vi.fn(),
}))
vi.mock('@/features/practice/api', () => ({ answerItem: vi.fn(), finishSession: vi.fn() }))

const { kid, nameVi } = targetedNodeFixture

function open(query = '?kid=' + kid) {
  return render(
    <StrictMode>
      <MemoryRouter initialEntries={['/learn/practice/targeted' + query]}>
        <Scr023TargetedPracticePage />
      </MemoryRouter>
    </StrictMode>,
  )
}

describe('SCR-023 targeted practice', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    vi.mocked(startTargetedPractice).mockResolvedValue(targetedSessionFixture)
    vi.mocked(getTargetedNodeName).mockResolvedValue(nameVi)
    vi.mocked(answerItem).mockResolvedValue({
      no: 1,
      correct: false,
      correctAnswer: 'A',
      explanation: 'Lời giải tự soạn',
    })
    vi.mocked(finishSession).mockResolvedValue({
      sessionId: targetedSessionFixture.sessionId,
      status: 'SUBMITTED',
      suspicious: false,
      items: [],
    })
  })

  it('FR027_AT28: phản hồi ngay từng câu, nhắc đúng câu lặp và hoàn thành lượt', async () => {
    const user = userEvent.setup()
    open('?kid=' + kid + '&qtype=MULTIPLE_CHOICE&exclude=48')

    expect(await screen.findByText(kid + ' · ' + nameVi)).toBeVisible()
    expect(await screen.findByText('Câu 1/2')).toBeVisible()
    expect(screen.getByText('Câu bạn vừa làm sai')).toBeVisible()
    expect(screen.queryByText('Câu tự soạn 2')).not.toBeInTheDocument()
    expect(screen.queryByText(/Đáp án đúng|Lời giải tự soạn/)).not.toBeInTheDocument()
    expect(startTargetedPractice).toHaveBeenCalledTimes(1)
    expect(startTargetedPractice).toHaveBeenCalledWith({
      kid,
      qtype: 'MULTIPLE_CHOICE',
      excludeQuestionId: 48,
    })
    expect(answerItem).not.toHaveBeenCalled()

    const choices = within(screen.getByRole('group', { name: 'Phương án' }))
    await user.click(choices.getByRole('button', { name: 'B. Phương án hai' }))
    expect(await screen.findByText('Chưa đúng')).toBeVisible()
    expect(screen.getByText('Đáp án đúng: A')).toBeVisible()
    expect(screen.getByText('Lời giải tự soạn')).toBeVisible()
    expect(answerItem).toHaveBeenCalledWith(targetedSessionFixture.sessionId, {
      no: 1,
      answer: 'B',
      durationMs: expect.any(Number),
    })

    await user.click(screen.getByRole('button', { name: 'Câu tiếp theo' }))
    expect(screen.getByText('Câu 2/2')).toBeVisible()
    expect(screen.queryByText('Câu bạn vừa làm sai')).not.toBeInTheDocument()
    expect(screen.queryByText(/Đáp án đúng|Lời giải tự soạn/)).not.toBeInTheDocument()
    vi.mocked(answerItem).mockResolvedValueOnce({ no: 2, correct: true })
    await user.click(screen.getByRole('button', { name: 'A. Phương án một' }))
    expect(await screen.findByText('Chính xác')).toBeVisible()
    await user.click(screen.getByRole('button', { name: 'Hoàn thành' }))
    expect(await screen.findByText('Đã hoàn thành bài luyện')).toBeVisible()
    expect(finishSession).toHaveBeenCalledWith(targetedSessionFixture.sessionId)
  })

  it('FR027_AT28: bỏ trường đáp án và lời giải từ dữ liệu phát câu trước khi trả lời', async () => {
    const item = targetedSessionFixture.items[0]!
    const unsafe = {
      ...item,
      answer: 'bí mật 1',
      accept: ['bí mật 2'],
      explanation: 'bí mật 3',
      is_correct: true,
      isCorrect: true,
      correctKey: 'bí mật 4',
    }
    vi.mocked(startTargetedPractice).mockResolvedValue({
      ...targetedSessionFixture,
      items: [unsafe],
    })

    const { container } = open()
    expect(await screen.findByText('Câu 1/1')).toBeVisible()
    expect(screen.queryByText(/bí mật|Đáp án đúng/)).not.toBeInTheDocument()
    expect(container.innerHTML).not.toMatch(/bí mật|is_correct|isCorrect|correctKey/)
  })

  it('FR027_AT28: rỗng không có nút hay gợi ý AI', async () => {
    vi.mocked(startTargetedPractice).mockResolvedValue({ session: null, reason: 'NO_QUESTIONS' })
    open()
    expect(await screen.findByText('Chưa có bài luyện cho nút này')).toBeVisible()
    expect(screen.queryByRole('button')).not.toBeInTheDocument()
    expect(screen.queryByText(/\bAI\b/)).not.toBeInTheDocument()
  })

  it('FR027_AT28: tên nút tải song song, lỗi tên vẫn luyện được bằng KID', async () => {
    let rejectName: (reason: Error) => void = () => undefined
    vi.mocked(getTargetedNodeName).mockImplementation(
      () =>
        new Promise((_, reject) => {
          rejectName = reject
        }),
    )
    open()
    expect(await screen.findByText('Câu 1/2')).toBeVisible()
    expect(getTargetedNodeName).toHaveBeenCalledWith(kid, expect.any(AbortSignal))
    rejectName(new Error('Không tìm thấy tên nút'))
    expect(screen.getByText(kid)).toBeVisible()
    expect(screen.getByRole('button', { name: 'A. Phương án một' })).toBeEnabled()
  })
})
