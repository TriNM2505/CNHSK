import type { FinishResult, SessionView } from '@/features/practice'
import { apiRequest } from '@/shared/api'
import type { HskLevel } from '@/shared/ui'

import type { GrammarLesson, GrammarSummary, StartGrammarLessonResult } from '../types'

function grammarPath(id: number, suffix = '') {
  return `/api/grammar/${encodeURIComponent(String(id))}${suffix}`
}

export function getGrammarPoints(hskLevel?: HskLevel, signal?: AbortSignal) {
  const query = hskLevel ? `?hskLevel=${hskLevel}` : ''
  return apiRequest<GrammarSummary[]>(`/api/grammar${query}`, { signal })
}

export function getGrammarLesson(id: number, signal?: AbortSignal) {
  return apiRequest<GrammarLesson>(grammarPath(id), { signal })
}

export function startGrammarLesson(id: number, readDurationMs: number) {
  return apiRequest<StartGrammarLessonResult>(grammarPath(id, '/lessons'), {
    body: { readDurationMs },
    method: 'POST',
  })
}

export type { FinishResult, SessionView }
