import type { ApiError, ApiQueryState } from '@/shared/api'
import {
  Button,
  Card,
  ConfirmPanel,
  FormField,
  PageHeader,
  Select,
  SUPPORTED_HSK_LEVELS,
  Textarea,
} from '@/shared/ui'
import { codePointLength, countHanzi, trimmedEssay, validSubmission } from '../essayRules'
import type { GradingPolicy } from '../types'

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
    <div className="mx-auto min-w-0 max-w-reading space-y-5 p-4 sm:p-6">
      <PageHeader
        title="Gửi bài chấm"
        subtitle="Nhận góp ý từ giáo viên cho bài viết tiếng Trung của bạn."
      />
      <Button to="/grading" variant="outline">
        Yêu cầu của tôi
      </Button>
      {state === 'loading' ? <p role="status">Đang tải điều kiện chấm bài…</p> : null}
      {state === 'error' ? (
        <div role="alert" className="space-y-3">
          <p>{props.error?.message ?? 'Không tải được điều kiện chấm bài.'}</p>
          <Button onClick={props.onRetry}>Thử lại</Button>
        </div>
      ) : null}
      {state === 'ready' && policy ? (
        <>
          <Card className="min-w-0 space-y-2 break-words">
            <h2 className="text-h3">Điều kiện gửi bài</h2>
            <p>
              Chi phí: {policy.cost} lượt chấm hoặc {policy.cost} điểm
            </p>
            <p>
              Hạn nhận bài: {policy.claimHours} giờ sau khi gửi. Hạn chấm: {policy.gradingHours} giờ
              từ khi giáo viên nhận.
            </p>
            <p>{policy.refundTerms}</p>
          </Card>
          <form
            onSubmit={(event) => {
              event.preventDefault()
              if (validSubmission(essay, prompt, policy) && !props.pending) props.onReview()
            }}
          >
            <FormField
              htmlFor="grading-essay"
              label="Bài viết tiếng Trung"
              required
              hint={`${policy.minHanzi}–${policy.maxHanzi} chữ Hán; tối đa ${policy.maxChars} ký tự.`}
              error={fieldError(error, 'essay')}
            >
              <Textarea
                rows={9}
                value={essay}
                onChange={(event) => props.onEssay(event.target.value)}
                disabled={props.pending}
              />
            </FormField>
            <p role="status" aria-live="polite" className="mb-4 text-body-sm text-ink-secondary">
              {hanzi} chữ Hán · {length}/{policy.maxChars} ký tự
            </p>
            <FormField
              htmlFor="grading-prompt"
              label="Đề bài (tuỳ chọn)"
              error={fieldError(error, 'prompt')}
              hint={`${codePointLength(prompt)}/500 ký tự`}
            >
              <Textarea
                rows={3}
                value={prompt}
                onChange={(event) => props.onPrompt(event.target.value)}
                disabled={props.pending}
              />
            </FormField>
            <FormField
              htmlFor="grading-hsk"
              label="Cấp HSK (tuỳ chọn)"
              error={fieldError(error, 'hskLevel')}
            >
              <Select
                value={props.hsk}
                onChange={(event) => props.onHsk(event.target.value)}
                disabled={props.pending}
              >
                <option value="">Chưa chọn</option>
                {SUPPORTED_HSK_LEVELS.map((level) => (
                  <option value={level} key={level}>
                    HSK {level}
                  </option>
                ))}
              </Select>
            </FormField>
            {error ? (
              <div role="alert" className="mb-4 space-y-2 break-words text-danger">
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
            <Button
              type="submit"
              disabled={!validSubmission(essay, prompt, policy)}
              loading={props.pending}
            >
              Gửi bài
            </Button>
          </form>
          <ConfirmPanel
            open={props.confirming}
            pending={props.pending}
            title="Xác nhận gửi bài"
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
          />
        </>
      ) : null}
    </div>
  )
}
