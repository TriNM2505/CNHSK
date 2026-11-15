import { beforeEach, describe, expect, it, vi } from 'vitest'

import { apiRequest } from '@/shared/api'
import { listAdminTopics, readAdminTopicCatalog, setTopicPrerequisites } from './topics'

vi.mock('@/shared/api', () => ({ apiRequest: vi.fn() }))
const request = vi.mocked(apiRequest)
describe('topic prerequisite API', () => {
  beforeEach(() => request.mockReset())
  it('uses zero-based pages and the contract hsk/q names with cancellation', async () => {
    const signal = new AbortController().signal
    await listAdminTopics({ hsk: 2, q: ' tên %_! ', page: 3, size: 50 }, signal)
    const [url, options] = request.mock.calls[0]!
    expect(Object.fromEntries(new URL(url, 'http://localhost').searchParams)).toEqual({
      hsk: '2',
      q: 'tên %_!',
      page: '3',
      size: '50',
    })
    expect(options).toEqual({ signal })
  })
  it('replaces prerequisites in input order, including an empty list, through shared CSRF client', async () => {
    await setTopicPrerequisites(31, [12, 11])
    await setTopicPrerequisites(31, [])
    expect(request).toHaveBeenNthCalledWith(1, '/api/admin/topics/31/prerequisites', {
      method: 'PUT',
      body: { prereqIds: [12, 11] },
    })
    expect(request).toHaveBeenNthCalledWith(2, '/api/admin/topics/31/prerequisites', {
      method: 'PUT',
      body: { prereqIds: [] },
    })
  })
  it.each([0, -1, 1.5, Infinity, Number.MAX_SAFE_INTEGER + 1])(
    'rejects invalid target %s before sending',
    (id) => {
      expect(() => setTopicPrerequisites(id, [])).toThrow()
      expect(request).not.toHaveBeenCalled()
    },
  )
  it('reads all unfiltered pages to resolve names outside the visible page', async () => {
    request
      .mockResolvedValueOnce({ items: [{ id: 1 }], page: 0, size: 50, totalElements: 51 })
      .mockResolvedValueOnce({ items: [{ id: 51 }], page: 1, size: 50, totalElements: 51 })
    expect(await readAdminTopicCatalog()).toEqual([{ id: 1 }, { id: 51 }])
    expect(request).toHaveBeenNthCalledWith(2, '/api/admin/topics?page=1&size=50', {
      signal: undefined,
    })
  })
})
