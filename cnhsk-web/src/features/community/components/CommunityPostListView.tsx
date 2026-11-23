import {
  Briefcase,
  CircleHelp,
  Heart,
  Landmark,
  LayoutList,
  Lightbulb,
  Megaphone,
  MessageCircle,
  Pin,
  Trophy,
  UsersRound,
  type LucideIcon,
} from 'lucide-react'
import type { ReactNode } from 'react'
import { Link, useLocation } from 'react-router-dom'

import { cn } from '@/shared/lib/cn'
import {
  Button,
  Card,
  EmptyState,
  ErrorState,
  FormField,
  PageState,
  Pagination,
  Select,
  Skeleton,
  StatusBadge,
  type StatusTone,
} from '@/shared/ui'

import { POST_CATEGORY_LABELS, POST_STATUS_LABELS } from '../labels'
import type { OwnPost, OwnPostPage, PinnedPost, PostCategory, PostStatus, PostSummary, PostSummaryPage } from '../types'

export type ListState = 'loading' | 'empty' | 'error' | 'ready'

/** Everyone's posts, posts by people the learner follows, or the learner's own posts. */
export type FeedMode = 'public' | 'following' | 'mine'

type Props = {
  /** Right column, normally the game-point ranking. */
  aside?: ReactNode
  categories: readonly PostCategory[]
  category: PostCategory | ''
  currentUserName?: string
  data: PostSummaryPage | OwnPostPage | undefined
  error?: string
  /** Most discussed recent questions for the left column. */
  hotQuestions?: readonly PostSummary[]
  isLearner: boolean
  mode: FeedMode
  onCategoryChange: (value: PostCategory | '') => void
  onPageChange: (page: number) => void
  onRetry: () => void
  onStatusChange: (value: PostStatus | '') => void
  onTabChange: (tab: FeedMode) => void
  /** Present for moderators, who may take a post off the top. */
  onUnpin?: (id: number) => void
  onWrite: () => void
  page: number
  /** Moderator posts kept above the feed. */
  pinned?: readonly PinnedPost[]
  size: number
  state: ListState
  status: PostStatus | ''
}

const CATEGORY_ICONS: Record<PostCategory, LucideIcon> = {
  EXPERIENCE: Lightbulb,
  QUESTION: CircleHelp,
  STUDY_BUDDY: UsersRound,
  CULTURE: Landmark,
  JOB: Briefcase,
  CONTEST: Trophy,
  ANNOUNCEMENT: Megaphone,
}

const EMPTY_TITLES: Record<FeedMode, string> = {
  public: 'Chưa có bài trong chủ đề này',
  following: 'Chưa có bài từ người bạn theo dõi',
  mine: 'Chưa có bài viết',
}

const EMPTY_DESCRIPTIONS: Record<FeedMode, string> = {
  public: 'Hãy chọn chủ đề khác hoặc chia sẻ bài viết mới cùng cộng đồng.',
  following: 'Mở một bài viết và bấm Theo dõi cạnh tên tác giả để thấy bài của họ ở đây.',
  mine: 'Bài bạn viết sẽ hiện ở đây kèm trạng thái duyệt.',
}

const ownStatusTones: Record<PostStatus, StatusTone> = {
  DRAFT: 'neutral',
  PENDING: 'warning',
  APPROVED: 'success',
  REJECTED: 'danger',
  HIDDEN: 'danger',
}

function Initial({ name, className }: { name: string; className?: string }) {
  return (
    <span
      aria-hidden="true"
      className={cn(
        'flex shrink-0 items-center justify-center rounded-full bg-brand-50 font-semibold text-brand-700',
        className,
      )}
    >
      {name.trim().charAt(0).toLocaleUpperCase('vi') || '?'}
    </span>
  )
}

function Loading() {
  return (
    <div aria-label="Đang tải bài viết" className="space-y-3" role="status">
      <p className="text-body-sm text-ink-secondary">Đang tải bài viết…</p>
      <Skeleton variant="card" />
      <Skeleton variant="card" />
    </div>
  )
}

