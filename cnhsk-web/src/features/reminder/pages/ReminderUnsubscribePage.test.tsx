import { act, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { StrictMode } from 'react'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { ApiError } from '@/shared/api'

import { unsubscribeReminderEmail } from '../api/reminder'
import ReminderUnsubscribePage from './ReminderUnsubscribePage'

vi.mock('../api/reminder', () => ({ unsubscribeReminderEmail: vi.fn() }))

const TOKEN = 'self-authored-reminder-token'

function renderPage(query = `?token=${TOKEN}`) {
  return render(
    <StrictMode>
      <MemoryRouter initialEntries={[`/reminder/unsubscribe${query}`]}>
        <ReminderUnsubscribePage />
      </MemoryRouter>
    </StrictMode>,
  )
}

beforeEach(() => {
  vi.mocked(unsubscribeReminderEmail).mockReset()
})

describe('public reminder unsubscribe confirmation', () => {
  it('AT-17 does not unsubscribe on mount, including StrictMode, and only calls once on click', async () => {
    vi.mocked(unsubscribeReminderEmail).mockResolvedValue(undefined)
    renderPage()
    await act(async () => undefined)
    expect(screen.getAllByRole('button')).toHaveLength(1)
    expect(unsubscribeReminderEmail).not.toHaveBeenCalled()
    expect(screen.queryByText(TOKEN)).not.toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: 'Tắt nhắc qua email' }))
    expect(await screen.findByRole('status')).toHaveTextContent(
      'Đã tắt. Bạn có thể bật lại trong Cài đặt nhắc học.',
    )
    expect(unsubscribeReminderEmail).toHaveBeenCalledOnce()
    expect(unsubscribeReminderEmail).toHaveBeenCalledWith(TOKEN)
    expect(screen.getByRole('button')).toBeDisabled()
  })

  it.each(['', '?token=', '?token=%20%20'])(
    'missing token %s is invalid and sends no request',
    (query) => {
      renderPage(query)
      expect(screen.getByRole('alert')).toHaveTextContent('Liên kết không hợp lệ')
      expect(screen.getByRole('button')).toBeDisabled()
      expect(unsubscribeReminderEmail).not.toHaveBeenCalled()
    },
  )

  it('400 offers login returning to reminder settings without displaying token or server text', async () => {
    vi.mocked(unsubscribeReminderEmail).mockRejectedValue(
      new ApiError({
        status: 400,
        errorCode: 'INVALID_UNSUBSCRIBE_TOKEN',
        message: `Invalid ${TOKEN}`,
      }),
    )
    renderPage()
    fireEvent.click(screen.getByRole('button'))
    expect(await screen.findByRole('alert')).toHaveTextContent('Liên kết không còn hiệu lực')
    expect(screen.getByRole('link', { name: 'Đăng nhập để cài đặt nhắc học' })).toHaveAttribute(
      'href',
      '/login?redirect=%2Fme%2Freminders',
    )
    expect(screen.queryByText(TOKEN, { exact: false })).not.toBeInTheDocument()
    expect(screen.getByRole('button')).toBeDisabled()
  })

  it('429 asks to retry later without an automatic second request', async () => {
    vi.mocked(unsubscribeReminderEmail)
      .mockRejectedValueOnce(
        new ApiError({
          status: 429,
          errorCode: 'RATE_LIMITED',
          message: 'Thử lại sau.',
        }),
      )
      .mockResolvedValue(undefined)
    renderPage()
    fireEvent.click(screen.getByRole('button'))
    expect(await screen.findByRole('alert')).toHaveTextContent('Vui lòng thử lại sau.')
    expect(unsubscribeReminderEmail).toHaveBeenCalledOnce()
    fireEvent.click(screen.getByRole('button'))
    expect(await screen.findByRole('status')).toHaveTextContent('Đã tắt.')
    expect(unsubscribeReminderEmail).toHaveBeenCalledTimes(2)
  })

  it('network failure leaves an explicit retry action without leaking error text', async () => {
    vi.mocked(unsubscribeReminderEmail).mockRejectedValue(new Error(TOKEN))
    renderPage()
    fireEvent.click(screen.getByRole('button'))
    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Không thể tắt nhắc qua email. Vui lòng thử lại.',
    )
    expect(screen.queryByText(TOKEN)).not.toBeInTheDocument()
    expect(screen.getByRole('button')).toBeEnabled()
  })

  it('disables the only action while pending and sends no duplicate request', async () => {
    let resolve!: () => void
    vi.mocked(unsubscribeReminderEmail).mockImplementation(
      () =>
        new Promise((done) => {
          resolve = done
        }),
    )
    renderPage()
    fireEvent.click(screen.getByRole('button'))
    await waitFor(() => expect(unsubscribeReminderEmail).toHaveBeenCalledOnce())
    expect(screen.getByRole('button')).toBeDisabled()
    fireEvent.click(screen.getByRole('button'))
    await act(async () => resolve())
    expect(unsubscribeReminderEmail).toHaveBeenCalledOnce()
  })
})
