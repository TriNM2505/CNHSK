import { apiRequest } from '@/shared/api'

import type {
  LikeState,
  MyPostListParams,
  OwnPost,
  OwnPostPage,
  PostDetail,
  PostSummaryPage,
  PostWrite,
  PublishedPostListParams,
  ViewerState,
} from '../types'

export function listPublishedPosts(
  { category, page = 0, size = 20 }: PublishedPostListParams = {},
  signal?: AbortSignal,
): Promise<PostSummaryPage> {
  const query = new URLSearchParams()
  if (category) query.set('category', category)
  query.set('page', String(page))
  query.set('size', String(size))
  return apiRequest<PostSummaryPage>(`/api/public/community/posts?${query.toString()}`, { signal })
}

export function getPublishedPost(id: number, signal?: AbortSignal): Promise<PostDetail> {
  return apiRequest<PostDetail>(`/api/public/community/posts/${id}`, { signal })
}

export function createPost(body: PostWrite): Promise<OwnPost> {
  return apiRequest<OwnPost>('/api/community/posts', { body, method: 'POST' })
}

export function updatePost(id: number, body: PostWrite): Promise<OwnPost> {
  return apiRequest<OwnPost>(`/api/community/posts/${id}`, { body, method: 'PUT' })
}

export function listMyPosts(
  { status, page = 0, size = 20 }: MyPostListParams = {},
  signal?: AbortSignal,
): Promise<OwnPostPage> {
  const query = new URLSearchParams()
  if (status) query.set('status', status)
  query.set('page', String(page))
  query.set('size', String(size))
  return apiRequest<OwnPostPage>(`/api/community/posts/mine?${query.toString()}`, { signal })
}

export function getMyPost(id: number, signal?: AbortSignal): Promise<OwnPost> {
  return apiRequest<OwnPost>(`/api/community/posts/mine/${id}`, { signal })
}

export function getViewerState(id: number, signal?: AbortSignal): Promise<ViewerState> {
  return apiRequest<ViewerState>(`/api/community/posts/${id}/viewer-state`, { signal })
}

export function likePost(id: number): Promise<LikeState> {
  return apiRequest<LikeState>(`/api/community/posts/${id}/like`, { method: 'POST' })
}

export function unlikePost(id: number): Promise<LikeState> {
  return apiRequest<LikeState>(`/api/community/posts/${id}/like`, { method: 'DELETE' })
}
