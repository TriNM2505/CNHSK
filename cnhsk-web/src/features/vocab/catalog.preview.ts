import { createElement } from 'react'

import type { PreviewEntry } from '@/app/preview/types'
import { AuthContext, type AuthContextValue } from '@/shared/auth'
import { LearnerLayout } from '@/shared/layout'
import { previewLearner } from '@/shared/layout/Layout.preview'

import { TopicListView } from './components/TopicListView'
import { topicListPreviews } from './components/TopicListView.preview'
import { StudyStepView, TopicStudyView } from './components/TopicStudyView'
import { studyStepPreviews, topicStudyPreviews } from './components/TopicStudyView.preview'

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

const topicListEntries = entries.map(([id, title, props]) => ({
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

const studyEntries = [
  ['scr-012-ready', 'Học chủ đề — Thẻ từ', topicStudyPreviews.ready, studyStepPreviews.ready],
  ['scr-012-loading', 'Học chủ đề — Đang tải', topicStudyPreviews.loading, undefined],
  ['scr-012-empty', 'Học chủ đề — Không tìm thấy', topicStudyPreviews.empty, undefined],
  ['scr-012-error', 'Học chủ đề — Lỗi tải', topicStudyPreviews.error, undefined],
  [
    'scr-012-no-new',
    'Học chủ đề — Đã học hết từ mới',
    topicStudyPreviews.noNew,
    studyStepPreviews.noNew,
  ],
  [
    'scr-012-finished',
    'Học chủ đề — Hoàn thành lượt',
    topicStudyPreviews.finished,
    studyStepPreviews.finished,
  ],
  ['scr-012-locked', 'Học chủ đề — Bước bị khoá', topicStudyPreviews.locked, undefined],
] as const

const topicStudyEntries = studyEntries.map(([id, title, props, studyProps]) => ({
  fullFrame: true,
  group: 'screen' as const,
  id,
  render: () =>
    createElement(
      AuthContext.Provider,
      { value: learnerAuth },
      createElement(
        LearnerLayout,
        null,
        createElement(TopicStudyView, {
          ...props,
          studyPanel: studyProps ? createElement(StudyStepView, studyProps) : props.studyPanel,
        }),
      ),
    ),
  scr: 'SCR-012',
  title,
})) satisfies readonly PreviewEntry[]

export const previews = [...topicListEntries, ...topicStudyEntries]
