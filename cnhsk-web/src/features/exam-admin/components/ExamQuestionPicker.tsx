import { useEffect, useRef, useState } from 'react'
import { getQuestion, listQuestions } from '@/features/question-bank/api/questions'
import { QUESTION_TYPE_LABELS } from '@/features/question-bank/questionListQuery'
import type { QuestionListParams, QuestionType } from '@/features/question-bank/types'
import { useApiQuery } from '@/shared/api'
import {
  Button,
  FormField,
  Input,
  Pagination,
  Select,
  Skeleton,
  SUPPORTED_HSK_LEVELS,
} from '@/shared/ui'
import { failureMessage } from '../examForm'
import type { DraftQuestion, DraftSection } from '../types'

export function ExamQuestionPicker({
  sections,
  selectedIds,
  disabled,
  onAdd,
  onClose,
}: {
  sections: DraftSection[]
  selectedIds: number[]
  disabled: boolean
  onAdd: (question: DraftQuestion) => void
  onClose: () => void
}) {
  const [filters, setFilters] = useState<QuestionListParams>({ page: 0 })
  const [q, setQ] = useState('')
  const [kid, setKid] = useState('')
  const [target, setTarget] = useState(sections[0]?.key ?? '')
  const [pending, setPending] = useState<number>()
  const [error, setError] = useState<string>()
  const [notice, setNotice] = useState<string>()
  const busy = useRef(false)
  const alive = useRef(true)
  const controller = useRef<AbortController>()
  useEffect(() => {
    alive.current = true
    return () => {
      alive.current = false
      controller.current?.abort()
    }
  }, [])
  const result = useApiQuery(
    (signal) => listQuestions({ ...filters, status: 'APPROVED', size: 10 }, signal),
    [filters],
    { isEmpty: (data) => data.items.length === 0 },
  )
  const sectionKey = sections.some((s) => s.key === target) ? target : (sections[0]?.key ?? '')
  async function add(id: number) {
    if (
      disabled ||
      busy.current ||
      selectedIds.includes(id) ||
      selectedIds.length >= 300 ||
      !sectionKey
    )
      return
    busy.current = true
    setPending(id)
    setError(undefined)
    setNotice(undefined)
    controller.current = new AbortController()
    try {
      const detail = await getQuestion(id, controller.current.signal)
      if (!alive.current) return
      if (detail.status !== 'APPROVED') {
        setError('Câu hỏi vừa đổi trạng thái. Tải lại danh sách để chọn câu đã duyệt.')
        return
      }
      const primary = detail.labels.find((label) => label.role === 'PRIMARY')
      if (!primary) {
        setError('Câu hỏi chưa có KID chính. Chọn câu khác.')
        return
      }
      onAdd({
        questionId: id,
        sectionKey,
        points: '1',
        code: detail.code ?? null,
        qtype: detail.qtype,
        status: detail.status,
        kid: primary.kid,
        hasAudio: !!detail.audioUrl?.trim(),
      })
      setNotice(`Đã thêm ${detail.code || `câu #${id}`} vào nháp.`)
    } catch (caught) {
      if (alive.current) setError(failureMessage(caught))
    } finally {
      busy.current = false
      if (alive.current) setPending(undefined)
    }
  }
  return (
    <section
      aria-label="Chọn câu từ ngân hàng"
      className="mx-auto w-full min-w-0 max-w-reading space-y-6 break-words rounded-md border border-brand-100 bg-brand-50 p-6"
    >
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <h2 className="text-h2">Chọn câu từ ngân hàng</h2>
          <p className="mt-1 text-body-sm text-ink-secondary">
            Chỉ câu đã duyệt. Điều kiện engine và công bố được kiểm tra trên máy chủ.
          </p>
        </div>
        <Button disabled={disabled || !!pending} variant="outline" onClick={onClose}>
          Đóng bộ chọn câu
        </Button>
      </div>
      <FormField htmlFor="picker-target" label="Thêm vào phần">
        <Select
          disabled={disabled || !!pending}
          value={sectionKey}
          onChange={(event) => setTarget(event.target.value)}
        >
          {sections.map((s, i) => (
            <option key={s.key} value={s.key}>
              {i + 1}. {s.name || 'Chưa đặt tên'}
            </option>
          ))}
        </Select>
      </FormField>
      <form
        className="grid min-w-0 gap-x-4 sm:grid-cols-2"
        onSubmit={(event) => {
          event.preventDefault()
          if (q.trim() && (Array.from(q.trim()).length < 2 || Array.from(q.trim()).length > 100)) {
            setError('Từ khoá cần 2–100 ký tự hoặc để trống.')
            return
          }
          setError(undefined)
          setFilters({
            ...filters,
            kid: kid.trim() || undefined,
            q: q.trim() || undefined,
            page: 0,
          })
        }}
      >
        <FormField htmlFor="picker-kid" label="Lọc KID">
          <Input disabled={disabled} value={kid} onChange={(e) => setKid(e.target.value)} />
        </FormField>
        <FormField htmlFor="picker-q" label="Tìm nội dung câu hỏi">
          <Input disabled={disabled} value={q} onChange={(e) => setQ(e.target.value)} />
        </FormField>
        <FormField htmlFor="picker-type" label="Lọc dạng câu">
          <Select
            disabled={disabled}
            value={filters.qtype ?? ''}
            onChange={(e) =>
              setFilters({
                ...filters,
                qtype: (e.target.value as QuestionType) || undefined,
                page: 0,
              })
            }
          >
            <option value="">Tất cả dạng</option>
            {Object.entries(QUESTION_TYPE_LABELS).map(([value, label]) => (
              <option key={value} value={value}>
                {label}
              </option>
            ))}
          </Select>
        </FormField>
        <FormField htmlFor="picker-hsk" label="Lọc cấp câu hỏi">
          <Select
            disabled={disabled}
            value={filters.hskLevel ?? ''}
            onChange={(e) =>
              setFilters({
                ...filters,
                hskLevel: e.target.value ? Number(e.target.value) : undefined,
                page: 0,
              })
            }
          >
            <option value="">Tất cả cấp</option>
            {SUPPORTED_HSK_LEVELS.map((value) => (
              <option key={value} value={value}>
                HSK {value}
              </option>
            ))}
          </Select>
        </FormField>
        <div className="mb-3">
          <Button type="submit" disabled={disabled}>
            Tìm câu hỏi
          </Button>
        </div>
      </form>
      {error && <p role="alert">{error}</p>}
      {notice && <p role="status">{notice}</p>}
      {selectedIds.length >= 300 && (
        <p role="status">Đã đạt tối đa 300 câu. Bỏ câu khỏi nháp trước khi thêm.</p>
      )}
      {result.state === 'loading' && (
        <div>
          <p role="status">Đang tải câu hỏi…</p>
          <Skeleton variant="card" />
        </div>
      )}
      {result.state === 'error' && (
        <div role="alert">
          <p>{result.error?.message}</p>
          <Button variant="outline" onClick={result.reload}>
            Thử tải câu hỏi lại
          </Button>
        </div>
      )}
      {result.state === 'empty' && <p>Không có câu hỏi đã duyệt phù hợp.</p>}
      {result.state === 'ready' && (
        <ul className="space-y-3">
          {result.data?.items.map((row) => (
            <li
              key={row.id}
              className="min-w-0 space-y-3 rounded-md border border-line bg-surface p-6"
            >
              <p className="break-words font-semibold">{row.code || `Câu #${row.id}`}</p>
              <p className="my-2 break-words font-hanzi text-h3">{row.stemSummary}</p>
              <p className="mb-3 break-words text-body-sm text-ink-secondary">
                {QUESTION_TYPE_LABELS[row.qtype]} · HSK {row.hskLevel} ·{' '}
                {row.primaryKid || 'Chưa có KID chính'}
              </p>
              <Button
                disabled={
                  disabled || !!pending || selectedIds.includes(row.id) || selectedIds.length >= 300
                }
                loading={pending === row.id}
                onClick={() => void add(row.id)}
              >
                {selectedIds.includes(row.id) ? 'Đã chọn' : `Thêm ${row.code || row.id}`}
              </Button>
            </li>
          ))}
        </ul>
      )}
      {result.data && (
        <Pagination
          label="Trang câu hỏi"
          page={(filters.page ?? 0) + 1}
          size={10}
          total={result.data.totalElements}
          onChange={(page) => {
            if (!disabled && !pending) setFilters({ ...filters, page: page - 1 })
          }}
        />
      )}
    </section>
  )
}
