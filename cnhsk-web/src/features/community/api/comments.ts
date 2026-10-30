import { apiRequest } from '@/shared/api'

import type { Comment, CommentWrite } from '../types'

export function listPostComments(id: number, signal?: AbortSignal): Promise<Comment[]> {
  return apiRequest<Comment[]>(`/api/public/community/posts/${id}/comments`, { signal })
}

export function createComment(postId: number, body: CommentWrite): Promise<Comment> {
  return apiRequest<Comment>(`/api/community/posts/${postId}/comments`, { body, method: 'POST' })
}

export function replyToComment(id: number, body: CommentWrite): Promise<Comment> {
  return apiRequest<Comment>(`/api/community/comments/${id}/replies`, { body, method: 'POST' })
}

export function updateComment(id: number, body: CommentWrite): Promise<Comment> {
  return apiRequest<Comment>(`/api/community/comments/${id}`, { body, method: 'PUT' })
}

export function deleteComment(id: number): Promise<void> {
  return apiRequest<void>(`/api/community/comments/${id}`, { method: 'DELETE' })
}
