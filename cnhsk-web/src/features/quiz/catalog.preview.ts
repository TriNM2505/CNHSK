import { createElement, useState } from 'react'

import type { PreviewEntry, PreviewState } from '@/app/preview/types'
import { ApiError } from '@/shared/api'
import { AuthContext, type AuthContextValue } from '@/shared/auth/AuthContext'
import { LearnerLayout } from '@/shared/layout'
import { EmptyState, ErrorState, Skeleton } from '@/shared/ui'
import { QuizListView } from './components/QuizListView'
import { QuizAttemptView } from './components/QuizAttemptView'
import { QuizResultView } from './components/QuizResultView'
import { quizEngineSession, quizPage, quizResult } from './test-fixtures'

function previewState(state: PreviewState) {
  if (state === 'ready') return null
  const content =
    state === 'loading'
      ? createElement(Skeleton, { variant: 'card' })
      : state === 'empty'
        ? createElement(EmptyState, {
            title: 'Chưa có nội dung quiz',
            description: 'Quay lại danh sách để chọn một chủ đề khác.',
          })
        : createElement(ErrorState, {
            message: 'Không thể tải quiz. Vui lòng thử lại.',
            onRetry: () => undefined,
          })
  return createElement(
    'main',
    { className: 'mx-auto w-full min-w-0 max-w-reading px-4 py-6 pb-20 sm:px-6' },
    content,
  )
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
    createElement(
      'main',
      {
        className:
          'mx-auto w-full min-w-0 max-w-content space-y-6 break-words px-4 py-6 pb-20 [overflow-wrap:anywhere] sm:px-6',
      },
      createElement(QuizResultView, {
        result: quizResult,
        pending: false,
        leaderboardAvailable: false,
        onRetry: () => undefined,
      }),
    )
  )
}

const previewAuth: AuthContextValue = {
  user: {
    id: 1,
    displayName: 'Học viên xem trước',
    email: 'quiz-preview@example.test',
    emailVerified: true,
    roles: ['LEARNER'],
  },
  status: 'authenticated',
  error: null,
  reloadCurrentUser: async () => undefined,
  setAuthenticatedUser: () => undefined,
  signOut: async () => undefined,
}

function inLearnerLayout(children: ReturnType<typeof createElement>) {
  return createElement(
    AuthContext.Provider,
    { value: previewAuth },
    createElement(LearnerLayout, { gameOrigin: 'http://localhost:5174', children }),
  )
}

function QuizPreview({ state }: { state: PreviewState }) {
  const [hsk, setHsk] = useState('')
  const content = createElement(QuizListView, {
    state,
    hsk,
    result: state === 'empty' ? { ...quizPage, items: [], total: 0 } : quizPage,
    error: new ApiError({
      status: 0,
      errorCode: 'NETWORK_ERROR',
      message: 'Không thể tải danh sách quiz. Vui lòng thử lại.',
    }),
    onHskChange: setHsk,
    onPageChange: () => undefined,
    onRetry: () => undefined,
    onStart: () => undefined,
  })
  return inLearnerLayout(content)
}

export const previews = [
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
    fullFrame: true,
    id: 'quiz-attempt',
    scr: 'SCR-044',
    title: 'Làm quiz theo chủ đề',
    states: ['loading', 'empty', 'error', 'ready'],
    render: (state) => inLearnerLayout(createElement(AttemptPreview, { state })),
  },
  {
    group: 'screen',
    fullFrame: true,
    id: 'quiz-result',
    scr: 'SCR-045',
    title: 'Kết quả quiz theo chủ đề',
    states: ['loading', 'empty', 'error', 'ready'],
    render: (state) => inLearnerLayout(createElement(ResultPreview, { state })),
  },
] satisfies readonly PreviewEntry[]
