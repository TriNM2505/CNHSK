import type { PostCategory, PostStatus, ReportReason } from './types'

export const POST_CATEGORIES = ['EXPERIENCE', 'QUESTION', 'STUDY_BUDDY', 'CULTURE', 'JOB'] as const satisfies readonly PostCategory[]

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

export const REPORT_REASON_LABELS = {
  SPAM: 'Spam, quảng cáo',
  OFFENSIVE: 'Ngôn từ xúc phạm',
  HARASSMENT: 'Quấy rối, công kích cá nhân',
  COPYRIGHT: 'Vi phạm bản quyền, chép đề thi',
  OTHER: 'Khác — ghi rõ',
} satisfies Record<ReportReason, string>
