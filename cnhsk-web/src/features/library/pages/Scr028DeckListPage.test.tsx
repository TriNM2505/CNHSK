import { act, fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { createMemoryRouter, RouterProvider } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import type { RoleCode } from '@/features/auth/types'
import { getTopics } from '@/features/vocab/api/topics'
import { ApiError } from '@/shared/api'
import { AuthContext, type AuthContextValue } from '@/shared/auth'

import { createDeck, listDecks } from '../api/flashcards'
import type { DeckDetail, DeckSummary } from '../types'
import Scr028DeckListPage from './Scr028DeckListPage'

vi.mock('../api/flashcards', () => ({ listDecks: vi.fn(), createDeck: vi.fn() }))
vi.mock('@/features/vocab/api/topics', () => ({ getTopics: vi.fn() }))

const deck: DeckDetail = {
  id: 501,
  name: 'Bộ tự soạn',
  description: 'Mô tả riêng',
  cardCount: 2,
  dueCount: 1,
  createdAt: '2026-11-01T00:00:00Z',
  cards: [],
}
const topic = {
  id: 11,
  name: 'Chủ đề tự soạn',
  totalWords: 3,
  hskLevel: 1 as const,
  code: 'original',
  status: 'NOT_STARTED' as const,
  learnedWords: 0,
  percent: 0,
  bestTestPercent: null,
}

function authValue(id: number | null = 7, roles: RoleCode[] = ['LEARNER']): AuthContextValue {
  return {
    user:
      id === null
        ? null
        : {
            id,
            displayName: 'Người học thử',
            email: 'learner@test.invalid',
            emailVerified: true,
            roles,
          },
    status: id === null ? 'guest' : 'authenticated',
    error: null,
    reloadCurrentUser: async () => undefined,
    setAuthenticatedUser: () => undefined,
    signOut: async () => undefined,
  }
}
function renderPage(auth = authValue()) {
  const router = createMemoryRouter(
    [
      { path: '/library/decks', element: <Scr028DeckListPage /> },
      { path: '/library/decks/:id', element: <h1>Đã mở bộ thẻ</h1> },
    ],
    { initialEntries: ['/library/decks'] },
  )
  const view = render(
    <AuthContext.Provider value={auth}>
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
function name(value = 'Bộ mới') {
  fireEvent.change(screen.getByLabelText(/^Tên bộ thẻ/), { target: { value } })
}
function submit() {
  fireEvent.submit(screen.getByRole('form', { name: 'Tạo bộ thẻ' }))
}
beforeEach(() => {
  vi.resetAllMocks()
  vi.mocked(listDecks).mockResolvedValue([deck])
  vi.mocked(createDeck).mockResolvedValue(deck)
  vi.mocked(getTopics).mockResolvedValue([topic])
})

describe('SCR-028 deck list (FR-027, AT-27)', () => {
  it('loads once, displays actual counts and static limits without quota or topic requests', async () => {
    renderPage()
    expect(await screen.findByText('2 thẻ · 1 đến hạn')).toBeVisible()
    expect(listDecks).toHaveBeenCalledTimes(1)
    expect(getTopics).not.toHaveBeenCalled()
    expect(screen.getByText(/50 bộ thẻ/)).toHaveTextContent('500 thẻ')
    expect(screen.getByRole('link', { name: 'Mở bộ thẻ' })).toHaveAttribute(
      'href',
      '/library/decks/501',
    )
    expect(screen.queryByRole('switch')).not.toBeInTheDocument()
    expect(screen.queryByText(/Chia sẻ|công khai/)).not.toBeInTheDocument()
  })

  it('creates an empty private deck and navigates to the returned ID; duplicate names are allowed', async () => {
    const { router } = renderPage()
    await screen.findByText(deck.name)
    name(deck.name)
    fireEvent.change(screen.getByLabelText('Mô tả'), { target: { value: 'Mô tả mới' } })
    submit()
    await screen.findByRole('heading', { name: 'Đã mở bộ thẻ' })
    expect(createDeck).toHaveBeenCalledWith({ name: deck.name, description: 'Mô tả mới' })
    expect(router.state.location.pathname).toBe('/library/decks/501')
    expect(getTopics).not.toHaveBeenCalled()
  })

  it('AT27_creates from a loaded topic by sending only topicId and no copied content/schedule', async () => {
    renderPage()
    name()
    fireEvent.change(screen.getByLabelText('Nguồn tạo bộ thẻ'), { target: { value: 'topic' } })
    await screen.findByRole('option', { name: 'Chủ đề tự soạn (3 từ)' })
    expect(getTopics).toHaveBeenCalledWith(undefined, expect.any(AbortSignal))
    submit()
    expect(screen.getByRole('alert')).toHaveTextContent('Chọn một chủ đề')
    expect(createDeck).not.toHaveBeenCalled()
    fireEvent.change(screen.getByLabelText(/^Chủ đề/), { target: { value: '11' } })
    submit()
    await screen.findByRole('heading', { name: 'Đã mở bộ thẻ' })
    expect(createDeck).toHaveBeenCalledWith({ name: 'Bộ mới', description: null, topicId: 11 })
  })

  it.each([
    { value: '', description: '', error: '1–100' },
    { value: 'a'.repeat(101), description: '', error: '1–100' },
    { value: 'Bộ mới', description: 'b'.repeat(501), error: '500' },
  ])('keeps invalid form input at contract boundaries', async ({ value, description, error }) => {
    renderPage()
    name(value)
    fireEvent.change(screen.getByLabelText('Mô tả'), { target: { value: description } })
    submit()
    expect(screen.getByRole('alert')).toHaveTextContent(error)
    expect(screen.getByLabelText(/^Tên bộ thẻ/)).toHaveValue(value)
    expect(screen.getByLabelText('Mô tả')).toHaveValue(description)
    expect(createDeck).not.toHaveBeenCalled()
  })

  it.each([
    {
      status: 422,
      code: 'LIBRARY_LIMIT_REACHED',
      details: { kind: 'DECKS', limit: 37 },
      message: '37 bộ thẻ',
    },
    {
      status: 404,
      code: 'TOPIC_NOT_FOUND',
      details: undefined,
      message: 'Chủ đề không còn tồn tại',
    },
    { status: 403, code: 'COLLECTION_NOT_OWNED', details: undefined, message: 'không có quyền' },
  ])(
    'preserves form on $code and only retries after submit',
    async ({ status, code, details, message }) => {
      vi.mocked(createDeck).mockRejectedValueOnce(
        new ApiError({ status, errorCode: code, message: 'Lỗi an toàn', details }),
      )
      renderPage()
      await screen.findByText(deck.name)
      name('Bản nháp')
      submit()
      expect(await screen.findByRole('alert')).toHaveTextContent(message)
      expect(screen.getByLabelText(/^Tên bộ thẻ/)).toHaveValue('Bản nháp')
      expect(createDeck).toHaveBeenCalledTimes(1)
      expect(listDecks).toHaveBeenCalledTimes(1)
      submit()
      await screen.findByRole('heading', { name: 'Đã mở bộ thẻ' })
      expect(createDeck).toHaveBeenCalledTimes(2)
    },
  )

  it('supports list error/retry and empty without discarding the independent form', async () => {
    vi.mocked(listDecks)
      .mockRejectedValueOnce(
        new ApiError({ status: 503, errorCode: 'UNAVAILABLE', message: 'Chưa tải được' }),
      )
      .mockResolvedValueOnce([])
    renderPage()
    name('Bản nháp đang nhập')
    await screen.findByRole('heading', { name: 'Chưa tải được bộ thẻ' })
    expect(screen.queryByRole('heading', { name: 'Chưa có bộ thẻ' })).not.toBeInTheDocument()
    await userEvent.click(
      within(screen.getByRole('region', { name: 'Danh sách bộ thẻ' })).getByRole('button', {
        name: 'Thử lại',
      }),
    )
    await screen.findByRole('heading', { name: 'Chưa có bộ thẻ' })
    expect(screen.getByLabelText(/^Tên bộ thẻ/)).toHaveValue('Bản nháp đang nhập')
  })

  it('supports topic error/retry and empty without blocking empty-deck creation', async () => {
    vi.mocked(getTopics)
      .mockRejectedValueOnce(
        new ApiError({ status: 503, errorCode: 'UNAVAILABLE', message: 'Chủ đề lỗi' }),
      )
      .mockResolvedValueOnce([])
    renderPage()
    name()
    fireEvent.change(screen.getByLabelText('Nguồn tạo bộ thẻ'), { target: { value: 'topic' } })
    await screen.findByRole('heading', { name: 'Chưa tải được chủ đề' })
    await userEvent.click(screen.getByRole('button', { name: 'Thử lại' }))
    await screen.findByText(/Chưa có chủ đề để tạo bộ/)
    fireEvent.change(screen.getByLabelText('Nguồn tạo bộ thẻ'), { target: { value: 'empty' } })
    submit()
    await screen.findByRole('heading', { name: 'Đã mở bộ thẻ' })
    expect(createDeck).toHaveBeenCalledWith({ name: 'Bộ mới', description: null })
  })

  it('uses client pages of 20 and preserves server order', async () => {
    vi.mocked(listDecks).mockResolvedValue(
      Array.from({ length: 21 }, (_, index) => ({ ...deck, id: 50 - index, name: `Bộ ${index}` })),
    )
    renderPage()
    await screen.findByText('Trang 1 / 2')
    const region = screen.getByRole('region', { name: 'Danh sách bộ thẻ' })
    expect(within(region).getAllByRole('heading')[0]).toHaveTextContent('Bộ 0')
    expect(within(region).getAllByRole('listitem')).toHaveLength(20)
    await userEvent.click(screen.getByRole('button', { name: 'Sau' }))
    expect(within(region).getByRole('heading')).toHaveTextContent('Bộ 20')
    expect(listDecks).toHaveBeenCalledTimes(1)
  })

  it('deduplicates create and ignores its late success after account switch', async () => {
    let resolve!: (deck: DeckDetail) => void
    vi.mocked(createDeck).mockImplementationOnce(
      () =>
        new Promise((done) => {
          resolve = done
        }),
    )
    const view = renderPage()
    await screen.findByText(deck.name)
    name('Bản nháp tài khoản cũ')
    submit()
    submit()
    expect(createDeck).toHaveBeenCalledTimes(1)
    vi.mocked(listDecks).mockResolvedValue([])
    view.changeAuth(authValue(8))
    await screen.findByRole('heading', { name: 'Chưa có bộ thẻ' })
    expect(screen.getByLabelText(/^Tên bộ thẻ/)).toHaveValue('')
    await act(async () => resolve(deck))
    expect(view.router.state.location.pathname).toBe('/library/decks')
  })

  it('aborts topic reads and ignores old results when source changes', async () => {
    let resolve!: (topics: (typeof topic)[]) => void
    vi.mocked(getTopics).mockImplementationOnce(
      () =>
        new Promise((done) => {
          resolve = done
        }),
    )
    renderPage()
    fireEvent.change(screen.getByLabelText('Nguồn tạo bộ thẻ'), { target: { value: 'topic' } })
    await waitFor(() => expect(getTopics).toHaveBeenCalledOnce())
    const signal = vi.mocked(getTopics).mock.calls[0]![1]!
    fireEvent.change(screen.getByLabelText('Nguồn tạo bộ thẻ'), { target: { value: 'empty' } })
    expect(signal.aborted).toBe(true)
    await act(async () => resolve([topic]))
    expect(screen.queryByLabelText(/^Chủ đề/)).not.toBeInTheDocument()
  })

  it('escapes deck names and descriptions', async () => {
    vi.mocked(listDecks).mockResolvedValue([
      { ...deck, name: '<script>mẫu</script>', description: '<img src=x onerror=bad()>' },
    ])
    const view = renderPage()
    await screen.findByText('<script>mẫu</script>')
    expect(view.container.querySelector('script,img')).toBeNull()
  })

  it.each([null, ['ADMIN'] as RoleCode[]])(
    'does not request private data without learner access',
    async (roles) => {
      renderPage(roles ? authValue(7, roles) : authValue(null))
      await screen.findByText('Cần đăng nhập với quyền học viên để xem bộ thẻ.')
      expect(listDecks).not.toHaveBeenCalled()
      expect(getTopics).not.toHaveBeenCalled()
    },
  )

  it('aborts the previous account list request and does not display late private data', async () => {
    let resolve!: (decks: DeckSummary[]) => void
    vi.mocked(listDecks)
      .mockImplementationOnce(
        () =>
          new Promise((done) => {
            resolve = done
          }),
      )
      .mockResolvedValueOnce([])
    const view = renderPage()
    await waitFor(() => expect(listDecks).toHaveBeenCalledOnce())
    const signal = vi.mocked(listDecks).mock.calls[0]![0]!
    view.changeAuth(authValue(8))
    await screen.findByRole('heading', { name: 'Chưa có bộ thẻ' })
    expect(signal.aborted).toBe(true)
    await act(async () => resolve([deck]))
    expect(screen.queryByText(deck.name)).not.toBeInTheDocument()
  })
})
