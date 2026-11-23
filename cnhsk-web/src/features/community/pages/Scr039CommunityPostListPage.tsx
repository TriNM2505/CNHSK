import { useEffect, useMemo, useRef, useState, type ReactNode } from 'react'
import { useLocation, useNavigate, useSearchParams } from 'react-router-dom'

import { useApiQuery } from '@/shared/api'
import { useCurrentUser } from '@/shared/auth'
import { Button } from '@/shared/ui/Button'
import { ToastProvider } from '@/shared/ui/Toast'
import { useToast } from '@/shared/ui/useToast'

import { listFollowingPosts, listLikedPostIds, listMyPosts, listPinnedPosts, listPublishedPosts, pinPost } from '../api/posts'
import { getCommunityRanking, type RankingPeriod } from '../api/rankings'
import { CommunityPostListView, type FeedMode } from '../components/CommunityPostListView'
import {
  CommunityRankingPanel,
  type RankingBoard,
  type RankingLevel,
} from '../components/CommunityRankingPanel'
import { getGameLeaderboard, listLeaderboardGames } from '@/features/leaderboard/api/leaderboards'
import { FEED_CATEGORIES } from '../labels'
import type { PinnedPost, PostCategory, PostStatus, PostSummary } from '../types'

const HOT_QUESTION_COUNT = 6

const PAGE_SIZE = 20
type SuccessFlash = { communityPostSuccess?: 'draftSaved' | 'submitted' }

function useSuccessFlash() {
  const location = useLocation()
  const navigate = useNavigate()
  const { showToast } = useToast()
  const consumed = useRef<string>()

  useEffect(() => {
    const flash = (location.state as SuccessFlash | null)?.communityPostSuccess
    if (!flash || consumed.current === location.key) return
    consumed.current = location.key
    showToast({
      message: flash === 'draftSaved' ? 'Đã lưu bản nháp' : 'Bài đã gửi, chờ duyệt',
      tone: 'success',
    })
    navigate(`${location.pathname}${location.search}`, { replace: true, state: null })
  }, [location, navigate, showToast])
}

type FeedProps = {
  aside: ReactNode
  onUnpin?: (id: number) => void
  pinned: readonly PinnedPost[]
  currentUserName?: string
  hotQuestions: readonly PostSummary[]
  category: PostCategory | ''
  isLearner: boolean
  onCategoryChange: (value: PostCategory | '') => void
  onPageChange: (page: number) => void
  onStatusChange: (value: PostStatus | '') => void
  onTabChange: (tab: FeedMode) => void
  onWrite: () => void
  page: number
  status: PostStatus | ''
}

/** Liked state for the cards on screen, fetched in one request once the feed has loaded. */
function useLikedIds(ids: readonly number[], enabled: boolean): ReadonlySet<number> {
  const key = ids.join(',')
  const query = useApiQuery(
    (signal) => (enabled && ids.length > 0 ? listLikedPostIds(ids.slice(0, 50), signal) : Promise.resolve([])),
    [key, enabled],
  )
  return useMemo(() => new Set(query.data ?? []), [query.data])
}

function PublicFeed(props: FeedProps) {
  const { aside, category, currentUserName, hotQuestions, isLearner, onCategoryChange, onPageChange, onStatusChange, onTabChange, onWrite, page, status } = props
  const query = useApiQuery(
    (signal) => listPublishedPosts({ category: category || undefined, page, size: PAGE_SIZE }, signal),
    [category, page],
    { isEmpty: (result) => result.items.length === 0 },
  )
  const likedIds = useLikedIds([...props.pinned, ...(query.data?.items ?? [])].map((post) => post.id), isLearner)
  return <CommunityPostListView likedIds={likedIds} aside={aside} currentUserName={currentUserName} hotQuestions={hotQuestions} onUnpin={props.onUnpin} pinned={props.pinned} categories={FEED_CATEGORIES} category={category} data={query.data} error={query.error?.message} isLearner={isLearner} mode="public" onCategoryChange={onCategoryChange} onPageChange={onPageChange} onRetry={query.reload} onStatusChange={onStatusChange} onTabChange={onTabChange} onWrite={onWrite} page={page} size={PAGE_SIZE} state={query.state} status={status} />
}

