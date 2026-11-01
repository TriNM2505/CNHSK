import { act, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { finishSession, findActiveSession, getSession } from '@/features/practice/api'
import type { FinishResult, SessionView } from '@/features/practice'
import { ApiError, parseApiError } from '@/shared/api'
import { AuthContext, type AuthContextValue } from '@/shared/auth'

import { getCharacter, getCharacters, recordWritingAttempt, startWritingChallenge } from '../api/characters'
import type { CharacterDetail, WritingAttemptRequest, WritingAttemptResult } from '../types'
import Scr014CharacterWritingPage from './Scr014CharacterWritingPage'

const canvasReadyMock = vi.hoisted(() => ({
  callback: undefined as (() => void) | undefined,
  dataError: undefined as (() => void) | undefined,
  mounts: [] as string[],
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
      canvasReadyMock.mounts.push(props.character)
    }, [props.character, props.onDataError, props.onReady])
    return React.createElement('div', { 'aria-label': 'Khung viết chữ', 'data-testid': 'hanzi-canvas' })
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
const getSessionMock = vi.mocked(getSession)
const finishSessionMock = vi.mocked(finishSession)

const testAuth: AuthContextValue = {
  error: null,
  reloadCurrentUser: vi.fn().mockResolvedValue(undefined),
  setAuthenticatedUser: vi.fn(),
  signOut: vi.fn().mockResolvedValue(undefined),
  status: 'authenticated',
  user: {
    displayName: 'Learner',
    email: 'learner@example.invalid',
    emailVerified: true,
    id: 7,
    roles: ['LEARNER'],
  },
}

const originalDateNow = Date.now
const originalPerformanceNow = performance.now

afterEach(() => {
  vi.useRealTimers()
  Object.defineProperty(Date, 'now', { configurable: true, value: originalDateNow, writable: true })
  Object.defineProperty(performance, 'now', { configurable: true, value: originalPerformanceNow, writable: true })
})

function LocationProbe() {
  const location = useLocation()
  return <output data-testid="location">{`${location.pathname}${location.search}`}</output>
}

function renderAt(path: string) {
  return render(
    <AuthContext.Provider value={testAuth}>
      <MemoryRouter initialEntries={[path]}>
        <Routes>
          <Route element={<Scr014CharacterWritingPage />} path="/learn/characters/writing" />
          <Route element={<Scr014CharacterWritingPage />} path="/learn/characters/writing/challenge" />
          <Route element={<Scr014CharacterWritingPage />} path="/learn/characters/writing/:lexemeId" />
        </Routes>
        <LocationProbe />
      </MemoryRouter>
    </AuthContext.Provider>,
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

const challengeGlyphs = ['好', '你', '我', '他', '她', '它', '们', '大', '小', '中']

function challengeSession(options: { servedAt?: string; sessionId?: number; status?: SessionView['status'] } = {}): SessionView {
  const servedAt = options.servedAt ?? new Date().toISOString()
  return {
    answered: [],
    expiresAt: new Date(Date.parse(servedAt) + 210_000).toISOString(),
    feedback: 'DEFERRED',
    items: challengeGlyphs.map((text, index) => ({
      no: index + 1,
      prompt: { meaning: `meaning-${index + 1}`, pinyin: `pinyin-${index + 1}`, strokeCount: 1, text },
      rules: { maxReplays: 0, minDurationMs: 0, hasAudio: false },
      skill: 'WRITING',
      type: 'WRITING_RESULT',
    })),
    meta: { maxMistakes: 3, timeLimitSeconds: 200 },
    servedAt,
    sessionId: options.sessionId ?? 77,
    source: 'WRITING_CHALLENGE',
    status: options.status ?? 'IN_PROGRESS',
  }
}

function challengeFinishResult(extra: Record<string, unknown> = {}): FinishResult {
  return {
    extra: { completedChars: 10, failedChars: 0, finishedReason: 'ALL_DONE', incompleteChars: 0, total: 10, ...extra },
    items: [],
    maxScore: null,
    percent: null,
    score: null,
    sessionId: 77,
    status: 'SUBMITTED',
    suspicious: false,
  }
}

function submittedChallenge(answers: Array<string | null>, sessionId = 77): SessionView {
  const base = challengeSession({ sessionId, status: 'SUBMITTED' })
  return { ...base, answered: answers.map((answer, index) => ({ answer, counted: false, no: index + 1 })) }
}

function currentQuizOptions() {
  return canvasReadyMock.quiz.mock.calls.at(-1)?.[0] as {
    onComplete: (summary: { character: string; totalMistakes: number }) => void
    onMistake: (stroke: { totalMistakes: number }) => void
    showHintAfterMisses: false
  }
}

async function readyStrokeCanvas() {
  await screen.findByTestId('hanzi-canvas')
  act(() => canvasReadyMock.callback?.())
  await waitFor(() => expect(canvasReadyMock.quiz).toHaveBeenCalled())
}

async function openChallenge(session = challengeSession()) {
  findActiveMock.mockResolvedValue(null)
  startChallengeMock.mockResolvedValue(session)
  finishSessionMock.mockResolvedValue(challengeFinishResult())
  getSessionMock.mockResolvedValue(session)
  renderAt('/learn/characters/writing/challenge')
  const user = userEvent.setup()
  await user.click(await screen.findByRole('button', { name: /Bắt đầu thử thách/ }))
  await screen.findByText('pinyin-1')
  await screen.findByTestId('hanzi-canvas')
  act(() => canvasReadyMock.callback?.())
  await waitFor(() => expect(canvasReadyMock.quiz).toHaveBeenCalled())
  return user
}

async function readyNextChallengeItem(index: number, previousQuizCount: number) {
  await screen.findByText(`pinyin-${index}`)
  await screen.findByTestId('hanzi-canvas')
  act(() => canvasReadyMock.callback?.())
  await waitFor(() => expect(canvasReadyMock.quiz).toHaveBeenCalledTimes(previousQuizCount + 1))
}

describe('Scr014CharacterWritingPage — thử thách FR-034', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    sessionStorage.clear()
    canvasReadyMock.callback = undefined
    canvasReadyMock.dataError = undefined
    canvasReadyMock.quiz.mockReset()
    findActiveMock.mockResolvedValue(null)
    getSessionMock.mockResolvedValue(challengeSession())
    finishSessionMock.mockResolvedValue(challengeFinishResult())
  })

  it('FR034_usesTenServerItemsAndHidesCharacterAndHint', async () => {
    await openChallenge()
    expect(startChallengeMock).toHaveBeenCalledOnce()
    expect(screen.getByText('pinyin-1')).toBeVisible()
    expect(screen.queryByText('好')).not.toBeInTheDocument()
    expect(screen.getByTestId('hanzi-canvas')).toHaveAttribute('aria-label', 'Khung viết chữ')
    expect(currentQuizOptions().showHintAfterMisses).toBe(false)
    expect(screen.getByText('1/10')).toBeVisible()
  })

  it('FR034_restoresOnlyMatchingSessionProgressFromSessionStorage', async () => {
    const active = challengeSession()
    findActiveMock.mockResolvedValue(active)
    getSessionMock.mockResolvedValue(active)
    sessionStorage.setItem('cnhsk:hanzi:challenge:7:88', JSON.stringify({
      answers: [{ answer: 'COMPLETED', clientMeta: { accuracy: 1, strokeMisses: 0 }, durationMs: 250, no: 1 }],
      sessionId: 88, userId: 7, version: 1,
    }))
    sessionStorage.setItem('cnhsk:hanzi:challenge:7:77', JSON.stringify({
      answers: [{ answer: 'COMPLETED', clientMeta: { accuracy: 1, strokeMisses: 0 }, durationMs: 250, no: 1 }],
      sessionId: 77, userId: 7, version: 1,
    }))
    renderAt('/learn/characters/writing/challenge')

    expect(await screen.findByText('pinyin-2')).toBeVisible()
    expect(startChallengeMock).not.toHaveBeenCalled()
    expect(findActiveMock).toHaveBeenCalledOnce()
  })

  it('FR034_marksACompletionBelowTheMinimumDurationAsIncompleteAndKeepsTheTurnAlive', async () => {
    vi.spyOn(performance, 'now').mockReturnValue(1_000)
    await openChallenge()
    act(() => currentQuizOptions().onComplete({ character: '好', totalMistakes: 0 }))
    await screen.findByText('pinyin-2')
    const stored = sessionStorage.getItem('cnhsk:hanzi:challenge:7:77')
    expect(stored).toContain('INCOMPLETE')
    expect(stored).not.toContain('好')
    expect(finishSessionMock).not.toHaveBeenCalled()
  })

  it('FR034_keepsACompletionAtTheMinimumDuration', async () => {
    let clock = 0
    vi.spyOn(performance, 'now').mockImplementation(() => clock)
    await openChallenge()
    clock = 200
    act(() => currentQuizOptions().onComplete({ character: '好', totalMistakes: 0 }))
    await screen.findByText('pinyin-2')
    expect(sessionStorage.getItem('cnhsk:hanzi:challenge:7:77')).toContain('COMPLETED')
  })

  it('FR034_stopsAfterTheThirdFailedCharacterWithoutStartingAFourth', async () => {
    await openChallenge()
    finishSessionMock.mockResolvedValue(challengeFinishResult({
      completedChars: 0, failedChars: 3, finishedReason: 'MAX_MISTAKES', incompleteChars: 7,
    }))
    for (let failed = 1; failed <= 3; failed += 1) {
      act(() => currentQuizOptions().onMistake({ totalMistakes: 3 }))
      if (failed < 3) await readyNextChallengeItem(failed + 1, failed)
    }
    await waitFor(() => expect(finishSessionMock).toHaveBeenCalledOnce())
    const request = finishSessionMock.mock.calls[0]![1]
    expect(request?.answers?.map((answer) => answer.answer)).toEqual(['FAILED', 'FAILED', 'FAILED'])
    expect(canvasReadyMock.quiz).toHaveBeenCalledTimes(3)
    expect(await screen.findByText('Kết thúc do trượt 3 chữ.')).toBeVisible()
  })

  it('FR034_finishesAllTenItemsAndSendsSnapshotNosAndClientEvidence', async () => {
    let clock = 0
    vi.spyOn(performance, 'now').mockImplementation(() => clock)
    await openChallenge()
    for (let index = 1; index <= 10; index += 1) {
      clock += 250
      act(() => currentQuizOptions().onComplete({ character: challengeGlyphs[index - 1]!, totalMistakes: 0 }))
      if (index < 10) await readyNextChallengeItem(index + 1, index)
    }
    await waitFor(() => expect(finishSessionMock).toHaveBeenCalledOnce())
    const request = finishSessionMock.mock.calls[0]![1]
    expect(request?.answers).toHaveLength(10)
    expect(request?.answers?.map((answer) => answer.no)).toEqual([1, 2, 3, 4, 5, 6, 7, 8, 9, 10])
    expect(request?.answers?.every((answer) => answer.replayCount === 0 && !answer.skipped && !answer.audioFailed
      && typeof answer.clientMeta?.accuracy === 'number' && typeof answer.clientMeta?.strokeMisses === 'number')).toBe(true)
    expect(await screen.findByText('Bạn đã hoàn thành thử thách.')).toBeVisible()
    expect(sessionStorage.getItem('cnhsk:hanzi:challenge:7:77')).toBeNull()
  })

  it('FR034_restartsQuizForEachRepeatedGlyphAndAcceptsSecondGlyphCompletion', async () => {
    let clock = 0
    vi.spyOn(performance, 'now').mockImplementation(() => clock)
    canvasReadyMock.mounts.length = 0
    const repeatedGlyphSession = challengeSession()
    repeatedGlyphSession.items = repeatedGlyphSession.items.map((item) => ({
      ...item,
      prompt: { ...item.prompt, text: '一' },
    }))
    await openChallenge(repeatedGlyphSession)
    expect(canvasReadyMock.quiz).toHaveBeenCalledOnce()
    expect(canvasReadyMock.mounts).toEqual(['一'])

    for (let index = 1; index <= 10; index += 1) {
      clock += 250
      act(() => currentQuizOptions().onComplete({ character: '一', totalMistakes: 0 }))
      if (index < 10) {
        await screen.findByText(`pinyin-${index + 1}`)
        await waitFor(() => expect(canvasReadyMock.mounts).toHaveLength(index + 1))
        act(() => canvasReadyMock.callback?.())
        await waitFor(() => expect(canvasReadyMock.quiz).toHaveBeenCalledTimes(index + 1))
      }
      const savedAnswers = JSON.parse(sessionStorage.getItem('cnhsk:hanzi:challenge:7:77') ?? '{}').answers
      expect(savedAnswers).toEqual(expect.arrayContaining([expect.objectContaining({ answer: 'COMPLETED', no: index })]))
    }
    await waitFor(() => expect(finishSessionMock).toHaveBeenCalledOnce())
    expect(canvasReadyMock.quiz).toHaveBeenCalledTimes(10)
    expect(finishSessionMock.mock.calls[0]![1]?.answers).toHaveLength(10)
  })

  it('FR034_doesNotFinishAgainAfter503RetryReconcilesSubmittedSessionPastDeadline', async () => {
    await openChallenge()
    finishSessionMock.mockRejectedValueOnce(new ApiError({
      errorCode: 'INTERNAL_ERROR', message: 'unavailable', status: 503,
    }))
    for (let failed = 1; failed <= 3; failed += 1) {
      act(() => currentQuizOptions().onMistake({ totalMistakes: 3 }))
      if (failed < 3) await readyNextChallengeItem(failed + 1, failed)
    }
    expect(await screen.findByRole('button', { name: /Kiểm tra \/ gửi lại/ })).toBeEnabled()
    expect(finishSessionMock).toHaveBeenCalledOnce()

    getSessionMock.mockResolvedValue(submittedChallenge(['FAILED', 'FAILED', 'FAILED', null, null, null, null, null, null, null]))
    await userEvent.setup().click(screen.getByRole('button', { name: /Kiểm tra \/ gửi lại/ }))
    await waitFor(() => expect(document.getElementById('challenge-summary-title')).toBeVisible())
    expect(finishSessionMock).toHaveBeenCalledOnce()

    const now = Date.now()
    vi.spyOn(Date, 'now').mockReturnValue(now + 250_000)
    await new Promise((resolve) => setTimeout(resolve, 350))
    expect(finishSessionMock).toHaveBeenCalledOnce()
  })

  it('FR034_doesNotFinishAgainAfter409ReconciliationPastDeadline', async () => {
    await openChallenge()
    getSessionMock.mockResolvedValue(submittedChallenge(['FAILED', 'FAILED', 'FAILED', null, null, null, null, null, null, null]))
    finishSessionMock.mockRejectedValueOnce(new ApiError({
      errorCode: 'SESSION_ALREADY_SUBMITTED', message: 'submitted', status: 409,
    }))
    for (let failed = 1; failed <= 3; failed += 1) {
      act(() => currentQuizOptions().onMistake({ totalMistakes: 3 }))
      if (failed < 3) await readyNextChallengeItem(failed + 1, failed)
    }
    await waitFor(() => expect(document.getElementById('challenge-summary-title')).toBeVisible())
    expect(finishSessionMock).toHaveBeenCalledOnce()

    const now = Date.now()
    vi.spyOn(Date, 'now').mockReturnValue(now + 250_000)
    await new Promise((resolve) => setTimeout(resolve, 350))
    expect(finishSessionMock).toHaveBeenCalledOnce()
  })

  it('FR034_showsOnlyPendingStateAfterTheTenthCharacterWhileFinishIsPending', async () => {
    let clock = 0
    vi.spyOn(performance, 'now').mockImplementation(() => clock)
    await openChallenge()
    finishSessionMock.mockImplementation(() => new Promise(() => undefined))
    for (let index = 1; index <= 10; index += 1) {
      clock += 250
      act(() => currentQuizOptions().onComplete({ character: challengeGlyphs[index - 1]!, totalMistakes: 0 }))
      if (index < 10) await readyNextChallengeItem(index + 1, index)
    }

    expect(await screen.findByText('Đang gửi kết quả…')).toBeVisible()
    expect(screen.queryByText('Không thể tải câu hỏi thử thách.')).not.toBeInTheDocument()
    expect(screen.getByText('10/10')).toBeVisible()
    expect(screen.queryByText('1/10')).not.toBeInTheDocument()
    expect(finishSessionMock).toHaveBeenCalledOnce()
  })

  it.each([
    ['400', 'IMPOSSIBLE_DURATION', 400],
    ['422', 'VALIDATION_FAILED', 422],
  ])('FR034_showsTerminalFinishErrorWithoutRetryForHTTP%s', async (_status, errorCode, status) => {
    let clock = 0
    vi.spyOn(performance, 'now').mockImplementation(() => clock)
    await openChallenge()
    finishSessionMock.mockRejectedValueOnce(new ApiError({
      errorCode, message: 'finish rejected', status,
    }))
    for (let index = 1; index <= 10; index += 1) {
      clock += 250
      act(() => currentQuizOptions().onComplete({ character: challengeGlyphs[index - 1]!, totalMistakes: 0 }))
      if (index < 10) await readyNextChallengeItem(index + 1, index)
    }

    expect(await screen.findByRole('alert')).toHaveTextContent('finish rejected')
    expect(screen.getByRole('link', { name: 'Danh sách chữ' })).toBeVisible()
    expect(screen.queryByRole('button', { name: /Kiểm tra \/ gửi lại/ })).not.toBeInTheDocument()
    expect(screen.queryByText('Không thể tải câu hỏi thử thách.')).not.toBeInTheDocument()
    expect(finishSessionMock).toHaveBeenCalledOnce()
  })

  it('FR034_startsDataErrorCharacterWithZeroDurationAfterPreviousCharacter', async () => {
    let clock = 0
    vi.spyOn(performance, 'now').mockImplementation(() => clock)
    await openChallenge()
    clock = 250
    act(() => currentQuizOptions().onComplete({ character: challengeGlyphs[0]!, totalMistakes: 0 }))
    await screen.findByText('pinyin-2')
    await screen.findByTestId('hanzi-canvas')
    expect(canvasReadyMock.quiz).toHaveBeenCalledOnce()
    clock = 1_000
    act(() => canvasReadyMock.dataError?.())

    const answers = JSON.parse(sessionStorage.getItem('cnhsk:hanzi:challenge:7:77') ?? '{}').answers as Array<{ durationMs: number; no: number }>
    expect(answers.find((answer) => answer.no === 2)?.durationMs).toBe(0)
  })

  it('FR034_countsThreeMistakesPerCharacterAndKeepsIncompleteDataErrorsOutOfTheFailureCount', async () => {
    await openChallenge()
    finishSessionMock.mockResolvedValue(challengeFinishResult({
      completedChars: 0, failedChars: 0, finishedReason: 'TIME_UP', incompleteChars: 10,
    }))
    for (let index = 1; index <= 10; index += 1) {
      act(() => canvasReadyMock.dataError?.())
      if (index < 10) await readyNextChallengeItem(index + 1, index)
    }
    await waitFor(() => expect(finishSessionMock).toHaveBeenCalledOnce())
    const answers = finishSessionMock.mock.calls[0]![1]?.answers ?? []
    expect(answers).toHaveLength(10)
    expect(answers.every((answer) => answer.answer === 'INCOMPLETE')).toBe(true)
    expect(await screen.findByText('Thời gian thử thách đã hết.')).toBeVisible()
  })

  it('FR034_autoFinishesOnceWhenReloadRestoresAnActiveSessionPastTheDeadline', async () => {
    const expiredForClient = challengeSession({ servedAt: new Date(Date.now() - 200_500).toISOString() })
    findActiveMock.mockResolvedValue(expiredForClient)
    getSessionMock.mockResolvedValue(expiredForClient)
    renderAt('/learn/characters/writing/challenge')
    await waitFor(() => expect(finishSessionMock).toHaveBeenCalledOnce())
    const answers = finishSessionMock.mock.calls[0]![1]?.answers ?? []
    expect(answers).toHaveLength(1)
    expect(answers[0]).toMatchObject({ answer: 'INCOMPLETE', no: 1 })
  })

  it('FR034_doesNotPostAgainWhenTheDeadlinePassesWhileFinishIsPending', async () => {
    vi.useFakeTimers()
    let resolveFinish: ((result: FinishResult) => void) | undefined
    finishSessionMock.mockImplementation(() => new Promise((resolve) => { resolveFinish = resolve }))
    const expiredForClient = challengeSession({ servedAt: new Date(Date.now() - 200_500).toISOString() })
    findActiveMock.mockResolvedValue(expiredForClient)
    getSessionMock.mockResolvedValue(expiredForClient)
    renderAt('/learn/characters/writing/challenge')
    await act(async () => { await Promise.resolve(); await Promise.resolve() })
    expect(finishSessionMock).toHaveBeenCalledOnce()
    await act(async () => { await vi.advanceTimersByTimeAsync(2_000) })
    expect(finishSessionMock).toHaveBeenCalledOnce()
    await act(async () => resolveFinish?.(challengeFinishResult()))
  })

  it('FR034_ignoresQuizCallbackAfterUnmount', async () => {
    startChallengeMock.mockResolvedValue(challengeSession())
    findActiveMock.mockResolvedValue(null)
    const view = renderAt('/learn/characters/writing/challenge')
    const user = userEvent.setup()
    await user.click(await screen.findByRole('button'))
    await screen.findByText('pinyin-1')
    await screen.findByTestId('hanzi-canvas')
    act(() => canvasReadyMock.callback?.())
    await waitFor(() => expect(canvasReadyMock.quiz).toHaveBeenCalled())
    const staleComplete = currentQuizOptions().onComplete

    view.unmount()
    act(() => staleComplete({ character: 'å¥½', totalMistakes: 0 }))

    expect(finishSessionMock).not.toHaveBeenCalled()
    expect(JSON.parse(sessionStorage.getItem('cnhsk:hanzi:challenge:7:77') ?? '{}').answers).toEqual([])
  })

  it('FR034_reconcilesUnknownFinishFromServerWithoutPostingAgain', async () => {
    const submitted = submittedChallenge(['COMPLETED', 'FAILED', null, null, null, null, null, null, null, null])
    findActiveMock.mockResolvedValue(null)
    getSessionMock.mockResolvedValue(submitted)
    sessionStorage.setItem('cnhsk:hanzi:challenge:7:77', JSON.stringify({
      answers: [{ answer: 'COMPLETED', clientMeta: { accuracy: 1, strokeMisses: 0 }, durationMs: 300, no: 1 }],
      finishPayload: [{ answer: 'COMPLETED', clientMeta: { accuracy: 1, strokeMisses: 0 }, durationMs: 300, no: 1 }],
      sessionId: 77,
      userId: 7,
      version: 1,
    }))
    renderAt('/learn/characters/writing/challenge')
    expect(await screen.findByText('Thời gian thử thách đã hết.')).toBeVisible()
    expect(screen.getByText('8')).toBeVisible()
    expect(finishSessionMock).not.toHaveBeenCalled()
    expect(sessionStorage.getItem('cnhsk:hanzi:challenge:7:77')).toBeNull()
  })

  it('FR034_rebuildsSubmittedSummaryWhenSyncHasNoFinishResult', async () => {
    const submitted = submittedChallenge(['COMPLETED', 'FAILED', null, null, null, null, null, null, null, null])
    findActiveMock.mockResolvedValue(submitted)
    getSessionMock.mockResolvedValue(submitted)
    sessionStorage.setItem('cnhsk:hanzi:challenge:7:77', JSON.stringify({
      answers: [], sessionId: 77, userId: 7, version: 1,
    }))
    renderAt('/learn/characters/writing/challenge')

    await waitFor(() => expect(document.getElementById('challenge-summary-title')).toBeVisible())
    expect(screen.getAllByText('1')).toHaveLength(2)
    expect(screen.getByText('8')).toBeVisible()
    expect(finishSessionMock).not.toHaveBeenCalled()
    expect(sessionStorage.getItem('cnhsk:hanzi:challenge:7:77')).toBeNull()
  })

  it('FR034_reconciles409FromServerWithoutPostingAgain', async () => {
    const submitted = submittedChallenge(['FAILED', 'FAILED', 'FAILED', null, null, null, null, null, null, null])
    await openChallenge()
    getSessionMock.mockResolvedValue(submitted)
    finishSessionMock.mockRejectedValue(new ApiError({
      errorCode: 'SESSION_ALREADY_SUBMITTED', message: 'submitted', status: 409,
    }))
    for (let failed = 1; failed <= 3; failed += 1) {
      act(() => currentQuizOptions().onMistake({ totalMistakes: 3 }))
      if (failed < 3) await readyNextChallengeItem(failed + 1, failed)
    }

    await waitFor(() => expect(document.getElementById('challenge-summary-title')).toBeVisible())
    expect(finishSessionMock).toHaveBeenCalledOnce()
    expect(getSessionMock).toHaveBeenCalledWith(77)
    expect(sessionStorage.getItem('cnhsk:hanzi:challenge:7:77')).toBeNull()
  })

  it('FR034_requiresManualRetryAfterUnknownFinishResponse', async () => {
    await openChallenge()
    finishSessionMock.mockRejectedValueOnce(new ApiError({
      errorCode: 'INTERNAL_ERROR', message: 'unavailable', status: 503,
    })).mockResolvedValueOnce(challengeFinishResult())
    getSessionMock.mockResolvedValue(challengeSession())
    for (let failed = 1; failed <= 3; failed += 1) {
      act(() => currentQuizOptions().onMistake({ totalMistakes: 3 }))
      if (failed < 3) await readyNextChallengeItem(failed + 1, failed)
    }

    expect(await screen.findByRole('button')).toBeEnabled()
    expect(finishSessionMock).toHaveBeenCalledOnce()
    const originalRequest = finishSessionMock.mock.calls[0]![1]
    const user = userEvent.setup()
    await user.click(screen.getByRole('button'))
    await waitFor(() => expect(finishSessionMock).toHaveBeenCalledTimes(2))
    expect(finishSessionMock.mock.calls[1]![1]).toEqual(originalRequest)
  })

  it('FR034_handlesTimeoutFinishWithSessionExpiredAndClearsSavedPayload', async () => {
    const expiredForClient = challengeSession({ servedAt: new Date(Date.now() - 200_500).toISOString() })
    findActiveMock.mockResolvedValue(expiredForClient)
    getSessionMock.mockResolvedValue(expiredForClient)
    finishSessionMock.mockRejectedValue(new ApiError({ errorCode: 'SESSION_EXPIRED', message: 'expired', status: 422 }))
    renderAt('/learn/characters/writing/challenge')
    expect(await screen.findByText('Đã hết hạn')).toBeVisible()
    expect(sessionStorage.getItem('cnhsk:hanzi:challenge:7:77')).toBeNull()
  })

  it('FR034_restoresExpiredSessionAsTerminalInsteadOfShowingTheIntro', async () => {
    const expired = challengeSession({ status: 'EXPIRED' })
    findActiveMock.mockResolvedValue(expired)
    getSessionMock.mockResolvedValue(expired)
    sessionStorage.setItem('cnhsk:hanzi:challenge:7:77', JSON.stringify({
      answers: [], sessionId: 77, userId: 7, version: 1,
    }))
    renderAt('/learn/characters/writing/challenge')

    await waitFor(() => expect(document.querySelector('h2')?.textContent).toBe('\u0110\u00e3 h\u1ebft h\u1ea1n'))
    expect(screen.queryByRole('button')).not.toBeInTheDocument()
    expect(sessionStorage.getItem('cnhsk:hanzi:challenge:7:77')).toBeNull()
    expect(finishSessionMock).not.toHaveBeenCalled()
  })

  it('FR034_keepsRateLimitedPayloadUntilManualRetry', async () => {
    const clock = 0
    vi.spyOn(performance, 'now').mockImplementation(() => clock)
    finishSessionMock.mockRejectedValueOnce(new ApiError({
      errorCode: 'RATE_LIMITED', extra: { retryAfterSeconds: 15 }, message: 'limited', status: 429,
    })).mockResolvedValueOnce(challengeFinishResult())
    await openChallenge()
    for (let failed = 1; failed <= 3; failed += 1) {
      act(() => currentQuizOptions().onMistake({ totalMistakes: 3 }))
      if (failed < 3) await readyNextChallengeItem(failed + 1, failed)
    }
    expect(finishSessionMock).toHaveBeenCalledOnce()
    const saved = sessionStorage.getItem('cnhsk:hanzi:challenge:7:77')
    expect(saved).toContain('finishPayload')
    getSessionMock.mockResolvedValue(challengeSession())
    const user = userEvent.setup()
    await user.click(await screen.findByRole('button', { name: /Kiểm tra \/ gửi lại/ }))
    await waitFor(() => expect(finishSessionMock).toHaveBeenCalledTimes(2))
    expect(finishSessionMock.mock.calls[1]![1]).toEqual(finishSessionMock.mock.calls[0]![1])
  })

  it('FR034_backToCharacterListDoesNotAbandonOrFinishTheActiveSession', async () => {
    const active = challengeSession()
    findActiveMock.mockResolvedValue(active)
    getSessionMock.mockResolvedValue(active)
    renderAt('/learn/characters/writing/challenge')
    const user = userEvent.setup()
    await screen.findByText('pinyin-1')
    await user.click(screen.getByRole('link', { name: 'Danh sách chữ' }))
    expect(await screen.findByRole('link', { name: /好/ })).toBeVisible()
    const { abandonSession } = await import('@/features/practice/api')
    expect(vi.mocked(abandonSession)).not.toHaveBeenCalled()
    expect(finishSessionMock).not.toHaveBeenCalled()
  })
})

describe('Scr014CharacterWritingPage — hồi quy restart T-08 trong T-09', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    canvasReadyMock.callback = undefined
    canvasReadyMock.dataError = undefined
    canvasReadyMock.quiz.mockReset()
  })

  it('FR034_clickingTheSelectedModeDuringWritingDoesNotClearOrRestartTheQuiz', async () => {
    getCharacterMock.mockResolvedValue(detail)
    renderAt('/learn/characters/writing/11')
    await readyStrokeCanvas()
    const user = userEvent.setup()
    await user.click(screen.getByRole('button', { name: /^Viết theo nét/ }))
    expect(canvasReadyMock.quiz).toHaveBeenCalledOnce()
    expect(screen.getByRole('button', { name: 'Dừng' })).toBeEnabled()
    expect(recordWritingAttemptMock).not.toHaveBeenCalled()
  })

  it('FR034_changingModeAfterRestartKeyChangeStartsExactlyOneNewQuiz', async () => {
    getCharacterMock.mockResolvedValue({
      ...detail,
      audioUrl: '/audio/hao.mp3',
      hasAudio: true,
      writing: { dictationAvailable: true, recallUnlocked: true, strokeAvailable: true },
    })
    recordWritingAttemptMock.mockResolvedValue({ attemptId: 1, counted: true, recorded: true, recallUnlocked: true })
    renderAt('/learn/characters/writing/11')
    await readyStrokeCanvas()
    act(() => currentQuizOptions().onComplete({ character: '好', totalMistakes: 0 }))
    await screen.findByText('Đã ghi nhận kết quả.')
    const user = userEvent.setup()
    await user.click(screen.getByRole('button', { name: /^Viết theo nét/ }))
    await waitFor(() => expect(canvasReadyMock.quiz).toHaveBeenCalledTimes(2))
    await user.click(screen.getByRole('button', { name: /Nghe chép/ }))
    await waitFor(() => expect(canvasReadyMock.quiz).toHaveBeenCalledTimes(3))
  })
})
