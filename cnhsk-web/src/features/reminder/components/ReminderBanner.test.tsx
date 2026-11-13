import { act, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { getTodayReminder } from '../api/reminder'
import { todayReminderFixture } from '../test-fixtures'
import { ReminderBanner, REMINDER_DISMISSED_KEY } from './ReminderBanner'

vi.mock('../api/reminder', () => ({ getTodayReminder: vi.fn() }))

function renderBanner() {
  return render(
    <MemoryRouter>
      <ReminderBanner />
    </MemoryRouter>,
  )
}

beforeEach(() => {
  vi.useFakeTimers({ toFake: ['Date'] })
  vi.setSystemTime(new Date('2026-11-14T13:30:00Z'))
  window.localStorage.clear()
  vi.mocked(getTodayReminder)
    .mockReset()
    .mockResolvedValue({ ...todayReminderFixture })
})

afterEach(() => {
  vi.restoreAllMocks()
  vi.useRealTimers()
})

describe('SCR-010 reminder banner', () => {
  it('show=false leaves no banner', async () => {
    vi.mocked(getTodayReminder).mockResolvedValue({ ...todayReminderFixture, show: false })
    renderBanner()
    await act(async () => undefined)
    expect(screen.queryByRole('complementary', { name: 'Nhắc ôn tập' })).not.toBeInTheDocument()
  })

  it('show=true renders the due count, review link and accessible close action', async () => {
    renderBanner()
    expect(await screen.findByText('Đã đến giờ ôn: 12 thẻ đang chờ')).toBeVisible()
    expect(screen.getByRole('link', { name: 'Ôn ngay' })).toHaveAttribute('href', '/learn/review')
    expect(screen.getByRole('button', { name: 'Đóng nhắc ôn tập' })).toBeEnabled()
  })

  it('persists dismissal through remounts on the same Vietnam day', async () => {
    const view = renderBanner()
    fireEvent.click(await screen.findByRole('button', { name: 'Đóng nhắc ôn tập' }))
    expect(window.localStorage.getItem(REMINDER_DISMISSED_KEY)).toBe('2026-11-14')
    expect(screen.queryByRole('complementary')).not.toBeInTheDocument()
    view.unmount()
    renderBanner()
    await act(async () => undefined)
    expect(screen.queryByRole('complementary')).not.toBeInTheDocument()
  })

  it('uses the Vietnam day instead of the UTC date and shows again on a new day', async () => {
    window.localStorage.setItem(REMINDER_DISMISSED_KEY, '2026-11-14')
    vi.setSystemTime(new Date('2026-11-14T16:59:59Z'))
    const view = renderBanner()
    await act(async () => undefined)
    expect(screen.queryByRole('complementary')).not.toBeInTheDocument()
    view.unmount()
    vi.setSystemTime(new Date('2026-11-14T17:00:00Z'))
    renderBanner()
    expect(await screen.findByText('Đã đến giờ ôn: 12 thẻ đang chờ')).toBeVisible()
  })

  it('survives localStorage read and write exceptions and still closes in memory', async () => {
    vi.spyOn(Storage.prototype, 'getItem').mockImplementation(() => {
      throw new Error('storage unavailable')
    })
    vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => {
      throw new Error('storage unavailable')
    })
    renderBanner()
    expect(await screen.findByText('Đã đến giờ ôn: 12 thẻ đang chờ')).toBeVisible()
    fireEvent.click(screen.getByRole('button', { name: 'Đóng nhắc ôn tập' }))
    expect(screen.queryByRole('complementary')).not.toBeInTheDocument()
  })

  it('hides API errors without throwing into the dashboard', async () => {
    vi.mocked(getTodayReminder).mockRejectedValue(new Error('unavailable'))
    renderBanner()
    await act(async () => undefined)
    expect(screen.queryByRole('complementary')).not.toBeInTheDocument()
    expect(screen.queryByText('unavailable')).not.toBeInTheDocument()
  })

  it('refreshes at Vietnam midnight while the page stays open', async () => {
    vi.useRealTimers()
    vi.useFakeTimers()
    vi.setSystemTime(new Date('2026-11-14T16:59:59Z'))
    window.localStorage.setItem(REMINDER_DISMISSED_KEY, '2026-11-14')
    renderBanner()
    await act(async () => undefined)
    expect(screen.queryByRole('complementary')).not.toBeInTheDocument()
    await act(async () => {
      await vi.advanceTimersByTimeAsync(1000)
    })
    await act(async () => undefined)
    expect(getTodayReminder).toHaveBeenCalledTimes(2)
    expect(screen.getByRole('complementary')).toBeVisible()
  })

  it('aborts the read when the banner unmounts', async () => {
    const view = renderBanner()
    await waitFor(() => expect(getTodayReminder).toHaveBeenCalledOnce())
    const signal = vi.mocked(getTodayReminder).mock.calls[0]![0]!
    view.unmount()
    expect(signal.aborted).toBe(true)
  })
})
