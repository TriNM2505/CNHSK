import { Link, useLocation } from 'react-router-dom'

import { cn } from '@/shared/lib/cn'
import {
  Button,
  Card,
  EmptyState,
  ErrorState,
  FormField,
  PageHeader,
  PageState,
  Pagination,
  Select,
  Skeleton,
  StatusBadge,
  type StatusTone,
} from '@/shared/ui'

import { POST_CATEGORY_LABELS, POST_STATUS_LABELS } from '../labels'
import type { OwnPost, OwnPostPage, PostCategory, PostStatus, PostSummaryPage } from '../types'

export type ListState = 'loading' | 'empty' | 'error' | 'ready'

type Props = {
  categories: readonly PostCategory[]
  category: PostCategory | ''
  data: PostSummaryPage | OwnPostPage | undefined
  error?: string
  isLearner: boolean
  mode: 'public' | 'mine'
  onCategoryChange: (value: PostCategory | '') => void
  onPageChange: (page: number) => void
  onRetry: () => void
  onStatusChange: (value: PostStatus | '') => void
  onTabChange: (tab: 'public' | 'mine') => void
  onWrite: () => void
  page: number
  size: number
  state: ListState
  status: PostStatus | ''
}

const ownStatusTones: Record<PostStatus, StatusTone> = {
  DRAFT: 'neutral',
  PENDING: 'warning',
  APPROVED: 'success',
  REJECTED: 'danger',
  HIDDEN: 'danger',
}

function Loading() {
  return (
    <div aria-label="Đang tải bài viết" className="space-y-3" role="status">
      <p className="text-body-sm text-ink-secondary">Đang tải bài viết…</p>
      <Skeleton variant="card" />
      <Skeleton variant="card" />
      <Skeleton variant="card" />
    </div>
  )
}

function OwnPostCard({ post }: { post: OwnPost }) {
  const location = useLocation()
  const showReason = (post.status === 'REJECTED' || post.status === 'HIDDEN') && post.reviewReason
  return (
    <article className="rounded-md border border-line bg-surface p-5 shadow-sm">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <h2 className="text-h3 text-ink">{post.title}</h2>
        <StatusBadge tone={ownStatusTones[post.status]}>
          {POST_STATUS_LABELS[post.status]}
        </StatusBadge>
      </div>
      <p className="mt-2 text-body-sm text-ink-secondary">{POST_CATEGORY_LABELS[post.category]}</p>
      {showReason ? (
        <p className="mt-3 whitespace-pre-wrap rounded-sm border border-line bg-canvas p-3 text-body-sm text-ink">
          {post.reviewReason}
        </p>
      ) : null}
      {post.status !== 'HIDDEN' ? (
        <Link
          className="mt-4 inline-block text-label text-brand-700 underline hover:text-brand-800"
          state={{ communityEditorOrigin: `${location.pathname}${location.search}` }}
          to={`/community/posts/${post.id}/edit`}
        >
          Sửa bài
        </Link>
      ) : null}
    </article>
  )
}

