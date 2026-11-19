import {
  ArrowRight,
  BarChart3,
  BookOpenCheck,
  BookOpenText,
  ClipboardList,
  FileText,
  Layers,
  Sparkles,
} from 'lucide-react'
import { useId } from 'react'

import { cn } from '@/shared/lib/cn'
import {
  Button,
  Card,
  EmptyState,
  ErrorState,
  HanziText,
  LevelBadge,
  PageBanner,
  Progress,
  Skeleton,
  StatusBadge,
  Tabs,
  type PageStateValue,
} from '@/shared/ui'

import { GRAMMAR_LEVEL_TABS, grammarTabLabel, type GrammarLevelTab } from '../grammarLevels'
import type { GrammarSummary } from '../types'
import './GrammarListView.css'

type LoadError = { message: string; requestId?: string }

export interface GrammarListViewProps {
  error?: LoadError
  groups: Readonly<Record<GrammarLevelTab, readonly GrammarSummary[]>>
  onLevelChange: (level: GrammarLevelTab) => void
  onRetry: () => void
  selectedLevel: GrammarLevelTab
  state: PageStateValue
}

function GrammarCardLandscapeArt() {
  const gradientId = useId()
  return (
    <svg
      aria-hidden="true"
      className="pointer-events-none absolute inset-0 h-full w-full select-none text-brand-600 opacity-30"
      fill="none"
      preserveAspectRatio="xMidYMax slice"
      viewBox="0 0 800 130"
    >
      <defs>
        <linearGradient id={`${gradientId}-far`} x1="0" x2="0" y1="0" y2="1">
          <stop offset="0%" stopColor="currentColor" stopOpacity="0.18" />
          <stop offset="100%" stopColor="currentColor" stopOpacity="0.02" />
        </linearGradient>
        <linearGradient id={`${gradientId}-mid`} x1="0" x2="0" y1="0" y2="1">
          <stop offset="0%" stopColor="currentColor" stopOpacity="0.28" />
          <stop offset="100%" stopColor="currentColor" stopOpacity="0.05" />
        </linearGradient>
        <linearGradient id={`${gradientId}-hill`} x1="0" x2="0" y1="0" y2="1">
          <stop offset="0%" stopColor="currentColor" stopOpacity="0.34" />
          <stop offset="100%" stopColor="currentColor" stopOpacity="0.12" />
        </linearGradient>
        <linearGradient id={`${gradientId}-softness`} x1="0" x2="0" y1="0" y2="1">
          <stop offset="0%" stopColor="currentColor" stopOpacity="0" />
          <stop offset="50%" stopColor="currentColor" stopOpacity="0.28" />
          <stop offset="100%" stopColor="currentColor" stopOpacity="0.85" />
        </linearGradient>
        <mask id={`${gradientId}-soft-mask`} style={{ maskType: 'alpha' }}>
          <rect fill={`url(#${gradientId}-softness)`} height="130" width="800" />
        </mask>
      </defs>
      <g mask={`url(#${gradientId}-soft-mask)`}>
        <path
          d="M0 115C95 94 166 120 264 104C368 88 453 116 550 101C651 85 720 111 800 93V130H0Z"
          fill="currentColor"
          opacity="0.2"
        />
        {/* Distant misty mountains */}
        <path
          fill={`url(#${gradientId}-far)`}
          d="M10 130L110 74L190 106L326 22L432 88L498 34L610 92L688 48L790 105V130H10Z"
        />

        {/* Midground mountains with light/shadow facets */}
        <path
          fill={`url(#${gradientId}-mid)`}
          d="M55 130L148 68L235 114L326 22L445 104L502 42L622 106L745 58L800 96V130H55Z"
        />
        <path className="fill-brand-600 opacity-10" d="M148 68L235 114L175 130H142L148 68Z" />
        <path className="fill-brand-600 opacity-15" d="M326 22L445 104L370 130H318L326 22Z" />
        <path className="fill-brand-600 opacity-10" d="M502 42L622 106L545 130H495L502 42Z" />

        {/* Soft foreground hill */}
        <path
          fill={`url(#${gradientId}-hill)`}
          d="M165 130C215 102 272 94 335 114C380 128 420 130 460 130H165Z"
        />

        {/* Rising sun + cloud wisps */}
        <circle className="fill-brand-100 opacity-75" cx="634" cy="34" r="24" />
        <circle className="fill-brand-600 opacity-25" cx="634" cy="34" r="17" />
        <rect className="fill-surface opacity-65" height="3" rx="1.5" width="54" x="596" y="38" />
        <rect
          className="fill-surface opacity-55"
          height="2.5"
          rx="1.25"
          width="42"
          x="618"
          y="45"
        />

        {/* Traditional main pavilion on ridge */}
        <g transform="translate(534, 34)">
          <path
            className="fill-brand-700"
            d="M28 0C21 7 12 12 2 13C7 15 14 15 28 15C42 15 49 15 54 13C44 12 35 7 28 0Z"
          />
          <circle className="fill-brand-700" cx="28" cy="-1" r="2" />
          <rect className="fill-brand-600 opacity-85" height="5" width="22" x="17" y="15" />
          <path
            className="fill-brand-600"
            d="M28 16C17 22 7 25 -4 26C3 29 14 29 28 29C42 29 53 29 60 26C49 25 39 22 28 16Z"
          />
          <rect className="fill-brand-700" height="15" width="2.5" x="12" y="29" />
          <rect className="fill-brand-700" height="15" width="2.5" x="21" y="29" />
          <rect className="fill-brand-700" height="15" width="2.5" x="32.5" y="29" />
          <rect className="fill-brand-700" height="15" width="2.5" x="41.5" y="29" />
          <rect className="fill-brand-700 opacity-90" height="4" rx="1" width="40" x="8" y="43" />
        </g>

        {/* Distant small pavilion / watchtower */}
        <g className="opacity-55" transform="translate(702, 74)">
          <path className="fill-brand-600" d="M12 0L0 7H24L12 0Z" />
          <rect className="fill-brand-600" height="8" width="2" x="5" y="7" />
          <rect className="fill-brand-600" height="8" width="2" x="11" y="7" />
          <rect className="fill-brand-600" height="8" width="2" x="17" y="7" />
          <rect className="fill-brand-700" height="3" width="18" x="3" y="15" />
        </g>

        {/* Winding Great Wall body & battlements */}
        <path
          className="fill-brand-600 opacity-25"
          d="M332 130C368 96 406 76 446 76C484 76 508 94 548 84C578 76 602 74 636 86C672 98 704 102 738 96C762 92 782 104 796 122V130H332Z"
        />
        <path
          className="fill-brand-600 opacity-15"
          d="M468 130C510 100 552 84 596 86C642 88 688 110 748 130H468Z"
        />
        <path
          className="stroke-brand-600 opacity-65"
          d="M330 128C366 94 405 74 446 74C484 74 508 92 548 82C578 74 602 72 636 84C672 96 704 100 738 94C762 90 782 102 796 120"
          strokeDasharray="4 3.5"
          strokeWidth="5"
        />
        <path
          className="stroke-brand-700 opacity-70"
          d="M332 130C368 96 406 76 446 76C484 76 508 94 548 84C578 76 602 74 636 86C672 98 704 102 738 96C762 92 782 104 796 122"
          strokeLinecap="round"
          strokeWidth="2"
        />
        <path
          className="stroke-brand-600 opacity-45"
          d="M346 130C378 104 412 86 446 86C484 86 508 104 548 94C578 86 602 84 636 96C672 108 704 112 738 106C760 102 778 112 792 128"
          strokeLinecap="round"
          strokeWidth="1.5"
        />

        {/* Bamboo grove on right edge */}
        <g className="stroke-brand-800 opacity-90" strokeLinecap="round">
          <path d="M792 130C778 88 762 42 742 -4" strokeWidth="3.5" />
          <path d="M795 130C786 84 776 38 764 -4" strokeWidth="3" />
          <path d="M798 130C793 86 788 42 784 -4" strokeWidth="2.5" />
        </g>
        <g className="fill-brand-700 opacity-90">
          <path d="M746 10C726 8 710 14 696 24C714 22 732 18 746 10Z" />
          <path d="M748 14C730 18 716 28 704 40C720 34 736 24 748 14Z" />
          <path d="M752 22C738 28 726 40 718 52C732 44 744 34 752 22Z" />
          <path d="M760 34C740 32 722 38 708 48C726 46 744 42 760 34Z" />
          <path d="M762 40C744 44 730 54 718 68C734 60 750 50 762 40Z" />
          <path d="M766 48C752 56 742 68 734 80C746 70 758 60 766 48Z" />
          <path d="M776 16C762 14 750 20 740 30C754 28 766 22 776 16Z" />
          <path d="M780 54C764 54 750 62 738 74C754 70 768 62 780 54Z" />
          <path d="M784 62C770 68 760 78 752 90C764 82 776 72 784 62Z" />
        </g>
      </g>
    </svg>
  )
}

