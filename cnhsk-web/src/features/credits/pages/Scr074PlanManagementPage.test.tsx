import { act, fireEvent, render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import type { RoleCode } from '@/features/auth/types'
import { ApiError } from '@/shared/api'
import { AuthContext, type AuthContextValue } from '@/shared/auth'

import { listPlans, updatePlan } from '../api/plans'
import { creditsPlans } from '../test-fixtures'
import type { PlanView } from '../types'
import Scr074PlanManagementPage from './Scr074PlanManagementPage'

vi.mock('../api/plans', () => ({ listPlans: vi.fn(), updatePlan: vi.fn() }))

function element(id: number | null = 7, roles: RoleCode[] = ['ADMIN']) {
  const auth: AuthContextValue = {
    status: id === null ? 'guest' : 'authenticated',
    user:
      id === null
        ? null
        : {
            id,
            roles,
            email: 'credits@example.test',
            displayName: 'Quản trị mẫu',
            emailVerified: false,
          },
    error: null,
    reloadCurrentUser: async () => undefined,
    setAuthenticatedUser: () => undefined,
    signOut: async () => undefined,
  }
  return (
    <AuthContext.Provider value={auth}>
      <MemoryRouter>
        <Scr074PlanManagementPage />
      </MemoryRouter>
    </AuthContext.Provider>
  )
}
function renderPage(id: number | null = 7, roles: RoleCode[] = ['ADMIN']) {
  return render(element(id, roles))
}
async function form(code = 'PREMIUM') {
  return within(await screen.findByRole('form', { name: `Cấu hình ${code}` }))
}
function change(input: HTMLElement, value: string) {
  fireEvent.change(input, { target: { value } })
}
async function save(code = 'PREMIUM') {
  const fields = await form(code)
  await userEvent.click(fields.getByRole('button', { name: `Lưu ${code}` }))
  await userEvent.click(fields.getByRole('button', { name: 'Xác nhận lưu' }))
}

beforeEach(() => {
  vi.resetAllMocks()
  const plans = structuredClone(creditsPlans)
  vi.mocked(listPlans).mockImplementation(async () => structuredClone(plans))
  vi.mocked(updatePlan).mockImplementation(async (code, body) => {
    const index = plans.findIndex((plan) => plan.code === code)
    const result = { ...plans[index]!, ...body, exists: true }
    plans[index] = result
    return structuredClone(result)
  })
})
afterEach(() => {
  vi.restoreAllMocks()
  vi.unstubAllGlobals()
})

describe('SCR-074 plan management (FR-027, AT-25)', () => {
  it('AT25_displays three codes, server active-user counts, FREE quota and read-only PREMIUM_PLUS', async () => {
    renderPage()
    await form()
    for (const plan of creditsPlans) {
      const section = within(screen.getByRole('region', { name: plan.code }))
      expect(section.getByRole('heading', { name: plan.code })).toBeVisible()
      expect(section.getByText(`Số người đang dùng: ${plan.activeUsers}`)).toBeVisible()
    }
    const plus = within(screen.getByRole('region', { name: 'PREMIUM_PLUS' }))
    expect(plus.getByText('Chưa hỗ trợ đợt này')).toBeVisible()
    expect(plus.queryByRole('button')).not.toBeInTheDocument()
    expect(plus.queryByRole('textbox')).not.toBeInTheDocument()
    expect(screen.getByText('10 lượt/tính năng/tháng')).toBeVisible()
    expect(
      screen.queryByRole('button', { name: /xoá|xóa|mua|thanh toán/i }),
    ).not.toBeInTheDocument()
  })

  it('AT25_PREMIUM has all approved fields, four feature limits, list-price note and selling state', async () => {
    renderPage()
    const fields = await form()
    expect(fields.getByRole('textbox', { name: 'Tên gói' })).toHaveValue('PREMIUM mẫu')
    expect(fields.getByRole('textbox', { name: 'Mô tả' })).toHaveValue('Cấu hình mẫu')
    expect(fields.getByRole('spinbutton', { name: 'Số ngày hiệu lực' })).toHaveValue(30)
    expect(fields.getByRole('textbox', { name: 'Giá niêm yết' })).toHaveValue('199000.00')
    expect(fields.getByText('giá niêm yết, không phải tiền đã thu')).toBeVisible()
    for (const [name, value] of [
      ['Tạo câu AI', 100],
      ['Dịch', 50],
      ['Trợ lý', 0],
      ['Chấm bài', 10],
    ] as const)
      expect(fields.getByRole('spinbutton', { name })).toHaveValue(value)
    expect(fields.getByRole('checkbox', { name: 'Đang bán' })).toBeChecked()
    expect(fields.getByText('Ngừng bán không thu hồi gói đang dùng.')).toBeVisible()
  })

  it('AT25_FREE saves only name and description through inline confirmation', async () => {
    const confirm = vi.spyOn(window, 'confirm')
    renderPage()
    const fields = await form('FREE')
    change(fields.getByRole('textbox', { name: 'Tên gói' }), '  Miễn phí mới  ')
    change(fields.getByRole('textbox', { name: 'Mô tả' }), '  Mô tả tự soạn  ')
    expect(fields.queryByRole('spinbutton')).not.toBeInTheDocument()
    expect(fields.queryByRole('checkbox')).not.toBeInTheDocument()
    await userEvent.click(fields.getByRole('button', { name: 'Lưu FREE' }))
    const confirmation = fields.getByRole('region', { name: 'Xác nhận lưu FREE' })
    expect(confirmation.closest('form')).toBe(screen.getByRole('form', { name: 'Cấu hình FREE' }))
    expect(within(confirmation).getByText('Miễn phí mới')).toBeVisible()
    expect(updatePlan).not.toHaveBeenCalled()
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    await userEvent.click(fields.getByRole('button', { name: 'Huỷ lưu' }))
    expect(fields.getByRole('textbox', { name: 'Tên gói' })).toHaveValue('  Miễn phí mới  ')
    expect(fields.getByRole('button', { name: 'Lưu FREE' })).toHaveFocus()
    await save('FREE')
    expect(await screen.findByText('Đã lưu gói FREE: Miễn phí mới.')).toBeVisible()
    expect(updatePlan).toHaveBeenCalledWith('FREE', {
      name: 'Miễn phí mới',
      description: 'Mô tả tự soạn',
    })
    expect(confirm).not.toHaveBeenCalled()
  })

  it('Q_T04_03_preserves partial benefits for price-only edits and sends the decimal string exactly', async () => {
    renderPage()
    const fields = await form()
    change(fields.getByRole('textbox', { name: 'Giá niêm yết' }), '1234567890.12')
    await save()
    await screen.findByText('Đã lưu gói PREMIUM: PREMIUM mẫu.')
    const [code, body] = vi.mocked(updatePlan).mock.calls[0]!
    expect(code).toBe('PREMIUM')
    expect(body.price).toBe('1234567890.12')
    expect(typeof body.price).toBe('string')
    expect(body.benefits).toEqual(creditsPlans[1]!.benefits)
    expect(body.benefits).not.toHaveProperty('ASSISTANT')
    expect(body.benefits).not.toBe(creditsPlans[1]!.benefits)
  })

  it('Q_T04_03_builds all four keys only when a feature value changes', async () => {
    renderPage()
    const fields = await form()
    change(fields.getByRole('spinbutton', { name: 'Tạo câu AI' }), '101')
    await save()
    expect(updatePlan).toHaveBeenCalledWith(
      'PREMIUM',
      expect.objectContaining({
        benefits: { AI_GENERATE: 101, TRANSLATE: 50, ASSISTANT: 0, GRADING: 10 },
      }),
    )
  })

  it('Q_T04_03_does not add an absent zero key after editing and restoring its displayed value', async () => {
    renderPage()
    const fields = await form()
    change(fields.getByRole('spinbutton', { name: 'Trợ lý' }), '1')
    change(fields.getByRole('spinbutton', { name: 'Trợ lý' }), '0')
    await save()
    expect(vi.mocked(updatePlan).mock.calls[0]![1].benefits).toEqual(creditsPlans[1]!.benefits)
  })

  it.each(['0', '12', '12.5', '12.50'])(
    'FR016_normalizes valid decimal %s using string operations',
    async (price) => {
      renderPage()
      const fields = await form()
      change(fields.getByRole('textbox', { name: 'Giá niêm yết' }), price)
      await save()
      const [whole, fraction = ''] = price.split('.')
      expect(vi.mocked(updatePlan).mock.calls[0]![1].price).toBe(
        `${whole}.${fraction.padEnd(2, '0')}`,
      )
    },
  )

  it.each(['', '-1', '1.999', '1,50', '10000000000', '1e3'])(
    'FR016_rejects invalid price %s before PUT',
    async (price) => {
      renderPage()
      const fields = await form()
      change(fields.getByRole('textbox', { name: 'Giá niêm yết' }), price)
      await userEvent.click(fields.getByRole('button', { name: 'Lưu PREMIUM' }))
      expect(fields.getByRole('textbox', { name: 'Giá niêm yết' })).toHaveAttribute(
        'aria-invalid',
        'true',
      )
      expect(updatePlan).not.toHaveBeenCalled()
    },
  )

  it.each(['0', '366', '1.5', ''])('FR016_rejects invalid duration %s', async (days) => {
    renderPage()
    const fields = await form()
    change(fields.getByRole('spinbutton', { name: 'Số ngày hiệu lực' }), days)
    await userEvent.click(fields.getByRole('button', { name: 'Lưu PREMIUM' }))
    expect(fields.getByRole('spinbutton', { name: 'Số ngày hiệu lực' })).toHaveAttribute(
      'aria-invalid',
      'true',
    )
    expect(updatePlan).not.toHaveBeenCalled()
  })

  it.each(['-1', '10001', '1.5', ''])('FR016_rejects invalid feature quota %s', async (quota) => {
    renderPage()
    const fields = await form()
    change(fields.getByRole('spinbutton', { name: 'Dịch' }), quota)
    await userEvent.click(fields.getByRole('button', { name: 'Lưu PREMIUM' }))
    expect(fields.getByRole('spinbutton', { name: 'Dịch' })).toHaveAttribute('aria-invalid', 'true')
    expect(updatePlan).not.toHaveBeenCalled()
  })

  it('FR016_validates name and description without truncating input', async () => {
    renderPage()
    const fields = await form('FREE')
    change(fields.getByRole('textbox', { name: 'Tên gói' }), ' ')
    await userEvent.click(fields.getByRole('button', { name: 'Lưu FREE' }))
    expect(fields.getByRole('textbox', { name: 'Tên gói' })).toHaveFocus()
    change(fields.getByRole('textbox', { name: 'Tên gói' }), 'x'.repeat(101))
    await userEvent.click(fields.getByRole('button', { name: 'Lưu FREE' }))
    expect(fields.getByRole('textbox', { name: 'Tên gói' })).toHaveValue('x'.repeat(101))
    change(fields.getByRole('textbox', { name: 'Tên gói' }), 'Gói tự soạn')
    change(fields.getByRole('textbox', { name: 'Mô tả' }), 'x'.repeat(2001))
    await userEvent.click(fields.getByRole('button', { name: 'Lưu FREE' }))
    expect(fields.getByRole('textbox', { name: 'Mô tả' })).toHaveAttribute('aria-invalid', 'true')
    expect(updatePlan).not.toHaveBeenCalled()
  })

  it('FR016_creates missing PREMIUM with explicit values and all four quota keys', async () => {
    const plans = structuredClone(creditsPlans)
    plans[1] = { code: 'PREMIUM', exists: false, activeUsers: 0 }
    vi.mocked(listPlans).mockResolvedValue(plans)
    renderPage()
    const fields = await form()
    expect(fields.getByRole('textbox', { name: 'Giá niêm yết' })).toHaveValue('')
    for (const name of ['Tạo câu AI', 'Dịch', 'Trợ lý', 'Chấm bài'])
      expect(fields.getByRole('spinbutton', { name })).toHaveValue(null)
    change(fields.getByRole('textbox', { name: 'Tên gói' }), 'PREMIUM tự soạn')
    change(fields.getByRole('textbox', { name: 'Giá niêm yết' }), '0')
    change(fields.getByRole('spinbutton', { name: 'Số ngày hiệu lực' }), '365')
    for (const name of ['Tạo câu AI', 'Dịch', 'Trợ lý', 'Chấm bài'])
      change(fields.getByRole('spinbutton', { name }), name === 'Dịch' ? '10000' : '0')
    await userEvent.click(fields.getByRole('checkbox', { name: 'Đang bán' }))
    await save()
    expect(updatePlan).toHaveBeenCalledWith('PREMIUM', {
      name: 'PREMIUM tự soạn',
      description: null,
      price: '0.00',
      durationDays: 365,
      benefits: { AI_GENERATE: 0, TRANSLATE: 10000, ASSISTANT: 0, GRADING: 0 },
      isActive: true,
    })
  })

  it('AT25_PLAN_IN_USE explains the conflict directly on every quota field and preserves draft', async () => {
    vi.mocked(updatePlan).mockRejectedValueOnce(
      new ApiError({
        status: 409,
        errorCode: 'PLAN_IN_USE',
        message: 'Internal details',
        requestId: 'req-plan',
      }),
    )
    renderPage()
    const fields = await form()
    change(fields.getByRole('spinbutton', { name: 'Tạo câu AI' }), '101')
    await save()
    expect(await screen.findByRole('alert')).toHaveTextContent('Chưa lưu thay đổi hạn mức')
    for (const name of ['Tạo câu AI', 'Dịch', 'Trợ lý', 'Chấm bài']) {
      expect(fields.getByRole('spinbutton', { name })).toHaveAttribute('aria-invalid', 'true')
      expect(fields.getByRole('spinbutton', { name })).toHaveAccessibleDescription(
        /Không thể đổi hạn mức khi còn người đang dùng PREMIUM/,
      )
    }
    expect(fields.getByRole('spinbutton', { name: 'Tạo câu AI' })).toHaveValue(101)
    expect(screen.queryByText('Internal details')).not.toBeInTheDocument()
    expect(screen.queryByText(/Đã lưu gói/)).not.toBeInTheDocument()
  })

  it('FR027_shows the complete PREMIUM confirmation and keeps stopping sales separate from existing rights', async () => {
    renderPage()
    const fields = await form()
    await userEvent.click(fields.getByRole('checkbox', { name: 'Đang bán' }))
    await userEvent.click(fields.getByRole('button', { name: 'Lưu PREMIUM' }))
    const confirmation = within(fields.getByRole('region', { name: 'Xác nhận lưu PREMIUM' }))
    expect(confirmation.getByText('199000.00')).toBeVisible()
    expect(confirmation.getByText('30')).toBeVisible()
    expect(confirmation.getByText('Không')).toBeVisible()
    expect(confirmation.getByText('Trợ lý')).toBeVisible()
    expect(updatePlan).not.toHaveBeenCalled()
    await userEvent.click(confirmation.getByRole('button', { name: 'Xác nhận lưu' }))
    expect(vi.mocked(updatePlan).mock.calls[0]![1].isActive).toBe(false)
  })

  it('FR027_blocks duplicate PUTs while saving', async () => {
    let resolve!: (result: PlanView) => void
    vi.mocked(updatePlan).mockReturnValueOnce(
      new Promise((done) => {
        resolve = done
      }),
    )
    renderPage()
    const fields = await form()
    await userEvent.click(fields.getByRole('button', { name: 'Lưu PREMIUM' }))
    const confirm = fields.getByRole('button', { name: 'Xác nhận lưu' })
    fireEvent.click(confirm)
    fireEvent.click(confirm)
    fireEvent.submit(screen.getByRole('form', { name: 'Cấu hình PREMIUM' }))
    expect(updatePlan).toHaveBeenCalledTimes(1)
    expect(fields.getByRole('textbox', { name: 'Giá niêm yết' })).toBeDisabled()
    await act(async () => resolve(structuredClone(creditsPlans[1]!)))
    expect(screen.getByText('Đã lưu gói PREMIUM: PREMIUM mẫu.')).toBeVisible()
  })

  it('FR027_keeps successful save feedback when reloading the list fails', async () => {
    vi.mocked(listPlans)
      .mockResolvedValueOnce(structuredClone(creditsPlans))
      .mockRejectedValueOnce(new Error('Internal reload details'))
    renderPage()
    await form()
    await save()
    expect(await screen.findByRole('heading', { name: 'Chưa tải được gói dịch vụ' })).toBeVisible()
    expect(screen.getByText('Đã lưu gói PREMIUM: PREMIUM mẫu.')).toBeVisible()
    expect(screen.queryByRole('heading', { name: 'Chưa lưu được PREMIUM' })).not.toBeInTheDocument()
  })

  it.each(['PLAN_NOT_AVAILABLE', 'VALIDATION_FAILED', 'FORBIDDEN'])(
    'FR027_handles %s without losing form values',
    async (errorCode) => {
      vi.mocked(updatePlan).mockRejectedValueOnce(
        new ApiError({
          status: errorCode === 'FORBIDDEN' ? 403 : 422,
          errorCode,
          message: 'Internal details',
        }),
      )
      renderPage()
      const fields = await form()
      await save()
      expect(await screen.findByRole('alert')).toBeVisible()
      expect(fields.getByRole('textbox', { name: 'Giá niêm yết' })).toHaveValue('199000.00')
      expect(screen.queryByText('Internal details')).not.toBeInTheDocument()
    },
  )

  it('FR027_supports loading, invalid/empty response, error and retry without inventing plans', async () => {
    let resolve!: (result: PlanView[]) => void
    vi.mocked(listPlans)
      .mockResolvedValueOnce([])
      .mockImplementationOnce(
        () =>
          new Promise((done) => {
            resolve = done
          }),
      )
    renderPage()
    expect(screen.getByRole('status', { name: 'Đang tải gói dịch vụ' })).toBeVisible()
    expect(await screen.findByRole('heading', { name: 'Chưa tải được gói dịch vụ' })).toBeVisible()
    expect(screen.queryByRole('form')).not.toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'Thử lại' }))
    expect(screen.getByRole('status', { name: 'Đang tải gói dịch vụ' })).toBeVisible()
    await act(async () => resolve(structuredClone(creditsPlans)))
    await form()
    expect(listPlans).toHaveBeenCalledTimes(2)
  })

  it('FR027_ignores a late PUT after unmount and aborts the list signal', async () => {
    let resolve!: (result: PlanView) => void
    vi.mocked(updatePlan).mockReturnValueOnce(
      new Promise((done) => {
        resolve = done
      }),
    )
    const view = renderPage()
    await form()
    await save()
    const signal = vi.mocked(listPlans).mock.calls[0]![0]!
    const errors = vi.spyOn(console, 'error')
    view.unmount()
    expect(signal.aborted).toBe(true)
    await act(async () => resolve(structuredClone(creditsPlans[1]!)))
    expect(listPlans).toHaveBeenCalledTimes(1)
    expect(errors).not.toHaveBeenCalled()
  })

  it('FR027_old administrator results cannot affect the newly mounted account', async () => {
    let resolve!: (result: PlanView) => void
    vi.mocked(updatePlan).mockReturnValueOnce(
      new Promise((done) => {
        resolve = done
      }),
    )
    const view = renderPage()
    await form()
    await save()
    view.rerender(element(8))
    await form()
    await act(async () => resolve({ ...creditsPlans[1]!, name: 'Old account result' }))
    expect(listPlans).toHaveBeenCalledTimes(2)
    expect(screen.queryByText(/Old account result/)).not.toBeInTheDocument()
  })

  it.each([null, 'ACCOUNTANT', 'LEARNER'] as const)(
    'FR001_does not call plan APIs for %s',
    (role) => {
      renderPage(role === null ? null : 7, role === null ? [] : [role])
      expect(listPlans).not.toHaveBeenCalled()
      expect(updatePlan).not.toHaveBeenCalled()
    },
  )

  it('FR027_uses mock APIs, leaves fixtures intact and escapes authored descriptions', async () => {
    const original = JSON.stringify(creditsPlans)
    const fetchMock = vi.fn()
    vi.stubGlobal('fetch', fetchMock)
    renderPage()
    const fields = await form('FREE')
    change(fields.getByRole('textbox', { name: 'Mô tả' }), '<img src=x onerror=bad()>')
    await userEvent.click(fields.getByRole('button', { name: 'Lưu FREE' }))
    expect(
      within(fields.getByRole('region', { name: 'Xác nhận lưu FREE' })).getByText(
        '<img src=x onerror=bad()>',
      ),
    ).toBeVisible()
    expect(
      screen.getByRole('form', { name: 'Cấu hình FREE' }).querySelector('img,script'),
    ).toBeNull()
    expect(fetchMock).not.toHaveBeenCalled()
    expect(JSON.stringify(creditsPlans)).toBe(original)
  })
})
