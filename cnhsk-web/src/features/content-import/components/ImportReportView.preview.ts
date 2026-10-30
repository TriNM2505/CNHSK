import type { ImportErrorPage, ImportRun } from '../types'
import type { ImportReportViewProps } from './ImportReportView'

const sampleRun: ImportRun = {
  completedAt: '2026-10-20T03:12:00Z',
  createdAt: '2026-10-20T03:10:00Z',
  createdBy: { displayName: 'Biên tập viên', id: 102 },
  dataType: 'LEXEME',
  errorCount: 2,
  fileName: 'tu-vung-hsk1.csv',
  result: {
    created: 18,
    failed: 1,
    invalid: 1,
    total: 40,
    unchanged: 10,
    updated: 10,
    valid: 39,
  },
  runId: 42,
  status: 'COMPLETED',
  totals: { total: 40, valid: 39, invalid: 1, create: 19, update: 10, unchanged: 10 },
}

const sampleErrors: ImportErrorPage = {
  items: [
    {
      code: 'INVALID_PINYIN',
      column: 'pinyin',
      line: 8,
      message: 'Pinyin của 学习 chứa ký tự không hợp lệ.',
      value: 'xue2xi',
    },
    {
      code: 'ROW_WRITE_FAILED',
      column: null,
      line: 23,
      message: 'Không ghi được dòng này do lỗi hệ thống — thử nhập lại.',
      value: '你好',
    },
  ],
  page: 0,
  size: 20,
  totalElements: 2,
}

const noop = () => undefined

const base = {
  downloadPending: false,
  errors: {
    data: sampleErrors,
    onFirstPage: noop,
    onPageChange: noop,
    onRetry: noop,
    state: 'ready',
  },
  onDownloadErrors: noop,
  onReload: noop,
  run: sampleRun,
  state: 'ready',
} satisfies ImportReportViewProps

const pending = {
  ...base,
  errors: {
    ...base.errors,
    data: { ...sampleErrors, items: sampleErrors.items.slice(0, 1), totalElements: 1 },
  },
  run: { ...sampleRun, completedAt: null, errorCount: 1, result: null, status: 'PENDING' },
} satisfies ImportReportViewProps

export const importReportPreviews = {
  earlyMultipartFailure: {
    ...base,
    errors: {
      ...base.errors,
      data: { ...sampleErrors, items: [], totalElements: 0 },
      state: 'empty',
    },
    run: {
      ...sampleRun,
      completedAt: null,
      dataType: null,
      errorCount: 0,
      failureReason: 'FILE_TOO_LARGE',
      fileName: null,
      result: null,
      status: 'FAILED',
      totals: null,
    },
  },
  empty: {
    ...base,
    errors: {
      ...base.errors,
      data: { ...sampleErrors, items: [], totalElements: 0 },
      state: 'empty',
    },
    run: {
      ...sampleRun,
      errorCount: 0,
      totals: { total: 40, valid: 40, invalid: 0, create: 20, update: 10, unchanged: 10 },
      result: {
        total: 40,
        valid: 40,
        invalid: 0,
        created: 20,
        updated: 10,
        unchanged: 10,
        failed: 0,
      },
    },
  },
  error: {
    ...base,
    error: { message: 'Không tải được báo cáo lần nhập.', requestId: 'mau-req-0069' },
    run: undefined,
    state: 'error',
  },
  loading: { ...base, run: undefined, state: 'loading' },
  ready: base,
  pending,
  expired: { ...pending, run: { ...pending.run, status: 'EXPIRED' } },
  running: {
    ...pending,
    run: { ...pending.run, status: 'RUNNING' },
  },
  errorsUnavailable: {
    ...base,
    errors: {
      ...base.errors,
      data: undefined,
      error: {
        message: 'Không tải được danh sách lỗi. Vui lòng thử lại.',
        requestId: 'mau-errors-0069',
      },
      state: 'error',
    },
  },
  pageOutOfRange: {
    ...base,
    errors: {
      ...base.errors,
      data: { ...sampleErrors, items: [], page: 99 },
      state: 'empty',
    },
  },
  downloading: { ...base, downloadPending: true },
  downloadError: {
    ...base,
    downloadError: {
      message: 'Chưa tải được danh sách lỗi. Vui lòng thử lại.',
      requestId: 'mau-download-0069',
    },
  },
} satisfies Record<string, ImportReportViewProps>