function GrammarRowLandscapeArt() {
  return (
    <svg
      aria-hidden="true"
      className="pointer-events-none absolute inset-x-0 bottom-0 h-10 w-full text-brand-600"
      fill="none"
      preserveAspectRatio="none"
      viewBox="0 0 1200 70"
    >
      <path
        d="M0 70L85 28L175 56L290 22L420 62L580 34L740 60L910 24L1060 54L1200 30V70Z"
        fill="currentColor"
        opacity="0.06"
      />
      <path
        d="M0 70C95 44 190 38 295 56C410 74 540 50 675 58C820 66 965 36 1095 48C1140 52 1175 60 1200 66V70Z"
        fill="currentColor"
        opacity="0.08"
      />
    </svg>
  )
}

function GrammarOverviewCards({
  levelLabel,
  points,
}: {
  levelLabel: string
  points: readonly GrammarSummary[]
}) {
  const totalCount = points.length
  const completedCount = points.filter((point) => point.learned).length
  const remainingCount = totalCount - completedCount
  const stats = [
    {
      label: 'Tổng cấu trúc',
      value: totalCount,
      Icon: Layers,
      tone: 'text-brand-600',
      hint: levelLabel,
    },
    {
      label: 'Cấu trúc đã học',
      value: completedCount,
      Icon: BookOpenCheck,
      tone: 'text-success',
      hint: 'Đã hoàn thành bài học',
    },
    {
      label: 'Cấu trúc cần học',
      value: remainingCount,
      Icon: ClipboardList,
      tone: 'text-brand-600',
      hint: 'Tiếp tục từng bước mỗi ngày',
    },
  ]

  return (
    <section
      aria-label={`Tổng quan ngữ pháp ${levelLabel}`}
      className="grid min-w-0 grid-cols-3 gap-2 sm:gap-4"
    >
      {stats.map(({ label, value, Icon, tone, hint }) => (
        <Card
          key={label}
          className="flex min-w-0 items-start gap-3 border-brand-100 bg-brand-50 p-3 sm:p-5"
        >
          <div
            className={cn(
              'hidden size-11 shrink-0 items-center justify-center rounded-md border border-brand-100 bg-surface sm:flex',
              tone,
            )}
          >
            <Icon aria-hidden="true" className="size-5" />
          </div>
          <div className="min-w-0">
            <p className="text-h2 tabular-nums text-ink sm:text-h1">{value}</p>
            <p className="mt-1 text-body-sm font-semibold text-ink">{label}</p>
            <p className="mt-1 hidden text-body-sm text-ink-secondary lg:block">{hint}</p>
          </div>
        </Card>
      ))}
    </section>
  )
}

