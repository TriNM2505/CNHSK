import { Award, BookOpen, Clock, Coins, FilePenLine, ShieldCheck, Sparkles } from 'lucide-react'

import type { ApiError, ApiQueryState } from '@/shared/api'
import {
  Button,
  Card,
  ConfirmPanel,
  ErrorState,
  FormField,
  Select,
  Skeleton,
  SUPPORTED_HSK_LEVELS,
  Textarea,
} from '@/shared/ui'
import { codePointLength, countHanzi, trimmedEssay, validSubmission } from '../essayRules'
import type { GradingPolicy } from '../types'
import { InkLandscapeBanner } from './InkLandscapeBanner'

type Props = {
  state: ApiQueryState
  policy?: GradingPolicy
  error?: ApiError
  submitError?: ApiError
  essay: string
  prompt: string
  hsk: string
  pending: boolean
  confirming: boolean
  onEssay: (value: string) => void
  onPrompt: (value: string) => void
  onHsk: (value: string) => void
  onRetry: () => void
  onReview: () => void
  onCancel: () => void
  onSend: () => void
}

function fieldError(error: ApiError | undefined, field: string): string | undefined {
  if (!Array.isArray(error?.details)) return undefined
  const entry: unknown = error.details.find(
    (item: unknown) =>
      typeof item === 'object' && item !== null && 'field' in item && item.field === field,
  )
  return typeof entry === 'object' &&
    entry !== null &&
    'message' in entry &&
    typeof entry.message === 'string'
    ? entry.message
    : undefined
}

