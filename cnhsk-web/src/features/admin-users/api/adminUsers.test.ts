import { beforeEach, describe, expect, it, vi } from 'vitest'

import { apiRequest } from '@/shared/api'

import { getAdminUsers } from './adminUsers'

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
})
