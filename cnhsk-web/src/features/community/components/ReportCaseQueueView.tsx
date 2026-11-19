import { Link } from 'react-router-dom'

import { Button } from '@/shared/ui/Button'
import { DataTable, type DataTableColumn } from '@/shared/ui/DataTable'
import { EmptyState } from '@/shared/ui/EmptyState'
import { ErrorState } from '@/shared/ui/ErrorState'
import { PageBanner } from '@/shared/ui/PageBanner'
import { PageState, type PageStateValue } from '@/shared/ui/PageState'
import { Pagination } from '@/shared/ui/Pagination'
import { Skeleton } from '@/shared/ui/Skeleton'
import { StatusBadge } from '@/shared/ui/StatusBadge'

import { REPORT_REASON_LABELS } from '../labels'
import { formatModerationTimestamp, MODERATION_PAGE_SIZE } from '../moderation-list-query'
import type { ReportCasePage, ReportCaseSummary } from '../types'
import { ModerationNavigation } from './ModerationNavigation'

export type ReportCaseQueueViewProps = {
  data?: ReportCasePage
  error?: string
  onPageChange: (page: number) => void
  onRetry: () => void
  page: number
  state: PageStateValue
}

export function ReportCaseQueueView({
  data,
  error,
  onPageChange,
  onRetry,
  page,
  state,
}: ReportCaseQueueViewProps) {
  const columns: DataTableColumn<ReportCaseSummary>[] = [
    {
      id: 'case',
      header: 'Hồ sơ',
      hideOnMobile: true,
      render: (reportCase) => (
        <div className="space-y-1">
          <h2 className="text-body font-semibold text-ink">
            <Link
              className="hover:text-brand-700 hover:underline"
              to={`/moderation/reports/${reportCase.id}?page=${page}`}
            >
              Hồ sơ #{reportCase.id}
            </Link>
          </h2>
          <p className="text-body-sm text-ink-secondary">
            {reportCase.targetType === 'POST' ? 'Bài viết' : 'Bình luận'} #{reportCase.targetId}
          </p>
        </div>
      ),
    },
    {
      id: 'preview',
      header: 'Nội dung bị báo cáo',
      hideOnMobile: true,
      render: (reportCase) => (
        <p className="whitespace-pre-wrap break-words text-body-sm text-ink">
          {reportCase.targetPreview}
        </p>
      ),
    },
    {
      id: 'reports',
      header: 'Số lượt & Lý do',
      hideOnMobile: true,
      render: (reportCase) => (
        <dl className="space-y-1 text-body-sm">
          <div className="flex gap-1.5">
            <dt className="text-ink-secondary">Số lượt báo cáo:</dt>
            <dd className="font-semibold text-ink">{reportCase.reportCount}</dd>
          </div>
          <div className="flex gap-1.5">
            <dt className="text-ink-secondary">Lý do gần nhất:</dt>
            <dd className="text-ink">{REPORT_REASON_LABELS[reportCase.latestReason]}</dd>
          </div>
        </dl>
      ),
    },
    {
      id: 'createdAt',
      header: 'Thời gian tạo',
      hideOnMobile: true,
      render: (reportCase) => (
        <time className="text-caption text-ink-muted" dateTime={reportCase.createdAt}>
          {formatModerationTimestamp(reportCase.createdAt)}
        </time>
      ),
    },
    {
      id: 'status',
      header: 'Trạng thái',
      hideOnMobile: true,
      render: () => <StatusBadge tone="warning">Chờ xử lý</StatusBadge>,
    },
    {
      id: 'mobileSummary',
      header: 'Mở hồ sơ',
      render: (reportCase) => (
        <Link
          className="inline-flex min-h-touch items-center text-body-sm font-semibold text-brand-700 underline"
          to={`/moderation/reports/${reportCase.id}?page=${page}`}
        >
          Xem hồ sơ #{reportCase.id}
        </Link>
      ),
    },
  ]

  return (
    <main className="mx-auto max-w-content space-y-6 p-4 sm:p-6">
      <ModerationNavigation active="reports" />
      <PageBanner
        className="px-4 pt-4 pb-20 shadow-sm sm:px-5 [&>svg]:inset-y-auto [&>svg]:bottom-0 [&>svg]:block [&>svg]:h-16 [&>svg]:w-80 [&>svg]:max-w-full lg:pb-4 lg:pr-80 lg:[&>svg]:h-full"
        actions={
          <Button disabled={state === 'loading'} onClick={onRetry} variant="outline">
            Tải lại báo cáo
          </Button>
        }
        subtitle="Các báo cáo được gộp theo nội dung, ưu tiên số lượt báo cáo."
        title="Báo cáo vi phạm"
      />
      <PageState
        empty={
          <EmptyState
            action={
              page > 0 ? (
                <Button onClick={() => onPageChange(0)} variant="outline">
                  Về trang đầu
                </Button>
              ) : undefined
            }
            description="Hiện không có hồ sơ báo cáo vi phạm nào đang chờ kiểm duyệt."
            title="Không có báo cáo chờ xử lý"
          />
        }
        error={
          <div role="alert">
            <ErrorState message={error ?? 'Không thể tải báo cáo vi phạm.'} onRetry={onRetry} />
          </div>
        }
        loading={
          <div aria-label="Đang tải báo cáo vi phạm" className="space-y-3" role="status">
            <p className="text-body-sm text-ink-secondary">Đang tải báo cáo vi phạm…</p>
            <Skeleton variant="table" />
          </div>
        }
        state={state}
      >
        <DataTable
          caption="Danh sách hồ sơ báo cáo"
          columns={columns}
          rowKey={(reportCase) => reportCase.id}
          rows={data?.items ?? []}
        />
        {data && data.totalElements > 0 ? (
          <div className="mt-6">
            <Pagination
              label="Phân trang báo cáo vi phạm"
              onChange={(next) => onPageChange(next - 1)}
              page={page + 1}
              size={MODERATION_PAGE_SIZE}
              total={data.totalElements}
            />
          </div>
        ) : null}
      </PageState>
    </main>
  )
}
