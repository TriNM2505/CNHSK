import { apiRequest } from '@/shared/api'

import type { AskRequest, AskResult } from '../types'

export function askAssistant(body: AskRequest, signal?: AbortSignal): Promise<AskResult> {
  return apiRequest<AskResult>('/api/assistant/ask', { body, method: 'POST', signal })
}
