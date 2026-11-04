import { useEffect, useRef, useState } from 'react'
import { useParams, useSearchParams } from 'react-router-dom'

import { useApiMutation, useApiQuery, type ApiError } from '@/shared/api'
import { useCurrentUser } from '@/shared/auth'

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
  const query = useApiQuery(
    (signal) => (id === undefined ? Promise.resolve(undefined) : getQuestion(id, signal)),
    [id],
    { isEmpty: (data) => data === undefined },
  )
  const [accepted, setAccepted] = useState<QuestionDetail>()
  const [action, setAction] = useState<Action>()
  const [notice, setNotice] = useState<string>()
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
  const canEdit =
    roles.includes('TEACHER') && data?.status !== 'ARCHIVED' && data?.qtype !== 'ESSAY'
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
        if (!action || mutation.pending || id === undefined) return
        if (action.kind === 'save' ? !canEdit : !canArchive) return
        void mutation.run(action).catch(() => undefined)
      }}
      onDismissError={mutation.reset}
      onNodesRetry={tree.reload}
      onRetry={() => {
        setAccepted(undefined)
        setAction(undefined)
        mutation.reset()
        query.reload()
      }}
      onSave={(body) => {
        mutation.reset()
        setNotice(undefined)
        setAction({ kind: 'save', body })
      }}
      pending={mutation.pending}
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
      backTo={`/content/questions${search ? `?${search}` : ''}`}
      id={parseQuestionId(rawId)}
      key={`${user?.id}:${user?.roles.join(',')}:${rawId}`}
      roles={user?.roles ?? []}
      tree={tree}
    />
  )
}