function FollowingFeed(props: FeedProps) {
  const { aside, category, currentUserName, hotQuestions, isLearner, onCategoryChange, onPageChange, onStatusChange, onTabChange, onWrite, page, status } = props
  const query = useApiQuery(
    (signal) => listFollowingPosts({ page, size: PAGE_SIZE }, signal),
    [page],
    { isEmpty: (result) => result.items.length === 0 },
  )
  const likedIds = useLikedIds((query.data?.items ?? []).map((post) => post.id), isLearner)
  return <CommunityPostListView likedIds={likedIds} aside={aside} currentUserName={currentUserName} hotQuestions={hotQuestions} categories={FEED_CATEGORIES} category={category} data={query.data} error={query.error?.message} isLearner={isLearner} mode="following" onCategoryChange={onCategoryChange} onPageChange={onPageChange} onRetry={query.reload} onStatusChange={onStatusChange} onTabChange={onTabChange} onWrite={onWrite} page={page} size={PAGE_SIZE} state={query.state} status={status} />
}

function MineFeed(props: FeedProps) {
  const { aside, category, currentUserName, hotQuestions, isLearner, onCategoryChange, onPageChange, onStatusChange, onTabChange, onWrite, page, status } = props
  const query = useApiQuery(
    (signal) => listMyPosts({ status: status || undefined, page, size: PAGE_SIZE }, signal),
    [status, page],
    { isEmpty: (result) => result.items.length === 0 },
  )
  return <CommunityPostListView aside={aside} currentUserName={currentUserName} hotQuestions={hotQuestions} categories={FEED_CATEGORIES} category={category} data={query.data} error={query.error?.message} isLearner={isLearner} onCategoryChange={onCategoryChange} onPageChange={onPageChange} onRetry={query.reload} onStatusChange={onStatusChange} onTabChange={onTabChange} onWrite={onWrite} page={page} size={PAGE_SIZE} state={query.state} status={status} mode="mine" />
}

