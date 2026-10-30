import { act, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter, Route, Routes, useLocation, useNavigate } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { CurrentUser } from '@/features/auth/types'
import { ApiError } from '@/shared/api'
import { AuthContext, type AuthContextValue } from '@/shared/auth'
import * as moderation from '../api/moderation'
import { MODERATOR_DRAFT_TTL_MS, moderatorDraftKey } from '../moderator-drafts'
import { moderationPage, moderationPost } from '../test-fixtures'
import type { ModerationPost, PostStatus } from '../types'
import Scr060PostModerationQueuePage from './Scr060PostModerationQueuePage'
import Scr061PostModerationDetailPage from './Scr061PostModerationDetailPage'

vi.mock('../api/moderation', () => ({ getModerationPost: vi.fn(), approvePost: vi.fn(), rejectPost: vi.fn(), listPendingPosts: vi.fn() }))
vi.mock('../components/PostModerationComments', () => ({
  PostModerationComments: ({ postId, onChanged }: { postId: number; onChanged?: () => void }) => <div>Private comments {postId}<button type="button" onClick={onChanged}>Refresh from comments</button></div>,
}))

const moderator: CurrentUser = { id: 100, displayName: 'Kiểm duyệt viên', email: 'moderator@test.invalid', emailVerified: true, roles: ['MODERATOR'] }
const draftKey = (resourceId = 42, userId = 100) => moderatorDraftKey({ userId, screenId: 'SCR-061', resourceId, field: 'rejectionReason' })
const apiError = (status: number, errorCode: string) => new ApiError({ status, errorCode, message: errorCode, requestId: 'request-061', details: status === 422 ? [{ field: 'reason', message: 'Lý do không hợp lệ' }] : undefined, extra: status === 429 ? { retryAfterSeconds: 25 } : undefined })
function deferred<T>() {
  let resolve!: (value: T) => void
  let reject!: (reason: unknown) => void
  const promise = new Promise<T>((done, fail) => { resolve = done; reject = fail })
  return { promise, resolve, reject }
}
function Navigation() {
  const navigate = useNavigate()
  return <><button type="button" onClick={() => navigate('/moderation/posts/43')}>Post B</button><button type="button" onClick={() => navigate('/moderation/posts/42')}>Post A</button></>
}
function QueueDestination() {
  const location = useLocation()
  return <><h1>Queue destination</h1><output aria-label="Navigation state">{JSON.stringify(location.state)}</output></>
}
function CurrentLocation() {
  const location = useLocation()
  return <output aria-label="Current location">{location.pathname}{location.search}</output>
}
function shell(user: CurrentUser | null = moderator, path = '/moderation/posts/42', status: AuthContextValue['status'] = user ? 'authenticated' : 'guest', realQueue = false) {
  const auth: AuthContextValue = { status, user, error: status === 'error' ? apiError(503, 'AUTH_UNAVAILABLE') : null, reloadCurrentUser: async () => undefined, setAuthenticatedUser: () => undefined, signOut: async () => undefined }
  return <AuthContext.Provider value={auth}><MemoryRouter initialEntries={[path]}><Navigation /><CurrentLocation /><Routes><Route path="/moderation/posts/:id" element={<Scr061PostModerationDetailPage />} /><Route path="/moderation/posts" element={realQueue ? <Scr060PostModerationQueuePage /> : <QueueDestination />} /></Routes></MemoryRouter></AuthContext.Provider>
}
const ready = () => screen.findByRole('heading', { name: moderationPost.title })
function enterReason(value = '  Cần bổ sung nguồn  ') {
  fireEvent.click(screen.getByRole('button', { name: 'Từ chối' }))
  fireEvent.change(screen.getByRole('textbox', { name: 'Lý do từ chối' }), { target: { value } })
}
function confirmReject() {
  fireEvent.click(screen.getByRole('button', { name: 'Tiếp tục từ chối' }))
  fireEvent.click(screen.getByRole('button', { name: 'Xác nhận từ chối' }))
}
function confirmApprove() {
  fireEvent.click(screen.getByRole('button', { name: 'Duyệt' }))
  fireEvent.click(screen.getByRole('button', { name: 'Xác nhận duyệt' }))
}

