import { apiRequest } from '@/shared/api'

export type NotificationType =
  | 'POST_APPROVED'
  | 'POST_REJECTED'
  | 'POST_HIDDEN'
  | 'COMMENT'
  | 'REPLY'
  | 'LIKE'
  | 'FOLLOW'

/** A queue staff still have to work through, counted live on the server. */
export type NotificationTask = { key: string; count: number }

export type NotificationItem = {
  key: string
  type: NotificationType | string
  at: string
  unread: boolean
  actorId: number | null
  actorName: string | null
  /** People who liked the post for LIKE; 1 otherwise. */
  total: number
  postId: number | null
  postTitle: string | null
  commentId: number | null
  excerpt: string | null
}

export type NotificationSummary = { unread: number; tasks: NotificationTask[] }
export type NotificationPage = { items: NotificationItem[]; tasks: NotificationTask[] }

export function getNotificationSummary(signal?: AbortSignal): Promise<NotificationSummary> {
  return apiRequest<NotificationSummary>('/api/me/notifications/summary', { signal })
}

export function listNotifications(signal?: AbortSignal): Promise<NotificationPage> {
  return apiRequest<NotificationPage>('/api/me/notifications', { signal })
}

export function markNotificationsSeen(): Promise<void> {
  return apiRequest<void>('/api/me/notifications/seen', { method: 'POST' })
}
