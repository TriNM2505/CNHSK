import { POST_CATEGORY_LABELS, POST_STATUS_LABELS } from '../labels'
import type { ModerationPost } from '../types'

function Timestamp({ value }: { value: string | null | undefined }) {
  if (!value) return <span>Chưa có</span>
  return <time dateTime={value}>{new Date(value).toLocaleString('vi-VN', { timeZone: 'Asia/Ho_Chi_Minh' })}</time>
}

export function PostModerationDetailView({ post }: { post: ModerationPost }) {
  return <article aria-label="Nội dung bài viết cần kiểm duyệt" className="my-6 rounded-md border border-line bg-surface p-4 sm:p-6">
    <h1 className="whitespace-pre-wrap text-h1-sm text-ink">{post.title}</h1>
    <dl className="my-4 grid gap-2 text-body-sm sm:grid-cols-2">
      <div><dt className="font-semibold">Tác giả</dt><dd className="whitespace-pre-wrap">{post.author.displayName ?? 'Người dùng đã rời'}</dd></div>
      <div><dt className="font-semibold">Danh mục</dt><dd>{POST_CATEGORY_LABELS[post.category]}</dd></div>
      <div><dt className="font-semibold">Trạng thái hiện tại</dt><dd>{POST_STATUS_LABELS[post.status]}</dd></div>
      <div><dt className="font-semibold">Thời gian tạo</dt><dd><Timestamp value={post.createdAt} /></dd></div>
      <div><dt className="font-semibold">Cập nhật gần nhất</dt><dd><Timestamp value={post.updatedAt} /></dd></div>
      <div><dt className="font-semibold">Thời gian đăng</dt><dd><Timestamp value={post.publishedAt} /></dd></div>
    </dl>
    <h2 className="text-h2">Nội dung</h2>
    <p className="my-4 whitespace-pre-wrap break-words">{post.content}</p>
    <section aria-label="Thông tin kiểm duyệt" className="border-t border-line pt-4">
      <h2 className="text-h2">Thông tin kiểm duyệt</h2>
      <dl className="mt-3 grid gap-2">
        <div><dt className="font-semibold">Người kiểm duyệt</dt><dd className="whitespace-pre-wrap">{post.reviewedBy ? post.reviewedBy.displayName ?? 'Người dùng đã rời' : 'Chưa có'}</dd></div>
        <div><dt className="font-semibold">Thời gian kiểm duyệt</dt><dd><Timestamp value={post.reviewedAt} /></dd></div>
        <div><dt className="font-semibold">Lý do kiểm duyệt</dt><dd className="whitespace-pre-wrap break-words">{post.reviewReason ?? 'Chưa có'}</dd></div>
      </dl>
    </section>
  </article>
}
