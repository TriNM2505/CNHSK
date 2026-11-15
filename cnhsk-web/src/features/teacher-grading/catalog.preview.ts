import { createElement, useState } from 'react'
import type { PreviewEntry, PreviewState } from '@/app/preview/types'
import { ApiError } from '@/shared/api'
import { TeacherGradingQueueView } from './components/TeacherGradingQueueView'
import { TeacherGradingWorkspaceView } from './components/TeacherGradingWorkspaceView'
import { emptyDraft, resultPayload, totalScore } from './gradingRules'
import { assigned, draft as sampleDraft, mine, queue } from './test-fixtures'
import type { TeacherGradingDetail } from './types'

const error = new ApiError({
  status: 0,
  errorCode: 'NETWORK_ERROR',
  message: 'Không tải được dữ liệu mẫu.',
})
function QueuePreview({ state }: { state: PreviewState }) {
  return createElement(TeacherGradingQueueView, {
    state,
    queue,
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
          result: { ...resultPayload(draft), score: totalScore(draft) },
        })
      setConfirmation(null)
    },
  })
}
export const previews = [
  {
    group: 'screen',
    id: 'teacher-grading-queue',
    scr: 'SCR-056',
    title: 'Hàng đợi chấm bài',
    states: ['loading', 'empty', 'error', 'ready'],
    render: (state) => createElement(QueuePreview, { state }),
  },
  {
    group: 'screen',
    id: 'teacher-grading-workspace',
    scr: 'SCR-057',
    title: 'Chấm bài viết',
    states: ['loading', 'empty', 'error', 'ready'],
    render: (state) => createElement(WorkspacePreview, { state }),
  },
] satisfies readonly PreviewEntry[]
