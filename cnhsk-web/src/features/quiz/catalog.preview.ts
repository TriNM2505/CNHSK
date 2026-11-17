import { createElement, Fragment, useState } from 'react'

import type { PreviewEntry, PreviewState } from '@/app/preview/types'
import { ApiError } from '@/shared/api'
import { AuthContext, type AuthContextValue } from '@/shared/auth/AuthContext'
import { LearnerLayout } from '@/shared/layout'
import { Button, EmptyState, ErrorState, Skeleton } from '@/shared/ui'
import { QuizListView } from './components/QuizListView'
import { QuizAttemptView } from './components/QuizAttemptView'
import { QuizResultView } from './components/QuizResultView'
import { quizEngineSession, quizPage, quizResult } from './test-fixtures'
import type { QuizListing } from './types'

function previewState(state: PreviewState) {
  if (state === 'loading') return createElement(Skeleton, { variant: 'card' })
  if (state === 'empty')
    return createElement(EmptyState, {
      title: 'Chưa có nội dung quiz',
      description: 'Quay lại danh sách để chọn một chủ đề khác.',
    })
  if (state === 'error')
    return createElement(ErrorState, {
      message: 'Không thể tải quiz. Vui lòng thử lại.',
      onRetry: () => undefined,
    })
  return null
}

function AttemptPreview({ state }: { state: PreviewState }) {
  const [currentNo, setCurrentNo] = useState(1)
  const [choices, setChoices] = useState<Record<number, string | null>>({})
  const [confirming, setConfirming] = useState<'submit' | 'exit'>()
  return (
    previewState(state) ??
    createElement(QuizAttemptView, {
      topicId: 43,
      rankingEligible: true,
      items: quizEngineSession.items,
      currentNo,
      choices,
      elapsedMs: 65000,
      pending: false,
      confirming,
      onNavigate: setCurrentNo,
      onChoose: (key: string) => setChoices((value) => ({ ...value, [currentNo]: key })),
      onSubmit: () => setConfirming('submit'),
      onExit: () => setConfirming('exit'),
      onConfirm: () => setConfirming(undefined),
      onCancel: () => setConfirming(undefined),
      onRetryAnswer: () => undefined,
    })
  )
}

function ResultPreview({ state }: { state: PreviewState }) {
  return (
    previewState(state) ??
    createElement(QuizResultView, {
      result: quizResult,
      pending: false,
      leaderboardAvailable: false,
      onRetry: () => undefined,
    })
  )
}

const quizSamples: QuizListing[] = [
  quizPage.items[0]!,
  quizPage.items[1]!,
  { ...quizPage.items[2]!, topicName: 'Thời gian và sinh hoạt' },
  {
    topicId: 46,
    topicName: 'Một chuyến đi nhỏ',
    hskLevel: 2,
    questionCount: 10,
    availableQuestions: 15,
    myFirstAttempt: null,
    myBestRetryScore: null,
  },
  {
    topicId: 47,
    topicName: '学习中文 · Học mỗi ngày',
    hskLevel: 1,
    questionCount: 10,
    availableQuestions: 18,
    myFirstAttempt: { score: 70, submittedAt: '2026-11-17T03:00:00Z', suspicious: false },
    myBestRetryScore: 90,
  },
  {
    topicId: 48,
    topicName: 'Món ăn và khẩu vị',
    hskLevel: 3,
    questionCount: 12,
    availableQuestions: 20,
    myFirstAttempt: null,
    myBestRetryScore: null,
  },
]

