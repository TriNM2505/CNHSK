import { BookOpenText, CheckCircle2, ChevronRight, Sparkles } from 'lucide-react'
import { Link } from 'react-router-dom'

import {
  Card,
  EmptyState,
  ErrorState,
  HanziText,
  LevelBadge,
  PageHeader,
  Progress,
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

function GrammarNotebookSummary({
  levelLabel,
  points,
}: {
  levelLabel: string
  points: readonly GrammarSummary[]
}) {
  const totalCount = points.length
  const completedCount = points.filter((point) => point.learned).length
  const remainingCount = totalCount - completedCount
  const progressPercent = totalCount > 0 ? Math.round((completedCount / totalCount) * 100) : 0

  return (
    <Card className="border-brand-100 bg-surface-subtle p-5 sm:p-6">
      <div className="grid grid-cols-1 items-center gap-5 lg:grid-cols-12">
        <div className="space-y-3 lg:col-span-7">
          <div className="inline-flex items-center gap-1.5 rounded-pill border border-brand-100 bg-surface px-3 py-1 text-caption font-semibold text-brand-700">
            <Sparkles aria-hidden="true" className="size-3.5 shrink-0" />
            <span>Sổ tay ngữ pháp · {levelLabel}</span>
          </div>
          <div>
            <h2 className="text-h3 text-ink">Sổ tay cấu trúc {levelLabel}</h2>
            <p className="mt-1 text-body-sm text-ink-secondary">
              Tổng hợp mẫu câu trọng tâm kèm giải thích, ví dụ song ngữ Trung – Việt và bài tập ngắn
              củng cố phản xạ.
            </p>
          </div>
          <Progress label={`Tiến độ nắm vững ${levelLabel}`} tone="brand" value={progressPercent} />
        </div>

        <div className="grid grid-cols-3 gap-3 rounded-md border border-line bg-surface p-4 shadow-sm lg:col-span-5">
          <div className="text-center">
            <p className="text-h2 font-bold text-brand-700">{progressPercent}%</p>
            <p className="mt-0.5 text-caption text-ink-secondary">Tỷ lệ hoàn thành</p>
          </div>
          <div className="border-x border-line px-2 text-center">
            <p className="text-h2 font-bold text-success">{completedCount}</p>
            <p className="mt-0.5 text-caption text-ink-secondary">Hoàn thành</p>
          </div>
          <div className="text-center">
            <p className="text-h2 font-bold text-ink">{remainingCount}</p>
            <p className="mt-0.5 text-caption text-ink-secondary">Chưa học</p>
          </div>
        </div>
      </div>
    </Card>
  )
}

function GrammarRows({
  levelLabel,
  points,
}: {
  levelLabel: string
  points: readonly GrammarSummary[]
}) {
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
    <div className="space-y-5">
      <GrammarNotebookSummary levelLabel={levelLabel} points={points} />

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
              <div className="min-w-0 space-y-3">
                <div className="flex flex-wrap items-center justify-between gap-2">
                  <LevelBadge exact level={point.hskLevel} />
                  {point.learned ? (
                    <StatusBadge tone="success">Đã học</StatusBadge>
                  ) : (
                    <span className="rounded-pill border border-line bg-canvas px-2.5 py-0.5 text-caption text-ink-muted">
                      Chưa học
                    </span>
                  )}
                </div>

                <p className="break-words text-h3 font-semibold text-ink transition-colors group-hover:text-brand-700">
                  {point.titleVi}
                </p>

                <div className="rounded-sm border border-brand-100 bg-brand-50 px-3.5 py-3">
                  <p className="text-caption font-semibold uppercase tracking-wide text-brand-700">
                    Mẫu cấu trúc
                  </p>
                  <HanziText className="mt-1 block break-words text-brand-800" size="sm">
                    {point.text}
                  </HanziText>
                  {point.structure && point.structure !== point.text ? (
                    <p
                      className="mt-1 break-words font-hanzi text-body-sm text-ink-secondary"
                      lang="zh-CN"
                    >
                      {point.structure}
                    </p>
                  ) : null}
                </div>
              </div>

              <div className="flex items-center justify-between gap-2 border-t border-line pt-3">
                {point.bestPercent !== null && point.bestPercent !== undefined ? (
                  <p className="inline-flex items-center gap-1.5 text-caption font-medium text-success">
                    <CheckCircle2 aria-hidden="true" className="size-3.5 shrink-0" />
                    <span>Điểm cao nhất: {point.bestPercent}%</span>
                  </p>
                ) : (
                  <p className="text-caption text-ink-muted">
                    {point.exerciseCount ? `${point.exerciseCount} câu luyện tập` : 'Xem bài học'}
                  </p>
                )}
                <span className="inline-flex items-center gap-1 text-caption font-semibold text-brand-700">
                  <span>Mở bài</span>
                  <ChevronRight
                    aria-hidden="true"
                    className="size-4 shrink-0 transition-transform group-hover:translate-x-0.5"
                  />
                </span>
              </div>
            </Card>
          </Link>
        ))}
      </div>
    </div>
  )
}

function LoadingList() {
  return (
    <div aria-label="Đang tải danh sách ngữ pháp" className="space-y-4" role="status">
      <Skeleton className="h-12" />
      <Skeleton className="h-32" />
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
        subtitle="Chọn một điểm ngữ pháp để đọc công thức cấu trúc, xem ví dụ song ngữ và luyện tập ngắn."
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
            panel: <GrammarRows levelLabel={grammarTabLabel(level)} points={groups[level]} />,
          }))}
          label="Cấp HSK"
          onChange={(level) => onLevelChange(level as GrammarLevelTab)}
          value={selectedLevel}
        />
      ) : null}
    </main>
  )
}
