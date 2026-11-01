import { act, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { findActiveSession } from '@/features/practice/api'
import type { SessionView } from '@/features/practice'
import { ApiError, parseApiError } from '@/shared/api'

import { getCharacter, getCharacters, recordWritingAttempt, startWritingChallenge } from '../api/characters'
import type { CharacterDetail, WritingAttemptRequest, WritingAttemptResult } from '../types'
import Scr014CharacterWritingPage from './Scr014CharacterWritingPage'

const canvasReadyMock = vi.hoisted(() => ({
  callback: undefined as (() => void) | undefined,
  dataError: undefined as (() => void) | undefined,
  quiz: vi.fn(),
}))

vi.mock('../api/characters', () => ({
  getCharacter: vi.fn(),
  getCharacters: vi.fn(),
  recordWritingAttempt: vi.fn(),
  startWritingChallenge: vi.fn(),
}))

vi.mock('../components/HanziCanvas', async () => {
  const React = await import('react')
  const Canvas = React.forwardRef(function Canvas(
    props: { character: string; onDataError?: () => void; onReady?: () => void },
    ref,
  ) {
    React.useImperativeHandle(ref, () => ({
      animateCharacter: vi.fn(),
      cancelAnimations: vi.fn(),
      cancelQuiz: vi.fn(),
      quiz: canvasReadyMock.quiz,
      setVisibility: vi.fn(),
    }))
    React.useEffect(() => {
      canvasReadyMock.callback = props.onReady
      canvasReadyMock.dataError = props.onDataError
    }, [props.character, props.onDataError, props.onReady])
    return React.createElement('div', { 'data-testid': 'hanzi-canvas', 'data-character': props.character })
  })
  return { HanziCanvas: Canvas }
})

vi.mock('@/features/practice/api', () => ({
  abandonSession: vi.fn(),
  answerItem: vi.fn(),
  findActiveSession: vi.fn(),
  finishSession: vi.fn(),
  getSession: vi.fn(),
}))

const getCharactersMock = vi.mocked(getCharacters)
const getCharacterMock = vi.mocked(getCharacter)
const recordWritingAttemptMock = vi.mocked(recordWritingAttempt)
const startChallengeMock = vi.mocked(startWritingChallenge)
const findActiveMock = vi.mocked(findActiveSession)

afterEach(() => vi.useRealTimers())

function LocationProbe() {
  const location = useLocation()
  return <output data-testid="location">{`${location.pathname}${location.search}`}</output>
}

function renderAt(path: string) {
  render(
    <MemoryRouter initialEntries={[path]}>
      <Routes>
        <Route element={<Scr014CharacterWritingPage />} path="/learn/characters/writing" />
        <Route element={<Scr014CharacterWritingPage />} path="/learn/characters/writing/challenge" />
        <Route element={<Scr014CharacterWritingPage />} path="/learn/characters/writing/:lexemeId" />
      </Routes>
      <LocationProbe />
    </MemoryRouter>,
  )
}

const detail: CharacterDetail = {
  audioUrl: null,
  hasAudio: false,
  hskLevel: 1,
  id: 11,
  meaning: 'tốt; hay',
  pinyin: 'hǎo',
  radical: '女',
  sinoViet: 'hảo',
  strokeCount: 6,
  text: '好',
  writing: { dictationAvailable: false, recallUnlocked: false, strokeAvailable: true },
}

describe('Scr014CharacterWritingPage — danh sách chữ', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    getCharactersMock.mockResolvedValue({
      items: [{ hasAudio: true, hskLevel: 1, id: 11, meaning: 'tốt; hay', pinyin: 'hǎo', strokeCount: 6, text: '好' }],
      page: 0,
      size: 20,
      totalElements: 1,
    })
  })

  it('tải trang đầu (page=0 ở API) và dẫn tới trang luyện của chữ', async () => {
    renderAt('/learn/characters/writing')

    const link = await screen.findByRole('link', { name: /好/ })
    expect(link).toHaveAttribute('href', '/learn/characters/writing/11')
    expect(getCharactersMock).toHaveBeenCalledWith(
      { hskLevel: undefined, page: 0, q: undefined, size: 20 },
      expect.any(AbortSignal),
    )
  })

  it('tìm và lọc cấp ghi lên URL, cấp chỉ có HSK 1–3', async () => {
    const user = userEvent.setup()
    renderAt('/learn/characters/writing?page=2')
    await screen.findByRole('link', { name: /好/ })
    expect(getCharactersMock).toHaveBeenLastCalledWith(
      expect.objectContaining({ page: 1 }),
      expect.any(AbortSignal),
    )

    const levels = Array.from((screen.getByLabelText('Cấp HSK') as HTMLSelectElement).options).map(
      (option) => option.value,
    )
    expect(levels).toEqual(['', '1', '2', '3'])

    await user.type(screen.getByLabelText('Tìm chữ'), 'hao')
    await user.click(screen.getByRole('button', { name: 'Tìm' }))
    expect(screen.getByTestId('location')).toHaveTextContent('/learn/characters/writing?q=hao')

    await user.selectOptions(screen.getByLabelText('Cấp HSK'), '2')
    expect(screen.getByTestId('location')).toHaveTextContent('?q=hao&hskLevel=2')
    await waitFor(() =>
      expect(getCharactersMock).toHaveBeenLastCalledWith(
        { hskLevel: 2, page: 0, q: 'hao', size: 20 },
        expect.any(AbortSignal),
      ),
    )
  })
})

