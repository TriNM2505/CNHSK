import type { NotificationItem, NotificationTask } from './api'

const quote = (title: string | null) => `“${title ?? 'bài viết'}”`

/** What a notification says and where it leads. */
export function describeNotification(item: NotificationItem): { text: string; detail?: string; to: string } {
  const actor = item.actorName ?? 'Một thành viên'
  const post = item.postId ? `/community/posts/${item.postId}` : '/community'
  switch (item.type) {
    case 'POST_APPROVED':
      return { text: `Bài viết ${quote(item.postTitle)} đã được duyệt và hiện trên bảng tin.`, to: post }
    case 'POST_REJECTED':
      return {
        detail: item.excerpt ?? undefined,
        text: `Bài viết ${quote(item.postTitle)} chưa được duyệt.`,
        to: item.postId ? `/community/posts/${item.postId}/edit` : '/community',
      }
    case 'POST_HIDDEN':
      return { detail: item.excerpt ?? undefined, text: `Bài viết ${quote(item.postTitle)} đã bị ẩn.`, to: '/community' }
    case 'COMMENT':
      return { detail: item.excerpt ?? undefined, text: `${actor} đã bình luận bài ${quote(item.postTitle)}.`, to: post }
    case 'REPLY':
      return { detail: item.excerpt ?? undefined, text: `${actor} đã trả lời bình luận của bạn.`, to: post }
    case 'LIKE':
      return {
        text:
          item.total > 1
            ? `${actor} và ${item.total - 1} người khác đã thích bài ${quote(item.postTitle)}.`
            : `${actor} đã thích bài ${quote(item.postTitle)}.`,
        to: post,
      }
    case 'FOLLOW':
      return { text: `${actor} đã bắt đầu theo dõi bạn.`, to: '/community' }
    default:
      return { text: 'Bạn có thông báo mới.', to: '/' }
  }
}

const TASKS: Record<string, { label: (count: number) => string; to: string }> = {
  PENDING_POSTS: { label: (count) => `${count} bài viết chờ duyệt`, to: '/moderation/posts' },
  PENDING_REPORTS: { label: (count) => `${count} báo cáo vi phạm chờ xử lý`, to: '/moderation/reports' },
}

export function describeTask(task: NotificationTask): { text: string; to: string } {
  const known = TASKS[task.key]
  return known ? { text: known.label(task.count), to: known.to } : { text: `${task.count} việc chờ xử lý`, to: '/' }
}
