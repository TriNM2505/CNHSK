import {
  ArrowLeft,
  ArrowRight,
  CircleCheck,
  CircleDashed,
  ClipboardCheck,
  Clock3,
} from 'lucide-react'

import { AudioButton, ChoiceQuestion, PinyinInput, type ServedItemView } from '@/features/practice'
import { cn } from '@/shared/lib/cn'
import {
  Button,
  Card,
  ConfirmPanel,
  EmptyState,
  ErrorState,
  HanziText,
  Skeleton,
  StatusBadge,
} from '@/shared/ui'

import { formatWaitTime, isAnswered, type TestDraft } from '../topicTestFlow'
import type { TopicWrongWord } from '../types'

type LoadError = { message: string; requestId?: string }

export type TopicTestResult = {
  firstCompletion: boolean
  maxScore?: number | null
  passed?: boolean
  percent?: number | null
  score?: number | null
  wrongWords: TopicWrongWord[]
}

export type TopicTestStepViewProps = {
  activeNo?: number
  confirmOpen?: boolean
  drafts?: Readonly<Record<number, TestDraft>>
  error?: LoadError
  items?: readonly ServedItemView[]
  learnedWords?: number
  onAudioFailed?: (replayCount: number) => void
  onCancelSubmit?: () => void
  onChoose?: (answer: string) => void
  onConfirmSubmit?: () => void
  onPinyinChange?: (value: string) => void
  onPracticeWrongWords?: () => void
  onReplayCountChange?: (count: number) => void
  onRequestSubmit?: () => void
  onRetry?: () => void
  onRetrySave?: () => void
  onSelectQuestion?: (no: number) => void
  onStart?: () => void
  requiredWords?: number
  result?: TopicTestResult
  saveFailed?: boolean
  starting?: boolean
  state:
    | 'loading'
    | 'intro'
    | 'not-enough'
    | 'retry-later'
    | 'ready'
    | 'finished'
    | 'submitted'
    | 'expired'
    | 'error'
  submitting?: boolean
  unanswered?: number
  waitSeconds?: number
}

const CHOICE_TYPES = new Set(['MEANING_CHOICE', 'AUDIO_WORD_CHOICE', 'CLOZE_CHOICE'])

function QuestionNavigator({
  activeNo,
  drafts,
  items,
  onSelectQuestion,
}: Required<Pick<TopicTestStepViewProps, 'drafts' | 'items' | 'onSelectQuestion'>> & {
  activeNo?: number
}) {
  return (
    <nav aria-label="Danh sách câu hỏi">
      <ol className="grid grid-cols-4 gap-2 sm:grid-cols-5 lg:grid-cols-10">
        {items.map((item, index) => {
          const done = isAnswered(drafts[item.no])
          const active = item.no === activeNo
          return (
            <li key={item.no}>
              <button
                aria-current={active ? 'step' : undefined}
                aria-label={`Câu ${index + 1}, ${done ? 'đã làm' : 'chưa làm'}`}
                className={cn(
                  'flex min-h-touch w-full items-center justify-center gap-1 rounded-md border text-body-sm font-semibold',
                  'focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus focus-visible:ring-offset-2',
                  active
                    ? 'border-brand-700 bg-brand-50 text-brand-700'
                    : 'border-line bg-surface text-ink',
                )}
                onClick={() => onSelectQuestion(item.no)}
                type="button"
              >
                {done ? (
                  <CircleCheck aria-hidden="true" className="size-4 text-success" />
                ) : (
                  <CircleDashed aria-hidden="true" className="size-4 text-ink-muted" />
                )}
                {index + 1}
              </button>
            </li>
          )
        })}
      </ol>
    </nav>
  )
}

function TestQuestion({
  draft,
  item,
  onAudioFailed,
  onChoose,
  onPinyinChange,
  onReplayCountChange,
}: {
  draft?: TestDraft
  item: ServedItemView
  onAudioFailed: (replayCount: number) => void
  onChoose: (answer: string) => void
  onPinyinChange: (value: string) => void
  onReplayCountChange: (count: number) => void
}) {
  const audioUrl = typeof item.prompt.audioUrl === 'string' ? item.prompt.audioUrl : undefined

  if (item.type === 'PINYIN_INPUT') {
    return (
      <PinyinInput
        hint={item.prompt.hint}
        initialValue={draft?.answer}
        key={`pinyin-${item.no}`}
        onChange={onPinyinChange}
        onSubmit={(answer) => onPinyinChange(answer)}
        prompt={item.prompt.text ?? ''}
      />
    )
  }

  if (!CHOICE_TYPES.has(item.type)) {
    return <p className="text-body text-ink-secondary">Dạng câu này chưa được hỗ trợ.</p>
  }

  return (
    <div className="space-y-5">
      {item.type === 'AUDIO_WORD_CHOICE' && audioUrl ? (
        <AudioButton
          audioUrl={audioUrl}
          initialReplayCount={draft?.replayCount ?? 0}
          key={`audio-${item.no}`}
          maxReplays={item.rules.maxReplays ?? 3}
          onAudioFailed={(payload) => onAudioFailed(payload.replayCount)}
          onReplayCountChange={onReplayCountChange}
        />
      ) : null}
      {draft?.audioFailed ? (
        <p className="text-body-sm text-ink-secondary" role="status">
          Đã ghi nhận “Không nghe được” cho câu này.
        </p>
      ) : null}
      <ChoiceQuestion
        feedbackMode="DEFERRED"
        item={item}
        key={`question-${item.no}`}
        onAnswer={onChoose}
        selectedAnswer={draft?.audioFailed ? null : (draft?.answer ?? null)}
      />
    </div>
  )
}

