import { useEffect, useRef, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { ApiError, useApiMutation, useApiQuery } from '@/shared/api'
import { useCurrentUser } from '@/shared/auth'
import { rubricForMode } from '@/features/grading/rubrics'
import { completeGrading, getAssignedGrading, getErrorCodes, releaseGrading } from '../api/requests'
import { TeacherGradingWorkspaceView } from '../components/TeacherGradingWorkspaceView'
import { emptyDraft, resultErrors, resultPayload } from '../gradingRules'
import type { GradingResultWrite, TeacherGradingDetail } from '../types'
import { useGradingDeadline } from '../useGradingDeadline'

type Action = { kind: 'release' } | { kind: 'complete'; body: GradingResultWrite }
function Workspace({ id }: { id: string }) {
  const navigate = useNavigate()
  const query = useApiQuery((signal) => getAssignedGrading(id, signal), [id])
  const [draft, setDraft] = useState(emptyDraft)
  const [confirmation, setConfirmation] = useState<'complete' | 'release' | null>(null)
  const [completed, setCompleted] = useState<TeacherGradingDetail>()
  const row = completed ?? query.data
  const rubric = rubricForMode(row?.mode)
  const errorCodes = useApiQuery((signal) => getErrorCodes(signal), [])
  const seconds = useGradingDeadline(row?.status === 'ASSIGNED' ? row.gradingDeadline : undefined)
  const mutation = useApiMutation((action: Action): Promise<TeacherGradingDetail | void> =>
    action.kind === 'release' ? releaseGrading(id) : completeGrading(id, action.body),
  )
  const alive = useRef(true),
    busy = useRef(false)
  useEffect(() => {
    alive.current = true
    return () => {
      alive.current = false
    }
  }, [])
  function confirm() {
    if (busy.current || !confirmation || row?.status !== 'ASSIGNED') return
    if (confirmation === 'complete' && Object.keys(resultErrors(draft, rubric)).length > 0) return
    const action: Action =
      confirmation === 'release'
        ? { kind: 'release' }
        : { kind: 'complete', body: resultPayload(draft, rubric) }
    busy.current = true
    void mutation
      .run(action)
      .then((response) => {
        if (!alive.current) return
        setConfirmation(null)
        if (action.kind === 'release') navigate('/teacher/grading')
        else if (response) setCompleted(response)
      })
      .catch((error: unknown) => {
        if (!alive.current) return
        setConfirmation(null)
        if (error instanceof ApiError && error.errorCode === 'GRADING_NOT_ASSIGNEE') query.reload()
      })
      .finally(() => {
        busy.current = false
      })
  }
  return (
    <TeacherGradingWorkspaceView
      state={query.state}
      detail={row}
      error={query.error}
      actionError={mutation.error}
      draft={draft}
      errorCodes={errorCodes.data ?? []}
      seconds={seconds}
      pending={mutation.pending}
      confirmation={confirmation}
      onDraft={(value) => {
        setDraft(value)
        mutation.reset()
      }}
      onRetry={query.reload}
      onReview={(action) => {
        mutation.reset()
        setConfirmation(action)
      }}
      onClose={() => setConfirmation(null)}
      onConfirm={confirm}
    />
  )
}
export default function Scr057TeacherGradingWorkspacePage() {
  const { id = '' } = useParams()
  const auth = useCurrentUser()
  return auth.status === 'authenticated' && auth.user?.roles.includes('TEACHER') ? (
    <Workspace key={`${id}-${auth.user.id}`} id={id} />
  ) : (
    <p role="status">Đang kiểm tra quyền truy cập…</p>
  )
}
