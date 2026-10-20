import type { ImportErrorPage, ImportRun } from '../types'
import type { ImportReportViewProps } from './ImportReportView'

const sampleRun: ImportRun = {
  completedAt: '2026-10-20T03:12:00Z',
  createdAt: '2026-10-20T03:10:00Z',
  createdBy: { displayName: 'Nguyễn Minh Anh', id: 102 },
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

export const importReportPreviews = {
  empty: {
    ...base,
    errors: {
      ...base.errors,
      data: { ...sampleErrors, items: [], totalElements: 0 },
      state: 'empty',
    },
    run: { ...sampleRun, errorCount: 0 },
  },
  error: {
    ...base,
    error: { message: 'Không tải được báo cáo lần nhập.', requestId: 'mau-req-0069' },
    run: undefined,
    state: 'error',
  },
  loading: { ...base, run: undefined, state: 'loading' },
  ready: base,
  running: {
    ...base,
    errors: {
      ...base.errors,
      data: { ...sampleErrors, items: [], totalElements: 0 },
      state: 'empty',
    },
    run: { ...sampleRun, completedAt: null, errorCount: 0, result: null, status: 'RUNNING' },
  },
} satisfies Record<string, ImportReportViewProps>
