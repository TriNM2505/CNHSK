import { useApiQuery } from '@/shared/api'

import { MasteryDashboardView } from '../components/MasteryDashboardView'
import { getMyRecommendations } from '../api/mastery'
import { getReviewSummary } from '../api/reviews'

export default function Scr010LearningDashboardPage() {
  const recommendations = useApiQuery(getMyRecommendations, [])
  const review = useApiQuery(getReviewSummary, [])

  return (
    <MasteryDashboardView
      onRetryRecommendations={recommendations.reload}
      onRetryReview={review.reload}
      recommendations={recommendations.data}
      recommendationsState={recommendations.state}
      reviewState={review.state}
      reviewSummary={review.data}
    />
  )
}
