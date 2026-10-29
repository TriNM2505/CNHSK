import { act, render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { createMemoryRouter, RouterProvider, useLocation } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { ApiError } from '@/shared/api'
import { downloadBlob } from '@/shared/lib/downloadBlob'

import { getImportErrors, getImportErrorsFile, getImportRun } from '../api/imports'
import type { ImportErrorPage, ImportRun } from '../types'
import Scr069ImportReportPage from './Scr069ImportReportPage'

vi.mock('../api/imports', () => ({
  getImportErrors: vi.fn(),
  getImportErrorsFile: vi.fn(),
  getImportRun: vi.fn(),
}))

vi.mock('@/shared/lib/downloadBlob', () => ({ downloadBlob: vi.fn() }))

const completedRun: ImportRun = {
  completedAt: '2026-10-20T03:12:00Z',
  createdAt: '2026-10-20T03:10:00Z',
  createdBy: { displayName: 'Biên tập viên', id: 3 },
  dataType: 'LEXEME',
  errorCount: 1,
  fileName: 'tu-vung.csv',
  result: {
    created: 4,
    failed: 0,
    invalid: 1,
    total: 10,
    unchanged: 3,
    updated: 2,
    valid: 9,
  },
  runId: 42,
  status: 'COMPLETED',
  totals: { total: 10, valid: 9, invalid: 1, create: 6, update: 1, unchanged: 2 },
}

const errorPage: ImportErrorPage = {
  items: [
    {
      code: 'INVALID_PINYIN',
      column: 'pinyin',
      line: 8,
      message: 'Pinyin của 学习 không hợp lệ.',
      value: '学习',
    },
  ],
  page: 0,
  size: 20,
  totalElements: 1,
}

function LocationProbe() {
  const location = useLocation()
  return <output data-testid="location">{`${location.pathname}${location.search}`}</output>
}

function deferred<T>() {
  let resolve!: (value: T) => void
  let reject!: (error: unknown) => void
  const promise = new Promise<T>((complete, fail) => {
    resolve = complete
    reject = fail
  })
  return { promise, resolve, reject }
}

function expectCounts(title: string, counts: Record<string, number>) {
  const section = screen.getByRole('region', { name: title })
  for (const [label, value] of Object.entries(counts)) {
    expect(within(section).getByText(label).nextElementSibling).toHaveTextContent(String(value))
  }
}

function renderPage(entry = '/content/imports/42') {
  const router = createMemoryRouter(
    [
      { element: <Scr069ImportReportPage />, path: '/content/imports/:id' },
      { element: <LocationProbe />, path: '/content/imports' },
    ],
    { initialEntries: [entry] },
  )
  render(<RouterProvider router={router} />)
  return router
}

describe('Scr069ImportReportPage', () => {
  beforeEach(() => {
    vi.mocked(getImportRun).mockReset().mockResolvedValue(completedRun)
    vi.mocked(getImportErrors).mockReset().mockResolvedValue(errorPage)
    vi.mocked(getImportErrorsFile).mockReset()
    vi.mocked(downloadBlob).mockReset()
  })

  it('FR008_FR026: báo cáo upload sớm thiếu metadata và nhập lại không gắn loại dữ liệu null', async () => {
    vi.mocked(getImportRun).mockResolvedValue({
      ...completedRun,
      dataType: null,
      fileName: null,
      status: 'FAILED',
      failureReason: 'FILE_TOO_LARGE',
      errorCount: 0,
      result: null,
      totals: null,
    })
    vi.mocked(getImportErrors).mockResolvedValue({ items: [], page: 0, size: 20, totalElements: 0 })
    renderPage()
    expect(await screen.findByText('Không đọc được tên tệp')).toBeVisible()
    expect(screen.getByText('Loại dữ liệu').nextElementSibling).toHaveTextContent('—')
    expect(screen.getByText('File vượt quá giới hạn 2 MB.')).toBeVisible()
    const retry = screen.getByRole('link', { name: 'Sửa file và nhập lại' })
    expect(retry).toHaveAttribute('href', '/content/imports')
    await userEvent.click(retry)
    expect(await screen.findByTestId('location')).toHaveTextContent(/^\/content\/imports$/)
  })

  it.each([
    '/content/imports/abc',
    '/content/imports/0',
    '/content/imports/-2',
    '/content/imports/1.5',
  ])('ID không phải số nguyên dương hiện trạng thái không tìm thấy: %s', async (path) => {
    renderPage(path)

    expect(screen.getByText('Không tìm thấy lần nhập')).toBeVisible()
    expect(getImportRun).not.toHaveBeenCalled()
    expect(getImportErrors).not.toHaveBeenCalled()
  })

  it('404 IMPORT_RUN_NOT_FOUND hiện trạng thái không tìm thấy', async () => {
    vi.mocked(getImportRun).mockRejectedValue(
      new ApiError({
        errorCode: 'IMPORT_RUN_NOT_FOUND',
        message: 'Không tồn tại.',
        requestId: 'req-404',
        status: 404,
      }),
    )
    renderPage()

    expect(await screen.findByText('Không tìm thấy lần nhập')).toBeVisible()
    expect(screen.getByRole('link', { name: 'Về trang nhập dữ liệu' })).toHaveAttribute(
      'href',
      '/content/imports',
    )
  })

  it('hiện tóm tắt, đủ cột lỗi và đánh dấu chữ Hán', async () => {
    renderPage()

    expect(await screen.findByText('Tóm tắt lần nhập #42')).toBeVisible()
    expect(screen.getByText('Biên tập viên')).toBeVisible()
    const table = screen.getByRole('table', { name: 'Danh sách lỗi của lần nhập' })
    for (const heading of ['Dòng', 'Cột', 'Giá trị', 'Mã lỗi', 'Thông báo']) {
      expect(within(table).getByRole('columnheader', { name: heading })).toBeVisible()
    }
    expect(within(table).getAllByText('学习')[0]).toHaveAttribute('lang', 'zh-CN')
    expect(within(table).getByText(/Pinyin của/)).toHaveAttribute('lang', 'zh-CN')
  })

  it.each([
    ['page=-1', null],
    ['page=abc', null],
    ['page=1.5', null],
    ['page=0', null],
  ])('chuẩn hoá URL trang không hợp lệ bằng replace: %s', async (search, expected) => {
    const router = renderPage(`/content/imports/42?${search}`)

    await waitFor(() =>
      expect(new URLSearchParams(router.state.location.search).get('page')).toBe(expected),
    )
    expect(router.state.historyAction).toBe('REPLACE')
    expect(getImportErrors).toHaveBeenCalledWith(42, 0, expect.any(AbortSignal))
  })

  it('trang vượt phạm vi hiện nút về trang đầu, không báo không có lỗi', async () => {
    vi.mocked(getImportErrors).mockResolvedValue({
      items: [],
      page: 99,
      size: 20,
      totalElements: 21,
    })
    const router = renderPage('/content/imports/42?page=99')
    const user = userEvent.setup()

    expect(await screen.findByText('Trang này không có dữ liệu')).toBeVisible()
    expect(screen.queryByText('Không có dòng lỗi')).not.toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Về trang đầu' }))

    await waitFor(() => expect(router.state.location.search).toBe(''))
    expect(getImportErrors).toHaveBeenLastCalledWith(42, 0, expect.any(AbortSignal))
  })

  it('RUNNING chỉ tải lại cả tóm tắt và bảng lỗi khi bấm nút', async () => {
    vi.mocked(getImportRun).mockResolvedValue({
      ...completedRun,
      completedAt: null,
      errorCount: 0,
      result: null,
      status: 'RUNNING',
      totals: { total: 10, valid: 10, invalid: 0, create: 6, update: 1, unchanged: 3 },
    })
    vi.mocked(getImportErrors).mockResolvedValue({
      items: [],
      page: 0,
      size: 20,
      totalElements: 0,
    })
    renderPage()
    const user = userEvent.setup()

    await screen.findByText(/Hệ thống đang ghi các dòng hợp lệ/)
    expect(getImportRun).toHaveBeenCalledTimes(1)
    expect(getImportErrors).toHaveBeenCalledTimes(1)

    vi.mocked(getImportRun).mockClear()
    vi.mocked(getImportErrors).mockClear()
    await user.click(screen.getByRole('button', { name: 'Tải lại' }))

    await waitFor(() => expect(getImportRun).toHaveBeenCalledTimes(1))
    expect(getImportErrors).toHaveBeenCalledTimes(1)
  })

  it('tải CSV dùng tên server và tên dự phòng khi thiếu', async () => {
    const blob = new Blob(['line,column'])
    vi.mocked(getImportErrorsFile)
      .mockResolvedValueOnce({ blob, fileName: 'loi-tu-server.csv' })
      .mockResolvedValueOnce({ blob })
    renderPage()
    const user = userEvent.setup()

    const button = await screen.findByRole('button', { name: 'Tải danh sách lỗi' })
    await user.click(button)
    await waitFor(() => expect(downloadBlob).toHaveBeenCalledWith(blob, 'loi-tu-server.csv'))

    await user.click(button)
    await waitFor(() => expect(downloadBlob).toHaveBeenLastCalledWith(blob, 'import-42-errors.csv'))
  })

  it('đi tới SCR-068 với dataType của lần nhập', async () => {
    renderPage()
    const user = userEvent.setup()

    await user.click(await screen.findByRole('link', { name: 'Sửa file và nhập lại' }))
    expect(screen.getByTestId('location')).toHaveTextContent('/content/imports?dataType=LEXEME')
  })

  it.each([
    ['INTERRUPTED', 'Quá trình nhập bị gián đoạn.'],
    ['ENCODING_ERROR', 'File không phải UTF-8 hoặc chữ Hán bị hỏng.'],
    ['SOMETHING_NEW', 'Không thể hoàn tất lần nhập dữ liệu.'],
  ])('FAILED ánh xạ lý do %s', async (failureReason, message) => {
    vi.mocked(getImportRun).mockResolvedValue({
      ...completedRun,
      errorCount: 0,
      failureReason,
      result: null,
      status: 'FAILED',
      totals: null,
    })
    vi.mocked(getImportErrors).mockResolvedValue({
      items: [],
      page: 0,
      size: 20,
      totalElements: 0,
    })
    renderPage()

    expect(await screen.findByText(message)).toBeVisible()
  })

  it('FR026_FR039_summary: phân biệt snapshot preview và đủ kết quả đã ghi', async () => {
    renderPage()
    await screen.findByText('Tóm tắt lần nhập #42')
    expectCounts('Số liệu lúc kiểm tra file', {
      'Tổng số dòng': 10,
      'Hợp lệ': 9,
      'Không hợp lệ': 1,
      'Tạo mới': 6,
      'Cập nhật': 1,
      'Không đổi': 2,
    })
    expectCounts('Kết quả ghi', {
      'Tổng số dòng': 10,
      'Hợp lệ': 9,
      'Không hợp lệ': 1,
      'Tạo mới': 4,
      'Cập nhật': 2,
      'Không đổi': 3,
      'Lỗi ghi': 0,
    })
    expect(screen.getByText('Số lỗi').nextElementSibling).toHaveTextContent('1')
    expect(screen.getByText('Loại dữ liệu').nextElementSibling).toHaveTextContent('Chữ và từ vựng')
    expect(screen.getByText('Thời điểm tạo').nextElementSibling).toHaveTextContent('10:10')
    expect(screen.getByText('Thời điểm hoàn thành').nextElementSibling).toHaveTextContent('10:12')
    expect(screen.getByText('tu-vung.csv')).toBeVisible()
    expect(screen.queryByText(/^Nhập thành công/)).not.toBeInTheDocument()
  })

  it('FR039_success: không lỗi thì thông báo đúng tổng created + updated + unchanged', async () => {
    vi.mocked(getImportRun).mockResolvedValue({
      ...completedRun,
      errorCount: 0,
      totals: { total: 10, valid: 10, invalid: 0, create: 5, update: 2, unchanged: 3 },
      result: { total: 10, valid: 10, invalid: 0, created: 5, updated: 2, unchanged: 3, failed: 0 },
    })
    vi.mocked(getImportErrors).mockResolvedValue({ items: [], page: 0, size: 20, totalElements: 0 })
    renderPage()
    expect(await screen.findByText('Nhập thành công 10 dòng')).toBeVisible()
    expect(screen.getByText('Không có dòng lỗi')).toBeVisible()
    expect(screen.queryByRole('button', { name: 'Tải danh sách lỗi' })).not.toBeInTheDocument()
    expectCounts('Kết quả ghi', { 'Hợp lệ': 10, 'Không hợp lệ': 0, 'Lỗi ghi': 0 })
  })

  it.each(['PENDING', 'RUNNING', 'EXPIRED', 'FAILED'] as const)(
    'FR039_unfinished_%s: giữ snapshot, không báo thành công dù không có lỗi cấp dòng',
    async (status) => {
      vi.mocked(getImportRun).mockResolvedValue({
        ...completedRun,
        status,
        completedAt: null,
        errorCount: 0,
        result: null,
        failureReason: status === 'FAILED' ? 'INTERRUPTED' : null,
        totals: { total: 10, valid: 10, invalid: 0, create: 6, update: 1, unchanged: 3 },
      })
      vi.mocked(getImportErrors).mockResolvedValue({
        items: [],
        page: 0,
        size: 20,
        totalElements: 0,
      })
      renderPage()
      await screen.findByText('Tóm tắt lần nhập #42')
      expectCounts('Số liệu lúc kiểm tra file', {
        'Hợp lệ': 10,
        'Tạo mới': 6,
        'Cập nhật': 1,
        'Không đổi': 3,
      })
      expect(screen.queryByRole('region', { name: 'Kết quả ghi' })).not.toBeInTheDocument()
      expect(screen.queryByText(/^Nhập thành công/)).not.toBeInTheDocument()
      expect(screen.getByText('Thời điểm hoàn thành').nextElementSibling).toHaveTextContent('—')
      if (status === 'FAILED')
        expect(screen.getByText('Quá trình nhập bị gián đoạn.')).toBeVisible()
    },
  )

  it.each([null, undefined])(
    'FR026_FR039_legacy: thiếu snapshot %s vẫn giữ kết quả đã lưu',
    async (totals) => {
      vi.mocked(getImportRun).mockResolvedValue({ ...completedRun, totals })
      renderPage()
      await screen.findByText('Tóm tắt lần nhập #42')
      expect(
        screen.queryByRole('region', { name: 'Số liệu lúc kiểm tra file' }),
      ).not.toBeInTheDocument()
      expectCounts('Kết quả ghi', { 'Tạo mới': 4, 'Cập nhật': 2, 'Không đổi': 3 })
    },
  )

  it('FR026_FR039_nullCounts: không bịa số 0 khi lỗi cấp file chưa kiểm được dòng nào', async () => {
    vi.mocked(getImportRun).mockResolvedValue({
      ...completedRun,
      status: 'FAILED',
      failureReason: 'ENCODING_ERROR',
      completedAt: null,
      totals: null,
      result: null,
      errorCount: 0,
    })
    vi.mocked(getImportErrors).mockResolvedValue({ items: [], page: 0, size: 20, totalElements: 0 })
    renderPage()
    expect(await screen.findByText('File không phải UTF-8 hoặc chữ Hán bị hỏng.')).toBeVisible()
    expect(screen.queryByText('Tổng số dòng')).not.toBeInTheDocument()
    expect(screen.queryByText('Tạo mới')).not.toBeInTheDocument()
    expect(screen.queryByText(/^Nhập thành công/)).not.toBeInTheDocument()
    expect(screen.getByText('Số lỗi').nextElementSibling).toHaveTextContent('0')
  })

  it('FR039_partialWrite: số dòng ghi lỗi không bị tính thành công', async () => {
    vi.mocked(getImportRun).mockResolvedValue({
      ...completedRun,
      errorCount: 2,
      result: { total: 10, valid: 9, invalid: 1, created: 3, updated: 2, unchanged: 3, failed: 1 },
    })
    renderPage()
    await screen.findByText('Tóm tắt lần nhập #42')
    expectCounts('Kết quả ghi', { 'Hợp lệ': 9, 'Không hợp lệ': 1, 'Tạo mới': 3, 'Lỗi ghi': 1 })
    expect(screen.getByText('Số lỗi').nextElementSibling).toHaveTextContent('2')
    expect(screen.queryByText(/^Nhập thành công/)).not.toBeInTheDocument()
  })

  it('FR027_FR039_pagination: 45 lỗi, trang thứ ba chỉ có 5 lỗi và chuyển trang bằng API', async () => {
    vi.mocked(getImportRun).mockResolvedValue({
      ...completedRun,
      errorCount: 45,
      totals: { total: 50, valid: 5, invalid: 45, create: 5, update: 0, unchanged: 0 },
      result: { total: 50, valid: 5, invalid: 45, created: 5, updated: 0, unchanged: 0, failed: 0 },
    })
    vi.mocked(getImportErrors).mockImplementation(async (_id, page) => ({
      items: Array.from({ length: page === 2 ? 5 : 20 }, (_, index) => ({
        line: page * 20 + index + 2,
        column: 'pinyin',
        value: 'xue2xi',
        code: 'INVALID_PINYIN',
        message: 'Pinyin không hợp lệ.',
      })),
      page,
      size: 20,
      totalElements: 45,
    }))
    const router = renderPage('/content/imports/42?page=2')
    const table = await screen.findByRole('table', { name: 'Danh sách lỗi của lần nhập' })
    expect(within(table).getAllByRole('row')).toHaveLength(6)
    expect(within(table).getByText('42')).toBeVisible()
    expect(within(table).getByText('46')).toBeVisible()
    expect(screen.getByText('Có 45 lỗi')).toBeVisible()
    expect(screen.getByText('Trang 3 / 3')).toBeVisible()
    expect(screen.getByRole('button', { name: 'Sau' })).toBeDisabled()
    await userEvent.click(screen.getByRole('button', { name: 'Trước' }))
    await screen.findByText('Trang 2 / 3')
    expect(router.state.location.search).toBe('?page=1')
    expect(getImportErrors).toHaveBeenLastCalledWith(42, 1, expect.any(AbortSignal))
    const secondTable = screen.getByRole('table', { name: 'Danh sách lỗi của lần nhập' })
    expect(within(secondTable).getAllByRole('row')).toHaveLength(21)
    await userEvent.click(screen.getByRole('button', { name: 'Trước' }))
    await screen.findByText('Trang 1 / 3')
    expect(router.state.location.search).toBe('')
    expect(getImportRun).toHaveBeenCalledTimes(1)
  })

  it('FR026_FR027_loading: từng vùng có loading, không hiển thị số liệu giả', async () => {
    const pendingRun = deferred<ImportRun>()
    const pendingErrors = deferred<ImportErrorPage>()
    vi.mocked(getImportRun).mockReturnValue(pendingRun.promise)
    vi.mocked(getImportErrors).mockReturnValue(pendingErrors.promise)
    renderPage()
    expect(await screen.findByRole('status', { name: 'Đang tải' })).toBeVisible()
    expect(screen.queryByText('Tổng số dòng')).not.toBeInTheDocument()
    await act(async () => pendingRun.resolve(completedRun))
    expect(screen.getByText('Tóm tắt lần nhập #42')).toBeVisible()
    const errorsRegion = screen.getByRole('region', { name: 'Chi tiết dòng lỗi' })
    expect(await within(errorsRegion).findByRole('status', { name: 'Đang tải' })).toBeVisible()
    expect(screen.queryByText('Không có dòng lỗi')).not.toBeInTheDocument()
    await act(async () => pendingErrors.resolve(errorPage))
    expect(screen.getByRole('table', { name: 'Danh sách lỗi của lần nhập' })).toBeVisible()
  })

  it('FR027_FR039_partialLoad: lỗi chi tiết giữ summary và chỉ thử lại bảng lỗi', async () => {
    vi.mocked(getImportErrors)
      .mockRejectedValueOnce(
        new ApiError({
          status: 500,
          errorCode: 'INTERNAL_ERROR',
          message: 'Chưa tải được lỗi.',
          requestId: 'errors-42',
        }),
      )
      .mockResolvedValueOnce(errorPage)
    renderPage()
    expect(await screen.findByText('Chưa tải được lỗi.')).toBeVisible()
    expect(screen.getByText('Mã lỗi: errors-42')).toBeVisible()
    expectCounts('Kết quả ghi', { 'Tạo mới': 4, 'Cập nhật': 2, 'Không đổi': 3 })
    expect(screen.queryByText('Không có dòng lỗi')).not.toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'Thử lại' }))
    expect(await screen.findByRole('table', { name: 'Danh sách lỗi của lần nhập' })).toBeVisible()
    expect(getImportRun).toHaveBeenCalledTimes(1)
    expect(getImportErrors).toHaveBeenCalledTimes(2)
  })

  it.each([403, 500])(
    'FR026_FR039_loadError_%s: hiện message/requestId và tải lại báo cáo',
    async (status) => {
      vi.mocked(getImportRun)
        .mockRejectedValueOnce(
          new ApiError({
            status,
            errorCode: status === 403 ? 'FORBIDDEN' : 'INTERNAL_ERROR',
            message: 'Chưa đọc được báo cáo.',
            requestId: 'report-42',
          }),
        )
        .mockResolvedValueOnce(completedRun)
      renderPage()
      expect(await screen.findByText('Chưa đọc được báo cáo.')).toBeVisible()
      expect(screen.getByText('Mã lỗi: report-42')).toBeVisible()
      expect(screen.queryByText('Tổng số dòng')).not.toBeInTheDocument()
      expect(
        screen.queryByRole('table', { name: 'Danh sách lỗi của lần nhập' }),
      ).not.toBeInTheDocument()
      await userEvent.click(screen.getByRole('button', { name: 'Thử lại' }))
      expect(await screen.findByText('Tóm tắt lần nhập #42')).toBeVisible()
      expect(getImportRun).toHaveBeenCalledTimes(2)
      expect(getImportErrors).toHaveBeenCalledTimes(2)
    },
  )

  it('FR028_FR039_downloadPending: khóa tải lặp, tải toàn bộ lỗi bằng blob nguyên vẹn', async () => {
    const pending = deferred<Awaited<ReturnType<typeof getImportErrorsFile>>>()
    const blob = new Blob([
      '\uFEFFline,column,value,code,message\r\n8,pinyin,学习,INVALID_PINYIN,Pinyin sai.\r\n',
    ])
    vi.mocked(getImportErrorsFile).mockReturnValue(pending.promise)
    renderPage()
    const user = userEvent.setup()
    await user.click(await screen.findByRole('button', { name: 'Tải danh sách lỗi' }))
    const button = screen.getByRole('button', { name: 'Đang tải' })
    expect(button).toBeDisabled()
    await user.click(button)
    expect(getImportErrorsFile).toHaveBeenCalledTimes(1)
    expect(getImportErrorsFile).toHaveBeenCalledWith(42, expect.any(AbortSignal))
    expect(downloadBlob).not.toHaveBeenCalled()
    await act(async () => pending.resolve({ blob, fileName: 'import-42-errors.csv' }))
    expect(downloadBlob).toHaveBeenCalledWith(blob, 'import-42-errors.csv')
    expect(screen.getByRole('button', { name: 'Tải danh sách lỗi' })).toBeEnabled()
    expect(getImportRun).toHaveBeenCalledTimes(1)
  })

  it('FR028_FR039_downloadRetry: lỗi JSON hiện message/requestId, giữ dữ liệu và cho tải lại', async () => {
    const blob = new Blob(['line,column,value,code,message'])
    vi.mocked(getImportErrorsFile)
      .mockRejectedValueOnce(
        new ApiError({
          status: 403,
          errorCode: 'FORBIDDEN',
          message: 'Không được phép tải file.',
          requestId: 'csv-42',
        }),
      )
      .mockResolvedValueOnce({ blob })
    renderPage()
    const user = userEvent.setup()
    await user.click(await screen.findByRole('button', { name: 'Tải danh sách lỗi' }))
    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Không được phép tải file. Mã lỗi: csv-42',
    )
    expectCounts('Kết quả ghi', { 'Tạo mới': 4 })
    expect(screen.getByRole('table', { name: 'Danh sách lỗi của lần nhập' })).toBeVisible()
    expect(downloadBlob).not.toHaveBeenCalled()
    await user.click(screen.getByRole('button', { name: 'Tải danh sách lỗi' }))
    expect(downloadBlob).toHaveBeenCalledWith(blob, 'import-42-errors.csv')
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
    expect(getImportRun).toHaveBeenCalledTimes(1)
  })

  it.each(['success', 'error'])(
    'FR028_FR039_staleDownload_%s: đổi run hủy tải cũ và bỏ phản hồi muộn',
    async (outcome) => {
      const oldDownload = deferred<Awaited<ReturnType<typeof getImportErrorsFile>>>()
      const nextBlob = new Blob(['file của lần nhập 43'])
      vi.mocked(getImportErrorsFile)
        .mockReturnValueOnce(oldDownload.promise)
        .mockResolvedValueOnce({ blob: nextBlob })
      vi.mocked(getImportRun).mockImplementation(async (id) => ({
        ...completedRun,
        runId: id,
        fileName: `run-${id}.csv`,
      }))
      const router = renderPage()
      const user = userEvent.setup()
      await user.click(await screen.findByRole('button', { name: 'Tải danh sách lỗi' }))
      const oldSignal = vi.mocked(getImportErrorsFile).mock.calls[0]![1]!
      expect(oldSignal.aborted).toBe(false)
      await act(async () => {
        await router.navigate('/content/imports/43')
      })
      expect(oldSignal.aborted).toBe(true)
      expect(await screen.findByText('Tóm tắt lần nhập #43')).toBeVisible()
      expect(screen.queryByText('run-42.csv')).not.toBeInTheDocument()
      expect(getImportErrors).toHaveBeenLastCalledWith(43, 0, expect.any(AbortSignal))
      await act(async () => {
        if (outcome === 'success') oldDownload.resolve({ blob: new Blob(['file cũ']) })
        else
          oldDownload.reject(
            new ApiError({
              status: 500,
              errorCode: 'INTERNAL_ERROR',
              message: 'Lỗi cũ của run 42.',
            }),
          )
      })
      expect(downloadBlob).not.toHaveBeenCalled()
      expect(screen.queryByRole('alert')).not.toBeInTheDocument()
      await user.click(screen.getByRole('button', { name: 'Tải danh sách lỗi' }))
      expect(downloadBlob).toHaveBeenCalledTimes(1)
      expect(downloadBlob).toHaveBeenCalledWith(nextBlob, 'import-43-errors.csv')
      expect(getImportErrorsFile).toHaveBeenLastCalledWith(43, expect.any(AbortSignal))
    },
  )

  it('FR026_FR039_unsafeId: không làm tròn ID vượt độ chính xác số của JavaScript để gọi API', () => {
    renderPage('/content/imports/9007199254740992')
    expect(screen.getByText('Không tìm thấy lần nhập')).toBeVisible()
    expect(getImportRun).not.toHaveBeenCalled()
    expect(getImportErrors).not.toHaveBeenCalled()
  })

  it('FR027_FR039_safeValues: giữ dòng/cột null, Unicode và HTML dưới dạng văn bản', async () => {
    const value = '学习'.repeat(50)
    vi.mocked(getImportErrors).mockResolvedValue({
      ...errorPage,
      items: [
        {
          line: 12,
          code: 'ROW_WRITE_FAILED',
          column: null,
          value: null,
          message: 'Không ghi được dòng này.',
        },
        {
          line: 13,
          code: 'INVALID_PINYIN',
          column: 'pinyin',
          value,
          message: '<script>không thực thi</script>',
        },
      ],
      totalElements: 2,
    })
    renderPage()
    const table = await screen.findByRole('table', { name: 'Danh sách lỗi của lần nhập' })
    const wholeRow = within(table).getByText('12').closest('tr')!
    expect(within(wholeRow).getAllByText('—')).toHaveLength(2)
    expect(within(table).getByText(value)).toHaveAttribute('lang', 'zh-CN')
    expect(within(table).getByText('<script>không thực thi</script>')).toBeVisible()
    expect(table.querySelector('script')).toBeNull()
  })
})
