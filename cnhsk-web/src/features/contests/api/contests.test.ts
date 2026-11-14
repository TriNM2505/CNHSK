import { beforeEach, describe, expect, it, vi } from 'vitest'
import { apiRequest } from '@/shared/api'
import { answerItem, getSession } from '@/features/practice/api/sessions'
import {
  enterContest,
  getContest,
  getContestDraft,
  getContestResults,
  listContests,
  reportContestTabHidden,
  saveContestAnswer,
  submitContest,
} from './contests'

vi.mock('@/shared/api', () => ({ apiRequest: vi.fn() }))
vi.mock('@/features/practice/api/sessions', () => ({ answerItem: vi.fn(), getSession: vi.fn() }))

describe('contest contract adapters', () => {
  beforeEach(() => vi.resetAllMocks())
  it('FR015_reads public pages with the specified filters and cancellation signal', async () => {
    const signal = new AbortController().signal
    await listContests()
    await listContests({ state: 'ONGOING', page: 1, size: 20 }, signal)
    await getContest(2101, signal)
    expect(apiRequest).toHaveBeenNthCalledWith(1, '/api/public/community/contests', {
      signal: undefined,
    })
    expect(apiRequest).toHaveBeenNthCalledWith(
      2,
      '/api/public/community/contests?state=ONGOING&page=1&size=20',
      { signal },
    )
    expect(apiRequest).toHaveBeenNthCalledWith(3, '/api/public/community/contests/2101', { signal })
  })
  it('FR016_uses contest submission, records raw events and adds no unapproved ranking parameters', async () => {
    await enterContest(2101)
    await submitContest(2101)
    await reportContestTabHidden(2101)
    await getContestResults(2101)
    expect(apiRequest).toHaveBeenNthCalledWith(1, '/api/community/contests/2101/attempts', {
      method: 'POST',
    })
    expect(apiRequest).toHaveBeenNthCalledWith(2, '/api/community/contests/2101/attempts/submit', {
      method: 'POST',
    })
    expect(apiRequest).toHaveBeenNthCalledWith(3, '/api/community/contests/2101/attempts/events', {
      method: 'POST',
      body: { type: 'TAB_HIDDEN' },
    })
    expect(apiRequest).toHaveBeenNthCalledWith(4, '/api/community/contests/2101/results', {
      signal: undefined,
    })
  })
  it('FR016_restores only draft fields and sends raw answers to the practice session id', async () => {
    vi.mocked(getSession).mockResolvedValue({
      sessionId: 2151,
      status: 'IN_PROGRESS',
      source: 'MOCK_EXAM',
      feedback: 'DEFERRED',
      servedAt: '',
      expiresAt: '',
      items: [],
      answered: [{ no: 1, answer: 'A' }],
    })
    expect(await getContestDraft(2151)).toEqual({
      sessionId: 2151,
      status: 'IN_PROGRESS',
      answered: [{ no: 1, answer: 'A' }],
    })
    const answer = { no: 1, answer: 'A', durationMs: 2000 }
    await saveContestAnswer(2151, answer)
    expect(answerItem).toHaveBeenCalledWith(2151, answer)
    expect(apiRequest).not.toHaveBeenCalled()
  })
})
