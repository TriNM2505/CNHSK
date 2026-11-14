import { StrictMode } from 'react'
import { act, render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { createMemoryRouter, RouterProvider } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError } from '@/shared/api'
import { AuthContext, type AuthContextValue } from '@/shared/auth/AuthContext'
import { answerItem, abandonSession, finishSession, getSession } from '@/features/practice/api'
import { startQuizAttempt } from '../api/quizzes'
import { quizEngineSession, quizSession } from '../test-fixtures'
import type { QuizSession } from '../types'
import Scr044QuizAttemptPage from './Scr044QuizAttemptPage'

vi.mock('../api/quizzes', () => ({ startQuizAttempt: vi.fn() }))
vi.mock('@/features/practice/api', () => ({
  answerItem: vi.fn(),
  abandonSession: vi.fn(),
  finishSession: vi.fn(),
  getSession: vi.fn(),
  findActiveSession: vi.fn(),
}))
const auth: AuthContextValue = {
  user: {
    id: 19,
    email: 'quiz@example.test',
    displayName: 'Người học mẫu',
    emailVerified: true,
    roles: ['LEARNER'],
  },
  status: 'authenticated',
  error: null,
  reloadCurrentUser: async () => undefined,
  setAuthenticatedUser: () => undefined,
  signOut: async () => undefined,
}
function mount(state?: unknown, path = '/community/quizzes/43/play?sessionId=999', strict = false) {
  const router = createMemoryRouter(
    [
      { path: '/community/quizzes/:topicId/play', element: <Scr044QuizAttemptPage /> },
      { path: '/community/quizzes/attempts/:sessionId', element: <h1>Kết quả đã nộp</h1> },
      { path: '/community/quizzes', element: <h1>Danh sách quiz</h1> },
      { path: '/login', element: <h1>Đăng nhập</h1> },
      { path: '/403', element: <h1>Không có quyền</h1> },
    ],
    {
      initialEntries: [
        {
          pathname: path.split('?')[0],
          search: path.includes('?') ? '?' + path.split('?')[1] : '',
          state,
        },
      ],
    },
  )
  const content = (
    <AuthContext.Provider value={auth}>
      <RouterProvider router={router} />
    </AuthContext.Provider>
  )
  const view = render(strict ? <StrictMode>{content}</StrictMode> : content)
  return { router, ...view }
}
function failure(status: number, errorCode: string) {
  return new ApiError({ status, errorCode, message: 'Lỗi mạng thử nghiệm.' })
}
function deferred<T>() {
  let resolve!: (value: T) => void
  const promise = new Promise<T>((done) => {
    resolve = done
  })
  return { promise, resolve }
}
async function loaded() {
  await screen.findByRole('heading', { name: 'Làm quiz theo chủ đề' })
}
describe('SCR-044 quiz engine flow', () => {
  afterEach(() => vi.restoreAllMocks())
  beforeEach(() => {
    vi.spyOn(Date, 'now').mockReturnValue(Date.UTC(2026, 10, 14, 4, 1, 5))
    vi.mocked(startQuizAttempt).mockReset().mockResolvedValue(quizSession)
    vi.mocked(getSession)
      .mockReset()
      .mockResolvedValue({
        ...quizEngineSession,
        servedAt: new Date(Date.now() - 65000).toISOString(),
      })
    vi.mocked(answerItem).mockReset().mockResolvedValue({
      no: 1,
      correct: true,
      correctAnswer: 'SECRET',
      explanation: 'Không được hiện trước nộp',
    })
    vi.mocked(finishSession)
      .mockReset()
      .mockResolvedValue({ sessionId: 101, status: 'SUBMITTED', suspicious: false, items: [] })
    vi.mocked(abandonSession).mockReset().mockResolvedValue(undefined)
  })
  it('resumes from topic after state loss, ignores URL id and deduplicates StrictMode POST', async () => {
    mount(undefined, undefined, true)
    await loaded()
    expect(startQuizAttempt).toHaveBeenCalledTimes(1)
    expect(startQuizAttempt).toHaveBeenCalledWith(43)
    expect(getSession).toHaveBeenCalledWith(101, expect.any(AbortSignal))
    expect(getSession).not.toHaveBeenCalledWith(999, expect.anything())
    expect(screen.getByText('Lượt tính hạng')).toBeVisible()
    expect(screen.getByText(/1 phút 5 giây/)).toBeVisible()
    expect(screen.queryByText('SECRET')).not.toBeInTheDocument()
  })
  it('uses matching state for handoff but GETs server drafts and source', async () => {
    vi.mocked(getSession).mockResolvedValue({
      ...quizEngineSession,
      source: 'QUIZ_RETRY',
      answered: [{ no: 1, answer: 'B' }],
    })
    mount({ quizSession })
    await loaded()
    expect(startQuizAttempt).not.toHaveBeenCalled()
    expect(screen.getByText('Làm lại — không tính hạng')).toBeVisible()
    expect(screen.getByText('Đã trả lời 1/2 câu')).toBeVisible()
    expect(screen.getByRole('button', { name: /^B\s*lửa/ })).toHaveAttribute('aria-pressed', 'true')
  })
  it('rejects state for a different topic and fetches the session returned by POST', async () => {
    mount({ quizSession: { ...quizSession, topicId: 44, sessionId: 888 } })
    await loaded()
    expect(startQuizAttempt).toHaveBeenCalledWith(43)
    expect(getSession).not.toHaveBeenCalledWith(888, expect.anything())
  })
  it('changes questions and choices while hiding all deferred feedback', async () => {
    const user = userEvent.setup()
    mount({ quizSession })
    await loaded()
    await user.click(screen.getByRole('button', { name: /^A\s*nước/ }))
    await screen.findByText('Đã lưu lựa chọn.')
    expect(answerItem).toHaveBeenCalledWith(
      101,
      expect.objectContaining({ no: 1, answer: 'A', durationMs: expect.any(Number) }),
    )
    expect(screen.queryByText('SECRET')).not.toBeInTheDocument()
    expect(screen.queryByText('Không được hiện trước nộp')).not.toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Câu tiếp theo' }))
    expect(screen.getByText('Chọn nghĩa của 火.')).toBeVisible()
    await user.click(screen.getByRole('button', { name: /Câu 1 — đã trả lời/ }))
    await user.click(screen.getByRole('button', { name: /^B\s*lửa/ }))
    await waitFor(() =>
      expect(answerItem).toHaveBeenLastCalledWith(
        101,
        expect.objectContaining({ no: 1, answer: 'B' }),
      ),
    )
  })
  it('confirms unanswered questions in-page and sends a failed draft in finish', async () => {
    vi.mocked(answerItem).mockRejectedValue(failure(0, 'NETWORK_ERROR'))
    const user = userEvent.setup()
    const { router } = mount({ quizSession })
    await loaded()
    await user.click(screen.getByRole('button', { name: /^A\s*nước/ }))
    await screen.findByText(/Chưa lưu được lựa chọn/)
    await user.click(screen.getByRole('button', { name: 'Nộp bài' }))
    const confirm = screen.getByRole('region', { name: 'Xác nhận nộp bài' })
    expect(within(confirm).getByText(/Còn 1 câu chưa trả lời/)).toBeVisible()
    expect(finishSession).not.toHaveBeenCalled()
    await user.click(within(confirm).getByRole('button', { name: 'Xác nhận nộp bài' }))
    await screen.findByRole('heading', { name: 'Kết quả đã nộp' })
    expect(router.state.location.pathname).toBe('/community/quizzes/attempts/101')
    expect(finishSession).toHaveBeenCalledTimes(1)
    expect(finishSession).toHaveBeenCalledWith(101, {
      answers: [expect.objectContaining({ no: 1, answer: 'A' })],
    })
  })
  it('submits all server-restored answers without overwriting their evidence', async () => {
    vi.mocked(getSession).mockResolvedValue({
      ...quizEngineSession,
      answered: [
        { no: 1, answer: 'A' },
        { no: 2, answer: 'B' },
      ],
    })
    const user = userEvent.setup()
    mount({ quizSession })
    await loaded()
    await user.click(screen.getByRole('button', { name: 'Nộp bài' }))
    await screen.findByRole('heading', { name: 'Kết quả đã nộp' })
    expect(finishSession).toHaveBeenCalledTimes(1)
    expect(finishSession).toHaveBeenCalledWith(101, undefined)
  })
  it('sends only one finish while confirmation is pending', async () => {
    const finish = deferred<{
      sessionId: number
      status: 'SUBMITTED'
      suspicious: boolean
      items: []
    }>()
    vi.mocked(finishSession).mockReturnValue(finish.promise)
    const user = userEvent.setup()
    mount({ quizSession })
    await loaded()
    await user.click(screen.getByRole('button', { name: 'Nộp bài' }))
    const confirm = screen.getByRole('button', { name: 'Xác nhận nộp bài' })
    await user.dblClick(confirm)
    expect(confirm).toBeDisabled()
    expect(finishSession).toHaveBeenCalledTimes(1)
    await act(async () =>
      finish.resolve({ sessionId: 101, status: 'SUBMITTED', suspicious: false, items: [] }),
    )
    await screen.findByRole('heading', { name: 'Kết quả đã nộp' })
  })
  it('retries a failed choice explicitly without losing its selection', async () => {
    vi.mocked(answerItem)
      .mockRejectedValueOnce(failure(0, 'NETWORK_ERROR'))
      .mockResolvedValue({ no: 1 })
    const user = userEvent.setup()
    mount({ quizSession })
    await loaded()
    await user.click(screen.getByRole('button', { name: /^A\s*nước/ }))
    await user.click(await screen.findByRole('button', { name: 'Lưu lại lựa chọn' }))
    await screen.findByText('Đã lưu lựa chọn.')
    expect(answerItem).toHaveBeenCalledTimes(2)
    expect(screen.getByRole('button', { name: /^A\s*nước/ })).toHaveAttribute(
      'aria-pressed',
      'true',
    )
  })
  it('disables submit while an answer is pending and supports explicit save retry', async () => {
    const save = deferred<{ no: number }>()
    vi.mocked(answerItem).mockReturnValueOnce(save.promise)
    const user = userEvent.setup()
    mount({ quizSession })
    await loaded()
    await user.click(screen.getByRole('button', { name: /^A\s*nước/ }))
    expect(screen.getByRole('button', { name: 'Nộp bài' })).toBeDisabled()
    expect(screen.getByRole('button', { name: /^B\s*lửa/ })).toBeDisabled()
    await act(async () => save.resolve({ no: 1 }))
    expect(screen.getByRole('button', { name: 'Nộp bài' })).toBeEnabled()
  })
  it('cancels exit, then abandons only after explicit confirmation', async () => {
    const user = userEvent.setup()
    mount({ quizSession })
    await loaded()
    await user.click(screen.getByRole('button', { name: 'Thoát' }))
    expect(abandonSession).not.toHaveBeenCalled()
    await user.keyboard('{Escape}')
    expect(screen.queryByRole('region', { name: 'Xác nhận thoát quiz' })).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Thoát' })).toHaveFocus()
    await user.click(screen.getByRole('button', { name: 'Thoát' }))
    await user.click(screen.getByRole('button', { name: 'Xác nhận thoát' }))
    await screen.findByRole('heading', { name: 'Danh sách quiz' })
    expect(abandonSession).toHaveBeenCalledTimes(1)
    expect(abandonSession).toHaveBeenCalledWith(101)
    expect(finishSession).not.toHaveBeenCalled()
  })
  it('blocks internal navigation until abandon succeeds, preserving the quiz on failure', async () => {
    vi.mocked(abandonSession).mockRejectedValueOnce(failure(0, 'NETWORK_ERROR'))
    const user = userEvent.setup()
    const { router } = mount({ quizSession })
    await loaded()
    await act(async () => {
      void router.navigate('/community/quizzes')
    })
    await user.click(screen.getByRole('button', { name: 'Xác nhận thoát' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('Lỗi mạng thử nghiệm.')
    expect(router.state.location.pathname).toContain('/play')
    await user.click(screen.getByRole('button', { name: 'Xác nhận thoát' }))
    await screen.findByRole('heading', { name: 'Danh sách quiz' })
    expect(abandonSession).toHaveBeenCalledTimes(2)
  })
  it.each(['EXPIRED', 'ABANDONED'] as const)('keeps %s sessions read-only', async (status) => {
    vi.mocked(getSession).mockResolvedValue({ ...quizEngineSession, status })
    mount({ quizSession })
    await screen.findByText(status === 'EXPIRED' ? 'Lượt quiz đã hết hạn' : 'Lượt quiz đã bỏ dở')
    expect(screen.queryByRole('button', { name: 'Nộp bài' })).not.toBeInTheDocument()
    expect(answerItem).not.toHaveBeenCalled()
  })
  it('opens an already submitted session result without a second finish', async () => {
    vi.mocked(getSession).mockResolvedValue({ ...quizEngineSession, status: 'SUBMITTED' })
    mount({ quizSession })
    await screen.findByRole('heading', { name: 'Kết quả đã nộp' })
    expect(finishSession).not.toHaveBeenCalled()
  })
  it('rejects a non-quiz engine session without offering answer controls', async () => {
    vi.mocked(getSession).mockResolvedValue({ ...quizEngineSession, source: 'MOCK_EXAM' })
    mount({ quizSession })
    await screen.findByText('Lượt quiz không khả dụng cho chủ đề này.')
    expect(screen.queryByRole('button', { name: /^A\s*nước/ })).not.toBeInTheDocument()
  })
  it.each([
    [401, 'UNAUTHENTICATED', 'Đăng nhập'],
    [403, 'ATTEMPT_NOT_OWNED', 'Không có quyền'],
  ] as const)('handles resume %s', async (status, code, heading) => {
    vi.mocked(startQuizAttempt).mockRejectedValue(failure(status, code))
    mount()
    await screen.findByRole('heading', { name: heading })
    expect(getSession).not.toHaveBeenCalled()
  })
  it.each([
    [422, 'QUIZ_NOT_AVAILABLE'],
    [429, 'RATE_LIMITED'],
  ] as const)('shows resume %s without automatic POST retries', async (status, code) => {
    vi.mocked(startQuizAttempt).mockRejectedValue(failure(status, code))
    mount()
    await screen.findByRole('heading', { name: 'Chưa thể mở quiz' })
    expect(startQuizAttempt).toHaveBeenCalledOnce()
    if (status === 429)
      expect(screen.queryByRole('button', { name: 'Thử lại' })).not.toBeInTheDocument()
  })
  it('does not load or start a malformed topic identifier', () => {
    mount(undefined, '/community/quizzes/invalid/play')
    expect(screen.getByText('Mã chủ đề không hợp lệ.')).toBeVisible()
    expect(startQuizAttempt).not.toHaveBeenCalled()
  })
  it('ignores a late resume response after unmount', async () => {
    const start = deferred<QuizSession>()
    vi.mocked(startQuizAttempt).mockReturnValue(start.promise)
    const view = mount()
    view.unmount()
    await act(async () => start.resolve(quizSession))
    expect(getSession).not.toHaveBeenCalled()
  })
})
