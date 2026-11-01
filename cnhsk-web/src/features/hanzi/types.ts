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

/** FR-001: một dòng trong danh sách chữ để luyện viết. */
export interface CharacterSummary {
  hasAudio: boolean
  hskLevel: HskLevel | null
  id: number
  meaning: string
  pinyin: string | null
  strokeCount: number | null
  text: string
}

export interface CharacterPage {
  items: CharacterSummary[]
  page: number
  size: number
  totalElements: number
}

export interface CharacterListQuery {
  hskLevel?: HskLevel
  page: number
  q?: string
  size: number
}

/** FR-002, FR-003: chi tiết chữ và chế độ viết nào đang dùng được. */
export interface CharacterDetail extends CharacterSummary {
  audioUrl: string | null
  radical: string | null
  sinoViet: string | null
  writing: {
    dictationAvailable: boolean
    lastPracticedAt?: string | null
    recallUnlocked: boolean
    strokeAvailable: boolean
  }
}

export type WritingMode = 'STROKE' | 'RECALL' | 'DICTATION'

export interface WritingAttemptRequest {
  accuracy: number
  completed: boolean
  durationMs: number
  lexemeId: number
  mode: WritingMode
  previewCount?: number
  previewMs?: number
  replayCount?: number
  strokeMisses: number
  usedAnimation?: boolean
}

export interface WritingAttemptResult {
  attemptId: number
  counted: boolean
  recorded: boolean
  recallUnlocked: boolean
}
