import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, describe, expect, it, vi } from 'vitest'

import { AuthContext, type AuthContextValue } from '@/shared/auth/AuthContext'

import Scr046TopicLeaderboardPage from './Scr046TopicLeaderboardPage'

const auth: AuthContextValue = {
  error: null,
  reloadCurrentUser: vi.fn(),
  setAuthenticatedUser: vi.fn(),
  signOut: vi.fn(),
  status: 'guest',
  user: null,
}
const board = {
  type: 'TOPIC',
  ref: '43',
  period: 'week',
  periodStart: null,
  periodEnd: null,
  entries: [
    { rank: 1, userId: 7, displayName: 'Lan', avatarUrl: null, score: 90, durationMs: 60000 },
  ],
  myEntry: null,
}

function mockFetch(topicStatus = 200) {
  const fetchMock = vi.fn(async (input: RequestInfo | URL) => {
    const url = String(input)
    if (url.includes('/topics?'))
      return new Response(JSON.stringify([{ id: 43, name: 'Chào hỏi', hskLevel: 1 }]), {
        status: 200,
      })
    if (url.includes('/topics/'))
      return new Response(
        JSON.stringify(
          topicStatus === 200
            ? board
            : { error_code: 'TOPIC_NOT_FOUND', message: 'missing', request_id: 'r1' },
        ),
        { status: topicStatus },
      )
    throw new Error(`Unexpected request ${url}`)
  })
  vi.stubGlobal('fetch', fetchMock)
  return fetchMock
}

afterEach(() => vi.unstubAllGlobals())

describe('SCR-046 topic leaderboard', () => {
  it('uses the first API topic when absent and keeps period/HSK selection in the URL', async () => {
    const fetchMock = mockFetch()
    render(
      <AuthContext.Provider value={auth}>
        <MemoryRouter initialEntries={['/leaderboards/topics?hsk=1&period=month']}>
          <Scr046TopicLeaderboardPage />
        </MemoryRouter>
      </AuthContext.Provider>,
    )
    expect(await screen.findAllByText('Lan')).toHaveLength(2)
    await waitFor(() =>
      expect(fetchMock).toHaveBeenCalledWith(
        expect.stringContaining('/topics/43?period=month'),
        expect.any(Object),
      ),
    )
    expect(screen.getByLabelText('Chủ đề')).toHaveValue('43')
  })

  it('keeps an explicit HSK 5 topic selection and shows the approved 404 recovery', async () => {
    mockFetch(404)
    render(
      <AuthContext.Provider value={auth}>
        <MemoryRouter initialEntries={['/leaderboards/topics?topicId=902&hsk=5']}>
          <Scr046TopicLeaderboardPage />
        </MemoryRouter>
      </AuthContext.Provider>,
    )
    expect(
      await screen.findByText('Chủ đề không tồn tại. Hãy chọn chủ đề khác.'),
    ).toBeInTheDocument()
    expect(screen.getByLabelText('Cấp độ HSK')).toHaveValue('5')
    expect(screen.getByLabelText('Chủ đề')).toHaveValue('902')
    fireEvent.change(screen.getByLabelText('Cấp độ HSK'), { target: { value: '2' } })
  })
})
