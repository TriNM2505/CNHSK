import { act, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { ApiError } from '@/shared/api'

import { getReference, reportReference } from '../api/references'
import { bookReference, mediaReference } from '../test-fixtures'
import Scr055BookDetailPage from './Scr055BookDetailPage'

const auth = vi.hoisted(() => ({ user: null as null | { id: number; roles: string[] } }))
vi.mock('@/shared/auth', () => ({ useCurrentUser: () => auth }))
vi.mock('../api/references', () => ({ getReference: vi.fn(), reportReference: vi.fn() }))
const get = vi.mocked(getReference)

function renderPage(id = '503') {
  return render(
    <MemoryRouter initialEntries={[`/references/books/${id}`]}>
      <Routes>
        <Route path="/references/books/:id" element={<Scr055BookDetailPage />} />
      </Routes>
    </MemoryRouter>,
  )
}

describe('SCR-055 book detail', () => {
  beforeEach(() => {
    auth.user = null
    get.mockReset().mockResolvedValue(bookReference)
    vi.mocked(reportReference).mockReset().mockResolvedValue(undefined)
  })
  it('shows full plain text, book metadata and affiliate label next to the safe link', async () => {
    const text = '<script>alert(1)</script>\nĐoạn mô tả đầy đủ.'
    get.mockResolvedValue({ ...bookReference, description: text })
    const { container } = renderPage()
    await screen.findByRole('heading', { name: bookReference.title })
    expect(
      screen.getByText((_, element) => element?.tagName === 'P' && element.textContent === text),
    ).toBeVisible()
    expect(container.querySelector('script')).toBeNull()
    expect(screen.getByText('Link tiếp thị liên kết')).toBeVisible()
    expect(screen.getByRole('link', { name: 'Mua / tham khảo' })).toHaveAttribute(
      'rel',
      'noopener noreferrer',
    )
    expect(screen.getByRole('link', { name: 'Mua / tham khảo' })).toHaveAttribute(
      'target',
      '_blank',
    )
    expect(screen.getAllByText('Đang cập nhật')).toHaveLength(2)
    expect(screen.getByRole('link', { name: /Danh mục sách tham khảo/ })).toHaveAttribute(
      'href',
      '/references/books',
    )
    expect(screen.queryByRole('button', { name: 'Báo lỗi' })).not.toBeInTheDocument()
  })
  it('does not show an affiliate label for a normal book', async () => {
    get.mockResolvedValue({ ...bookReference, isAffiliate: false })
    renderPage()
    await screen.findByText(bookReference.title)
    expect(screen.queryByText('Link tiếp thị liên kết')).not.toBeInTheDocument()
  })
  it('does not render another reference kind as a book', async () => {
    get.mockResolvedValue(mediaReference)
    renderPage()
    expect(await screen.findByText('Không tìm thấy sách')).toBeVisible()
    expect(screen.queryByText(mediaReference.title)).not.toBeInTheDocument()
    expect(screen.queryByRole('link', { name: 'Mua / tham khảo' })).not.toBeInTheDocument()
  })
  it.each(['abc', '0', '-1', '1.5', '9223372036854775808'])(
    'does not request an invalid ID %s',
    async (id) => {
      renderPage(id)
      await screen.findByText('Không tìm thấy sách')
      expect(get).not.toHaveBeenCalled()
    },
  )
  it('maps a hidden/missing book to not found', async () => {
    get.mockRejectedValue(
      new ApiError({ status: 404, errorCode: 'REFERENCE_NOT_FOUND', message: 'Không tìm thấy.' }),
    )
    renderPage()
    expect(await screen.findByText('Không tìm thấy sách')).toBeVisible()
  })
  it('keeps network errors separate from not found and supports retry', async () => {
    const event = userEvent.setup()
    get.mockRejectedValueOnce(
      new ApiError({ status: 0, errorCode: 'NETWORK_ERROR', message: 'Lỗi mạng.' }),
    )
    renderPage()
    expect(await screen.findByRole('alert')).toHaveTextContent('Lỗi mạng.')
    expect(screen.queryByText('Không tìm thấy sách')).not.toBeInTheDocument()
    await event.click(screen.getByRole('button', { name: 'Thử lại' }))
    expect(await screen.findByText(bookReference.title)).toBeVisible()
  })
  it('allows learner reporting and shows success', async () => {
    const event = userEvent.setup()
    auth.user = { id: 1, roles: ['LEARNER'] }
    renderPage()
    await screen.findByText(bookReference.title)
    await event.click(screen.getByRole('button', { name: 'Báo lỗi' }))
    await event.selectOptions(screen.getByLabelText('Lý do'), 'WRONG_CONTENT')
    await event.click(screen.getByRole('button', { name: 'Gửi báo lỗi' }))
    expect(await screen.findByText('Đã ghi nhận báo lỗi.')).toBeVisible()
    expect(reportReference).toHaveBeenCalledWith(503, { reason: 'WRONG_CONTENT', note: null })
  })
  it('aborts detail request on unmount', async () => {
    get.mockReturnValue(new Promise(() => undefined))
    const { unmount } = renderPage()
    expect(screen.getByRole('status')).toHaveTextContent('Đang tải sách')
    const signal = get.mock.calls[0]![1]
    await act(async () => unmount())
    expect(signal?.aborted).toBe(true)
  })
})
