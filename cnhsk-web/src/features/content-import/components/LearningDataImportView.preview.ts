import type { ImportPreview, ImportRunPage, ImportTemplate } from '../types'
import type { LearningDataImportViewProps } from './LearningDataImportView'

/** Dữ liệu mẫu chỉ dùng cho /__preview. */
const sampleTemplates: ImportTemplate[] = [
  {
    columns: [
      {
        description: 'Chữ Hán giản thể',
        example: '学习',
        maxLength: 32,
        name: 'hanzi',
        required: true,
      },
      {
        description: 'Pinyin có dấu thanh',
        example: 'xuéxí',
        maxLength: 64,
        name: 'pinyin',
        required: true,
      },
      {
        description: 'Nghĩa tiếng Việt',
        example: 'học tập',
        maxLength: 255,
        name: 'meaning_vi',
        required: true,
      },
      {
        description: 'Cấp HSK 1–9',
        example: '1',
        maxLength: null,
        name: 'hsk_level',
        required: true,
      },
    ],
    dataType: 'LEXEME',
    dependsOn: [],
    label: 'Chữ và từ vựng',
    naturalKey: ['hanzi', 'pinyin'],
  },
  {
    columns: [
      {
        description: 'Mã chủ đề',
        example: 'gia-dinh',
        maxLength: 64,
        name: 'topic_code',
        required: true,
      },
      {
        description: 'Chữ Hán của từ',
        example: '妈妈',
        maxLength: 32,
        name: 'hanzi',
        required: true,
      },
      {
        description: 'Pinyin của từ',
        example: 'māma',
        maxLength: 64,
        name: 'pinyin',
        required: true,
      },
    ],
    dataType: 'TOPIC_ITEM',
    dependsOn: ['TOPIC', 'LEXEME'],
    label: 'Từ trong chủ đề',
    naturalKey: ['topic_code', 'hanzi', 'pinyin'],
  },
]

const samplePreview: ImportPreview = {
  alreadyImported: false,
  dataType: 'LEXEME',
  errorCount: 2,
  errors: [
    {
      code: 'INVALID_PINYIN',
      column: 'pinyin',
      line: 5,
      message: 'Pinyin không hợp lệ.',
      value: 'xue2xi',
    },
    {
      code: 'REQUIRED_MISSING',
      column: 'meaning_vi',
      line: 9,
      message: 'Thiếu nghĩa tiếng Việt.',
      value: null,
    },
  ],
  expiresAt: '2026-10-20T05:30:00Z',
  fileName: 'tu-vung-hsk1.csv',
  preview: [
    {
      action: 'CREATE',
      line: 2,
      values: { hanzi: '学习', hsk_level: '1', meaning_vi: 'học tập', pinyin: 'xuéxí' },
    },
    {
      action: 'UPDATE',
      line: 3,
      values: { hanzi: '妈妈', hsk_level: '1', meaning_vi: 'mẹ', pinyin: 'māma' },
    },
    {
      action: 'UNCHANGED',
      line: 4,
      values: { hanzi: '你好', hsk_level: '1', meaning_vi: 'xin chào', pinyin: 'nǐ hǎo' },
    },
  ],
  runId: 42,
  status: 'PENDING',
  totals: { create: 1, invalid: 2, total: 5, unchanged: 1, update: 1, valid: 3 },
}

const sampleHistory: ImportRunPage = {
  items: [
    {
      createdAt: '2026-10-19T08:15:00Z',
      createdBy: { displayName: 'Biên tập viên', id: 102 },
      dataType: 'LEXEME',
      errorCount: 0,
      fileName: 'tu-vung-hsk1.csv',
      runId: 41,
      status: 'COMPLETED',
    },
    {
      createdAt: '2026-10-18T03:40:00Z',
      createdBy: { displayName: 'Biên tập viên', id: 102 },
      dataType: 'GRAMMAR',
      errorCount: 1,
      failureReason: 'ENCODING_ERROR',
      fileName: 'ngu-phap.csv',
      runId: 40,
      status: 'FAILED',
    },
  ],
  page: 0,
  size: 10,
  totalElements: 2,
}

const noop = () => undefined

const base = {
  acknowledgeRerun: false,
  confirmOpen: false,
  confirmPending: false,
  dataType: 'LEXEME',
  downloadPending: false,
  fileInputKey: 0,
  history: {
    data: sampleHistory,
    onOpenRun: noop,
    onPageChange: noop,
    onRetry: noop,
    state: 'ready',
  },
  onAcknowledgeChange: noop,
  onCancelConfirm: noop,
  onCancelPreview: noop,
  onConfirm: noop,
  onDataTypeChange: noop,
  onDownloadTemplate: noop,
  onFileChange: noop,
  onRequestConfirm: noop,
  onRetry: noop,
  onUpload: noop,
  remainingSeconds: 1500,
  selectionErrors: {},
  state: 'ready',
  templates: sampleTemplates,
  uploading: false,
} satisfies LearningDataImportViewProps

export const learningDataImportPreviews = {
  earlyMultipartFailure: {
    ...base,
    history: {
      ...base.history,
      data: {
        ...sampleHistory,
        items: [
          {
            createdAt: '2026-10-20T05:00:00Z',
            createdBy: { displayName: 'Biên tập viên', id: 102 },
            dataType: null,
            errorCount: 0,
            failureReason: 'FILE_TOO_LARGE',
            fileName: null,
            runId: 43,
            status: 'FAILED',
          },
          ...sampleHistory.items,
        ],
        totalElements: sampleHistory.totalElements + 1,
      },
    },
  },
  ready: base,
  loading: { ...base, history: { ...base.history, state: 'loading' }, state: 'loading' },
  error: {
    ...base,
    loadError: {
      message: 'Không tải được danh sách mẫu. Vui lòng thử lại.',
      requestId: 'mau-req-0068',
    },
    state: 'error',
  },
  emptyHistory: { ...base, history: { ...base.history, data: undefined, state: 'empty' } },
  uploadError: {
    ...base,
    uploadError: {
      message: 'Các cột trong file không khớp mẫu đã chọn.',
      requestId: 'mau-req-0069',
    },
  },
  preview: { ...base, preview: samplePreview },
  alreadyImported: { ...base, preview: { ...samplePreview, alreadyImported: true } },
  confirm: { ...base, confirmOpen: true, preview: samplePreview },
  expired: { ...base, preview: samplePreview, remainingSeconds: 0 },
} satisfies Record<string, LearningDataImportViewProps>