function TopicNav({
  categories,
  category,
  onCategoryChange,
}: Pick<Props, 'categories' | 'category' | 'onCategoryChange'>) {
  const options: { icon: LucideIcon; label: string; value: PostCategory | '' }[] = [
    { icon: LayoutList, label: 'Tất cả chủ đề', value: '' },
    ...categories.map((value) => ({ icon: CATEGORY_ICONS[value], label: POST_CATEGORY_LABELS[value], value })),
  ]
  return (
    <nav aria-label="Chủ đề" className="min-w-0 xl:rounded-lg xl:bg-surface xl:p-4 xl:shadow-sm">
      <h2 className="mb-2 hidden text-h3 text-ink xl:block">Chủ đề</h2>
      <ul className="-mx-1 flex gap-2 overflow-x-auto px-1 pb-1 [scrollbar-width:none] xl:mx-0 xl:flex-col xl:gap-0.5 xl:overflow-visible xl:px-0">
        {options.map(({ icon: Icon, label, value }) => {
          const selected = category === value
          return (
            <li className="shrink-0" key={value || 'all'}>
              <button
                aria-pressed={selected}
                className={cn(
                  'flex min-h-touch w-full items-center gap-3 whitespace-nowrap rounded-pill border px-4 text-body-sm transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus xl:rounded-sm xl:border-0 xl:px-3',
                  selected
                    ? 'border-brand-600 bg-brand-50 font-semibold text-brand-700'
                    : 'border-line bg-surface text-ink hover:text-brand-700 xl:bg-transparent',
                )}
                onClick={() => onCategoryChange(value)}
                type="button"
              >
                <Icon aria-hidden="true" className="size-5 shrink-0" />
                {label}
              </button>
            </li>
          )
        })}
      </ul>
    </nav>
  )
}

function HotQuestions({ questions }: { questions: readonly PostSummary[] }) {
  if (questions.length === 0) return null
  return (
    <section
      aria-labelledby="hot-questions-title"
      className="hidden min-w-0 rounded-lg bg-surface p-4 shadow-sm xl:block"
    >
      <h2 className="mb-2 text-h3 text-ink" id="hot-questions-title">
        Câu hỏi được quan tâm
      </h2>
      <ol className="space-y-1">
        {questions.map((post, index) => (
          <li className="flex min-w-0 gap-2 text-body-sm" key={post.id}>
            <span className="w-4 shrink-0 tabular-nums text-ink-muted">{index + 1}.</span>
            <Link className="line-clamp-2 min-w-0 text-ink hover:text-brand-700" to={`/community/posts/${post.id}`}>
              {post.title}
            </Link>
          </li>
        ))}
      </ol>
    </section>
  )
}

function Composer({ name, onWrite }: { name?: string; onWrite: () => void }) {
  return (
    <div className="flex items-center gap-3">
      {name ? <Initial className="size-11 text-body" name={name} /> : null}
      <button
        className="flex min-h-touch min-w-0 flex-1 items-center rounded-pill border border-line bg-surface px-5 text-left text-body text-ink-muted transition-colors hover:border-brand-600 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus"
        onClick={onWrite}
        type="button"
      >
        <span className="truncate">{name ? `${name}, bạn đang thắc mắc điều gì?` : 'Viết bài chia sẻ với cộng đồng'}</span>
      </button>
    </div>
  )
}

function FeedTabs({ isLearner, mode, onTabChange }: Pick<Props, 'isLearner' | 'mode' | 'onTabChange'>) {
  const tabs: { label: string; value: FeedMode }[] = [
    { label: 'Tất cả', value: 'public' },
    ...(isLearner
      ? [
          { label: 'Đang theo dõi', value: 'following' as const },
          { label: 'Bài của tôi', value: 'mine' as const },
        ]
      : []),
  ]
  // A single tab chooses nothing, so guests see the feed straight away.
  if (tabs.length < 2) return null
  return (
    <div aria-label="Bài viết cộng đồng" className="flex gap-1 rounded-pill bg-surface p-1 shadow-sm" role="tablist">
      {tabs.map(({ label, value }) => (
        <button
          aria-selected={mode === value}
          className={cn(
            'min-h-10 flex-1 rounded-pill px-4 text-label transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus',
            mode === value ? 'bg-brand-600 text-surface' : 'text-ink-secondary hover:text-ink',
          )}
          key={value}
          onClick={() => onTabChange(value)}
          role="tab"
          type="button"
        >
          {label}
        </button>
      ))}
    </div>
  )
}

