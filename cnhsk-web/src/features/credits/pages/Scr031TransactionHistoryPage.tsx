import { useState } from 'react'

import { useApiQuery } from '@/shared/api'
import { useCurrentUser } from '@/shared/auth'
import { ErrorState, PageHeader } from '@/shared/ui'

import { listMyTransactions } from '../api/wallet'
import { TransactionHistoryView } from '../components/TransactionHistoryView'

function TransactionHistoryContent() {
  const [page, setPage] = useState(0)
  const transactions = useApiQuery(
    (signal) => listMyTransactions({ page, size: 20 }, signal),
    [page],
    { isEmpty: (data) => data.items.length === 0 },
  )
  return (
    <TransactionHistoryView
      state={transactions.state}
      data={transactions.data}
      error={transactions.error}
      page={page}
      onPageChange={setPage}
      onRetry={transactions.reload}
    />
  )
}

export default function Scr031TransactionHistoryPage() {
  const auth = useCurrentUser()
  if (auth.status !== 'authenticated' || !auth.user?.roles.includes('LEARNER')) {
    return (
      <div className="space-y-4">
        <PageHeader title="Lịch sử giao dịch" />
        {auth.status === 'loading' ? (
          <p role="status">Đang kiểm tra quyền truy cập…</p>
        ) : (
          <ErrorState message="Cần đăng nhập với quyền học viên để xem lịch sử giao dịch." />
        )}
      </div>
    )
  }
  return <TransactionHistoryContent key={auth.user.id} />
}
