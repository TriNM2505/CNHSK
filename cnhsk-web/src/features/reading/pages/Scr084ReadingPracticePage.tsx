import { BookOpenText } from 'lucide-react'
import { useCallback, useEffect, useMemo, useRef, useState } from 'react'

import {
  ChoiceQuestion,
  restoredFinishResult,
  SessionProgress,
  SessionSummary,
  usePracticeSession,
} from '@/features/practice'
import { cn } from '@/shared/lib/cn'
import { Button, HskLevelPicker, Skeleton, useHskLevel } from '@/shared/ui'

import { startReadingPractice, type ReadingQuestionType } from '../api'

type TypeChoice = 'ALL' | ReadingQuestionType

const TYPE_CHOICES: readonly { value: TypeChoice; label: string; hint: string }[] = [
  { value: 'ALL', label: 'Tất cả dạng', hint: 'Trộn các dạng câu đọc của cấp này.' },
  {
    value: 'MULTIPLE_CHOICE',
    label: 'Chọn đáp án',
    hint: 'Chọn từ điền chỗ trống hoặc câu đáp phù hợp.',
  },
  { value: 'TRUE_FALSE', label: 'Đúng / sai', hint: 'Đọc câu, xét câu nhận định đúng hay sai.' },
]

function clampDuration(value: number) {
  return Math.min(Math.max(Math.round(value), 0), 3_600_000)
}

