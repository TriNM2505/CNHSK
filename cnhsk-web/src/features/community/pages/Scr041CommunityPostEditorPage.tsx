import { useEffect, useRef, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'

import { ApiError, useApiMutation, useApiQuery } from '@/shared/api'

import { createPost, getMyPost, updatePost } from '../api/posts'
import { PostEditorView } from '../components/PostEditorView'
import type { PostFormErrors, PostFormValues } from '../postForm'
import { parsePostId, toPostWrite, validatePostForm } from '../postForm'
import type { PostWrite } from '../types'

const EMPTY_FORM: PostFormValues = { title: '', content: '', category: '' }

function CreatePostEditor() {
  const navigate = useNavigate()
  const [values, setValues] = useState(EMPTY_FORM)
  const [errors, setErrors] = useState<PostFormErrors>({})
  const [formError, setFormError] = useState<string>()
  const [verificationRequired, setVerificationRequired] = useState(false)
  const busyRef = useRef(false)
  const [busy, setBusy] = useState(false)
  const mutation = useApiMutation((body: PostWrite) => createPost(body))

  const onChange = (field: keyof PostFormValues, value: string) => {
    setValues((current) => ({ ...current, [field]: value }))
    setErrors((current) => ({ ...current, [field]: undefined }))
  }

  const onSubmit = (submit: boolean) => {
    if (busyRef.current) return
    const validation = validatePostForm(values)
    const body = toPostWrite(values, submit)
    setErrors(validation)
    setFormError(undefined)
    setVerificationRequired(false)
    if (!body) return
    busyRef.current = true
    setBusy(true)
    void mutation.run(body).then(() => {
      navigate('/community?tab=mine', { state: { communityPostSuccess: submit ? 'submitted' : 'draftSaved' } })
    }).catch((error: unknown) => {
      setFormError(error instanceof ApiError ? error.message : 'Không thể kết nối tới máy chủ. Vui lòng thử lại.')
      setVerificationRequired(error instanceof ApiError && error.errorCode === 'ACCOUNT_UNVERIFIED')
      busyRef.current = false
      setBusy(false)
    })
  }

  return <PostEditorView busy={busy} errors={errors} formError={formError} locked={false} mode="create" onChange={onChange} onSubmit={onSubmit} values={values} verificationRequired={verificationRequired} />
}

function InvalidPostId() {
  return <main className="mx-auto max-w-reading p-6"><h1 className="text-h1-sm">Không tìm thấy bài viết</h1><p className="mt-3" role="alert">ID bài viết không hợp lệ.</p><Link className="mt-4 inline-block underline" to="/community?tab=mine">Về Bài viết của tôi</Link></main>
}

function EditPostEditor({ id }: { id: number }) {
  const navigate = useNavigate()
  const query = useApiQuery((signal) => getMyPost(id, signal), [id])
  const [values, setValues] = useState<PostFormValues | null>(null)
  const [errors, setErrors] = useState<PostFormErrors>({})
  const [formError, setFormError] = useState<string>()
  const [verificationRequired, setVerificationRequired] = useState(false)
  const [locked, setLocked] = useState(false)
  const busyRef = useRef(false)
  const [busy, setBusy] = useState(false)
  const mutation = useApiMutation((body: PostWrite) => updatePost(id, body))

  useEffect(() => {
    if (!query.data) return
    setValues({ title: query.data.title, content: query.data.content, category: query.data.category })
    setLocked(query.data.status === 'HIDDEN')
  }, [query.data])

  const onChange = (field: keyof PostFormValues, value: string) => {
    setValues((current) => current ? { ...current, [field]: value } : current)
    setErrors((current) => ({ ...current, [field]: undefined }))
  }

  const onSubmit = (submit: boolean) => {
    if (busyRef.current || locked || !values) return
    const validation = validatePostForm(values)
    const body = toPostWrite(values, submit)
    setErrors(validation)
    setFormError(undefined)
    setVerificationRequired(false)
    if (!body) return
    busyRef.current = true
    setBusy(true)
    void mutation.run(body).then(() => {
      navigate('/community?tab=mine', { state: { communityPostSuccess: submit ? 'submitted' : 'draftSaved' } })
    }).catch((error: unknown) => {
      const apiError = error instanceof ApiError ? error : undefined
      setFormError(apiError?.message ?? 'Không thể kết nối tới máy chủ. Vui lòng thử lại.')
      setVerificationRequired(apiError?.errorCode === 'ACCOUNT_UNVERIFIED')
      if (apiError?.status === 409 && apiError.errorCode === 'POST_NOT_EDITABLE') setLocked(true)
      busyRef.current = false
      setBusy(false)
    })
  }

  if (query.state === 'error') return <main className="mx-auto max-w-reading p-6"><p role="alert">{query.error?.message ?? 'Không thể tải bài viết.'}</p><button className="mt-3 underline" onClick={query.reload} type="button">Thử lại</button><Link className="ml-4 underline" to="/community?tab=mine">Về Bài viết của tôi</Link></main>
  if (query.state === 'loading' || !query.data || !values) return <main aria-label="Đang tải bài viết" className="mx-auto max-w-reading p-6" role="status">Đang tải bài viết…</main>

  return <PostEditorView busy={busy} errors={errors} formError={formError} locked={locked} mode="edit" onChange={onChange} onSubmit={onSubmit} post={query.data} values={values} verificationRequired={verificationRequired} />
}

export default function Scr041CommunityPostEditorPage() {
  const { id: rawId } = useParams()
  if (rawId === undefined) return <CreatePostEditor />
  const id = parsePostId(rawId)
  if (id === undefined) return <InvalidPostId />
  return <EditPostEditor id={id} key={id} />
}
