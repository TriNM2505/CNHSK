import { act, fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { createMemoryRouter, RouterProvider } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import type { CurrentUser, RoleCode } from '@/features/auth/types'
import { ApiError } from '@/shared/api'
import { AuthContext, type AuthContextValue } from '@/shared/auth'

import { getGuestTrial, getLexeme, listRadicals, searchByRadical, searchDictionary } from '../api/dictionary'
import type { SearchResult } from '../types'
import { translate } from '../api/translation'
import Scr024DictionarySearchPage from './Scr024DictionarySearchPage'
import Scr026TranslationPage from './Scr026TranslationPage'

vi.mock('../api/dictionary', () => ({
  getGuestTrial: vi.fn(async () => undefined),
  searchDictionary: vi.fn(),
  listRadicals: vi.fn(),
  searchByRadical: vi.fn(),
  getLexeme: vi.fn(),
}))
vi.mock('../api/translation', () => ({ translate: vi.fn() }))

const result: SearchResult = {
  items: [
    {
      id: 101,
      kind: 'CHAR',
      text: '日',
      pinyin: 'rì',
      sinoViet: 'nhật',
      meaningVi: 'ngày; mặt trời',
      hskLevel: 1,
      approximate: false,
    },
  ],
  total: 1,
}
const dictionaryMock = vi.mocked(searchDictionary)
const radicalMock = vi.mocked(searchByRadical)
const trialMock = vi.mocked(getGuestTrial)
const translateMock = vi.mocked(translate)
function renderPage(
  path = '/translate',
  roles: RoleCode[] | null = null,
  status?: AuthContextValue['status'],
) {
  const user: CurrentUser | null = roles
    ? {
        id: 7,
        displayName: 'Người học thử',
        email: 'learner@test.invalid',
        emailVerified: true,
        roles,
      }
    : null
  const auth: AuthContextValue = {
    user,
    status: status ?? (user ? 'authenticated' : 'guest'),
    error: null,
    reloadCurrentUser: async () => undefined,
    setAuthenticatedUser: () => undefined,
    signOut: async () => undefined,
  }
  const router = createMemoryRouter(
    [
      { path: '/translate', element: <Scr026TranslationPage /> },
      { path: '/dictionary', element: <Scr024DictionarySearchPage /> },
    ],
    { initialEntries: [path] },
  )
  return {
    router,
    ...render(
      <AuthContext.Provider value={auth}>
        <RouterProvider router={router} />
      </AuthContext.Provider>,
    ),
  }
}
/** Text lookups pick a source language; pinyin and radical lookups have their own mode. */
function selectMode(type: string) {
  if (type === 'PINYIN') return fireEvent.click(screen.getByRole('button', { name: 'Pinyin' }))
  if (type === 'RADICAL') return fireEvent.click(screen.getByRole('button', { name: 'Bộ thủ' }))
  fireEvent.click(screen.getByRole('button', { name: 'Văn bản' }))
  fireEvent.click(screen.getByRole('button', { name: type === 'MEANING' ? 'Tiếng Việt' : 'Tiếng Trung' }))
}
function enterQuery(q: string) {
  fireEvent.change(screen.getByLabelText(/Nội dung cần (dịch|tra)/), { target: { value: q } })
}
function submit() {
  fireEvent.click(screen.getByRole('button', { name: /^(Dịch|Tra cứu)$/ }))
}

beforeEach(() => {
  vi.resetAllMocks()
  dictionaryMock.mockResolvedValue(result)
  trialMock.mockResolvedValue({ limit: 10, remaining: 10 })
  vi.mocked(listRadicals).mockResolvedValue([{ radical: '日', charCount: 2 }])
  radicalMock.mockResolvedValue(result)
  // The full entry below the panels loads separately; tests that need it resolve it themselves.
  vi.mocked(getLexeme).mockImplementation(() => new Promise(() => undefined))
})

describe('merged translate screen: dictionary lookups (FR-023, AT-23)', () => {
  it('does not search before the visitor submits a query', async () => {
    renderPage()
    expect(screen.getByRole('heading', { name: 'Dịch', level: 1 })).toBeVisible()
    await waitFor(() =>
      expect(screen.getByText('Nhập một từ hoặc một câu rồi bấm Dịch.')).toBeVisible(),
    )
    expect(dictionaryMock).not.toHaveBeenCalled()
    expect(listRadicals).not.toHaveBeenCalled()
    expect(radicalMock).not.toHaveBeenCalled()
  })

  it.each([
    { type: 'HANZI', q: '日' },
    { type: 'PINYIN', q: 'rì' },
    { type: 'MEANING', q: 'mặt trời' },
  ])('FR023_submits $type without client-side search or accent conversion', async ({ type, q }) => {
    renderPage()
    selectMode(type)
    enterQuery(q)
    submit()
    expect(await screen.findByText('Hán Việt: nhật')).toBeVisible()
    expect(dictionaryMock).toHaveBeenCalledWith({ type, q, page: 0 }, expect.any(AbortSignal))
    expect(screen.getByText('rì')).toBeVisible()
    expect(screen.getByText('Hán Việt: nhật')).toBeVisible()
    expect(radicalMock).not.toHaveBeenCalled()
  })

  it('FR023_searches radical and strokes using the dedicated endpoint', async () => {
    renderPage()
    selectMode('RADICAL')
    expect(await screen.findByRole('option', { name: '日 (2 chữ)' })).toBeInTheDocument()
    fireEvent.change(screen.getByLabelText('Bộ thủ'), { target: { value: '日' } })
    fireEvent.change(screen.getByLabelText('Số nét'), { target: { value: '4' } })
    submit()
    expect(await screen.findByText('Hán Việt: nhật')).toBeVisible()
    expect(radicalMock).toHaveBeenCalledWith(
      { radical: '日', strokes: 4, page: 0 },
      expect.any(AbortSignal),
    )
    expect(dictionaryMock).not.toHaveBeenCalled()
  })

  it.each([
    { filter: 'strokes', path: 'strokes=4', expected: { radical: undefined, strokes: 4, page: 0 } },
    {
      filter: 'radical',
      path: 'radical=%E6%97%A5',
      expected: { radical: '日', strokes: undefined, page: 0 },
    },
  ])('accepts only $filter and shows the server approximate marker', async ({ path, expected }) => {
    radicalMock.mockResolvedValue({
      ...result,
      items: result.items.map((entry) => ({ ...entry, approximate: true })),
    })
    renderPage(`/translate?type=RADICAL&${path}`)
    expect(await screen.findByText('Số nét gần đúng')).toBeVisible()
    expect(radicalMock).toHaveBeenCalledWith(expected, expect.any(AbortSignal))
  })

  it.each(['0', '65', '2.5', 'abc'])(
    'rejects invalid stroke count %s before any search',
    async (strokes) => {
      renderPage()
      selectMode('RADICAL')
      fireEvent.change(screen.getByLabelText('Số nét'), { target: { value: strokes } })
      submit()
      expect(screen.getByText('Số nét phải là số nguyên từ 1 đến 64.')).toBeVisible()
      expect(radicalMock).not.toHaveBeenCalled()
    },
  )

  it('requires at least one radical filter', () => {
    renderPage()
    selectMode('RADICAL')
    submit()
    expect(screen.getByText('Chọn bộ thủ hoặc nhập số nét.')).toBeVisible()
    expect(radicalMock).not.toHaveBeenCalled()
  })

  it.each([
    { type: 'HANZI', q: '   ', error: 'Nhập nội dung cần dịch.' },
    { type: 'MEANING', q: 'a', error: 'Nghĩa Việt cần ít nhất 2 ký tự.' },
  ])('validates $type without erasing input', ({ type, q, error }) => {
    renderPage()
    selectMode(type)
    enterQuery(q)
    submit()
    expect(screen.getByText(error)).toBeVisible()
    expect(screen.getByLabelText(/Nội dung cần (dịch|tra)/)).toHaveValue(q)
    expect(dictionaryMock).not.toHaveBeenCalled()
  })

  it('FR023_preserves a pinyin query when no matches are found', async () => {
    dictionaryMock.mockResolvedValue({ items: [], total: 0 })
    renderPage()
    selectMode('PINYIN')
    enterQuery('zzz')
    submit()
    expect(await screen.findByText('Không tìm thấy — thử kiểu tra khác')).toBeVisible()
    expect(screen.getByLabelText(/Nội dung cần (dịch|tra)/)).toHaveValue('zzz')
    expect(translateMock).not.toHaveBeenCalled()
  })

  it('answers a word found in the dictionary without calling the AI', async () => {
    renderPage('/translate?type=HANZI&q=日', ['LEARNER'])
    expect(await screen.findByText('Hán Việt: nhật')).toBeVisible()
    expect(translateMock).not.toHaveBeenCalled()
    expect(screen.queryByRole('region', { name: 'Kết quả dịch' })).not.toBeInTheDocument()
  })

  it.each([
    { roles: null, panel: 'Đăng nhập để dịch cụm từ và câu bằng AI.' },
    {
      roles: ['ADMIN'] as RoleCode[],
      panel: 'Dịch cụm từ và câu bằng AI chỉ dành cho tài khoản học viên.',
    },
  ])('sends a phrase straight to translation and explains who may translate: $roles', async ({ roles, panel }) => {
    renderPage('/translate?type=HANZI&q=我喜欢喝茶', roles)
    expect(await screen.findByText(panel)).toBeVisible()
    expect(dictionaryMock).not.toHaveBeenCalled()
    expect(translateMock).not.toHaveBeenCalled()
  })
  it('FR023_guest 429 shows retry delay and login with the complete return destination', async () => {
    dictionaryMock.mockRejectedValue(
      new ApiError({
        status: 429,
        errorCode: 'RATE_LIMITED',
        message: 'Giới hạn tra cứu',
        requestId: 'dictionary-429',
        extra: { retryAfterSeconds: 45, loginSuggested: true },
      }),
    )
    const path = '/translate?type=PINYIN&q=ri4&page=2#results'
    renderPage(path)
    const login = await screen.findByRole('link', { name: 'Đăng nhập để tra tiếp' })
    expect(
      new URL(login.getAttribute('href')!, 'https://test.invalid').searchParams.get('redirect'),
    ).toBe(path)
    expect(screen.getByText('Thử lại sau 45 giây.')).toBeVisible()
    expect(screen.getByText('Mã lỗi: dictionary-429')).toBeVisible()
    expect(screen.getByLabelText(/Nội dung cần (dịch|tra)/)).toHaveValue('ri4')
  })

  it('counts the guest trial down after every finished lookup', async () => {
    trialMock
      .mockResolvedValueOnce({ limit: 10, remaining: 10 })
      .mockResolvedValueOnce({ limit: 10, remaining: 9 })
      .mockResolvedValueOnce({ limit: 10, remaining: 3 })
    renderPage()
    expect(await screen.findByText('10/10')).toBeVisible()
    enterQuery('日')
    submit()
    expect(await screen.findByText('9/10')).toBeVisible()
    enterQuery('月')
    submit()
    expect(await screen.findByText('3/10')).toBeVisible()
    expect(trialMock).toHaveBeenCalledTimes(3)
    expect(screen.getByRole('button', { name: /^(Dịch|Tra cứu)$/ })).toBeEnabled()
  })

  it('locks the form at zero and keeps the last results with a way to sign in and come back', async () => {
    trialMock.mockResolvedValue({ limit: 10, remaining: 0 })
    renderPage('/translate?type=HANZI&q=日')
    expect(await screen.findByText('Bạn đã dùng hết 10 lượt tra thử hôm nay')).toBeVisible()
    expect(await screen.findByText('rì')).toBeVisible()
    expect(screen.getByRole('button', { name: /^(Dịch|Tra cứu)$/ })).toBeDisabled()
    expect(screen.getByLabelText(/Nội dung cần (dịch|tra)/)).toBeDisabled()
    expect(screen.getByRole('button', { name: 'Pinyin' })).toBeDisabled()
    for (const [name, path] of [
      ['Đăng nhập để tra tiếp', '/login'],
      ['Đăng ký miễn phí', '/register'],
    ] as const) {
      const target = new URL(
        screen.getByRole('link', { name }).getAttribute('href')!,
        'https://test.invalid',
      )
      expect(target.pathname).toBe(path)
      expect(target.searchParams.get('redirect')).toBe('/translate?type=HANZI&q=日')
    }
  })

  it('replaces the error with the sign-in prompt when the server rejects a lookup past the trial', async () => {
    trialMock.mockResolvedValue(undefined)
    dictionaryMock.mockRejectedValue(
      new ApiError({
        status: 429,
        errorCode: 'RATE_LIMITED',
        message: 'Hết lượt',
        extra: { guestTrialExhausted: true, loginSuggested: true, retryAfterSeconds: 60 },
      }),
    )
    renderPage('/translate?type=HANZI&q=日')
    expect(await screen.findByText('Bạn đã dùng hết lượt tra thử hôm nay')).toBeVisible()
    expect(screen.getAllByRole('link', { name: 'Đăng nhập để tra tiếp' })).toHaveLength(1)
    expect(screen.queryByRole('heading', { name: 'Chưa thể tra từ điển' })).not.toBeInTheDocument()
    expect(screen.queryByText('Thử lại sau 60 giây.')).not.toBeInTheDocument()
  })

  it('shows no trial to a signed-in user and stays usable when the count is unknown', async () => {
    renderPage('/translate?type=HANZI&q=日', ['LEARNER'])
    await screen.findByText('rì')
    expect(trialMock).not.toHaveBeenCalled()
    expect(screen.queryByText(/lượt tra thử/)).not.toBeInTheDocument()
  })

  it('keeps lookups available to a guest when the trial count cannot be read', async () => {
    trialMock.mockRejectedValue(
      new ApiError({ status: 503, errorCode: 'UNAVAILABLE', message: 'Chưa sẵn sàng' }),
    )
    renderPage()
    expect(await screen.findByText('Bạn đang tra cứu với tư cách khách.')).toBeVisible()
    enterQuery('日')
    submit()
    expect(await screen.findByText('rì')).toBeVisible()
  })

  it('does not suggest login to an authenticated learner on 429', async () => {
    dictionaryMock.mockRejectedValue(
      new ApiError({ status: 429, errorCode: 'RATE_LIMITED', message: 'Giới hạn' }),
    )
    renderPage('/translate?q=日', ['LEARNER'])
    await screen.findByRole('heading', { name: 'Chưa thể tra từ điển' })
    expect(screen.queryByRole('link', { name: 'Đăng nhập để tra tiếp' })).not.toBeInTheDocument()
  })

  it('keeps the search form during loading and supports manual retry after an error', async () => {
    let resolve!: (value: SearchResult) => void
    dictionaryMock.mockImplementationOnce(
      () =>
        new Promise((done) => {
          resolve = done
        }),
    )
    renderPage('/translate?type=HANZI&q=日')
    expect(screen.getByLabelText('Đang tra từ điển')).toBeVisible()
    await act(async () => resolve(result))
    expect(await screen.findByText('rì')).toBeVisible()
    dictionaryMock.mockRejectedValueOnce(
      new ApiError({ status: 503, errorCode: 'UNAVAILABLE', message: 'Chưa sẵn sàng' }),
    )
    submit()
    expect(await screen.findByText('Chưa sẵn sàng')).toBeVisible()
    await userEvent.click(screen.getByRole('button', { name: 'Thử lại' }))
    expect(await screen.findByText('rì')).toBeVisible()
  })

  it('keeps a stroke-only search usable while the radical list fails, then retries that list', async () => {
    vi.mocked(listRadicals).mockRejectedValueOnce(
      new ApiError({ status: 503, errorCode: 'UNAVAILABLE', message: 'Chưa có danh sách' }),
    )
    renderPage('/translate?type=RADICAL&strokes=4')
    expect(await screen.findByText('rì')).toBeVisible()
    await userEvent.click(
      within(
        screen.getByRole('heading', { name: 'Chưa tải được danh sách bộ thủ' }).closest('section')!,
      ).getByRole('button', { name: 'Thử lại' }),
    )
    expect(await screen.findByRole('option', { name: '日 (2 chữ)' })).toBeInTheDocument()
  })

  it('uses zero-based API pages and one-based display without losing query or result order', async () => {
    dictionaryMock.mockResolvedValue({
      ...result,
      total: 45,
      items: [{ ...result.items[0]!, id: 102, text: '月', pinyin: 'yuè' }, result.items[0]!],
    })
    const { router } = renderPage('/translate?type=PINYIN&q=ri4&page=1')
    expect(await screen.findByText('Trang 2 / 3')).toBeVisible()
    const entries = within(screen.getByRole('region', { name: 'Kết quả tra cứu' })).getAllByRole(
      'button',
      { pressed: false },
    )
    expect(entries[0]).toHaveTextContent('月')
    expect(entries[1]).toHaveTextContent('日')
    await userEvent.click(screen.getByRole('button', { name: 'Sau' }))
    await waitFor(() =>
      expect(dictionaryMock).toHaveBeenLastCalledWith(
        { type: 'PINYIN', q: 'ri4', page: 2 },
        expect.any(AbortSignal),
      ),
    )
    expect(new URLSearchParams(router.state.location.search).get('page')).toBe('2')
    expect(screen.getByLabelText(/Nội dung cần (dịch|tra)/)).toHaveValue('ri4')
  })

  it('aborts stale searches and ignores a late result from the previous query', async () => {
    let resolveOld!: (value: SearchResult) => void
    dictionaryMock
      .mockImplementationOnce(
        () =>
          new Promise((done) => {
            resolveOld = done
          }),
      )
      .mockResolvedValueOnce({
        items: [{ ...result.items[0]!, id: 102, text: '月', pinyin: 'yuè' }],
        total: 1,
      })
    renderPage('/translate?type=HANZI&q=日')
    await waitFor(() => expect(dictionaryMock).toHaveBeenCalledTimes(1))
    const signal = dictionaryMock.mock.calls[0]![1]!
    enterQuery('月')
    submit()
    expect(await screen.findByText('yuè')).toBeVisible()
    expect(signal.aborted).toBe(true)
    await act(async () => resolveOld(result))
    expect(screen.queryByText('rì')).not.toBeInTheDocument()
    expect(screen.getByText('yuè')).toBeVisible()
  })

  it.each(['-1', 'abc', '9007199254740992'])(
    'normalizes unsafe URL page %s before the request',
    async (page) => {
      renderPage(`/translate?type=HANZI&q=日&page=${page}`)
      await screen.findByText('rì')
      expect(dictionaryMock).toHaveBeenCalledWith(
        { type: 'HANZI', q: '日', page: 0 },
        expect.any(AbortSignal),
      )
    },
  )
})

describe('merged translate screen: AI translation (FR-025, AT-25)', () => {
  const translation = {
    translated: 'Tôi thích uống trà.',
    pinyin: 'wǒ xǐhuan hē chá',
    segments: [
      { text: '我', pinyin: 'wǒ', lexemeId: 101 },
      { text: '喜欢喝茶', pinyin: null, lexemeId: null },
    ],
    cached: false,
    consumption: 'QUOTA' as const,
  }
  beforeEach(() => {
    translateMock.mockResolvedValue(translation)
  })

  it('translates a Chinese phrase for a learner without asking the dictionary', async () => {
    renderPage('/translate?type=HANZI&q=我喜欢喝茶', ['LEARNER'])
    expect(await screen.findByText('Tôi thích uống trà.')).toBeVisible()
    expect(translateMock).toHaveBeenCalledWith(
      { text: '我喜欢喝茶', from: 'ZH', to: 'VI' },
      expect.any(AbortSignal),
    )
    expect(dictionaryMock).not.toHaveBeenCalled()
    expect(screen.queryByText(/lượt miễn phí|bộ nhớ đệm/)).not.toBeInTheDocument()
  })

  it('translates a Vietnamese sentence into Chinese', async () => {
    renderPage('/translate?type=MEANING&q=tôi thích uống trà', ['LEARNER'])
    await screen.findByText('Tôi thích uống trà.')
    expect(translateMock).toHaveBeenCalledWith(
      { text: 'tôi thích uống trà', from: 'VI', to: 'ZH' },
      expect.any(AbortSignal),
    )
  })

  it('translates a short word the dictionary only knows character by character', async () => {
    dictionaryMock.mockResolvedValue({
      items: [{ ...result.items[0]!, id: 103, text: '喝' }, { ...result.items[0]!, id: 104, text: '茶' }],
      total: 2,
    })
    renderPage('/translate?type=HANZI&q=喝茶', ['LEARNER'])
    expect(await screen.findByText('Tôi thích uống trà.')).toBeVisible()
    expect(dictionaryMock).toHaveBeenCalledTimes(1)
    expect(screen.queryByRole('region', { name: 'Kết quả tra cứu' })).not.toBeInTheDocument()
  })

  it('shows a refunded translation failure in place and retries on request', async () => {
    translateMock.mockRejectedValueOnce(
      new ApiError({ status: 503, errorCode: 'TRANSLATE_UNAVAILABLE', message: 'Dịch tạm thời lỗi' }),
    )
    renderPage('/translate?type=HANZI&q=我喜欢喝茶', ['LEARNER'])
    expect(await screen.findByText('Lượt đã được hoàn. Vui lòng thử lại.')).toBeVisible()
    await userEvent.click(screen.getByRole('button', { name: 'Thử lại' }))
    expect(await screen.findByText('Tôi thích uống trà.')).toBeVisible()
    expect(translateMock).toHaveBeenCalledTimes(2)
  })

  it('points to plans when the learner has no translation left', async () => {
    translateMock.mockRejectedValueOnce(
      new ApiError({ status: 402, errorCode: 'INSUFFICIENT_CREDITS', message: 'Hết lượt' }),
    )
    renderPage('/translate?type=HANZI&q=我喜欢喝茶', ['LEARNER'])
    expect(await screen.findByText('Bạn đã dùng hết lượt dịch miễn phí tháng này.')).toBeVisible()
    expect(screen.getByRole('link', { name: 'Nâng cấp PREMIUM' })).toHaveAttribute(
      'href',
      '/account/credits',
    )
  })

  it('opens a word of the translated sentence right below, without leaving the screen', async () => {
    vi.mocked(getLexeme).mockResolvedValue({
      ...result.items[0]!,
      id: 101,
      text: '我',
      pinyin: 'wǒ',
      meaningVi: 'tôi',
      examples: [{ cn: '我是学生。', pinyin: 'Wǒ shì xuésheng.', vi: 'Tôi là học sinh.' }],
      components: [],
      containedIn: [],
      related: [],
      strokeData: null,
    } as never)
    renderPage('/translate?type=HANZI&q=我喜欢喝茶', ['LEARNER'])
    await userEvent.click(await screen.findByRole('button', { name: 'Tra từ 我' }))
    expect(await screen.findByText('Tôi là học sinh.')).toBeVisible()
    expect(getLexeme).toHaveBeenCalledWith(101, expect.any(AbortSignal))
  })

  it('shows the full entry of a looked-up word right below, examples included', async () => {
    vi.mocked(getLexeme).mockResolvedValue({
      ...result.items[0]!,
      examples: [{ cn: '今天是星期日。', pinyin: 'Jīntiān shì xīngqīrì.', vi: 'Hôm nay là chủ nhật.' }],
      components: [],
      containedIn: [],
      related: [],
      strokeData: null,
    } as never)
    renderPage('/translate?type=HANZI&q=日', ['LEARNER'])
    expect(await screen.findByText('Hôm nay là chủ nhật.')).toBeVisible()
    expect(getLexeme).toHaveBeenCalledWith(101, expect.any(AbortSignal))
    expect(screen.queryByRole('link', { name: /Xem chi tiết/ })).not.toBeInTheDocument()
  })
  it('sends old /dictionary links to the merged screen with their search', async () => {
    const { router } = renderPage('/dictionary?type=PINYIN&q=ri4', ['LEARNER'])
    await waitFor(() => expect(router.state.location.pathname).toBe('/translate'))
    expect(router.state.location.search).toBe('?type=PINYIN&q=ri4')
    expect(await screen.findByText('rì')).toBeVisible()
  })
})
