import { act, renderHook, waitFor } from '@testing-library/react'
import { afterEach, expect, it, vi } from 'vitest'

import { GAME_SESSION_EXPIRED_EVENT } from '../api/client'
import { gameLoginHref, useCurrentUser } from './useCurrentUser'

afterEach(() => {
  vi.unstubAllGlobals()
  vi.unstubAllEnvs()
})

it('exposes loading and resolves the current user from /api/auth/me', async () => {
  const user = { id: 7, displayName: 'Fixture', roles: ['LEARNER'] }
  const fetchMock = vi.fn().mockResolvedValue(Response.json(user))
  vi.stubGlobal('fetch', fetchMock)
  const { result } = renderHook(useCurrentUser)
  expect(result.current.status).toBe('loading')
  await waitFor(() => expect(result.current).toEqual({ status: 'authenticated', user }))
  expect(fetchMock).toHaveBeenCalledWith(
    '/api/auth/me',
    expect.objectContaining({ credentials: 'include' }),
  )

  act(() => {
    window.dispatchEvent(new Event(GAME_SESSION_EXPIRED_EVENT))
  })
  expect(result.current).toEqual({ status: 'guest' })
})

it('goes guest after one unsuccessful refresh', async () => {
  const fetchMock = vi
    .fn()
    .mockResolvedValueOnce(Response.json({ error_code: 'TOKEN_EXPIRED' }, { status: 401 }))
    .mockResolvedValueOnce(Response.json({ error_code: 'INVALID_TOKEN' }, { status: 401 }))
  vi.stubGlobal('fetch', fetchMock)
  const { result } = renderHook(useCurrentUser)
  await waitFor(() => expect(result.current.status).toBe('guest'))
  expect(fetchMock.mock.calls.map(([path]) => path)).toEqual(['/api/auth/me', '/api/auth/refresh'])
})

it('shows an error for network failure and aborts use on unmount', async () => {
  const fetchMock = vi.fn().mockRejectedValue(new TypeError('Offline'))
  vi.stubGlobal('fetch', fetchMock)
  const { result, unmount } = renderHook(useCurrentUser)
  await waitFor(() => expect(result.current.status).toBe('error'))
  const signal: AbortSignal | undefined = fetchMock.mock.calls[0]?.[1].signal
  unmount()
  expect(signal?.aborted).toBe(true)
})

it.each([
  'https://unapproved.example.test/x',
  '//unapproved.example.test/x',
  'javascript:alert(1)',
])('rejects an unsafe return URL %s', (returnTo) => {
  vi.stubEnv('VITE_WEB_ORIGIN', 'https://web.example.test')
  const login = new URL(gameLoginHref(returnTo))
  expect(login.origin).toBe('https://web.example.test')
  expect(login.pathname).toBe('/login')
  expect(login.searchParams.get('redirect')).toBe(window.location.origin + '/')
})
