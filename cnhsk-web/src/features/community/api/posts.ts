import { apiRequest } from '@/shared/api'

import type {
  LikeState,
  ModeratorPostWrite,
  MyPostListParams,
  OwnPost,
  PinnedPost,
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

/** Which of the given posts the learner has liked; at most 50 ids. */
export function listLikedPostIds(ids: readonly number[], signal?: AbortSignal): Promise<number[]> {
  return apiRequest<number[]>(`/api/community/posts/liked?ids=${ids.join(',')}`, { signal })
}

export function listPinnedPosts(signal?: AbortSignal): Promise<PinnedPost[]> {
  return apiRequest<PinnedPost[]>('/api/public/community/posts/pinned', { signal })
}

/** Moderators publish straight away, without review. */
export function createModeratorPost(body: ModeratorPostWrite): Promise<OwnPost> {
  return apiRequest<OwnPost>('/api/community/moderation/posts', { body, method: 'POST' })
}

/** Pin until a future moment, or unpin with null. */
export function pinPost(id: number, pinnedUntil: string | null): Promise<void> {
  return apiRequest<void>(`/api/community/moderation/posts/${id}/pin`, { body: { pinnedUntil }, method: 'PUT' })
}

/** Approved posts by the people the learner follows. */
export function listFollowingPosts(
  { page, size }: { page: number; size: number },
  signal?: AbortSignal,
): Promise<PostSummaryPage> {
  return apiRequest<PostSummaryPage>(`/api/community/posts/following?page=${page}&size=${size}`, { signal })
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
