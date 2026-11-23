import { Clock3, Crown, Trophy } from 'lucide-react'

import type { GameOption } from '@/features/leaderboard/types'
import { cn } from '@/shared/lib/cn'
import { Button, Card, Skeleton } from '@/shared/ui'

import type { RankingEntry, RankingPeriod } from '../api/rankings'
import { GameSealPicker } from './GameSealPicker'

export type RankingLevel = 1 | 2 | 3

export type RankingBoard = {
  entries: RankingEntry[]
  myEntry: RankingEntry | null
}

export type CommunityRankingPanelProps = {
  /** Empty string ranks total points over every game and level. */
  game: string
  games: readonly GameOption[]
  level: RankingLevel
  onGameChange: (game: string) => void
  onLevelChange: (level: RankingLevel) => void
  onPeriodChange: (period: RankingPeriod) => void
  onRetry: () => void
  period: RankingPeriod
  ranking: RankingBoard | undefined
  state: 'loading' | 'error' | 'empty' | 'ready'
}

const PERIODS: readonly { label: string; value: RankingPeriod }[] = [
  { label: 'Tuần', value: 'week' },
  { label: 'Tháng', value: 'month' },
  { label: 'Năm', value: 'year' },
]

const PERIOD_TEXT: Record<RankingPeriod, string> = { week: 'tuần này', month: 'tháng này', year: 'năm nay' }

const LEVELS: readonly RankingLevel[] = [1, 2, 3]

/**
 * Podium order is 2 – 1 – 3 so the winner stands in the middle. Each card has a medal-coloured cap with the place
 * in a white disc, and a dark body holding the player; the winner's card is taller and wears a crown.
 */
const PODIUM = [
  { place: 2, cap: 'h-16 bg-gradient-to-b from-line to-ink-muted/50', disc: 'text-ink-secondary', lift: 'mt-8' },
  { place: 1, cap: 'h-20 bg-gradient-to-b from-warning-bg to-accent-500', disc: 'text-accent-700', lift: 'mt-0' },
  { place: 3, cap: 'h-16 bg-gradient-to-b from-warning-bg to-brand-100', disc: 'text-warning', lift: 'mt-8' },
] as const

function formatScore(score: number) {
  return new Intl.NumberFormat('vi-VN').format(score)
}

/** Completion time as m:ss, the way a game clock reads. */
function formatDuration(durationMs: number | null | undefined): string | null {
  if (durationMs === null || durationMs === undefined || durationMs < 0) return null
  const seconds = Math.round(durationMs / 1000)
  return `${Math.floor(seconds / 60)}:${String(seconds % 60).padStart(2, '0')}`
}

function Avatar({ className, entry }: { className: string; entry: RankingEntry }) {
  return entry.avatarUrl ? (
    <img alt="" className={cn('shrink-0 rounded-full object-cover', className)} src={entry.avatarUrl} />
  ) : (
    <span
      aria-hidden="true"
      className={cn('flex shrink-0 items-center justify-center rounded-full bg-brand-50 font-semibold text-brand-700', className)}
    >
      {entry.displayName.trim().charAt(0).toLocaleUpperCase('vi') || '?'}
    </span>
  )
}

function Segmented<T extends string | number>({
  label,
  onChange,
  options,
  value,
  plain = false,
}: {
  label: string
  onChange: (value: T) => void
  options: readonly { label: string; value: T }[]
  value: T
  /** Tabs without a track; the chosen one becomes a dark pill. */
  plain?: boolean
}) {
  return (
    <div
      aria-label={label}
      className={cn('grid auto-cols-fr grid-flow-col gap-1 rounded-pill', plain ? undefined : 'bg-canvas p-1')}
      role="group"
    >
      {options.map((option) => (
        <button
          aria-pressed={value === option.value}
          className={cn(
            'min-h-9 rounded-pill px-2 text-label transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus',
            value === option.value
              ? cn('text-surface shadow-sm', plain ? 'bg-brand-800' : 'bg-brand-600')
              : 'text-ink-secondary hover:text-ink',
          )}
          key={option.value}
          onClick={() => onChange(option.value)}
          type="button"
        >
          {option.label}
        </button>
      ))}
    </div>
  )
}

function Podium({ entries, showTime }: { entries: RankingEntry[]; showTime: boolean }) {
  return (
    <ol aria-label="Ba người dẫn đầu" className="grid grid-cols-3 items-end gap-1.5">
      {PODIUM.map(({ cap, disc, lift, place }) => {
        const entry = entries.find((item) => item.rank === place)
        const time = showTime ? formatDuration(entry?.durationMs) : null
        return (
          <li className={cn('relative flex min-w-0 flex-col', lift)} key={place}>
            {place === 1 ? (
              <Crown
                aria-hidden="true"
                className="absolute -top-3 left-1/2 z-10 size-6 -translate-x-1/2 fill-accent-500 text-accent-700"
              />
            ) : null}
            <div className={cn('flex items-center justify-center rounded-t-lg', cap)}>
              <span
                aria-label={`Hạng ${place}`}
                className={cn('flex size-10 items-center justify-center rounded-full bg-surface text-h3 tabular-nums shadow-sm', disc)}
              >
                {place}
              </span>
            </div>
            <div
              className={cn(
                'flex flex-col items-center gap-1 rounded-b-lg bg-brand-800 px-1.5 pb-3 pt-3 text-center text-surface',
                place === 1 ? 'min-h-36' : 'min-h-32',
              )}
            >
              {entry ? (
                <>
                  <Avatar className="size-11 text-body ring-2 ring-surface" entry={entry} />
                  <p className="mt-1 w-full truncate text-label" title={entry.displayName}>
                    {entry.displayName}
                  </p>
                  <p className="text-label tabular-nums text-accent-500">{formatScore(entry.score)}</p>
                  {time ? (
                    <p className="inline-flex items-center gap-1 text-caption tabular-nums text-surface/75">
                      <Clock3 aria-hidden="true" className="size-3" />
                      {time}
                    </p>
                  ) : null}
                </>
              ) : (
                <p className="my-auto text-caption text-surface/60">Còn trống</p>
              )}
            </div>
          </li>
        )
      })}
    </ol>
  )
}

