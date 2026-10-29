import type { SessionView } from '@/features/practice'
import type { HskLevel } from '@/shared/ui'

/** Spec 006 FR-004: hai chế độ nhận diện chữ. */
export type RecognitionMode = 'CHAR_TO_MEANING' | 'AUDIO_TO_CHAR'

export interface StartRecognitionRequest {
  count?: number
  hskLevel: HskLevel
  mode: RecognitionMode
}

/** FR-008: không còn chữ phù hợp — trả 200, không phải lỗi. */
export interface NoCharactersResult {
  reason: 'NO_CHARACTERS'
  session: null
}

export type StartRecognitionResponse = SessionView | NoCharactersResult
