import type { AnswerRequest, FinishResult, ServedItemView } from '@/features/practice'
import { type ApiError } from '@/shared/api'

import type { TopicTestExtra, TopicWrongWord } from './types'

export const PINYIN_DRAFT_DEBOUNCE_MS = 500

/** Nháp phía trình duyệt của một câu; luôn là bản mới nhất, kể cả khi chưa kịp lưu lên server. */
export type TestDraft = {
  answer?: string
  audioFailed?: boolean
  replayCount: number
}

export function isAnswered(draft: TestDraft | undefined) {
  return Boolean(draft && (draft.audioFailed || (draft.answer && draft.answer.trim())))
}

export function countUnanswered(
  items: readonly ServedItemView[],
  drafts: Readonly<Record<number, TestDraft>>,
) {
  return items.filter((item) => !isAnswered(drafts[item.no])).length
}

export function toAnswerRequest(no: number, draft: TestDraft, durationMs: number): AnswerRequest {
  return {
    durationMs,
    no,
    replayCount: draft.replayCount,
    ...(draft.audioFailed ? { audioFailed: true } : { answer: draft.answer?.trim() ?? null }),
  }
}

/** E-F1: nộp kèm mọi nháp mới nhất để câu đang chờ debounce không bị mất. */
export function draftsToAnswers(
  items: readonly ServedItemView[],
  drafts: Readonly<Record<number, TestDraft>>,
  durations: Readonly<Record<number, number>>,
): AnswerRequest[] {
  return items
    .filter((item) => isAnswered(drafts[item.no]))
    .map((item) => toAnswerRequest(item.no, drafts[item.no] as TestDraft, durations[item.no] ?? 0))
}

function isWrongWord(value: unknown): value is TopicWrongWord {
  if (!value || typeof value !== 'object') return false
  const word = value as Record<string, unknown>
  return (
    typeof word.lexemeId === 'number' &&
    typeof word.text === 'string' &&
    typeof word.meaning === 'string'
  )
}

export function readTestExtra(result: FinishResult | undefined): TopicTestExtra {
  const extra = result?.extra ?? {}
  return {
    bestPercent: typeof extra.bestPercent === 'number' ? extra.bestPercent : undefined,
    firstCompletion: extra.firstCompletion === true,
    passed: typeof extra.passed === 'boolean' ? extra.passed : undefined,
    topicCompleted: extra.topicCompleted === true,
    wrongWords: Array.isArray(extra.wrongWords) ? extra.wrongWords.filter(isWrongWord) : [],
  }
}

export type TestStartFailure =
  | { kind: 'not-enough'; learnedWords?: number; requiredWords?: number }
  | { kind: 'retry-later'; retryAfterSeconds: number }
  | { kind: 'other'; message: string; requestId?: string }

function numberExtra(error: ApiError, key: string) {
  const value = error.extra[key]
  return typeof value === 'number' && Number.isFinite(value) ? value : undefined
}

export function getTestStartFailure(error: ApiError): TestStartFailure {
  if (error.status === 422 && error.errorCode === 'NOT_ENOUGH_LEARNED') {
    return {
      kind: 'not-enough',
      learnedWords: numberExtra(error, 'learnedWords'),
      requiredWords: numberExtra(error, 'requiredWords'),
    }
  }
  if (error.status === 429 && error.errorCode === 'RETRY_TOO_SOON') {
    return {
      kind: 'retry-later',
      retryAfterSeconds: numberExtra(error, 'retryAfterSeconds') ?? 600,
    }
  }
  return { kind: 'other', message: error.message, requestId: error.requestId }
}

export function formatWaitTime(seconds: number) {
  const safe = Math.max(0, Math.ceil(seconds))
  const minutes = Math.floor(safe / 60)
  const rest = safe % 60
  return `${String(minutes).padStart(2, '0')}:${String(rest).padStart(2, '0')}`
}
