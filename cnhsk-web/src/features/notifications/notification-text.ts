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
    // Learning events: total carries the score, points or approved count, postId the service request and
    // commentId the rejected count.
    case 'GRADED':
      return { text: `Bài viết bạn nhờ chấm đã có kết quả: ${item.total}/100 điểm.`, to: `/grading/${item.postId}` }
    case 'GRADING_EXPIRED':
      return { text: 'Bài nhờ chấm đã quá hạn mà chưa có giáo viên chấm.', to: `/grading/${item.postId}` }
    case 'AI_REVIEWED': {
      const rejected = item.commentId ?? 0
      return {
        text: `Giáo viên đã duyệt lô câu hỏi AI #${item.postId}: ${item.total} câu được duyệt${rejected > 0 ? `, ${rejected} câu bị từ chối` : ''}.`,
        to: '/content/questions',
      }
    }
    case 'IMPORT_DONE':
      return { text: `Nhập dữ liệu #${item.postId} đã xong.`, to: `/content/imports/${item.postId}` }
    case 'IMPORT_FAILED':
      return { detail: item.excerpt ?? undefined, text: `Nhập dữ liệu #${item.postId} bị lỗi.`, to: `/content/imports/${item.postId}` }
    case 'AI_GENERATE_DONE':
      return { text: `AI đã tạo xong lô câu hỏi #${item.postId}, đang chờ giáo viên duyệt.`, to: '/content/questions' }
    case 'AI_GENERATE_FAILED':
      return { detail: item.excerpt ?? undefined, text: `Tạo câu hỏi bằng AI #${item.postId} bị lỗi.`, to: '/content/questions' }
    case 'PAYOUT':
      return { text: `Bạn đã nhận ${item.total} điểm tiền chấm bài.`, to: '/teacher/grading' }
    default:
      return { text: 'Bạn có thông báo mới.', to: '/' }
  }
}

const TASKS: Record<string, { label: (count: number) => string; to: string }> = {
  PENDING_POSTS: { label: (count) => `${count} bài viết chờ duyệt`, to: '/moderation/posts' },
  PENDING_REPORTS: { label: (count) => `${count} báo cáo vi phạm chờ xử lý`, to: '/moderation/reports' },
  GRADING_DUE_SOON: { label: (count) => `${count} bài bạn đã nhận sắp hết hạn chấm (dưới 2 giờ)`, to: '/teacher/grading' },
  GRADING_OPEN: { label: (count) => `${count} bài viết đang chờ giáo viên nhận chấm`, to: '/teacher/grading' },
  AI_REVIEW: { label: (count) => `${count} câu hỏi AI chờ duyệt`, to: '/content/ai-review' },
}

export function describeTask(task: NotificationTask): { text: string; to: string } {
  const known = TASKS[task.key]
  return known ? { text: known.label(task.count), to: known.to } : { text: `${task.count} việc chờ xử lý`, to: '/' }
}
