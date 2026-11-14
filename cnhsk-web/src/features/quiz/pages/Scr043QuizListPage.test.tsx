import { act, fireEvent, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { ApiError } from '@/shared/api'
import { AuthContext, type AuthContextValue } from '@/shared/auth/AuthContext'
import { listQuizzes, startQuizAttempt } from '../api/quizzes'
import { quizPage, quizSession } from '../test-fixtures'
import type { QuizListingPage, QuizSession } from '../types'
import Scr043QuizListPage from './Scr043QuizListPage'

const firstQuiz = quizPage.items[0]
const retryQuiz = quizPage.items[1]
if (!firstQuiz || !retryQuiz) throw new Error('Thiếu dữ liệu quiz tự soạn cho test.')

vi.mock('../api/quizzes', () => ({ listQuizzes: vi.fn(), startQuizAttempt: vi.fn() }))
const user = {
  id: 1,
  displayName: 'Người học',
  email: 'quiz@example.test',
  emailVerified: true,
  roles: ['LEARNER'] as const,
}
function LocationValue() {
  const location = useLocation()
  return (
    <output aria-label="Đường dẫn">
      {location.pathname + location.search} {JSON.stringify(location.state)}
    </output>
  )
}
function renderPage(path = '/community/quizzes') {
  const value: AuthContextValue = {
    user: { ...user, roles: ['LEARNER'] },
    status: 'authenticated',
    error: null,
    reloadCurrentUser: vi.fn(),
    setAuthenticatedUser: vi.fn(),
    signOut: vi.fn(),
  }
  return render(
    <AuthContext.Provider value={value}>
      <MemoryRouter initialEntries={[path]}>
        <LocationValue />
        <Routes>
          <Route path="/community/quizzes" element={<Scr043QuizListPage />} />
          <Route path="/community/quizzes/:topicId/play" element={<h1>Màn làm quiz</h1>} />
          <Route path="/login" element={<h1>Đăng nhập</h1>} />
          <Route path="/403" element={<h1>Không có quyền</h1>} />
        </Routes>
      </MemoryRouter>
    </AuthContext.Provider>,
  )
}
function deferred<T>() {
  let resolve!: (value: T) => void
  const promise = new Promise<T>((done) => {
    resolve = done
  })
  return { promise, resolve }
}
function error(status: number, errorCode: string) {
  return new ApiError({ status, errorCode, message: 'Lỗi kiểm thử.' })
}

describe('SCR-043', () => {
  beforeEach(() => {
    vi.mocked(listQuizzes).mockReset().mockResolvedValue(quizPage)
    vi.mocked(startQuizAttempt).mockReset().mockResolvedValue(quizSession)
  })
  it('shows exact competition notice, configured question count, first/retry labels and zero scores', async () => {
    renderPage()
    expect(await screen.findByRole('heading', { name: 'Giao tiếp hằng ngày' })).toBeVisible()
    expect(
      screen.getByText(
        'Quiz là thi đua. Chỉ lần làm đầu tiên được tính xếp hạng. Quiz không ảnh hưởng tiến độ học.',
      ),
    ).toBeVisible()
    expect(screen.getByText('12 câu hỏi')).toBeVisible()
    expect(screen.getByText('Đã làm lần đầu: 0 điểm')).toBeVisible()
    expect(screen.getByText('Điểm làm lại tốt nhất: 0')).toBeVisible()
    expect(screen.getAllByText('Làm lại — không tính hạng')).toHaveLength(2)
    expect(
      screen.getByRole('button', { name: 'Bắt đầu quiz Giao tiếp hằng ngày' }),
    ).toHaveAttribute('aria-label')
    expect(screen.getByText('Lượt đầu bị đánh dấu nghi vấn.')).toBeVisible()
  })
  it('keeps first eligibility when only a historical retry score exists', async () => {
    vi.mocked(listQuizzes).mockResolvedValue({
      ...quizPage,
      items: [{ ...firstQuiz, myBestRetryScore: 100 }],
    })
    renderPage()
    expect(
      await screen.findByRole('button', { name: 'Bắt đầu quiz Giao tiếp hằng ngày' }),
    ).toBeVisible()
    expect(screen.getByText('Điểm làm lại tốt nhất: 100')).toBeVisible()
  })
  it('renders loading, empty, error and safe GET retry', async () => {
    const pending = deferred<QuizListingPage>()
    vi.mocked(listQuizzes)
      .mockReturnValueOnce(pending.promise)
      .mockRejectedValueOnce(error(0, 'NETWORK_ERROR'))
      .mockResolvedValueOnce(quizPage)
    renderPage()
    expect(screen.getByText('Đang tải quiz…')).toHaveAttribute('role', 'status')
    await act(async () => pending.resolve({ ...quizPage, items: [], total: 0 }))
    expect(await screen.findByText('Chưa có quiz phù hợp')).toBeVisible()
    await userEvent.click(screen.getByRole('button', { name: 'Tải lại danh sách' }))
    expect(await screen.findByText('Không tải được quiz')).toBeVisible()
    await userEvent.click(screen.getByRole('button', { name: 'Thử lại' }))
    expect(await screen.findByRole('heading', { name: 'Giao tiếp hằng ngày' })).toBeVisible()
    expect(startQuizAttempt).not.toHaveBeenCalled()
  })
  it('converts pagination to zero-based API pages and resets page when selecting HSK', async () => {
    vi.mocked(listQuizzes).mockImplementation(async (params) => ({
      ...quizPage,
      page: params?.page ?? 0,
      total: 45,
    }))
    renderPage()
    await screen.findByRole('heading', { name: 'Giao tiếp hằng ngày' })
    await userEvent.click(screen.getByRole('button', { name: 'Sau' }))
    await waitFor(() =>
      expect(listQuizzes).toHaveBeenLastCalledWith(
        { hsk: undefined, page: 1 },
        expect.any(AbortSignal),
      ),
    )
    await userEvent.selectOptions(screen.getByLabelText('Cấp HSK'), '2')
    await waitFor(() =>
      expect(listQuizzes).toHaveBeenLastCalledWith({ hsk: 2, page: 0 }, expect.any(AbortSignal)),
    )
    expect(screen.getByRole('status', { name: 'Đường dẫn' })).toHaveTextContent(
      '/community/quizzes?hsk=2',
    )
    expect(screen.queryByRole('option', { name: 'HSK 4' })).not.toBeInTheDocument()
  })
  it('ignores and aborts the previous catalog request after HSK changes', async () => {
    const old = deferred<QuizListingPage>()
    vi.mocked(listQuizzes)
      .mockReturnValueOnce(old.promise)
      .mockResolvedValueOnce({ ...quizPage, items: [retryQuiz] })
    renderPage()
    await userEvent.selectOptions(screen.getByLabelText('Cấp HSK'), '2')
    expect(await screen.findByRole('heading', { name: 'Gia đình và bạn bè' })).toBeVisible()
    await act(async () => old.resolve(quizPage))
    expect(screen.queryByRole('heading', { name: 'Giao tiếp hằng ngày' })).not.toBeInTheDocument()
    expect(vi.mocked(listQuizzes).mock.calls.at(0)?.[1]?.aborted).toBe(true)
  })
  it.each([true, false])(
    'passes server session and eligibility %s to SCR-044',
    async (rankingEligible) => {
      vi.mocked(startQuizAttempt).mockResolvedValue({ ...quizSession, rankingEligible })
      renderPage()
      await userEvent.click(
        await screen.findByRole('button', { name: 'Bắt đầu quiz Giao tiếp hằng ngày' }),
      )
      expect(await screen.findByRole('heading', { name: 'Màn làm quiz' })).toBeVisible()
      expect(screen.getByRole('status', { name: 'Đường dẫn' })).toHaveTextContent(
        '/community/quizzes/43/play?sessionId=101',
      )
      expect(screen.getByRole('status', { name: 'Đường dẫn' })).toHaveTextContent(
        `"rankingEligible":${rankingEligible}`,
      )
      expect(startQuizAttempt).toHaveBeenCalledTimes(1)
      expect(startQuizAttempt).toHaveBeenCalledWith(43)
    },
  )
  it('starts the retry topic through the same POST and prevents duplicate submissions', async () => {
    const pending = deferred<QuizSession>()
    vi.mocked(startQuizAttempt).mockReturnValue(pending.promise)
    renderPage()
    const button = await screen.findByRole('button', { name: 'Làm lại quiz Gia đình và bạn bè' })
    fireEvent.click(button)
    fireEvent.click(button)
    await waitFor(() => expect(startQuizAttempt).toHaveBeenCalledTimes(1))
    expect(startQuizAttempt).toHaveBeenCalledWith(44)
    expect(button).toBeDisabled()
    expect(screen.getByRole('button', { name: 'Bắt đầu quiz Giao tiếp hằng ngày' })).toBeDisabled()
    await act(async () => pending.resolve({ ...quizSession, topicId: 44, rankingEligible: false }))
    expect(await screen.findByRole('heading', { name: 'Màn làm quiz' })).toBeVisible()
  })
  it.each([
    [422, 'QUIZ_NOT_AVAILABLE', 'Chủ đề này chưa có đủ câu hỏi quiz.'],
    [429, 'RATE_LIMITED', 'Bạn đã đạt giới hạn 30 lượt bắt đầu quiz trong một giờ.'],
    [404, 'TOPIC_NOT_FOUND', 'Chủ đề này không còn khả dụng.'],
  ] as const)('handles %s %s without automatic POST retry', async (status, code, message) => {
    vi.mocked(startQuizAttempt).mockRejectedValue(error(status, code))
    renderPage('/community/quizzes?hsk=1')
    await userEvent.click(
      await screen.findByRole('button', { name: 'Bắt đầu quiz Giao tiếp hằng ngày' }),
    )
    expect(await screen.findByRole('alert')).toHaveTextContent(message)
    expect(startQuizAttempt).toHaveBeenCalledTimes(1)
    expect(screen.getByLabelText('Cấp HSK')).toHaveValue('1')
  })
  it.each([401, 403])('redirects catalog %s errors', async (status) => {
    vi.mocked(listQuizzes).mockRejectedValue(
      error(status, status === 401 ? 'UNAUTHENTICATED' : 'FORBIDDEN'),
    )
    renderPage()
    expect(
      await screen.findByRole('heading', { name: status === 401 ? 'Đăng nhập' : 'Không có quyền' }),
    ).toBeVisible()
    expect(startQuizAttempt).not.toHaveBeenCalled()
  })
  it.each([401, 403])('redirects POST %s errors without retry', async (status) => {
    vi.mocked(startQuizAttempt).mockRejectedValue(
      error(status, status === 401 ? 'UNAUTHENTICATED' : 'FORBIDDEN'),
    )
    renderPage()
    await userEvent.click(
      await screen.findByRole('button', { name: 'Bắt đầu quiz Giao tiếp hằng ngày' }),
    )
    expect(
      await screen.findByRole('heading', { name: status === 401 ? 'Đăng nhập' : 'Không có quyền' }),
    ).toBeVisible()
    expect(startQuizAttempt).toHaveBeenCalledTimes(1)
  })
  it('does not navigate after unmount while POST is still in flight', async () => {
    const pending = deferred<QuizSession>()
    vi.mocked(startQuizAttempt).mockReturnValue(pending.promise)
    const rendered = renderPage()
    await userEvent.click(
      await screen.findByRole('button', { name: 'Bắt đầu quiz Giao tiếp hằng ngày' }),
    )
    rendered.unmount()
    await act(async () => pending.resolve(quizSession))
    expect(screen.queryByRole('heading', { name: 'Màn làm quiz' })).not.toBeInTheDocument()
  })
})
