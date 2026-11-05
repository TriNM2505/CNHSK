import { act, fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { ApiError } from '@/shared/api'
import { AuthContext, type AuthContextValue } from '@/shared/auth'
import type { RoleCode } from '@/features/auth/types'
import { downloadBlob } from '@/shared/lib/downloadBlob'

import { createCardBatch, listCardBatches, voidCardBatch } from '../api/cardBatches'
import { creditsBatchPage, creditsCreatedBatch } from '../test-fixtures'
import type { BatchPage, CreatedBatch, VoidResult } from '../types'
import Scr073CardBatchPage from './Scr073CardBatchPage'

vi.mock('../api/cardBatches', () => ({
  createCardBatch: vi.fn(),
  listCardBatches: vi.fn(),
  voidCardBatch: vi.fn(),
}))
vi.mock('@/shared/lib/downloadBlob', () => ({ downloadBlob: vi.fn() }))

function pageElement(id: number | null = 7, roles: RoleCode[] = ['ADMIN']) {
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
        <Scr073CardBatchPage />
      </MemoryRouter>
    </AuthContext.Provider>
  )
}

function renderPage(id: number | null = 7, roles: RoleCode[] = ['ADMIN']) {
  return render(pageElement(id, roles))
}

function field(name: string) {
  if (name === 'Loại thẻ' || name === 'Kênh phát hành')
    return screen.getByRole('combobox', { name })
  if (['Số lượng mã', 'Giá trị điểm mỗi mã', 'Số ngày hiệu lực'].includes(name))
    return screen.getByRole('spinbutton', { name })
  if (name !== 'Hạn dùng (giờ Việt Nam)') return screen.getByRole('textbox', { name })
  return screen.getByLabelText(name, { exact: true })
}

function change(name: string, value: string) {
  fireEvent.change(field(name), { target: { value } })
}

function fillCredit(channel = 'DIRECT') {
  change('Loại thẻ', 'CREDIT')
  change('Kênh phát hành', channel)
  change('Số lượng mã', '3')
  change('Giá trị điểm mỗi mã', '100')
}

async function table() {
  return within(await screen.findByRole('table', { name: 'Lô mã thẻ' }))
}

async function prepare() {
  await userEvent.click(screen.getByRole('button', { name: 'Phát hành lô mã' }))
}

async function issue() {
  await prepare()
  await userEvent.click(screen.getByRole('button', { name: 'Xác nhận phát hành' }))
  return screen.findByRole('list', { name: 'Mã vừa phát hành' })
}

function bytesOf(blob: Blob): Promise<Uint8Array> {
  return new Promise((resolve, reject) => {
    const reader = new FileReader()
    reader.onload = () => resolve(new Uint8Array(reader.result as ArrayBuffer))
    reader.onerror = reject
    reader.readAsArrayBuffer(blob)
  })
}

function deferred<T>() {
  let resolve!: (value: T) => void
  const promise = new Promise<T>((done) => {
    resolve = done
  })
  return { promise, resolve }
}

beforeEach(() => {
  vi.resetAllMocks()
  vi.spyOn(Date, 'now').mockReturnValue(Date.parse('2026-11-06T00:00:00Z'))
  vi.mocked(listCardBatches).mockResolvedValue(structuredClone(creditsBatchPage))
  vi.mocked(createCardBatch).mockResolvedValue(structuredClone(creditsCreatedBatch))
  vi.mocked(voidCardBatch).mockResolvedValue({ batchId: 'B20261102-DEMO02', voided: 7 })
})

afterEach(() => {
  vi.restoreAllMocks()
  vi.unstubAllGlobals()
})

