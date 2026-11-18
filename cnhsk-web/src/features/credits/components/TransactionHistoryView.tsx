import { Coins } from 'lucide-react'

import type { ApiError, ApiQueryState } from '@/shared/api'
import {
  PageBanner,
  Button,
  Card,
  DataTable,
  EmptyState,
  ErrorState,
  PageState,
  Pagination,
  Section,
  Skeleton,
  type DataTableColumn,
} from '@/shared/ui'

import type { Transaction, TransactionPage, TransactionType } from '../types'

export interface TransactionHistoryViewProps {
  state: ApiQueryState
  data?: TransactionPage
  error?: ApiError
  page: number
  onPageChange: (page: number) => void
  onRetry: () => void
}

const pageSize = 20
const transactionNames: Record<TransactionType, string> = {
  TOPUP: 'Nạp điểm',
  DEDUCT: 'Sử dụng điểm',
  REFUND: 'Hoàn điểm',
  ADJUSTMENT: 'Điều chỉnh',
  TEACHER_PAYOUT: 'Điểm chấm bài',
  SUBSCRIPTION: 'Kích hoạt gói',
}
const vietnamTime = new Intl.DateTimeFormat('vi-VN', {
  timeZone: 'Asia/Ho_Chi_Minh',
  day: '2-digit',
  month: '2-digit',
  year: 'numeric',
  hour: '2-digit',
  minute: '2-digit',
  hourCycle: 'h23',
})
const columns: readonly DataTableColumn<Transaction>[] = [
  {
    id: 'amount',
    header: 'Số điểm',
    numeric: true,
    render: ({ amount }) => (
      <span className={amount < 0 ? 'font-semibold text-danger' : 'font-semibold text-ink'}>
        {amount > 0 ? `+${amount}` : amount < 0 ? `−${Math.abs(amount)}` : '0'}
      </span>
    ),
  },
  { id: 'type', header: 'Loại giao dịch', render: ({ type }) => transactionNames[type] },
  {
    id: 'before',
    header: 'Số dư trước',
    numeric: true,
    render: ({ balanceBefore }) => balanceBefore,
  },
  { id: 'after', header: 'Số dư sau', numeric: true, render: ({ balanceAfter }) => balanceAfter },
  {
    id: 'reason',
    header: 'Nội dung',
    render: ({ reason }) => <span className="break-words">{reason || '—'}</span>,
  },
  {
    id: 'time',
    header: 'Thời gian (giờ Việt Nam)',
    render: ({ createdAt }) => (
      <time dateTime={createdAt}>{vietnamTime.format(new Date(createdAt))}</time>
    ),
  },
]

export function TransactionHistoryView({
  state,
  data,
  error,
  page,
  onPageChange,
  onRetry,
}: TransactionHistoryViewProps) {
  return (
    <div className="mx-auto min-w-0 max-w-content space-y-6 px-4 py-6 pb-16 sm:px-6 lg:px-8">
      <PageBanner
        className="mb-0"
        title="Lịch sử giao dịch"
        subtitle="Giao dịch mới nhất trước · Thời gian theo giờ Việt Nam"
      />
      <Card className="flex flex-wrap items-center justify-between gap-3 border-brand-100 bg-brand-50 p-4 sm:px-5">
        <div className="flex items-center gap-3">
          <div className="flex size-10 shrink-0 items-center justify-center rounded-pill bg-brand-100 text-brand-700">
            <Coins aria-hidden="true" className="size-5" />
          </div>
          <div>
            <p className="text-label text-ink">Quản lý ví điểm & Gói dịch vụ</p>
            <p className="text-body-sm text-ink-secondary">
              Kiểm tra hạn mức sử dụng trong tháng hoặc nạp thêm mã thẻ mới.
            </p>
          </div>
        </div>
        <Button to="/account/credits" variant="outline">
          Về gói dịch vụ và điểm
        </Button>
      </Card>
      <Section title="Chi tiết giao dịch">
        <PageState
          state={state}
          loading={
            <div role="status" aria-label="Đang tải lịch sử giao dịch">
              <Skeleton variant="table" />
            </div>
          }
          error={
            <ErrorState
              title="Chưa tải được lịch sử giao dịch"
              message="Vui lòng thử lại để xem lịch sử."
              requestId={error?.requestId}
              onRetry={onRetry}
            />
          }
          empty={
            <EmptyState
              title="Chưa có giao dịch"
              description="Các giao dịch điểm của bạn sẽ xuất hiện tại đây."
            />
          }
        >
          <DataTable
            caption="Giao dịch điểm"
            columns={columns}
            rowKey={(row) => row.seq}
            rows={data?.items ?? []}
          />
        </PageState>
        {data && data.total > 0 ? (
          <Pagination
            page={page + 1}
            size={pageSize}
            total={data.total}
            onChange={(next) => onPageChange(next - 1)}
          />
        ) : null}
      </Section>
    </div>
  )
}
