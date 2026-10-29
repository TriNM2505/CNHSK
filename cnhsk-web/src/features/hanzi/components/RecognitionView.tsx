import { ArrowRight, Eye, Headphones } from 'lucide-react'

import type { AnswerResult, FinishResult, ServedItemView } from '@/features/practice'
import { AudioButton, ChoiceQuestion, SessionProgress, SessionSummary } from '@/features/practice'
import { cn } from '@/shared/lib/cn'
import {
  Button,
  Card,
  ConfirmPanel,
  EmptyState,
  ErrorState,
  FormField,
  PageHeader,
  Select,
  Skeleton,
  SUPPORTED_HSK_LEVELS,
  type HskLevel,
} from '@/shared/ui'

import { RECOGNITION_MODE_LABELS, RECOGNITION_MODES } from '../labels'
import type { RecognitionMode } from '../types'

type LoadError = { message: string; requestId?: string }

export type RecognitionViewState = 'loading' | 'setup' | 'ready' | 'finished' | 'restart' | 'error'

export interface RecognitionViewProps {
  current?: number
  error?: LoadError
  finishConfirmOpen?: boolean
  finishResult?: FinishResult
  hskLevel: HskLevel
  item?: ServedItemView
  mode: RecognitionMode
  noCharacters?: boolean
  onAnswer?: (answer: string) => Promise<unknown>
  onAudioFailed?: (payload: { audioFailed: true; replayCount: number }) => void
  onFinishCancel?: () => void
  onFinishConfirm?: () => void
  onFinishRequest?: () => void
  onHskLevelChange?: (level: HskLevel) => void
  onModeChange?: (mode: RecognitionMode) => void
  onNext?: () => void
  onReplayCountChange?: (count: number) => void
  onRestart?: () => void
  onRetry?: () => void
  onSkip?: () => void
  onStart?: () => void
  pending?: boolean
  result?: AnswerResult
  state: RecognitionViewState
  total?: number
}

const MODE_ICONS: Record<RecognitionMode, typeof Eye> = {
  AUDIO_TO_CHAR: Headphones,
  CHAR_TO_MEANING: Eye,
}

export function RecognitionView(props: RecognitionViewProps) {
  return (
    <div className="space-y-6">
      <PageHeader
        subtitle="Luyện nhận mặt chữ: nhìn chữ chọn nghĩa hoặc nghe rồi chọn chữ."
        title="Nhận diện chữ Hán"
      />
      <RecognitionBody {...props} />
    </div>
  )
}

