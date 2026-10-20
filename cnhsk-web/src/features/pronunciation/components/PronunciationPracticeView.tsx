import { ArrowRight, Headphones, LockKeyhole, PartyPopper, RotateCcw } from 'lucide-react'

import type { AnswerResult, ServedItemView } from '@/features/practice'
import { AudioButton, ChoiceQuestion, SessionProgress } from '@/features/practice'
import { Button, Card, EmptyState, ErrorState, PageHeader, Skeleton } from '@/shared/ui'

import { formatPercentage } from '../formatters'
import type { PronunciationFinishExtra, PronunciationStageSummary } from '../types'

type LoadError = { message: string; requestId?: string }

export interface PronunciationPracticeViewProps {
  answered?: number
  error?: LoadError
  finishExtra?: PronunciationFinishExtra
  incompleteRemaining?: number
  item?: ServedItemView
  onAnswer?: (answer: string) => Promise<unknown>
  onAudioFailed?: (payload: { audioFailed: true; replayCount: number }) => Promise<unknown> | void
  onNext?: () => void
  onReplayCountChange?: (count: number) => void
  onRetry: () => void
  onRestart?: () => void
  pending?: boolean
  requiredStageIndex?: number
  result?: AnswerResult
  stage?: PronunciationStageSummary
  state: 'loading' | 'empty' | 'locked' | 'unavailable' | 'error' | 'ready' | 'finished'
  total?: number
}

function PracticeLoading() {
  return (
    <div aria-label="Đang tải bài luyện phát âm" className="space-y-4">
      <Skeleton className="h-20" />
      <Skeleton className="h-72" />
    </div>
  )
}

function ResultView({
  extra,
  onRestart,
  pending,
}: {
  extra: PronunciationFinishExtra
  onRestart?: () => void
  pending: boolean
}) {
  return (
    <Card className="mx-auto max-w-reading space-y-5 text-center">
      <div
        className="mx-auto flex size-14 items-center justify-center rounded-pill bg-success-bg text-success motion-safe:animate-pulse motion-reduce:animate-none"
        data-testid="pronunciation-result-icon"
      >
        <PartyPopper aria-hidden="true" className="size-7" />
      </div>
      <div>
        <h2 className="text-h2 text-ink">
          {extra.noScore ? 'Chưa tính điểm lượt này' : 'Hoàn thành tầng phát âm'}
        </h2>
        <div className="mt-4 grid gap-3 sm:grid-cols-2">
          <div className="rounded-md bg-canvas p-4">
            <p className="text-caption text-ink-muted">Độ chính xác</p>
            <p className="mt-1 text-h2 text-ink">{formatPercentage(extra.accuracy)}</p>
          </div>
          <div className="rounded-md bg-canvas p-4">
            <p className="text-caption text-ink-muted">Tốt nhất</p>
            <p className="mt-1 text-h2 text-ink">{formatPercentage(extra.bestAccuracy)}</p>
          </div>
        </div>
      </div>

      {extra.completedAllStages ? (
        <p className="rounded-md bg-success-bg p-4 text-label text-success">
          Hoàn thành lộ trình phát âm
        </p>
      ) : extra.unlockedNextStage && extra.nextStageIndex ? (
        <p className="rounded-md bg-success-bg p-4 text-label text-success motion-safe:animate-pulse motion-reduce:animate-none">
          Đã mở tầng {extra.nextStageIndex}
        </p>
      ) : !extra.passed && !extra.noScore && extra.needed !== null ? (
        <p className="rounded-md bg-warning-bg p-4 text-label text-warning">
          Còn {formatPercentage(extra.needed)} nữa
        </p>
      ) : null}

      <div className="flex flex-wrap justify-center gap-3">
        <Button to="/learn/pronunciation" variant="outline">
          Quay lại danh sách
        </Button>
        {extra.passed && extra.nextStageIndex ? (
          <Button to={`/learn/pronunciation/stages/${extra.nextStageIndex}`}>
            Sang tầng {extra.nextStageIndex}
            <ArrowRight aria-hidden="true" className="size-5" />
          </Button>
        ) : null}
        <Button disabled={pending} loading={pending} onClick={onRestart} variant="secondary">
          <RotateCcw aria-hidden="true" className="size-5" />
          Luyện lại
        </Button>
      </div>
    </Card>
  )
}

