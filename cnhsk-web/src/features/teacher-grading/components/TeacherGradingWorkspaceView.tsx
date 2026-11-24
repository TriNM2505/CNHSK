import {
  ArrowLeft,
  Award,
  Clock,
  Coins,
  FilePenLine,
  FileText,
  Plus,
  Sparkles,
  Trash2,
} from 'lucide-react'

import { GradingScoreBreakdown } from '@/features/grading/components/GradingScoreBreakdown'
import { codePointLength } from '@/features/grading/essayRules'
import { levelText, rubricForMode } from '@/features/grading/rubrics'
import { displayTime } from '@/features/grading/types'
import type { ApiError, ApiQueryState } from '@/shared/api'
import {
  PageBanner,
  Button,
  Card,
  ConfirmPanel,
  EmptyState,
  ErrorState,
  FormField,
  LevelBadge,
  Skeleton,
  StatusBadge,
  Textarea,
  type HskLevel,
} from '@/shared/ui'
import { resultErrors, totalScore } from '../gradingRules'
import type { ErrorCodeOption, ResultDraft, TeacherGradingDetail } from '../types'
import { deadlineLabel } from '../useGradingDeadline'
import { ErrorCodePicker } from './ErrorCodePicker'

type Props = {
  state: ApiQueryState
  detail?: TeacherGradingDetail
  error?: ApiError
  actionError?: ApiError
  draft: ResultDraft
  /** Error codes the teacher can confirm; empty while they load or before any were imported. */
  errorCodes: readonly ErrorCodeOption[]
  seconds: number | null
  pending: boolean
  confirmation: 'complete' | 'release' | null
  onDraft: (draft: ResultDraft) => void
  onRetry: () => void
  onReview: (action: 'complete' | 'release') => void
  onClose: () => void
  onConfirm: () => void
}

function toHskLevel(level: number | null): HskLevel | null {
  if (level === null || !Number.isInteger(level) || level < 1 || level > 9) return null
  return level as HskLevel
}

