import { getMyWallet } from '@/features/credits/api/wallet'
import { useApiQuery } from '@/shared/api'
import { ReminderBanner } from '@/features/reminder/components/ReminderBanner'

import { getMyMistakes } from '@/features/exam/api/targetedPractice'
import { PlacementInviteBanner } from '@/features/exam/components/PlacementInviteBanner'

import { HskSkillsPanel } from '../components/HskSkillsPanel'
import { MistakePracticePanel } from '../components/MistakePracticePanel'
import { MasteryDashboardView } from '../components/MasteryDashboardView'
import { getMyRecommendations, getMySkillMastery } from '../api/mastery'
import { getReviewSummary } from '../api/reviews'

export default function Scr010LearningDashboardPage() {
  const recommendations = useApiQuery(getMyRecommendations, [])
  const review = useApiQuery(getReviewSummary, [])
  // The skills panel still works without the wallet; it only adds the remaining lessons for free accounts.
  const wallet = useApiQuery(getMyWallet, [])
  // The dashboard covers the whole HSK 1–3 range the learning area teaches.
  const skills = useApiQuery((signal) => getMySkillMastery(3, signal), [])
  const mistakes = useApiQuery(getMyMistakes, [])

  return (
    <MasteryDashboardView
      notices={
        <>
          <ReminderBanner />
          <PlacementInviteBanner />
        </>
      }
      onRetryRecommendations={recommendations.reload}
      onRetryReview={review.reload}
      recommendations={recommendations.data}
      recommendationsState={recommendations.state}
      reviewState={review.state}
      reviewSummary={review.data}
      skills={<HskSkillsPanel mastery={skills.data} wallet={wallet.data} />}
      mistakes={
        <MistakePracticePanel
          onRetry={mistakes.reload}
          state={mistakes.state}
          summary={mistakes.data}
        />
      }
    />
  )
}
