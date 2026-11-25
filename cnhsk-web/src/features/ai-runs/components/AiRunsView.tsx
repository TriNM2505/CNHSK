import type { ApiError, ApiQueryState } from '@/shared/api'
import {
  Button,
  DataTable,
  EmptyState,
  ErrorState,
  FormField,
  PageHeader,
  PageState,
  Pagination,
  Select,
  Skeleton,
  StatTile,
  StatusBadge,
  type DataTableColumn,
} from '@/shared/ui'
import type { AiRun, RunError, RunPage, RunStatus } from '../types'

const statuses = { RUNNING: 'Đang chạy', COMPLETED: 'Hoàn tất', FAILED: 'Thất bại' } as const
const errors: Record<RunError, string> = {
  INVALID_OUTPUT: 'Kết quả không hợp lệ',
  BUDGET_EXCEEDED: 'Vượt ngân sách token',
  PROVIDER_ERROR: 'Lỗi nhà cung cấp',
  NO_VALID_QUESTION: 'Không có câu hỏi hợp lệ',
  STALE: 'Lô quá hạn',
  EMBEDDING_UNAVAILABLE: 'Bộ lọc trùng nghĩa không chạy được',
}
const date = (value: string | null) =>
  value === null
    ? 'Chưa kết thúc'
    : new Intl.DateTimeFormat('vi-VN', {
        dateStyle: 'short',
        timeStyle: 'short',
        timeZone: 'Asia/Ho_Chi_Minh',
      }).format(new Date(value))
const number = (value: number) => value.toLocaleString('vi-VN')
const columns: DataTableColumn<AiRun>[] = [
  {
    id: 'id',
    header: 'Lô / kiến thức',
    render: (row) => (
      <div className="break-words">
        <p className="font-semibold">#{row.id}</p>
        <p className="text-caption text-ink-secondary">{row.kid || 'Không có mã kiến thức'}</p>
      </div>
    ),
  },
  {
    id: 'status',
    header: 'Trạng thái',
    render: (row) => (
      <StatusBadge
        tone={row.status === 'COMPLETED' ? 'success' : row.status === 'FAILED' ? 'danger' : 'info'}
      >
        {statuses[row.status]}
      </StatusBadge>
    ),
  },
  {
    id: 'time',
    header: 'Thời gian (VN)',
    render: (row) => (
      <div>
        <p>Bắt đầu: {date(row.createdAt)}</p>
        <p className="text-ink-secondary">Kết thúc: {date(row.completedAt)}</p>
      </div>
    ),
  },
  {
    id: 'questions',
    header: 'Câu hỏi',
    render: (row) => (
      <div>
        <p>{number(row.questionCount)} đã tạo</p>
        <p className="text-ink-secondary">{number(row.droppedCount)} loại bỏ</p>
      </div>
    ),
  },
  {
    id: 'tokens',
    header: 'Token',
    render: (row) => (
      <div>
        <p>{number(row.inputTokens)} đầu vào</p>
        <p className="text-ink-secondary">{number(row.outputTokens)} đầu ra</p>
      </div>
    ),
  },
  {
    id: 'error',
    header: 'Lỗi',
    render: (row) =>
      row.error ? (errors[row.error] ?? 'Lỗi khác') : row.status === 'FAILED' ? 'Lỗi khác' : '—',
  },
]

