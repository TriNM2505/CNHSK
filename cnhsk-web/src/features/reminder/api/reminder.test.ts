import { act, fireEvent, render, screen } from '@testing-library/react'
import { createElement, StrictMode } from 'react'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { apiRequest } from '@/shared/api'

import ReminderUnsubscribePage from '../pages/ReminderUnsubscribePage'
import { defaultReminderSettings, todayReminderFixture } from '../test-fixtures'
import {
  getReminderSettings,
  getTodayReminder,
  unsubscribeReminderEmail,
  updateReminderSettings,
} from './reminder'

const ORIGIN = 'https://api.example.test'

function json(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}

function clearCsrf() {
  document.cookie = 'XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/'
}

beforeEach(() => {
  vi.stubEnv('VITE_API_BASE_URL', ORIGIN)
  clearCsrf()
})

afterEach(() => {
  vi.unstubAllGlobals()
  vi.unstubAllEnvs()
  clearCsrf()
})

describe('reminder API with the real shared client', () => {
  it('reads settings and today with cookie authentication and cancellation signals', async () => {
    const fetchMock = vi
      .fn<typeof fetch>()
      .mockResolvedValueOnce(json(defaultReminderSettings))
      .mockResolvedValueOnce(json(todayReminderFixture))
    vi.stubGlobal('fetch', fetchMock)
    const controller = new AbortController()
    await expect(getReminderSettings(controller.signal)).resolves.toEqual(defaultReminderSettings)
    await expect(getTodayReminder(controller.signal)).resolves.toEqual(todayReminderFixture)
    expect(fetchMock.mock.calls.map(([url]) => url)).toEqual([
      `${ORIGIN}/api/me/reminder`,
      `${ORIGIN}/api/reminders/today`,
    ])
    for (const [, init] of fetchMock.mock.calls) {
      expect(init).toMatchObject({
        method: 'GET',
        credentials: 'include',
        signal: controller.signal,
      })
    }
  })

  it('obtains CSRF before PUT and sends exactly the three update fields', async () => {
    const body = { reminderTime: '09:00', web: true, email: false }
    const fetchMock = vi.fn<typeof fetch>().mockImplementation(async (url) => {
      if (url === `${ORIGIN}/api/auth/csrf`) {
        document.cookie = 'XSRF-TOKEN=reminder-csrf; path=/'
        return new Response(null, { status: 200 })
      }
      return json({ ...defaultReminderSettings, reminderTime: '09:00', configured: true })
    })
    vi.stubGlobal('fetch', fetchMock)
    await updateReminderSettings(body)
    expect(fetchMock).toHaveBeenCalledTimes(2)
    expect(fetchMock.mock.calls[0]![0]).toBe(`${ORIGIN}/api/auth/csrf`)
    const [url, init] = fetchMock.mock.calls[1]!
    expect(url).toBe(`${ORIGIN}/api/me/reminder`)
    expect(init).toMatchObject({
      method: 'PUT',
      credentials: 'include',
      body: JSON.stringify(body),
    })
    expect(new Headers(init?.headers).get('X-XSRF-TOKEN')).toBe('reminder-csrf')
  })

  it('preserves error code, message and request_id from a settings rejection', async () => {
    document.cookie = 'XSRF-TOKEN=reminder-csrf; path=/'
    vi.stubGlobal(
      'fetch',
      vi.fn<typeof fetch>().mockResolvedValue(
        json(
          {
            error_code: 'EMAIL_NOT_VERIFIED',
            message: 'Vui lòng xác thực email.',
            request_id: 'req-reminder-api',
          },
          422,
        ),
      ),
    )
    await expect(
      updateReminderSettings({ reminderTime: '20:00', web: true, email: true }),
    ).rejects.toMatchObject({
      status: 422,
      errorCode: 'EMAIL_NOT_VERIFIED',
      message: 'Vui lòng xác thực email.',
      requestId: 'req-reminder-api',
    })
  })

  it('AT-17 opening a token link sends zero POSTs; clicking sends exactly one anonymous POST', async () => {
    const fetchMock = vi.fn<typeof fetch>().mockResolvedValue(new Response(null, { status: 204 }))
    vi.stubGlobal('fetch', fetchMock)
    render(
      createElement(
        StrictMode,
        null,
        createElement(
          MemoryRouter,
          {
            initialEntries: ['/reminder/unsubscribe?token=fixture-signed-reminder-token'],
          },
          createElement(ReminderUnsubscribePage),
        ),
      ),
    )
    await act(async () => undefined)
    expect(fetchMock).not.toHaveBeenCalled()
    fireEvent.click(screen.getByRole('button', { name: 'Tắt nhắc qua email' }))
    expect(await screen.findByRole('status')).toHaveTextContent('Đã tắt.')
    expect(fetchMock).toHaveBeenCalledOnce()
    const [url, init] = fetchMock.mock.calls[0]!
    expect(url).toBe(`${ORIGIN}/api/public/reminder/unsubscribe`)
    expect(init).toMatchObject({
      method: 'POST',
      body: JSON.stringify({ token: 'fixture-signed-reminder-token' }),
    })
    expect(new Headers(init?.headers).has('X-XSRF-TOKEN')).toBe(false)
    expect(screen.queryByText('fixture-signed-reminder-token')).not.toBeInTheDocument()
  })

  it.each([
    [400, 'INVALID_UNSUBSCRIBE_TOKEN'],
    [429, 'RATE_LIMITED'],
  ] as const)(
    'unsubscribe preserves %s %s and never retries automatically',
    async (status, errorCode) => {
      const fetchMock = vi.fn<typeof fetch>().mockResolvedValue(
        json(
          {
            error_code: errorCode,
            message: 'Thông báo an toàn.',
            request_id: 'req-unsubscribe',
          },
          status,
        ),
      )
      vi.stubGlobal('fetch', fetchMock)
      await expect(unsubscribeReminderEmail('fixture-signed-reminder-token')).rejects.toMatchObject(
        { status, errorCode, requestId: 'req-unsubscribe' },
      )
      expect(fetchMock).toHaveBeenCalledOnce()
      expect(fetchMock.mock.calls[0]![1]?.method).toBe('POST')
    },
  )

  it('settings PUT remains protected after adding the public unsubscribe exemption', async () => {
    const fetchMock = vi.fn<typeof fetch>().mockImplementation(async (url) => {
      if (url === `${ORIGIN}/api/auth/csrf`) {
        document.cookie = 'XSRF-TOKEN=reminder-csrf; path=/'
        return new Response(null, { status: 200 })
      }
      return new Response(null, { status: 204 })
    })
    vi.stubGlobal('fetch', fetchMock)
    await apiRequest<void>('/api/me/reminder', { method: 'PUT', body: {} })
    expect(fetchMock).toHaveBeenCalledTimes(2)
    expect(new Headers(fetchMock.mock.calls[1]![1]?.headers).get('X-XSRF-TOKEN')).toBe(
      'reminder-csrf',
    )
  })
})
