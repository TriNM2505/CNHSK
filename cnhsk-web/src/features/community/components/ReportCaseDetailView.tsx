import { PageBanner } from '@/shared/ui/PageBanner'
import { Link } from 'react-router-dom'
import { POST_CATEGORY_LABELS, POST_STATUS_LABELS, REPORT_REASON_LABELS } from '../labels'
import type { ReportCaseDetail } from '../types'

const caseLabels = {
  PENDING: 'Chờ xử lý',
  ACTIONED: 'Đã xử lý nội dung',
  REJECTED: 'Đã bác báo cáo',
}
const commentLabels = { VISIBLE: 'Hiển thị', HIDDEN: 'Đã bị ẩn', DELETED: 'Đã xoá' }
const date = (value: string) =>
  new Date(value).toLocaleString('vi-VN', { timeZone: 'Asia/Ho_Chi_Minh' })

export function ReportCaseDetailView({ reportCase }: { reportCase: ReportCaseDetail }) {
  const { target } = reportCase
  return (
    <>
      <section
        className="rounded-md border border-line bg-surface p-4 shadow-sm sm:p-6"
        aria-label="Hồ sơ báo cáo"
      >
        <PageBanner
          className="px-4 pt-4 pb-20 shadow-sm sm:px-5 [&>svg]:inset-y-auto [&>svg]:bottom-0 [&>svg]:block [&>svg]:h-16 [&>svg]:w-80 [&>svg]:max-w-full lg:pb-4 lg:pr-80 lg:[&>svg]:h-full"
          title={<>Hồ sơ báo cáo #{reportCase.id}</>}
        />
        <div className="mt-3 grid gap-2 rounded-sm bg-canvas p-3 text-body-sm text-ink sm:grid-cols-3">
          <p>Trạng thái xử lý: {caseLabels[reportCase.status]}</p>
          <p>Số báo cáo: {reportCase.reportCount}</p>
          <p>
            Tạo lúc:{' '}
            <time className="text-ink" dateTime={reportCase.createdAt}>
              {date(reportCase.createdAt)}
            </time>
          </p>
        </div>
        <h2 className="mt-5 text-h2 text-ink">Nội dung cần xem xét</h2>
        <div className="mt-2 space-y-1 text-body-sm text-ink-secondary">
          <p className="font-semibold text-ink">
            {target.kind === 'POST' ? 'Bài viết' : 'Bình luận'} #{target.id}
          </p>
          <p>Tác giả: {target.author?.displayName ?? 'Người dùng đã rời'}</p>
          <p>
            Trạng thái nội dung:{' '}
            {target.kind === 'POST'
              ? POST_STATUS_LABELS[target.status]
              : commentLabels[target.status]}
          </p>
        </div>
        {target.kind === 'POST' ? (
          <div className="mt-3">
            <h3 className="whitespace-pre-wrap text-h3 text-ink">{target.title}</h3>
            <p className="text-body-sm text-ink-secondary">
              {POST_CATEGORY_LABELS[target.category]}
            </p>
          </div>
        ) : (
          <p className="mt-3 whitespace-pre-wrap text-body-sm font-medium text-ink">
            Bài viết: {target.postTitle}
          </p>
        )}
        <p className="my-4 whitespace-pre-wrap break-words rounded-sm bg-canvas p-3 text-body text-ink">
          {target.content}
        </p>
        <Link
          className="inline-flex min-h-touch items-center text-body-sm font-semibold text-brand-700 underline"
          to={`/moderation/posts/${target.kind === 'POST' ? target.id : target.postId}`}
        >
          Xem bài viết trong kiểm duyệt
        </Link>
      </section>
      <section
        className="mt-4 rounded-md border border-line bg-surface p-4 shadow-sm sm:p-6"
        aria-label="Các lý do báo cáo"
      >
        <h2 className="text-h2 text-ink">Các lý do báo cáo</h2>
        {reportCase.reports.length === 0 ? (
          <p className="mt-2 text-body-sm text-ink-secondary">
            Không có bằng chứng báo cáo được trả về.
          </p>
        ) : (
          <ul className="mt-3 space-y-3">
            {reportCase.reports.map((report, index) => (
              <li
                className="rounded-sm border border-line bg-canvas p-3 text-body-sm"
                key={`${report.reporter.id}:${report.reportedAt}:${index}`}
              >
                <p className="font-semibold text-ink">
                  Người báo cáo: {report.reporter.displayName ?? 'Người dùng đã rời'} (#
                  {report.reporter.id})
                </p>
                <p className="mt-1 font-medium text-brand-700">
                  {REPORT_REASON_LABELS[report.reason]}
                </p>
                <p className="mt-1 whitespace-pre-wrap text-ink-secondary">
                  {report.detail ?? 'Không có thông tin bổ sung.'}
                </p>
                <time
                  className="mt-1 block text-caption text-ink-muted"
                  dateTime={report.reportedAt}
                >
                  {date(report.reportedAt)}
                </time>
              </li>
            ))}
          </ul>
        )}
      </section>
      {reportCase.status !== 'PENDING' ? (
        <section
          className="mt-4 rounded-md border border-line bg-surface p-4 shadow-sm sm:p-6"
          aria-label="Kết quả xử lý"
        >
          <h2 className="text-h2 text-ink">Kết quả xử lý</h2>
          <div className="mt-2 space-y-1 text-body-sm text-ink">
            <p>
              Người xử lý: {reportCase.handledBy?.displayName ?? 'Không có thông tin người xử lý.'}
            </p>
            {reportCase.handledAt ? (
              <time className="block text-ink-secondary" dateTime={reportCase.handledAt}>
                {date(reportCase.handledAt)}
              </time>
            ) : (
              <p className="text-ink-secondary">Không có thời điểm xử lý.</p>
            )}
            <p className="whitespace-pre-wrap">
              {reportCase.decisionNote ?? 'Không có ghi chú xử lý.'}
            </p>
            <p className="mt-2 font-medium text-ink-secondary">
              Hồ sơ đã đóng, không thể quyết định lại.
            </p>
          </div>
        </section>
      ) : null}
    </>
  )
}
