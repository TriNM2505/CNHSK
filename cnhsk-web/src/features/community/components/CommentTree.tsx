import { Button } from '@/shared/ui/Button'
import { buildCommentTree, type CommentNode } from '../comment-tree'
import type { Comment, ModerationComment } from '../types'
import { CommentItem, type CommentActions } from './CommentItem'

export function CommentTree({ comments, postId, actions, onRetry }: { comments: readonly (Comment | ModerationComment)[]; postId: number; actions: CommentActions; onRetry: () => void }) {
  let roots: CommentNode<Comment | ModerationComment>[]
  try { roots = buildCommentTree(comments, postId) } catch {
    return <section className="rounded-md border border-danger bg-danger-bg p-4 text-body-sm text-danger" role="alert"><p>Dữ liệu bình luận không hợp lệ.</p><Button className="mt-3" type="button" variant="outline" onClick={onRetry}>Thử lại bình luận</Button></section>
  }
  // Comments are one level: replies written before this change show in time order like any other comment.
  const flat: (Comment | ModerationComment)[] = []
  const collect = (nodes: typeof roots) => nodes.forEach(({ value, children }) => { flat.push(value); collect(children) })
  collect(roots)
  flat.sort((a, b) => a.createdAt.localeCompare(b.createdAt) || a.id - b.id)
  return flat.length ? <ul className="space-y-3">{flat.map((value) => <li key={value.id}>
    <CommentItem comment={value} actions={actions} />
  </li>)}</ul> : <p className="rounded-md border border-dashed border-line bg-canvas p-4 text-body-sm text-ink-secondary">Chưa có bình luận.</p>
}
