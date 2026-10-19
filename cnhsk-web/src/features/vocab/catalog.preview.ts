import { createElement } from 'react'

import type { PreviewEntry } from '@/app/preview/types'
import { AuthContext, type AuthContextValue } from '@/shared/auth'
import { LearnerLayout } from '@/shared/layout'
import { previewLearner } from '@/shared/layout/Layout.preview'

import { TopicListView } from './components/TopicListView'
import { TopicPracticeStepView } from './components/TopicPracticeStepView'
import { topicPracticePreviews } from './components/TopicPracticeStepView.preview'
import { TopicTestStepView } from './components/TopicTestStepView'
import { topicTestPreviews } from './components/TopicTestStepView.preview'
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

const practiceEntries = [
  ['scr-012-recognition', 'Học chủ đề — Luyện nhận diện', topicPracticePreviews.recognition],
  ['scr-012-feedback', 'Học chủ đề — Phản hồi sau khi chấm', topicPracticePreviews.feedback],
  ['scr-012-listening', 'Học chủ đề — Luyện nghe', topicPracticePreviews.listening],
  ['scr-012-practice-loading', 'Học chủ đề — Đang tải lượt luyện', topicPracticePreviews.loading],
  ['scr-012-mismatch', 'Học chủ đề — Lượt luyện khác đang dở', topicPracticePreviews.mismatch],
  [
    'scr-012-practice-unavailable',
    'Học chủ đề — Chưa đủ từ để luyện',
    topicPracticePreviews.unavailable,
  ],
  ['scr-012-practice-error', 'Học chủ đề — Lỗi lượt luyện', topicPracticePreviews.error],
  ['scr-012-practice-finished', 'Học chủ đề — Tổng kết luyện', topicPracticePreviews.finished],
] as const

const topicPracticeEntries = practiceEntries.map(([id, title, props]) => ({
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
          ...topicStudyPreviews.ready,
          activeStep: props.mode === 'LISTENING' ? 'listening' : 'recognition',
          stepPanel: createElement(TopicPracticeStepView, props),
        }),
      ),
    ),
  scr: 'SCR-012',
  title,
})) satisfies readonly PreviewEntry[]

const testEntries = [
  ['scr-012-test-intro', 'Học chủ đề — Bắt đầu kiểm tra', topicTestPreviews.intro],
  ['scr-012-test-choice', 'Học chủ đề — Câu chọn nghĩa', topicTestPreviews.ready],
  ['scr-012-test-pinyin', 'Học chủ đề — Câu gõ pinyin', topicTestPreviews.pinyin],
  ['scr-012-test-cloze', 'Học chủ đề — Câu điền chữ', topicTestPreviews.cloze],
  ['scr-012-test-confirm', 'Học chủ đề — Xác nhận nộp bài', topicTestPreviews.confirm],
  ['scr-012-test-not-enough', 'Học chủ đề — Chưa đủ từ để thi', topicTestPreviews.notEnough],
  ['scr-012-test-retry', 'Học chủ đề — Chờ thi lại', topicTestPreviews.retryLater],
  ['scr-012-test-expired', 'Học chủ đề — Bài kiểm tra hết hạn', topicTestPreviews.expired],
  ['scr-012-test-failed', 'Học chủ đề — Kết quả chưa đạt', topicTestPreviews.finished],
  ['scr-012-test-passed', 'Học chủ đề — Kết quả đạt', topicTestPreviews.passed],
] as const

const topicTestEntries = testEntries.map(([id, title, props]) => ({
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
          ...topicStudyPreviews.ready,
          activeStep: 'test',
          stepPanel: createElement(TopicTestStepView, props),
        }),
      ),
    ),
  scr: 'SCR-012',
  title,
})) satisfies readonly PreviewEntry[]

export const previews = [
  ...topicListEntries,
  ...topicStudyEntries,
  ...topicPracticeEntries,
  ...topicTestEntries,
]
