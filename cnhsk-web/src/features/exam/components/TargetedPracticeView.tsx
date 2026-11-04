import type { Option } from '@/features/practice'
import { Button } from '@/shared/ui/Button'

export type TargetedQuestion = {
  text: string
  options: readonly Option[]
  repeated: boolean
}

export type TargetedFeedback = {
  correct: boolean | null | undefined
  correctAnswer: string | null | undefined
  explanation: string | null | undefined
}

type Props = {
  kid: string
  name?: string
  state: 'loading' | 'empty' | 'error' | 'ready' | 'finished'
  question?: TargetedQuestion
  position?: number
  total?: number
  feedback?: TargetedFeedback
  pending?: boolean
  error?: string
  onAnswer?: (key: string) => void
  onContinue?: () => void
  onRetry?: () => void
}

export function TargetedPracticeView({
  kid,
  name,
  state,
  question,
  position,
  total,
  feedback,
  pending,
  error,
  onAnswer,
  onContinue,
  onRetry,
}: Props) {
  return (
    <main className="mx-auto max-w-content space-y-5 p-4 sm:p-6">
      <h1 className="text-h1-sm sm:text-h1">Luyện theo nút</h1>
      <p>
        {kid}
        {name ? ' · ' + name : ''}
      </p>
      {state === 'loading' && <p role="status">Đang tải bài luyện…</p>}
      {state === 'empty' && <p>Chưa có bài luyện cho nút này</p>}
      {state === 'finished' && <p role="status">Đã hoàn thành bài luyện</p>}
      {error && (
        <p role="alert" className="text-danger">
          {error}
        </p>
      )}
      {state === 'error' && onRetry && <Button onClick={onRetry}>Thử lại</Button>}
      {state === 'ready' && question && (
        <section className="space-y-3 rounded-md border border-line bg-surface p-5">
          <h2 className="text-h3">
            Câu {position}/{total}
          </h2>
          {question.repeated && <p>Câu bạn vừa làm sai</p>}
          <p>{question.text}</p>
          <div className="grid gap-2" role="group" aria-label="Phương án">
            {question.options.map(({ key, text }) => (
              <Button
                key={key}
                variant="outline"
                disabled={pending || !!feedback}
                onClick={() => onAnswer?.(key)}
              >
                {key}. {text}
              </Button>
            ))}
          </div>
          {feedback && (
            <>
              <div role="status">
                <p>
                  {feedback.correct === true
                    ? 'Chính xác'
                    : feedback.correct === false
                      ? 'Chưa đúng'
                      : 'Đã ghi nhận'}
                </p>
                {feedback.correctAnswer && <p>Đáp án đúng: {feedback.correctAnswer}</p>}
                {feedback.explanation && <p>{feedback.explanation}</p>}
              </div>
              <Button disabled={pending} onClick={onContinue}>
                {position === total ? 'Hoàn thành' : 'Câu tiếp theo'}
              </Button>
            </>
          )}
        </section>
      )}
    </main>
  )
}
