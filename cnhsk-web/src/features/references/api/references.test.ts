import { beforeEach, describe, expect, it, vi } from 'vitest'

import { apiRequest } from '@/shared/api'

import { bookReference, mediaPage } from '../test-fixtures'
import { getReference, listReferences, reportReference } from './references'

vi.mock('@/shared/api', () => ({ apiRequest: vi.fn() }))
const request = vi.mocked(apiRequest)

describe('references API contract', () => {
  beforeEach(() => {
    request.mockReset()
  })
  it('sends the required kind and zero-based defaults without absent filters', async () => {
    request.mockResolvedValue(mediaPage)
    expect(await listReferences({ kind: 'YOUTUBE_CHANNEL' })).toEqual(mediaPage)
    const url = new URL(String(request.mock.calls[0]![0]), 'http://localhost')
    expect(url.pathname).toBe('/api/public/references')
    expect(Object.fromEntries(url.searchParams)).toEqual({
      kind: 'YOUTUBE_CHANNEL',
      page: '0',
      size: '20',
    })
  })
  it('encodes filters without trimming tag and passes cancellation', async () => {
    const controller = new AbortController()
    await listReferences(
      { kind: 'PODCAST', hsk: 3, tag: ' nghe & đọc ', page: 2, size: 50 },
      controller.signal,
    )
    const url = new URL(String(request.mock.calls[0]![0]), 'http://localhost')
    expect(Object.fromEntries(url.searchParams)).toEqual({
      kind: 'PODCAST',
      hsk: '3',
      tag: ' nghe & đọc ',
      page: '2',
      size: '50',
    })
    expect(request.mock.calls[0]![1]).toEqual({ signal: controller.signal })
  })
  it('gets detail and sends a report expecting no response body', async () => {
    request.mockResolvedValueOnce(bookReference).mockResolvedValueOnce(undefined)
    const signal = new AbortController().signal
    expect(await getReference(503, signal)).toEqual(bookReference)
    expect(request).toHaveBeenNthCalledWith(1, '/api/public/references/503', { signal })
    const body = { reason: 'OTHER' as const, note: 'Ghi chú tự soạn' }
    expect(await reportReference(503, body)).toBeUndefined()
    expect(request).toHaveBeenNthCalledWith(2, '/api/references/503/report', {
      method: 'POST',
      body,
    })
  })
  it.each([0, -1, 1.5, NaN, Infinity, Number.MAX_SAFE_INTEGER + 1])(
    'does not send unsafe ID %s',
    (id) => {
      expect(() => getReference(id)).toThrow()
      expect(() => reportReference(id, { reason: 'DEAD_LINK' })).toThrow()
      expect(request).not.toHaveBeenCalled()
    },
  )
  it('propagates API errors instead of replacing them with empty data', async () => {
    const error = new Error('offline')
    request.mockRejectedValue(error)
    await expect(listReferences({ kind: 'BOOK' })).rejects.toBe(error)
  })
})
