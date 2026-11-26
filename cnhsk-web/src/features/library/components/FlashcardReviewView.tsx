import { ArrowLeft, CheckCircle2, Layers3 } from 'lucide-react'

import type { ApiError, ApiQueryState } from '@/shared/api'
import { Button, EmptyState, ErrorState, Skeleton } from '@/shared/ui'

import { flashcardDate, flashcardError, reviewChoices } from '../flashcardForms'
import type { Card, ReviewRating, ReviewResult } from '../types'
import { FlashcardStage } from './FlashcardStage'

export interface FlashcardReviewViewProps {
  state: ApiQueryState
  card?: Card
  remaining: number
  flipped: boolean
  pending: boolean
  error?: ApiError
  ratingError?: ApiError
  result?: ReviewResult
  completed: boolean
  ahead: boolean
  onFlip: () => void
  onRate: (rating: ReviewRating) => void
  onRetry: () => void
  onAhead: () => void
  onClose: () => void
}

/** How hard each answer was, from forgotten to easy, tinted so the scale reads at a glance. */
const ratingTone: Record<ReviewRating, string> = {
  AGAIN: 'border-danger text-danger hover:bg-danger-bg',
  HARD: 'border-warning text-warning hover:bg-warning-bg',
  GOOD: 'border-brand-600 text-brand-700 hover:bg-brand-50',
  EASY: 'border-success text-success hover:bg-success-bg',
}

export function FlashcardReviewView(props: FlashcardReviewViewProps) {
  return (
    <section aria-label="Ôn bộ thẻ" className="deck-detail-enter min-w-0 space-y-4">
      <div className="flex min-w-0 flex-wrap items-center justify-between gap-3">
        <div className="min-w-0 space-y-1">
          <h2 className="text-h3 text-ink">{props.ahead ? 'Ôn trước' : 'Ôn bộ này'}</h2>
          <p className="text-body-sm text-ink-secondary">Ôn thẻ chỉ đổi lịch của thẻ, không đổi mức thành thạo</p>
        </div>
        {props.state === 'ready' ? (
          <p
            className="inline-flex items-center gap-2 rounded-pill bg-brand-50 px-3 py-2 text-label text-brand-700"
            role="status"
          >
            <Layers3 aria-hidden="true" className="size-4" />
            Còn {props.remaining} thẻ trong lượt ôn
          </p>
        ) : null}
      </div>
      {props.state === 'loading' ? (
        <div aria-label="Đang tải thẻ ôn">
          <Skeleton className="h-72 sm:h-96" />
        </div>
      ) : null}
      {props.state === 'error' ? (
        <ErrorState
          message={props.error ? flashcardError(props.error) : 'Vui lòng thử lại.'}
          onRetry={props.onRetry}
          requestId={props.error?.requestId}
          title="Chưa tải được thẻ ôn"
        />
      ) : null}
      {props.state === 'empty' && !props.completed ? (
        <EmptyState
          action={<Button onClick={props.onAhead}>Ôn trước</Button>}
          description="Bạn có thể ôn trước các thẻ chưa đến hạn."
          title="Không có thẻ đến hạn"
        />
      ) : null}
      {props.completed ? (
        <p className="flex items-center gap-2 rounded-lg bg-success-bg p-5 text-body text-success" role="status">
          <CheckCircle2 aria-hidden="true" className="size-5" />
          Đã ôn xong lượt này.
        </p>
      ) : null}
      {props.state === 'ready' && props.card ? (
        <>
          <FlashcardStage
            back={props.card.back}
            disabled={props.pending || props.flipped}
            flipped={props.flipped}
            front={props.card.front}
            hideBackUntilFlipped
            key={props.card.id}
            label="Lật thẻ"
            onFlip={props.onFlip}
          />
          <div className="space-y-2">
            <p className="text-center text-body-sm text-ink-secondary">
              {props.flipped ? 'Bạn nhớ thẻ này tới đâu?' : 'Nhớ lại mặt sau rồi bấm vào thẻ để lật.'}
            </p>
            <div className="grid grid-cols-2 gap-2 sm:grid-cols-4">
              {reviewChoices.map(({ rating, label }) => (
                <button
                  className={`min-h-touch rounded-md border-2 bg-surface px-4 text-label transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus disabled:border-line disabled:text-ink-muted disabled:hover:bg-surface ${ratingTone[rating]}`}
                  disabled={!props.flipped || props.pending}
                  key={rating}
                  onClick={() => props.onRate(rating)}
                  type="button"
                >
                  {label}
                </button>
              ))}
            </div>
          </div>
        </>
      ) : null}
      {props.ratingError ? (
        <div role="alert">
          <ErrorState
            message={flashcardError(props.ratingError)}
            requestId={props.ratingError.requestId}
            title="Chưa ghi nhận đánh giá"
          />
          <p className="mt-2 text-body-sm text-ink-secondary">
            Thẻ vẫn giữ nguyên. Bạn có thể chọn mức đánh giá để thử lại.
          </p>
        </div>
      ) : null}
      {props.result ? (
        <p className="text-center text-body-sm text-success" role="status">
          Lần ôn kế tiếp: <time dateTime={props.result.nextReviewAt}>{flashcardDate(props.result.nextReviewAt)}</time>{' '}
          · Sau {props.result.intervalDays} ngày.
        </p>
      ) : null}
      <Button disabled={props.pending} onClick={props.onClose} variant="ghost">
        <ArrowLeft aria-hidden="true" className="mr-2 inline-block size-4 align-middle" />
        Về bộ thẻ
      </Button>
    </section>
  )
}
