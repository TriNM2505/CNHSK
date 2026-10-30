import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes, useLocation, useNavigate } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import type { CurrentUser } from '@/features/auth/types'
import { AuthContext, type AuthContextValue } from '@/shared/auth'
import { ApiError } from '@/shared/api'

import { listMyPosts, listPublishedPosts } from '../api/posts'
import Scr039CommunityPostListPage from './Scr039CommunityPostListPage'
import { ownPage, ownPageWithStatuses, publishedPage } from '../test-fixtures'

vi.mock('../api/posts', () => ({ listMyPosts: vi.fn(), listPublishedPosts: vi.fn() }))

const guestUser: CurrentUser = { id: 1, displayName: 'Guest test', email: 'guest@test.invalid', emailVerified: true, roles: [] }
const learnerUser: CurrentUser = { ...guestUser, roles: ['LEARNER'] }

function renderList(path: string, user: CurrentUser | null = null, statusOverride?: AuthContextValue['status']) {
  const auth: AuthContextValue = {
    error: null,
    reloadCurrentUser: async () => undefined,
    setAuthenticatedUser: () => undefined,
    signOut: async () => undefined,
    status: statusOverride ?? (user ? 'authenticated' : 'guest'),
    user,
  }
  function CurrentLocation() {
    const location = useLocation()
    const navigate = useNavigate()
    return <><output aria-label="current location">{location.pathname}{location.search}</output><button onClick={() => navigate(-1)} type="button">Back</button></>
  }
  return render(<AuthContext.Provider value={auth}><MemoryRouter initialEntries={[path]}><Routes><Route element={<><Scr039CommunityPostListPage /><CurrentLocation /></>} path="/community" /><Route element={<CurrentLocation />} path="/login" /></Routes></MemoryRouter></AuthContext.Provider>)
}

