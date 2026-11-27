import { act, fireEvent, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { createMemoryRouter, MemoryRouter, RouterProvider, useLocation } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError } from '@/shared/api'
import { createGradingRequest, getGradingPolicy } from '../api/requests'
import { pendingRequest, policy } from '../test-fixtures'
import Scr032GradingRequestSubmissionPage from './Scr032GradingRequestSubmissionPage'

const auth = vi.hoisted(() => ({ status: 'authenticated', user: { id: 1, roles: ['LEARNER'] } }))
vi.mock('@/shared/auth', () => ({ useCurrentUser: () => auth }))
vi.mock('../api/requests', () => ({ getGradingPolicy: vi.fn(), createGradingRequest: vi.fn() }))

function page() {
  const router = createMemoryRouter(
    [
      { path: '/grading/new', element: <Scr032GradingRequestSubmissionPage /> },
      { path: '/grading/:id', element: <p>Trang chi tiết đích</p> },
    ],
    { initialEntries: ['/grading/new'] },
  )
  const view = render(<RouterProvider router={router} />)
  return { ...view, router }
}
async function prepare() {
  await screen.findByLabelText(/Bài viết tiếng Trung/)
  fireEvent.change(screen.getByLabelText(/Bài viết tiếng Trung/), {
    target: { value: '字'.repeat(50) },
  })
}
async function confirm() {
  await userEvent.click(screen.getByRole('button', { name: 'Gửi bài' }))
  await userEvent.click(screen.getByRole('button', { name: 'Xác nhận gửi bài' }))
}
beforeEach(() => {
  auth.status = 'authenticated'
  auth.user = { id: 1, roles: ['LEARNER'] }
  vi.mocked(getGradingPolicy).mockReset().mockResolvedValue(policy)
  vi.mocked(createGradingRequest).mockReset().mockResolvedValue(pendingRequest)
})
afterEach(() => vi.restoreAllMocks())

