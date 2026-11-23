import { ArrowRight } from 'lucide-react'
import type { ReactNode } from 'react'

import type { ApiQueryState } from '@/shared/api'
import {
  Button,
  Card,
  EmptyState,
  ErrorState,
  PageBanner,
  Skeleton,
  StatusBadge,
  type StatusTone,
} from '@/shared/ui'

import { formatMasteryDateTime } from '../formatDateTime'
import type { Recommendation, ReviewSummary } from '../types'

export interface MasteryDashboardViewProps {
  recommendations: Recommendation[] | undefined
  recommendationsState: ApiQueryState
  onRetryRecommendations: () => void
  reviewSummary: ReviewSummary | undefined
  reviewState: ApiQueryState
  onRetryReview: () => void
}

const MAX_RECOMMENDATIONS = 5

/** Tên loại gợi ý theo cách người học hiểu, thay cho mã PREREQ/REVIEW/... của server. */
const KIND_LABELS: Record<Recommendation['kind'], { label: string; tone: StatusTone }> = {
  PREREQ: { label: 'Học nền trước', tone: 'warning' },
  REVIEW: { label: 'Cần ôn', tone: 'danger' },
  CONSOLIDATE: { label: 'Củng cố', tone: 'info' },
  NEW: { label: 'Phần mới', tone: 'success' },
}

function ReviewPanel({
  reviewSummary,
  reviewState,
  onRetryReview,
}: Pick<MasteryDashboardViewProps, 'reviewSummary' | 'reviewState' | 'onRetryReview'>) {
  const dueCount = reviewSummary?.dueCount ?? 0

  return (
    <Card
      aria-labelledby="review-summary-title"
      className="flex min-w-0 flex-wrap items-end justify-between gap-x-6 gap-y-4 p-5 sm:px-6"
      role="region"
    >
      <div className="min-w-0 space-y-1">
        <h2 className="text-label text-ink-secondary" id="review-summary-title">
          Ôn tập
        </h2>
        {reviewState === 'loading' ? <Skeleton variant="text" /> : null}
        {reviewState === 'error' ? (
          <ErrorState message="Không tải được lịch ôn tập." onRetry={onRetryReview} />
        ) : null}
        {reviewState === 'ready' && reviewSummary ? (
          <>
            <p className="text-h2 tabular-nums text-ink">{dueCount} mục đến hạn</p>
            {dueCount === 0 && reviewSummary.nextDueAt ? (
              <p className="text-body-sm text-ink-secondary">
                Lần ôn tiếp theo: {formatMasteryDateTime(reviewSummary.nextDueAt)}
              </p>
            ) : null}
            {dueCount === 0 && reviewSummary.nextDueAt === null ? (
              <p className="text-body-sm text-ink-secondary">Chưa có lịch ôn tiếp theo</p>
            ) : null}
          </>
        ) : null}
      </div>
      <div className="flex flex-wrap gap-3">
        {reviewState === 'ready' && dueCount > 0 ? (
          <Button to="/learn/review">Ôn ngay</Button>
        ) : null}
        {reviewState === 'ready' && dueCount === 0 && reviewSummary?.nextDueAt ? (
          <Button to="/learn/review?ahead=true">Ôn trước</Button>
        ) : null}
        <Button to="/learn/progress" variant="outline">
          Xem tiến độ
        </Button>
      </div>
    </Card>
  )
}

function RecommendationRow({ recommendation }: { recommendation: Recommendation }) {
  const kind = KIND_LABELS[recommendation.kind]

  return (
    <li className="flex min-w-0 flex-col gap-3 px-5 py-4 sm:flex-row sm:items-center sm:gap-6 sm:px-6">
      <div className="min-w-0 flex-1 space-y-1">
        <div className="flex flex-wrap items-center gap-x-3 gap-y-1">
          <h3 className="break-words text-h3 text-ink">{recommendation.nameVi}</h3>
          <StatusBadge tone={kind.tone}>{kind.label}</StatusBadge>
        </div>
        <p className="text-body-sm text-ink-secondary">{recommendation.reason}</p>
      </div>
      <Button
        className="shrink-0 self-start sm:self-center"
        size="sm"
        to={`/learn/practice/targeted?kid=${encodeURIComponent(recommendation.kid)}`}
        variant="outline"
      >
        Luyện
        <ArrowRight aria-hidden="true" className="ml-1 inline-block size-4 align-middle" />
      </Button>
    </li>
  )
}

export function MasteryDashboardView({
  recommendations,
  recommendationsState,
  reviewSummary,
  reviewState,
  onRetryRecommendations,
  onRetryReview,
  skills,
}: MasteryDashboardViewProps & { skills?: ReactNode }) {
  const shown = recommendations?.slice(0, MAX_RECOMMENDATIONS) ?? []

  return (
    <main className="mx-auto w-full min-w-0 max-w-content space-y-6 px-4 py-6 pb-16 sm:px-6 lg:px-8">
      <PageBanner subtitle="Một chút mỗi ngày, thêm vững tiếng Trung." title="Học tập" />

      <ReviewPanel
        onRetryReview={onRetryReview}
        reviewState={reviewState}
        reviewSummary={reviewSummary}
      />

      {skills}

      <section aria-labelledby="today-recommendations-heading" className="space-y-3">
        <h2 className="text-h3 text-ink" id="today-recommendations-heading">
          Gợi ý hôm nay
        </h2>

        {recommendationsState === 'loading' ? <Skeleton variant="card" /> : null}
        {recommendationsState === 'error' ? (
          <ErrorState message="Không tải được gợi ý hôm nay." onRetry={onRetryRecommendations} />
        ) : null}
        {recommendationsState === 'ready' && shown.length === 0 ? (
          <EmptyState
            title="Hôm nay chưa có gợi ý phù hợp"
            description="Bạn có thể xem lại tiến độ học tập của mình."
          />
        ) : null}
        {recommendationsState === 'ready' && shown.length > 0 ? (
          <Card className="min-w-0 p-0">
            <ul className="divide-y divide-line">
              {shown.map((recommendation) => (
                <RecommendationRow key={recommendation.kid} recommendation={recommendation} />
              ))}
            </ul>
          </Card>
        ) : null}
      </section>
    </main>
  )
}
