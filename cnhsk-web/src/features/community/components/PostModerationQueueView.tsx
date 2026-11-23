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

import { POST_CATEGORY_LABELS } from '../labels'
import { formatModerationTimestamp, MODERATION_PAGE_SIZE } from '../moderation-list-query'
import type { ModerationPostPage } from '../types'

type ModerationPostQueueItem = ModerationPostPage['items'][number]

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
  const columns: DataTableColumn<ModerationPostQueueItem>[] = [
    {
      id: 'post',
      header: 'Bài viết',
      hideOnMobile: true,
      render: (post) => (
        <div className="space-y-1.5">
          <h2 className="text-body font-semibold text-ink">
            <Link
              className="hover:text-brand-700 hover:underline"
              to={`/moderation/posts/${post.id}?page=${page}`}
            >
              {post.title}
            </Link>
          </h2>
          <p className="whitespace-pre-wrap break-words text-body-sm text-ink-secondary">
            {post.excerpt}
          </p>
        </div>
      ),
    },
    {
      id: 'meta',
      header: 'Danh mục & Tác giả',
      hideOnMobile: true,
      render: (post) => (
        <div className="space-y-1 text-body-sm text-ink-secondary">
          <p>Danh mục: {POST_CATEGORY_LABELS[post.category]}</p>
          <p>Tác giả: {post.author.displayName ?? 'Người dùng đã rời'}</p>
        </div>
      ),
    },
    {
      id: 'timestamps',
      header: 'Thời gian',
      hideOnMobile: true,
      render: (post) => (
        <div className="space-y-1 text-caption text-ink-muted">
          <p>
            Thời gian tạo:{' '}
            <time dateTime={post.createdAt}>{formatModerationTimestamp(post.createdAt)}</time>
          </p>
          <p>
            Cập nhật gần nhất:{' '}
            <time dateTime={post.updatedAt}>{formatModerationTimestamp(post.updatedAt)}</time>
          </p>
        </div>
      ),
    },
    {
      id: 'status',
      header: 'Trạng thái',
      hideOnMobile: true,
      render: () => <StatusBadge tone="warning">Chờ duyệt</StatusBadge>,
    },
    {
      id: 'mobileSummary',
      header: 'Mở chi tiết',
      render: (post) => (
        <Link
          className="inline-flex min-h-touch items-center text-body-sm font-semibold text-brand-700 underline"
          to={`/moderation/posts/${post.id}?page=${page}`}
        >
          Mở bài #{post.id}
        </Link>
      ),
    },
  ]

  return (
    <main className="mx-auto max-w-content space-y-6 p-4 sm:p-6">
      <PageBanner
        className="px-4 pt-4 pb-20 shadow-sm sm:px-5 [&>svg]:inset-y-auto [&>svg]:bottom-0 [&>svg]:block [&>svg]:h-16 [&>svg]:w-80 [&>svg]:max-w-full lg:pb-4 lg:pr-80 lg:[&>svg]:h-full"
        actions={
          <Button disabled={state === 'loading'} onClick={onRetry} variant="outline">
            Tải lại hàng đợi
          </Button>
        }
        subtitle="Bài gửi trước được duyệt trước."
        title="Bài chờ duyệt"
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
            description="Tất cả các bài viết gửi lên đều đã được xử lý."
            title="Chưa có bài chờ duyệt"
          />
        }
        error={
          <div role="alert">
            <ErrorState message={error ?? 'Không thể tải bài chờ duyệt.'} onRetry={onRetry} />
          </div>
        }
        loading={
          <div aria-label="Đang tải bài chờ duyệt" className="space-y-3" role="status">
            <p className="text-body-sm text-ink-secondary">Đang tải bài chờ duyệt…</p>
            <Skeleton variant="table" />
          </div>
        }
        state={state}
      >
        <DataTable
          caption="Danh sách bài chờ duyệt"
          columns={columns}
          rowKey={(post) => post.id}
          rows={data?.items ?? []}
        />
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
