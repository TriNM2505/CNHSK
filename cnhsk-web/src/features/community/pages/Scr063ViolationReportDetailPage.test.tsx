import { act, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter, Route, Routes, useLocation, useNavigate } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { CurrentUser } from '@/features/auth/types'
import { ApiError } from '@/shared/api'
import { AuthContext, type AuthContextValue } from '@/shared/auth'
import * as api from '../api/moderation'
import { reportCase, reportPage } from '../test-fixtures'
import type { ReportCaseDetail } from '../types'
import Scr062ViolationReportQueuePage from './Scr062ViolationReportQueuePage'
import Scr063ViolationReportDetailPage from './Scr063ViolationReportDetailPage'

const { showToast, observeLocation } = vi.hoisted(() => ({ showToast: vi.fn(), observeLocation: vi.fn() }))
vi.mock('../api/moderation', () => ({ getReportCase: vi.fn(), decideReportCase: vi.fn(), listPendingReportCases: vi.fn() }))
vi.mock('@/shared/ui/useToast', () => ({ useToast: () => ({ showToast }) }))
vi.mock('../components/ReportTargetModeration', () => ({ ReportTargetModeration: ({ onChanged }: { onChanged: () => void }) => <button onClick={onChanged}>Simulate independent comment success</button> }))

const moderator: CurrentUser = { id: 100, displayName: 'Moderator', email: 'mod@test.invalid', emailVerified: true, roles: ['MODERATOR'] }
const apiError = (status: number, errorCode: string) => new ApiError({ status, errorCode, message: errorCode, requestId: 'report-request' })
function pending(): ReportCaseDetail {
  const entry = reportCase.reports[0]
  if (!entry) throw new Error('Report case fixture must include a report entry.')
  return { ...reportCase, reports: [{ ...entry, reporter: { id: 10, displayName: 'Private reporter' } }] }
}
function deferred<T>() { let resolve!: (value: T) => void; let reject!: (error: unknown) => void; const promise = new Promise<T>((done, fail) => { resolve = done; reject = fail }); return { promise, resolve, reject } }
function Navigation() {
  const navigate = useNavigate()
  return <><button onClick={() => navigate('/moderation/reports/10')}>Case B</button><button onClick={() => navigate('/moderation/reports/9')}>Case A</button></>
}
function QueueDestination() {
  const location = useLocation()
  return <><h1>Report queue destination</h1><p>{JSON.stringify(location.state)}</p></>
}
function LocationObservation() {
  const location = useLocation()
  observeLocation(location.state)
  return <><output aria-label="Current route">{location.pathname}{location.search}</output><output aria-label="Current navigation state">{JSON.stringify(location.state)}</output></>
}
function shell(user: CurrentUser | null = moderator, path = '/moderation/reports/9', status: AuthContextValue['status'] = user ? 'authenticated' : 'guest', realQueue = false) {
  const auth: AuthContextValue = { user, status, error: null, reloadCurrentUser: async () => undefined, setAuthenticatedUser: () => undefined, signOut: async () => undefined }
  return <AuthContext.Provider value={auth}><MemoryRouter initialEntries={[path]}><Navigation />{realQueue ? <LocationObservation /> : null}<Routes><Route path="/moderation/reports/:id" element={<Scr063ViolationReportDetailPage />} /><Route path="/moderation/reports" element={realQueue ? <Scr062ViolationReportQueuePage /> : <QueueDestination />} /></Routes></MemoryRouter></AuthContext.Provider>
}
const ready = () => screen.findByRole('heading', { name: 'Hồ sơ báo cáo #9' })
function fill(decision: 'ACTION' | 'REJECT', commentAction?: 'HIDE' | 'DELETE', note = '  Đánh giá\n中文  ') {
  fireEvent.change(screen.getByRole('combobox', { name: 'Quyết định' }), { target: { value: decision } })
  fireEvent.change(screen.getByRole('textbox', { name: 'Lý do xử lý' }), { target: { value: note } })
  if (commentAction) fireEvent.change(screen.getByRole('combobox', { name: 'Thao tác với bình luận' }), { target: { value: commentAction } })
  fireEvent.click(screen.getByRole('button', { name: 'Tiếp tục quyết định' }))
}
beforeEach(() => {
  vi.resetAllMocks()
  localStorage.clear()
  sessionStorage.clear()
  vi.mocked(api.getReportCase).mockResolvedValue(pending())
  vi.mocked(api.decideReportCase).mockResolvedValue({ ...pending(), status: 'ACTIONED' })
  vi.mocked(api.listPendingReportCases).mockImplementation(async ({ page = 0 } = {}) => ({
    ...reportPage,
    items: reportPage.items.map((item) => ({ ...item, targetPreview: 'Safe queue summary' })),
    page,
    totalElements: 61,
  }))
})

