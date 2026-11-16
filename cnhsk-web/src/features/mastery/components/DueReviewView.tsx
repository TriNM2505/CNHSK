import { ChoiceQuestion } from '@/features/practice'
import type { AnswerResult, ServedItemView, SessionView } from '@/features/practice/types'
import { Button, Card, ErrorState, HanziText, PageHeader, Progress, Skeleton } from '@/shared/ui'
import { formatMasteryDateTime } from '../formatDateTime'
import type { FlashcardRating } from '../types'

const RATINGS: readonly { label: string; value: FlashcardRating }[] = [
  { label: 'Quên', value: 'AGAIN' },
  { label: 'Khó', value: 'HARD' },
  { label: 'Được', value: 'GOOD' },
  { label: 'Dễ', value: 'EASY' },
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

export function DueReviewView(props: DueReviewViewProps) {
  return (
    <main className="mx-auto w-full min-w-0 max-w-reading space-y-5 px-4 py-6 pb-16 sm:px-6">
      <PageHeader title={props.state === 'finished' ? 'Đã hoàn thành ôn tập' : 'Ôn tập'} />
      {props.state === 'loading' ? <Skeleton variant="card" /> : null}
      {props.state === 'error' ? (
        <ErrorState message={props.error} onRetry={props.onRetry} />
      ) : null}
      {props.state === 'empty' ? (
        <Card className="space-y-4 p-6 text-center">
          <p role="status" className="text-h3 text-ink">
            Chưa có mục đến hạn
          </p>
          <Button onClick={props.onStartAhead}>Ôn trước</Button>
        </Card>
      ) : null}
      {props.state === 'finished' ? (
        <Card className="space-y-4 p-6">
          <p role="status" className="text-h3 text-ink">
            Đã xử lý {props.answeredCount} mục
          </p>
          <p>
            Số câu chọn đúng: {props.correctChoices} / {props.gradedCount}
          </p>
          {props.summaryLoading ? <Skeleton variant="text" /> : null}
          {props.summaryError ? (
            <ErrorState message="Không tải được hạn ôn kế tiếp" onRetry={props.onRetrySummary} />
          ) : null}
          {!props.summaryLoading && !props.summaryError && props.nextDueAt ? (
            <p>Lần ôn tiếp theo: {formatMasteryDateTime(props.nextDueAt)}</p>
          ) : null}
          {!props.summaryLoading && !props.summaryError && props.nextDueAt === null ? (
            <p>Chưa có lịch ôn tiếp theo</p>
          ) : null}
          <Button to="/learn/progress" variant="outline">
            Xem tiến độ
          </Button>
        </Card>
      ) : null}
      {props.state === 'ready' ? (
        <>
          <Card className="space-y-3 border-brand-100 bg-brand-50">
            <div className="flex flex-wrap items-center justify-between gap-3">
              <div aria-live="polite" className="text-label text-ink">
                {props.remaining} mục còn lại
              </div>
              <Button disabled={props.pending} onClick={props.onPause} variant="outline">
                Tạm dừng
              </Button>
            </div>
            <Progress
              value={props.session.items.length - props.remaining}
              max={props.session.items.length}
              label={`${props.session.items.length - props.remaining}/${props.session.items.length}`}
            />
          </Card>
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
              <section aria-labelledby="review-flashcard-title" className="space-y-5 text-center">
                <HanziText className="block break-words" id="review-flashcard-title" size="lg">
                  {props.currentItem.prompt.text}
                </HanziText>
                {props.revealed ? (
                  <>
                    {props.currentItem.prompt.pinyin ? (
                      <p className="text-h3 text-brand-700">{props.currentItem.prompt.pinyin}</p>
                    ) : null}
                    {props.currentItem.prompt.sinoViet ? (
                      <p className="text-label text-ink-secondary">
                        {props.currentItem.prompt.sinoViet}
                      </p>
                    ) : null}
                    {props.currentItem.prompt.meaning ? (
                      <p className="text-body text-ink">{props.currentItem.prompt.meaning}</p>
                    ) : null}
                    {props.currentItem.prompt.examples?.length ? (
                      <ul className="space-y-2 text-body-sm text-ink-secondary">
                        {props.currentItem.prompt.examples.map((example, index) => (
                          <li key={`${example.cn ?? 'example'}-${index}`}>
                            {example.cn ? (
                              <span className="font-hanzi text-ink">{example.cn}</span>
                            ) : null}
                            {example.vi ? <span> — {example.vi}</span> : null}
                          </li>
                        ))}
                      </ul>
                    ) : null}
                    <div
                      aria-label="Đánh giá mức độ nhớ"
                      className="grid grid-cols-2 gap-3 sm:grid-cols-4"
                      role="group"
                    >
                      {RATINGS.map(({ label, value }) => (
                        <Button
                          disabled={props.pending}
                          key={value}
                          onClick={() => props.onRating(value)}
                          variant="outline"
                        >
                          {label}
                        </Button>
                      ))}
                    </div>
                  </>
                ) : (
                  <Button disabled={props.pending} onClick={props.onReveal}>
                    Hiện đáp án
                  </Button>
                )}
              </section>
            ) : (
              <ErrorState
                message={`Loại câu hỏi không được hỗ trợ: ${props.currentItem.type}`}
                onRetry={props.onSync}
              />
            )}
            {props.isChoice && props.result ? (
              <Button disabled={props.pending} onClick={props.onAdvance}>
                {props.remaining === 0 ? 'Kết thúc ôn tập' : 'Tiếp'}
              </Button>
            ) : null}
            <div className="flex flex-wrap justify-between gap-3 border-t border-line pt-4">
              {!props.currentItemAnswered ? (
                <Button disabled={props.pending} onClick={props.onSkip} variant="outline">
                  Bỏ qua
                </Button>
              ) : null}
            </div>
          </Card>
        </>
      ) : null}
    </main>
  )
}
