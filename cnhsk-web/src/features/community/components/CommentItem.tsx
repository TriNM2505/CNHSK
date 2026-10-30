import { Link } from 'react-router-dom'
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
  return <article aria-label={`Bình luận ${comment.id}`} className="my-3 rounded-md border border-line p-3">
    {privileged || visible ? <>
      <p>{comment.author?.displayName ?? 'Người dùng đã rời'}</p>
      <p className="whitespace-pre-wrap">{comment.content}</p>
    </> : <p>{comment.status === 'HIDDEN' ? '[đã bị ẩn]' : '[đã xoá]'}</p>}
    <time dateTime={comment.createdAt}>{new Date(comment.createdAt).toLocaleString('vi-VN', { timeZone: 'Asia/Ho_Chi_Minh' })}</time>
    {'editedAt' in comment && comment.editedAt ? <span> · Đã sửa</span> : null}
    {privileged ? <div><p>Trạng thái: {comment.status}</p>{comment.moderationReason ? <p className="whitespace-pre-wrap">Lý do kiểm duyệt: {comment.moderationReason}</p> : null}{comment.moderatedBy ? <p>Người kiểm duyệt: {comment.moderatedBy.displayName ?? 'Người dùng đã rời'}</p> : null}</div> : null}
    <div className="mt-3 flex flex-wrap gap-3">
      {actions.guest ? <Link to="/login">Trả lời</Link> : canWrite ? <button disabled={actions.disabled} type="button" onClick={() => actions.onSelect({ kind: 'reply', comment })}>Trả lời</button> : null}
      {canWrite && canEditComment(comment, actions.userId, actions.now) && !actions.lockedEdits.has(comment.id) ? <button disabled={actions.disabled} type="button" onClick={() => actions.onSelect({ kind: 'edit', comment })}>Sửa</button> : null}
      {canWrite && visible && own ? <button disabled={actions.disabled} type="button" onClick={() => actions.onSelect({ kind: 'delete', comment })}>Xoá</button> : null}
      {visible && actions.guest ? <Link to="/login">Báo cáo</Link> : visible && canWrite && !own ? <button disabled={actions.disabled} type="button" onClick={() => actions.onSelect({ kind: 'report', comment })}>Báo cáo</button> : null}
      {privileged && actions.moderator && actions.userId !== undefined ? (['HIDE', 'DELETE', 'RESTORE'] as const).filter((action) => canModerateComment(comment, actions.userId!, action)).map((action) => <button disabled={actions.disabled} key={action} type="button" onClick={() => actions.onSelect({ kind: 'moderate', comment, action })}>{action === 'HIDE' ? 'Ẩn' : action === 'DELETE' ? 'Xoá kiểm duyệt' : 'Khôi phục'}</button>) : null}
    </div>
  </article>
}
