import { beforeEach, describe, expect, it, vi } from 'vitest'

import type { SessionView } from '@/features/practice'
import { apiRequest } from '@/shared/api'

import { startRecognitionSession } from './recognition'

vi.mock('@/shared/api', () => ({ apiRequest: vi.fn() }))

const apiRequestMock = vi.mocked(apiRequest)

const session: SessionView = {
  answered: [],
  expiresAt: '2026-10-29T14:00:00Z',
  feedback: 'IMMEDIATE',
  items: [],
  servedAt: '2026-10-29T12:00:00Z',
  sessionId: 401,
  source: 'CHAR_RECOGNITION',
  status: 'IN_PROGRESS',
}

describe('startRecognitionSession', () => {
  beforeEach(() => apiRequestMock.mockReset())

  it('gửi chế độ, cấp và số câu rồi trả lượt mới', async () => {
    apiRequestMock.mockResolvedValue(session)

    await expect(
      startRecognitionSession({ count: 10, hskLevel: 2, mode: 'AUDIO_TO_CHAR' }),
    ).resolves.toEqual(session)
    expect(apiRequestMock).toHaveBeenCalledWith('/api/characters/recognition-sessions', {
      body: { count: 10, hskLevel: 2, mode: 'AUDIO_TO_CHAR' },
      method: 'POST',
    })
  })

  it('trả null khi máy chủ báo chưa có chữ phù hợp (FR-008)', async () => {
    apiRequestMock.mockResolvedValue({ reason: 'NO_CHARACTERS', session: null })

    await expect(
      startRecognitionSession({ hskLevel: 3, mode: 'CHAR_TO_MEANING' }),
    ).resolves.toBeNull()
  })
})
