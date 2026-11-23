import { Link } from 'react-router-dom'

import { cn } from '@/shared/lib/cn'
import { Button } from '@/shared/ui/Button'

import { canEditComment, canModerateComment } from '../comment-permissions'
import { formatRelative } from '../relative-time'
import type { Comment, CommentModeration, ModerationComment } from '../types'

export type DiscussionSelection =
  | { kind: 'new' }
  | { kind: 'reply' | 'edit' | 'delete' | 'report'; comment: Comment | ModerationComment }
  | { kind: 'moderate'; comment: ModerationComment; action: CommentModeration['action'] }

export type CommentActions = {
  userId?: number; learner: boolean; verified: boolean; moderator: boolean; guest: boolean
  disabled: boolean; now: number; lockedEdits: ReadonlySet<number>; onSelect: (selection: DiscussionSelection) => void
}

const actionClass =
  'inline-flex min-h-8 items-center text-caption font-semibold text-ink-secondary hover:text-ink disabled:opacity-50 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus'

/**
 * A comment the way a social feed shows it: avatar, "Name · 2 giờ", the text, then small text actions. A reply that
 * answers someone other than the thread's first comment starts with that person's name in colour.
 */
export function CommentItem({
  comment,
  actions,
  mention,
}: {
  comment: Comment | ModerationComment
  actions: CommentActions
  mention?: string
}) {
  const privileged = 'moderationReason' in comment
  const visible = comment.status === 'VISIBLE'
  const own = actions.userId !== undefined && comment.author?.id === actions.userId
  const canWrite = actions.learner && actions.verified
  const name = comment.author?.displayName ?? 'Người dùng đã rời'
  const small = comment.depth > 0
  return (
    <article aria-label={`Bình luận ${comment.id}`} className="flex min-w-0 gap-3">
      <span
        aria-hidden="true"
        className={cn(
          'flex shrink-0 items-center justify-center rounded-full bg-brand-50 font-semibold text-brand-700',
          small ? 'size-8 text-label' : 'size-10 text-body',
        )}
      >
        {privileged || visible ? name.trim().charAt(0).toLocaleUpperCase('vi') || '?' : '–'}
      </span>
      <div className="min-w-0 flex-1">
        {privileged || visible ? (
          <>
            <p className="text-caption text-ink-muted">
              <span className="text-label text-ink">{name}</span> ·{' '}
              <time dateTime={comment.createdAt} title={new Date(comment.createdAt).toLocaleString('vi-VN', { timeZone: 'Asia/Ho_Chi_Minh' })}>
                {formatRelative(comment.createdAt)}
              </time>
              {'editedAt' in comment && comment.editedAt ? ' · Đã sửa' : null}
            </p>
            <p className="mt-0.5 whitespace-pre-wrap break-words text-body text-ink">
              {mention ? <span className="font-semibold text-brand-700">{mention} </span> : null}
              {comment.content}
            </p>
          </>
        ) : (
          <p className="text-body-sm italic text-ink-muted">{comment.status === 'HIDDEN' ? '[đã bị ẩn]' : '[đã xoá]'}</p>
        )}
        {privileged ? (
          <div className="mt-2 rounded-sm bg-canvas p-2.5 text-caption text-ink-secondary">
            <p>Trạng thái: {comment.status}</p>
            {comment.moderationReason ? <p className="whitespace-pre-wrap">Lý do kiểm duyệt: {comment.moderationReason}</p> : null}
            {comment.moderatedBy ? <p>Người kiểm duyệt: {comment.moderatedBy.displayName ?? 'Người dùng đã rời'}</p> : null}
          </div>
        ) : null}
        <div className="mt-1 flex flex-wrap items-center gap-x-4">
          {!visible ? null : actions.guest ? <Link className={actionClass} to="/login">Trả lời</Link> : canWrite ? <button className={actionClass} disabled={actions.disabled} onClick={() => actions.onSelect({ kind: 'reply', comment })} type="button">Trả lời</button> : null}
          {canWrite && canEditComment(comment, actions.userId, actions.now) && !actions.lockedEdits.has(comment.id) ? <button className={actionClass} disabled={actions.disabled} onClick={() => actions.onSelect({ kind: 'edit', comment })} type="button">Sửa</button> : null}
          {canWrite && visible && own ? <button className={actionClass} disabled={actions.disabled} onClick={() => actions.onSelect({ kind: 'delete', comment })} type="button">Xoá</button> : null}
          {visible && actions.guest ? <Link className={actionClass} to="/login">Báo cáo</Link> : visible && canWrite && !own ? <button className={actionClass} disabled={actions.disabled} onClick={() => actions.onSelect({ kind: 'report', comment })} type="button">Báo cáo</button> : null}
          {privileged && actions.moderator && actions.userId !== undefined ? (['HIDE', 'DELETE', 'RESTORE'] as const).filter((action) => canModerateComment(comment, actions.userId!, action)).map((action) => <Button disabled={actions.disabled} key={action} size="sm" type="button" variant="outline" onClick={() => actions.onSelect({ kind: 'moderate', comment, action })}>{action === 'HIDE' ? 'Ẩn' : action === 'DELETE' ? 'Xoá kiểm duyệt' : 'Khôi phục'}</Button>) : null}
        </div>
      </div>
    </article>
  )
}
