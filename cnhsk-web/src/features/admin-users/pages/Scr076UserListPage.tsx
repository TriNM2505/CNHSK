import { useEffect, useMemo, useState } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'

import { useApiQuery } from '@/shared/api'

import { getAdminUsers } from '../api/adminUsers'
import { UserListView } from '../components/UserListView'
import {
  ADMIN_USER_PAGE_SIZE,
  ADMIN_USER_SEARCH_DEBOUNCE_MS,
  parseAdminUserSearchParams,
  toAdminUserSearchParams,
} from '../userListQuery'

export default function Scr076UserListPage() {
  const navigate = useNavigate()
  const [searchParams, setSearchParams] = useSearchParams()
  const parsed = useMemo(() => parseAdminUserSearchParams(searchParams), [searchParams])
  const { query } = parsed
  const [queryDraft, setQueryDraft] = useState(query.q)

  useEffect(() => {
    if (parsed.needsCleanup) setSearchParams(parsed.canonical, { replace: true })
  }, [parsed.canonical, parsed.needsCleanup, setSearchParams])

  useEffect(() => setQueryDraft(query.q), [query.q])

  useEffect(() => {
    const normalizedDraft = queryDraft.trim()
    const valid = normalizedDraft.length === 0 || normalizedDraft.length >= 2
    if (!valid || normalizedDraft === query.q) return

    const timeoutId = window.setTimeout(() => {
      setSearchParams(
        toAdminUserSearchParams({ ...query, page: 0, q: normalizedDraft }, searchParams),
        { replace: true },
      )
    }, ADMIN_USER_SEARCH_DEBOUNCE_MS)

    return () => window.clearTimeout(timeoutId)
  }, [query, queryDraft, searchParams, setSearchParams])

  const result = useApiQuery(
    (signal) =>
      getAdminUsers(
        {
          page: query.page,
          q: query.q || undefined,
          role: query.role || undefined,
          size: ADMIN_USER_PAGE_SIZE,
          status: query.status || undefined,
        },
        signal,
      ),
    [query.page, query.q, query.role, query.status],
    { isEmpty: (data) => data.items.length === 0 },
  )

  const updateQuery = (next: typeof query) => {
    setSearchParams(toAdminUserSearchParams(next, searchParams))
  }

  return (
    <UserListView
      data={result.data}
      error={
        result.error
          ? { message: result.error.message, requestId: result.error.requestId }
          : undefined
      }
      onClearFilters={() => {
        setQueryDraft('')
        updateQuery({ page: 0, q: '', role: '', status: '' })
      }}
      onFirstPage={() => updateQuery({ ...query, page: 0 })}
      onOpenUser={(id) => navigate(`/admin/users/${id}`)}
      onPageChange={(page) => updateQuery({ ...query, page: page - 1 })}
      onQueryChange={setQueryDraft}
      onRetry={result.reload}
      onRoleChange={(role) => updateQuery({ ...query, page: 0, role })}
      onStatusChange={(status) => updateQuery({ ...query, page: 0, status })}
      query={query}
      queryDraft={queryDraft}
      queryError={
        queryDraft.trim().length === 1 ? 'Nhập ít nhất 2 ký tự hoặc để trống.' : undefined
      }
      state={result.state}
    />
  )
}
