import { ArrowRight, BookOpenText, Headphones } from 'lucide-react'

import type { AnswerResult, FinishResult, ServedItemView } from '@/features/practice'
import { AudioButton, ChoiceQuestion, SessionProgress, SessionSummary } from '@/features/practice'
import { Button, Card, ConfirmPanel, EmptyState, ErrorState, Skeleton } from '@/shared/ui'

import type { TopicPracticeMode } from '../types'

type LoadError = { message: string; requestId?: string }

export type TopicPracticeStepViewProps = {
  abandonConfirmOpen?: boolean
  continuePath?: string
  current?: number
  error?: LoadError
  finishResult?: FinishResult
  item?: ServedItemView
  mode: TopicPracticeMode
  onAbandonCancel?: () => void
  onAbandonConfirm?: () => void
  onAbandonRequest?: () => void
  onAnswer?: (answer: string) => Promise<unknown>
  onAudioFailed?: (payload: { audioFailed: true; replayCount: number }) => void
  onNext?: () => void
  onReplayCountChange?: (count: number) => void
  onRetry?: () => void
  onSkip?: () => void
  onStartNormal?: () => void
  pending?: boolean
  recognitionPath: string
  result?: AnswerResult
  studyPath: string
  state:
    | 'loading'
    | 'finishing'
    | 'ready'
    | 'finished'
    | 'mismatch'
    | 'unavailable'
    | 'invalid-focus'
    | 'restart'
    | 'error'
  total?: number
}

const modeLabel = (mode: TopicPracticeMode) =>
  mode === 'RECOGNITION' ? 'Luyện nhận diện' : 'Luyện nghe'

export function TopicPracticeStepView({
  abandonConfirmOpen = false,
  continuePath,
  current = 1,
  error,
  finishResult,
  item,
  mode,
  onAbandonCancel,
  onAbandonConfirm,
  onAbandonRequest,
  onAnswer,
  onAudioFailed,
  onNext,
  onReplayCountChange,
  onRetry,
  onSkip,
  onStartNormal,
  pending = false,
  recognitionPath,
  result,
  state,
  studyPath,
  total = 1,
}: TopicPracticeStepViewProps) {
  if (state === 'loading' || state === 'finishing') {
    return (
      <div aria-label="Đang tải lượt luyện" className="space-y-4">
        {state === 'finishing' ? <p role="status">Đang hoàn tất lượt luyện</p> : null}
        <Skeleton variant="card" />
        <Skeleton variant="card" />
      </div>
    )
  }

  if (state === 'mismatch' && continuePath) {
    return (
      <>
        <EmptyState
          action={
            <div className="flex flex-wrap justify-center gap-3">
              <Button to={continuePath}>Tiếp tục lượt đang dở</Button>
              <Button onClick={onAbandonRequest} variant="outline">
                Bỏ lượt đó, bắt đầu lượt mới
              </Button>
            </div>
          }
          description={`Bạn đang có một lượt ${mode === 'RECOGNITION' ? 'luyện nghe' : 'luyện nhận diện'} chưa hoàn thành.`}
          title="Có lượt luyện đang dở"
        />
        <ConfirmPanel
          confirmLabel="Bỏ lượt và bắt đầu mới"
          danger
          message="Các câu đã trả lời vẫn được giữ, nhưng lượt này sẽ không thể tiếp tục."
          onCancel={onAbandonCancel ?? (() => undefined)}
          onConfirm={onAbandonConfirm ?? (() => undefined)}
          open={abandonConfirmOpen}
          pending={pending}
          title="Bỏ lượt đang dở?"
        />
      </>
    )
  }

  if (state === 'invalid-focus') {
    return (
      <EmptyState
        action={<Button onClick={onStartNormal}>Luyện bình thường</Button>}
        description="Danh sách từ cần luyện lại không còn phù hợp với chủ đề."
        title="Không thể luyện lại các từ đã chọn"
      />
    )
  }

  if (state === 'unavailable' || state === 'restart') {
    return (
      <EmptyState
        action={
          state === 'restart' ? (
            <Button onClick={onStartNormal}>Bắt đầu lượt mới</Button>
          ) : mode === 'LISTENING' ? (
            <Button to={recognitionPath}>Luyện nhận diện</Button>
          ) : (
            <Button to={studyPath}>Học từ mới</Button>
          )
        }
        description={error?.message ?? 'Lượt luyện này đã kết thúc. Hãy bắt đầu lượt mới.'}
        icon={
          mode === 'LISTENING' ? (
            <Headphones aria-hidden="true" className="size-5" />
          ) : (
            <BookOpenText aria-hidden="true" className="size-5" />
          )
        }
        title={
          state === 'restart' ? 'Cần bắt đầu lại' : `Chưa thể ${modeLabel(mode).toLowerCase()}`
        }
      />
    )
  }

  if (state === 'error') {
    return (
      <ErrorState
        message={error?.message ?? 'Vui lòng thử lại sau.'}
        onRetry={onRetry}
        requestId={error?.requestId}
        title="Không thể tải lượt luyện"
      />
    )
  }

  if (state === 'finished' && finishResult) {
    return (
      <SessionSummary
        actions={<Button onClick={onStartNormal}>Luyện lượt mới</Button>}
        result={finishResult}
      />
    )
  }

  if (state === 'ready' && item && onAnswer) {
    const audioUrl = typeof item.prompt.audioUrl === 'string' ? item.prompt.audioUrl : undefined
    return (
      <div className="space-y-5">
        <SessionProgress current={current} total={total} />
        <Card className="space-y-5">
          {mode === 'LISTENING' && audioUrl && onAudioFailed ? (
            <AudioButton
              audioUrl={audioUrl}
              key={`audio-${item.no}`}
              disabled={pending || Boolean(result)}
              maxReplays={item.rules.maxReplays ?? 3}
              onAudioFailed={onAudioFailed}
              onReplayCountChange={onReplayCountChange}
            />
          ) : null}
          <ChoiceQuestion
            feedbackMode="IMMEDIATE"
            key={`question-${item.no}`}
            item={item}
            onAnswer={onAnswer}
            pending={pending}
            result={result}
          />
          {result && (result.correct === null || result.correct === undefined) ? (
            <p className="text-body-sm text-ink-secondary" role="status">
              Câu này không tính điểm.
            </p>
          ) : null}
          <div className="flex flex-wrap justify-end gap-3">
            {!result ? (
              <Button disabled={pending} onClick={onSkip} variant="ghost">
                Bỏ qua
              </Button>
            ) : (
              <Button disabled={pending} onClick={onNext}>
                Câu tiếp
                <ArrowRight aria-hidden="true" className="size-5" />
              </Button>
            )}
          </div>
        </Card>
      </div>
    )
  }

  return null
}
