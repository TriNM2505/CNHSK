import { apiRequest } from '@/shared/api'

export type CommunityProfile = {
  id: number
  displayName: string | null
  avatarUrl: string | null
  followerCount: number
  followingCount: number
  approvedPostCount: number
  followedByMe: boolean
  self: boolean
}

export type FollowState = { following: boolean; followerCount: number }

export function getCommunityProfile(id: number, signal?: AbortSignal): Promise<CommunityProfile> {
  return apiRequest<CommunityProfile>(`/api/community/users/${id}`, { signal })
}

export function followUser(id: number): Promise<FollowState> {
  return apiRequest<FollowState>(`/api/community/users/${id}/follow`, { method: 'POST' })
}

export function unfollowUser(id: number): Promise<FollowState> {
  return apiRequest<FollowState>(`/api/community/users/${id}/follow`, { method: 'DELETE' })
}