export function TeacherGradingWorkspaceView(props: Props) {
  const { detail, draft } = props
  const rubric = rubricForMode(detail?.mode)
  const errors = resultErrors(draft, rubric)
  const completed = detail?.status === 'COMPLETED'
  const badgeLevel = detail ? toHskLevel(detail.hskLevel) : null

  return (
    <main className="mx-auto w-full min-w-0 max-w-content space-y-6 px-4 py-6 pb-16 sm:px-6 lg:px-8">
      <PageBanner
        subtitle="Đọc hoặc nghe bài, chấm theo rubric, gắn mã lỗi và gửi nhận xét chi tiết."
        title="Chấm bài"
      />

      <div className="flex flex-wrap items-center justify-between gap-3">
        <Button size="sm" to="/teacher/grading" variant="outline">
          <ArrowLeft aria-hidden="true" className="mr-1.5 inline-block size-4 align-middle" />
          Về hàng đợi
        </Button>
      </div>

      {props.state === 'loading' ? (
        <div className="space-y-3" role="status">
          <p className="text-body-sm text-ink-secondary">Đang tải bài cần chấm…</p>
          <Skeleton variant="card" />
        </div>
      ) : null}

      {props.state === 'error' ? (
        <div role="alert">
          <ErrorState
            message={props.error?.message ?? 'Không tải được bài cần chấm.'}
            onRetry={props.error?.status !== 403 ? props.onRetry : undefined}
            requestId={props.error?.requestId}
            title="Chưa tải được bài cần chấm"
          />
        </div>
      ) : null}

      {props.state === 'empty' ? (
        <div role="status">
          <EmptyState
            action={
              <Button to="/teacher/grading" variant="outline">
                Về hàng đợi
              </Button>
            }
            description="Bài viết này hiện không có trong danh sách cần chấm của bạn."
            icon={<FileText aria-hidden="true" className="size-5" />}
            title="Không có bài cần chấm."
          />
        </div>
      ) : null}

      {props.state === 'ready' && detail ? (
        <div className="grid min-w-0 items-start gap-6 lg:grid-cols-12">
          <div className="min-w-0 space-y-5 lg:col-span-5">
            <Card className="min-w-0 space-y-4 border-brand-100 bg-brand-50 p-5 break-words sm:p-6">
              <div className="flex flex-wrap items-start justify-between gap-3">
                <div className="flex items-center gap-3">
                  <div className="flex size-11 shrink-0 items-center justify-center rounded-pill bg-brand-100 text-brand-700">
                    <FileText aria-hidden="true" className="size-5" />
                  </div>
                  <div>
                    <div className="flex flex-wrap items-center gap-2">
                      <h2 className="text-h3 text-ink">{detail.reference}</h2>
                      {badgeLevel !== null ? <LevelBadge exact level={badgeLevel} /> : null}
                    </div>
                    <p className="text-body-sm text-ink-secondary">
                      {detail.hanziCount} chữ Hán
                      {detail.hskLevel !== null ? ` · HSK ${detail.hskLevel}` : ''}
                    </p>
                  </div>
                </div>
                <StatusBadge tone={completed ? 'success' : 'info'}>
                  {completed ? 'Đã chấm' : 'Đang chấm'}
                </StatusBadge>
              </div>

              <div className="space-y-2 border-t border-brand-100 pt-3 text-body-sm text-ink">
                <p>Nhận lúc: {displayTime(detail.assignedAt)}</p>
                <p>Hạn chấm: {displayTime(detail.gradingDeadline)}</p>
                {!completed ? (
                  <div className="flex items-center gap-2 rounded-md border border-brand-100 bg-surface px-3 py-2 font-semibold text-brand-700">
                    <Clock aria-hidden="true" className="size-4 shrink-0 text-brand-600" />
                    <span className="text-ink-secondary">Còn lại:</span>
                    <p aria-label="Thời gian chấm còn lại" role="timer">
                      {deadlineLabel(props.seconds)}
                    </p>
                  </div>
                ) : null}
                <p className="flex items-center gap-2 font-medium text-brand-700">
                  <Coins aria-hidden="true" className="size-4 shrink-0 text-brand-600" />
                  <span>Điểm trả cho bài: {detail.payoutCredits} điểm</span>
                </p>
              </div>
            </Card>

            {detail.prompt !== null ? (
              <Card className="min-w-0 space-y-2 p-5 sm:p-6">
                <h2 className="text-h3 text-ink">Đề bài</h2>
                <p className="whitespace-pre-wrap break-words text-body text-ink-secondary [overflow-wrap:anywhere]">
                  {detail.prompt}
                </p>
              </Card>
            ) : null}

            <Card className="min-w-0 space-y-3 p-5 sm:p-6">
              <div className="flex items-center justify-between gap-2 border-b border-line pb-3">
                <h2 className="text-h3 text-ink">
                  {detail.mode === 'SPEAKING' ? 'Bài nói' : 'Bài viết'}
                </h2>
                <span className="inline-flex items-center gap-1.5 rounded-pill bg-brand-50 px-3 py-1 text-caption font-semibold text-brand-700">
                  <Sparkles aria-hidden="true" className="size-3.5" />
                  {detail.hanziCount} chữ Hán
                </span>
              </div>
              {detail.mode === 'SPEAKING' ? (
                <p className="text-body-sm text-ink-secondary">
                  Học viên đọc câu dưới đây. Nghe bản ghi rồi chấm phát âm.
                </p>
              ) : null}
              <p className="rounded-md border border-brand-100 bg-brand-50 p-4 whitespace-pre-wrap break-words font-hanzi text-body text-ink [overflow-wrap:anywhere]">
                {detail.essay}
              </p>
              {detail.mode === 'SPEAKING' && detail.audioUrl ? (
                <audio
                  aria-label="Bản ghi âm của học viên"
                  className="w-full"
                  controls
                  preload="metadata"
                  src={detail.audioUrl}
                />
              ) : null}
            </Card>
          </div>

          <div className="min-w-0 lg:col-span-7">
            {completed && detail.result ? (
              <Card className="min-w-0 space-y-5 border-brand-100 bg-surface p-6 break-words [overflow-wrap:anywhere]">
                <div className="flex flex-wrap items-center justify-between gap-3 border-b border-line pb-4">
                  <div className="flex items-center gap-2.5">
                    <div className="flex size-10 shrink-0 items-center justify-center rounded-md border border-brand-100 bg-brand-50 text-brand-600">
                      <Award aria-hidden="true" className="size-5" />
                    </div>
                    <h2 className="text-h3 text-ink">Đã nộp kết quả</h2>
                  </div>
                  <p className="rounded-pill border border-brand-100 bg-brand-50 px-4 py-1 text-h2 tabular-nums text-brand-700">
                    {detail.result.score}/100
                  </p>
                </div>

                <GradingScoreBreakdown mode={detail.mode} result={detail.result} />

                <div className="space-y-2">
                  <h3 className="text-h3 text-ink">Nhận xét đã gửi</h3>
                  <p className="rounded-md border border-line bg-canvas p-4 whitespace-pre-wrap text-body text-ink">
                    {detail.result.feedback}
                  </p>
                </div>

                <div className="space-y-3">
                  <h3 className="text-h3 text-ink">Sửa lỗi đã gửi</h3>
                  {detail.result.corrections.length === 0 ? (
                    <p className="text-body-sm text-ink-secondary">Không có mục sửa lỗi.</p>
                  ) : (
                    <div className="space-y-3">
                      {detail.result.corrections.map((item, index) => (
                        <div
                          className="space-y-1.5 rounded-md border border-brand-100 bg-brand-50 p-4"
                          key={index}
                        >
                          <p className="whitespace-pre-wrap font-hanzi text-body text-ink">
                            Đã viết: {item.original}
                          </p>
                          <p className="whitespace-pre-wrap font-hanzi text-body text-brand-800">
                            Sửa thành: {item.corrected}
                          </p>
                          {item.note ? (
                            <p className="whitespace-pre-wrap text-body-sm text-ink-secondary">
                              Ghi chú: {item.note}
                            </p>
                          ) : null}
                        </div>
                      ))}
                    </div>
                  )}
                </div>
              </Card>
            ) : detail.status === 'ASSIGNED' ? (
              <>
                <Card className="min-w-0 p-5 sm:p-6">
                  <form
                    onSubmit={(event) => {
                      event.preventDefault()
                      if (!props.pending && Object.keys(errors).length === 0)
                        props.onReview('complete')
                    }}
                  >
                    <fieldset className="min-w-0 space-y-5" disabled={props.pending}>
                      <div className="flex flex-wrap items-center justify-between gap-3 border-b border-line pb-4">
                        <div className="flex items-center gap-2.5">
                          <FilePenLine aria-hidden="true" className="size-5 text-brand-600" />
                          <legend className="text-h3 text-ink">Chấm theo {rubric.label}</legend>
                        </div>
                        <p
                          className="rounded-pill border border-brand-100 bg-brand-50 px-4 py-1 text-h3 font-bold tabular-nums text-brand-700"
                          role="status"
                        >
                          Tổng điểm: {totalScore(draft, rubric)}/100
                        </p>
                      </div>
                      {rubric.note ? (
                        <p className="text-body-sm text-ink-secondary">{rubric.note}</p>
                      ) : null}

                      <div className="min-w-0 space-y-3">
                        {rubric.criteria.map((criterion) => {
                          const chosen = draft.scores[criterion.code] ?? ''
                          return (
                            <div
                              aria-label={`${criterion.code} ${criterion.label}`}
                              className="min-w-0 space-y-2 rounded-md border border-line p-3.5"
                              key={criterion.code}
                              role="radiogroup"
                            >
                              <p className="text-label text-ink">
                                {criterion.code} · {criterion.label}
                              </p>
                              <div className="flex flex-wrap gap-2">
                                {Array.from({ length: rubric.max + 1 }, (_, value) => (
                                  <button
                                    aria-checked={chosen === String(value)}
                                    className={
                                      chosen === String(value)
                                        ? 'min-h-10 min-w-10 rounded-md bg-brand-600 px-3 text-label text-surface'
                                        : 'min-h-10 min-w-10 rounded-md border border-line bg-surface px-3 text-label text-ink hover:border-brand-600'
                                    }
                                    key={value}
                                    onClick={() =>
                                      props.onDraft({
                                        ...draft,
                                        scores: {
                                          ...draft.scores,
                                          [criterion.code]: String(value),
                                        },
                                      })
                                    }
                                    role="radio"
                                    type="button"
                                  >
                                    {value}
                                  </button>
                                ))}
                              </div>
                              <p className="text-body-sm text-ink-secondary">
                                {chosen !== ''
                                  ? (levelText(criterion, Number(chosen)) ??
                                    'Mức 0: không làm hoặc lạc đề hoàn toàn.')
                                  : `Mức cao nhất: ${criterion.levels[rubric.max] ?? ''}`}
                              </p>
                            </div>
                          )
                        })}
                      </div>

                      <ErrorCodePicker
                        codes={props.errorCodes}
                        error={errors.errors}
                        onChange={(codes) => props.onDraft({ ...draft, errors: codes })}
                        selected={draft.errors}
                      />

                      <FormField
                        error={draft.feedback ? errors.feedback : undefined}
                        hint={`${codePointLength(draft.feedback)}/5.000 ký tự`}
                        htmlFor="teacher-feedback"
                        label="Nhận xét"
                        required
                      >
                        <Textarea
                          onChange={(event) =>
                            props.onDraft({ ...draft, feedback: event.target.value })
                          }
                          rows={5}
                          value={draft.feedback}
                        />
                      </FormField>

                      <section
                        aria-label="Bảng sửa lỗi"
                        className="min-w-0 space-y-4 rounded-md border border-brand-100 bg-brand-50 p-4 sm:p-5"
                      >
                        <div className="flex flex-wrap items-center justify-between gap-2">
                          <h2 className="text-h3 text-ink">
                            Sửa lỗi ({draft.corrections.length}/50)
                          </h2>
                          <Button
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
                            size="sm"
                            type="button"
                            variant="outline"
                          >
                            <Plus
                              aria-hidden="true"
                              className="mr-1 inline-block size-4 align-middle"
                            />
                            Thêm lỗi
                          </Button>
                        </div>

                        {draft.corrections.map((item, index) => (
                          <Card
                            className="min-w-0 space-y-3 border-brand-100 bg-surface p-4"
                            key={index}
                          >
                            <div className="flex flex-wrap items-center justify-between gap-2 border-b border-line pb-2">
                              <h3 className="text-h3 text-ink">Lỗi {index + 1}</h3>
                              <Button
                                onClick={() =>
                                  props.onDraft({
                                    ...draft,
                                    corrections: draft.corrections.filter((_, i) => i !== index),
                                  })
                                }
                                size="sm"
                                type="button"
                                variant="outline"
                              >
                                <Trash2
                                  aria-hidden="true"
                                  className="mr-1 inline-block size-4 align-middle text-danger"
                                />
                                Xoá lỗi {index + 1}
                              </Button>
                            </div>
                            {(['original', 'corrected', 'note'] as const).map((key) => (
                              <FormField
                                error={
                                  item[key] ? errors[`corrections.${index}.${key}`] : undefined
                                }
                                hint={`${codePointLength(item[key])}/500 ký tự`}
                                htmlFor={`correction-${index}-${key}`}
                                key={key}
                                label={`${key === 'original' ? 'Đã viết' : key === 'corrected' ? 'Sửa thành' : 'Ghi chú'} ${index + 1}`}
                                required={key !== 'note'}
                              >
                                <Textarea
                                  className={key !== 'note' ? 'font-hanzi' : undefined}
                                  onChange={(event) =>
                                    props.onDraft({
                                      ...draft,
                                      corrections: draft.corrections.map((row, i) =>
                                        i === index ? { ...row, [key]: event.target.value } : row,
                                      ),
                                    })
                                  }
                                  rows={2}
                                  value={item[key]}
                                />
                              </FormField>
                            ))}
                          </Card>
                        ))}
                        {errors.corrections ? (
                          <p className="text-body-sm text-danger" role="alert">
                            {errors.corrections}
                          </p>
                        ) : null}
                      </section>

                      {props.actionError ? (
                        <p
                          className="rounded-md border border-danger bg-danger-bg p-4 break-words text-body-sm text-danger"
                          role="alert"
                        >
                          {props.actionError.message}
                        </p>
                      ) : null}

                      <div className="flex flex-wrap items-center gap-3 border-t border-line pt-4">
                        <Button
                          disabled={Object.keys(errors).length > 0}
                          loading={props.pending}
                          type="submit"
                        >
                          Nộp kết quả
                        </Button>
                        <Button
                          onClick={() => props.onReview('release')}
                          type="button"
                          variant="outline"
                        >
                          Trả lại bài
                        </Button>
                      </div>
                    </fieldset>
                  </form>
                </Card>

                <ConfirmPanel
                  confirmLabel={
                    props.confirmation === 'release' ? 'Xác nhận trả lại' : 'Xác nhận nộp'
                  }
                  message={
                    props.confirmation === 'release'
                      ? 'Bài sẽ được trả lại theo trạng thái hiện tại trên máy chủ. Bạn sẽ rời khung chấm.'
                      : `Nộp kết quả ${totalScore(draft, rubric)}/100 cho ${detail.reference}?`
                  }
                  onCancel={props.onClose}
                  onConfirm={props.onConfirm}
                  open={props.confirmation !== null}
                  pending={props.pending}
                  title={
                    props.confirmation === 'release'
                      ? 'Xác nhận trả lại bài'
                      : 'Xác nhận nộp kết quả'
                  }
                />
              </>
            ) : (
              <Card className="border-brand-100 bg-brand-50 p-5">
                <p className="text-body-sm text-ink-secondary" role="status">
                  Bài không còn ở trạng thái đang chấm.
                </p>
              </Card>
            )}
          </div>
        </div>
      ) : null}
    </main>
  )
}
