import { Crown } from 'lucide-react'

import { cn } from '@/shared/lib/cn'
import { Button, Card, Skeleton } from '@/shared/ui'

import type { CommunityRanking, RankingEntry, RankingPeriod } from '../api/rankings'

const PERIODS: readonly { label: string; value: RankingPeriod }[] = [
  { label: 'Tuần', value: 'week' },
  { label: 'Tháng', value: 'month' },
  { label: 'Năm', value: 'year' },
]

const EMPTY_TEXT: Record<RankingPeriod, string> = {
  week: 'Tuần này chưa ai ghi điểm game.',
  month: 'Tháng này chưa ai ghi điểm game.',
  year: 'Năm nay chưa ai ghi điểm game.',
}

/** Podium order is 2 – 1 – 3 so the winner stands in the middle. */
const PODIUM = [
  { place: 2, height: 'h-20', tone: 'bg-line text-ink' },
  { place: 1, height: 'h-28', tone: 'bg-accent-500 text-ink' },
  { place: 3, height: 'h-16', tone: 'bg-warning-bg text-warning' },
] as const

export type CommunityRankingPanelProps = {
  period: RankingPeriod
  onPeriodChange: (period: RankingPeriod) => void
  onRetry: () => void
  ranking: CommunityRanking | undefined
  state: 'loading' | 'error' | 'empty' | 'ready'
}

function Avatar({ entry, size }: { entry: RankingEntry; size: 'sm' | 'lg' }) {
  const box = size === 'lg' ? 'size-12 text-h3' : 'size-8 text-label'
  return entry.avatarUrl ? (
    <img alt="" className={cn(box, 'shrink-0 rounded-full object-cover')} src={entry.avatarUrl} />
  ) : (
    <span
      aria-hidden="true"
      className={cn(box, 'flex shrink-0 items-center justify-center rounded-full bg-brand-50 text-brand-700')}
    >
      {entry.displayName.trim().charAt(0).toLocaleUpperCase('vi') || '?'}
    </span>
  )
}

function formatScore(score: number) {
  return new Intl.NumberFormat('vi-VN').format(score)
}

function Podium({ entries }: { entries: RankingEntry[] }) {
  return (
    <ol aria-label="Ba người dẫn đầu" className="grid grid-cols-3 items-end gap-2">
      {PODIUM.map(({ height, place, tone }) => {
        const entry = entries.find((item) => item.rank === place)
        return (
          <li className="flex min-w-0 flex-col items-center gap-1.5 text-center" key={place}>
            {entry ? (
              <>
                {place === 1 ? <Crown aria-hidden="true" className="size-5 text-accent-700" /> : null}
                <Avatar entry={entry} size="lg" />
                <p className="w-full truncate text-label text-ink" title={entry.displayName}>
                  {entry.displayName}
                </p>
                <p className="text-caption tabular-nums text-ink-secondary">{formatScore(entry.score)} điểm</p>
              </>
            ) : null}
            <div
              className={cn('flex w-full items-start justify-center rounded-t-md pt-2 text-h2 tabular-nums', height, tone)}
            >
              <span aria-label={`Hạng ${place}`}>{place}</span>
            </div>
          </li>
        )
      })}
    </ol>
  )
}

function Row({ entry, mine = false }: { entry: RankingEntry; mine?: boolean }) {
  return (
    <li
      className={cn(
        'flex min-w-0 items-center gap-3 rounded-sm px-2 py-2',
        mine ? 'bg-brand-50' : undefined,
      )}
    >
      <span className="w-6 shrink-0 text-center text-label tabular-nums text-ink-secondary">{entry.rank}</span>
      <Avatar entry={entry} size="sm" />
      <span className="min-w-0 flex-1 truncate text-body-sm text-ink">
        {mine ? 'Bạn' : entry.displayName}
      </span>
      <span className="shrink-0 text-label tabular-nums text-ink">{formatScore(entry.score)}</span>
    </li>
  )
}

export function CommunityRankingPanel({ onPeriodChange, onRetry, period, ranking, state }: CommunityRankingPanelProps) {
  const entries = ranking?.entries ?? []
  const rest = entries.filter((entry) => entry.rank > 3 && entry.rank <= 10)
  const mine = ranking?.myEntry
  const showMine = mine && mine.rank > 10

  return (
    <Card aria-labelledby="community-ranking-title" className="min-w-0 space-y-4 p-4" role="region">
      <div className="space-y-3">
        <h2 className="text-h3 text-ink" id="community-ranking-title">
          Vinh danh
        </h2>
        <div aria-label="Kỳ xếp hạng" className="grid grid-cols-3 gap-1 rounded-pill bg-canvas p-1" role="group">
          {PERIODS.map(({ label, value }) => (
            <button
              aria-pressed={period === value}
              className={cn(
                'min-h-9 rounded-pill text-label transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus',
                period === value ? 'bg-brand-600 text-surface' : 'text-ink-secondary hover:text-ink',
              )}
              key={value}
              onClick={() => onPeriodChange(value)}
              type="button"
            >
              {label}
            </button>
          ))}
        </div>
      </div>

      {state === 'loading' ? <Skeleton variant="card" /> : null}
      {state === 'error' ? (
        <div className="space-y-2 text-body-sm text-ink-secondary" role="alert">
          <p>Không tải được bảng xếp hạng.</p>
          <Button onClick={onRetry} size="sm" variant="outline">
            Thử lại
          </Button>
        </div>
      ) : null}
      {state === 'empty' ? <p className="text-body-sm text-ink-secondary">{EMPTY_TEXT[period]}</p> : null}
      {state === 'ready' ? (
        <>
          <Podium entries={entries} />
          {rest.length > 0 ? (
            <ol aria-label="Hạng 4 đến 10" className="space-y-0.5">
              {rest.map((entry) => (
                <Row entry={entry} key={entry.userId} mine={mine?.userId === entry.userId} />
              ))}
            </ol>
          ) : null}
          {showMine ? (
            <ol aria-label="Hạng của bạn" className="border-t border-line pt-2">
              <Row entry={mine} mine />
            </ol>
          ) : null}
          <p className="text-caption text-ink-muted">Tính theo tổng điểm các lượt chơi game.</p>
        </>
      ) : null}
    </Card>
  )
}