export function CommunityPostListView({
  categories,
  category,
  data,
  error,
  isLearner,
  mode,
  onCategoryChange,
  onPageChange,
  onRetry,
  onStatusChange,
  onTabChange,
  onWrite,
  page,
  size,
  state,
  status,
}: Props) {
  const items = data?.items ?? []
  const totalElements = data?.totalElements ?? 0

  return (
    <main className="mx-auto w-full min-w-0 max-w-content space-y-6 p-4 pb-16 sm:p-6">
      <PageHeader
        actions={
          <Button onClick={onWrite} type="button">
            Viết bài
          </Button>
        }
        className="mb-0"
        subtitle="Chia sẻ và học hỏi cùng cộng đồng."
        title="Cộng đồng"
      />

      <nav
        aria-label="Bài viết cộng đồng"
        className="flex gap-4 border-b border-line"
        role="tablist"
      >
        <Button
          aria-selected={mode === 'public'}
          className={cn(
            'rounded-none border-x-0 border-t-0 border-b-2 px-3 py-2 text-label',
            mode === 'public'
              ? 'border-brand-600 text-brand-700'
              : 'border-transparent text-ink-secondary',
          )}
          onClick={() => onTabChange('public')}
          role="tab"
          type="button"
          variant="ghost"
        >
          Bài công khai
        </Button>
        {isLearner ? (
          <Button
            aria-selected={mode === 'mine'}
            className={cn(
              'rounded-none border-x-0 border-t-0 border-b-2 px-3 py-2 text-label',
              mode === 'mine'
                ? 'border-brand-600 text-brand-700'
                : 'border-transparent text-ink-secondary',
            )}
            onClick={() => onTabChange('mine')}
            role="tab"
            type="button"
            variant="ghost"
          >
            Bài viết của tôi
          </Button>
        ) : null}
      </nav>

      <Card className="p-4 shadow-sm">
        {mode === 'public' ? (
          <FormField className="mb-0 max-w-sm" htmlFor="community-category" label="Danh mục">
            <Select
              id="community-category"
              onChange={(event) => onCategoryChange(event.target.value as PostCategory | '')}
              value={category}
            >
              <option value="">Tất cả danh mục</option>
              {categories.map((value) => (
                <option key={value} value={value}>
                  {POST_CATEGORY_LABELS[value]}
                </option>
              ))}
            </Select>
          </FormField>
        ) : (
          <FormField className="mb-0 max-w-sm" htmlFor="community-status" label="Trạng thái">
            <Select
              id="community-status"
              onChange={(event) => onStatusChange(event.target.value as PostStatus | '')}
              value={status}
            >
              <option value="">Tất cả trạng thái</option>
              {(Object.keys(POST_STATUS_LABELS) as PostStatus[]).map((value) => (
                <option key={value} value={value}>
                  {POST_STATUS_LABELS[value]}
                </option>
              ))}
            </Select>
          </FormField>
        )}
      </Card>

      <PageState
        empty={
          <EmptyState
            action={
              page > 0 ? (
                <Button onClick={() => onPageChange(0)} type="button" variant="outline">
                  Về trang đầu
                </Button>
              ) : undefined
            }
            description="Hãy chọn bộ lọc khác hoặc chia sẻ bài viết mới cùng cộng đồng."
            title={mode === 'mine' ? 'Chưa có bài viết' : 'Chưa có bài công khai'}
          />
        }
        error={
          <div role="alert">
            <ErrorState
              message={error ?? 'Không thể tải bài viết.'}
              onRetry={onRetry}
              title="Không thể tải bài viết"
            />
          </div>
        }
        loading={<Loading />}
        state={state}
      >
        <>
          {mode === 'public' ? (
            <div aria-label="Danh sách bài công khai" className="grid gap-4" role="list">
              {((data as PostSummaryPage | undefined)?.items ?? []).map((post) => (
                <article
                  className="rounded-md border border-line bg-surface p-5 shadow-sm transition hover:border-brand-600"
                  key={post.id}
                  role="listitem"
                >
                  <Link
                    className="text-h3 text-ink hover:text-brand-700 hover:underline"
                    to={`/community/posts/${post.id}`}
                  >
                    {post.title}
                  </Link>
                  <p className="mt-2 text-body-sm font-medium text-ink-secondary">
                    {POST_CATEGORY_LABELS[post.category]}
                  </p>
                  <p className="mt-1 text-body-sm text-ink-secondary">
                    {post.author.displayName ?? 'Người dùng đã rời'}
                  </p>
                  <p className="mt-3 whitespace-pre-wrap break-words text-body-sm text-ink-secondary">
                    {post.excerpt}
                  </p>
                  <p className="mt-4 border-t border-line pt-3 text-caption text-ink-muted">
                    {formatPublishedAt(post.publishedAt)} · {post.commentCount} bình luận ·{' '}
                    {post.likeCount} lượt thích
                  </p>
                </article>
              ))}
            </div>
          ) : (
            <div aria-label="Bài viết của tôi" className="grid gap-4">
              {((data as OwnPostPage | undefined)?.items ?? []).map((post) => (
                <OwnPostCard key={post.id} post={post} />
              ))}
            </div>
          )}
          {totalElements > 0 ? (
            <div className="mt-6">
              <Pagination
                label="Phân trang bài viết"
                onChange={(nextPage) => onPageChange(nextPage - 1)}
                page={page + 1}
                size={size}
                total={totalElements}
              />
            </div>
          ) : null}
          {items.length === 0 ? (
            <p className="text-body-sm text-ink-secondary">Không có bài phù hợp với bộ lọc.</p>
          ) : null}
        </>
      </PageState>
    </main>
  )
}

function formatPublishedAt(value: string): string {
  const date = new Date(value)
  return Number.isNaN(date.getTime())
    ? value
    : new Intl.DateTimeFormat('vi-VN', {
        dateStyle: 'medium',
        timeStyle: 'short',
        timeZone: 'Asia/Ho_Chi_Minh',
      }).format(date)
}