function GrammarStudyCompanion() {
  return (
    <Card className="relative isolate flex min-w-0 items-center gap-4 overflow-hidden border-brand-100 bg-surface p-4 sm:gap-6 sm:px-6">
      <GrammarRowLandscapeArt />
      <div className="relative z-base min-w-0 flex-1 space-y-1">
        <h2 className="text-label text-brand-800 sm:text-h3">Học từng cấu trúc, nhớ lâu hơn.</h2>
        <p className="hidden text-body-sm text-ink-secondary sm:block">
          Chọn cấp HSK, đọc ví dụ và làm bài tập ngắn để củng cố cách dùng.
        </p>
        <Button className="px-0" size="sm" to="/learn/progress" variant="ghost">
          <BarChart3
            aria-hidden="true"
            className="mr-1.5 inline-block size-4 align-middle text-brand-600"
          />
          Xem tiến độ học tập
          <ArrowRight aria-hidden="true" className="ml-1.5 inline-block size-4 align-middle" />
        </Button>
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
    <section aria-label={`Điểm ngữ pháp ${levelLabel}`} className="space-y-4">
      <div className="flex items-center justify-between gap-2">
        <div className="flex items-center gap-2">
          <Sparkles aria-hidden="true" className="size-5 shrink-0 text-brand-600" />
          <h2 className="text-h3 text-ink">Điểm ngữ pháp {levelLabel}</h2>
        </div>
        <span className="text-caption font-medium text-ink-secondary">
          {points.length} cấu trúc
        </span>
      </div>

      <div aria-label="Danh sách điểm ngữ pháp" className="space-y-4">
        {points.map((point) => {
          const hasBest = point.bestPercent !== null && point.bestPercent !== undefined
          return (
            <Card
              className="relative isolate min-w-0 overflow-hidden border-brand-100 p-0 shadow-sm"
              key={point.id}
            >
              <GrammarCardLandscapeArt />
              <div className="relative z-base flex min-w-0 flex-col gap-5 p-4 sm:flex-row sm:items-center sm:justify-between sm:p-5">
                <div className="min-w-0 flex-1 space-y-2">
                  <div className="flex flex-wrap items-center gap-2">
                    <StatusBadge tone={point.learned ? 'success' : 'neutral'}>
                      {point.learned ? 'Đã học' : 'Chưa học'}
                    </StatusBadge>
                    <LevelBadge exact level={point.hskLevel} />
                  </div>
                  <h3 className="break-words text-h3">
                    <HanziText className="whitespace-pre-wrap break-words" size="sm">
                      {point.text}
                    </HanziText>
                  </h3>
                  <p className="break-words text-body-sm text-ink-secondary">{point.titleVi}</p>
                  <p className="inline-flex items-center gap-2 text-body-sm text-ink-secondary">
                    <FileText aria-hidden="true" className="size-4 shrink-0 text-brand-600" />
                    {point.exerciseCount ? `${point.exerciseCount} câu luyện tập` : 'Bài đọc'}
                  </p>
                </div>

                <div className="space-y-3 sm:w-48 sm:shrink-0">
                  {hasBest ? (
                    <div className="space-y-2">
                      <p className="text-body-sm text-ink-secondary">
                        Điểm cao nhất: {point.bestPercent}%
                      </p>
                      <Progress
                        aria-label={`Điểm cao nhất của ${point.text}`}
                        value={point.bestPercent!}
                      />
                    </div>
                  ) : null}
                  <Button
                    className="w-full rounded-pill px-6"
                    aria-label={`${point.learned ? 'Học lại' : 'Học bài'}: ${point.text} — ${point.titleVi}`}
                    size="sm"
                    to={`/learn/grammar/${point.id}`}
                  >
                    {point.learned ? 'Học lại' : 'Học bài'}
                    <ArrowRight
                      aria-hidden="true"
                      className="ml-1.5 inline-block size-4 align-middle"
                    />
                  </Button>
                </div>
              </div>
            </Card>
          )
        })}
      </div>
    </section>
  )
}

function LoadingList() {
  return (
    <div aria-label="Đang tải danh sách ngữ pháp" className="space-y-3" role="status">
      <div className="grid grid-cols-3 gap-2 sm:gap-4">
        <Skeleton className="h-24" />
        <Skeleton className="h-24" />
        <Skeleton className="h-24" />
      </div>
      <Skeleton className="h-32" />
      <Skeleton className="h-12" />
      <div className="space-y-4">
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
  const activePoints = groups[selectedLevel] ?? []

  return (
    <main className="grammar-catalog mx-auto w-full min-w-0 max-w-content space-y-6 px-4 py-6 pb-24 sm:px-6 lg:px-8">
      <PageBanner
        subtitle="Một chút mỗi ngày, nắm vững cấu trúc tiếng Trung."
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
        <div className="grammar-catalog-content space-y-6">
          <GrammarOverviewCards levelLabel={grammarTabLabel(selectedLevel)} points={activePoints} />
          <GrammarStudyCompanion />

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
        </div>
      ) : null}
    </main>
  )
}
