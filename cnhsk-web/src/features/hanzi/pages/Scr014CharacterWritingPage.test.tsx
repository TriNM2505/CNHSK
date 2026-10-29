import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { findActiveSession } from '@/features/practice/api'
import type { SessionView } from '@/features/practice'
import { ApiError } from '@/shared/api'

import { getCharacter, getCharacters, startWritingChallenge } from '../api/characters'
import type { CharacterDetail } from '../types'
import Scr014CharacterWritingPage from './Scr014CharacterWritingPage'

vi.mock('../api/characters', () => ({
  getCharacter: vi.fn(),
  getCharacters: vi.fn(),
  startWritingChallenge: vi.fn(),
}))

vi.mock('@/features/practice/api', () => ({
  abandonSession: vi.fn(),
  answerItem: vi.fn(),
  findActiveSession: vi.fn(),
  finishSession: vi.fn(),
  getSession: vi.fn(),
}))

const getCharactersMock = vi.mocked(getCharacters)
const getCharacterMock = vi.mocked(getCharacter)
const startChallengeMock = vi.mocked(startWritingChallenge)
const findActiveMock = vi.mocked(findActiveSession)

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
  beforeEach(() => vi.clearAllMocks())

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
    expect(screen.getByTestId('hanzi-canvas-slot')).toBeVisible()
    expect(getCharacterMock).toHaveBeenCalledWith(11, expect.any(AbortSignal))
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
