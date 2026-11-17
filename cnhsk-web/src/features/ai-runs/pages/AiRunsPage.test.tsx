import { act, fireEvent, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError } from '@/shared/api'
import { listAiRuns, triggerAiRun } from '../api/ai-runs'
import type { RunPage } from '../types'
import AiRunsPage from './AiRunsPage'

vi.mock('../api/ai-runs', () => ({ listAiRuns: vi.fn(), triggerAiRun: vi.fn() }))
const page: RunPage = {
  enabled: true,
  monthTokens: 1234,
  page: 0,
  size: 20,
  totalElements: 1,
  items: [
    {
      id: 91,
      kid: 'KNOWLEDGE-1',
      status: 'FAILED',
      createdAt: '2026-11-17T00:00:00Z',
      completedAt: null,
      questionCount: 2,
      droppedCount: 1,
      inputTokens: 12,
      outputTokens: 34,
      error: null,
    },
  ],
}
beforeEach(() => {
  vi.mocked(listAiRuns).mockReset().mockResolvedValue(page)
  vi.mocked(triggerAiRun).mockReset().mockResolvedValue({ started: true })
})
function deferred<T>() {
  let resolve!: (value: T) => void
  const promise = new Promise<T>((done) => {
    resolve = done
  })
  return { promise, resolve }
}
describe('AI run history', () => {
  it('shows loading, all ten safe fields and neutral failed error, with no detail request', async () => {
    const pending = deferred<RunPage>()
    vi.mocked(listAiRuns).mockReturnValue(pending.promise)
    render(<AiRunsPage />)
    expect(screen.getByText('Đang tải lịch sử…')).toBeVisible()
    expect(screen.getByRole('button', { name: 'Chạy lô mới' })).toBeDisabled()
    await act(async () => pending.resolve(page))
    for (const text of [
      '#91',
      'KNOWLEDGE-1',
      'Thất bại',
      'Lỗi khác',
      'Chưa kết thúc',
      '2 đã tạo',
      '1 loại bỏ',
      '12 đầu vào',
      '34 đầu ra',
    ])
      expect(screen.getAllByText(text, { exact: false }).length).toBeGreaterThan(0)
    expect(screen.getByText('Token đã ghi nhận trong tháng')).toBeVisible()
    expect(screen.getByText(/Có thể thiếu token/)).toBeVisible()
    expect(listAiRuns).toHaveBeenCalledOnce()
  })
  it('keeps disabled history visible and blocks triggering', async () => {
    vi.mocked(listAiRuns).mockResolvedValue({ ...page, enabled: false })
    render(<AiRunsPage />)
    await screen.findByText('Đã tắt sinh câu hỏi')
    expect(screen.getAllByText('#91').length).toBeGreaterThan(0)
    expect(screen.getByRole('button', { name: 'Chạy lô mới' })).toBeDisabled()
  })
  it('recovers from GET failure without displaying server text', async () => {
    vi.mocked(listAiRuns).mockRejectedValueOnce(
      new ApiError({
        status: 500,
        errorCode: 'INTERNAL_ERROR',
        message: 'private provider canary',
        requestId: 'req-history',
      }),
    )
    render(<AiRunsPage />)
    await screen.findByText('Mã lỗi: req-history')
    expect(screen.queryByText(/private provider/)).not.toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'Thử lại' }))
    await screen.findByText('Token đã ghi nhận trong tháng')
  })
  it('shows empty state and resets page on status changes while retaining server monthly summary', async () => {
    vi.mocked(listAiRuns).mockResolvedValue({ ...page, totalElements: 41 })
    render(<AiRunsPage />)
    await screen.findByText('Token đã ghi nhận trong tháng')
    await userEvent.click(screen.getByRole('button', { name: 'Sau' }))
    await waitFor(() =>
      expect(listAiRuns).toHaveBeenLastCalledWith(
        { status: undefined, page: 1 },
        expect.any(AbortSignal),
      ),
    )
    vi.mocked(listAiRuns).mockResolvedValue({ ...page, items: [] })
    await userEvent.selectOptions(screen.getByLabelText('Trạng thái'), 'COMPLETED')
    await screen.findByText('Chưa có lô phù hợp')
    expect(listAiRuns).toHaveBeenLastCalledWith(
      { status: 'COMPLETED', page: 0 },
      expect.any(AbortSignal),
    )
  })
  it('ignores stale list results after a changed filter', async () => {
    const old = deferred<RunPage>()
    vi.mocked(listAiRuns)
      .mockReturnValueOnce(old.promise)
      .mockResolvedValue({ ...page, items: [] })
    render(<AiRunsPage />)
    await userEvent.selectOptions(screen.getByLabelText('Trạng thái'), 'RUNNING')
    await screen.findByText('Chưa có lô phù hợp')
    await act(async () => old.resolve(page))
    expect(screen.queryByText('#91')).not.toBeInTheDocument()
  })
  it('submits one POST for rapid clicks and keeps accepted notice when refresh fails', async () => {
    const accepted = deferred<{ started: true }>()
    vi.mocked(triggerAiRun).mockReturnValue(accepted.promise)
    render(<AiRunsPage />)
    await screen.findByText('Token đã ghi nhận trong tháng')
    const button = screen.getByRole('button', { name: 'Chạy lô mới' })
    fireEvent.click(button)
    fireEvent.click(button)
    expect(triggerAiRun).toHaveBeenCalledOnce()
    vi.mocked(listAiRuns).mockRejectedValue(new Error('private'))
    await act(async () => accepted.resolve({ started: true }))
    await screen.findByText(/Đã tiếp nhận yêu cầu/)
    await screen.findByText('Không thể tải lịch sử lô. Vui lòng thử lại.')
    expect(screen.getByText(/chưa hoàn tất/)).toBeVisible()
    expect(triggerAiRun).toHaveBeenCalledOnce()
  })
  it.each([
    ['AI_GENERATION_BUSY', 409, 'Một lô khác đang chạy.'],
    ['AI_GENERATION_DISABLED', 409, 'Chức năng chạy lô đã tắt.'],
    ['UNAUTHENTICATED', 401, 'Phiên đăng nhập đã hết hạn.'],
    ['FORBIDDEN', 403, 'Bạn không có quyền chạy lô.'],
    ['INTERNAL_ERROR', 500, 'Chưa xác nhận được yêu cầu.'],
    ['NETWORK_ERROR', 0, 'Chưa xác nhận được yêu cầu.'],
  ])('handles %s safely with request ID and no POST retry', async (errorCode, status, message) => {
    vi.mocked(triggerAiRun).mockRejectedValue(
      new ApiError({ errorCode, status, message: 'private canary', requestId: 'req-trigger' }),
    )
    render(<AiRunsPage />)
    await screen.findByText('Token đã ghi nhận trong tháng')
    await userEvent.click(screen.getByRole('button', { name: 'Chạy lô mới' }))
    await screen.findByText(message, { exact: false })
    expect(screen.getByText('Mã lỗi: req-trigger')).toBeVisible()
    expect(screen.queryByText('private canary')).not.toBeInTheDocument()
    expect(triggerAiRun).toHaveBeenCalledOnce()
  })
  it('aborts unmounted trigger and ignores late acceptance', async () => {
    const accepted = deferred<{ started: true }>()
    vi.mocked(triggerAiRun).mockReturnValue(accepted.promise)
    const view = render(<AiRunsPage />)
    await screen.findByText('Token đã ghi nhận trong tháng')
    await userEvent.click(screen.getByRole('button', { name: 'Chạy lô mới' }))
    const signal = vi.mocked(triggerAiRun).mock.calls[0]![0]!
    view.unmount()
    expect(signal.aborted).toBe(true)
    await act(async () => accepted.resolve({ started: true }))
    expect(listAiRuns).toHaveBeenCalledOnce()
  })
})
