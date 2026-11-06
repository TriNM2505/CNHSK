import { act, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes, useLocation, useNavigate } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import type { CurrentUser } from '@/features/auth/types'
import Scr004VerifyEmailPage from '@/features/auth/pages/Scr004VerifyEmailPage'
import { ApiError } from '@/shared/api'
import { AuthContext, type AuthContextValue } from '@/shared/auth'

import { createPost, getMyPost, listMyPosts, listPublishedPosts, updatePost } from '../api/posts'
import { postEditorDraftKey, readPostEditorDraft, writePostEditorDraft } from '../post-editor-drafts'
import { ownPost, ownPage } from '../test-fixtures'
import Scr039CommunityPostListPage from './Scr039CommunityPostListPage'
import Scr041CommunityPostEditorPage from './Scr041CommunityPostEditorPage'

vi.mock('../api/posts', () => ({
  createPost: vi.fn(), getMyPost: vi.fn(), listMyPosts: vi.fn(), listPublishedPosts: vi.fn(), updatePost: vi.fn(),
}))

const learner: CurrentUser = { id: 7, displayName: 'Learner', email: 'learner@test.invalid', emailVerified: true, roles: ['LEARNER'] }

function renderEditorSession(path: string, origin?: string) {
  const initialAuth: AuthContextValue = { error: null, reloadCurrentUser: async () => undefined, setAuthenticatedUser: () => undefined, signOut: async () => undefined, status: 'authenticated', user: learner }
  function Navigation() {
    const location = useLocation()
    const navigate = useNavigate()
    return <><output aria-label="session location">{location.pathname}{location.search}</output><button onClick={() => navigate('/outside')} type="button">Rời trình soạn thảo</button><button onClick={() => navigate(path)} type="button">Trở lại trình soạn thảo</button></>
  }
  const view = (auth: AuthContextValue) => <AuthContext.Provider value={auth}><MemoryRouter initialEntries={[{ pathname: path, state: { communityEditorOrigin: origin } }]}><Navigation /><Routes>
    <Route element={<Scr041CommunityPostEditorPage />} path="/community/posts/new" />
    <Route element={<Scr041CommunityPostEditorPage />} path="/community/posts/:id/edit" />
    <Route element={<Scr004VerifyEmailPage />} path="/verify-email" />
    <Route element={<p>Đã rời trình soạn thảo</p>} path="*" />
  </Routes></MemoryRouter></AuthContext.Provider>
  const result = render(view(initialAuth))
  return { ...result, setSession: (user: CurrentUser | null, status: AuthContextValue['status'] = user ? 'authenticated' : 'guest') => result.rerender(view({ ...initialAuth, user, status })) }
}

