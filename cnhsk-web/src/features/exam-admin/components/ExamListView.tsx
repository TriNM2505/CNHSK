import { Link, useLocation } from 'react-router-dom'
import type { ApiQueryState } from '@/shared/api'
import {
  Button,
  Card,
  Skeleton,
  StatusBadge,
  DataTable,
  FormField,
  Input,
  PageHeader,
  Pagination,
  Select,
  SUPPORTED_HSK_LEVELS,
} from '@/shared/ui'
import { statusLabels } from '../examForm'
import type { AdminExam, ExamFilters, ExamPage, ExamStatus } from '../types'

export type ExamListViewProps = {
  state: ApiQueryState
  data?: ExamPage
  error?: string
  filters: ExamFilters
  busy?: boolean
  onFilter: (filters: ExamFilters) => void
  onRetry: () => void
  onAction: (exam: AdminExam, action: 'archive' | 'clone') => void
}
export function ExamListView({
  state,
  data,
  error,
  filters,
  busy,
  onFilter,
  onRetry,
  onAction,
}: ExamListViewProps) {
  const location = useLocation()
  const returnState = { examListSearch: location.search }
  return (
    <main className="mx-auto w-full min-w-0 max-w-content space-y-6 break-words px-4 py-6 pb-16 sm:px-6">
      <PageHeader
        title="Quản lý đề thi"
        subtitle="Tổ chức phần thi, chọn câu từ ngân hàng và công bố sau khi kiểm tra."
        actions={
          <Button to="/content/exams/new" state={returnState}>
            Tạo đề nháp
          </Button>
        }
      />
      <form
        className="grid min-w-0 items-end gap-x-4 rounded-md border border-line bg-surface p-6 md:grid-cols-2 xl:grid-cols-3"
        onSubmit={(event) => {
          event.preventDefault()
          const values = new FormData(event.currentTarget)
          onFilter({ ...filters, q: String(values.get('q') ?? '').trim(), page: 0 })
        }}
      >
        <FormField htmlFor="exam-q" label="Tìm mã hoặc tên đề">
          <Input name="q" maxLength={100} defaultValue={filters.q ?? ''} key={filters.q ?? ''} />
        </FormField>
        <FormField htmlFor="exam-status" label="Trạng thái đề">
          <Select
            disabled={busy}
            value={filters.status ?? ''}
            onChange={(e) =>
              onFilter({ ...filters, status: (e.target.value as ExamStatus) || undefined, page: 0 })
            }
          >
            <option value="">Tất cả trạng thái</option>
            {Object.entries(statusLabels).map(([value, label]) => (
              <option key={value} value={value}>
                {label}
              </option>
            ))}
          </Select>
        </FormField>
        <FormField htmlFor="exam-hsk" label="Cấp đề">
          <Select
            disabled={busy}
            value={filters.hsk ?? ''}
            onChange={(e) =>
              onFilter({
                ...filters,
                hsk: e.target.value ? Number(e.target.value) : undefined,
                page: 0,
              })
            }
          >
            <option value="">Tất cả cấp</option>
            {filters.hsk && filters.hsk > 3 && (
              <option value={filters.hsk}>HSK {filters.hsk} (dữ liệu hiện có)</option>
            )}
            {SUPPORTED_HSK_LEVELS.map((hsk) => (
              <option key={hsk} value={hsk}>
                HSK {hsk}
              </option>
            ))}
          </Select>
        </FormField>
        <Button type="submit" disabled={busy} variant="outline" className="mb-2 justify-self-start">
          Tìm đề thi
        </Button>
      </form>
      {state === 'loading' && (
        <div>
          <p role="status">Đang tải đề thi…</p>
          <Skeleton variant="table" />
        </div>
      )}
      {state === 'error' && (
        <Card role="alert" className="space-y-3 border-danger bg-danger-bg">
          <p>{error}</p>
          <Button variant="outline" onClick={onRetry}>
            Thử tải danh sách lại
          </Button>
        </Card>
      )}
      {state === 'empty' && (
        <Card className="p-6">
          <h2 className="text-h2">Không có đề thi phù hợp</h2>
          <p className="my-3 text-ink-secondary">Thử đổi bộ lọc hoặc tạo một đề nháp.</p>
          <Button variant="outline" onClick={() => onFilter({ page: 0 })}>
            Xoá bộ lọc
          </Button>
        </Card>
      )}
      {state === 'ready' && data && (
        <DataTable
          caption="Danh sách đề thi quản trị"
          captionVisible
          rows={data.items}
          rowKey={(row) => row.id}
          columns={[
            {
              id: 'title',
              header: 'Đề thi',
              render: (row) => (
                <div className="min-w-0 break-words">
                  <Link
                    className="inline-flex min-h-touch items-center font-semibold text-brand-700 underline focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus focus-visible:ring-offset-2"
                    to={`/content/exams/${row.id}`}
                    state={returnState}
                  >
                    {row.title}
                  </Link>
                  <p className="mt-1 text-body-sm text-ink-secondary">{row.code}</p>
                </div>
              ),
            },
            {
              id: 'status',
              header: 'Trạng thái',
              render: (row) => (
                <div>
                  <StatusBadge
                    tone={
                      row.status === 'PUBLISHED'
                        ? 'success'
                        : row.status === 'DRAFT'
                          ? 'warning'
                          : 'neutral'
                    }
                  >
                    {statusLabels[row.status]}
                  </StatusBadge>
                  {row.inUse && (
                    <p className="mt-2 text-body-sm text-ink-secondary">Cuộc thi đang sử dụng</p>
                  )}
                </div>
              ),
            },
            {
              id: 'count',
              header: 'Nội dung',
              render: (row) => (
                <span>
                  HSK {row.hskLevel} · {row.questionCount} câu
                </span>
              ),
            },
            {
              id: 'published',
              header: 'Công bố',
              render: (row) =>
                row.publishedAt
                  ? new Date(row.publishedAt).toLocaleString('vi-VN', {
                      timeZone: 'Asia/Ho_Chi_Minh',
                    })
                  : 'Chưa công bố',
            },
            {
              id: 'actions',
              header: 'Thao tác',
              render: (row) => (
                <div className="flex flex-wrap gap-2">
                  <Button
                    size="sm"
                    variant="outline"
                    disabled={busy}
                    onClick={() => onAction(row, 'clone')}
                  >
                    Nhân bản {row.code}
                  </Button>
                  <Button
                    size="sm"
                    variant="ghost"
                    disabled={busy || row.inUse || row.status === 'ARCHIVED'}
                    onClick={() => onAction(row, 'archive')}
                  >
                    Lưu trữ {row.code}
                  </Button>
                </div>
              ),
            },
          ]}
        />
      )}
      {data && (
        <Pagination
          page={(filters.page ?? 0) + 1}
          size={data.size}
          total={data.totalElements}
          onChange={(page) => {
            if (!busy) onFilter({ ...filters, page: page - 1 })
          }}
        />
      )}
    </main>
  )
}
