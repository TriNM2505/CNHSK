import { ArrowRight, BookOpenText } from 'lucide-react'
import { Link } from 'react-router-dom'

import landscape from '@/features/auth/assets/auth-bg-002.webp'
import readingPanda from '@/features/contests/assets/contest-ongoing-panda.webp'
import { cn } from '@/shared/lib/cn'
import {
  Button,
  Card,
  EmptyState,
  ErrorState,
  FormField,
  HanziText,
  Input,
  LevelBadge,
  PageHeader,
  PageState,
  Progress,
  Select,
  Skeleton,
  StatusBadge,
  type PageStateValue,
} from '@/shared/ui'

import { TOPIC_STATUS_LABELS, TOPIC_STATUS_TONES } from '../labels'
import { HSK_LEVELS, TOPIC_STATUSES, type TopicStatusFilter } from '../topicListQuery'
import type { TopicSummary } from '../types'
import { TopicLockNotice } from './TopicLockNotice'

type LoadError = { message: string; requestId?: string }

export type TopicListStats = {
  completed: number
  inProgress: number
  total: number
}

export type TopicListViewProps = {
  error?: LoadError
  hskLevel?: TopicSummary['hskLevel']
  onClearClientFilters: () => void
  onHskLevelChange: (level?: TopicSummary['hskLevel']) => void
  onRetry: () => void
  onSearchChange: (value: string) => void
  onStatusChange: (status: TopicStatusFilter) => void
  search: string
  state: PageStateValue
  stats: TopicListStats
  status: TopicStatusFilter
  topics: readonly TopicSummary[]
}

function containsHanzi(value: string) {
  return /\p{Script=Han}/u.test(value)
}

function TopicName({ name }: { name: string }) {
  if (containsHanzi(name))
    return (
      <HanziText className="break-keep font-medium text-brand-800" size="sm">
        {name}
      </HanziText>
    )
  return <span className="text-h3 font-semibold text-ink">{name}</span>
}

function TopicCard({ topic }: { topic: TopicSummary }) {
  return (
    <article className="min-w-0 basis-72 grow">
      <Card className="group relative flex h-full flex-col gap-4 overflow-hidden rounded-lg border-brand-100 p-5 shadow-md transition-shadow hover:border-brand-600 focus-within:border-brand-600">
        <div className="-mx-5 -mt-5 flex items-center justify-between gap-3 border-b border-brand-100 bg-gradient-to-r from-brand-100/70 to-surface-subtle px-5 py-3 pr-14">
          <p className="flex min-w-0 items-center gap-2 break-words text-caption text-brand-800">
            <BookOpenText aria-hidden="true" className="size-4 shrink-0" />
            Mã chủ đề: {topic.code}
          </p>
          <LevelBadge exact level={topic.hskLevel} />
        </div>
        <Link
          aria-labelledby={`topic-title-${topic.id}`}
          className="flex min-w-0 flex-1 flex-col gap-4 rounded-sm text-ink no-underline focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus focus-visible:ring-offset-2"
          to={`/learn/topics/${topic.id}`}
        >
          <span
            className="inline-flex min-h-touch items-center break-words"
            id={`topic-title-${topic.id}`}
          >
            <TopicName name={topic.name} />
          </span>

          <div className="flex flex-wrap items-center justify-between gap-3 pr-10">
            {topic.status !== 'LOCKED' ? (
              <StatusBadge tone={TOPIC_STATUS_TONES[topic.status]}>
                {TOPIC_STATUS_LABELS[topic.status]}
              </StatusBadge>
            ) : null}
            <span className="text-body-sm tabular-nums text-ink-secondary">
              {topic.learnedWords}/{topic.totalWords} từ
            </span>
          </div>

          <div className="pr-10">
            <p className="mb-2 text-body-sm text-ink-secondary">Tiến độ học</p>
            <Progress label={`${topic.percent}%`} value={topic.percent} />
          </div>

          <div className="mt-auto space-y-4 border-t border-line pt-4">
            <div className="flex flex-wrap items-center gap-2 pr-10 text-body-sm">
              <p className="text-ink-secondary">Kết quả tốt nhất</p>
              <strong className="tabular-nums text-ink">
                {topic.bestTestPercent === null ? 'Chưa kiểm tra' : `${topic.bestTestPercent}%`}
              </strong>
            </div>
            <span
              className={cn(
                'inline-flex min-h-control items-center gap-2 rounded-sm border px-4 text-label',
                topic.status === 'IN_PROGRESS'
                  ? 'border-transparent bg-brand-600 text-surface group-hover:bg-brand-700'
                  : 'border-line bg-surface text-ink group-hover:border-brand-600 group-hover:text-brand-700',
              )}
            >
              <span className="flex items-center gap-2">
                {topic.status === 'LOCKED'
                  ? 'Xem điều kiện'
                  : topic.status === 'IN_PROGRESS'
                    ? 'Tiếp tục học'
                    : 'Xem chủ đề'}
                <ArrowRight aria-hidden="true" className="size-4 shrink-0" />
              </span>
            </span>
          </div>
        </Link>
        {topic.status === 'LOCKED' ? <TopicLockNotice lockedBy={topic.lockedBy} /> : null}
      </Card>
    </article>
  )
}

function LoadingCards() {
  return (
    <div aria-label="Đang tải chủ đề" className="flex flex-wrap gap-4">
      {[0, 1, 2].map((item) => (
        <Skeleton className="h-80 min-w-0 basis-72 grow" key={item} />
      ))}
    </div>
  )
}

