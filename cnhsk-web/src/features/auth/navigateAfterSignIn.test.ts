import { beforeEach, describe, expect, it, vi } from 'vitest'

import { getAuthenticatedDestination } from '@/shared/auth/navigation'

import { navigateAfterSignIn } from './navigateAfterSignIn'
import type { CurrentUser } from './types'

vi.mock('@/shared/auth/navigation', () => ({ getAuthenticatedDestination: vi.fn() }))

const user: CurrentUser = {
  displayName: 'Người học',
  email: 'learner@example.com',
  emailVerified: true,
  id: 1,
  roles: ['LEARNER'],
}

describe('navigateAfterSignIn', () => {
  beforeEach(() => {
    vi.mocked(getAuthenticatedDestination).mockReset()
  })

  it('cập nhật AuthProvider trước khi điều hướng nội bộ bằng replace', () => {
    const calls: string[] = []
    const setAuthenticatedUser = vi.fn(() => calls.push('set-user'))
    const navigate = vi.fn(() => calls.push('navigate'))
    vi.mocked(getAuthenticatedDestination).mockReturnValue({
      to: '/learn/topics',
      type: 'internal',
    })

    navigateAfterSignIn({ navigate, redirect: '/learn/topics', setAuthenticatedUser, user })

    expect(calls).toEqual(['set-user', 'navigate'])
    expect(getAuthenticatedDestination).toHaveBeenCalledWith(user, '/learn/topics')
    expect(navigate).toHaveBeenCalledWith('/learn/topics', { replace: true })
  })

  it('dùng location.assign cho đích game do helper FE-10 trả về', () => {
    const setAuthenticatedUser = vi.fn()
    const assignExternal = vi.fn()
    vi.mocked(getAuthenticatedDestination).mockReturnValue({
      href: 'http://localhost:5174/play/vocab',
      type: 'external',
    })

    navigateAfterSignIn({
      assignExternal,
      navigate: vi.fn(),
      redirect: 'http://localhost:5174/play/vocab',
      setAuthenticatedUser,
      user,
    })

    expect(setAuthenticatedUser).toHaveBeenCalledWith(user)
    expect(assignExternal).toHaveBeenCalledWith('http://localhost:5174/play/vocab')
  })
})
