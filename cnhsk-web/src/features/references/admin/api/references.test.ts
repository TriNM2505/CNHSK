import { beforeEach, describe, expect, it, vi } from 'vitest'

import { apiRequest } from '@/shared/api'
import { draftOf, inputOf } from '../referenceForm'
import { draftReference } from '../test-fixtures'
import {
  createReference,
  getAdminReference,
  listAdminReferences,
  transitionReference,
  updateReference,
} from './references'

vi.mock('@/shared/api', () => ({ apiRequest: vi.fn() }))
const request = vi.mocked(apiRequest)

describe('reference admin API', () => {
  beforeEach(() => {
    request.mockReset()
  })
  it('requests unfiltered zero-based list and passes cancellation', async () => {
    const signal = new AbortController().signal
    await listAdminReferences({}, signal)
    expect(request).toHaveBeenCalledWith('/api/admin/references?page=0&size=20', { signal })
  })
  it('serializes all filters including explicit reported=false', async () => {
    await listAdminReferences({
      kind: 'BOOK',
      status: 'HIDDEN',
      reported: false,
      page: 2,
      size: 50,
    })
    expect(
      Object.fromEntries(
        new URL(String(request.mock.calls[0]![0]), 'http://localhost').searchParams,
      ),
    ).toEqual({ kind: 'BOOK', status: 'HIDDEN', reported: 'false', page: '2', size: '50' })
  })
  it('uses GET detail, POST create and PUT replacement through shared apiRequest', async () => {
    const body = inputOf(draftOf(draftReference))
    const signal = new AbortController().signal
    await getAdminReference(501, signal)
    await createReference(body)
    await updateReference(501, body)
    expect(request).toHaveBeenNthCalledWith(1, '/api/admin/references/501', { signal })
    expect(request).toHaveBeenNthCalledWith(2, '/api/admin/references', { method: 'POST', body })
    expect(request).toHaveBeenNthCalledWith(3, '/api/admin/references/501', { method: 'PUT', body })
    expect(body).not.toHaveProperty('status')
    expect(body).not.toHaveProperty('updatedAt')
  })
  it.each(['publish', 'hide', 'unhide'] as const)(
    'POSTs %s without client state fields',
    async (action) => {
      request.mockResolvedValue(draftReference)
      expect(await transitionReference(501, action)).toEqual(draftReference)
      expect(request).toHaveBeenCalledWith(`/api/admin/references/501/${action}`, {
        method: 'POST',
      })
    },
  )
  it.each([0, -1, 1.1, Infinity, Number.MAX_SAFE_INTEGER + 1])(
    'rejects unsafe ID %s before a request',
    (id) => {
      expect(() => getAdminReference(id)).toThrow()
      expect(() => updateReference(id, inputOf(draftOf(draftReference)))).toThrow()
      expect(() => transitionReference(id, 'hide')).toThrow()
      expect(request).not.toHaveBeenCalled()
    },
  )
  it('does not convert server errors into a successful empty result', async () => {
    const error = new Error('offline')
    request.mockRejectedValue(error)
    await expect(listAdminReferences()).rejects.toBe(error)
  })
})
