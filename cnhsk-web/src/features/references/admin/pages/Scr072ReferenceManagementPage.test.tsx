import { act, render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { ApiError } from '@/shared/api'
import {
  createReference,
  getAdminReference,
  listAdminReferences,
  transitionReference,
  updateReference,
} from '../api/references'
import { adminPage, draftReference, hiddenReference, publishedReference } from '../test-fixtures'
import type { AdminReference, AdminReferencePage } from '../types'
import Scr072ReferenceManagementPage from './Scr072ReferenceManagementPage'

const auth = vi.hoisted(() => ({ user: { id: 1, roles: ['CONTENT_EDITOR'] } }))
vi.mock('@/shared/auth', () => ({ useCurrentUser: () => auth }))
vi.mock('../api/references', () => ({
  listAdminReferences: vi.fn(),
  getAdminReference: vi.fn(),
  createReference: vi.fn(),
  updateReference: vi.fn(),
  transitionReference: vi.fn(),
}))
const list = vi.mocked(listAdminReferences)
function row(title: string) {
  return within(screen.getByRole('rowheader', { name: title }).closest('tr')!)
}

describe('SCR-072 reference management', () => {
  beforeEach(() => {
    auth.user = { id: 1, roles: ['CONTENT_EDITOR'] }
    list.mockReset().mockResolvedValue(adminPage)
    vi.mocked(getAdminReference).mockReset().mockResolvedValue(publishedReference)
    vi.mocked(createReference).mockReset().mockResolvedValue(draftReference)
    vi.mocked(updateReference).mockReset().mockResolvedValue(publishedReference)
    vi.mocked(transitionReference)
      .mockReset()
      .mockResolvedValue({ ...draftReference, status: 'PUBLISHED' })
  })
  it('renders server report counts including zero and nullable last-report time', async () => {
    render(<Scr072ReferenceManagementPage />)
    await screen.findByRole('table')
    expect(row(draftReference.title).getByText('0')).toBeVisible()
    expect(row(draftReference.title).getByText('Chưa có')).toBeVisible()
    expect(row(publishedReference.title).getByText('3')).toBeVisible()
    expect(list).toHaveBeenCalledWith({ page: 0 }, expect.any(AbortSignal))
  })
  it.each([
    [draftReference, ['Sửa', 'Công bố', 'Ẩn']],
    [publishedReference, ['Sửa', 'Ẩn']],
    [hiddenReference, ['Sửa', 'Hiện lại', 'Công bố']],
  ] as const)('shows only allowed buttons for $0.status', async (item, labels) => {
    render(<Scr072ReferenceManagementPage />)
    await screen.findByRole('table')
    expect(
      row(item.title)
        .getAllByRole('button')
        .map((button) => button.textContent),
    ).toEqual(labels)
  })
  it('does not send a transition before inline confirmation or after cancel', async () => {
    const event = userEvent.setup()
    render(<Scr072ReferenceManagementPage />)
    await screen.findByRole('table')
    await event.click(row(draftReference.title).getByRole('button', { name: 'Công bố' }))
    expect(screen.getByRole('region', { name: 'Xác nhận thay đổi trạng thái' })).toBeVisible()
    expect(transitionReference).not.toHaveBeenCalled()
    await event.click(screen.getByRole('button', { name: 'Huỷ' }))
    expect(
      screen.queryByRole('region', { name: 'Xác nhận thay đổi trạng thái' }),
    ).not.toBeInTheDocument()
    expect(transitionReference).not.toHaveBeenCalled()
  })
  it('accepts an unchanged-state 200 from a racing editor without fabricating metadata', async () => {
    const event = userEvent.setup()
    const unchanged = {
      ...draftReference,
      status: 'PUBLISHED' as const,
      publishedAt: '2026-11-13T12:00:00Z',
    }
    vi.mocked(transitionReference).mockResolvedValue(unchanged)
    render(<Scr072ReferenceManagementPage />)
    await screen.findByRole('table')
    await event.click(row(draftReference.title).getByRole('button', { name: 'Công bố' }))
    await event.click(screen.getByRole('button', { name: 'Xác nhận công bố' }))
    expect(await screen.findByRole('status')).toHaveTextContent(
      `Đã cập nhật ${draftReference.title} — Đã công bố.`,
    )
    expect(transitionReference).toHaveBeenCalledOnce()
    expect(transitionReference).toHaveBeenCalledWith(501, 'publish')
    await waitFor(() => expect(list).toHaveBeenCalledTimes(2))
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
    expect(unchanged.updatedAt).toBe(draftReference.updatedAt)
    expect(unchanged.reportCount).toBe(0)
  })
  it('unhides to DRAFT and reloads the server list', async () => {
    const event = userEvent.setup()
    vi.mocked(transitionReference).mockResolvedValue({ ...hiddenReference, status: 'DRAFT' })
    render(<Scr072ReferenceManagementPage />)
    await screen.findByRole('table')
    await event.click(row(hiddenReference.title).getByRole('button', { name: 'Hiện lại' }))
    expect(screen.getByText(/Mục sẽ về Bản nháp/)).toBeVisible()
    await event.click(screen.getByRole('button', { name: 'Xác nhận hiện lại' }))
    expect(await screen.findByRole('status')).toHaveTextContent('Bản nháp')
    expect(transitionReference).toHaveBeenCalledWith(hiddenReference.id, 'unhide')
  })
  it('keeps publish validation errors in the confirmation and leaves the list unchanged', async () => {
    const event = userEvent.setup()
    vi.mocked(transitionReference).mockRejectedValue(
      new ApiError({
        status: 422,
        errorCode: 'PUBLISH_INVALID_RESOURCE',
        message: 'Thiếu dữ liệu công bố.',
        details: [{ field: 'author', message: 'Cần tác giả.' }],
      }),
    )
    render(<Scr072ReferenceManagementPage />)
    await screen.findByRole('table')
    await event.click(row(draftReference.title).getByRole('button', { name: 'Công bố' }))
    await event.click(screen.getByRole('button', { name: 'Xác nhận công bố' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('Tác giả: Cần tác giả.')
    expect(row(draftReference.title).getByText('Bản nháp')).toBeVisible()
    expect(list).toHaveBeenCalledOnce()
  })
  it('prevents duplicate confirmations while a transition is pending', async () => {
    const event = userEvent.setup()
    let finish!: (value: AdminReference) => void
    vi.mocked(transitionReference).mockReturnValue(
      new Promise((resolve) => {
        finish = resolve
      }),
    )
    render(<Scr072ReferenceManagementPage />)
    await screen.findByRole('table')
    await event.click(row(publishedReference.title).getByRole('button', { name: 'Ẩn' }))
    await event.click(screen.getByRole('button', { name: 'Xác nhận ẩn' }))
    expect(screen.getByRole('button', { name: 'Xác nhận ẩn' })).toBeDisabled()
    expect(screen.getByRole('button', { name: 'Huỷ' })).toBeDisabled()
    expect(transitionReference).toHaveBeenCalledOnce()
    await act(async () => finish({ ...publishedReference, status: 'HIDDEN' }))
  })
  it('resets page on filters and preserves filters when changing pages', async () => {
    const event = userEvent.setup()
    list.mockResolvedValue({ ...adminPage, total: 40 })
    render(<Scr072ReferenceManagementPage />)
    await screen.findByRole('table')
    await event.click(screen.getByRole('button', { name: 'Sau' }))
    await waitFor(() => expect(list.mock.lastCall?.[0]).toMatchObject({ page: 1 }))
    await event.selectOptions(screen.getByLabelText('Loại'), 'BOOK')
    await event.selectOptions(screen.getByLabelText('Trạng thái'), 'HIDDEN')
    await event.click(screen.getByLabelText('Có báo lỗi'))
    await waitFor(() =>
      expect(list.mock.lastCall?.[0]).toMatchObject({
        kind: 'BOOK',
        status: 'HIDDEN',
        reported: true,
        page: 0,
      }),
    )
    await event.click(screen.getByRole('button', { name: 'Sau' }))
    await waitFor(() =>
      expect(list.mock.lastCall?.[0]).toMatchObject({
        kind: 'BOOK',
        status: 'HIDDEN',
        reported: true,
        page: 1,
      }),
    )
  })
  it('steps back from an emptied last page', async () => {
    const event = userEvent.setup()
    list
      .mockResolvedValueOnce({ ...adminPage, total: 21 })
      .mockResolvedValueOnce({ items: [], page: 1, size: 20, total: 1 })
      .mockResolvedValue(adminPage)
    render(<Scr072ReferenceManagementPage />)
    await screen.findByRole('table')
    await event.click(screen.getByRole('button', { name: 'Sau' }))
    await waitFor(() => expect(list).toHaveBeenCalledTimes(3))
    expect(list.mock.lastCall?.[0]).toMatchObject({ page: 0 })
  })
  it('creates a DRAFT through the API and reloads after success', async () => {
    const event = userEvent.setup()
    render(<Scr072ReferenceManagementPage />)
    await screen.findByRole('table')
    await event.click(screen.getByRole('button', { name: 'Tạo tài nguyên' }))
    await screen.findByRole('form', { name: 'Tạo tài nguyên' })
    await event.type(screen.getByLabelText('Tên tài nguyên'), 'Nguồn tự soạn')
    await event.type(screen.getByLabelText('Liên kết'), 'https://youtube.com/@demo')
    await event.click(screen.getByRole('button', { name: 'Lưu' }))
    await waitFor(() => expect(createReference).toHaveBeenCalledOnce())
    expect(createReference).toHaveBeenCalledWith(
      expect.objectContaining({
        kind: 'YOUTUBE_CHANNEL',
        title: 'Nguồn tự soạn',
        tags: [],
        sortOrder: 0,
        author: null,
        isAffiliate: false,
      }),
    )
    expect(screen.queryByRole('form')).not.toBeInTheDocument()
    await waitFor(() => expect(list).toHaveBeenCalledTimes(2))
  })
  it('loads the full detail for PUT, reports field errors and retains the draft', async () => {
    const event = userEvent.setup()
    vi.mocked(updateReference).mockRejectedValue(
      new ApiError({
        status: 422,
        errorCode: 'DOMAIN_NOT_ALLOWED',
        message: 'Tên miền không được phép.',
        details: [{ field: 'url', message: 'Chọn liên kết khác.' }],
      }),
    )
    render(<Scr072ReferenceManagementPage />)
    await screen.findByRole('table')
    await event.click(row(publishedReference.title).getByRole('button', { name: 'Sửa' }))
    await screen.findByRole('form', { name: 'Sửa tài nguyên' })
    expect(getAdminReference).toHaveBeenCalledWith(publishedReference.id, expect.any(AbortSignal))
    await event.click(screen.getByRole('button', { name: 'Lưu' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('Tên miền không được phép.')
    expect(screen.getByLabelText('Liên kết')).toHaveValue(publishedReference.url)
    expect(screen.getByLabelText('Liên kết')).toHaveAttribute('aria-invalid', 'true')
    expect(updateReference).toHaveBeenCalledWith(
      publishedReference.id,
      expect.not.objectContaining({ status: expect.anything() }),
    )
  })
  it('distinguishes loading, error with retry, and empty', async () => {
    const event = userEvent.setup()
    let reject!: (error: unknown) => void
    list
      .mockReturnValueOnce(
        new Promise((_, fail) => {
          reject = fail
        }),
      )
      .mockResolvedValueOnce({ ...adminPage, items: [], total: 0 })
    render(<Scr072ReferenceManagementPage />)
    expect(screen.getByRole('status')).toHaveTextContent('Đang tải tài nguyên')
    await act(async () =>
      reject(new ApiError({ status: 0, errorCode: 'NETWORK_ERROR', message: 'Lỗi mạng.' })),
    )
    expect(await screen.findByRole('alert')).toHaveTextContent('Lỗi mạng.')
    expect(screen.queryByText('Chưa có tài nguyên phù hợp.')).not.toBeInTheDocument()
    await event.click(screen.getByRole('button', { name: 'Thử lại' }))
    expect(await screen.findByText('Chưa có tài nguyên phù hợp.')).toBeVisible()
  })
  it('cancels a stale filtered list request', async () => {
    const event = userEvent.setup()
    let finish!: (value: AdminReferencePage) => void
    list
      .mockReturnValueOnce(
        new Promise((resolve) => {
          finish = resolve
        }),
      )
      .mockResolvedValueOnce({ ...adminPage, items: [publishedReference], total: 1 })
    render(<Scr072ReferenceManagementPage />)
    const signal = list.mock.calls[0]![1]
    await event.selectOptions(screen.getByLabelText('Loại'), 'BOOK')
    await screen.findByRole('table')
    expect(signal?.aborted).toBe(true)
    await act(async () => finish(adminPage))
    expect(screen.queryByRole('rowheader', { name: draftReference.title })).not.toBeInTheDocument()
  })
  it.each(['ADMIN', 'LEARNER', 'TEACHER', 'MODERATOR', 'ACCOUNTANT'])(
    'never loads admin data directly for %s',
    (role) => {
      auth.user = { id: 1, roles: [role] }
      render(<Scr072ReferenceManagementPage />)
      expect(screen.getByRole('alert')).toHaveTextContent('Cần quyền biên tập nội dung')
      expect(list).not.toHaveBeenCalled()
    },
  )
})
