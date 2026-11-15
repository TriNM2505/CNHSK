import { StrictMode } from 'react'
import { act, fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { createMemoryRouter, RouterProvider } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError } from '@/shared/api'
import { AuthContext } from '@/shared/auth/AuthContext'
import type { RoleCode } from '@/features/auth/types'
import * as api from '../api/contests'
import {
  contestFixture,
  endedFixture,
  examFixture,
  examPageFixture,
  participantFixtures,
  publishedFixture,
  winnerFixtures,
} from '../test-fixtures'
import type { Winner } from '../types'
import Scr071ContestEditorPage from './Scr071ContestEditorPage'

const auth = vi.hoisted(() => ({ user: { id: 1, roles: ['CONTENT_EDITOR'] } }))
vi.mock('../api/contests', () => ({
  getContest: vi.fn(),
  listExams: vi.fn(),
  findAttachedExam: vi.fn(),
  saveContest: vi.fn(),
  transitionContest: vi.fn(),
  listParticipants: vi.fn(),
  decideParticipant: vi.fn(),
  revealWinners: vi.fn(),
}))
function renderPage(entry = '/content/contests/2109', strict = false) {
  const router = createMemoryRouter(
    [
      { path: '/content/contests/new', element: <Scr071ContestEditorPage /> },
      { path: '/content/contests/:id', element: <Scr071ContestEditorPage /> },
      { path: '/content/contests', element: <h1>Danh sách cuộc thi</h1> },
    ],
    { initialEntries: [entry] },
  )
  const authenticatedTree = () => (
    <AuthContext.Provider
      value={{
        user: {
          ...auth.user,
          roles: auth.user.roles as RoleCode[],
          email: 'editor@example.test',
          displayName: 'Biên tập',
          emailVerified: true,
        },
        status: 'authenticated',
        error: null,
        reloadCurrentUser: async () => undefined,
        setAuthenticatedUser: () => undefined,
        signOut: async () => undefined,
      }}
    >
      <RouterProvider router={router} />
    </AuthContext.Provider>
  )
  const tree = () => (strict ? <StrictMode>{authenticatedTree()}</StrictMode> : authenticatedTree())
  return { ...render(tree()), router, tree }
}
async function ready() {
  return screen.findByRole('form', { name: 'Thông tin cuộc thi' })
}
beforeEach(() => {
  vi.resetAllMocks()
  auth.user = { id: 1, roles: ['CONTENT_EDITOR'] }
  vi.mocked(api.getContest).mockResolvedValue(contestFixture)
  vi.mocked(api.listExams).mockResolvedValue(examPageFixture)
  vi.mocked(api.findAttachedExam).mockResolvedValue(examFixture)
  vi.mocked(api.saveContest).mockResolvedValue(contestFixture)
  vi.mocked(api.transitionContest).mockResolvedValue(publishedFixture)
  vi.mocked(api.listParticipants).mockResolvedValue(participantFixtures)
  vi.mocked(api.decideParticipant).mockResolvedValue(participantFixtures[0]!)
  vi.mocked(api.revealWinners).mockResolvedValue(winnerFixtures)
})
describe('SCR-071 contest editor', () => {
  it('creates a draft with VN +07:00 input, no implicit exam, and opens the saved record', async () => {
    const { router } = renderPage('/content/contests/new')
    await ready()
    expect(api.getContest).not.toHaveBeenCalled()
    expect(screen.getByLabelText('Đề được chọn', { exact: false })).toHaveValue('')
    fireEvent.change(screen.getByLabelText('Tên cuộc thi', { exact: false }), {
      target: { value: 'Cuộc thi tự soạn' },
    })
    fireEvent.change(screen.getByLabelText('Bắt đầu (giờ Việt Nam)', { exact: false }), {
      target: { value: '2030-12-06T19:00' },
    })
    fireEvent.change(screen.getByLabelText('Kết thúc (giờ Việt Nam)', { exact: false }), {
      target: { value: '2030-12-06T21:00' },
    })
    await userEvent.click(screen.getByRole('button', { name: 'Lưu nháp' }))
    await waitFor(() =>
      expect(api.saveContest).toHaveBeenCalledWith(
        null,
        expect.objectContaining({
          name: 'Cuộc thi tự soạn',
          examId: null,
          startsAt: '2030-12-06T19:00:00+07:00',
          endsAt: '2030-12-06T21:00:00+07:00',
        }),
      ),
    )
    await waitFor(() => expect(router.state.location.pathname).toBe('/content/contests/2109'))
    expect(api.transitionContest).not.toHaveBeenCalled()
  })
  it('validates the form locally and preserves it on server field errors', async () => {
    vi.mocked(api.saveContest).mockRejectedValue(
      new ApiError({
        status: 422,
        errorCode: 'VALIDATION_FAILED',
        message: 'Dữ liệu không hợp lệ.',
        details: [{ field: 'name', message: 'Tên bị trùng.' }],
      }),
    )
    renderPage()
    await ready()
    fireEvent.change(screen.getByLabelText('Tên cuộc thi', { exact: false }), {
      target: { value: ' ' },
    })
    await userEvent.click(screen.getByRole('button', { name: 'Lưu nháp' }))
    expect(await screen.findByText('Tên cuộc thi phải có 1–200 ký tự.')).toBeVisible()
    expect(api.saveContest).not.toHaveBeenCalled()
    fireEvent.change(screen.getByLabelText('Tên cuộc thi', { exact: false }), {
      target: { value: 'Tên mới' },
    })
    await userEvent.click(screen.getByRole('button', { name: 'Lưu nháp' }))
    expect(await screen.findByText('Tên bị trùng.')).toBeVisible()
    expect(screen.getByLabelText('Tên cuộc thi', { exact: false })).toHaveValue('Tên mới')
    expect(api.getContest).toHaveBeenCalledTimes(1)
  })
  it('AT21 locks all metadata after PUT returns CONTEST_LOCKED, reloads and never replays PUT', async () => {
    vi.mocked(api.saveContest).mockRejectedValue(
      new ApiError({ status: 409, errorCode: 'CONTEST_LOCKED', message: 'Locked' }),
    )
    renderPage()
    await ready()
    await userEvent.click(screen.getByRole('button', { name: 'Lưu nháp' }))
    await waitFor(() => expect(api.getContest).toHaveBeenCalledTimes(2))
    await ready()
    for (const field of screen.getByRole('form').querySelectorAll('input, select, textarea'))
      expect(field).toBeDisabled()
    expect(screen.getByRole('alert')).toHaveTextContent('tải lại')
    expect(api.saveContest).toHaveBeenCalledTimes(1)
  })
  it.each(['CANCELLED', 'PUBLISHED'] as const)(
    'locks known %s metadata before editing is attempted',
    async (status) => {
      vi.mocked(api.getContest).mockResolvedValue({ ...endedFixture, status })
      renderPage()
      await ready()
      expect(screen.getByLabelText('Tên cuộc thi', { exact: false })).toBeDisabled()
      expect(screen.getByLabelText('Đề được chọn', { exact: false })).toBeDisabled()
      expect(api.saveContest).not.toHaveBeenCalled()
    },
  )
  it('AT21 disables ranking before endsAt, leaves winners absent and does not load participants early', async () => {
    vi.mocked(api.getContest).mockResolvedValue({
      ...contestFixture,
      status: 'PUBLISHED',
      state: 'ONGOING',
    })
    renderPage()
    await ready()
    expect(screen.getByRole('button', { name: 'Công bố kết quả' })).toBeDisabled()
    expect(screen.queryByRole('button', { name: 'Hiện email' })).not.toBeInTheDocument()
    expect(api.listParticipants).not.toHaveBeenCalled()
    expect(api.revealWinners).not.toHaveBeenCalled()
  })
  it('Q-T09-01 preserves the attached archived exam and allows draft save while publication stays locked', async () => {
    vi.mocked(api.findAttachedExam).mockResolvedValue({ ...examFixture, status: 'ARCHIVED' })
    vi.mocked(api.listExams).mockResolvedValue({
      ...examPageFixture,
      items: [{ ...examFixture, status: 'ARCHIVED' }],
    })
    renderPage()
    await ready()
    await waitFor(() =>
      expect(
        screen.getAllByText('Đề đã lưu trữ — chọn đề khác trước khi công bố').length,
      ).toBeGreaterThan(0),
    )
    expect(screen.getByLabelText('Đề được chọn', { exact: false })).toHaveValue('41')
    expect(screen.getByLabelText('Đề được chọn', { exact: false })).toHaveTextContent(
      examFixture.title,
    )
    expect(screen.getByRole('button', { name: 'Công bố cuộc thi' })).toBeDisabled()
    await userEvent.click(screen.getByRole('button', { name: 'Lưu nháp' }))
    expect(api.saveContest).toHaveBeenCalledWith(2109, expect.objectContaining({ examId: 41 }))
  })
  it('pages and searches exams, excludes archived choices, and requires saving an exam replacement before publishing', async () => {
    const replacement = {
      ...examFixture,
      id: 42,
      title: 'Đề thay thế',
      status: 'PUBLISHED' as const,
    }
    vi.mocked(api.listExams).mockResolvedValue({
      items: [
        replacement,
        { ...examFixture, id: 99, title: 'Đề không được chọn', status: 'ARCHIVED' },
      ],
      page: 0,
      size: 20,
      totalElements: 21,
    })
    vi.mocked(api.findAttachedExam).mockImplementation(async (id) =>
      id === 42 ? replacement : examFixture,
    )
    renderPage()
    await ready()
    await screen.findByRole('option', { name: /Đề thay thế/ })
    expect(screen.queryByRole('option', { name: /Đề không được chọn/ })).not.toBeInTheDocument()
    await userEvent.click(
      within(screen.getByRole('navigation', { name: 'Phân trang đề thi' })).getByRole('button', {
        name: 'Sau',
      }),
    )
    await waitFor(() =>
      expect(api.listExams).toHaveBeenLastCalledWith({ page: 1 }, expect.any(AbortSignal)),
    )
    fireEvent.change(screen.getByLabelText('Tìm đề theo tên hoặc mã', { exact: false }), {
      target: { value: 'thay thế' },
    })
    await userEvent.click(screen.getByRole('button', { name: 'Tìm đề' }))
    await waitFor(() =>
      expect(api.listExams).toHaveBeenLastCalledWith(
        { q: 'thay thế', page: 0 },
        expect.any(AbortSignal),
      ),
    )
    await userEvent.selectOptions(screen.getByLabelText('Đề được chọn', { exact: false }), '42')
    expect(screen.getByRole('button', { name: 'Công bố cuộc thi' })).toBeDisabled()
    await userEvent.click(screen.getByRole('button', { name: 'Lưu nháp' }))
    expect(api.saveContest).toHaveBeenCalledWith(2109, expect.objectContaining({ examId: 42 }))
  })
  it('handles stale publication 422 CONTEST_EXAM_INVALID with a message and authoritative reload', async () => {
    vi.mocked(api.transitionContest).mockRejectedValue(
      new ApiError({ status: 422, errorCode: 'CONTEST_EXAM_INVALID', message: 'Archived' }),
    )
    renderPage()
    await ready()
    await waitFor(() =>
      expect(screen.getByRole('button', { name: 'Công bố cuộc thi' })).toBeEnabled(),
    )
    await userEvent.click(screen.getByRole('button', { name: 'Công bố cuộc thi' }))
    expect(api.transitionContest).not.toHaveBeenCalled()
    await userEvent.click(screen.getByRole('button', { name: 'Xác nhận' }))
    await waitFor(() => expect(api.getContest).toHaveBeenCalledTimes(2))
    await ready()
    expect(screen.getByRole('alert')).toHaveTextContent('Đề thi không hợp lệ hoặc đã lưu trữ')
    expect(api.transitionContest).toHaveBeenCalledTimes(1)
    expect(api.listExams).toHaveBeenCalledTimes(2)
  })
  it('publishes ranking only after confirmation and leaves contact loading exclusively to Hiện email', async () => {
    vi.mocked(api.getContest)
      .mockResolvedValueOnce(endedFixture)
      .mockResolvedValue(publishedFixture)
    renderPage()
    await ready()
    await userEvent.click(screen.getByRole('button', { name: 'Công bố kết quả' }))
    expect(api.transitionContest).not.toHaveBeenCalled()
    await userEvent.click(screen.getByRole('button', { name: 'Xác nhận' }))
    await screen.findByRole('button', { name: 'Hiện email' })
    expect(api.transitionContest).toHaveBeenCalledWith(2109, 'ranking')
    expect(api.revealWinners).not.toHaveBeenCalled()
    expect(screen.queryByText(winnerFixtures[0]!.email)).not.toBeInTheDocument()
  })
  it.each([true, false])(
    'requires a 1–500 character note for excluded=%s and reloads participants after success',
    async (excluded) => {
      vi.mocked(api.getContest).mockResolvedValue(endedFixture)
      renderPage()
      await ready()
      await screen.findByRole('table', { name: 'Thí sinh cuộc thi' })
      await userEvent.click(screen.getAllByRole('button', { name: excluded ? 'Loại' : 'Giữ' })[0]!)
      const confirm = () =>
        screen.getByRole('button', { name: excluded ? 'Xác nhận loại' : 'Xác nhận giữ' })
      await userEvent.click(confirm())
      expect(await screen.findByText('Ghi chú bắt buộc có 1–500 ký tự.')).toBeVisible()
      expect(api.decideParticipant).not.toHaveBeenCalled()
      fireEvent.change(screen.getByLabelText('Ghi chú quyết định', { exact: false }), {
        target: { value: ' '.repeat(10) },
      })
      await userEvent.click(confirm())
      expect(api.decideParticipant).not.toHaveBeenCalled()
      fireEvent.change(screen.getByLabelText('Ghi chú quyết định', { exact: false }), {
        target: { value: 'x'.repeat(501) },
      })
      await userEvent.click(confirm())
      expect(api.decideParticipant).not.toHaveBeenCalled()
      fireEvent.change(screen.getByLabelText('Ghi chú quyết định', { exact: false }), {
        target: { value: 'x'.repeat(500) },
      })
      await userEvent.click(confirm())
      await waitFor(() =>
        expect(api.decideParticipant).toHaveBeenCalledWith(2109, 31, {
          excluded,
          note: 'x'.repeat(500),
        }),
      )
      await waitFor(() => expect(api.listParticipants).toHaveBeenCalledTimes(2))
    },
  )
  it('refreshes the contest after a racing participant decision returns 409 and never loads contacts', async () => {
    vi.mocked(api.getContest)
      .mockResolvedValueOnce(endedFixture)
      .mockResolvedValue(publishedFixture)
    vi.mocked(api.decideParticipant).mockRejectedValue(
      new ApiError({ status: 409, errorCode: 'CONTEST_LOCKED', message: 'Locked' }),
    )
    renderPage()
    await ready()
    await screen.findByRole('table', { name: 'Thí sinh cuộc thi' })
    await userEvent.click(screen.getAllByRole('button', { name: 'Giữ' })[0]!)
    fireEvent.change(screen.getByLabelText('Ghi chú quyết định', { exact: false }), {
      target: { value: 'Đã kiểm tra' },
    })
    await userEvent.click(screen.getByRole('button', { name: 'Xác nhận giữ' }))
    await waitFor(() => expect(api.getContest).toHaveBeenCalledTimes(2))
    await ready()
    expect(api.decideParticipant).toHaveBeenCalledTimes(1)
    for (const button of screen.getAllByRole('button', { name: 'Giữ' }))
      expect(button).toBeDisabled()
    expect(api.revealWinners).not.toHaveBeenCalled()
  })
  it('AT21 makes no winners request in StrictMode until a click; every reopening fetches anew without storage writes', async () => {
    vi.mocked(api.getContest).mockResolvedValue(publishedFixture)
    const storage = vi.spyOn(Storage.prototype, 'setItem')
    const view = renderPage(undefined, true)
    await ready()
    await screen.findByRole('button', { name: 'Hiện email' })
    view.rerender(view.tree())
    expect(api.revealWinners).not.toHaveBeenCalled()
    expect(screen.queryByText(winnerFixtures[0]!.email)).not.toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'Hiện email' }))
    expect(await screen.findByText(winnerFixtures[0]!.email)).toBeVisible()
    expect(api.revealWinners).toHaveBeenCalledTimes(1)
    await userEvent.click(screen.getByRole('button', { name: 'Ẩn email' }))
    expect(screen.queryByText(winnerFixtures[0]!.email)).not.toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'Hiện email' }))
    await screen.findByText(winnerFixtures[0]!.email)
    expect(api.revealWinners).toHaveBeenCalledTimes(2)
    expect(storage).not.toHaveBeenCalled()
    storage.mockRestore()
  })
  it('aborts contact loading on hide, ignores its late response, and forgets contacts on contest navigation', async () => {
    vi.mocked(api.getContest).mockResolvedValue(publishedFixture)
    let resolve!: (value: Winner[]) => void
    vi.mocked(api.revealWinners).mockReturnValueOnce(
      new Promise((done) => {
        resolve = done
      }),
    )
    const view = renderPage()
    await ready()
    await userEvent.click(screen.getByRole('button', { name: 'Hiện email' }))
    const signal = vi.mocked(api.revealWinners).mock.calls[0]![1]!
    await userEvent.click(screen.getByRole('button', { name: 'Ẩn email' }))
    expect(signal.aborted).toBe(true)
    await act(async () => resolve(winnerFixtures))
    expect(screen.queryByText(winnerFixtures[0]!.email)).not.toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'Hiện email' }))
    await screen.findByText(winnerFixtures[0]!.email)
    await act(() => view.router.navigate('/content/contests/2110'))
    await ready()
    expect(screen.queryByText(winnerFixtures[0]!.email)).not.toBeInTheDocument()
    expect(api.revealWinners).toHaveBeenCalledTimes(2)
  })
  it('clears contacts on actor change and does not expose data if editor access is removed', async () => {
    vi.mocked(api.getContest).mockResolvedValue(publishedFixture)
    const view = renderPage()
    await ready()
    await userEvent.click(screen.getByRole('button', { name: 'Hiện email' }))
    await screen.findByText(winnerFixtures[0]!.email)
    auth.user = { id: 2, roles: ['CONTENT_EDITOR'] }
    view.rerender(view.tree())
    await ready()
    expect(screen.queryByText(winnerFixtures[0]!.email)).not.toBeInTheDocument()
    auth.user.roles = ['ADMIN']
    view.rerender(view.tree())
    expect(screen.getByRole('alert')).toHaveTextContent('Cần quyền biên tập nội dung')
    expect(screen.queryByRole('button', { name: 'Hiện email' })).not.toBeInTheDocument()
  })
  it('does not replay a failed winners GET after 409, and each explicit retry is a new request', async () => {
    vi.mocked(api.getContest).mockResolvedValue(publishedFixture)
    vi.mocked(api.revealWinners)
      .mockRejectedValueOnce(
        new ApiError({ status: 409, errorCode: 'CONTEST_LOCKED', message: 'Locked' }),
      )
      .mockResolvedValueOnce([])
    renderPage()
    await ready()
    await userEvent.click(screen.getByRole('button', { name: 'Hiện email' }))
    await waitFor(() => expect(api.getContest).toHaveBeenCalledTimes(2))
    await ready()
    expect(api.revealWinners).toHaveBeenCalledTimes(1)
    await userEvent.click(screen.getByRole('button', { name: 'Hiện email' }))
    expect(
      await screen.findByText('Không có người đạt giải trong các khoảng hạng đã đặt.'),
    ).toBeVisible()
    expect(api.revealWinners).toHaveBeenCalledTimes(2)
  })
  it('does not turn detail load failures into a create form and retries on explicit request', async () => {
    vi.mocked(api.getContest)
      .mockRejectedValueOnce(
        new ApiError({
          status: 404,
          errorCode: 'CONTEST_NOT_FOUND',
          message: 'Không tìm thấy cuộc thi.',
        }),
      )
      .mockResolvedValue(contestFixture)
    renderPage()
    expect(await screen.findByRole('alert')).toHaveTextContent('Không tìm thấy cuộc thi.')
    expect(screen.queryByRole('form')).not.toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'Thử tải cuộc thi lại' }))
    await ready()
  })
  it.each(['ADMIN', 'LEARNER', 'TEACHER', 'MODERATOR', 'ACCOUNTANT'])(
    'does not fetch any admin data for %s',
    (role) => {
      auth.user.roles = [role]
      renderPage()
      expect(screen.getByRole('alert')).toHaveTextContent('Cần quyền biên tập nội dung')
      expect(api.getContest).not.toHaveBeenCalled()
      expect(api.listExams).not.toHaveBeenCalled()
    },
  )
  it('rejects malformed IDs without fetching', () => {
    renderPage('/content/contests/not-a-number')
    expect(screen.getByRole('alert')).toHaveTextContent('Mã cuộc thi không hợp lệ')
    expect(api.getContest).not.toHaveBeenCalled()
  })
})
