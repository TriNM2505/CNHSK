import { useEffect, useRef, useState } from 'react'
import { Link, useBlocker, useLocation, useNavigate, useParams } from 'react-router-dom'
import { toApiError, useApiQuery } from '@/shared/api'
import { Button, ConfirmPanel, FormField, Input } from '@/shared/ui'
import { archiveExam, cloneExam, getExam, publishExam, saveExam } from '../api/exams'
import { ExamEditorView } from '../components/ExamEditorView'
import { ExamQuestionPicker } from '../components/ExamQuestionPicker'
import {
  emptyForm,
  examWrite,
  failureMessage,
  formFromExam,
  publicationErrors,
  validateForm,
} from '../examForm'
import type { ExamDetail, ExamForm, PublicationError } from '../types'

function Editor({ id }: { id: number | null }) {
  const navigate = useNavigate()
  const location = useLocation()
  const listSearch =
    typeof location.state?.examListSearch === 'string' &&
    location.state.examListSearch.startsWith('?')
      ? location.state.examListSearch
      : ''
  const listPath = `/content/exams${listSearch}`
  const query = useApiQuery(
    (signal) => (id === null ? Promise.resolve(null) : getExam(id, signal)),
    [id],
  )
  const [saved, setSaved] = useState<ExamDetail | null>(null)
  const [form, setForm] = useState(emptyForm)
  const [dirty, setDirty] = useState(false)
  const [pending, setPending] = useState(false)
  const [locked, setLocked] = useState(false)
  const [reconcileCode, setReconcileCode] = useState<string>()
  const [errors, setErrors] = useState<string[]>([])
  const [serverErrors, setServerErrors] = useState<PublicationError[]>([])
  const [notice, setNotice] = useState<string>()
  const [picker, setPicker] = useState(false)
  const [confirmation, setConfirmation] = useState<'publish' | 'archive' | 'clone' | 'reload'>()
  const [code, setCode] = useState('')
  const [title, setTitle] = useState('')
  const busy = useRef(false)
  const alive = useRef(true)
  const editVersion = useRef(0)
  const selectionVersion = editVersion.current
  const allowNavigation = useRef(false)
  const blocker = useBlocker(
    ({ currentLocation, nextLocation }) =>
      !allowNavigation.current && dirty && currentLocation.pathname !== nextLocation.pathname,
  )
  useEffect(() => {
    alive.current = true
    return () => {
      alive.current = false
    }
  }, [])
  useEffect(() => {
    if (query.state !== 'ready') return
    editVersion.current++
    const detail = query.data ?? null
    setSaved(detail)
    setForm(detail ? formFromExam(detail) : emptyForm())
    setServerErrors(detail?.publicationErrors ?? [])
    setDirty(false)
    setLocked(false)
    setPicker(false)
    setErrors([])
  }, [query.data, query.state])
  useEffect(() => {
    function prevent(event: BeforeUnloadEvent) {
      if (dirty) {
        event.preventDefault()
        event.returnValue = ''
      }
    }
    window.addEventListener('beforeunload', prevent)
    return () => window.removeEventListener('beforeunload', prevent)
  }, [dirty])
  function change(next: ExamForm) {
    if (busy.current || locked || (saved && saved.status !== 'DRAFT')) return
    setForm(next)
    setDirty(true)
    setNotice(undefined)
    setErrors([])
  }
  async function perform(action: 'save' | 'publish' | 'archive' | 'clone') {
    if (busy.current || locked) return
    if (action === 'save') {
      const validation = validateForm(form)
      if (validation.length) {
        setErrors(validation)
        return
      }
    }
    if (action === 'publish' && (!saved || dirty || saved.status !== 'DRAFT')) return
    if (
      action === 'clone' &&
      (!/^[A-Z0-9-]{1,50}$/.test(code) || Array.from(title.trim()).length > 200)
    ) {
      setErrors(['Nhập mã mới gồm 1–50 ký tự A–Z, số, dấu gạch ngang; tên tối đa 200 ký tự.'])
      return
    }
    if (action !== 'save' && !saved) return
    editVersion.current++
    setPicker(false)
    busy.current = true
    setPending(true)
    setErrors([])
    setNotice(undefined)
    try {
      const updated =
        action === 'save'
          ? await saveExam(id, examWrite(form), saved?.etag)
          : action === 'publish'
            ? await publishExam(saved!.id, saved!.etag)
            : action === 'archive'
              ? await archiveExam(saved!.id)
              : await cloneExam(saved!.id, code, title)
      if (!alive.current) return
      setConfirmation(undefined)
      if (id === null || action === 'clone') {
        allowNavigation.current = true
        navigate(`/content/exams/${updated.id}`, {
          replace: id === null,
          state: { examListSearch: listSearch },
        })
        return
      }
      setSaved(updated)
      setForm(formFromExam(updated))
      setServerErrors(updated.publicationErrors)
      setDirty(false)
      setPicker(false)
      setNotice(action === 'save' ? 'Đã lưu nháp.' : 'Đã cập nhật trạng thái đề thi.')
    } catch (caught) {
      if (!alive.current) return
      const failure = toApiError(caught)
      setErrors([
        failureMessage(failure),
        ...(failure.requestId ? [`Mã yêu cầu: ${failure.requestId}`] : []),
      ])
      if (failure.errorCode === 'EXAM_PUBLISH_INVALID')
        setServerErrors(publicationErrors(failure.details))
      else if (failure.status === 422 && Array.isArray(failure.details)) {
        const messages = failure.details
          .filter(
            (v: unknown): v is { message: string } =>
              !!v && typeof v === 'object' && 'message' in v && typeof v.message === 'string',
          )
          .map((v) => v.message)
        setErrors((previous) => [...previous, ...messages])
      }
      if (
        failure.errorCode === 'EDIT_CONFLICT' ||
        failure.errorCode === 'EXAM_NOT_EDITABLE' ||
        failure.status === 404 ||
        failure.status === 0 ||
        failure.status >= 500
      )
        setLocked(true)
      if ((failure.status === 0 || failure.status >= 500) && (id === null || action === 'clone'))
        setReconcileCode(action === 'clone' ? code : form.code)
      setConfirmation(undefined)
    } finally {
      busy.current = false
      if (alive.current) setPending(false)
    }
  }
  if (query.state === 'loading')
    return (
      <main>
        <p role="status">Đang tải đề thi…</p>
      </main>
    )
  if (query.state === 'error')
    return (
      <main>
        <p role="alert">{failureMessage(query.error)}</p>
        <Button variant="outline" onClick={query.reload}>
          Thử tải đề lại
        </Button>
        <Link to={listPath}>Về danh sách đề</Link>
      </main>
    )
  return (
    <>
      <ExamEditorView
        form={form}
        saved={saved}
        dirty={dirty}
        busy={pending}
        locked={locked}
        errors={errors}
        publicationErrors={serverErrors}
        notice={notice}
        onChange={change}
        onSave={() => void perform('save')}
        onPublish={() => setConfirmation('publish')}
        onArchive={() => setConfirmation('archive')}
        onClone={() => {
          setCode('')
          setTitle('')
          setConfirmation('clone')
        }}
        onReload={() => setConfirmation('reload')}
        onBack={() => {
          if (!busy.current) navigate(listPath)
        }}
        onPicker={() => setPicker(true)}
      />
      {picker && (
        <div className="mt-6">
          <ExamQuestionPicker
            sections={form.sections}
            selectedIds={form.questions.map((q) => q.questionId)}
            disabled={pending || locked || (!!saved && saved.status !== 'DRAFT')}
            onClose={() => setPicker(false)}
            onAdd={(question) => {
              if (!alive.current || busy.current || selectionVersion !== editVersion.current) return
              setForm((current) =>
                current.questions.some((q) => q.questionId === question.questionId) ||
                current.questions.length >= 300 ||
                !current.sections.some((s) => s.key === question.sectionKey)
                  ? current
                  : { ...current, questions: [...current.questions, question] },
              )
              setDirty(true)
              setNotice(undefined)
              setErrors([])
            }}
          />
        </div>
      )}
      <ConfirmPanel
        open={!!confirmation}
        pending={pending}
        danger={confirmation === 'archive' || confirmation === 'reload'}
        title={
          {
            publish: 'Công bố đề thi?',
            archive: 'Lưu trữ đề thi?',
            clone: 'Nhân bản bản đã lưu',
            reload: 'Tải lại và bỏ thay đổi chưa lưu?',
          }[confirmation ?? 'publish']
        }
        confirmLabel={
          confirmation === 'clone'
            ? 'Nhân bản đề'
            : confirmation === 'reload'
              ? 'Tải lại bản trên máy chủ'
              : 'Xác nhận'
        }
        onCancel={() => {
          if (!busy.current) setConfirmation(undefined)
        }}
        onConfirm={() => {
          if (!confirmation) return
          if (confirmation === 'reload') {
            setConfirmation(undefined)
            if (reconcileCode) {
              allowNavigation.current = true
              navigate(`/content/exams?q=${encodeURIComponent(reconcileCode)}`)
            } else query.reload()
          } else void perform(confirmation)
        }}
        message={
          <div>
            <p className="mb-4">
              {confirmation === 'publish'
                ? 'Máy chủ sẽ kiểm lại toàn bộ điều kiện trước khi công bố.'
                : confirmation === 'reload'
                  ? reconcileCode
                    ? 'Kết quả tạo hoặc nhân bản chưa rõ. Kiểm tra mã đề trong danh sách trước khi tạo lại; thay đổi chưa lưu sẽ được bỏ.'
                    : 'Bản nháp trên màn hình sẽ được thay bằng dữ liệu mới nhất; không ghi đè lên máy chủ.'
                  : 'Thao tác dùng bản đã lưu. Thay đổi chưa lưu không được áp dụng; lượt thi đang làm được giữ.'}
            </p>
            {confirmation === 'clone' && (
              <>
                <FormField htmlFor="editor-clone-code" label="Mã đề nhân bản" required>
                  <Input
                    disabled={pending}
                    value={code}
                    onChange={(e) => setCode(e.target.value)}
                  />
                </FormField>
                <FormField htmlFor="editor-clone-title" label="Tên đề nhân bản (không bắt buộc)">
                  <Input
                    disabled={pending}
                    value={title}
                    onChange={(e) => setTitle(e.target.value)}
                  />
                </FormField>
              </>
            )}
            {confirmation === 'clone' &&
              errors.map((error, index) => (
                <p role="alert" key={index}>
                  {error}
                </p>
              ))}
          </div>
        }
      />
      <ConfirmPanel
        open={blocker.state === 'blocked'}
        pending={pending}
        title="Rời đề và bỏ thay đổi chưa lưu?"
        message="Bản nháp này chưa được lưu. Bạn có thể ở lại để lưu hoặc rời trang và bỏ thay đổi."
        danger
        confirmLabel="Rời trang"
        onCancel={() => {
          if (blocker.state === 'blocked') blocker.reset()
        }}
        onConfirm={() => {
          if (blocker.state === 'blocked' && !busy.current) {
            allowNavigation.current = true
            blocker.proceed()
          }
        }}
      />
    </>
  )
}
export default function Scr066ExamEditorPage() {
  const { id } = useParams()
  if (id !== undefined && (!/^[1-9]\d*$/.test(id) || !Number.isSafeInteger(Number(id))))
    return (
      <main>
        <p role="alert">Mã đề không hợp lệ.</p>
        <Link to="/content/exams">Về danh sách đề</Link>
      </main>
    )
  return <Editor key={id ?? 'new'} id={id === undefined ? null : Number(id)} />
}
