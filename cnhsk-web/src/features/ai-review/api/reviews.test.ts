import { beforeEach, describe, expect, it, vi } from 'vitest'

import { apiRequest } from '@/shared/api'

import { approveReview, bulkApproveReviews, listReviews, rejectReview } from './reviews'

vi.mock('@/shared/api', () => ({ apiRequest: vi.fn() }))
beforeEach(() => vi.mocked(apiRequest).mockReset().mockResolvedValue({}))

describe('review wire adapters', () => {
  it('uses queue defaults and forwards AbortSignal', async () => {
    const signal = new AbortController().signal
    await listReviews({}, signal)
    expect(apiRequest).toHaveBeenCalledWith('/api/admin/ai-questions?page=0&size=20', { signal })
  })
  it('encodes exact filter names, UTC bounds and caps size at fifty', async () => {
    await listReviews({
      kid: '词-1',
      hsk: 3,
      from: '2026-11-16T17:00:00.000Z',
      to: '2026-11-17T16:59:59.999Z',
      page: 2,
      size: 100,
    })
    const url = new URL(vi.mocked(apiRequest).mock.calls[0]![0], 'https://local.test')
    expect(Object.fromEntries(url.searchParams)).toEqual({
      kid: '词-1',
      hsk: '3',
      from: '2026-11-16T17:00:00.000Z',
      to: '2026-11-17T16:59:59.999Z',
      page: '2',
      size: '50',
    })
    expect(url.searchParams.has('hskLevel')).toBe(false)
  })
  it('omits empty filters and preserves nullable run ids and label shapes', async () => {
    const data = {
      items: [{ runId: null, labels: [{ kid: 'TV1-901', role: 'PRIMARY' }] }],
      page: 0,
      size: 20,
      totalElements: 1,
    }
    vi.mocked(apiRequest).mockResolvedValue(data)
    expect(await listReviews({ kid: '', from: '', to: '' })).toBe(data)
    expect(apiRequest).toHaveBeenCalledWith('/api/admin/ai-questions?page=0&size=20', {
      signal: undefined,
    })
  })
  it('posts exact independent decision and bulk payloads', async () => {
    const signal = new AbortController().signal
    await approveReview(901, signal)
    await rejectReview(902, 'Cần sửa lời giải.', signal)
    await bulkApproveReviews([901, 902], signal)
    expect(vi.mocked(apiRequest).mock.calls).toEqual([
      ['/api/admin/ai-questions/901/approve', { method: 'POST', signal }],
      [
        '/api/admin/ai-questions/902/reject',
        { method: 'POST', body: { reason: 'Cần sửa lời giải.' }, signal },
      ],
      [
        '/api/admin/ai-questions/bulk-approve',
        { method: 'POST', body: { ids: [901, 902] }, signal },
      ],
    ])
  })
})
