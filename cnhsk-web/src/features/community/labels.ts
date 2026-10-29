import type { PostCategory, PostStatus } from './types'

export const POST_CATEGORY_LABELS = {
  EXPERIENCE: 'Chia sẻ kinh nghiệm',
  QUESTION: 'Hỏi bài',
  STUDY_BUDDY: 'Tìm bạn học',
  CULTURE: 'Văn hoá',
  JOB: 'Việc làm',
} satisfies Record<PostCategory, string>

export const POST_STATUS_LABELS = {
  DRAFT: 'Bản nháp',
  PENDING: 'Chờ duyệt',
  APPROVED: 'Đã đăng',
  REJECTED: 'Bị từ chối',
  HIDDEN: 'Đã bị ẩn',
} satisfies Record<PostStatus, string>
