import { getMyWallet } from '@/features/credits/api/wallet'
import { useApiQuery } from '@/shared/api'
import { ReminderBanner } from '@/features/reminder/components/ReminderBanner'

import { HskSkillsPanel } from '../components/HskSkillsPanel'
import { MasteryDashboardView } from '../components/MasteryDashboardView'
import { getMyRecommendations } from '../api/mastery'
import { getReviewSummary } from '../api/reviews'

export default function Scr010LearningDashboardPage() {
  const recommendations = useApiQuery(getMyRecommendations, [])
  const review = useApiQuery(getReviewSummary, [])
  // The skills panel still works without the wallet; it only adds the remaining lessons for free accounts.
  const wallet = useApiQuery(getMyWallet, [])

  return (
    <>
      <ReminderBanner />
      <MasteryDashboardView
        onRetryRecommendations={recommendations.reload}
        onRetryReview={review.reload}
        recommendations={recommendations.data}
        recommendationsState={recommendations.state}
        reviewState={review.state}
        reviewSummary={review.data}
        skills={<HskSkillsPanel wallet={wallet.data} />}
      />
    </>
  )
}
