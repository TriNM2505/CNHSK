import type { SessionView } from '@/features/practice'
import { apiRequest } from '@/shared/api'

import type {
  CharacterDetail,
  CharacterListQuery,
  CharacterPage,
  WritingAttemptRequest,
  WritingAttemptResult,
} from '../types'

/** FR-001: `page` gửi lên API đếm từ 0. */
export function getCharacters(query: CharacterListQuery, signal?: AbortSignal) {
  const params = new URLSearchParams({ page: String(query.page), size: String(query.size) })
  if (query.hskLevel) params.set('hskLevel', String(query.hskLevel))
  if (query.q) params.set('q', query.q)
  return apiRequest<CharacterPage>(`/api/characters?${params.toString()}`, { signal })
}

export function getCharacter(characterId: number, signal?: AbortSignal) {
  return apiRequest<CharacterDetail>(
    `/api/characters/${encodeURIComponent(String(characterId))}`,
    { signal },
  )
}

export function recordWritingAttempt(request: WritingAttemptRequest) {
  return apiRequest<WritingAttemptResult>('/api/writing/attempts', {
    body: request,
    method: 'POST',
  })
}

/** FR-020…FR-023: bắt đầu thử thách, hoặc nhận lại lượt đang làm. */
export function startWritingChallenge() {
  return apiRequest<SessionView>('/api/writing/challenges', { method: 'POST' })
}
