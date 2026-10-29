import { render, screen, waitFor, within } from '@testing-library/react'
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
})
