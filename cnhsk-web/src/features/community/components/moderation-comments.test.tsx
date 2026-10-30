import { act, fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { CurrentUser } from '@/features/auth/types'
import { AuthContext, type AuthContextValue } from '@/shared/auth'
import { ApiError } from '@/shared/api'
import * as moderation from '../api/moderation'
import { moderationComment, moderationPost, reportCase } from '../test-fixtures'
import type { ModerationComment, ModerationPost, PostStatus, ReportCaseDetail } from '../types'
import Scr061PostModerationDetailPage from '../pages/Scr061PostModerationDetailPage'
import Scr063ViolationReportDetailPage from '../pages/Scr063ViolationReportDetailPage'
import { moderatorDraftKey, MODERATOR_DRAFT_PREFIX } from '../moderator-drafts'

vi.mock('../api/moderation', () => ({ getModerationPost: vi.fn(), approvePost: vi.fn(), rejectPost: vi.fn(), getReportCase: vi.fn(), decideReportCase: vi.fn(), listPostCommentsForModeration: vi.fn(), moderateComment: vi.fn() }))
const moderator: CurrentUser = { id: 100, displayName: 'Moderator test', email: 'mod@test.invalid', emailVerified: true, roles: ['MODERATOR'] }
const visible: ModerationComment = { ...moderationComment, status: 'VISIBLE', moderationReason: null, moderatedBy: null, content: 'Privileged source comment' }
const firstReport = reportCase.reports[0]
if (!firstReport || firstReport.reporter.displayName === null) throw new Error('Reporter fixture must have a display name.')
const reporterName = firstReport.reporter.displayName
function deferred<T>() { let resolve!: (value: T) => void; let reject!: (error: unknown) => void; const promise = new Promise<T>((done, fail) => { resolve = done; reject = fail }); return { promise, resolve, reject } }
function shell(path = '/moderation/posts/42', user: CurrentUser | null = moderator, status: AuthContextValue['status'] = user ? 'authenticated' : 'guest') {
  const auth: AuthContextValue = { user, status, error: null, reloadCurrentUser: async () => undefined, setAuthenticatedUser: () => undefined, signOut: async () => undefined }
  return <AuthContext.Provider value={auth}><MemoryRouter initialEntries={[path]}><Routes><Route path="/moderation/posts/:id" element={<Scr061PostModerationDetailPage />} /><Route path="/moderation/reports/:id" element={<Scr063ViolationReportDetailPage />} /></Routes></MemoryRouter></AuthContext.Provider>
}
const reason = () => screen.getByRole('textbox', { name: 'Lý do kiểm duyệt' })
const openPostComments = async () => fireEvent.click(await screen.findByRole('button', { name: 'Bình luận' }))
const openCasePanel = async () => fireEvent.click(await screen.findByRole('button', { name: 'Kiểm duyệt bình luận' }))
function typeAndConfirm(text = '  Moderator typed reason  ') {
  fireEvent.change(reason(), { target: { value: text } })
  fireEvent.click(screen.getByRole('button', { name: 'Tiếp tục' }))
  fireEvent.click(screen.getByRole('button', { name: 'Xác nhận kiểm duyệt' }))
}
beforeEach(() => {
  vi.resetAllMocks(); sessionStorage.clear()
  vi.mocked(moderation.getModerationPost).mockResolvedValue(moderationPost)
  vi.mocked(moderation.getReportCase).mockResolvedValue(reportCase)
  vi.mocked(moderation.listPostCommentsForModeration).mockResolvedValue([visible])
  vi.mocked(moderation.moderateComment).mockResolvedValue({ ...visible, status: 'HIDDEN', moderationReason: 'Moderator typed reason' })
})
afterEach(() => { vi.restoreAllMocks(); sessionStorage.clear() })
describe('SCR-061 and SCR-063 independent comment moderation', () => {
  it.each(['DRAFT', 'PENDING', 'APPROVED', 'REJECTED', 'HIDDEN'] satisfies PostStatus[])('FR040_FR063_all post status %s supports lazy full moderation comments', async (status) => {
    vi.mocked(moderation.getModerationPost).mockResolvedValue({ ...moderationPost, status })
    render(shell())
    await screen.findByRole('heading', { name: moderationPost.title })
    expect(moderation.listPostCommentsForModeration).not.toHaveBeenCalled()
    await openPostComments()
    expect(await screen.findByText(visible.content)).toBeVisible()
    expect(moderation.listPostCommentsForModeration).toHaveBeenCalledWith(42, expect.any(AbortSignal))
    expect(screen.queryByRole('button', { name: 'Trả lời' })).not.toBeInTheDocument()
  })
  it('FR052_HIDDEN direct moderation returns 200, refreshes case still PENDING and never decides it', async () => {
    render(shell('/moderation/reports/9'))
    await openCasePanel()
    fireEvent.click(await screen.findByRole('button', { name: 'Ẩn' }))
    typeAndConfirm()
    await waitFor(() => expect(moderation.moderateComment).toHaveBeenCalledWith(11, { action: 'HIDE', reason: 'Moderator typed reason' }))
    await waitFor(() => expect(moderation.getReportCase).toHaveBeenCalledTimes(2))
    expect(await screen.findByRole('form', { name: 'Quyết định hồ sơ' })).toBeVisible()
    expect(screen.getByText('Đã kiểm duyệt bình luận')).toBeVisible()
    expect(screen.queryByText('Đã xử lý hồ sơ báo cáo')).not.toBeInTheDocument()
    expect(screen.getByText('Trạng thái xử lý: Chờ xử lý')).toBeVisible()
    expect(moderation.decideReportCase).not.toHaveBeenCalled()
    expect(sessionStorage.getItem(moderatorDraftKey({ userId: 100, screenId: 'SCR-063', resourceId: 9, field: 'comment-11-reason' }))).toBeNull()
  })
  it.each(['ACTIONED', 'REJECTED'] as const)('FR052_closed case %s still permits eligible direct moderation', async (status) => {
    vi.mocked(moderation.getReportCase).mockResolvedValue({ ...reportCase, status })
    render(shell('/moderation/reports/9')); await openCasePanel()
    expect(await screen.findByRole('button', { name: 'Ẩn' })).toBeEnabled()
    expect(screen.queryByRole('form', { name: 'Quyết định hồ sơ' })).not.toBeInTheDocument()
  })
  it.each(['HIDE', 'DELETE', 'RESTORE'] as const)('FR052_FR055_%s sends exact comment payload with confirmation and preserves descendants', async (action) => {
    const parent = action === 'RESTORE' ? moderationComment : visible
    const child: ModerationComment = { ...visible, id: 12, parentId: 11, depth: 1, content: 'Descendant stays' }
    vi.mocked(moderation.listPostCommentsForModeration).mockResolvedValue([parent, child])
    const pending = deferred<ModerationComment>()
    vi.mocked(moderation.moderateComment).mockReturnValueOnce(pending.promise)
    render(shell()); await openPostComments()
    const card = await screen.findByRole('article', { name: 'Bình luận 11' })
    fireEvent.click(within(card).getByRole('button', { name: action === 'HIDE' ? 'Ẩn' : action === 'DELETE' ? 'Xoá kiểm duyệt' : 'Khôi phục' }))
    fireEvent.change(reason(), { target: { value: '  reason  ' } }); fireEvent.click(screen.getByRole('button', { name: 'Tiếp tục' }))
    expect(moderation.moderateComment).not.toHaveBeenCalled()
    fireEvent.click(screen.getByRole('button', { name: 'Xác nhận kiểm duyệt' })); fireEvent.click(screen.getByRole('button', { name: 'Xác nhận kiểm duyệt' }))
    await waitFor(() => expect(moderation.moderateComment).toHaveBeenCalledTimes(1))
    expect(moderation.moderateComment).toHaveBeenCalledWith(11, { action, reason: 'reason' })
    await act(async () => pending.resolve({ ...parent, status: action === 'RESTORE' ? 'VISIBLE' : action === 'DELETE' ? 'DELETED' : 'HIDDEN' }))
    expect(await screen.findByText('Descendant stays')).toBeVisible()
    expect(moderation.decideReportCase).not.toHaveBeenCalled()
  })
  it.each(['SELF', 'AUTHOR_DELETED'] as const)('FR054_FR055_%s metadata never enables forbidden actions', async (kind) => {
    vi.mocked(moderation.listPostCommentsForModeration).mockResolvedValue([{ ...moderationComment, author: kind === 'SELF' ? { id: 100, displayName: 'Self' } : moderationComment.author, moderationReason: kind === 'AUTHOR_DELETED' ? 'AUTHOR_DELETED' : 'Reason' }])
    render(shell('/moderation/reports/9')); await openCasePanel()
    await screen.findByText(kind === 'SELF' ? 'Self' : 'Lý do kiểm duyệt: AUTHOR_DELETED')
    expect(screen.queryByRole('button', { name: 'Khôi phục' })).not.toBeInTheDocument()
    expect(moderation.moderateComment).not.toHaveBeenCalled()
  })
  it('FR054_other moderator ownership does not block comment moderation', async () => {
    vi.mocked(moderation.listPostCommentsForModeration).mockResolvedValue([{ ...visible, author: { id: 101, displayName: 'Other moderator' } }])
    render(shell()); await openPostComments()
    expect(await screen.findByRole('button', { name: 'Ẩn' })).toBeEnabled()
  })
  it.each(['missing', 'wrongPost', 'duplicate', 'orphan'] as const)('FR063_%s metadata disables direct workflow without guessing restorability', async (kind) => {
    const entries = kind === 'missing' ? [] : kind === 'wrongPost' ? [{ ...visible, postId: 99 }] : kind === 'duplicate' ? [visible, visible] : [{ ...visible, parentId: 999, depth: 1 }]
    vi.mocked(moderation.listPostCommentsForModeration).mockResolvedValue(entries)
    render(shell('/moderation/reports/9')); await openCasePanel()
    await screen.findByRole('button', { name: 'Thử lại bình luận' })
    expect(screen.queryByRole('button', { name: 'Ẩn' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Khôi phục' })).not.toBeInTheDocument()
    expect(screen.getByRole('form', { name: 'Quyết định hồ sơ' })).toBeVisible()
  })
  it('FR063_parent refresh pending/error removes privileged comments and reloads only after ready', async () => {
    const parent = deferred<ModerationPost>()
    render(shell()); await openPostComments()
    await screen.findByText(visible.content)
    vi.mocked(moderation.getModerationPost).mockReturnValueOnce(parent.promise)
    fireEvent.click(screen.getByRole('button', { name: 'Tải lại bài viết' }))
    expect(screen.queryByText(visible.content)).not.toBeInTheDocument()
    expect(moderation.listPostCommentsForModeration).toHaveBeenCalledTimes(1)
    await act(async () => parent.reject(new ApiError({ status: 503, errorCode: 'FAILED', message: 'Refresh failed', requestId: 'test' })))
    await screen.findByText('Refresh failed')
    expect(screen.queryByText(visible.content)).not.toBeInTheDocument()
    expect(moderation.listPostCommentsForModeration).toHaveBeenCalledTimes(1)
    fireEvent.click(screen.getByRole('button', { name: 'Thử lại bài viết' }))
    expect(await screen.findByText(visible.content)).toBeVisible()
    expect(moderation.listPostCommentsForModeration).toHaveBeenCalledTimes(2)
  })
  it('FR063_direct draft and case note remain separate across refresh, storing no fetched data', async () => {
    render(shell('/moderation/reports/9')); await openCasePanel()
    expect(await screen.findByText(visible.content)).toBeVisible()
    expect(sessionStorage.length).toBe(0)
    fireEvent.change(screen.getByRole('textbox', { name: 'Lý do xử lý' }), { target: { value: 'Case note typed' } })
    fireEvent.click(screen.getByRole('button', { name: 'Ẩn' }))
    fireEvent.change(reason(), { target: { value: 'Direct reason typed' } })
    fireEvent.click(screen.getByRole('button', { name: 'Tiếp tục' }))
    fireEvent.click(screen.getByRole('button', { name: 'Quay lại' }))
    expect(reason()).toHaveValue('Direct reason typed')
    const values = Array.from({ length: sessionStorage.length }, (_, index) => ({ key: sessionStorage.key(index)!, value: sessionStorage.getItem(sessionStorage.key(index)!)! }))
    expect(values).toHaveLength(2)
    values.forEach(({ key, value }) => { expect(key.startsWith(MODERATOR_DRAFT_PREFIX)).toBe(true); expect(Object.keys(JSON.parse(value)).sort()).toEqual(['savedAt', 'text']) })
    expect(values.map(({ value }) => JSON.parse(value).text).sort()).toEqual(['Case note typed', 'Direct reason typed'])
    for (const forbidden of [visible.content, reportCase.target.content, reporterName, moderationPost.title, moderator.email]) expect(values.map(({ value }) => value).join()).not.toContain(forbidden)
  })
  it('FR052_mutation failure preserves reason; cancel clears that draft alone', async () => {
    vi.mocked(moderation.moderateComment).mockRejectedValueOnce(new ApiError({ status: 422, errorCode: 'VALIDATION_FAILED', message: 'Invalid reason', requestId: 'test' }))
    render(shell('/moderation/reports/9')); await openCasePanel()
    fireEvent.change(screen.getByRole('textbox', { name: 'Lý do xử lý' }), { target: { value: 'Keep note' } })
    fireEvent.click(await screen.findByRole('button', { name: 'Ẩn' })); typeAndConfirm('Unsent reason')
    await screen.findByText('Invalid reason')
    expect(reason()).toHaveValue('Unsent reason')
    await waitFor(() => expect(screen.getByRole('button', { name: 'Đóng' })).toBeEnabled())
    fireEvent.click(screen.getByRole('button', { name: 'Đóng' }))
    expect(sessionStorage.getItem(moderatorDraftKey({ userId: 100, screenId: 'SCR-063', resourceId: 9, field: 'comment-11-reason' }))).toBeNull()
    expect(screen.getByRole('textbox', { name: 'Lý do xử lý' })).toHaveValue('Keep note')
  })
  it('FR063_direct reason restores on same-user refresh without replaying confirmation', async () => {
    const view = render(shell('/moderation/reports/9')); await openCasePanel()
    fireEvent.click(await screen.findByRole('button', { name: 'Ẩn' }))
    fireEvent.change(reason(), { target: { value: 'Restorable typed reason' } })
    fireEvent.click(screen.getByRole('button', { name: 'Tiếp tục' }))
    view.unmount()
    render(shell('/moderation/reports/9')); await openCasePanel()
    fireEvent.click(await screen.findByRole('button', { name: 'Ẩn' }))
    expect(reason()).toHaveValue('Restorable typed reason')
    expect(screen.queryByRole('button', { name: 'Xác nhận kiểm duyệt' })).not.toBeInTheDocument()
    expect(moderation.moderateComment).not.toHaveBeenCalled()
    expect(moderation.decideReportCase).not.toHaveBeenCalled()
  })
  it.each(['direct', 'case'] as const)('FR048_FR052_%s first case/direct confirmation shares one synchronous mutation lane', async (first) => {
    const pending = deferred<ModerationComment>()
    const casePending = deferred<ReportCaseDetail>()
    vi.mocked(moderation.moderateComment).mockReturnValueOnce(pending.promise)
    vi.mocked(moderation.decideReportCase).mockReturnValueOnce(casePending.promise)
    render(shell('/moderation/reports/9')); await openCasePanel()
    fireEvent.change(screen.getByRole('combobox', { name: 'Quyết định' }), { target: { value: 'REJECT' } })
    fireEvent.change(screen.getByRole('textbox', { name: 'Lý do xử lý' }), { target: { value: 'Independent case note' } })
    fireEvent.click(screen.getByRole('button', { name: 'Tiếp tục quyết định' }))
    fireEvent.click(await screen.findByRole('button', { name: 'Ẩn' }))
    fireEvent.change(reason(), { target: { value: 'Direct reason' } }); fireEvent.click(screen.getByRole('button', { name: 'Tiếp tục' }))
    const directConfirm = screen.getByRole('button', { name: 'Xác nhận kiểm duyệt' })
    const caseConfirm = screen.getByRole('button', { name: 'Xác nhận quyết định' })
    act(() => {
      const ordered = first === 'direct' ? [directConfirm, caseConfirm] : [caseConfirm, directConfirm]
      ordered.forEach((button) => button.dispatchEvent(new MouseEvent('click', { bubbles: true })))
    })
    await waitFor(() => expect(first === 'direct' ? moderation.moderateComment : moderation.decideReportCase).toHaveBeenCalledTimes(1))
    expect(first === 'direct' ? moderation.decideReportCase : moderation.moderateComment).not.toHaveBeenCalled()
    expect(caseConfirm).toBeDisabled()
    if (first === 'direct') await act(async () => pending.resolve(visible))
    else await act(async () => casePending.reject(new ApiError({ status: 422, errorCode: 'VALIDATION_FAILED', message: 'Keep forms', requestId: 'test' })))
    expect(await screen.findByRole('button', { name: 'Xác nhận quyết định' })).toBeEnabled()
    expect(screen.getByRole('textbox', { name: 'Lý do xử lý' })).toHaveValue('Independent case note')
  })
  it.each(['post', 'case'] as const)('FR040_FR047_server 404 %s renders NotFound and no dependent moderation query', async (kind) => {
    const error = new ApiError({ status: 404, errorCode: kind === 'post' ? 'POST_NOT_FOUND' : 'REPORT_CASE_NOT_FOUND', message: 'Missing resource', requestId: 'test' })
    if (kind === 'post') vi.mocked(moderation.getModerationPost).mockRejectedValueOnce(error)
    else vi.mocked(moderation.getReportCase).mockRejectedValueOnce(error)
    render(shell(kind === 'post' ? '/moderation/posts/42' : '/moderation/reports/9'))
    expect(await screen.findByRole('heading', { name: kind === 'post' ? 'Không tìm thấy bài viết' : 'Không tìm thấy hồ sơ báo cáo' })).toBeVisible()
    expect(moderation.listPostCommentsForModeration).not.toHaveBeenCalled()
    expect(screen.queryByRole('button', { name: 'Kiểm duyệt bình luận' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Duyệt' })).not.toBeInTheDocument()
  })
  it.each(['pending', 'ready'] as const)('FR001_role downgrade while metadata %s removes reporter/comment DOM and ignores late response', async (phase) => {
    const pending = deferred<ModerationComment[]>()
    if (phase === 'pending') vi.mocked(moderation.listPostCommentsForModeration).mockReturnValueOnce(pending.promise)
    const view = render(shell('/moderation/reports/9')); await openCasePanel()
    if (phase === 'ready') await screen.findByText(visible.content)
    view.rerender(shell('/moderation/reports/9', { ...moderator, roles: ['ADMIN'] }))
    expect(screen.queryByText(reporterName)).not.toBeInTheDocument()
    expect(screen.queryByText(visible.content)).not.toBeInTheDocument()
    await act(async () => pending.resolve([visible]))
    expect(screen.queryByText(visible.content)).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Kiểm duyệt bình luận' })).not.toBeInTheDocument()
  })
  it('FR001_late direct mutation after logout never toasts/refetches or clears a later draft', async () => {
    const pending = deferred<ModerationComment>()
    vi.mocked(moderation.moderateComment).mockReturnValueOnce(pending.promise)
    const view = render(shell('/moderation/reports/9')); await openCasePanel()
    fireEvent.click(await screen.findByRole('button', { name: 'Ẩn' })); typeAndConfirm()
    await waitFor(() => expect(moderation.moderateComment).toHaveBeenCalledTimes(1))
    view.rerender(shell('/moderation/reports/9', null))
    await act(async () => pending.resolve(visible))
    expect(moderation.getReportCase).toHaveBeenCalledTimes(1)
    expect(screen.queryByText('Đã kiểm duyệt bình luận')).not.toBeInTheDocument()
  })
  it('FR005_AT48_privileged comment and reason are text nodes with pre-wrap, never HTML', async () => {
    vi.mocked(moderation.listPostCommentsForModeration).mockResolvedValue([{ ...moderationComment, content: '<img src=x onerror=alert(1)>\nline', moderationReason: '<script>alert(1)</script>' }])
    const view = render(shell()); await openPostComments()
    expect(await screen.findByText('<img src=x onerror=alert(1)> line')).toHaveClass('whitespace-pre-wrap')
    expect(view.container.querySelector('img,script')).toBeNull()
  })
})
