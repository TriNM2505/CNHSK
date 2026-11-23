import { UserCheck, UserPlus } from 'lucide-react'
import { useEffect, useState } from 'react'

import { Button } from '@/shared/ui'

import { followUser, getCommunityProfile, unfollowUser } from '../api/follows'

/** "Theo dõi" beside an author's name; hidden for the author themself or when the profile cannot load. */
export function FollowAuthorButton({ authorId }: { authorId: number }) {
  const [following, setFollowing] = useState<boolean>()
  const [busy, setBusy] = useState(false)
  const [failed, setFailed] = useState(false)

  useEffect(() => {
    const controller = new AbortController()
    getCommunityProfile(authorId, controller.signal)
      .then((profile) => setFollowing(profile.self ? undefined : profile.followedByMe))
      .catch(() => undefined)
    return () => controller.abort()
  }, [authorId])

  if (following === undefined) return null

  async function toggle() {
    setBusy(true)
    setFailed(false)
    try {
      const state = following ? await unfollowUser(authorId) : await followUser(authorId)
      setFollowing(state.following)
    } catch {
      setFailed(true)
    } finally {
      setBusy(false)
    }
  }

  return (
    <span className="inline-flex items-center gap-2">
      <Button
        aria-pressed={following}
        disabled={busy}
        onClick={() => void toggle()}
        size="sm"
        variant={following ? 'outline' : 'primary'}
      >
        {following ? (
          <UserCheck aria-hidden="true" className="mr-1.5 inline-block size-4 align-middle" />
        ) : (
          <UserPlus aria-hidden="true" className="mr-1.5 inline-block size-4 align-middle" />
        )}
        {following ? 'Đang theo dõi' : 'Theo dõi'}
      </Button>
      {failed ? (
        <span className="text-caption text-danger" role="alert">
          Chưa lưu được, thử lại.
        </span>
      ) : null}
    </span>
  )
}