describe('Scr014CharacterWritingPage — luyện một chữ', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    canvasReadyMock.callback = undefined
    canvasReadyMock.dataError = undefined
  })

  it('mở sẵn Viết theo nét, khoá chế độ chưa dùng được kèm lý do, có khung viết 300 px', async () => {
    getCharacterMock.mockResolvedValue(detail)
    renderAt('/learn/characters/writing/11')

    expect(await screen.findByRole('button', { name: /^Viết theo nét/ })).toHaveAttribute(
      'aria-pressed',
      'true',
    )
    expect(screen.getByRole('button', { name: /Nhớ rồi viết/ })).toBeDisabled()
    expect(screen.getByText('Viết theo nét trước')).toBeVisible()
    expect(screen.getByRole('button', { name: /Nghe chép/ })).toBeDisabled()
    expect(screen.getByText('Chữ chưa có âm thanh')).toBeVisible()
    expect(screen.getByRole('link', { name: /Thử thách/ })).toHaveAttribute(
      'href',
      '/learn/characters/writing/challenge',
    )
    expect(screen.getByTestId('hanzi-canvas')).toBeVisible()
    expect(getCharacterMock).toHaveBeenCalledWith(11, expect.any(AbortSignal))
    expect(screen.queryByText(/Chưa xác nhận được kết quả/)).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Gửi lại' })).not.toBeInTheDocument()
    expect(recordWritingAttemptMock).not.toHaveBeenCalled()
  })

  it('ẩn chữ đích bên cạnh khung viết trong RECALL và DICTATION', async () => {
    const user = userEvent.setup()
    getCharacterMock.mockResolvedValue({
      ...detail,
      audioUrl: '/audio/hao.mp3',
      hasAudio: true,
      writing: { dictationAvailable: true, recallUnlocked: true, strokeAvailable: true },
    })
    renderAt('/learn/characters/writing/11')

    expect(await screen.findAllByText('好')).toHaveLength(2)
    await user.click(screen.getByRole('button', { name: /Nhớ rồi viết/ }))
    expect(screen.queryByText('好')).not.toBeInTheDocument()
    expect(screen.getByText(/chữ mẫu không nằm cạnh bài làm/i)).toBeVisible()

    await user.click(screen.getByRole('button', { name: /Nghe chép/ }))
    expect(screen.queryByText('好')).not.toBeInTheDocument()
    expect(screen.getByText(/chữ đích và thông tin gợi ý được giấu/i)).toBeVisible()
  })

  it('id không hợp lệ hoặc 404 hiện không tìm thấy chữ', async () => {
    getCharacterMock.mockRejectedValue(
      new ApiError({ errorCode: 'CHARACTER_NOT_FOUND', message: 'x', status: 404 }),
    )
    renderAt('/learn/characters/writing/999')
    expect(await screen.findByText('Không tìm thấy chữ')).toBeVisible()
  })
})

