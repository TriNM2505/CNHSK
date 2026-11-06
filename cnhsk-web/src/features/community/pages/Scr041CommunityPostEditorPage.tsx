import { useState } from 'react'
import { Link, useLocation, useNavigate, useParams } from 'react-router-dom'

import { ApiError, useApiQuery } from '@/shared/api'
import { useCurrentUser } from '@/shared/auth'

import { createPost, getMyPost, updatePost } from '../api/posts'
import { PostEditorView } from '../components/PostEditorView'
import { clearPostEditorDraft, readPostEditorDraft, writePostEditorDraft } from '../post-editor-drafts'
import type { PostFormErrors, PostFormValues } from '../postForm'
import { getPostFormChanges, parsePostId, sanitizePostEditorOrigin, toPostWrite, validatePostForm } from '../postForm'
import type { OwnPost } from '../types'
import { useSessionAction } from '../use-session-action'

const EMPTY_FORM: PostFormValues = { title: '', content: '', category: '' }
const DEFAULT_ORIGIN = '/community?tab=mine'
const DRAFT_STORAGE_ERROR = 'Không thể lưu bản nháp trong tab này. Vui lòng giữ trang đang mở và thử lại trước khi xác minh email.'

function PostEditor({ userId, id, post }: { userId: number; id?: number; post?: OwnPost }) {
  const navigate = useNavigate()
  const location = useLocation()
  const resource = id ?? 'new'
  const baseline: PostFormValues = post ? { title: post.title, content: post.content, category: post.category } : EMPTY_FORM
  const [draft] = useState(() => readPostEditorDraft(userId, resource))
  const state = location.state as { communityEditorOrigin?: unknown } | null
  const [origin] = useState(() => sanitizePostEditorOrigin(state?.communityEditorOrigin) ?? draft?.origin ?? DEFAULT_ORIGIN)
  const [values, setValues] = useState<PostFormValues>(() => ({ ...baseline, ...draft?.fields }))
  const [errors, setErrors] = useState<PostFormErrors>({})
  const [formError, setFormError] = useState<string>()
  const [draftError, setDraftError] = useState<string>()
  const [verificationRequired, setVerificationRequired] = useState(false)
  const [locked, setLocked] = useState(post?.status === 'HIDDEN')
  const [confirmingDiscard, setConfirmingDiscard] = useState(false)
  const action = useSessionAction<OwnPost>()

  const preserveDraft = (next: PostFormValues) => {
    const stored = writePostEditorDraft(userId, resource, { fields: getPostFormChanges(next, baseline), origin })
    setDraftError(stored ? undefined : DRAFT_STORAGE_ERROR)
    return stored
  }

  const onChange = (field: keyof PostFormValues, value: string) => {
    const next = { ...values, [field]: value }
    setValues(next)
    preserveDraft(next)
    setErrors((current) => ({ ...current, [field]: undefined }))
  }

  const onSubmit = (submit: boolean) => {
    if (action.busy || locked) return
    const validation = validatePostForm(values)
    const body = toPostWrite(values, submit)
    setErrors(validation)
    setFormError(undefined)
    setVerificationRequired(false)
    if (!body) return
    action.run(() => id === undefined ? createPost(body) : updatePost(id, body), () => {
      clearPostEditorDraft(userId, resource)
      navigate('/community?tab=mine', { state: { communityPostSuccess: submit ? 'submitted' : 'draftSaved' } })
    }, (error) => {
      const apiError = error instanceof ApiError ? error : undefined
      setFormError(apiError?.message ?? 'Không thể kết nối tới máy chủ. Vui lòng thử lại.')
      setVerificationRequired(apiError?.errorCode === 'ACCOUNT_UNVERIFIED')
      if (apiError?.status === 409 && apiError.errorCode === 'POST_NOT_EDITABLE') setLocked(true)
    })
  }

  const discard = () => {
    clearPostEditorDraft(userId, resource)
    navigate(origin)
  }
  const onCancel = () => {
    if (Object.keys(getPostFormChanges(values, baseline)).length > 0) setConfirmingDiscard(true)
    else discard()
  }
  const editorPath = id === undefined ? '/community/posts/new' : `/community/posts/${id}/edit`

  return <PostEditorView busy={action.busy} confirmingDiscard={confirmingDiscard} draftError={draftError} errors={errors} formError={formError} locked={locked} mode={post ? 'edit' : 'create'} onCancel={onCancel} onChange={onChange} onConfirmDiscard={discard} onKeepEditing={() => setConfirmingDiscard(false)} onSubmit={onSubmit} onVerify={() => preserveDraft(values)} post={post} values={values} verificationRequired={verificationRequired} verificationTo={`/verify-email?redirect=${encodeURIComponent(editorPath)}`} />
}

function InvalidPostId() {
  return <main className="mx-auto max-w-reading p-6"><h1 className="text-h1-sm">Không tìm thấy bài viết</h1><p className="mt-3" role="alert">ID bài viết không hợp lệ.</p><Link className="mt-4 inline-block underline" to="/community?tab=mine">Về Bài viết của tôi</Link></main>
}

function EditPostEditor({ id, userId }: { id: number; userId: number }) {
  const query = useApiQuery((signal) => getMyPost(id, signal), [id])

  if (query.state === 'error') return <main className="mx-auto max-w-reading p-6"><p role="alert">{query.error?.message ?? 'Không thể tải bài viết.'}</p><button className="mt-3 underline" onClick={query.reload} type="button">Thử lại</button><Link className="ml-4 underline" to="/community?tab=mine">Về Bài viết của tôi</Link></main>
  if (query.state !== 'ready' || !query.data) return <main aria-label="Đang tải bài viết" className="mx-auto max-w-reading p-6" role="status">Đang tải bài viết…</main>

  return <PostEditor id={id} post={query.data} userId={userId} />
}

export default function Scr041CommunityPostEditorPage() {
  const { id: rawId } = useParams()
  const auth = useCurrentUser()
  if (auth.status === 'loading') return <main className="mx-auto max-w-reading p-6" role="status">Đang kiểm tra phiên đăng nhập…</main>
  if (auth.status === 'error') return <main className="mx-auto max-w-reading p-6"><p role="alert">{auth.error?.message ?? 'Không thể kiểm tra phiên đăng nhập.'}</p><button onClick={() => void auth.reloadCurrentUser()} type="button">Thử lại</button></main>
  if (auth.status !== 'authenticated' || !auth.user?.roles.includes('LEARNER')) return null
  const session = `${auth.user.id}:${[...auth.user.roles].sort().join(',')}`
  if (rawId === undefined) return <PostEditor key={`${session}:new`} userId={auth.user.id} />
  const id = parsePostId(rawId)
  if (id === undefined) return <InvalidPostId />
  return <EditPostEditor id={id} key={`${session}:${id}`} userId={auth.user.id} />
}