async function fillCreateForm() {
  const user = userEvent.setup()
  await user.type(screen.getByRole('textbox', { name: 'Tiêu đề' }), 'Bản nháp của tôi')
  await user.selectOptions(screen.getByRole('combobox', { name: 'Danh mục' }), 'QUESTION')
  await user.type(screen.getByRole('textbox', { name: 'Nội dung' }), 'Nội dung chưa lưu')
  return user
}

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
    sessionStorage.clear()
    vi.mocked(createPost).mockReset().mockResolvedValue(ownPost)
    vi.mocked(updatePost).mockReset().mockResolvedValue(ownPost)
    vi.mocked(getMyPost).mockReset().mockResolvedValue(ownPost)
    vi.mocked(listMyPosts).mockReset().mockResolvedValue(ownPage)
    vi.mocked(listPublishedPosts).mockReset().mockResolvedValue({ items: [], page: 0, size: 20, totalElements: 0 })
  })
  afterEach(() => vi.restoreAllMocks())

  it.each(['/community/posts/new', '/community/posts/42/edit'])('ignores mutation success after leaving editor %s', async (path) => {
    let resolve!: (post: typeof ownPost) => void
    const pending = new Promise<typeof ownPost>((done) => { resolve = done })
    vi.mocked(createPost).mockReturnValue(pending)
    vi.mocked(updatePost).mockReturnValue(pending)
    renderEditorSession(path)
    if (path.endsWith('/new')) await fillCreateForm()
    else await screen.findByDisplayValue(ownPost.title)
    const user = userEvent.setup()
    await user.click(screen.getByRole('button', { name: 'Lưu nháp' }))
    await user.click(screen.getByRole('button', { name: 'Rời trình soạn thảo' }))
    await act(async () => { resolve(ownPost); await pending })
    expect(screen.getByLabelText('session location')).toHaveTextContent('/outside')
    expect(screen.getByText('Đã rời trình soạn thảo')).toBeVisible()
  })

  it.each(['account change', 'account roundtrip', 'role loss', 'auth loading', 'auth error', 'guest'] as const)('ignores an old save success after %s', async (change) => {
    let resolve!: (post: typeof ownPost) => void
    const pending = new Promise<typeof ownPost>((done) => { resolve = done })
    vi.mocked(createPost).mockReturnValue(pending)
    const session = renderEditorSession('/community/posts/new')
    const user = await fillCreateForm()
    await user.click(screen.getByRole('button', { name: 'Lưu nháp' }))
    if (change === 'role loss') session.setSession({ ...learner, roles: [] })
    else if (change === 'auth loading') session.setSession(learner, 'loading')
    else if (change === 'auth error') session.setSession(learner, 'error')
    else if (change === 'guest') session.setSession(null)
    else {
      session.setSession({ ...learner, id: 8 })
      if (change === 'account roundtrip') session.setSession(learner)
    }
    await act(async () => { resolve(ownPost); await pending })
    expect(screen.getByLabelText('session location')).toHaveTextContent('/community/posts/new')
  })

  it('ignores an old rejection after an account roundtrip', async () => {
    let reject!: (error: unknown) => void
    vi.mocked(createPost).mockReturnValue(new Promise((_, fail) => { reject = fail }))
    const session = renderEditorSession('/community/posts/new')
    const user = await fillCreateForm()
    await user.click(screen.getByRole('button', { name: 'Gửi duyệt' }))
    session.setSession({ ...learner, id: 8 })
    session.setSession(learner)
    await act(async () => { reject(new ApiError({ status: 403, errorCode: 'ACCOUNT_UNVERIFIED', message: 'Lỗi từ phiên cũ', requestId: 'old' })); await Promise.resolve() })
    expect(screen.queryByText('Lỗi từ phiên cũ')).not.toBeInTheDocument()
    expect(screen.queryByRole('link', { name: 'Xác minh email' })).not.toBeInTheDocument()
  })

  it('restores unsaved values and origin after the existing verification continuation', async () => {
    vi.mocked(createPost).mockRejectedValue(new ApiError({ status: 403, errorCode: 'ACCOUNT_UNVERIFIED', message: 'Cần xác minh email.', requestId: 'verify' }))
    renderEditorSession('/community/posts/new', '/community?tab=mine')
    const user = await fillCreateForm()
    await user.click(screen.getByRole('button', { name: 'Gửi duyệt' }))
    await user.click(await screen.findByRole('link', { name: 'Xác minh email' }))
    expect(screen.getByRole('link', { name: 'Tiếp tục' })).toHaveAttribute('href', '/community/posts/new')
    await user.click(screen.getByRole('link', { name: 'Tiếp tục' }))
    expect(await screen.findByRole('textbox', { name: 'Tiêu đề' })).toHaveValue('Bản nháp của tôi')
    expect(screen.getByRole('textbox', { name: 'Nội dung' })).toHaveValue('Nội dung chưa lưu')
    expect(screen.getByRole('combobox', { name: 'Danh mục' })).toHaveValue('QUESTION')
    expect(createPost).toHaveBeenCalledTimes(1)
    await user.click(screen.getByRole('button', { name: 'Hủy' }))
    expect(screen.getByRole('region', { name: 'Xác nhận bỏ thay đổi' })).toBeVisible()
    await user.click(screen.getByRole('button', { name: 'Bỏ thay đổi' }))
    expect(screen.getByLabelText('session location')).toHaveTextContent('/community?tab=mine')
  })

  it('keeps raw whitespace changes until inline discard is confirmed and returns to origin without writing', async () => {
    const user = userEvent.setup()
    renderEditorSession('/community/posts/42/edit', '/community/posts/42?view=discussion')
    await user.type(await screen.findByRole('textbox', { name: 'Tiêu đề' }), ' ')
    await user.click(screen.getByRole('button', { name: 'Hủy' }))
    expect(screen.getByLabelText('session location')).toHaveTextContent('/community/posts/42/edit')
    expect(screen.getByRole('region', { name: 'Xác nhận bỏ thay đổi' })).toBeVisible()
    await user.click(screen.getByRole('button', { name: 'Tiếp tục chỉnh sửa' }))
    expect(screen.getByRole('textbox', { name: 'Tiêu đề' })).toHaveValue(`${ownPost.title} `)
    await user.click(screen.getByRole('button', { name: 'Hủy' }))
    await user.click(screen.getByRole('button', { name: 'Bỏ thay đổi' }))
    expect(screen.getByLabelText('session location')).toHaveTextContent('/community/posts/42?view=discussion')
    expect(createPost).not.toHaveBeenCalled()
    expect(updatePost).not.toHaveBeenCalled()
  })

  it('keeps a newer draft when an old save resolves after an account roundtrip', async () => {
    let resolve!: (post: typeof ownPost) => void
    const pending = new Promise<typeof ownPost>((done) => { resolve = done })
    vi.mocked(createPost).mockReturnValue(pending)
    const session = renderEditorSession('/community/posts/new')
    const user = await fillCreateForm()
    await user.click(screen.getByRole('button', { name: 'Lưu nháp' }))
    session.setSession({ ...learner, id: 8 })
    expect(screen.getByRole('textbox', { name: 'Tiêu đề' })).toHaveValue('')
    session.setSession(learner)
    await user.clear(screen.getByRole('textbox', { name: 'Tiêu đề' }))
    await user.type(screen.getByRole('textbox', { name: 'Tiêu đề' }), 'Bản nháp mới hơn')
    await act(async () => { resolve(ownPost); await pending })
    expect(screen.getByLabelText('session location')).toHaveTextContent('/community/posts/new')
    expect(screen.getByRole('textbox', { name: 'Tiêu đề' })).toHaveValue('Bản nháp mới hơn')
    expect(readPostEditorDraft(learner.id, 'new')?.fields.title).toBe('Bản nháp mới hơn')
  })

  it('ignores an old edit rejection after switching accounts without locking the new owner', async () => {
    let reject!: (error: unknown) => void
    vi.mocked(updatePost).mockReturnValue(new Promise((_, fail) => { reject = fail }))
    const session = renderEditorSession('/community/posts/42/edit')
    const user = userEvent.setup()
    await screen.findByRole('textbox', { name: 'Tiêu đề' })
    await user.click(screen.getByRole('button', { name: 'Lưu nháp' }))
    session.setSession({ ...learner, id: 8 })
    await user.type(await screen.findByRole('textbox', { name: 'Tiêu đề' }), ' sửa mới')
    await act(async () => { reject(new ApiError({ status: 409, errorCode: 'POST_NOT_EDITABLE', message: 'Lỗi khoá từ phiên cũ', requestId: 'old-edit' })); await Promise.resolve() })
    expect(screen.queryByText('Lỗi khoá từ phiên cũ')).not.toBeInTheDocument()
    expect(screen.getByRole('textbox', { name: 'Tiêu đề' })).toBeEnabled()
    expect(readPostEditorDraft(8, 42)?.fields.title).toBe(`${ownPost.title} sửa mới`)
  })

  it('refreshes owned edit data after verification and overlays only the manual draft patch', async () => {
    vi.mocked(updatePost).mockRejectedValue(new ApiError({ status: 403, errorCode: 'ACCOUNT_UNVERIFIED', message: 'Cần xác minh email.', requestId: 'verify-edit' }))
    vi.mocked(getMyPost).mockResolvedValueOnce(ownPost).mockResolvedValue({ ...ownPost, content: 'Nội dung vừa được cập nhật', category: 'CULTURE', status: 'APPROVED' })
    renderEditorSession('/community/posts/42/edit', '/community/posts/42?view=discussion')
    const user = userEvent.setup()
    await user.clear(await screen.findByRole('textbox', { name: 'Tiêu đề' }))
    await user.type(screen.getByRole('textbox', { name: 'Tiêu đề' }), 'Tiêu đề đang soạn')
    await user.click(screen.getByRole('button', { name: 'Gửi duyệt' }))
    await user.click(await screen.findByRole('link', { name: 'Xác minh email' }))
    expect(screen.getByRole('link', { name: 'Tiếp tục' })).toHaveAttribute('href', '/community/posts/42/edit')
    await user.click(screen.getByRole('link', { name: 'Tiếp tục' }))
    expect(await screen.findByRole('textbox', { name: 'Tiêu đề' })).toHaveValue('Tiêu đề đang soạn')
    expect(screen.getByRole('textbox', { name: 'Nội dung' })).toHaveValue('Nội dung vừa được cập nhật')
    expect(screen.getByRole('combobox', { name: 'Danh mục' })).toHaveValue('CULTURE')
    expect(screen.getByRole('note')).toHaveTextContent('Bài sẽ tạm ẩn khỏi cộng đồng cho tới khi được duyệt lại')
    expect(getMyPost).toHaveBeenCalledTimes(2)
    expect(updatePost).toHaveBeenCalledTimes(1)
    expect(readPostEditorDraft(learner.id, 42)).toEqual({ fields: { title: 'Tiêu đề đang soạn' }, origin: '/community/posts/42?view=discussion' })
  })

  it('restores only changed fields after the owned edit request succeeds and keeps fresh server data', async () => {
    writePostEditorDraft(learner.id, 42, { fields: { title: '' }, origin: '/community/posts/42' })
    let resolve!: (post: typeof ownPost) => void
    const pending = new Promise<typeof ownPost>((done) => { resolve = done })
    vi.mocked(getMyPost).mockReturnValue(pending)
    renderEditorSession('/community/posts/42/edit')
    expect(screen.queryByRole('textbox', { name: 'Tiêu đề' })).not.toBeInTheDocument()
    await act(async () => { resolve({ ...ownPost, content: 'Nội dung mới từ máy chủ', category: 'CULTURE', status: 'APPROVED' }); await pending })
    expect(screen.getByRole('textbox', { name: 'Tiêu đề' })).toHaveValue('')
    expect(screen.getByRole('textbox', { name: 'Nội dung' })).toHaveValue('Nội dung mới từ máy chủ')
    expect(screen.getByRole('combobox', { name: 'Danh mục' })).toHaveValue('CULTURE')
    expect(screen.getByRole('note')).toHaveTextContent('Bài sẽ tạm ẩn khỏi cộng đồng cho tới khi được duyệt lại')
    expect(updatePost).not.toHaveBeenCalled()
  })

  it('does not read an edit draft before ownership succeeds', async () => {
    writePostEditorDraft(learner.id, 42, { fields: { title: 'Nội dung riêng đang soạn' } })
    const read = vi.spyOn(Storage.prototype, 'getItem')
    vi.mocked(getMyPost).mockRejectedValue(new ApiError({ status: 403, errorCode: 'FORBIDDEN', message: 'Không có quyền.', requestId: 'forbidden' }))
    renderEditorSession('/community/posts/42/edit')
    expect(await screen.findByRole('alert')).toHaveTextContent('Không có quyền.')
    expect(read).not.toHaveBeenCalled()
    expect(screen.queryByDisplayValue('Nội dung riêng đang soạn')).not.toBeInTheDocument()
  })

  it('blocks verification navigation when the latest draft cannot be stored', async () => {
    vi.mocked(createPost).mockRejectedValue(new ApiError({ status: 403, errorCode: 'ACCOUNT_UNVERIFIED', message: 'Cần xác minh email.', requestId: 'verify-storage' }))
    renderEditorSession('/community/posts/new')
    const user = await fillCreateForm()
    await user.click(screen.getByRole('button', { name: 'Gửi duyệt' }))
    const verification = await screen.findByRole('link', { name: 'Xác minh email' })
    const write = vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => { throw new DOMException('Quota exceeded', 'QuotaExceededError') })
    await user.click(verification)
    expect(screen.getByLabelText('session location')).toHaveTextContent('/community/posts/new')
    expect(screen.getByText(/Không thể lưu bản nháp trong tab này/)).toHaveAttribute('role', 'alert')
    expect(screen.getByRole('textbox', { name: 'Tiêu đề' })).toHaveValue('Bản nháp của tôi')
    write.mockRestore()
    await user.click(verification)
    expect(screen.getByRole('link', { name: 'Tiếp tục' })).toHaveAttribute('href', '/community/posts/new')
  })

  it.each([undefined, '//example.invalid/community', '/community/posts/new', '/admin/users'])('cancels a clean editor with origin %s to the safe fallback without a request', async (origin) => {
    renderEditorSession('/community/posts/new', origin)
    await userEvent.setup().click(screen.getByRole('button', { name: 'Hủy' }))
    expect(screen.getByLabelText('session location')).toHaveTextContent('/community?tab=mine')
    expect(screen.queryByRole('region', { name: 'Xác nhận bỏ thay đổi' })).not.toBeInTheDocument()
    expect(createPost).not.toHaveBeenCalled()
    expect(updatePost).not.toHaveBeenCalled()
  })

  it('allows cancelling a pending save and ignores its later success', async () => {
    let resolve!: (post: typeof ownPost) => void
    const pending = new Promise<typeof ownPost>((done) => { resolve = done })
    vi.mocked(createPost).mockReturnValue(pending)
    renderEditorSession('/community/posts/new', '/community')
    const user = await fillCreateForm()
    await user.click(screen.getByRole('button', { name: 'Lưu nháp' }))
    expect(screen.getByRole('button', { name: 'Hủy' })).toBeEnabled()
    await user.click(screen.getByRole('button', { name: 'Hủy' }))
    await user.click(screen.getByRole('button', { name: 'Bỏ thay đổi' }))
    expect(sessionStorage.getItem(postEditorDraftKey(learner.id, 'new'))).toBeNull()
    await act(async () => { resolve(ownPost); await pending })
    expect(screen.getByLabelText('session location')).toHaveTextContent(/^\/community$/)
    expect(createPost).toHaveBeenCalledTimes(1)
  })

  it('does not write a draft during unmount', async () => {
    const session = renderEditorSession('/community/posts/new')
    const write = vi.spyOn(Storage.prototype, 'setItem')
    await fillCreateForm()
    const writes = write.mock.calls.length
    session.unmount()
    expect(write).toHaveBeenCalledTimes(writes)
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
    expect(readPostEditorDraft(learner.id, 'new')).toBeUndefined()
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
    expect(screen.getByRole('button', { name: 'Hủy' })).toBeEnabled()
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
      expect(screen.getByRole('link', { name: 'Xác minh email' })).toHaveAttribute('href', '/verify-email?redirect=%2Fcommunity%2Fposts%2Fnew')
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