describe('Scr014CharacterWritingPage — thử thách', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    findActiveMock.mockResolvedValue(null)
  })

  it('chưa đủ 10 chữ thì báo số chữ hiện có', async () => {
    const user = userEvent.setup()
    startChallengeMock.mockRejectedValue(
      new ApiError({
        errorCode: 'NOT_ENOUGH_CHARACTERS',
        extra: { have: 4, need: 10 },
        message: 'x',
        status: 422,
      }),
    )
    renderAt('/learn/characters/writing/challenge')

    await user.click(await screen.findByRole('button', { name: /Bắt đầu thử thách/ }))
    expect(await screen.findByText(/hiện có 4/)).toBeVisible()
    expect(screen.getByRole('button', { name: /Bắt đầu thử thách/ })).toBeDisabled()
  })

  it('khôi phục lượt đang làm và hiện đồng hồ, số chữ trượt, chữ đầu tiên', async () => {
    const active: SessionView = {
      answered: [],
      expiresAt: new Date(Date.now() + 210_000).toISOString(),
      feedback: 'DEFERRED',
      items: [
        {
          no: 1,
          prompt: { meaning: 'mẹ', pinyin: 'mā', strokeCount: 6, text: '妈' },
          rules: {},
          skill: 'WRITING',
          type: 'WRITING_RESULT',
        },
      ],
      meta: { maxMistakes: 3, timeLimitSeconds: 200 },
      servedAt: new Date().toISOString(),
      sessionId: 77,
      source: 'WRITING_CHALLENGE',
      status: 'IN_PROGRESS',
    }
    findActiveMock.mockResolvedValue(active)
    const { getSession } = await import('@/features/practice/api')
    vi.mocked(getSession).mockResolvedValue(active)
    renderAt('/learn/characters/writing/challenge')

    expect(await screen.findByText('mā')).toBeVisible()
    expect(screen.getByText('0/3')).toBeVisible()
    expect(screen.getByText(/^3:[12]\d$/)).toBeVisible()
    expect(startChallengeMock).not.toHaveBeenCalled()
  })
})

