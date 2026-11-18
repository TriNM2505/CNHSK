import { Link, useLocation } from 'react-router-dom'

import { PageHeader } from '@/shared/ui'

import { POST_CATEGORY_LABELS } from '../labels'
import type { PostDetail } from '../types'

export function CommunityPostDetailView({
  post,
  likeCount,
  canEdit,
}: {
  post: PostDetail
  likeCount: number
  canEdit: boolean
}) {
  const location = useLocation()
  return (
    <article
      aria-label="Nội dung bài viết"
      className="my-5 rounded-md border border-line bg-surface p-5 shadow-sm sm:p-6"
    >
      <PageHeader
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
        className="mb-3"
        title={<span className="whitespace-pre-wrap break-words">{post.title}</span>}
      />
      <p className="text-body-sm font-medium text-ink-secondary">
        {post.author.displayName ?? 'Người dùng đã rời'} · {POST_CATEGORY_LABELS[post.category]}
      </p>
      <time className="mt-1 block text-caption text-ink-muted" dateTime={post.publishedAt}>
        {new Date(post.publishedAt).toLocaleString('vi-VN', { timeZone: 'Asia/Ho_Chi_Minh' })}
      </time>
      <p className="my-5 whitespace-pre-wrap break-words border-t border-line pt-4 text-body text-ink">
        {post.content}
      </p>
      <div className="flex flex-wrap items-center gap-4 border-t border-line pt-3 text-body-sm text-ink-secondary">
        <p aria-label="Số bình luận">{post.commentCount} bình luận</p>
        <p aria-label="Số lượt thích">{likeCount} lượt thích</p>
      </div>
    </article>
  )
}
