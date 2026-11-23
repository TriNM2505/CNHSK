import { ArrowRight, CheckCircle2, Headphones, LockKeyhole, Sparkles } from 'lucide-react'

import {
  PageBanner,
  Button,
  Card,
  EmptyState,
  ErrorState,
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
  const bestPercent =
    typeof stage.bestAccuracy === 'number' && Number.isFinite(stage.bestAccuracy)
      ? Math.min(Math.max(stage.bestAccuracy, 0), 100)
      : 0

  return (
    <Card className="flex h-full min-w-0 flex-col gap-4 p-5">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div className="flex items-start gap-3">
          <div className="flex size-10 shrink-0 items-center justify-center rounded-md border border-brand-100 bg-brand-50 text-brand-700">
            <Headphones aria-hidden="true" className="size-5" />
          </div>
          <div>
            <p className="text-caption font-semibold uppercase tracking-wide text-brand-700">
              Tầng {stage.stageIndex}
            </p>
            <h2 className="mt-0.5 text-h3 text-ink">{stage.name}</h2>
          </div>
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
              className="rounded-pill border border-brand-100 bg-brand-50 px-3 py-1 font-hanzi text-caption font-semibold text-brand-700"
              key={sound}
            >
              {sound}
            </span>
          ))}
        </div>
      ) : null}

      <div className="mt-auto space-y-3 border-t border-line pt-4">
        <div className="space-y-1.5">
          <p className="flex items-center justify-between gap-2 text-body-sm text-ink-secondary">
            <span>Độ chính xác tốt nhất:</span>
            <strong className="tabular-nums text-ink">
              {formatPercentage(stage.bestAccuracy)}
            </strong>
          </p>
          <div aria-hidden="true" className="h-2 w-full overflow-hidden rounded-pill bg-line">
            <div
              className="h-full rounded-pill bg-brand-600"
              style={{ width: `${Math.round(bestPercent)}%` }}
            />
          </div>
        </div>

        {locked ? (
          <p className="flex items-start gap-2 rounded-md border border-brand-100 bg-brand-50 p-3 text-body-sm text-ink-secondary">
            <LockKeyhole aria-hidden="true" className="mt-0.5 size-4 shrink-0 text-brand-700" />
            <span>
              {previousStage
                ? `Hoàn thành tầng ${requiredStageIndex} với ≥ ${formatPercentage(previousStage.passPercent)}`
                : `Hoàn thành tầng ${requiredStageIndex} để mở tầng này`}
            </span>
          </p>
        ) : null}

        <div className="pt-1">
          <StageAction stage={stage} />
        </div>
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
  const completedCount = stages.filter((item) => item.state === 'COMPLETED').length
  const unlockedCount = stages.filter((item) => item.state !== 'LOCKED').length
  const nextStage =
    stages.find((item) => item.playable && item.state === 'AVAILABLE') ??
    stages.find((item) => item.playable && item.state === 'COMPLETED')

  return (
    <div className="mx-auto w-full min-w-0 max-w-content space-y-6 pb-16">
      <PageBanner
        subtitle="Nghe và phân biệt âm theo tám tầng, từ thanh mẫu đến biến điệu."
        title="Nghe phân biệt âm"
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
        <>
          <div className="grid min-w-0 gap-4 sm:grid-cols-2">
            <Card className="flex min-w-0 items-start justify-between gap-4 border-brand-100 bg-brand-50 p-5">
              <div className="flex min-w-0 items-start gap-4">
                <div className="flex size-12 shrink-0 items-center justify-center rounded-md border border-brand-100 bg-surface text-brand-600">
                  <Headphones aria-hidden="true" className="size-6" />
                </div>
                <div className="min-w-0">
                  <p className="text-h1 tabular-nums text-ink">
                    {unlockedCount}/{stages.length}
                  </p>
                  <p className="mt-0.5 text-label text-ink">Tầng đã mở khoá</p>
                  <p className="mt-0.5 text-body-sm text-ink-secondary">
                    Lộ trình luyện nghe tuần tự từng nhóm âm
                  </p>
                </div>
              </div>
              <span className="shrink-0 rounded-pill bg-brand-100 px-3 py-1 text-caption font-semibold text-brand-700">
                Đang mở
              </span>
            </Card>

            <Card className="flex min-w-0 items-start justify-between gap-4 border-brand-100 bg-brand-50 p-5">
              <div className="flex min-w-0 items-start gap-4">
                <div className="flex size-12 shrink-0 items-center justify-center rounded-md border border-brand-100 bg-surface text-success">
                  <CheckCircle2 aria-hidden="true" className="size-6" />
                </div>
                <div className="min-w-0">
                  <p className="text-h1 tabular-nums text-ink">{completedCount}</p>
                  <p className="mt-0.5 text-label text-ink">Tầng đã hoàn thành</p>
                  <p className="mt-0.5 text-body-sm text-ink-secondary">
                    Đạt chuẩn độ chính xác theo ngưỡng yêu cầu
                  </p>
                </div>
              </div>
              <span className="shrink-0 rounded-pill bg-success-bg px-3 py-1 text-caption font-semibold text-success">
                Đã đạt chuẩn
              </span>
            </Card>
          </div>

          {nextStage ? (
            <Card className="flex min-w-0 flex-wrap items-center justify-between gap-4 border-brand-100 bg-brand-50 p-5 sm:px-6">
              <div className="flex min-w-0 items-center gap-3.5">
                <div className="flex size-11 shrink-0 items-center justify-center rounded-pill bg-brand-100 text-brand-700">
                  <Sparkles aria-hidden="true" className="size-5" />
                </div>
                <div className="min-w-0 space-y-0.5">
                  <p className="text-label text-ink">
                    Gợi ý tiếp theo: Tầng {nextStage.stageIndex} — {nextStage.name}
                  </p>
                  <p className="text-body-sm text-ink-secondary">
                    Nghe âm mẫu và đạt tối thiểu {formatPercentage(nextStage.passPercent)} để mở
                    tầng kế tiếp.
                  </p>
                </div>
              </div>
              <Button size="sm" to="/learn/progress" variant="outline">
                Xem tiến độ
                <ArrowRight aria-hidden="true" className="ml-1 inline-block size-4 align-middle" />
              </Button>
            </Card>
          ) : null}

          <section aria-labelledby="pronunciation-stages-title" className="space-y-4">
            <div className="flex items-center justify-between gap-2">
              <div className="flex items-center gap-2">
                <Sparkles aria-hidden="true" className="size-5 text-brand-600" />
                <h2 className="text-h3 text-ink" id="pronunciation-stages-title">
                  Lộ trình tám tầng
                </h2>
              </div>
              <span className="text-body-sm text-ink-secondary">{stages.length} tầng</span>
            </div>
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
        </>
      ) : null}
    </div>
  )
}
