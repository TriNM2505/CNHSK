import type { SessionView } from '@/features/practice'
import { apiRequest } from '@/shared/api'

import type { StartRecognitionRequest, StartRecognitionResponse } from '../types'

function isSession(response: StartRecognitionResponse): response is SessionView {
  return 'sessionId' in response
}

/** FR-004…FR-008: tạo lượt nhận diện; `null` khi cấp đã chọn chưa có chữ phù hợp. */
export async function startRecognitionSession(
  request: StartRecognitionRequest,
): Promise<SessionView | null> {
  const response = await apiRequest<StartRecognitionResponse>(
    '/api/characters/recognition-sessions',
    { body: request, method: 'POST' },
  )
  return isSession(response) ? response : null
}
