import { BookOpenText, LockKeyhole } from 'lucide-react'
import type { ReactNode } from 'react'
import { Link } from 'react-router-dom'

import type { FinishResult, ServedItemView } from '@/features/practice'
import { SessionProgress, SessionSummary, StudyCard } from '@/features/practice'
import { cn } from '@/shared/lib/cn'
import {
  Button,
  EmptyState,
  ErrorState,
  HanziText,
  LevelBadge,
  PageState,
  Progress,
  Skeleton,
  type PageStateValue,
} from '@/shared/ui'

import { getTopicStepBlockedReason, topicStepPath, type TopicStep } from '../topicStudyFlow'
import type { TopicDetail } from '../types'

type LoadError = { message: string; requestId?: string }

const STEP_LABELS: Record<TopicStep, string> = {
  study: 'Học từ mới',
  recognition: 'Luyện nhận diện',
  listening: 'Luyện nghe',
  test: 'Kiểm tra cuối chủ đề',
}

const STEPS: readonly TopicStep[] = ['study', 'recognition', 'listening', 'test']

function containsHanzi(value: string) {
  return /\p{Script=Han}/u.test(value)
}

function TopicTitle({ name }: { name: string }) {
  if (containsHanzi(name)) return <HanziText size="md">{name}</HanziText>
  return <span>{name}</span>
}

export type StudyStepViewProps = {
  current?: number
  error?: LoadError
  finishResult?: FinishResult
  item?: ServedItemView
  learnedCount?: number
  onAudioFailed?: (payload: { audioFailed: true; replayCount: number }) => void
  onContinue?: () => void
  onKnown?: () => void
  onReplayCountChange?: (count: number) => void
  onRetry?: () => void
  onStartAgain?: () => void
  pending?: boolean
  recognitionPath: string
  state: 'loading' | 'ready' | 'finished' | 'no-new' | 'error' | 'restart'
  total?: number
}

export function StudyStepView({
  current = 1,
  error,
  finishResult,
  item,
  learnedCount = 0,
  onAudioFailed,
  onContinue,
  onKnown,
  onReplayCountChange,
  onRetry,
  onStartAgain,
  pending = false,
  recognitionPath,
  state,
  total = 1,
}: StudyStepViewProps) {
  if (state === 'loading') {
    return (
      <div aria-label="Đang tải lượt học" className="space-y-4">
        <Skeleton variant="card" />
        <Skeleton variant="card" />
      </div>
    )
  }

  if (state === 'no-new') {
    return (
      <EmptyState
        action={
          <Button to={recognitionPath} variant="primary">
            Luyện nhận diện
          </Button>
        }
        description="Bạn có thể tiếp tục củng cố những từ đã học."
        icon={<BookOpenText aria-hidden="true" className="size-5" />}
        title="Bạn đã học hết từ mới của chủ đề này"
      />
    )
  }

  if (state === 'restart') {
    return (
      <EmptyState
        action={<Button onClick={onStartAgain}>Bắt đầu lượt mới</Button>}
        description={error?.message ?? 'Lượt học hiện tại không thể tiếp tục.'}
        title="Cần bắt đầu lại"
      />
    )
  }

  if (state === 'error') {
    return (
      <ErrorState
        message={error?.message ?? 'Vui lòng thử lại sau.'}
        onRetry={onRetry}
        requestId={error?.requestId}
        title="Không thể tải lượt học"
      />
    )
  }

  if (state === 'finished' && finishResult) {
    return (
      <SessionSummary
        actions={<Button to={recognitionPath}>Luyện nhận diện</Button>}
        result={finishResult}
        title={`Đã học ${learnedCount} từ mới`}
      />
    )
  }

  if (state === 'ready' && item && onAudioFailed && onContinue && onKnown) {
    return (
      <div className="space-y-5">
        <SessionProgress current={current} total={total} />
        <StudyCard
          item={item}
          key={item.no}
          onAudioFailed={onAudioFailed}
          onContinue={onContinue}
          onKnown={onKnown}
          onReplayCountChange={onReplayCountChange}
          pending={pending}
        />
      </div>
    )
  }

  return null
}

