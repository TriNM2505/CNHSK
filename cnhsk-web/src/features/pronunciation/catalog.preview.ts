import { createElement } from 'react'

import type { PreviewEntry } from '@/app/preview/types'
import { AuthContext, type AuthContextValue } from '@/shared/auth'
import { LearnerLayout } from '@/shared/layout'
import { previewLearner } from '@/shared/layout/Layout.preview'

import { PronunciationPracticeView } from './components/PronunciationPracticeView'
import { pronunciationPracticePreviews } from './components/PronunciationPracticeView.preview'
import { PronunciationStageListView } from './components/PronunciationStageListView'
import { pronunciationStageListPreviews } from './components/PronunciationStageListView.preview'

const learnerAuth: AuthContextValue = {
  error: null,
  reloadCurrentUser: async () => undefined,
  setAuthenticatedUser: () => undefined,
  signOut: async () => undefined,
  status: 'authenticated',
  user: previewLearner,
}

function inLearnerLayout(element: ReturnType<typeof createElement>) {
  return createElement(
    AuthContext.Provider,
    { value: learnerAuth },
    createElement(LearnerLayout, null, element),
  )
}

const listEntries = [
  ['scr-016-list-ready', 'Luyện phát âm — Lộ trình', pronunciationStageListPreviews.ready],
  ['scr-016-list-loading', 'Luyện phát âm — Đang tải', pronunciationStageListPreviews.loading],
  ['scr-016-list-empty', 'Luyện phát âm — Chưa có dữ liệu', pronunciationStageListPreviews.empty],
  ['scr-016-list-error', 'Luyện phát âm — Lỗi tải', pronunciationStageListPreviews.error],
] as const

const practiceEntries = [
  ['scr-016-practice-ready', 'Luyện phát âm — Câu hỏi', pronunciationPracticePreviews.ready],
  ['scr-016-practice-feedback', 'Luyện phát âm — Phản hồi', pronunciationPracticePreviews.feedback],
  ['scr-016-result-unlocked', 'Luyện phát âm — Mở tầng', pronunciationPracticePreviews.unlocked],
  ['scr-016-result-needed', 'Luyện phát âm — Chưa đạt', pronunciationPracticePreviews.needed],
  [
    'scr-016-result-complete',
    'Luyện phát âm — Hoàn tất',
    pronunciationPracticePreviews.completedAll,
  ],
  [
    'scr-016-result-no-score',
    'Luyện phát âm — Chưa tính điểm',
    pronunciationPracticePreviews.noScore,
  ],
] as const

export const previews = [
  ...listEntries.map(([id, title, props]) => ({
    fullFrame: true,
    group: 'screen' as const,
    id,
    render: () => inLearnerLayout(createElement(PronunciationStageListView, props)),
    scr: 'SCR-016',
    title,
  })),
  ...practiceEntries.map(([id, title, props]) => ({
    fullFrame: true,
    group: 'screen' as const,
    id,
    render: () => inLearnerLayout(createElement(PronunciationPracticeView, props)),
    scr: 'SCR-016',
    title,
  })),
] satisfies readonly PreviewEntry[]
