import { CornerDownRight } from 'lucide-react'
import { useState } from 'react'

import { Button } from '@/shared/ui/Button'

import { buildCommentTree, type CommentNode } from '../comment-tree'
import type { Comment, ModerationComment } from '../types'
import { CommentItem, type CommentActions } from './CommentItem'

type AnyComment = Comment | ModerationComment

/** Every reply under a first-level comment, in time order, with the comment it answers. */
function flattenReplies(node: CommentNode<AnyComment>): { reply: AnyComment; parent: AnyComment }[] {
  const result: { reply: AnyComment; parent: AnyComment }[] = []
  const walk = (current: CommentNode<AnyComment>) =>
    current.children.forEach((child) => {
      result.push({ reply: child.value, parent: current.value })
      walk(child)
    })
  walk(node)
  return result.sort((a, b) => a.reply.createdAt.localeCompare(b.reply.createdAt) || a.reply.id - b.reply.id)
}

/**
 * Comments as threads: each first-level comment with its replies folded under it, opened by "Xem N phản hồi". Replies
 * hang off a thread line with a curved link, like a social feed.
 */
export function CommentTree({ comments, postId, actions, onRetry }: { comments: readonly AnyComment[]; postId: number; actions: CommentActions; onRetry: () => void }) {
  const [opened, setOpened] = useState<ReadonlySet<number>>(new Set())
  let roots: CommentNode<AnyComment>[]
  try { roots = buildCommentTree(comments, postId) } catch {
    return <section className="rounded-md border border-danger bg-danger-bg p-4 text-body-sm text-danger" role="alert"><p>Dữ liệu bình luận không hợp lệ.</p><Button className="mt-3" type="button" variant="outline" onClick={onRetry}>Thử lại bình luận</Button></section>
  }
  if (!roots.length) {
    return <p className="rounded-md border border-dashed border-line bg-canvas p-4 text-body-sm text-ink-secondary">Chưa có bình luận.</p>
  }
  const toggle = (id: number) => setOpened((current) => {
    const next = new Set(current)
    if (next.has(id)) next.delete(id)
    else next.add(id)
    return next
  })

  return (
    <ul className="space-y-5">
      {roots.map((root) => {
        const replies = flattenReplies(root)
        const open = opened.has(root.value.id)
        return (
          <li data-depth={root.value.depth} key={root.value.id}>
            <CommentItem actions={actions} comment={root.value} />
            {replies.length > 0 && !open ? (
              <button className="ml-12 mt-2 flex items-center gap-2 text-caption font-semibold text-ink-secondary hover:text-ink" onClick={() => toggle(root.value.id)} type="button">
                <CornerDownRight aria-hidden="true" className="size-4" />
                Xem {replies.length} phản hồi
              </button>
            ) : null}
            {replies.length > 0 && open ? (
              <ul className="ml-5 mt-3 space-y-4 border-l-2 border-line pl-7">
                {replies.map(({ parent, reply }) => (
                  <li className="relative" data-depth={reply.depth} key={reply.id}>
                    <span aria-hidden="true" className="absolute -left-7 top-0 h-5 w-6 rounded-bl-lg border-b-2 border-line" />
                    <CommentItem
                      actions={actions}
                      comment={reply}
                      mention={parent.id !== root.value.id ? parent.author?.displayName ?? undefined : undefined}
                    />
                  </li>
                ))}
                <li>
                  <button className="text-caption font-semibold text-ink-secondary hover:text-ink" onClick={() => toggle(root.value.id)} type="button">
                    Ẩn phản hồi
                  </button>
                </li>
              </ul>
            ) : null}
          </li>
        )
      })}
    </ul>
  )
}
