import { act, fireEvent, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { createMemoryRouter, RouterProvider } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError } from '@/shared/api'
import { cancelGradingRequest, getMyGradingRequest } from '../api/requests'
import { completedRequest, pendingRequest } from '../test-fixtures'
import type { GradingRequest, RequestStatus } from '../types'
import Scr034GradingRequestDetailPage from './Scr034GradingRequestDetailPage'

vi.mock('@/shared/auth', () => ({
  useCurrentUser: () => ({ status: 'authenticated', user: { id: 1, roles: ['LEARNER'] } }),
}))
vi.mock('../api/requests', () => ({ getMyGradingRequest: vi.fn(), cancelGradingRequest: vi.fn() }))
function page(id = '42') {
  const router = createMemoryRouter(
    [{ path: '/grading/:id', element: <Scr034GradingRequestDetailPage /> }],
    { initialEntries: [`/grading/${id}`] },
  )
  return { router, ...render(<RouterProvider router={router} />) }
}
beforeEach(() => {
  vi.mocked(getMyGradingRequest).mockReset().mockResolvedValue(pendingRequest)
  vi.mocked(cancelGradingRequest)
    .mockReset()
    .mockResolvedValue({ ...pendingRequest, status: 'FAILED', refunded: true })
})

describe('SCR-034 own grading request', () => {
  it('displays full result and corrections as text, including a zero score', async () => {
    const text = '<script>alert("fixture")</script>\nNội dung tự soạn.'
    vi.mocked(getMyGradingRequest).mockResolvedValue({
      ...completedRequest,
      essay: text,
      prompt: text,
      result: {
        ...completedRequest.result!,
        score: 0,
        feedback: text,
        corrections: [{ original: text, corrected: text, note: text }],
      },
    })
    const { container } = page()
    await screen.findByRole('heading', { name: 'Kết quả chấm bài' })
    expect(screen.getByText('0/100')).toBeVisible()
    for (const label of ['Ngữ pháp', 'Từ vựng', 'Mạch lạc', 'Chữ viết'])
      expect(screen.getByText(label)).toBeVisible()
    expect(
      screen.getAllByText((_, node) => node?.tagName === 'P' && node.textContent === text),
    ).toHaveLength(3)
    expect(container.querySelector('script')).toBeNull()
    expect(screen.queryByRole('button', { name: 'Huỷ yêu cầu' })).not.toBeInTheDocument()
    expect(getMyGradingRequest).toHaveBeenCalledWith('42', expect.any(AbortSignal))
  })
  it.each(['PENDING', 'ASSIGNED', 'FAILED', 'EXPIRED'] as RequestStatus[])(
    'does not reveal a result before COMPLETED (%s)',
    async (status) => {
      vi.mocked(getMyGradingRequest).mockResolvedValue({ ...completedRequest, status })
      page()
      await screen.findByRole('heading', { name: 'GR-000042' })
      expect(screen.queryByRole('heading', { name: 'Kết quả chấm bài' })).not.toBeInTheDocument()
      expect(screen.queryByText(completedRequest.result!.feedback)).not.toBeInTheDocument()
      if (status === 'PENDING')
        expect(screen.getByRole('button', { name: 'Huỷ yêu cầu' })).toBeVisible()
      else expect(screen.queryByRole('button', { name: 'Huỷ yêu cầu' })).not.toBeInTheDocument()
    },
  )
  it('requires confirmation and applies the cancel response without an extra GET', async () => {
    page()
    await screen.findByRole('button', { name: 'Huỷ yêu cầu' })
    await userEvent.click(screen.getByRole('button', { name: 'Huỷ yêu cầu' }))
    expect(cancelGradingRequest).not.toHaveBeenCalled()
    await userEvent.click(screen.getByRole('button', { name: 'Xác nhận huỷ' }))
    await screen.findByText('Hoàn lượt: Đã hoàn')
    expect(cancelGradingRequest).toHaveBeenCalledTimes(1)
    expect(cancelGradingRequest).toHaveBeenCalledWith('42')
    expect(getMyGradingRequest).toHaveBeenCalledTimes(1)
    expect(screen.queryByRole('button', { name: 'Huỷ yêu cầu' })).not.toBeInTheDocument()
  })
  it('keeps cancellation 409 visible, reloads GET and never repeats POST', async () => {
    vi.mocked(cancelGradingRequest).mockRejectedValue(
      new ApiError({
        status: 409,
        errorCode: 'GRADING_NOT_CANCELLABLE',
        message: 'Bài đã có giáo viên nhận.',
      }),
    )
    vi.mocked(getMyGradingRequest)
      .mockResolvedValueOnce(pendingRequest)
      .mockResolvedValueOnce({ ...pendingRequest, status: 'ASSIGNED' })
    page()
    await screen.findByRole('button', { name: 'Huỷ yêu cầu' })
    await userEvent.click(screen.getByRole('button', { name: 'Huỷ yêu cầu' }))
    await userEvent.click(screen.getByRole('button', { name: 'Xác nhận huỷ' }))
    await screen.findByText('Đang chấm')
    expect(screen.getByText('Bài đã có giáo viên nhận.')).toBeVisible()
    expect(getMyGradingRequest).toHaveBeenCalledTimes(2)
    expect(cancelGradingRequest).toHaveBeenCalledTimes(1)
    expect(screen.queryByRole('button', { name: 'Huỷ yêu cầu' })).not.toBeInTheDocument()
  })
  it('renders nullable fields and no corrections without inventing missing values', async () => {
    vi.mocked(getMyGradingRequest).mockResolvedValue({
      ...completedRequest,
      prompt: null,
      hskLevel: null,
      deadline: null,
      result: { ...completedRequest.result!, corrections: [] },
    })
    page()
    await screen.findByText('Không có mục sửa lỗi.')
    expect(screen.queryByRole('heading', { name: 'Đề bài' })).not.toBeInTheDocument()
    expect(screen.queryByText(/^HSK /)).not.toBeInTheDocument()
    expect(screen.queryByText(/^Hạn chấm bài:/)).not.toBeInTheDocument()
  })
  it('shows ownership 403 without showing content or treating it as 404', async () => {
    vi.mocked(getMyGradingRequest).mockRejectedValue(
      new ApiError({
        status: 403,
        errorCode: 'GRADING_REQUEST_NOT_OWNED',
        message: 'Không có quyền truy cập yêu cầu chấm bài.',
      }),
    )
    page('invalid')
    await screen.findByText('Không có quyền truy cập yêu cầu chấm bài.')
    expect(screen.queryByText(pendingRequest.essay)).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Huỷ yêu cầu' })).not.toBeInTheDocument()
    expect(getMyGradingRequest).toHaveBeenCalledWith('invalid', expect.any(AbortSignal))
  })
  it('shows loading/error/retry', async () => {
    vi.mocked(getMyGradingRequest).mockRejectedValueOnce(
      new ApiError({ status: 0, errorCode: 'NETWORK_ERROR', message: 'Lỗi chi tiết.' }),
    )
    page()
    expect(screen.getByText('Đang tải bài viết…')).toBeVisible()
    await screen.findByText('Lỗi chi tiết.')
    await userEvent.click(screen.getByRole('button', { name: 'Thử lại' }))
    await screen.findByRole('heading', { name: 'GR-000042' })
  })
  it('ignores an old cancel response after changing request ID and prevents duplicate POST', async () => {
    let resolve!: (value: GradingRequest) => void
    vi.mocked(cancelGradingRequest).mockImplementation(
      () =>
        new Promise((done) => {
          resolve = done
        }),
    )
    const { router } = page()
    await screen.findByRole('button', { name: 'Huỷ yêu cầu' })
    await userEvent.click(screen.getByRole('button', { name: 'Huỷ yêu cầu' }))
    await userEvent.click(screen.getByRole('button', { name: 'Xác nhận huỷ' }))
    fireEvent.click(screen.getByRole('button', { name: 'Đang xử lý' }))
    expect(cancelGradingRequest).toHaveBeenCalledTimes(1)
    vi.mocked(getMyGradingRequest).mockResolvedValue({
      ...pendingRequest,
      id: 43,
      reference: 'GR-000043',
      essay: 'Bài khác',
    })
    await act(async () => {
      await router.navigate('/grading/43')
    })
    await screen.findByText('Bài khác')
    await act(async () => resolve({ ...pendingRequest, status: 'FAILED', refunded: true }))
    await waitFor(() => expect(screen.getByRole('heading', { name: 'GR-000043' })).toBeVisible())
    expect(screen.queryByText('Hoàn lượt: Đã hoàn')).not.toBeInTheDocument()
  })
})
