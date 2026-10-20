import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { answerItem, findActiveSession, finishSession, getSession } from '@/features/practice/api'
import type { FinishResult, SessionView } from '@/features/practice'
import { ApiError } from '@/shared/api'

import { getPronunciationStages, startPronunciationStageSession } from '../api/pronunciation'
import type { PronunciationStageSummary } from '../types'
import Scr016PronunciationPage from './Scr016PronunciationPage'

vi.mock('../api/pronunciation', () => ({
  getPronunciationStages: vi.fn(),
  startPronunciationStageSession: vi.fn(),
}))

vi.mock('@/features/practice/api', () => ({
  abandonSession: vi.fn(),
  answerItem: vi.fn(),
  findActiveSession: vi.fn(),
  finishSession: vi.fn(),
  getSession: vi.fn(),
}))

const getStagesMock = vi.mocked(getPronunciationStages)
const startSessionMock = vi.mocked(startPronunciationStageSession)
const findActiveSessionMock = vi.mocked(findActiveSession)
const getSessionMock = vi.mocked(getSession)
const answerItemMock = vi.mocked(answerItem)
const finishSessionMock = vi.mocked(finishSession)

const stage: PronunciationStageSummary = {
  activeAnswered: null,
  activeSessionId: null,
  bestAccuracy: null,
  description: 'Phân biệt các âm môi.',
  id: 701,
  name: 'Thanh mẫu môi',
  passPercent: 80,
  playable: true,
  stageIndex: 1,
  state: 'AVAILABLE',
  targetSounds: ['b', 'p', 'm', 'f'],
  unitCount: 2,
}

const options = [
  { key: 'A', text: 'bā' },
  { key: 'B', text: 'pā' },
  { key: 'C', text: 'mā' },
  { key: 'D', text: 'fā' },
]

function item(no: number): SessionView['items'][number] {
  return {
    no,
    options,
    prompt: { audioUrl: `/audio/${no}.mp3`, text: `Nghe câu ${no}` },
    rules: { hasAudio: true, maxReplays: 3, minDurationMs: 500 },
    skill: 'PRONUNCIATION',
    type: 'PRON_CHOICE',
  }
}

function session(overrides: Partial<SessionView> = {}): SessionView {
  return {
    answered: [],
    expiresAt: '2026-10-27T12:00:00Z',
    feedback: 'IMMEDIATE',
    items: [item(1), item(2)],
    refId: 701,
    servedAt: '2026-10-20T12:00:00Z',
    sessionId: 901,
    source: 'PRON_STAGE',
    status: 'IN_PROGRESS',
    ...overrides,
  }
}

function finished(overrides: Record<string, unknown> = {}): FinishResult {
  return {
    extra: {
      accuracy: 100,
      bestAccuracy: 100,
      completedAllStages: false,
      needed: 0,
      nextStageIndex: 2,
      noScore: false,
      passed: true,
      passPercent: 80,
      stageIndex: 1,
      unlockedNextStage: true,
      ...overrides,
    },
    items: [],
    sessionId: 901,
    status: 'SUBMITTED',
    suspicious: false,
  }
}

function renderPage(path = '/learn/pronunciation/stages/1') {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <Routes>
        <Route element={<Scr016PronunciationPage />} path="/learn/pronunciation" />
        <Route
          element={<Scr016PronunciationPage />}
          path="/learn/pronunciation/stages/:stageIndex"
        />
      </Routes>
    </MemoryRouter>,
  )
}