describe('SCR063 report evidence and authorization', () => {
  it.each([null, { ...moderator, roles: ['ADMIN'] }, { ...moderator, roles: ['LEARNER'] }] satisfies (CurrentUser | null)[])('FR001_FR047_does not fetch or render reporter evidence without explicit moderator role', async (user) => {
    render(shell(user))
    expect(await screen.findByRole('alert')).toHaveTextContent('Không có quyền kiểm duyệt.')
    expect(api.getReportCase).not.toHaveBeenCalled()
    expect(screen.queryByText(/Private reporter/)).not.toBeInTheDocument()
  })
  it.each(['0', '-1', '1.1', 'nope', '9007199254740992'])('FR047_does not fetch invalid case id %s', async (id) => {
    render(shell(moderator, `/moderation/reports/${id}`))
    expect(await screen.findByText('Không tìm thấy hồ sơ báo cáo')).toBeVisible()
    expect(api.getReportCase).not.toHaveBeenCalled()
  })
  it('FR005_FR047_renders safe evidence, nullable fields and thread link without caching identities or evidence', async () => {
    vi.mocked(api.getReportCase).mockResolvedValue({ ...pending(), target: { ...reportCase.target, content: '<img src=x onerror=alert(1)>\n中文' } })
    const view = render(shell())
    await ready()
    expect(screen.getByText('Người báo cáo: Private reporter (#10)')).toBeVisible()
    expect(screen.getByText('Không có thông tin bổ sung.')).toBeVisible()
    expect(screen.getByText('Tác giả: Người dùng đã rời')).toBeVisible()
    expect(screen.getByText('<img src=x onerror=alert(1)> 中文')).toHaveClass('whitespace-pre-wrap')
    expect(view.container.querySelector('img')).toBeNull()
    expect(screen.getByRole('link', { name: 'Xem bài viết trong kiểm duyệt' })).toHaveAttribute('href', '/moderation/posts/42')
    expect(sessionStorage.length).toBe(0)
    expect(sessionStorage.length).toBe(0)
  })
  it.each(['ACTIONED', 'REJECTED'] as const)('FR047_FR052_shows %s history and direct comment panel but no decision form', async (status) => {
    vi.mocked(api.getReportCase).mockResolvedValue({ ...pending(), status, decisionNote: 'Ghi chú đã xử lý', handledBy: { id: 9, displayName: null }, handledAt: '2026-10-30T03:00:00Z' })
    render(shell())
    await ready()
    expect(screen.getByText('Ghi chú đã xử lý')).toBeVisible()
    expect(screen.queryByRole('form', { name: 'Quyết định hồ sơ' })).not.toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: 'Kiểm duyệt bình luận' }))
    expect(screen.getByRole('button', { name: 'Simulate independent comment success' })).toBeVisible()
    expect(api.decideReportCase).not.toHaveBeenCalled()
  })
  it('FR049_blocks decisions on own target', async () => {
    vi.mocked(api.getReportCase).mockResolvedValue({ ...pending(), target: { ...reportCase.target, author: { id: moderator.id, displayName: 'My content' } } })
    render(shell())
    await ready()
    expect(screen.getByText('Không thể xử lý báo cáo về nội dung của chính mình.')).toBeVisible()
    expect(screen.queryByRole('form', { name: 'Quyết định hồ sơ' })).not.toBeInTheDocument()
  })
  it('FR001_FR047_ignores late private GET after logout and aborts its signal', async () => {
    const response = deferred<ReportCaseDetail>()
    vi.mocked(api.getReportCase).mockReturnValue(response.promise)
    const view = render(shell())
    await waitFor(() => expect(api.getReportCase).toHaveBeenCalledTimes(1))
    view.rerender(shell(null))
    expect(vi.mocked(api.getReportCase).mock.calls[0]?.[1]?.aborted).toBe(true)
    await act(async () => response.resolve(pending()))
    expect(screen.queryByText(/Private reporter/)).not.toBeInTheDocument()
  })
  it.each(['loading', 'error'] as const)('FR001_FR047_does not fetch evidence while auth is %s', (status) => {
    render(shell(moderator, '/moderation/reports/9', status))
    expect(api.getReportCase).not.toHaveBeenCalled()
    expect(screen.queryByText(/Private reporter/)).not.toBeInTheDocument()
  })
  it.each([apiError(404, 'REPORT_CASE_NOT_FOUND'), apiError(403, 'FORBIDDEN'), apiError(503, 'SERVICE_UNAVAILABLE')])('FR047_shows GET $errorCode and retries without projecting stale evidence', async (error) => {
    vi.mocked(api.getReportCase).mockRejectedValueOnce(error)
    render(shell())
    expect(await screen.findByText(error.errorCode)).toBeVisible()
    expect(screen.queryByText(/Private reporter/)).not.toBeInTheDocument()
    expect(screen.queryByRole('form', { name: 'Quyết định hồ sơ' })).not.toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: 'Thử lại hồ sơ' }))
    await ready()
    expect(api.getReportCase).toHaveBeenCalledTimes(2)
  })
})

