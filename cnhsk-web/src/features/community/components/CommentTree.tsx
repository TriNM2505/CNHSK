import { buildCommentTree, type CommentNode } from '../comment-tree'
import type { Comment, ModerationComment } from '../types'
import { CommentItem, type CommentActions } from './CommentItem'

export function CommentTree({ comments, postId, actions, onRetry }: { comments: readonly (Comment | ModerationComment)[]; postId: number; actions: CommentActions; onRetry: () => void }) {
  let roots: CommentNode<Comment | ModerationComment>[]
  try { roots = buildCommentTree(comments, postId) } catch {
    return <section role="alert"><p>Dữ liệu bình luận không hợp lệ.</p><button type="button" onClick={onRetry}>Thử lại bình luận</button></section>
  }
  const renderNodes = (nodes: typeof roots) => <ul className="space-y-3">{nodes.map(({ value, children }) => <li key={value.id} data-depth={value.depth}>
    <CommentItem comment={value} actions={actions} />
    {children.length ? <div className="ml-4 border-l border-line pl-3">{renderNodes(children)}</div> : null}
  </li>)}</ul>
  return roots.length ? renderNodes(roots) : <p>Chưa có bình luận.</p>
}
