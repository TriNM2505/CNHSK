import { act, fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { ApiError } from '@/shared/api'
import { AuthContext, type AuthContextValue } from '@/shared/auth'

import {
  creditsCreatedBatch,
  creditsFreeWallet,
  creditsRedeemResult,
  creditsWallet,
} from '../test-fixtures'
import type { RedeemResult, Wallet } from '../types'
import { getMyWallet, redeemCard } from '../api/wallet'
import Scr030PlanCreditsPage from './Scr030PlanCreditsPage'

vi.mock('../api/wallet', () => ({ getMyWallet: vi.fn(), redeemCard: vi.fn() }))

const code = creditsCreatedBatch.codes[0]!
const invalidMessage = 'Mã thẻ không hợp lệ hoặc không thể sử dụng.'

function authValue(id: number | null = 7): AuthContextValue {
  return {
    user:
      id === null
        ? null
        : {
            id,
            email: 'credits@example.test',
            displayName: 'Người học thử',
            roles: ['LEARNER'],
            emailVerified: false,
          },
    status: id === null ? 'guest' : 'authenticated',
    error: null,
    reloadCurrentUser: async () => undefined,
    setAuthenticatedUser: () => undefined,
    signOut: async () => undefined,
  }
}

function renderPage(id: number | null = 7) {
  const content = (userId: number | null) => (
    <AuthContext.Provider value={authValue(userId)}>
      <MemoryRouter>
        <Scr030PlanCreditsPage />
      </MemoryRouter>
    </AuthContext.Provider>
  )
  const view = render(content(id))
  return { ...view, changeUser: (userId: number | null) => view.rerender(content(userId)) }
}

async function ready() {
  await screen.findByRole('table', { name: 'Lượt sử dụng theo tính năng' })
}

function enterCode(value = code) {
  fireEvent.change(screen.getByRole('textbox', { name: 'Mã thẻ' }), { target: { value } })
}

function submit() {
  fireEvent.submit(screen.getByRole('form', { name: 'Nạp mã thẻ' }))
}

async function confirm() {
  submit()
  await userEvent.click(screen.getByRole('button', { name: 'Xác nhận nạp' }))
}

beforeEach(() => {
  vi.resetAllMocks()
  vi.mocked(getMyWallet).mockResolvedValue(structuredClone(creditsWallet))
  vi.mocked(redeemCard).mockResolvedValue(structuredClone(creditsRedeemResult))
})

afterEach(() => {
  vi.restoreAllMocks()
  vi.useRealTimers()
  vi.unstubAllGlobals()
})

describe('SCR-030 plan and credits (FR-024, AT-22)', () => {
  it('FR024_AT22_rendersPlanAndExpiry in Vietnam time', async () => {
    renderPage()
    await ready()
    const plan = within(screen.getByRole('region', { name: 'Gói hiện tại' }))
    expect(plan.getByText('PREMIUM mẫu')).toBeVisible()
    expect(plan.getByText(/31\/12\/2026/)).toHaveTextContent('09:01')
    expect(plan.getByText(/31\/12\/2026/)).toHaveTextContent('giờ Việt Nam')
  })

  it('FR024_AT22_rendersPlanAndExpiry with no expiry for FREE', async () => {
    vi.mocked(getMyWallet).mockResolvedValue(structuredClone(creditsFreeWallet))
    renderPage()
    await ready()
    expect(screen.getByText('Miễn phí')).toBeVisible()
    expect(screen.getByText('Không có ngày hết hạn')).toBeVisible()
    expect(screen.getByText('0 điểm')).toBeVisible()
  })

  it('FR024_AT22_rendersBalance from the fixture', async () => {
    renderPage()
    await ready()
    expect(screen.getByText('121 điểm')).toBeVisible()
  })

  it('FR024_AT22_rendersFourQuotas with separate limits, used and remaining', async () => {
    vi.mocked(getMyWallet).mockResolvedValue(structuredClone(creditsFreeWallet))
    renderPage()
    await ready()
    const rows = within(screen.getByRole('table')).getAllByRole('row')
    expect(rows).toHaveLength(5)
    expect(rows.slice(1).map((row) => within(row).getByRole('rowheader').textContent)).toEqual([
      'Tạo câu hỏi',
      'Dịch văn bản',
      'Trợ lý',
      'Chấm bài',
    ])
    expect(
      within(rows[1]!)
        .getAllByRole('cell')
        .map((cell) => cell.textContent),
    ).toEqual(['10', '3', '7'])
  })

  it('FR024_AT22_formatsResetInVietnamTime across the UTC month boundary', async () => {
    renderPage()
    await ready()
    expect(creditsWallet.resetAt).toBe('2026-11-30T17:00:00Z')
    expect(screen.getByText(/Đặt lại lúc/)).toHaveTextContent('01/12/2026')
    expect(screen.getByText(/Đặt lại lúc/)).toHaveTextContent('00:00')
  })

  it('FR024_AT22_formatsCodeInput while typing, without a trailing separator', async () => {
    renderPage()
    await ready()
    const input = screen.getByRole('textbox', { name: 'Mã thẻ' })
    await userEvent.type(input, 'abcd')
    expect(input).toHaveValue('ABCD')
    await userEvent.type(input, 'efgh01234567')
    expect(input).toHaveValue('ABCD-EFGH-0123-4567')
  })

  it.each(['demo only 0001 use0', 'demo-only-0001-use0', 'demoonly0001use0'])(
    'FR024_AT22_formatsCodeInput when pasting %s and preserves O/I/L',
    async (value) => {
      renderPage()
      await ready()
      const input = screen.getByRole('textbox', { name: 'Mã thẻ' })
      await userEvent.click(input)
      await userEvent.paste(value)
      expect(input).toHaveValue(code)
    },
  )

  it('FR024_AT22_formatsCodeInput with backspace/delete across a separator and selection replacement', async () => {
    renderPage()
    await ready()
    const input = screen.getByRole('textbox', { name: 'Mã thẻ' }) as HTMLInputElement
    enterCode()
    input.focus()
    input.setSelectionRange(5, 5)
    await userEvent.keyboard('{Backspace}')
    expect(input).toHaveValue('DEMO-NLY0-001U-SE0')
    expect(input.selectionStart).toBe(3)
    enterCode()
    input.setSelectionRange(4, 4)
    await userEvent.keyboard('{Delete}')
    expect(input).toHaveValue('DEMO-NLY0-001U-SE0')
    input.setSelectionRange(5, 9)
    await userEvent.paste('abcd')
    expect(input).toHaveValue('DEMO-ABCD-001U-SE0')
  })

  it.each(['ABCD$EFGH', 'ABCD_1234', '中文', 'ＡＢＣＤ', 'DEMOONLY0001USE00'])(
    'FR024_AT22_formatsCodeInput by rejecting the whole invalid edit %s',
    async (value) => {
      renderPage()
      await ready()
      enterCode('ABCD')
      enterCode(value)
      expect(screen.getByRole('textbox')).toHaveValue('ABCD')
      expect(screen.getByText(/Nhập đủ 16/, { selector: 'span' })).toBeVisible()
      submit()
      expect(screen.queryByRole('button', { name: 'Xác nhận nạp' })).not.toBeInTheDocument()
    },
  )

  it('FR024_requiresInlineConfirmation and cancellation keeps the code', async () => {
    vi.mocked(redeemCard).mockResolvedValue({ ...creditsRedeemResult, balance: 221 })
    const browserConfirm = vi.spyOn(window, 'confirm')
    renderPage()
    await ready()
    enterCode()
    submit()
    expect(redeemCard).not.toHaveBeenCalled()
    expect(screen.getByRole('region', { name: 'Xác nhận nạp mã thẻ?' })).toBeVisible()
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'Huỷ' }))
    expect(screen.getByRole('textbox')).toHaveValue(code)
    expect(redeemCard).not.toHaveBeenCalled()
    expect(browserConfirm).not.toHaveBeenCalled()
    await confirm()
    expect(redeemCard).toHaveBeenCalledTimes(1)
    expect(redeemCard).toHaveBeenCalledWith({ code })
  })

  it('FR024_requiresInlineConfirmation for exactly 16 characters', async () => {
    renderPage()
    await ready()
    enterCode('ABCD')
    submit()
    expect(screen.getByRole('textbox')).toHaveAttribute('aria-invalid', 'true')
    expect(screen.queryByRole('button', { name: 'Xác nhận nạp' })).not.toBeInTheDocument()
    expect(redeemCard).not.toHaveBeenCalled()
  })

  it('FR024_rendersRedeemResult consistently without changing original fixtures', async () => {
    vi.mocked(getMyWallet)
      .mockResolvedValueOnce(structuredClone(creditsWallet))
      .mockResolvedValueOnce({ ...structuredClone(creditsWallet), balance: 221 })
    const original = JSON.stringify({ creditsWallet, creditsRedeemResult })
    renderPage()
    await ready()
    enterCode()
    await confirm()
    expect(await screen.findByRole('status')).toHaveTextContent(
      'Đã nạp +100 điểm. Số dư: 221 điểm.',
    )
    expect(await screen.findByText('221 điểm')).toBeVisible()
    expect(screen.getByRole('textbox')).toHaveValue('')
    expect(JSON.stringify({ creditsWallet, creditsRedeemResult })).toBe(original)
  })

  it('FR024_rendersRedeemResult for SUBSCRIPTION from the supplied snapshot', async () => {
    vi.mocked(redeemCard).mockResolvedValue({
      cardType: 'SUBSCRIPTION',
      amount: 0,
      balance: 121,
      plan: { ...creditsWallet.plan, name: 'Gói sau gia hạn', expiresAt: '2027-01-30T02:01:00Z' },
    } satisfies RedeemResult)
    vi.mocked(getMyWallet)
      .mockResolvedValueOnce(structuredClone(creditsWallet))
      .mockResolvedValueOnce({
        ...structuredClone(creditsWallet),
        plan: { ...creditsWallet.plan, name: 'Gói sau gia hạn', expiresAt: '2027-01-30T02:01:00Z' },
      })
    renderPage()
    await ready()
    enterCode()
    await confirm()
    expect(await screen.findByRole('status')).toHaveTextContent('Đã cập nhật gói Gói sau gia hạn.')
    expect(screen.getByText('Gói sau gia hạn')).toBeVisible()
    expect(screen.getByText(/30\/01\/2027/)).toHaveTextContent('09:01')
    expect(screen.getByText('121 điểm')).toBeVisible()
  })

  it.each(['Mã đã dùng', 'Mã hết hạn', 'Lô đã khoá', 'Mã do chính bạn tạo'])(
    'FR024_AT22_usesOneInvalidCodeMessage instead of %s',
    async (reason) => {
      vi.mocked(redeemCard).mockRejectedValue(
        new ApiError({
          status: 400,
          errorCode: 'INVALID_CARD_CODE',
          message: reason,
        }),
      )
      renderPage()
      await ready()
      enterCode()
      await confirm()
      expect(await screen.findByRole('alert')).toHaveTextContent(invalidMessage)
      expect(screen.queryByText(reason)).not.toBeInTheDocument()
      expect(screen.getByText('121 điểm')).toBeVisible()
    },
  )

  it('FR024 preserves the generic invalid-code response from the API', async () => {
    vi.mocked(redeemCard).mockRejectedValue(
      new ApiError({ status: 400, errorCode: 'INVALID_CARD_CODE', message: 'Mã không dùng được' }),
    )
    renderPage()
    await ready()
    enterCode(creditsCreatedBatch.codes[1]!)
    await confirm()
    expect(await screen.findByRole('alert')).toHaveTextContent(invalidMessage)
    expect(screen.getByText('121 điểm')).toBeVisible()
  })

  it('FR024_AT22_countsDownRateLimit using its deadline, blocking both actions without automatic retry', async () => {
    vi.mocked(redeemCard).mockRejectedValue(
      new ApiError({
        status: 429,
        errorCode: 'RATE_LIMITED',
        message: 'Thông báo mẫu',
        extra: { retryAfterSeconds: 60 },
      }),
    )
    renderPage()
    await ready()
    vi.useFakeTimers()
    vi.setSystemTime(new Date('2026-11-05T00:00:00Z'))
    enterCode()
    submit()
    await act(async () => {
      fireEvent.click(screen.getByRole('button', { name: 'Xác nhận nạp' }))
    })
    expect(screen.getByRole('status')).toHaveTextContent('Thử lại sau 60 giây.')
    expect(screen.getByRole('button', { name: /^Nạp mã$/ })).toBeDisabled()
    expect(screen.getByRole('button', { name: 'Xác nhận nạp' })).toBeDisabled()
    submit()
    fireEvent.click(screen.getByRole('button', { name: 'Xác nhận nạp' }))
    expect(redeemCard).toHaveBeenCalledTimes(1)
    act(() => {
      vi.advanceTimersByTime(1000)
    })
    expect(screen.getByRole('status')).toHaveTextContent('59 giây')
    // Simulate a background tab whose clock advances before the next interval fires.
    vi.setSystemTime(new Date('2026-11-05T00:01:30Z'))
    act(() => {
      vi.advanceTimersByTime(250)
    })
    expect(screen.queryByText(/Thử lại sau/)).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: /^Nạp mã$/ })).toBeEnabled()
    expect(screen.getByRole('button', { name: 'Xác nhận nạp' })).toBeEnabled()
    expect(redeemCard).toHaveBeenCalledTimes(1)
  })

  it('FR024 clears its countdown timer on unmount', async () => {
    vi.mocked(redeemCard).mockRejectedValue(
      new ApiError({
        status: 429,
        errorCode: 'RATE_LIMITED',
        message: 'Thông báo mẫu',
        extra: { retryAfterSeconds: 60 },
      }),
    )
    const view = renderPage()
    await ready()
    vi.useFakeTimers()
    const baseline = vi.getTimerCount()
    const errors = vi.spyOn(console, 'error')
    enterCode()
    submit()
    await act(async () => {
      fireEvent.click(screen.getByRole('button', { name: 'Xác nhận nạp' }))
    })
    expect(vi.getTimerCount()).toBeGreaterThan(baseline)
    expect(screen.getByRole('status')).toHaveTextContent('Thử lại sau 60 giây.')
    act(() => {
      vi.advanceTimersByTime(1000)
    })
    expect(screen.getByRole('status')).toHaveTextContent('Thử lại sau 59 giây.')
    view.unmount()
    // Flush jsdom's pending select event without retaining a page countdown.
    act(() => {
      vi.runOnlyPendingTimers()
    })
    expect(vi.getTimerCount()).toBe(baseline)
    act(() => {
      vi.advanceTimersByTime(10_000)
    })
    expect(redeemCard).toHaveBeenCalledTimes(1)
    expect(errors).not.toHaveBeenCalled()
  })

  it('FR024 restores real timers before the next test', () => {
    expect(vi.isFakeTimers()).toBe(false)
    expect(globalThis.setTimeout).not.toHaveProperty('clock')
  })

  it('FR024 prevents duplicate pending submissions and ignores completion after unmount', async () => {
    let resolve!: (value: RedeemResult) => void
    vi.mocked(redeemCard).mockImplementation(
      () =>
        new Promise<RedeemResult>((done) => {
          resolve = done
        }),
    )
    const view = renderPage()
    await ready()
    enterCode()
    submit()
    fireEvent.click(screen.getByRole('button', { name: 'Xác nhận nạp' }))
    submit()
    fireEvent.click(screen.getByRole('button', { name: 'Xác nhận nạp' }))
    expect(screen.getByRole('button', { name: /^Nạp mã$/ })).toBeDisabled()
    expect(redeemCard).toHaveBeenCalledTimes(1)
    view.unmount()
    await act(async () => {
      resolve({ ...creditsRedeemResult, balance: 221 })
    })
  })

  it('FR024_rendersNoticeAndHistoryLink', async () => {
    renderPage()
    await ready()
    expect(screen.getByText('Mã thẻ do hệ sinh thái phát hành; CNHSK không thu tiền')).toBeVisible()
    expect(screen.getByRole('link', { name: 'Lịch sử giao dịch' })).toHaveAttribute(
      'href',
      '/account/credits/history',
    )
  })

  it('FR024_AT22_hasNoPurchaseControls or hard-coded price', async () => {
    renderPage()
    await ready()
    expect(screen.queryByRole('button', { name: /mua|thanh toán/i })).not.toBeInTheDocument()
    expect(screen.queryByRole('link', { name: /mua|thanh toán/i })).not.toBeInTheDocument()
    expect(screen.queryByText(/199000|199\.000|₫|VNĐ|giá niêm yết/i)).not.toBeInTheDocument()
  })

  it('FR024 supports loading, error and explicit retry', async () => {
    let resolve!: (wallet: Wallet) => void
    const loadWallet = vi
      .mocked(getMyWallet)
      .mockRejectedValueOnce(new Error('Chi tiết nội bộ'))
      .mockImplementationOnce(
        () =>
          new Promise<Wallet>((done) => {
            resolve = done
          }),
      )
    renderPage()
    expect(screen.getByRole('status', { name: 'Đang tải ví điểm' })).toBeVisible()
    await screen.findByRole('heading', { name: 'Chưa tải được ví điểm' })
    expect(screen.queryByText('Chi tiết nội bộ')).not.toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'Thử lại' }))
    expect(screen.getByRole('status', { name: 'Đang tải ví điểm' })).toBeVisible()
    await act(async () => {
      resolve(creditsWallet)
    })
    await ready()
    expect(loadWallet).toHaveBeenCalledTimes(2)
    expect(loadWallet.mock.calls[0]![0]).toBeInstanceOf(AbortSignal)
  })

  it('FR024 preserves the draft on a temporary redeem error and allows an explicit retry', async () => {
    vi.mocked(redeemCard)
      .mockRejectedValueOnce(new Error('Chi tiết nội bộ'))
      .mockResolvedValueOnce({ ...creditsRedeemResult, balance: 221 })
    renderPage()
    await ready()
    enterCode()
    await confirm()
    expect(await screen.findByRole('alert')).toHaveTextContent('Chưa thể nạp mã. Vui lòng thử lại.')
    expect(screen.getByRole('textbox')).toHaveValue(code)
    expect(redeemCard).toHaveBeenCalledTimes(1)
    await confirm()
    expect(await screen.findByRole('status')).toHaveTextContent('221 điểm')
    expect(redeemCard).toHaveBeenCalledTimes(2)
  })

  it('FR024 uses mocked API modules without HTTP, storage or code logging', async () => {
    const fetchMock = vi.fn()
    vi.stubGlobal('fetch', fetchMock)
    const storage = vi.spyOn(Storage.prototype, 'setItem')
    const logs = [vi.spyOn(console, 'log'), vi.spyOn(console, 'warn'), vi.spyOn(console, 'error')]
    renderPage()
    await ready()
    enterCode()
    await confirm()
    await waitFor(() => expect(screen.getByRole('status')).toHaveTextContent('Đã nạp'))
    expect(getMyWallet).toHaveBeenCalledWith(expect.any(AbortSignal))
    expect(redeemCard).toHaveBeenCalledWith({ code })
    expect(fetchMock).not.toHaveBeenCalled()
    expect(storage).not.toHaveBeenCalled()
    for (const log of logs) expect(JSON.stringify(log.mock.calls)).not.toContain(code)
  })

  it.each([undefined, '60', NaN, -1, Infinity])(
    'FR024 handles invalid retryAfterSeconds %s without a countdown',
    async (retryAfterSeconds) => {
      vi.mocked(redeemCard).mockRejectedValue(
        new ApiError({
          status: 429,
          errorCode: 'RATE_LIMITED',
          message: 'Thông báo riêng',
          extra: { retryAfterSeconds },
        }),
      )
      renderPage()
      await ready()
      enterCode()
      await confirm()
      expect(await screen.findByRole('alert')).toHaveTextContent(
        'Chưa thể nạp mã. Vui lòng thử lại.',
      )
      expect(screen.queryByText(/Thử lại sau/)).not.toBeInTheDocument()
      expect(screen.getByRole('button', { name: /^Nạp mã$/ })).toBeEnabled()
    },
  )

  it('FR024 keeps the successful redemption result when refreshing the wallet fails', async () => {
    let reject!: (error: Error) => void
    vi.mocked(getMyWallet)
      .mockResolvedValueOnce(structuredClone(creditsWallet))
      .mockImplementationOnce(
        () =>
          new Promise<Wallet>((_resolve, fail) => {
            reject = fail
          }),
      )
      .mockResolvedValueOnce({ ...structuredClone(creditsWallet), balance: 221 })
    renderPage()
    await ready()
    enterCode()
    await confirm()
    expect(screen.getByText('Đã nạp +100 điểm. Số dư: 221 điểm.')).toBeVisible()
    expect(screen.getByRole('status', { name: 'Đang tải ví điểm' })).toBeVisible()
    await act(async () => {
      reject(new Error('Tải ví lỗi'))
    })
    expect(await screen.findByRole('heading', { name: 'Chưa tải được ví điểm' })).toBeVisible()
    expect(screen.getByText('Đã nạp +100 điểm. Số dư: 221 điểm.')).toBeVisible()
    expect(screen.queryByText('Chưa thể nạp mã. Vui lòng thử lại.')).not.toBeInTheDocument()
    expect(redeemCard).toHaveBeenCalledTimes(1)
    await userEvent.click(screen.getByRole('button', { name: 'Thử lại' }))
    await ready()
    expect(screen.getByText('221 điểm')).toBeVisible()
    expect(getMyWallet).toHaveBeenCalledTimes(3)
    expect(redeemCard).toHaveBeenCalledTimes(1)
  })

  it('FR024 ignores a late redemption after switching accounts and clears the old draft', async () => {
    let resolve!: (value: RedeemResult) => void
    vi.mocked(redeemCard).mockImplementationOnce(
      () =>
        new Promise<RedeemResult>((done) => {
          resolve = done
        }),
    )
    const view = renderPage()
    await ready()
    enterCode()
    submit()
    fireEvent.click(screen.getByRole('button', { name: 'Xác nhận nạp' }))
    view.changeUser(8)
    await ready()
    expect(screen.getByRole('textbox')).toHaveValue('')
    await act(async () => {
      resolve(structuredClone(creditsRedeemResult))
    })
    expect(screen.queryByText(/Đã nạp/)).not.toBeInTheDocument()
    expect(getMyWallet).toHaveBeenCalledTimes(2)
  })

  it('FR024 does not fetch private wallet data without learner authentication', () => {
    renderPage(null)
    expect(getMyWallet).not.toHaveBeenCalled()
    expect(redeemCard).not.toHaveBeenCalled()
  })
})
