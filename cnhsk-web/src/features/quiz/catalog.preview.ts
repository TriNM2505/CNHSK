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

function QuizPreview({ state }: { state: PreviewState }) {
  const [hsk, setHsk] = useState('')
  const auth: AuthContextValue = {
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
  return createElement(
    AuthContext.Provider,
    { value: auth },
    createElement(LearnerLayout, { gameOrigin: 'http://localhost:5174', children: content }),
  )
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
