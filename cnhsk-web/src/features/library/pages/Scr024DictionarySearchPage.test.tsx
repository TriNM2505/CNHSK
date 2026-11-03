import { act, fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { createMemoryRouter, RouterProvider } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import type { CurrentUser, RoleCode } from '@/features/auth/types'
import { ApiError } from '@/shared/api'
import { AuthContext, type AuthContextValue } from '@/shared/auth'

import { listRadicals, searchByRadical, searchDictionary } from '../api/dictionary'
import type { SearchResult } from '../types'
import Scr024DictionarySearchPage from './Scr024DictionarySearchPage'

vi.mock('../api/dictionary', () => ({
  searchDictionary: vi.fn(),
  listRadicals: vi.fn(),
  searchByRadical: vi.fn(),
  getLexeme: vi.fn(),
}))

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
function renderPage(
  path = '/dictionary',
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
    [{ path: '/dictionary', element: <Scr024DictionarySearchPage /> }],
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
function selectMode(type: string) {
  fireEvent.change(screen.getByLabelText('Kiểu tra'), { target: { value: type } })
}
function enterQuery(q: string) {
  fireEvent.change(screen.getByLabelText(/Nội dung cần tra/), { target: { value: q } })
}
function submit() {
  fireEvent.click(screen.getByRole('button', { name: 'Tra cứu' }))
}

beforeEach(() => {
  vi.resetAllMocks()
  dictionaryMock.mockResolvedValue(result)
  vi.mocked(listRadicals).mockResolvedValue([{ radical: '日', charCount: 2 }])
  radicalMock.mockResolvedValue(result)
})

describe('SCR-024 dictionary search (FR-023, AT-23)', () => {
  it('does not search before the visitor submits a query', async () => {
    renderPage()
    expect(screen.getByRole('heading', { name: 'Tra từ điển' })).toBeVisible()
    await waitFor(() =>
      expect(screen.getByText('Nhập nội dung rồi bấm Tra cứu để bắt đầu.')).toBeVisible(),
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
    expect(await screen.findByRole('link', { name: /日/ })).toBeVisible()
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
    expect(await screen.findByRole('link', { name: /日/ })).toBeVisible()
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
    renderPage(`/dictionary?type=RADICAL&${path}`)
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
    { type: 'HANZI', q: '   ', error: 'Nhập nội dung cần tra.' },
    { type: 'MEANING', q: 'a', error: 'Nghĩa Việt cần ít nhất 2 ký tự.' },
  ])('validates $type without erasing input', ({ type, q, error }) => {
    renderPage()
    selectMode(type)
    enterQuery(q)
    submit()
    expect(screen.getByText(error)).toBeVisible()
    expect(screen.getByLabelText(/Nội dung cần tra/)).toHaveValue(q)
    expect(dictionaryMock).not.toHaveBeenCalled()
  })

  it('FR023_preserves the query when no matches are found', async () => {
    dictionaryMock.mockResolvedValue({ items: [], total: 0 })
    renderPage()
    enterQuery('từ tự soạn chưa có')
    selectMode('MEANING')
    submit()
    expect(await screen.findByText('Không tìm thấy — thử kiểu tra khác')).toBeVisible()
    expect(screen.getByLabelText(/Nội dung cần tra/)).toHaveValue('từ tự soạn chưa có')
  })

  it.each([
    { type: 'HANZI', q: '日'.repeat(11) },
    { type: 'PINYIN', q: 'a'.repeat(51) },
    { type: 'MEANING', q: 'b'.repeat(51) },
  ])('lets the server return USE_TRANSLATE for long $type queries', async ({ type, q }) => {
    dictionaryMock.mockResolvedValue({ items: [], total: 0, hint: 'USE_TRANSLATE' })
    renderPage('/dictionary', ['LEARNER'])
    selectMode(type)
    enterQuery(q)
    submit()
    expect(await screen.findByRole('link', { name: 'Chuyển sang dịch câu' })).toHaveAttribute(
      'href',
      '/translate',
    )
    expect(dictionaryMock).toHaveBeenCalledWith({ type, q, page: 0 }, expect.any(AbortSignal))
  })

  it.each([
    { roles: null, visible: false },
    { roles: ['ADMIN'] as RoleCode[], visible: false },
    { roles: ['LEARNER'] as RoleCode[], visible: true },
    { roles: ['ADMIN', 'LEARNER'] as RoleCode[], visible: true },
  ])('FR023_only LEARNER gets the translation action: $roles', async ({ roles, visible }) => {
    dictionaryMock.mockResolvedValue({ items: [], total: 0, hint: 'USE_TRANSLATE' })
    renderPage('/dictionary?type=HANZI&q=日', roles)
    await screen.findByText('Không tìm thấy — thử kiểu tra khác')
    expect(screen.queryByRole('link', { name: 'Chuyển sang dịch câu' }) !== null).toBe(visible)
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
    const path = '/dictionary?type=PINYIN&q=ri4&page=2#results'
    renderPage(path)
    const login = await screen.findByRole('link', { name: 'Đăng nhập để tra tiếp' })
    expect(
      new URL(login.getAttribute('href')!, 'https://test.invalid').searchParams.get('redirect'),
    ).toBe(path)
    expect(screen.getByText('Thử lại sau 45 giây.')).toBeVisible()
    expect(screen.getByText('Mã lỗi: dictionary-429')).toBeVisible()
    expect(screen.getByLabelText(/Nội dung cần tra/)).toHaveValue('ri4')
  })

  it('does not suggest login to an authenticated learner on 429', async () => {
    dictionaryMock.mockRejectedValue(
      new ApiError({ status: 429, errorCode: 'RATE_LIMITED', message: 'Giới hạn' }),
    )
    renderPage('/dictionary?q=日', ['LEARNER'])
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
    renderPage('/dictionary?type=HANZI&q=日')
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
    renderPage('/dictionary?type=RADICAL&strokes=4')
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
    const { router } = renderPage('/dictionary?type=PINYIN&q=ri4&page=1')
    expect(await screen.findByText('Trang 2 / 3')).toBeVisible()
    const links = within(screen.getByRole('region', { name: 'Kết quả tra cứu' })).getAllByRole(
      'link',
    )
    expect(links[0]).toHaveTextContent('月')
    expect(links[1]).toHaveTextContent('日')
    expect(
      new URL(links[0]!.getAttribute('href')!, 'https://test.invalid').searchParams.get('from'),
    ).toBe('/dictionary?type=PINYIN&q=ri4&page=1')
    await userEvent.click(screen.getByRole('button', { name: 'Sau' }))
    await waitFor(() =>
      expect(dictionaryMock).toHaveBeenLastCalledWith(
        { type: 'PINYIN', q: 'ri4', page: 2 },
        expect.any(AbortSignal),
      ),
    )
    expect(new URLSearchParams(router.state.location.search).get('page')).toBe('2')
    expect(screen.getByLabelText(/Nội dung cần tra/)).toHaveValue('ri4')
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
    renderPage('/dictionary?type=HANZI&q=日')
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
      renderPage(`/dictionary?type=HANZI&q=日&page=${page}`)
      await screen.findByText('rì')
      expect(dictionaryMock).toHaveBeenCalledWith(
        { type: 'HANZI', q: '日', page: 0 },
        expect.any(AbortSignal),
      )
    },
  )
})
