import { cleanup, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { CurrentUser } from '@/features/auth/types'
import { AuthContext, type AuthContextValue } from '@/shared/auth/AuthContext'
import { getContest, getContestResults } from '../api/contests'
import { contestFixture, unpublishedFixture } from '../test-fixtures'
import Scr049ContestDetailPage from './Scr049ContestDetailPage'

vi.mock('../api/contests', () => ({ getContest: vi.fn(), getContestResults: vi.fn() }))
const learner: CurrentUser = {
  id: 202,
  displayName: 'Người học mẫu',
  email: 'learner@example.test',
  emailVerified: true,
  roles: ['LEARNER'],
}
function Location() {
  return <output aria-label="Vị trí">{useLocation().pathname}</output>
}
function mount(user: CurrentUser | null = learner) {
  const auth: AuthContextValue = {
    user,
    status: user ? 'authenticated' : 'guest',
    error: null,
    signOut: vi.fn(),
    reloadCurrentUser: vi.fn(),
    setAuthenticatedUser: vi.fn(),
  }
  return render(
    <AuthContext.Provider value={auth}>
      <MemoryRouter initialEntries={['/contests/2101']}>
        <Routes>
          <Route path="/contests/:id" element={<Scr049ContestDetailPage />} />
          <Route path="/contests/:id/attempt" element={<p>Đang vào làm bài</p>} />
        </Routes>
        <Location />
      </MemoryRouter>
    </AuthContext.Provider>,
  )
}

describe('SCR-049 FR015 / AT20', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    vi.useFakeTimers({ toFake: ['Date'] })
    vi.setSystemTime(new Date('2026-11-14T12:08:00Z'))
    vi.mocked(getContest).mockResolvedValue(contestFixture)
    vi.mocked(getContestResults).mockResolvedValue({ ...unpublishedFixture, mine: null })
  })
  afterEach(() => {
    cleanup()
    vi.useRealTimers()
    vi.restoreAllMocks()
  })
  it('sends a game contest to its community page instead of an exam detail', async () => {
    vi.mocked(getContest).mockResolvedValue({ ...contestFixture, gameCode: 'CHAR_RAIN', examTitle: null })
    vi.mocked(getContestResults).mockResolvedValue(null as never)
    mount()
    await waitFor(() => expect(screen.getByLabelText('Vị trí')).toHaveTextContent('/community/contests/2101'))
  })
  it('allows entry only after the learner confirms the one-attempt warning', async () => {
    const user = userEvent.setup()
    mount()
    await user.click(await screen.findByRole('button', { name: 'Vào thi' }))
    expect(screen.getByRole('region', { name: 'Xác nhận vào thi' })).toHaveTextContent(
      /Mỗi người chỉ thi một lần; thời gian còn lại: 20 phút/,
    )
    expect(screen.getByLabelText('Vị trí')).toHaveTextContent('/contests/2101')
    await user.click(screen.getByRole('button', { name: 'Bắt đầu làm bài' }))
    await waitFor(() =>
      expect(screen.getByLabelText('Vị trí')).toHaveTextContent('/contests/2101/attempt'),
    )
  })
  it('resumes an open attempt with its original deadline instead of promising a new duration', async () => {
    vi.mocked(getContestResults).mockResolvedValue({
      ...unpublishedFixture,
      mine: { ...unpublishedFixture.mine!, status: 'IN_PROGRESS', submittedAt: null },
    })
    const user = userEvent.setup()
    mount()
    await screen.findByText('Bạn có lượt thi đang mở. Vào thi để tiếp tục.')
    await user.click(screen.getByRole('button', { name: 'Vào thi' }))
    const confirmation = screen.getByRole('region', { name: 'Xác nhận vào thi' })
    expect(confirmation).toHaveTextContent('tiếp tục lượt đang mở với hạn nộp đã có')
    expect(confirmation).not.toHaveTextContent('20 phút')
    await user.click(screen.getByRole('button', { name: 'Bắt đầu làm bài' }))
    await waitFor(() =>
      expect(screen.getByLabelText('Vị trí')).toHaveTextContent('/contests/2101/attempt'),
    )
  })
  it.each(['UPCOMING', 'ENDED'] as const)('does not enable entry for %s', async (state) => {
    vi.mocked(getContest).mockResolvedValue({ ...contestFixture, state })
    mount()
    await screen.findByRole('heading', { name: contestFixture.name })
    expect(screen.queryByRole('button', { name: 'Vào thi' })).not.toBeInTheDocument()
    expect(screen.queryByRole('link', { name: 'Đăng nhập để vào thi' })).not.toBeInTheDocument()
  })
  it('sends guests to login with their detail return path and does not read private results', async () => {
    mount(null)
    expect(await screen.findByRole('link', { name: 'Đăng nhập để vào thi' })).toHaveAttribute(
      'href',
      '/login?redirect=%2Fcontests%2F2101',
    )
    expect(getContestResults).not.toHaveBeenCalled()
  })
  it('guides unverified learners to verification instead of allowing entry', async () => {
    mount({ ...learner, emailVerified: false })
    expect(await screen.findByRole('link', { name: 'Xác thực email' })).toHaveAttribute(
      'href',
      '/verify-email',
    )
    expect(screen.queryByRole('button', { name: 'Vào thi' })).not.toBeInTheDocument()
  })
  it('offers results for a submitted attempt without another entry action', async () => {
    vi.mocked(getContestResults).mockResolvedValue(unpublishedFixture)
    mount()
    expect(await screen.findByRole('link', { name: 'Xem kết quả' })).toHaveAttribute(
      'href',
      '/contests/2101/results',
    )
    expect(screen.queryByRole('button', { name: 'Vào thi' })).not.toBeInTheDocument()
  })
})
