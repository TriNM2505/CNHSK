import { useEffect, useRef, useState } from 'react'
import { useNavigate, useParams, useSearchParams } from 'react-router-dom'

import { useApiMutation, useApiQuery, type ApiError } from '@/shared/api'
import { useCurrentUser } from '@/shared/auth'
import { approveReview, rejectReview } from '@/features/ai-review/api/reviews'
import { rejectionReasonError, reviewQueueBackTo } from '@/features/ai-review/reviewQueueQuery'

import { getKnowledgeTree } from '../api/knowledge'
import { archiveQuestion, getQuestion, updateQuestion } from '../api/questions'
import { QuestionEditView } from '../components/QuestionEditView'
import { parseQuestionId } from '../questionEditForm'
import type { KnowledgeTree, QuestionDetail, QuestionUpdate } from '../types'

type Action = { kind: 'save'; body: QuestionUpdate } | { kind: 'archive' }

function QuestionEditor({
  id,
  roles,
  backTo,
  tree,
}: {
  id: number | undefined
  roles: readonly string[]
  backTo: string
  tree: {
    data?: KnowledgeTree
    state: 'loading' | 'empty' | 'error' | 'ready'
    error?: ApiError
    reload: () => void
  }
}) {
  const navigate = useNavigate()
  const query = useApiQuery(
    (signal) => (id === undefined ? Promise.resolve(undefined) : getQuestion(id, signal)),
    [id],
    { isEmpty: (data) => data === undefined },
  )
  const [accepted, setAccepted] = useState<QuestionDetail>()
  const [action, setAction] = useState<Action>()
  const [notice, setNotice] = useState<string>()
  const [reviewAction, setReviewAction] = useState<'approve' | 'reject'>()
  const [reason, setReason] = useState('')
  const [reasonError, setReasonError] = useState<string>()
  const [reviewLocked, setReviewLocked] = useState(false)
  const controllerRef = useRef(new AbortController())
  useEffect(() => {
    const controller = new AbortController()
    controllerRef.current = controller
    return () => controller.abort()
  }, [])
  const mutation = useApiMutation(async (next: Action) => {
    const signal = controllerRef.current.signal
    const data =
      next.kind === 'save'
        ? await updateQuestion(id!, next.body, signal)
        : await archiveQuestion(id!, signal)
    if (!signal.aborted) {
      setAccepted(data)
      setAction(undefined)
      setNotice(next.kind === 'save' ? 'Đã lưu thay đổi.' : 'Đã lưu trữ câu hỏi.')
    }
    return data
  })
  const data = accepted ?? query.data
  const canReview =
    roles.some((role) => role === 'TEACHER' || role === 'CONTENT_EDITOR') &&
    data?.source === 'AI' &&
    data.status === 'PENDING_REVIEW'
  const reviewMutation = useApiMutation(async (decision: 'approve' | 'reject') => {
    const signal = controllerRef.current.signal
    try {
      if (decision === 'approve') await approveReview(id!, signal)
      else await rejectReview(id!, reason.trim(), signal)
      setReviewLocked(true)
      setReviewAction(undefined)
      const refreshed = await getQuestion(id!, signal)
      if (!signal.aborted) {
        setAccepted(refreshed)
        setReviewLocked(false)
        setNotice(decision === 'approve' ? 'Đã duyệt câu hỏi.' : 'Đã từ chối câu hỏi.')
        if (backTo.startsWith('/content/ai-review')) navigate(backTo)
      }
      return refreshed
    } catch (error) {
      if (!signal.aborted) {
        setReviewAction(undefined)
        if ((error as ApiError).status === 409) {
          setReviewLocked(true)
          try {
            const refreshed = await getQuestion(id!, signal)
            if (!signal.aborted) {
              setAccepted(refreshed)
              setReviewLocked(false)
            }
          } catch {
            /* Keep stale decisions blocked until an explicit successful reload. */
          }
        }
      }
      throw error
    }
  })
  const canEdit =
    !reviewLocked &&
    (roles.includes('TEACHER') || (roles.includes('CONTENT_EDITOR') && canReview)) &&
    data?.status !== 'ARCHIVED' &&
    data?.qtype !== 'ESSAY'
  const canArchive = roles.includes('CONTENT_EDITOR') && data?.status !== 'ARCHIVED'
  const state = id === undefined || query.error?.status === 404 ? 'empty' : query.state
  return (
    <QuestionEditView
      action={action?.kind}
      actionError={mutation.error}
      backTo={backTo}
      canArchive={canArchive}
      canEdit={canEdit}
      data={data}
      error={query.error}
      nodes={tree.data?.nodes ?? []}
      nodesError={tree.error}
      nodesState={tree.state}
      notice={notice}
      onArchive={() => {
        mutation.reset()
        setNotice(undefined)
        setAction({ kind: 'archive' })
      }}
      onCancelAction={() => {
        if (!mutation.pending) {
          setAction(undefined)
          mutation.reset()
        }
      }}
      onConfirmAction={() => {
        if (!action || mutation.pending || reviewMutation.pending || id === undefined) return
        if (action.kind === 'save' ? !canEdit : !canArchive) return
        void mutation.run(action).catch(() => undefined)
      }}
      onDismissError={() => { mutation.reset(); reviewMutation.reset() }}
      onNodesRetry={tree.reload}
      onRetry={() => {
        setAccepted(undefined)
        setAction(undefined)
        mutation.reset()
        reviewMutation.reset()
        setReviewLocked(false)
        setReviewAction(undefined)
        query.reload()
      }}
      onSave={(body) => {
        mutation.reset()
        setNotice(undefined)
        setAction({ kind: 'save', body })
      }}
      pending={mutation.pending}
      review={{
        allowed: canReview,
        locked: reviewLocked,
        action: reviewAction,
        reason,
        reasonError,
        error: reviewMutation.error,
        pending: reviewMutation.pending,
        onReasonChange: (value) => {
          setReason(value)
          setReasonError(undefined)
          reviewMutation.reset()
        },
        onRequest: (decision) => {
          if (!canReview || reviewLocked || mutation.pending || reviewMutation.pending) return
          const error = decision === 'reject' ? rejectionReasonError(reason) : undefined
          setReasonError(error)
          if (error) {
            requestAnimationFrame(() => document.getElementById('review-reason')?.focus())
            return
          }
          reviewMutation.reset()
          setReviewAction(decision)
        },
        onCancel: () => {
          if (!reviewMutation.pending) setReviewAction(undefined)
        },
        onConfirm: () => {
          if (
            !canReview ||
            reviewLocked ||
            !reviewAction ||
            reviewMutation.pending ||
            mutation.pending
          )
            return
          if (reviewAction === 'reject' && rejectionReasonError(reason)) return
          void reviewMutation.run(reviewAction).catch(() => undefined)
        },
      }}
      state={state}
    />
  )
}

export default function Scr059QuestionEditPage() {
  const { id: rawId } = useParams()
  const [searchParams] = useSearchParams()
  const { user } = useCurrentUser()
  const tree = useApiQuery((signal) => getKnowledgeTree({}, signal), [])
  const search = searchParams.toString()
  return (
    <QuestionEditor
      backTo={reviewQueueBackTo(searchParams) ?? `/content/questions${search ? `?${search}` : ''}`}
      id={parseQuestionId(rawId)}
      key={`${user?.id}:${user?.roles.join(',')}:${rawId}`}
      roles={user?.roles ?? []}
      tree={tree}
    />
  )
}