describe('SCR-073 card batches (FR-026, AT-24)', () => {
  it('AT24_startsWithExplicitEmptyChoices and no generated codes or checkout', async () => {
    renderPage()
    await table()
    expect(field('Loại thẻ')).toHaveValue('')
    expect(field('Kênh phát hành')).toHaveValue('')
    expect(field('Số lượng mã')).toHaveValue(null)
    expect(screen.queryByRole('list', { name: 'Mã vừa phát hành' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Tải CSV' })).not.toBeInTheDocument()
    expect(
      screen.queryByRole('button', { name: /mua|thanh toán|xoá|xóa|sửa/i }),
    ).not.toBeInTheDocument()
    expect(screen.getAllByRole('combobox')).toHaveLength(2)
    await prepare()
    expect(field('Loại thẻ')).toHaveAttribute('aria-invalid', 'true')
    expect(field('Loại thẻ')).toHaveFocus()
    expect(createCardBatch).not.toHaveBeenCalled()
  })

  it('AT24_channelFields are conditional, required and discarded when switching channels', async () => {
    renderPage()
    await table()
    fillCredit('PARTNER_BATCH')
    expect(field('Mã đối tác')).toBeVisible()
    expect(
      screen.queryByRole('textbox', { name: 'Thông tin người nhận quà' }),
    ).not.toBeInTheDocument()
    await prepare()
    expect(field('Mã đối tác')).toHaveAttribute('aria-invalid', 'true')
    change('Mã đối tác', '  PARTNER_DEMO  ')
    change('Kênh phát hành', 'ECOSYSTEM_GIFT')
    expect(screen.queryByRole('textbox', { name: 'Mã đối tác' })).not.toBeInTheDocument()
    await prepare()
    expect(field('Thông tin người nhận quà')).toHaveAttribute('aria-invalid', 'true')
    change('Thông tin người nhận quà', 'Nhóm học thử mẫu')
    change('Kênh phát hành', 'PARTNER_BATCH')
    expect(field('Mã đối tác')).toHaveValue('')
    change('Mã đối tác', '  PARTNER_DEMO  ')
    await issue()
    expect(createCardBatch).toHaveBeenCalledWith({
      cardType: 'CREDIT',
      channel: 'PARTNER_BATCH',
      quantity: 3,
      value: 100,
      partnerCode: 'PARTNER_DEMO',
    })
  })

  it('AT24_giftPayload trims recipient and campaign and omits partner data', async () => {
    renderPage()
    await table()
    fillCredit('ECOSYSTEM_GIFT')
    change('Thông tin người nhận quà', '  Nhóm học thử mẫu  ')
    change('Chiến dịch', '  tang-thu  ')
    await issue()
    expect(createCardBatch).toHaveBeenCalledWith({
      cardType: 'CREDIT',
      channel: 'ECOSYSTEM_GIFT',
      quantity: 3,
      value: 100,
      issuedTo: 'Nhóm học thử mẫu',
      campaign: 'tang-thu',
    })
  })

  it('AT24_cardType switches between credit value and PREMIUM days without stale payload fields', async () => {
    renderPage()
    await table()
    fillCredit()
    change('Loại thẻ', 'SUBSCRIPTION')
    expect(
      screen.queryByRole('spinbutton', { name: 'Giá trị điểm mỗi mã' }),
    ).not.toBeInTheDocument()
    expect(field('Số ngày hiệu lực')).toHaveValue(null)
    await prepare()
    expect(field('Số ngày hiệu lực')).toHaveAttribute('aria-invalid', 'true')
    change('Số ngày hiệu lực', '30')
    await issue()
    expect(createCardBatch).toHaveBeenCalledWith({
      cardType: 'SUBSCRIPTION',
      channel: 'DIRECT',
      quantity: 3,
      planDays: 30,
    })
  })

  it.each(['101', '0', '-1', '1.5', ''])(
    'AT24_rejects invalid quantity %s before POST',
    async (value) => {
      renderPage()
      await table()
      fillCredit()
      change('Số lượng mã', value)
      await prepare()
      expect(screen.getByText('Số lượng mã phải là số nguyên từ 1 đến 100.')).toBeVisible()
      expect(field('Số lượng mã')).toHaveAttribute('aria-invalid', 'true')
      expect(field('Số lượng mã')).toHaveFocus()
      expect(createCardBatch).not.toHaveBeenCalled()
    },
  )

  it.each(['1', '100'])('AT24_accepts the quantity boundary %s', async (value) => {
    renderPage()
    await table()
    fillCredit()
    change('Số lượng mã', value)
    await issue()
    expect(createCardBatch).toHaveBeenCalledWith(
      expect.objectContaining({ quantity: Number(value) }),
    )
  })

  it.each([
    { kind: 'CREDIT', label: 'Giá trị điểm mỗi mã', value: '10001' },
    { kind: 'CREDIT', label: 'Giá trị điểm mỗi mã', value: '0' },
    { kind: 'CREDIT', label: 'Giá trị điểm mỗi mã', value: '1.5' },
    { kind: 'SUBSCRIPTION', label: 'Số ngày hiệu lực', value: '366' },
    { kind: 'SUBSCRIPTION', label: 'Số ngày hiệu lực', value: '0' },
    { kind: 'SUBSCRIPTION', label: 'Số ngày hiệu lực', value: '1.5' },
  ])('FR005_rejects $kind $label=$value', async ({ kind, label, value }) => {
    renderPage()
    await table()
    fillCredit()
    change('Loại thẻ', kind)
    change(label, value)
    await prepare()
    expect(field(label)).toHaveAttribute('aria-invalid', 'true')
    expect(createCardBatch).not.toHaveBeenCalled()
  })

  it.each([
    { kind: 'CREDIT', label: 'Giá trị điểm mỗi mã', value: '1', payload: { value: 1 } },
    { kind: 'CREDIT', label: 'Giá trị điểm mỗi mã', value: '10000', payload: { value: 10000 } },
    { kind: 'SUBSCRIPTION', label: 'Số ngày hiệu lực', value: '1', payload: { planDays: 1 } },
    { kind: 'SUBSCRIPTION', label: 'Số ngày hiệu lực', value: '365', payload: { planDays: 365 } },
  ])('FR005_accepts $kind boundary $value', async ({ kind, label, value, payload }) => {
    renderPage()
    await table()
    fillCredit()
    change('Loại thẻ', kind)
    change(label, value)
    await issue()
    expect(createCardBatch).toHaveBeenCalledWith({
      cardType: kind,
      channel: 'DIRECT',
      quantity: 3,
      ...payload,
    })
  })

  it.each([
    { channel: 'PARTNER_BATCH', label: 'Mã đối tác', length: 51 },
    { channel: 'ECOSYSTEM_GIFT', label: 'Thông tin người nhận quà', length: 256 },
    { channel: 'DIRECT', label: 'Chiến dịch', length: 51 },
  ])('FR005_rejects overlong $label without truncating', async ({ channel, label, length }) => {
    renderPage()
    await table()
    fillCredit(channel)
    change(label, 'x'.repeat(length))
    await prepare()
    expect(field(label)).toHaveValue('x'.repeat(length))
    expect(field(label)).toHaveAttribute('aria-invalid', 'true')
    expect(createCardBatch).not.toHaveBeenCalled()
  })

  it('FR005_rejects past expiry and converts Vietnam input to UTC in confirmation and POST', async () => {
    renderPage()
    await table()
    fillCredit()
    change('Hạn dùng (giờ Việt Nam)', '2026-11-05T07:00')
    await prepare()
    expect(field('Hạn dùng (giờ Việt Nam)')).toHaveAttribute('aria-invalid', 'true')
    expect(createCardBatch).not.toHaveBeenCalled()
    change('Hạn dùng (giờ Việt Nam)', '2026-12-01T00:00')
    await prepare()
    const confirmation = within(screen.getByRole('region', { name: 'Xác nhận phát hành' }))
    expect(confirmation.getByText('00:00 01/12/2026')).toBeVisible()
    await userEvent.click(confirmation.getByRole('button', { name: 'Xác nhận phát hành' }))
    await screen.findByRole('list', { name: 'Mã vừa phát hành' })
    expect(createCardBatch).toHaveBeenCalledWith(
      expect.objectContaining({ expiresAt: '2026-11-30T17:00:00.000Z' }),
    )
  })

  it('AT24_inlineCreateConfirmation shows actual values and cancellation retains the draft', async () => {
    renderPage()
    await table()
    fillCredit()
    change('Chiến dịch', 'chien-dich-mau')
    const confirmSpy = vi.spyOn(window, 'confirm')
    await prepare()
    const confirmation = screen.getByRole('region', { name: 'Xác nhận phát hành' })
    expect(
      screen.getByRole('heading', { name: 'Lô mã thẻ', level: 1 }).parentElement?.closest('body'),
    ).toContainElement(confirmation)
    expect(confirmation.closest('.mx-auto')).toContainElement(
      screen.getByRole('form', { name: 'Phát hành lô mã' }),
    )
    expect(within(confirmation).getByText('Trực tiếp')).toBeVisible()
    expect(within(confirmation).getByText('Điểm (CREDIT)')).toBeVisible()
    expect(within(confirmation).getByText('3')).toBeVisible()
    expect(within(confirmation).getByText('100')).toBeVisible()
    expect(within(confirmation).getByText('chien-dich-mau')).toBeVisible()
    expect(within(confirmation).getByText('Không có hạn')).toBeVisible()
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    expect(createCardBatch).not.toHaveBeenCalled()
    await userEvent.click(screen.getByRole('button', { name: 'Huỷ phát hành' }))
    expect(screen.getByRole('button', { name: 'Phát hành lô mã' })).toHaveFocus()
    expect(field('Giá trị điểm mỗi mã')).toHaveValue(100)
    expect(field('Chiến dịch')).toHaveValue('chien-dich-mau')
    expect(confirmSpy).not.toHaveBeenCalled()
  })

  it('AT24_guards duplicate creation and blocks conflicting actions while pending', async () => {
    const response = deferred<CreatedBatch>()
    vi.mocked(createCardBatch).mockReturnValueOnce(response.promise)
    renderPage()
    await table()
    fillCredit()
    await prepare()
    fireEvent.click(screen.getByRole('button', { name: 'Xác nhận phát hành' }))
    fireEvent.submit(screen.getByRole('form', { name: 'Phát hành lô mã' }))
    expect(createCardBatch).toHaveBeenCalledTimes(1)
    expect(field('Số lượng mã')).toBeDisabled()
    expect(screen.getByRole('button', { name: 'Phát hành lô mã' })).toBeDisabled()
    const data = await table()
    for (const button of data.getAllByRole('button')) expect(button).toBeDisabled()
    await act(async () => response.resolve(structuredClone(creditsCreatedBatch)))
    expect(await screen.findByRole('list', { name: 'Mã vừa phát hành' })).toBeVisible()
    expect(createCardBatch).toHaveBeenCalledTimes(1)
  })

  it('AT24_displays exact one-time codes and exports only batchId/code as UTF-8 CRLF without BOM', async () => {
    renderPage()
    await table()
    fillCredit('ECOSYSTEM_GIFT')
    change('Thông tin người nhận quà', 'PRIVATE_DEMO_RECIPIENT')
    change('Chiến dịch', 'PRIVATE_DEMO_CAMPAIGN')
    const codes = await issue()
    expect(
      within(codes)
        .getAllByRole('listitem')
        .map((item) => item.textContent),
    ).toEqual(creditsCreatedBatch.codes)
    expect(screen.getByText('Mã chỉ hiện một lần — tải ngay')).toBeVisible()
    await userEvent.click(screen.getByRole('button', { name: 'Tải CSV' }))
    const [blob, fileName] = vi.mocked(downloadBlob).mock.calls[0]!
    expect(fileName).toBe(`card-batch-${creditsCreatedBatch.batchId}.csv`)
    expect(blob.type).toBe('text/csv;charset=utf-8')
    const bytes = await bytesOf(blob)
    expect(Array.from(bytes.slice(0, 3))).not.toEqual([0xef, 0xbb, 0xbf])
    const csv = new TextDecoder('utf-8', { fatal: true }).decode(bytes)
    expect(csv).toBe(
      'batchId,code\r\n' +
        creditsCreatedBatch.codes
          .map((code) => `${creditsCreatedBatch.batchId},${code}\r\n`)
          .join(''),
    )
    expect(csv.charCodeAt(0)).not.toBe(0xfeff)
    expect(csv).not.toMatch(/PRIVATE_DEMO|issuedTo|campaign|partnerCode/)
    for (const row of csv.trimEnd().split('\r\n'))
      for (const cell of row.split(',')) expect(cell).not.toMatch(/^[=+\-@]/)
    expect(screen.getByRole('list', { name: 'Mã vừa phát hành' })).toBeVisible()
    await userEvent.click(screen.getByRole('button', { name: 'Tải CSV' }))
    expect(downloadBlob).toHaveBeenCalledTimes(2)
  })

  it('AT24_preserves codes on CSV failure and allows another download', async () => {
    vi.mocked(downloadBlob).mockImplementationOnce(() => {
      throw new Error('Internal download details')
    })
    renderPage()
    await table()
    fillCredit()
    await issue()
    await userEvent.click(screen.getByRole('button', { name: 'Tải CSV' }))
    expect(screen.getByRole('alert')).toHaveTextContent('Chưa tải được CSV')
    expect(screen.queryByText('Internal download details')).not.toBeInTheDocument()
    expect(screen.getByRole('list', { name: 'Mã vừa phát hành' })).toBeVisible()
    await userEvent.click(screen.getByRole('button', { name: 'Tải CSV' }))
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
  })

  it('AT24_warns before replacing previous codes and only replaces after the next success', async () => {
    const next = { batchId: 'B20261106-DEMO05', codes: ['DEMO-ONLY-0004-USE0'] }
    const response = deferred<CreatedBatch>()
    vi.mocked(createCardBatch)
      .mockResolvedValueOnce(structuredClone(creditsCreatedBatch))
      .mockReturnValueOnce(response.promise)
    renderPage()
    await table()
    fillCredit()
    await issue()
    await prepare()
    expect(screen.getByText('Mã lô trước sẽ mất khi phát hành thành công lô mới.')).toBeVisible()
    await userEvent.click(screen.getByRole('button', { name: 'Huỷ phát hành' }))
    expect(screen.getByText(creditsCreatedBatch.codes[0]!)).toBeVisible()
    await prepare()
    await userEvent.click(screen.getByRole('button', { name: 'Xác nhận phát hành' }))
    expect(screen.getByText(creditsCreatedBatch.codes[0]!)).toBeVisible()
    await act(async () => response.resolve(next))
    expect(screen.getByText(next.codes[0]!)).toBeVisible()
    expect(screen.queryByText(creditsCreatedBatch.codes[0]!)).not.toBeInTheDocument()
  })

  it('AT24_loses codes on remount and never stores, logs or sends them through the URL', async () => {
    const storage = vi.spyOn(Storage.prototype, 'setItem')
    const logs = [vi.spyOn(console, 'log'), vi.spyOn(console, 'warn'), vi.spyOn(console, 'error')]
    const fetchMock = vi.fn()
    vi.stubGlobal('fetch', fetchMock)
    const view = renderPage()
    await table()
    fillCredit()
    await issue()
    const href = window.location.href
    expect(href).not.toContain(creditsCreatedBatch.codes[0]!)
    view.unmount()
    renderPage()
    await table()
    expect(screen.queryByRole('list', { name: 'Mã vừa phát hành' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Tải CSV' })).not.toBeInTheDocument()
    expect(storage).not.toHaveBeenCalled()
    for (const log of logs)
      for (const code of creditsCreatedBatch.codes)
        expect(JSON.stringify(log.mock.calls)).not.toContain(code)
    expect(fetchMock).not.toHaveBeenCalled()
    expect(createCardBatch).toHaveBeenCalledTimes(1)
    expect(window.location.href).toBe(href)
  })

  it('FR026_renders authoritative lifecycle totals and Vietnam timestamps without exposing raw codes in the table', async () => {
    renderPage()
    const data = await table()
    const cells = within(data.getAllByRole('row')[1]!).getAllByRole('cell')
    expect(cells.map((cell) => cell.textContent)).toEqual([
      'B20261103-DEMO03',
      'Điểm (CREDIT) · 50 điểm',
      'Trực tiếp · thu-nghiem',
      '2/5',
      '0',
      '3',
      '00:00 01/12/2026',
      'Không có hạn',
      'Không còn mã chưa dùng',
    ])
    expect(data.getAllByRole('row')).toHaveLength(4)
    expect(data.queryByRole('button', { name: 'Khoá lô B20261103-DEMO03' })).not.toBeInTheDocument()
    expect(data.getByRole('button', { name: 'Khoá lô B20261102-DEMO02' })).toBeEnabled()
    expect(data.queryByText(/DEMO-ONLY|code_hash|PRIVATE/)).not.toBeInTheDocument()
  })

  it('AT24_voidConfirmsInline with cancellation and keeps USED untouched', async () => {
    let current = structuredClone(creditsBatchPage)
    vi.mocked(listCardBatches).mockImplementation(async () => structuredClone(current))
    vi.mocked(voidCardBatch).mockImplementation(async (batchId) => {
      current = {
        ...current,
        items: current.items.map((batch) =>
          batch.batchId === batchId ? { ...batch, unused: 0, voided: 7 } : batch,
        ),
      }
      return { batchId, voided: 7 }
    })
    renderPage()
    let data = await table()
    await userEvent.click(data.getByRole('button', { name: 'Khoá lô B20261102-DEMO02' }))
    let confirmation = within(screen.getByRole('region', { name: 'Xác nhận khoá lô' }))
    expect(confirmation.getByText(/7 mã chưa dùng; 3 mã đã dùng được giữ nguyên/)).toBeVisible()
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    expect(voidCardBatch).not.toHaveBeenCalled()
    await userEvent.click(confirmation.getByRole('button', { name: 'Huỷ khoá lô' }))
    expect(data.getByRole('button', { name: 'Khoá lô B20261102-DEMO02' })).toHaveFocus()
    expect(voidCardBatch).not.toHaveBeenCalled()
    await userEvent.click(data.getByRole('button', { name: 'Khoá lô B20261102-DEMO02' }))
    confirmation = within(screen.getByRole('region', { name: 'Xác nhận khoá lô' }))
    await userEvent.click(confirmation.getByRole('button', { name: 'Xác nhận khoá lô' }))
    expect(await screen.findByText('Lô B20261102-DEMO02: đã khoá 7 mã chưa dùng.')).toBeVisible()
    data = await table()
    expect(data.queryByRole('button', { name: 'Khoá lô B20261102-DEMO02' })).not.toBeInTheDocument()
    const row = within(data.getAllByRole('row')[2]!)
    expect(row.getByText('3/10')).toBeVisible()
    expect(voidCardBatch).toHaveBeenCalledTimes(1)
    expect(voidCardBatch).toHaveBeenCalledWith('B20261102-DEMO02')
  })

  it('AT24_guards duplicate void requests and accepts a zero-voided result', async () => {
    const response = deferred<VoidResult>()
    vi.mocked(voidCardBatch).mockReturnValueOnce(response.promise)
    renderPage()
    const data = await table()
    await userEvent.click(data.getByRole('button', { name: 'Khoá lô B20261102-DEMO02' }))
    const confirm = screen.getByRole('button', { name: 'Xác nhận khoá lô' })
    fireEvent.click(confirm)
    fireEvent.click(confirm)
    expect(voidCardBatch).toHaveBeenCalledTimes(1)
    expect(field('Loại thẻ')).toBeDisabled()
    await act(async () => response.resolve({ batchId: 'B20261102-DEMO02', voided: 0 }))
    expect(screen.getByText('Lô B20261102-DEMO02: đã khoá 0 mã chưa dùng.')).toBeVisible()
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
  })

  it.each([
    {
      status: 422,
      errorCode: 'PLAN_NOT_AVAILABLE',
      message: 'Gói PREMIUM chưa có hoặc đang ngừng phát hành.',
    },
    { status: 422, errorCode: 'VALIDATION_FAILED', message: 'Thông tin lô chưa hợp lệ.' },
    {
      status: 403,
      errorCode: 'FORBIDDEN',
      message: 'Phiên đăng nhập hoặc quyền quản trị không còn hợp lệ.',
    },
    {
      status: 401,
      errorCode: 'UNAUTHENTICATED',
      message: 'Phiên đăng nhập hoặc quyền quản trị không còn hợp lệ.',
    },
  ])(
    'FR005_handles $errorCode without changing the draft or leaking internals',
    async ({ status, errorCode, message }) => {
      vi.mocked(createCardBatch).mockRejectedValueOnce(
        new ApiError({ status, errorCode, message: 'Internal details', requestId: 'req-batch' }),
      )
      renderPage()
      await table()
      fillCredit()
      await prepare()
      await userEvent.click(screen.getByRole('button', { name: 'Xác nhận phát hành' }))
      expect(await screen.findByRole('alert')).toHaveTextContent(message)
      expect(screen.getByRole('alert')).toHaveTextContent('req-batch')
      expect(field('Giá trị điểm mỗi mã')).toHaveValue(100)
      expect(screen.queryByText('Internal details')).not.toBeInTheDocument()
      expect(screen.queryByRole('list', { name: 'Mã vừa phát hành' })).not.toBeInTheDocument()
      expect(createCardBatch).toHaveBeenCalledTimes(1)
    },
  )

  it('FR026_networkFailure reloads the list, keeps earlier codes and never retries POST automatically', async () => {
    vi.mocked(createCardBatch)
      .mockResolvedValueOnce(structuredClone(creditsCreatedBatch))
      .mockRejectedValueOnce(new Error('Internal network details'))
    renderPage()
    await table()
    fillCredit()
    await issue()
    const callsBefore = vi.mocked(listCardBatches).mock.calls.length
    await prepare()
    await userEvent.click(screen.getByRole('button', { name: 'Xác nhận phát hành' }))
    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Không rõ lô đã được tạo chưa — kiểm tra bảng lô trước khi tạo lại',
    )
    await waitFor(() => expect(listCardBatches).toHaveBeenCalledTimes(callsBefore + 1))
    await table()
    expect(createCardBatch).toHaveBeenCalledTimes(2)
    expect(screen.getByText(creditsCreatedBatch.codes[0]!)).toBeVisible()
  })

  it('FR026_keeps committed codes available when the list reload fails', async () => {
    vi.mocked(listCardBatches)
      .mockResolvedValueOnce(structuredClone(creditsBatchPage))
      .mockRejectedValueOnce(new Error('Reload details'))
    renderPage()
    await table()
    fillCredit()
    await issue()
    expect(await screen.findByRole('heading', { name: 'Chưa tải được danh sách lô' })).toBeVisible()
    expect(screen.getByRole('button', { name: 'Tải CSV' })).toBeEnabled()
    expect(screen.getByText(creditsCreatedBatch.codes[0]!)).toBeVisible()
    expect(screen.queryByRole('heading', { name: 'Thông báo phát hành' })).not.toBeInTheDocument()
    expect(createCardBatch).toHaveBeenCalledTimes(1)
  })

  it('FR026_handles missing batches without optimistically changing counts', async () => {
    vi.mocked(voidCardBatch).mockRejectedValueOnce(
      new ApiError({ status: 404, errorCode: 'CARD_BATCH_NOT_FOUND', message: 'Internal details' }),
    )
    renderPage()
    const data = await table()
    await userEvent.click(data.getByRole('button', { name: 'Khoá lô B20261102-DEMO02' }))
    await userEvent.click(screen.getByRole('button', { name: 'Xác nhận khoá lô' }))
    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Không tìm thấy lô mã. Tải lại danh sách để kiểm tra.',
    )
    expect(data.getByRole('button', { name: 'Khoá lô B20261102-DEMO02' })).toBeEnabled()
    expect(data.getByText('3/10')).toBeVisible()
    await userEvent.click(screen.getByRole('button', { name: 'Tải lại danh sách' }))
    await table()
    expect(listCardBatches).toHaveBeenCalledTimes(2)
    expect(voidCardBatch).toHaveBeenCalledTimes(1)
  })

  it('FR026_supports loading, error, explicit retry and an empty list with issuance still available', async () => {
    const response = deferred<BatchPage>()
    vi.mocked(listCardBatches)
      .mockRejectedValueOnce(new Error('Internal details'))
      .mockReturnValueOnce(response.promise)
    renderPage()
    expect(screen.getByRole('status', { name: 'Đang tải danh sách lô' })).toBeVisible()
    expect(await screen.findByRole('heading', { name: 'Chưa tải được danh sách lô' })).toBeVisible()
    expect(screen.queryByRole('heading', { name: 'Chưa có lô mã' })).not.toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'Thử lại' }))
    expect(screen.getByRole('status', { name: 'Đang tải danh sách lô' })).toBeVisible()
    await act(async () => response.resolve({ items: [], page: 0, size: 20, total: 0 }))
    expect(screen.getByRole('heading', { name: 'Chưa có lô mã' })).toBeVisible()
    expect(screen.getByRole('button', { name: 'Phát hành lô mã' })).toBeEnabled()
    expect(screen.queryByRole('navigation', { name: 'Phân trang' })).not.toBeInTheDocument()
    expect(screen.queryByText('Internal details')).not.toBeInTheDocument()
  })

  it('FR026_paginates twenty rows and ignores stale responses during quick 1 to 2 to 1 navigation', async () => {
    const first: BatchPage = {
      ...structuredClone(creditsBatchPage),
      total: 25,
      items: Array.from({ length: 20 }, (_, i) => ({
        ...creditsBatchPage.items[0]!,
        batchId: `B20261106-DEMO${String(i).padStart(2, '0')}`,
      })),
    }
    const last: BatchPage = {
      ...first,
      page: 1,
      items: first.items
        .slice(0, 5)
        .map((batch) => ({ ...batch, batchId: batch.batchId.replace('B20261106', 'B20261105') })),
    }
    const old = deferred<BatchPage>()
    const latest = deferred<BatchPage>()
    vi.mocked(listCardBatches)
      .mockResolvedValueOnce(first)
      .mockReturnValueOnce(old.promise)
      .mockReturnValueOnce(latest.promise)
      .mockResolvedValueOnce(last)
    renderPage()
    let data = await table()
    expect(data.getAllByRole('row')).toHaveLength(21)
    expect(screen.getByText('Trang 1 / 2')).toBeVisible()
    await userEvent.click(screen.getByRole('button', { name: 'Sau' }))
    await userEvent.click(screen.getByRole('button', { name: 'Trước' }))
    expect(vi.mocked(listCardBatches).mock.calls[1]![1]!.aborted).toBe(true)
    await act(async () => latest.resolve(first))
    await act(async () => old.resolve(last))
    data = await table()
    expect(data.getByText(first.items[0]!.batchId)).toBeVisible()
    expect(data.queryByText(last.items[0]!.batchId)).not.toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'Sau' }))
    data = await table()
    expect(data.getAllByRole('row')).toHaveLength(6)
    expect(screen.getByText('Trang 2 / 2')).toBeVisible()
    expect(vi.mocked(listCardBatches).mock.calls.map(([params]) => params)).toEqual([
      { page: 0, size: 20 },
      { page: 1, size: 20 },
      { page: 0, size: 20 },
      { page: 1, size: 20 },
    ])
  })

  it('FR026_aborts reads and ignores late creation after unmount', async () => {
    const response = deferred<CreatedBatch>()
    vi.mocked(createCardBatch).mockReturnValueOnce(response.promise)
    const view = renderPage()
    await table()
    fillCredit()
    await prepare()
    await userEvent.click(screen.getByRole('button', { name: 'Xác nhận phát hành' }))
    const signal = vi.mocked(listCardBatches).mock.calls[0]![1]!
    const errors = vi.spyOn(console, 'error')
    view.unmount()
    expect(signal.aborted).toBe(true)
    await act(async () => response.resolve(structuredClone(creditsCreatedBatch)))
    expect(listCardBatches).toHaveBeenCalledTimes(1)
    expect(screen.queryByRole('list')).not.toBeInTheDocument()
    expect(errors).not.toHaveBeenCalled()
  })

  it('FR026_ignores late void results after the administrator identity changes', async () => {
    const response = deferred<VoidResult>()
    vi.mocked(voidCardBatch).mockReturnValueOnce(response.promise)
    const view = renderPage()
    const data = await table()
    await userEvent.click(data.getByRole('button', { name: 'Khoá lô B20261102-DEMO02' }))
    await userEvent.click(screen.getByRole('button', { name: 'Xác nhận khoá lô' }))
    view.rerender(pageElement(8))
    await table()
    const calls = vi.mocked(listCardBatches).mock.calls.length
    await act(async () => response.resolve({ batchId: 'B20261102-DEMO02', voided: 7 }))
    expect(listCardBatches).toHaveBeenCalledTimes(calls)
    expect(
      screen.queryByText('Lô B20261102-DEMO02: đã khoá 7 mã chưa dùng.'),
    ).not.toBeInTheDocument()
  })

  it.each([null, 'LEARNER', 'ACCOUNTANT'] as const)(
    'FR001_does not load batches for %s',
    async (role) => {
      renderPage(role === null ? null : 7, role === null ? [] : [role])
      expect(
        screen.getByText('Cần đăng nhập với quyền quản trị để quản lý lô mã thẻ.'),
      ).toBeVisible()
      expect(listCardBatches).not.toHaveBeenCalled()
      expect(createCardBatch).not.toHaveBeenCalled()
      expect(voidCardBatch).not.toHaveBeenCalled()
    },
  )

  it('FR026_escapes metadata and leaves test fixtures unchanged', async () => {
    const original = JSON.stringify(creditsBatchPage)
    const batch = structuredClone(creditsBatchPage)
    batch.items[0]!.campaign = '<img src=x onerror=bad()>'
    vi.mocked(listCardBatches).mockResolvedValue(batch)
    renderPage()
    const data = await table()
    expect(data.getByText('Trực tiếp · <img src=x onerror=bad()>')).toBeVisible()
    expect(screen.getByRole('table').querySelector('img,script')).toBeNull()
    fillCredit()
    await issue()
    expect(JSON.stringify(creditsBatchPage)).toBe(original)
    expect(creditsCreatedBatch.codes).toEqual([
      'DEMO-ONLY-0001-USE0',
      'DEMO-ONLY-0002-USE0',
      'DEMO-ONLY-0003-USE0',
    ])
  })
})
