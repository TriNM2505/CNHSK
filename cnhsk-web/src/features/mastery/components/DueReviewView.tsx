import { ArrowRight } from 'lucide-react'

import { ChoiceQuestion } from '@/features/practice'
import type { AnswerResult, ServedItemView, SessionView } from '@/features/practice/types'
import { Button, Card, ErrorState, HanziText, PageBanner, Progress, Skeleton } from '@/shared/ui'

import { formatMasteryDateTime } from '../formatDateTime'
import type { FlashcardRating } from '../types'

const RATINGS: readonly { label: string; value: FlashcardRating; toneClass: string }[] = [
  {
    label: 'Quên',
    value: 'AGAIN',
    toneClass: 'border-danger bg-danger-bg text-danger hover:border-danger hover:text-danger',
  },
  {
    label: 'Khó',
    value: 'HARD',
    toneClass: 'border-warning bg-warning-bg text-warning hover:border-warning hover:text-warning',
  },
  {
    label: 'Được',
    value: 'GOOD',
    toneClass: 'border-brand-100 bg-brand-50 text-brand-700 hover:border-brand-600',
  },
  {
    label: 'Dễ',
    value: 'EASY',
    toneClass: 'border-success bg-success-bg text-success hover:border-success hover:text-success',
  },
]

export type DueReviewViewProps =
  | { state: 'loading' }
  | { state: 'error'; error: string; onRetry: () => void }
  | { state: 'empty'; onStartAhead: () => void }
  | {
      state: 'finished'
      answeredCount: number
      correctChoices: number
      gradedCount: number
      summaryLoading: boolean
      summaryError: boolean
      nextDueAt: string | null
      onRetrySummary: () => void
    }
  | {
      state: 'ready'
      session: SessionView
      currentItem: ServedItemView
      remaining: number
      pending: boolean
      result?: AnswerResult
      selectedAnswer?: string | null
      currentItemAnswered: boolean
      isChoice: boolean
      revealed: boolean
      error?: string
      onAnswer: (answer: string) => Promise<unknown>
      onRating: (rating: FlashcardRating) => void
      onReveal: () => void
      onAdvance: () => void
      onSkip: () => void
      onPause: () => void
      onSync: () => void
    }

type ReadyProps = Extract<DueReviewViewProps, { state: 'ready' }>

/** Thanh phiên ôn: tiêu đề, vị trí câu, tiến độ và nút tạm dừng. */
function SessionBar(props: ReadyProps) {
  const total = props.session.items.length
  const done = total - props.remaining

  return (
    <header className="space-y-3">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div className="flex min-w-0 items-baseline gap-3">
          <h1 className="text-h2 text-ink">Ôn tập</h1>
          <p aria-live="polite" className="text-body-sm text-ink-secondary">
            {props.remaining} mục còn lại
          </p>
        </div>
        <Button disabled={props.pending} onClick={props.onPause} size="sm" variant="outline">
          Tạm dừng
        </Button>
      </div>
      <Progress label={`${done}/${total}`} max={total} value={done} />
    </header>
  )
}

function FlashcardReview(props: ReadyProps) {
  const prompt = props.currentItem.prompt

  return (
    <section aria-labelledby="review-flashcard-title" className="space-y-6 text-center">
      <HanziText className="block break-words py-4" id="review-flashcard-title" size="lg">
        {prompt.text}
      </HanziText>
      {props.revealed ? (
        <div className="space-y-6">
          <div className="space-y-1 border-t border-line pt-5">
            {prompt.pinyin ? <p className="text-h2 text-brand-700">{prompt.pinyin}</p> : null}
            {prompt.sinoViet ? (
              <p className="text-label text-ink-secondary">{prompt.sinoViet}</p>
            ) : null}
            {prompt.meaning ? <p className="text-h3 text-ink">{prompt.meaning}</p> : null}
            {prompt.examples?.length ? (
              <ul className="mx-auto max-w-reading space-y-1 pt-3 text-body-sm text-ink-secondary">
                {prompt.examples.map((example, index) => (
                  <li key={`${example.cn ?? 'example'}-${index}`}>
                    {example.cn ? <span className="font-hanzi text-ink">{example.cn}</span> : null}
                    {example.vi ? <span> — {example.vi}</span> : null}
                  </li>
                ))}
              </ul>
            ) : null}
          </div>

          <div className="space-y-2">
            <p className="text-body-sm text-ink-secondary">Bạn nhớ từ này tới đâu?</p>
            <div
              aria-label="Đánh giá mức độ nhớ"
              className="grid grid-cols-2 gap-3 sm:grid-cols-4"
              role="group"
            >
              {RATINGS.map(({ label, value, toneClass }) => (
                <Button
                  className={toneClass}
                  disabled={props.pending}
                  key={value}
                  onClick={() => props.onRating(value)}
                  variant="outline"
                >
                  {label}
                </Button>
              ))}
            </div>
          </div>
        </div>
      ) : (
        <Button disabled={props.pending} onClick={props.onReveal}>
          Hiện đáp án
        </Button>
      )}
    </section>
  )
}

