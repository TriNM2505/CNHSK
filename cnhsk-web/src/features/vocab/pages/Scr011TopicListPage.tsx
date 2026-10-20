import { useEffect, useMemo, useState } from 'react'
import { useSearchParams } from 'react-router-dom'

import { useApiQuery } from '@/shared/api'
import type { HskLevel } from '@/shared/ui'

import { getTopics } from '../api/topics'
import { TopicListView } from '../components/TopicListView'
import {
  filterTopics,
  parseTopicListSearchParams,
  withHskLevel,
  type TopicStatusFilter,
} from '../topicListQuery'
import type { TopicSummary } from '../types'

const EMPTY_TOPICS: readonly TopicSummary[] = []

export default function Scr011TopicListPage() {
  const [searchParams, setSearchParams] = useSearchParams()
  const parsedQuery = useMemo(() => parseTopicListSearchParams(searchParams), [searchParams])
  const [search, setSearch] = useState('')
  const [status, setStatus] = useState<TopicStatusFilter>('ALL')
  const topicsQuery = useApiQuery(
    (signal) => getTopics(parsedQuery.hskLevel, signal),
    [parsedQuery.hskLevel],
    { isEmpty: (data) => data.length === 0 },
  )
  const topics = topicsQuery.data ?? EMPTY_TOPICS
  const filteredTopics = useMemo(
    () => filterTopics(topics, search, status),
    [search, status, topics],
  )
  const stats = useMemo(
    () => ({
      completed: topics.filter((topic) => topic.status === 'COMPLETED').length,
      inProgress: topics.filter((topic) => topic.status === 'IN_PROGRESS').length,
      total: topics.length,
    }),
    [topics],
  )

  useEffect(() => {
    if (parsedQuery.needsCleanup) setSearchParams(parsedQuery.canonical, { replace: true })
  }, [parsedQuery, setSearchParams])

  const handleHskLevelChange = (hskLevel?: HskLevel) => {
    setSearchParams(withHskLevel(searchParams, hskLevel))
  }

  return (
    <TopicListView
      error={topicsQuery.error}
      hskLevel={parsedQuery.hskLevel}
      onClearClientFilters={() => {
        setSearch('')
        setStatus('ALL')
      }}
      onHskLevelChange={handleHskLevelChange}
      onRetry={topicsQuery.reload}
      onSearchChange={setSearch}
      onStatusChange={setStatus}
      search={search}
      state={topicsQuery.state}
      stats={stats}
      status={status}
      topics={filteredTopics}
    />
  )
}
