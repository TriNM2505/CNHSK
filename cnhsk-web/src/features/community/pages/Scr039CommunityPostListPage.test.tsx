import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes, useLocation, useNavigate } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import type { CurrentUser } from '@/features/auth/types'
import { AuthContext, type AuthContextValue } from '@/shared/auth'
import { ApiError } from '@/shared/api'

import {
  listFollowingPosts,
  listLikedPostIds,
  listMyPosts,
  listPinnedPosts,
  listPublishedPosts,
  pinPost,
} from '../api/posts'
import type { PinnedPost, PostSummary } from '../types'
import Scr039CommunityPostListPage from './Scr039CommunityPostListPage'
import { ownPage, ownPageWithStatuses, publishedPage } from '../test-fixtures'

vi.mock('../api/posts', () => ({
  likePost: vi.fn(),
  listFollowingPosts: vi.fn(),
  listLikedPostIds: vi.fn(),
  listMyPosts: vi.fn(),
  listPinnedPosts: vi.fn(),
  listPublishedPosts: vi.fn(),
  pinPost: vi.fn(),
  unlikePost: vi.fn(),
}))
vi.mock('../api/rankings', () => ({
  getCommunityRanking: vi.fn(async () => ({ period: 'week', entries: [], myEntry: null })),
}))
vi.mock('@/features/leaderboard/api/leaderboards', () => ({
  getGameLeaderboard: vi.fn(),
  listLeaderboardGames: vi.fn(async () => []),
}))

const guestUser: CurrentUser = { id: 1, displayName: 'Guest test', email: 'guest@test.invalid', emailVerified: true, roles: [] }
const learnerUser: CurrentUser = { ...guestUser, displayName: 'Lan', roles: ['LEARNER'] }
const moderatorUser: CurrentUser = { ...guestUser, displayName: 'Kiểm duyệt', roles: ['LEARNER', 'MODERATOR'] }
const firstTitle = publishedPage.items[0]!.title
/** The side panel asks for recent questions with this exact query; the feed never does unless QUESTION is chosen. */
const HOT_QUESTIONS_QUERY = { category: 'QUESTION', page: 0, size: 20 }

const pinnedGuide: PinnedPost = {
  ...publishedPage.items[0]!,
  id: 90,
  title: 'Thể lệ cuộc thi tuần',
  category: 'CONTEST',
  author: { id: 3, displayName: 'Ban quản trị' },
  contestId: 5,
  pinnedUntil: '2099-01-01T00:00:00Z',
  commentsCloseAt: null,
}

function auth(user: CurrentUser | null, statusOverride?: AuthContextValue['status']): AuthContextValue {
  return {
    error: null,
    reloadCurrentUser: async () => undefined,
    setAuthenticatedUser: () => undefined,
    signOut: async () => undefined,
    status: statusOverride ?? (user ? 'authenticated' : 'guest'),
    user,
  }
}

function renderList(path: string, user: CurrentUser | null = null, statusOverride?: AuthContextValue['status']) {
  function CurrentLocation() {
    const location = useLocation()
    const navigate = useNavigate()
    return <><output aria-label="current location">{location.pathname}{location.search}</output><button onClick={() => navigate(-1)} type="button">Back</button></>
  }
  return render(<AuthContext.Provider value={auth(user, statusOverride)}><MemoryRouter initialEntries={[path]}><Routes><Route element={<><Scr039CommunityPostListPage /><CurrentLocation /></>} path="/community" /><Route element={<CurrentLocation />} path="/login" /></Routes></MemoryRouter></AuthContext.Provider>)
}

async function publicFeed() {
  return within(await screen.findByRole('list', { name: 'Danh sách bài công khai' }))
}

/** Calls for the main feed, leaving out the side panel's recent-questions query. */
function feedCalls() {
  return vi.mocked(listPublishedPosts).mock.calls.filter(([query]) => query?.category !== 'QUESTION')
}