beforeEach(() => {
  vi.resetAllMocks()
  sessionStorage.clear()
  vi.mocked(moderation.getModerationPost).mockResolvedValue(moderationPost)
  vi.mocked(moderation.approvePost).mockResolvedValue({ ...moderationPost, status: 'APPROVED' })
  vi.mocked(moderation.rejectPost).mockResolvedValue({ ...moderationPost, status: 'REJECTED' })
  vi.mocked(moderation.listPendingPosts).mockResolvedValue({ items: [], page: 0, size: 20, totalElements: 0 })
})

describe('SCR-061 moderation source and permissions', () => {
  it.each(['DRAFT', 'PENDING', 'APPROVED', 'REJECTED', 'HIDDEN'] satisfies PostStatus[])('FR040_loads %s through the moderation endpoint and safely renders complete metadata', async (status) => {
    const content = '<img src=x onerror=alert(1)>\n中文'
    vi.mocked(moderation.getModerationPost).mockResolvedValue({ ...moderationPost, status, content, reviewReason: 'Lý do\n<script>alert(1)</script>', reviewedBy: { id: 8, displayName: null }, reviewedAt: '2026-10-30T03:00:00Z' })
    const view = render(shell())
    await ready()
    expect(moderation.getModerationPost).toHaveBeenCalledWith(42, expect.any(AbortSignal))
    expect(screen.getByText(content, { normalizer: (text) => text })).toHaveClass('whitespace-pre-wrap')
    expect(screen.getByText('Lý do\n<script>alert(1)</script>', { normalizer: (text) => text })).toHaveClass('whitespace-pre-wrap')
    expect(screen.getAllByText('Người dùng đã rời')).toHaveLength(2)
    expect(view.container.querySelector('img, script')).toBeNull()
    expect(view.container.querySelector('time[datetime="2026-10-30T03:00:00Z"]')).toHaveTextContent('10:00:00')
    expect(screen.queryByRole('button', { name: 'Duyệt' }) !== null).toBe(status === 'PENDING')
    expect(screen.queryByRole('button', { name: 'Từ chối' }) !== null).toBe(status === 'PENDING')
    expect(screen.getByRole('button', { name: 'Bình luận' })).toBeEnabled()
  })

  it('FR040_keeps absent review metadata empty without inventing a submission timestamp', async () => {
    render(shell())
    await ready()
    expect(screen.getAllByText('Chưa có')).toHaveLength(4)
    expect(screen.queryByText('Thời gian gửi duyệt')).not.toBeInTheDocument()
  })

  it('FR060_hides both decisions for the author while retaining read access and comments', async () => {
    vi.mocked(moderation.getModerationPost).mockResolvedValue({ ...moderationPost, author: { id: moderator.id, displayName: 'Tôi' } })
    render(shell())
    await ready()
    expect(screen.getByText('Bạn không thể kiểm duyệt bài viết của chính mình.')).toBeVisible()
    expect(screen.queryByRole('button', { name: 'Duyệt' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Từ chối' })).not.toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: 'Bình luận' }))
    expect(screen.getByText('Private comments 42')).toBeVisible()
  })

  it.each([null, { ...moderator, roles: ['ADMIN'] }, { ...moderator, roles: ['LEARNER'] }, { ...moderator, roles: ['TEACHER', 'CONTENT_EDITOR', 'ACCOUNTANT'] }] as (CurrentUser | null)[])('FR040_does not fetch private detail without an explicit moderator role', (user) => {
    render(shell(user))
    expect(moderation.getModerationPost).not.toHaveBeenCalled()
    expect(screen.queryByText(moderationPost.content)).not.toBeInTheDocument()
  })

  it.each(['loading', 'error'] as const)('FR040_does not fetch before auth %s is resolved', (status) => {
    render(shell(moderator, '/moderation/posts/42', status))
    expect(moderation.getModerationPost).not.toHaveBeenCalled()
  })

  it.each(['0', '-1', '2.5', '1e2', 'abc', '9007199254740992'])('FR040_rejects malformed or unsafe id %s without a request', (raw) => {
    render(shell(moderator, `/moderation/posts/${raw}`))
    expect(screen.getByRole('heading', { name: 'Không tìm thấy bài viết' })).toBeVisible()
    expect(moderation.getModerationPost).not.toHaveBeenCalled()
  })

  it('FR040_supports the maximum safe positive id', async () => {
    render(shell(moderator, '/moderation/posts/9007199254740991'))
    await ready()
    expect(moderation.getModerationPost).toHaveBeenCalledWith(Number.MAX_SAFE_INTEGER, expect.any(AbortSignal))
  })
})

describe('SCR-061 queue return context', () => {
  it('FR039_FR040_returns from a real page3 queue detail to page3 with a fresh list GET', async () => {
    vi.mocked(moderation.listPendingPosts).mockResolvedValue({ ...moderationPage, page: 3, totalElements: 80 })
    render(shell(moderator, '/moderation/posts?page=3', 'authenticated', true))
    fireEvent.click(await screen.findByRole('link', { name: moderationPost.title }))
    await ready()
    expect(screen.getByLabelText('Current location')).toHaveTextContent('/moderation/posts/42?page=3')
    expect(moderation.listPendingPosts).toHaveBeenCalledTimes(1)
    expect(screen.getByRole('link', { name: 'Về hàng đợi bài viết' })).toHaveAttribute('href', '/moderation/posts?page=3')
    fireEvent.click(screen.getByRole('link', { name: 'Về hàng đợi bài viết' }))
    expect(await screen.findByRole('link', { name: moderationPost.title })).toBeVisible()
    expect(screen.getByLabelText('Current location')).toHaveTextContent(/^\/moderation\/posts\?page=3$/)
    expect(moderation.listPendingPosts).toHaveBeenCalledTimes(2)
    for (const [query] of vi.mocked(moderation.listPendingPosts).mock.calls) expect(query).toEqual({ page: 3, size: 20 })
    expect(moderation.approvePost).not.toHaveBeenCalled()
    expect(moderation.rejectPost).not.toHaveBeenCalled()
  })

  it.each(['approve', 'reject', 'cancel'] as const)('FR041_FR043_FR060_preserves page3 after %s without changing success or cancellation flash semantics', async (outcome) => {
    vi.mocked(moderation.listPendingPosts).mockResolvedValue({ ...moderationPage, page: 3, totalElements: 80 })
    render(shell(moderator, '/moderation/posts/42?page=3', 'authenticated', true))
    await ready()
    expect(moderation.listPendingPosts).not.toHaveBeenCalled()
    if (outcome === 'approve') confirmApprove()
    else if (outcome === 'reject') { enterReason(); confirmReject() }
    else { enterReason('Cancelled reason'); fireEvent.click(screen.getByRole('button', { name: 'Huỷ kiểm duyệt' })) }
    expect(await screen.findByRole('link', { name: moderationPost.title })).toBeVisible()
    expect(screen.getByLabelText('Current location')).toHaveTextContent(/^\/moderation\/posts\?page=3$/)
    expect(moderation.listPendingPosts).toHaveBeenCalledTimes(1)
    expect(moderation.listPendingPosts).toHaveBeenCalledWith({ page: 3, size: 20 }, expect.any(AbortSignal))
    expect(moderation.approvePost).toHaveBeenCalledTimes(outcome === 'approve' ? 1 : 0)
    expect(moderation.rejectPost).toHaveBeenCalledTimes(outcome === 'reject' ? 1 : 0)
    expect(sessionStorage.getItem(draftKey())).toBeNull()
    if (outcome === 'cancel') {
      expect(screen.queryByText('Đã duyệt bài viết')).not.toBeInTheDocument()
      expect(screen.queryByText('Đã từ chối bài viết')).not.toBeInTheDocument()
    } else expect(screen.getByText(outcome === 'approve' ? 'Đã duyệt bài viết' : 'Đã từ chối bài viết')).toBeVisible()
  })

  it.each(['approve', 'reject'] as const)('FR042_FR060_returns %s POST_NOT_PENDING to empty page3 once and waits for manual return to page0', async (decision) => {
    vi.mocked(moderation.approvePost).mockRejectedValue(apiError(409, 'POST_NOT_PENDING'))
    vi.mocked(moderation.rejectPost).mockRejectedValue(apiError(409, 'POST_NOT_PENDING'))
    vi.mocked(moderation.listPendingPosts)
      .mockResolvedValueOnce({ items: [], page: 3, size: 20, totalElements: 1 })
      .mockResolvedValueOnce(moderationPage)
    render(shell(moderator, '/moderation/posts/42?page=3', 'authenticated', true))
    await ready()
    if (decision === 'approve') confirmApprove()
    else { enterReason(); confirmReject() }
    expect(await screen.findByText('Chưa có bài chờ duyệt')).toBeVisible()
    expect(screen.getByLabelText('Current location')).toHaveTextContent(/^\/moderation\/posts\?page=3$/)
    expect(screen.getAllByText('Bài đã được người khác xử lý')).toHaveLength(1)
    expect(moderation.listPendingPosts).toHaveBeenCalledTimes(1)
    expect(moderation.listPendingPosts).toHaveBeenLastCalledWith({ page: 3, size: 20 }, expect.any(AbortSignal))
    expect(moderation.getModerationPost).toHaveBeenCalledTimes(1)
    expect(moderation.approvePost).toHaveBeenCalledTimes(decision === 'approve' ? 1 : 0)
    expect(moderation.rejectPost).toHaveBeenCalledTimes(decision === 'reject' ? 1 : 0)
    fireEvent.click(screen.getByRole('button', { name: 'Về trang đầu' }))
    expect(await screen.findByRole('link', { name: moderationPost.title })).toBeVisible()
    expect(screen.getByLabelText('Current location')).toHaveTextContent(/^\/moderation\/posts\?page=0$/)
    expect(moderation.listPendingPosts).toHaveBeenCalledTimes(2)
    expect(moderation.listPendingPosts).toHaveBeenLastCalledWith({ page: 0, size: 20 }, expect.any(AbortSignal))
    expect(moderation.approvePost).toHaveBeenCalledTimes(decision === 'approve' ? 1 : 0)
    expect(moderation.rejectPost).toHaveBeenCalledTimes(decision === 'reject' ? 1 : 0)
  })

  it.each([null, '', 'abc', '-1', '1.5', '9007199254740992', 'https://outside.invalid/path', '//outside.invalid/path'])('FR039_FR040_defaults missing or invalid return page %s to a fixed internal page0 queue', async (page) => {
    const query = page === null ? '' : `?page=${encodeURIComponent(page)}&returnTo=https%3A%2F%2Foutside.invalid%2Fpath`
    render(shell(moderator, `/moderation/posts/42${query}`, 'authenticated', true))
    await ready()
    expect(screen.getByRole('link', { name: 'Về hàng đợi bài viết' })).toHaveAttribute('href', '/moderation/posts')
    fireEvent.click(screen.getByRole('link', { name: 'Về hàng đợi bài viết' }))
    expect(await screen.findByRole('heading', { name: 'Bài chờ duyệt' })).toBeVisible()
    expect(screen.getByLabelText('Current location')).toHaveTextContent(/^\/moderation\/posts$/)
    expect(moderation.listPendingPosts).toHaveBeenCalledTimes(1)
    expect(moderation.listPendingPosts).toHaveBeenCalledWith({ page: 0, size: 20 }, expect.any(AbortSignal))
  })
})

describe('SCR-061 review decisions', () => {
  it('FR041_FR045_requires explicit approval confirmation, sends only id, and clears the draft before success navigation', async () => {
    sessionStorage.setItem(draftKey(), JSON.stringify({ text: 'Unsent reason', savedAt: Date.now() }))
    render(shell())
    await ready()
    fireEvent.click(screen.getByRole('button', { name: 'Duyệt' }))
    expect(moderation.approvePost).not.toHaveBeenCalled()
    fireEvent.click(screen.getByRole('button', { name: 'Quay lại' }))
    expect(moderation.approvePost).not.toHaveBeenCalled()
    expect(screen.getByRole('button', { name: 'Duyệt' })).toHaveFocus()
    confirmApprove()
    await screen.findByRole('heading', { name: 'Queue destination' })
    expect(moderation.approvePost).toHaveBeenCalledTimes(1)
    expect(moderation.approvePost).toHaveBeenCalledWith(42)
    expect(screen.getByLabelText('Navigation state')).toHaveTextContent('"moderationFlash":{"code":"postApproved","userId":100}')
    expect(sessionStorage.getItem(draftKey())).toBeNull()
  })

  it('FR043_keeps rejection inline and sends trimmed reason only after confirmation', async () => {
    render(shell())
    await ready()
    enterReason()
    fireEvent.click(screen.getByRole('button', { name: 'Tiếp tục từ chối' }))
    expect(moderation.rejectPost).not.toHaveBeenCalled()
    expect(screen.getByRole('textbox', { name: 'Lý do từ chối' })).toBeDisabled()
    fireEvent.click(screen.getByRole('button', { name: 'Quay lại' }))
    expect(screen.getByRole('textbox', { name: 'Lý do từ chối' })).toHaveValue('  Cần bổ sung nguồn  ')
    confirmReject()
    await screen.findByRole('heading', { name: 'Queue destination' })
    expect(moderation.rejectPost).toHaveBeenCalledTimes(1)
    expect(moderation.rejectPost).toHaveBeenCalledWith(42, { reason: 'Cần bổ sung nguồn' })
    expect(screen.getByLabelText('Navigation state')).toHaveTextContent('"code":"postRejected"')
    expect(sessionStorage.getItem(draftKey())).toBeNull()
  })

  it.each(['', '   ', 'x'.repeat(1001)])('FR043_validates rejection boundaries before showing confirmation (%s)', async (value) => {
    render(shell())
    await ready()
    enterReason(value)
    fireEvent.click(screen.getByRole('button', { name: 'Tiếp tục từ chối' }))
    expect(screen.getByRole('textbox', { name: 'Lý do từ chối' })).toHaveAttribute('aria-invalid', 'true')
    expect(screen.getByRole('textbox', { name: 'Lý do từ chối' })).toHaveFocus()
    expect(screen.queryByRole('button', { name: 'Xác nhận từ chối' })).not.toBeInTheDocument()
    expect(moderation.rejectPost).not.toHaveBeenCalled()
  })

  it.each([1, 1000])('FR043_accepts %i trimmed rejection characters', async (length) => {
    render(shell())
    await ready()
    enterReason(`  ${'x'.repeat(length)}  `)
    confirmReject()
    await screen.findByRole('heading', { name: 'Queue destination' })
    expect(moderation.rejectPost).toHaveBeenCalledWith(42, { reason: 'x'.repeat(length) })
  })

  it('FR041_FR043_synchronously suppresses double approval and competing rejection while mutation is pending', async () => {
    const pending = deferred<ModerationPost>()
    vi.mocked(moderation.approvePost).mockReturnValue(pending.promise)
    render(shell())
    await ready()
    fireEvent.click(screen.getByRole('button', { name: 'Duyệt' }))
    const confirm = screen.getByRole('button', { name: 'Xác nhận duyệt' })
    act(() => { confirm.click(); confirm.click() })
    fireEvent.click(screen.getByRole('button', { name: 'Từ chối' }))
    await waitFor(() => expect(moderation.approvePost).toHaveBeenCalledTimes(1))
    expect(moderation.rejectPost).not.toHaveBeenCalled()
    expect(screen.getByRole('button', { name: 'Duyệt' })).toBeDisabled()
    await act(async () => pending.resolve({ ...moderationPost, status: 'APPROVED' }))
    await screen.findByRole('heading', { name: 'Queue destination' })
  })

  it.each(['approve', 'reject'] as const)('FR042_FR060_returns to queue with conflict flash after %s 409 and never retries PATCH', async (decision) => {
    vi.mocked(moderation.approvePost).mockRejectedValue(apiError(409, 'POST_NOT_PENDING'))
    vi.mocked(moderation.rejectPost).mockRejectedValue(apiError(409, 'POST_NOT_PENDING'))
    sessionStorage.setItem(draftKey(), JSON.stringify({ text: 'Conflict draft', savedAt: Date.now() }))
    render(shell())
    await ready()
    if (decision === 'approve') confirmApprove()
    else { enterReason(); confirmReject() }
    await screen.findByRole('heading', { name: 'Queue destination' })
    expect(screen.getByLabelText('Navigation state')).toHaveTextContent('"moderationFlash":{"code":"postConflict","userId":100}')
    expect(vi.mocked(moderation.approvePost).mock.calls.length + vi.mocked(moderation.rejectPost).mock.calls.length).toBe(1)
    expect(JSON.parse(sessionStorage.getItem(draftKey()) ?? '{}').text).toBe(decision === 'reject' ? '  Cần bổ sung nguồn  ' : 'Conflict draft')
  })

  it.each(['approve', 'reject', 'conflict'] as const)('FR060_navigates %s to the real queue with the exact result message and a fresh queue GET', async (outcome) => {
    if (outcome === 'conflict') vi.mocked(moderation.approvePost).mockRejectedValue(apiError(409, 'POST_NOT_PENDING'))
    render(shell(moderator, '/moderation/posts/42', 'authenticated', true))
    await ready()
    expect(moderation.listPendingPosts).not.toHaveBeenCalled()
    if (outcome === 'reject') { enterReason(); confirmReject() }
    else confirmApprove()
    await screen.findByRole('heading', { name: 'Bài chờ duyệt' })
    expect(await screen.findByText(outcome === 'conflict' ? 'Bài đã được người khác xử lý' : outcome === 'approve' ? 'Đã duyệt bài viết' : 'Đã từ chối bài viết')).toBeVisible()
    await waitFor(() => expect(moderation.listPendingPosts).toHaveBeenCalledTimes(1))
    expect(moderation.listPendingPosts).toHaveBeenCalledWith({ page: 0, size: 20 }, expect.any(AbortSignal))
  })

  it.each([[403, 'SELF_MODERATION'], [404, 'POST_NOT_FOUND'], [409, 'OTHER_CONFLICT'], [422, 'VALIDATION_FAILED'], [429, 'RATE_LIMITED'], [503, 'UNAVAILABLE']] as const)('FR043_FR060_retains typed reason after %i %s and does not claim success', async (status, code) => {
    vi.mocked(moderation.rejectPost).mockRejectedValue(apiError(status, code))
    render(shell())
    await ready()
    enterReason('Keep safe draft')
    confirmReject()
    expect(await screen.findByRole('alert')).toHaveTextContent(code)
    expect(screen.getByRole('alert')).toHaveTextContent('request-061')
    expect(screen.getByRole('textbox', { name: 'Lý do từ chối' })).toHaveValue('Keep safe draft')
    expect(JSON.parse(sessionStorage.getItem(draftKey()) ?? '{}').text).toBe('Keep safe draft')
    expect(moderation.rejectPost).toHaveBeenCalledTimes(1)
    expect(screen.queryByRole('heading', { name: 'Queue destination' })).not.toBeInTheDocument()
    if (status === 422) expect(screen.getByRole('alert')).toHaveTextContent('Lý do không hợp lệ')
    if (status === 429) expect(screen.getByRole('alert')).toHaveTextContent('25 giây')
  })

  it('FR060_retains reason after network failure', async () => {
    vi.mocked(moderation.rejectPost).mockRejectedValue(new TypeError('fetch failed'))
    render(shell())
    await ready()
    enterReason('Network draft')
    confirmReject()
    expect(await screen.findByRole('alert')).toHaveTextContent('Không thể kết nối tới máy chủ')
    expect(screen.getByRole('textbox', { name: 'Lý do từ chối' })).toHaveValue('Network draft')
  })
})

describe('SCR-061 query and draft lifetime', () => {
  it('FR040_FR063_loads comments lazily, hides cached detail/comments during refresh failure, and retains reason without reopening confirmation', async () => {
    render(shell())
    await ready()
    expect(screen.queryByText('Private comments 42')).not.toBeInTheDocument()
    enterReason('Refresh draft')
    fireEvent.click(screen.getByRole('button', { name: 'Tiếp tục từ chối' }))
    fireEvent.click(screen.getByRole('button', { name: 'Bình luận' }))
    expect(screen.getByText('Private comments 42')).toBeVisible()
    const pending = deferred<ModerationPost>()
    vi.mocked(moderation.getModerationPost).mockReturnValueOnce(pending.promise)
    fireEvent.click(screen.getByRole('button', { name: 'Refresh from comments' }))
    expect(screen.getByText('Đang tải bài viết…')).toBeVisible()
    expect(screen.queryByRole('heading', { name: moderationPost.title })).not.toBeInTheDocument()
    expect(screen.queryByText('Private comments 42')).not.toBeInTheDocument()
    expect(screen.queryByRole('textbox', { name: 'Lý do từ chối' })).not.toBeInTheDocument()
    await act(async () => pending.reject(apiError(503, 'DETAIL_UNAVAILABLE')))
    expect(await screen.findByRole('alert')).toHaveTextContent('DETAIL_UNAVAILABLE')
    expect(screen.queryByText('Private comments 42')).not.toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: 'Thử lại bài viết' }))
    await ready()
    expect(screen.getByRole('textbox', { name: 'Lý do từ chối' })).toHaveValue('Refresh draft')
    expect(screen.queryByRole('button', { name: 'Xác nhận từ chối' })).not.toBeInTheDocument()
    expect(screen.getByText('Private comments 42')).toBeVisible()
    expect(moderation.rejectPost).not.toHaveBeenCalled()
  })

  it('FR060_retains a draft but hides decisions when refreshed detail is no longer pending', async () => {
    render(shell())
    await ready()
    enterReason('Now read only')
    vi.mocked(moderation.getModerationPost).mockResolvedValue({ ...moderationPost, status: 'APPROVED' })
    fireEvent.click(screen.getByRole('button', { name: 'Tải lại bài viết' }))
    expect(await screen.findByText('Bài viết không còn chờ duyệt. Chỉ có thể xem thông tin kiểm duyệt.')).toBeVisible()
    expect(screen.queryByRole('button', { name: 'Duyệt' })).not.toBeInTheDocument()
    expect(screen.queryByRole('textbox', { name: 'Lý do từ chối' })).not.toBeInTheDocument()
    expect(JSON.parse(sessionStorage.getItem(draftKey()) ?? '{}').text).toBe('Now read only')
  })

  it('FR040_aborts old route GET and ignores its out-of-order response', async () => {
    const pending = deferred<ModerationPost>()
    vi.mocked(moderation.getModerationPost).mockReturnValueOnce(pending.promise).mockResolvedValueOnce({ ...moderationPost, id: 43, title: 'Bài B' })
    render(shell())
    await waitFor(() => expect(moderation.getModerationPost).toHaveBeenCalledTimes(1))
    fireEvent.click(screen.getByRole('button', { name: 'Post B' }))
    await screen.findByRole('heading', { name: 'Bài B' })
    expect(vi.mocked(moderation.getModerationPost).mock.calls[0]?.[1]?.aborted).toBe(true)
    await act(async () => pending.resolve(moderationPost))
    expect(screen.queryByRole('heading', { name: moderationPost.title })).not.toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Bài B' })).toBeVisible()
  })

  it.each([null, { ...moderator, roles: ['LEARNER'] }] as (CurrentUser | null)[])('FR040_FR060_drops private data and ignores late mutation after logout or role loss', async (nextUser) => {
    const pending = deferred<ModerationPost>()
    vi.mocked(moderation.rejectPost).mockReturnValue(pending.promise)
    const view = render(shell())
    await ready()
    enterReason('Private draft')
    confirmReject()
    view.rerender(shell(nextUser))
    await act(async () => pending.resolve({ ...moderationPost, status: 'REJECTED' }))
    expect(screen.queryByText(moderationPost.content)).not.toBeInTheDocument()
    expect(screen.queryByRole('textbox', { name: 'Lý do từ chối' })).not.toBeInTheDocument()
    expect(screen.queryByRole('heading', { name: 'Queue destination' })).not.toBeInTheDocument()
    expect(JSON.parse(sessionStorage.getItem(draftKey()) ?? '{}').text).toBe('Private draft')
  })

  it('FR060_late decision for A cannot navigate away from B or clear its draft', async () => {
    const pending = deferred<ModerationPost>()
    vi.mocked(moderation.rejectPost).mockReturnValue(pending.promise)
    vi.mocked(moderation.getModerationPost).mockImplementation(async (id) => ({ ...moderationPost, id, title: id === 42 ? moderationPost.title : 'Bài B' }))
    render(shell())
    await ready()
    enterReason('A private draft')
    confirmReject()
    fireEvent.click(screen.getByRole('button', { name: 'Post B' }))
    await screen.findByRole('heading', { name: 'Bài B' })
    enterReason('B private draft')
    await act(async () => pending.resolve({ ...moderationPost, status: 'REJECTED' }))
    expect(screen.getByRole('heading', { name: 'Bài B' })).toBeVisible()
    expect(screen.getByRole('textbox', { name: 'Lý do từ chối' })).toHaveValue('B private draft')
    expect(JSON.parse(sessionStorage.getItem(draftKey(43)) ?? '{}').text).toBe('B private draft')
    expect(JSON.parse(sessionStorage.getItem(draftKey()) ?? '{}').text).toBe('A private draft')
  })

  it('FR060_rehydrates only manually typed text for the same resource and never persists confirmation or server content', async () => {
    vi.mocked(moderation.getModerationPost).mockResolvedValue({ ...moderationPost, reviewReason: 'Server reason' })
    const first = render(shell())
    await ready()
    expect(sessionStorage.getItem(draftKey())).toBeNull()
    enterReason('Typed reason')
    fireEvent.click(screen.getByRole('button', { name: 'Tiếp tục từ chối' }))
    expect(JSON.parse(sessionStorage.getItem(draftKey()) ?? '{}')).toEqual({ text: 'Typed reason', savedAt: expect.any(Number) })
    first.unmount()
    render(shell())
    await ready()
    fireEvent.click(screen.getByRole('button', { name: 'Từ chối' }))
    expect(screen.getByRole('textbox', { name: 'Lý do từ chối' })).toHaveValue('Typed reason')
    expect(screen.queryByRole('button', { name: 'Xác nhận từ chối' })).not.toBeInTheDocument()
  })

  it('FR060_expires stored reason at two hours and removes foreign-account entries after the moderator gate', async () => {
    sessionStorage.setItem(draftKey(), JSON.stringify({ text: 'Expired reason', savedAt: Date.now() - MODERATOR_DRAFT_TTL_MS }))
    sessionStorage.setItem(draftKey(42, 101), JSON.stringify({ text: 'Other account', savedAt: Date.now() }))
    render(shell())
    await ready()
    fireEvent.click(screen.getByRole('button', { name: 'Từ chối' }))
    expect(screen.getByRole('textbox', { name: 'Lý do từ chối' })).toHaveValue('')
    expect(sessionStorage.getItem(draftKey())).toBeNull()
    expect(sessionStorage.getItem(draftKey(42, 101))).toBeNull()
  })

  it('FR060_clears typed reason when cancellation returns to the queue without a write', async () => {
    render(shell())
    await ready()
    enterReason('Cancelled draft')
    fireEvent.click(screen.getByRole('button', { name: 'Huỷ kiểm duyệt' }))
    await screen.findByRole('heading', { name: 'Queue destination' })
    expect(sessionStorage.getItem(draftKey())).toBeNull()
    expect(moderation.approvePost).not.toHaveBeenCalled()
    expect(moderation.rejectPost).not.toHaveBeenCalled()
  })
})
