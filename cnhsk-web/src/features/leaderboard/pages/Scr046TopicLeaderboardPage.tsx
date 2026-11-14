import { useEffect } from 'react'
import { useSearchParams } from 'react-router-dom'

import { useApiQuery } from '@/shared/api'
import { useCurrentUser } from '@/shared/auth'

import { getTopicLeaderboard, listLeaderboardTopics } from '../api/leaderboards'
import { LeaderboardView } from '../components/LeaderboardView'
import { firstTopic, parseHsk, parsePeriod, parseTopicId } from '../query'
import type { Leaderboard } from '../types'

export default function Scr046TopicLeaderboardPage() {
  const [params, setParams] = useSearchParams()
  const { user } = useCurrentUser()
  const period = parsePeriod(params.get('period'))
  const hsk = parseHsk(params.get('hsk'))
  const topicId = parseTopicId(params.get('topicId'))
  const topicsQuery = useApiQuery((signal) => listLeaderboardTopics(hsk, signal), [hsk])
  const boardQuery = useApiQuery(
    (signal) =>
      topicId
        ? getTopicLeaderboard(topicId, period, signal)
        : Promise.resolve(undefined as unknown as Leaderboard),
    [topicId, period, user?.id],
  )

  useEffect(() => {
    if (topicsQuery.state !== 'ready' || topicId !== undefined) return
    const first = firstTopic(topicsQuery.data ?? [])
    if (first) {
      const next = new URLSearchParams(params)
      next.set('topicId', String(first.id))
      if (!next.has('period')) next.set('period', period)
      setParams(next, { replace: true })
    }
  }, [hsk, params, period, setParams, topicId, topicsQuery.data, topicsQuery.state])

  function updateParams(values: { hsk?: string; period?: string; topicId?: string }) {
    const next = new URLSearchParams(params)
    if (values.hsk === undefined) next.delete('hsk')
    else if (values.hsk) next.set('hsk', values.hsk)
    else next.delete('hsk')
    if (values.topicId !== undefined) {
      if (values.topicId) next.set('topicId', values.topicId)
      else next.delete('topicId')
    }
    if (values.period) next.set('period', values.period)
    setParams(next)
  }

  const state =
    topicsQuery.state === 'loading' && !topicsQuery.data
      ? 'loading'
      : topicsQuery.state === 'error'
        ? 'error'
        : !topicsQuery.data?.length
          ? 'empty'
          : topicId === undefined
            ? 'loading'
            : boardQuery.data === undefined && boardQuery.error === undefined
              ? 'loading'
              : boardQuery.state

  return (
    <LeaderboardView
      error={topicsQuery.state === 'error' ? topicsQuery.error : boardQuery.error}
      guest={!user}
      hsk={hsk}
      kind="topic"
      onHskChange={(value) => updateParams({ hsk: value, topicId: '' })}
      onPeriodChange={(value) => updateParams({ period: value })}
      onRetry={() => (topicsQuery.state === 'error' ? topicsQuery.reload() : boardQuery.reload())}
      onSelect={(value) => updateParams({ topicId: value })}
      period={period}
      selectedId={params.get('topicId') ?? ''}
      state={state}
      title="Bảng xếp hạng chủ đề"
      topicMissing={boardQuery.error?.errorCode === 'TOPIC_NOT_FOUND'}
      topics={topicsQuery.data}
      data={boardQuery.data}
    />
  )
}
