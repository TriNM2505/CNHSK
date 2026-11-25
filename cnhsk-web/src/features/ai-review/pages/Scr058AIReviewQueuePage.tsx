import { useEffect, useMemo, useRef, useState } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'

import { useApiMutation, useApiQuery } from '@/shared/api'
import { useCurrentUser } from '@/shared/auth'

import { bulkApproveReviews, listReviews } from '../api/reviews'
import { AIReviewQueueView } from '../components/AIReviewQueueView'
import {
  canBulkSelect,
  parseReviewQueueQuery,
  reviewDetailTo,
  reviewQueueParams,
  reviewQueueSearch,
} from '../reviewQueueQuery'
import type { BulkResult } from '../types'

/** Ticks survive opening a question and coming back, so a teacher can open several and approve them together. */
function selectionKey(owner: string, searchKey: string) {
  return `ai-review:selected:${owner}:${searchKey}`
}

function readSelection(key: string): number[] {
  try {
    const stored: unknown = JSON.parse(window.sessionStorage.getItem(key) ?? '[]')
    return Array.isArray(stored)
      ? stored.filter((id): id is number => Number.isSafeInteger(id) && id > 0).slice(0, 50)
      : []
  } catch {
    return []
  }
}

function Queue({ owner }: { owner: string }) {
  const [search, setSearch] = useSearchParams()
  const navigate = useNavigate()
  const searchKey = search.toString()
  const filters = useMemo(() => parseReviewQueueQuery(new URLSearchParams(searchKey)), [searchKey])
  const query = useApiQuery(
    (signal) => listReviews(reviewQueueParams(filters), signal),
    [searchKey],
    { isEmpty: (data) => data.items.length === 0 },
  )
  const storageKey = selectionKey(owner, searchKey)
  const [selected, setSelected] = useState<number[]>(() => readSelection(storageKey))
  const [result, setResult] = useState<BulkResult>()
  const controller = useRef(new AbortController())
  useEffect(() => {
    const current = new AbortController()
    controller.current = current
    return () => current.abort()
  }, [])
  useEffect(() => {
    try {
      window.sessionStorage.setItem(storageKey, JSON.stringify(selected))
    } catch {
      /* Without storage the ticks simply last until the page is left. */
    }
  }, [storageKey, selected])
  const reload = query.reload
  useEffect(() => {
    // Coming back to the window refreshes the queue; ticks stay and are re-checked against the fresh rows.
    window.addEventListener('focus', reload)
    return () => window.removeEventListener('focus', reload)
  }, [reload])
  const firstSearch = useRef(searchKey)
  useEffect(() => {
    if (firstSearch.current === searchKey) return
    firstSearch.current = searchKey
    setSelected(readSelection(selectionKey(owner, searchKey)))
  }, [owner, searchKey])
  useEffect(() => {
    if (query.state === 'loading') return
    if (query.state !== 'ready') {
      setSelected([])
      return
    }
    setSelected((ids) =>
      ids.filter((id) => query.data?.items.some((row) => row.id === id && canBulkSelect(row))),
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
      onOpen={(id) =>
        navigate(reviewDetailTo(id, filters, query.data?.items.find((row) => row.id === id)?.step))
      }
      onRetry={() => {
        setSelected([])
        query.reload()
      }}
      onSelect={(id, checked) => {
        if (
          mutation.pending ||
          query.state !== 'ready' ||
          !query.data?.items.some((row) => row.id === id && canBulkSelect(row))
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
          query.data?.items.some((row) => row.id === id && canBulkSelect(row)),
        )
        if (eligible.length) void mutation.run(eligible).catch(() => undefined)
      }}
    />
  )
}

export default function Scr058AIReviewQueuePage() {
  const { user } = useCurrentUser()
  const owner = `${user?.id}:${user?.roles.join(',')}`
  return <Queue key={owner} owner={owner} />
}