function Row({ entry, mine, showTime }: { entry: RankingEntry; mine: boolean; showTime: boolean }) {
  const time = showTime ? formatDuration(entry.durationMs) : null
  return (
    <li
      className={cn(
        'flex min-w-0 items-center gap-3 rounded-pill py-1.5 pl-1.5 pr-1.5',
        mine ? 'bg-brand-50 ring-1 ring-brand-100' : 'bg-canvas',
      )}
    >
      <span className="flex size-9 shrink-0 items-center justify-center rounded-full bg-surface text-label tabular-nums text-ink shadow-sm">
        {entry.rank}
      </span>
      <span className="min-w-0 flex-1 truncate text-body-sm text-ink">
        {mine ? `${entry.displayName} (bạn)` : entry.displayName}
      </span>
      <span className="shrink-0 text-right">
        <span className="block text-label tabular-nums text-ink">{formatScore(entry.score)}</span>
        {time ? <span className="block text-caption tabular-nums text-ink-muted">{time}</span> : null}
      </span>
      <Avatar className="size-9 text-label" entry={entry} />
    </li>
  )
}

export function CommunityRankingPanel(props: CommunityRankingPanelProps) {
  const { game, games, level, period, ranking, state } = props
  const entries = ranking?.entries ?? []
  const rest = entries.filter((entry) => entry.rank > 3 && entry.rank <= 10)
  const mine = ranking?.myEntry ?? null
  const showTime = game !== ''
  const gameName = games.find((option) => option.code === game)?.name
  const scope = gameName ? `${gameName} · HSK ${level}` : 'Mọi trò chơi'

  return (
    <Card aria-labelledby="community-ranking-title" className="min-w-0 space-y-4 p-4" role="region">
      <header className="flex items-center gap-3">
        <span className="flex size-10 shrink-0 items-center justify-center rounded-full bg-accent-500 text-ink">
          <Trophy aria-hidden="true" className="size-5" />
        </span>
        <div className="min-w-0">
          <h2 className="text-h3 text-ink" id="community-ranking-title">
            Vinh danh
          </h2>
          <p className="truncate text-caption text-ink-secondary">
            {scope} · {PERIOD_TEXT[period]}
          </p>
        </div>
      </header>

      <div className="space-y-3">
        <GameSealPicker games={games} includeTotal onChange={props.onGameChange} value={game} />
        {showTime ? (
          <div className="flex items-center gap-2">
            <span className="w-10 shrink-0 text-caption font-semibold uppercase tracking-wide text-ink-muted">Cấp</span>
            <div className="min-w-0 flex-1">
              <Segmented<RankingLevel>
                label="Cấp HSK"
                onChange={props.onLevelChange}
                options={LEVELS.map((value) => ({ label: `HSK ${value}`, value }))}
                plain
                value={level}
              />
            </div>
          </div>
        ) : null}
        <div className="flex items-center gap-2">
          <span className="w-10 shrink-0 text-caption font-semibold uppercase tracking-wide text-ink-muted">Kỳ</span>
          <div className="min-w-0 flex-1">
            <Segmented<RankingPeriod> label="Kỳ xếp hạng" onChange={props.onPeriodChange} options={PERIODS} plain value={period} />
          </div>
        </div>
      </div>

      {state === 'loading' ? <Skeleton variant="card" /> : null}
      {state === 'error' ? (
        <div className="space-y-2 text-body-sm text-ink-secondary" role="alert">
          <p>Không tải được bảng xếp hạng.</p>
          <Button onClick={props.onRetry} size="sm" variant="outline">
            Thử lại
          </Button>
        </div>
      ) : null}
      {state === 'empty' ? (
        <p className="rounded-md bg-canvas p-4 text-center text-body-sm text-ink-secondary">
          Chưa ai ghi điểm {gameName ? `${gameName} HSK ${level}` : 'trò chơi'} {PERIOD_TEXT[period]}.
        </p>
      ) : null}
      {state === 'ready' ? (
        <>
          <Podium entries={entries} showTime={showTime} />
          {rest.length > 0 ? (
            <ol aria-label="Hạng 4 đến 10" className="space-y-2">
              {rest.map((entry) => (
                <Row entry={entry} key={entry.userId} mine={mine?.userId === entry.userId} showTime={showTime} />
              ))}
            </ol>
          ) : null}
          {mine && mine.rank > 10 ? (
            <ol aria-label="Hạng của bạn" className="border-t border-line pt-2">
              <Row entry={mine} mine showTime={showTime} />
            </ol>
          ) : null}
          <p className="text-caption text-ink-muted">
            {showTime
              ? 'Lấy lượt điểm cao nhất của mỗi người; bằng điểm thì ai hoàn thành nhanh hơn xếp trên.'
              : 'Cộng điểm mọi lượt chơi game trong kỳ, ở mọi cấp HSK.'}
          </p>
        </>
      ) : null}
    </Card>
  )
}
