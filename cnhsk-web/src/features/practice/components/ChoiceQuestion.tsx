import { CheckCircle2, Circle, CircleAlert, XCircle } from 'lucide-react'
import { useEffect, useState } from 'react'

import type { AnswerResult, FeedbackMode, ServedItemView } from '@/features/practice/types'
import { cn } from '@/shared/lib/cn'
import { HanziText } from '@/shared/ui'

export interface ChoiceQuestionProps {
  item: ServedItemView
  feedbackMode: FeedbackMode
  selectedAnswer?: string | null
  result?: AnswerResult | null
  pending?: boolean
  onAnswer: (answer: string) => Promise<unknown> | unknown
}

export function ChoiceQuestion({
  item,
  feedbackMode,
  selectedAnswer,
  result,
  pending = false,
  onAnswer,
}: ChoiceQuestionProps) {
  const [submittedAnswer, setSubmittedAnswer] = useState<string | null>(selectedAnswer ?? null)
  const options = item.options ?? []
  const locked = feedbackMode !== 'DEFERRED' && (submittedAnswer !== null || Boolean(result))

  useEffect(() => {
    setSubmittedAnswer(selectedAnswer ?? null)
  }, [item.no, selectedAnswer])

  const choose = async (answer: string) => {
    if (pending || locked) return
    const previous = submittedAnswer
    setSubmittedAnswer(answer)
    try {
      await onAnswer(answer)
    } catch {
      setSubmittedAnswer(previous)
    }
  }

  return (
    <section aria-labelledby={`practice-question-${item.no}`} className="space-y-5">
      <div className="text-center">
        {item.prompt.text ? (
          <HanziText className="block" id={`practice-question-${item.no}`} size="lg">
            {item.prompt.text}
          </HanziText>
        ) : (
          <h2 className="text-h2 text-ink" id={`practice-question-${item.no}`}>
            Chọn đáp án phù hợp
          </h2>
        )}
        {item.prompt.sentence ? (
          <p className="mt-3 font-hanzi text-h2 text-ink" lang="zh-CN">
            {item.prompt.sentence}
          </p>
        ) : null}
        {item.prompt.hint ? (
          <p className="mt-2 text-body-sm text-ink-secondary">Gợi ý: {item.prompt.hint}</p>
        ) : null}
      </div>

      <div aria-label="Các phương án" className="grid gap-3 sm:grid-cols-2" role="group">
        {options.map((option) => {
          const selected = submittedAnswer === option.key || selectedAnswer === option.key
          return (
            <button
              aria-pressed={selected}
              className={cn(
                'flex min-h-touch w-full items-start gap-3 rounded-md border bg-surface p-4 text-left text-body text-ink transition-colors',
                'focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus focus-visible:ring-offset-2',
                selected ? 'border-brand-600 bg-brand-50' : 'border-line hover:border-brand-600',
                'disabled:cursor-not-allowed disabled:opacity-70',
              )}
              disabled={pending || locked}
              key={option.key}
              onClick={() => void choose(option.key)}
              type="button"
            >
              <span className="flex size-7 shrink-0 items-center justify-center rounded-pill border border-line text-label text-ink-secondary">
                {option.key}
              </span>
              <span className="min-w-0 flex-1 break-words">{option.text}</span>
              {selected ? (
                <CheckCircle2
                  aria-hidden="true"
                  className="mt-0.5 size-5 shrink-0 text-brand-700"
                />
              ) : (
                <Circle aria-hidden="true" className="mt-0.5 size-5 shrink-0 text-ink-muted" />
              )}
            </button>
          )
        })}
      </div>

      {feedbackMode !== 'DEFERRED' && result ? (
        <AnswerFeedback options={options} result={result} />
      ) : null}
    </section>
  )
}

function AnswerFeedback({
  options,
  result,
}: {
  options: ServedItemView['options']
  result: AnswerResult
}) {
  if (result.recorded) {
    return (
      <p className="flex items-start gap-2 rounded-md bg-info-bg p-3 text-body-sm text-info">
        <CheckCircle2 aria-hidden="true" className="mt-0.5 size-5 shrink-0" />
        <span>Đã ghi nhận câu trả lời.</span>
      </p>
    )
  }

  if (result.correct === null || result.correct === undefined) return null

  return (
    <div
      className={cn(
        'rounded-md p-4 text-body-sm',
        result.correct ? 'bg-success-bg text-success' : 'bg-danger-bg text-danger',
      )}
      role="status"
    >
      <p className="flex items-start gap-2 font-semibold">
        {result.correct ? (
          <CheckCircle2 aria-hidden="true" className="mt-0.5 size-5 shrink-0" />
        ) : (
          <XCircle aria-hidden="true" className="mt-0.5 size-5 shrink-0" />
        )}
        <span>{result.correct ? 'Chính xác' : 'Chưa chính xác'}</span>
      </p>
      {!result.correct && result.correctAnswer ? (
        <p className="mt-2 flex items-start gap-2 text-ink">
          <CircleAlert aria-hidden="true" className="mt-0.5 size-4 shrink-0" />
          <span>
            Đáp án đúng: {result.correctAnswer}
            {options?.find((option) => option.key === result.correctAnswer)?.text
              ? ` — ${options.find((option) => option.key === result.correctAnswer)?.text}`
              : ''}
          </span>
        </p>
      ) : null}
      {result.explanation ? <p className="mt-2 text-ink-secondary">{result.explanation}</p> : null}
    </div>
  )
}
