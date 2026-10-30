import { Headphones, LockKeyhole } from 'lucide-react'

import {
  Button,
  Card,
  EmptyState,
  ErrorState,
  PageHeader,
  Skeleton,
  StatusBadge,
  type PageStateValue,
} from '@/shared/ui'

import { formatPercentage } from '../formatters'
import type { PronunciationStageSummary } from '../types'

type LoadError = { message: string; requestId?: string }

export interface PronunciationStageListViewProps {
  error?: LoadError
  onRetry: () => void
  stages: readonly PronunciationStageSummary[]
  state: PageStateValue
}

function StageAction({ stage }: { stage: PronunciationStageSummary }) {
  if (!stage.playable) return <Button disabled>Đang cập nhật</Button>
  if (stage.state === 'LOCKED') return <Button disabled>Đang khoá</Button>

  const path = `/learn/pronunciation/stages/${stage.stageIndex}`
  if (stage.activeSessionId) {
    return (
      <Button to={path}>
        Tiếp tục ({stage.activeAnswered ?? 0}/{stage.unitCount})
      </Button>
    )
  }
  return <Button to={path}>{stage.state === 'COMPLETED' ? 'Luyện lại' : 'Bắt đầu'}</Button>
}

function StageCard({
  previousStage,
  stage,
}: {
  previousStage?: PronunciationStageSummary
  stage: PronunciationStageSummary
}) {
  const locked = stage.state === 'LOCKED'
  const requiredStageIndex = Math.max(1, stage.stageIndex - 1)

  return (
    <Card className="flex h-full flex-col gap-4">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <p className="text-caption font-semibold uppercase tracking-wide text-brand-700">
            Tầng {stage.stageIndex}
          </p>
          <h2 className="mt-1 text-h3 text-ink">{stage.name}</h2>
        </div>
        <StatusBadge tone={locked ? 'neutral' : stage.state === 'COMPLETED' ? 'success' : 'info'}>
          {locked ? 'Khoá' : stage.state === 'COMPLETED' ? 'Hoàn thành' : 'Đang học'}
        </StatusBadge>
      </div>

      {stage.description ? (
        <p className="text-body-sm text-ink-secondary">{stage.description}</p>
      ) : null}

      {stage.targetSounds.length ? (
        <div aria-label={`Âm luyện tầng ${stage.stageIndex}`} className="flex flex-wrap gap-2">
          {stage.targetSounds.map((sound) => (
            <span
              className="rounded-pill border border-brand-100 bg-brand-50 px-3 py-1 text-caption text-brand-700"
              key={sound}
            >
              {sound}
            </span>
          ))}
        </div>
      ) : null}

      <div className="mt-auto space-y-3 border-t border-line pt-4">
        <p className="text-body-sm text-ink-secondary">
          Độ chính xác tốt nhất:{' '}
          <strong className="text-ink">{formatPercentage(stage.bestAccuracy)}</strong>
        </p>
        {locked ? (
          <p className="flex items-start gap-2 text-body-sm text-ink-secondary">
            <LockKeyhole aria-hidden="true" className="mt-0.5 size-4 shrink-0" />
            <span>
              {previousStage
                ? `Hoàn thành tầng ${requiredStageIndex} với ≥ ${formatPercentage(previousStage.passPercent)}`
                : `Hoàn thành tầng ${requiredStageIndex} để mở tầng này`}
            </span>
          </p>
        ) : null}
        <StageAction stage={stage} />
      </div>
    </Card>
  )
}

function LoadingStages() {
  return (
    <div aria-label="Đang tải các tầng phát âm" className="grid gap-4 sm:grid-cols-2">
      {[0, 1, 2, 3].map((item) => (
        <Skeleton className="h-64" key={item} />
      ))}
    </div>
  )
}

export function PronunciationStageListView({
  error,
  onRetry,
  stages,
  state,
}: PronunciationStageListViewProps) {
  return (
    <div>
      <PageHeader
        subtitle="Luyện nghe và phân biệt âm theo tám tầng, từ thanh mẫu đến biến điệu."
        title="Luyện phát âm"
      />

      {state === 'loading' ? <LoadingStages /> : null}
      {state === 'empty' ? (
        <EmptyState
          description="Các tầng luyện phát âm chưa có dữ liệu. Vui lòng quay lại sau."
          icon={<Headphones aria-hidden="true" className="size-5" />}
          title="Nội dung phát âm đang được cập nhật"
        />
      ) : null}
      {state === 'error' ? (
        <ErrorState
          message={error?.message ?? 'Vui lòng thử lại sau.'}
          onRetry={onRetry}
          requestId={error?.requestId}
          title="Không thể tải lộ trình phát âm"
        />
      ) : null}
      {state === 'ready' ? (
        <section aria-labelledby="pronunciation-stages-title">
          <h2 className="sr-only" id="pronunciation-stages-title">
            Lộ trình tám tầng
          </h2>
          <div className="grid gap-4 sm:grid-cols-2">
            {stages.map((stage) => (
              <StageCard
                key={stage.id}
                previousStage={stages.find(
                  (candidate) => candidate.stageIndex === stage.stageIndex - 1,
                )}
                stage={stage}
              />
            ))}
          </div>
        </section>
      ) : null}
    </div>
  )
}
