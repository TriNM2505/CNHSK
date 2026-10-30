import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes, useLocation, useNavigate } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import type { CurrentUser } from '@/features/auth/types'
import { ApiError } from '@/shared/api'
import { AuthContext, type AuthContextValue } from '@/shared/auth'

import { createPost, getMyPost, listMyPosts, listPublishedPosts, updatePost } from '../api/posts'
import { ownPost, ownPage } from '../test-fixtures'
import Scr039CommunityPostListPage from './Scr039CommunityPostListPage'
import Scr041CommunityPostEditorPage from './Scr041CommunityPostEditorPage'

vi.mock('../api/posts', () => ({
  createPost: vi.fn(), getMyPost: vi.fn(), listMyPosts: vi.fn(), listPublishedPosts: vi.fn(), updatePost: vi.fn(),
}))

const learner: CurrentUser = { id: 7, displayName: 'Learner', email: 'learner@test.invalid', emailVerified: true, roles: ['LEARNER'] }

function renderEditor(path: string) {
  const auth: AuthContextValue = { error: null, reloadCurrentUser: async () => undefined, setAuthenticatedUser: () => undefined, signOut: async () => undefined, status: 'authenticated', user: learner }
  function CurrentLocation() { const location = useLocation(); return <output aria-label="current location">{location.pathname}{location.search}</output> }
  return render(<AuthContext.Provider value={auth}><MemoryRouter initialEntries={[path]}><Routes>
    <Route element={<><Scr041CommunityPostEditorPage /><CurrentLocation /></>} path="/community/posts/new" />
    <Route element={<><Scr041CommunityPostEditorPage /><CurrentLocation /></>} path="/community/posts/:id/edit" />
    <Route element={<><Scr039CommunityPostListPage /><CurrentLocation /></>} path="/community" />
  </Routes></MemoryRouter></AuthContext.Provider>)
}