function RecognitionBody({
  current = 1,
  error,
  finishConfirmOpen = false,
  finishResult,
  hskLevel,
  item,
  mode,
  noCharacters = false,
  onAnswer,
  onAudioFailed,
  onFinishCancel,
  onFinishConfirm,
  onFinishRequest,
  onHskLevelChange,
  onModeChange,
  onNext,
  onReplayCountChange,
  onRestart,
  onRetry,
  onSkip,
  onStart,
  pending = false,
  result,
  state,
  total = 1,
}: RecognitionViewProps) {
  if (state === 'loading') {
    return (
      <div aria-label="Đang tải lượt nhận diện" className="space-y-4">
        <Skeleton variant="card" />
        <Skeleton variant="card" />
      </div>
    )
  }

  if (state === 'error') {
    return (
      <ErrorState
        message={error?.message ?? 'Vui lòng thử lại sau.'}
        onRetry={onRetry}
        requestId={error?.requestId}
        title="Không thể tải lượt nhận diện"
      />
    )
  }

  if (state === 'restart') {
    return (
      <EmptyState
        action={<Button onClick={onRestart}>Bắt đầu lượt mới</Button>}
        description={error?.message ?? 'Lượt luyện này đã kết thúc. Hãy bắt đầu lượt mới.'}
        title="Cần bắt đầu lại"
      />
    )
  }

  if (state === 'finished' && finishResult) {
    return (
      <SessionSummary
        actions={<Button onClick={onRestart}>Luyện lượt mới</Button>}
        result={finishResult}
        title={`Kết quả — ${RECOGNITION_MODE_LABELS[mode].title}`}
      />
    )
  }

  if (state === 'ready' && item && onAnswer) {
    const audioUrl = typeof item.prompt.audioUrl === 'string' ? item.prompt.audioUrl : undefined
    return (
      <div className="space-y-5">
        <SessionProgress current={current} total={total} />
        <Card className="space-y-5">
          <p className="text-label text-ink-secondary">{RECOGNITION_MODE_LABELS[mode].title}</p>
          {mode === 'AUDIO_TO_CHAR' && audioUrl && onAudioFailed ? (
            <AudioButton
              audioUrl={audioUrl}
              disabled={pending || Boolean(result)}
              key={`audio-${item.no}`}
              maxReplays={item.rules.maxReplays ?? 3}
              onAudioFailed={onAudioFailed}
              onReplayCountChange={onReplayCountChange}
            />
          ) : null}
          <ChoiceQuestion
            feedbackMode="IMMEDIATE"
            item={item}
            key={`question-${item.no}`}
            onAnswer={onAnswer}
            pending={pending}
            result={result}
          />
          {result && (result.correct === null || result.correct === undefined) ? (
            <p className="text-body-sm text-ink-secondary" role="status">
              Câu này không tính điểm.
            </p>
          ) : null}
          <div className="flex flex-wrap items-center justify-between gap-3">
            <Button disabled={pending} onClick={onFinishRequest} variant="ghost">
              Kết thúc luyện tập
            </Button>
            {!result ? (
              <Button disabled={pending} onClick={onSkip} variant="outline">
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
        <ConfirmPanel
          confirmLabel="Kết thúc và xem kết quả"
          message="Các câu đã trả lời được giữ lại; câu chưa làm không tính."
          onCancel={onFinishCancel ?? (() => undefined)}
          onConfirm={onFinishConfirm ?? (() => undefined)}
          open={finishConfirmOpen}
          pending={pending}
          title="Kết thúc lượt luyện?"
        />
      </div>
    )
  }

  return (
    <Card className="space-y-6">
      <fieldset className="space-y-3">
        <legend className="text-h3 text-ink">Chọn chế độ</legend>
        <div className="grid gap-3 sm:grid-cols-2">
          {RECOGNITION_MODES.map((option) => {
            const Icon = MODE_ICONS[option]
            const selected = option === mode
            return (
              <button
                aria-pressed={selected}
                className={cn(
                  'flex min-h-touch items-start gap-3 rounded-md border bg-surface p-4 text-left transition-colors',
                  'focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus focus-visible:ring-offset-2',
                  selected ? 'border-brand-600 bg-brand-50' : 'border-line hover:border-brand-600',
                )}
                disabled={pending}
                key={option}
                onClick={() => onModeChange?.(option)}
                type="button"
              >
                <Icon aria-hidden="true" className="mt-0.5 size-5 shrink-0 text-brand-700" />
                <span className="space-y-1">
                  <span className="block text-body font-semibold text-ink">
                    {RECOGNITION_MODE_LABELS[option].title}
                  </span>
                  <span className="block text-body-sm text-ink-secondary">
                    {RECOGNITION_MODE_LABELS[option].description}
                  </span>
                </span>
              </button>
            )
          })}
        </div>
      </fieldset>

      <FormField htmlFor="recognition-hsk-level" label="Cấp HSK">
        <Select
          disabled={pending}
          id="recognition-hsk-level"
          onChange={(event) => onHskLevelChange?.(Number(event.target.value) as HskLevel)}
          value={String(hskLevel)}
        >
          {SUPPORTED_HSK_LEVELS.map((level) => (
            <option key={level} value={String(level)}>
              HSK {level}
            </option>
          ))}
        </Select>
      </FormField>

      {noCharacters ? (
        <p className="rounded-md bg-info-bg p-3 text-body-sm text-info" role="status">
          Chưa có chữ phù hợp ở HSK {hskLevel} cho chế độ này. Hãy chọn cấp hoặc chế độ khác.
        </p>
      ) : null}
      {error ? (
        <p className="rounded-md bg-danger-bg p-3 text-body-sm text-danger" role="alert">
          {error.message}
        </p>
      ) : null}

      <div className="flex justify-end">
        <Button disabled={pending} onClick={onStart}>
          Bắt đầu luyện
          <ArrowRight aria-hidden="true" className="size-5" />
        </Button>
      </div>
    </Card>
  )
}
