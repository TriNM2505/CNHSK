import type { Option } from '@/features/practice'
import { Button, PageHeader, Progress, Skeleton } from '@/shared/ui'

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
    <main className="mx-auto w-full min-w-0 max-w-reading space-y-5 px-4 py-6 pb-16 sm:px-6">
      <PageHeader title="Luyện theo nút" />
      <div className="space-y-3 rounded-md border border-brand-100 bg-brand-50 p-4">
        <p className="break-words text-label text-ink">
          {kid}
          {name ? ' · ' + name : ''}
        </p>
        {state === 'ready' && total ? (
          <Progress value={position ?? 0} max={total} label={`${position}/${total}`} />
        ) : null}
        <Button to="/learn/progress" disabled={pending} variant="outline">
          Về tiến độ
        </Button>
      </div>
      {state === 'loading' && <Skeleton variant="card" />}
      {state === 'empty' && <p>Chưa có bài luyện cho nút này</p>}
      {state === 'finished' && <p role="status">Đã hoàn thành bài luyện</p>}
      {error && (
        <p role="alert" className="text-danger">
          {error}
        </p>
      )}
      {state === 'error' && onRetry && <Button onClick={onRetry}>Thử lại</Button>}
      {state === 'ready' && question && (
        <section className="min-w-0 space-y-5 rounded-md border border-line bg-surface p-6 sm:p-8">
          <h2 className="text-h3">
            Câu {position}/{total}
          </h2>
          {question.repeated && <p>Câu bạn vừa làm sai</p>}
          <p className="break-words font-hanzi text-h3" lang="zh-CN">
            {question.text}
          </p>
          <div className="grid gap-3" role="group" aria-label="Phương án">
            {question.options.map(({ key, text }) => (
              <Button
                key={key}
                variant="outline"
                className="h-auto min-h-touch justify-start whitespace-normal p-4 text-left"
                disabled={pending || !!feedback}
                onClick={() => onAnswer?.(key)}
              >
                {key}. <span className="font-hanzi">{text}</span>
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
