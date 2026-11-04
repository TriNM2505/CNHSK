import { act, fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { createMemoryRouter, RouterProvider } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import type { RoleCode } from '@/features/auth/types'
import { ApiError } from '@/shared/api'
import { AuthContext, type AuthContextValue } from '@/shared/auth'

import {
  addCard,
  deleteCard,
  deleteDeck,
  getDeck,
  listDueCards,
  reviewCard,
  updateCard,
  updateDeck,
} from '../api/flashcards'
import type { Card, DeckDetail, ReviewResult } from '../types'
import Scr029DeckDetailPage from './Scr029DeckDetailPage'

vi.mock('../api/flashcards', () => ({
  getDeck: vi.fn(),
  addCard: vi.fn(),
  updateCard: vi.fn(),
  deleteCard: vi.fn(),
  deleteDeck: vi.fn(),
  updateDeck: vi.fn(),
  listDueCards: vi.fn(),
  reviewCard: vi.fn(),
}))

const first: Card = {
  id: 601,
  lexemeId: 101,
  front: '今天',
  back: 'jīntiān — hôm nay',
  dueAt: null,
  reviewCount: 0,
  lapseCount: 0,
}
const second: Card = {
  id: 602,
  front: '明天',
  back: 'míngtiān — ngày mai',
  dueAt: '2026-11-03T00:00:00Z',
  reviewCount: 3,
  lapseCount: 1,
}
const deck: DeckDetail = {
  id: 501,
  name: 'Bộ tự soạn',
  description: 'Mô tả mẫu',
  cardCount: 2,
  dueCount: 2,
  createdAt: '2026-11-01T00:00:00Z',
  cards: [first, second],
}
const result: ReviewResult = { cardId: 601, nextReviewAt: '2026-11-12T00:00:00Z', intervalDays: 7 }

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
function renderPage(path = '/library/decks/501', auth = authValue()) {
  const router = createMemoryRouter(
    [
      { path: '/library/decks/:id', element: <Scr029DeckDetailPage /> },
      { path: '/library/decks', element: <h1>Đã về danh sách</h1> },
    ],
    { initialEntries: [path] },
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
const ready = () => screen.findByRole('heading', { name: deck.name })
function cardDraft(front = 'Mặt trước mới', back = 'Mặt sau mới') {
  fireEvent.change(screen.getByLabelText(/^Mặt trước/), { target: { value: front } })
  fireEvent.change(screen.getByLabelText(/^Mặt sau/), { target: { value: back } })
}
function saveCard() {
  fireEvent.submit(screen.getByRole('form', { name: 'Soạn thẻ' }))
}
function failure(status: number, code: string, details?: unknown) {
  return new ApiError({
    status,
    errorCode: code,
    message: 'Lỗi an toàn',
    requestId: 'flashcards-test',
    details,
  })
}
async function review() {
  await ready()
  await userEvent.click(screen.getByRole('button', { name: 'Ôn bộ này' }))
  await screen.findByRole('button', { name: 'Lật thẻ' })
}

beforeEach(() => {
  vi.resetAllMocks()
  vi.mocked(getDeck).mockResolvedValue(deck)
  vi.mocked(addCard).mockResolvedValue(first)
  vi.mocked(updateCard).mockResolvedValue(first)
  vi.mocked(updateDeck).mockResolvedValue(deck)
  vi.mocked(deleteCard).mockResolvedValue(undefined)
  vi.mocked(deleteDeck).mockResolvedValue(undefined)
  vi.mocked(listDueCards).mockResolvedValue([first, second])
  vi.mocked(reviewCard).mockResolvedValue(result)
})

describe('SCR-029 deck management (FR-028, AT-28)', () => {
  it('loads actual cards, review metadata and counts without fetching due cards early', async () => {
    renderPage()
    await ready()
    expect(getDeck).toHaveBeenCalledWith(501, expect.any(AbortSignal))
    expect(listDueCards).not.toHaveBeenCalled()
    const cards = screen.getByRole('region', { name: 'Thẻ trong bộ' })
    expect(within(cards).getAllByRole('listitem')[0]).toHaveTextContent(first.front)
    expect(within(cards).getAllByRole('listitem')[1]).toHaveTextContent('3 lượt ôn · 1 lần quên')
    expect(screen.getByText(/2 thẻ · 2 đến hạn/)).toBeVisible()
    expect(screen.queryByText(/Chia sẻ|công khai|Sao chép/)).not.toBeInTheDocument()
  })

  it('handles PATCH DeckSummary without expecting cards and refreshes detail separately', async () => {
    const summary = {
      id: 501,
      name: 'Tên đã sửa',
      description: 'Mô tả đã sửa',
      cardCount: 2,
      dueCount: 2,
      createdAt: deck.createdAt,
    }
    // The existing client annotation is wider than the contract; PATCH returns no cards.
    vi.mocked(updateDeck).mockResolvedValueOnce(summary as DeckDetail)
    vi.mocked(getDeck)
      .mockResolvedValueOnce(deck)
      .mockResolvedValue({ ...deck, ...summary })
    renderPage()
    await ready()
    fireEvent.change(screen.getByLabelText(/^Tên bộ thẻ/), { target: { value: summary.name } })
    fireEvent.change(screen.getByLabelText('Mô tả'), { target: { value: summary.description } })
    fireEvent.submit(screen.getByRole('form', { name: 'Sửa bộ thẻ' }))
    await screen.findByText('Đã lưu bộ thẻ.')
    expect(updateDeck).toHaveBeenCalledWith(501, {
      name: summary.name,
      description: summary.description,
    })
    await screen.findByRole('heading', { name: summary.name })
    expect(screen.getByText(first.front)).toBeVisible()
  })

  it('adds a manual card and preserves raw text in the snapshot request', async () => {
    renderPage()
    await ready()
    cardDraft('  早上  ', 'Buổi sáng\nTự đặt câu')
    saveCard()
    await screen.findByText('Đã lưu thẻ.')
    expect(addCard).toHaveBeenCalledWith(501, { front: '  早上  ', back: 'Buổi sáng\nTự đặt câu' })
    expect(screen.getByLabelText(/^Mặt trước/)).toHaveValue('')
  })

  it('edits only front/back of a dictionary-linked card without clearing its lexeme', async () => {
    renderPage()
    await ready()
    await userEvent.click(screen.getAllByRole('button', { name: 'Sửa thẻ' })[0]!)
    expect(screen.getByLabelText(/^Mặt trước/)).toHaveValue(first.front)
    cardDraft('Mặt trước đã sửa', 'Mặt sau đã sửa')
    saveCard()
    await screen.findByText('Đã lưu thẻ.')
    expect(updateCard).toHaveBeenCalledWith(501, 601, {
      front: 'Mặt trước đã sửa',
      back: 'Mặt sau đã sửa',
    })
    expect(addCard).not.toHaveBeenCalled()
  })

  it.each([
    { front: '', back: 'a' },
    { front: 'a'.repeat(201), back: 'a' },
    { front: 'a', back: '' },
    { front: 'a', back: 'a'.repeat(2001) },
  ])('keeps invalid card fields without calling API', async ({ front, back }) => {
    renderPage()
    await ready()
    cardDraft(front, back)
    saveCard()
    expect(screen.getByRole('alert')).toBeVisible()
    expect(screen.getByLabelText(/^Mặt trước/)).toHaveValue(front)
    expect(screen.getByLabelText(/^Mặt sau/)).toHaveValue(back)
    expect(addCard).not.toHaveBeenCalled()
  })

  it.each([
    { status: 409, code: 'CARD_DUPLICATE', details: undefined, message: 'Thẻ đã có' },
    {
      status: 422,
      code: 'LIBRARY_LIMIT_REACHED',
      details: { kind: 'CARDS', limit: 123 },
      message: '123 thẻ trong bộ',
    },
    {
      status: 404,
      code: 'LEXEME_NOT_FOUND',
      details: undefined,
      message: 'Mục từ không còn tồn tại',
    },
    { status: 403, code: 'COLLECTION_NOT_OWNED', details: undefined, message: 'không có quyền' },
    { status: 422, code: 'VALIDATION_FAILED', details: undefined, message: 'Lỗi an toàn' },
  ])(
    'preserves card form on $code and does not auto-retry',
    async ({ status, code, details, message }) => {
      vi.mocked(addCard).mockRejectedValueOnce(failure(status, code, details))
      renderPage()
      await ready()
      cardDraft()
      saveCard()
      expect(await screen.findByRole('alert')).toHaveTextContent(message)
      expect(screen.getByLabelText(/^Mặt trước/)).toHaveValue('Mặt trước mới')
      expect(screen.getByLabelText(/^Mặt sau/)).toHaveValue('Mặt sau mới')
      expect(getDeck).toHaveBeenCalledTimes(1)
      expect(addCard).toHaveBeenCalledTimes(1)
      saveCard()
      await screen.findByText('Đã lưu thẻ.')
      expect(addCard).toHaveBeenCalledTimes(2)
    },
  )

  it('keeps the deck form when rename fails', async () => {
    vi.mocked(updateDeck).mockRejectedValueOnce(failure(403, 'COLLECTION_NOT_OWNED'))
    renderPage()
    await ready()
    fireEvent.change(screen.getByLabelText(/^Tên bộ thẻ/), { target: { value: 'Tên đang nhập' } })
    fireEvent.submit(screen.getByRole('form', { name: 'Sửa bộ thẻ' }))
    await screen.findByRole('alert')
    expect(screen.getByLabelText(/^Tên bộ thẻ/)).toHaveValue('Tên đang nhập')
  })

  it('confirms card deletion, restores focus on cancel, and waits for API success', async () => {
    let resolve!: () => void
    vi.mocked(deleteCard).mockImplementationOnce(
      () =>
        new Promise((done) => {
          resolve = done
        }),
    )
    renderPage()
    await ready()
    const trigger = screen.getAllByRole('button', { name: 'Xoá thẻ' })[0]!
    await userEvent.click(trigger)
    await userEvent.click(screen.getByRole('button', { name: 'Huỷ' }))
    expect(trigger).toHaveFocus()
    expect(deleteCard).not.toHaveBeenCalled()
    await userEvent.click(trigger)
    await userEvent.click(screen.getByRole('button', { name: 'Xác nhận xoá' }))
    expect(screen.getByText(first.front)).toBeVisible()
    expect(getDeck).toHaveBeenCalledTimes(1)
    vi.mocked(getDeck).mockResolvedValue({ ...deck, cards: [second], cardCount: 1 })
    await act(async () => resolve())
    await screen.findByText('Đã xoá thẻ.')
    await waitFor(() => expect(screen.queryByText(first.front)).not.toBeInTheDocument())
  })

  it('keeps data after delete failure and reopens confirmation for manual retry', async () => {
    vi.mocked(deleteCard).mockRejectedValueOnce(failure(403, 'COLLECTION_NOT_OWNED'))
    renderPage()
    await ready()
    cardDraft('Bản nháp', 'Cần giữ')
    await userEvent.click(screen.getAllByRole('button', { name: 'Xoá thẻ' })[0]!)
    await userEvent.click(screen.getByRole('button', { name: 'Xác nhận xoá' }))
    await screen.findByRole('alert')
    expect(screen.getByText(first.front)).toBeVisible()
    expect(screen.getByLabelText(/^Mặt trước/)).toHaveValue('Bản nháp')
    await userEvent.click(screen.getByRole('button', { name: 'Thử lại' }))
    expect(screen.getByRole('dialog')).toBeVisible()
    expect(deleteCard).toHaveBeenCalledTimes(1)
  })

  it('confirms deck deletion and navigates only after success', async () => {
    let resolve!: () => void
    vi.mocked(deleteDeck).mockImplementationOnce(
      () =>
        new Promise((done) => {
          resolve = done
        }),
    )
    const { router } = renderPage()
    await ready()
    const trigger = screen.getByRole('button', { name: 'Xoá bộ thẻ' })
    await userEvent.click(trigger)
    await userEvent.click(screen.getByRole('button', { name: 'Huỷ' }))
    expect(trigger).toHaveFocus()
    await userEvent.click(trigger)
    await userEvent.click(screen.getByRole('button', { name: 'Xác nhận xoá' }))
    expect(router.state.location.pathname).toBe('/library/decks/501')
    await act(async () => resolve())
    await screen.findByRole('heading', { name: 'Đã về danh sách' })
    expect(deleteDeck).toHaveBeenCalledWith(501)
  })

  it('paginates cards locally in server order and clamps after the last-page card is deleted', async () => {
    const cards = Array.from({ length: 21 }, (_, index) => ({
      ...first,
      id: 700 + index,
      front: `Thẻ ${index}`,
    }))
    vi.mocked(getDeck).mockResolvedValue({ ...deck, cards, cardCount: 21 })
    renderPage()
    await ready()
    expect(
      within(screen.getByRole('region', { name: 'Thẻ trong bộ' })).getAllByRole('listitem'),
    ).toHaveLength(20)
    await userEvent.click(screen.getByRole('button', { name: 'Sau' }))
    expect(screen.getByText('Thẻ 20')).toBeVisible()
    expect(getDeck).toHaveBeenCalledTimes(1)
    vi.mocked(getDeck).mockResolvedValue({ ...deck, cards: cards.slice(0, 20), cardCount: 20 })
    await userEvent.click(screen.getByRole('button', { name: 'Xoá thẻ' }))
    await userEvent.click(screen.getByRole('button', { name: 'Xác nhận xoá' }))
    await screen.findByText('Trang 1 / 1')
    expect(screen.getByText('Thẻ 0')).toBeVisible()
  })

  it('keeps deck and card drafts when a successful mutation is followed by a failed refresh', async () => {
    vi.mocked(getDeck)
      .mockResolvedValueOnce(deck)
      .mockRejectedValueOnce(failure(503, 'UNAVAILABLE'))
      .mockResolvedValue(deck)
    renderPage()
    await ready()
    fireEvent.change(screen.getByLabelText(/^Tên bộ thẻ/), { target: { value: 'Tên chưa lưu' } })
    cardDraft()
    saveCard()
    await screen.findByRole('heading', { name: 'Chưa tải được bộ thẻ' })
    expect(screen.getByLabelText(/^Tên bộ thẻ/)).toHaveValue('Tên chưa lưu')
    await userEvent.click(screen.getByRole('button', { name: 'Thử lại' }))
    await screen.findByText(first.front)
    expect(screen.getByLabelText(/^Tên bộ thẻ/)).toHaveValue('Tên chưa lưu')
  })

  it.each(['bad', '0', '-1', '1.5', '9007199254740992'])(
    'rejects malformed private deck ID %s without request',
    async (id) => {
      renderPage(`/library/decks/${id}`)
      await screen.findByRole('heading', { name: 'Chưa tải được bộ thẻ' })
      expect(getDeck).not.toHaveBeenCalled()
      expect(screen.getByText(/không có quyền truy cập bộ thẻ/)).toBeVisible()
    },
  )

  it('escapes deck/card HTML as plain text', async () => {
    vi.mocked(getDeck).mockResolvedValue({
      ...deck,
      name: '<script>tên</script>',
      cards: [{ ...first, front: '<script>mặt trước</script>', back: '<img src=x onerror=bad()>' }],
    })
    const view = renderPage()
    await screen.findByText('<script>mặt trước</script>')
    expect(view.container.querySelector('script,img')).toBeNull()
  })
})

describe('SCR-029 review (FR-028, AT-28)', () => {
  it('keeps an empty deck editable and displays its empty state', async () => {
    vi.mocked(getDeck).mockResolvedValue({ ...deck, cards: [], cardCount: 0, dueCount: 0 })
    renderPage()
    await screen.findByRole('heading', { name: 'Bộ thẻ trống' })
    expect(screen.getByRole('button', { name: 'Lưu thẻ' })).toBeEnabled()
    expect(listDueCards).not.toHaveBeenCalled()
  })

  it('starts a batch of 100 cards without reordering or pre-submitting ratings', async () => {
    const cards = Array.from({ length: 100 }, (_, index) => ({
      ...first,
      id: 900 - index,
      front: `Từ thứ ${index}`,
    }))
    vi.mocked(listDueCards).mockResolvedValue(cards)
    renderPage()
    await review()
    expect(screen.getByText('Còn 100 thẻ trong lượt ôn')).toBeVisible()
    expect(screen.getByText('Từ thứ 0')).toBeVisible()
    expect(reviewCard).not.toHaveBeenCalled()
  })

  it.each([
    { label: 'Quên', rating: 'AGAIN' },
    { label: 'Khó', rating: 'HARD' },
    { label: 'Được', rating: 'GOOD' },
    { label: 'Dễ', rating: 'EASY' },
  ])('AT28_requires flip and sends $rating on $label', async ({ label, rating }) => {
    renderPage()
    await review()
    expect(listDueCards).toHaveBeenCalledWith(501, false, expect.any(AbortSignal))
    expect(screen.getByText('Còn 2 thẻ trong lượt ôn')).toBeVisible()
    expect(screen.getByRole('button', { name: label })).toBeDisabled()
    expect(screen.queryByText(first.back)).not.toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'Lật thẻ' }))
    expect(screen.getByText(first.back)).toBeVisible()
    await userEvent.click(screen.getByRole('button', { name: label }))
    await screen.findByText('Còn 1 thẻ trong lượt ôn')
    expect(reviewCard).toHaveBeenCalledTimes(1)
    expect(reviewCard).toHaveBeenCalledWith(601, { rating })
    expect(screen.getByText(second.front)).toBeVisible()
    expect(screen.getByText(/Sau 7 ngày/)).toBeVisible()
    expect(screen.getByText(/Lần ôn kế tiếp/).querySelector('time')).toHaveAttribute(
      'dateTime',
      result.nextReviewAt,
    )
    expect(screen.getByText('Ôn thẻ chỉ đổi lịch của thẻ, không đổi mức thành thạo')).toBeVisible()
    expect(screen.getByRole('button', { name: label })).toBeDisabled()
  })

  it('does not advance or duplicate rating while pending, then completes the batch', async () => {
    let resolve!: (result: ReviewResult) => void
    vi.mocked(reviewCard).mockImplementationOnce(
      () =>
        new Promise((done) => {
          resolve = done
        }),
    )
    renderPage()
    await review()
    await userEvent.click(screen.getByRole('button', { name: 'Lật thẻ' }))
    const good = screen.getByRole('button', { name: 'Được' })
    fireEvent.click(good)
    fireEvent.click(good)
    expect(reviewCard).toHaveBeenCalledTimes(1)
    expect(screen.getByText('Còn 2 thẻ trong lượt ôn')).toBeVisible()
    expect(good).toBeDisabled()
    await act(async () => resolve(result))
    await screen.findByText('Còn 1 thẻ trong lượt ôn')
    await userEvent.click(screen.getByRole('button', { name: 'Lật thẻ' }))
    await userEvent.click(screen.getByRole('button', { name: 'Dễ' }))
    await screen.findByText('Đã ôn xong lượt này.')
    expect(screen.getByText('Còn 0 thẻ trong lượt ôn')).toBeVisible()
    expect(reviewCard).toHaveBeenLastCalledWith(602, { rating: 'EASY' })
    expect(reviewCard).toHaveBeenCalledTimes(2)
  })

  it.each([
    { status: 429, code: 'RATE_LIMITED', message: '45 giây' },
    { status: 503, code: 'UNAVAILABLE', message: 'Lỗi an toàn' },
    { status: 403, code: 'COLLECTION_NOT_OWNED', message: 'không có quyền' },
  ])(
    'retains current card on $code and only retries on user rating',
    async ({ status, code, message }) => {
      vi.mocked(reviewCard).mockRejectedValueOnce(
        new ApiError({
          status,
          errorCode: code,
          message: 'Lỗi an toàn',
          extra: { retryAfterSeconds: 45 },
        }),
      )
      renderPage()
      await review()
      await userEvent.click(screen.getByRole('button', { name: 'Lật thẻ' }))
      await userEvent.click(screen.getByRole('button', { name: 'Được' }))
      expect(await screen.findByRole('alert')).toHaveTextContent(message)
      expect(screen.getByText('Còn 2 thẻ trong lượt ôn')).toBeVisible()
      expect(screen.getByText(first.front)).toBeVisible()
      expect(screen.getByText(first.back)).toBeVisible()
      expect(reviewCard).toHaveBeenCalledTimes(1)
      await userEvent.click(screen.getByRole('button', { name: 'Khó' }))
      await screen.findByText('Còn 1 thẻ trong lượt ôn')
      expect(reviewCard).toHaveBeenLastCalledWith(601, { rating: 'HARD' })
    },
  )

  it('AT28_offers ahead=true when no cards are due', async () => {
    vi.mocked(listDueCards).mockResolvedValueOnce([]).mockResolvedValueOnce([second])
    renderPage()
    await ready()
    await userEvent.click(screen.getByRole('button', { name: 'Ôn bộ này' }))
    await screen.findByRole('heading', { name: 'Không có thẻ đến hạn' })
    expect(reviewCard).not.toHaveBeenCalled()
    await userEvent.click(screen.getByRole('button', { name: 'Ôn trước' }))
    await screen.findByRole('button', { name: 'Lật thẻ' })
    expect(listDueCards).toHaveBeenLastCalledWith(501, true, expect.any(AbortSignal))
    expect(screen.getByText(second.front)).toBeVisible()
  })

  it('has distinct due loading/error states and a manual retry', async () => {
    let reject!: (error: unknown) => void
    vi.mocked(listDueCards).mockImplementationOnce(
      () =>
        new Promise((_, fail) => {
          reject = fail
        }),
    )
    renderPage()
    await ready()
    await userEvent.click(screen.getByRole('button', { name: 'Ôn bộ này' }))
    expect(screen.getByLabelText('Đang tải thẻ ôn')).toBeVisible()
    await act(async () => reject(failure(503, 'UNAVAILABLE')))
    await screen.findByRole('heading', { name: 'Chưa tải được thẻ ôn' })
    expect(screen.queryByRole('heading', { name: 'Không có thẻ đến hạn' })).not.toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'Thử lại' }))
    await screen.findByRole('button', { name: 'Lật thẻ' })
    expect(reviewCard).not.toHaveBeenCalled()
  })

  it('ignores a late due response after leaving review and keeps unfinished form drafts', async () => {
    let resolve!: (cards: Card[]) => void
    vi.mocked(listDueCards).mockImplementationOnce(
      () =>
        new Promise((done) => {
          resolve = done
        }),
    )
    renderPage()
    await ready()
    cardDraft('Bản nháp trước ôn', 'Nội dung cần giữ')
    await userEvent.click(screen.getByRole('button', { name: 'Ôn bộ này' }))
    const signal = vi.mocked(listDueCards).mock.calls[0]![2]!
    await userEvent.click(screen.getByRole('button', { name: 'Về bộ thẻ' }))
    expect(signal.aborted).toBe(true)
    await act(async () => resolve([first, second]))
    await screen.findByRole('form', { name: 'Soạn thẻ' })
    expect(screen.getByLabelText(/^Mặt trước/)).toHaveValue('Bản nháp trước ôn')
    expect(screen.queryByRole('region', { name: 'Ôn bộ thẻ' })).not.toBeInTheDocument()
  })

  it('clears private review state and ignores late rating success on account switch', async () => {
    let resolve!: (result: ReviewResult) => void
    vi.mocked(reviewCard).mockImplementationOnce(
      () =>
        new Promise((done) => {
          resolve = done
        }),
    )
    const view = renderPage()
    await review()
    await userEvent.click(screen.getByRole('button', { name: 'Lật thẻ' }))
    await userEvent.click(screen.getByRole('button', { name: 'Được' }))
    vi.mocked(getDeck).mockResolvedValue({
      ...deck,
      name: 'Bộ của tài khoản mới',
      cards: [],
      cardCount: 0,
      dueCount: 0,
    })
    view.changeAuth(authValue(8))
    await screen.findByRole('heading', { name: 'Bộ của tài khoản mới' })
    await act(async () => resolve(result))
    expect(screen.queryByRole('region', { name: 'Ôn bộ thẻ' })).not.toBeInTheDocument()
    expect(screen.queryByText(first.front)).not.toBeInTheDocument()
    expect(screen.queryByText(/Lần ôn kế tiếp/)).not.toBeInTheDocument()
  })

  it('aborts old detail requests when navigating to another deck', async () => {
    let resolve!: (deck: DeckDetail) => void
    vi.mocked(getDeck)
      .mockImplementationOnce(
        () =>
          new Promise((done) => {
            resolve = done
          }),
      )
      .mockResolvedValueOnce({ ...deck, id: 502, name: 'Bộ thứ hai' })
    const { router } = renderPage()
    await waitFor(() => expect(getDeck).toHaveBeenCalledTimes(1))
    const signal = vi.mocked(getDeck).mock.calls[0]![1]!
    await act(async () => {
      await router.navigate('/library/decks/502')
    })
    await screen.findByRole('heading', { name: 'Bộ thứ hai' })
    expect(signal.aborted).toBe(true)
    await act(async () => resolve(deck))
    expect(screen.queryByRole('heading', { name: deck.name })).not.toBeInTheDocument()
  })

  it.each([null, ['ADMIN'] as RoleCode[]])(
    'does not request private cards without learner access',
    async (roles) => {
      renderPage('/library/decks/501', roles ? authValue(7, roles) : authValue(null))
      await screen.findByText('Cần đăng nhập với quyền học viên để xem bộ thẻ.')
      expect(getDeck).not.toHaveBeenCalled()
      expect(listDueCards).not.toHaveBeenCalled()
    },
  )
})
