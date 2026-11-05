import { useRef, useState, type FormEvent } from 'react'

import type { ApiError, ApiQueryState } from '@/shared/api'
import {
  Button,
  DataTable,
  EmptyState,
  ErrorState,
  FormField,
  Input,
  PageHeader,
  Pagination,
  Section,
  Select,
  Skeleton,
  type DataTableColumn,
} from '@/shared/ui'

import type {
  LedgerFilter,
  LedgerPage,
  LedgerRow,
  Reconciliation,
  ReconciliationIssue,
  TransactionType,
} from '../types'

export interface CreditLedgerViewProps {
  state: ApiQueryState | 'idle'
  data?: LedgerPage
  error?: ApiError
  filter?: LedgerFilter
  page: number
  exportPending: boolean
  exportError?: ApiError
  reconcilePending: boolean
  reconcileError?: ApiError
  reconciliation?: Reconciliation
  onApply: (filter: LedgerFilter) => void
  onFilterEdit: () => void
  onPageChange: (page: number) => void
  onRetry: () => void
  onExport: () => Promise<void>
  onReconcile: () => Promise<void>
}

const transactionNames: Record<TransactionType, string> = {
  TOPUP: 'Nạp điểm',
  DEDUCT: 'Sử dụng điểm',
  REFUND: 'Hoàn điểm',
  ADJUSTMENT: 'Điều chỉnh',
  TEACHER_PAYOUT: 'Điểm chấm bài',
  SUBSCRIPTION: 'Kích hoạt gói',
}
const issueNames: Record<ReconciliationIssue['kind'], string> = {
  SEQ_GAP: 'Thứ tự giao dịch không liên tục',
  BALANCE_CHAIN_BROKEN: 'Số dư trước không khớp giao dịch liền trước',
  FIRST_BALANCE_NOT_ZERO: 'Số dư trước của giao dịch đầu khác 0',
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

function signed(value: number) {
  return value > 0 ? `+${value}` : value < 0 ? `−${Math.abs(value)}` : '0'
}

function calendarDay(value: string) {
  if (!/^\d{4}-\d{2}-\d{2}$/.test(value)) return undefined
  const instant = Date.parse(`${value}T00:00:00Z`)
  if (!Number.isFinite(instant) || new Date(instant).toISOString().slice(0, 10) !== value)
    return undefined
  return instant / 86_400_000
}

const columns: readonly DataTableColumn<LedgerRow>[] = [
  { id: 'id', header: 'Mã giao dịch', render: (row) => row.id },
  { id: 'email', header: 'Email đã che', render: (row) => row.maskedEmail },
  { id: 'seq', header: 'Thứ tự', numeric: true, render: (row) => row.seq },
  { id: 'type', header: 'Loại giao dịch', render: (row) => transactionNames[row.type] },
  { id: 'amount', header: 'Số điểm', numeric: true, render: (row) => signed(row.amount) },
  { id: 'before', header: 'Số dư trước', numeric: true, render: (row) => row.balanceBefore },
  { id: 'after', header: 'Số dư sau', numeric: true, render: (row) => row.balanceAfter },
  { id: 'reason', header: 'Nội dung', render: (row) => row.reason || '—' },
  {
    id: 'created',
    header: 'Thời gian (giờ Việt Nam)',
    render: (row) => (
      <time dateTime={row.createdAt}>{vietnamTime.format(new Date(row.createdAt))}</time>
    ),
  },
]
const issueColumns: readonly DataTableColumn<ReconciliationIssue>[] = [
  { id: 'email', header: 'Email đã che', render: (issue) => issue.maskedEmail },
  { id: 'user', header: 'Mã tài khoản', numeric: true, render: (issue) => issue.userId },
  { id: 'seq', header: 'Thứ tự', numeric: true, render: (issue) => issue.seq },
  { id: 'kind', header: 'Lỗi đối chiếu', render: (issue) => issueNames[issue.kind] },
]

export function CreditLedgerView({
  state,
  data,
  error,
  filter,
  page,
  exportPending,
  exportError,
  reconcilePending,
  reconcileError,
  reconciliation,
  onApply,
  onFilterEdit,
  onPageChange,
  onRetry,
  onExport,
  onReconcile,
}: CreditLedgerViewProps) {
  const [draft, setDraft] = useState({ from: '', to: '', type: '' })
  const [validation, setValidation] = useState<{ from?: string; to?: string }>({})
  const form = useRef<HTMLFormElement>(null)
  const matches =
    filter &&
    draft.from === filter.from &&
    draft.to === filter.to &&
    draft.type === (filter.type ?? '')

  const change = (name: 'from' | 'to' | 'type', value: string) => {
    onFilterEdit()
    setDraft((current) => ({ ...current, [name]: value }))
    setValidation({})
  }

  const apply = (event: FormEvent) => {
    event.preventDefault()
    const from = calendarDay(draft.from)
    const to = calendarDay(draft.to)
    const errors: typeof validation = {}
    if (from === undefined) errors.from = 'Chọn ngày bắt đầu hợp lệ.'
    if (to === undefined) errors.to = 'Chọn ngày kết thúc hợp lệ.'
    if (from !== undefined && to !== undefined) {
      if (to < from) errors.to = 'Ngày kết thúc phải từ ngày bắt đầu trở đi.'
      else if (to - from > 92) errors.to = 'Hai ngày được chọn không được cách nhau quá 92 ngày.'
    }
    setValidation(errors)
    const first = Object.keys(errors)[0]
    if (first) {
      ;(form.current?.elements.namedItem(first) as HTMLElement | null)?.focus()
      return
    }
    // The contract accepts Vietnam calendar dates, not UTC timestamps.
    onApply({
      from: draft.from,
      to: draft.to,
      ...(draft.type ? { type: draft.type as TransactionType } : {}),
    })
  }

  return (
    <div className="mx-auto max-w-content space-y-6">
      <PageHeader title="Sổ cái điểm" />
      <Section title="Bộ lọc sổ cái">
        <form ref={form} aria-label="Lọc sổ cái" noValidate onSubmit={apply}>
          <div className="grid gap-x-4 md:grid-cols-3">
            <FormField
              htmlFor="ledger-from"
              label="Từ ngày (giờ Việt Nam)"
              required
              error={validation.from}
            >
              <Input
                name="from"
                type="date"
                value={draft.from}
                onChange={(event) => change('from', event.target.value)}
              />
            </FormField>
            <FormField
              htmlFor="ledger-to"
              label="Đến ngày (giờ Việt Nam)"
              required
              error={validation.to}
            >
              <Input
                name="to"
                type="date"
                value={draft.to}
                onChange={(event) => change('to', event.target.value)}
              />
            </FormField>
            <FormField htmlFor="ledger-type" label="Loại giao dịch">
              <Select
                name="type"
                value={draft.type}
                onChange={(event) => change('type', event.target.value)}
              >
                <option value="">Tất cả loại giao dịch</option>
                {Object.entries(transactionNames).map(([value, label]) => (
                  <option key={value} value={value}>
                    {label}
                  </option>
                ))}
              </Select>
            </FormField>
          </div>
          <p className="mb-3 text-body-sm text-ink-secondary">
            Bao gồm cả ngày kết thúc theo giờ Việt Nam. Hai ngày được chọn cách nhau tối đa 92 ngày.
          </p>
          <Button type="submit">Lọc sổ cái</Button>
        </form>
      </Section>

      <Section
        title="Giao dịch điểm"
        actions={
          <Button
            variant="outline"
            loading={exportPending}
            disabled={!matches || state === 'loading' || state === 'error'}
            onClick={() => {
              void onExport()
            }}
          >
            Xuất CSV
          </Button>
        }
      >
        {filter ? (
          <p className="mb-3 text-body-sm text-ink-secondary">
            Đang xem từ {filter.from} đến hết {filter.to} (giờ Việt Nam)
            {filter.type ? ` · ${transactionNames[filter.type]}` : ''}.
          </p>
        ) : null}
        {filter && !matches ? (
          <p className="mb-3 text-body-sm text-ink-secondary">
            Bấm Lọc sổ cái để áp dụng thay đổi trước khi xuất CSV.
          </p>
        ) : null}
        {exportError ? (
          <div role="alert" className="mb-4">
            <ErrorState
              title="Chưa xuất được CSV"
              requestId={exportError.requestId}
              message={
                exportError.errorCode === 'EXPORT_TOO_LARGE'
                  ? 'Phạm vi có hơn 10.000 giao dịch. Thu hẹp khoảng ngày hoặc chọn loại giao dịch rồi xuất lại.'
                  : 'Chưa tải được file CSV. Kiểm tra kết nối, quyền truy cập và bộ lọc trước khi thử lại.'
              }
            />
          </div>
        ) : null}
        {state === 'idle' ? (
          <p className="text-body text-ink-secondary">
            Chọn ngày bắt đầu và ngày kết thúc để xem sổ cái.
          </p>
        ) : state === 'loading' ? (
          <div role="status" aria-label="Đang tải sổ cái">
            <Skeleton variant="table" />
          </div>
        ) : state === 'error' ? (
          <ErrorState
            title="Chưa tải được sổ cái"
            message="Vui lòng thử lại với phạm vi đã chọn."
            requestId={error?.requestId}
            onRetry={onRetry}
          />
        ) : state === 'empty' ? (
          <EmptyState
            title="Không có giao dịch trong khoảng đã chọn"
            description="Hãy thử một khoảng ngày hoặc loại giao dịch khác."
          />
        ) : (
          <DataTable
            caption="Sổ cái điểm"
            columns={columns}
            rows={data?.items ?? []}
            rowKey={(row) => row.id}
          />
        )}
        {data && (state === 'ready' || state === 'empty') ? (
          <Section
            title="Tổng điểm"
            subtitle="Số liệu server trả cho toàn bộ bộ lọc, gồm mọi trang."
          >
            <dl className="grid gap-4 text-body text-ink md:grid-cols-3">
              <div>
                <dt>Số giao dịch</dt>
                <dd>{data.totals.count}</dd>
              </div>
              <div>
                <dt>Tổng điểm cộng</dt>
                <dd>{signed(data.totals.credited)}</dd>
              </div>
              <div>
                <dt>Tổng điểm trừ</dt>
                <dd>{signed(data.totals.debited)}</dd>
              </div>
            </dl>
          </Section>
        ) : null}
        {data && data.total > 0 ? (
          <Pagination
            page={page + 1}
            size={20}
            total={data.total}
            onChange={(next) => onPageChange(next - 1)}
          />
        ) : null}
      </Section>

      <Section
        title="Đối chiếu toàn sổ"
        subtitle="Kiểm tra toàn bộ sổ cái, không phụ thuộc bộ lọc ngày hoặc loại giao dịch."
        actions={
          <Button
            variant="outline"
            loading={reconcilePending}
            onClick={() => {
              void onReconcile()
            }}
          >
            Đối chiếu
          </Button>
        }
      >
        {reconcilePending ? (
          <p role="status" className="text-body-sm text-ink-secondary">
            Đang đối chiếu toàn sổ…
          </p>
        ) : reconcileError ? (
          <ErrorState
            title="Chưa đối chiếu được sổ cái"
            message="Vui lòng thử lại để nhận kết quả từ máy chủ."
            requestId={reconcileError.requestId}
            onRetry={() => {
              void onReconcile()
            }}
          />
        ) : reconciliation ? (
          <section aria-label="Kết quả đối chiếu toàn sổ" className="space-y-4">
            <dl className="grid gap-4 text-body text-ink md:grid-cols-2">
              <div>
                <dt>Số tài khoản</dt>
                <dd>{reconciliation.users}</dd>
              </div>
              <div>
                <dt>Số dòng giao dịch</dt>
                <dd>{reconciliation.rows}</dd>
              </div>
              <div>
                <dt>Tổng số dư cuối</dt>
                <dd>{reconciliation.totalBalance}</dd>
              </div>
              <div>
                <dt>Tổng điểm giao dịch</dt>
                <dd>{signed(reconciliation.totalAmount)}</dd>
              </div>
            </dl>
            {reconciliation.issues.length ? (
              <>
                <p role="status" className="text-body text-danger">
                  Phát hiện {reconciliation.issues.length} lỗi cần kiểm tra. Không có giao dịch nào
                  được tự sửa.
                </p>
                <DataTable
                  caption="Lỗi đối chiếu toàn sổ"
                  columns={issueColumns}
                  rows={reconciliation.issues}
                  rowKey={(issue) => `${issue.userId}-${issue.seq}-${issue.kind}`}
                />
              </>
            ) : (
              <p role="status" className="text-body text-ink">
                Không phát hiện lỗi đối chiếu trong toàn sổ.
              </p>
            )}
          </section>
        ) : (
          <p className="text-body-sm text-ink-secondary">
            Bấm Đối chiếu để kiểm tra chuỗi giao dịch từ máy chủ.
          </p>
        )}
      </Section>
    </div>
  )
}
