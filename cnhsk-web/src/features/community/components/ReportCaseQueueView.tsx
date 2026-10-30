import { Link } from 'react-router-dom'

import { Button } from '@/shared/ui/Button'
import { PageState, type PageStateValue } from '@/shared/ui/PageState'
import { Pagination } from '@/shared/ui/Pagination'

import { REPORT_REASON_LABELS } from '../labels'
import { formatModerationTimestamp, MODERATION_PAGE_SIZE } from '../moderation-list-query'
import type { ReportCasePage } from '../types'
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
  return (
    <main className="mx-auto max-w-content p-4 sm:p-6">
      <ModerationNavigation active="reports" />
      <header className="mb-6 flex flex-wrap items-center justify-between gap-4">
        <div>
          <h1 className="text-h1-sm text-ink sm:text-h1">Báo cáo vi phạm</h1>
          <p className="mt-2 text-body text-ink-secondary">
            Các báo cáo được gộp theo nội dung, ưu tiên số lượt báo cáo.
          </p>
        </div>
        <Button disabled={state === 'loading'} onClick={onRetry} variant="outline">
          Tải lại báo cáo
        </Button>
      </header>
      <PageState
        empty={
          <section className="rounded-md border border-dashed border-line p-8 text-center">
            <h2 className="text-h3">Không có báo cáo chờ xử lý</h2>
            {page > 0 ? (
              <Button className="mt-4" onClick={() => onPageChange(0)} variant="outline">
                Về trang đầu
              </Button>
            ) : null}
          </section>
        }
        error={
          <section className="rounded-md border border-danger bg-danger-bg p-4" role="alert">
            <p>{error ?? 'Không thể tải báo cáo vi phạm.'}</p>
            <Button className="mt-3" onClick={onRetry} variant="outline">
              Thử lại
            </Button>
          </section>
        }
        loading={
          <div aria-label="Đang tải báo cáo vi phạm" className="space-y-3" role="status">
            <p>Đang tải báo cáo vi phạm…</p>
            {[0, 1, 2].map((item) => (
              <div className="h-28 animate-pulse rounded-md bg-canvas" key={item} />
            ))}
          </div>
        }
        state={state}
      >
        <div aria-label="Danh sách hồ sơ báo cáo" className="grid gap-4" role="list">
          {data?.items.map((reportCase) => (
            <article
              className="rounded-md border border-line bg-surface p-4"
              key={reportCase.id}
              role="listitem"
            >
              <div className="flex flex-wrap items-start justify-between gap-3">
                <h2 className="text-h3 text-ink">
                  <Link
                    className="hover:text-brand-700"
                    to={`/moderation/reports/${reportCase.id}?page=${page}`}
                  >
                    Hồ sơ #{reportCase.id}
                  </Link>
                </h2>
                <span className="rounded-pill bg-canvas px-2 py-1 text-caption">Chờ xử lý</span>
              </div>
              <p className="mt-2 text-body-sm text-ink-secondary">
                {reportCase.targetType === 'POST' ? 'Bài viết' : 'Bình luận'} #{reportCase.targetId}
              </p>
              <p className="mt-3 whitespace-pre-wrap break-words text-body-sm">
                {reportCase.targetPreview}
              </p>
              <dl className="mt-3 grid gap-2 text-body-sm sm:grid-cols-2">
                <div>
                  <dt className="text-ink-secondary">Số lượt báo cáo</dt>
                  <dd>{reportCase.reportCount}</dd>
                </div>
                <div>
                  <dt className="text-ink-secondary">Lý do gần nhất</dt>
                  <dd>{REPORT_REASON_LABELS[reportCase.latestReason]}</dd>
                </div>
                <div>
                  <dt className="text-ink-secondary">Thời gian tạo</dt>
                  <dd>
                    <time dateTime={reportCase.createdAt}>
                      {formatModerationTimestamp(reportCase.createdAt)}
                    </time>
                  </dd>
                </div>
              </dl>
            </article>
          ))}
        </div>
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
