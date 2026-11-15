import { useState, type FormEvent } from 'react'
import type { ApiError, ApiQueryState } from '@/shared/api'
import { Button, Card, FormField, Input, Pagination, Select, Textarea } from '@/shared/ui'
import { draftOf, validateDraft, writeOf, type ContestDraft } from '../contestForm'
import type { AdminContest, AdminExamSummary, ContestWrite, ExamListParams, Page } from '../types'

type Props = {
  contest: AdminContest | null
  lockedReason?: string
  pending?: boolean
  error?: ApiError
  examState: ApiQueryState
  exams?: Page<AdminExamSummary>
  examError?: string
  examFilters: ExamListParams
  selectedExam?: AdminExamSummary | null
  checkingExam?: boolean
  onExamFilter: (filter: ExamListParams) => void
  onExamRetry: () => void
  onSelectedExam: (id: number | null) => void
  onDirty: () => void
  onSubmit: (input: ContestWrite) => void
}
export function ContestEditorForm(props: Props) {
  const {
    contest,
    lockedReason,
    pending,
    error,
    examState,
    exams,
    examError,
    examFilters,
    selectedExam,
    checkingExam,
    onExamFilter,
    onExamRetry,
    onSelectedExam,
    onDirty,
    onSubmit,
  } = props
  const [draft, setDraft] = useState<ContestDraft>(() => draftOf(contest))
  const [errors, setErrors] = useState<Record<string, string>>({})
  const [search, setSearch] = useState(examFilters.q ?? '')
  const disabled = pending || !!lockedReason
  function change<K extends keyof ContestDraft>(key: K, value: ContestDraft[K]) {
    setDraft((current) => ({ ...current, [key]: value }))
    setErrors((current) => ({ ...current, [key]: '' }))
    onDirty()
  }
  const serverFields = Array.isArray(error?.details)
    ? (error.details as { field: string; message: string }[])
    : []
  const fieldError = (field: string) =>
    errors[field] || serverFields.find((item) => item.field === field)?.message
  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (disabled) return
    const validation = validateDraft(draft)
    setErrors(validation)
    if (Object.keys(validation).length) {
      window.setTimeout(
        () => document.querySelector<HTMLElement>('[aria-invalid="true"]')?.focus(),
        0,
      )
      return
    }
    onSubmit(writeOf(draft))
  }
  const choices = exams?.items.filter((exam) => exam.status !== 'ARCHIVED') ?? []
  const selectedOnPage = choices.some((exam) => String(exam.id) === draft.examId)
  return (
    <Card className="min-w-0">
      <form aria-label="Thông tin cuộc thi" noValidate onSubmit={submit}>
        <h2 className="mb-4 text-h3">Thông tin cuộc thi</h2>
        {lockedReason && (
          <p className="mb-4 text-body-sm" role="status">
            {lockedReason}
          </p>
        )}
        <fieldset disabled={disabled} className="min-w-0">
          <FormField
            htmlFor="contest-name"
            label="Tên cuộc thi"
            required
            error={fieldError('name')}
          >
            <Input
              value={draft.name}
              maxLength={200}
              onChange={(e) => change('name', e.target.value)}
            />
          </FormField>
          <FormField htmlFor="contest-description" label="Mô tả" error={fieldError('description')}>
            <Textarea
              value={draft.description}
              maxLength={5000}
              onChange={(e) => change('description', e.target.value)}
            />
          </FormField>
          <div className="grid min-w-0 gap-x-4 sm:grid-cols-2">
            <FormField
              htmlFor="contest-start"
              label="Bắt đầu (giờ Việt Nam)"
              required
              error={fieldError('startsAt')}
            >
              <Input
                type="datetime-local"
                className="min-w-0"
                value={draft.startsAt}
                onChange={(e) => change('startsAt', e.target.value)}
              />
            </FormField>
            <FormField
              htmlFor="contest-end"
              label="Kết thúc (giờ Việt Nam)"
              required
              error={fieldError('endsAt')}
            >
              <Input
                type="datetime-local"
                className="min-w-0"
                value={draft.endsAt}
                onChange={(e) => change('endsAt', e.target.value)}
              />
            </FormField>
          </div>
          <p className="mb-4 text-body-sm text-ink-secondary">
            Mọi thời gian nhập theo Việt Nam (UTC+7), kể cả khi thiết bị ở múi giờ khác.
          </p>
          <FormField
            htmlFor="contest-capacity"
            label="Số thí sinh tối đa"
            hint="Để trống nếu không giới hạn."
            error={fieldError('maxParticipants')}
          >
            <Input
              type="number"
              min={1}
              max={100000}
              value={draft.maxParticipants}
              onChange={(e) => change('maxParticipants', e.target.value)}
            />
          </FormField>
          <div className="mb-4 space-y-3 rounded-md border border-line p-3">
            <h3 className="font-semibold">Đề thi</h3>
            <FormField htmlFor="exam-search" label="Tìm đề theo tên hoặc mã">
              <Input value={search} maxLength={100} onChange={(e) => setSearch(e.target.value)} />
            </FormField>
            <Button
              type="button"
              variant="outline"
              onClick={() => onExamFilter({ q: search.trim() || undefined, page: 0 })}
            >
              Tìm đề
            </Button>
            {examState === 'loading' && <p role="status">Đang tải danh sách đề…</p>}
            {examState === 'error' && (
              <div role="alert">
                <p>{examError}</p>
                <Button type="button" variant="outline" onClick={onExamRetry}>
                  Thử tải đề lại
                </Button>
              </div>
            )}
            {examState !== 'loading' && examState !== 'error' && choices.length === 0 && (
              <p>Trang này không có đề nháp hoặc đã công bố. Thử trang khác hoặc đổi từ tìm.</p>
            )}
            <FormField
              htmlFor="contest-exam"
              label="Đề được chọn"
              hint="Đề nháp giữ riêng cho cuộc thi; đề đã công bố cũng được dùng. Cần đề có câu hỏi hợp lệ để công bố."
              error={fieldError('examId')}
            >
              <Select
                value={draft.examId}
                onChange={(e) => {
                  change('examId', e.target.value)
                  onSelectedExam(e.target.value ? Number(e.target.value) : null)
                }}
              >
                <option value="">Chưa chọn đề</option>
                {draft.examId && !selectedOnPage && (
                  <option value={draft.examId}>
                    {selectedExam?.title ?? contest?.examTitle ?? `Đề #${draft.examId}`} (đang gắn)
                  </option>
                )}
                {choices.map((exam) => (
                  <option key={exam.id} value={exam.id}>
                    {exam.code} · {exam.title} ·{' '}
                    {exam.status === 'DRAFT' ? 'Bản nháp' : 'Đã công bố'}
                  </option>
                ))}
              </Select>
            </FormField>
            {checkingExam && draft.examId && <p role="status">Đang kiểm tra đề đã chọn…</p>}
            {selectedExam?.status === 'ARCHIVED' && (
              <p role="alert">Đề đã lưu trữ — chọn đề khác trước khi công bố</p>
            )}
            {exams && examState !== 'loading' && examState !== 'error' && (
              <Pagination
                label="Phân trang đề thi"
                page={exams.page + 1}
                size={exams.size}
                total={exams.totalElements}
                onChange={(page) => onExamFilter({ ...examFilters, page: page - 1 })}
              />
            )}
          </div>
          <div className="space-y-3">
            <h3 className="font-semibold">Giải thưởng</h3>
            <p className="text-body-sm text-ink-secondary">
              Tối đa 10 mục, chỉ mô tả giải. Ban tổ chức liên hệ ngoài hệ thống.
            </p>
            {errors.prizes && <p role="alert">{errors.prizes}</p>}
            {draft.prizes.map((prize, index) => (
              <fieldset key={index} className="min-w-0 rounded-md border border-line p-3">
                <legend className="px-1 font-semibold">Giải {index + 1}</legend>
                <div className="grid grid-cols-2 gap-3">
                  <FormField htmlFor={`prize-from-${index}`} label="Từ hạng">
                    <Input
                      type="number"
                      min={1}
                      value={prize.fromRank}
                      onChange={(e) =>
                        change(
                          'prizes',
                          draft.prizes.map((item, i) =>
                            i === index ? { ...item, fromRank: e.target.value } : item,
                          ),
                        )
                      }
                    />
                  </FormField>
                  <FormField htmlFor={`prize-to-${index}`} label="Đến hạng">
                    <Input
                      type="number"
                      min={1}
                      value={prize.toRank}
                      onChange={(e) =>
                        change(
                          'prizes',
                          draft.prizes.map((item, i) =>
                            i === index ? { ...item, toRank: e.target.value } : item,
                          ),
                        )
                      }
                    />
                  </FormField>
                </div>
                <FormField
                  htmlFor={`prize-description-${index}`}
                  label="Mô tả giải"
                  error={errors[`prizes.${index}`]}
                >
                  <Textarea
                    maxLength={200}
                    value={prize.description}
                    onChange={(e) =>
                      change(
                        'prizes',
                        draft.prizes.map((item, i) =>
                          i === index ? { ...item, description: e.target.value } : item,
                        ),
                      )
                    }
                  />
                </FormField>
                <Button
                  type="button"
                  variant="ghost"
                  onClick={() =>
                    change(
                      'prizes',
                      draft.prizes.filter((_, i) => i !== index),
                    )
                  }
                >
                  Bỏ giải {index + 1}
                </Button>
              </fieldset>
            ))}
            <Button
              type="button"
              variant="outline"
              disabled={draft.prizes.length >= 10}
              onClick={() =>
                change('prizes', [...draft.prizes, { fromRank: '', toRank: '', description: '' }])
              }
            >
              Thêm giải thưởng
            </Button>
          </div>
          <div className="mt-6">
            <Button type="submit" loading={pending}>
              {contest?.status === 'PUBLISHED' ? 'Lưu thay đổi' : 'Lưu nháp'}
            </Button>
          </div>
        </fieldset>
      </form>
    </Card>
  )
}
