import { act, render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { createMemoryRouter, RouterProvider } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError } from '@/shared/api'
import { AuthContext, type AuthContextValue } from '@/shared/auth/AuthContext'
import { getQuizResult, startQuizAttempt } from '../api/quizzes'
import { getQuizTopTen } from '../api/leaderboards'
import { quizLeaderboard, quizResult, quizSession } from '../test-fixtures'
import type { QuizResult, QuizSession } from '../types'
import Scr045QuizResultPage from './Scr045QuizResultPage'

const definitions = vi.hoisted(() => [] as { path: string }[])
vi.mock('@/app/routeAccess', () => ({ appRouteDefinitions: definitions }))
vi.mock('../api/quizzes', () => ({ getQuizResult: vi.fn(), startQuizAttempt: vi.fn() }))
vi.mock('../api/leaderboards', () => ({ getQuizTopTen: vi.fn() }))
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
function mount(id = '101') {
  const router = createMemoryRouter(
    [
      { path: '/community/quizzes/attempts/:sessionId', element: <Scr045QuizResultPage /> },
      { path: '/community/quizzes/:topicId/play', element: <h1>Làm lại quiz</h1> },
      { path: '/community/quizzes', element: <h1>Danh sách quiz</h1> },
      { path: '/login', element: <h1>Đăng nhập</h1> },
      { path: '/403', element: <h1>Không có quyền</h1> },
    ],
    { initialEntries: ['/community/quizzes/attempts/' + id] },
  )
  return {
    router,
    ...render(
      <AuthContext.Provider value={auth}>
        <RouterProvider router={router} />
      </AuthContext.Provider>,
    ),
  }
}
function failure(status: number, errorCode: string) {
  return new ApiError({ status, errorCode, message: 'Lỗi kiểm thử.' })
}
function deferred<T>() {
  let resolve!: (value: T) => void
  const promise = new Promise<T>((done) => {
    resolve = done
  })
  return { promise, resolve }
}
describe('SCR-045 owned quiz result', () => {
  beforeEach(() => {
    vi.mocked(getQuizResult).mockReset().mockResolvedValue(quizResult)
    vi.mocked(getQuizTopTen).mockReset().mockResolvedValue(quizLeaderboard)
    vi.mocked(startQuizAttempt)
      .mockReset()
      .mockResolvedValue({ ...quizSession, rankingEligible: false })
    definitions.splice(0, definitions.length, { path: '/community/quizzes' })
  })
  it('shows server score/count/time, answer labels/explanations, first rank and only ten entries', async () => {
    mount()
    await screen.findByRole('heading', { name: 'Kết quả quiz theo chủ đề' })
    expect(screen.getByText('50/100 điểm')).toBeVisible()
    expect(screen.getByText('Đúng: 1 câu · Sai: 1 câu')).toBeVisible()
    expect(screen.getByText('Thời gian: 1 phút 5 giây')).toBeVisible()
    expect(screen.getByText('Hạng lần đầu: #12/240')).toBeVisible()
    expect(screen.getByText('Tính hạng')).toBeVisible()
    const answers = screen.getByRole('region', { name: 'Đáp án quiz' })
    expect(within(answers).getByText('Đúng', { exact: true })).toBeVisible()
    expect(within(answers).getByText('Bỏ trống — Sai')).toBeVisible()
    expect(within(answers).getByText(/A. nước — Đã chọn — Đáp án đúng/)).toBeVisible()
    expect(within(answers).getByText('Giải thích: 水 chỉ nước.')).toBeVisible()
    const top = screen.getByRole('region', { name: 'Top 10 chủ đề' })
    await waitFor(() => expect(within(top).getAllByRole('listitem')).toHaveLength(10))
    expect(screen.queryByText(/Học viên mẫu 11/)).not.toBeInTheDocument()
    expect(getQuizTopTen).toHaveBeenCalledWith(43, expect.any(AbortSignal))
    expect(screen.queryByRole('link', { name: 'Bảng xếp hạng chủ đề' })).not.toBeInTheDocument()
    expect(screen.getByText('Quiz không ảnh hưởng tiến độ học.')).toBeVisible()
  })
  it.each([
    [false, false, 'Làm lại — không tính hạng'],
    [true, true, 'Bị đánh dấu nghi vấn — không tính hạng'],
    [false, true, 'Bị đánh dấu nghi vấn — không tính hạng'],
  ] as const)(
    'uses authoritative result ranking=%s suspicious=%s',
    async (rankingEligible, suspicious, text) => {
      vi.mocked(getQuizResult).mockResolvedValue({ ...quizResult, rankingEligible, suspicious })
      mount()
      expect(await screen.findByText(text as string)).toBeVisible()
    },
  )
  it('preserves first score/time but hides rank when null, even while top 10 fails', async () => {
    vi.mocked(getQuizResult).mockResolvedValue({
      ...quizResult,
      firstAttempt: { score: 0, durationMs: 0, rank: null, total: null },
    })
    vi.mocked(getQuizTopTen).mockRejectedValue(failure(404, 'NOT_FOUND'))
    const user = userEvent.setup()
    mount()
    await screen.findByText('Bảng xếp hạng chưa khả dụng')
    expect(screen.getByText('50/100 điểm')).toBeVisible()
    expect(screen.getByText('Điểm lượt đầu: 0')).toBeVisible()
    expect(screen.getByText('Thời gian lượt đầu: 0 phút 0 giây')).toBeVisible()
    expect(screen.queryByText(/Hạng lần đầu:/)).not.toBeInTheDocument()
    vi.mocked(getQuizTopTen).mockResolvedValue(quizLeaderboard)
    await user.click(
      within(screen.getByRole('region', { name: 'Top 10 chủ đề' })).getByRole('button', {
        name: 'Thử lại',
      }),
    )
    await screen.findByText('#1 · Học viên mẫu 1')
    expect(getQuizResult).toHaveBeenCalledOnce()
  })
  it('shows independent empty top ten and handles no first attempt', async () => {
    vi.mocked(getQuizResult).mockResolvedValue({ ...quizResult, firstAttempt: null })
    vi.mocked(getQuizTopTen).mockResolvedValue({ ...quizLeaderboard, entries: [] })
    mount()
    await screen.findByText('Chưa có lượt tính hạng')
    expect(screen.getByText('50/100 điểm')).toBeVisible()
    expect(screen.queryByRole('heading', { name: 'Lượt đầu tiên' })).not.toBeInTheDocument()
  })
  it('keeps result and retry visible while top ten is still loading', async () => {
    const top = deferred<typeof quizLeaderboard>()
    vi.mocked(getQuizTopTen).mockReturnValue(top.promise)
    mount()
    await screen.findByText('50/100 điểm')
    expect(screen.getByLabelText('Đang tải bảng xếp hạng')).toBeVisible()
    expect(screen.getByRole('button', { name: 'Làm lại' })).toBeEnabled()
    await act(async () => top.resolve(quizLeaderboard))
    await screen.findByText('#1 · Học viên mẫu 1')
  })
  it('shows the leaderboard link only when the route is registered', async () => {
    definitions.push({ path: '/leaderboards/topics' })
    mount()
    expect(await screen.findByRole('link', { name: 'Bảng xếp hạng chủ đề' })).toHaveAttribute(
      'href',
      '/leaderboards/topics?topicId=43',
    )
  })
  it('starts a retry once and passes the server session into the play route', async () => {
    const start = deferred<QuizSession>()
    vi.mocked(startQuizAttempt).mockReturnValue(start.promise)
    const user = userEvent.setup()
    const { router } = mount()
    await screen.findByText('50/100 điểm')
    const button = screen.getByRole('button', { name: 'Làm lại' })
    await user.dblClick(button)
    expect(startQuizAttempt).toHaveBeenCalledTimes(1)
    expect(startQuizAttempt).toHaveBeenCalledWith(43)
    expect(button).toBeDisabled()
    const session = { ...quizSession, rankingEligible: false }
    await act(async () => start.resolve(session))
    await screen.findByRole('heading', { name: 'Làm lại quiz' })
    expect(router.state.location.pathname + router.state.location.search).toBe(
      '/community/quizzes/43/play?sessionId=101',
    )
    expect(router.state.location.state).toEqual({ quizSession: session })
  })
  it.each([
    [422, 'QUIZ_NOT_AVAILABLE'],
    [429, 'RATE_LIMITED'],
  ] as const)('keeps the result on retry %s without auto-retry', async (status, code) => {
    vi.mocked(startQuizAttempt).mockRejectedValue(failure(status, code))
    const user = userEvent.setup()
    mount()
    await screen.findByText('50/100 điểm')
    await user.click(screen.getByRole('button', { name: 'Làm lại' }))
    expect(await screen.findByRole('alert')).toBeVisible()
    expect(screen.getByText('50/100 điểm')).toBeVisible()
    expect(startQuizAttempt).toHaveBeenCalledOnce()
  })
  it.each([
    [401, 'UNAUTHENTICATED', 'Đăng nhập'],
    [403, 'ATTEMPT_NOT_OWNED', 'Không có quyền'],
  ] as const)('handles result %s without rendering answers', async (status, code, heading) => {
    vi.mocked(getQuizResult).mockRejectedValue(failure(status, code))
    mount()
    await screen.findByRole('heading', { name: heading })
    expect(screen.queryByRole('region', { name: 'Đáp án quiz' })).not.toBeInTheDocument()
    expect(getQuizTopTen).not.toHaveBeenCalled()
  })
  it.each([
    [422, 'ATTEMPT_NOT_SUBMITTED', 'Lượt quiz này chưa được nộp.'],
    [404, 'QUIZ_ATTEMPT_NOT_FOUND', 'Không tìm thấy kết quả quiz này.'],
  ] as const)('handles result %s without fabricating answers', async (status, code, text) => {
    vi.mocked(getQuizResult).mockRejectedValue(failure(status, code))
    mount()
    expect(await screen.findByText(text)).toBeVisible()
    expect(screen.queryByRole('region', { name: 'Đáp án quiz' })).not.toBeInTheDocument()
  })
  it('shows loading and ignores a late result after unmount', async () => {
    const result = deferred<QuizResult>()
    vi.mocked(getQuizResult).mockReturnValue(result.promise)
    const view = mount()
    expect(screen.getByRole('status', { name: 'Đang tải kết quả quiz' })).toBeVisible()
    const signal = vi.mocked(getQuizResult).mock.calls[0]?.[1]
    view.unmount()
    expect(signal?.aborted).toBe(true)
    await act(async () => result.resolve(quizResult))
    expect(getQuizTopTen).not.toHaveBeenCalled()
  })
  it('rejects invalid session ids without network', () => {
    mount('invalid')
    expect(screen.getByText('Mã lượt quiz không hợp lệ.')).toBeVisible()
    expect(getQuizResult).not.toHaveBeenCalled()
  })
})
