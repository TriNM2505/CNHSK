import { act, fireEvent, render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { ApiError } from '@/shared/api'

import {
  confirmImportRun,
  getImportRuns,
  getImportTemplateFile,
  getImportTemplates,
  uploadImportFile,
} from '../api/imports'
import type { ImportPreview, ImportRun, ImportTemplate } from '../types'
import Scr068LearningDataImportPage from './Scr068LearningDataImportPage'

vi.mock('../api/imports', () => ({
  confirmImportRun: vi.fn(),
  getImportRuns: vi.fn(),
  getImportTemplateFile: vi.fn(),
  getImportTemplates: vi.fn(),
  uploadImportFile: vi.fn(),
}))

vi.mock('@/shared/lib/downloadBlob', () => ({ downloadBlob: vi.fn() }))

const templates: ImportTemplate[] = [
  {
    columns: [
      { name: 'hanzi', required: true },
      { name: 'pinyin', required: true },
    ],
    dataType: 'LEXEME',
    dependsOn: [],
    label: 'Chữ và từ vựng',
    naturalKey: ['hanzi', 'pinyin'],
  },
  {
    columns: [{ name: 'code', required: true }],
    dataType: 'GRAMMAR',
    dependsOn: [],
    label: 'Điểm ngữ pháp',
    naturalKey: ['code'],
  },
]

const NOW = Date.parse('2026-10-20T05:00:00Z')

function makePreview(overrides: Partial<ImportPreview> = {}): ImportPreview {
  return {
    alreadyImported: false,
    dataType: 'LEXEME',
    errorCount: 1,
    errors: [
      { code: 'INVALID_PINYIN', column: 'pinyin', line: 3, message: 'Pinyin sai.', value: 'x' },
    ],
    expiresAt: '2026-10-20T05:30:00Z',
    fileName: 'tu.csv',
    preview: [{ action: 'CREATE', line: 2, values: { hanzi: '学习', pinyin: 'xuéxí' } }],
    runId: 42,
    status: 'PENDING',
    totals: { create: 1, invalid: 1, total: 2, unchanged: 0, update: 0, valid: 1 },
    ...overrides,
  }
}

const completedRun: ImportRun = {
  createdAt: '2026-10-20T05:01:00Z',
  createdBy: { displayName: 'Biên tập', id: 1 },
  dataType: 'LEXEME',
  errorCount: 0,
  fileName: 'tu.csv',
  runId: 42,
  status: 'COMPLETED',
}

function csvFile(name = 'tu.csv', size = 10) {
  return new File(['x'.repeat(size)], name, { type: 'text/csv' })
}

function Probe() {
  const location = useLocation()
  return <output data-testid="location">{location.pathname}</output>
}

function renderPage(path = '/content/imports') {
  render(
    <MemoryRouter initialEntries={[path]}>
      <Routes>
        <Route element={<Scr068LearningDataImportPage />} path="/content/imports" />
        <Route element={<Probe />} path="/content/imports/:id" />
      </Routes>
    </MemoryRouter>,
  )
}

async function chooseAndUpload(file = csvFile()) {
  const user = userEvent.setup({ applyAccept: false })
  await user.selectOptions(await screen.findByLabelText(/Loại dữ liệu/), 'LEXEME')
  await user.upload(screen.getByLabelText(/File CSV/), file)
  await user.click(screen.getByRole('button', { name: 'Tải lên và kiểm tra' }))
  return user
}

describe('Scr068LearningDataImportPage', () => {
  beforeEach(() => {
    vi.mocked(getImportTemplates).mockReset().mockResolvedValue(templates)
    vi.mocked(getImportRuns)
      .mockReset()
      .mockResolvedValue({ items: [], page: 0, size: 10, totalElements: 0 })
    vi.mocked(uploadImportFile).mockReset()
    vi.mocked(confirmImportRun).mockReset()
    vi.mocked(getImportTemplateFile).mockReset()
    vi.spyOn(Date, 'now').mockReturnValue(NOW)
  })

  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('FR-036: kiểm tra phía khách trước khi gửi', async () => {
    renderPage()
    const user = userEvent.setup({ applyAccept: false })

    await user.click(await screen.findByRole('button', { name: 'Tải lên và kiểm tra' }))
    expect(screen.getByText('Hãy chọn loại dữ liệu.')).toBeVisible()
    expect(screen.getByText('Hãy chọn file CSV cần kiểm tra.')).toBeVisible()

    await user.selectOptions(screen.getByLabelText(/Loại dữ liệu/), 'LEXEME')
    await user.upload(screen.getByLabelText(/File CSV/), csvFile('tu.xlsx'))
    await user.click(screen.getByRole('button', { name: 'Tải lên và kiểm tra' }))
    expect(screen.getByText('Chỉ chấp nhận file có đuôi .csv.')).toBeVisible()

    await user.upload(screen.getByLabelText(/File CSV/), csvFile('lon.csv', 2 * 1024 * 1024 + 1))
    await user.click(screen.getByRole('button', { name: 'Tải lên và kiểm tra' }))
    expect(screen.getByText('File vượt quá giới hạn 2 MB.')).toBeVisible()
    expect(uploadImportFile).not.toHaveBeenCalled()
  })

  it('chọn sẵn loại dữ liệu từ ?dataType= hợp lệ, bỏ qua giá trị lạ', async () => {
    renderPage('/content/imports?dataType=GRAMMAR')
    await screen.findByText('Cột của mẫu Điểm ngữ pháp')
    expect(screen.getByLabelText(/Loại dữ liệu/)).toHaveValue('GRAMMAR')
  })

  it('bỏ qua ?dataType= không hợp lệ', async () => {
    renderPage('/content/imports?dataType=EXAM')
    expect(await screen.findByLabelText(/Loại dữ liệu/)).toHaveValue('')
  })

  it('FR-037: hiện 6 số, cột theo mẫu, link xem tất cả lỗi', async () => {
    vi.mocked(uploadImportFile).mockResolvedValue(makePreview())
    renderPage()

    await chooseAndUpload()

    expect(await screen.findByText('Kết quả kiểm tra')).toBeVisible()
    expect(uploadImportFile).toHaveBeenCalledWith('LEXEME', expect.any(File))
    for (const label of [
      'Tổng số dòng',
      'Hợp lệ',
      'Không hợp lệ',
      'Tạo mới',
      'Cập nhật',
      'Không đổi',
    ]) {
      expect(screen.getAllByText(label).length).toBeGreaterThan(0)
    }
    const previewTable = screen.getByRole('table', { name: 'Xem trước các dòng hợp lệ đầu tiên' })
    expect(within(previewTable).getByRole('columnheader', { name: 'hanzi' })).toBeInTheDocument()
    expect(within(previewTable).getAllByText('学习')[0]).toHaveAttribute('lang', 'zh-CN')
    expect(screen.getByRole('link', { name: 'Xem tất cả 1 lỗi' })).toHaveAttribute(
      'href',
      '/content/imports/42',
    )
  })

  it('FR-008: lỗi cấp file có runId thì tải lại lịch sử', async () => {
    vi.mocked(uploadImportFile).mockRejectedValue(
      new ApiError({
        errorCode: 'TEMPLATE_MISMATCH',
        extra: { runId: 7 },
        message: 'x',
        requestId: 'req-9',
        status: 422,
      }),
    )
    renderPage()

    await chooseAndUpload()

    expect(await screen.findByText('Các cột trong file không khớp mẫu đã chọn.')).toBeVisible()
    expect(getImportRuns).toHaveBeenCalledTimes(2)
  })

  it('file đã nhập trước: phải tick mới xác nhận được và gửi acknowledgeRerun=true', async () => {
    vi.mocked(uploadImportFile).mockResolvedValue(makePreview({ alreadyImported: true }))
    vi.mocked(confirmImportRun).mockResolvedValue(completedRun)
    renderPage()

    const user = await chooseAndUpload()
    const confirmButton = await screen.findByRole('button', { name: 'Xác nhận nhập dữ liệu' })
    expect(confirmButton).toBeDisabled()

    await user.click(screen.getByLabelText('Tôi hiểu file này đã nhập trước đó'))
    await user.click(confirmButton)
    expect(screen.getByText('Ghi 1 dòng vào kho? Không hoàn tác được.')).toBeVisible()
    await user.click(screen.getByRole('button', { name: 'Ghi vào kho' }))

    expect(confirmImportRun).toHaveBeenCalledWith(42, { acknowledgeRerun: true })
    expect(await screen.findByTestId('location')).toHaveTextContent('/content/imports/42')
  })

  it('valid = 0 thì khoá nút xác nhận', async () => {
    vi.mocked(uploadImportFile).mockResolvedValue(
      makePreview({
        preview: [],
        totals: { create: 0, invalid: 2, total: 2, unchanged: 0, update: 0, valid: 0 },
      }),
    )
    renderPage()

    await chooseAndUpload()

    expect(await screen.findByRole('button', { name: 'Xác nhận nhập dữ liệu' })).toBeDisabled()
  })

  it('CONCURRENT_IMPORT giữ kết quả kiểm tra để bấm xác nhận lại', async () => {
    vi.mocked(uploadImportFile).mockResolvedValue(makePreview())
    vi.mocked(confirmImportRun)
      .mockRejectedValueOnce(
        new ApiError({ errorCode: 'CONCURRENT_IMPORT', message: 'x', status: 409 }),
      )
      .mockResolvedValueOnce(completedRun)
    renderPage()

    const user = await chooseAndUpload()
    await user.click(await screen.findByRole('button', { name: 'Xác nhận nhập dữ liệu' }))
    await user.click(screen.getByRole('button', { name: 'Ghi vào kho' }))

    expect(await screen.findByText(/Đang có lần nhập khác chạy/)).toBeVisible()
    const confirmButton = screen.getByRole('button', { name: 'Xác nhận nhập dữ liệu' })
    expect(confirmButton).toBeEnabled()

    await user.click(confirmButton)
    await user.click(screen.getByRole('button', { name: 'Ghi vào kho' }))
    expect(await screen.findByTestId('location')).toHaveTextContent('/content/imports/42')
  })

  it('410 IMPORT_PREVIEW_EXPIRED khoá xác nhận và mời tải lại', async () => {
    vi.mocked(uploadImportFile).mockResolvedValue(makePreview())
    vi.mocked(confirmImportRun).mockRejectedValue(
      new ApiError({ errorCode: 'IMPORT_PREVIEW_EXPIRED', message: 'x', status: 410 }),
    )
    renderPage()

    const user = await chooseAndUpload()
    await user.click(await screen.findByRole('button', { name: 'Xác nhận nhập dữ liệu' }))
    await user.click(screen.getByRole('button', { name: 'Ghi vào kho' }))

    expect(
      await screen.findByText('Kết quả kiểm tra đã hết hạn, hãy tải file lên lại.'),
    ).toBeVisible()
    expect(screen.getByRole('button', { name: 'Xác nhận nhập dữ liệu' })).toBeDisabled()
  })

  it('đồng hồ về 0 thì khoá xác nhận', async () => {
    vi.mocked(uploadImportFile).mockResolvedValue(
      makePreview({ expiresAt: '2026-10-20T05:00:02Z' }),
    )
    renderPage()

    await chooseAndUpload()
    expect(await screen.findByRole('button', { name: 'Xác nhận nhập dữ liệu' })).toBeEnabled()
    expect(screen.getByText('00:02')).toBeVisible()

    vi.mocked(Date.now).mockReturnValue(NOW + 3000)
    await act(async () => {
      await new Promise((resolve) => setTimeout(resolve, 1100))
    })

    expect(screen.getByText('Kết quả kiểm tra đã hết hạn, hãy tải file lên lại.')).toBeVisible()
    expect(screen.getByRole('button', { name: 'Xác nhận nhập dữ liệu' })).toBeDisabled()
  })

  it('Huỷ chỉ xoá kết quả trên trang, không gọi API', async () => {
    vi.mocked(uploadImportFile).mockResolvedValue(makePreview())
    renderPage()

    const user = await chooseAndUpload()
    await user.click(await screen.findByRole('button', { name: 'Huỷ' }))

    expect(screen.queryByText('Kết quả kiểm tra')).not.toBeInTheDocument()
    expect((screen.getByLabelText(/File CSV/) as HTMLInputElement).files).toHaveLength(0)
    expect(confirmImportRun).not.toHaveBeenCalled()
    expect(uploadImportFile).toHaveBeenCalledTimes(1)
  })

  it('tải mẫu dùng tên file từ server, không có thì theo dataType', async () => {
    const { downloadBlob } = await import('@/shared/lib/downloadBlob')
    const blob = new Blob(['hanzi,pinyin'])
    vi.mocked(getImportTemplateFile).mockResolvedValue({ blob })
    renderPage()
    const user = userEvent.setup()

    await user.selectOptions(await screen.findByLabelText(/Loại dữ liệu/), 'LEXEME')
    await user.click(screen.getByRole('button', { name: 'Tải mẫu' }))

    expect(getImportTemplateFile).toHaveBeenCalledWith('LEXEME')
    expect(downloadBlob).toHaveBeenCalledWith(blob, 'LEXEME.csv')
  })

  it('FR-040: lịch sử rỗng hiện thông báo', async () => {
    renderPage()
    expect(await screen.findByText('Chưa có lần nhập dữ liệu')).toBeVisible()
  })

  it('FR-040: bấm một dòng lịch sử mở báo cáo', async () => {
    vi.mocked(getImportRuns).mockResolvedValue({
      items: [completedRun],
      page: 0,
      size: 10,
      totalElements: 1,
    })
    renderPage()

    const row = await screen.findAllByRole('button', { name: 'Mở báo cáo lần nhập #42' })
    fireEvent.click(row[0] as HTMLElement)

    expect(await screen.findByTestId('location')).toHaveTextContent('/content/imports/42')
  })
})
