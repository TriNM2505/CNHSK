import { beforeEach, describe, expect, it, vi } from 'vitest'

import { apiRequest } from '@/shared/api'

import {
  confirmImportRun,
  getImportErrors,
  getImportErrorsFile,
  getImportRun,
  getImportRuns,
  getImportTemplateFile,
  getImportTemplates,
  uploadImportFile,
} from './imports'

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

  it('FR034_FR035_templates: đọc metadata và tải CSV dạng blob', async () => {
    const controller = new AbortController()
    await getImportTemplates(controller.signal)
    await getImportTemplateFile('TOPIC_ITEM')
    expect(apiRequestMock).toHaveBeenNthCalledWith(1, '/api/admin/imports/templates', {
      signal: controller.signal,
    })
    expect(apiRequestMock).toHaveBeenNthCalledWith(
      2,
      '/api/admin/imports/templates/TOPIC_ITEM.csv',
      { responseType: 'blob' },
    )
  })

  it('FR036_upload: gửi đúng hai phần multipart, để trình duyệt tạo boundary', async () => {
    const file = new File(['kind,text,pinyin\nWORD,我们,wǒmen'], 'tu.csv', { type: 'text/csv' })
    await uploadImportFile('LEXEME', file)
    const [url, options] = apiRequestMock.mock.calls[0]!
    expect(url).toBe('/api/admin/imports')
    expect(options).toEqual({ method: 'POST', body: expect.any(FormData) })
    const body = options!.body as FormData
    expect([...body.keys()]).toEqual(['dataType', 'file'])
    expect(body.get('dataType')).toBe('LEXEME')
    expect(body.get('file')).toBe(file)
  })

  it.each([false, true])(
    'FR038_confirm: gửi acknowledgeRerun=%s bằng POST',
    async (acknowledgeRerun) => {
      await confirmImportRun(42, { acknowledgeRerun })
      expect(apiRequestMock).toHaveBeenCalledWith('/api/admin/imports/42/confirm', {
        method: 'POST',
        body: { acknowledgeRerun },
      })
    },
  )

  it('FR040_history: phân trang 10 theo chỉ số server và truyền tín hiệu hủy', async () => {
    const controller = new AbortController()
    await getImportRuns(2, controller.signal)
    expect(apiRequestMock).toHaveBeenCalledWith('/api/admin/imports?page=2&size=10', {
      signal: controller.signal,
    })
  })
})
