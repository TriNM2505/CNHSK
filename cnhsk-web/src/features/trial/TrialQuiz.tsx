import { CheckCircle2, RotateCcw, Volume2, XCircle } from 'lucide-react'
import { useEffect, useState } from 'react'

import { playModel } from '@/features/pronunciation/use-voice-recorder'
import { cn } from '@/shared/lib/cn'
import { Button, Card, EmptyState, ErrorState, Skeleton } from '@/shared/ui'

import { getTrial, type TrialFeature, type TrialQuestion } from './api'

const QUESTION_TITLE: Record<TrialFeature, string> = {
  GRAMMAR: 'Câu này nghĩa là gì?',
  HANZI: 'Chữ này đọc Hán Việt là gì?',
  LISTENING: 'Nghe và chọn pinyin đúng',
  VOCAB: 'Từ này nghĩa là gì?',
}

/** Five questions marked on the spot; the result disappears with the page. */
export function TrialQuiz({ feature, level, signUpTo }: { feature: TrialFeature; level: number; signUpTo: string }) {
  const [questions, setQuestions] = useState<TrialQuestion[]>()
  const [failed, setFailed] = useState<string>()
  const [index, setIndex] = useState(0)
  const [picked, setPicked] = useState<number>()
  const [correct, setCorrect] = useState(0)
  const [round, setRound] = useState(0)

  useEffect(() => {
    let alive = true
    setQuestions(undefined)
    setFailed(undefined)
    setIndex(0)
    setPicked(undefined)
    setCorrect(0)
    getTrial(feature, level)
      .then((set) => alive && setQuestions(set.questions))
      .catch((error: unknown) => alive && setFailed(error instanceof Error ? error.message : 'Vui lòng thử lại sau.'))
    return () => {
      alive = false
    }
  }, [feature, level, round])

  if (failed) return <ErrorState message={failed} onRetry={() => setRound((value) => value + 1)} title="Không tải được bài học thử" />
  if (!questions) return <Skeleton className="h-80" />
  if (questions.length === 0) {
    return (
      <EmptyState
        description="Cấp này chưa có nội dung cho phần học này. Hãy chọn cấp khác."
        icon={<CheckCircle2 aria-hidden="true" className="size-5" />}
        title="Chưa có bài thử"
      />
    )
  }

  if (index >= questions.length) {
    return (
      <Card className="space-y-4 p-6 text-center sm:p-8">
        <p className="text-caption font-semibold uppercase tracking-wide text-brand-700">Kết quả học thử</p>
        <p className="text-display-sm tabular-nums text-ink">
          {correct}/{questions.length}
        </p>
        <p className="text-body text-ink-secondary">
          Kết quả học thử không được lưu. Tạo tài khoản miễn phí để lưu tiến độ, ôn lại từ sai và mở lộ trình học.
        </p>
        <div className="flex flex-wrap justify-center gap-3">
          <Button onClick={() => setRound((value) => value + 1)} variant="outline">
            <RotateCcw aria-hidden="true" className="size-4" />
            Làm bộ khác
          </Button>
          <Button to={signUpTo}>Đăng ký để lưu kết quả</Button>
        </div>
      </Card>
    )
  }

  const question = questions[index]!
  const answered = picked !== undefined
  const listening = feature === 'LISTENING'

  function choose(option: number) {
    if (answered) return
    setPicked(option)
    if (option === question.answer) setCorrect((value) => value + 1)
  }

  return (
    <Card className="space-y-6 p-6 sm:p-8">
      <div className="flex items-center justify-between gap-3 text-body-sm text-ink-secondary">
        <span>{QUESTION_TITLE[feature]}</span>
        <span className="tabular-nums">
          Câu {index + 1}/{questions.length}
        </span>
      </div>

      <div className="space-y-2 text-center">
        {listening ? (
          <Button onClick={() => playModel(question.prompt, question.audioUrl)}>
            <Volume2 aria-hidden="true" className="size-5" />
            Nghe âm
          </Button>
        ) : (
          <>
            <p className={cn('break-words font-hanzi text-ink', feature === 'GRAMMAR' ? 'text-hanzi-md' : 'text-hanzi-lg')} lang="zh">
              {question.prompt}
            </p>
            {question.pinyin ? <p className="text-h3 text-brand-700">{question.pinyin}</p> : null}
          </>
        )}
      </div>

      <div className="grid gap-3 sm:grid-cols-2">
        {question.options.map((option, optionIndex) => {
          const right = answered && optionIndex === question.answer
          const wrong = answered && optionIndex === picked && picked !== question.answer
          return (
            <button
              className={cn(
                'flex min-h-14 items-center justify-between gap-2 rounded-md border px-4 text-left text-body text-ink focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus',
                right ? 'border-success bg-success-bg' : wrong ? 'border-danger bg-danger-bg' : 'border-line bg-surface hover:border-brand-600',
                answered && !right && !wrong && 'opacity-60',
              )}
              disabled={answered}
              key={option}
              onClick={() => choose(optionIndex)}
              type="button"
            >
              <span className="min-w-0 break-words">{option}</span>
              {right ? <CheckCircle2 aria-label="Đúng" className="size-5 shrink-0 text-success" /> : null}
              {wrong ? <XCircle aria-label="Sai" className="size-5 shrink-0 text-danger" /> : null}
            </button>
          )
        })}
      </div>

      {answered ? (
        <div className="flex flex-wrap items-center justify-between gap-3 border-t border-line pt-4">
          <p className="min-w-0 break-words text-body-sm text-ink-secondary" role="status">
            {question.explanation}
          </p>
          <Button
            onClick={() => {
              setPicked(undefined)
              setIndex(index + 1)
            }}
          >
            {index + 1 < questions.length ? 'Câu tiếp' : 'Xem kết quả'}
          </Button>
        </div>
      ) : null}
    </Card>
  )
}
