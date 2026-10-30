import { useState } from 'react'
import { useCurrentUser } from '@/shared/auth'
import { cleanModeratorDrafts, readModeratorDraft, removeModeratorDraft, writeModeratorDraft, type ModeratorDraftIdentity } from './moderator-drafts'

export function useModeratorDraft(userId: number, screenId: ModeratorDraftIdentity['screenId'], resourceId: number, field: string) {
  const auth = useCurrentUser()
  const allowed = auth.status === 'authenticated' && auth.user?.id === userId && auth.user.roles.includes('MODERATOR') && Number.isSafeInteger(userId) && userId > 0 && Number.isSafeInteger(resourceId) && resourceId > 0
  const identity: ModeratorDraftIdentity = { userId, screenId, resourceId, field }
  // The owner is keyed by screen/resource/user in ModeratorSession; hydration never writes text.
  const [text, setLocalText] = useState(() => {
    if (!allowed) return ''
    cleanModeratorDrafts(userId)
    return readModeratorDraft(identity)
  })
  return {
    text: allowed ? text : '',
    setText: (value: string) => {
      if (!allowed) return
      setLocalText(value)
      writeModeratorDraft(identity, value)
    },
    clear: () => {
      if (!allowed) return
      setLocalText('')
      removeModeratorDraft(identity)
    },
  }
}
