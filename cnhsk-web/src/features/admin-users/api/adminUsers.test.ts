import { beforeEach, describe, expect, it, vi } from 'vitest'

import { apiRequest } from '@/shared/api'

import {
  banAdminUser,
  getAdminUserDetail,
  getAdminUsers,
  grantAdminUserRole,
  revokeAdminUserRole,
  unbanAdminUser,
} from './adminUsers'

vi.mock('@/shared/api', () => ({ apiRequest: vi.fn() }))

const apiRequestMock = vi.mocked(apiRequest)

describe('admin users API', () => {
  beforeEach(() => apiRequestMock.mockReset())

  it('gửi đúng query string của OpenAPI và giữ nguyên dữ liệu email đã che', async () => {
    const response = {
      items: [
        {
          createdAt: '2026-10-19T17:30:00Z',
          displayName: 'Minh Anh',
          emailMasked: 'mi•••@example.com',
          id: 1,
          roles: ['LEARNER' as const],
          status: 'ACTIVE' as const,
        },
      ],
      page: 2,
      size: 20,
      totalElements: 41,
    }
    const controller = new AbortController()
    apiRequestMock.mockResolvedValue(response)

    await expect(
      getAdminUsers(
        { page: 2, q: 'minh anh', role: 'LEARNER', size: 20, status: 'ACTIVE' },
        controller.signal,
      ),
    ).resolves.toBe(response)

    expect(apiRequestMock).toHaveBeenCalledWith(
      '/api/admin/users?q=minh+anh&role=LEARNER&status=ACTIVE&page=2&size=20',
      { signal: controller.signal },
    )
    expect(response.items[0]?.emailMasked).toBe('mi•••@example.com')
  })

  it('bỏ các bộ lọc rỗng nhưng luôn gửi page và size', async () => {
    apiRequestMock.mockResolvedValue({ items: [], page: 0, size: 20, totalElements: 0 })

    await getAdminUsers({ page: 0, size: 20 })

    expect(apiRequestMock).toHaveBeenCalledWith('/api/admin/users?page=0&size=20', {
      signal: undefined,
    })
  })

  it('gọi GET chi tiết với signal và không đưa dữ liệu nhạy cảm vào nơi khác', async () => {
    const response = {
      createdAt: '2026-10-19T17:30:00Z',
      displayName: 'Minh Anh',
      email: 'minh.anh@example.com',
      id: 12,
      roles: [],
      status: 'ACTIVE' as const,
    }
    const controller = new AbortController()
    apiRequestMock.mockResolvedValue(response)

    await expect(getAdminUserDetail(12, controller.signal)).resolves.toBe(response)
    expect(apiRequestMock).toHaveBeenCalledWith('/api/admin/users/12', {
      signal: controller.signal,
    })
  })

  it('gọi đúng các endpoint thay đổi trả 204 của OpenAPI', async () => {
    apiRequestMock.mockResolvedValue(undefined)

    await grantAdminUserRole(12, { roleCode: 'TEACHER' })
    await revokeAdminUserRole(12, 'CONTENT_EDITOR')
    await banAdminUser(12, { reason: 'Vi phạm điều khoản' })
    await unbanAdminUser(12)

    expect(apiRequestMock).toHaveBeenNthCalledWith(1, '/api/admin/users/12/roles', {
      body: { roleCode: 'TEACHER' },
      method: 'POST',
    })
    expect(apiRequestMock).toHaveBeenNthCalledWith(2, '/api/admin/users/12/roles/CONTENT_EDITOR', {
      method: 'DELETE',
    })
    expect(apiRequestMock).toHaveBeenNthCalledWith(3, '/api/admin/users/12/ban', {
      body: { reason: 'Vi phạm điều khoản' },
      method: 'POST',
    })
    expect(apiRequestMock).toHaveBeenNthCalledWith(4, '/api/admin/users/12/ban', {
      method: 'DELETE',
    })
  })
})
