import { act, fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { createMemoryRouter, RouterProvider } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { ApiError } from '@/shared/api'
import { AuthContext, type AuthContextValue } from '@/shared/auth'

import { bulkApproveReviews, listReviews } from '../api/reviews'
import type { AIQuestionPage, BulkResult } from '../types'
import Scr058AIReviewQueuePage from './Scr058AIReviewQueuePage'

vi.mock('../api/reviews', () => ({ listReviews: vi.fn(), bulkApproveReviews: vi.fn() }))
const list = vi.mocked(listReviews)
const bulk = vi.mocked(bulkApproveReviews)
const page = (viewed = true): AIQuestionPage => ({
  page: 0,
  size: 20,
  totalElements: 2,
  items: [
    {
      id: 901,
      stemSummary: 'Câu tự soạn thứ nhất.',
      hskLevel: 1,
      labels: [{ kid: 'TV1-901', role: 'PRIMARY' }],
      generatedAt: '2026-11-17T00:00:00Z',
      runId: null,
      viewedByMe: viewed,
    },
    {
      id: 902,
      stemSummary: 'Câu tự soạn thứ hai.',
      hskLevel: 2,
      labels: [{ kid: 'NP2-901', role: 'PRIMARY' }],
      generatedAt: '2026-11-17T01:00:00Z',
      runId: 12,
      viewedByMe: true,
    },
  ],
})
function deferred<T>() {
  let resolve!: (value: T) => void
  const promise = new Promise<T>((done) => {
    resolve = done
  })
  return { promise, resolve }
}
function renderPage(entry = '/content/ai-review', actor = 7) {
  const auth: AuthContextValue = {
    user: {
      id: actor,
      displayName: 'Kiểm thử',
      email: 'test@example.test',
      emailVerified: true,
      roles: ['TEACHER'],
    },
    status: 'authenticated',
    error: null,
    setAuthenticatedUser: vi.fn(),
    reloadCurrentUser: vi.fn(),
    signOut: vi.fn(),
  }
  const router = createMemoryRouter(
    [
      { path: '/content/ai-review', element: <Scr058AIReviewQueuePage /> },
      { path: '/content/questions/:id', element: <h1>Chi tiết dùng GET riêng</h1> },
    ],
    { initialEntries: [entry] },
  )
  const result = render(
    <AuthContext.Provider value={auth}>
      <RouterProvider router={router} />
    </AuthContext.Provider>,
  )
  return { ...result, router, auth }
}
async function loaded() {
  return screen.findAllByLabelText('Chọn câu #901')
}
async function submitBulk() {
  fireEvent.click(screen.getByRole('button', { name: 'Duyệt các câu đã chọn' }))
  fireEvent.click(await screen.findByRole('button', { name: 'Xác nhận duyệt hàng loạt' }))
}
beforeEach(() => {
  list.mockReset().mockResolvedValue(page())
  bulk.mockReset().mockResolvedValue({ results: [{ id: 901, outcome: 'APPROVED' }] })
})

describe('SCR-058 authoritative review queue', () => {
  it('keeps selected IDs unique and sends no write without a selection', async () => {
    renderPage()
    const choices = await loaded()
    expect(screen.getByRole('button', { name: 'Duyệt các câu đã chọn' })).toBeDisabled()
    fireEvent.click(choices[0]!)
    expect(choices[1]).toBeChecked()
    fireEvent.click(choices[1]!)
    expect(choices[0]).not.toBeChecked()
    expect(screen.getByRole('button', { name: 'Duyệt các câu đã chọn' })).toBeDisabled()
    expect(screen.queryByLabelText(/Chọn tất cả/)).not.toBeInTheDocument()
    expect(bulk).not.toHaveBeenCalled()
  })
  it('loads via queue contract and disables unviewed selection without changing it on open', async () => {
    list.mockResolvedValue(page(false))
    const { router } = renderPage('/content/ai-review?hsk=2&page=0&kid=TV1-901')
    for (const checkbox of await loaded()) expect(checkbox).toBeDisabled()
    expect(list).toHaveBeenCalledWith(
      { kid: 'TV1-901', hsk: 2, page: 0, size: 20, from: undefined, to: undefined },
      expect.any(AbortSignal),
    )
    fireEvent.click(screen.getAllByRole('button', { name: 'Mở câu #901' })[0]!)
    expect(router.state.location.pathname).toBe('/content/questions/901')
    expect(new URLSearchParams(router.state.location.search).get('reviewQueue')).toBe('1')
    expect(bulk).not.toHaveBeenCalled()
  })
  it('only enables expired/outage markers after an authoritative refresh says true', async () => {
    list
      .mockResolvedValueOnce(page(false))
      .mockResolvedValueOnce(page(true))
      .mockResolvedValueOnce(page(false))
    renderPage()
    expect((await loaded())[0]).toBeDisabled()
    fireEvent.click(screen.getByRole('button', { name: 'Làm mới hàng đợi' }))
    await waitFor(() => expect(screen.getAllByLabelText('Chọn câu #901')[0]).toBeEnabled())
    fireEvent.click(screen.getAllByLabelText('Chọn câu #901')[0]!)
    expect(screen.getByRole('button', { name: 'Duyệt các câu đã chọn' })).toBeEnabled()
    fireEvent(window, new Event('focus'))
    await waitFor(() => expect(list).toHaveBeenCalledTimes(3))
    expect(screen.getAllByLabelText('Chọn câu #901')[0]).toBeDisabled()
    expect(screen.getByRole('button', { name: 'Duyệt các câu đã chọn' })).toBeDisabled()
  })
  it('clears actor-specific selection and refetches on actor change', async () => {
    const rendered = renderPage()
    fireEvent.click((await loaded())[0]!)
    list.mockResolvedValue(page(false))
    rendered.rerender(
      <AuthContext.Provider value={{ ...rendered.auth, user: { ...rendered.auth.user!, id: 8 } }}>
        <RouterProvider router={rendered.router} />
      </AuthContext.Provider>,
    )
    await waitFor(() => expect(list).toHaveBeenCalledTimes(2))
    await waitFor(() => expect(screen.getAllByLabelText('Chọn câu #901')[0]).toBeDisabled())
    expect(screen.getByRole('button', { name: 'Duyệt các câu đã chọn' })).toBeDisabled()
  })
  it('resets selection and page when applying safe Vietnam date filters', async () => {
    const { router } = renderPage()
    fireEvent.click((await loaded())[0]!)
    fireEvent.change(screen.getByLabelText('Từ ngày (giờ Việt Nam)'), {
      target: { value: '2026-11-17' },
    })
    fireEvent.change(screen.getByLabelText('Đến ngày (giờ Việt Nam)'), {
      target: { value: '2026-11-17' },
    })
    fireEvent.click(screen.getByRole('button', { name: 'Áp dụng bộ lọc' }))
    await waitFor(() => expect(list).toHaveBeenCalledTimes(2))
    expect(list.mock.calls[1]![0]).toMatchObject({
      page: 0,
      from: '2026-11-16T17:00:00.000Z',
      to: '2026-11-17T16:59:59.999Z',
    })
    expect(new URLSearchParams(router.state.location.search).get('page')).toBe('0')
    expect(screen.getByRole('button', { name: 'Duyệt các câu đã chọn' })).toBeDisabled()
  })
  it('explains invalid KID/range locally and focuses the filter error', async () => {
    renderPage()
    await loaded()
    fireEvent.change(screen.getByLabelText('Mã năng lực (KID)'), {
      target: { value: '词'.repeat(21) },
    })
    fireEvent.click(screen.getByRole('button', { name: 'Áp dụng bộ lọc' }))
    expect(await screen.findByText(/KID tối đa 20/)).toBeVisible()
    expect(list).toHaveBeenCalledTimes(1)
    await waitFor(() => expect(screen.getByLabelText('Mã năng lực (KID)')).toHaveFocus())
  })
  it('does not let an aborted older request overwrite newer filters', async () => {
    const old = deferred<AIQuestionPage>()
    list.mockReturnValueOnce(old.promise)
    const { router } = renderPage()
    expect(screen.getByLabelText('Đang tải hàng đợi')).toBeVisible()
    await act(async () => router.navigate('/content/ai-review?hsk=2'))
    await loaded()
    expect(list.mock.calls[0]![1]?.aborted).toBe(true)
    await act(async () => old.resolve(page(false)))
    expect(screen.getAllByLabelText('Chọn câu #901')[0]).toBeEnabled()
  })
  it('reports each outcome in response order and retains it after approved rows disappear', async () => {
    const report: BulkResult = {
      results: [
        { id: 901, outcome: 'APPROVED' },
        { id: 902, outcome: 'NOT_VIEWED' },
        { id: 903, outcome: 'NOT_PENDING' },
        { id: 904, outcome: 'MALFORMED' },
      ],
    }
    bulk.mockResolvedValue(report)
    list
      .mockResolvedValueOnce(page())
      .mockResolvedValue({ items: [], page: 0, size: 20, totalElements: 0 })
    renderPage()
    fireEvent.click((await loaded())[0]!)
    await submitBulk()
    const region = await screen.findByRole('region', { name: 'Kết quả duyệt hàng loạt' })
    expect(
      within(region)
        .getAllByRole('listitem')
        .map((item) => item.textContent),
    ).toEqual([
      '#901: Đã duyệt',
      '#902: Chưa xem hoặc lượt xem đã hết hạn',
      '#903: Không còn chờ duyệt',
      '#904: Nội dung chưa hợp lệ',
    ])
    expect(await screen.findByText('Không có câu hỏi chờ duyệt')).toBeVisible()
    expect(region).toHaveFocus()
    expect(bulk).toHaveBeenCalledWith([901], expect.any(AbortSignal))
    expect(list).toHaveBeenCalledTimes(2)
  })
  it('requires explicit confirmation and blocks duplicate writes while pending', async () => {
    const response = deferred<BulkResult>()
    bulk.mockReturnValue(response.promise)
    renderPage()
    fireEvent.click((await loaded())[0]!)
    fireEvent.click(screen.getByRole('button', { name: 'Duyệt các câu đã chọn' }))
    const dialog = await screen.findByRole('dialog')
    expect(within(dialog).getByRole('button', { name: 'Huỷ' })).toHaveFocus()
    fireEvent.click(within(dialog).getByRole('button', { name: 'Huỷ' }))
    expect(bulk).not.toHaveBeenCalled()
    await submitBulk()
    await waitFor(() => expect(bulk).toHaveBeenCalledTimes(1))
    expect(screen.getByRole('button', { name: 'Duyệt các câu đã chọn' })).toBeDisabled()
    await act(async () => response.resolve({ results: [] }))
  })
  it('explains uncertain partial 500, never replays POST and blocks until reload succeeds', async () => {
    bulk.mockRejectedValue(
      new ApiError({
        status: 500,
        errorCode: 'INTERNAL_ERROR',
        message: 'Không hoàn tất.',
        requestId: 'req-partial',
      }),
    )
    list
      .mockResolvedValueOnce(page())
      .mockRejectedValueOnce(
        new ApiError({ status: 503, errorCode: 'NETWORK_ERROR', message: 'Tải lại thất bại.' }),
      )
      .mockResolvedValueOnce(page(false))
    renderPage()
    fireEvent.click((await loaded())[0]!)
    await submitBulk()
    expect(await screen.findByText(/Một số câu trước đó có thể đã được duyệt/)).toBeVisible()
    expect(await screen.findByText('Không thể tải hàng đợi')).toBeVisible()
    expect(screen.getByText('Mã yêu cầu: req-partial')).toBeVisible()
    expect(screen.queryByRole('button', { name: 'Duyệt các câu đã chọn' })).not.toBeInTheDocument()
    expect(bulk).toHaveBeenCalledTimes(1)
    fireEvent.click(screen.getByRole('button', { name: 'Tải lại hàng đợi' }))
    await loaded()
    expect(screen.getByRole('button', { name: 'Duyệt các câu đã chọn' })).toBeDisabled()
    expect(screen.getAllByLabelText('Chọn câu #901')[0]).toBeDisabled()
    expect(bulk).toHaveBeenCalledTimes(1)
  })
  it('corrects an emptied final page after queue totals change', async () => {
    list
      .mockResolvedValueOnce({ items: [], page: 3, size: 20, totalElements: 1 })
      .mockResolvedValueOnce(page())
    const { router } = renderPage('/content/ai-review?page=3')
    await loaded()
    expect(new URLSearchParams(router.state.location.search).get('page')).toBe('0')
    expect(list).toHaveBeenCalledTimes(2)
  })
  it('keeps real empty and retryable error distinct from loading', async () => {
    list
      .mockRejectedValueOnce(
        new ApiError({
          status: 422,
          errorCode: 'VALIDATION_FAILED',
          message: 'Bộ lọc không hợp lệ.',
          requestId: 'req-filter',
        }),
      )
      .mockResolvedValueOnce({ items: [], page: 0, size: 20, totalElements: 0 })
    renderPage()
    expect(await screen.findByText('Không thể tải hàng đợi')).toBeVisible()
    expect(screen.getByText('Bộ lọc không hợp lệ.')).toBeVisible()
    fireEvent.click(screen.getByRole('button', { name: 'Thử lại' }))
    expect(await screen.findByText('Không có câu hỏi chờ duyệt')).toBeVisible()
    expect(screen.queryByLabelText('Đang tải hàng đợi')).not.toBeInTheDocument()
  })
})
