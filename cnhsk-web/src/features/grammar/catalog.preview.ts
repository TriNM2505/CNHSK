import { createElement } from 'react'

import type { PreviewEntry } from '@/app/preview/types'
import { AuthContext, type AuthContextValue } from '@/shared/auth'
import { LearnerLayout } from '@/shared/layout'
import { previewLearner } from '@/shared/layout/Layout.preview'

import { GrammarLessonView } from './components/GrammarLessonView'
import { grammarLessonPreviews } from './components/GrammarLessonView.preview'
import { GrammarListView } from './components/GrammarListView'
import { grammarListPreviews } from './components/GrammarListView.preview'

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
  ['scr-017-ready', 'Ngữ pháp HSK — Danh sách', grammarListPreviews.ready],
  ['scr-017-loading', 'Ngữ pháp HSK — Đang tải', grammarListPreviews.loading],
  ['scr-017-empty', 'Ngữ pháp HSK — Chưa có dữ liệu', grammarListPreviews.empty],
  ['scr-017-error', 'Ngữ pháp HSK — Lỗi tải', grammarListPreviews.error],
] as const

const lessonEntries = [
  ['scr-018-ready', 'Bài ngữ pháp — Nội dung', grammarLessonPreviews.ready],
  ['scr-018-restored', 'Bài ngữ pháp — Có lượt đang dở', grammarLessonPreviews.restored],
  ['scr-018-exercise', 'Bài ngữ pháp — Câu hỏi', grammarLessonPreviews.exercise],
  ['scr-018-feedback', 'Bài ngữ pháp — Lời giải', grammarLessonPreviews.feedback],
  ['scr-018-finished', 'Bài ngữ pháp — Kết quả', grammarLessonPreviews.finished],
  ['scr-018-loading', 'Bài ngữ pháp — Đang tải', grammarLessonPreviews.loading],
  ['scr-018-empty', 'Bài ngữ pháp — Không tìm thấy', grammarLessonPreviews.empty],
  ['scr-018-error', 'Bài ngữ pháp — Lỗi tải', grammarLessonPreviews.error],
] as const

const grammarListEntries = listEntries.map(([id, title, props]) => ({
  fullFrame: true,
  group: 'screen' as const,
  id,
  render: () => inLearnerLayout(createElement(GrammarListView, props)),
  scr: 'SCR-017',
  title,
})) satisfies readonly PreviewEntry[]

const grammarLessonEntries = lessonEntries.map(([id, title, props]) => ({
  fullFrame: true,
  group: 'screen' as const,
  id,
  render: () => inLearnerLayout(createElement(GrammarLessonView, props)),
  scr: 'SCR-018',
  title,
})) satisfies readonly PreviewEntry[]

export const previews = [...grammarListEntries, ...grammarLessonEntries]
