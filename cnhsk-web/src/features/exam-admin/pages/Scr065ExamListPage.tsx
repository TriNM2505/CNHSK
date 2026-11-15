import { useEffect, useRef, useState } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { toApiError, useApiQuery } from '@/shared/api'
import { Button, ConfirmPanel, FormField, Input } from '@/shared/ui'
import { archiveExam, cloneExam, listExams } from '../api/exams'
import { ExamListView } from '../components/ExamListView'
import { failureMessage } from '../examForm'
import type { AdminExam, ExamFilters, ExamStatus } from '../types'

export default function Scr065ExamListPage() {
  const [search, setSearch] = useSearchParams()
  const navigate = useNavigate()
  const rawPage = search.get('page') ?? '0'
  const filters: ExamFilters = {
    status: (['DRAFT', 'PUBLISHED', 'ARCHIVED'] as ExamStatus[]).find(
      (value) => value === search.get('status'),
    ),
    hsk: /^[1-6]$/.test(search.get('hsk') ?? '') ? Number(search.get('hsk')) : undefined,
    q: search.get('q') ?? '',
    page: /^\d+$/.test(rawPage) && Number.isSafeInteger(Number(rawPage)) ? Number(rawPage) : 0,
  }
  const result = useApiQuery(
    (signal) => listExams(filters, signal),
    [filters.status, filters.hsk, filters.q, filters.page],
    { isEmpty: (data) => data.items.length === 0 },
  )
  const [confirmation, setConfirmation] = useState<{
    exam: AdminExam
    action: 'archive' | 'clone'
  }>()
  const [code, setCode] = useState('')
  const [title, setTitle] = useState('')
  const [error, setError] = useState<string>()
  const [notice, setNotice] = useState<string>()
  const [pending, setPending] = useState(false)
  const [locked, setLocked] = useState(false)
  const busy = useRef(false)
  const alive = useRef(true)
  useEffect(() => {
    alive.current = true
    return () => {
      alive.current = false
    }
  }, [])
  function filter(next: ExamFilters) {
    const params = new URLSearchParams()
    if (next.status) params.set('status', next.status)
    if (next.hsk) params.set('hsk', String(next.hsk))
    if (next.q) params.set('q', next.q)
    if (next.page) params.set('page', String(next.page))
    setSearch(params)
  }
  function reload() {
    setLocked(false)
    setError(undefined)
    setConfirmation(undefined)
    result.reload()
  }
  async function perform() {
    if (!confirmation || busy.current || locked) return
    if (
      confirmation.action === 'clone' &&
      (!/^[A-Z0-9-]{1,50}$/.test(code) || Array.from(title.trim()).length > 200)
    ) {
      setError('Nhập mã mới gồm 1–50 ký tự A–Z, số, dấu gạch ngang; tên tối đa 200 ký tự.')
      return
    }
    busy.current = true
    setPending(true)
    setError(undefined)
    try {
      const updated =
        confirmation.action === 'clone'
          ? await cloneExam(confirmation.exam.id, code, title)
          : await archiveExam(confirmation.exam.id)
      if (!alive.current) return
      setConfirmation(undefined)
      if (confirmation.action === 'clone')
        navigate(`/content/exams/${updated.id}`, {
          state: { examListSearch: search.toString() ? `?${search}` : '' },
        })
      else {
        setNotice('Đã lưu trữ đề thi.')
        result.reload()
      }
    } catch (caught) {
      if (!alive.current) return
      const failure = toApiError(caught)
      setError(failureMessage(failure))
      if (failure.status === 0 || failure.status >= 500 || failure.status === 404) {
        setLocked(true)
        setConfirmation(undefined)
      }
    } finally {
      busy.current = false
      if (alive.current) setPending(false)
    }
  }
  return (
    <>
      {notice && (
        <p className="mb-4" role="status">
          {notice}
        </p>
      )}
      {error && !confirmation && (
        <div role="alert" className="mb-4">
          <p>{error}</p>
          {locked && (
            <>
              <p>
                Kết quả thao tác chưa rõ hoặc dữ liệu đã cũ. Tải lại danh sách trước khi ghi tiếp.
              </p>
              <Button variant="outline" onClick={reload}>
                Tải lại danh sách
              </Button>
            </>
          )}
        </div>
      )}
      <ExamListView
        state={result.state}
        data={result.data}
        error={result.error?.message}
        filters={filters}
        busy={pending || locked}
        onFilter={filter}
        onRetry={reload}
        onAction={(exam, action) => {
          if (busy.current || locked) return
          setError(undefined)
          setNotice(undefined)
          setCode('')
          setTitle('')
          setConfirmation({ exam, action })
        }}
      />
      <ConfirmPanel
        open={!!confirmation}
        pending={pending}
        danger={confirmation?.action === 'archive'}
        title={confirmation?.action === 'clone' ? 'Nhân bản đề thi' : 'Lưu trữ đề thi?'}
        confirmLabel={confirmation?.action === 'clone' ? 'Nhân bản đề' : 'Xác nhận lưu trữ'}
        onCancel={() => {
          if (!busy.current) setConfirmation(undefined)
        }}
        onConfirm={() => void perform()}
        message={
          <div>
            <p className="mb-4 break-words">
              {confirmation?.exam.title}
              {confirmation?.action === 'archive'
                ? ' — đề sẽ rời danh sách đề công khai; lượt đang làm được giữ.'
                : ' — tạo bản nháp mới từ bản đã lưu.'}
            </p>
            {confirmation?.action === 'clone' && (
              <>
                <FormField htmlFor="list-clone-code" label="Mã đề nhân bản" required>
                  <Input
                    disabled={pending}
                    value={code}
                    onChange={(e) => setCode(e.target.value)}
                  />
                </FormField>
                <FormField htmlFor="list-clone-title" label="Tên đề nhân bản (không bắt buộc)">
                  <Input
                    disabled={pending}
                    value={title}
                    onChange={(e) => setTitle(e.target.value)}
                  />
                </FormField>
              </>
            )}
            {error && <p role="alert">{error}</p>}
          </div>
        }
      />
    </>
  )
}
