import type { SessionView } from '@/features/practice'
import { apiRequest } from '@/shared/api'

import type { PronunciationStageSummary } from '../types'

export function getPronunciationStages(signal?: AbortSignal) {
  return apiRequest<PronunciationStageSummary[]>('/api/pronunciation/stages', { signal })
}

export function startPronunciationStageSession(stageIndex: number) {
  return apiRequest<SessionView>(
    `/api/pronunciation/stages/${encodeURIComponent(String(stageIndex))}/sessions`,
    { method: 'POST' },
  )
}
