import { Link } from 'react-router-dom'

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

function Loading() {
  return <div aria-label="Đang tải bài viết" className="space-y-3" role="status">
    {[0, 1, 2].map((item) => <div className="h-28 animate-pulse rounded-md bg-canvas" key={item} />)}
  </div>
}

function OwnPostCard({ post }: { post: OwnPost }) {
  const showReason = (post.status === 'REJECTED' || post.status === 'HIDDEN') && post.reviewReason
  return (
    <article className="rounded-md border border-line bg-surface p-4">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <h2 className="text-h3 text-ink">{post.title}</h2>
        <span className="rounded-pill bg-canvas px-2 py-1 text-caption">{POST_STATUS_LABELS[post.status]}</span>
      </div>
      <p className="mt-2 text-body-sm text-ink-secondary">{POST_CATEGORY_LABELS[post.category]}</p>
      {showReason ? <p className="mt-3 whitespace-pre-wrap text-body-sm">{post.reviewReason}</p> : null}
      {post.status !== 'HIDDEN' ? (
        <Link className="mt-3 inline-block text-brand-700 underline" to={`/community/posts/${post.id}/edit`}>Sửa bài</Link>
      ) : null}
    </article>
  )
}

export function CommunityPostListView({
  categories, category, data, error, isLearner, mode, onCategoryChange, onPageChange,
  onRetry, onStatusChange, onTabChange, onWrite, page, size, state, status,
}: Props) {
  const items = data?.items ?? []
  const totalElements = data?.totalElements ?? 0
  const totalPages = Math.max(1, Math.ceil(totalElements / size))

  return (
    <main className="mx-auto max-w-content p-4 sm:p-6">
      <header className="mb-6 flex flex-wrap items-start justify-between gap-4">
        <div><h1 className="text-h1-sm text-ink sm:text-h1">Cộng đồng</h1><p className="mt-2 text-body text-ink-secondary">Chia sẻ và học hỏi cùng cộng đồng.</p></div>
        <button className="min-h-touch rounded-sm bg-brand-700 px-4 text-white" onClick={onWrite} type="button">Viết bài</button>
      </header>
      <nav aria-label="Bài viết cộng đồng" className="mb-5 flex gap-4 border-b border-line" role="tablist">
        <button aria-selected={mode === 'public'} className={`min-h-touch border-b-2 px-3 py-2 text-label ${mode === 'public' ? 'border-brand-600 text-brand-700' : 'border-transparent text-ink-secondary'}`} onClick={() => onTabChange('public')} role="tab" type="button">Bài công khai</button>
        {isLearner ? <button aria-selected={mode === 'mine'} className={`min-h-touch border-b-2 px-3 py-2 text-label ${mode === 'mine' ? 'border-brand-600 text-brand-700' : 'border-transparent text-ink-secondary'}`} onClick={() => onTabChange('mine')} role="tab" type="button">Bài viết của tôi</button> : null}
      </nav>
      {mode === 'public' ? (
        <label className="mb-5 flex max-w-sm flex-col gap-1.5 text-label" htmlFor="community-category">
          Danh mục
          <select className="min-h-control rounded-sm border border-line bg-surface px-3" id="community-category" onChange={(event) => onCategoryChange(event.target.value as PostCategory | '')} value={category}>
            <option value="">Tất cả danh mục</option>
            {categories.map((value) => <option key={value} value={value}>{POST_CATEGORY_LABELS[value]}</option>)}
          </select>
        </label>
      ) : (
        <label className="mb-5 flex max-w-sm flex-col gap-1.5 text-label" htmlFor="community-status">
          Trạng thái
          <select className="min-h-control rounded-sm border border-line bg-surface px-3" id="community-status" onChange={(event) => onStatusChange(event.target.value as PostStatus | '')} value={status}>
            <option value="">Tất cả trạng thái</option>
            {(Object.keys(POST_STATUS_LABELS) as PostStatus[]).map((value) => <option key={value} value={value}>{POST_STATUS_LABELS[value]}</option>)}
          </select>
        </label>
      )}
      {state === 'loading' ? <Loading /> : null}
      {state === 'error' ? <section className="rounded-md border border-danger bg-danger-bg p-4" role="alert"><p>{error ?? 'Không thể tải bài viết.'}</p><button className="mt-3 underline" onClick={onRetry} type="button">Thử lại</button></section> : null}
      {state === 'empty' ? <section className="rounded-md border border-dashed border-line p-10 text-center"><h2 className="text-h3">{mode === 'mine' ? 'Chưa có bài viết' : 'Chưa có bài công khai'}</h2>{page > 0 ? <button className="mt-4 text-brand-700 underline" onClick={() => onPageChange(0)} type="button">Về trang đầu</button> : null}</section> : null}
      {state === 'ready' ? (
        <>
          {mode === 'public' ? <div aria-label="Danh sách bài công khai" className="grid gap-4" role="list">
            {(data as PostSummaryPage).items.map((post) => <article className="rounded-md border border-line bg-surface p-4" key={post.id} role="listitem">
              <Link className="text-h3 text-ink hover:text-brand-700" to={`/community/posts/${post.id}`}>{post.title}</Link>
              <p className="mt-2 text-body-sm text-ink-secondary">{POST_CATEGORY_LABELS[post.category]}</p>
              <p className="mt-1 text-body-sm text-ink-secondary">{post.author.displayName ?? 'Người dùng đã rời'}</p>
              <p className="mt-3 whitespace-pre-wrap text-body-sm text-ink-secondary">{post.excerpt}</p>
              <p className="mt-3 text-caption text-ink-muted">
                {formatPublishedAt(post.publishedAt)} · {post.commentCount} bình luận · {post.likeCount} lượt thích
              </p>
            </article>)}
          </div> : <div aria-label="Bài viết của tôi" className="grid gap-4">{(data as OwnPostPage).items.map((post) => <OwnPostCard key={post.id} post={post} />)}</div>}
          {totalElements > 0 ? <nav aria-label="Phân trang bài viết" className="mt-6 flex items-center justify-center gap-4">
            <button disabled={page === 0} onClick={() => onPageChange(page - 1)} type="button">Trước</button>
            <span aria-live="polite">Trang {page + 1} / {totalPages}</span>
            <button disabled={page + 1 >= totalPages} onClick={() => onPageChange(page + 1)} type="button">Sau</button>
          </nav> : null}
          {items.length === 0 ? <p className="text-body-sm text-ink-secondary">Không có bài phù hợp với bộ lọc.</p> : null}
        </>
      ) : null}
    </main>
  )
}

function formatPublishedAt(value: string): string {
  const date = new Date(value)
  return Number.isNaN(date.getTime())
    ? value
    : new Intl.DateTimeFormat('vi-VN', { dateStyle: 'medium', timeStyle: 'short', timeZone: 'Asia/Ho_Chi_Minh' }).format(date)
}
