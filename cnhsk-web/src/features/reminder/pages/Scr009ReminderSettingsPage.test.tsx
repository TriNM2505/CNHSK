import { act, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { ApiError } from '@/shared/api'
import { AuthContext, type AuthContextValue } from '@/shared/auth'

import { getReminderSettings, updateReminderSettings } from '../api/reminder'
import { defaultReminderSettings, reminderAuth } from '../test-fixtures'
import type { ReminderSettings } from '../types'
import Scr009ReminderSettingsPage from './Scr009ReminderSettingsPage'

vi.mock('../api/reminder', () => ({
  getReminderSettings: vi.fn(),
  updateReminderSettings: vi.fn(),
}))

function renderPage(auth: AuthContextValue = reminderAuth) {
  return render(
    <AuthContext.Provider value={auth}>
      <MemoryRouter>
        <Scr009ReminderSettingsPage />
      </MemoryRouter>
    </AuthContext.Provider>,
  )
}

beforeEach(() => {
  vi.mocked(getReminderSettings)
    .mockReset()
    .mockResolvedValue(structuredClone(defaultReminderSettings))
  vi.mocked(updateReminderSettings).mockReset()
})

describe('SCR-009 reminder settings', () => {
  it('loads unconfigured defaults, all 24 whole hours and the Vietnam timezone', async () => {
    renderPage()
    const time = await screen.findByRole('combobox', { name: 'Giờ nhắc' })
    expect(time).toHaveValue('20:00')
    expect(screen.getAllByRole('option')).toHaveLength(24)
    expect(screen.getByRole('option', { name: '00:00' })).toBeVisible()
    expect(screen.getByRole('option', { name: '23:00' })).toBeVisible()
    expect(screen.getByRole('switch', { name: 'Web' })).toHaveAttribute('aria-checked', 'true')
    expect(screen.getByText('Múi giờ: Giờ Việt Nam (UTC+7)')).toBeVisible()
    expect(screen.getByText(/Bạn chưa cài đặt nhắc học/)).toBeVisible()
  })

  it('disables Email when unverified and links to SCR-004', async () => {
    renderPage()
    expect(await screen.findByRole('switch', { name: 'Email' })).toBeDisabled()
    expect(screen.getByRole('link', { name: 'Xác thực email' })).toHaveAttribute(
      'href',
      '/verify-email?redirect=%2Fme%2Freminders',
    )
  })

  it('saves the exact PUT body and displays the returned reminder hour', async () => {
    vi.mocked(getReminderSettings).mockResolvedValue({
      ...defaultReminderSettings,
      emailVerified: true,
    })
    vi.mocked(updateReminderSettings).mockResolvedValue({
      ...defaultReminderSettings,
      reminderTime: '07:00',
      channels: { web: false, email: true },
      configured: true,
    })
    renderPage()
    fireEvent.change(await screen.findByRole('combobox'), { target: { value: '07:00' } })
    fireEvent.click(screen.getByRole('switch', { name: 'Web' }))
    fireEvent.click(screen.getByRole('switch', { name: 'Email' }))
    fireEvent.click(screen.getByRole('button', { name: 'Lưu' }))
    expect(await screen.findByRole('status')).toHaveTextContent('Bạn sẽ được nhắc lúc 07:00')
    expect(updateReminderSettings).toHaveBeenCalledOnce()
    expect(updateReminderSettings).toHaveBeenCalledWith({
      reminderTime: '07:00',
      web: false,
      email: true,
    })
  })

  it('allows both channels to be disabled and shows the all-off message', async () => {
    vi.mocked(updateReminderSettings).mockResolvedValue({
      ...defaultReminderSettings,
      channels: { web: false, email: false },
      configured: true,
    })
    renderPage()
    fireEvent.click(await screen.findByRole('switch', { name: 'Web' }))
    fireEvent.click(screen.getByRole('button', { name: 'Lưu' }))
    expect(await screen.findByRole('status')).toHaveTextContent('Bạn đã tắt mọi nhắc học')
    expect(updateReminderSettings).toHaveBeenCalledWith({
      reminderTime: '20:00',
      web: false,
      email: false,
    })
  })

  it('puts EMAIL_NOT_VERIFIED beside Email and preserves the other edits', async () => {
    vi.mocked(getReminderSettings).mockResolvedValue({
      ...defaultReminderSettings,
      emailVerified: true,
    })
    vi.mocked(updateReminderSettings).mockRejectedValue(
      new ApiError({
        status: 422,
        errorCode: 'EMAIL_NOT_VERIFIED',
        message: 'Email chưa xác thực.',
        requestId: 'req-email',
      }),
    )
    renderPage()
    fireEvent.change(await screen.findByRole('combobox'), { target: { value: '08:00' } })
    fireEvent.click(screen.getByRole('switch', { name: 'Email' }))
    fireEvent.click(screen.getByRole('button', { name: 'Lưu' }))
    expect(
      await screen.findByText('Vui lòng xác thực email trước khi bật nhắc qua email.'),
    ).toBeVisible()
    expect(screen.getByRole('switch', { name: 'Email' })).toHaveAttribute('aria-invalid', 'true')
    expect(screen.getByRole('switch', { name: 'Email' })).toBeDisabled()
    expect(screen.getByRole('combobox')).toHaveValue('08:00')
  })

  it('cancel restores loaded values without a PUT', async () => {
    renderPage()
    fireEvent.change(await screen.findByRole('combobox'), { target: { value: '23:00' } })
    fireEvent.click(screen.getByRole('switch', { name: 'Web' }))
    fireEvent.click(screen.getByRole('button', { name: 'Huỷ' }))
    expect(screen.getByRole('combobox')).toHaveValue('20:00')
    expect(screen.getByRole('switch', { name: 'Web' })).toHaveAttribute('aria-checked', 'true')
    expect(updateReminderSettings).not.toHaveBeenCalled()
  })

  it('cancel restores the latest successful save', async () => {
    vi.mocked(updateReminderSettings).mockResolvedValue({
      ...defaultReminderSettings,
      reminderTime: '09:00',
      configured: true,
    })
    renderPage()
    fireEvent.change(await screen.findByRole('combobox'), { target: { value: '09:00' } })
    fireEvent.click(screen.getByRole('button', { name: 'Lưu' }))
    await screen.findByText('Bạn sẽ được nhắc lúc 09:00')
    fireEvent.change(screen.getByRole('combobox'), { target: { value: '11:00' } })
    fireEvent.click(screen.getByRole('button', { name: 'Huỷ' }))
    expect(screen.getByRole('combobox')).toHaveValue('09:00')
    expect(updateReminderSettings).toHaveBeenCalledOnce()
  })

  it('disables save and cancel while saving and prevents duplicate submissions', async () => {
    let resolve!: (settings: ReminderSettings) => void
    vi.mocked(updateReminderSettings).mockImplementation(
      () =>
        new Promise((done) => {
          resolve = done
        }),
    )
    renderPage()
    await screen.findByRole('combobox')
    fireEvent.click(screen.getByRole('button', { name: 'Lưu' }))
    await waitFor(() => expect(updateReminderSettings).toHaveBeenCalledOnce())
    expect(screen.getByRole('button', { name: 'Lưu' })).toBeDisabled()
    expect(screen.getByRole('button', { name: 'Huỷ' })).toBeDisabled()
    fireEvent.click(screen.getByRole('button', { name: 'Lưu' }))
    await act(async () => resolve(defaultReminderSettings))
    expect(updateReminderSettings).toHaveBeenCalledOnce()
  })

  it('shows loading, then empty data with a safe reload action', async () => {
    let resolve!: (settings: ReminderSettings) => void
    vi.mocked(getReminderSettings).mockImplementation(
      () =>
        new Promise((done) => {
          resolve = done
        }),
    )
    renderPage()
    expect(await screen.findByRole('status', { name: 'Đang tải' })).toBeVisible()
    expect(screen.queryByRole('combobox')).not.toBeInTheDocument()
    await act(async () => resolve(undefined as unknown as ReminderSettings))
    expect(
      await screen.findByRole('heading', { name: 'Chưa tải được cài đặt nhắc học' }),
    ).toBeVisible()
    expect(screen.getByRole('button', { name: 'Tải lại' })).toBeEnabled()
  })

  it('keeps request_id and retries a failed settings read', async () => {
    vi.mocked(getReminderSettings).mockRejectedValueOnce(
      new ApiError({
        status: 503,
        errorCode: 'UNAVAILABLE',
        message: 'Không tải được cài đặt.',
        requestId: 'req-reminder',
      }),
    )
    renderPage()
    expect(await screen.findByText('Mã lỗi: req-reminder')).toBeVisible()
    fireEvent.click(screen.getByRole('button', { name: 'Thử lại' }))
    expect(await screen.findByRole('combobox')).toHaveValue('20:00')
    expect(getReminderSettings).toHaveBeenCalledTimes(2)
  })

  it('shows validation errors at the time selector', async () => {
    vi.mocked(updateReminderSettings).mockRejectedValue(
      new ApiError({
        status: 422,
        errorCode: 'VALIDATION_FAILED',
        message: 'Giờ không hợp lệ.',
      }),
    )
    renderPage()
    await screen.findByRole('combobox')
    fireEvent.click(screen.getByRole('button', { name: 'Lưu' }))
    expect(await screen.findByText('Vui lòng chọn giờ tròn từ 00:00 đến 23:00.')).toBeVisible()
    expect(screen.getByRole('combobox')).toHaveAttribute('aria-invalid', 'true')
  })

  it('does not load private settings without LEARNER access', () => {
    renderPage({ ...reminderAuth, user: null, status: 'guest' })
    expect(getReminderSettings).not.toHaveBeenCalled()
  })
})