export function GradingSubmissionView(props: Props) {
  const { state, policy, essay, prompt, submitError: error } = props
  const hanzi = countHanzi(essay),
    length = codePointLength(trimmedEssay(essay))
  const details: unknown = error?.details
  const serverCount =
    typeof details === 'object' &&
    details !== null &&
    'hanziCount' in details &&
    typeof details.hanziCount === 'number'
      ? details.hanziCount
      : undefined
  return (
    <main className="mx-auto w-full min-w-0 max-w-content space-y-6 px-4 py-6 pb-16 sm:px-6 lg:px-8">
      <InkLandscapeBanner
        subtitle="Nhận góp ý từ giáo viên cho bài viết tiếng Trung của bạn."
        title="Gửi bài chấm"
      />

      {state === 'loading' ? (
        <div className="space-y-3" role="status">
          <p className="text-body-sm text-ink-secondary">Đang tải điều kiện chấm bài…</p>
          <Skeleton variant="card" />
        </div>
      ) : null}

      {state === 'error' ? (
        <div role="alert">
          <ErrorState
            message={props.error?.message ?? 'Không tải được điều kiện chấm bài.'}
            onRetry={props.onRetry}
            requestId={props.error?.requestId}
            title="Chưa tải được điều kiện chấm bài"
          />
        </div>
      ) : null}

      {state === 'ready' && policy ? (
        <>
          <div className="grid min-w-0 gap-4 sm:grid-cols-2">
            <Card className="flex min-w-0 items-start justify-between gap-4 border-brand-100 bg-brand-50 p-5">
              <div className="flex min-w-0 items-start gap-4">
                <div className="flex size-12 shrink-0 items-center justify-center rounded-md border border-brand-100 bg-surface text-brand-600">
                  <BookOpen aria-hidden="true" className="size-6" />
                </div>
                <div className="min-w-0">
                  <p className="text-h2 tabular-nums text-ink">
                    {policy.minHanzi}–{policy.maxHanzi} chữ Hán
                  </p>
                  <p className="mt-0.5 text-label text-ink">Độ dài bài viết tiêu chuẩn</p>
                  <p className="mt-0.5 text-body-sm text-ink-secondary">
                    Tối đa {policy.maxChars} ký tự cho mỗi bài gửi chấm
                  </p>
                </div>
              </div>
              <span className="shrink-0 rounded-pill bg-brand-100 px-3 py-1 text-caption font-semibold text-brand-700">
                Quy chuẩn
              </span>
            </Card>

            <Card className="flex min-w-0 items-start justify-between gap-4 border-brand-100 bg-brand-50 p-5">
              <div className="flex min-w-0 items-start gap-4">
                <div className="flex size-12 shrink-0 items-center justify-center rounded-md border border-brand-100 bg-surface text-brand-600">
                  <Award aria-hidden="true" className="size-6" />
                </div>
                <div className="min-w-0">
                  <p className="text-h2 tabular-nums text-ink">100 điểm</p>
                  <p className="mt-0.5 text-label text-ink">Chấm theo 4 tiêu chí chuyên môn</p>
                  <p className="mt-0.5 text-body-sm text-ink-secondary">
                    Kèm nhận xét tổng quan và sửa lỗi từng câu
                  </p>
                </div>
              </div>
              <span className="shrink-0 rounded-pill bg-success-bg px-3 py-1 text-caption font-semibold text-success">
                Giáo viên chấm
              </span>
            </Card>
          </div>

          <div className="grid min-w-0 items-start gap-6 lg:grid-cols-12">
            <Card className="min-w-0 space-y-5 p-6 sm:p-7 lg:col-span-8">
              <div className="flex flex-wrap items-center justify-between gap-2 border-b border-line pb-3">
                <span className="inline-flex items-center gap-1.5 rounded-pill bg-brand-50 px-3 py-1 text-caption font-semibold text-brand-700">
                  <Sparkles aria-hidden="true" className="size-3.5" />
                  Soạn bài viết
                </span>
                <p
                  aria-live="polite"
                  className="text-body-sm font-semibold text-ink-secondary"
                  role="status"
                >
                  {hanzi} chữ Hán · {length}/{policy.maxChars} ký tự
                </p>
              </div>

              <form
                onSubmit={(event) => {
                  event.preventDefault()
                  if (validSubmission(essay, prompt, policy) && !props.pending) props.onReview()
                }}
              >
                <FormField
                  error={fieldError(error, 'essay')}
                  hint={`${policy.minHanzi}–${policy.maxHanzi} chữ Hán; tối đa ${policy.maxChars} ký tự.`}
                  htmlFor="grading-essay"
                  label="Bài viết tiếng Trung"
                  required
                >
                  <Textarea
                    className="font-hanzi"
                    disabled={props.pending}
                    onChange={(event) => props.onEssay(event.target.value)}
                    rows={10}
                    value={essay}
                  />
                </FormField>

                <div className="grid gap-x-4 sm:grid-cols-3">
                  <div className="sm:col-span-2">
                    <FormField
                      error={fieldError(error, 'prompt')}
                      hint={`${codePointLength(prompt)}/500 ký tự`}
                      htmlFor="grading-prompt"
                      label="Đề bài (tuỳ chọn)"
                    >
                      <Textarea
                        disabled={props.pending}
                        onChange={(event) => props.onPrompt(event.target.value)}
                        rows={3}
                        value={prompt}
                      />
                    </FormField>
                  </div>

                  <div>
                    <FormField
                      error={fieldError(error, 'hskLevel')}
                      htmlFor="grading-hsk"
                      label="Cấp HSK (tuỳ chọn)"
                    >
                      <Select
                        disabled={props.pending}
                        onChange={(event) => props.onHsk(event.target.value)}
                        value={props.hsk}
                      >
                        <option value="">Chưa chọn</option>
                        {SUPPORTED_HSK_LEVELS.map((level) => (
                          <option key={level} value={level}>
                            HSK {level}
                          </option>
                        ))}
                      </Select>
                    </FormField>
                  </div>
                </div>

                {error ? (
                  <div
                    className="mb-4 space-y-2 rounded-md border border-danger bg-danger-bg p-4 break-words text-body-sm text-danger"
                    role="alert"
                  >
                    <p>{error.message}</p>
                    {error.errorCode === 'GRADING_INVALID_ESSAY' && serverCount !== undefined ? (
                      <p>Máy chủ đếm được {serverCount} chữ Hán.</p>
                    ) : null}
                    {error.errorCode === 'INSUFFICIENT_CREDITS' ? (
                      <Button to="/account/credits" variant="outline">
                        Mua gói / Nạp điểm
                      </Button>
                    ) : null}
                  </div>
                ) : null}

                <div className="mt-6 flex flex-wrap items-center justify-between gap-3 border-t border-line pt-4">
                  <Button
                    disabled={!validSubmission(essay, prompt, policy)}
                    loading={props.pending}
                    type="submit"
                  >
                    Gửi bài
                  </Button>
                  <Button size="sm" to="/grading" variant="outline">
                    Yêu cầu của tôi
                  </Button>
                </div>
              </form>
            </Card>

            <div className="min-w-0 space-y-5 lg:col-span-4">
              <Card className="min-w-0 space-y-4 border-brand-100 bg-brand-50 p-5 break-words sm:p-6">
                <div className="flex items-center gap-3">
                  <div className="flex size-10 shrink-0 items-center justify-center rounded-pill bg-brand-100 text-brand-700">
                    <FilePenLine aria-hidden="true" className="size-5" />
                  </div>
                  <div>
                    <h2 className="text-h3 text-ink">Điều kiện gửi bài</h2>
                    <p className="text-caption text-ink-secondary">
                      Kiểm tra quy định trước khi gửi bài viết
                    </p>
                  </div>
                </div>

                <div className="space-y-3 border-t border-brand-100 pt-3 text-body-sm text-ink">
                  <p className="flex items-start gap-2.5">
                    <Coins aria-hidden="true" className="mt-0.5 size-4 shrink-0 text-brand-700" />
                    <span>
                      Chi phí: {policy.cost} lượt chấm hoặc {policy.cost} điểm
                    </span>
                  </p>
                  <p className="flex items-start gap-2.5">
                    <Clock aria-hidden="true" className="mt-0.5 size-4 shrink-0 text-brand-700" />
                    <span>
                      Hạn nhận bài: {policy.claimHours} giờ sau khi gửi. Hạn chấm:{' '}
                      {policy.gradingHours} giờ từ khi giáo viên nhận.
                    </span>
                  </p>
                  <p className="flex items-start gap-2.5 text-ink-secondary">
                    <ShieldCheck
                      aria-hidden="true"
                      className="mt-0.5 size-4 shrink-0 text-brand-700"
                    />
                    <span>{policy.refundTerms}</span>
                  </p>
                </div>
              </Card>

              <Card className="min-w-0 space-y-3 border-brand-100 bg-surface p-5">
                <h2 className="text-h3 text-ink">Thang điểm đánh giá</h2>
                <p className="text-body-sm text-ink-secondary">
                  Giáo viên chấm chi tiết từng phần trên thang 25 điểm:
                </p>
                <div className="grid grid-cols-2 gap-2.5 pt-1">
                  {[
                    ['Ngữ pháp', '0–25 điểm'],
                    ['Từ vựng', '0–25 điểm'],
                    ['Mạch lạc', '0–25 điểm'],
                    ['Chữ viết', '0–25 điểm'],
                  ].map(([title, range]) => (
                    <div
                      className="rounded-md border border-brand-100 bg-brand-50 p-3"
                      key={title}
                    >
                      <p className="text-label text-ink">{title}</p>
                      <p className="mt-0.5 text-caption font-semibold text-brand-700">{range}</p>
                    </div>
                  ))}
                </div>
              </Card>
            </div>
          </div>

          <ConfirmPanel
            confirmLabel="Xác nhận gửi bài"
            message={
              <>
                <p>
                  Bài có {hanzi} chữ Hán. Gửi bài sẽ sử dụng {policy.cost} lượt chấm hoặc{' '}
                  {policy.cost} điểm.
                </p>
                <p className="mt-2">{policy.refundTerms}</p>
              </>
            }
            onCancel={props.onCancel}
            onConfirm={props.onSend}
            open={props.confirming}
            pending={props.pending}
            title="Xác nhận gửi bài"
          />
        </>
      ) : null}

      {state !== 'ready' ? (
        <div>
          <Button to="/grading" variant="outline">
            Yêu cầu của tôi
          </Button>
        </div>
      ) : null}
    </main>
  )
}
