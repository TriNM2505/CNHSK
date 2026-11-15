import { codePointLength } from '@/features/grading/essayRules'
import { displayTime } from '@/features/grading/types'
import type { ApiError, ApiQueryState } from '@/shared/api'
import { Button, Card, ConfirmPanel, FormField, Input, PageHeader, Textarea } from '@/shared/ui'
import { criterionLabels, resultErrors, totalScore } from '../gradingRules'
import type { Criteria, ResultDraft, TeacherGradingDetail } from '../types'
import { deadlineLabel } from '../useGradingDeadline'

type Props = {
  state: ApiQueryState
  detail?: TeacherGradingDetail
  error?: ApiError
  actionError?: ApiError
  draft: ResultDraft
  seconds: number | null
  pending: boolean
  confirmation: 'complete' | 'release' | null
  onDraft: (draft: ResultDraft) => void
  onRetry: () => void
  onReview: (action: 'complete' | 'release') => void
  onClose: () => void
  onConfirm: () => void
}
export function TeacherGradingWorkspaceView(props: Props) {
  const { detail, draft } = props
  const errors = resultErrors(draft)
  const completed = detail?.status === 'COMPLETED'
  return (
    <div className="mx-auto min-w-0 max-w-reading space-y-5 p-4 sm:p-6">
      <PageHeader title="Chấm bài viết" />
      <Button to="/teacher/grading" variant="outline">
        Về hàng đợi
      </Button>
      {props.state === 'loading' ? <p role="status">Đang tải bài cần chấm…</p> : null}
      {props.state === 'error' ? (
        <div role="alert" className="space-y-3">
          <p>{props.error?.message ?? 'Không tải được bài cần chấm.'}</p>
          {props.error?.status !== 403 ? <Button onClick={props.onRetry}>Thử lại</Button> : null}
        </div>
      ) : null}
      {props.state === 'empty' ? <p role="status">Không có bài cần chấm.</p> : null}
      {props.state === 'ready' && detail ? (
        <>
          <Card className="min-w-0 space-y-2 break-words">
            <h2 className="text-h3">{detail.reference}</h2>
            <p>
              {detail.hanziCount} chữ Hán
              {detail.hskLevel !== null ? ` · HSK ${detail.hskLevel}` : ''}
            </p>
            <p>Nhận lúc: {displayTime(detail.assignedAt)}</p>
            <p>Hạn chấm: {displayTime(detail.gradingDeadline)}</p>
            {!completed ? (
              <p role="timer" aria-label="Thời gian chấm còn lại">
                {deadlineLabel(props.seconds)}
              </p>
            ) : null}
            <p>Điểm trả cho bài: {detail.payoutCredits} điểm</p>
          </Card>
          {detail.prompt !== null ? (
            <Card className="min-w-0">
              <h2 className="text-h3">Đề bài</h2>
              <p className="mt-2 whitespace-pre-wrap break-words [overflow-wrap:anywhere]">
                {detail.prompt}
              </p>
            </Card>
          ) : null}
          <Card className="min-w-0">
            <h2 className="text-h3">Bài viết</h2>
            <p className="mt-2 whitespace-pre-wrap break-words font-hanzi [overflow-wrap:anywhere]">
              {detail.essay}
            </p>
          </Card>
          {completed && detail.result ? (
            <Card className="min-w-0 space-y-3 break-words [overflow-wrap:anywhere]">
              <h2 className="text-h3">Đã nộp kết quả</h2>
              <p className="text-h2">{detail.result.score}/100</p>
              <dl>
                {Object.entries(criterionLabels).map(([key, label]) => (
                  <div key={key} className="flex flex-wrap justify-between gap-2">
                    <dt>{label}</dt>
                    <dd>{detail.result!.criteria[key as keyof Criteria]}/25</dd>
                  </div>
                ))}
              </dl>
              <h3 className="text-h3">Nhận xét đã gửi</h3>
              <p className="whitespace-pre-wrap">{detail.result.feedback}</p>
              <h3 className="text-h3">Sửa lỗi đã gửi</h3>
              {detail.result.corrections.length === 0 ? (
                <p>Không có mục sửa lỗi.</p>
              ) : (
                detail.result.corrections.map((item, index) => (
                  <div key={index} className="space-y-1 border-t border-line pt-2">
                    <p className="whitespace-pre-wrap">Đã viết: {item.original}</p>
                    <p className="whitespace-pre-wrap">Sửa thành: {item.corrected}</p>
                    {item.note ? <p className="whitespace-pre-wrap">Ghi chú: {item.note}</p> : null}
                  </div>
                ))
              )}
            </Card>
          ) : detail.status === 'ASSIGNED' ? (
            <>
              <form
                onSubmit={(event) => {
                  event.preventDefault()
                  if (!props.pending && Object.keys(errors).length === 0) props.onReview('complete')
                }}
              >
                <fieldset disabled={props.pending} className="min-w-0 space-y-4">
                  <legend className="mb-4 text-h3">Chấm theo bốn tiêu chí</legend>
                  <div className="grid min-w-0 grid-cols-2 gap-3">
                    {Object.entries(criterionLabels).map(([key, label]) => (
                      <FormField
                        key={key}
                        htmlFor={`teacher-${key}`}
                        label={label}
                        required
                        hint="0–25 điểm"
                        error={
                          draft.criteria[key as keyof Criteria] !== ''
                            ? errors[`criteria.${key}`]
                            : undefined
                        }
                      >
                        <Input
                          type="number"
                          min={0}
                          max={25}
                          step={1}
                          value={draft.criteria[key as keyof Criteria]}
                          onChange={(event) =>
                            props.onDraft({
                              ...draft,
                              criteria: { ...draft.criteria, [key]: event.target.value },
                            })
                          }
                        />
                      </FormField>
                    ))}
                  </div>
                  <p role="status" className="text-h3">
                    Tổng điểm: {totalScore(draft)}/100
                  </p>
                  <FormField
                    htmlFor="teacher-feedback"
                    label="Nhận xét"
                    required
                    hint={`${codePointLength(draft.feedback)}/5.000 ký tự`}
                    error={draft.feedback ? errors.feedback : undefined}
                  >
                    <Textarea
                      rows={5}
                      value={draft.feedback}
                      onChange={(event) =>
                        props.onDraft({ ...draft, feedback: event.target.value })
                      }
                    />
                  </FormField>
                  <section aria-label="Bảng sửa lỗi" className="min-w-0 space-y-4">
                    <h2 className="text-h3">Sửa lỗi ({draft.corrections.length}/50)</h2>
                    {draft.corrections.map((item, index) => (
                      <Card key={index} className="min-w-0">
                        <h3 className="mb-3 text-h3">Lỗi {index + 1}</h3>
                        {(['original', 'corrected', 'note'] as const).map((key) => (
                          <FormField
                            key={key}
                            htmlFor={`correction-${index}-${key}`}
                            label={`${key === 'original' ? 'Đã viết' : key === 'corrected' ? 'Sửa thành' : 'Ghi chú'} ${index + 1}`}
                            required={key !== 'note'}
                            hint={`${codePointLength(item[key])}/500 ký tự`}
                            error={item[key] ? errors[`corrections.${index}.${key}`] : undefined}
                          >
                            <Textarea
                              rows={2}
                              value={item[key]}
                              onChange={(event) =>
                                props.onDraft({
                                  ...draft,
                                  corrections: draft.corrections.map((row, i) =>
                                    i === index ? { ...row, [key]: event.target.value } : row,
                                  ),
                                })
                              }
                            />
                          </FormField>
                        ))}
                        <Button
                          variant="outline"
                          onClick={() =>
                            props.onDraft({
                              ...draft,
                              corrections: draft.corrections.filter((_, i) => i !== index),
                            })
                          }
                        >
                          Xoá lỗi {index + 1}
                        </Button>
                      </Card>
                    ))}
                    {errors.corrections ? <p role="alert">{errors.corrections}</p> : null}
                    <Button
                      variant="outline"
                      disabled={draft.corrections.length >= 50}
                      onClick={() =>
                        props.onDraft({
                          ...draft,
                          corrections: [
                            ...draft.corrections,
                            { original: '', corrected: '', note: '' },
                          ],
                        })
                      }
                    >
                      Thêm lỗi
                    </Button>
                  </section>
                  {props.actionError ? (
                    <p role="alert" className="break-words text-danger">
                      {props.actionError.message}
                    </p>
                  ) : null}
                  <div className="flex flex-wrap gap-3">
                    <Button
                      type="submit"
                      disabled={Object.keys(errors).length > 0}
                      loading={props.pending}
                    >
                      Nộp kết quả
                    </Button>
                    <Button variant="outline" onClick={() => props.onReview('release')}>
                      Trả lại bài
                    </Button>
                  </div>
                </fieldset>
              </form>
              <ConfirmPanel
                open={props.confirmation !== null}
                pending={props.pending}
                title={
                  props.confirmation === 'release' ? 'Xác nhận trả lại bài' : 'Xác nhận nộp kết quả'
                }
                confirmLabel={
                  props.confirmation === 'release' ? 'Xác nhận trả lại' : 'Xác nhận nộp'
                }
                message={
                  props.confirmation === 'release'
                    ? 'Bài sẽ được trả lại theo trạng thái hiện tại trên máy chủ. Bạn sẽ rời khung chấm.'
                    : `Nộp kết quả ${totalScore(draft)}/100 cho ${detail.reference}?`
                }
                onCancel={props.onClose}
                onConfirm={props.onConfirm}
              />
            </>
          ) : (
            <p role="status">Bài không còn ở trạng thái đang chấm.</p>
          )}
        </>
      ) : null}
    </div>
  )
}
