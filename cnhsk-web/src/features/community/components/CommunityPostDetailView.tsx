import type { ReactNode } from 'react'
import { Link, useLocation } from 'react-router-dom'

import { PageBanner } from '@/shared/ui'

import { POST_CATEGORY_LABELS } from '../labels'
import { CommentsCountdown } from './CommentsCountdown'
import { MediaGallery } from './MediaGallery'
import type { PostDetail } from '../types'

export function CommunityPostDetailView({
  post,
  likeCount,
  canEdit,
  authorAction,
}: {
  post: PostDetail
  likeCount: number
  canEdit: boolean
  /** Shown beside the author's name, for example the follow button. */
  authorAction?: ReactNode
}) {
  const location = useLocation()
  return (
    <article
      aria-label="Nội dung bài viết"
      className="my-5 rounded-md border border-line bg-surface p-5 shadow-sm sm:p-6"
    >
      <PageBanner
        actions={
          canEdit ? (
            <Link
              className="inline-flex min-h-control items-center rounded-sm border border-line bg-surface px-4 text-label text-brand-700 underline hover:border-brand-600"
              state={{ communityEditorOrigin: `${location.pathname}${location.search}` }}
              to={`/community/posts/${post.id}/edit`}
            >
              Sửa bài viết
            </Link>
          ) : undefined
        }
        className="mb-4 px-4 pt-4 pb-20 shadow-sm sm:px-5 [&>svg]:inset-y-auto [&>svg]:bottom-0 [&>svg]:block [&>svg]:h-16 [&>svg]:w-80 [&>svg]:max-w-full lg:pb-4 lg:pr-80 lg:[&>svg]:h-full"
        title={<span className="whitespace-pre-wrap break-words">{post.title}</span>}
      />
      <div className="flex flex-wrap items-center justify-between gap-3">
        <p className="text-body-sm font-medium text-ink-secondary">
          {post.author.displayName ?? 'Người dùng đã rời'} · {POST_CATEGORY_LABELS[post.category]}
        </p>
        {authorAction}
      </div>
      {post.commentsCloseAt ? <CommentsCountdown className="mt-2" closeAt={post.commentsCloseAt} /> : null}
      <time className="mt-1 block text-caption text-ink-muted" dateTime={post.publishedAt}>
        {new Date(post.publishedAt).toLocaleString('vi-VN', { timeZone: 'Asia/Ho_Chi_Minh' })}
      </time>
      <p className="my-5 whitespace-pre-wrap break-words border-t border-line pt-4 text-body text-ink">
        {post.content}
      </p>
      <MediaGallery className="mb-5" media={post.media} />
      <div className="flex flex-wrap items-center gap-4 border-t border-line pt-3 text-body-sm text-ink-secondary">
        <p aria-label="Số bình luận">{post.commentCount} bình luận</p>
        <p aria-label="Số lượt thích">{likeCount} lượt thích</p>
      </div>
    </article>
  )
}
