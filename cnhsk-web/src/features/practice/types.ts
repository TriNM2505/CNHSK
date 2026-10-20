export type PracticeSource =
  | 'TOPIC_STUDY'
  | 'TOPIC_PRACTICE'
  | 'TOPIC_TEST'
  | 'GRAMMAR_LESSON'
  | 'PRON_STAGE'
  | 'CHAR_RECOGNITION'
  | 'CHAR_WRITING'
  | 'WRITING_CHALLENGE'
  | 'GAME_ROUND'

export type FeedbackMode = 'IMMEDIATE' | 'DEFERRED' | 'RECORD'
export type SessionStatus = 'IN_PROGRESS' | 'SUBMITTED' | 'ABANDONED' | 'EXPIRED'
export type Skill =
  'VOCAB' | 'LISTENING' | 'READING' | 'CHARACTER' | 'WRITING' | 'GRAMMAR' | 'PRONUNCIATION'

export interface Option {
  key: string
  text: string
}

export interface ServedItemExample {
  cn?: string
  pinyin?: string | null
  vi?: string
}

export interface ServedItemPrompt extends Record<string, unknown> {
  text?: string | null
  pinyin?: string | null
  sinoViet?: string | null
  meaning?: string | null
  audioUrl?: string | null
  sentence?: string | null
  hint?: string | null
  examples?: ServedItemExample[]
}

export interface ServedItemRules extends Record<string, unknown> {
  minDurationMs?: number
  maxReplays?: number
  hasAudio?: boolean
}

export interface ServedItemView {
  no: number
  type: string
  skill: Skill
  lexemeId?: number | null
  prompt: ServedItemPrompt
  options?: Option[]
  rules: ServedItemRules
}

export interface ItemState {
  no: number
  answer?: string | null
  correct?: boolean | null
  correctAnswer?: string | null
  counted?: boolean
}

export interface SessionView {
  sessionId: number
  source: PracticeSource
  refId?: number | null
  feedback: FeedbackMode
  status: SessionStatus
  servedAt: string
  expiresAt: string
  items: ServedItemView[]
  answered: ItemState[]
  meta?: Record<string, unknown>
}

export interface AnswerRequest {
  no: number
  answer?: string | null
  durationMs: number
  replayCount?: number
  skipped?: boolean
  audioFailed?: boolean
  clientMeta?: Record<string, unknown>
}

export interface AnswerResult {
  no: number
  correct?: boolean | null
  correctAnswer?: string | null
  accept?: string[] | null
  explanation?: string | null
  counted?: boolean
  accepted?: boolean
  recorded?: boolean
  extra?: Record<string, unknown>
}

export interface FinishRequest {
  answers?: AnswerRequest[]
}

export interface FinishResultItem {
  no?: number
  correct?: boolean | null
  correctAnswer?: string | null
  yourAnswer?: string | null
}

export interface FinishResult {
  sessionId: number
  status: SessionStatus
  score?: number | null
  maxScore?: number | null
  percent?: number | null
  suspicious: boolean
  items: FinishResultItem[]
  extra?: Record<string, unknown>
}
