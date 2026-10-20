import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { createMemoryRouter, RouterProvider } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { ApiError } from '@/shared/api'

import { getAdminUsers } from '../api/adminUsers'
import type { AdminUserListParams, AdminUserPage } from '../types'
import Scr076UserListPage from './Scr076UserListPage'

vi.mock('../api/adminUsers', () => ({ getAdminUsers: vi.fn() }))

const getAdminUsersMock = vi.mocked(getAdminUsers)

const emptyPage: AdminUserPage = { items: [], page: 0, size: 20, totalElements: 0 }
const readyPage: AdminUserPage = {
  items: [
    {
      createdAt: '2026-10-19T17:30:00Z',
      displayName: 'Minh Anh',
      emailMasked: 'mi•••@example.com',
      id: 12,
      roles: ['LEARNER', 'ADMIN'],
      status: 'ACTIVE',
    },
  ],
  page: 0,
  size: 20,
  totalElements: 21,
}

function renderPage(entry = '/admin/users') {
  const router = createMemoryRouter(
    [
      { element: <Scr076UserListPage />, path: '/admin/users' },
      { element: <h1>Chi tiết tài khoản</h1>, path: '/admin/users/:id' },
    ],
    { initialEntries: [entry] },
  )
  render(<RouterProvider router={router} />)
  return router
}

describe('Scr076UserListPage', () => {
  beforeEach(() => {
    getAdminUsersMock.mockReset()
    getAdminUsersMock.mockResolvedValue(emptyPage)
  })

  it.each([
    ['role=FOO', 'role'],
    ['status=locked', 'status'],
    ['page=-1', 'page'],
    ['page=abc', 'page'],
    ['q=a', 'q'],
  ])('làm sạch URL không hợp lệ bằng replace: %s', async (search, key) => {
    const router = renderPage(`/admin/users?${search}`)

    await waitFor(() =>
      expect(new URLSearchParams(router.state.location.search).has(key)).toBe(false),
    )
    expect(router.state.historyAction).toBe('REPLACE')
    expect(getAdminUsersMock).toHaveBeenCalledWith(
      { page: 0, q: undefined, role: undefined, size: 20, status: undefined },
      expect.any(AbortSignal),
    )
  })

  it('debounce ô tìm và thay URL bằng replace, không gọi API với một ký tự', async () => {
    const router = renderPage('/admin/users?page=3')
    const user = userEvent.setup()
    await screen.findByText('Chưa có tài khoản nào')
    getAdminUsersMock.mockClear()

    const input = screen.getByLabelText('Email hoặc tên cần tìm')
    await user.type(input, 'm')
    expect(screen.getByText('Nhập ít nhất 2 ký tự hoặc để trống.')).toBeVisible()
    await new Promise((resolve) => window.setTimeout(resolve, 350))
    expect(getAdminUsersMock).not.toHaveBeenCalled()

    await user.type(input, 'i')
    await waitFor(() =>
      expect(new URLSearchParams(router.state.location.search).get('q')).toBe('mi'),
    )
    expect(new URLSearchParams(router.state.location.search).has('page')).toBe(false)
    expect(router.state.historyAction).toBe('REPLACE')
  })

  it('đổi role và chuyển trang dùng push, đồng thời bộ lọc đặt trang về 0', async () => {
    getAdminUsersMock.mockImplementation(async ({ page }: AdminUserListParams) => ({
      ...readyPage,
      page,
    }))
    const router = renderPage('/admin/users?page=2')
    const user = userEvent.setup()

    await screen.findAllByText('Minh Anh')
    await user.selectOptions(screen.getByLabelText('Lọc theo vai trò'), 'ADMIN')
    await waitFor(() =>
      expect(new URLSearchParams(router.state.location.search).get('role')).toBe('ADMIN'),
    )
    expect(new URLSearchParams(router.state.location.search).has('page')).toBe(false)
    expect(router.state.historyAction).toBe('PUSH')

    await screen.findByText('Trang 1 / 2')
    await user.click(screen.getByRole('button', { name: 'Sau' }))
    await waitFor(() =>
      expect(new URLSearchParams(router.state.location.search).get('page')).toBe('1'),
    )
    expect(router.state.historyAction).toBe('PUSH')
  })

  it('hiện trạng thái riêng cho trang vượt phạm vi và đưa về trang đầu', async () => {
    getAdminUsersMock.mockImplementation(async ({ page }: AdminUserListParams) => ({
      items: page === 0 ? readyPage.items : [],
      page,
      size: 20,
      totalElements: 21,
    }))
    const router = renderPage('/admin/users?page=99')
    const user = userEvent.setup()

    expect(await screen.findByText('Trang này không có dữ liệu')).toBeVisible()
    expect(screen.queryByText('Chưa có tài khoản nào')).not.toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Về trang đầu' }))

    await screen.findAllByText('Minh Anh')
    expect(new URLSearchParams(router.state.location.search).has('page')).toBe(false)
    expect(getAdminUsersMock).toHaveBeenLastCalledWith(
      expect.objectContaining({ page: 0 }),
      expect.any(AbortSignal),
    )
  })

  it('phân biệt danh sách chưa có tài khoản và bộ lọc không có kết quả', async () => {
    const user = userEvent.setup()
    const router = renderPage('/admin/users?role=TEACHER')

    expect(await screen.findByText('Không tìm thấy tài khoản phù hợp')).toBeVisible()
    await user.click(screen.getByRole('button', { name: 'Xoá bộ lọc' }))
    await screen.findByText('Chưa có tài khoản nào')
    expect(router.state.location.search).toBe('')
  })

  it('hiện nhãn role tiếng Việt, email đúng API, ngày cố định và mở SCR-077 từ dòng', async () => {
    getAdminUsersMock.mockResolvedValue(readyPage)
    const router = renderPage()
    const user = userEvent.setup()

    const row = await screen.findByRole('row', { name: 'Mở tài khoản Minh Anh' })
    expect(screen.getAllByText('Người học').length).toBeGreaterThan(1)
    expect(screen.getAllByText('Quản trị viên').length).toBeGreaterThan(1)
    expect(screen.getAllByText('mi•••@example.com')).toHaveLength(2)
    expect(screen.getAllByText('20/10/26')).toHaveLength(2)

    await user.click(row)
    expect(await screen.findByRole('heading', { name: 'Chi tiết tài khoản' })).toBeVisible()
    expect(router.state.location.pathname).toBe('/admin/users/12')
  })

  it('hiện lỗi tải và cho phép thử lại', async () => {
    getAdminUsersMock
      .mockRejectedValueOnce(
        new ApiError({
          errorCode: 'INTERNAL_ERROR',
          message: 'Máy chủ tạm thời không phản hồi.',
          requestId: 'req-20',
          status: 500,
        }),
      )
      .mockResolvedValueOnce(emptyPage)
    renderPage()

    expect(await screen.findByText('Máy chủ tạm thời không phản hồi.')).toBeVisible()
    await userEvent.click(screen.getByRole('button', { name: 'Thử lại' }))
    expect(await screen.findByText('Chưa có tài khoản nào')).toBeVisible()
    expect(getAdminUsersMock).toHaveBeenCalledTimes(2)
  })
})