describe('SCR063 case decisions', () => {
  it.each(['HIDE', 'DELETE'] as const)('FR048_FR050_sends trimmed explicit comment %s only after confirmation and clears draft before navigation', async (commentAction) => {
    const response = deferred<ReportCaseDetail>()
    vi.mocked(api.decideReportCase).mockReturnValue(response.promise)
    render(shell())
    await ready()
    fill('ACTION', commentAction)
    expect(api.decideReportCase).not.toHaveBeenCalled()
    const confirm = screen.getByRole('button', { name: 'Xác nhận quyết định' })
    fireEvent.click(confirm)
    fireEvent.click(confirm)
    await waitFor(() => expect(api.decideReportCase).toHaveBeenCalledTimes(1))
    expect(api.decideReportCase).toHaveBeenCalledWith(9, { decision: 'ACTION', note: 'Đánh giá\n中文', commentAction })
    await act(async () => response.resolve({ ...pending(), status: 'ACTIONED' }))
    expect(await screen.findByRole('heading', { name: 'Report queue destination' })).toBeVisible()
    expect(screen.getByText(/caseDecided/)).toHaveTextContent('100')
    expect(sessionStorage.length).toBe(0)
  })
  it('FR048_rejects a comment case without commentAction after switching away from ACTION', async () => {
    render(shell())
    await ready()
    fill('ACTION', 'DELETE')
    fireEvent.click(screen.getByRole('button', { name: 'Quay lại' }))
    fill('REJECT')
    fireEvent.click(screen.getByRole('button', { name: 'Xác nhận quyết định' }))
    await waitFor(() => expect(api.decideReportCase).toHaveBeenCalledWith(9, { decision: 'REJECT', note: 'Đánh giá\n中文' }))
  })
  it.each(['ACTION', 'REJECT'] as const)('FR048_sends %s for a post without exposing or sending commentAction', async (decision) => {
    vi.mocked(api.getReportCase).mockResolvedValue({ ...pending(), target: { kind: 'POST', id: 42, content: 'Bài viết bị báo cáo', title: 'Tiêu đề', category: 'QUESTION', status: 'APPROVED', author: { id: 7, displayName: null } } })
    render(shell())
    await ready()
    fill(decision)
    expect(screen.queryByRole('combobox', { name: 'Thao tác với bình luận' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Kiểm duyệt bình luận' })).not.toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: 'Xác nhận quyết định' }))
    await waitFor(() => expect(api.decideReportCase).toHaveBeenCalledWith(9, { decision, note: 'Đánh giá\n中文' }))
  })
  it('FR048_validates choice and trimmed note bounds, and requires an explicit comment action', async () => {
    render(shell())
    await ready()
    fireEvent.click(screen.getByRole('button', { name: 'Tiếp tục quyết định' }))
    expect(screen.getByRole('combobox', { name: 'Quyết định' })).toHaveFocus()
    fill('ACTION', undefined, '   ')
    expect(screen.getByRole('textbox', { name: 'Lý do xử lý' })).toHaveAttribute('aria-invalid', 'true')
    fill('ACTION', undefined, 'a'.repeat(1001))
    expect(screen.queryByRole('button', { name: 'Xác nhận quyết định' })).not.toBeInTheDocument()
    fill('ACTION', undefined, 'Lý do')
    expect(screen.getByText('Chọn Ẩn hoặc Xoá bình luận.')).toBeVisible()
    expect(screen.getByRole('combobox', { name: 'Thao tác với bình luận' })).toHaveFocus()
    expect(api.decideReportCase).not.toHaveBeenCalled()
  })
  it.each([apiError(403, 'SELF_MODERATION'), apiError(422, 'VALIDATION_FAILED'), apiError(503, 'SERVER_FAILURE'), apiError(0, 'NETWORK_ERROR')])('FR048_FR049_preserves a typed note after mutation failure $errorCode', async (error) => {
    vi.mocked(api.decideReportCase).mockRejectedValue(error)
    render(shell())
    await ready()
    fill('REJECT')
    fireEvent.click(screen.getByRole('button', { name: 'Xác nhận quyết định' }))
    expect(await screen.findByText(error.errorCode)).toBeVisible()
    expect(screen.getByText('Mã yêu cầu: report-request')).toBeVisible()
    expect(api.getReportCase).toHaveBeenCalledTimes(1)
    expect(sessionStorage.getItem(sessionStorage.key(0)!)).toContain('Đánh giá')
    expect(screen.queryByRole('heading', { name: 'Report queue destination' })).not.toBeInTheDocument()
  })
  it('FR050_handles REPORT_ALREADY_HANDLED by masking stale evidence and refetching before returning to queue', async () => {
    const response = deferred<ReportCaseDetail>()
    vi.mocked(api.getReportCase).mockResolvedValueOnce(pending()).mockReturnValueOnce(response.promise)
    vi.mocked(api.decideReportCase).mockRejectedValue(apiError(409, 'REPORT_ALREADY_HANDLED'))
    render(shell())
    await ready()
    fill('REJECT')
    fireEvent.click(screen.getByRole('button', { name: 'Xác nhận quyết định' }))
    await waitFor(() => expect(api.getReportCase).toHaveBeenCalledTimes(2))
    expect(screen.queryByText(/Private reporter/)).not.toBeInTheDocument()
    expect(screen.queryByRole('form', { name: 'Quyết định hồ sơ' })).not.toBeInTheDocument()
    expect(sessionStorage.length).toBe(1)
    await act(async () => response.resolve({ ...pending(), status: 'REJECTED' }))
    expect(await screen.findByRole('heading', { name: 'Report queue destination' })).toBeVisible()
    expect(screen.getByText(/caseConflict/)).toHaveTextContent('100')
    expect(sessionStorage.length).toBe(1)
    expect(api.decideReportCase).toHaveBeenCalledTimes(1)
  })
  it('FR048_associates contract field errors and retains confirmation without resubmitting automatically', async () => {
    vi.mocked(api.decideReportCase).mockRejectedValue(new ApiError({ status: 422, errorCode: 'VALIDATION_FAILED', message: 'Dữ liệu không hợp lệ', details: [{ field: 'note', message: 'Ghi chú từ máy chủ' }, { field: 'commentAction', message: 'Thao tác từ máy chủ' }] }))
    render(shell())
    await ready()
    fill('ACTION', 'HIDE')
    fireEvent.click(screen.getByRole('button', { name: 'Xác nhận quyết định' }))
    expect(await screen.findByText('Ghi chú từ máy chủ')).toBeVisible()
    expect(screen.getByRole('textbox', { name: 'Lý do xử lý' })).toHaveAccessibleDescription(/Ghi chú từ máy chủ/)
    expect(screen.getByRole('combobox', { name: 'Thao tác với bình luận' })).toHaveAccessibleDescription('Thao tác từ máy chủ')
    expect(screen.getByRole('button', { name: 'Xác nhận quyết định' })).toBeVisible()
    expect(api.decideReportCase).toHaveBeenCalledTimes(1)
  })
  it('FR062_clears the manual draft on explicit cancel without a write', async () => {
    render(shell())
    await ready()
    fill('REJECT')
    fireEvent.click(screen.getByRole('button', { name: 'Huỷ và về hàng đợi' }))
    expect(await screen.findByRole('heading', { name: 'Report queue destination' })).toBeVisible()
    expect(sessionStorage.length).toBe(0)
    expect(api.decideReportCase).not.toHaveBeenCalled()
  })
})

describe('SCR063 report queue return navigation', () => {
  async function openFromSecondQueuePage() {
    render(shell(moderator, '/moderation/reports?page=2', 'authenticated', true))
    const link = await screen.findByRole('link', { name: 'Hồ sơ #9' })
    expect(link).toHaveAttribute('href', '/moderation/reports/9?page=2')
    expect(api.listPendingReportCases).toHaveBeenCalledWith({ page: 2, size: 20 }, expect.any(AbortSignal))
    fireEvent.click(link)
    await ready()
    expect(screen.getByLabelText('Current route')).toHaveTextContent('/moderation/reports/9?page=2')
  }

  async function expectFreshSecondQueuePage() {
    expect(await screen.findByText('Safe queue summary')).toBeVisible()
    expect(screen.getByLabelText('Current route').textContent).toBe('/moderation/reports?page=2')
    expect(api.listPendingReportCases).toHaveBeenCalledTimes(2)
    expect(api.listPendingReportCases).toHaveBeenLastCalledWith({ page: 2, size: 20 }, expect.any(AbortSignal))
    expect(screen.queryByText(/Private reporter/)).not.toBeInTheDocument()
  }

  function expectSafeFlash(code: 'caseDecided' | 'caseConflict', message: string, tone: 'success' | 'error') {
    const states = observeLocation.mock.calls.map(([state]) => state).filter((state) => state !== null)
    expect(states.length).toBeGreaterThan(0)
    for (const state of states) expect(state).toEqual({ moderationFlash: { code, userId: moderator.id } })
    expect(JSON.stringify(states)).not.toMatch(/Private reporter|Safe queue summary|Nội dung ẩn|Đánh giá|中文/)
    expect(showToast).toHaveBeenCalledTimes(1)
    expect(showToast).toHaveBeenCalledWith({ message, tone })
    expect(screen.getByLabelText('Current navigation state')).toHaveTextContent('null')
  }

  it('FR046_FR047_returns through the real queue link to page 2 and requests a fresh list', async () => {
    await openFromSecondQueuePage()
    expect(screen.getByRole('link', { name: 'Về hàng đợi báo cáo' })).toHaveAttribute('href', '/moderation/reports?page=2')
    fireEvent.click(screen.getByRole('link', { name: 'Về hàng đợi báo cáo' }))
    await expectFreshSecondQueuePage()
    expect(api.decideReportCase).not.toHaveBeenCalled()
    expect(showToast).not.toHaveBeenCalled()
  })

  it('FR046_FR062_cancels to queue page 2 with a fresh list and clears only the manual draft', async () => {
    await openFromSecondQueuePage()
    fill('REJECT')
    fireEvent.click(screen.getByRole('button', { name: 'Huỷ và về hàng đợi' }))
    await expectFreshSecondQueuePage()
    expect(sessionStorage.length).toBe(0)
    expect(api.decideReportCase).not.toHaveBeenCalled()
    expect(showToast).not.toHaveBeenCalled()
  })

  it.each(['ACTION', 'REJECT'] as const)('FR046_FR048_returns a successful %s to queue page 2 with a fresh list and safe flash', async (decision) => {
    await openFromSecondQueuePage()
    fill(decision, decision === 'ACTION' ? 'HIDE' : undefined)
    fireEvent.click(screen.getByRole('button', { name: 'Xác nhận quyết định' }))
    await expectFreshSecondQueuePage()
    expect(api.decideReportCase).toHaveBeenCalledTimes(1)
    expect(sessionStorage.length).toBe(0)
    expectSafeFlash('caseDecided', 'Đã xử lý hồ sơ báo cáo', 'success')
  })

  it('FR046_FR050_refetches a 409 case before returning to queue page 2 with one conflict flash and no replay', async () => {
    const response = deferred<ReportCaseDetail>()
    vi.mocked(api.getReportCase).mockResolvedValueOnce(pending()).mockReturnValueOnce(response.promise)
    vi.mocked(api.decideReportCase).mockRejectedValue(apiError(409, 'REPORT_ALREADY_HANDLED'))
    await openFromSecondQueuePage()
    fill('REJECT')
    fireEvent.click(screen.getByRole('button', { name: 'Xác nhận quyết định' }))
    await waitFor(() => expect(api.getReportCase).toHaveBeenCalledTimes(2))
    expect(screen.getByLabelText('Current route').textContent).toBe('/moderation/reports/9?page=2')
    expect(api.listPendingReportCases).toHaveBeenCalledTimes(1)
    expect(showToast).not.toHaveBeenCalled()
    expect(screen.queryByText(/Private reporter/)).not.toBeInTheDocument()
    await act(async () => response.resolve({ ...pending(), status: 'REJECTED' }))
    await expectFreshSecondQueuePage()
    expectSafeFlash('caseConflict', 'Hồ sơ đã được người khác xử lý', 'error')
    expect(api.decideReportCase).toHaveBeenCalledTimes(1)
    expect(sessionStorage.length).toBe(1)
  })

  it.each(['-1', '9007199254740992', 'https://outside.invalid/reports', '//outside.invalid/reports'])(
    'FR046_FR047_uses the internal first queue page for invalid page %s',
    async (page) => {
      render(shell(moderator, `/moderation/reports/9?page=${encodeURIComponent(page)}&returnTo=https%3A%2F%2Foutside.invalid`, 'authenticated', true))
      await ready()
      expect(screen.getByRole('link', { name: 'Về hàng đợi báo cáo' })).toHaveAttribute('href', '/moderation/reports')
      fireEvent.click(screen.getByRole('link', { name: 'Về hàng đợi báo cáo' }))
      expect(await screen.findByText('Safe queue summary')).toBeVisible()
      expect(screen.getByLabelText('Current route').textContent).toBe('/moderation/reports')
      expect(api.listPendingReportCases).toHaveBeenCalledTimes(1)
      expect(api.listPendingReportCases).toHaveBeenCalledWith({ page: 0, size: 20 }, expect.any(AbortSignal))
      expect(api.decideReportCase).not.toHaveBeenCalled()
      expect(showToast).not.toHaveBeenCalled()
    },
  )

  it('FR046_FR047_defaults a direct detail entry without page to the first queue page', async () => {
    render(shell(moderator, '/moderation/reports/9', 'authenticated', true))
    await ready()
    expect(screen.getByRole('link', { name: 'Về hàng đợi báo cáo' })).toHaveAttribute('href', '/moderation/reports')
    fireEvent.click(screen.getByRole('link', { name: 'Về hàng đợi báo cáo' }))
    expect(await screen.findByText('Safe queue summary')).toBeVisible()
    expect(screen.getByLabelText('Current route').textContent).toBe('/moderation/reports')
    expect(api.listPendingReportCases).toHaveBeenCalledTimes(1)
    expect(api.listPendingReportCases).toHaveBeenCalledWith({ page: 0, size: 20 }, expect.any(AbortSignal))
  })
})

describe('SCR063 independent panel and request lifetime', () => {
  it('FR052_FR063_keeps decision note and confirmation through independent refresh pending/error/retry while hiding projections', async () => {
    render(shell())
    await ready()
    fill('ACTION', 'DELETE', 'Case decision draft')
    expect(screen.queryByRole('button', { name: 'Simulate independent comment success' })).not.toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: 'Kiểm duyệt bình luận' }))
    const response = deferred<ReportCaseDetail>()
    vi.mocked(api.getReportCase).mockReturnValueOnce(response.promise)
    fireEvent.click(screen.getByRole('button', { name: 'Simulate independent comment success' }))
    await waitFor(() => expect(api.getReportCase).toHaveBeenCalledTimes(2))
    expect(screen.queryByText(/Private reporter/)).not.toBeInTheDocument()
    expect(screen.queryByRole('textbox', { name: 'Lý do xử lý' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Simulate independent comment success' })).not.toBeInTheDocument()
    await act(async () => response.reject(apiError(503, 'REFRESH_FAILED')))
    expect(await screen.findByText('REFRESH_FAILED')).toBeVisible()
    expect(screen.queryByText(/Private reporter/)).not.toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: 'Thử lại hồ sơ' }))
    expect(await screen.findByRole('textbox', { name: 'Lý do xử lý' })).toHaveValue('Case decision draft')
    expect(screen.getByRole('button', { name: 'Xác nhận quyết định' })).toBeVisible()
    expect(screen.getByRole('combobox', { name: 'Thao tác với bình luận' })).toHaveValue('DELETE')
    expect(api.decideReportCase).not.toHaveBeenCalled()
  })
  it('FR047_ignores an old case GET after A to B navigation', async () => {
    const response = deferred<ReportCaseDetail>()
    vi.mocked(api.getReportCase).mockReturnValueOnce(response.promise).mockResolvedValueOnce({ ...pending(), id: 10, reports: [] })
    render(shell())
    await waitFor(() => expect(api.getReportCase).toHaveBeenCalledTimes(1))
    fireEvent.click(screen.getByRole('button', { name: 'Case B' }))
    expect(await screen.findByRole('heading', { name: 'Hồ sơ báo cáo #10' })).toBeVisible()
    await act(async () => response.resolve(pending()))
    expect(screen.queryByText(/Private reporter/)).not.toBeInTheDocument()
    expect(screen.queryByRole('heading', { name: 'Hồ sơ báo cáo #9' })).not.toBeInTheDocument()
  })
  it('FR001_FR062_ignores a late mutation result after logout without clearing storage or navigating', async () => {
    const response = deferred<ReportCaseDetail>()
    vi.mocked(api.decideReportCase).mockReturnValue(response.promise)
    const view = render(shell())
    await ready()
    fill('REJECT')
    fireEvent.click(screen.getByRole('button', { name: 'Xác nhận quyết định' }))
    view.rerender(shell(null))
    await act(async () => response.resolve({ ...pending(), status: 'REJECTED' }))
    expect(screen.queryByText(/Private reporter/)).not.toBeInTheDocument()
    expect(screen.queryByRole('heading', { name: 'Report queue destination' })).not.toBeInTheDocument()
    expect(sessionStorage.length).toBe(1)
  })
  it('FR062_does not let an A-B-A late mutation clear a newer A draft or navigate its session', async () => {
    const response = deferred<ReportCaseDetail>()
    vi.mocked(api.decideReportCase).mockReturnValue(response.promise)
    vi.mocked(api.getReportCase).mockImplementation(async (id) => ({ ...pending(), id }))
    render(shell())
    await ready()
    fill('REJECT', undefined, 'Old A note')
    fireEvent.click(screen.getByRole('button', { name: 'Xác nhận quyết định' }))
    await waitFor(() => expect(api.decideReportCase).toHaveBeenCalledTimes(1))
    fireEvent.click(screen.getByRole('button', { name: 'Case B' }))
    await screen.findByRole('heading', { name: 'Hồ sơ báo cáo #10' })
    fireEvent.click(screen.getByRole('button', { name: 'Case A' }))
    await ready()
    fireEvent.change(screen.getByRole('textbox', { name: 'Lý do xử lý' }), { target: { value: 'New A note' } })
    await act(async () => response.resolve({ ...pending(), status: 'REJECTED' }))
    expect(screen.getByRole('textbox', { name: 'Lý do xử lý' })).toHaveValue('New A note')
    expect(sessionStorage.getItem(sessionStorage.key(0)!)).toContain('New A note')
    expect(screen.queryByRole('heading', { name: 'Report queue destination' })).not.toBeInTheDocument()
  })
})