function QuizPreview({
  state,
  pending = false,
  actionCode,
  paginated = false,
}: {
  state: PreviewState
  pending?: boolean
  actionCode?: 'QUIZ_NOT_AVAILABLE' | 'RATE_LIMITED'
  paginated?: boolean
}) {
  const [hsk, setHsk] = useState('')
  const [page, setPage] = useState(0)
  const [selectedTopic, setSelectedTopic] = useState<number>()
  const matches = quizSamples.filter((quiz) => !hsk || String(quiz.hskLevel) === hsk)
  const size = paginated ? 3 : 20
  const auth: AuthContextValue = {
    user: {
      id: 1,
      displayName: 'Học viên xem trước',
      email: '',
      emailVerified: true,
      roles: ['LEARNER'],
    },
    status: 'authenticated',
    error: null,
    reloadCurrentUser: async () => undefined,
    setAuthenticatedUser: () => undefined,
    signOut: async () => undefined,
  }
  const content = createElement(QuizListView, {
    state,
    hsk,
    result: {
      items: state === 'empty' ? [] : matches.slice(page * size, (page + 1) * size),
      page,
      size,
      total: state === 'empty' ? 0 : matches.length,
    },
    pendingTopic: pending ? matches[0]?.topicId : undefined,
    actionError: actionCode
      ? new ApiError({
          status: actionCode === 'RATE_LIMITED' ? 429 : 422,
          errorCode: actionCode,
          message: 'Lỗi bắt đầu quiz mẫu.',
        })
      : undefined,
    error: new ApiError({
      status: 0,
      errorCode: 'NETWORK_ERROR',
      message: 'Không thể tải danh sách quiz. Vui lòng thử lại.',
    }),
    onHskChange: (value) => {
      setHsk(value)
      setPage(0)
      setSelectedTopic(undefined)
    },
    onPageChange: setPage,
    onRetry: () => setSelectedTopic(undefined),
    onStart: setSelectedTopic,
  })
  return createElement(
    AuthContext.Provider,
    { value: auth },
    createElement(LearnerLayout, {
      gameOrigin: 'http://localhost:5174',
      children: createElement(
        Fragment,
        null,
        selectedTopic !== undefined
          ? createElement(
              'div',
              {
                className:
                  'mx-4 mt-4 flex flex-wrap items-center gap-3 rounded-md border border-brand-100 bg-brand-50 p-3 text-body-sm text-ink-secondary sm:mx-6 lg:mx-8',
                role: 'status',
              },
              'Đã chọn quiz mẫu. Bản xem trước không tạo lượt hoặc gửi câu trả lời.',
              createElement(Button, {
                to: '/__preview/frame/quiz-attempt/ready',
                variant: 'outline',
                children: 'Xem mẫu màn làm quiz',
              }),
            )
          : null,
        content,
      ),
    }),
  )
}

export const previews = [
  {
    fullFrame: true,
    group: 'screen',
    id: 'scr-043-ready',
    scr: 'SCR-043',
    title: 'Danh sách quiz — Redesign',
    states: ['loading', 'empty', 'error', 'ready'],
    render: (state) => createElement(QuizPreview, { state }),
  },
  {
    fullFrame: true,
    group: 'screen',
    id: 'scr-043-pending',
    scr: 'SCR-043',
    title: 'Danh sách quiz — Đang bắt đầu',
    states: ['ready'],
    render: (state) => createElement(QuizPreview, { state, pending: true }),
  },
  {
    fullFrame: true,
    group: 'screen',
    id: 'scr-043-rate-limited',
    scr: 'SCR-043',
    title: 'Danh sách quiz — Hạn mức bắt đầu',
    states: ['ready'],
    render: (state) => createElement(QuizPreview, { state, actionCode: 'RATE_LIMITED' }),
  },
  {
    fullFrame: true,
    group: 'screen',
    id: 'scr-043-unavailable',
    scr: 'SCR-043',
    title: 'Danh sách quiz — Chủ đề chưa đủ câu hỏi',
    states: ['ready'],
    render: (state) => createElement(QuizPreview, { state, actionCode: 'QUIZ_NOT_AVAILABLE' }),
  },
  {
    fullFrame: true,
    group: 'screen',
    id: 'scr-043-pagination',
    scr: 'SCR-043',
    title: 'Danh sách quiz — Phân trang mẫu',
    states: ['ready'],
    render: (state) => createElement(QuizPreview, { state, paginated: true }),
  },
  {
    group: 'screen',
    fullFrame: true,
    id: 'quiz-list',
    scr: 'SCR-043',
    title: 'Quiz theo chủ đề',
    states: ['loading', 'empty', 'error', 'ready'],
    render: (state) => createElement(QuizPreview, { state }),
  },
  {
    group: 'screen',
    id: 'quiz-attempt',
    scr: 'SCR-044',
    title: 'Làm quiz theo chủ đề',
    states: ['loading', 'empty', 'error', 'ready'],
    render: (state) => createElement(AttemptPreview, { state }),
  },
  {
    group: 'screen',
    id: 'quiz-result',
    scr: 'SCR-045',
    title: 'Kết quả quiz theo chủ đề',
    states: ['loading', 'empty', 'error', 'ready'],
    render: (state) => createElement(ResultPreview, { state }),
  },
] satisfies readonly PreviewEntry[]