/** SCR-084: reading practice by HSK level; every answer counts toward the learner's knowledge tree. */
export default function Scr084ReadingPracticePage() {
  const [level, setLevel] = useHskLevel()
  const [type, setType] = useState<TypeChoice>('ALL')
  const [noQuestions, setNoQuestions] = useState(false)
  const [setupRequested, setSetupRequested] = useState(false)
  const [activeNo, setActiveNo] = useState<number>()
  const initializedSession = useRef<number>()
  const shownAt = useRef(performance.now())

  const startSession = useCallback(async () => {
    const created = await startReadingPractice({
      hskLevel: level,
      ...(type === 'ALL' ? {} : { qtype: type }),
    })
    setNoQuestions(created === null)
    return created
  }, [level, type])

  const practice = usePracticeSession({
    refId: null,
    source: 'READING_PRACTICE',
    start: startSession,
  })
  const session = practice.session

  const answered = useMemo(() => {
    const values = new Set(session?.answered.map((item) => item.no) ?? [])
    Object.keys(practice.results).forEach((no) => values.add(Number(no)))
    return values
  }, [practice.results, session?.answered])

  useEffect(() => {
    if (!session || initializedSession.current === session.sessionId) return
    initializedSession.current = session.sessionId
    setActiveNo(session.items.find((item) => !answered.has(item.no))?.no)
  }, [answered, session])

  useEffect(() => {
    shownAt.current = performance.now()
  }, [activeNo])

  const item = session?.items.find((candidate) => candidate.no === activeNo)
  const result = activeNo === undefined ? undefined : practice.results[activeNo]
  const position = item ? session!.items.findIndex((candidate) => candidate.no === item.no) + 1 : 0
  const finishResult = practice.finishResult ?? restoredFinishResult(session)

  async function answer(selected: string) {
    if (!item) return null
    const outcome = await practice.answer(item.no, {
      answer: selected,
      durationMs: clampDuration(performance.now() - shownAt.current),
    })
    // ChoiceQuestion clears the choice when the promise rejects, so the learner can retry.
    if (!outcome) throw new Error('answer-failed')
    return outcome
  }

  async function next() {
    const following = session?.items.find((candidate) => !answered.has(candidate.no))
    if (following) {
      setActiveNo(following.no)
      return
    }
    setActiveNo(undefined)
    await practice.finish()
  }

  async function start() {
    initializedSession.current = undefined
    const created = await practice.start()
    if (created) setSetupRequested(false)
  }

  const showSetup =
    setupRequested ||
    practice.state === 'idle' ||
    practice.state === 'abandoned' ||
    (practice.state === 'finished' && !finishResult)

  return (
    <main className="mx-auto w-full min-w-0 max-w-content space-y-5 px-4 py-6 pb-16 sm:px-6 lg:max-w-[960px]">
      <header className="flex items-start gap-4 rounded-md border border-brand-100 bg-brand-50 px-5 py-5 sm:px-6">
        <span className="flex size-12 shrink-0 items-center justify-center rounded-pill bg-brand-100 text-brand-700">
          <BookOpenText aria-hidden="true" className="size-6" />
        </span>
        <div className="min-w-0 space-y-1">
          <h1 className="text-h2 font-bold text-ink">Đọc</h1>
          <p className="text-body-sm text-ink-secondary">
            Đọc câu tiếng Trung rồi chọn đáp án. Mỗi câu trả lời được tính vào các nút năng lực của
            bạn trên cây tri thức.
          </p>
        </div>
      </header>

      {practice.state === 'loading' || practice.pending === 'sync' ? (
        <Skeleton variant="card" />
      ) : null}

      {practice.state === 'error' ? (
        <div className="space-y-3 rounded-md border border-line bg-surface p-5">
          <p role="alert" className="text-danger">
            {practice.error?.message ?? 'Không tải được lượt luyện đọc.'}
          </p>
          <Button onClick={practice.reload}>Thử lại</Button>
        </div>
      ) : null}

      {showSetup ? (
        <section
          aria-labelledby="reading-setup"
          className="space-y-5 rounded-md border border-line bg-surface p-5 sm:p-6"
        >
          <h2 className="text-h3 text-ink" id="reading-setup">
            Chọn bài đọc
          </h2>
          <HskLevelPicker
            level={level}
            onChange={(value) => {
              setNoQuestions(false)
              setLevel(value)
            }}
          />
          <div aria-label="Dạng câu" className="grid gap-3 sm:grid-cols-3" role="group">
            {TYPE_CHOICES.map((choice) => (
              <button
                aria-pressed={type === choice.value}
                className={cn(
                  'min-h-touch rounded-md border p-4 text-left focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus',
                  type === choice.value
                    ? 'border-brand-600 bg-brand-50'
                    : 'border-line hover:border-brand-600',
                )}
                key={choice.value}
                onClick={() => {
                  setNoQuestions(false)
                  setType(choice.value)
                }}
                type="button"
              >
                <span className="block font-semibold text-ink">{choice.label}</span>
                <span className="mt-1 block text-body-sm text-ink-secondary">{choice.hint}</span>
              </button>
            ))}
          </div>
          {noQuestions ? (
            <p className="rounded-md bg-info-bg p-3 text-body-sm text-info" role="status">
              HSK {level} chưa có câu đọc đã duyệt cho dạng này. Hãy chọn cấp hoặc dạng khác.
            </p>
          ) : null}
          {practice.error && practice.state !== 'error' ? (
            <p role="alert" className="text-danger">
              {practice.error.message}
            </p>
          ) : null}
          <Button disabled={practice.pending !== null} onClick={() => void start()}>
            Bắt đầu đọc
          </Button>
        </section>
      ) : null}

      {!showSetup && practice.state === 'ready' && item ? (
        <section className="space-y-5 rounded-md border border-line bg-surface p-5 sm:p-6">
          <SessionProgress current={position} total={session!.items.length} />
          <ChoiceQuestion
            feedbackMode={session!.feedback}
            item={item}
            onAnswer={answer}
            pending={practice.pending === 'answer'}
            result={result}
          />
          {practice.error ? (
            <p role="alert" className="text-danger">
              {practice.error.message}
            </p>
          ) : null}
          {result ? (
            <Button disabled={practice.pending !== null} onClick={() => void next()}>
              {position < session!.items.length ? 'Câu tiếp' : 'Xem kết quả'}
            </Button>
          ) : null}
        </section>
      ) : null}

      {!showSetup && practice.state === 'finished' && finishResult ? (
        <SessionSummary
          actions={
            <Button onClick={() => setSetupRequested(true)} variant="outline">
              Đọc bài khác
            </Button>
          }
          result={finishResult}
          title={`Kết quả đọc HSK ${typeof session?.meta?.hskLevel === 'number' ? session.meta.hskLevel : level}`}
        />
      ) : null}
    </main>
  )
}