function ReviewSession(props: ReadyProps) {
  return (
    <>
      <SessionBar {...props} />

      {props.error ? <ErrorState message={props.error} onRetry={props.onSync} /> : null}

      <Card className="min-w-0 space-y-6 p-6 sm:p-8">
        {props.isChoice ? (
          <ChoiceQuestion
            feedbackMode={props.session.feedback}
            item={props.currentItem}
            onAnswer={props.onAnswer}
            pending={props.pending}
            result={props.result}
            selectedAnswer={props.selectedAnswer}
          />
        ) : props.currentItem.type === 'FLASHCARD_RATING' ? (
          <FlashcardReview {...props} />
        ) : (
          <ErrorState
            message={`Loại câu hỏi không được hỗ trợ: ${props.currentItem.type}`}
            onRetry={props.onSync}
          />
        )}

        {(props.isChoice && props.result) || !props.currentItemAnswered ? (
          <div className="flex flex-wrap items-center justify-between gap-3 border-t border-line pt-4">
            {!props.currentItemAnswered ? (
              <Button disabled={props.pending} onClick={props.onSkip} variant="ghost">
                Bỏ qua
              </Button>
            ) : null}
            {props.isChoice && props.result ? (
              <Button className="ml-auto" disabled={props.pending} onClick={props.onAdvance}>
                {props.remaining === 0 ? 'Kết thúc ôn tập' : 'Tiếp'}
                <ArrowRight aria-hidden="true" className="ml-1.5 inline-block size-4 align-middle" />
              </Button>
            ) : null}
          </div>
        ) : null}
      </Card>
    </>
  )
}

export function DueReviewView(props: DueReviewViewProps) {
  if (props.state === 'ready') {
    return (
      <main className="mx-auto w-full min-w-0 max-w-reading space-y-5 px-4 py-6 pb-16 sm:px-6">
        <ReviewSession {...props} />
      </main>
    )
  }

  const finished = props.state === 'finished'

  return (
    <main className="mx-auto w-full min-w-0 max-w-reading space-y-5 px-4 py-6 pb-16 sm:px-6">
      <PageBanner
        subtitle={
          finished
            ? 'Kết quả phiên ôn tập vừa hoàn thành.'
            : 'Ôn lại các từ đã đến lịch để nhớ lâu hơn.'
        }
        title={finished ? 'Đã hoàn thành ôn tập' : 'Ôn tập'}
      />

      {props.state === 'loading' ? <Skeleton variant="card" /> : null}

      {props.state === 'error' ? <ErrorState message={props.error} onRetry={props.onRetry} /> : null}

      {props.state === 'empty' ? (
        <Card className="space-y-4 p-6 text-center sm:p-8">
          <div className="space-y-1">
            <p className="text-h3 text-ink" role="status">
              Chưa có mục đến hạn
            </p>
            <p className="text-body-sm text-ink-secondary">
              Bạn đã ôn hết các mục tới lịch. Muốn nhớ chắc hơn thì ôn trước lịch.
            </p>
          </div>
          <Button onClick={props.onStartAhead}>Ôn trước</Button>
        </Card>
      ) : null}

      {props.state === 'finished' ? (
        <Card className="space-y-5 p-6 sm:p-8">
          <div className="space-y-1">
            <p className="text-h3 text-ink" role="status">
              Đã xử lý {props.answeredCount} mục
            </p>
            <p className="text-body-sm text-ink-secondary">
              Số câu chọn đúng: {props.correctChoices} / {props.gradedCount}
            </p>
          </div>

          {props.summaryLoading ? <Skeleton variant="text" /> : null}
          {props.summaryError ? (
            <ErrorState message="Không tải được hạn ôn kế tiếp" onRetry={props.onRetrySummary} />
          ) : null}
          {!props.summaryLoading && !props.summaryError ? (
            <p className="border-t border-line pt-4 text-body-sm text-ink">
              {props.nextDueAt
                ? `Lần ôn tiếp theo: ${formatMasteryDateTime(props.nextDueAt)}`
                : 'Chưa có lịch ôn tiếp theo'}
            </p>
          ) : null}

          <div className="flex flex-wrap gap-3">
            <Button to="/learn/progress" variant="outline">
              Xem tiến độ
            </Button>
            <Button to="/learn" variant="ghost">
              Về trang học tập
            </Button>
          </div>
        </Card>
      ) : null}
    </main>
  )
}
