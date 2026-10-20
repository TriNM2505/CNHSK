import { BookOpenText, ChevronRight } from 'lucide-react'
import { Link } from 'react-router-dom'

import {
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
      <div className="rounded-md border border-dashed border-line bg-surface px-5 py-10 text-center">
        <BookOpenText aria-hidden="true" className="mx-auto size-6 text-ink-muted" />
        <p className="mt-3 text-h3 text-ink">Chưa có điểm ngữ pháp cho cấp này</p>
      </div>
    )
  }

  return (
    <div
      aria-label="Danh sách điểm ngữ pháp"
      className="divide-y divide-line rounded-md border border-line bg-surface"
    >
      {points.map((point) => (
        <Link
          className="group flex min-h-touch items-center gap-4 p-4 no-underline transition-colors hover:bg-brand-50 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-inset focus-visible:ring-focus sm:p-5"
          key={point.id}
          to={`/learn/grammar/${point.id}`}
        >
          <div className="min-w-0 flex-1">
            <div className="flex flex-wrap items-center gap-2">
              <HanziText size="sm">{point.text}</HanziText>
              <LevelBadge exact level={point.hskLevel} />
            </div>
            <p className="mt-1 text-body text-ink-secondary">{point.titleVi}</p>
            {point.bestPercent !== null && point.bestPercent !== undefined ? (
              <p className="mt-1 text-caption text-ink-muted">
                Điểm cao nhất: {point.bestPercent}%
              </p>
            ) : null}
          </div>
          {point.learned ? <StatusBadge tone="success">Đã học</StatusBadge> : null}
          <ChevronRight
            aria-hidden="true"
            className="size-5 shrink-0 text-ink-muted group-hover:text-brand-700"
          />
        </Link>
      ))}
    </div>
  )
}

function LoadingList() {
  return (
    <div aria-label="Đang tải danh sách ngữ pháp" className="space-y-3">
      <Skeleton className="h-12" />
      {[0, 1, 2].map((item) => (
        <Skeleton className="h-24" key={item} />
      ))}
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
    <div>
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
    </div>
  )
}
