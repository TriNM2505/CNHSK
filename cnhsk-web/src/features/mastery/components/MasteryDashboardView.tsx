import type { ApiQueryState } from '@/shared/api'
import { Button, Card, ErrorState, PageHeader, Skeleton } from '@/shared/ui'

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

function RecommendationsSection({
  recommendations,
  state,
  onRetry,
}: {
  recommendations: Recommendation[] | undefined
  state: ApiQueryState
  onRetry: () => void
}) {
  return (
    <section aria-labelledby="today-recommendations-title" className="space-y-4">
      <h2 className="text-h2 text-ink" id="today-recommendations-title">
        Gợi ý hôm nay
      </h2>
      {state === 'loading' ? <Skeleton variant="card" /> : null}
      {state === 'error' ? (
        <ErrorState message="Không tải được gợi ý hôm nay." onRetry={onRetry} />
      ) : null}
      {state === 'ready' && recommendations?.length === 0 ? (
        <Card>
          <p className="text-body-sm text-ink-secondary">Hôm nay chưa có gợi ý phù hợp</p>
        </Card>
      ) : null}
      {state === 'ready' && recommendations?.length ? (
        <ul className="grid gap-4 md:grid-cols-2 xl:grid-cols-3">
          {recommendations.slice(0, 5).map((recommendation) => (
            <li key={recommendation.kid}>
              <Card className="flex h-full flex-col gap-3">
                <h3 className="text-h3 text-ink">{recommendation.nameVi}</h3>
                <p className="flex-1 text-body-sm text-ink-secondary">{recommendation.reason}</p>
                <Button to={`/learn/practice/targeted?kid=${encodeURIComponent(recommendation.kid)}`}>
                  Luyện
                </Button>
              </Card>
            </li>
          ))}
        </ul>
      ) : null}
    </section>
  )
}

function ReviewSection({
  summary,
  state,
  onRetry,
}: {
  summary: ReviewSummary | undefined
  state: ApiQueryState
  onRetry: () => void
}) {
  return (
    <section aria-labelledby="review-summary-title" className="space-y-4">
      <h2 className="text-h2 text-ink" id="review-summary-title">
        Ôn tập
      </h2>
      {state === 'loading' ? <Skeleton variant="card" /> : null}
      {state === 'error' ? (
        <ErrorState message="Không tải được lịch ôn tập." onRetry={onRetry} />
      ) : null}
      {state === 'ready' && summary ? (
        <Card className="flex flex-wrap items-center justify-between gap-4">
          <div className="space-y-2">
            <p className="text-h3 text-ink">{summary.dueCount} mục đến hạn</p>
            {summary.dueCount === 0 && summary.nextDueAt ? (
              <p className="text-body-sm text-ink-secondary">
                Lần ôn tiếp theo: {formatMasteryDateTime(summary.nextDueAt)}
              </p>
            ) : null}
            {summary.dueCount === 0 && summary.nextDueAt === null ? (
              <p className="text-body-sm text-ink-secondary">Chưa có lịch ôn tiếp theo</p>
            ) : null}
          </div>
          {summary.dueCount > 0 ? <Button to="/learn/review">Ôn ngay</Button> : null}
          {summary.dueCount === 0 && summary.nextDueAt ? (
            <Button to="/learn/review?ahead=true">Ôn trước</Button>
          ) : null}
        </Card>
      ) : null}
    </section>
  )
}

export function MasteryDashboardView({
  recommendations,
  recommendationsState,
  onRetryRecommendations,
  reviewSummary,
  reviewState,
  onRetryReview,
}: MasteryDashboardViewProps) {
  return (
    <main className="mx-auto w-full max-w-screen-xl space-y-8 px-4 py-6 sm:px-6 lg:px-8">
      <PageHeader subtitle="Chọn nội dung học và xem lịch ôn của bạn." title="Học tập" />
      <RecommendationsSection
        onRetry={onRetryRecommendations}
        recommendations={recommendations}
        state={recommendationsState}
      />
      <ReviewSection onRetry={onRetryReview} state={reviewState} summary={reviewSummary} />
    </main>
  )
}
