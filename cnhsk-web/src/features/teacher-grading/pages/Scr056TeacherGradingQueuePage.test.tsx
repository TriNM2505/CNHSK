import { act, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError } from '@/shared/api'
import { claimGrading, getGradingQueue, getMyAssignedGrading } from '../api/requests'
import { assigned, mine, queue } from '../test-fixtures'
import Scr056TeacherGradingQueuePage from './Scr056TeacherGradingQueuePage'

const auth = vi.hoisted(() => ({ status: 'authenticated', user: { id: 1, roles: ['TEACHER'] } }))
vi.mock('@/shared/auth', () => ({ useCurrentUser: () => auth }))
vi.mock('../api/requests', () => ({
  claimGrading: vi.fn(),
  getGradingQueue: vi.fn(),
  getMyAssignedGrading: vi.fn(),
}))
function Location() {
  return <p data-testid="path">{useLocation().pathname}</p>
}
function content() {
  return (
    <MemoryRouter initialEntries={['/teacher/grading']}>
      <Routes>
        <Route path="/teacher/grading" element={<Scr056TeacherGradingQueuePage />} />
        <Route path="/teacher/grading/:id" element={<p>Bài đã nhận</p>} />
      </Routes>
      <Location />
    </MemoryRouter>
  )
}
beforeEach(() => {
  auth.user = { id: 1, roles: ['TEACHER'] }
  vi.mocked(claimGrading).mockReset().mockResolvedValue(assigned)
  vi.mocked(getGradingQueue).mockReset().mockResolvedValue(queue)
  vi.mocked(getMyAssignedGrading).mockReset().mockResolvedValue(mine)
})
afterEach(() => vi.restoreAllMocks())
describe('teacher grading queue', () => {
  it('loads queue and mine independently, shows anonymous metadata and zero payout', async () => {
    let resolve!: (value: typeof queue) => void
    vi.mocked(getGradingQueue).mockImplementationOnce(
      () =>
        new Promise((done) => {
          resolve = done
        }),
    )
    render(content())
    expect(screen.getByText('Đang tải hàng đợi…')).toBeVisible()
    await screen.findByRole('link', { name: 'Chấm GR-000042' })
    await act(async () => resolve(queue))
    expect(await screen.findByRole('button', { name: 'Nhận GR-000043' })).toBeVisible()
    expect(screen.getByText('Chưa chọn')).toBeVisible()
    expect(screen.getByText('0 điểm')).toBeVisible()
    expect(screen.queryByText(assigned.essay)).not.toBeInTheDocument()
  })
  it('paginates the queue from page zero and keeps mine loaded', async () => {
    vi.mocked(getGradingQueue).mockResolvedValue({ ...queue, totalElements: 21 })
    render(content())
    await screen.findByText('Trang 1 / 2')
    await userEvent.click(screen.getByRole('button', { name: 'Sau' }))
    await waitFor(() =>
      expect(getGradingQueue).toHaveBeenLastCalledWith(1, 20, expect.any(AbortSignal)),
    )
    expect(getMyAssignedGrading).toHaveBeenCalledTimes(1)
  })
  it('prevents duplicate claim requests and opens the ID returned by the server', async () => {
    let resolve!: (value: typeof assigned) => void
    vi.mocked(claimGrading).mockImplementation(
      () =>
        new Promise((done) => {
          resolve = done
        }),
    )
    render(content())
    const button = await screen.findByRole('button', { name: 'Nhận GR-000043' })
    await userEvent.click(button)
    expect(button).toBeDisabled()
    await userEvent.click(button)
    expect(claimGrading).toHaveBeenCalledTimes(1)
    expect(claimGrading).toHaveBeenCalledWith('43')
    await act(async () => resolve(assigned))
    await screen.findByText('Bài đã nhận')
    expect(screen.getByTestId('path')).toHaveTextContent('/teacher/grading/42')
  })
  it('handles 409 with the specified message and reloads GETs without repeating POST', async () => {
    vi.mocked(claimGrading).mockRejectedValue(
      new ApiError({
        status: 409,
        errorCode: 'GRADING_ALREADY_CLAIMED',
        message: 'Thông báo server',
      }),
    )
    vi.mocked(getGradingQueue)
      .mockResolvedValueOnce(queue)
      .mockResolvedValue({ ...queue, items: [], totalElements: 0 })
    render(content())
    await userEvent.click(await screen.findByRole('button', { name: 'Nhận GR-000043' }))
    await screen.findByText('Bài đã có người nhận')
    await screen.findByText('Chưa có bài chờ nhận.')
    expect(claimGrading).toHaveBeenCalledTimes(1)
    expect(getGradingQueue).toHaveBeenCalledTimes(2)
    expect(getMyAssignedGrading).toHaveBeenCalledTimes(2)
  })
  it.each([
    [422, 'GRADING_CLAIM_LIMIT'],
    [403, 'GRADING_SELF_GRADING'],
  ] as const)('keeps %i %s visible without retrying or navigating', async (status, errorCode) => {
    vi.mocked(claimGrading).mockRejectedValue(
      new ApiError({ status, errorCode, message: 'Không nhận được bài.' }),
    )
    render(content())
    await userEvent.click(await screen.findByRole('button', { name: 'Nhận GR-000043' }))
    await screen.findByText('Không nhận được bài.')
    expect(claimGrading).toHaveBeenCalledTimes(1)
    expect(screen.getByTestId('path')).toHaveTextContent('/teacher/grading')
  })
  it('shows both empty states and independent errors/retries', async () => {
    vi.mocked(getGradingQueue)
      .mockRejectedValueOnce(
        new ApiError({ status: 0, errorCode: 'NETWORK_ERROR', message: 'Lỗi hàng đợi.' }),
      )
      .mockResolvedValue({ ...queue, items: [], totalElements: 0 })
    vi.mocked(getMyAssignedGrading)
      .mockRejectedValueOnce(
        new ApiError({ status: 0, errorCode: 'NETWORK_ERROR', message: 'Lỗi bài đang giữ.' }),
      )
      .mockResolvedValue([])
    render(content())
    await screen.findByText('Lỗi hàng đợi.')
    await screen.findByText('Lỗi bài đang giữ.')
    await userEvent.click(screen.getByRole('button', { name: 'Thử lại hàng đợi' }))
    await screen.findByText('Chưa có bài chờ nhận.')
    expect(screen.getByText('Lỗi bài đang giữ.')).toBeVisible()
    await userEvent.click(screen.getByRole('button', { name: 'Thử lại bài đang giữ' }))
    await screen.findByText('Bạn chưa giữ bài nào.')
  })
  it('ignores an old claim response after account changes', async () => {
    let resolve!: (value: typeof assigned) => void
    vi.mocked(claimGrading).mockImplementation(
      () =>
        new Promise((done) => {
          resolve = done
        }),
    )
    const view = render(content())
    await userEvent.click(await screen.findByRole('button', { name: 'Nhận GR-000043' }))
    auth.user = { id: 2, roles: ['TEACHER'] }
    vi.mocked(getGradingQueue).mockResolvedValue({ ...queue, items: [], totalElements: 0 })
    view.rerender(content())
    await screen.findByText('Chưa có bài chờ nhận.')
    await act(async () => resolve(assigned))
    expect(screen.getByTestId('path')).toHaveTextContent('/teacher/grading')
    expect(screen.queryByText('Bài đã nhận')).not.toBeInTheDocument()
  })
})
