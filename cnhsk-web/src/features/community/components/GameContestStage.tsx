import { Clock3, Crown, Gamepad2, Lock, Medal, Trophy, Users } from 'lucide-react'
import type { CSSProperties } from 'react'
import { Link } from 'react-router-dom'

import { cn } from '@/shared/lib/cn'
import { Card } from '@/shared/ui'

import type { GameContestLive, GameContestPhase, GameContestRankingRow } from '../api/game-contests'
import { gameSeal } from '../game-seals'
import './GameContestStage.css'

export type GameContestStageProps = {
  contest: GameContestLive
  gameName: string
  /** Server time now, so every countdown agrees with the server whatever the device clock says. */
  now: number
  viewerId?: number
  /** Where "Vào thi" goes: the game app for a learner, the sign-in page for a guest; absent when nobody can play. */
  play?: { href: string; label: string; external: boolean }
  /** Moderators get a link to the winners with contact emails. */
  resultsHref?: string
}

const PHASE: Record<GameContestPhase, { label: string; tone: string }> = {
  WAITING: { label: 'Sắp mở', tone: 'bg-canvas text-ink-secondary' },
  OPEN: { label: 'Đang thi', tone: 'bg-brand-600 text-surface' },
  TALLYING: { label: 'Đang tính kết quả', tone: 'bg-warning-bg text-accent-700' },
  FINISHED: { label: 'Đã có kết quả', tone: 'bg-brand-800 text-surface' },
}

const TIME = new Intl.DateTimeFormat('vi-VN', {
  day: '2-digit',
  hour: '2-digit',
  minute: '2-digit',
  month: '2-digit',
  timeZone: 'Asia/Ho_Chi_Minh',
})

function score(points: number) {
  return new Intl.NumberFormat('vi-VN').format(points)
}

function clock(durationMs: number | null): string {
  if (durationMs === null || durationMs < 0) return '—'
  const seconds = Math.round(durationMs / 1000)
  return `${Math.floor(seconds / 60)}:${String(seconds % 60).padStart(2, '0')}`
}

/** Big countdown blocks: days only when needed, then hours, minutes, seconds. */
function Countdown({ ms }: { ms: number }) {
  const total = Math.max(0, Math.ceil(ms / 1000))
  const parts = [
    { label: 'ngày', value: Math.floor(total / 86_400) },
    { label: 'giờ', value: Math.floor((total % 86_400) / 3600) },
    { label: 'phút', value: Math.floor((total % 3600) / 60) },
    { label: 'giây', value: total % 60 },
  ].filter((part, index) => index > 0 || part.value > 0)
  return (
    <div aria-live="off" className="flex justify-center gap-2 sm:gap-3">
      {parts.map((part) => (
        <span className="flex w-16 flex-col items-center rounded-md bg-brand-800 py-2 text-surface sm:w-20" key={part.label}>
          <span className="text-h2 font-bold tabular-nums leading-none">{String(part.value).padStart(2, '0')}</span>
          <span className="mt-1 text-caption text-brand-100">{part.label}</span>
        </span>
      ))}
    </div>
  )
}

function Initial({ name, className }: { name: string | null; className?: string }) {
  return (
    <span
      aria-hidden="true"
      className={cn('flex shrink-0 items-center justify-center rounded-full bg-brand-50 font-semibold text-brand-700', className)}
    >
      {(name ?? '?').trim().charAt(0).toLocaleUpperCase('vi') || '?'}
    </span>
  )
}

function RankingList({ rows, viewerId }: { rows: readonly GameContestRankingRow[]; viewerId?: number }) {
  if (rows.length === 0) {
    return <p className="rounded-md bg-canvas p-4 text-center text-body-sm text-ink-secondary">Chưa có ai hoàn thành lượt chơi.</p>
  }
  return (
    <ol aria-label="Bảng xếp hạng cuộc thi" className="space-y-1.5">
      {rows.map((row) => {
        const mine = row.userId === viewerId
        return (
          <li
            className={cn(
              'contest-row-enter flex items-center gap-3 rounded-pill px-3 py-2',
              mine ? 'bg-brand-800 text-surface' : row.rank <= 3 ? 'bg-warning-bg' : 'bg-canvas',
            )}
            key={row.userId}
          >
            <span className={cn('w-7 shrink-0 text-center text-h3 tabular-nums', mine ? 'text-accent-500' : 'text-brand-700')}>
              {row.rank}
            </span>
            <Initial className="size-8 text-label" name={row.displayName} />
            <span className="min-w-0 flex-1 truncate text-label">
              {row.displayName ?? 'Người chơi'}
              {mine ? <span className="ml-2 text-caption text-brand-100">(bạn)</span> : null}
            </span>
            <span className="flex items-center gap-1 text-caption tabular-nums opacity-80">
              <Clock3 aria-hidden="true" className="size-3.5" />
              {clock(row.durationMs)}
            </span>
            <span className="w-16 shrink-0 text-right font-semibold tabular-nums">{score(row.points)}</span>
          </li>
        )
      })}
    </ol>
  )
}

