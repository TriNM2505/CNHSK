import {
  ArrowRight,
  CheckCircle2,
  Clock,
  FilePenLine,
  FileText,
  RefreshCw,
  Sparkles,
} from 'lucide-react'

import type { ApiError, ApiQueryState } from '@/shared/api'
import {
  PageBanner,
  Button,
  Card,
  EmptyState,
  ErrorState,
  FormField,
  Pagination,
  Select,
  Skeleton,
  StatusBadge,
  type StatusTone,
} from '@/shared/ui'
import { displayTime, statusLabels, type GradingRequestPage, type RequestStatus } from '../types'

type Props = {
  state: ApiQueryState
  result?: GradingRequestPage
  error?: ApiError
  status: RequestStatus | ''
  page: number
  onStatus: (value: RequestStatus | '') => void
  onPage: (page: number) => void
  onRetry: () => void
}

const statusTones: Readonly<Record<RequestStatus, StatusTone>> = {
  PENDING: 'warning',
  ASSIGNED: 'info',
  COMPLETED: 'success',
  FAILED: 'danger',
  EXPIRED: 'neutral',
}

export function MyGradingRequestsView(props: Props) {
  const items = props.result?.items ?? []
  const totalCount = props.result?.totalElements ?? 0
  const completedOnPage = items.filter((item) => item.status === 'COMPLETED').length
  const activeOnPage = items.filter(
    (item) => item.status === 'PENDING' || item.status === 'ASSIGNED',
  ).length

  return (
    <main className="mx-auto w-full min-w-0 max-w-content space-y-6 px-4 py-6 pb-16 sm:px-6 lg:px-8">
      <PageBanner
        subtitle="Theo dõi bài đã gửi và đọc góp ý của giáo viên."
        title="Yêu cầu chấm bài của tôi"
      />

      {props.state === 'ready' && props.result ? (
        <div className="grid min-w-0 gap-4 sm:grid-cols-2">
          <Card className="flex min-w-0 items-start justify-between gap-4 border-brand-100 bg-brand-50 p-5">
            <div className="flex min-w-0 items-start gap-4">
              <div className="flex size-12 shrink-0 items-center justify-center rounded-md border border-brand-100 bg-surface text-brand-600">
                <Clock aria-hidden="true" className="size-6" />
              </div>
              <div className="min-w-0">
                <p className="text-h1 tabular-nums text-ink">{activeOnPage}</p>
                <p className="mt-0.5 text-label text-ink">Bài chờ nhận / đang chấm</p>
                <p className="mt-0.5 text-body-sm text-ink-secondary">
                  Tổng số {totalCount} yêu cầu theo bộ lọc hiện tại
                </p>
              </div>
            </div>
            <span className="shrink-0 rounded-pill bg-brand-100 px-3 py-1 text-caption font-semibold text-brand-700">
              Đang xử lý
            </span>
          </Card>

          <Card className="flex min-w-0 items-start justify-between gap-4 border-brand-100 bg-brand-50 p-5">
            <div className="flex min-w-0 items-start gap-4">
              <div className="flex size-12 shrink-0 items-center justify-center rounded-md border border-brand-100 bg-surface text-success">
                <CheckCircle2 aria-hidden="true" className="size-6" />
              </div>
              <div className="min-w-0">
                <p className="text-h1 tabular-nums text-ink">{completedOnPage}</p>
                <p className="mt-0.5 text-label text-ink">Bài đã có kết quả</p>
                <p className="mt-0.5 text-body-sm text-ink-secondary">
                  Trong trang hiện tại ({items.length} mục)
                </p>
              </div>
            </div>
            <span className="shrink-0 rounded-pill bg-success-bg px-3 py-1 text-caption font-semibold text-success">
              Hoàn thành
            </span>
          </Card>
        </div>
      ) : null}

      <Card className="flex min-w-0 flex-wrap items-center justify-between gap-4 border-brand-100 bg-brand-50 p-5 sm:px-6">
        <div className="flex min-w-0 items-center gap-3.5">
          <div className="flex size-11 shrink-0 items-center justify-center rounded-pill bg-brand-100 text-brand-700">
            <FilePenLine aria-hidden="true" className="size-6" />
          </div>
          <div className="min-w-0 space-y-0.5">
            <h2 className="text-h3 text-ink">Gửi bài chấm mới</h2>
            <p className="text-body-sm text-ink-secondary">
              Nhận đánh giá theo 4 tiêu chí Ngữ pháp, Từ vựng, Mạch lạc và Chữ viết.
            </p>
          </div>
        </div>
        <div className="flex w-full flex-wrap gap-3 sm:w-auto">
          <Button to="/grading/new">
            Gửi bài mới
            <ArrowRight aria-hidden="true" className="ml-1.5 inline-block size-4 align-middle" />
          </Button>
          <Button onClick={props.onRetry} variant="outline">
            <RefreshCw
              aria-hidden="true"
              className="mr-1.5 inline-block size-4 align-middle text-brand-600"
            />
            Làm mới
          </Button>
        </div>
      </Card>

      <div className="flex flex-wrap items-end justify-between gap-4 rounded-md border border-line bg-surface p-4 sm:px-5">
        <div className="w-full max-w-xs">
          <FormField className="mb-0" htmlFor="grading-status" label="Trạng thái">
            <Select
              onChange={(event) => props.onStatus(event.target.value as RequestStatus | '')}
              value={props.status}
            >
              <option value="">Tất cả</option>
              {Object.entries(statusLabels).map(([value, label]) => (
                <option key={value} value={value}>
                  {label}
                </option>
              ))}
            </Select>
          </FormField>
        </div>
        {props.state === 'ready' && props.result ? (
          <div className="flex items-center gap-2 text-body-sm font-medium text-ink-secondary">
            <Sparkles aria-hidden="true" className="size-4 text-brand-600" />
            <span>{totalCount} yêu cầu</span>
          </div>
        ) : null}
      </div>

      {props.state === 'loading' ? (
        <div className="space-y-3" role="status">
          <p className="text-body-sm text-ink-secondary">Đang tải yêu cầu…</p>
          <div className="grid gap-4 lg:grid-cols-2">
            <Skeleton variant="card" />
            <Skeleton variant="card" />
          </div>
        </div>
      ) : null}

      {props.state === 'error' ? (
        <div role="alert">
          <ErrorState
            message={props.error?.message ?? 'Không tải được yêu cầu.'}
            onRetry={props.onRetry}
            requestId={props.error?.requestId}
            title="Chưa tải được danh sách yêu cầu"
          />
        </div>
      ) : null}

      {props.state === 'empty' ? (
        <div role="status">
          <EmptyState
            action={
              <Button to="/grading/new" variant="outline">
                Gửi bài mới
              </Button>
            }
            description="Hãy gửi bài viết tiếng Trung đầu tiên hoặc đổi bộ lọc trạng thái để xem các bài đã gửi."
            icon={<FileText aria-hidden="true" className="size-5" />}
            title="Chưa có yêu cầu phù hợp."
          />
        </div>
      ) : null}

      {props.state === 'ready' && props.result ? (
        <>
          <div className="grid min-w-0 gap-4 lg:grid-cols-2">
            {props.result.items.map((item) => (
              <Card
                className="flex h-full min-w-0 flex-col justify-between gap-4 p-5 break-words [overflow-wrap:anywhere]"
                key={item.id}
              >
                <div className="space-y-3.5">
                  <div className="flex flex-wrap items-center justify-between gap-2">
                    <div className="flex items-center gap-2.5">
                      <FileText aria-hidden="true" className="size-5 shrink-0 text-brand-600" />
                      <h2 className="text-h3 text-ink">{item.reference}</h2>
                    </div>
                    <StatusBadge tone={statusTones[item.status]}>
                      {statusLabels[item.status]}
                    </StatusBadge>
                  </div>

                  <p className="line-clamp-3 rounded-md border border-brand-100 bg-brand-50 p-3.5 whitespace-pre-wrap font-hanzi text-body text-ink">
                    {item.essayPreview}
                  </p>

                  <div className="space-y-1 text-body-sm text-ink-secondary">
                    <p>Gửi lúc: {displayTime(item.createdAt)}</p>
                    {item.deadline ? (
                      <p>
                        {item.status === 'PENDING' ? 'Hạn nhận bài' : 'Hạn chấm bài'}:{' '}
                        {displayTime(item.deadline)}
                      </p>
                    ) : null}
                    {item.score !== null ? (
                      <div className="space-y-1.5 pt-1">
                        <div className="flex items-center justify-between gap-2 text-body-sm">
                          <span className="text-ink-secondary">Kết quả chấm</span>
                          <span className="font-semibold tabular-nums text-ink">
                            Điểm: {item.score}/100
                          </span>
                        </div>
                        <div
                          aria-hidden="true"
                          className="h-2 w-full overflow-hidden rounded-pill bg-line"
                        >
                          <div
                            className="h-full rounded-pill bg-brand-600"
                            style={{ width: `${Math.min(Math.max(item.score, 0), 100)}%` }}
                          />
                        </div>
                      </div>
                    ) : null}
                    {item.completedAt ? (
                      <p>Hoàn thành lúc: {displayTime(item.completedAt)}</p>
                    ) : null}
                  </div>
                </div>

                <div className="flex flex-wrap items-center justify-between gap-2 border-t border-line pt-3">
                  <Button
                    aria-label={item.reference}
                    size="sm"
                    to={`/grading/${item.id}`}
                    variant="primary"
                  >
                    Xem chi tiết
                    <ArrowRight
                      aria-hidden="true"
                      className="ml-1 inline-block size-4 align-middle"
                    />
                  </Button>
                </div>
              </Card>
            ))}
          </div>
          <Pagination
            onChange={(page) => props.onPage(page - 1)}
            page={props.page + 1}
            size={props.result.size}
            total={props.result.totalElements}
          />
        </>
      ) : null}
    </main>
  )
}