function CommunityPostListContent() {
  const { error: authError, reloadCurrentUser, status: authStatus, user } = useCurrentUser()
  const isLearner = authStatus === 'authenticated' && Boolean(user?.roles.includes('LEARNER'))
  const [searchParams, setSearchParams] = useSearchParams()
  const navigate = useNavigate()
  const location = useLocation()
  const requestedTab = searchParams.get('tab')
  const requestedMine = requestedTab === 'mine' || requestedTab === 'following'
  const mode: FeedMode = requestedMine && isLearner ? (requestedTab as FeedMode) : 'public'
  const [followingPage, setFollowingPage] = useState(0)
  const [category, setCategory] = useState<PostCategory | ''>('')
  const [publicPage, setPublicPage] = useState(0)
  const [status, setStatus] = useState<PostStatus | ''>('')
  const [minePage, setMinePage] = useState(0)
  const [rankingPeriod, setRankingPeriod] = useState<RankingPeriod>('week')
  const [rankingGame, setRankingGame] = useState('')
  const [rankingLevel, setRankingLevel] = useState<RankingLevel>(1)
  useSuccessFlash()
  const games = useApiQuery((signal) => listLeaderboardGames(signal), [])
  const pinned = useApiQuery((signal) => listPinnedPosts(signal), [])
  const isModerator = authStatus === 'authenticated' && Boolean(user?.roles.includes('MODERATOR'))
  const onUnpin = async (id: number) => {
    try {
      await pinPost(id, null)
    } finally {
      pinned.reload()
    }
  }
  // No game chosen ranks total points; a game ranks each player's best round at one HSK level.
  const ranking = useApiQuery<RankingBoard>(
    (signal) =>
      rankingGame
        ? getGameLeaderboard(rankingGame, rankingPeriod, rankingLevel, signal)
        : getCommunityRanking(rankingPeriod, signal),
    [rankingGame, rankingLevel, rankingPeriod],
    { isEmpty: (result) => result.entries.length === 0 },
  )
  // The newest questions, most discussed first; a light stand-in until the server ranks them.
  const questions = useApiQuery(
    (signal) => listPublishedPosts({ category: 'QUESTION', page: 0, size: 20 }, signal),
    [],
  )
  const hotQuestions = useMemo(
    () =>
      [...(questions.data?.items ?? [])]
        .sort((a, b) => b.commentCount + b.likeCount - (a.commentCount + a.likeCount))
        .slice(0, HOT_QUESTION_COUNT),
    [questions.data],
  )

  useEffect(() => {
    const tab = searchParams.get('tab')
    if (authStatus !== 'guest' && authStatus !== 'authenticated') return
    if (tab && ((tab !== 'mine' && tab !== 'following') || !isLearner)) {
      const next = new URLSearchParams(searchParams)
      next.delete('tab')
      setSearchParams(next, { replace: true })
    }
  }, [authStatus, isLearner, searchParams, setSearchParams])

  const onWrite = () => navigate(isLearner ? '/community/posts/new' : '/login', isLearner ? { state: { communityEditorOrigin: `${location.pathname}${location.search}` } } : undefined)
  const onTabChange = (tab: FeedMode) => {
    const next = new URLSearchParams(searchParams)
    if (tab === 'public') next.delete('tab')
    else next.set('tab', tab)
    setSearchParams(next)
  }

  if (requestedMine && authStatus === 'loading') {
    return <main aria-label="Đang kiểm tra phiên đăng nhập" className="mx-auto max-w-content p-6" role="status">Đang kiểm tra phiên đăng nhập…</main>
  }
  if (requestedMine && authStatus === 'error') {
    return <main className="mx-auto max-w-content p-6"><p role="alert">{authError?.message ?? 'Không thể kiểm tra phiên đăng nhập.'}</p><Button className="mt-3" onClick={() => void reloadCurrentUser()} type="button" variant="outline">Thử lại</Button></main>
  }

  const feedProps: FeedProps = {
    aside: (
      <CommunityRankingPanel
        game={rankingGame}
        games={games.data ?? []}
        level={rankingLevel}
        onGameChange={setRankingGame}
        onLevelChange={setRankingLevel}
        onPeriodChange={setRankingPeriod}
        onRetry={ranking.reload}
        period={rankingPeriod}
        ranking={ranking.data}
        state={ranking.state}
      />
    ),
    currentUserName: user?.displayName,
    onUnpin: isModerator ? (id) => void onUnpin(id) : undefined,
    pinned: pinned.data ?? [],
    hotQuestions,
    category,
    isLearner,
    onCategoryChange: (value) => { setCategory(value); setPublicPage(0) },
    onPageChange: mode === 'public' ? setPublicPage : mode === 'following' ? setFollowingPage : setMinePage,
    onStatusChange: (value) => { setStatus(value); setMinePage(0) },
    onTabChange,
    onWrite,
    page: mode === 'public' ? publicPage : mode === 'following' ? followingPage : minePage,
    status,
  }

  return (
    <>
      {mode === 'public' ? (
        <PublicFeed key={`public:${category}:${publicPage}`} {...feedProps} />
      ) : mode === 'following' ? (
        <FollowingFeed key={`following:${followingPage}`} {...feedProps} />
      ) : (
        <MineFeed key={`mine:${status}:${minePage}`} {...feedProps} />
      )}
    </>
  )
}

export default function Scr039CommunityPostListPage() {
  return <ToastProvider><CommunityPostListContent /></ToastProvider>
}
