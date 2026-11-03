import { apiRequest } from '@/shared/api'

import type { TranslateRequest, TranslateResult } from '../types'

export function translate(body: TranslateRequest): Promise<TranslateResult> {
  return apiRequest<TranslateResult>('/api/translate', { body, method: 'POST' })
}