function TestResultView({
  onPracticeWrongWords,
  onStart,
  result,
  starting,
}: Pick<TopicTestStepViewProps, 'onPracticeWrongWords' | 'onStart' | 'starting'> & {
  result: TopicTestResult
}) {
  const scoreText =
    result.score === null || result.score === undefined
      ? '—'
      : result.maxScore
        ? `${result.score}/${result.maxScore}`
        : String(result.score)

  return (
    <section aria-labelledby="topic-test-result-title" className="space-y-5">
      <Card className="space-y-4">
        <div className="flex flex-wrap items-center justify-between gap-3">
          <h2 className="text-h2 text-ink" id="topic-test-result-title">
            Kết quả kiểm tra
          </h2>
          {result.passed === undefined ? null : result.passed ? (
            <StatusBadge tone="success">Đạt</StatusBadge>
          ) : (
            <StatusBadge tone="warning">Chưa đạt</StatusBadge>
          )}
        </div>
        <dl className="grid grid-cols-2 gap-3">
          <div className="rounded-md border border-line p-4">
            <dt className="text-body-sm text-ink-secondary">Số câu đúng</dt>
            <dd className="text-h1 tabular-nums text-ink">{scoreText}</dd>
          </div>
          <div className="rounded-md border border-line p-4">
            <dt className="text-body-sm text-ink-secondary">Tỉ lệ đúng</dt>
            <dd className="text-h1 tabular-nums text-ink">
              {result.percent === null || result.percent === undefined ? '—' : `${result.percent}%`}
            </dd>
          </div>
        </dl>
        {result.firstCompletion ? (
          <p
            className="flex items-start gap-2 rounded-md bg-success-bg p-3 text-body-sm text-success"
            role="status"
          >
            <CircleCheck aria-hidden="true" className="mt-0.5 size-5 shrink-0" />
            Hoàn thành chủ đề
          </p>
        ) : null}
      </Card>

      {result.wrongWords.length ? (
        <Card className="space-y-3">
          <h3 className="text-h3 text-ink">Từ làm sai ({result.wrongWords.length})</h3>
          <ul className="divide-y divide-line">
            {result.wrongWords.map((word) => (
              <li
                className="flex flex-wrap items-baseline gap-x-3 gap-y-1 py-2"
                key={word.lexemeId}
              >
                <HanziText size="sm">{word.text}</HanziText>
                {word.pinyin ? (
                  <span className="text-body text-ink-secondary">{word.pinyin}</span>
                ) : null}
                <span className="text-body text-ink">{word.meaning}</span>
              </li>
            ))}
          </ul>
        </Card>
      ) : null}

      <div className="flex flex-wrap gap-3">
        {result.wrongWords.length ? (
          <Button onClick={onPracticeWrongWords}>Luyện lại từ sai</Button>
        ) : null}
        <Button loading={starting} onClick={onStart} variant="outline">
          Làm bài kiểm tra mới
        </Button>
      </div>
    </section>
  )
}

