import { apiRequest } from '@/shared/api'

import type {
  CommentModeration,
  ModerationComment,
  ModerationPost,
  ModerationPostPage,
  PaginationParams,
  RejectWrite,
  ReportCaseDetail,
  ReportCasePage,
  ReportDecision,
} from '../types'

export function listPendingPosts(
  { page = 0, size = 20 }: PaginationParams = {},
  signal?: AbortSignal,
): Promise<ModerationPostPage> {
  const query = new URLSearchParams({ page: String(page), size: String(size) })
  return apiRequest<ModerationPostPage>(`/api/community/moderation/posts?${query.toString()}`, {
    signal,
  })
}

export function getModerationPost(id: number, signal?: AbortSignal): Promise<ModerationPost> {
  return apiRequest<ModerationPost>(`/api/community/moderation/posts/${id}`, { signal })
}

export function approvePost(id: number): Promise<ModerationPost> {
  return apiRequest<ModerationPost>(`/api/community/moderation/posts/${id}/approve`, {
    method: 'PATCH',
  })
}

export function rejectPost(id: number, body: RejectWrite): Promise<ModerationPost> {
  return apiRequest<ModerationPost>(`/api/community/moderation/posts/${id}/reject`, {
    body,
    method: 'PATCH',
  })
}

export function listPendingReportCases(
  { page = 0, size = 20 }: PaginationParams = {},
  signal?: AbortSignal,
): Promise<ReportCasePage> {
  const query = new URLSearchParams({ page: String(page), size: String(size) })
  return apiRequest<ReportCasePage>(`/api/community/moderation/reports?${query.toString()}`, {
    signal,
  })
}

export function getReportCase(id: number, signal?: AbortSignal): Promise<ReportCaseDetail> {
  return apiRequest<ReportCaseDetail>(`/api/community/moderation/reports/${id}`, { signal })
}

export function decideReportCase(id: number, body: ReportDecision): Promise<ReportCaseDetail> {
  return apiRequest<ReportCaseDetail>(`/api/community/moderation/reports/${id}`, {
    body,
    method: 'PATCH',
  })
}

export function listPostCommentsForModeration(
  id: number,
  signal?: AbortSignal,
): Promise<ModerationComment[]> {
  return apiRequest<ModerationComment[]>(`/api/community/moderation/posts/${id}/comments`, {
    signal,
  })
}

export function moderateComment(id: number, body: CommentModeration): Promise<ModerationComment> {
  return apiRequest<ModerationComment>(`/api/community/moderation/comments/${id}`, {
    body,
    method: 'PATCH',
  })
}
