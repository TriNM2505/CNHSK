import { BookOpenText, LockKeyhole } from 'lucide-react'
import type { ReactNode } from 'react'
import { Link } from 'react-router-dom'

import landscape from '@/features/auth/assets/auth-bg-002.webp'
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
import { TopicLockNotice } from './TopicLockNotice'

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
  if (containsHanzi(name))
    return (
      <HanziText className="break-keep text-h1-sm text-brand-800 sm:text-h1" size="md">
        {name}
      </HanziText>
    )
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
      <div className="mx-auto min-w-0 max-w-reading space-y-5 pr-8">
        <SessionProgress current={current} total={total} />
        <div className="rounded-lg border border-brand-100 border-t-4 border-t-brand-600 bg-gradient-to-b from-brand-50 to-surface p-2 shadow-md sm:p-3">
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
    <div className="mx-auto min-w-0 max-w-content p-4 sm:p-6 lg:p-8">
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
          <div className="min-w-0">
            <Link
              className="inline-flex min-h-touch items-center rounded-sm text-label text-brand-700 hover:underline focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus focus-visible:ring-offset-2"
              to="/learn/topics"
            >
              ← Danh sách chủ đề
            </Link>

            <header className="relative mt-2 overflow-hidden rounded-lg border border-brand-100 bg-surface p-4 shadow-sm sm:p-5">
              <div
                aria-hidden="true"
                className="pointer-events-none absolute right-0 top-0 h-32 w-32 overflow-hidden opacity-20"
              >
                <img
                  alt=""
                  className="h-80 w-full object-cover object-right-top"
                  height="1040"
                  src={landscape}
                  width="2048"
                />
              </div>
              <div className="relative">
                <div className="flex flex-wrap items-start justify-between gap-3">
                  <h1 className="min-w-0 break-words border-l-4 border-brand-600 pl-4 text-h1-sm text-ink sm:text-h1">
                    <TopicTitle name={topic.name} />
                  </h1>
                  <LevelBadge exact level={topic.hskLevel} />
                </div>
                <p className="mt-4 text-body-sm text-ink-secondary">
                  Đã học {topic.learnedWords}/{topic.totalWords} từ
                </p>
                <Progress className="mt-2" label={`${topic.percent}%`} value={topic.percent} />
                {topic.status === 'LOCKED' ? <TopicLockNotice lockedBy={topic.lockedBy} /> : null}
              </div>
            </header>

            {topic.status !== 'LOCKED' ? (
              <nav aria-label="Các bước học chủ đề" className="mt-4">
                <ol className="flex flex-wrap gap-3">
                  {STEPS.map((step, index) => {
                    const blockedReason =
                      step === 'study' && canResumeStudy
                        ? undefined
                        : getTopicStepBlockedReason(topic, step)
                    const active = step === activeStep
                    const classes = cn(
                      'flex h-full min-h-touch items-start gap-2 rounded-md border p-3 text-label no-underline focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus focus-visible:ring-offset-2',
                      active
                        ? 'border-brand-600 bg-brand-100/70 text-brand-800 shadow-sm'
                        : 'border-brand-100 bg-surface text-ink-secondary',
                      blockedReason && 'cursor-not-allowed text-ink-secondary',
                    )
                    const content = (
                      <>
                        <span
                          className={cn(
                            'flex size-6 shrink-0 items-center justify-center rounded-pill text-caption',
                            active ? 'bg-brand-600 text-surface' : 'bg-canvas text-ink-secondary',
                          )}
                        >
                          {index + 1}.
                        </span>
                        <span className="min-w-0 flex-1">
                          <span className="block">{STEP_LABELS[step]}</span>
                          {blockedReason ? (
                            <span className="mt-1 block text-caption font-normal">
                              {blockedReason}
                            </span>
                          ) : null}
                        </span>
                        {blockedReason ? (
                          <LockKeyhole aria-hidden="true" className="size-4 shrink-0" />
                        ) : null}
                      </>
                    )

                    return (
                      <li className="min-w-0 basis-36 grow" key={step}>
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
            ) : null}

            <section
              aria-labelledby="topic-step-title"
              className="mx-auto mt-6 min-w-0 max-w-reading"
            >
              <h2 className="mb-4 text-h2 text-ink" id="topic-step-title">
                {STEP_LABELS[activeStep]}
              </h2>
              {(activeStep === 'study' ? studyPanel : stepPanel) ??
                (topic.status !== 'LOCKED' && getTopicStepBlockedReason(topic, activeStep) ? (
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
    </div>
  )
}