describe('SCR-039 community post list (FR-057, FR-061, AT-49)', () => {
  beforeEach(() => {
    vi.mocked(listPublishedPosts).mockReset().mockResolvedValue(publishedPage)
    vi.mocked(listMyPosts).mockReset().mockResolvedValue(ownPage)
    vi.mocked(listPinnedPosts).mockReset().mockResolvedValue([])
    vi.mocked(listLikedPostIds).mockReset().mockResolvedValue([])
    vi.mocked(listFollowingPosts).mockReset().mockResolvedValue({ items: [], page: 0, size: 20, totalElements: 0 })
    vi.mocked(pinPost).mockReset().mockResolvedValue(undefined)
  })

  it('FR057_guest sees public feed, no tabs, and never calls private wrappers', async () => {
    renderList('/community', null)
    const feed = await publicFeed()
    expect(feed.getByText(firstTitle)).toBeVisible()
    expect(feed.getByText('Người dùng đã rời')).toBeVisible()
    expect(screen.queryByRole('tablist')).not.toBeInTheDocument()
    expect(listPublishedPosts).toHaveBeenCalledWith({ category: undefined, page: 0, size: 20 }, expect.any(AbortSignal))
    expect(listMyPosts).not.toHaveBeenCalled()
    expect(listFollowingPosts).not.toHaveBeenCalled()
    expect(listLikedPostIds).not.toHaveBeenCalled()
  })

  it('FR006_public card displays the available author displayName as text', async () => {
    vi.mocked(listPublishedPosts).mockResolvedValue({
      ...publishedPage,
      items: [{ ...publishedPage.items[0]!, author: { ...publishedPage.items[0]!.author, displayName: 'Lan Anh' } }],
    })
    renderList('/community', learnerUser)
    const feed = await publicFeed()
    expect(feed.getByText('Lan Anh')).toBeVisible()
    expect(feed.queryByText('Người dùng đã rời')).not.toBeInTheDocument()
  })

  it('FR057_guest writing navigates to login with a return path without calling mine feed', async () => {
    const user = userEvent.setup()
    renderList('/community', null)
    await publicFeed()
    await user.click(screen.getByRole('button', { name: 'Viết bài chia sẻ với cộng đồng' }))
    expect(screen.getByLabelText('current location')).toHaveTextContent('/login?redirect=%2Fcommunity')
    expect(listMyPosts).not.toHaveBeenCalled()
  })

  it('FR057_learner writing opens the composer dialog over the feed', async () => {
    const user = userEvent.setup()
    renderList('/community', learnerUser)
    await publicFeed()
    await user.click(screen.getByRole('button', { name: 'Lan, bạn đang thắc mắc điều gì?' }))
    expect(screen.getByRole('dialog', { name: 'Tạo bài viết' })).toBeVisible()
    expect(screen.getByLabelText('current location')).toHaveTextContent(/^\/community$/)
    expect(listMyPosts).not.toHaveBeenCalled()
  })

  it('preserves the mine pathname and search when opening an owned post editor', async () => {
    const user = userEvent.setup()
    function EditorOrigin() { const location = useLocation(); return <output aria-label="editor origin">{(location.state as { communityEditorOrigin?: string } | null)?.communityEditorOrigin}</output> }
    render(<AuthContext.Provider value={auth(learnerUser)}><MemoryRouter initialEntries={['/community?tab=mine&view=own']}><Routes>
      <Route element={<Scr039CommunityPostListPage />} path="/community" />
      <Route element={<EditorOrigin />} path="/community/posts/:id/edit" />
    </Routes></MemoryRouter></AuthContext.Provider>)
    await user.click(await screen.findByRole('link', { name: 'Sửa bài' }))
    expect(screen.getByLabelText('editor origin')).toHaveTextContent('/community?tab=mine&view=own')
  })

  it('FR057_learner URL selects mine and calls only listMyPosts for the feed', async () => {
    renderList('/community?tab=mine', learnerUser)
    const mine = within(await screen.findByLabelText('Bài viết của tôi'))
    expect(await mine.findByText(ownPage.items[0]!.title)).toBeVisible()
    expect(screen.getByRole('tab', { name: 'Bài của tôi' })).toHaveAttribute('aria-selected', 'true')
    expect(listMyPosts).toHaveBeenCalledWith({ status: undefined, page: 0, size: 20 }, expect.any(AbortSignal))
    expect(feedCalls()).toHaveLength(0)
  })

  it('FR057_tab selection writes the URL and mine exposes status filter only', async () => {
    const user = userEvent.setup()
    renderList('/community', learnerUser)
    await publicFeed()
    const publicTab = screen.getByRole('tab', { name: 'Tất cả' })
    const mineTab = screen.getByRole('tab', { name: 'Bài của tôi' })
    expect(publicTab).toHaveAttribute('aria-selected', 'true')
    expect(publicTab).toHaveClass('bg-brand-600')
    expect(mineTab).toHaveAttribute('aria-selected', 'false')
    expect(mineTab).not.toHaveClass('bg-brand-600')
    await user.click(mineTab)
    expect(await screen.findByLabelText('current location')).toHaveTextContent('/community?tab=mine')
    expect(screen.getByRole('tab', { name: 'Bài của tôi' })).toHaveAttribute('aria-selected', 'true')
    expect(screen.getByRole('tab', { name: 'Bài của tôi' })).toHaveClass('bg-brand-600')
    expect(screen.getByRole('tab', { name: 'Tất cả' })).toHaveAttribute('aria-selected', 'false')
    expect(screen.getByLabelText('Trạng thái')).toBeVisible()
    expect(screen.queryByRole('navigation', { name: 'Chủ đề' })).not.toBeInTheDocument()
    await user.selectOptions(screen.getByLabelText('Trạng thái'), 'REJECTED')
    await waitFor(() => expect(listMyPosts).toHaveBeenLastCalledWith({ status: 'REJECTED', page: 0, size: 20 }, expect.any(AbortSignal)))
    expect(feedCalls()).toHaveLength(1)
  })

  it('FR057_following tab lists posts by followed authors and explains the empty state', async () => {
    const user = userEvent.setup()
    renderList('/community', learnerUser)
    await publicFeed()
    await user.click(screen.getByRole('tab', { name: 'Đang theo dõi' }))
    expect(await screen.findByLabelText('current location')).toHaveTextContent('/community?tab=following')
    expect(await screen.findByText('Chưa có bài từ người bạn theo dõi')).toBeVisible()
    expect(listFollowingPosts).toHaveBeenCalledWith({ page: 0, size: 20 }, expect.any(AbortSignal))
    expect(screen.queryByRole('navigation', { name: 'Chủ đề' })).not.toBeInTheDocument()
    expect(listMyPosts).not.toHaveBeenCalled()
  })

  it('FR012_empty non-first page can return to page zero without showing stale cards', async () => {
    const user = userEvent.setup()
    vi.mocked(listPublishedPosts).mockImplementation(async ({ category, page = 0 } = {}) => category || page === 0
      ? { ...publishedPage, totalElements: 45 }
      : { items: [], page, size: 20, totalElements: 45 })
    renderList('/community', learnerUser)
    expect((await publicFeed()).getByText(firstTitle)).toBeVisible()
    await user.click(screen.getByRole('button', { name: 'Sau' }))
    expect(await screen.findByText('Chưa có bài trong chủ đề này')).toBeVisible()
    expect(screen.queryByRole('list', { name: 'Danh sách bài công khai' })).not.toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Về trang đầu' }))
    expect((await publicFeed()).getByText(firstTitle)).toBeVisible()
    expect(listPublishedPosts).toHaveBeenLastCalledWith({ category: undefined, page: 0, size: 20 }, expect.any(AbortSignal))
  })

  it('FR012_empty first page keeps the empty state without unnecessary pagination action', async () => {
    vi.mocked(listPublishedPosts).mockResolvedValue({ items: [], page: 0, size: 20, totalElements: 0 })
    renderList('/community', learnerUser)
    expect(await screen.findByText('Chưa có bài trong chủ đề này')).toBeVisible()
    expect(screen.queryByRole('button', { name: 'Về trang đầu' })).not.toBeInTheDocument()
  })

  it('FR061_topic navigation offers all topics, the five learner categories and the two moderator topics', async () => {
    renderList('/community', learnerUser)
    await publicFeed()
    const topics = screen.getByRole('navigation', { name: 'Chủ đề' })
    expect(within(topics).getAllByRole('button').map((button) => button.textContent)).toEqual([
      'Tất cả chủ đề', 'Chia sẻ kinh nghiệm', 'Hỏi bài', 'Tìm bạn học', 'Văn hoá', 'Việc làm', 'Cuộc thi', 'Thông báo',
    ])
    expect(within(topics).getByRole('button', { name: 'Tất cả chủ đề' })).toHaveAttribute('aria-pressed', 'true')
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
    const mine = await screen.findByLabelText('Bài viết của tôi')
    await waitFor(() => expect(within(mine).getAllByRole('article')).toHaveLength(2))
    const cards = within(mine).getAllByRole('article')
    const rejectedCard = cards.find((card) => within(card).queryByText('Bị từ chối'))
    const hiddenCard = cards.find((card) => within(card).queryByText('Đã bị ẩn'))
    expect(rejectedCard).toBeDefined()
    expect(hiddenCard).toBeDefined()
    expect(within(rejectedCard as HTMLElement).queryByText('Vui lòng bổ sung nguồn trích dẫn.')).not.toBeInTheDocument()
    expect(within(hiddenCard as HTMLElement).queryByText('Bài viết vi phạm quy định cộng đồng.')).not.toBeInTheDocument()
  })

  it('FR057_old public response cannot overwrite a newer category response', async () => {
    const user = userEvent.setup()
    let resolveOld!: (page: typeof publishedPage) => void
    vi.mocked(listPublishedPosts).mockImplementation((query = {}) => query.category === 'QUESTION'
      ? Promise.resolve({ items: [], page: 0, size: 20, totalElements: 0 })
      : query.category
        ? Promise.resolve({ ...publishedPage, items: [{ ...publishedPage.items[0]!, title: 'Bài danh mục mới' }] })
        : new Promise((resolve) => { resolveOld = resolve }))
    renderList('/community', learnerUser)
    await user.click(screen.getByRole('button', { name: 'Văn hoá' }))
    expect((await publicFeed()).getByText('Bài danh mục mới')).toBeVisible()
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
    const feed = await publicFeed()
    expect(feed.getByText('<img src=x onerror=alert(1)>')).toBeVisible()
    expect(feed.getByText('<script>unsafe</script>')).toBeVisible()
    expect(screen.queryByRole('img')).not.toBeInTheDocument()
  })

  it('FR057_invalid mine URL for non-learner normalizes to public without private request', async () => {
    renderList('/community?tab=mine', guestUser)
    expect((await publicFeed()).getByText(firstTitle)).toBeVisible()
    await waitFor(() => expect(screen.getByLabelText('current location')).toHaveTextContent(/^\/community$/))
    expect(listMyPosts).not.toHaveBeenCalled()
  })

  it('FR057_mine URL waits for auth resolution without calling either feed prematurely', async () => {
    renderList('/community?tab=mine', null, 'loading')
    expect(screen.getByRole('status', { name: 'Đang kiểm tra phiên đăng nhập' })).toBeVisible()
    expect(screen.getByLabelText('current location')).toHaveTextContent('/community?tab=mine')
    expect(listMyPosts).not.toHaveBeenCalled()
    expect(listFollowingPosts).not.toHaveBeenCalled()
    // Only the public side panel may load; the public feed itself waits.
    for (const [query] of vi.mocked(listPublishedPosts).mock.calls) expect(query).toEqual(HOT_QUESTIONS_QUERY)
  })

  it('FR057_browser back synchronizes the URL tab and unmounts the private query', async () => {
    const user = userEvent.setup()
    renderList('/community', learnerUser)
    await publicFeed()
    await user.click(screen.getByRole('tab', { name: 'Bài của tôi' }))
    await within(await screen.findByLabelText('Bài viết của tôi')).findByText(ownPage.items[0]!.title)
    await user.click(screen.getByRole('button', { name: 'Back' }))
    expect(await screen.findByLabelText('current location')).toHaveTextContent(/^\/community$/)
    expect((await publicFeed()).getByText(firstTitle)).toBeVisible()
    expect(listMyPosts).toHaveBeenCalledTimes(1)
  })

  it('FR057_filter changes public API query and safely renders the fresh loading state', async () => {
    const user = userEvent.setup()
    vi.mocked(listPublishedPosts).mockImplementation(async ({ category } = {}) => category === 'CULTURE' ? { ...publishedPage, items: [] } : publishedPage)
    renderList('/community', learnerUser)
    await publicFeed()
    await user.click(screen.getByRole('button', { name: 'Văn hoá' }))
    await waitFor(() => expect(listPublishedPosts).toHaveBeenLastCalledWith({ category: 'CULTURE', page: 0, size: 20 }, expect.any(AbortSignal)))
    expect(screen.getByRole('button', { name: 'Văn hoá' })).toHaveAttribute('aria-pressed', 'true')
    expect(await screen.findByText('Chưa có bài trong chủ đề này')).toBeVisible()
    expect(screen.queryByRole('list', { name: 'Danh sách bài công khai' })).not.toBeInTheDocument()
  })

  it('FR057_retry is available for request errors and remains distinct from empty results', async () => {
    vi.mocked(listPublishedPosts).mockImplementation(async (query = {}) => {
      if (query.category === 'QUESTION') return { items: [], page: 0, size: 20, totalElements: 0 }
      throw new ApiError({ status: 503, errorCode: 'TEMPORARY', message: 'Try again', requestId: 'r1' })
    })
    const user = userEvent.setup()
    renderList('/community')
    expect(await screen.findByRole('alert')).toHaveTextContent('Try again')
    vi.mocked(listPublishedPosts).mockResolvedValue(publishedPage)
    await user.click(screen.getByRole('button', { name: 'Thử lại' }))
    expect((await publicFeed()).getByText(firstTitle)).toBeVisible()
  })

  it('pins moderator posts above the unfiltered feed once and hides them under a topic', async () => {
    const user = userEvent.setup()
    vi.mocked(listPinnedPosts).mockResolvedValue([pinnedGuide])
    vi.mocked(listPublishedPosts).mockResolvedValue({ ...publishedPage, items: [...publishedPage.items, pinnedGuide] })
    renderList('/community', learnerUser)
    const pinned = within(await screen.findByRole('list', { name: 'Bài ghim' }))
    expect(pinned.getByText('Thể lệ cuộc thi tuần')).toBeVisible()
    expect(pinned.getByText('Bài ghim')).toBeVisible()
    expect(pinned.getByRole('link', { name: 'Vào phòng thi và xếp hạng' })).toHaveAttribute('href', '/community/contests/5')
    expect(pinned.queryByRole('button', { name: 'Bỏ ghim' })).not.toBeInTheDocument()
    const feed = await publicFeed()
    expect(feed.queryByText('Thể lệ cuộc thi tuần')).not.toBeInTheDocument()
    await waitFor(() => expect(listLikedPostIds).toHaveBeenCalledWith([90, 42, 90], expect.any(AbortSignal)))
    await user.click(screen.getByRole('button', { name: 'Văn hoá' }))
    await waitFor(() => expect(screen.queryByRole('list', { name: 'Bài ghim' })).not.toBeInTheDocument())
  })

  it('lets a moderator unpin a post and reloads the pinned list', async () => {
    const user = userEvent.setup()
    vi.mocked(listPinnedPosts).mockResolvedValueOnce([pinnedGuide]).mockResolvedValue([])
    renderList('/community', moderatorUser)
    const pinned = within(await screen.findByRole('list', { name: 'Bài ghim' }))
    await user.click(pinned.getByRole('button', { name: 'Bỏ ghim' }))
    expect(pinPost).toHaveBeenCalledWith(90, null)
    await waitFor(() => expect(screen.queryByRole('list', { name: 'Bài ghim' })).not.toBeInTheDocument())
    expect(listPinnedPosts).toHaveBeenCalledTimes(2)
  })

  it('lists the most discussed recent questions in the side panel', async () => {
    const question = (id: number, title: string, comments: number, likes: number): PostSummary => ({
      ...publishedPage.items[0]!, id, title, commentCount: comments, likeCount: likes,
    })
    vi.mocked(listPublishedPosts).mockImplementation(async (query = {}) => query.category === 'QUESTION'
      ? { items: [question(1, 'Ít quan tâm', 0, 1), question(2, 'Sôi nổi nhất', 9, 9), question(3, 'Vừa phải', 2, 2)], page: 0, size: 20, totalElements: 3 }
      : publishedPage)
    renderList('/community')
    const panel = within(await screen.findByRole('region', { name: 'Câu hỏi được quan tâm' }))
    expect(panel.getAllByRole('link').map((link) => link.textContent)).toEqual(['Sôi nổi nhất', 'Vừa phải', 'Ít quan tâm'])
    expect(panel.getByRole('link', { name: 'Sôi nổi nhất' })).toHaveAttribute('href', '/community/posts/2')
    expect(listPublishedPosts).toHaveBeenCalledWith(HOT_QUESTIONS_QUERY, expect.any(AbortSignal))
  })
})
