import { useState } from 'react'
import { ApiError, useApiQuery } from '@/shared/api'
import { useToast } from '@/shared/ui/useToast'
import { listPostCommentsForModeration, moderateComment } from '../api/moderation'
import { canModerateComment } from '../comment-permissions'
import { buildCommentTree } from '../comment-tree'
import type { CommentModeration, ModerationComment } from '../types'
import { useModeratorDraft } from '../use-moderator-draft'
import { CommentModerationForm } from './CommentModerationForm'
import { CommentTree } from './CommentTree'
import { InteractionError } from './InteractionError'
import { useModeratorAction } from '../use-moderator-action'

export type PostModerationCommentsProps = {
  postId: number
  userId: number
  screenId: 'SCR-061' | 'SCR-063'
  resourceId: number
  onChanged?: () => void
}

function StoredCommentForm({ userId, screenId, resourceId, commentId, action, disabled, onSubmit, onCancel }: {
  userId: number; screenId: 'SCR-061' | 'SCR-063'; resourceId: number; commentId: number
  action: CommentModeration['action']; disabled: boolean
  onSubmit: (body: CommentModeration, clearDraft: () => void) => void; onCancel: () => void
}) {
  const draft = useModeratorDraft(userId, screenId, resourceId, `comment-${commentId}-reason`)
  const [confirming, setConfirming] = useState(false)
  return <CommentModerationForm action={action} disabled={disabled} draft={{ reason: draft.text, confirming }} onDraftChange={(next) => {
    if (next.reason !== draft.text) draft.setText(next.reason)
    setConfirming(next.confirming)
  }} onSubmit={(body) => onSubmit(body, draft.clear)} onCancel={() => { draft.clear(); onCancel() }} />
}

export function ModeratorComments({ postId, userId, screenId, resourceId, onChanged, targetId }: PostModerationCommentsProps & { targetId?: number }) {
  const [version, setVersion] = useState(0)
  const query = useApiQuery(async (signal) => ({ comments: await listPostCommentsForModeration(postId, signal), version }), [postId, version])
  const [selection, setSelection] = useState<{ id: number; action: CommentModeration['action'] }>()
  const [error, setError] = useState<unknown>()
  const action = useModeratorAction()
  const { showToast } = useToast()
  const refresh = () => setVersion((value) => value + 1)
  const ready = query.state === 'ready' && query.data?.version === version
  const comments = ready ? query.data?.comments ?? [] : []
  let valid = true
  try { buildCommentTree(comments, postId) } catch { valid = false }
  const target = targetId === undefined ? undefined : comments.find((comment) => comment.id === targetId && comment.postId === postId)
  const selected = selection ? comments.find((comment) => comment.id === selection.id && comment.postId === postId) : undefined
  const eligible = Boolean(ready && valid && selected && selection && canModerateComment(selected, userId, selection.action))
  const renderComments: ModerationComment[] = targetId === undefined ? comments : target ? [target] : []
  const select = (comment: ModerationComment, nextAction: CommentModeration['action']) => { setSelection({ id: comment.id, action: nextAction }); setError(undefined) }
  return <section aria-label="Bình luận kiểm duyệt" className="mt-5 space-y-4">
    {!ready && query.state !== 'error' ? <p role="status">Đang tải bình luận kiểm duyệt…</p> : query.state === 'error' ? <><InteractionError error={query.error} /><button type="button" onClick={refresh}>Thử lại bình luận</button></> : !valid ? <><p role="alert">Dữ liệu bình luận không hợp lệ.</p><button type="button" onClick={refresh}>Thử lại bình luận</button></> : targetId !== undefined && !target ? <><p role="alert">Không tìm thấy metadata bình luận. Không thể kiểm duyệt.</p><button type="button" onClick={refresh}>Thử lại bình luận</button></> : targetId === undefined ? <CommentTree comments={renderComments} postId={postId} onRetry={refresh} actions={{ userId, learner: false, verified: false, guest: false, moderator: true, disabled: action.busy, now: Date.now(), lockedEdits: new Set<number>(), onSelect: (next) => { if (next.kind === 'moderate') select(next.comment, next.action) } }} /> : target ? <article aria-label={`Bình luận ${target.id}`} className="rounded-md border border-line p-4">
      <p>{target.author.displayName ?? 'Người dùng đã rời'}</p><p className="whitespace-pre-wrap">{target.content}</p><p>Trạng thái: {target.status}</p>
      {target.moderationReason ? <p className="whitespace-pre-wrap">Lý do kiểm duyệt: {target.moderationReason}</p> : null}
      {(['HIDE', 'DELETE', 'RESTORE'] as const).filter((item) => canModerateComment(target, userId, item)).map((item) => <button className="mr-4 underline" key={item} type="button" disabled={action.busy} onClick={() => select(target, item)}>{item === 'HIDE' ? 'Ẩn' : item === 'DELETE' ? 'Xoá kiểm duyệt' : 'Khôi phục'}</button>)}
    </article> : null}
    <InteractionError error={error} />
    {selection ? <div hidden={!ready}>
      {!eligible && ready ? <p role="status">Bình luận không còn đủ điều kiện cho thao tác này.</p> : null}
      <StoredCommentForm key={`${selection.id}:${selection.action}`} userId={userId} screenId={screenId} resourceId={resourceId} commentId={selection.id} action={selection.action} disabled={!eligible || action.busy} onCancel={() => setSelection(undefined)} onSubmit={(body, clearDraft) => {
        if (!eligible || !selected || !selection || body.action !== selection.action) return
        action.run(() => moderateComment(selected.id, body), () => {
          clearDraft(); setSelection(undefined); setError(undefined)
          showToast({ message: 'Đã kiểm duyệt bình luận', tone: 'success' })
          refresh(); onChanged?.()
        }, (caught) => {
          setError(caught)
          if (caught instanceof ApiError && (caught.status === 409 || caught.status === 404 || caught.errorCode === 'SELF_MODERATION')) refresh()
        })
      }} />
    </div> : null}
  </section>
}

export function PostModerationComments(props: PostModerationCommentsProps) {
  return <ModeratorComments {...props} />
}