describe('Scr014CharacterWritingPage — ghi lượt luyện viết', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    getCharacterMock.mockResolvedValue(detail)
  })

  async function submitIncomplete(user: ReturnType<typeof userEvent.setup>) {
    const stopButton = await screen.findByRole('button', { name: 'Dừng' })
    await screen.findByTestId('hanzi-canvas')
    act(() => canvasReadyMock.callback?.())
    await waitFor(() => expect(stopButton).toBeEnabled())
    await user.click(stopButton)
    await waitFor(() => expect(recordWritingAttemptMock).toHaveBeenCalledOnce())
    return recordWritingAttemptMock.mock.calls[0]![0]
  }

  it('FR010_unknownOutcomeDoesNotRetryUntilTheLearnerClicksAndReusesTheSamePayload', async () => {
    const user = userEvent.setup()
    const response: WritingAttemptResult = { attemptId: 4, counted: true, recorded: true, recallUnlocked: true }
    recordWritingAttemptMock.mockRejectedValueOnce(new ApiError({ errorCode: 'NETWORK_ERROR', message: 'offline', status: 0 }))
      .mockResolvedValueOnce(response)
    renderAt('/learn/characters/writing/11')

    const submitted = await submitIncomplete(user) as WritingAttemptRequest
    expect(await screen.findByText(/Chưa xác nhận được kết quả/)).toBeVisible()
    expect(recordWritingAttemptMock).toHaveBeenCalledOnce()

    await user.click(screen.getByRole('button', { name: 'Gửi lại' }))
    await waitFor(() => expect(recordWritingAttemptMock).toHaveBeenCalledTimes(2))
    expect(recordWritingAttemptMock.mock.calls[1]![0]).toEqual(submitted)
    expect(await screen.findByText('Đã ghi nhận kết quả.')).toBeVisible()
  })

  it('FR019_readsTopLevelRetryAfterFromTheParsed429BodyAndDoesNotRetryAutomatically', async () => {
    const user = userEvent.setup()
    const error = parseApiError(429, {
      error_code: 'RATE_LIMITED',
      message: 'rate limit',
      request_id: 'request-test',
      retryAfterSeconds: 37,
    })
    expect(error.extra.retryAfterSeconds).toBe(37)
    recordWritingAttemptMock.mockRejectedValue(error)
    renderAt('/learn/characters/writing/11')

    await submitIncomplete(user)
    expect(await screen.findByText(/thử lại sau 37 giây/)).toBeVisible()
    expect(screen.queryByRole('button', { name: 'Gửi lại' })).not.toBeInTheDocument()
    expect(recordWritingAttemptMock).toHaveBeenCalledOnce()
  })

  it('FR010_leavingTheWritingRouteDoesNotPostAnAttempt', async () => {
    const user = userEvent.setup()
    renderAt('/learn/characters/writing/11')

    await screen.findByTestId('hanzi-canvas')
    await user.click(screen.getByRole('link', { name: 'Danh sách chữ' }))
    expect(await screen.findByRole('link', { name: /好/ })).toBeVisible()
    expect(recordWritingAttemptMock).not.toHaveBeenCalled()
  })

  it('FR013_modeLockedReloadsCharacterAndReturnsToStroke', async () => {
    const user = userEvent.setup()
    recordWritingAttemptMock.mockRejectedValue(new ApiError({
      errorCode: 'MODE_LOCKED',
      message: 'locked',
      status: 403,
    }))
    renderAt('/learn/characters/writing/11')

    await submitIncomplete(user)
    await waitFor(() => expect(getCharacterMock).toHaveBeenCalledTimes(2))
    expect(screen.getByRole('button', { name: /^Viết theo nét/ })).toHaveAttribute('aria-pressed', 'true')
  })

  async function readyStrokeQuiz() {
    await screen.findByTestId('hanzi-canvas')
    act(() => canvasReadyMock.callback?.())
    await waitFor(() => expect(canvasReadyMock.quiz).toHaveBeenCalled())
  }

  async function completeQuiz(quizIndex = -1) {
    const quiz = canvasReadyMock.quiz.mock.calls.at(quizIndex)?.[0]
    act(() => quiz.onComplete({ character: '好', totalMistakes: 0 }))
    await waitFor(() => expect(recordWritingAttemptMock).toHaveBeenCalled())
  }

  it('FR032_recordedStrokeCanContinueIntoRecallAndPostsTheRecallPayload', async () => {
    const user = userEvent.setup()
    const recallCharacter = {
      ...detail,
      writing: { dictationAvailable: true, recallUnlocked: true, strokeAvailable: true },
    }
    getCharacterMock.mockResolvedValue(recallCharacter)
    recordWritingAttemptMock.mockResolvedValue({
      attemptId: 1, counted: true, recorded: true, recallUnlocked: true,
    })
    renderAt('/learn/characters/writing/11')
    await readyStrokeQuiz()
    await completeQuiz()
    expect(await screen.findByText('Đã ghi nhận kết quả.')).toBeVisible()

    const recallButton = screen.getByRole('button', { name: /Nhớ rồi viết/ })
    expect(recallButton).toBeEnabled()
    await user.click(recallButton)
    expect(recallButton).toHaveAttribute('aria-pressed', 'true')
    expect(screen.queryByText('Đang gửi kết quả…')).not.toBeInTheDocument()
    expect(screen.getByText('Xem chữ mẫu trong 5 giây…')).toBeVisible()

    await act(async () => { await new Promise((resolve) => window.setTimeout(resolve, 5100)) })
    await waitFor(() => expect(canvasReadyMock.quiz).toHaveBeenCalledTimes(2))
    await completeQuiz(1)
    expect(recordWritingAttemptMock).toHaveBeenLastCalledWith(expect.objectContaining({ mode: 'RECALL' }))
  }, 15_000)

  it('FR031_recordedAttemptCanRestartWithFreshQuizAndOneNewPost', async () => {
    const user = userEvent.setup()
    recordWritingAttemptMock.mockResolvedValue({
      attemptId: 1, counted: true, recorded: true, recallUnlocked: true,
    })
    renderAt('/learn/characters/writing/11')
    await readyStrokeQuiz()
    await completeQuiz()
    expect(screen.queryByText('Đang gửi kết quả…')).not.toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'Viết lại' }))
    await waitFor(() => expect(canvasReadyMock.quiz).toHaveBeenCalledTimes(2))
    expect(screen.getByText('Số nét sai: 0')).toBeVisible()
    await completeQuiz(1)
    expect(recordWritingAttemptMock).toHaveBeenCalledTimes(2)
    expect(recordWritingAttemptMock).toHaveBeenLastCalledWith(expect.objectContaining({
      completed: true,
      mode: 'STROKE',
      strokeMisses: 0,
    }))
  })

  it('FR031_rejectedAttemptRestartsWhenTheSelectedModeIsClickedAgain', async () => {
    const user = userEvent.setup()
    recordWritingAttemptMock.mockRejectedValueOnce(new ApiError({
      errorCode: 'VALIDATION_FAILED', message: 'invalid', status: 422,
    })).mockResolvedValueOnce({ attemptId: 2, counted: true, recorded: true, recallUnlocked: false })
    renderAt('/learn/characters/writing/11')
    await readyStrokeQuiz()
    await completeQuiz()
    expect(await screen.findByRole('alert')).toHaveTextContent('invalid')
    expect(screen.queryByText('Đang gửi kết quả…')).not.toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: /^Viết theo nét/ }))
    await waitFor(() => expect(canvasReadyMock.quiz).toHaveBeenCalledTimes(2))
    const stopButton = screen.getByRole('button', { name: 'Dừng' })
    expect(stopButton).toBeEnabled()
    await user.click(stopButton)
    await waitFor(() => expect(recordWritingAttemptMock).toHaveBeenCalledTimes(2))
  })

  it('FR031_rejectedAttemptCanRestartFromTheWriteAgainButton', async () => {
    const user = userEvent.setup()
    recordWritingAttemptMock.mockRejectedValueOnce(new ApiError({
      errorCode: 'VALIDATION_FAILED', message: 'invalid', status: 422,
    })).mockResolvedValueOnce({ attemptId: 2, counted: true, recorded: true, recallUnlocked: false })
    renderAt('/learn/characters/writing/11')
    await readyStrokeQuiz()
    await completeQuiz()
    expect(await screen.findByRole('alert')).toHaveTextContent('invalid')

    await user.click(screen.getByRole('button', { name: 'Viết lại' }))
    await waitFor(() => expect(canvasReadyMock.quiz).toHaveBeenCalledTimes(2))
    expect(screen.getByRole('button', { name: 'Dừng' })).toBeEnabled()
    await user.click(screen.getByRole('button', { name: 'Dừng' }))
    await waitFor(() => expect(recordWritingAttemptMock).toHaveBeenCalledTimes(2))
  })

  it('FR030_dataErrorPersistsAcrossModeChangesWithoutLoadingOrPosting', async () => {
    const user = userEvent.setup()
    getCharacterMock.mockResolvedValue({
      ...detail,
      writing: { dictationAvailable: true, recallUnlocked: true, strokeAvailable: true },
    })
    renderAt('/learn/characters/writing/11')
    await readyStrokeQuiz()
    act(() => canvasReadyMock.dataError?.())
    expect(await screen.findByRole('alert')).toHaveTextContent('Chữ này chưa có dữ liệu nét')
    const quizCallsBeforeModeChange = canvasReadyMock.quiz.mock.calls.length

    await user.click(screen.getByRole('button', { name: /Nhớ rồi viết/ }))
    expect(await screen.findByRole('alert')).toHaveTextContent('Chữ này chưa có dữ liệu nét')
    expect(screen.queryByText('Đang tải dữ liệu nét…')).not.toBeInTheDocument()
    expect(canvasReadyMock.quiz).toHaveBeenCalledTimes(quizCallsBeforeModeChange)
    expect(recordWritingAttemptMock).not.toHaveBeenCalled()
  })

  it('FR033_recordedDictationShowsTheAnswerAndAllowsReplayWithoutPostingAgain', async () => {
    const user = userEvent.setup()
    const playAudio = vi.spyOn(HTMLMediaElement.prototype, 'play').mockResolvedValue()
    getCharacterMock.mockResolvedValue({
      ...detail,
      audioUrl: '/audio/hao.mp3',
      hasAudio: true,
      writing: { dictationAvailable: true, recallUnlocked: false, strokeAvailable: true },
    })
    recordWritingAttemptMock.mockResolvedValue({
      attemptId: 1, counted: true, recorded: true, recallUnlocked: false,
    })
    renderAt('/learn/characters/writing/11')
    await readyStrokeQuiz()
    await user.click(screen.getByRole('button', { name: /Nghe chép/ }))
    await waitFor(() => expect(canvasReadyMock.quiz).toHaveBeenCalledTimes(2))
    await completeQuiz(1)

    expect(await screen.findByText('Chữ đúng: 好')).toBeVisible()
    const listenButton = screen.getByRole('button', { name: 'Nghe từ' })
    expect(listenButton).toBeEnabled()
    await user.click(listenButton)
    expect(playAudio).toHaveBeenCalledOnce()
    expect(recordWritingAttemptMock).toHaveBeenCalledOnce()
  })
})
