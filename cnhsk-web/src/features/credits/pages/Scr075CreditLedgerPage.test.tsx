import { act, fireEvent, render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import type { RoleCode } from '@/features/auth/types'
import { ApiError, type BlobApiResponse } from '@/shared/api'
import { AuthContext, type AuthContextValue } from '@/shared/auth'
import { downloadBlob } from '@/shared/lib/downloadBlob'

import { exportLedger, listLedger, reconcileLedger } from '../api/ledger'
import {
  creditsEmptyLedgerPage,
  creditsLedgerLastPage,
  creditsLedgerPage,
  creditsLedgerRows,
  creditsReconciliation,
  creditsReconciliationWithIssues,
} from '../test-fixtures'
import type { LedgerPage, Reconciliation } from '../types'
import Scr075CreditLedgerPage from './Scr075CreditLedgerPage'

vi.mock('../api/ledger', () => ({
  exportLedger: vi.fn(),
  listLedger: vi.fn(),
  reconcileLedger: vi.fn(),
}))
vi.mock('@/shared/lib/downloadBlob', () => ({ downloadBlob: vi.fn() }))

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
        <Scr075CreditLedgerPage />
      </MemoryRouter>
    </AuthContext.Provider>
  )
}
function renderPage(id: number | null = 7, roles: RoleCode[] = ['ADMIN']) {
  return render(element(id, roles))
}
function dates(from = '2026-11-01', to = '2026-11-03') {
  fireEvent.change(screen.getByLabelText(/^Từ ngày/), { target: { value: from } })
  fireEvent.change(screen.getByLabelText(/^Đến ngày/), { target: { value: to } })
}
async function apply(from = '2026-11-01', to = '2026-11-03') {
  dates(from, to)
  await userEvent.click(screen.getByRole('button', { name: 'Lọc sổ cái' }))
}
async function table() {
  return within(await screen.findByRole('table', { name: 'Sổ cái điểm' }))
}
function deferred<T>() {
  let resolve!: (value: T) => void
  const promise = new Promise<T>((done) => {
    resolve = done
  })
  return { resolve, promise }
}

beforeEach(() => {
  vi.resetAllMocks()
  vi.mocked(listLedger).mockImplementation(async ({ page = 0 }) =>
    structuredClone(page === 0 ? creditsLedgerPage : creditsLedgerLastPage),
  )
  vi.mocked(exportLedger).mockResolvedValue({
    blob: new Blob(['server CSV'], { type: 'text/csv' }),
    fileName: 'server-ledger.csv',
  })
  vi.mocked(reconcileLedger).mockResolvedValue(structuredClone(creditsReconciliation))
})
afterEach(() => {
  vi.restoreAllMocks()
  vi.unstubAllGlobals()
})

