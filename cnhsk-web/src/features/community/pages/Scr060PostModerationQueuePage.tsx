import { useEffect } from 'react'
import { useSearchParams } from 'react-router-dom'

import { useApiQuery } from '@/shared/api'

import { listPendingPosts } from '../api/moderation'
import { ModerationFlash } from '../components/ModerationFlash'
import { ModeratorSession } from '../components/ModeratorSession'
import { PostModerationQueueView } from '../components/PostModerationQueueView'
import { MODERATION_PAGE_SIZE, parseModerationPage } from '../moderation-list-query'

function PendingPosts({
  onPageChange,
  page,
  userId,
}: {
  onPageChange: (page: number) => void
  page: number
  userId: number
}) {
  const query = useApiQuery(
    (signal) => listPendingPosts({ page, size: MODERATION_PAGE_SIZE }, signal),
    [page, userId],
    { isEmpty: (data) => data.items.length === 0 },
  )
  return (
    <>
      <ModerationFlash userId={userId} />
      <PostModerationQueueView
        data={query.data}
        error={query.error?.message}
        onPageChange={onPageChange}
        onRetry={query.reload}
        page={page}
        state={query.state}
      />
    </>
  )
}

export default function Scr060PostModerationQueuePage() {
  const [searchParams, setSearchParams] = useSearchParams()
  const page = parseModerationPage(searchParams.get('page'))
  useEffect(() => {
    if (
      !searchParams.has('page') ||
      (searchParams.getAll('page').length === 1 && searchParams.get('page') === String(page))
    )
      return
    const next = new URLSearchParams(searchParams)
    next.set('page', String(page))
    setSearchParams(next, { replace: true })
  }, [page, searchParams, setSearchParams])
  const onPageChange = (nextPage: number) => {
    const next = new URLSearchParams(searchParams)
    next.set('page', String(nextPage))
    setSearchParams(next)
  }
  return (
    <ModeratorSession sessionKey={`post-queue:${page}`}>
      {(userId) => (
        <PendingPosts
          key={`${userId}:${page}`}
          onPageChange={onPageChange}
          page={page}
          userId={userId}
        />
      )}
    </ModeratorSession>
  )
}
