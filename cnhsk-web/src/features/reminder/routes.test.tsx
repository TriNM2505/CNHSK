import { act, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { createMemoryRouter, MemoryRouter, RouterProvider } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { appRouteDefinitions, canUserAccessRoute, findKnownRoute } from '@/app/routeAccess'
import { appRoutes } from '@/app/routes'
import { screenRegistry } from '@/app/screenRegistry'
import { getCurrentUser } from '@/features/auth/api/getCurrentUser'
import type { RoleCode } from '@/features/auth/types'
import { ApiError } from '@/shared/api'
import { AuthContext } from '@/shared/auth'
import { AccountMenu } from '@/shared/layout/AccountMenu'

import { getReminderSettings, getTodayReminder, unsubscribeReminderEmail } from './api/reminder'
import { defaultReminderSettings, reminderAuth, todayReminderFixture } from './test-fixtures'

vi.mock('@/features/auth/api/getCurrentUser', () => ({ getCurrentUser: vi.fn() }))
vi.mock('./api/reminder', () => ({
  getReminderSettings: vi.fn(),
  updateReminderSettings: vi.fn(),
  getTodayReminder: vi.fn(),
  unsubscribeReminderEmail: vi.fn(),
}))

vi.mock('@/features/account/api/account', () => ({
  getProfile: vi.fn(async () => ({
    displayName: 'Người học thử',
    email: 'reminder@example.test',
    emailVerified: false,
  })),
  updateProfile: vi.fn(),
}))
vi.mock('@/features/mastery/api/mastery', () => ({ getMyRecommendations: vi.fn(async () => []) }))
vi.mock('@/features/mastery/api/reviews', () => ({
  getReviewSummary: vi.fn(async () => ({ dueCount: 1, nextDueAt: null })),
}))

function renderRoute(path: string) {
  const router = createMemoryRouter(appRoutes, { initialEntries: [path] })
  render(<RouterProvider router={router} />)
  return router
}

beforeEach(() => {
  vi.mocked(getCurrentUser)
    .mockReset()
    .mockResolvedValue({ ...reminderAuth.user! })
  vi.mocked(getReminderSettings).mockReset().mockResolvedValue(defaultReminderSettings)
  vi.mocked(getTodayReminder).mockReset().mockResolvedValue(todayReminderFixture)
  vi.mocked(unsubscribeReminderEmail).mockReset().mockResolvedValue(undefined)
})

describe('reminder route integration', () => {
  it('registers SCR-009 once as LEARNER in the learner layout', () => {
    expect(appRouteDefinitions.filter(({ path }) => path === '/me/reminders')).toEqual([
      {
        access: ['LEARNER'],
        kind: 'screen',
        layout: 'learner',
        path: '/me/reminders',
        scr: 'SCR-009',
      },
    ])
    expect(screenRegistry['SCR-009']).toBeDefined()
    expect(findKnownRoute('/me/reminders')?.access).toEqual(['LEARNER'])
  })

  it.each(['ADMIN', 'ACCOUNTANT', 'TEACHER', 'CONTENT_EDITOR', 'MODERATOR'] as RoleCode[])(
    'denies %s access to settings without reading private data',
    async (role) => {
      vi.mocked(getCurrentUser).mockResolvedValue({ ...reminderAuth.user!, roles: [role] })
      const route = findKnownRoute('/me/reminders')!
      expect(canUserAccessRoute({ ...reminderAuth.user!, roles: [role] }, route.access)).toBe(false)
      const router = renderRoute('/me/reminders')
      await waitFor(() => expect(router.state.location.pathname).toBe('/403'))
      expect(getReminderSettings).not.toHaveBeenCalled()
      router.dispose()
    },
  )

  it('lets an unverified LEARNER reach SCR-009', async () => {
    const router = renderRoute('/me/reminders')
    expect(await screen.findByRole('heading', { name: 'Cài đặt nhắc học' })).toBeVisible()
    expect(await screen.findByRole('switch', { name: 'Email' })).toBeDisabled()
    router.dispose()
  })

  it('sends guests to login while preserving the settings destination', async () => {
    vi.mocked(getCurrentUser).mockRejectedValue(
      new ApiError({
        status: 401,
        errorCode: 'UNAUTHENTICATED',
        message: 'Bạn chưa đăng nhập.',
      }),
    )
    const router = renderRoute('/me/reminders')
    expect(await screen.findByRole('heading', { name: 'Đăng nhập' })).toBeVisible()
    expect(router.state.location.pathname).toBe('/login')
    expect(new URLSearchParams(router.state.location.search).get('redirect')).toBe('/me/reminders')
    expect(getReminderSettings).not.toHaveBeenCalled()
    router.dispose()
  })

  it('declares the public utility route without adding a SCR identity', () => {
    const route = findKnownRoute('/reminder/unsubscribe')
    expect(route).toEqual({
      access: 'public',
      kind: 'utility',
      layout: 'public',
      path: '/reminder/unsubscribe',
    })
    expect(route).not.toHaveProperty('scr')
  })

  it.each([false, true])(
    'public unsubscribe works with authenticated=%s and never posts on entry',
    async (authenticated) => {
      if (authenticated) {
        vi.mocked(getCurrentUser).mockResolvedValue({ ...reminderAuth.user!, roles: ['ADMIN'] })
      } else {
        vi.mocked(getCurrentUser).mockRejectedValue(
          new ApiError({
            status: 401,
            errorCode: 'UNAUTHENTICATED',
            message: 'Bạn chưa đăng nhập.',
          }),
        )
      }
      const router = renderRoute('/reminder/unsubscribe?token=fixture-route-reminder-token')
      await screen.findByRole('button', { name: 'Tắt nhắc qua email' })
      await act(async () => undefined)
      expect(router.state.location.pathname).toBe('/reminder/unsubscribe')
      expect(unsubscribeReminderEmail).not.toHaveBeenCalled()
      fireEvent.click(screen.getByRole('button', { name: 'Tắt nhắc qua email' }))
      expect(
        await screen.findByText('Đã tắt. Bạn có thể bật lại trong Cài đặt nhắc học.'),
      ).toBeVisible()
      expect(unsubscribeReminderEmail).toHaveBeenCalledOnce()
      expect(unsubscribeReminderEmail).toHaveBeenCalledWith('fixture-route-reminder-token')
      router.dispose()
    },
  )

  it.each([
    'LEARNER',
    'ADMIN',
    'ACCOUNTANT',
    'TEACHER',
    'CONTENT_EDITOR',
    'MODERATOR',
  ] as RoleCode[])('account menu exposes Nhắc lịch học only to LEARNER, role=%s', (role) => {
    render(
      <AuthContext.Provider
        value={{ ...reminderAuth, user: { ...reminderAuth.user!, roles: [role] } }}
      >
        <MemoryRouter>
          <AccountMenu />
        </MemoryRouter>
      </AuthContext.Provider>,
    )
    const link = screen.queryByRole('link', { name: 'Nhắc lịch học', hidden: true })
    if (role === 'LEARNER') expect(link).toHaveAttribute('href', '/me/reminders')
    else expect(link).not.toBeInTheDocument()
  })

  it('provides the SCR-007 entry without changing the profile form', async () => {
    const router = renderRoute('/me/profile')
    expect(await screen.findByRole('link', { name: 'Cài đặt nhắc học' })).toHaveAttribute(
      'href',
      '/me/reminders',
    )
    expect(await screen.findByRole('heading', { name: 'Thông tin cá nhân' })).toBeVisible()
    router.dispose()
  })

  it('mounts the reminder banner alongside the existing SCR-010 dashboard', async () => {
    const router = renderRoute('/learn')
    expect(await screen.findByText('Đã đến giờ ôn: 12 thẻ đang chờ')).toBeVisible()
    expect(await screen.findByRole('heading', { name: 'Học tập' })).toBeVisible()
    expect(getTodayReminder).toHaveBeenCalledOnce()
    router.dispose()
  })
})
