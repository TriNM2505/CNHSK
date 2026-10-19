import { BookOpenText } from 'lucide-react'
import { Link } from 'react-router-dom'

import {
  Button,
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
  StatTile,
  StatusBadge,
  type PageStateValue,
} from '@/shared/ui'

import { TOPIC_STATUS_LABELS, TOPIC_STATUS_TONES } from '../labels'
import { HSK_LEVELS, TOPIC_STATUSES, type TopicStatusFilter } from '../topicListQuery'
import type { TopicSummary } from '../types'

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
  if (containsHanzi(name)) return <HanziText size="sm">{name}</HanziText>
  return <span className="text-h2 text-ink">{name}</span>
}

function TopicCard({ topic }: { topic: TopicSummary }) {
  return (
    <Link
      className="group flex min-h-56 flex-col rounded-md border border-line bg-surface p-5 no-underline shadow-sm transition hover:border-brand-600 hover:shadow-md focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus focus-visible:ring-offset-2"
      to={`/learn/topics/${topic.id}`}
    >
      <div className="flex items-start justify-between gap-3">
        <div className="min-w-0">
          <TopicName name={topic.name} />
          <p className="mt-1 text-caption text-ink-muted">Mã chủ đề: {topic.code}</p>
        </div>
        <LevelBadge exact level={topic.hskLevel} />
      </div>

      <div className="mt-4 flex items-center justify-between gap-3">
        <StatusBadge tone={TOPIC_STATUS_TONES[topic.status]}>
          {TOPIC_STATUS_LABELS[topic.status]}
        </StatusBadge>
        <span className="text-body-sm tabular-nums text-ink-secondary">
          {topic.learnedWords}/{topic.totalWords} từ
        </span>
      </div>

      <Progress className="mt-4" label={`${topic.percent}%`} value={topic.percent} />

      <div className="mt-auto flex items-center justify-between border-t border-line pt-4 text-body-sm">
        <span className="text-ink-secondary">Kết quả tốt nhất</span>
        <strong className="text-ink">
          {topic.bestTestPercent === null ? 'Chưa kiểm tra' : `${topic.bestTestPercent}%`}
        </strong>
      </div>
    </Link>
  )
}

function LoadingCards() {
  return (
    <div aria-label="Đang tải chủ đề" className="grid gap-4 sm:grid-cols-2 xl:grid-cols-3">
      {[0, 1, 2].map((item) => (
        <Skeleton className="h-56" key={item} />
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
    <div>
      <PageHeader
        subtitle="Theo dõi tiến độ và tiếp tục học theo từng chủ đề."
        title="Chủ đề từ vựng"
      />

      <section
        aria-label="Bộ lọc chủ đề"
        className="mb-6 rounded-md border border-line bg-surface p-4"
      >
        <div className="grid gap-4 md:grid-cols-2">
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
            variant={status === 'ALL' ? 'secondary' : 'outline'}
          >
            Tất cả
          </Button>
          {TOPIC_STATUSES.map((value) => (
            <Button
              aria-pressed={status === value}
              key={value}
              onClick={() => onStatusChange(value)}
              size="sm"
              variant={status === value ? 'secondary' : 'outline'}
            >
              {TOPIC_STATUS_LABELS[value]}
            </Button>
          ))}
        </div>
      </section>

      {state === 'ready' ? (
        <section aria-label="Thống kê chủ đề" className="mb-6 grid gap-3 sm:grid-cols-3">
          <StatTile label="Tổng chủ đề" value={stats.total} />
          <StatTile label="Đang học" value={stats.inProgress} />
          <StatTile label="Hoàn thành" value={stats.completed} />
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
          <section
            aria-label="Danh sách chủ đề"
            className="grid gap-4 sm:grid-cols-2 xl:grid-cols-3"
          >
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
