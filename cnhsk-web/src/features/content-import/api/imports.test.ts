import { beforeEach, describe, expect, it, vi } from 'vitest'

import { apiRequest } from '@/shared/api'

import { getImportErrors, getImportErrorsFile, getImportRun } from './imports'

vi.mock('@/shared/api', () => ({ apiRequest: vi.fn() }))

const apiRequestMock = vi.mocked(apiRequest)

describe('import report API', () => {
  beforeEach(() => apiRequestMock.mockReset())

  it('gọi đúng endpoint chi tiết và danh sách lỗi phân trang 20', async () => {
    const controller = new AbortController()
    apiRequestMock.mockResolvedValue(undefined)

    await getImportRun(42, controller.signal)
    await getImportErrors(42, 3, controller.signal)

    expect(apiRequestMock).toHaveBeenNthCalledWith(1, '/api/admin/imports/42', {
      signal: controller.signal,
    })
    expect(apiRequestMock).toHaveBeenNthCalledWith(
      2,
      '/api/admin/imports/42/errors?page=3&size=20',
      { signal: controller.signal },
    )
  })

  it('yêu cầu file lỗi dưới dạng blob', async () => {
    apiRequestMock.mockResolvedValue(undefined)

    await getImportErrorsFile(42)

    expect(apiRequestMock).toHaveBeenCalledWith('/api/admin/imports/42/errors.csv', {
      responseType: 'blob',
    })
  })
})
