import { Link, useLocation } from 'react-router-dom'
import type { ApiQueryState } from '@/shared/api'
import {
  Button,
  Card,
  DataTable,
  FormField,
  PageHeader,
  Pagination,
  Select,
  Skeleton,
  StatusBadge,
} from '@/shared/ui'
import {
  actionLabels,
  actionReason,
  editReason,
  stateLabels,
  statusLabels,
  vietnamTime,
} from '../contestActions'
import type { AdminContest, AdminContestPage, ContestAction, ContestStatus } from '../types'

type Props = {
  state: ApiQueryState
  data?: AdminContestPage
  error?: string
  status: ContestStatus | ''
  busy?: boolean
  now?: number
  onFilter: (status: ContestStatus | '') => void
  onPage: (page: number) => void
  onRetry: () => void
  onAction: (contest: AdminContest, action: ContestAction) => void
}
export function ContestListView({
  state,
  data,
  error,
  status,
  busy,
  now,
  onFilter,
  onPage,
  onRetry,
  onAction,
}: Props) {
  const location = useLocation()
  const returnState = { contestListSearch: location.search }
  return (
    <main className="mx-auto w-full min-w-0 max-w-content space-y-6 px-4 py-6 pb-16 sm:px-6">
      <PageHeader
        title="Quản lý cuộc thi"
        subtitle="Lịch thi hiển thị theo giờ Việt Nam (UTC+7)."
        actions={
          <Button to="/content/contests/new" state={returnState}>
            Tạo cuộc thi
          </Button>
        }
      />

      <Card className="p-6">
        <FormField htmlFor="contest-status" label="Lọc trạng thái">
          <Select
            disabled={busy}
            value={status}
            onChange={(event) => onFilter(event.target.value as ContestStatus | '')}
          >
            <option value="">Tất cả trạng thái</option>
            {Object.entries(statusLabels).map(([value, label]) => (
              <option key={value} value={value}>
                {label}
              </option>
            ))}
          </Select>
        </FormField>
      </Card>
      {state === 'loading' && (
        <div>
          <p role="status">Đang tải cuộc thi…</p>
          <Skeleton variant="table" />
        </div>
      )}
      {state === 'error' && (
        <Card role="alert" className="space-y-3 border-danger bg-danger-bg">
          <p>{error}</p>
          <Button onClick={onRetry} variant="outline">
            Thử tải lại
          </Button>
        </Card>
      )}
      {state === 'empty' && (
        <Card className="space-y-3 p-6">
          <p>
            {data && data.totalElements > 0
              ? 'Trang này không có cuộc thi.'
              : status
                ? 'Không có cuộc thi phù hợp.'
                : 'Chưa có cuộc thi.'}
          </p>
          <Button onClick={() => onPage(0)} variant="ghost">
            Về trang đầu
          </Button>
        </Card>
      )}
      {state === 'ready' && data && (
        <DataTable
          caption="Danh sách cuộc thi quản trị"
          captionVisible
          rows={data.items}
          rowKey={(item) => item.id}
          columns={[
            {
              id: 'name',
              header: 'Cuộc thi',
              render: (item) => (
                <div className="space-y-1 break-words">
                  <Link
                    className="inline-flex min-h-touch items-center font-semibold text-brand-700 underline focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus focus-visible:ring-offset-2"
                    to={`/content/contests/${item.id}`}
                    state={returnState}
                  >
                    Quản lý: {item.name}
                  </Link>
                  <p>{item.examTitle ?? 'Chưa chọn đề'}</p>
                </div>
              ),
            },
            {
              id: 'schedule',
              header: 'Lịch thi (VN)',
              render: (item) => (
                <div>
                  <p>Bắt đầu: {vietnamTime(item.startsAt)}</p>
                  <p>Kết thúc: {vietnamTime(item.endsAt)}</p>
                </div>
              ),
            },
            {
              id: 'status',
              header: 'Trạng thái',
              render: (item) => (
                <div>
                  <StatusBadge
                    tone={
                      item.status === 'PUBLISHED'
                        ? 'success'
                        : item.status === 'DRAFT'
                          ? 'warning'
                          : 'neutral'
                    }
                  >
                    {statusLabels[item.status]}
                  </StatusBadge>
                  <p>{stateLabels[item.state]}</p>
                  <p>{item.participantCount} lượt thi</p>
                  <p>Kết quả: {vietnamTime(item.rankingPublishedAt)}</p>
                </div>
              ),
            },
            {
              id: 'actions',
              header: 'Thao tác',
              render: (item) => (
                <div className="space-y-3">
                  {editReason(item) ? (
                    <div>
                      <Button disabled size="sm">
                        Sửa
                      </Button>
                      <p className="text-caption text-ink-secondary">{editReason(item)}</p>
                    </div>
                  ) : (
                    <Button
                      variant="outline"
                      size="sm"
                      to={`/content/contests/${item.id}`}
                      state={returnState}
                    >
                      Sửa
                    </Button>
                  )}
                  {(['cancel', 'ranking'] as const).map((action) => {
                    const reason = actionReason(item, action, now)
                    return (
                      <div key={action}>
                        <Button
                          title={reason}
                          disabled={busy || !!reason}
                          size="sm"
                          variant={action === 'cancel' ? 'danger' : 'outline'}
                          onClick={() => onAction(item, action)}
                        >
                          {actionLabels[action]}
                        </Button>
                        {reason && <p className="text-caption text-ink-secondary">{reason}</p>}
                      </div>
                    )
                  })}
                </div>
              ),
            },
          ]}
        />
      )}
      {data && state !== 'loading' && state !== 'error' && (
        <Pagination
          label="Phân trang cuộc thi"
          page={data.page + 1}
          size={data.size}
          total={data.totalElements}
          onChange={(page) => {
            if (!busy) onPage(page - 1)
          }}
        />
      )}
    </main>
  )
}