export type AiRunsViewProps = {
  data?: RunPage
  state: ApiQueryState
  error?: ApiError
  status: RunStatus | ''
  page: number
  busy: boolean
  notice?: string
  triggerError?: { message: string; requestId?: string }
  onStatus: (status: RunStatus | '') => void
  onPage: (page: number) => void
  onReload: () => void
  onTrigger: () => void
}
export function AiRunsView({
  data,
  state,
  error,
  status,
  page,
  busy,
  notice,
  triggerError,
  onStatus,
  onPage,
  onReload,
  onTrigger,
}: AiRunsViewProps) {
  const loaded = state === 'ready' || state === 'empty'
  return (
    <main className="mx-auto max-w-content space-y-6 p-4 sm:p-6">
      <PageHeader
        title="Lô AI"
        subtitle="Theo dõi lịch sử sinh câu hỏi và khởi chạy lô thủ công."
        actions={
          <>
            <Button variant="outline" onClick={onReload} disabled={state === 'loading'}>
              Làm mới
            </Button>
            <Button onClick={onTrigger} disabled={!loaded || !data?.enabled || busy} loading={busy}>
              Chạy lô mới
            </Button>
          </>
        }
      />
      {notice ? (
        <p role="status" className="rounded-md border border-info bg-info-bg p-4 text-info">
          {notice}
        </p>
      ) : null}
      {triggerError ? (
        <ErrorState
          title="Chưa xác nhận được lô mới"
          message={triggerError.message}
          requestId={triggerError.requestId}
        />
      ) : null}
      {loaded && data ? (
        <section aria-label="Tổng quan lô AI" className="grid gap-4 sm:grid-cols-2">
          <StatTile label="Token đã ghi nhận trong tháng" value={number(data.monthTokens)} />
          <div className="rounded-md border border-line bg-surface p-4">
            <StatusBadge tone={data.enabled ? 'success' : 'warning'}>
              {data.enabled ? 'Đã bật sinh câu hỏi' : 'Đã tắt sinh câu hỏi'}
            </StatusBadge>
            <p className="mt-3 text-body-sm text-ink-secondary">
              Theo tháng tạo lô, giờ Việt Nam. Có thể thiếu token khi lưu kết quả thất bại; đây
              không phải chi phí thanh toán.
            </p>
          </div>
        </section>
      ) : null}
      {loaded && data && data.enabled && !data.embeddingReady ? (
        <p role="status" className="rounded-md border border-warning bg-warning-bg p-4 text-ink">
          Bộ lọc trùng nghĩa chưa sẵn sàng nên lô mới sẽ tạm dừng. Máy chủ tự tải mô hình ở lần chạy
          tới; kiểm tra kết nối mạng nếu thông báo này còn sau vài giờ.
        </p>
      ) : null}
      {loaded && data && !data.enabled ? (
        <p className="text-body-sm text-ink-secondary">
          Chức năng chạy lô đang tắt. Bạn vẫn có thể xem lịch sử.
        </p>
      ) : null}
      <section aria-label="Bộ lọc lịch sử" className="rounded-md border border-line bg-surface p-4">
        <FormField htmlFor="run-status" label="Trạng thái" className="mb-0 sm:max-w-xs">
          <Select
            value={status}
            onChange={(event) => onStatus(event.target.value as RunStatus | '')}
          >
            <option value="">Tất cả trạng thái</option>
            {Object.entries(statuses).map(([value, label]) => (
              <option key={value} value={value}>
                {label}
              </option>
            ))}
          </Select>
        </FormField>
      </section>
      <PageState
        state={state}
        loading={
          <div aria-live="polite">
            <p className="text-body-sm text-ink-secondary">Đang tải lịch sử…</p>
            <Skeleton variant="table" />
          </div>
        }
        error={
          <ErrorState
            message="Không thể tải lịch sử lô. Vui lòng thử lại."
            requestId={error?.requestId}
            onRetry={onReload}
          />
        }
        empty={
          <EmptyState
            title="Chưa có lô phù hợp"
            description="Lịch sử sẽ xuất hiện sau khi một lô được ghi nhận."
            action={
              page > 0 ? (
                <Button onClick={() => onPage(0)} variant="outline">
                  Về trang đầu
                </Button>
              ) : undefined
            }
          />
        }
      >
        <DataTable
          caption="Lịch sử lô AI"
          columns={columns}
          rowKey={(row) => row.id}
          rows={data?.items ?? []}
        />
      </PageState>
      {loaded && data && data.totalElements > 0 ? (
        <Pagination
          page={page + 1}
          size={data.size}
          total={data.totalElements}
          onChange={(value) => onPage(value - 1)}
        />
      ) : null}
    </main>
  )
}
