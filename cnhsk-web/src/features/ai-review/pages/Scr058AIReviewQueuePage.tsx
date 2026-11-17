import { useEffect, useMemo, useRef, useState } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'

import { useApiMutation, useApiQuery } from '@/shared/api'
import { useCurrentUser } from '@/shared/auth'

import { bulkApproveReviews, listReviews } from '../api/reviews'
import { AIReviewQueueView } from '../components/AIReviewQueueView'
import {
  parseReviewQueueQuery,
  reviewDetailTo,
  reviewQueueParams,
  reviewQueueSearch,
} from '../reviewQueueQuery'
import type { BulkResult } from '../types'

function Queue() {
  const [search, setSearch] = useSearchParams()
  const navigate = useNavigate()
  const searchKey = search.toString()
  const filters = useMemo(() => parseReviewQueueQuery(new URLSearchParams(searchKey)), [searchKey])
  const query = useApiQuery(
    (signal) => listReviews(reviewQueueParams(filters), signal),
    [searchKey],
    { isEmpty: (data) => data.items.length === 0 },
  )
  const [selected, setSelected] = useState<number[]>([])
  const [result, setResult] = useState<BulkResult>()
  const controller = useRef(new AbortController())
  useEffect(() => {
    const current = new AbortController()
    controller.current = current
    return () => current.abort()
  }, [])
  const reload = query.reload
  useEffect(() => {
    const refresh = () => {
      setSelected([])
      reload()
    }
    window.addEventListener('focus', refresh)
    return () => window.removeEventListener('focus', refresh)
  }, [reload])
  useEffect(() => {
    setSelected([])
  }, [searchKey])
  useEffect(() => {
    if (query.state !== 'ready') {
      setSelected([])
      return
    }
    setSelected((ids) =>
      ids.filter((id) => query.data?.items.some((row) => row.id === id && row.viewedByMe)),
    )
  }, [query.data, query.state])
  useEffect(() => {
    if (query.data && (query.state === 'ready' || query.state === 'empty')) {
      const last = Math.max(0, Math.ceil(query.data.totalElements / filters.size) - 1)
      if (filters.page > last)
        setSearch(reviewQueueSearch({ ...filters, page: last }), { replace: true })
    }
  }, [query.data, query.state, filters, setSearch])
  const mutation = useApiMutation(async (ids: number[]) => {
    setResult(undefined)
    try {
      const report = await bulkApproveReviews(ids, controller.current.signal)
      if (!controller.current.signal.aborted) setResult(report)
      return report
    } finally {
      if (!controller.current.signal.aborted) {
        setSelected([])
        query.reload()
      }
    }
  })
  return (
    <AIReviewQueueView
      query={filters}
      data={query.data}
      state={query.state}
      error={query.error}
      selected={selected}
      result={result}
      bulkError={mutation.error}
      pending={mutation.pending}
      onQueryChange={(next) => {
        setSelected([])
        setSearch(reviewQueueSearch(next))
      }}
      onOpen={(id) => navigate(reviewDetailTo(id, filters))}
      onRetry={() => {
        setSelected([])
        query.reload()
      }}
      onSelect={(id, checked) => {
        if (
          mutation.pending ||
          query.state !== 'ready' ||
          !query.data?.items.some((row) => row.id === id && row.viewedByMe)
        )
          return
        setSelected((current) =>
          checked
            ? Array.from(new Set([...current, id])).slice(0, 50)
            : current.filter((value) => value !== id),
        )
      }}
      onBulkApprove={() => {
        if (mutation.pending || query.state !== 'ready' || selected.length === 0) return
        const eligible = selected.filter((id) =>
          query.data?.items.some((row) => row.id === id && row.viewedByMe),
        )
        if (eligible.length) void mutation.run(eligible).catch(() => undefined)
      }}
    />
  )
}

export default function Scr058AIReviewQueuePage() {
  const { user } = useCurrentUser()
  return <Queue key={`${user?.id}:${user?.roles.join(',')}`} />
}
