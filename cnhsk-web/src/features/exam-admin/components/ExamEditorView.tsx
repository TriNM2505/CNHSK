import { Button, FormField, Input, PageHeader, Select, SUPPORTED_HSK_LEVELS } from '@/shared/ui'
import { pointsTotal, statusLabels } from '../examForm'
import type { ExamDetail, ExamForm, PublicationError } from '../types'
import { ExamSectionsEditor } from './ExamSectionsEditor'
import { ExamQuestionsEditor } from './ExamQuestionsEditor'

export type ExamEditorViewProps = {
  form: ExamForm
  saved: ExamDetail | null
  dirty: boolean
  busy: boolean
  locked?: boolean
  errors: string[]
  publicationErrors: PublicationError[]
  notice?: string
  onChange: (form: ExamForm) => void
  onSave: () => void
  onPublish: () => void
  onArchive: () => void
  onClone: () => void
  onReload: () => void
  onBack: () => void
  onPicker: () => void
}
export function ExamEditorView({
  form,
  saved,
  dirty,
  busy,
  locked,
  errors,
  publicationErrors,
  notice,
  onChange,
  onSave,
  onPublish,
  onArchive,
  onClone,
  onReload,
  onBack,
  onPicker,
}: ExamEditorViewProps) {
  const readonly = !!saved && saved.status !== 'DRAFT'
  const disabled = busy || readonly || !!locked
  const sum = pointsTotal(form)
  const level = Number(form.hskLevel)
  return (
    <main className="min-w-0 space-y-6 px-4 py-6 [overflow-wrap:anywhere] sm:px-6">
      <PageHeader
        title={saved ? `Đề thi · ${saved.code}` : 'Tạo đề nháp'}
        subtitle={
          saved
            ? `${statusLabels[saved.status]}${dirty ? ' · Có thay đổi chưa lưu' : ' · Bản đã lưu'}`
            : 'Lưu nháp trước khi công bố.'
        }
        actions={
          <Button variant="outline" disabled={busy} onClick={onBack}>
            Về danh sách đề
          </Button>
        }
      />
      {notice && (
        <p role="status" className="rounded-md bg-brand-50 p-4">
          {notice}
        </p>
      )}
      {readonly && (
        <p className="rounded-md border border-line p-4">
          Đề {statusLabels[saved.status].toLowerCase()} chỉ đọc cấu trúc. Nhân bản để tạo bản nháp
          mới.
        </p>
      )}
      {locked && (
        <div role="alert" className="rounded-md border border-danger p-4">
          <p>
            Dữ liệu cần được đọc lại trước khi tiếp tục ghi. Bản nháp đang được giữ trên màn hình.
          </p>
          <Button variant="outline" disabled={busy} onClick={onReload}>
            Tải lại đề
          </Button>
        </div>
      )}
      {!!errors.length && (
        <ul role="alert" className="list-inside list-disc rounded-md border border-danger p-4">
          {errors.map((error, index) => (
            <li key={index}>{error}</li>
          ))}
        </ul>
      )}
      <section
        className="min-w-0 rounded-md border border-line bg-surface p-4 sm:p-6"
        aria-label="Thông tin đề"
      >
        <h2 className="mb-4 text-h2">Thông tin đề</h2>
        <div className="grid gap-x-4 sm:grid-cols-2">
          <FormField htmlFor="exam-code" label="Mã đề" required>
            <Input
              disabled={disabled}
              value={form.code}
              onChange={(e) => onChange({ ...form, code: e.target.value })}
            />
          </FormField>
          <FormField htmlFor="exam-title" label="Tên đề" required>
            <Input
              disabled={disabled}
              value={form.title}
              onChange={(e) => onChange({ ...form, title: e.target.value })}
            />
          </FormField>
          <FormField htmlFor="exam-level" label="Cấp HSK" required>
            <Select
              disabled={disabled}
              value={form.hskLevel}
              onChange={(e) => onChange({ ...form, hskLevel: e.target.value })}
            >
              <option value="">Chọn cấp</option>
              {level > 3 && <option value={level}>HSK {level} (dữ liệu hiện có)</option>}
              {SUPPORTED_HSK_LEVELS.map((value) => (
                <option key={value} value={value}>
                  HSK {value}
                </option>
              ))}
            </Select>
          </FormField>
          <FormField
            htmlFor="exam-score"
            label="Tổng điểm khai báo"
            required
            hint="Từ 1 đến 1.000 điểm. Có thể lưu nháp khi tổng điểm câu hỏi chưa khớp."
          >
            <Input
              disabled={disabled}
              type="number"
              min={1}
              max={1000}
              step={1}
              value={form.totalScore}
              onChange={(e) => onChange({ ...form, totalScore: e.target.value })}
            />
          </FormField>
        </div>
        <p
          role="status"
          className={
            sum !== Number(form.totalScore)
              ? 'rounded-md border border-warning bg-warning-bg p-3'
              : 'rounded-md bg-brand-50 p-3'
          }
        >
          Tổng điểm câu hỏi: <strong>{sum}</strong> / {form.totalScore || 'chưa khai báo'}
          {sum !== Number(form.totalScore) &&
            ' · Chưa khớp tổng khai báo; máy chủ sẽ kiểm khi công bố.'}
        </p>
      </section>
      <ExamSectionsEditor form={form} disabled={disabled} onChange={onChange} />
      <div className="flex flex-wrap items-center justify-between gap-3">
        <h2 className="text-h2">Ngân hàng câu hỏi</h2>
        <Button
          variant="outline"
          disabled={disabled || form.questions.length >= 300}
          onClick={onPicker}
        >
          Chọn câu hỏi
        </Button>
      </div>
      <ExamQuestionsEditor form={form} disabled={disabled} onChange={onChange} />
      <section
        aria-label="Lỗi chặn công bố"
        className="rounded-md border border-line bg-surface p-4 sm:p-6"
      >
        <h2 className="text-h2">Lỗi chặn công bố</h2>
        {dirty && (
          <p className="my-2 text-body-sm text-ink-secondary">
            Các lỗi dưới đây thuộc bản đã lưu. Lưu nháp để cập nhật kiểm tra.
          </p>
        )}
        {publicationErrors.length ? (
          <ul className="mt-3 space-y-2">
            {publicationErrors.map((error, index) => (
              <li key={index} className="break-words rounded-sm bg-warning-bg p-3">
                <strong>{error.code}</strong>: {error.message}
                {error.sectionIndex !== null &&
                  error.sectionIndex !== undefined &&
                  ` · Phần ${error.sectionIndex + 1}`}
                {error.questionId && ` · Câu #${error.questionId}`}
              </li>
            ))}
          </ul>
        ) : (
          <p className="mt-3 text-ink-secondary">
            Chưa có lỗi công bố từ máy chủ. Máy chủ sẽ kiểm lại khi công bố.
          </p>
        )}
      </section>
      <div className="flex flex-wrap gap-3 rounded-md border border-line bg-surface p-4">
        {!readonly && (
          <Button loading={busy} disabled={disabled} onClick={onSave}>
            Lưu nháp
          </Button>
        )}
        {saved && !readonly && (
          <Button variant="secondary" disabled={busy || dirty || !!locked} onClick={onPublish}>
            Công bố
          </Button>
        )}
        {saved && (
          <>
            <Button variant="outline" disabled={busy || !!locked} onClick={onClone}>
              Nhân bản đề
            </Button>
            <Button
              variant="danger"
              disabled={busy || !!locked || saved.status === 'ARCHIVED'}
              onClick={onArchive}
            >
              Lưu trữ đề
            </Button>
          </>
        )}
        {dirty && (
          <p className="basis-full text-body-sm text-ink-secondary">
            Lưu nháp trước khi công bố. Thay đổi hiện tại chưa được áp dụng trên máy chủ.
          </p>
        )}
      </div>
    </main>
  )
}
