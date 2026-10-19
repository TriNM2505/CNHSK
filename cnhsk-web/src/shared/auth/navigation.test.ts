import { afterEach, describe, expect, it, vi } from 'vitest'

import type { CurrentUser } from '@/features/auth/types'

import { getAuthenticatedDestination } from './navigation'

const learner: CurrentUser = {
  id: 1,
  email: 'learner@example.com',
  displayName: 'Người học',
  emailVerified: true,
  roles: ['LEARNER'],
}

afterEach(() => {
  vi.unstubAllEnvs()
})

describe('getAuthenticatedDestination', () => {
  it('uses the only available role area as the default destination', () => {
    expect(getAuthenticatedDestination({ ...learner, roles: ['ADMIN'] })).toEqual({
      type: 'internal',
      to: '/admin/users',
    })
  })

  it('uses the landing page when a user has several roles', () => {
    expect(getAuthenticatedDestination({ ...learner, roles: ['LEARNER', 'ADMIN'] })).toEqual({
      type: 'internal',
      to: '/',
    })
  })

  it('accepts an internal redirect only when the user has access', () => {
    expect(getAuthenticatedDestination(learner, '/learn/topics/topic-1/study?step=2')).toEqual({
      type: 'internal',
      to: '/learn/topics/topic-1/study?step=2',
    })
    expect(getAuthenticatedDestination(learner, '/admin/users')).toEqual({
      type: 'internal',
      to: '/me/profile',
    })
  })

  it('rejects protocol-relative and unapproved absolute redirects', () => {
    vi.stubEnv('VITE_GAME_ORIGIN', 'http://localhost:5174')

    expect(getAuthenticatedDestination(learner, '//evil.example/path')).toEqual({
      type: 'internal',
      to: '/me/profile',
    })
    expect(getAuthenticatedDestination(learner, 'https://evil.example/path')).toEqual({
      type: 'internal',
      to: '/me/profile',
    })
  })

  it('allows an absolute redirect only for the configured game origin', () => {
    vi.stubEnv('VITE_GAME_ORIGIN', 'http://localhost:5174')

    expect(getAuthenticatedDestination(learner, 'http://localhost:5174/play/vocab')).toEqual({
      type: 'external',
      href: 'http://localhost:5174/play/vocab',
    })
  })
})
