import { ArrowLeft, CheckCircle2, Layers3, RotateCcw } from 'lucide-react'

import type { ApiError, ApiQueryState } from '@/shared/api'
import { Button, EmptyState, ErrorState, Skeleton } from '@/shared/ui'

import { flashcardDate, flashcardError, reviewChoices } from '../flashcardForms'
import type { Card, ReviewRating, ReviewResult } from '../types'
import { DeckDetailText } from './DeckDetailParts'
import { DictionaryLandscapeArt } from './DictionaryLandscapeArt'

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

export function FlashcardReviewView(props: FlashcardReviewViewProps) {
  return (
    <section
      aria-label="Ôn bộ thẻ"
      className="deck-detail-enter mx-auto w-full min-w-0 max-w-reading space-y-4 rounded-md border border-brand-100 bg-surface p-4 shadow-sm sm:p-6"
    >
      <h2 className="inline-flex items-center gap-2 text-h3 text-ink">
        <RotateCcw aria-hidden="true" className="size-5 text-brand-600" />
        {props.ahead ? 'Ôn trước' : 'Ôn bộ này'}
      </h2>
      <p className="text-body-sm text-ink-secondary">
        Ôn thẻ chỉ đổi lịch của thẻ, không đổi mức thành thạo
      </p>
      {props.state === 'loading' ? (
        <div aria-label="Đang tải thẻ ôn">
          <Skeleton className="h-40" />
        </div>
      ) : null}
      {props.state === 'error' ? (
        <ErrorState
          title="Chưa tải được thẻ ôn"
          message={props.error ? flashcardError(props.error) : 'Vui lòng thử lại.'}
          requestId={props.error?.requestId}
          onRetry={props.onRetry}
        />
      ) : null}
      {props.state === 'empty' && !props.completed ? (
        <EmptyState
          title="Không có thẻ đến hạn"
          description="Bạn có thể ôn trước các thẻ chưa đến hạn."
          action={<Button onClick={props.onAhead}>Ôn trước</Button>}
        />
      ) : null}
      {props.completed ? (
        <p
          role="status"
          className="flex items-center gap-2 rounded-md bg-success-bg p-4 text-body text-success"
        >
          <CheckCircle2 aria-hidden="true" className="size-5" />
          Đã ôn xong lượt này.
        </p>
      ) : null}
      {props.state === 'ready' ? (
        <p
          role="status"
          className="inline-flex items-center gap-2 rounded-pill border border-brand-100 bg-brand-50 px-3 py-2 text-body-sm text-brand-700"
        >
          <Layers3 aria-hidden="true" className="size-4" />
          Còn {props.remaining} thẻ trong lượt ôn
        </p>
      ) : null}
      {props.state === 'ready' && props.card ? (
        <>
          <div
            key={`${props.card.id}:${props.flipped}`}
            className="deck-detail-enter relative isolate min-w-0 overflow-hidden rounded-md border border-brand-100 bg-brand-50 p-5 sm:p-6"
          >
            <DictionaryLandscapeArt bridge />
            <div className="relative z-base space-y-3">
              <h3 className="text-label text-ink">Mặt trước</h3>
              <p className="whitespace-pre-wrap break-words text-h2 text-ink">
                <DeckDetailText text={props.card.front} />
              </p>
              {props.flipped ? (
                <>
                  <h3 className="text-label text-ink">Mặt sau</h3>
                  <p className="whitespace-pre-wrap break-words text-body text-ink">
                    <DeckDetailText text={props.card.back} />
                  </p>
                </>
              ) : (
                <Button
                  className="w-full rounded-pill sm:w-auto"
                  disabled={props.pending}
                  onClick={props.onFlip}
                >
                  Lật thẻ
                </Button>
              )}
            </div>
          </div>
          <div className="grid grid-cols-2 gap-2 sm:grid-cols-4">
            {reviewChoices.map(({ rating, label }) => (
              <Button
                key={rating}
                variant="outline"
                disabled={!props.flipped || props.pending}
                onClick={() => props.onRate(rating)}
              >
                {label}
              </Button>
            ))}
          </div>
        </>
      ) : null}
      {props.ratingError ? (
        <div role="alert">
          <ErrorState
            title="Chưa ghi nhận đánh giá"
            message={flashcardError(props.ratingError)}
            requestId={props.ratingError.requestId}
          />
          <p className="mt-2 text-body-sm text-ink-secondary">
            Thẻ vẫn giữ nguyên. Bạn có thể chọn mức đánh giá để thử lại.
          </p>
        </div>
      ) : null}
      {props.result ? (
        <p role="status" className="text-body-sm text-success">
          Lần ôn kế tiếp:{' '}
          <time dateTime={props.result.nextReviewAt}>
            {flashcardDate(props.result.nextReviewAt)}
          </time>{' '}
          · Sau {props.result.intervalDays} ngày.
        </p>
      ) : null}
      <Button variant="ghost" disabled={props.pending} onClick={props.onClose}>
        <ArrowLeft aria-hidden="true" className="mr-2 inline-block size-4 align-middle" />
        Về bộ thẻ
      </Button>
    </section>
  )
}
