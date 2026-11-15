import { act, render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { createMemoryRouter, RouterProvider } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError } from '@/shared/api'
import { listContests, transitionContest } from '../api/contests'
import { contestFixture, endedFixture } from '../test-fixtures'
import Scr070ContestListPage from './Scr070ContestListPage'

const auth = vi.hoisted(() => ({ user: { id: 1, roles: ['CONTENT_EDITOR'] } }))
vi.mock('@/shared/auth', () => ({ useCurrentUser: () => auth }))
vi.mock('../api/contests', () => ({ listContests: vi.fn(), transitionContest: vi.fn() }))
const list = vi.mocked(listContests)
const transition = vi.mocked(transitionContest)
function renderPage(entry = '/content/contests') {
  const router = createMemoryRouter(
    [
      { path: '/content/contests', element: <Scr070ContestListPage /> },
      { path: '/content/contests/:id', element: <h1>Éditeur</h1> },
      { path: '/content/contests/new', element: <h1>Tạo mới</h1> },
    ],
    { initialEntries: [entry] },
  )
  return { ...render(<RouterProvider router={router} />), router }
}
beforeEach(() => {
  vi.resetAllMocks()
  auth.user = { id: 1, roles: ['CONTENT_EDITOR'] }
  list.mockResolvedValue({ items: [contestFixture], page: 0, size: 20, totalElements: 1 })
  transition.mockResolvedValue({ ...contestFixture, status: 'CANCELLED' })
})
describe('SCR-070 contest management', () => {
  it('keeps filters and pagination in the URL and resets page on filter changes', async () => {
    list.mockImplementation(async (params) => ({
      items: [contestFixture],
      page: params?.page ?? 0,
      size: 20,
      totalElements: 61,
    }))
    const { router } = renderPage('/content/contests?status=DRAFT&page=2')
    await screen.findByRole('table')
    expect(list).toHaveBeenLastCalledWith(
      { status: 'DRAFT', page: 2, size: 20 },
      expect.any(AbortSignal),
    )
    await userEvent.click(
      within(screen.getByRole('navigation', { name: 'Phân trang cuộc thi' })).getByRole('button', {
        name: 'Sau',
      }),
    )
    await waitFor(() => expect(list.mock.lastCall?.[0]).toMatchObject({ status: 'DRAFT', page: 3 }))
    await userEvent.selectOptions(screen.getByLabelText('Lọc trạng thái'), 'PUBLISHED')
    await waitFor(() =>
      expect(list.mock.lastCall?.[0]).toMatchObject({ status: 'PUBLISHED', page: 0 }),
    )
    expect(router.state.location.search).toBe('?status=PUBLISHED')
    await userEvent.click(
      screen.getAllByRole('link', { name: `Quản lý: ${contestFixture.name}` })[0]!,
    )
    await act(() => router.navigate(-1))
    expect(router.state.location.search).toBe('?status=PUBLISHED')
  })
  it('requires confirmation and canceling it performs no mutation', async () => {
    renderPage()
    await screen.findByRole('table')
    await userEvent.click(screen.getAllByRole('button', { name: 'Huỷ cuộc thi' })[0]!)
    expect(transition).not.toHaveBeenCalled()
    expect(screen.getByRole('dialog')).toHaveTextContent(contestFixture.name)
    await userEvent.click(screen.getByRole('button', { name: 'Bỏ qua' }))
    expect(transition).not.toHaveBeenCalled()
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
  })
  it.each([
    ['CONTEST_LOCKED', 'cancel'],
    ['CONTEST_HAS_ATTEMPTS', 'cancel'],
    ['CONTEST_RANKING_TOO_EARLY', 'ranking'],
  ] as const)(
    'reloads once after 409 %s without replaying the mutation',
    async (errorCode, action) => {
      const initial = action === 'ranking' ? endedFixture : contestFixture
      list
        .mockResolvedValueOnce({ items: [initial], page: 0, size: 20, totalElements: 1 })
        .mockResolvedValue({
          items: [
            {
              ...initial,
              participantCount: 1,
              rankingPublishedAt: action === 'ranking' ? '2026-01-02T00:00:00+07:00' : null,
            },
          ],
          page: 0,
          size: 20,
          totalElements: 1,
        })
      transition.mockRejectedValue(new ApiError({ status: 409, errorCode, message: 'Conflict' }))
      renderPage()
      await screen.findByRole('table')
      await userEvent.click(
        screen.getAllByRole('button', {
          name: action === 'ranking' ? 'Công bố kết quả' : 'Huỷ cuộc thi',
        })[0]!,
      )
      await userEvent.click(screen.getByRole('button', { name: 'Xác nhận' }))
      await waitFor(() => expect(list).toHaveBeenCalledTimes(2))
      await screen.findByRole('table')
      expect(transition).toHaveBeenCalledTimes(1)
      expect(transition).toHaveBeenCalledWith(2109, action)
      expect(screen.getByRole('alert')).toHaveTextContent(/tải lại/)
      expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
      for (const button of screen.getAllByRole('button', {
        name: action === 'ranking' ? 'Công bố kết quả' : 'Huỷ cuộc thi',
      }))
        expect(button).toBeDisabled()
    },
  )
  it('AT21 locks ranking before end, cancellation with attempts, and editing after start with explanations', async () => {
    list.mockResolvedValue({
      items: [{ ...contestFixture, status: 'PUBLISHED', state: 'ONGOING', participantCount: 1 }],
      page: 0,
      size: 20,
      totalElements: 1,
    })
    renderPage()
    await screen.findByRole('table')
    for (const label of ['Sửa', 'Huỷ cuộc thi', 'Công bố kết quả'])
      for (const button of screen.getAllByRole('button', { name: label }))
        expect(button).toBeDisabled()
    expect(screen.getAllByText('Chỉ công bố kết quả sau giờ kết thúc.')).toHaveLength(2)
    expect(
      screen.getAllByRole('link', { name: `Quản lý: ${contestFixture.name}` })[0],
    ).toHaveAttribute('href', '/content/contests/2109')
  })
  it('handles loading, error and empty as separate states with retry', async () => {
    let reject!: (error: unknown) => void
    list
      .mockReturnValueOnce(
        new Promise((_, fail) => {
          reject = fail
        }),
      )
      .mockResolvedValueOnce({ items: [], page: 0, size: 20, totalElements: 0 })
    renderPage()
    expect(screen.getByRole('status')).toHaveTextContent('Đang tải cuộc thi')
    await act(async () =>
      reject(
        new ApiError({
          status: 500,
          errorCode: 'INTERNAL_ERROR',
          message: 'Không tải được danh sách.',
        }),
      ),
    )
    expect(await screen.findByRole('alert')).toHaveTextContent('Không tải được danh sách.')
    expect(screen.queryByText('Chưa có cuộc thi.')).not.toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'Thử tải lại' }))
    expect(await screen.findByText('Chưa có cuộc thi.')).toBeVisible()
  })
  it.each(['ADMIN', 'LEARNER', 'TEACHER', 'MODERATOR', 'ACCOUNTANT'])(
    'never fetches contest admin data for %s',
    (role) => {
      auth.user.roles = [role]
      renderPage()
      expect(screen.getByRole('alert')).toHaveTextContent('Cần quyền biên tập nội dung')
      expect(list).not.toHaveBeenCalled()
    },
  )
})