describe('SCR-039 community post list (FR-057, FR-061, AT-49)', () => {
  beforeEach(() => {
    vi.mocked(listPublishedPosts).mockReset().mockResolvedValue(publishedPage)
    vi.mocked(listMyPosts).mockReset().mockResolvedValue(ownPage)
  })

  it('FR057_guest sees public feed, no mine tab, and never calls the private wrapper', async () => {
    renderList('/community', null)
    expect(await screen.findByText(publishedPage.items[0]!.title)).toBeVisible()
    expect(screen.getByText('Người dùng đã rời')).toBeVisible()
    expect(screen.queryByRole('tab', { name: 'Bài viết của tôi' })).not.toBeInTheDocument()
    expect(listPublishedPosts).toHaveBeenCalledWith({ category: undefined, page: 0, size: 20 }, expect.any(AbortSignal))
    expect(listMyPosts).not.toHaveBeenCalled()
  })

  it('FR006_public card displays the available author displayName as text', async () => {
    vi.mocked(listPublishedPosts).mockResolvedValue({
      ...publishedPage,
      items: [{ ...publishedPage.items[0]!, author: { ...publishedPage.items[0]!.author, displayName: 'Lan Anh' } }],
    })
    renderList('/community', learnerUser)
    expect(await screen.findByText('Lan Anh')).toBeVisible()
    expect(screen.queryByText('Người dùng đã rời')).not.toBeInTheDocument()
  })

  it('FR057_guest writing navigates to login without calling mine feed', async () => {
    const user = userEvent.setup()
    renderList('/community', null)
    await user.click(await screen.findByRole('button', { name: 'Viết bài' }))
    expect(screen.getByLabelText('current location')).toHaveTextContent('/login')
    expect(listMyPosts).not.toHaveBeenCalled()
  })

  it('FR057_learner writing opens the registered create route', async () => {
    const user = userEvent.setup()
    function CreateRoute() { return <h1>SCR-041 create route</h1> }
    const auth: AuthContextValue = { error: null, reloadCurrentUser: async () => undefined, setAuthenticatedUser: () => undefined, signOut: async () => undefined, status: 'authenticated', user: learnerUser }
    render(<AuthContext.Provider value={auth}><MemoryRouter initialEntries={['/community']}><Routes>
      <Route element={<Scr039CommunityPostListPage />} path="/community" />
      <Route element={<CreateRoute />} path="/community/posts/new" />
    </Routes></MemoryRouter></AuthContext.Provider>)
    await user.click(await screen.findByRole('button', { name: 'Viết bài' }))
    expect(await screen.findByRole('heading', { name: 'SCR-041 create route' })).toBeVisible()
    expect(listMyPosts).not.toHaveBeenCalled()
  })

  it('FR057_learner URL selects mine and calls only listMyPosts with status/page/size', async () => {
    renderList('/community?tab=mine', learnerUser)
    expect(await screen.findByText(ownPage.items[0]!.title)).toBeVisible()
    expect(screen.getByRole('tab', { name: 'Bài viết của tôi' })).toHaveAttribute('aria-selected', 'true')
    expect(listMyPosts).toHaveBeenCalledWith({ status: undefined, page: 0, size: 20 }, expect.any(AbortSignal))
    expect(listPublishedPosts).not.toHaveBeenCalled()
  })

  it('FR057_tab selection writes the URL and mine exposes status filter only', async () => {
    const user = userEvent.setup()
    renderList('/community', learnerUser)
    await screen.findByText(publishedPage.items[0]!.title)
    const publicTab = screen.getByRole('tab', { name: 'Bài công khai' })
    const mineTab = screen.getByRole('tab', { name: 'Bài viết của tôi' })
    expect(publicTab).toHaveAttribute('aria-selected', 'true')
    expect(publicTab).toHaveClass('border-brand-600')
    expect(mineTab).toHaveAttribute('aria-selected', 'false')
    expect(mineTab).toHaveClass('border-transparent')
    await user.click(screen.getByRole('tab', { name: 'Bài viết của tôi' }))
    expect(await screen.findByLabelText('current location')).toHaveTextContent('/community?tab=mine')
    expect(screen.getByRole('tab', { name: 'Bài viết của tôi' })).toHaveAttribute('aria-selected', 'true')
    expect(screen.getByRole('tab', { name: 'Bài viết của tôi' })).toHaveClass('border-brand-600')
    expect(screen.getByRole('tab', { name: 'Bài công khai' })).toHaveAttribute('aria-selected', 'false')
    expect(screen.getByRole('tab', { name: 'Bài công khai' })).toHaveClass('border-transparent')
    expect(screen.getByLabelText('Trạng thái')).toBeVisible()
    expect(screen.queryByLabelText('Danh mục')).not.toBeInTheDocument()
    await user.selectOptions(screen.getByLabelText('Trạng thái'), 'REJECTED')
    await waitFor(() => expect(listMyPosts).toHaveBeenLastCalledWith({ status: 'REJECTED', page: 0, size: 20 }, expect.any(AbortSignal)))
    expect(listPublishedPosts).toHaveBeenCalledTimes(1)
  })

  it('FR012_empty non-first page can return to page zero without showing stale cards', async () => {
    const user = userEvent.setup()
    vi.mocked(listPublishedPosts).mockImplementation(async ({ page = 0 } = {}) => page === 0
      ? { ...publishedPage, totalElements: 45 }
      : { items: [], page, size: 20, totalElements: 45 })
    renderList('/community', learnerUser)
    expect(await screen.findByText(publishedPage.items[0]!.title)).toBeVisible()
    await user.click(screen.getByRole('button', { name: 'Sau' }))
    expect(await screen.findByText('Chưa có bài công khai')).toBeVisible()
    expect(screen.queryByText(publishedPage.items[0]!.title)).not.toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Về trang đầu' }))
    expect(await screen.findByText(publishedPage.items[0]!.title)).toBeVisible()
    expect(listPublishedPosts).toHaveBeenLastCalledWith({ category: undefined, page: 0, size: 20 }, expect.any(AbortSignal))
  })

  it('FR012_empty first page keeps the empty state without unnecessary pagination action', async () => {
    vi.mocked(listPublishedPosts).mockResolvedValue({ items: [], page: 0, size: 20, totalElements: 0 })
    renderList('/community', learnerUser)
    expect(await screen.findByText('Chưa có bài công khai')).toBeVisible()
    expect(screen.queryByRole('button', { name: 'Về trang đầu' })).not.toBeInTheDocument()
  })

  it('FR061_public category selector contains exactly the five approved categories', async () => {
    renderList('/community', learnerUser)
    await screen.findByText(publishedPage.items[0]!.title)
    expect(Array.from(screen.getByLabelText('Danh mục').querySelectorAll('option')).map((option) => option.value)).toEqual([
      '', 'EXPERIENCE', 'QUESTION', 'STUDY_BUDDY', 'CULTURE', 'JOB',
    ])
  })

  it('FR057_mine shows status labels and reasons only for rejected/hidden posts with real text', async () => {
    vi.mocked(listMyPosts).mockResolvedValue(ownPageWithStatuses)
    renderList('/community?tab=mine', learnerUser)
    const rejectedReason = await screen.findByText('Vui lòng bổ sung nguồn trích dẫn.')
    const rejectedCard = rejectedReason.closest('article')
    expect(rejectedCard).not.toBeNull()
    expect(within(rejectedCard as HTMLElement).getByText('Bị từ chối')).toBeVisible()
    const hiddenReason = screen.getByText('Bài viết vi phạm quy định cộng đồng.')
    const hiddenCard = hiddenReason.closest('article')
    expect(hiddenCard).not.toBeNull()
    expect(within(hiddenCard as HTMLElement).getByText('Đã bị ẩn')).toBeVisible()
    expect(within(hiddenCard as HTMLElement).getByText('Bài viết vi phạm quy định cộng đồng.')).toBeVisible()
    expect(within(hiddenCard as HTMLElement).queryByRole('link', { name: 'Sửa bài' })).not.toBeInTheDocument()
  })

  it('FR057_null reviewReason on REJECTED/HIDDEN creates no fabricated message', async () => {
    vi.mocked(listMyPosts).mockResolvedValue({
      items: [
        { ...ownPage.items[0]!, id: 43, status: 'REJECTED', reviewReason: null },
        { ...ownPage.items[0]!, id: 44, status: 'HIDDEN', reviewReason: undefined },
      ], page: 0, size: 20, totalElements: 2,
    })
    renderList('/community?tab=mine', learnerUser)
    await waitFor(() => expect(screen.getAllByRole('article')).toHaveLength(2))
    const cards = screen.getAllByRole('article')
    const rejectedCard = cards.find((card) => within(card).queryByText('Bị từ chối'))
    const hiddenCard = cards.find((card) => within(card).queryByText('Đã bị ẩn'))
    expect(rejectedCard).toBeDefined()
    expect(hiddenCard).toBeDefined()
    expect(within(rejectedCard as HTMLElement).queryByText('Vui lòng bổ sung nguồn trích dẫn.')).not.toBeInTheDocument()
    expect(within(hiddenCard as HTMLElement).queryByText('Bài viết vi phạm quy định cộng đồng.')).not.toBeInTheDocument()
    expect(within(hiddenCard as HTMLElement).getByText('Đã bị ẩn')).toBeVisible()
  })

  it('FR057_old public response cannot overwrite a newer category response', async () => {
    const user = userEvent.setup()
    let resolveOld!: (page: typeof publishedPage) => void
    vi.mocked(listPublishedPosts).mockImplementation(({ category } = {}) => category
      ? Promise.resolve({ ...publishedPage, items: [{ ...publishedPage.items[0]!, title: 'Bài danh mục mới' }] })
      : new Promise((resolve) => { resolveOld = resolve }))
    renderList('/community', learnerUser)
    await user.selectOptions(screen.getByLabelText('Danh mục'), 'CULTURE')
    expect(await screen.findByText('Bài danh mục mới')).toBeVisible()
    resolveOld({ ...publishedPage, items: [{ ...publishedPage.items[0]!, title: 'Response cũ' }] })
    expect(screen.queryByText('Response cũ')).not.toBeInTheDocument()
    expect(screen.getByText('Bài danh mục mới')).toBeVisible()
  })

  it('FR057_public page converts the UI next-page action into zero-based API page', async () => {
    const user = userEvent.setup()
    vi.mocked(listPublishedPosts).mockImplementation(async ({ page = 0 } = {}) => ({ ...publishedPage, page, totalElements: 45 }))
    renderList('/community')
    expect(await screen.findByText('Trang 1 / 3')).toBeVisible()
    await user.click(screen.getByRole('button', { name: 'Sau' }))
    expect(await screen.findByText('Trang 2 / 3')).toBeVisible()
    expect(listPublishedPosts).toHaveBeenLastCalledWith({ category: undefined, page: 1, size: 20 }, expect.any(AbortSignal))
  })

  it('FR005_public post title and excerpt remain text when they resemble HTML', async () => {
    vi.mocked(listPublishedPosts).mockResolvedValue({ ...publishedPage, items: [{ ...publishedPage.items[0]!, title: '<img src=x onerror=alert(1)>', excerpt: '<script>unsafe</script>' }] })
    renderList('/community')
    expect(await screen.findByText('<img src=x onerror=alert(1)>')).toBeVisible()
    expect(screen.getByText('<script>unsafe</script>')).toBeVisible()
    expect(screen.queryByRole('img')).not.toBeInTheDocument()
  })

  it('FR057_invalid mine URL for non-learner normalizes to public without private request', async () => {
    renderList('/community?tab=mine', guestUser)
    expect(await screen.findByText(publishedPage.items[0]!.title)).toBeVisible()
    await waitFor(() => expect(screen.getByLabelText('current location')).toHaveTextContent('/community'))
    expect(listMyPosts).not.toHaveBeenCalled()
  })

  it('FR057_mine URL waits for auth resolution without calling either feed prematurely', async () => {
    renderList('/community?tab=mine', null, 'loading')
    expect(screen.getByRole('status', { name: 'Đang kiểm tra phiên đăng nhập' })).toBeVisible()
    expect(screen.getByLabelText('current location')).toHaveTextContent('/community?tab=mine')
    expect(listMyPosts).not.toHaveBeenCalled()
    expect(listPublishedPosts).not.toHaveBeenCalled()
  })

  it('FR057_browser back synchronizes the URL tab and unmounts the private query', async () => {
    const user = userEvent.setup()
    renderList('/community', learnerUser)
    await screen.findByText(publishedPage.items[0]!.title)
    await user.click(screen.getByRole('tab', { name: 'Bài viết của tôi' }))
    await screen.findByText(ownPage.items[0]!.title)
    await user.click(screen.getByRole('button', { name: 'Back' }))
    expect(await screen.findByLabelText('current location')).toHaveTextContent('/community')
    expect(await screen.findByText(publishedPage.items[0]!.title)).toBeVisible()
    expect(listMyPosts).toHaveBeenCalledTimes(1)
  })

  it('FR057_filter changes public API query and safely renders the fresh loading state', async () => {
    const user = userEvent.setup()
    vi.mocked(listPublishedPosts).mockImplementation(async ({ category } = {}) => category ? { ...publishedPage, items: [] } : publishedPage)
    renderList('/community', learnerUser)
    await screen.findByText(publishedPage.items[0]!.title)
    await user.selectOptions(screen.getByLabelText('Danh mục'), 'CULTURE')
    await waitFor(() => expect(listPublishedPosts).toHaveBeenLastCalledWith({ category: 'CULTURE', page: 0, size: 20 }, expect.any(AbortSignal)))
    expect(screen.queryByText(publishedPage.items[0]!.title)).not.toBeInTheDocument()
  })

  it('FR057_retry is available for request errors and remains distinct from empty results', async () => {
    vi.mocked(listPublishedPosts).mockRejectedValueOnce(new ApiError({ status: 503, errorCode: 'TEMPORARY', message: 'Try again', requestId: 'r1' })).mockResolvedValue(publishedPage)
    const user = userEvent.setup()
    renderList('/community')
    expect(await screen.findByRole('alert')).toHaveTextContent('Try again')
    await user.click(screen.getByRole('button', { name: 'Thử lại' }))
    expect(await screen.findByText(publishedPage.items[0]!.title)).toBeVisible()
  })
})
