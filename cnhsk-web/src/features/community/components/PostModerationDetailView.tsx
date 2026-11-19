import { PageBanner } from '@/shared/ui/PageBanner'
import { Card } from '@/shared/ui/Card'
import { POST_CATEGORY_LABELS, POST_STATUS_LABELS } from '../labels'
import type { ModerationPost } from '../types'

function Timestamp({ value }: { value: string | null | undefined }) {
  if (!value) return <span>Chưa có</span>
  return (
    <time dateTime={value}>
      {new Date(value).toLocaleString('vi-VN', { timeZone: 'Asia/Ho_Chi_Minh' })}
    </time>
  )
}

export function PostModerationDetailView({ post }: { post: ModerationPost }) {
  return (
    <article aria-label="Nội dung bài viết cần kiểm duyệt" className="my-6 space-y-6">
      <Card className="p-5 sm:p-6">
        <PageBanner
          className="px-4 pt-4 pb-20 shadow-sm sm:px-5 [&>svg]:inset-y-auto [&>svg]:bottom-0 [&>svg]:block [&>svg]:h-16 [&>svg]:w-80 [&>svg]:max-w-full lg:pb-4 lg:pr-80 lg:[&>svg]:h-full"
          title={<span className="whitespace-pre-wrap break-words">{post.title}</span>}
        />
        <dl className="my-4 grid gap-3 rounded-sm bg-canvas p-4 text-body-sm sm:grid-cols-2">
          <div>
            <dt className="font-semibold text-ink-secondary">Tác giả</dt>
            <dd className="whitespace-pre-wrap text-ink">
              {post.author.displayName ?? 'Người dùng đã rời'}
            </dd>
          </div>
          <div>
            <dt className="font-semibold text-ink-secondary">Danh mục</dt>
            <dd className="text-ink">{POST_CATEGORY_LABELS[post.category]}</dd>
          </div>
          <div>
            <dt className="font-semibold text-ink-secondary">Trạng thái hiện tại</dt>
            <dd className="font-semibold text-ink">{POST_STATUS_LABELS[post.status]}</dd>
          </div>
          <div>
            <dt className="font-semibold text-ink-secondary">Thời gian tạo</dt>
            <dd className="text-ink">
              <Timestamp value={post.createdAt} />
            </dd>
          </div>
          <div>
            <dt className="font-semibold text-ink-secondary">Cập nhật gần nhất</dt>
            <dd className="text-ink">
              <Timestamp value={post.updatedAt} />
            </dd>
          </div>
          <div>
            <dt className="font-semibold text-ink-secondary">Thời gian đăng</dt>
            <dd className="text-ink">
              <Timestamp value={post.publishedAt} />
            </dd>
          </div>
        </dl>
        <h2 className="text-h2 text-ink">Nội dung</h2>
        <p className="my-4 whitespace-pre-wrap break-words text-body text-ink">{post.content}</p>
        <section aria-label="Thông tin kiểm duyệt" className="border-t border-line pt-4">
          <h2 className="text-h2 text-ink">Thông tin kiểm duyệt</h2>
          <dl className="mt-3 grid gap-2 text-body-sm sm:grid-cols-3">
            <div>
              <dt className="font-semibold text-ink-secondary">Người kiểm duyệt</dt>
              <dd className="whitespace-pre-wrap text-ink">
                {post.reviewedBy ? (post.reviewedBy.displayName ?? 'Người dùng đã rời') : 'Chưa có'}
              </dd>
            </div>
            <div>
              <dt className="font-semibold text-ink-secondary">Thời gian kiểm duyệt</dt>
              <dd className="text-ink">
                <Timestamp value={post.reviewedAt} />
              </dd>
            </div>
            <div>
              <dt className="font-semibold text-ink-secondary">Lý do kiểm duyệt</dt>
              <dd className="whitespace-pre-wrap break-words text-ink">
                {post.reviewReason ?? 'Chưa có'}
              </dd>
            </div>
          </dl>
        </section>
      </Card>
    </article>
  )
}
