import { Link } from 'react-router-dom'
import { POST_CATEGORY_LABELS } from '../labels'
import type { PostDetail } from '../types'

export function CommunityPostDetailView({ post, likeCount, canEdit }: { post: PostDetail; likeCount: number; canEdit: boolean }) {
  return <article aria-label="Nội dung bài viết" className="mb-6 rounded-md border border-line bg-surface p-4">
    <h1 className="whitespace-pre-wrap text-h1-sm text-ink">{post.title}</h1>
    <p className="mt-3">{post.author.displayName ?? 'Người dùng đã rời'} · {POST_CATEGORY_LABELS[post.category]}</p>
    <time dateTime={post.publishedAt}>{new Date(post.publishedAt).toLocaleString('vi-VN', { timeZone: 'Asia/Ho_Chi_Minh' })}</time>
    <p className="my-5 whitespace-pre-wrap">{post.content}</p>
    <p aria-label="Số bình luận">{post.commentCount} bình luận</p>
    <p aria-label="Số lượt thích">{likeCount} lượt thích</p>
    {canEdit ? <Link className="mt-3 inline-block underline" to={`/community/posts/${post.id}/edit`}>Sửa bài viết</Link> : null}
  </article>
}
