import { useState } from 'react'
import { useApiQuery } from '@/shared/api'
import { listContests } from '../api/contests'
import { ContestListView } from '../components/ContestListView'
import type { ScheduleState } from '../types'

export default function Scr048ContestListPage() {
  const [filter, setFilter] = useState<ScheduleState | ''>('')
  const [page, setPage] = useState(0)
  const query = useApiQuery(
    (signal) => listContests({ ...(filter ? { state: filter } : {}), page, size: 20 }, signal),
    [filter, page],
    { isEmpty: (result) => result.items.length === 0 },
  )
  return (
    <ContestListView
      state={query.state}
      result={query.data}
      error={query.error?.message}
      filter={filter}
      onFilter={(value) => {
        setFilter(value)
        setPage(0)
      }}
      onPage={setPage}
      onRetry={query.reload}
    />
  )
}