describe('SCR-016 Pronunciation Practice', () => {
  afterEach(() => vi.restoreAllMocks())

  beforeEach(() => {
    getStagesMock.mockReset().mockResolvedValue([stage])
    startSessionMock.mockReset().mockResolvedValue(session())
    findActiveSessionMock.mockReset().mockResolvedValue(null)
    getSessionMock.mockReset().mockResolvedValue(session())
    answerItemMock.mockReset().mockImplementation(async (_sessionId, request) => ({
      accepted: true,
      correct: true,
      correctAnswer: 'A',
      counted: true,
      explanation: `Giải thích câu ${request.no}`,
      no: request.no,
    }))
    finishSessionMock.mockReset().mockResolvedValue(finished())
  })

  it('tải danh sách một lần và hiển thị đủ tám tầng từ API', async () => {
    getStagesMock.mockResolvedValue(
      Array.from({ length: 8 }, (_, index) => ({
        ...stage,
        id: 701 + index,
        name: `Tầng mẫu ${index + 1}`,
        stageIndex: index + 1,
        state: index === 0 ? ('AVAILABLE' as const) : ('LOCKED' as const),
      })),
    )
    renderPage('/learn/pronunciation')

    expect(await screen.findAllByText(/^Tầng mẫu \d$/)).toHaveLength(8)
    expect(getStagesMock).toHaveBeenCalledTimes(1)
    expect(getStagesMock).toHaveBeenCalledWith(expect.any(AbortSignal))
  })

  it('hiện EmptyState khi stageIndex nằm ngoài 1–8', async () => {
    renderPage('/learn/pronunciation/stages/9')

    expect(
      await screen.findByRole('heading', { name: 'Không tìm thấy tầng phát âm' }),
    ).toBeVisible()
    expect(screen.getByRole('link', { name: 'Quay lại danh sách' })).toHaveAttribute(
      'href',
      '/learn/pronunciation',
    )
    expect(startSessionMock).not.toHaveBeenCalled()
  })

  it('coi stageIndex không có trong danh sách như STAGE_NOT_FOUND', async () => {
    renderPage('/learn/pronunciation/stages/2')

    expect(
      await screen.findByRole('heading', { name: 'Không tìm thấy tầng phát âm' }),
    ).toBeVisible()
    expect(startSessionMock).not.toHaveBeenCalled()
  })

  it('map STAGE_LOCKED và dẫn tới tầng bắt buộc', async () => {
    startSessionMock.mockRejectedValue(
      new ApiError({
        errorCode: 'STAGE_LOCKED',
        extra: { requiredStageIndex: 1 },
        message: 'Tầng đang khoá.',
        requestId: 'req-locked',
        status: 403,
      }),
    )
    getStagesMock.mockResolvedValue([{ ...stage, id: 702, stageIndex: 2, state: 'LOCKED' }])
    renderPage('/learn/pronunciation/stages/2')

    expect(await screen.findByRole('heading', { name: 'Tầng phát âm đang bị khoá' })).toBeVisible()
    expect(screen.getByText('Hoàn thành tầng 1 trước để mở tầng này.')).toBeVisible()
    expect(screen.getByRole('link', { name: 'Học tầng 1' })).toHaveAttribute(
      'href',
      '/learn/pronunciation/stages/1',
    )
  })

  it('map STAGE_HAS_NO_UNITS sang trạng thái đang cập nhật', async () => {
    startSessionMock.mockRejectedValue(
      new ApiError({
        errorCode: 'STAGE_HAS_NO_UNITS',
        message: 'Tầng chưa có bài.',
        requestId: 'req-empty-stage',
        status: 422,
      }),
    )
    renderPage()

    expect(
      await screen.findByRole('heading', { name: 'Tầng phát âm đang được cập nhật' }),
    ).toBeVisible()
  })

  it('khôi phục lượt theo stage.id và không POST lại', async () => {
    const restored = session({
      answered: [{ answer: 'A', correct: true, correctAnswer: 'A', counted: true, no: 1 }],
    })
    findActiveSessionMock.mockResolvedValue(restored)
    getSessionMock.mockResolvedValue(restored)
    renderPage()

    expect(await screen.findByText('Nghe câu 2')).toBeVisible()
    expect(findActiveSessionMock).toHaveBeenCalledWith('PRON_STAGE', 701, expect.any(AbortSignal))
    expect(startSessionMock).not.toHaveBeenCalled()
    expect(screen.getByText('Câu 1/2')).toBeVisible()
  })

  it('không có nút Bỏ qua và giới hạn nghe tối đa ba lần', async () => {
    vi.spyOn(HTMLMediaElement.prototype, 'play').mockResolvedValue(undefined)
    const user = userEvent.setup()
    renderPage()

    const listen = await screen.findByRole('button', { name: 'Nghe từ' })
    expect(screen.queryByRole('button', { name: /Bỏ qua/ })).not.toBeInTheDocument()
    await user.click(listen)
    await user.click(listen)
    await user.click(listen)

    expect(screen.getByRole('button', { name: 'Đã hết lượt nghe' })).toBeDisabled()
    await user.click(screen.getByRole('button', { name: /bā/ }))
    expect(answerItemMock).toHaveBeenCalledWith(
      901,
      expect.objectContaining({ answer: 'A', no: 1, replayCount: 3 }),
    )
  })

  it('gửi audioFailed khi người học không nghe được', async () => {
    const user = userEvent.setup()
    renderPage()

    await user.click(await screen.findByRole('button', { name: 'Không nghe được' }))

    expect(answerItemMock).toHaveBeenCalledWith(
      901,
      expect.objectContaining({ audioFailed: true, no: 1, replayCount: 0 }),
    )
  })

  it('tự finish sau câu cuối và tải lại danh sách đúng một lần mà vẫn giữ kết quả', async () => {
    const user = userEvent.setup()
    renderPage()

    let choices = await screen.findByRole('group', { name: 'Các phương án' })
    await user.click(within(choices).getByRole('button', { name: /bā/ }))
    expect(await screen.findByText('Giải thích câu 1')).toBeVisible()
    await user.click(screen.getByRole('button', { name: /Câu tiếp/ }))

    choices = await screen.findByRole('group', { name: 'Các phương án' })
    await user.click(within(choices).getByRole('button', { name: /bā/ }))

    expect(await screen.findByText('Đã mở tầng 2')).toBeVisible()
    expect(finishSessionMock).toHaveBeenCalledTimes(1)
    await waitFor(() => expect(getStagesMock).toHaveBeenCalledTimes(2))
    expect(screen.getByText('Đã mở tầng 2')).toBeVisible()
  })

  it('STAGE_INCOMPLETE sync rồi nhảy tới câu chưa làm và báo remaining', async () => {
    const restored = session({
      answered: [{ answer: 'A', correct: true, correctAnswer: 'A', counted: true, no: 1 }],
    })
    findActiveSessionMock.mockResolvedValue(restored)
    getSessionMock.mockResolvedValue(restored)
    finishSessionMock.mockRejectedValue(
      new ApiError({
        errorCode: 'STAGE_INCOMPLETE',
        extra: { remaining: 1 },
        message: 'Còn câu chưa làm.',
        requestId: 'req-incomplete',
        status: 422,
      }),
    )
    const user = userEvent.setup()
    renderPage()

    const choices = await screen.findByRole('group', { name: 'Các phương án' })
    await user.click(within(choices).getByRole('button', { name: /bā/ }))

    expect(await screen.findByText('Còn 1 câu chưa làm')).toBeVisible()
    expect(getSessionMock).toHaveBeenCalledTimes(2)
    expect(screen.getByText('Nghe câu 2')).toBeVisible()
  })
})
