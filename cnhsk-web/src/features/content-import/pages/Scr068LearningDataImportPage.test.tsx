import { act, fireEvent, render, screen, waitFor, within } from '@testing-library/react'
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
import type { ImportPreview, ImportRun, ImportRunPage, ImportTemplate } from '../types'
import Scr068LearningDataImportPage from './Scr068LearningDataImportPage'

vi.mock('../api/imports', () => ({
  confirmImportRun: vi.fn(),
  getImportRuns: vi.fn(),
  getImportTemplateFile: vi.fn(),
  getImportTemplates: vi.fn(),
  uploadImportFile: vi.fn(),
}))

vi.mock('@/shared/lib/downloadBlob', () => ({ downloadBlob: vi.fn() }))

function column(name: string, required: boolean, maxLength: number | null = null, example = '') {
  return { name, required, maxLength, example }
}

const templates: ImportTemplate[] = [
  {
    dataType: 'LEXEME',
    label: 'Chữ và từ vựng',
    naturalKey: ['kind', 'text'],
    dependsOn: [],
    columns: [
      column('kind', true),
      column('text', true, 20, '我们'),
      column('pinyin', true, 200),
      column('sino_viet', false, 200),
      column('meaning_vi', true, 1000),
      column('hsk_level', true),
      column('audio_url', false, 500),
      column('radical', false, 1),
      column('stroke_count', false),
      column('example_cn', false, 300),
      column('example_pinyin', false, 500),
      column('example_vi', false, 500),
      column('tags', false),
    ],
  },
  {
    dataType: 'TOPIC',
    label: 'Chủ đề từ vựng',
    naturalKey: ['code'],
    dependsOn: [],
    columns: [
      column('code', true, 50),
      column('name', true, 200),
      column('hsk_level', true),
      column('order_index', false),
    ],
  },
  {
    dataType: 'TOPIC_ITEM',
    label: 'Từ trong chủ đề',
    naturalKey: ['topic_code', 'kind', 'text'],
    dependsOn: ['TOPIC', 'LEXEME'],
    columns: [
      column('topic_code', true),
      column('kind', true),
      column('text', true),
      column('order_index', false),
    ],
  },
  {
    dataType: 'GRAMMAR',
    label: 'Điểm ngữ pháp',
    naturalKey: ['text'],
    dependsOn: [],
    columns: [
      column('text', true, 200),
      column('hsk_level', true),
      column('title_vi', true, 300),
      column('structure', false, 300),
      column('explanation_vi', true, 3000),
      column('common_mistake_vi', false, 1000),
      column('example1_cn', true, 300),
      column('example2_cn', false, 300),
      column('example3_cn', false, 300),
      column('example1_pinyin', false, 500),
      column('example2_pinyin', false, 500),
      column('example3_pinyin', false, 500),
      column('example1_vi', true, 500),
      column('example2_vi', false, 500),
      column('example3_vi', false, 500),
    ],
  },
  {
    dataType: 'GRAMMAR_EXERCISE',
    label: 'Bài tập ngữ pháp',
    naturalKey: ['grammar_text', 'exercise_no'],
    dependsOn: ['GRAMMAR'],
    columns: [
      column('grammar_text', true),
      column('exercise_no', true),
      column('stem', true, 500),
      column('option_a', true, 200),
      column('option_b', true, 200),
      column('option_c', false, 200),
      column('option_d', false, 200),
      column('answer', true),
      column('explanation_vi', false, 1000),
    ],
  },
  {
    dataType: 'PRON_STAGE',
    label: 'Tầng phát âm',
    naturalKey: ['stage_index'],
    dependsOn: [],
    columns: [
      column('stage_index', true),
      column('name', true, 100),
      column('description', false, 2000),
      column('target_sounds', false),
      column('pass_percent', false),
    ],
  },
  {
    dataType: 'PRON_UNIT',
    label: 'Bài nghe phát âm',
    naturalKey: ['stage_index', 'unit_code'],
    dependsOn: ['PRON_STAGE'],
    columns: [
      column('stage_index', true),
      column('unit_code', true, 30),
      column('audio_url', true, 500),
      column('prompt_vi', false, 200),
      column('option_a', true, 50),
      column('option_b', true, 50),
      column('option_c', false, 50),
      column('option_d', false, 50),
      column('answer', true),
      column('explanation_vi', false, 500),
    ],
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
    preview: [
      { action: 'CREATE', line: 2, values: { kind: 'WORD', text: '学习', pinyin: 'xuéxí' } },
    ],
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

function deferred<T>() {
  let resolve!: (value: T) => void
  const promise = new Promise<T>((complete) => {
    resolve = complete
  })
  return { promise, resolve }
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

  it('FR006: shows missing, unknown and duplicate column groups below the file input', async () => {
    vi.mocked(uploadImportFile).mockRejectedValue(
      new ApiError({
        errorCode: 'TEMPLATE_MISMATCH',
        message: 'Sai mẫu',
        status: 422,
        details: [
          { field: 'pinyin', message: 'Thiếu cột bắt buộc: pinyin' },
          { field: 'hsk_level', message: 'Thiếu cột bắt buộc: hsk_level' },
          { field: 'note', message: 'Cột không có trong mẫu: note' },
          { field: 'text', message: 'Tên cột bị trùng sau khi bỏ khoảng trắng: text' },
        ],
      }),
    )
    renderPage()
    await chooseAndUpload()
    const alert = await screen.findByRole('alert')
    expect(within(alert).getByText('Thiếu cột: pinyin, hsk_level')).toBeVisible()
    expect(within(alert).getByText('Cột không thuộc mẫu: note')).toBeVisible()
    expect(within(alert).getByText('Cột bị trùng: text')).toBeVisible()
    expect(screen.getByLabelText(/File CSV/)).toHaveAttribute(
      'aria-describedby',
      expect.stringContaining('import-upload-error'),
    )
  })

  it('FR006: a template mismatch without details keeps the general message', async () => {
    vi.mocked(uploadImportFile).mockRejectedValue(
      new ApiError({
        errorCode: 'TEMPLATE_MISMATCH',
        message: 'Sai mẫu',
        status: 422,
      }),
    )
    renderPage()
    await chooseAndUpload()
    const alert = await screen.findByRole('alert')
    expect(within(alert).getByText('Các cột trong file không khớp mẫu đã chọn.')).toBeVisible()
    expect(
      within(alert).queryByText(/Thiếu cột:|Cột không thuộc mẫu:|Cột bị trùng:/),
    ).not.toBeInTheDocument()
  })

  it('FR006: omits empty column groups when only required columns are missing', async () => {
    vi.mocked(uploadImportFile).mockRejectedValue(
      new ApiError({
        errorCode: 'TEMPLATE_MISMATCH',
        message: 'Sai mẫu',
        status: 422,
        details: [{ field: 'pinyin', message: 'Thiếu cột bắt buộc: pinyin' }],
      }),
    )
    renderPage()
    await chooseAndUpload()
    const alert = await screen.findByRole('alert')
    expect(within(alert).getByText('Thiếu cột: pinyin')).toBeVisible()
    expect(within(alert).queryByText(/Cột không thuộc mẫu:|Cột bị trùng:/)).not.toBeInTheDocument()
  })

  it('FR008_FR026: lịch sử hiển thị lần upload bị chặn trước khi đọc metadata', async () => {
    vi.mocked(getImportRuns).mockResolvedValue({
      items: [
        {
          ...completedRun,
          dataType: null,
          fileName: null,
          status: 'FAILED',
          failureReason: 'FILE_TOO_LARGE',
        },
      ],
      page: 0,
      size: 10,
      totalElements: 1,
    })
    renderPage()
    const table = await screen.findByRole('table', { name: 'Lịch sử các lần nhập dữ liệu' })
    const fileName = await within(table).findByText('Không đọc được tên tệp')
    const row = fileName.closest('tr')!
    expect(within(row).getByText('—')).toBeVisible()
    await userEvent.click(row)
    expect(await screen.findByTestId('location')).toHaveTextContent('/content/imports/42')
  })

  it.each([
    { reason: 'FILE_TOO_LARGE', message: 'File vượt quá giới hạn 2 MB.' },
    { reason: 'ENCODING_ERROR', message: 'File không phải UTF-8 hoặc chữ Hán bị hỏng.' },
    { reason: 'INTERRUPTED', message: 'Quá trình nhập bị gián đoạn.' },
    { reason: 'PRIVATE_DATABASE_DETAIL', message: 'Không thể hoàn tất lần nhập dữ liệu.' },
    { reason: undefined, message: 'Không thể hoàn tất lần nhập dữ liệu.' },
  ])('FR029: lịch sử FAILED hiện lý do ngắn $reason', async ({ reason, message }) => {
    vi.mocked(getImportRuns).mockResolvedValue({
      items: [{ ...completedRun, status: 'FAILED', failureReason: reason }],
      page: 0,
      size: 10,
      totalElements: 1,
    })
    renderPage()
    const table = await screen.findByRole('table', { name: 'Lịch sử các lần nhập dữ liệu' })
    expect(within(table).getByText(message)).toBeVisible()
    expect(within(table).queryByText('PRIVATE_DATABASE_DETAIL')).not.toBeInTheDocument()
  })

  it('FR029: chỉ hiện lý do thất bại ở lần nhập FAILED', async () => {
    vi.mocked(getImportRuns).mockResolvedValue({
      items: [{ ...completedRun, failureReason: 'INTERRUPTED' }],
      page: 0,
      size: 10,
      totalElements: 1,
    })
    renderPage()
    const table = await screen.findByRole('table', { name: 'Lịch sử các lần nhập dữ liệu' })
    expect(within(table).queryByText('Quá trình nhập bị gián đoạn.')).not.toBeInTheDocument()
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
    expect(within(previewTable).getByRole('columnheader', { name: 'text' })).toBeInTheDocument()
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
    expect(screen.getByLabelText(/File CSV/)).toHaveAccessibleDescription(
      /Các cột trong file không khớp mẫu đã chọn/,
    )
    expect(screen.getByText('Mã lỗi: req-9')).toBeVisible()
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
    const blob = new Blob(['kind,text,pinyin'])
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

  it.each([
    ['LEXEME', 'Chữ và từ vựng', 'kind, text', 13],
    ['TOPIC', 'Chủ đề từ vựng', 'code', 4],
    ['TOPIC_ITEM', 'Từ trong chủ đề', 'topic_code, kind, text', 4],
    ['GRAMMAR', 'Điểm ngữ pháp', 'text', 15],
    ['GRAMMAR_EXERCISE', 'Bài tập ngữ pháp', 'grammar_text, exercise_no', 9],
    ['PRON_STAGE', 'Tầng phát âm', 'stage_index', 5],
    ['PRON_UNIT', 'Bài nghe phát âm', 'stage_index, unit_code', 10],
  ] as const)(
    'FR036_guide_%s: chọn đủ bảy mẫu và hướng dẫn đúng khóa/cột',
    async (type, label, key, count) => {
      renderPage()
      const user = userEvent.setup()
      const select = await screen.findByLabelText(/Loại dữ liệu/)
      expect(within(select).getAllByRole('option')).toHaveLength(8)
      expect(within(select).getByRole('option', { name: label })).toHaveValue(type)
      await user.selectOptions(select, type)
      expect(screen.getAllByText(key)[0]).toBeVisible()
      const table = screen.getByRole('table', { name: `Danh sách cột mẫu ${label}` })
      expect(within(table).getAllByRole('row')).toHaveLength(Number(count) + 1)
      for (const header of ['Tên cột', 'Bắt buộc', 'Tối đa', 'Mô tả', 'Ví dụ']) {
        expect(within(table).getByRole('columnheader', { name: header })).toBeVisible()
      }
      expect(screen.getByLabelText(/File CSV/)).toHaveAttribute('accept', '.csv')
    },
  )

  it('FR036_guideDetails: hiện giới hạn, ví dụ và mẫu phụ thuộc từ API', async () => {
    renderPage('/content/imports?dataType=LEXEME')
    const table = await screen.findByRole('table', { name: 'Danh sách cột mẫu Chữ và từ vựng' })
    const textRow = within(table).getByText('text').closest('tr')!
    expect(within(textRow).getByText('Có')).toBeVisible()
    expect(within(textRow).getByText('20 ký tự')).toBeVisible()
    expect(within(textRow).getByText('我们')).toHaveAttribute('lang', 'zh-CN')
    await userEvent.selectOptions(screen.getByLabelText(/Loại dữ liệu/), 'TOPIC_ITEM')
    expect(screen.getByText(/Cần nhập trước: Chủ đề từ vựng, Chữ và từ vựng/)).toBeVisible()
  })

  it('FR036_exactFileLimit: nhận đúng 2 MB và khóa thao tác suốt lúc kiểm tra', async () => {
    const pending = deferred<ImportPreview>()
    vi.mocked(uploadImportFile).mockReturnValue(pending.promise)
    renderPage()
    const file = csvFile('du-2mb.csv', 2 * 1024 * 1024)
    const user = await chooseAndUpload(file)
    expect(uploadImportFile).toHaveBeenCalledWith('LEXEME', file)
    expect(screen.getByLabelText(/Loại dữ liệu/)).toBeDisabled()
    expect(screen.getByLabelText(/File CSV/)).toBeDisabled()
    expect(screen.getByRole('button', { name: 'Tải mẫu' })).toBeDisabled()
    expect(screen.getByRole('button', { name: 'Đang kiểm tra' })).toBeDisabled()
    await user.click(screen.getByRole('button', { name: 'Đang kiểm tra' }))
    expect(uploadImportFile).toHaveBeenCalledTimes(1)
    await act(async () => pending.resolve(makePreview()))
    expect(screen.getByLabelText(/File CSV/)).toBeEnabled()
    expect(screen.getByText('Kết quả kiểm tra')).toBeVisible()
  })

  it('FR036_download: giữ tên file server và cho thử lại sau lỗi tải mẫu', async () => {
    const { downloadBlob } = await import('@/shared/lib/downloadBlob')
    const blob = new Blob(['kind,text,pinyin'])
    vi.mocked(getImportTemplateFile)
      .mockRejectedValueOnce(
        new ApiError({ errorCode: 'INTERNAL_ERROR', message: 'x', status: 500 }),
      )
      .mockResolvedValueOnce({ blob, fileName: 'lexeme-v0.csv' })
    renderPage('/content/imports?dataType=LEXEME')
    const user = userEvent.setup()
    await user.click(await screen.findByRole('button', { name: 'Tải mẫu' }))
    expect(await screen.findByText('Chưa tải được file mẫu. Vui lòng thử lại.')).toBeVisible()
    await user.click(screen.getByRole('button', { name: 'Tải mẫu' }))
    expect(downloadBlob).toHaveBeenCalledWith(blob, 'lexeme-v0.csv')
    expect(screen.queryByText('Chưa tải được file mẫu. Vui lòng thử lại.')).not.toBeInTheDocument()
  })

  it('FR037_totals: giữ số liệu server và đủ ba hành động trong bảng xem trước', async () => {
    vi.mocked(uploadImportFile).mockResolvedValue(
      makePreview({
        totals: { total: 5, valid: 3, invalid: 2, create: 1, update: 1, unchanged: 1 },
        preview: [
          { line: 2, action: 'CREATE', values: { kind: 'WORD', text: '学习' } },
          { line: 3, action: 'UPDATE', values: { kind: 'WORD', text: '妈妈' } },
          { line: 4, action: 'UNCHANGED', values: { kind: 'WORD', text: '你好' } },
        ],
      }),
    )
    renderPage()
    await chooseAndUpload()
    const section = screen.getByRole('region', { name: 'Kết quả kiểm tra' })
    for (const [label, value] of [
      ['Tổng số dòng', 5],
      ['Hợp lệ', 3],
      ['Không hợp lệ', 2],
      ['Tạo mới', 1],
      ['Cập nhật', 1],
      ['Không đổi', 1],
    ] as const) {
      expect(within(section).getAllByText(label)[0]!.nextElementSibling).toHaveTextContent(
        String(value),
      )
    }
    const table = within(section).getByRole('table', { name: 'Xem trước các dòng hợp lệ đầu tiên' })
    expect(within(table).getAllByRole('columnheader')).toHaveLength(15)
    for (const action of ['Tạo mới', 'Cập nhật', 'Không đổi']) {
      expect(within(table).getByText(action)).toBeVisible()
    }
  })

  it('FR037_rerunChanged: server yêu cầu nhập lại thì hiện tick dù preview ban đầu chưa trùng', async () => {
    vi.mocked(uploadImportFile).mockResolvedValue(makePreview())
    vi.mocked(confirmImportRun)
      .mockRejectedValueOnce(
        new ApiError({ errorCode: 'RERUN_CONFIRMATION_REQUIRED', message: 'x', status: 409 }),
      )
      .mockResolvedValueOnce(completedRun)
    renderPage()
    const user = await chooseAndUpload()
    await user.click(screen.getByRole('button', { name: 'Xác nhận nhập dữ liệu' }))
    await user.click(screen.getByRole('button', { name: 'Ghi vào kho' }))
    const checkbox = await screen.findByLabelText('Tôi hiểu file này đã nhập trước đó')
    expect(checkbox).not.toBeChecked()
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Xác nhận nhập dữ liệu' })).toBeDisabled()
    expect(confirmImportRun).toHaveBeenNthCalledWith(1, 42, { acknowledgeRerun: false })
    await user.click(checkbox)
    await user.click(screen.getByRole('button', { name: 'Xác nhận nhập dữ liệu' }))
    await user.click(screen.getByRole('button', { name: 'Ghi vào kho' }))
    expect(confirmImportRun).toHaveBeenNthCalledWith(2, 42, { acknowledgeRerun: true })
    expect(await screen.findByTestId('location')).toHaveTextContent('/content/imports/42')
  })

  it('FR037_uncheck: đóng xác nhận khi bỏ tick nhập lại', async () => {
    vi.mocked(uploadImportFile).mockResolvedValue(makePreview({ alreadyImported: true }))
    renderPage()
    const user = await chooseAndUpload()
    const checkbox = screen.getByLabelText('Tôi hiểu file này đã nhập trước đó')
    await user.click(checkbox)
    await user.click(screen.getByRole('button', { name: 'Xác nhận nhập dữ liệu' }))
    expect(screen.getByRole('dialog')).toBeVisible()
    fireEvent.click(checkbox)
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Xác nhận nhập dữ liệu' })).toBeDisabled()
    expect(confirmImportRun).not.toHaveBeenCalled()
  })

  it.each(['open', 'submit'])(
    'FR037_expiry_%s: kiểm hạn ngay lúc thao tác, trước tick đồng hồ',
    async (step) => {
      vi.mocked(uploadImportFile).mockResolvedValue(makePreview())
      renderPage()
      const user = await chooseAndUpload()
      if (step === 'submit') {
        await user.click(screen.getByRole('button', { name: 'Xác nhận nhập dữ liệu' }))
      }
      vi.mocked(Date.now).mockReturnValue(NOW + 30 * 60 * 1000)
      fireEvent.click(
        screen.getByRole('button', {
          name: step === 'open' ? 'Xác nhận nhập dữ liệu' : 'Ghi vào kho',
        }),
      )
      expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
      expect(screen.getByText('Kết quả kiểm tra đã hết hạn, hãy tải file lên lại.')).toBeVisible()
      expect(confirmImportRun).not.toHaveBeenCalled()
    },
  )

  it('FR038_confirmation: chỉ gọi confirm sau bước trong trang, có thể hủy', async () => {
    vi.mocked(uploadImportFile).mockResolvedValue(makePreview())
    const browserConfirm = vi.spyOn(window, 'confirm')
    renderPage()
    const user = await chooseAndUpload()
    await user.click(screen.getByRole('button', { name: 'Xác nhận nhập dữ liệu' }))
    const panel = screen.getByRole('dialog', { name: 'Xác nhận nhập dữ liệu' })
    expect(within(panel).getByText('Ghi 1 dòng vào kho? Không hoàn tác được.')).toBeVisible()
    expect(confirmImportRun).not.toHaveBeenCalled()
    expect(browserConfirm).not.toHaveBeenCalled()
    await user.click(within(panel).getByRole('button', { name: 'Huỷ' }))
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    expect(screen.getByText('Kết quả kiểm tra')).toBeVisible()
    expect(confirmImportRun).not.toHaveBeenCalled()
  })

  it('FR038_pending: khóa tải/đổi/hủy/ghi lần hai đến khi confirm trả về', async () => {
    const pending = deferred<ImportRun>()
    vi.mocked(uploadImportFile).mockResolvedValue(makePreview({ alreadyImported: true }))
    vi.mocked(confirmImportRun).mockReturnValue(pending.promise)
    vi.mocked(getImportRuns).mockResolvedValue({
      items: [completedRun],
      page: 0,
      size: 10,
      totalElements: 11,
    })
    renderPage()
    const user = await chooseAndUpload()
    await user.click(screen.getByLabelText('Tôi hiểu file này đã nhập trước đó'))
    await user.click(screen.getByRole('button', { name: 'Xác nhận nhập dữ liệu' }))
    await user.click(screen.getByRole('button', { name: 'Ghi vào kho' }))
    const panel = screen.getByRole('dialog')
    expect(within(panel).getByRole('button', { name: 'Đang xử lý' })).toBeDisabled()
    expect(within(panel).getByRole('button', { name: 'Huỷ' })).toBeDisabled()
    expect(screen.getByLabelText(/Loại dữ liệu/)).toBeDisabled()
    expect(screen.getByLabelText(/File CSV/)).toBeDisabled()
    expect(screen.getByLabelText('Tôi hiểu file này đã nhập trước đó')).toBeDisabled()
    for (const name of ['Tải mẫu', 'Tải lên và kiểm tra', 'Xác nhận nhập dữ liệu', 'Sau']) {
      expect(screen.getByRole('button', { name })).toBeDisabled()
    }
    expect(screen.getAllByRole('button', { name: 'Huỷ' })).toHaveLength(2)
    for (const button of screen.getAllByRole('button', { name: 'Huỷ' }))
      expect(button).toBeDisabled()
    fireEvent.submit(screen.getByLabelText(/File CSV/).closest('form')!)
    fireEvent.click(screen.getByRole('button', { name: 'Mở báo cáo lần nhập #42' }))
    fireEvent.click(screen.getByRole('link', { name: 'Xem tất cả 1 lỗi' }))
    await user.keyboard('{Escape}')
    expect(screen.getByRole('dialog')).toBeVisible()
    expect(screen.queryByTestId('location')).not.toBeInTheDocument()
    expect(uploadImportFile).toHaveBeenCalledTimes(1)
    expect(confirmImportRun).toHaveBeenCalledTimes(1)
    await act(async () => pending.resolve(completedRun))
    expect(await screen.findByTestId('location')).toHaveTextContent('/content/imports/42')
  })

  it('FR040_pagination: đổi trang lịch sử với 10 lần nhập, giữ trạng thái chờ rồi mở báo cáo', async () => {
    const secondPage = deferred<ImportRunPage>()
    vi.mocked(getImportRuns)
      .mockResolvedValueOnce({ items: [completedRun], page: 0, size: 10, totalElements: 11 })
      .mockReturnValueOnce(secondPage.promise)
    renderPage()
    const pagination = await screen.findByRole('navigation', { name: 'Phân trang lịch sử nhập' })
    expect(within(pagination).getByText('Trang 1 / 2')).toBeVisible()
    await userEvent.click(within(pagination).getByRole('button', { name: 'Sau' }))
    expect(getImportRuns).toHaveBeenNthCalledWith(2, 1, expect.any(AbortSignal))
    expect(
      screen.queryByRole('table', { name: 'Lịch sử các lần nhập dữ liệu' }),
    ).not.toBeInTheDocument()
    expect(await screen.findByRole('status', { name: 'Đang tải' })).toBeVisible()
    await act(async () =>
      secondPage.resolve({
        items: [{ ...completedRun, runId: 43 }],
        page: 1,
        size: 10,
        totalElements: 11,
      }),
    )
    expect(screen.getByText('Trang 2 / 2')).toBeVisible()
    expect(screen.getByRole('button', { name: 'Sau' })).toBeDisabled()
    await userEvent.click(screen.getByRole('button', { name: 'Mở báo cáo lần nhập #43' }))
    expect(await screen.findByTestId('location')).toHaveTextContent('/content/imports/43')
  })

  it('FR040_historyRetry: lỗi lịch sử hiện requestId, thử lại độc lập với phần upload', async () => {
    vi.mocked(getImportRuns)
      .mockRejectedValueOnce(
        new ApiError({
          errorCode: 'INTERNAL_ERROR',
          message: 'Chưa tải được lịch sử.',
          requestId: 'history-42',
          status: 500,
        }),
      )
      .mockResolvedValueOnce({ items: [], page: 0, size: 10, totalElements: 0 })
    renderPage()
    expect(await screen.findByText('Chưa tải được lịch sử.')).toBeVisible()
    expect(screen.getByText('Mã lỗi: history-42')).toBeVisible()
    expect(screen.getByRole('button', { name: 'Tải lên và kiểm tra' })).toBeEnabled()
    await userEvent.click(screen.getByRole('button', { name: 'Thử lại' }))
    expect(await screen.findByText('Chưa có lần nhập dữ liệu')).toBeVisible()
    expect(getImportTemplates).toHaveBeenCalledTimes(1)
  })

  it('FR036_templateRetry: lỗi tải mẫu có thể thử lại, lịch sử vẫn hiển thị', async () => {
    vi.mocked(getImportTemplates)
      .mockRejectedValueOnce(
        new ApiError({ errorCode: 'INTERNAL_ERROR', message: 'Chưa tải được mẫu.', status: 500 }),
      )
      .mockResolvedValueOnce(templates)
    renderPage()
    expect(await screen.findByText('Chưa tải được mẫu.')).toBeVisible()
    expect(screen.getByText('Chưa có lần nhập dữ liệu')).toBeVisible()
    await userEvent.click(screen.getByRole('button', { name: 'Thử lại' }))
    await waitFor(() => expect(screen.getByLabelText(/Loại dữ liệu/)).toBeEnabled())
    expect(getImportRuns).toHaveBeenCalledTimes(1)
  })
})