const CONFETTI_COLORS = ['bg-accent-500', 'bg-brand-600', 'bg-warning', 'bg-brand-100', 'bg-accent-700']

/** Places are revealed 3 → 2 → 1, the winner last and tallest, then the paper falls. */
function WinnerReveal({ rows }: { rows: readonly GameContestRankingRow[] }) {
  const order = [
    { place: 2, delay: 900, height: 'h-24', medal: 'text-ink-secondary' },
    { place: 1, delay: 1900, height: 'h-32', medal: 'text-accent-500' },
    { place: 3, delay: 0, height: 'h-16', medal: 'text-warning' },
  ]
  return (
    <div className="relative overflow-hidden rounded-lg bg-brand-800 px-3 pb-0 pt-6 text-surface">
      <p className="contest-reveal-step text-center text-label text-brand-100" style={{ animationDelay: '0ms' }}>
        Chúc mừng những người chiến thắng
      </p>
      <ol aria-label="Bục vinh danh" className="mt-4 grid grid-cols-3 items-end gap-2">
        {order.map(({ delay, height, medal, place }) => {
          const row = rows.find((item) => item.rank === place)
          return (
            <li className="contest-reveal-step flex min-w-0 flex-col items-center" key={place} style={{ animationDelay: `${delay}ms` }}>
              {place === 1 ? <Crown aria-hidden="true" className="mb-1 size-7 fill-accent-500 text-accent-500" /> : null}
              <Initial className={cn('border-2 border-accent-500', place === 1 ? 'size-16 text-h3' : 'size-12 text-h3')} name={row?.displayName ?? null} />
              <span className="mt-2 w-full truncate text-center text-label">{row ? row.displayName ?? 'Người chơi' : '—'}</span>
              <span className="text-caption tabular-nums text-brand-100">{row ? `${score(row.points)} điểm` : 'Chưa có'}</span>
              <span className={cn('mt-2 flex w-full flex-col items-center justify-start rounded-t-md bg-brand-700 pt-2', height)}>
                <Medal aria-hidden="true" className={cn('size-6', medal)} />
                <span className="text-h3 font-bold text-accent-500">{place}</span>
              </span>
            </li>
          )
        })}
      </ol>
      <div aria-hidden="true" className="contest-confetti">
        {Array.from({ length: 36 }, (_, index) => (
          <span
            className={CONFETTI_COLORS[index % CONFETTI_COLORS.length]}
            key={index}
            style={{ animationDelay: `${2300 + (index % 12) * 90}ms`, left: `${(index * 37) % 100}%` } as CSSProperties}
          />
        ))}
      </div>
    </div>
  )
}