export function TopicListView({
  error,
  hskLevel,
  onClearClientFilters,
  onHskLevelChange,
  onRetry,
  onSearchChange,
  onStatusChange,
  search,
  state,
  stats,
  status,
  topics,
}: TopicListViewProps) {
  const clientFilterActive = Boolean(search.trim()) || status !== 'ALL'

  return (
    <div className="mx-auto min-w-0 max-w-content space-y-4 p-4 sm:space-y-5 sm:p-6 lg:p-8">
      <div className="relative flex h-32 items-center overflow-hidden rounded-lg border border-brand-100 bg-surface-subtle p-4 shadow-sm sm:h-44 sm:p-6">
        <img
          alt=""
          aria-hidden="true"
          className="pointer-events-none absolute left-0 top-0 h-80 w-full object-cover object-right-top"
          height="1040"
          src={landscape}
          width="2048"
        />
        <div
          aria-hidden="true"
          className="pointer-events-none absolute inset-0 bg-gradient-to-r from-surface/95 via-surface/80 to-transparent"
        />
        <img
          alt=""
          aria-hidden="true"
          className="pointer-events-none absolute bottom-0 right-1 h-24 w-24 object-contain sm:right-6 sm:h-40 sm:w-40"
          height="351"
          src={readingPanda}
          width="384"
        />
        <div className="relative w-3/5 sm:w-full sm:pr-44">
          <p className="mb-2 hidden items-center gap-2 text-label text-brand-700 sm:flex">
            <BookOpenText aria-hidden="true" className="size-4 shrink-0" />
            Học từng từ, vững từng chủ đề
          </p>
          <PageHeader
            className="mb-0"
            subtitle={
              <span className="block text-caption text-ink-secondary sm:text-body-sm">
                Vững từng chủ đề
              </span>
            }
            title={
              <span className="text-brand-800">
                <span className="inline-block">Chủ đề</span>{' '}
                <span className="inline-block">từ vựng</span>
              </span>
            }
          />
        </div>
      </div>

      <section
        aria-label="Bộ lọc chủ đề"
        className="rounded-lg border border-line bg-surface p-4 shadow-sm sm:p-5"
      >
        <div className="grid gap-3 sm:grid-cols-2 sm:gap-4">
          <FormField className="mb-0" htmlFor="topic-hsk-level" label="Cấp HSK">
            <Select
              onChange={(event) => {
                const value = Number(event.target.value)
                onHskLevelChange(HSK_LEVELS.find((level) => level === value))
              }}
              value={hskLevel ?? ''}
            >
              <option value="">Tất cả</option>
              {HSK_LEVELS.map((level) => (
                <option key={level} value={level}>
                  HSK {level}
                </option>
              ))}
            </Select>
          </FormField>

          <FormField className="mb-0" htmlFor="topic-search" label="Tìm chủ đề">
            <Input
              onChange={(event) => onSearchChange(event.target.value)}
              placeholder="Nhập tên chủ đề"
              type="search"
              value={search}
            />
          </FormField>
        </div>

        <div aria-label="Lọc theo trạng thái" className="mt-4 flex flex-wrap gap-2" role="group">
          <Button
            aria-pressed={status === 'ALL'}
            onClick={() => onStatusChange('ALL')}
            size="sm"
            variant={status === 'ALL' ? 'primary' : 'outline'}
          >
            Tất cả
          </Button>
          {TOPIC_STATUSES.map((value) => (
            <Button
              aria-pressed={status === value}
              key={value}
              onClick={() => onStatusChange(value)}
              size="sm"
              variant={status === value ? 'primary' : 'outline'}
            >
              {TOPIC_STATUS_LABELS[value]}
            </Button>
          ))}
        </div>
      </section>

      {state === 'ready' ? (
        <section
          aria-label="Thống kê chủ đề"
          className="grid grid-cols-3 gap-3 rounded-md border border-brand-100 bg-surface-subtle px-3 py-3 sm:px-4"
        >
          <div className="flex min-w-0 items-center gap-2">
            <span className="text-caption text-ink-secondary">Tổng chủ đề</span>
            <strong className="order-first text-h3 tabular-nums text-brand-700">
              {stats.total}
            </strong>
          </div>
          <div className="flex min-w-0 items-center gap-2">
            <span className="text-caption text-ink-secondary">Đang học</span>
            <strong className="order-first text-h3 tabular-nums text-brand-700">
              {stats.inProgress}
            </strong>
          </div>
          <div className="flex min-w-0 items-center gap-2">
            <span className="text-caption text-ink-secondary">Hoàn thành</span>
            <strong className="order-first text-h3 tabular-nums text-success">
              {stats.completed}
            </strong>
          </div>
        </section>
      ) : null}

      <PageState
        empty={
          <EmptyState
            description="Hãy chọn cấp HSK khác để xem các chủ đề hiện có."
            icon={<BookOpenText aria-hidden="true" className="size-5" />}
            title="Chưa có chủ đề cho cấp này"
          />
        }
        error={
          <ErrorState
            message={error?.message ?? 'Vui lòng thử lại sau.'}
            onRetry={onRetry}
            requestId={error?.requestId}
          />
        }
        loading={<LoadingCards />}
        state={state}
      >
        {topics.length ? (
          <section aria-label="Danh sách chủ đề" className="flex flex-wrap gap-4">
            {topics.map((topic) => (
              <TopicCard key={topic.id} topic={topic} />
            ))}
          </section>
        ) : (
          <EmptyState
            action={
              clientFilterActive ? (
                <Button onClick={onClearClientFilters} variant="outline">
                  Xoá bộ lọc
                </Button>
              ) : undefined
            }
            description="Thử thay đổi từ khoá hoặc trạng thái đang chọn."
            title="Không có chủ đề phù hợp"
          />
        )}
      </PageState>
    </div>
  )
}