describe('SCR-041 create/edit post (FR-007, FR-010, FR-059, AT-49)', () => {
  beforeEach(() => {
    vi.mocked(createPost).mockReset().mockResolvedValue(ownPost)
    vi.mocked(updatePost).mockReset().mockResolvedValue(ownPost)
    vi.mocked(getMyPost).mockReset().mockResolvedValue(ownPost)
    vi.mocked(listMyPosts).mockReset().mockResolvedValue(ownPage)
    vi.mocked(listPublishedPosts).mockReset().mockResolvedValue({ items: [], page: 0, size: 20, totalElements: 0 })
  })

  it('FR059_create mode never calls getMyPost and saves with submit=false, then shows one destination toast', async () => {
    const user = userEvent.setup()
    renderEditor('/community/posts/new')
    await user.type(screen.getByRole('textbox', { name: 'Tiêu đề' }), 'Bài mới')
    await user.selectOptions(screen.getByRole('combobox', { name: 'Danh mục' }), 'QUESTION')
    await user.type(screen.getByRole('textbox', { name: 'Nội dung' }), 'Nội dung')
    await user.click(screen.getByRole('button', { name: 'Lưu nháp' }))
    await waitFor(() => expect(createPost).toHaveBeenCalledWith({ title: 'Bài mới', content: 'Nội dung', category: 'QUESTION', submit: false }))
    expect(getMyPost).not.toHaveBeenCalled()
    expect(await screen.findByText('Đã lưu bản nháp')).toBeVisible()
    expect(screen.getAllByText('Đã lưu bản nháp')).toHaveLength(1)
    expect(screen.getByLabelText('current location')).toHaveTextContent('/community?tab=mine')
    expect(listMyPosts).toHaveBeenCalled()
  })

  it('FR059_submit sends submit=true and leaves a single success toast after route navigation', async () => {
    const user = userEvent.setup()
    renderEditor('/community/posts/new')
    await user.type(screen.getByRole('textbox', { name: 'Tiêu đề' }), 'Bài gửi duyệt')
    await user.selectOptions(screen.getByRole('combobox', { name: 'Danh mục' }), 'CULTURE')
    await user.type(screen.getByRole('textbox', { name: 'Nội dung' }), 'Nội dung có xuống dòng\nthuần văn bản')
    await user.click(screen.getByRole('button', { name: 'Gửi duyệt' }))
    expect(await screen.findByText('Bài đã gửi, chờ duyệt')).toBeVisible()
    expect(createPost).toHaveBeenCalledWith({ title: 'Bài gửi duyệt', content: 'Nội dung có xuống dòng\nthuần văn bản', category: 'CULTURE', submit: true })
    expect(screen.getAllByText('Bài đã gửi, chờ duyệt')).toHaveLength(1)
    expect(screen.getByLabelText('current location')).toHaveTextContent('/community?tab=mine')
  })

  it.each(['abc', '0', '-2', '1.0', '9007199254740992'])('FR059_invalid edit ID %s never calls getMyPost', async (id) => {
    renderEditor(`/community/posts/${id}/edit`)
    expect(await screen.findByRole('alert')).toHaveTextContent('ID bài viết không hợp lệ')
    expect(getMyPost).not.toHaveBeenCalled()
  })

  it('FR059_valid edit ID loads through getMyPost and updates using PUT wrapper body', async () => {
    const user = userEvent.setup()
    renderEditor('/community/posts/42/edit')
    expect(await screen.findByDisplayValue(ownPost.title)).toBeVisible()
    expect(getMyPost).toHaveBeenCalledWith(42, expect.any(AbortSignal))
    await user.clear(screen.getByRole('textbox', { name: 'Tiêu đề' }))
    await user.type(screen.getByRole('textbox', { name: 'Tiêu đề' }), 'Sửa tiêu đề')
    await user.click(screen.getByRole('button', { name: 'Gửi duyệt' }))
    expect(await screen.findByText('Bài đã gửi, chờ duyệt')).toBeVisible()
    expect(updatePost).toHaveBeenCalledWith(42, { title: 'Sửa tiêu đề', content: ownPost.content, category: ownPost.category, submit: true })
  })

  it('FR059_initial HIDDEN post is read-only, shows its reason, and offers mine navigation', async () => {
    vi.mocked(getMyPost).mockResolvedValue({ ...ownPost, status: 'HIDDEN', reviewReason: 'Đã ẩn theo quy định.' })
    renderEditor('/community/posts/42/edit')
    expect(await screen.findByText('Đã bị ẩn')).toBeVisible()
    expect(screen.getByText('Đã ẩn theo quy định.')).toBeVisible()
    expect(screen.queryByLabelText('Tiêu đề')).not.toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Về Bài viết của tôi' })).toHaveAttribute('href', '/community?tab=mine')
  })

  it('FR059_REJECTED edit displays its latest reason as safe text', async () => {
    vi.mocked(getMyPost).mockResolvedValue({ ...ownPost, status: 'REJECTED', reviewReason: '<script>reason</script>' })
    renderEditor('/community/posts/42/edit')
    expect(await screen.findByText('Lý do từ chối: <script>reason</script>')).toBeVisible()
    expect(screen.queryByRole('script')).not.toBeInTheDocument()
  })

  it.each([null, undefined] as const)('FR059_REJECTED edit with reviewReason %s keeps the editor without a fabricated reason', async (reviewReason) => {
    vi.mocked(getMyPost).mockResolvedValue({ ...ownPost, status: 'REJECTED', reviewReason })
    renderEditor('/community/posts/42/edit')
    expect(await screen.findByRole('textbox', { name: 'Tiêu đề' })).toBeVisible()
    expect(screen.getByRole('combobox', { name: 'Danh mục' })).toBeVisible()
    expect(screen.getByRole('textbox', { name: 'Nội dung' })).toBeVisible()
    expect(screen.queryByRole('note')).not.toBeInTheDocument()
    expect(screen.queryByText(/Lý do từ chối:/)).not.toBeInTheDocument()
  })

  it('FR059_APPROVED edit displays the exact re-review warning before mutation', async () => {
    vi.mocked(getMyPost).mockResolvedValue({ ...ownPost, status: 'APPROVED', publishedAt: '2026-10-30T00:00:00Z' })
    renderEditor('/community/posts/42/edit')
    expect(await screen.findByRole('note')).toHaveTextContent('Bài sẽ tạm ẩn khỏi cộng đồng cho tới khi được duyệt lại')
  })

  it('FR059_409 POST_NOT_EDITABLE locks editing and preserves unsaved values', async () => {
    const user = userEvent.setup()
    vi.mocked(updatePost).mockRejectedValue(new ApiError({ status: 409, errorCode: 'POST_NOT_EDITABLE', message: 'Bài không thể sửa.', requestId: 'r409' }))
    renderEditor('/community/posts/42/edit')
    await user.clear(await screen.findByRole('textbox', { name: 'Tiêu đề' }))
    await user.type(screen.getByRole('textbox', { name: 'Tiêu đề' }), 'Chưa lưu - giữ nguyên')
    await user.click(screen.getByRole('button', { name: 'Gửi duyệt' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('Bài không thể sửa.')
    expect(screen.getByRole('textbox', { name: 'Tiêu đề' })).toHaveValue('Chưa lưu - giữ nguyên')
    expect(screen.getByRole('textbox', { name: 'Tiêu đề' })).toBeDisabled()
    expect(screen.getByRole('link', { name: 'Về Bài viết của tôi' })).toBeVisible()
    expect(updatePost).toHaveBeenCalledTimes(1)
  })

  it('FR059_validation blocks invalid/blank forms before requesting', async () => {
    const user = userEvent.setup()
    renderEditor('/community/posts/new')
    await user.click(screen.getByRole('button', { name: 'Gửi duyệt' }))
    expect(await screen.findByText('Nhập tiêu đề bài viết.')).toBeVisible()
    expect(createPost).not.toHaveBeenCalled()
  })

  it.each([
    [403, 'ACCOUNT_UNVERIFIED', 'Cần xác minh email.'],
    [403, 'FORBIDDEN', 'Không có quyền sửa bài.'],
    [422, 'VALIDATION_FAILED', 'Dữ liệu không hợp lệ.'],
    [429, 'TOO_MANY_REQUESTS', 'Thử lại sau.'],
  ] as const)('FR059_mutation status %s displays server error and preserves form values', async (status, code, message) => {
    const user = userEvent.setup()
    vi.mocked(createPost).mockRejectedValue(new ApiError({ status, errorCode: code, message, requestId: `r${status}` }))
    renderEditor('/community/posts/new')
    await user.type(screen.getByRole('textbox', { name: 'Tiêu đề' }), 'Giữ nguyên tiêu đề')
    await user.selectOptions(screen.getByRole('combobox', { name: 'Danh mục' }), 'QUESTION')
    await user.type(screen.getByRole('textbox', { name: 'Nội dung' }), 'Giữ nguyên nội dung')
    await user.click(screen.getByRole('button', { name: 'Gửi duyệt' }))
    expect(await screen.findByRole('alert')).toHaveTextContent(message)
    expect(screen.getByRole('textbox', { name: 'Tiêu đề' })).toHaveValue('Giữ nguyên tiêu đề')
    expect(screen.getByRole('textbox', { name: 'Nội dung' })).toHaveValue('Giữ nguyên nội dung')
    if (code === 'ACCOUNT_UNVERIFIED') {
      expect(screen.getByRole('link', { name: 'Xác minh email' })).toHaveAttribute('href', '/verify-email')
    }
  })

  it('FR059_changing edit ID while request A is pending never shows response A on route B', async () => {
    const user = userEvent.setup()
    let resolveA!: (post: typeof ownPost) => void
    vi.mocked(getMyPost).mockImplementation((id) => id === 1
      ? new Promise((resolve) => { resolveA = resolve })
      : Promise.resolve({ ...ownPost, id: 2, title: 'Bài của ID B' }))
    function Switcher() { const navigate = useNavigate(); return <button onClick={() => navigate('/community/posts/2/edit')} type="button">Mở B</button> }
    const auth: AuthContextValue = { error: null, reloadCurrentUser: async () => undefined, setAuthenticatedUser: () => undefined, signOut: async () => undefined, status: 'authenticated', user: learner }
    render(<AuthContext.Provider value={auth}><MemoryRouter initialEntries={['/community/posts/1/edit']}><Switcher /><Routes><Route element={<Scr041CommunityPostEditorPage />} path="/community/posts/:id/edit" /></Routes></MemoryRouter></AuthContext.Provider>)
    expect(getMyPost).toHaveBeenCalledWith(1, expect.any(AbortSignal))
    await user.click(screen.getByRole('button', { name: 'Mở B' }))
    expect(await screen.findByDisplayValue('Bài của ID B')).toBeVisible()
    resolveA({ ...ownPost, id: 1, title: 'Bài của ID A' })
    expect(screen.queryByDisplayValue('Bài của ID A')).not.toBeInTheDocument()
  })

  it('FR059_double click and alternating submit buttons create only one request', async () => {
    const user = userEvent.setup()
    let resolve!: (value: typeof ownPost) => void
    vi.mocked(createPost).mockReturnValue(new Promise((done) => { resolve = done }))
    renderEditor('/community/posts/new')
    await user.type(screen.getByRole('textbox', { name: 'Tiêu đề' }), 'Bài mới')
    await user.selectOptions(screen.getByRole('combobox', { name: 'Danh mục' }), 'QUESTION')
    await user.type(screen.getByRole('textbox', { name: 'Nội dung' }), 'Nội dung')
    await user.click(screen.getByRole('button', { name: 'Lưu nháp' }))
    await user.click(screen.getByRole('button', { name: 'Gửi duyệt' }))
    expect(createPost).toHaveBeenCalledTimes(1)
    resolve(ownPost)
    expect(await screen.findByText('Đã lưu bản nháp')).toBeVisible()
    expect(screen.queryByText('Bài đã gửi, chờ duyệt')).not.toBeInTheDocument()
  })

  it('FR059_reload on mine URL preserves the tab and does not replay a consumed success toast', async () => {
    renderEditor('/community?tab=mine')
    expect(await screen.findByRole('tab', { name: 'Bài viết của tôi' })).toHaveAttribute('aria-selected', 'true')
    expect(screen.queryByText('Đã lưu bản nháp')).not.toBeInTheDocument()
    expect(screen.queryByText('Bài đã gửi, chờ duyệt')).not.toBeInTheDocument()
  })
})
