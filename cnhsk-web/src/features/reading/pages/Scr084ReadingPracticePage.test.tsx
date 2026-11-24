import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import type { FinishResult, SessionView } from '@/features/practice'
import { answerItem, findActiveSession, finishSession } from '@/features/practice/api'

import { startReadingPractice } from '../api'
import Scr084ReadingPracticePage from './Scr084ReadingPracticePage'

vi.mock('../api', () => ({ startReadingPractice: vi.fn() }))
vi.mock('@/features/practice/api', () => ({
  abandonSession: vi.fn(),
  answerItem: vi.fn(),
  findActiveSession: vi.fn(),
  finishSession: vi.fn(),
  getSession: vi.fn(),
}))

const startMock = vi.mocked(startReadingPractice)
const answerMock = vi.mocked(answerItem)
const finishMock = vi.mocked(finishSession)

function session(): SessionView {
  return {
    answered: [],
    expiresAt: '2026-11-25T14:00:00Z',
    feedback: 'IMMEDIATE',
    items: [
      {
        no: 1,
        options: [
          { key: 'A', text: '对' },
          { key: 'B', text: '错' },
        ],
        prompt: { text: '他每天早上七点起床。★ 他起得很晚。' },
        rules: { minDurationMs: 0 },
        skill: 'READING',
        type: 'QUESTION_CHOICE',
      },
    ],
    meta: { hskLevel: 2, skill: 'READING' },
    refId: null,
    servedAt: '2026-11-25T12:00:00Z',
    sessionId: 901,
    source: 'READING_PRACTICE',
    status: 'IN_PROGRESS',
  }
}

const finished: FinishResult = {
  items: [],
  maxScore: 1,
  percent: 100,
  score: 1,
  sessionId: 901,
  status: 'SUBMITTED',
  suspicious: false,
}

function renderPage(path = '/learn/reading') {
  render(
    <MemoryRouter initialEntries={[path]}>
      <Scr084ReadingPracticePage />
    </MemoryRouter>,
  )
}

describe('Scr084ReadingPracticePage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(findActiveSession).mockResolvedValue(null)
  })

  it('starts the chosen HSK level and type, answers and shows the result', async () => {
    const user = userEvent.setup()
    startMock.mockResolvedValue(session())
    answerMock.mockResolvedValue({
      correct: true,
      correctAnswer: 'B',
      explanation: null,
      no: 1,
    } as never)
    finishMock.mockResolvedValue(finished)
    renderPage()

    expect(await screen.findByRole('heading', { name: 'Đọc', level: 1 })).toBeVisible()
    await user.click(screen.getByRole('button', { name: 'HSK 2' }))
    await user.click(screen.getByRole('button', { name: /Đúng \/ sai/ }))
    await user.click(screen.getByRole('button', { name: 'Bắt đầu đọc' }))

    expect(startMock).toHaveBeenCalledWith({ hskLevel: 2, qtype: 'TRUE_FALSE' })
    await user.click(await screen.findByRole('button', { name: /错/ }))
    expect(answerMock).toHaveBeenCalledWith(901, expect.objectContaining({ no: 1, answer: 'B' }))
    await user.click(await screen.findByRole('button', { name: 'Xem kết quả' }))
    expect(finishMock).toHaveBeenCalled()
    expect(await screen.findByRole('button', { name: 'Đọc bài khác' })).toBeVisible()
  })

  it('says so when the level has no approved reading question', async () => {
    const user = userEvent.setup()
    startMock.mockResolvedValue(null)
    renderPage('/learn/reading?hsk=1')

    await user.click(await screen.findByRole('button', { name: 'Bắt đầu đọc' }))
    expect(startMock).toHaveBeenCalledWith({ hskLevel: 1 })
    expect(await screen.findByRole('status')).toHaveTextContent('HSK 1 chưa có câu đọc đã duyệt')
  })
})
