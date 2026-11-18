import {
  ArrowLeft,
  ArrowRight,
  Headphones,
  LockKeyhole,
  PartyPopper,
  RotateCcw,
  ShieldCheck,
  Sparkles,
  Target,
  Volume2,
} from 'lucide-react'

import type { AnswerResult, ServedItemView } from '@/features/practice'
import { AudioButton, ChoiceQuestion, SessionProgress } from '@/features/practice'
import { PageBanner, Button, Card, EmptyState, ErrorState, Skeleton } from '@/shared/ui'

import { formatPercentage } from '../formatters'
import type { PronunciationFinishExtra, PronunciationStageSummary } from '../types'

type LoadError = { message: string; requestId?: string }

export interface PronunciationPracticeViewProps {
  answered?: number
  error?: LoadError
  finalAnswer?: { item: ServedItemView; result: AnswerResult }
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
  state:
    | 'loading'
    | 'empty'
    | 'locked'
    | 'unavailable'
    | 'error'
    | 'ready'
    | 'finished'
    | 'submitted'
  total?: number
}

function PracticeLoading() {
  return (
    <div
      aria-label="Đang tải bài luyện phát âm"
      className="mx-auto max-w-content space-y-4 px-4 py-6 sm:px-6 lg:px-8"
    >
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
    <Card className="min-w-0 space-y-5 border-brand-100 bg-surface p-6 text-center sm:p-8">
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
          <div className="rounded-md border border-brand-100 bg-brand-50 p-4">
            <p className="text-caption text-ink-secondary">Độ chính xác</p>
            <p className="mt-1 text-h2 tabular-nums text-ink">{formatPercentage(extra.accuracy)}</p>
          </div>
          <div className="rounded-md border border-brand-100 bg-brand-50 p-4">
            <p className="text-caption text-ink-secondary">Tốt nhất</p>
            <p className="mt-1 text-h2 tabular-nums text-ink">
              {formatPercentage(extra.bestAccuracy)}
            </p>
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

      <div className="flex flex-wrap justify-center gap-3 pt-1">
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
  finalAnswer,
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
      <div className="mx-auto max-w-content px-4 py-6 sm:px-6 lg:px-8">
        <EmptyState
          action={<Button to="/learn/pronunciation">Quay lại danh sách</Button>}
          description="Tầng phát âm này không tồn tại hoặc không còn khả dụng."
          icon={<Headphones aria-hidden="true" className="size-5" />}
          title="Không tìm thấy tầng phát âm"
        />
      </div>
    )
  }

  if (state === 'locked') {
    return (
      <div className="mx-auto max-w-content px-4 py-6 sm:px-6 lg:px-8">
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
      </div>
    )
  }

  if (state === 'unavailable') {
    return (
      <div className="mx-auto max-w-content px-4 py-6 sm:px-6 lg:px-8">
        <EmptyState
          action={<Button to="/learn/pronunciation">Quay lại danh sách</Button>}
          description="Tầng này chưa có bài nghe để luyện. Vui lòng quay lại sau."
          icon={<Headphones aria-hidden="true" className="size-5" />}
          title="Tầng phát âm đang được cập nhật"
        />
      </div>
    )
  }

  if (state === 'error' || !stage) {
    return (
      <div className="mx-auto max-w-content px-4 py-6 sm:px-6 lg:px-8">
        <ErrorState
          message={error?.message ?? 'Vui lòng thử lại sau.'}
          onRetry={onRetry}
          requestId={error?.requestId}
          title="Không thể tải bài luyện phát âm"
        />
      </div>
    )
  }

  return (
    <div className="mx-auto w-full min-w-0 max-w-content space-y-6 px-4 py-6 pb-16 sm:px-6 lg:px-8">
      <PageBanner
        subtitle={stage.description ?? 'Nghe âm và chọn đáp án phù hợp.'}
        title={`Tầng ${stage.stageIndex}: ${stage.name}`}
      />

      {state === 'finished' && finishExtra ? (
        <div className="grid min-w-0 items-start gap-6 lg:grid-cols-12">
          <div className="min-w-0 space-y-5 lg:col-span-8">
            <ResultView extra={finishExtra} onRestart={onRestart} pending={pending} />
            {finalAnswer ? (
              <Card className="min-w-0 space-y-4 border-brand-100 bg-surface p-6">
                <h2 className="text-h3 text-ink">Câu cuối</h2>
                <ChoiceQuestion
                  feedbackMode="IMMEDIATE"
                  item={finalAnswer.item}
                  onAnswer={() => undefined}
                  result={finalAnswer.result}
                />
                {finalAnswer.result.correct === null || finalAnswer.result.correct === undefined ? (
                  <p className="text-body-sm text-ink-secondary" role="status">
                    Câu này không tính điểm.
                  </p>
                ) : null}
              </Card>
            ) : null}
          </div>

          <div className="min-w-0 space-y-5 lg:col-span-4">
            <Card className="min-w-0 space-y-4 border-brand-100 bg-brand-50 p-5">
              <div className="flex items-center gap-3">
                <div className="flex size-10 shrink-0 items-center justify-center rounded-pill bg-brand-100 text-brand-700">
                  <Volume2 aria-hidden="true" className="size-5" />
                </div>
                <div>
                  <h3 className="text-h3 text-ink">Nhóm âm đã luyện</h3>
                  <p className="text-caption text-ink-secondary">
                    Trọng tâm phát âm của tầng hiện tại
                  </p>
                </div>
              </div>
              {stage.targetSounds.length ? (
                <div className="grid grid-cols-2 gap-2.5 border-t border-brand-100 pt-3">
                  {stage.targetSounds.map((sound) => (
                    <div
                      className="rounded-md border border-brand-100 bg-surface p-3 text-center font-hanzi text-h3 font-semibold text-brand-700"
                      key={sound}
                    >
                      {sound}
                    </div>
                  ))}
                </div>
              ) : null}
            </Card>

            <Card className="min-w-0 space-y-3 border-brand-100 bg-surface p-5">
              <div className="flex items-center gap-2">
                <ShieldCheck aria-hidden="true" className="size-4 shrink-0 text-brand-700" />
                <h3 className="text-label text-ink">Ghi nhận kết quả</h3>
              </div>
              <p className="text-body-sm text-ink-secondary">
                Điểm chính xác cao nhất sẽ được lưu tự động vào tiến độ lộ trình 8 tầng phát âm.
              </p>
            </Card>
          </div>
        </div>
      ) : null}

      {state === 'submitted' ? (
        <Card className="mx-auto max-w-reading space-y-5 border-brand-100 bg-surface p-6 text-center sm:p-8">
          <h2 className="text-h2 text-ink">Lượt đã kết thúc</h2>
          <p className="text-body-sm text-ink-secondary">
            Bạn có thể quay lại danh sách tầng hoặc bắt đầu lượt luyện khác.
          </p>
          <div className="flex flex-wrap justify-center gap-3">
            <Button to="/learn/pronunciation" variant="outline">
              Quay lại danh sách
            </Button>
            <Button disabled={pending} loading={pending} onClick={onRestart}>
              Luyện lại
            </Button>
          </div>
        </Card>
      ) : null}

      {state === 'ready' && incompleteRemaining !== undefined ? (
        <p className="rounded-md bg-warning-bg p-3.5 text-body-sm text-warning" role="status">
          Còn {incompleteRemaining} câu chưa làm
        </p>
      ) : null}

      {state === 'ready' && !item && incompleteRemaining !== undefined ? (
        <Button disabled={pending} onClick={onRetry}>
          Thử lại
        </Button>
      ) : null}

      {state === 'ready' && item && onAnswer && onAudioFailed ? (
        <div className="space-y-6">
          <Card className="space-y-3 border-brand-100 bg-brand-50 p-5">
            <div className="flex flex-wrap items-center justify-between gap-3">
              <div className="flex items-center gap-3">
                <div className="flex size-10 shrink-0 items-center justify-center rounded-pill bg-brand-100 text-brand-700">
                  <Target aria-hidden="true" className="size-5" />
                </div>
                <div>
                  <p className="text-label text-ink">Tiến độ phiên luyện</p>
                  {stage.targetSounds.length ? (
                    <p className="font-hanzi text-caption text-brand-700">
                      Âm mục tiêu: {stage.targetSounds.join(' · ')}
                    </p>
                  ) : null}
                </div>
              </div>
              <Button size="sm" to="/learn/pronunciation" variant="outline">
                <ArrowLeft aria-hidden="true" className="mr-1 inline-block size-4 align-middle" />
                Về danh sách tầng
              </Button>
            </div>
            <SessionProgress current={answered} total={total} />
          </Card>

          <div className="grid min-w-0 items-start gap-6 lg:grid-cols-12">
            <Card className="min-w-0 space-y-6 p-6 sm:p-8 lg:col-span-8">
              <div className="flex items-center justify-between gap-2 border-b border-line pb-3">
                <span className="inline-flex items-center gap-1.5 rounded-pill bg-brand-50 px-3 py-1 text-caption font-semibold text-brand-700">
                  <Sparkles aria-hidden="true" className="size-3.5" />
                  Bài tập phân biệt âm
                </span>
                <span className="text-caption text-ink-secondary">Mục #{item.no}</span>
              </div>

              {typeof item.prompt.audioUrl === 'string' ? (
                <div className="rounded-md border border-brand-100 bg-brand-50 p-4">
                  <AudioButton
                    audioUrl={item.prompt.audioUrl}
                    disabled={pending || Boolean(result)}
                    key={`pronunciation-audio-${item.no}`}
                    maxReplays={item.rules.maxReplays ?? 3}
                    onAudioFailed={onAudioFailed}
                    onReplayCountChange={onReplayCountChange}
                  />
                </div>
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
                <div className="flex justify-end border-t border-line pt-4">
                  <Button disabled={pending} onClick={onNext}>
                    Câu tiếp
                    <ArrowRight aria-hidden="true" className="size-5" />
                  </Button>
                </div>
              ) : null}
            </Card>

            <div className="min-w-0 space-y-5 lg:col-span-4">
              <Card className="min-w-0 space-y-4 border-brand-100 bg-brand-50 p-5">
                <div className="flex items-center gap-3">
                  <div className="flex size-10 shrink-0 items-center justify-center rounded-pill bg-brand-100 text-brand-700">
                    <Volume2 aria-hidden="true" className="size-5" />
                  </div>
                  <div>
                    <h2 className="text-h3 text-ink">Nhóm âm trọng tâm</h2>
                    <p className="text-caption text-ink-secondary">
                      Lắng nghe kỹ sự khác biệt về vị trí phát âm
                    </p>
                  </div>
                </div>
                {stage.targetSounds.length ? (
                  <div className="grid grid-cols-2 gap-2.5 border-t border-brand-100 pt-3">
                    {stage.targetSounds.map((sound) => (
                      <div
                        className="rounded-md border border-brand-100 bg-surface p-3 text-center font-hanzi text-h3 font-semibold text-brand-700"
                        key={sound}
                      >
                        {sound}
                      </div>
                    ))}
                  </div>
                ) : null}
              </Card>

              <Card className="min-w-0 space-y-3 border-brand-100 bg-surface p-5">
                <h2 className="text-h3 text-ink">Hướng dẫn luyện nghe</h2>
                <ul className="space-y-2 text-body-sm text-ink-secondary">
                  <li>• Bấm nút nghe từ để phát âm mẫu tối đa 3 lần cho mỗi câu hỏi.</li>
                  <li>• Chọn phiên âm Pinyin tương ứng với âm vừa nghe được.</li>
                  <li>• Nếu âm thanh gặp sự cố, chọn báo không nghe được để chuyển câu.</li>
                </ul>
              </Card>
            </div>
          </div>
        </div>
      ) : null}
    </div>
  )
}