export function TopicTestStepView({
  activeNo,
  confirmOpen = false,
  drafts = {},
  error,
  items = [],
  learnedWords,
  onAudioFailed = () => undefined,
  onCancelSubmit = () => undefined,
  onChoose = () => undefined,
  onConfirmSubmit = () => undefined,
  onPinyinChange = () => undefined,
  onPracticeWrongWords,
  onReplayCountChange = () => undefined,
  onRequestSubmit,
  onRetry,
  onRetrySave,
  onSelectQuestion = () => undefined,
  onStart,
  requiredWords,
  result,
  saveFailed = false,
  starting = false,
  state,
  submitting = false,
  unanswered = 0,
  waitSeconds = 0,
}: TopicTestStepViewProps) {
  if (state === 'loading') {
    return (
      <div aria-label="Đang tải bài kiểm tra" className="space-y-4">
        <Skeleton variant="card" />
        <Skeleton variant="card" />
      </div>
    )
  }

  if (state === 'intro') {
    return (
      <EmptyState
        action={
          <Button loading={starting} onClick={onStart}>
            Bắt đầu kiểm tra
          </Button>
        }
        description="Bài gồm tối đa 20 câu, làm trong 24 giờ. Bạn có thể đi lại giữa các câu và đổi đáp án trước khi nộp."
        icon={<ClipboardCheck aria-hidden="true" className="size-5" />}
        title="Kiểm tra cuối chủ đề"
      />
    )
  }

  if (state === 'not-enough') {
    return (
      <EmptyState
        description={
          requiredWords === undefined
            ? 'Bạn cần học thêm từ trước khi làm bài kiểm tra.'
            : `Cần học ít nhất ${requiredWords} từ (đã học ${learnedWords ?? 0}).`
        }
        title="Chưa đủ từ để kiểm tra"
      />
    )
  }

  if (state === 'retry-later') {
    return (
      <EmptyState
        action={
          <Button disabled={waitSeconds > 0} loading={starting} onClick={onStart}>
            Làm lại bài kiểm tra
          </Button>
        }
        description={
          waitSeconds > 0
            ? `Bạn có thể làm lại sau ${formatWaitTime(waitSeconds)}.`
            : 'Bạn đã có thể làm lại bài kiểm tra.'
        }
        icon={<Clock3 aria-hidden="true" className="size-5" />}
        title="Chưa thể làm lại ngay"
      />
    )
  }

  if (state === 'expired') {
    return (
      <EmptyState
        action={
          <Button loading={starting} onClick={onStart}>
            Bắt đầu bài mới
          </Button>
        }
        description="Bài kiểm tra quá 24 giờ chưa nộp nên không được chấm."
        title="Bài kiểm tra đã hết hạn"
      />
    )
  }

  if (state === 'error') {
    return (
      <ErrorState
        message={error?.message ?? 'Vui lòng thử lại sau.'}
        onRetry={onRetry}
        requestId={error?.requestId}
        title="Không thể tải bài kiểm tra"
      />
    )
  }

  if (state === 'finished' && result) {
    return (
      <TestResultView
        onPracticeWrongWords={onPracticeWrongWords}
        onStart={onStart}
        result={result}
        starting={starting}
      />
    )
  }

  if (state === 'submitted') {
    return (
      <EmptyState
        description="Bài kiểm tra này đã được nộp. Điểm cao nhất được cập nhật ở đầu trang."
        title="Đã nộp bài"
      />
    )
  }

  const index = items.findIndex((item) => item.no === activeNo)
  const item = index >= 0 ? items[index] : undefined
  if (state !== 'ready' || !item) return null
  const previous = items[index - 1]
  const next = items[index + 1]

  return (
    <div className="space-y-5">
      {error ? (
        <div
          className="rounded-md border border-danger bg-danger-bg p-3 text-body-sm text-danger"
          role="alert"
        >
          {error.message}
          {error.requestId ? (
            <span className="mt-1 block text-caption">Mã lỗi: {error.requestId}</span>
          ) : null}
          {saveFailed ? (
            <Button className="mt-3" onClick={onRetrySave} variant="outline">
              Thử lại lưu nháp
            </Button>
          ) : null}
        </div>
      ) : null}
      <QuestionNavigator
        activeNo={activeNo}
        drafts={drafts}
        items={items}
        onSelectQuestion={onSelectQuestion}
      />
      <Card className="space-y-5">
        <p className="text-label text-ink-secondary">
          Câu {index + 1}/{items.length}
        </p>
        <TestQuestion
          draft={drafts[item.no]}
          item={item}
          onAudioFailed={onAudioFailed}
          onChoose={onChoose}
          onPinyinChange={onPinyinChange}
          onReplayCountChange={onReplayCountChange}
        />
      </Card>
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div className="flex gap-2">
          <Button
            disabled={!previous}
            onClick={() => previous && onSelectQuestion(previous.no)}
            variant="outline"
          >
            <ArrowLeft aria-hidden="true" className="size-5" />
            Câu trước
          </Button>
          <Button
            disabled={!next}
            onClick={() => next && onSelectQuestion(next.no)}
            variant="outline"
          >
            Câu sau
            <ArrowRight aria-hidden="true" className="size-5" />
          </Button>
        </div>
        <Button onClick={onRequestSubmit}>Nộp bài</Button>
      </div>
      <ConfirmPanel
        confirmLabel="Nộp bài"
        message={
          unanswered > 0
            ? `Còn ${unanswered} câu chưa làm — câu chưa làm tính sai.`
            : 'Bạn đã làm hết các câu. Nộp bài để xem kết quả?'
        }
        onCancel={onCancelSubmit}
        onConfirm={onConfirmSubmit}
        open={confirmOpen}
        pending={submitting}
        title="Nộp bài kiểm tra?"
      />
    </div>
  )
}
