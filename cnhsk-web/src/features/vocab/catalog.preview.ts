import { createElement } from 'react'

import type { PreviewEntry } from '@/app/preview/types'
import { AuthContext, type AuthContextValue } from '@/shared/auth'
import { LearnerLayout } from '@/shared/layout'
import { previewLearner } from '@/shared/layout/Layout.preview'

import { TopicListView } from './components/TopicListView'
import { topicListPreviews } from './components/TopicListView.preview'

const learnerAuth: AuthContextValue = {
  error: null,
  reloadCurrentUser: async () => undefined,
  setAuthenticatedUser: () => undefined,
  signOut: async () => undefined,
  status: 'authenticated',
  user: previewLearner,
}

const entries = [
  ['scr-011-ready', 'Chủ đề từ vựng — Mặc định', topicListPreviews.ready],
  ['scr-011-loading', 'Chủ đề từ vựng — Đang tải', topicListPreviews.loading],
  ['scr-011-empty', 'Chủ đề từ vựng — Chưa có chủ đề', topicListPreviews.empty],
  ['scr-011-error', 'Chủ đề từ vựng — Lỗi tải', topicListPreviews.error],
  ['scr-011-filtered-empty', 'Chủ đề từ vựng — Không khớp bộ lọc', topicListPreviews.filteredEmpty],
] as const

export const previews = entries.map(([id, title, props]) => ({
  fullFrame: true,
  group: 'screen' as const,
  id,
  render: () =>
    createElement(
      AuthContext.Provider,
      { value: learnerAuth },
      createElement(LearnerLayout, null, createElement(TopicListView, props)),
    ),
  scr: 'SCR-011',
  title,
})) satisfies readonly PreviewEntry[]
