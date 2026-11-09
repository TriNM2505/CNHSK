import { apiRequest } from '@/shared/api'

import { checkedIdentifiers } from './identifiers'

import type { TranslateRequest, TranslateResult } from '../types'

export async function translate(
  body: TranslateRequest,
  signal?: AbortSignal,
): Promise<TranslateResult> {
  return checkedIdentifiers(
    apiRequest<TranslateResult>('/api/translate', { body, method: 'POST', signal }),
  )
}