describe('SCR-075 credit ledger (FR-028, AT-26)', () => {
  it('AT26_requires dates before reading or exporting and never queries all history on mount', async () => {
    renderPage()
    expect(screen.getByLabelText(/^Từ ngày/)).toHaveValue('')
    expect(screen.getByLabelText(/^Đến ngày/)).toHaveValue('')
    expect(screen.getByRole('button', { name: 'Xuất CSV' })).toBeDisabled()
    expect(listLedger).not.toHaveBeenCalled()
    expect(exportLedger).not.toHaveBeenCalled()
    await userEvent.click(screen.getByRole('button', { name: 'Lọc sổ cái' }))
    expect(screen.getByText('Chọn ngày bắt đầu hợp lệ.')).toBeVisible()
    expect(screen.getByText('Chọn ngày kết thúc hợp lệ.')).toBeVisible()
    expect(screen.getByLabelText(/^Từ ngày/)).toHaveFocus()
    expect(listLedger).not.toHaveBeenCalled()
    expect(screen.queryByRole('region', { name: 'Tổng điểm' })).not.toBeInTheDocument()
  })

  it.each([
    { from: '', to: '2026-11-01' },
    { from: '2026-11-01', to: '' },
    { from: '2026-11-02', to: '2026-11-01' },
    { from: '2026-01-01', to: '2026-04-04' },
    { from: '2026-02-30', to: '2026-03-01' },
  ])('AT26_rejects missing/invalid/reversed/93-day range $from to $to', async ({ from, to }) => {
    renderPage()
    await apply(from, to)
    expect(listLedger).not.toHaveBeenCalled()
    expect(exportLedger).not.toHaveBeenCalled()
    expect(screen.getByRole('button', { name: 'Xuất CSV' })).toBeDisabled()
    expect(screen.queryByRole('table')).not.toBeInTheDocument()
  })

  it('AT26_explains a range over 92 days at the end-date field', async () => {
    renderPage()
    await apply('2026-01-01', '2026-04-04')
    expect(screen.getByLabelText(/^Đến ngày/)).toHaveAttribute('aria-invalid', 'true')
    expect(screen.getByLabelText(/^Đến ngày/)).toHaveAccessibleDescription(
      'Hai ngày được chọn không được cách nhau quá 92 ngày.',
    )
  })

  it.each([
    { from: '2026-11-01', to: '2026-11-01' },
    { from: '2026-01-01', to: '2026-04-03' },
    { from: '2028-02-28', to: '2028-03-01' },
  ])(
    'Q_T04_01_02_accepts date bounds $from to $to and sends unchanged Vietnam dates',
    async ({ from, to }) => {
      renderPage()
      await apply(from, to)
      await table()
      expect(listLedger).toHaveBeenCalledWith(
        { from, to, page: 0, size: 20 },
        expect.any(AbortSignal),
      )
      expect(
        screen.getByText(
          'Bao gồm cả ngày kết thúc theo giờ Việt Nam. Hai ngày được chọn cách nhau tối đa 92 ngày.',
        ),
      ).toBeVisible()
      expect(screen.getByText(`Đang xem từ ${from} đến hết ${to} (giờ Việt Nam).`)).toBeVisible()
    },
  )

  it('AT26_displays exact server masking, signed values and both balances without attempting to unmask', async () => {
    const response = structuredClone(creditsLedgerPage)
    response.items[0]!.maskedEmail = 'server***+tag@example.test'
    renderPage()
    vi.mocked(listLedger).mockResolvedValue(response)
    await apply()
    const data = await table()
    const cells = within(data.getAllByRole('row')[1]!).getAllByRole('cell')
    expect(cells.map((cell) => cell.textContent)).toEqual([
      '7025',
      'server***+tag@example.test',
      '25',
      'Sử dụng điểm',
      '−1',
      '122',
      '121',
      'Lượt chấm bài mẫu',
      '09:24 01/11/2026',
    ])
    expect(data.getByText('+10')).toBeVisible()
    expect(data.getByText('Kích hoạt gói')).toBeVisible()
    expect(data.getAllByText('0').length).toBeGreaterThan(0)
    expect(cells[8]!.querySelector('time')).toHaveAttribute('dateTime', '2026-11-01T02:24:00Z')
  })

  it('AT26_formats an instant at Vietnam midnight and safely renders a missing or authored reason', async () => {
    vi.mocked(listLedger).mockResolvedValue({
      ...structuredClone(creditsLedgerPage),
      items: [
        {
          ...creditsLedgerRows[0]!,
          createdAt: '2026-11-30T17:00:00Z',
          reason: '<img src=x onerror=bad()>',
        },
        { ...creditsLedgerRows[1]!, reason: null },
      ],
    })
    renderPage()
    await apply()
    const data = await table()
    expect(data.getByText('00:00 01/12/2026')).toBeVisible()
    expect(data.getByText('<img src=x onerror=bad()>')).toBeVisible()
    expect(data.getByText('—')).toBeVisible()
    expect(screen.getByRole('table').querySelector('img,script')).toBeNull()
  })

  it('AT26_uses whole-filter totals from the server rather than calculating a page total', async () => {
    renderPage()
    await apply()
    await table()
    const totals = within(screen.getByRole('region', { name: 'Tổng điểm' }))
    expect(totals.getByText('25')).toBeVisible()
    expect(totals.getByText(`+${creditsLedgerPage.totals.credited}`)).toBeVisible()
    expect(totals.getByText(`−${Math.abs(creditsLedgerPage.totals.debited)}`)).toBeVisible()
    expect(screen.queryByText(/doanh thu/i)).not.toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'Sau' }))
    const data = await table()
    expect(data.getAllByRole('row')).toHaveLength(6)
    expect(within(screen.getByRole('region', { name: 'Tổng điểm' })).getByText('25')).toBeVisible()
  })

  it('FR028_keeps the applied type across API pages and resets to page zero when applying a new filter', async () => {
    renderPage()
    dates()
    await userEvent.selectOptions(
      screen.getByRole('combobox', { name: 'Loại giao dịch' }),
      'DEDUCT',
    )
    await userEvent.click(screen.getByRole('button', { name: 'Lọc sổ cái' }))
    let data = await table()
    expect(data.getAllByRole('row')).toHaveLength(21)
    await userEvent.click(screen.getByRole('button', { name: 'Sau' }))
    await table()
    expect(screen.getByText('Trang 2 / 2')).toBeVisible()
    await userEvent.selectOptions(screen.getByRole('combobox', { name: 'Loại giao dịch' }), 'TOPUP')
    expect(screen.getByRole('button', { name: 'Xuất CSV' })).toBeDisabled()
    await userEvent.click(screen.getByRole('button', { name: 'Lọc sổ cái' }))
    data = await table()
    expect(data.getAllByRole('row')).toHaveLength(21)
    expect(vi.mocked(listLedger).mock.calls.map(([params]) => params)).toEqual([
      { from: '2026-11-01', to: '2026-11-03', type: 'DEDUCT', page: 0, size: 20 },
      { from: '2026-11-01', to: '2026-11-03', type: 'DEDUCT', page: 1, size: 20 },
      { from: '2026-11-01', to: '2026-11-03', type: 'TOPUP', page: 0, size: 20 },
    ])
    expect(screen.getAllByRole('option')).toHaveLength(7)
  })

  it('FR028_empty results show only server-confirmed zero totals', async () => {
    vi.mocked(listLedger).mockResolvedValue(structuredClone(creditsEmptyLedgerPage))
    renderPage()
    await apply()
    expect(
      await screen.findByRole('heading', { name: 'Không có giao dịch trong khoảng đã chọn' }),
    ).toBeVisible()
    expect(screen.queryByRole('table')).not.toBeInTheDocument()
    expect(
      within(screen.getByRole('region', { name: 'Tổng điểm' })).getAllByText('0'),
    ).toHaveLength(3)
    expect(screen.queryByRole('navigation', { name: 'Phân trang' })).not.toBeInTheDocument()
  })

  it('FR028_supports loading/error/retry without replacing a failure with empty rows or zero totals', async () => {
    const response = deferred<LedgerPage>()
    vi.mocked(listLedger)
      .mockRejectedValueOnce(new Error('Internal details'))
      .mockReturnValueOnce(response.promise)
    renderPage()
    await apply()
    expect(await screen.findByRole('heading', { name: 'Chưa tải được sổ cái' })).toBeVisible()
    expect(screen.queryByRole('region', { name: 'Tổng điểm' })).not.toBeInTheDocument()
    expect(screen.queryByText('Internal details')).not.toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'Thử lại' }))
    expect(screen.getByRole('status', { name: 'Đang tải sổ cái' })).toBeVisible()
    await act(async () => response.resolve(structuredClone(creditsLedgerPage)))
    await table()
    expect(listLedger).toHaveBeenCalledTimes(2)
  })

  it.each(['server-ledger.csv', undefined])(
    'FR018_downloads the identical server Blob with filename %s',
    async (fileName) => {
      const blob = new Blob(['id,maskedEmail,amount\r\n1,ma***@example.test,-1\r\n'], {
        type: 'text/csv;charset=utf-8',
      })
      vi.mocked(exportLedger).mockResolvedValue({ blob, fileName })
      renderPage()
      dates()
      await userEvent.selectOptions(
        screen.getByRole('combobox', { name: 'Loại giao dịch' }),
        'TOPUP',
      )
      await userEvent.click(screen.getByRole('button', { name: 'Lọc sổ cái' }))
      await table()
      await userEvent.click(screen.getByRole('button', { name: 'Sau' }))
      await table()
      await userEvent.click(screen.getByRole('button', { name: 'Xuất CSV' }))
      expect(exportLedger).toHaveBeenCalledWith(
        { from: '2026-11-01', to: '2026-11-03', type: 'TOPUP' },
        expect.any(AbortSignal),
      )
      expect(downloadBlob).toHaveBeenCalledWith(blob, fileName ?? 'credit-ledger.csv')
      expect(vi.mocked(downloadBlob).mock.calls[0]![0]).toBe(blob)
      expect(exportLedger).toHaveBeenCalledTimes(1)
    },
  )

  it('FR018_EXPORT_TOO_LARGE preserves the filter and table and never downloads a file', async () => {
    vi.mocked(exportLedger).mockRejectedValueOnce(
      new ApiError({
        status: 422,
        errorCode: 'EXPORT_TOO_LARGE',
        message: 'Internal details',
        requestId: 'req-export',
      }),
    )
    renderPage()
    await apply()
    await table()
    await userEvent.click(screen.getByRole('button', { name: 'Xuất CSV' }))
    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Phạm vi có hơn 10.000 giao dịch. Thu hẹp khoảng ngày hoặc chọn loại giao dịch rồi xuất lại.',
    )
    expect(screen.getByRole('table', { name: 'Sổ cái điểm' })).toBeVisible()
    expect(screen.getByLabelText(/^Từ ngày/)).toHaveValue('2026-11-01')
    expect(downloadBlob).not.toHaveBeenCalled()
    expect(screen.queryByText('Internal details')).not.toBeInTheDocument()
  })

  it('FR018_guards duplicate exports while the response is pending', async () => {
    const response = deferred<BlobApiResponse>()
    vi.mocked(exportLedger).mockReturnValueOnce(response.promise)
    renderPage()
    await apply()
    await table()
    const button = screen.getByRole('button', { name: 'Xuất CSV' })
    fireEvent.click(button)
    fireEvent.click(button)
    expect(exportLedger).toHaveBeenCalledTimes(1)
    expect(button).toBeDisabled()
    await act(async () => response.resolve({ blob: new Blob(['CSV']) }))
    expect(downloadBlob).toHaveBeenCalledTimes(1)
  })

  it('FR018_handles an absent response and a failed download without claiming success', async () => {
    vi.mocked(exportLedger).mockResolvedValueOnce(undefined)
    renderPage()
    await apply()
    await table()
    await userEvent.click(screen.getByRole('button', { name: 'Xuất CSV' }))
    expect(await screen.findByRole('heading', { name: 'Chưa xuất được CSV' })).toBeVisible()
    expect(downloadBlob).not.toHaveBeenCalled()
    vi.mocked(downloadBlob).mockImplementationOnce(() => {
      throw new Error('Internal download details')
    })
    await userEvent.click(screen.getByRole('button', { name: 'Xuất CSV' }))
    expect(screen.getByRole('heading', { name: 'Chưa xuất được CSV' })).toBeVisible()
    expect(screen.queryByText('Internal download details')).not.toBeInTheDocument()
    expect(exportLedger).toHaveBeenCalledTimes(2)
  })

  it.each(['unmount', 'account', 'draft', 'apply'] as const)(
    'FR018_ignores late CSV after %s and aborts its signal',
    async (scenario) => {
      const response = deferred<BlobApiResponse>()
      vi.mocked(exportLedger).mockReturnValueOnce(response.promise)
      const view = renderPage()
      await apply()
      await table()
      await userEvent.click(screen.getByRole('button', { name: 'Xuất CSV' }))
      const signal = vi.mocked(exportLedger).mock.calls[0]![1]!
      if (scenario === 'unmount') view.unmount()
      else if (scenario === 'account') view.rerender(element(8))
      else {
        dates('2026-11-02', '2026-11-03')
        if (scenario === 'apply') {
          await userEvent.click(screen.getByRole('button', { name: 'Lọc sổ cái' }))
          await table()
        }
      }
      expect(signal.aborted).toBe(true)
      await act(async () => response.resolve({ blob: new Blob(['Old CSV']) }))
      expect(downloadBlob).not.toHaveBeenCalled()
    },
  )

  it('FR018_a stale export cannot clear a newer request pending state or cause an old download', async () => {
    const old = deferred<BlobApiResponse>()
    const latest = deferred<BlobApiResponse>()
    vi.mocked(exportLedger).mockReturnValueOnce(old.promise).mockReturnValueOnce(latest.promise)
    renderPage()
    await apply()
    await table()
    await userEvent.click(screen.getByRole('button', { name: 'Xuất CSV' }))
    await apply('2026-11-02', '2026-11-03')
    await table()
    await userEvent.click(screen.getByRole('button', { name: 'Xuất CSV' }))
    await act(async () => old.resolve({ blob: new Blob(['Old CSV']) }))
    expect(screen.getByRole('button', { name: 'Xuất CSV' })).toBeDisabled()
    expect(downloadBlob).not.toHaveBeenCalled()
    const blob = new Blob(['Latest CSV'])
    await act(async () => latest.resolve({ blob }))
    expect(downloadBlob).toHaveBeenCalledWith(blob, 'credit-ledger.csv')
    expect(downloadBlob).toHaveBeenCalledTimes(1)
  })

  it('AT26_reconciles the whole ledger without date/type parameters and shows a clean result inline', async () => {
    renderPage()
    expect(listLedger).not.toHaveBeenCalled()
    await userEvent.click(screen.getByRole('button', { name: 'Đối chiếu' }))
    const result = within(await screen.findByRole('region', { name: 'Kết quả đối chiếu toàn sổ' }))
    expect(
      screen.getByText('Kiểm tra toàn bộ sổ cái, không phụ thuộc bộ lọc ngày hoặc loại giao dịch.'),
    ).toBeVisible()
    expect(result.getByText('Không phát hiện lỗi đối chiếu trong toàn sổ.')).toBeVisible()
    expect(result.getByText('25')).toBeVisible()
    expect(result.getByText('121')).toBeVisible()
    expect(result.getByText('+121')).toBeVisible()
    expect(reconcileLedger).toHaveBeenCalledWith(expect.any(AbortSignal))
    expect(vi.mocked(reconcileLedger).mock.calls[0]).toHaveLength(1)
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
  })

  it('AT26_lists every server issue with its masked email, sequence and kind without repair actions', async () => {
    vi.mocked(reconcileLedger).mockResolvedValue(structuredClone(creditsReconciliationWithIssues))
    renderPage()
    await userEvent.click(screen.getByRole('button', { name: 'Đối chiếu' }))
    const issues = within(await screen.findByRole('table', { name: 'Lỗi đối chiếu toàn sổ' }))
    expect(issues.getAllByRole('row')).toHaveLength(4)
    expect(issues.getAllByText('ma***@example.test')).toHaveLength(3)
    expect(issues.getByText('Thứ tự giao dịch không liên tục')).toBeVisible()
    expect(issues.getByText('Số dư trước không khớp giao dịch liền trước')).toBeVisible()
    expect(issues.getByText('Số dư trước của giao dịch đầu khác 0')).toBeVisible()
    expect(
      screen.getByText('Phát hiện 3 lỗi cần kiểm tra. Không có giao dịch nào được tự sửa.'),
    ).toBeVisible()
    expect(
      screen.queryByRole('button', { name: /sửa|xoá|xóa|điều chỉnh|hoàn điểm/i }),
    ).not.toBeInTheDocument()
  })

  it('FR019_guards duplicate reconciliation and supports error with explicit retry', async () => {
    const response = deferred<Reconciliation>()
    vi.mocked(reconcileLedger)
      .mockRejectedValueOnce(new Error('Internal details'))
      .mockReturnValueOnce(response.promise)
    renderPage()
    await userEvent.click(screen.getByRole('button', { name: 'Đối chiếu' }))
    expect(await screen.findByRole('heading', { name: 'Chưa đối chiếu được sổ cái' })).toBeVisible()
    expect(
      screen.queryByText('Không phát hiện lỗi đối chiếu trong toàn sổ.'),
    ).not.toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'Thử lại' }))
    expect(screen.getByText('Đang đối chiếu toàn sổ…')).toBeVisible()
    fireEvent.click(screen.getByRole('button', { name: 'Đối chiếu' }))
    expect(reconcileLedger).toHaveBeenCalledTimes(2)
    await act(async () => response.resolve(structuredClone(creditsReconciliation)))
    expect(screen.getByText('Không phát hiện lỗi đối chiếu trong toàn sổ.')).toBeVisible()
  })

  it('FR019_aborts and ignores a reconciliation result after leaving the page', async () => {
    const response = deferred<Reconciliation>()
    vi.mocked(reconcileLedger).mockReturnValueOnce(response.promise)
    const view = renderPage()
    await userEvent.click(screen.getByRole('button', { name: 'Đối chiếu' }))
    const signal = vi.mocked(reconcileLedger).mock.calls[0]![0]!
    view.unmount()
    expect(signal.aborted).toBe(true)
    await act(async () => response.resolve(structuredClone(creditsReconciliation)))
    expect(
      screen.queryByRole('region', { name: 'Kết quả đối chiếu toàn sổ' }),
    ).not.toBeInTheDocument()
  })

  it('FR028_ignores an old page response during fast 1 to 2 to 1 navigation', async () => {
    const old = deferred<LedgerPage>()
    const latest = deferred<LedgerPage>()
    vi.mocked(listLedger)
      .mockResolvedValueOnce(structuredClone(creditsLedgerPage))
      .mockReturnValueOnce(old.promise)
      .mockReturnValueOnce(latest.promise)
    renderPage()
    await apply()
    await table()
    await userEvent.click(screen.getByRole('button', { name: 'Sau' }))
    await userEvent.click(screen.getByRole('button', { name: 'Trước' }))
    expect(vi.mocked(listLedger).mock.calls[1]![1]!.aborted).toBe(true)
    await act(async () =>
      latest.resolve({
        ...structuredClone(creditsLedgerPage),
        items: [{ ...creditsLedgerRows[0]!, reason: 'Latest response' }],
      }),
    )
    await act(async () =>
      old.resolve({
        ...structuredClone(creditsLedgerLastPage),
        items: [{ ...creditsLedgerRows[1]!, reason: 'Old response' }],
      }),
    )
    const data = await table()
    expect(data.getByText('Latest response')).toBeVisible()
    expect(data.queryByText('Old response')).not.toBeInTheDocument()
    expect(screen.getByText('Trang 1 / 2')).toBeVisible()
  })

  it('FR028_aborts a pending read on unmount and ignores its late response', async () => {
    const response = deferred<LedgerPage>()
    vi.mocked(listLedger).mockReturnValueOnce(response.promise)
    const view = renderPage()
    await apply()
    expect(screen.getByRole('status', { name: 'Đang tải sổ cái' })).toBeVisible()
    const signal = vi.mocked(listLedger).mock.calls[0]![1]!
    view.unmount()
    expect(signal.aborted).toBe(true)
    await act(async () => response.resolve(structuredClone(creditsLedgerPage)))
    expect(screen.queryByRole('table')).not.toBeInTheDocument()
  })

  it.each([null, 'ACCOUNTANT', 'LEARNER'] as const)(
    'FR001_does not call ledger APIs for %s',
    (role) => {
      renderPage(role === null ? null : 7, role === null ? [] : [role])
      expect(listLedger).not.toHaveBeenCalled()
      expect(exportLedger).not.toHaveBeenCalled()
      expect(reconcileLedger).not.toHaveBeenCalled()
    },
  )

  it('FR028_uses offline API mocks and never mutates shared test fixtures', async () => {
    const original = JSON.stringify([
      creditsLedgerRows,
      creditsLedgerPage,
      creditsReconciliationWithIssues,
    ])
    const fetchMock = vi.fn()
    vi.stubGlobal('fetch', fetchMock)
    renderPage()
    await apply()
    await table()
    await userEvent.click(screen.getByRole('button', { name: 'Xuất CSV' }))
    await userEvent.click(screen.getByRole('button', { name: 'Đối chiếu' }))
    expect(fetchMock).not.toHaveBeenCalled()
    expect(
      JSON.stringify([creditsLedgerRows, creditsLedgerPage, creditsReconciliationWithIssues]),
    ).toBe(original)
  })
})
