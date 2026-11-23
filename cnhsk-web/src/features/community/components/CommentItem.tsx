import { Link } from 'react-router-dom'
import { Button } from '@/shared/ui/Button'
import { canEditComment, canModerateComment } from '../comment-permissions'
import type { Comment, CommentModeration, ModerationComment } from '../types'

export type DiscussionSelection =
  | { kind: 'new' }
  | { kind: 'reply' | 'edit' | 'delete' | 'report'; comment: Comment | ModerationComment }
  | { kind: 'moderate'; comment: ModerationComment; action: CommentModeration['action'] }

export type CommentActions = {
  userId?: number; learner: boolean; verified: boolean; moderator: boolean; guest: boolean
  disabled: boolean; now: number; lockedEdits: ReadonlySet<number>; onSelect: (selection: DiscussionSelection) => void
}

export function CommentItem({ comment, actions }: { comment: Comment | ModerationComment; actions: CommentActions }) {
  const privileged = 'moderationReason' in comment
  const visible = comment.status === 'VISIBLE'
  const own = actions.userId !== undefined && comment.author?.id === actions.userId
  const canWrite = actions.learner && actions.verified
  return <article aria-label={`Bình luận ${comment.id}`} className="my-3 rounded-md border border-line bg-surface p-4 shadow-sm">
    {privileged || visible ? <>
      <p className="text-body-sm font-semibold text-ink">{comment.author?.displayName ?? 'Người dùng đã rời'}</p>
      <p className="mt-1 whitespace-pre-wrap break-words text-body text-ink">{comment.content}</p>
    </> : <p className="text-body-sm italic text-ink-muted">{comment.status === 'HIDDEN' ? '[đã bị ẩn]' : '[đã xoá]'}</p>}
    <div className="mt-2 flex flex-wrap items-center gap-1 text-caption text-ink-muted">
      <time dateTime={comment.createdAt}>{new Date(comment.createdAt).toLocaleString('vi-VN', { timeZone: 'Asia/Ho_Chi_Minh' })}</time>
      {'editedAt' in comment && comment.editedAt ? <span> · Đã sửa</span> : null}
    </div>
    {privileged ? <div className="mt-2 rounded-sm bg-canvas p-2.5 text-caption text-ink-secondary"><p>Trạng thái: {comment.status}</p>{comment.moderationReason ? <p className="whitespace-pre-wrap">Lý do kiểm duyệt: {comment.moderationReason}</p> : null}{comment.moderatedBy ? <p>Người kiểm duyệt: {comment.moderatedBy.displayName ?? 'Người dùng đã rời'}</p> : null}</div> : null}
    <div className="mt-3 flex flex-wrap gap-2">
      {canWrite && canEditComment(comment, actions.userId, actions.now) && !actions.lockedEdits.has(comment.id) ? <Button disabled={actions.disabled} size="sm" type="button" variant="ghost" onClick={() => actions.onSelect({ kind: 'edit', comment })}>Sửa</Button> : null}
      {canWrite && visible && own ? <Button disabled={actions.disabled} size="sm" type="button" variant="ghost" onClick={() => actions.onSelect({ kind: 'delete', comment })}>Xoá</Button> : null}
      {visible && actions.guest ? <Link className="inline-flex min-h-touch items-center text-body-sm font-semibold text-brand-700 underline" to="/login">Báo cáo</Link> : visible && canWrite && !own ? <Button disabled={actions.disabled} size="sm" type="button" variant="ghost" onClick={() => actions.onSelect({ kind: 'report', comment })}>Báo cáo</Button> : null}
      {privileged && actions.moderator && actions.userId !== undefined ? (['HIDE', 'DELETE', 'RESTORE'] as const).filter((action) => canModerateComment(comment, actions.userId!, action)).map((action) => <Button disabled={actions.disabled} key={action} size="sm" type="button" variant="outline" onClick={() => actions.onSelect({ kind: 'moderate', comment, action })}>{action === 'HIDE' ? 'Ẩn' : action === 'DELETE' ? 'Xoá kiểm duyệt' : 'Khôi phục'}</Button>) : null}
    </div>
  </article>
}