/** SCR-081: a game contest from the waiting room through the live ranking to the winners. */
export function GameContestStage({ contest, gameName, now, play, resultsHref, viewerId }: GameContestStageProps) {
  const phase = PHASE[contest.phase]
  const startsIn = new Date(contest.startsAt).getTime() - now
  const endsIn = new Date(contest.endsAt).getTime() - now
  const resultsIn = new Date(contest.resultsAt).getTime() - now
  const me = contest.ranking.find((row) => row.userId === viewerId)

  return (
    <main className="mx-auto w-full min-w-0 max-w-reading space-y-5 px-4 py-6 pb-16 sm:px-6">
      <header className="flex items-start gap-4">
        <span
          aria-hidden="true"
          className="flex size-16 shrink-0 -rotate-3 items-center justify-center rounded-md border-2 border-accent-500 bg-brand-700 font-hanzi text-h1 text-accent-500"
          lang="zh-CN"
        >
          {gameSeal(contest.gameCode, gameName)}
        </span>
        <div className="min-w-0 flex-1 space-y-1">
          <span className={cn('inline-flex rounded-pill px-3 py-0.5 text-caption font-semibold', phase.tone)}>{phase.label}</span>
          <h1 className="text-h2 text-ink [text-wrap:balance]">{contest.title}</h1>
          <p className="flex flex-wrap items-center gap-x-3 gap-y-1 text-body-sm text-ink-secondary">
            <span className="inline-flex items-center gap-1">
              <Gamepad2 aria-hidden="true" className="size-4" />
              {gameName} · HSK 1–3
            </span>
            <span className="inline-flex items-center gap-1">
              <Clock3 aria-hidden="true" className="size-4" />
              {TIME.format(new Date(contest.startsAt))} – {TIME.format(new Date(contest.endsAt))}
            </span>
            <span className="inline-flex items-center gap-1">
              <Trophy aria-hidden="true" className="size-4" />
              Top {contest.prizeCount} nhận quà
            </span>
          </p>
        </div>
      </header>

      {contest.phase === 'WAITING' ? (
        <Card className="space-y-4 p-5 text-center">
          <p className="text-label text-ink-secondary">Cuộc thi mở sau</p>
          <Countdown ms={startsIn} />
          <p className="text-body-sm text-ink-secondary">Trang tự mở nút vào thi đúng giờ, bạn không cần tải lại.</p>
          <button className="inline-flex min-h-touch items-center gap-2 rounded-pill bg-canvas px-5 text-label text-ink-muted" disabled type="button">
            <Lock aria-hidden="true" className="size-4" />
            Chưa tới giờ thi
          </button>
        </Card>
      ) : null}

      {contest.phase === 'OPEN' ? (
        <Card className="space-y-4 p-5 text-center">
          <p className="text-label text-ink-secondary">Thời gian còn lại</p>
          <Countdown ms={endsIn} />
          {play ? (
            <a
              className="inline-flex min-h-touch items-center gap-2 rounded-pill bg-brand-600 px-6 text-label text-surface no-underline shadow-md transition-colors hover:bg-brand-700 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus"
              href={play.href}
              rel={play.external ? 'noopener' : undefined}
              target={play.external ? '_blank' : undefined}
            >
              <Gamepad2 aria-hidden="true" className="size-5" />
              {play.label}
            </a>
          ) : (
            <p className="text-body-sm text-ink-secondary">Chỉ học viên đăng nhập mới tham gia được.</p>
          )}
          <p className="text-body-sm text-ink-secondary">
            {me ? `Bạn đang hạng ${me.rank} với ${score(me.points)} điểm. Chơi thêm để nâng điểm!` : 'Chơi bao nhiêu lượt cũng được, tính lượt điểm cao nhất.'}
          </p>
        </Card>
      ) : null}

      {contest.phase === 'TALLYING' ? (
        <Card className="flex flex-col items-center gap-4 p-8 text-center" role="status">
          <span aria-hidden="true" className="contest-ring size-16" />
          <p className="text-h3 text-ink">Đang tính kết quả…</p>
          <p className="text-body-sm text-ink-secondary">
            Chờ những lượt chơi cuối nộp bài, khoảng {Math.max(1, Math.ceil(resultsIn / 1000))} giây nữa.
          </p>
        </Card>
      ) : null}

      {contest.phase === 'FINISHED' ? <WinnerReveal rows={contest.ranking} /> : null}

      {resultsHref && contest.phase === 'FINISHED' ? (
        <Link className="flex min-h-touch items-center justify-center gap-2 rounded-md border border-brand-600 text-label text-brand-700 no-underline hover:bg-brand-50" to={resultsHref}>
          <Trophy aria-hidden="true" className="size-4" />
          Xem kết quả, email người thắng và soạn bài công bố
        </Link>
      ) : null}

      {contest.phase === 'OPEN' || contest.phase === 'FINISHED' ? (
        <Card className="space-y-3 p-4 sm:p-5">
          <div className="flex items-center justify-between gap-3">
            <h2 className="text-h3 text-ink">{contest.phase === 'OPEN' ? 'Xếp hạng trực tiếp' : 'Bảng xếp hạng chung cuộc'}</h2>
            <span className="inline-flex items-center gap-1 text-caption text-ink-secondary">
              <Users aria-hidden="true" className="size-4" />
              {contest.participants} người chơi
            </span>
          </div>
          <RankingList rows={contest.ranking} viewerId={viewerId} />
          {contest.phase === 'OPEN' ? <p className="text-caption text-ink-muted">Tự cập nhật mỗi 5 giây.</p> : null}
        </Card>
      ) : null}

      {contest.description ? (
        <Card className="space-y-2 p-4 sm:p-5">
          <h2 className="text-h3 text-ink">Thể lệ</h2>
          <p className="whitespace-pre-line text-body text-ink">{contest.description}</p>
        </Card>
      ) : null}
    </main>
  )
}
