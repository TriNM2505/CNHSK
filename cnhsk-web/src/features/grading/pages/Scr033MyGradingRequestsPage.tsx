import { useState } from 'react'
import { useApiQuery } from '@/shared/api'
import { useCurrentUser } from '@/shared/auth'
import { listMyGradingRequests } from '../api/requests'
import { MyGradingRequestsView } from '../components/MyGradingRequestsView'
import type { RequestStatus } from '../types'

function Requests() {
  const [status, setStatus] = useState<RequestStatus | ''>('')
  const [page, setPage] = useState(0)
  const query = useApiQuery(
    (signal) => listMyGradingRequests({ status: status || undefined, page, size: 20 }, signal),
    [status, page],
    { isEmpty: (result) => result.items.length === 0 },
  )
  return (
    <MyGradingRequestsView
      state={query.state}
      result={query.data}
      error={query.error}
      status={status}
      page={page}
      onStatus={(value) => {
        setStatus(value)
        setPage(0)
      }}
      onPage={setPage}
      onRetry={query.reload}
    />
  )
}

export default function Scr033MyGradingRequestsPage() {
  const auth = useCurrentUser()
  return auth.status === 'authenticated' && auth.user?.roles.includes('LEARNER') ? (
    <Requests key={auth.user.id} />
  ) : (
    <p role="status">Đang kiểm tra quyền truy cập…</p>
  )
}
