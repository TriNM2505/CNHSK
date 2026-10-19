import { createElement } from 'react'

import type { PreviewEntry } from '@/app/preview/types'
import { AuthContext, type AuthContextValue } from '@/shared/auth'
import { AdminLayout } from '@/shared/layout'
import { previewContentEditor } from '@/shared/layout/Layout.preview'

import { LearningDataImportView } from './components/LearningDataImportView'
import { learningDataImportPreviews } from './components/LearningDataImportView.preview'

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

export const previews = importEntries.map(([id, title, props]) => ({
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
