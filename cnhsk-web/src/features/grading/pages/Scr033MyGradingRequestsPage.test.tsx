import { act, fireEvent, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError } from '@/shared/api'
import { listMyGradingRequests } from '../api/requests'
import { requestPage } from '../test-fixtures'
import type { GradingRequestPage } from '../types'
import Scr033MyGradingRequestsPage from './Scr033MyGradingRequestsPage'

const auth = vi.hoisted(() => ({ status: 'authenticated', user: { id: 1, roles: ['LEARNER'] } }))
vi.mock('@/shared/auth', () => ({ useCurrentUser: () => auth }))
vi.mock('../api/requests', () => ({ listMyGradingRequests: vi.fn() }))
function page() {
  return render(
    <MemoryRouter>
      <Scr033MyGradingRequestsPage />
    </MemoryRouter>,
  )
}
beforeEach(() => {
  auth.user = { id: 1, roles: ['LEARNER'] }
  vi.mocked(listMyGradingRequests).mockReset().mockResolvedValue(requestPage)
})

describe('SCR-033 own request list', () => {
  it('shows summaries, all status filters, zero scores and nullable values', async () => {
    vi.mocked(listMyGradingRequests).mockResolvedValue({
      ...requestPage,
      items: [requestPage.items[0]!, { ...requestPage.items[1]!, score: 0 }],
    })
    const { container } = page()
    await screen.findByRole('link', { name: 'GR-000042' })
    expect(screen.getByText('Điểm: 0/100')).toBeVisible()
    expect(screen.getAllByText(/^Điểm:/)).toHaveLength(1)
    expect(screen.getAllByRole('option')).toHaveLength(6)
    expect(screen.getByRole('link', { name: 'Gửi bài mới' })).toHaveAttribute(
      'href',
      '/grading/new',
    )
    expect(container.querySelector('script')).toBeNull()
    expect(listMyGradingRequests).toHaveBeenCalledWith(
      { status: undefined, page: 0, size: 20 },
      expect.any(AbortSignal),
    )
  })
  it('paginates with totalElements and resets to page zero when status changes', async () => {
    vi.mocked(listMyGradingRequests).mockResolvedValue({ ...requestPage, totalElements: 45 })
    page()
    await screen.findByRole('link', { name: 'GR-000042' })
    await userEvent.click(screen.getByRole('button', { name: 'Sau' }))
    await waitFor(() =>
      expect(listMyGradingRequests).toHaveBeenLastCalledWith(
        { status: undefined, page: 1, size: 20 },
        expect.any(AbortSignal),
      ),
    )
    await userEvent.selectOptions(screen.getByLabelText('Trạng thái'), 'COMPLETED')
    await waitFor(() =>
      expect(listMyGradingRequests).toHaveBeenLastCalledWith(
        { status: 'COMPLETED', page: 0, size: 20 },
        expect.any(AbortSignal),
      ),
    )
  })
  it('shows loading/error/retry and an empty filtered result', async () => {
    vi.mocked(listMyGradingRequests)
      .mockRejectedValueOnce(
        new ApiError({ status: 0, errorCode: 'NETWORK_ERROR', message: 'Lỗi danh sách.' }),
      )
      .mockResolvedValueOnce({ ...requestPage, items: [], totalElements: 0 })
    page()
    expect(screen.getByText('Đang tải yêu cầu…')).toBeVisible()
    await screen.findByText('Lỗi danh sách.')
    await userEvent.click(screen.getByRole('button', { name: 'Thử lại' }))
    await screen.findByText('Chưa có yêu cầu phù hợp.')
    expect(screen.queryByRole('link', { name: 'GR-000042' })).not.toBeInTheDocument()
  })
  it('aborts the old filter request and prevents its late data replacing the current list', async () => {
    let resolve!: (value: GradingRequestPage) => void
    vi.mocked(listMyGradingRequests)
      .mockImplementationOnce(
        () =>
          new Promise((done) => {
            resolve = done
          }),
      )
      .mockResolvedValueOnce({
        ...requestPage,
        items: [{ ...requestPage.items[1]!, reference: 'CURRENT-RESULT' }],
      })
    page()
    await waitFor(() => expect(listMyGradingRequests).toHaveBeenCalledTimes(1))
    const signal = vi.mocked(listMyGradingRequests).mock.calls[0]![1]!
    fireEvent.change(screen.getByLabelText('Trạng thái'), { target: { value: 'COMPLETED' } })
    await screen.findByRole('link', { name: 'CURRENT-RESULT' })
    expect(signal.aborted).toBe(true)
    await act(async () =>
      resolve({ ...requestPage, items: [{ ...requestPage.items[0]!, reference: 'OLD-RESULT' }] }),
    )
    expect(screen.queryByText('OLD-RESULT')).not.toBeInTheDocument()
  })
})
