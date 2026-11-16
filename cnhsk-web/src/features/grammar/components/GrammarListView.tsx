import { BookOpenText, ChevronRight } from 'lucide-react'
import { Link } from 'react-router-dom'

import {
  Card,
  EmptyState,
  ErrorState,
  HanziText,
  LevelBadge,
  PageHeader,
  Skeleton,
  StatusBadge,
  Tabs,
  type PageStateValue,
} from '@/shared/ui'

import { GRAMMAR_LEVEL_TABS, grammarTabLabel, type GrammarLevelTab } from '../grammarLevels'
import type { GrammarSummary } from '../types'

type LoadError = { message: string; requestId?: string }

export interface GrammarListViewProps {
  error?: LoadError
  groups: Readonly<Record<GrammarLevelTab, readonly GrammarSummary[]>>
  onLevelChange: (level: GrammarLevelTab) => void
  onRetry: () => void
  selectedLevel: GrammarLevelTab
  state: PageStateValue
}

function GrammarRows({ points }: { points: readonly GrammarSummary[] }) {
  if (!points.length) {
    return (
      <EmptyState
        description="Hãy chọn cấp HSK khác trên thanh tab để xem các cấu trúc ngữ pháp hiện có."
        icon={<BookOpenText aria-hidden="true" className="size-5" />}
        title="Chưa có điểm ngữ pháp cho cấp này"
      />
    )
  }

  return (
    <div
      aria-label="Danh sách điểm ngữ pháp"
      className="grid min-w-0 grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3"
    >
      {points.map((point) => (
        <Link
          className="group block min-w-0 rounded-md no-underline focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus focus-visible:ring-offset-2"
          key={point.id}
          to={`/learn/grammar/${point.id}`}
        >
          <Card
            className="flex h-full min-w-0 flex-col justify-between gap-4 p-5"
            variant="interactive"
          >
            <div className="min-w-0 space-y-2">
              <div className="flex flex-wrap items-center justify-between gap-2">
                <LevelBadge exact level={point.hskLevel} />
                {point.learned ? <StatusBadge tone="success">Đã học</StatusBadge> : null}
              </div>
              <div className="min-w-0 break-words pt-1">
                <HanziText className="block break-words text-ink" size="sm">
                  {point.text}
                </HanziText>
              </div>
              <p className="break-words text-body text-ink-secondary">{point.titleVi}</p>
            </div>

            <div className="flex items-center justify-between gap-2 border-t border-line pt-3">
              {point.bestPercent !== null && point.bestPercent !== undefined ? (
                <p className="text-caption text-ink-muted">Điểm cao nhất: {point.bestPercent}%</p>
              ) : (
                <p className="text-caption text-ink-muted">
                  {point.exerciseCount ? `${point.exerciseCount} câu luyện tập` : 'Xem bài học'}
                </p>
              )}
              <ChevronRight
                aria-hidden="true"
                className="size-5 shrink-0 text-ink-muted transition-colors group-hover:text-brand-700"
              />
            </div>
          </Card>
        </Link>
      ))}
    </div>
  )
}

function LoadingList() {
  return (
    <div aria-label="Đang tải danh sách ngữ pháp" className="space-y-4" role="status">
      <Skeleton className="h-12" />
      <div className="grid min-w-0 grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
        {[0, 1, 2].map((item) => (
          <Skeleton key={item} variant="card" />
        ))}
      </div>
    </div>
  )
}

export function GrammarListView({
  error,
  groups,
  onLevelChange,
  onRetry,
  selectedLevel,
  state,
}: GrammarListViewProps) {
  return (
    <main className="mx-auto w-full min-w-0 max-w-content space-y-6 px-4 py-6 pb-20 sm:px-6">
      <PageHeader
        subtitle="Chọn một điểm ngữ pháp để đọc bài và luyện tập ngắn."
        title="Ngữ pháp HSK"
      />

      {state === 'loading' ? <LoadingList /> : null}
      {state === 'error' ? (
        <ErrorState
          message={error?.message ?? 'Vui lòng thử lại sau.'}
          onRetry={onRetry}
          requestId={error?.requestId}
          title="Không thể tải danh sách ngữ pháp"
        />
      ) : null}
      {state === 'ready' || state === 'empty' ? (
        <Tabs
          items={GRAMMAR_LEVEL_TABS.map((level) => ({
            id: level,
            label: `${grammarTabLabel(level)} (${groups[level].length})`,
            panel: <GrammarRows points={groups[level]} />,
          }))}
          label="Cấp HSK"
          onChange={(level) => onLevelChange(level as GrammarLevelTab)}
          value={selectedLevel}
        />
      ) : null}
    </main>
  )
}
