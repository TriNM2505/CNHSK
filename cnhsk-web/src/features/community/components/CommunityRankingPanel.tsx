import { Clock3, Crown, Trophy } from 'lucide-react'

import type { GameOption } from '@/features/leaderboard/types'
import { cn } from '@/shared/lib/cn'
import { Button, Card, Select, Skeleton } from '@/shared/ui'

import type { RankingEntry, RankingPeriod } from '../api/rankings'

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

/** Podium order is 2 – 1 – 3 so the winner stands in the middle. */
const PODIUM = [
  { place: 2, block: 'h-16 bg-surface/20', ring: 'ring-line' },
  { place: 1, block: 'h-24 bg-accent-500 text-ink', ring: 'ring-accent-500' },
  { place: 3, block: 'h-12 bg-surface/10', ring: 'ring-warning-bg' },
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
}: {
  label: string
  onChange: (value: T) => void
  options: readonly { label: string; value: T }[]
  value: T
}) {
  return (
    <div aria-label={label} className="grid auto-cols-fr grid-flow-col gap-1 rounded-pill bg-canvas p-1" role="group">
      {options.map((option) => (
        <button
          aria-pressed={value === option.value}
          className={cn(
            'min-h-9 rounded-pill px-2 text-label transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus',
            value === option.value ? 'bg-brand-600 text-surface shadow-sm' : 'text-ink-secondary hover:text-ink',
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
    <ol aria-label="Ba người dẫn đầu" className="grid grid-cols-3 items-end gap-2 rounded-lg bg-brand-700 px-3 pt-4 text-surface">
      {PODIUM.map(({ block, place, ring }) => {
        const entry = entries.find((item) => item.rank === place)
        const time = showTime ? formatDuration(entry?.durationMs) : null
        return (
          <li className="flex min-w-0 flex-col items-center gap-1 text-center" key={place}>
            {entry ? (
              <>
                {place === 1 ? <Crown aria-hidden="true" className="size-5 text-accent-500" /> : null}
                <Avatar className={cn('ring-2 ring-offset-2 ring-offset-brand-700', ring, place === 1 ? 'size-14 text-h3' : 'size-11 text-body')} entry={entry} />
                <p className="mt-1 w-full truncate text-label" title={entry.displayName}>
                  {entry.displayName}
                </p>
                <p className="text-caption font-semibold tabular-nums text-accent-500">{formatScore(entry.score)} điểm</p>
                {time ? (
                  <p className="inline-flex items-center gap-1 text-caption tabular-nums text-surface/80">
                    <Clock3 aria-hidden="true" className="size-3" />
                    {time}
                  </p>
                ) : null}
              </>
            ) : (
              <p className="pb-2 text-caption text-surface/60">Còn trống</p>
            )}
            <div className={cn('mt-1 flex w-full items-start justify-center rounded-t-md pt-1.5 text-h2 tabular-nums', block)}>
              <span aria-label={`Hạng ${place}`}>{place}</span>
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
    <li className={cn('flex min-w-0 items-center gap-3 rounded-md px-2 py-2', mine ? 'bg-brand-50' : 'odd:bg-canvas')}>
      <span className="w-6 shrink-0 text-center text-label tabular-nums text-ink-secondary">{entry.rank}</span>
      <Avatar className="size-8 text-label" entry={entry} />
      <span className="min-w-0 flex-1 truncate text-body-sm text-ink">{mine ? `${entry.displayName} (bạn)` : entry.displayName}</span>
      <span className="shrink-0 text-right">
        <span className="block text-label tabular-nums text-ink">{formatScore(entry.score)}</span>
        {time ? <span className="block text-caption tabular-nums text-ink-muted">{time}</span> : null}
      </span>
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

      <div className="space-y-2">
        <label className="sr-only" htmlFor="ranking-game">
          Trò chơi
        </label>
        <Select id="ranking-game" onChange={(event) => props.onGameChange(event.target.value)} value={game}>
          <option value="">Tổng điểm mọi trò chơi</option>
          {games.map((option) => (
            <option key={option.code} value={option.code}>
              {option.name}
            </option>
          ))}
        </Select>
        {showTime ? (
          <Segmented<RankingLevel>
            label="Cấp HSK"
            onChange={props.onLevelChange}
            options={LEVELS.map((value) => ({ label: `HSK ${value}`, value }))}
            value={level}
          />
        ) : null}
        <Segmented<RankingPeriod> label="Kỳ xếp hạng" onChange={props.onPeriodChange} options={PERIODS} value={period} />
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
            <ol aria-label="Hạng 4 đến 10" className="space-y-0.5">
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
