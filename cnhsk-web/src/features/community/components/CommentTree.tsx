import { Button } from '@/shared/ui/Button'
import { buildCommentTree, type CommentNode } from '../comment-tree'
import type { Comment, ModerationComment } from '../types'
import { CommentItem, type CommentActions } from './CommentItem'

export function CommentTree({ comments, postId, actions, onRetry }: { comments: readonly (Comment | ModerationComment)[]; postId: number; actions: CommentActions; onRetry: () => void }) {
  let roots: CommentNode<Comment | ModerationComment>[]
  try { roots = buildCommentTree(comments, postId) } catch {
    return <section className="rounded-md border border-danger bg-danger-bg p-4 text-body-sm text-danger" role="alert"><p>Dữ liệu bình luận không hợp lệ.</p><Button className="mt-3" type="button" variant="outline" onClick={onRetry}>Thử lại bình luận</Button></section>
  }
  const renderNodes = (nodes: typeof roots) => <ul className="space-y-3">{nodes.map(({ value, children }) => <li key={value.id} data-depth={value.depth}>
    <CommentItem comment={value} actions={actions} />
    {children.length ? <div className="ml-4 border-l-2 border-line pl-3 sm:ml-6 sm:pl-4">{renderNodes(children)}</div> : null}
  </li>)}</ul>
  return roots.length ? renderNodes(roots) : <p className="rounded-md border border-dashed border-line bg-canvas p-4 text-body-sm text-ink-secondary">Chưa có bình luận.</p>
}
