import type { CommentStatus } from './types'

export const COMMENT_EDIT_WINDOW_MS = 15 * 60 * 1000

export function canEditComment(comment: { author: { id: number } | null; status: CommentStatus; createdAt: string }, userId: number | undefined, now: number): boolean {
  const created = Date.parse(comment.createdAt)
  const age = now - created
  // UI stops at equality conservatively; the server independently enforces FR-024.
  return userId !== undefined && comment.author?.id === userId && comment.status === 'VISIBLE' && Number.isFinite(created) && age >= 0 && age < COMMENT_EDIT_WINDOW_MS
}

export function canModerateComment(comment: { author: { id: number }; status: CommentStatus; moderationReason: string | null }, userId: number, action: 'HIDE' | 'DELETE' | 'RESTORE'): boolean {
  if (comment.author.id === userId) return false
  if (action === 'RESTORE') return comment.status !== 'VISIBLE' && comment.moderationReason !== 'AUTHOR_DELETED'
  return comment.status === 'VISIBLE'
}
