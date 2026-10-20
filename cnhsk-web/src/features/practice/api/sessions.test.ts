import { beforeEach, describe, expect, it, vi } from 'vitest'

import {
  abandonSession,
  answerItem,
  findActiveSession,
  finishSession,
  getSession,
} from '@/features/practice/api/sessions'
import type { AnswerRequest, SessionView } from '@/features/practice/types'
import { apiRequest } from '@/shared/api'

vi.mock('@/shared/api', () => ({ apiRequest: vi.fn() }))

const apiRequestMock = vi.mocked(apiRequest)
const session = { sessionId: 12 } as SessionView

beforeEach(() => {
  apiRequestMock.mockReset()
})

describe('practice session API', () => {
  it('gets a session and finds an active session with encoded query parameters', async () => {
    apiRequestMock.mockResolvedValueOnce(session).mockResolvedValueOnce(session)

    await expect(getSession(12)).resolves.toBe(session)
    await expect(findActiveSession('TOPIC_PRACTICE', 34)).resolves.toBe(session)

    expect(apiRequestMock).toHaveBeenNthCalledWith(1, '/api/practice/sessions/12', {
      signal: undefined,
    })
    expect(apiRequestMock).toHaveBeenNthCalledWith(
      2,
      '/api/practice/sessions/active?source=TOPIC_PRACTICE&refId=34',
      { signal: undefined },
    )
  })

  it('maps an empty active-session response to null', async () => {
    apiRequestMock.mockResolvedValueOnce(undefined)

    await expect(findActiveSession('CHAR_RECOGNITION')).resolves.toBeNull()
  })

  it('sends answer, finish and abandon requests to their contract endpoints', async () => {
    const answer: AnswerRequest = { answer: 'B', durationMs: 900, no: 2, replayCount: 3 }
    apiRequestMock.mockResolvedValue(undefined)

    await answerItem(12, answer)
    await finishSession(12, { answers: [answer] })
    await abandonSession(12)

    expect(apiRequestMock).toHaveBeenNthCalledWith(1, '/api/practice/sessions/12/answers', {
      body: answer,
      method: 'POST',
    })
    expect(apiRequestMock).toHaveBeenNthCalledWith(2, '/api/practice/sessions/12/finish', {
      body: { answers: [answer] },
      method: 'POST',
    })
    expect(apiRequestMock).toHaveBeenNthCalledWith(3, '/api/practice/sessions/12/abandon', {
      method: 'POST',
    })
  })
})
