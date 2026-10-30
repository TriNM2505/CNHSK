import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

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
  beforeEach(() => {
    apiRequestMock.mockReset()
  })
  afterEach(() => {
    vi.unstubAllGlobals()
  })

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
      signal: undefined,
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

  it('FR028_csvSignal: truyền tín hiệu hủy cho endpoint CSV toàn bộ lỗi', async () => {
    const controller = new AbortController()
    await getImportErrorsFile(42, controller.signal)
    expect(apiRequestMock).toHaveBeenCalledWith('/api/admin/imports/42/errors.csv', {
      responseType: 'blob',
      signal: controller.signal,
    })
  })

  it('FR028_csvClient: shared client giữ BOM/Unicode, tên file và gửi cookie', async () => {
    const actualApi = await vi.importActual<typeof import('@/shared/api')>('@/shared/api')
    apiRequestMock.mockImplementation(actualApi.apiRequest)
    const csv =
      '\uFEFFline,column,value,code,message\r\n8,pinyin,学习,INVALID_PINYIN,Pinyin sai.\r\n'
    const fetchMock = vi.fn().mockResolvedValue(
      new Response(csv, {
        headers: {
          'Content-Type': 'text/csv; charset=UTF-8',
          'Content-Disposition': 'attachment; filename="import-42-errors.csv"',
        },
      }),
    )
    vi.stubGlobal('fetch', fetchMock)
    const controller = new AbortController()
    const response = await getImportErrorsFile(42, controller.signal)
    expect(response?.fileName).toBe('import-42-errors.csv')
    expect(Array.from(new Uint8Array(await response!.blob.arrayBuffer()))).toEqual(
      Array.from(new TextEncoder().encode(csv)),
    )
    expect(fetchMock).toHaveBeenCalledWith(
      expect.stringMatching(/\/api\/admin\/imports\/42\/errors\.csv$/),
      expect.objectContaining({
        credentials: 'include',
        method: 'GET',
        signal: controller.signal,
      }),
    )
  })

  it.each([403, 500])(
    'FR028_csvError_%s: shared client đọc lỗi JSON thay vì tải xuống',
    async (status) => {
      const actualApi = await vi.importActual<typeof import('@/shared/api')>('@/shared/api')
      apiRequestMock.mockImplementation(actualApi.apiRequest)
      const errorCode = status === 403 ? 'FORBIDDEN' : 'INTERNAL_ERROR'
      vi.stubGlobal(
        'fetch',
        vi.fn().mockResolvedValue(
          new Response(
            JSON.stringify({
              error_code: errorCode,
              message: 'Chưa tải được CSV.',
              request_id: 'csv-client-42',
            }),
            { status, headers: { 'Content-Type': 'application/json' } },
          ),
        ),
      )
      await expect(getImportErrorsFile(42)).rejects.toMatchObject({
        status,
        errorCode,
        message: 'Chưa tải được CSV.',
        requestId: 'csv-client-42',
      })
    },
  )
})