export type TopicStudyViewProps = {
  activeStep: TopicStep
  canResumeStudy?: boolean
  error?: LoadError
  onRetry: () => void
  state: PageStateValue
  stepPanel?: ReactNode
  studyPanel?: ReactNode
  topic?: TopicDetail
}

export function TopicStudyView({
  activeStep,
  canResumeStudy = false,
  error,
  onRetry,
  state,
  stepPanel,
  studyPanel,
  topic,
}: TopicStudyViewProps) {
  return (
    <PageState
      empty={
        <EmptyState
          action={
            <Button to="/learn/topics" variant="outline">
              Về danh sách chủ đề
            </Button>
          }
          description="Chủ đề này không tồn tại hoặc chưa có dữ liệu học."
          title="Không tìm thấy chủ đề"
        />
      }
      error={
        <ErrorState
          message={error?.message ?? 'Vui lòng thử lại sau.'}
          onRetry={onRetry}
          requestId={error?.requestId}
        />
      }
      loading={
        <div aria-label="Đang tải chủ đề" className="space-y-4">
          <Skeleton variant="card" />
          <Skeleton variant="card" />
        </div>
      }
      state={state}
    >
      {topic ? (
        <div>
          <Link className="text-label text-brand-700 hover:underline" to="/learn/topics">
            ← Danh sách chủ đề
          </Link>

          <header className="mt-4 rounded-md border border-line bg-surface p-5">
            <div className="flex flex-wrap items-start justify-between gap-3">
              <h1 className="text-h1-sm text-ink sm:text-h1">
                <TopicTitle name={topic.name} />
              </h1>
              <LevelBadge exact level={topic.hskLevel} />
            </div>
            <p className="mt-3 text-body-sm text-ink-secondary">
              Đã học {topic.learnedWords}/{topic.totalWords} từ
            </p>
            <Progress className="mt-2" label={`${topic.percent}%`} value={topic.percent} />
          </header>

          <nav aria-label="Các bước học chủ đề" className="mt-6">
            <ol className="grid gap-2 sm:grid-cols-2 lg:grid-cols-4">
              {STEPS.map((step, index) => {
                const blockedReason =
                  step === 'study' && canResumeStudy
                    ? undefined
                    : getTopicStepBlockedReason(topic, step)
                const active = step === activeStep
                const classes = cn(
                  'flex min-h-touch items-start gap-2 rounded-sm border px-3 py-2 text-label no-underline',
                  active
                    ? 'border-brand-600 bg-brand-50 text-brand-700'
                    : 'border-line bg-surface text-ink-secondary',
                  blockedReason && 'cursor-not-allowed text-ink-muted',
                )
                const content = (
                  <>
                    <span>{index + 1}.</span>
                    <span className="min-w-0 flex-1">
                      <span className="block">{STEP_LABELS[step]}</span>
                      {blockedReason ? (
                        <span className="mt-1 block text-caption font-normal">{blockedReason}</span>
                      ) : null}
                    </span>
                    {blockedReason ? (
                      <LockKeyhole aria-hidden="true" className="size-4 shrink-0" />
                    ) : null}
                  </>
                )

                return (
                  <li key={step}>
                    {blockedReason ? (
                      <span
                        aria-current={active ? 'step' : undefined}
                        aria-disabled="true"
                        className={classes}
                        title={blockedReason}
                      >
                        {content}
                      </span>
                    ) : (
                      <Link
                        aria-current={active ? 'step' : undefined}
                        className={classes}
                        to={topicStepPath(topic.id, step)}
                      >
                        {content}
                      </Link>
                    )}
                  </li>
                )
              })}
            </ol>
          </nav>

          <section aria-labelledby="topic-step-title" className="mt-6">
            <h2 className="mb-4 text-h2 text-ink" id="topic-step-title">
              {STEP_LABELS[activeStep]}
            </h2>
            {(activeStep === 'study' ? studyPanel : stepPanel) ??
              (getTopicStepBlockedReason(topic, activeStep) ? (
                <EmptyState
                  description={getTopicStepBlockedReason(topic, activeStep)}
                  icon={<LockKeyhole aria-hidden="true" className="size-5" />}
                  title="Bước này đang bị khoá"
                />
              ) : null)}
          </section>
        </div>
      ) : null}
    </PageState>
  )
}