function PostCard({ post }: { post: PostSummary }) {
  const author = post.author.displayName ?? 'Người dùng đã rời'
  return (
    <article className="min-w-0 rounded-lg bg-surface p-5 shadow-sm" role="listitem">
      <header className="flex min-w-0 items-center gap-3">
        <Initial className="size-10 text-body" name={author} />
        <div className="min-w-0">
          <p className="truncate text-label text-ink">{author}</p>
          <p className="text-caption text-ink-muted">
            {POST_CATEGORY_LABELS[post.category]} · {formatRelative(post.publishedAt)}
          </p>
        </div>
      </header>
      <Link
        className="mt-3 block text-h3 text-ink hover:text-brand-700"
        to={`/community/posts/${post.id}`}
      >
        {post.title}
      </Link>
      <p className="mt-2 line-clamp-3 whitespace-pre-wrap break-words text-body text-ink-secondary">{post.excerpt}</p>
      <footer className="mt-4 flex items-center gap-5 text-body-sm text-ink-secondary">
        <span className="inline-flex items-center gap-1.5">
          <Heart aria-hidden="true" className="size-4" />
          {post.likeCount} lượt thích
        </span>
        <Link
          className="inline-flex items-center gap-1.5 hover:text-brand-700"
          to={`/community/posts/${post.id}`}
        >
          <MessageCircle aria-hidden="true" className="size-4" />
          {post.commentCount} bình luận
        </Link>
      </footer>
    </article>
  )
}

/** Moderator post held above the feed; a contest guide offers the way into the contest and its ranking. */
function PinnedCard({ onUnpin, post }: { onUnpin?: (id: number) => void; post: PinnedPost }) {
  const author = post.author.displayName ?? 'Ban quản trị'
  return (
    <article className="min-w-0 rounded-lg border-l-4 border-accent-500 bg-surface p-5 shadow-sm" role="listitem">
      <header className="flex min-w-0 items-start gap-3">
        <Initial className="size-10 text-body" name={author} />
        <div className="min-w-0 flex-1">
          <p className="truncate text-label text-ink">{author}</p>
          <p className="text-caption text-ink-muted">
            {POST_CATEGORY_LABELS[post.category]} · {formatRelative(post.publishedAt)}
          </p>
        </div>
        <span className="inline-flex shrink-0 items-center gap-1.5 rounded-pill bg-warning-bg px-3 py-1 text-label text-accent-700">
          <Pin aria-hidden="true" className="size-4" />
          Bài ghim
        </span>
      </header>
      <Link className="mt-3 block text-h3 text-ink hover:text-brand-700" to={`/community/posts/${post.id}`}>
        {post.title}
      </Link>
      <p className="mt-2 line-clamp-3 whitespace-pre-wrap break-words text-body text-ink-secondary">{post.excerpt}</p>
      <footer className="mt-4 flex flex-wrap items-center gap-3">
        {post.contestId ? (
          <>
            <Button size="sm" to={`/contests/${post.contestId}`}>
              <Trophy aria-hidden="true" className="mr-1.5 inline-block size-4 align-middle" />
              Vào thi
            </Button>
            <Button size="sm" to={`/contests/${post.contestId}/results`} variant="outline">
              Xem xếp hạng
            </Button>
          </>
        ) : (
          <Link className="text-label text-brand-700 hover:underline" to={`/community/posts/${post.id}`}>
            Đọc tiếp
          </Link>
        )}
        {onUnpin ? (
          <Button className="ml-auto" onClick={() => onUnpin(post.id)} size="sm" variant="ghost">
            Bỏ ghim
          </Button>
        ) : null}
      </footer>
    </article>
  )
}

function OwnPostCard({ post }: { post: OwnPost }) {
  const location = useLocation()
  const showReason = (post.status === 'REJECTED' || post.status === 'HIDDEN') && post.reviewReason
  return (
    <article className="rounded-lg bg-surface p-5 shadow-sm">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <h2 className="text-h3 text-ink">{post.title}</h2>
        <StatusBadge tone={ownStatusTones[post.status]}>{POST_STATUS_LABELS[post.status]}</StatusBadge>
      </div>
      <p className="mt-2 text-body-sm text-ink-secondary">{POST_CATEGORY_LABELS[post.category]}</p>
      {showReason ? (
        <p className="mt-3 whitespace-pre-wrap rounded-sm border border-line bg-canvas p-3 text-body-sm text-ink">
          {post.reviewReason}
        </p>
      ) : null}
      {post.status !== 'HIDDEN' ? (
        <Link
          className="mt-4 inline-block text-label text-brand-700 underline hover:text-brand-800"
          state={{ communityEditorOrigin: `${location.pathname}${location.search}` }}
          to={`/community/posts/${post.id}/edit`}
        >
          Sửa bài
        </Link>
      ) : null}
    </article>
  )
}