export function PronunciationPracticeView({
  answered = 0,
  error,
  finishExtra,
  incompleteRemaining,
  item,
  onAnswer,
  onAudioFailed,
  onNext,
  onReplayCountChange,
  onRestart,
  onRetry,
  pending = false,
  requiredStageIndex,
  result,
  stage,
  state,
  total = 1,
}: PronunciationPracticeViewProps) {
  if (state === 'loading') return <PracticeLoading />

  if (state === 'empty') {
    return (
      <EmptyState
        action={<Button to="/learn/pronunciation">Quay lại danh sách</Button>}
        description="Tầng phát âm này không tồn tại hoặc không còn khả dụng."
        icon={<Headphones aria-hidden="true" className="size-5" />}
        title="Không tìm thấy tầng phát âm"
      />
    )
  }

  if (state === 'locked') {
    return (
      <EmptyState
        action={
          requiredStageIndex ? (
            <Button to={`/learn/pronunciation/stages/${requiredStageIndex}`}>
              Học tầng {requiredStageIndex}
            </Button>
          ) : (
            <Button to="/learn/pronunciation">Quay lại danh sách</Button>
          )
        }
        description={
          requiredStageIndex
            ? `Hoàn thành tầng ${requiredStageIndex} trước để mở tầng này.`
            : 'Hãy hoàn thành tầng trước để mở tầng này.'
        }
        icon={<LockKeyhole aria-hidden="true" className="size-5" />}
        title="Tầng phát âm đang bị khoá"
      />
    )
  }

  if (state === 'unavailable') {
    return (
      <EmptyState
        action={<Button to="/learn/pronunciation">Quay lại danh sách</Button>}
        description="Tầng này chưa có bài nghe để luyện. Vui lòng quay lại sau."
        icon={<Headphones aria-hidden="true" className="size-5" />}
        title="Tầng phát âm đang được cập nhật"
      />
    )
  }

  if (state === 'error' || !stage) {
    return (
      <ErrorState
        message={error?.message ?? 'Vui lòng thử lại sau.'}
        onRetry={onRetry}
        requestId={error?.requestId}
        title="Không thể tải bài luyện phát âm"
      />
    )
  }

  return (
    <div>
      <PageHeader
        subtitle={stage.description ?? 'Nghe âm và chọn đáp án phù hợp.'}
        title={`Tầng ${stage.stageIndex}: ${stage.name}`}
      />

      {state === 'finished' && finishExtra ? (
        <ResultView extra={finishExtra} onRestart={onRestart} pending={pending} />
      ) : null}

      {state === 'ready' && item && onAnswer && onAudioFailed ? (
        <div className="space-y-4">
          <SessionProgress current={answered} total={total} />
          {incompleteRemaining !== undefined ? (
            <p className="rounded-md bg-warning-bg p-3 text-body-sm text-warning" role="status">
              Còn {incompleteRemaining} câu chưa làm
            </p>
          ) : null}
          <Card className="space-y-5">
            {typeof item.prompt.audioUrl === 'string' ? (
              <AudioButton
                audioUrl={item.prompt.audioUrl}
                disabled={pending || Boolean(result)}
                key={`pronunciation-audio-${item.no}`}
                maxReplays={item.rules.maxReplays ?? 3}
                onAudioFailed={onAudioFailed}
                onReplayCountChange={onReplayCountChange}
              />
            ) : null}
            <ChoiceQuestion
              feedbackMode="IMMEDIATE"
              item={item}
              key={`pronunciation-question-${item.no}`}
              onAnswer={onAnswer}
              pending={pending}
              result={result}
            />
            {result && (result.correct === null || result.correct === undefined) ? (
              <p className="text-body-sm text-ink-secondary" role="status">
                Câu này không tính điểm.
              </p>
            ) : null}
            {result && answered < total ? (
              <div className="flex justify-end">
                <Button disabled={pending} onClick={onNext}>
                  Câu tiếp
                  <ArrowRight aria-hidden="true" className="size-5" />
                </Button>
              </div>
            ) : null}
          </Card>
        </div>
      ) : null}
    </div>
  )
}
