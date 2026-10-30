import { Link } from 'react-router-dom'

import { Button } from '@/shared/ui/Button'
import { PageState, type PageStateValue } from '@/shared/ui/PageState'
import { Pagination } from '@/shared/ui/Pagination'

import { POST_CATEGORY_LABELS } from '../labels'
import { formatModerationTimestamp, MODERATION_PAGE_SIZE } from '../moderation-list-query'
import type { ModerationPostPage } from '../types'
import { ModerationNavigation } from './ModerationNavigation'

export type PostModerationQueueViewProps = {
  data?: ModerationPostPage
  error?: string
  onPageChange: (page: number) => void
  onRetry: () => void
  page: number
  state: PageStateValue
}

export function PostModerationQueueView({
  data,
  error,
  onPageChange,
  onRetry,
  page,
  state,
}: PostModerationQueueViewProps) {
  return (
    <main className="mx-auto max-w-content p-4 sm:p-6">
      <ModerationNavigation active="posts" />
      <header className="mb-6 flex flex-wrap items-center justify-between gap-4">
        <div>
          <h1 className="text-h1-sm text-ink sm:text-h1">Bài chờ duyệt</h1>
          <p className="mt-2 text-body text-ink-secondary">Bài gửi trước được duyệt trước.</p>
        </div>
        <Button disabled={state === 'loading'} onClick={onRetry} variant="outline">
          Tải lại hàng đợi
        </Button>
      </header>
      <PageState
        empty={
          <section className="rounded-md border border-dashed border-line p-8 text-center">
            <h2 className="text-h3">Chưa có bài chờ duyệt</h2>
            {page > 0 ? (
              <Button className="mt-4" onClick={() => onPageChange(0)} variant="outline">
                Về trang đầu
              </Button>
            ) : null}
          </section>
        }
        error={
          <section className="rounded-md border border-danger bg-danger-bg p-4" role="alert">
            <p>{error ?? 'Không thể tải bài chờ duyệt.'}</p>
            <Button className="mt-3" onClick={onRetry} variant="outline">
              Thử lại
            </Button>
          </section>
        }
        loading={
          <div aria-label="Đang tải bài chờ duyệt" className="space-y-3" role="status">
            <p>Đang tải bài chờ duyệt…</p>
            {[0, 1, 2].map((item) => (
              <div className="h-28 animate-pulse rounded-md bg-canvas" key={item} />
            ))}
          </div>
        }
        state={state}
      >
        <div aria-label="Danh sách bài chờ duyệt" className="grid gap-4" role="list">
          {data?.items.map((post) => (
            <article
              className="rounded-md border border-line bg-surface p-4"
              key={post.id}
              role="listitem"
            >
              <div className="flex flex-wrap items-start justify-between gap-3">
                <h2 className="text-h3 text-ink">
                  <Link
                    className="hover:text-brand-700"
                    to={`/moderation/posts/${post.id}?page=${page}`}
                  >
                    {post.title}
                  </Link>
                </h2>
                <span className="rounded-pill bg-canvas px-2 py-1 text-caption">Chờ duyệt</span>
              </div>
              <p className="mt-2 text-body-sm text-ink-secondary">
                Danh mục: {POST_CATEGORY_LABELS[post.category]}
              </p>
              <p className="mt-1 text-body-sm text-ink-secondary">
                Tác giả: {post.author.displayName ?? 'Người dùng đã rời'}
              </p>
              <p className="mt-3 whitespace-pre-wrap break-words text-body-sm">{post.excerpt}</p>
              <p className="mt-3 text-caption text-ink-muted">
                Thời gian tạo:{' '}
                <time dateTime={post.createdAt}>{formatModerationTimestamp(post.createdAt)}</time>
              </p>
              <p className="mt-1 text-caption text-ink-muted">
                Cập nhật gần nhất:{' '}
                <time dateTime={post.updatedAt}>{formatModerationTimestamp(post.updatedAt)}</time>
              </p>
            </article>
          ))}
        </div>
        {data && data.totalElements > 0 ? (
          <div className="mt-6">
            <Pagination
              label="Phân trang bài chờ duyệt"
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
