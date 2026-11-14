import { createElement, useState } from 'react'

import type { PreviewEntry, PreviewState } from '@/app/preview/types'
import { ApiError } from '@/shared/api'
import { AuthContext, type AuthContextValue } from '@/shared/auth/AuthContext'
import { LearnerLayout } from '@/shared/layout'
import { QuizListView } from './components/QuizListView'
import { quizPage } from './test-fixtures'

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
] satisfies readonly PreviewEntry[]
