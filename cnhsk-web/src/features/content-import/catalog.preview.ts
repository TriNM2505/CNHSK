import { createElement } from 'react'

import type { PreviewEntry } from '@/app/preview/types'
import { AuthContext, type AuthContextValue } from '@/shared/auth'
import { AdminLayout } from '@/shared/layout'
import { previewContentEditor } from '@/shared/layout/Layout.preview'

import { LearningDataImportView } from './components/LearningDataImportView'
import { learningDataImportPreviews } from './components/LearningDataImportView.preview'
import { ImportReportView } from './components/ImportReportView'
import { importReportPreviews } from './components/ImportReportView.preview'

const importEntries = [
  ['scr-068-ready', 'Nhập dữ liệu — Mặc định', learningDataImportPreviews.ready],
  ['scr-068-loading', 'Nhập dữ liệu — Đang tải', learningDataImportPreviews.loading],
  ['scr-068-error', 'Nhập dữ liệu — Lỗi tải mẫu', learningDataImportPreviews.error],
  [
    'scr-068-empty-history',
    'Nhập dữ liệu — Chưa có lịch sử',
    learningDataImportPreviews.emptyHistory,
  ],
  ['scr-068-upload-error', 'Nhập dữ liệu — Lỗi file', learningDataImportPreviews.uploadError],
  ['scr-068-preview', 'Nhập dữ liệu — Kết quả kiểm tra', learningDataImportPreviews.preview],
  [
    'scr-068-already-imported',
    'Nhập dữ liệu — File đã nhập trước',
    learningDataImportPreviews.alreadyImported,
  ],
  ['scr-068-confirm', 'Nhập dữ liệu — Xác nhận ghi kho', learningDataImportPreviews.confirm],
  ['scr-068-expired', 'Nhập dữ liệu — Hết hạn kiểm tra', learningDataImportPreviews.expired],
] as const

const editorAuth: AuthContextValue = {
  error: null,
  reloadCurrentUser: async () => undefined,
  setAuthenticatedUser: () => undefined,
  signOut: async () => undefined,
  status: 'authenticated',
  user: previewContentEditor,
}

const learningDataImportEntries = importEntries.map(([id, title, props]) => ({
  fullFrame: true,
  group: 'screen' as const,
  id,
  render: () =>
    createElement(
      AuthContext.Provider,
      { value: editorAuth },
      createElement(AdminLayout, null, createElement(LearningDataImportView, props)),
    ),
  scr: 'SCR-068',
  title,
})) satisfies readonly PreviewEntry[]

const reportEntries = [
  ['scr-069-ready', 'Báo cáo nhập — Có dữ liệu', importReportPreviews.ready],
  ['scr-069-loading', 'Báo cáo nhập — Đang tải', importReportPreviews.loading],
  ['scr-069-empty', 'Báo cáo nhập — Không có lỗi', importReportPreviews.empty],
  ['scr-069-error', 'Báo cáo nhập — Lỗi tải', importReportPreviews.error],
  ['scr-069-running', 'Báo cáo nhập — Đang nhập', importReportPreviews.running],
] as const

const importReportEntries = reportEntries.map(([id, title, props]) => ({
  fullFrame: true,
  group: 'screen' as const,
  id,
  render: () =>
    createElement(
      AuthContext.Provider,
      { value: editorAuth },
      createElement(AdminLayout, null, createElement(ImportReportView, props)),
    ),
  scr: 'SCR-069',
  title,
})) satisfies readonly PreviewEntry[]

export const previews = [...learningDataImportEntries, ...importReportEntries]
