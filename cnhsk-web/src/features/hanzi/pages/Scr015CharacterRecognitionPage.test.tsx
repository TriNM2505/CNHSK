import { act, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { answerItem, findActiveSession, finishSession, getSession } from '@/features/practice/api'
import type { FinishResult, SessionView } from '@/features/practice'

import { ApiError } from '@/shared/api'

import { startRecognitionSession } from '../api/recognition'
import Scr015CharacterRecognitionPage from './Scr015CharacterRecognitionPage'

vi.mock('../api/recognition', () => ({ startRecognitionSession: vi.fn() }))

vi.mock('@/features/practice/api', () => ({
  abandonSession: vi.fn(),
  answerItem: vi.fn(),
  findActiveSession: vi.fn(),
  finishSession: vi.fn(),
  getSession: vi.fn(),
}))

const startMock = vi.mocked(startRecognitionSession)
const findActiveMock = vi.mocked(findActiveSession)
const getSessionMock = vi.mocked(getSession)
const answerMock = vi.mocked(answerItem)
const finishMock = vi.mocked(finishSession)

const options = [
  { key: 'A', text: 'mẹ' },
  { key: 'B', text: 'tốt; hay' },
  { key: 'C', text: 'cô ấy' },
  { key: 'D', text: 'nữ' },
]

function meaningItem(no: number, text: string): SessionView['items'][number] {
  return {
    no,
    options,
    prompt: { text },
    rules: { minDurationMs: 800 },
    skill: 'CHARACTER',
    type: 'CHAR_MEANING_CHOICE',
  }
}

function session(overrides: Partial<SessionView> = {}): SessionView {
  return {
    answered: [],
    expiresAt: '2026-10-29T14:00:00Z',
    feedback: 'IMMEDIATE',
    items: [meaningItem(1, '好'), meaningItem(2, '妈')],
    refId: null,
    servedAt: '2026-10-29T12:00:00Z',
    sessionId: 401,
    source: 'CHAR_RECOGNITION',
    status: 'IN_PROGRESS',
    ...overrides,
  }
}

const finishResult: FinishResult = {
  items: [],
  maxScore: 2,
  percent: 50,
  score: 1,
  sessionId: 401,
  status: 'SUBMITTED',
  suspicious: false,
}

function renderPage() {
  render(
    <MemoryRouter>
      <Scr015CharacterRecognitionPage />
    </MemoryRouter>,
  )
}

describe('Scr015CharacterRecognitionPage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    findActiveMock.mockResolvedValue(null)
  })

  it('không có lượt dở thì hiện bước chọn chế độ, chỉ cho chọn HSK 1–3', async () => {
    renderPage()

    expect(await screen.findByRole('button', { name: /Bắt đầu luyện/ })).toBeVisible()
    expect(
      screen.getByRole('heading', { name: 'Nhận diện chữ Hán' }).closest('.max-w-reading'),
    ).not.toBeNull()
    expect(findActiveMock).toHaveBeenCalledWith('CHAR_RECOGNITION', null, expect.any(AbortSignal))
    const values = Array.from(
      (screen.getByLabelText('Cấp HSK') as HTMLSelectElement).options,
    ).map((option) => option.value)
    expect(values).toEqual(['1', '2', '3'])
    expect(startMock).not.toHaveBeenCalled()
  })

  it('gửi đúng chế độ và cấp đã chọn, rồi hiện câu đầu tiên', async () => {
    const user = userEvent.setup()
    startMock.mockResolvedValue(session())
    renderPage()

    await user.click(await screen.findByRole('button', { name: /Nghe chọn chữ/ }))
    await user.selectOptions(screen.getByLabelText('Cấp HSK'), '2')
    startMock.mockResolvedValue(session())
    await user.click(screen.getByRole('button', { name: /Bắt đầu luyện/ }))

    expect(startMock).toHaveBeenCalledWith({ count: 10, hskLevel: 2, mode: 'AUDIO_TO_CHAR' })
    expect(await screen.findByText('好')).toBeVisible()
  })

  it('báo chưa có chữ phù hợp khi máy chủ trả session null', async () => {
    const user = userEvent.setup()
    startMock.mockResolvedValue(null)
    renderPage()

    await user.selectOptions(await screen.findByLabelText('Cấp HSK'), '3')
    await user.click(screen.getByRole('button', { name: /Bắt đầu luyện/ }))

    expect(await screen.findByText(/Chưa có chữ phù hợp ở HSK 3/)).toBeVisible()
  })

  it('trả lời, xem phản hồi, sang câu tiếp và nộp khi hết câu', async () => {
    const user = userEvent.setup()
    startMock.mockResolvedValue(session())
    answerMock
      .mockResolvedValueOnce({ accepted: true, correct: true, correctAnswer: 'B', no: 1 })
      .mockResolvedValueOnce({ accepted: true, correct: false, correctAnswer: 'A', no: 2 })
    finishMock.mockResolvedValue(finishResult)
    renderPage()

    await user.click(await screen.findByRole('button', { name: /Bắt đầu luyện/ }))
    await user.click(await screen.findByRole('button', { name: /tốt; hay/ }))
    expect(await screen.findByText('Chính xác')).toBeVisible()
    expect(answerMock).toHaveBeenCalledWith(401, expect.objectContaining({ answer: 'B', no: 1 }))

    await user.click(screen.getByRole('button', { name: /Câu tiếp/ }))
    expect(await screen.findByText('妈')).toBeVisible()
    await user.click(screen.getByRole('button', { name: /cô ấy/ }))
    expect(await screen.findByText('Chưa chính xác')).toBeVisible()

    await user.click(screen.getByRole('button', { name: /Câu tiếp/ }))
    await waitFor(() => expect(finishMock).toHaveBeenCalledWith(401, undefined))
    expect(await screen.findByRole('button', { name: /Luyện lượt mới/ })).toBeVisible()
  })

  it('bỏ qua gửi skipped, không gửi đáp án', async () => {
    const user = userEvent.setup()
    startMock.mockResolvedValue(session())
    answerMock.mockResolvedValue({ accepted: true, correct: null, counted: false, no: 1 })
    renderPage()

    await user.click(await screen.findByRole('button', { name: /Bắt đầu luyện/ }))
    await user.click(await screen.findByRole('button', { name: 'Bỏ qua' }))

    expect(answerMock).toHaveBeenCalledWith(401, expect.objectContaining({ no: 1, skipped: true }))
    expect(answerMock.mock.calls[0]?.[1]).not.toHaveProperty('answer')
  })

  it('khôi phục lượt đang dở và tiếp tục ở câu chưa trả lời', async () => {
    findActiveMock.mockResolvedValue(session())
    getSessionMock.mockResolvedValue(
      session({ answered: [{ correct: true, correctAnswer: 'B', counted: true, no: 1 }] }),
    )
    renderPage()

    expect(await screen.findByText('妈')).toBeVisible()
    expect(screen.queryByRole('button', { name: /Bắt đầu luyện/ })).not.toBeInTheDocument()
  })

  it('kết thúc sớm phải xác nhận trong trang rồi mới nộp', async () => {
    const user = userEvent.setup()
    startMock.mockResolvedValue(session())
    finishMock.mockResolvedValue(finishResult)
    renderPage()

    await user.click(await screen.findByRole('button', { name: /Bắt đầu luyện/ }))
    await user.click(await screen.findByRole('button', { name: 'Kết thúc luyện tập' }))
    expect(finishMock).not.toHaveBeenCalled()

    await user.click(screen.getByRole('button', { name: 'Kết thúc và xem kết quả' }))
    await waitFor(() => expect(finishMock).toHaveBeenCalledWith(401, undefined))
  })
  it('FR028_answerFailureKeepsQuestionShowsAlertAndCanRetry', async () => {
    const user = userEvent.setup()
    startMock.mockResolvedValue(session())
    answerMock
      .mockRejectedValueOnce(new ApiError({ status: 0, errorCode: 'NETWORK_ERROR', message: 'Lỗi mạng' }))
      .mockResolvedValueOnce({ accepted: true, correct: true, correctAnswer: 'B', no: 1 })
    renderPage()
    await user.click(await screen.findByRole('button', { name: /Bắt đầu luyện/ }))
    await user.click(screen.getByRole('button', { name: /tốt; hay/ }))
    expect(await screen.findByRole('alert')).toHaveTextContent('Lỗi mạng')
    expect(screen.getByText('好')).toBeVisible()
    expect(screen.getByRole('button', { name: /tốt; hay/ })).toBeEnabled()
    await user.click(screen.getByRole('button', { name: /tốt; hay/ }))
    expect(await screen.findByText('Chính xác')).toBeVisible()
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
  })

  it('FR028_finishFailureShowsAlertAndAllowsRetry', async () => {
    const user = userEvent.setup()
    startMock.mockResolvedValue(session({ items: [meaningItem(1, '好')] }))
    answerMock.mockResolvedValue({ accepted: true, correct: true, correctAnswer: 'B', no: 1 })
    finishMock
      .mockRejectedValueOnce(new ApiError({ status: 0, errorCode: 'NETWORK_ERROR', message: 'Lỗi kết thúc' }))
      .mockResolvedValueOnce(finishResult)
    renderPage()
    await user.click(await screen.findByRole('button', { name: /Bắt đầu luyện/ }))
    await user.click(screen.getByRole('button', { name: /tốt; hay/ }))
    await user.click(await screen.findByRole('button', { name: /Câu tiếp/ }))
    expect(await screen.findByRole('alert')).toHaveTextContent('Lỗi kết thúc')
    await user.click(screen.getByRole('button', { name: /Câu tiếp/ }))
    expect(await screen.findByRole('button', { name: /Luyện lượt mới/ })).toBeVisible()
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
    expect(finishMock).toHaveBeenCalledTimes(2)
  })

  it('FR028_autoFinishesAllAnsweredRestoredSessionOnceThenWaitsForManualRetry', async () => {
    const user = userEvent.setup()
    const restored = session({ answered: [
      { correct: true, correctAnswer: 'B', counted: true, no: 1 },
      { correct: false, correctAnswer: 'A', counted: true, no: 2 },
    ] })
    findActiveMock.mockResolvedValue(restored)
    getSessionMock.mockResolvedValue(restored)
    finishMock
      .mockRejectedValueOnce(new ApiError({ status: 0, errorCode: 'NETWORK_ERROR', message: 'Lỗi tự kết thúc' }))
      .mockResolvedValueOnce(finishResult)
    renderPage()
    expect(await screen.findByRole('alert')).toHaveTextContent('Lỗi tự kết thúc')
    await act(async () => { await new Promise((resolve) => setTimeout(resolve, 0)) })
    expect(finishMock).toHaveBeenCalledTimes(1)
    await user.click(screen.getByRole('button', { name: 'Thử kết thúc lại' }))
    expect(await screen.findByRole('button', { name: /Luyện lượt mới/ })).toBeVisible()
    expect(finishMock).toHaveBeenCalledTimes(2)
  })

  it('FR028_restoresSummaryFromSubmittedSessionAnswers', async () => {
    const restored = session({
      status: 'SUBMITTED',
      answered: [
        { answer: 'B', correct: true, correctAnswer: 'B', counted: true, no: 1 },
        { answer: 'A', correct: false, correctAnswer: 'B', counted: true, no: 2 },
      ],
    })
    findActiveMock.mockResolvedValue(restored)
    getSessionMock.mockResolvedValue(restored)
    renderPage()
    expect(await screen.findByText('1/2')).toBeVisible()
    expect(screen.getByRole('button', { name: /Luyện lượt mới/ })).toBeVisible()
  })

  it('FR028_hidesHskLabelForRestoredSessionWithoutLevel', async () => {
    findActiveMock.mockResolvedValue(session())
    getSessionMock.mockResolvedValue(session())
    renderPage()
    await screen.findByText('好')
    expect(screen.queryByText('HSK 1')).not.toBeInTheDocument()
  })

  it('FR028_usesHskLevelFromRestoredSessionMetadata', async () => {
    findActiveMock.mockResolvedValue(session())
    getSessionMock.mockResolvedValue(session({ meta: { hskLevel: 2 } }))
    renderPage()
    expect(await screen.findByText('HSK 2')).toBeVisible()
  })

  it('FR028_showsExactlyFourOptionsWithoutAnswerBeforeSubmit', async () => {
    const user = userEvent.setup()
    startMock.mockResolvedValue(session())
    renderPage()
    await user.click(await screen.findByRole('button', { name: /Bắt đầu luyện/ }))
    expect(screen.getByRole('group', { name: 'Các phương án' }).querySelectorAll('button')).toHaveLength(4)
    expect(screen.queryByText('Đáp án đúng:')).not.toBeInTheDocument()
    expect(screen.queryByText('Chính xác')).not.toBeInTheDocument()
  })

  it('FR028_audioFailedSendsNoAnswerAndIsNotMarkedIncorrect', async () => {
    const user = userEvent.setup()
    startMock.mockResolvedValue(session({ items: [{
      no: 1,
      options: [{ key: 'A', text: '好' }, { key: 'B', text: '妈' }, { key: 'C', text: '是' }, { key: 'D', text: '事' }],
      prompt: { audioUrl: '/audio/hao.mp3' },
      rules: { hasAudio: true, maxReplays: 3, minDurationMs: 800 },
      skill: 'LISTENING',
      type: 'AUDIO_CHAR_CHOICE',
    }] }))
    answerMock.mockResolvedValue({ accepted: true, correct: null, counted: false, no: 1 })
    renderPage()
    await user.click(await screen.findByRole('button', { name: /Bắt đầu luyện/ }))
    expect(document.querySelector('audio')).toHaveAttribute('src', '/audio/hao.mp3')
    await user.click(screen.getByRole('button', { name: 'Không nghe được' }))
    await waitFor(() => expect(answerMock).toHaveBeenCalledTimes(1))
    expect(answerMock.mock.calls[0]?.[1]).toEqual(expect.objectContaining({ audioFailed: true, replayCount: 0 }))
    expect(answerMock.mock.calls[0]?.[1]).not.toHaveProperty('answer')
    expect(await screen.findByText('Câu này không tính điểm.')).toBeVisible()
  })

  it('FR028_limitsAudioToThreeReplaysAndSubmitsReplayCount', async () => {
    const user = userEvent.setup()
    const play = vi.spyOn(HTMLMediaElement.prototype, 'play').mockResolvedValue()
    startMock.mockResolvedValue(session({ items: [{
      no: 1,
      options: [{ key: 'A', text: '好' }, { key: 'B', text: '妈' }, { key: 'C', text: '是' }, { key: 'D', text: '事' }],
      prompt: { audioUrl: '/audio/hao.mp3' },
      rules: { hasAudio: true, maxReplays: 3, minDurationMs: 800 },
      skill: 'LISTENING',
      type: 'AUDIO_CHAR_CHOICE',
    }] }))
    answerMock.mockResolvedValue({ accepted: true, correct: true, correctAnswer: 'A', no: 1 })
    renderPage()
    await user.click(await screen.findByRole('button', { name: /Bắt đầu luyện/ }))
    await user.click(screen.getByRole('button', { name: 'Nghe từ' }))
    await user.click(screen.getByRole('button', { name: 'Nghe từ' }))
    await user.click(screen.getByRole('button', { name: 'Nghe từ' }))
    await waitFor(() => expect(screen.getByText('Đã nghe 3/3 lần')).toBeVisible())
    expect(screen.getByRole('button', { name: 'Đã hết lượt nghe' })).toBeDisabled()
    await user.click(screen.getByRole('button', { name: /好/ }))
    await waitFor(() => expect(answerMock).toHaveBeenCalledWith(401, expect.objectContaining({ replayCount: 3 })))
    play.mockRestore()
  })

  it('FR028_showsLoadingWhileActiveSessionIsPending', async () => {
    let resolveActive!: (value: SessionView | null) => void
    findActiveMock.mockReturnValue(new Promise((resolve) => { resolveActive = resolve }))
    renderPage()
    expect(screen.getByLabelText('Đang tải lượt nhận diện')).toBeVisible()
    resolveActive(null)
    expect(await screen.findByRole('button', { name: /Bắt đầu luyện/ })).toBeVisible()
  })

  it('FR028_preventsDuplicateAnswerRequests', async () => {
    const user = userEvent.setup()
    let resolveAnswer!: (value: Awaited<ReturnType<typeof answerItem>>) => void
    startMock.mockResolvedValue(session())
    answerMock.mockReturnValue(new Promise((resolve) => { resolveAnswer = resolve }))
    renderPage()
    await user.click(await screen.findByRole('button', { name: /Bắt đầu luyện/ }))
    await user.dblClick(screen.getByRole('button', { name: /tốt; hay/ }))
    expect(answerMock).toHaveBeenCalledTimes(1)
    resolveAnswer({ accepted: true, correct: true, correctAnswer: 'B', no: 1 })
  })
})
