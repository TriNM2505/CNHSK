import { act, render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { AuthContext, type AuthContextValue } from '@/shared/auth'

import { listMyTransactions } from '../api/wallet'
import {
  creditsEmptyTransactionPage,
  creditsTransactionPage,
  creditsTransactionLastPage,
  creditsTransactions,
} from '../test-fixtures'
import type { TransactionPage } from '../types'
import Scr031TransactionHistoryPage from './Scr031TransactionHistoryPage'

vi.mock('../api/wallet', () => ({ listMyTransactions: vi.fn() }))

function renderPage(id: number | null = 7) {
  const auth: AuthContextValue = {
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
  return render(
    <AuthContext.Provider value={auth}>
      <MemoryRouter>
        <Scr031TransactionHistoryPage />
      </MemoryRouter>
    </AuthContext.Provider>,
  )
}

async function table() {
  return within(await screen.findByRole('table', { name: 'Giao dịch điểm' }))
}

beforeEach(() => {
  vi.resetAllMocks()
  vi.mocked(listMyTransactions).mockImplementation(({ page = 0 } = {}) =>
    Promise.resolve(
      structuredClone(page === 0 ? creditsTransactionPage : creditsTransactionLastPage),
    ),
  )
})

afterEach(() => {
  vi.restoreAllMocks()
  vi.unstubAllGlobals()
})

describe('SCR-031 transaction history (FR-025, AT-23)', () => {
  it('FR025_AT23_rendersSignedAmountsAndTypes including zero-value subscriptions', async () => {
    renderPage()
    const data = await table()
    expect(data.getByText('+10')).toBeVisible()
    expect(data.getAllByText('−1').length).toBeGreaterThan(0)
    expect(data.getByText('0')).toBeVisible()
    expect(data.getByText('Kích hoạt gói')).toBeVisible()
    expect(data.getByText('Điều chỉnh')).toBeVisible()
    expect(data.getByText('Điểm chấm bài')).toBeVisible()
    expect(data.getAllByText('Nạp điểm').length).toBeGreaterThan(0)
    expect(data.getAllByText('Hoàn điểm').length).toBeGreaterThan(0)
    expect(data.getAllByText('Sử dụng điểm').length).toBeGreaterThan(0)
  })

  it('FR025_AT23_rendersTransactionDetails with balances, reason and Vietnam time', async () => {
    renderPage()
    const data = await table()
    const latest = within(data.getAllByRole('row')[1]!)
    const cells = latest.getAllByRole('cell')
    expect(cells.map((cell) => cell.textContent)).toEqual([
      '−1',
      'Sử dụng điểm',
      '122',
      '121',
      'Lượt chấm bài mẫu',
      '09:24 01/11/2026',
    ])
    expect(cells[5]!.querySelector('time')).toHaveAttribute('dateTime', '2026-11-01T02:24:00Z')
    const mobile = within(screen.getByRole('region', { name: 'Giao dịch điểm' }))
    expect(mobile.getAllByText('Lượt chấm bài mẫu').length).toBeGreaterThan(0)
  })

  it('FR025_AT23_paginatesTwentyRows using API pages 0 and 1 without slicing twice', async () => {
    renderPage()
    let data = await table()
    expect(data.getAllByRole('row')).toHaveLength(21)
    expect(screen.getByText('Trang 1 / 2')).toBeVisible()
    expect(screen.getByRole('button', { name: 'Trước' })).toBeDisabled()
    expect(
      data
        .getAllByRole('row')
        .slice(1)
        .map((row) => row.querySelector('time')?.dateTime),
    ).toEqual(creditsTransactions.slice(0, 20).map((row) => row.createdAt))
    await userEvent.click(screen.getByRole('button', { name: 'Sau' }))
    data = await table()
    expect(data.getAllByRole('row')).toHaveLength(6)
    expect(screen.getByText('Trang 2 / 2')).toBeVisible()
    expect(screen.getByRole('button', { name: 'Sau' })).toBeDisabled()
    expect(
      data
        .getAllByRole('row')
        .slice(1)
        .map((row) => row.querySelector('time')?.dateTime),
    ).toEqual(creditsTransactions.slice(20).map((row) => row.createdAt))
    await userEvent.click(screen.getByRole('button', { name: 'Trước' }))
    data = await table()
    expect(data.getAllByRole('row')).toHaveLength(21)
    expect(vi.mocked(listMyTransactions).mock.calls.map(([params]) => params)).toEqual([
      { page: 0, size: 20 },
      { page: 1, size: 20 },
      { page: 0, size: 20 },
    ])
    for (const [, signal] of vi.mocked(listMyTransactions).mock.calls)
      expect(signal).toBeInstanceOf(AbortSignal)
  })

  it('FR025_AT23_rendersEmptyHistory without pagination or editing actions', async () => {
    vi.mocked(listMyTransactions).mockResolvedValue(structuredClone(creditsEmptyTransactionPage))
    renderPage()
    expect(await screen.findByRole('heading', { name: 'Chưa có giao dịch' })).toBeVisible()
    expect(screen.queryByRole('table')).not.toBeInTheDocument()
    expect(screen.queryByRole('navigation', { name: 'Phân trang' })).not.toBeInTheDocument()
  })

  it('FR025_keepsHistoryReadOnly with a link back and no filters or private card fields', async () => {
    renderPage()
    const data = await table()
    expect(screen.getByRole('link', { name: 'Về gói dịch vụ và điểm' })).toHaveAttribute(
      'href',
      '/account/credits',
    )
    expect(screen.queryByRole('button', { name: /sửa|xoá|xóa/i })).not.toBeInTheDocument()
    expect(screen.queryByRole('combobox')).not.toBeInTheDocument()
    expect(data.getAllByRole('columnheader').map((cell) => cell.textContent)).toEqual([
      'Số điểm',
      'Loại giao dịch',
      'Số dư trước',
      'Số dư sau',
      'Nội dung',
      'Thời gian (giờ Việt Nam)',
    ])
  })

  it('FR025 supports loading, error and explicit retry without displaying internal details', async () => {
    let resolve!: (page: TransactionPage) => void
    vi.mocked(listMyTransactions)
      .mockRejectedValueOnce(new Error('Chi tiết nội bộ'))
      .mockImplementationOnce(
        () =>
          new Promise<TransactionPage>((done) => {
            resolve = done
          }),
      )
    renderPage()
    expect(screen.getByRole('status', { name: 'Đang tải lịch sử giao dịch' })).toBeVisible()
    await screen.findByRole('heading', { name: 'Chưa tải được lịch sử giao dịch' })
    expect(screen.queryByText('Chi tiết nội bộ')).not.toBeInTheDocument()
    expect(screen.queryByText('Chưa có giao dịch')).not.toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'Thử lại' }))
    expect(screen.getByRole('status', { name: 'Đang tải lịch sử giao dịch' })).toBeVisible()
    await act(async () => {
      resolve(structuredClone(creditsTransactionPage))
    })
    await table()
    expect(listMyTransactions).toHaveBeenCalledTimes(2)
  })

  it('FR025 handles a missing reason and escapes user-authored text', async () => {
    vi.mocked(listMyTransactions).mockResolvedValue({
      items: [
        { ...creditsTransactions[0]!, seq: 2, reason: '<img src=x onerror=bad()>' },
        { ...creditsTransactions[0]!, seq: 1, reason: null },
      ],
      page: 0,
      size: 20,
      total: 2,
    })
    renderPage()
    const data = await table()
    expect(data.getByText('<img src=x onerror=bad()>')).toBeVisible()
    expect(data.getByText('—')).toBeVisible()
    expect(screen.getByRole('table').querySelector('img,script')).toBeNull()
  })

  it('FR025 uses API mocks without HTTP or fixture mutation', async () => {
    const original = JSON.stringify(creditsTransactions)
    const fetchMock = vi.fn()
    vi.stubGlobal('fetch', fetchMock)
    renderPage()
    await table()
    await userEvent.click(screen.getByRole('button', { name: 'Sau' }))
    await table()
    expect(fetchMock).not.toHaveBeenCalled()
    expect(listMyTransactions).toHaveBeenCalledTimes(2)
    expect(JSON.stringify(creditsTransactions)).toBe(original)
    expect(creditsTransactions).toHaveLength(25)
  })

  it('FR025 only displays the latest request when navigating 1 to 2 to 1 quickly', async () => {
    let resolveOld!: (page: TransactionPage) => void
    let resolveLatest!: (page: TransactionPage) => void
    vi.mocked(listMyTransactions)
      .mockResolvedValueOnce(structuredClone(creditsTransactionPage))
      .mockImplementationOnce(
        () =>
          new Promise<TransactionPage>((done) => {
            resolveOld = done
          }),
      )
      .mockImplementationOnce(
        () =>
          new Promise<TransactionPage>((done) => {
            resolveLatest = done
          }),
      )
    renderPage()
    await table()
    await userEvent.click(screen.getByRole('button', { name: 'Sau' }))
    expect(screen.getByRole('status', { name: 'Đang tải lịch sử giao dịch' })).toBeVisible()
    await userEvent.click(screen.getByRole('button', { name: 'Trước' }))
    const oldSignal = vi.mocked(listMyTransactions).mock.calls[1]![1]!
    expect(oldSignal.aborted).toBe(true)
    await act(async () => {
      resolveLatest({
        ...structuredClone(creditsTransactionPage),
        items: [{ ...creditsTransactions[0]!, reason: 'Phản hồi mới nhất' }],
      })
    })
    let data = await table()
    expect(data.getByText('Phản hồi mới nhất')).toBeVisible()
    await act(async () => {
      resolveOld({
        ...structuredClone(creditsTransactionLastPage),
        items: [{ ...creditsTransactions[0]!, reason: 'Phản hồi cũ' }],
      })
    })
    data = await table()
    expect(data.getByText('Phản hồi mới nhất')).toBeVisible()
    expect(data.queryByText('Phản hồi cũ')).not.toBeInTheDocument()
    expect(screen.getByText('Trang 1 / 2')).toBeVisible()
  })

  it('FR025 aborts reads and ignores a late response after unmount', async () => {
    let resolve!: (page: TransactionPage) => void
    vi.mocked(listMyTransactions).mockImplementationOnce(
      () =>
        new Promise<TransactionPage>((done) => {
          resolve = done
        }),
    )
    const view = renderPage()
    const signal = vi.mocked(listMyTransactions).mock.calls[0]![1]!
    view.unmount()
    expect(signal.aborted).toBe(true)
    await act(async () => {
      resolve(structuredClone(creditsTransactionPage))
    })
    expect(screen.queryByRole('table')).not.toBeInTheDocument()
  })

  it('FR025 does not fetch private transactions without learner authentication', () => {
    renderPage(null)
    expect(listMyTransactions).not.toHaveBeenCalled()
  })
})
