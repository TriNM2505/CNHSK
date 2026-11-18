import { Button, FormField, Input, Select } from '@/shared/ui'
import { ClipboardList, FileQuestion, ListChecks, Plus } from 'lucide-react'
import {
  QUESTION_STATUS_LABELS,
  QUESTION_TYPE_LABELS,
} from '@/features/question-bank/questionListQuery'
import { moveQuestion } from '../examForm'
import type { ExamForm } from '../types'

export function ExamQuestionsEditor({
  form,
  disabled,
  onChange,
  onPicker,
}: {
  form: ExamForm
  disabled: boolean
  onChange: (form: ExamForm) => void
  onPicker?: () => void
}) {
  return (
    <section
      aria-label="Câu hỏi đã chọn"
      className="min-w-0 space-y-4 rounded-md border border-brand-100 bg-brand-50/30 p-5"
    >
      <h2 className="flex items-center gap-2 text-h3 font-bold">
        <ListChecks aria-hidden="true" className="size-5 text-brand-700" />
        Câu hỏi đã chọn ({form.questions.length}/300)
      </h2>
      {!form.questions.length && (
        <div className="space-y-3 rounded-md border border-brand-100 bg-surface px-4 py-6 text-center">
          <ClipboardList aria-hidden="true" className="mx-auto size-10 text-ink-muted" />
          <p className="font-semibold">Chưa có câu hỏi nào</p>
          <p className="text-body-sm text-ink-secondary">
            Chưa có câu hỏi. Có thể lưu nháp trước khi chọn câu.
          </p>
          {onPicker && (
            <Button
              aria-label="Chọn câu hỏi từ ngân hàng"
              variant="outline"
              disabled={disabled}
              onClick={onPicker}
            >
              <span className="inline-flex items-center gap-2">
                <Plus aria-hidden="true" className="size-4" />
                Chọn câu hỏi
              </span>
            </Button>
          )}
        </div>
      )}
      {form.sections.map((section, sectionIndex) => {
        const questions = form.questions.filter((q) => q.sectionKey === section.key)
        return (
          <div
            key={section.key}
            className="min-w-0 space-y-4"
            onDragOver={(event) => {
              if (!disabled) event.preventDefault()
            }}
            onDrop={(event) => {
              event.preventDefault()
              const raw = event.dataTransfer.getData('application/question')
              if (!disabled && /^\d+$/.test(raw))
                onChange(moveQuestion(form, Number(raw), section.key))
            }}
          >
            <h3 className="text-label">
              {sectionIndex + 1}. {section.name || 'Phần chưa đặt tên'} · {questions.length} câu
            </h3>
            {questions.map((q, index) => (
              <article
                key={q.questionId}
                draggable={!disabled}
                onDragStart={(event) =>
                  event.dataTransfer.setData('application/question', String(q.questionId))
                }
                onDragOver={(event) => {
                  if (!disabled) event.preventDefault()
                }}
                onDrop={(event) => {
                  event.preventDefault()
                  event.stopPropagation()
                  const raw = event.dataTransfer.getData('application/question')
                  if (!disabled && /^\d+$/.test(raw))
                    onChange(moveQuestion(form, Number(raw), section.key, q.questionId))
                }}
                className="min-w-0 rounded-md border border-brand-100 bg-surface p-4"
              >
                <p className="flex items-start gap-2 break-words text-label font-semibold text-ink">
                  <FileQuestion
                    aria-hidden="true"
                    className="mt-0.5 size-4 shrink-0 text-brand-700"
                  />
                  {index + 1}. {q.code || `Câu #${q.questionId}`}
                </p>
                <p className="my-2 break-words text-body-sm text-ink-secondary">
                  {(QUESTION_TYPE_LABELS as Record<string, string>)[q.qtype] ?? q.qtype} ·{' '}
                  {(QUESTION_STATUS_LABELS as Record<string, string>)[q.status] ?? q.status} · KID:{' '}
                  {q.kid || 'Chưa có'} · {q.hasAudio ? 'Có audio' : 'Không có audio'}
                </p>
                <div className="grid gap-x-4 sm:grid-cols-2">
                  <FormField
                    htmlFor={`points-${q.questionId}`}
                    label={`Điểm câu ${q.code || q.questionId}`}
                  >
                    <Input
                      disabled={disabled}
                      type="number"
                      min={1}
                      max={100}
                      step={1}
                      value={q.points}
                      onChange={(event) =>
                        onChange({
                          ...form,
                          questions: form.questions.map((item) =>
                            item.questionId === q.questionId
                              ? { ...item, points: event.target.value }
                              : item,
                          ),
                        })
                      }
                    />
                  </FormField>
                  <FormField
                    htmlFor={`target-${q.questionId}`}
                    label={`Phần của câu ${q.code || q.questionId}`}
                  >
                    <Select
                      disabled={disabled}
                      value={q.sectionKey}
                      onChange={(event) =>
                        onChange(moveQuestion(form, q.questionId, event.target.value))
                      }
                    >
                      {form.sections.map((s, i) => (
                        <option key={s.key} value={s.key}>
                          {i + 1}. {s.name || 'Chưa đặt tên'}
                        </option>
                      ))}
                    </Select>
                  </FormField>
                </div>
                <div className="flex flex-wrap gap-2 border-t border-line pt-4">
                  <Button
                    size="sm"
                    variant="ghost"
                    disabled={disabled || index === 0}
                    aria-label={`Đưa câu ${q.questionId} lên`}
                    onClick={() =>
                      onChange(
                        moveQuestion(
                          form,
                          q.questionId,
                          section.key,
                          questions[index - 1]?.questionId,
                        ),
                      )
                    }
                  >
                    Lên
                  </Button>
                  <Button
                    size="sm"
                    variant="ghost"
                    disabled={disabled || index === questions.length - 1}
                    aria-label={`Đưa câu ${q.questionId} xuống`}
                    onClick={() =>
                      onChange(
                        moveQuestion(
                          form,
                          q.questionId,
                          section.key,
                          questions[index + 2]?.questionId,
                        ),
                      )
                    }
                  >
                    Xuống
                  </Button>
                  <Button
                    size="sm"
                    variant="danger"
                    disabled={disabled}
                    onClick={() =>
                      onChange({
                        ...form,
                        questions: form.questions.filter(
                          (item) => item.questionId !== q.questionId,
                        ),
                      })
                    }
                  >
                    Bỏ câu {q.code || q.questionId}
                  </Button>
                </div>
              </article>
            ))}
          </div>
        )
      })}
    </section>
  )
}
