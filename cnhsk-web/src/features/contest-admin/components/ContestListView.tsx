import { Link, useLocation } from 'react-router-dom'
import type { ApiQueryState } from '@/shared/api'
import { Button, DataTable, FormField, PageHeader, Pagination, Select } from '@/shared/ui'
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
    <main className="min-w-0 space-y-6">
      <PageHeader
        title="Quản lý cuộc thi"
        subtitle="Lịch thi hiển thị theo giờ Việt Nam (UTC+7)."
      />
      <Link
        className="inline-flex min-h-touch items-center rounded-sm bg-brand-600 px-4 font-semibold text-white"
        to="/content/contests/new"
        state={returnState}
      >
        Tạo cuộc thi
      </Link>
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
      {state === 'loading' && <p role="status">Đang tải cuộc thi…</p>}
      {state === 'error' && (
        <div role="alert">
          <p>{error}</p>
          <Button onClick={onRetry} variant="outline">
            Thử tải lại
          </Button>
        </div>
      )}
      {state === 'empty' && (
        <div>
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
        </div>
      )}
      {state === 'ready' && data && (
        <DataTable
          caption="Danh sách cuộc thi quản trị"
          rows={data.items}
          rowKey={(item) => item.id}
          columns={[
            {
              id: 'name',
              header: 'Cuộc thi',
              render: (item) => (
                <div className="space-y-1 break-words">
                  <Link
                    className="font-semibold text-brand-700 underline"
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
                  <p>{statusLabels[item.status]}</p>
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
                    <Link
                      className="inline-flex min-h-touch items-center text-brand-700 underline"
                      to={`/content/contests/${item.id}`}
                      state={returnState}
                    >
                      Sửa
                    </Link>
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
