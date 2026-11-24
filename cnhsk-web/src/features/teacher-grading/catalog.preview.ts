import { createElement, useState } from 'react'
import type { PreviewEntry, PreviewState } from '@/app/preview/types'
import { ApiError } from '@/shared/api'
import { AuthContext, type AuthContextValue } from '@/shared/auth'
import { AdminLayout } from '@/shared/layout'
import { TeacherGradingQueueView } from './components/TeacherGradingQueueView'
import { TeacherGradingWorkspaceView } from './components/TeacherGradingWorkspaceView'
import { WRITING_RUBRIC } from '@/features/grading/rubrics'
import { emptyDraft, resultPayload, totalScore } from './gradingRules'
import {
  assigned,
  draft as sampleDraft,
  errorCodes as sampleErrorCodes,
  mine,
  queue,
} from './test-fixtures'
import type { QueuePage, TeacherGradingDetail } from './types'

const teacherAuth: AuthContextValue = {
  error: null,
  reloadCurrentUser: async () => undefined,
  setAuthenticatedUser: () => undefined,
  signOut: async () => undefined,
  status: 'authenticated',
  user: {
    id: 105,
    displayName: 'Giáo viên',
    email: 'gv•••@example.com',
    emailVerified: true,
    roles: ['TEACHER'],
  },
}

function inTeacherAdminLayout(element: ReturnType<typeof createElement>) {
  return createElement(
    AuthContext.Provider,
    { value: teacherAuth },
    createElement(AdminLayout, null, element),
  )
}

const previewQueue: QueuePage = {
  ...queue,
  totalElements: 3,
  items: [
    ...queue.items,
    {
      id: 44,
      reference: 'GR-000044',
      hanziCount: 168,
      hskLevel: 2,
      sentAt: '2035-11-15T01:30:00Z',
      claimDeadline: '2035-11-17T01:30:00Z',
      payoutCredits: 5,
    },
    {
      id: 45,
      reference: 'GR-000045',
      hanziCount: 215,
      hskLevel: 3,
      sentAt: '2035-11-15T02:15:00Z',
      claimDeadline: '2035-11-17T02:15:00Z',
      payoutCredits: 5,
    },
  ],
}

const error = new ApiError({
  status: 0,
  errorCode: 'NETWORK_ERROR',
  message: 'Không tải được dữ liệu mẫu.',
})
function QueuePreview({ state }: { state: PreviewState }) {
  return createElement(TeacherGradingQueueView, {
    state,
    queue: previewQueue,
    error,
    mineState: state,
    mine,
    mineError: error,
    pending: false,
    page: 0,
    onPage: () => undefined,
    onClaim: () => undefined,
    onRetry: () => undefined,
    onRetryMine: () => undefined,
  })
}
function WorkspacePreview({ state }: { state: PreviewState }) {
  const [draft, setDraft] = useState(
    state === 'empty' ? emptyDraft : () => structuredClone(sampleDraft),
  )
  const [confirmation, setConfirmation] = useState<'complete' | 'release' | null>(null)
  const [completed, setCompleted] = useState<TeacherGradingDetail>()
  return createElement(TeacherGradingWorkspaceView, {
    state: state === 'empty' ? 'ready' : state,
    detail: completed ?? assigned,
    error,
    draft,
    errorCodes: sampleErrorCodes,
    seconds: 86399,
    pending: false,
    confirmation,
    onDraft: setDraft,
    onRetry: () => undefined,
    onReview: setConfirmation,
    onClose: () => setConfirmation(null),
    onConfirm: () => {
      if (confirmation === 'complete')
        setCompleted({
          ...assigned,
          status: 'COMPLETED',
          result: {
            ...resultPayload(draft, WRITING_RUBRIC),
            errors: draft.errors.flatMap((code) => {
              const option = sampleErrorCodes.find((item) => item.code === code)
              return option
                ? [
                    {
                      code,
                      nameVi: option.nameVi,
                      severity: option.severity,
                      fixHint: option.fixHint,
                    },
                  ]
                : []
            }),
            score: totalScore(draft, WRITING_RUBRIC),
          },
        })
      setConfirmation(null)
    },
  })
}
export const previews = [
  {
    fullFrame: true,
    group: 'screen',
    id: 'teacher-grading-queue',
    scr: 'SCR-056',
    title: 'Hàng đợi chấm bài',
    states: ['loading', 'empty', 'error', 'ready'],
    render: (state) => inTeacherAdminLayout(createElement(QueuePreview, { state })),
  },
  {
    fullFrame: true,
    group: 'screen',
    id: 'teacher-grading-workspace',
    scr: 'SCR-057',
    title: 'Chấm bài viết',
    states: ['loading', 'empty', 'error', 'ready'],
    render: (state) => inTeacherAdminLayout(createElement(WorkspacePreview, { state })),
  },
] satisfies readonly PreviewEntry[]
