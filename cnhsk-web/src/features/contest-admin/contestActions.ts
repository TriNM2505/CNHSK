import { ApiError } from '@/shared/api'
import type { AdminContest, ContestAction } from './types'

export const statusLabels = { DRAFT: 'Bản nháp', PUBLISHED: 'Đã công bố', CANCELLED: 'Đã huỷ' }
export const stateLabels = {
  UPCOMING: 'Sắp diễn ra',
  ONGOING: 'Đang diễn ra',
  ENDED: 'Đã kết thúc',
}
export const actionLabels: Record<ContestAction, string> = {
  publish: 'Công bố cuộc thi',
  cancel: 'Huỷ cuộc thi',
  ranking: 'Công bố kết quả',
}
export function editReason(contest: AdminContest): string | undefined {
  if (contest.status === 'CANCELLED') return 'Cuộc thi đã huỷ, không thể sửa.'
  if (contest.status === 'PUBLISHED' && contest.state !== 'UPCOMING')
    return 'Cuộc thi đã bắt đầu, các trường đã khoá.'
}
export function actionReason(
  contest: AdminContest,
  action: ContestAction,
  now = Date.now(),
): string | undefined {
  if (action === 'cancel') {
    if (contest.status === 'CANCELLED') return 'Cuộc thi đã huỷ.'
    if (contest.participantCount > 0) return 'Cuộc thi đã có lượt thi, không thể huỷ.'
  } else if (action === 'publish') {
    if (contest.status !== 'DRAFT') return 'Chỉ công bố cuộc thi đang ở bản nháp.'
    if (contest.examId === null) return 'Chọn đề thi hợp lệ trước khi công bố.'
    if (Date.parse(contest.startsAt) <= now) return 'Giờ bắt đầu phải ở tương lai.'
  } else {
    if (contest.status !== 'PUBLISHED') return 'Cuộc thi phải được công bố trước.'
    if (contest.rankingPublishedAt) return 'Kết quả đã được công bố; không thể công bố lần nữa.'
    if (contest.state !== 'ENDED' || now < Date.parse(contest.endsAt))
      return 'Chỉ công bố kết quả sau giờ kết thúc.'
  }
}
export function isConflict(error: unknown): error is ApiError {
  return error instanceof ApiError && error.status === 409
}
export function errorMessage(error: unknown): string {
  if (!(error instanceof ApiError)) return 'Không thể kết nối tới máy chủ. Vui lòng thử lại.'
  const messages: Record<string, string> = {
    CONTEST_LOCKED: 'Thao tác đã bị khoá. Dữ liệu cuộc thi được tải lại.',
    CONTEST_HAS_ATTEMPTS: 'Cuộc thi đã có lượt thi, không thể huỷ. Dữ liệu được tải lại.',
    CONTEST_RANKING_TOO_EARLY: 'Chưa tới giờ công bố kết quả. Dữ liệu được tải lại.',
    CONTEST_EXAM_INVALID: 'Đề thi không hợp lệ hoặc đã lưu trữ. Vui lòng chọn đề khác.',
  }
  return messages[error.errorCode] ?? error.message
}
export function vietnamTime(value: string | null): string {
  return value === null
    ? 'Chưa có'
    : new Intl.DateTimeFormat('vi-VN', {
        timeZone: 'Asia/Ho_Chi_Minh',
        dateStyle: 'short',
        timeStyle: 'short',
      }).format(new Date(value))
}
export function duration(value: number | null): string {
  return value === null
    ? 'Chưa có'
    : `${Math.floor(value / 60_000)} phút ${Math.floor(value / 1000) % 60} giây`
}
