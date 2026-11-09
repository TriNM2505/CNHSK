import { act, fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { createMemoryRouter, RouterProvider } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { ApiError } from '@/shared/api'
import { AuthContext, type AuthContextValue } from '@/shared/auth'

import { getLexeme } from '../api/dictionary'
import { addCard, listDecks } from '../api/flashcards'
import { createNote } from '../api/notes'
import { translate } from '../api/translation'
import type { LexemeDetail, TranslateResult } from '../types'
import Scr026TranslationPage from './Scr026TranslationPage'

vi.mock('../api/translation', () => ({ translate: vi.fn() }))
vi.mock('../api/dictionary', () => ({ getLexeme: vi.fn() }))
vi.mock('../api/notes', () => ({ createNote: vi.fn() }))
vi.mock('../api/flashcards', () => ({ listDecks: vi.fn(), addCard: vi.fn() }))

const response: TranslateResult = {
  translated: 'Tôi đọc sách.',
  pinyin: 'wǒ dú shū',
  cached: false,
  consumption: 'QUOTA',
  segments: [
    { text: '我', pinyin: 'wǒ', lexemeId: null },
    { text: '读书', pinyin: 'dú shū', lexemeId: 41 },
    { text: '。', pinyin: null, lexemeId: null },
  ],
}
const entry: LexemeDetail = {
  id: 41,
  kind: 'WORD',
  text: '读书',
  pinyin: 'dú shū',
  meaningVi: 'đọc sách',
  examples: [],
  components: [],
  containedIn: [],
  related: [],
}
function auth(id: number | null = 7): AuthContextValue {
  return {
    status: id === null ? 'guest' : 'authenticated',
    user:
      id === null
        ? null
        : {
            id,
            displayName: 'Người học',
            email: 'test@example.invalid',
            emailVerified: true,
            roles: ['LEARNER'],
          },
    error: null,
    reloadCurrentUser: vi.fn(),
    setAuthenticatedUser: vi.fn(),
    signOut: async () => undefined,
  }
}
function renderPage(value = auth()) {
  const router = createMemoryRouter(
    [
      { path: '/translate', element: <Scr026TranslationPage /> },
      { path: '/elsewhere', element: <h1>Trang khác</h1> },
    ],
    { initialEntries: ['/translate'] },
  )
  const view = render(
    <AuthContext.Provider value={value}>
      <RouterProvider router={router} />
    </AuthContext.Provider>,
  )
  return {
    ...view,
    router,
    changeAuth: (next: AuthContextValue) =>
      view.rerender(
        <AuthContext.Provider value={next}>
          <RouterProvider router={router} />
        </AuthContext.Provider>,
      ),
  }
}
function text() {
  return screen.getByRole('textbox', { name: /Nội dung cần dịch/ })
}
function fill(value = '我读书。') {
  fireEvent.change(text(), { target: { value } })
}
function submit() {
  fireEvent.submit(screen.getByRole('form', { name: 'Dịch câu' }))
}
async function translated() {
  fill()
  submit()
  await screen.findByRole('region', { name: 'Kết quả dịch' })
}
function deferred<T>() {
  let resolve!: (value: T) => void
  let reject!: (error: unknown) => void
  const promise = new Promise<T>((res, rej) => {
    resolve = res
    reject = rej
  })
  return { promise, resolve, reject }
}
beforeEach(() => {
  vi.resetAllMocks()
  vi.mocked(translate).mockResolvedValue(response)
  vi.mocked(getLexeme).mockResolvedValue(entry)
  vi.mocked(listDecks).mockResolvedValue([
    { id: 61, name: 'Bộ tự soạn', cardCount: 0, dueCount: 0, createdAt: '2026-11-07T00:00:00Z' },
  ])
  vi.mocked(createNote).mockResolvedValue({
    id: 71,
    content: 'đọc sách',
    tags: [],
    createdAt: '2026-11-07T00:00:00Z',
    updatedAt: '2026-11-07T00:00:00Z',
  })
  vi.mocked(addCard).mockResolvedValue({
    id: 81,
    lexemeId: 41,
    front: '读书',
    back: 'dú shū — đọc sách',
    reviewCount: 0,
    lapseCount: 0,
  })
})

it('keeps the translation and popup note draft on NOTE_DUPLICATE until an explicit open', async () => {
  vi.mocked(createNote).mockRejectedValueOnce(
    new ApiError({
      status: 409,
      errorCode: 'NOTE_DUPLICATE',
      message: 'Trùng',
      details: { noteId: 42 },
    }),
  )
  const { router } = renderPage()
  await translated()
  fireEvent.click(screen.getByRole('button', { name: /Tra từ/ }))
  await screen.findByRole('button', { name: 'Lưu vào sổ tay' })
  fireEvent.click(screen.getByRole('button', { name: 'Lưu vào sổ tay' }))
  fireEvent.change(screen.getByLabelText(/^Nội dung ghi chú/), {
    target: { value: 'Nháp từ bản dịch' },
  })
  fireEvent.click(screen.getByRole('button', { name: 'Lưu ghi chú' }))
  await screen.findByText('Từ này đã có trong sổ tay')
  expect(screen.getByLabelText(/^Nội dung ghi chú/)).toHaveValue('Nháp từ bản dịch')
  expect(screen.getByRole('link', { name: 'Mở ghi chú đã có' })).toHaveAttribute(
    'href',
    '/library/notes?noteId=42',
  )
  expect(router.state.location.pathname).toBe('/translate')
  expect(translate).toHaveBeenCalledOnce()
})

describe('SCR-026 FR-025 / AT-25', () => {
  it('AT25_shows cached API output without pinyin or segments and without consuming another request', async () => {
    vi.mocked(translate).mockResolvedValue({
      ...response,
      cached: true,
      consumption: null,
      pinyin: null,
      segments: [],
    })
    renderPage()
    await translated()
    expect(screen.getByText('Từ bộ nhớ đệm — không trừ lượt')).toBeVisible()
    expect(screen.getByText('Tôi đọc sách.')).toBeVisible()
    expect(screen.queryByText('Pinyin của nội dung tiếng Trung')).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /^Tra từ/ })).not.toBeInTheDocument()
    expect(getLexeme).not.toHaveBeenCalled()
    expect(translate).toHaveBeenCalledOnce()
  })

  it.each([
    { value: '中'.repeat(2000), count: 2000, allowed: true },
    { value: '中'.repeat(2001), count: 2001, allowed: false },
    { value: '𠀀'.repeat(2000), count: 2000, allowed: true },
    { value: '𠀀'.repeat(2001), count: 2001, allowed: false },
  ])(
    'counts $count code points, allowed=$allowed including supplementary characters',
    async ({ value, count, allowed }) => {
      renderPage()
      fill(value)
      expect(text()).not.toHaveAttribute('maxlength')
      expect(screen.getByText(`${count}/2.000 ký tự`)).toBeVisible()
      expect(screen.getByRole('button', { name: 'Dịch' })).toHaveProperty('disabled', !allowed)
      submit()
      if (allowed) {
        await screen.findByRole('region', { name: 'Kết quả dịch' })
        expect(translate).toHaveBeenCalledWith(
          { text: value, from: 'ZH', to: 'VI' },
          expect.any(AbortSignal),
        )
      } else {
        expect(translate).not.toHaveBeenCalled()
        expect(text()).toHaveAttribute('aria-invalid', 'true')
      }
    },
  )

  it.each(['', '   ', '\n\t'])('rejects blank input %j at the textarea', (value) => {
    renderPage()
    fill(value)
    submit()
    expect(translate).not.toHaveBeenCalled()
    expect(text()).toHaveAttribute('aria-invalid', 'true')
    expect(screen.getByText('Nhập nội dung cần dịch, tối đa 2.000 ký tự.')).toBeVisible()
  })

  it('changes direction without translating and preserves raw text in the request', async () => {
    renderPage()
    expect(screen.getByLabelText('Hướng dịch')).toHaveValue('ZH')
    await userEvent.click(screen.getByRole('button', { name: 'Đổi hướng' }))
    expect(screen.getByLabelText('Hướng dịch')).toHaveValue('VI')
    fill('  Tôi đọc sách.\n')
    expect(translate).not.toHaveBeenCalled()
    submit()
    await screen.findByRole('region', { name: 'Kết quả dịch' })
    expect(translate).toHaveBeenCalledWith(
      { text: '  Tôi đọc sách.\n', from: 'VI', to: 'ZH' },
      expect.any(AbortSignal),
    )
    expect(screen.getByText('Tra từ trong bản dịch tiếng Trung')).toBeVisible()
    fireEvent.change(screen.getByLabelText('Hướng dịch'), { target: { value: 'ZH' } })
    expect(screen.queryByRole('region', { name: 'Kết quả dịch' })).not.toBeInTheDocument()
    expect(translate).toHaveBeenCalledOnce()
  })

  it('locks every control while loading, ignores changes and duplicate submits without aborting', async () => {
    const request = deferred<TranslateResult>()
    vi.mocked(translate).mockReturnValue(request.promise)
    renderPage()
    fill()
    submit()
    submit()
    const signal = vi.mocked(translate).mock.calls[0]![1]!
    expect(screen.getByRole('form', { name: 'Dịch câu' })).toHaveAttribute('aria-busy', 'true')
    expect(text()).toBeDisabled()
    expect(screen.getByLabelText('Hướng dịch')).toBeDisabled()
    expect(screen.getByRole('button', { name: 'Đổi hướng' })).toBeDisabled()
    expect(screen.getByRole('button', { name: 'Đang dịch…' })).toBeDisabled()
    fireEvent.change(text(), { target: { value: 'Không được thay' } })
    fireEvent.change(screen.getByLabelText('Hướng dịch'), { target: { value: 'VI' } })
    await userEvent.click(screen.getByRole('button', { name: 'Đổi hướng' }))
    expect(text()).toHaveValue('我读书。')
    expect(screen.getByLabelText('Hướng dịch')).toHaveValue('ZH')
    expect(translate).toHaveBeenCalledOnce()
    expect(signal.aborted).toBe(false)
    await act(async () => request.resolve(response))
    expect(screen.getByRole('region', { name: 'Kết quả dịch' })).toBeVisible()
    expect(text()).toBeEnabled()
    expect(signal.aborted).toBe(false)
  })

  it.each([
    [402, 'INSUFFICIENT_CREDITS', 'Bạn đã hết lượt dịch và không đủ điểm.'],
    [503, 'TRANSLATE_UNAVAILABLE', 'Dịch tạm thời lỗi — lượt đã được hoàn'],
    [422, 'VALIDATION_FAILED', 'Thông báo an toàn'],
    [500, 'INTERNAL_ERROR', 'Thông báo an toàn'],
    [429, 'RATE_LIMITED', 'Thông báo an toàn'],
    [403, 'FORBIDDEN', 'Thông báo an toàn'],
  ] as const)(
    'handles %s %s in place and only retries on explicit submit',
    async (status, errorCode, message) => {
      vi.mocked(translate).mockRejectedValue(
        new ApiError({
          status,
          errorCode,
          message: 'Thông báo an toàn',
          requestId: 'translation-error',
        }),
      )
      const { router } = renderPage()
      fireEvent.change(screen.getByLabelText('Hướng dịch'), { target: { value: 'VI' } })
      fill('Bản nháp cần giữ')
      submit()
      expect(await screen.findByText(message)).toBeVisible()
      expect(text()).toHaveValue('Bản nháp cần giữ')
      expect(screen.getByLabelText('Hướng dịch')).toHaveValue('VI')
      await waitFor(() => expect(screen.getByRole('button', { name: 'Dịch' })).toBeEnabled())
      expect(translate).toHaveBeenCalledOnce()
      if (status === 402)
        expect(screen.getByRole('link', { name: 'Xem gói dịch vụ và điểm' })).toHaveAttribute(
          'href',
          '/account/credits',
        )
      if (status === 422) expect(text()).toHaveAttribute('aria-invalid', 'true')
      expect(router.state.location.search).toBe('')
      vi.mocked(translate).mockResolvedValue(response)
      await userEvent.click(screen.getByRole('button', { name: 'Dịch' }))
      await screen.findByRole('region', { name: 'Kết quả dịch' })
      expect(translate).toHaveBeenCalledTimes(2)
    },
  )

  it('opens dictionary only for linked segments, traps focus, closes with Escape or button and restores focus', async () => {
    renderPage()
    await translated()
    expect(screen.queryByRole('button', { name: 'Tra từ 我' })).not.toBeInTheDocument()
    const trigger = screen.getByRole('button', { name: 'Tra từ 读书' })
    await userEvent.click(trigger)
    const popup = await screen.findByRole('dialog', { name: 'Tra từ' })
    expect(await within(popup).findByText('đọc sách')).toBeVisible()
    expect(getLexeme).toHaveBeenCalledWith(41, expect.any(AbortSignal))
    expect(translate).toHaveBeenCalledOnce()
    const close = within(popup).getByRole('button', { name: 'Đóng tra từ' })
    expect(close).toHaveFocus()
    await userEvent.tab({ shift: true })
    expect(within(popup).getByRole('button', { name: 'Thêm vào bộ thẻ' })).toHaveFocus()
    await userEvent.tab()
    expect(close).toHaveFocus()
    await userEvent.keyboard('{Escape}')
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    expect(trigger).toHaveFocus()
    await userEvent.click(trigger)
    await userEvent.click(screen.getByRole('button', { name: 'Đóng tra từ' }))
    expect(trigger).toHaveFocus()
  })

  it('shows lookup loading/error/retry and ignores a response after popup close', async () => {
    const request = deferred<LexemeDetail>()
    vi.mocked(getLexeme).mockReturnValueOnce(request.promise)
    renderPage()
    await translated()
    await userEvent.click(screen.getByRole('button', { name: 'Tra từ 读书' }))
    expect(screen.getByText('Đang tải mục từ…')).toBeVisible()
    await act(async () =>
      request.reject(
        new ApiError({
          status: 404,
          errorCode: 'LEXEME_NOT_FOUND',
          message: 'Mục từ không tồn tại.',
        }),
      ),
    )
    expect(screen.getByText('Không tìm thấy mục từ')).toBeVisible()
    await userEvent.click(screen.getByRole('button', { name: 'Thử lại' }))
    expect(await screen.findByText('đọc sách')).toBeVisible()
    await userEvent.keyboard('{Escape}')
    const late = deferred<LexemeDetail>()
    vi.mocked(getLexeme).mockReturnValueOnce(late.promise)
    await userEvent.click(screen.getByRole('button', { name: 'Tra từ 读书' }))
    const signal = vi.mocked(getLexeme).mock.calls.at(-1)![1]!
    await userEvent.keyboard('{Escape}')
    expect(signal.aborted).toBe(true)
    await act(async () => late.resolve(entry))
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    expect(translate).toHaveBeenCalledOnce()
  })

  it('reuses save actions with TRANSLATION source and lexeme-only card creation', async () => {
    renderPage()
    await translated()
    await userEvent.click(screen.getByRole('button', { name: 'Tra từ 读书' }))
    await userEvent.click(await screen.findByRole('button', { name: 'Lưu vào sổ tay' }))
    await userEvent.click(screen.getByRole('button', { name: 'Lưu ghi chú' }))
    await screen.findByText('Đã lưu vào sổ tay.')
    expect(createNote).toHaveBeenCalledWith(
      expect.objectContaining({ lexemeId: 41, sourceType: 'TRANSLATION' }),
    )
    await userEvent.click(screen.getByRole('button', { name: 'Thêm vào bộ thẻ' }))
    await screen.findByRole('option', { name: 'Bộ tự soạn (0 thẻ)' })
    await userEvent.selectOptions(screen.getByLabelText(/^Bộ thẻ/), '61')
    await userEvent.click(screen.getByRole('button', { name: 'Thêm thẻ' }))
    await screen.findByText('Đã thêm vào bộ thẻ.')
    expect(addCard).toHaveBeenCalledWith(61, { lexemeId: 41 })
    expect(translate).toHaveBeenCalledOnce()
  })

  it('clears old result and closes lookup after editing a completed translation', async () => {
    renderPage()
    await translated()
    await userEvent.click(screen.getByRole('button', { name: 'Tra từ 读书' }))
    await screen.findByText('đọc sách')
    fireEvent.change(text(), { target: { value: 'Đoạn khác' } })
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    expect(screen.queryByRole('region', { name: 'Kết quả dịch' })).not.toBeInTheDocument()
    expect(translate).toHaveBeenCalledOnce()
  })

  it.each(['resolve', 'reject'] as const)(
    'aborts on navigation and ignores late %s with no history on remount',
    async (outcome) => {
      const request = deferred<TranslateResult>()
      vi.mocked(translate).mockReturnValueOnce(request.promise)
      const { router } = renderPage()
      fill()
      submit()
      const signal = vi.mocked(translate).mock.calls[0]![1]!
      await act(async () => {
        await router.navigate('/elsewhere')
      })
      expect(signal.aborted).toBe(true)
      await act(async () => {
        if (outcome === 'resolve') request.resolve(response)
        else request.reject(new Error('Không thể dịch'))
      })
      await act(async () => {
        await router.navigate('/translate')
      })
      expect(text()).toHaveValue('')
      expect(screen.queryByRole('region', { name: 'Kết quả dịch' })).not.toBeInTheDocument()
      expect(screen.queryByText('Không thể dịch')).not.toBeInTheDocument()
    },
  )

  it('clears private state on account change and old finally cannot unlock the new account request', async () => {
    const old = deferred<TranslateResult>()
    const current = deferred<TranslateResult>()
    vi.mocked(translate).mockReturnValueOnce(old.promise).mockReturnValueOnce(current.promise)
    const view = renderPage()
    fill('Nội dung tài khoản cũ')
    submit()
    const oldSignal = vi.mocked(translate).mock.calls[0]![1]!
    view.changeAuth(auth(8))
    expect(oldSignal.aborted).toBe(true)
    expect(text()).toHaveValue('')
    fill('Nội dung tài khoản mới')
    submit()
    await act(async () => old.resolve(response))
    expect(screen.getByRole('button', { name: 'Đang dịch…' })).toBeDisabled()
    expect(screen.queryByRole('region', { name: 'Kết quả dịch' })).not.toBeInTheDocument()
    await act(async () => current.resolve({ ...response, translated: 'Kết quả mới' }))
    expect(screen.getByText('Kết quả mới')).toBeVisible()
    expect(screen.queryByText(response.translated)).not.toBeInTheDocument()
    view.changeAuth(auth(null))
    expect(screen.queryByRole('textbox')).not.toBeInTheDocument()
    expect(screen.queryByText('Kết quả mới')).not.toBeInTheDocument()
  })

  it('does not write source or result to either storage or URL', async () => {
    const local = vi.spyOn(window.localStorage, 'setItem')
    const session = vi.spyOn(window.sessionStorage, 'setItem')
    try {
      const { router } = renderPage()
      await translated()
      await userEvent.click(screen.getByRole('button', { name: 'Tra từ 读书' }))
      await screen.findByText('đọc sách')
      await userEvent.keyboard('{Escape}')
      expect(local).not.toHaveBeenCalled()
      expect(session).not.toHaveBeenCalled()
      expect(router.state.location).toMatchObject({ pathname: '/translate', search: '', hash: '' })
      await act(async () => {
        await router.navigate('/elsewhere')
      })
      await act(async () => {
        await router.navigate('/translate')
      })
      expect(text()).toHaveValue('')
      expect(local).not.toHaveBeenCalled()
      expect(session).not.toHaveBeenCalled()
    } finally {
      local.mockRestore()
      session.mockRestore()
    }
  })
})
