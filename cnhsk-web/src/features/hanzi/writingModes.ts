import type { CharacterDetail, WritingMode } from './types'

export const WRITING_MODES: readonly WritingMode[] = ['STROKE', 'RECALL', 'DICTATION']

export const WRITING_MODE_LABELS: Record<WritingMode, { description: string; title: string }> = {
  DICTATION: {
    description: 'Nghe phát âm rồi viết chữ, không có mẫu.',
    title: 'Nghe chép',
  },
  RECALL: {
    description: 'Xem chữ mẫu 5 giây rồi viết lại từ trí nhớ.',
    title: 'Nhớ rồi viết',
  },
  STROKE: {
    description: 'Viết đè lên nét mờ theo đúng thứ tự nét.',
    title: 'Viết theo nét',
  },
}

export interface WritingModeAvailability {
  available: boolean
  reason?: string
}

/** FR-029: chế độ không dùng được phải khoá kèm lý do. */
export function writingModeAvailability(
  character: CharacterDetail,
  mode: WritingMode,
): WritingModeAvailability {
  const { dictationAvailable, recallUnlocked, strokeAvailable } = character.writing
  if (!strokeAvailable) return { available: false, reason: 'Chữ chưa có dữ liệu nét' }
  if (mode === 'RECALL' && !recallUnlocked) {
    return { available: false, reason: 'Viết theo nét trước' }
  }
  if (mode === 'DICTATION' && !dictationAvailable) {
    return { available: false, reason: 'Chữ chưa có âm thanh' }
  }
  return { available: true }
}

/** Chế độ đầu tiên dùng được, để trang luyện mở sẵn một chế độ hợp lệ. */
export function defaultWritingMode(character: CharacterDetail): WritingMode | undefined {
  return WRITING_MODES.find((mode) => writingModeAvailability(character, mode).available)
}
