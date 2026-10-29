import { useEffect, useRef, useState } from 'react'
import { useLocation, useNavigate, useSearchParams } from 'react-router-dom'

import { useApiQuery } from '@/shared/api'
import { useCurrentUser } from '@/shared/auth'
import { ToastProvider } from '@/shared/ui/Toast'
import { useToast } from '@/shared/ui/useToast'

import { listMyPosts, listPublishedPosts } from '../api/posts'
import { CommunityPostListView } from '../components/CommunityPostListView'
import { POST_CATEGORIES } from '../labels'
import type { PostCategory, PostStatus } from '../types'

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
  category: PostCategory | ''
  isLearner: boolean
  onCategoryChange: (value: PostCategory | '') => void
  onPageChange: (page: number) => void
  onStatusChange: (value: PostStatus | '') => void
  onTabChange: (tab: 'public' | 'mine') => void
  onWrite: () => void
  page: number
  status: PostStatus | ''
}

function PublicFeed(props: FeedProps) {
  const { category, isLearner, onCategoryChange, onPageChange, onStatusChange, onTabChange, onWrite, page, status } = props
  const query = useApiQuery(
    (signal) => listPublishedPosts({ category: category || undefined, page, size: PAGE_SIZE }, signal),
    [category, page],
    { isEmpty: (result) => result.items.length === 0 },
  )
  return <CommunityPostListView categories={POST_CATEGORIES} category={category} data={query.data} error={query.error?.message} isLearner={isLearner} mode="public" onCategoryChange={onCategoryChange} onPageChange={onPageChange} onRetry={query.reload} onStatusChange={onStatusChange} onTabChange={onTabChange} onWrite={onWrite} page={page} size={PAGE_SIZE} state={query.state} status={status} />
}

function MineFeed(props: FeedProps) {
  const { category, isLearner, onCategoryChange, onPageChange, onStatusChange, onTabChange, onWrite, page, status } = props
  const query = useApiQuery(
    (signal) => listMyPosts({ status: status || undefined, page, size: PAGE_SIZE }, signal),
    [status, page],
    { isEmpty: (result) => result.items.length === 0 },
  )
  return <CommunityPostListView categories={POST_CATEGORIES} category={category} data={query.data} error={query.error?.message} isLearner={isLearner} onCategoryChange={onCategoryChange} onPageChange={onPageChange} onRetry={query.reload} onStatusChange={onStatusChange} onTabChange={onTabChange} onWrite={onWrite} page={page} size={PAGE_SIZE} state={query.state} status={status} mode="mine" />
}

function CommunityPostListContent() {
  const { error: authError, reloadCurrentUser, status: authStatus, user } = useCurrentUser()
  const isLearner = authStatus === 'authenticated' && Boolean(user?.roles.includes('LEARNER'))
  const [searchParams, setSearchParams] = useSearchParams()
  const navigate = useNavigate()
  const requestedMine = searchParams.get('tab') === 'mine'
  const mode = requestedMine && isLearner ? 'mine' : 'public'
  const [category, setCategory] = useState<PostCategory | ''>('')
  const [publicPage, setPublicPage] = useState(0)
  const [status, setStatus] = useState<PostStatus | ''>('')
  const [minePage, setMinePage] = useState(0)
  useSuccessFlash()

  useEffect(() => {
    const tab = searchParams.get('tab')
    if (authStatus !== 'guest' && authStatus !== 'authenticated') return
    if (tab && (tab !== 'mine' || !isLearner)) {
      const next = new URLSearchParams(searchParams)
      next.delete('tab')
      setSearchParams(next, { replace: true })
    }
  }, [authStatus, isLearner, searchParams, setSearchParams])

  const onWrite = () => navigate(isLearner ? '/community/posts/new' : '/login')
  const onTabChange = (tab: 'public' | 'mine') => {
    const next = new URLSearchParams(searchParams)
    if (tab === 'mine') next.set('tab', 'mine')
    else next.delete('tab')
    setSearchParams(next)
  }

  if (requestedMine && authStatus === 'loading') {
    return <main aria-label="Đang kiểm tra phiên đăng nhập" className="mx-auto max-w-content p-6" role="status">Đang kiểm tra phiên đăng nhập…</main>
  }
  if (requestedMine && authStatus === 'error') {
    return <main className="mx-auto max-w-content p-6"><p role="alert">{authError?.message ?? 'Không thể kiểm tra phiên đăng nhập.'}</p><button className="mt-3 underline" onClick={() => void reloadCurrentUser()} type="button">Thử lại</button></main>
  }

  const feedProps: FeedProps = {
    category,
    isLearner,
    onCategoryChange: (value) => { setCategory(value); setPublicPage(0) },
    onPageChange: mode === 'public' ? setPublicPage : setMinePage,
    onStatusChange: (value) => { setStatus(value); setMinePage(0) },
    onTabChange,
    onWrite,
    page: mode === 'public' ? publicPage : minePage,
    status,
  }

  return (
    <>
      {mode === 'public' ? (
        <PublicFeed key={`public:${category}:${publicPage}`} {...feedProps} />
      ) : (
        <MineFeed key={`mine:${status}:${minePage}`} {...feedProps} />
      )}
    </>
  )
}

export default function Scr039CommunityPostListPage() {
  return <ToastProvider><CommunityPostListContent /></ToastProvider>
}
