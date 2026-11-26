import { ArrowRight } from 'lucide-react'
import type { ReactNode } from 'react'

import type { ApiQueryState } from '@/shared/api'
import { Button, EmptyState, ErrorState, Skeleton, StatusBadge, type StatusTone } from '@/shared/ui'

import { formatMasteryDateTime } from '../formatDateTime'
import type { Recommendation, ReviewSummary } from '../types'
import './MasteryDashboardView.css'

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

/** "Thứ Năm, 27 tháng 11" in Vietnam time, so the page reads as today's desk. */
function todayLabel(): string {
  const text = new Intl.DateTimeFormat('vi-VN', {
    timeZone: 'Asia/Ho_Chi_Minh',
    weekday: 'long',
    day: 'numeric',
    month: 'long',
  }).format(new Date())
  return text.charAt(0).toUpperCase() + text.slice(1)
}

/** A square name seal reading 温故知新, "review the old to learn the new". */
function ReviewSeal() {
  return (
    <div aria-hidden="true" className="learn-seal">
      <span>温</span>
      <span>故</span>
      <span>知</span>
      <span>新</span>
    </div>
  )
}

const onDark = 'border-transparent bg-surface text-brand-800 hover:bg-brand-50 hover:text-brand-800'
const onDarkOutline =
  'border-surface/40 bg-transparent text-surface hover:border-surface hover:bg-surface/10 hover:text-surface'

function ReviewPanel({
  reviewSummary,
  reviewState,
  onRetryReview,
}: Pick<MasteryDashboardViewProps, 'reviewSummary' | 'reviewState' | 'onRetryReview'>) {
  const dueCount = reviewSummary?.dueCount ?? 0

  return (
    <section
      aria-labelledby="review-summary-title"
      className="learn-review learn-rise relative flex min-w-0 flex-col justify-between gap-6 overflow-hidden rounded-lg bg-brand-800 p-6 text-surface sm:p-8"
    >
      <div className="relative flex min-w-0 items-start justify-between gap-4">
        <div className="min-w-0 space-y-2">
          <h2
            className="text-label uppercase tracking-wider text-accent-500"
            id="review-summary-title"
          >
            Ôn tập
          </h2>
          {reviewState === 'loading' ? <Skeleton variant="text" /> : null}
          {reviewState === 'error' ? (
            <div className="rounded-md bg-surface p-3 text-ink">
              <ErrorState message="Không tải được lịch ôn tập." onRetry={onRetryReview} />
            </div>
          ) : null}
          {reviewState === 'ready' && reviewSummary ? (
            <>
              <p className="text-h1-sm tabular-nums sm:text-h1">{dueCount} mục đến hạn</p>
              {dueCount === 0 && reviewSummary.nextDueAt ? (
                <p className="text-body-sm text-surface/80">
                  Lần ôn tiếp theo: {formatMasteryDateTime(reviewSummary.nextDueAt)}
                </p>
              ) : null}
              {dueCount === 0 && reviewSummary.nextDueAt === null ? (
                <p className="text-body-sm text-surface/80">Chưa có lịch ôn tiếp theo</p>
              ) : null}
              {dueCount > 0 ? (
                <p className="text-body-sm text-surface/80">
                  Ôn đúng hạn giúp nhớ lâu mà không phải học lại từ đầu.
                </p>
              ) : null}
            </>
          ) : null}
        </div>
        <ReviewSeal />
      </div>
      <div className="relative flex flex-wrap gap-3">
        {reviewState === 'ready' && dueCount > 0 ? (
          <Button className={onDark} to="/learn/review">
            Ôn ngay
          </Button>
        ) : null}
        {reviewState === 'ready' && dueCount === 0 && reviewSummary?.nextDueAt ? (
          <Button className={onDark} to="/learn/review?ahead=true">
            Ôn trước
          </Button>
        ) : null}
        <Button className={onDarkOutline} to="/learn/progress" variant="outline">
          Xem tiến độ
        </Button>
      </div>
    </section>
  )
}

function RecommendationRow({
  recommendation,
  order,
}: {
  recommendation: Recommendation
  order: number
}) {
  const kind = KIND_LABELS[recommendation.kind]

  return (
    <li className="group flex min-w-0 items-start gap-4 px-5 py-4 transition-colors hover:bg-surface-subtle sm:items-center sm:px-6">
      <span
        aria-hidden="true"
        className="mt-0.5 w-6 shrink-0 text-h3 tabular-nums text-brand-600 sm:mt-0"
      >
        {order}
      </span>
      <div className="flex min-w-0 flex-1 flex-col gap-3 sm:flex-row sm:items-center sm:gap-6">
        <div className="min-w-0 flex-1 space-y-1">
          <div className="flex flex-wrap items-center gap-x-3 gap-y-1">
            <h3 className="break-words text-body font-semibold text-ink">
              {recommendation.nameVi}
            </h3>
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
          <ArrowRight
            aria-hidden="true"
            className="ml-1 inline-block size-4 align-middle transition-transform motion-safe:group-hover:translate-x-0.5"
          />
        </Button>
      </div>
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
  mistakes,
  notices,
}: MasteryDashboardViewProps & { skills?: ReactNode; mistakes?: ReactNode; notices?: ReactNode }) {
  const shown = recommendations?.slice(0, MAX_RECOMMENDATIONS) ?? []

  return (
    <main className="learn-desk mx-auto w-full min-w-0 max-w-content space-y-8 px-4 py-6 pb-16 sm:px-6 lg:px-8">
      <header className="learn-rise flex min-w-0 flex-wrap items-end justify-between gap-x-6 gap-y-2 border-b border-line pb-5">
        <div className="min-w-0 space-y-1">
          <p className="text-caption uppercase tracking-wider text-brand-700">{todayLabel()}</p>
          <h1 className="text-h1-sm text-ink sm:text-h1">Học tập</h1>
        </div>
        <p className="text-body-sm text-ink-secondary">Một chút mỗi ngày, thêm vững tiếng Trung.</p>
      </header>

      {notices ? <div className="space-y-4 empty:hidden">{notices}</div> : null}

      <div className="grid min-w-0 gap-4 lg:grid-cols-[minmax(0,7fr)_minmax(0,5fr)]">
        <ReviewPanel
          onRetryReview={onRetryReview}
          reviewState={reviewState}
          reviewSummary={reviewSummary}
        />
        {mistakes}
      </div>

      {skills}

      <section
        aria-labelledby="today-recommendations-heading"
        className="learn-rise space-y-3"
        style={{ animationDelay: '160ms' }}
      >
        <div className="flex flex-wrap items-end justify-between gap-2">
          <h2 className="text-h3 text-ink" id="today-recommendations-heading">
            Gợi ý hôm nay
          </h2>
          {recommendationsState === 'ready' && shown.length > 0 ? (
            <p className="text-body-sm text-ink-secondary">Xếp theo thứ tự nên học trước</p>
          ) : null}
        </div>

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
          <ol className="min-w-0 divide-y divide-line overflow-hidden rounded-lg border border-line bg-surface">
            {shown.map((recommendation, index) => (
              <RecommendationRow
                key={recommendation.kid}
                order={index + 1}
                recommendation={recommendation}
              />
            ))}
          </ol>
        ) : null}
      </section>
    </main>
  )
}
