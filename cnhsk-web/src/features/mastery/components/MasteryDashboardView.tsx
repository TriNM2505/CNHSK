import { ArrowRight, BookOpen, CalendarCheck, Sparkles } from 'lucide-react'

import type { ApiQueryState } from '@/shared/api'
import {
  Button,
  Card,
  EmptyState,
  ErrorState,
  PageHeader,
  Section,
  Skeleton,
  StatTile,
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

export function MasteryDashboardView({
  recommendations,
  recommendationsState,
  reviewSummary,
  reviewState,
  onRetryRecommendations,
  onRetryReview,
}: MasteryDashboardViewProps) {
  return (
    <main className="mx-auto w-full min-w-0 max-w-content space-y-8 px-4 py-6 pb-16 sm:px-6 lg:px-8">
      <PageHeader subtitle="Một chút mỗi ngày, thêm vững tiếng Trung." title="Học tập" />

      <div className="grid min-w-0 gap-4 sm:grid-cols-2">
        {reviewState === 'ready' && reviewSummary ? (
          <StatTile label="Mục đến hạn ôn tập" value={reviewSummary.dueCount} />
        ) : reviewState === 'loading' ? (
          <Skeleton variant="card" />
        ) : null}
        {recommendationsState === 'ready' && recommendations ? (
          <StatTile label="Gợi ý học hôm nay" value={recommendations.slice(0, 5).length} />
        ) : recommendationsState === 'loading' ? (
          <Skeleton variant="card" />
        ) : null}
      </div>

      <Card
        role="region"
        aria-labelledby="review-summary-title"
        className="flex min-w-0 flex-wrap items-center justify-between gap-4 border-brand-100 bg-brand-50 p-6"
      >
        <div className="flex min-w-0 items-start gap-3">
          <CalendarCheck aria-hidden="true" className="mt-1 size-6 shrink-0 text-brand-700" />
          <div className="min-w-0 space-y-1">
            <h2 className="text-h3 text-ink" id="review-summary-title">
              Ôn tập
            </h2>
            {reviewState === 'ready' && reviewSummary ? (
              <>
                <p className="text-body text-ink">{reviewSummary.dueCount} mục đến hạn</p>
                {reviewSummary.dueCount === 0 && reviewSummary.nextDueAt ? (
                  <p className="text-body-sm text-ink-secondary">
                    Lần ôn tiếp theo: {formatMasteryDateTime(reviewSummary.nextDueAt)}
                  </p>
                ) : null}
                {reviewSummary.dueCount === 0 && reviewSummary.nextDueAt === null ? (
                  <p className="text-body-sm text-ink-secondary">Chưa có lịch ôn tiếp theo</p>
                ) : null}
              </>
            ) : null}
            {reviewState === 'loading' ? <Skeleton variant="text" /> : null}
            {reviewState === 'error' ? (
              <ErrorState message="Không tải được lịch ôn tập." onRetry={onRetryReview} />
            ) : null}
          </div>
        </div>
        <div className="flex w-full flex-wrap gap-3 sm:w-auto">
          {reviewState === 'ready' && reviewSummary && reviewSummary.dueCount > 0 ? (
            <Button to="/learn/review">
              Ôn ngay <ArrowRight aria-hidden="true" className="inline-block size-4 align-middle" />
            </Button>
          ) : null}
          {reviewState === 'ready' && reviewSummary?.dueCount === 0 && reviewSummary.nextDueAt ? (
            <Button to="/learn/review?ahead=true">Ôn trước</Button>
          ) : null}
          <Button to="/learn/progress" variant="outline">
            <BookOpen aria-hidden="true" className="mr-2 inline-block size-4 align-middle" />
            Xem tiến độ
          </Button>
        </div>
      </Card>

      <Section title="Gợi ý hôm nay">
        {recommendationsState === 'loading' ? <Skeleton variant="card" /> : null}
        {recommendationsState === 'error' ? (
          <ErrorState message="Không tải được gợi ý hôm nay." onRetry={onRetryRecommendations} />
        ) : null}
        {recommendationsState === 'ready' && recommendations?.length === 0 ? (
          <EmptyState
            title="Hôm nay chưa có gợi ý phù hợp"
            description="Bạn có thể xem lại tiến độ học tập của mình."
          />
        ) : null}
        {recommendationsState === 'ready' && recommendations?.length ? (
          <ul className="grid min-w-0 gap-4 md:grid-cols-2 xl:grid-cols-3">
            {recommendations.slice(0, 5).map((recommendation) => (
              <li className="min-w-0" key={recommendation.kid}>
                <Card className="flex h-full min-w-0 flex-col gap-4 p-6">
                  <Sparkles aria-hidden="true" className="size-5 text-brand-700" />
                  <h3 className="break-words text-h3 text-ink">{recommendation.nameVi}</h3>
                  <p className="flex-1 text-body-sm text-ink-secondary">{recommendation.reason}</p>
                  <Button
                    className="self-start"
                    to={`/learn/practice/targeted?kid=${encodeURIComponent(recommendation.kid)}`}
                    variant="outline"
                  >
                    Luyện <ArrowRight aria-hidden="true" className="inline-block size-4 align-middle" />
                  </Button>
                </Card>
              </li>
            ))}
          </ul>
        ) : null}
      </Section>
    </main>
  )
}
