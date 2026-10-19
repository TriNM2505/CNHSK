import type { FinishResult, SessionView } from '@/features/practice'
import type { HskLevel } from '@/shared/ui'

export interface GrammarSummary {
  id: number
  text: string
  titleVi: string
  hskLevel: HskLevel
  structure?: string | null
  exerciseCount: number
  learned: boolean
  bestPercent?: number | null
  lastStudiedAt?: string | null
}

export interface GrammarExample {
  cn: string
  pinyin?: string | null
  vi: string
}

export interface GrammarLesson extends GrammarSummary {
  explanation: string
  commonMistake?: string | null
  examples: GrammarExample[]
}

export interface CompletedGrammarLesson {
  session: SessionView
  finish: FinishResult
}

export type StartGrammarLessonResult = SessionView | CompletedGrammarLesson

export interface GrammarLessonExtra {
  exercisesTotal: number
  answered: number
  correct: number
  percent: number | null
  skippedExercises: boolean
  firstTimeLearned: boolean
}

export function isCompletedGrammarLesson(
  result: StartGrammarLessonResult,
): result is CompletedGrammarLesson {
  return 'session' in result && 'finish' in result
}