describe('SCR-032 learner submission', () => {
  it('loads policy, shows the actual terms/cost and never submits before explicit confirmation', async () => {
    page()
    await prepare()
    expect(screen.getByText('Chi phí: 1 lượt chấm hoặc 1 điểm')).toBeVisible()
    expect(screen.getByText(policy.refundTerms)).toBeVisible()
    expect(screen.getByText(/Hạn nhận bài: 48 giờ/)).toBeVisible()
    await userEvent.click(screen.getByRole('button', { name: 'Gửi bài' }))
    expect(createGradingRequest).not.toHaveBeenCalled()
    expect(screen.getByRole('dialog', { name: 'Xác nhận gửi bài' })).toBeVisible()
    await userEvent.keyboard('{Escape}')
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    expect(createGradingRequest).not.toHaveBeenCalled()
    expect(screen.getByLabelText(/Bài viết tiếng Trung/)).toHaveValue('字'.repeat(50))
  })
  it('counts Hanzi live, respects policy/code-point bounds and offers only HSK 1–3', async () => {
    page()
    await prepare()
    const field = screen.getByLabelText(/Bài viết tiếng Trung/),
      button = screen.getByRole('button', { name: 'Gửi bài' })
    fireEvent.change(field, { target: { value: '字'.repeat(49) + '㐀𠀀😀' } })
    expect(button).toBeDisabled()
    fireEvent.change(field, { target: { value: '字'.repeat(50) + '😀'.repeat(2950) } })
    expect(button).toBeEnabled()
    expect(screen.getByText('50 chữ Hán · 3000/3000 ký tự')).toBeVisible()
    expect(field).not.toHaveAttribute('maxlength')
    fireEvent.change(field, { target: { value: '字'.repeat(501) } })
    expect(button).toBeDisabled()
    fireEvent.change(field, { target: { value: '字'.repeat(50) } })
    fireEvent.change(screen.getByLabelText('Đề bài (tuỳ chọn)'), {
      target: { value: '😀'.repeat(501) },
    })
    expect(button).toBeDisabled()
    expect(screen.getAllByRole('option').map((option) => option.textContent)).toEqual([
      'Chưa chọn',
      'HSK 1',
      'HSK 2',
      'HSK 3',
    ])
  })
  it('sends only the contract body once, preserves line breaks and navigates after 201', async () => {
    let resolve!: (value: typeof pendingRequest) => void
    vi.mocked(createGradingRequest).mockImplementation(
      () =>
        new Promise((done) => {
          resolve = done
        }),
    )
    const { router } = page()
    await prepare()
    const raw = '\u3000' + '字'.repeat(50) + '\n词\u00a0'
    fireEvent.change(screen.getByLabelText(/Bài viết tiếng Trung/), { target: { value: raw } })
    fireEvent.change(screen.getByLabelText('Đề bài (tuỳ chọn)'), {
      target: { value: 'Đề tự soạn' },
    })
    await userEvent.selectOptions(screen.getByLabelText('Cấp HSK (tuỳ chọn)'), '2')
    await confirm()
    const submit = screen.getByRole('button', { name: 'Đang xử lý' })
    fireEvent.click(submit)
    expect(createGradingRequest).toHaveBeenCalledTimes(1)
    expect(createGradingRequest).toHaveBeenCalledWith({
      essay: raw,
      prompt: 'Đề tự soạn',
      hskLevel: 2,
    })
    await act(async () => resolve(pendingRequest))
    await screen.findByText('Trang chi tiết đích')
    expect(router.state.location.pathname).toBe('/grading/42')
  })
  it.each([
    [402, 'INSUFFICIENT_CREDITS'],
    [409, 'GRADING_DUPLICATE_ACTIVE'],
  ] as const)(
    'keeps the draft on %i and does not automatically resubmit',
    async (status, errorCode) => {
      vi.mocked(createGradingRequest).mockRejectedValue(
        new ApiError({ status, errorCode, message: 'Thông báo từ máy chủ.' }),
      )
      const storage = vi.spyOn(Storage.prototype, 'setItem')
      page()
      await prepare()
      await confirm()
      await screen.findByText('Thông báo từ máy chủ.')
      expect(screen.getByLabelText(/Bài viết tiếng Trung/)).toHaveValue('字'.repeat(50))
      expect(createGradingRequest).toHaveBeenCalledTimes(1)
      expect(storage).not.toHaveBeenCalled()
      if (status === 402)
        expect(screen.getByRole('link', { name: 'Nâng cấp PREMIUM' })).toHaveAttribute(
          'href',
          '/account/credits',
        )
    },
  )
  it('displays object hanziCount details and array field errors without discarding input', async () => {
    vi.mocked(createGradingRequest)
      .mockRejectedValueOnce(
        new ApiError({
          status: 422,
          errorCode: 'GRADING_INVALID_ESSAY',
          message: 'Sai độ dài.',
          details: { hanziCount: 49 },
        }),
      )
      .mockRejectedValueOnce(
        new ApiError({
          status: 422,
          errorCode: 'VALIDATION_FAILED',
          message: 'Sai đầu vào.',
          details: [{ field: 'prompt', message: 'Đề bài quá dài.' }],
        }),
      )
    page()
    await prepare()
    await confirm()
    await screen.findByText('Máy chủ đếm được 49 chữ Hán.')
    await confirm()
    await screen.findByText('Đề bài quá dài.')
    expect(screen.getByLabelText('Đề bài (tuỳ chọn)')).toHaveAttribute('aria-invalid', 'true')
  })
  it('shows policy loading/error/retry and cannot submit without the policy', async () => {
    vi.mocked(getGradingPolicy).mockRejectedValueOnce(
      new ApiError({ status: 0, errorCode: 'NETWORK_ERROR', message: 'Không tải được policy.' }),
    )
    page()
    expect(screen.getByText('Đang tải điều kiện chấm bài…')).toBeVisible()
    await screen.findByText('Không tải được policy.')
    expect(screen.queryByRole('button', { name: 'Gửi bài' })).not.toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'Thử lại' }))
    await prepare()
    expect(createGradingRequest).not.toHaveBeenCalled()
  })
  it('clears the draft on account change and ignores the old pending submission response', async () => {
    let resolve!: (value: typeof pendingRequest) => void
    vi.mocked(createGradingRequest).mockImplementation(
      () =>
        new Promise((done) => {
          resolve = done
        }),
    )
    function Location() {
      return <p data-testid="current-path">{useLocation().pathname}</p>
    }
    const content = () => (
      <MemoryRouter initialEntries={['/grading/new']}>
        <Scr032GradingRequestSubmissionPage />
        <Location />
      </MemoryRouter>
    )
    const { rerender } = render(content())
    await prepare()
    await confirm()
    auth.user = { id: 2, roles: ['LEARNER'] }
    rerender(content())
    await waitFor(() => expect(screen.getByLabelText(/Bài viết tiếng Trung/)).toHaveValue(''))
    await act(async () => resolve(pendingRequest))
    expect(screen.getByTestId('current-path')).toHaveTextContent('/grading/new')
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
  })
})
