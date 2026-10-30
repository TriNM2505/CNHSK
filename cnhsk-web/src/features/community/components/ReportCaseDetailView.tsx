import { Link } from 'react-router-dom'
import { POST_CATEGORY_LABELS, POST_STATUS_LABELS, REPORT_REASON_LABELS } from '../labels'
import type { ReportCaseDetail } from '../types'

const caseLabels = { PENDING: 'Chờ xử lý', ACTIONED: 'Đã xử lý nội dung', REJECTED: 'Đã bác báo cáo' }
const commentLabels = { VISIBLE: 'Hiển thị', HIDDEN: 'Đã bị ẩn', DELETED: 'Đã xoá' }
const date = (value: string) => new Date(value).toLocaleString('vi-VN', { timeZone: 'Asia/Ho_Chi_Minh' })

export function ReportCaseDetailView({ reportCase }: { reportCase: ReportCaseDetail }) {
  const { target } = reportCase
  return <>
    <section className="rounded-md border border-line bg-surface p-4" aria-label="Hồ sơ báo cáo">
      <h1 className="text-h1-sm">Hồ sơ báo cáo #{reportCase.id}</h1>
      <p>Trạng thái xử lý: {caseLabels[reportCase.status]}</p>
      <p>Số báo cáo: {reportCase.reportCount}</p>
      <p>Tạo lúc: <time dateTime={reportCase.createdAt}>{date(reportCase.createdAt)}</time></p>
      <h2 className="mt-5 text-h2">Nội dung cần xem xét</h2>
      <p>{target.kind === 'POST' ? 'Bài viết' : 'Bình luận'} #{target.id}</p>
      <p>Tác giả: {target.author?.displayName ?? 'Người dùng đã rời'}</p>
      <p>Trạng thái nội dung: {target.kind === 'POST' ? POST_STATUS_LABELS[target.status] : commentLabels[target.status]}</p>
      {target.kind === 'POST' ? <><h3 className="whitespace-pre-wrap text-h3">{target.title}</h3><p>{POST_CATEGORY_LABELS[target.category]}</p></> : <p className="whitespace-pre-wrap">Bài viết: {target.postTitle}</p>}
      <p className="my-4 whitespace-pre-wrap">{target.content}</p>
      <Link className="underline" to={`/moderation/posts/${target.kind === 'POST' ? target.id : target.postId}`}>Xem bài viết trong kiểm duyệt</Link>
    </section>
    <section className="mt-4 rounded-md border border-line bg-surface p-4" aria-label="Các lý do báo cáo">
      <h2 className="text-h2">Các lý do báo cáo</h2>
      {reportCase.reports.length === 0 ? <p>Không có bằng chứng báo cáo được trả về.</p> : <ul className="space-y-4">{reportCase.reports.map((report, index) => <li className="border-b border-line py-3" key={`${report.reporter.id}:${report.reportedAt}:${index}`}>
        <p>Người báo cáo: {report.reporter.displayName ?? 'Người dùng đã rời'} (#{report.reporter.id})</p>
        <p>{REPORT_REASON_LABELS[report.reason]}</p>
        <p className="whitespace-pre-wrap">{report.detail ?? 'Không có thông tin bổ sung.'}</p>
        <time dateTime={report.reportedAt}>{date(report.reportedAt)}</time>
      </li>)}</ul>}
    </section>
    {reportCase.status !== 'PENDING' ? <section className="mt-4 rounded-md border border-line p-4" aria-label="Kết quả xử lý">
      <h2 className="text-h2">Kết quả xử lý</h2>
      <p>Người xử lý: {reportCase.handledBy?.displayName ?? 'Không có thông tin người xử lý.'}</p>
      {reportCase.handledAt ? <time dateTime={reportCase.handledAt}>{date(reportCase.handledAt)}</time> : <p>Không có thời điểm xử lý.</p>}
      <p className="whitespace-pre-wrap">{reportCase.decisionNote ?? 'Không có ghi chú xử lý.'}</p>
      <p>Hồ sơ đã đóng, không thể quyết định lại.</p>
    </section> : null}
  </>
}