export function CommunityPostListView(props: Props) {
  const { aside, data, error, hotQuestions = [], mode, onPageChange, onRetry, page, size, state } = props
  // Pinned posts lead the unfiltered feed and are not repeated below it.
  const pinned = mode === 'public' && props.category === '' ? (props.pinned ?? []) : []
  const pinnedIds = new Set(pinned.map((post) => post.id))
  const totalElements = data?.totalElements ?? 0

  return (
    <main className="mx-auto grid w-full min-w-0 max-w-content gap-5 px-4 py-6 pb-16 [grid-template-areas:'side'_'main'_'aside'] sm:px-6 lg:grid-cols-[minmax(0,1fr)_20rem] lg:[grid-template-areas:'side_side'_'main_aside'] xl:grid-cols-[15rem_minmax(0,1fr)_20rem] xl:[grid-template-areas:'side_main_aside']">
      <h1 className="sr-only">Cộng đồng</h1>

      <div className="min-w-0 space-y-4 [grid-area:side] xl:sticky xl:top-28 xl:self-start">
        {mode === 'following' ? null : mode === 'public' ? (
          <TopicNav categories={props.categories} category={props.category} onCategoryChange={props.onCategoryChange} />
        ) : (
          <FormField className="mb-0 max-w-sm" htmlFor="community-status" label="Trạng thái">
            <Select
              id="community-status"
              onChange={(event) => props.onStatusChange(event.target.value as PostStatus | '')}
              value={props.status}
            >
              <option value="">Tất cả trạng thái</option>
              {(Object.keys(POST_STATUS_LABELS) as PostStatus[]).map((value) => (
                <option key={value} value={value}>
                  {POST_STATUS_LABELS[value]}
                </option>
              ))}
            </Select>
          </FormField>
        )}
        <HotQuestions questions={hotQuestions} />
      </div>

      <div className="min-w-0 space-y-4 [grid-area:main]">
        <Card className="p-4">
          <Composer name={props.isLearner ? props.currentUserName : undefined} onWrite={props.onWrite} />
        </Card>
        <FeedTabs isLearner={props.isLearner} mode={mode} onTabChange={props.onTabChange} />

        {pinned.length > 0 ? (
          <div aria-label="Bài ghim" className="grid gap-4" role="list">
            {pinned.map((post) => (
              <PinnedCard key={post.id} onUnpin={props.onUnpin} post={post} />
            ))}
          </div>
        ) : null}

        <PageState
          empty={
            <EmptyState
              action={
                page > 0 ? (
                  <Button onClick={() => onPageChange(0)} type="button" variant="outline">
                    Về trang đầu
                  </Button>
                ) : undefined
              }
              description={EMPTY_DESCRIPTIONS[mode]}
              title={EMPTY_TITLES[mode]}
            />
          }
          error={
            <div role="alert">
              <ErrorState message={error ?? 'Không thể tải bài viết.'} onRetry={onRetry} title="Không thể tải bài viết" />
            </div>
          }
          loading={<Loading />}
          state={state}
        >
          <>
            {mode !== 'mine' ? (
              <div aria-label={mode === 'following' ? 'Bài của người bạn theo dõi' : 'Danh sách bài công khai'} className="grid gap-4" role="list">
                {((data as PostSummaryPage | undefined)?.items ?? []).filter((post) => !pinnedIds.has(post.id)).map((post) => (
                  <PostCard key={post.id} post={post} />
                ))}
              </div>
            ) : (
              <div aria-label="Bài viết của tôi" className="grid gap-4">
                {((data as OwnPostPage | undefined)?.items ?? []).map((post) => (
                  <OwnPostCard key={post.id} post={post} />
                ))}
              </div>
            )}
            {totalElements > size ? (
              <div className="mt-6">
                <Pagination
                  label="Phân trang bài viết"
                  onChange={(nextPage) => onPageChange(nextPage - 1)}
                  page={page + 1}
                  size={size}
                  total={totalElements}
                />
              </div>
            ) : null}
          </>
        </PageState>
      </div>

      {aside ? <div className="min-w-0 [grid-area:aside] lg:sticky lg:top-28 lg:self-start">{aside}</div> : null}
    </main>
  )
}

const RELATIVE = new Intl.RelativeTimeFormat('vi', { numeric: 'auto' })

/** "3 giờ trước" for recent posts, a plain date after a week. */
function formatRelative(value: string, now = Date.now()): string {
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  const minutes = Math.round((date.getTime() - now) / 60_000)
  if (Math.abs(minutes) < 60) return RELATIVE.format(Math.min(minutes, 0), 'minute')
  const hours = Math.round(minutes / 60)
  if (Math.abs(hours) < 24) return RELATIVE.format(hours, 'hour')
  const days = Math.round(hours / 24)
  if (Math.abs(days) < 7) return RELATIVE.format(days, 'day')
  return new Intl.DateTimeFormat('vi-VN', { dateStyle: 'medium', timeZone: 'Asia/Ho_Chi_Minh' }).format(date)
}
