import type { SessionView } from '@/features/practice'

import type { RecognitionMode } from './types'

export const RECOGNITION_MODES: readonly RecognitionMode[] = ['CHAR_TO_MEANING', 'AUDIO_TO_CHAR']

export const RECOGNITION_MODE_LABELS: Record<
  RecognitionMode,
  { description: string; title: string }
> = {
  AUDIO_TO_CHAR: { description: 'Nghe phát âm rồi chọn chữ Hán đúng.', title: 'Nghe chọn chữ' },
  CHAR_TO_MEANING: {
    description: 'Xem chữ Hán rồi chọn nghĩa tiếng Việt đúng.',
    title: 'Nhìn chữ chọn nghĩa',
  },
}

/** Lượt khôi phục không mang theo chế độ, nên suy ra từ loại câu (spec 006 §0). */
export function modeFromSession(session: SessionView | null): RecognitionMode | undefined {
  const type = session?.items[0]?.type
  if (type === 'CHAR_MEANING_CHOICE') return 'CHAR_TO_MEANING'
  if (type === 'AUDIO_CHAR_CHOICE') return 'AUDIO_TO_CHAR'
  return undefined
}
