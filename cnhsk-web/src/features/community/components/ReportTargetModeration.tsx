import type { ReportedComment } from '../types'
import { ModeratorComments } from './PostModerationComments'

export function ReportTargetModeration({ target, userId, caseId, onChanged }: { target: ReportedComment; userId: number; caseId: number; onChanged: () => void }) {
  return <ModeratorComments key={`${target.postId}:${target.id}`} postId={target.postId} targetId={target.id} userId={userId} screenId="SCR-063" resourceId={caseId} onChanged={onChanged} />
}
